package com.example.vitruvianredux.presentation.viewmodel

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.vitruvianredux.data.ble.VitruvianBleManager
import com.example.vitruvianredux.data.logger.ConnectionLogger
import com.example.vitruvianredux.data.repository.BleRepository
import com.example.vitruvianredux.util.DeviceInfo
import com.example.vitruvianredux.util.ProtocolTester
import com.example.vitruvianredux.util.ProtocolTester.TestConfig
import com.example.vitruvianredux.util.ProtocolTester.TestResult
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * ViewModel for the Protocol Tester diagnostic tool
 */
@HiltViewModel
class ProtocolTesterViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val bleRepository: BleRepository,
    private val connectionLogger: ConnectionLogger
) : ViewModel() {

    // Test state
    sealed class TestState {
        object Idle : TestState()
        object Scanning : TestState()
        data class Testing(val currentConfig: TestConfig, val progress: Int, val total: Int) : TestState()
        data class Completed(val results: List<TestResult>) : TestState()
        data class Error(val message: String) : TestState()
    }

    private val _testState = MutableStateFlow<TestState>(TestState.Idle)
    val testState: StateFlow<TestState> = _testState.asStateFlow()

    private val _results = MutableStateFlow<List<TestResult>>(emptyList())
    val results: StateFlow<List<TestResult>> = _results.asStateFlow()

    private val _currentDeviceName = MutableStateFlow<String?>(null)
    val currentDeviceName: StateFlow<String?> = _currentDeviceName.asStateFlow()

    private val _testMode = MutableStateFlow(TestMode.RECOMMENDED)
    val testMode: StateFlow<TestMode> = _testMode.asStateFlow()

    private var testJob: Job? = null
    private var foundDevice: BluetoothDevice? = null

    enum class TestMode(val displayName: String, val description: String) {
        QUICK("Quick Test", "Test the 3 most common configurations"),
        RECOMMENDED("Recommended", "Test 7 recommended configurations"),
        COMPREHENSIVE("Comprehensive", "Test ALL protocol/delay combinations (35 tests)")
    }

    /**
     * Set the test mode
     */
    fun setTestMode(mode: TestMode) {
        _testMode.value = mode
    }

    /**
     * Start the protocol testing process
     */
    fun startTesting() {
        testJob?.cancel()
        _results.value = emptyList()

        testJob = viewModelScope.launch {
            try {
                // First, scan for device
                _testState.value = TestState.Scanning
                Timber.d("PROTOCOL_TESTER: Starting scan for Vitruvian device...")

                // Get Bluetooth adapter
                val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
                val bluetoothAdapter = bluetoothManager.adapter

                if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
                    _testState.value = TestState.Error("Bluetooth is not enabled")
                    return@launch
                }

                // Use the existing BLE repository to scan
                var deviceFound = false
                val scanJob = launch {
                    bleRepository.scannedDevices.collect { devices ->
                        if (devices.isNotEmpty() && !deviceFound) {
                            deviceFound = true
                            val device = devices.first()
                            foundDevice = bluetoothAdapter.getRemoteDevice(device.address)
                            _currentDeviceName.value = device.name ?: device.address
                            Timber.d("PROTOCOL_TESTER: Found device: ${device.name}")
                        }
                    }
                }

                // Start scanning
                bleRepository.startScanning()

                // Wait for device (max 30 seconds)
                var waitTime = 0
                while (!deviceFound && waitTime < 30000) {
                    delay(500)
                    waitTime += 500
                }

                scanJob.cancel()
                bleRepository.stopScanning()

                if (!deviceFound || foundDevice == null) {
                    _testState.value = TestState.Error("No Vitruvian device found within 30 seconds")
                    return@launch
                }

                // Get test configurations based on mode
                val configs = when (_testMode.value) {
                    TestMode.QUICK -> listOf(
                        TestConfig(ProtocolTester.InitProtocol.NO_INIT, ProtocolTester.ConnectionDelay.DELAY_2000MS),
                        TestConfig(ProtocolTester.InitProtocol.INIT_0x0A_NO_WAIT, ProtocolTester.ConnectionDelay.DELAY_50MS),
                        TestConfig(ProtocolTester.InitProtocol.INIT_0x0A_WAIT_0x0B, ProtocolTester.ConnectionDelay.DELAY_2000MS)
                    )
                    TestMode.RECOMMENDED -> ProtocolTester.generateRecommendedTestConfigs()
                    TestMode.COMPREHENSIVE -> ProtocolTester.generateAllTestConfigs()
                }

                Timber.d("PROTOCOL_TESTER: Running ${configs.size} test configurations")

                // Run each test
                val testResults = mutableListOf<TestResult>()
                configs.forEachIndexed { index, config ->
                    _testState.value = TestState.Testing(config, index + 1, configs.size)
                    Timber.d("PROTOCOL_TESTER: Test ${index + 1}/${configs.size}: ${config.protocol.displayName} + ${config.delay.displayName}")

                    val result = runSingleTest(config)
                    testResults.add(result)
                    _results.value = testResults.toList()

                    // Delay between tests to allow device to stabilize
                    if (index < configs.size - 1) {
                        delay(3000) // 3 second cooldown between tests
                    }
                }

                _testState.value = TestState.Completed(testResults)
                Timber.d("PROTOCOL_TESTER: Testing complete. ${testResults.count { it.success }}/${testResults.size} succeeded")

            } catch (e: Exception) {
                Timber.e(e, "PROTOCOL_TESTER: Error during testing")
                _testState.value = TestState.Error(e.message ?: "Unknown error")
            }
        }
    }

    /**
     * Run a single protocol test
     */
    private suspend fun runSingleTest(config: TestConfig): TestResult {
        val device = foundDevice ?: return TestResult(
            protocol = config.protocol,
            delay = config.delay,
            success = false,
            connectionTimeMs = 0,
            initTimeMs = 0,
            errorMessage = "No device available"
        )

        var connectionTimeMs = 0L
        var initTimeMs = 0L
        var errorMessage: String? = null
        var success = false

        try {
            // Create a new BLE manager for this test
            val testManager = VitruvianBleManager(context, connectionLogger)

            // Time the connection
            val connectStart = System.currentTimeMillis()

            // Connect to device
            try {
                testManager.connect(device)
                    ?.timeout(15000)
                    ?.retry(2, 200)
                    ?.useAutoConnect(false)
                    ?.await()

                connectionTimeMs = System.currentTimeMillis() - connectStart
                Timber.d("PROTOCOL_TESTER: Connected in ${connectionTimeMs}ms")

                // Apply delay if configured
                if (config.delay.delayMs > 0) {
                    Timber.d("PROTOCOL_TESTER: Waiting ${config.delay.delayMs}ms before init")
                    delay(config.delay.delayMs)
                }

                // Run init protocol
                val initStart = System.currentTimeMillis()
                success = executeInitProtocol(testManager, config.protocol)
                initTimeMs = System.currentTimeMillis() - initStart

                if (success) {
                    Timber.d("PROTOCOL_TESTER: Init successful in ${initTimeMs}ms")

                    // Try to send a simple command to verify communication
                    try {
                        val verifyResult = testManager.sendCommand(
                            byteArrayOf(0x0A, 0x00, 0x00, 0x00) // Simple reset command
                        )
                        if (verifyResult.isFailure) {
                            success = false
                            errorMessage = "Command verification failed"
                        }
                    } catch (e: Exception) {
                        success = false
                        errorMessage = "Command send failed: ${e.message}"
                    }
                } else {
                    errorMessage = "Init protocol failed"
                }

            } catch (e: Exception) {
                connectionTimeMs = System.currentTimeMillis() - connectStart
                errorMessage = "Connection failed: ${e.message}"
                Timber.e(e, "PROTOCOL_TESTER: Connection failed")
            }

            // Clean up
            try {
                testManager.stopPolling()
                testManager.cleanup()
                testManager.disconnect()?.await()
            } catch (e: Exception) {
                Timber.w("PROTOCOL_TESTER: Cleanup exception (expected): ${e.message}")
            }

            // Extra delay to ensure device is fully disconnected
            delay(1000)

        } catch (e: Exception) {
            errorMessage = "Test error: ${e.message}"
            Timber.e(e, "PROTOCOL_TESTER: Test exception")
        }

        return TestResult(
            protocol = config.protocol,
            delay = config.delay,
            success = success,
            connectionTimeMs = connectionTimeMs,
            initTimeMs = initTimeMs,
            errorMessage = errorMessage
        )
    }

    /**
     * Execute the init protocol and return success/failure
     */
    private suspend fun executeInitProtocol(
        manager: VitruvianBleManager,
        protocol: ProtocolTester.InitProtocol
    ): Boolean {
        return when (protocol) {
            ProtocolTester.InitProtocol.NO_INIT -> {
                // No init needed - success by default
                true
            }

            ProtocolTester.InitProtocol.INIT_0x0A_NO_WAIT -> {
                // Send init command but don't wait for response
                val cmd = ProtocolTester.buildInitCommandForProtocol(protocol) ?: return false
                val result = manager.sendCommand(cmd)
                result.isSuccess
            }

            ProtocolTester.InitProtocol.INIT_0x0A_WAIT_0x0B -> {
                // Send init and wait for 0x0B response
                val cmd = ProtocolTester.buildInitCommandForProtocol(protocol) ?: return false
                val sendResult = manager.sendCommand(cmd)
                if (sendResult.isFailure) return false

                // Wait for 0x0B response (5 second timeout)
                manager.awaitResponse(0x0Bu, 5000L)
            }

            ProtocolTester.InitProtocol.INIT_0x0A_PLUS_PRESET -> {
                // Send init command
                val initCmd = ProtocolTester.buildInitCommandForProtocol(protocol) ?: return false
                val initResult = manager.sendCommand(initCmd)
                if (initResult.isFailure) return false

                delay(50) // Brief delay

                // Send preset command
                val presetCmd = ProtocolTester.buildSecondaryCommandForProtocol(protocol) ?: return false
                val presetResult = manager.sendCommand(presetCmd)
                presetResult.isSuccess
            }

            ProtocolTester.InitProtocol.DOUBLE_0x0A -> {
                // Send init twice with delay
                val cmd = ProtocolTester.buildInitCommandForProtocol(protocol) ?: return false
                val result1 = manager.sendCommand(cmd)
                if (result1.isFailure) return false

                delay(100)

                val secondCmd = ProtocolTester.buildSecondaryCommandForProtocol(protocol) ?: cmd
                val result2 = manager.sendCommand(secondCmd)
                result2.isSuccess
            }
        }
    }

    /**
     * Cancel ongoing testing
     */
    fun cancelTesting() {
        testJob?.cancel()
        testJob = null
        _testState.value = TestState.Idle
        bleRepository.stopScanning()
    }

    /**
     * Generate shareable report
     */
    fun generateReport(): String {
        return ProtocolTester.formatTestReport(
            results = _results.value,
            deviceName = _currentDeviceName.value ?: "Unknown",
            androidVersion = DeviceInfo.androidVersionFull,
            appVersion = DeviceInfo.appVersionName
        )
    }

    /**
     * Reset to idle state
     */
    fun reset() {
        testJob?.cancel()
        testJob = null
        _testState.value = TestState.Idle
        _results.value = emptyList()
        _currentDeviceName.value = null
        foundDevice = null
    }

    override fun onCleared() {
        super.onCleared()
        testJob?.cancel()
    }
}
