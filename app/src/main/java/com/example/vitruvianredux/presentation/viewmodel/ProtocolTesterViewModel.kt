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
import com.example.vitruvianredux.util.ProtocolBuilder
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
        data class ExerciseCycleTesting(
            val currentPhase: ProtocolTester.ExerciseCyclePhase,
            val phaseIndex: Int,
            val totalPhases: Int,
            val elapsedWaitSeconds: Int = 0
        ) : TestState()

        data class ExerciseCycleCompleted(
            val phaseResults: List<ProtocolTester.ExerciseCyclePhaseResult>
        ) : TestState()
    }

    private val _testState = MutableStateFlow<TestState>(TestState.Idle)
    val testState: StateFlow<TestState> = _testState.asStateFlow()

    private val _results = MutableStateFlow<List<TestResult>>(emptyList())
    val results: StateFlow<List<TestResult>> = _results.asStateFlow()

    private val _exerciseCycleResults = MutableStateFlow<List<ProtocolTester.ExerciseCyclePhaseResult>>(emptyList())
    val exerciseCycleResults: StateFlow<List<ProtocolTester.ExerciseCyclePhaseResult>> = _exerciseCycleResults.asStateFlow()

    private val _currentDeviceName = MutableStateFlow<String?>(null)
    val currentDeviceName: StateFlow<String?> = _currentDeviceName.asStateFlow()

    private val _testMode = MutableStateFlow(TestMode.RECOMMENDED)
    val testMode: StateFlow<TestMode> = _testMode.asStateFlow()

    private var testJob: Job? = null
    private var foundDevice: BluetoothDevice? = null

    enum class TestMode(val displayName: String, val description: String) {
        QUICK("Quick Test", "Test the 3 most common configurations"),
        RECOMMENDED("Recommended", "Test 7 recommended configurations"),
        COMPREHENSIVE("Comprehensive", "Test ALL protocol/delay combinations (35 tests)"),
        EXERCISE_CYCLE("Exercise Cycle", "Test full start/wait/stop sequence (15 sec)")
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
        _exerciseCycleResults.value = emptyList()

        testJob = viewModelScope.launch {
            // Handle exercise cycle test mode separately
            if (_testMode.value == TestMode.EXERCISE_CYCLE) {
                runExerciseCycleTest()
                return@launch
            }

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
                    TestMode.EXERCISE_CYCLE -> emptyList() // Handled separately above
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
     * Run the exercise cycle test
     */
    private suspend fun runExerciseCycleTest() {
        val phaseResults = mutableListOf<ProtocolTester.ExerciseCyclePhaseResult>()
        val phases = ProtocolTester.ExerciseCyclePhase.values()

        try {
            // Phase 1: SCAN
            _testState.value = TestState.ExerciseCycleTesting(
                currentPhase = ProtocolTester.ExerciseCyclePhase.SCAN,
                phaseIndex = 0,
                totalPhases = phases.size
            )

            val scanStart = System.currentTimeMillis()
            Timber.d("EXERCISE_CYCLE: Phase 1 - Scanning for device...")

            val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
            val bluetoothAdapter = bluetoothManager.adapter

            if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
                phaseResults.add(ProtocolTester.ExerciseCyclePhaseResult(
                    phase = ProtocolTester.ExerciseCyclePhase.SCAN,
                    success = false,
                    durationMs = System.currentTimeMillis() - scanStart,
                    errorMessage = "Bluetooth is not enabled"
                ))
                _exerciseCycleResults.value = phaseResults
                _testState.value = TestState.ExerciseCycleCompleted(phaseResults)
                return
            }

            var deviceFound = false
            val scanJob = viewModelScope.launch {
                bleRepository.scannedDevices.collect { devices ->
                    if (devices.isNotEmpty() && !deviceFound) {
                        deviceFound = true
                        val device = devices.first()
                        foundDevice = bluetoothAdapter.getRemoteDevice(device.address)
                        _currentDeviceName.value = device.name ?: device.address
                        Timber.d("EXERCISE_CYCLE: Found device: ${device.name}")
                    }
                }
            }

            bleRepository.startScanning()

            var waitTime = 0
            while (!deviceFound && waitTime < 30000) {
                delay(500)
                waitTime += 500
            }

            scanJob.cancel()
            bleRepository.stopScanning()

            val scanDuration = System.currentTimeMillis() - scanStart

            if (!deviceFound || foundDevice == null) {
                phaseResults.add(ProtocolTester.ExerciseCyclePhaseResult(
                    phase = ProtocolTester.ExerciseCyclePhase.SCAN,
                    success = false,
                    durationMs = scanDuration,
                    errorMessage = "No Vitruvian device found within 30 seconds"
                ))
                _exerciseCycleResults.value = phaseResults
                _testState.value = TestState.ExerciseCycleCompleted(phaseResults)
                return
            }

            phaseResults.add(ProtocolTester.ExerciseCyclePhaseResult(
                phase = ProtocolTester.ExerciseCyclePhase.SCAN,
                success = true,
                durationMs = scanDuration,
                notes = "Found device: ${_currentDeviceName.value}"
            ))
            _exerciseCycleResults.value = phaseResults.toList()

            // Create BLE manager for test
            val testManager = VitruvianBleManager(context, connectionLogger)
            val device = foundDevice!!

            // Phase 2: CONNECT
            _testState.value = TestState.ExerciseCycleTesting(
                currentPhase = ProtocolTester.ExerciseCyclePhase.CONNECT,
                phaseIndex = 1,
                totalPhases = phases.size
            )

            val connectStart = System.currentTimeMillis()
            Timber.d("EXERCISE_CYCLE: Phase 2 - Connecting...")

            try {
                testManager.connect(device)
                    ?.timeout(15000)
                    ?.retry(2, 200)
                    ?.useAutoConnect(false)
                    ?.await()

                val connectDuration = System.currentTimeMillis() - connectStart
                phaseResults.add(ProtocolTester.ExerciseCyclePhaseResult(
                    phase = ProtocolTester.ExerciseCyclePhase.CONNECT,
                    success = true,
                    durationMs = connectDuration
                ))
                Timber.d("EXERCISE_CYCLE: Connected in ${connectDuration}ms")
            } catch (e: Exception) {
                val connectDuration = System.currentTimeMillis() - connectStart
                phaseResults.add(ProtocolTester.ExerciseCyclePhaseResult(
                    phase = ProtocolTester.ExerciseCyclePhase.CONNECT,
                    success = false,
                    durationMs = connectDuration,
                    errorMessage = "Connection failed: ${e.message}"
                ))
                _exerciseCycleResults.value = phaseResults
                _testState.value = TestState.ExerciseCycleCompleted(phaseResults)
                return
            }
            _exerciseCycleResults.value = phaseResults.toList()

            // Phase 3: INITIALIZE
            _testState.value = TestState.ExerciseCycleTesting(
                currentPhase = ProtocolTester.ExerciseCyclePhase.INITIALIZE,
                phaseIndex = 2,
                totalPhases = phases.size
            )

            val initStart = System.currentTimeMillis()
            val initCommand = ProtocolBuilder.buildInitCommand()
            Timber.d("EXERCISE_CYCLE: Phase 3 - Sending INIT command...")

            val initResult = testManager.sendCommand(initCommand)
            val initDuration = System.currentTimeMillis() - initStart

            phaseResults.add(ProtocolTester.ExerciseCyclePhaseResult(
                phase = ProtocolTester.ExerciseCyclePhase.INITIALIZE,
                success = initResult.isSuccess,
                durationMs = initDuration,
                commandSent = initCommand,
                errorMessage = if (initResult.isFailure) "Init command failed" else null
            ))
            _exerciseCycleResults.value = phaseResults.toList()

            if (initResult.isFailure) {
                cleanupTestManager(testManager)
                _testState.value = TestState.ExerciseCycleCompleted(phaseResults)
                return
            }

            delay(50) // Brief delay after init

            // Phase 4: CONFIGURE
            _testState.value = TestState.ExerciseCycleTesting(
                currentPhase = ProtocolTester.ExerciseCyclePhase.CONFIGURE,
                phaseIndex = 3,
                totalPhases = phases.size
            )

            val configStart = System.currentTimeMillis()
            Timber.d("EXERCISE_CYCLE: Phase 4 - Sending workout configuration...")

            // Build a safe test workout config: Old School, 10kg, 3 reps, 0 warmup
            val testParams = com.example.vitruvianredux.domain.model.WorkoutParameters(
                workoutType = com.example.vitruvianredux.domain.model.WorkoutType.Program(
                    com.example.vitruvianredux.domain.model.ProgramMode.OldSchool
                ),
                weightPerCableKg = 10f,
                reps = 3,
                warmupReps = 0,
                progressionRegressionKg = 0f,
                isJustLift = false,
                isAMRAP = false
            )
            val programFrame = ProtocolBuilder.buildProgramParams(testParams)

            val configResult = testManager.sendCommand(programFrame)
            val configDuration = System.currentTimeMillis() - configStart

            phaseResults.add(ProtocolTester.ExerciseCyclePhaseResult(
                phase = ProtocolTester.ExerciseCyclePhase.CONFIGURE,
                success = configResult.isSuccess,
                durationMs = configDuration,
                commandSent = programFrame,
                notes = "Mode: Old School, Weight: 10kg, Reps: 3",
                errorMessage = if (configResult.isFailure) "Config command failed" else null
            ))
            _exerciseCycleResults.value = phaseResults.toList()

            if (configResult.isFailure) {
                cleanupTestManager(testManager)
                _testState.value = TestState.ExerciseCycleCompleted(phaseResults)
                return
            }

            delay(100) // Brief delay after config

            // Phase 5: START
            _testState.value = TestState.ExerciseCycleTesting(
                currentPhase = ProtocolTester.ExerciseCyclePhase.START,
                phaseIndex = 4,
                totalPhases = phases.size
            )

            val startCmdStart = System.currentTimeMillis()
            val startCommand = ProtocolBuilder.buildStartCommand()
            Timber.d("EXERCISE_CYCLE: Phase 5 - Sending START command...")

            val startResult = testManager.sendCommand(startCommand)
            val startDuration = System.currentTimeMillis() - startCmdStart

            phaseResults.add(ProtocolTester.ExerciseCyclePhaseResult(
                phase = ProtocolTester.ExerciseCyclePhase.START,
                success = startResult.isSuccess,
                durationMs = startDuration,
                commandSent = startCommand,
                errorMessage = if (startResult.isFailure) "Start command failed" else null
            ))
            _exerciseCycleResults.value = phaseResults.toList()

            if (startResult.isFailure) {
                cleanupTestManager(testManager)
                _testState.value = TestState.ExerciseCycleCompleted(phaseResults)
                return
            }

            // Phase 6: WAIT (15 seconds)
            Timber.d("EXERCISE_CYCLE: Phase 6 - Waiting 15 seconds...")
            val waitStart = System.currentTimeMillis()

            for (second in 0 until 15) {
                _testState.value = TestState.ExerciseCycleTesting(
                    currentPhase = ProtocolTester.ExerciseCyclePhase.WAIT,
                    phaseIndex = 5,
                    totalPhases = phases.size,
                    elapsedWaitSeconds = second + 1
                )
                delay(1000)
            }

            val waitDuration = System.currentTimeMillis() - waitStart
            phaseResults.add(ProtocolTester.ExerciseCyclePhaseResult(
                phase = ProtocolTester.ExerciseCyclePhase.WAIT,
                success = true,
                durationMs = waitDuration,
                notes = "Waited 15 seconds"
            ))
            _exerciseCycleResults.value = phaseResults.toList()

            // Phase 7: STOP (Primary - 0x05)
            _testState.value = TestState.ExerciseCycleTesting(
                currentPhase = ProtocolTester.ExerciseCyclePhase.STOP_PRIMARY,
                phaseIndex = 6,
                totalPhases = phases.size
            )

            val stop1Start = System.currentTimeMillis()
            val stopCommand = ProtocolBuilder.buildStopCommand()
            Timber.d("EXERCISE_CYCLE: Phase 7 - Sending STOP command (0x05)...")

            val stop1Result = testManager.sendCommand(stopCommand)
            val stop1Duration = System.currentTimeMillis() - stop1Start

            phaseResults.add(ProtocolTester.ExerciseCyclePhaseResult(
                phase = ProtocolTester.ExerciseCyclePhase.STOP_PRIMARY,
                success = stop1Result.isSuccess,
                durationMs = stop1Duration,
                commandSent = stopCommand,
                errorMessage = if (stop1Result.isFailure) "Stop command (0x05) failed" else null
            ))
            _exerciseCycleResults.value = phaseResults.toList()

            delay(100) // Brief delay between stop commands

            // Phase 8: STOP (Official - 0x50)
            _testState.value = TestState.ExerciseCycleTesting(
                currentPhase = ProtocolTester.ExerciseCyclePhase.STOP_OFFICIAL,
                phaseIndex = 7,
                totalPhases = phases.size
            )

            val stop2Start = System.currentTimeMillis()
            val officialStopPacket = ProtocolBuilder.buildOfficialStopPacket()
            Timber.d("EXERCISE_CYCLE: Phase 8 - Sending official stop packet (0x50)...")

            val stop2Result = testManager.sendCommand(officialStopPacket)
            val stop2Duration = System.currentTimeMillis() - stop2Start

            phaseResults.add(ProtocolTester.ExerciseCyclePhaseResult(
                phase = ProtocolTester.ExerciseCyclePhase.STOP_OFFICIAL,
                success = stop2Result.isSuccess,
                durationMs = stop2Duration,
                commandSent = officialStopPacket,
                errorMessage = if (stop2Result.isFailure) "Official stop packet (0x50) failed" else null
            ))
            _exerciseCycleResults.value = phaseResults.toList()

            // Phase 9: CLEANUP
            _testState.value = TestState.ExerciseCycleTesting(
                currentPhase = ProtocolTester.ExerciseCyclePhase.CLEANUP,
                phaseIndex = 8,
                totalPhases = phases.size
            )

            val cleanupStart = System.currentTimeMillis()
            Timber.d("EXERCISE_CYCLE: Phase 9 - Cleaning up...")

            cleanupTestManager(testManager)

            val cleanupDuration = System.currentTimeMillis() - cleanupStart
            phaseResults.add(ProtocolTester.ExerciseCyclePhaseResult(
                phase = ProtocolTester.ExerciseCyclePhase.CLEANUP,
                success = true,
                durationMs = cleanupDuration
            ))
            _exerciseCycleResults.value = phaseResults.toList()

            Timber.d("EXERCISE_CYCLE: Test complete. ${phaseResults.count { it.success }}/${phaseResults.size} phases passed")
            _testState.value = TestState.ExerciseCycleCompleted(phaseResults)

        } catch (e: Exception) {
            Timber.e(e, "EXERCISE_CYCLE: Unexpected error")
            phaseResults.add(ProtocolTester.ExerciseCyclePhaseResult(
                phase = ProtocolTester.ExerciseCyclePhase.values().last(),
                success = false,
                durationMs = 0,
                errorMessage = "Unexpected error: ${e.message}"
            ))
            _exerciseCycleResults.value = phaseResults
            _testState.value = TestState.ExerciseCycleCompleted(phaseResults)
        }
    }

    private suspend fun cleanupTestManager(testManager: VitruvianBleManager) {
        try {
            testManager.stopPolling()
            testManager.cleanup()
            testManager.disconnect()?.await()
        } catch (e: Exception) {
            Timber.w("EXERCISE_CYCLE: Cleanup exception (expected): ${e.message}")
        }
        delay(1000) // Stabilization delay
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
     * Generate shareable report for exercise cycle test
     */
    fun generateExerciseCycleReport(): String {
        return ProtocolTester.formatExerciseCycleReport(
            phaseResults = _exerciseCycleResults.value,
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
        _exerciseCycleResults.value = emptyList()
        _currentDeviceName.value = null
        foundDevice = null
    }

    override fun onCleared() {
        super.onCleared()
        testJob?.cancel()
    }
}
