package com.example.vitruvianredux.presentation.viewmodel

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.vitruvianredux.data.ble.VitruvianBleManager
import com.example.vitruvianredux.data.logger.ConnectionLogger
import com.example.vitruvianredux.data.repository.BleRepository
import com.example.vitruvianredux.util.DeviceInfo
import com.example.vitruvianredux.util.ProtocolBuilder
import com.example.vitruvianredux.util.ProtocolTester
import com.example.vitruvianredux.util.ProtocolTester.TestConfig
import com.example.vitruvianredux.util.ProtocolTester.TestDiagnostics
import com.example.vitruvianredux.util.ProtocolTester.TestResult
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import com.example.vitruvianredux.data.ble.ConnectionStatus
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
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
                    // scannedDevices emits individual ScanResult items
                    bleRepository.scannedDevices.collect { scanResult ->
                        if (!deviceFound) {
                            deviceFound = true
                            foundDevice = bluetoothAdapter.getRemoteDevice(scanResult.device.address)
                            _currentDeviceName.value = scanResult.device.name ?: scanResult.device.address
                            Timber.d("PROTOCOL_TESTER: Found device: ${scanResult.device.name}")
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

        // Diagnostic data collection
        var firmwareVersion: String? = null
        var mtuSize: Int? = null
        var monitorPollsReceived = 0
        var disconnectTimeSeconds: Int? = null
        var lastDisconnectReason: String? = null
        var workoutSimulationDurationMs = 0L

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
                    ?.enqueue()

                // CRITICAL: Wait for service discovery to complete (ConnectionStatus.Ready)
                // The .await()/.enqueue() only waits for GATT connection, not service discovery
                Timber.d("PROTOCOL_TESTER: GATT connection initiated, waiting for service discovery...")

                val readyState = withTimeoutOrNull(15000L) {
                    testManager.connectionState.first { status ->
                        status is ConnectionStatus.Ready || status is ConnectionStatus.Error
                    }
                }

                connectionTimeMs = System.currentTimeMillis() - connectStart

                // Capture diagnostic info after connection
                firmwareVersion = testManager.detectedFirmwareVersion
                mtuSize = testManager.negotiatedMtu
                Timber.d("PROTOCOL_TESTER: Diagnostics - Firmware: $firmwareVersion, MTU: $mtuSize")

                when (readyState) {
                    null -> {
                        errorMessage = "Service discovery timeout (15s)"
                        lastDisconnectReason = "Service discovery timeout"
                        Timber.e("PROTOCOL_TESTER: Service discovery timeout")
                        throw Exception("Service discovery timeout")
                    }
                    is ConnectionStatus.Error -> {
                        errorMessage = "Connection error: ${readyState.message}"
                        lastDisconnectReason = readyState.message
                        Timber.e("PROTOCOL_TESTER: Connection error: ${readyState.message}")
                        throw Exception(readyState.message)
                    }
                    is ConnectionStatus.Ready -> {
                        Timber.d("PROTOCOL_TESTER: Service discovery complete in ${connectionTimeMs}ms")
                    }
                    else -> {
                        errorMessage = "Unexpected state: $readyState"
                        throw Exception("Unexpected state")
                    }
                }

                // Apply delay if configured (after service discovery)
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

                    // ========== WORKOUT SIMULATION TEST ==========
                    // This tests the actual workout flow where users see 5-second disconnects
                    val workoutStart = System.currentTimeMillis()
                    try {
                        Timber.d("PROTOCOL_TESTER: === STARTING WORKOUT SIMULATION ===")

                        // Step 1: Send INIT command (0x0A) - resets machine state
                        Timber.d("PROTOCOL_TESTER: Step 1 - Sending INIT command (0x0A)")
                        val initCmd = ProtocolBuilder.buildInitCommand()
                        testManager.sendCommand(initCmd).getOrThrow()
                        delay(50) // Web app uses 50ms between init and program

                        // Step 2: Send PROGRAM frame (Old School mode, 10kg, unlimited reps)
                        // This simulates starting a Just Lift workout
                        Timber.d("PROTOCOL_TESTER: Step 2 - Sending PROGRAM frame (Old School, 10kg)")
                        val programFrame = buildTestProgramFrame()
                        testManager.sendCommand(programFrame).getOrThrow()
                        delay(100)

                        // Step 3: Start monitor polling (100ms interval)
                        // This is where disconnects often occur
                        Timber.d("PROTOCOL_TESTER: Step 3 - Starting monitor polling")
                        testManager.startMonitorPolling()

                        // Step 4: Wait 10 seconds, watching for disconnection
                        // The 5-second disconnect issue would manifest here
                        Timber.d("PROTOCOL_TESTER: Step 4 - Monitoring connection for 10 seconds...")
                        var disconnected = false
                        var secondsElapsed = 0

                        // Collect monitor data to verify polling is working
                        val monitorJob = viewModelScope.launch {
                            testManager.monitorData.collect {
                                monitorPollsReceived++
                            }
                        }

                        repeat(10) { second ->
                            delay(1000)
                            secondsElapsed = second + 1

                            // Check if still connected
                            val currentState = testManager.connectionState.value
                            if (currentState !is ConnectionStatus.Ready) {
                                Timber.e("PROTOCOL_TESTER: DISCONNECT DETECTED at ${secondsElapsed}s! State: $currentState")
                                disconnected = true
                                disconnectTimeSeconds = secondsElapsed
                                lastDisconnectReason = when (currentState) {
                                    is ConnectionStatus.Error -> currentState.message
                                    is ConnectionStatus.Disconnected -> "Disconnected"
                                    else -> "Unknown state: $currentState"
                                }
                                errorMessage = "Disconnected after ${secondsElapsed}s during workout (5-second disconnect issue?)"
                                success = false
                                return@repeat
                            }
                            Timber.d("PROTOCOL_TESTER: Still connected at ${secondsElapsed}s (monitor polls: $monitorPollsReceived)")
                        }

                        monitorJob.cancel()
                        workoutSimulationDurationMs = System.currentTimeMillis() - workoutStart

                        if (!disconnected) {
                            // Step 5: Send STOP command to end workout cleanly
                            Timber.d("PROTOCOL_TESTER: Step 5 - Sending STOP command")
                            testManager.stopPolling()
                            val stopCmd = ProtocolBuilder.buildOfficialStopPacket()
                            testManager.sendCommand(stopCmd)

                            Timber.d("PROTOCOL_TESTER: === WORKOUT SIMULATION PASSED ===")
                            Timber.d("PROTOCOL_TESTER: Monitor polls received: $monitorPollsReceived")
                            // success remains true
                        }

                    } catch (e: Exception) {
                        workoutSimulationDurationMs = System.currentTimeMillis() - workoutStart
                        success = false
                        errorMessage = "Workout simulation failed: ${e.message}"
                        lastDisconnectReason = e.message
                        Timber.e(e, "PROTOCOL_TESTER: Workout simulation error")
                    }
                } else {
                    errorMessage = "Init protocol failed"
                }

            } catch (e: Exception) {
                connectionTimeMs = System.currentTimeMillis() - connectStart
                errorMessage = "Connection failed: ${e.message}"
                lastDisconnectReason = e.message
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
            errorMessage = errorMessage,
            diagnostics = TestDiagnostics(
                firmwareVersion = firmwareVersion,
                mtuSize = mtuSize,
                monitorPollsReceived = monitorPollsReceived,
                monitorPollsFailed = 0, // TODO: track failures
                disconnectTimeSeconds = disconnectTimeSeconds,
                lastDisconnectReason = lastDisconnectReason,
                workoutSimulationDurationMs = workoutSimulationDurationMs
            )
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

                // Wait for expected response opcode
                val expected = ProtocolTester.getExpectedResponseOpcode(protocol) ?: return false
                manager.awaitResponse(expected, 5000L)
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

            ProtocolTester.InitProtocol.INIT_DOUBLE_0x0A -> {
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
        // stopScanning is suspend - call from a coroutine
        viewModelScope.launch { bleRepository.stopScanning() }
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

    /**
     * Build a test PROGRAM frame for workout simulation
     * Uses Old School mode with 10kg weight and unlimited reps (Just Lift style)
     */
    private fun buildTestProgramFrame(): ByteArray {
        val frame = ByteArray(96)
        val buffer = java.nio.ByteBuffer.wrap(frame).order(java.nio.ByteOrder.LITTLE_ENDIAN)

        // Command 0x04 for PROGRAM mode
        frame[0] = 0x04
        frame[1] = 0x00
        frame[2] = 0x00
        frame[3] = 0x00

        // Reps = 0xFF for unlimited (Just Lift mode)
        frame[0x04] = 0xFF.toByte()

        // Constants from working capture
        frame[5] = 0x03
        frame[6] = 0x03
        frame[7] = 0x00

        // Float values
        buffer.putFloat(0x08, 5.0f)
        buffer.putFloat(0x0c, 5.0f)
        buffer.putFloat(0x1c, 5.0f)

        // Standard values
        frame[0x14] = 0xFA.toByte()
        frame[0x15] = 0x00
        frame[0x16] = 0xFA.toByte()
        frame[0x17] = 0x00
        frame[0x18] = 0xC8.toByte()
        frame[0x19] = 0x00
        frame[0x1a] = 0x1E
        frame[0x1b] = 0x00

        frame[0x24] = 0xFA.toByte()
        frame[0x25] = 0x00
        frame[0x26] = 0xFA.toByte()
        frame[0x27] = 0x00
        frame[0x28] = 0xC8.toByte()
        frame[0x29] = 0x00
        frame[0x2a] = 0x1E
        frame[0x2b] = 0x00

        frame[0x2c] = 0xFA.toByte()
        frame[0x2d] = 0x00
        frame[0x2e] = 0x50
        frame[0x2f] = 0x00

        // Old School mode profile (32 bytes at 0x30-0x4F)
        val profile = java.nio.ByteBuffer.allocate(32).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        profile.putShort(0x00, 0)
        profile.putShort(0x02, 20)
        profile.putFloat(0x04, 3.0f)
        profile.putShort(0x08, 75)
        profile.putShort(0x0a, 600)
        profile.putFloat(0x0c, 50.0f)
        profile.putShort(0x10, -1300)
        profile.putShort(0x12, -1200)
        profile.putFloat(0x14, 100.0f)
        profile.putShort(0x18, -260)
        profile.putShort(0x1a, -110)
        profile.putFloat(0x1c, 0.0f)
        System.arraycopy(profile.array(), 0, frame, 0x30, 32)

        // Weight: 10kg test weight (effective = 20kg with offset)
        buffer.putFloat(0x54, 20.0f)  // effective weight
        buffer.putFloat(0x58, 10.0f)  // total weight
        buffer.putFloat(0x5c, 0.0f)   // no progression

        return frame
    }
}
