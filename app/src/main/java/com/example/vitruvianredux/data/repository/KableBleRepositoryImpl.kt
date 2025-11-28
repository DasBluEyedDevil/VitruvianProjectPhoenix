package com.example.vitruvianredux.data.repository

import android.annotation.SuppressLint
import android.content.Context
import com.example.vitruvianredux.data.ble.BleConnectionState
import com.example.vitruvianredux.data.ble.DiscoveredDevice
import com.example.vitruvianredux.data.ble.HandleState
import com.example.vitruvianredux.data.ble.KableBleManager
import com.example.vitruvianredux.data.ble.KableBleScanner
import com.example.vitruvianredux.data.ble.RepNotification
import com.example.vitruvianredux.domain.model.ConnectionState
import com.example.vitruvianredux.domain.model.WorkoutMetric
import com.example.vitruvianredux.domain.model.WorkoutParameters
import com.example.vitruvianredux.util.ProtocolBuilder
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Kable-based BLE Repository Implementation.
 * Uses KableBleManager and KableBleScanner instead of Nordic BLE library.
 *
 * This implementation maintains API compatibility with the BleRepository interface
 * while using Kable as the underlying BLE stack.
 */
@Singleton
class KableBleRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val scanner: KableBleScanner
) : BleRepository {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var bleManager: KableBleManager? = null

    // Store discovered devices to enable connection by address
    private val discoveredDevices = mutableMapOf<String, DiscoveredDevice>()

    // Connection state
    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    override val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    // Monitor data flow
    private val _monitorData = MutableSharedFlow<WorkoutMetric>(
        replay = 0,
        extraBufferCapacity = 64,
        onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
    )
    override val monitorData: SharedFlow<WorkoutMetric> = _monitorData.asSharedFlow()

    // Rep events flow
    private val _repEvents = MutableSharedFlow<RepNotification>(
        replay = 0,
        extraBufferCapacity = 64,
        onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
    )
    override val repEvents: SharedFlow<RepNotification> = _repEvents.asSharedFlow()

    // Scanned devices flow - convert Kable advertisements to ScanResult
    private val _scannedDevices = MutableSharedFlow<android.bluetooth.le.ScanResult>(
        replay = 10,
        extraBufferCapacity = 50,
        onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
    )
    override val scannedDevices: Flow<android.bluetooth.le.ScanResult> = _scannedDevices.asSharedFlow()

    // Handle state flow
    private val _handleState = MutableStateFlow(HandleState.Released)
    override val handleState: StateFlow<HandleState> = _handleState.asStateFlow()

    // Scanning state
    private var scanningJob: kotlinx.coroutines.Job? = null

    // Cached workout parameters for re-arming (Just Lift mode)
    private var cachedWorkoutParams: WorkoutParameters? = null

    init {
        Timber.d("KableBleRepositoryImpl initialized with Kable BLE stack")
    }

    @SuppressLint("MissingPermission")
    override suspend fun startScanning(): Result<Unit> = withContext(Dispatchers.Main) {
        try {
            Timber.d("Starting Kable BLE scan...")

            // Cancel any existing scan
            stopScanning()

            _connectionState.value = ConnectionState.Scanning

            // Start collecting from scanner's advertisement flow
            scanningJob = scanner.advertisements
                .onEach { device ->
                    Timber.d("Kable found device: ${device.name} (${device.address})")
                    // Store device for later connection
                    discoveredDevices[device.address] = device

                    // Convert DiscoveredDevice to ScanResult equivalent
                    // NOTE: We can't create real ScanResult objects easily, so we skip emission for now.
                    // The UI must be updated to use DiscoveredDevice or we need a wrapper.
                }
                .catch { e ->
                    Timber.e(e, "Scan error")
                    _connectionState.value = ConnectionState.Error("Scan error: ${e.message}")
                }
                .launchIn(scope)

            Timber.d("Kable BLE scan started successfully")
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Failed to start scanning")
            _connectionState.value = ConnectionState.Error("Failed to start scanning: ${e.message}")
            Result.failure(e)
        }
    }

    override suspend fun stopScanning() = withContext(Dispatchers.Main) {
        try {
            Timber.d("Stopping Kable BLE scan...")
            scanningJob?.cancel()
            scanningJob = null

            if (_connectionState.value == ConnectionState.Scanning) {
                _connectionState.value = ConnectionState.Disconnected
            }

            Timber.d("Kable BLE scan stopped")
        } catch (e: Exception) {
            Timber.e(e, "Error stopping scan")
        }
    }

    override suspend fun connectToDevice(deviceAddress: String): Result<Unit> = withContext(Dispatchers.Main) {
        try {
            Timber.d("Connecting to device: $deviceAddress")
            stopScanning()

            // Find the device from cached scan results
            val device = discoveredDevices[deviceAddress]

            if (device == null) {
                 Timber.e("Device not found in scan results: $deviceAddress")
                 return@withContext Result.failure(Exception("Device not found. Please scan first."))
            }

            _connectionState.value = ConnectionState.Connecting

            // Create a new BLE manager
            val manager = KableBleManager()

            // Observe connection state from manager
            scope.launch {
                manager.connectionState.collect { state ->
                    Timber.d("Manager connection state: $state")
                    when (state) {
                        is com.example.vitruvianredux.data.ble.ConnectionStatus.Ready -> {
                            _connectionState.value = ConnectionState.Connected(
                                deviceName = device.name ?: "Vitruvian",
                                deviceAddress = device.address
                            )
                            Timber.d("Device connected and ready")
                        }
                        is com.example.vitruvianredux.data.ble.ConnectionStatus.Disconnected -> {
                            _connectionState.value = ConnectionState.Disconnected
                            Timber.d("Device disconnected")
                        }
                        is com.example.vitruvianredux.data.ble.ConnectionStatus.Error -> {
                            _connectionState.value = ConnectionState.Error(state.message)
                            Timber.e("Connection error: ${state.message}")
                        }
                    }
                }
            }

            // Observe deload events
            scope.launch {
                manager.deloadOccurredEvents.collect {
                    Timber.d("DELOAD_EVENT: Deload occurred (safety holding state) - NOT sending stop, exercise can resume")
                    // Note: We deliberately DO NOT send stop command here to match VitruvianBleManager behavior.
                    // The machine handles safety state; app should just monitor.
                }
            }

            // Forward monitor data
            scope.launch {
                manager.monitorData.collect { metric ->
                    _monitorData.emit(metric)
                }
            }

            // Forward rep events
            scope.launch {
                manager.repEvents.collect { rep ->
                    _repEvents.emit(rep)
                }
            }

            // Forward handle state
            scope.launch {
                manager.handleState.collect { state ->
                    _handleState.value = state
                }
            }

            bleManager = manager
            manager.connect(device)

            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Failed to connect to device")
            _connectionState.value = ConnectionState.Error("Connection failed: ${e.message}")
            Result.failure(e)
        }
    }

    override suspend fun cancelConnection() = withContext(Dispatchers.Main) {
        try {
            Timber.d("Cancelling connection...")
            bleManager?.disconnect()
            bleManager = null

            if (_connectionState.value is ConnectionState.Connecting) {
                _connectionState.value = ConnectionState.Disconnected
            }
        } catch (e: Exception) {
            Timber.e(e, "Error cancelling connection")
        }
    }

    override suspend fun disconnect() = withContext(Dispatchers.Main) {
        try {
            Timber.d("Disconnecting from device...")
            bleManager?.disconnect()
            bleManager?.close()
            bleManager = null
            _connectionState.value = ConnectionState.Disconnected
            Timber.d("Disconnected successfully")
        } catch (e: Exception) {
            Timber.e(e, "Error during disconnect")
            bleManager = null
            _connectionState.value = ConnectionState.Disconnected
        }
    }

    override suspend fun sendInitSequence(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            Timber.d("sendInitSequence - deprecated (official app doesn't use 0x0A handshake)")
            // The init sequence is deprecated as per the existing implementation
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Failed to send init sequence")
            Result.failure(e)
        }
    }

    override suspend fun startWorkout(params: WorkoutParameters): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            Timber.d("Starting workout with params: $params")
            cachedWorkoutParams = params

            val manager = bleManager ?: return@withContext Result.failure(Exception("Not connected"))

            // Send INIT command (0x0A) to reset machine state
            Timber.d("Sending INIT command (0x0A) to reset machine state...")
            val initCommand = ProtocolBuilder.buildInitCommand()
            manager.sendCommand(initCommand).getOrThrow()
            delay(50) // Web app uses 50ms between init and program commands

            // Send workout command based on type
            when (params.workoutType) {
                is com.example.vitruvianredux.domain.model.WorkoutType.Echo -> {
                    Timber.d("Echo mode: sending echo control frame")
                    val echoFrame = ProtocolBuilder.buildEchoControl(
                        level = params.workoutType.level,
                        warmupReps = params.warmupReps,
                        targetReps = params.reps,
                        isJustLift = params.isJustLift,
                        isAMRAP = params.isAMRAP,
                        eccentricPct = params.workoutType.eccentricLoad.percentage
                    )
                    manager.sendCommand(echoFrame).getOrThrow()
                }
                is com.example.vitruvianredux.domain.model.WorkoutType.Program -> {
                    Timber.d("Program mode: sending program parameters")
                    val programFrame = ProtocolBuilder.buildProgramParams(params)
                    manager.sendCommand(programFrame).getOrThrow()
                }
            }

            delay(100)

            // Start monitor polling
            Timber.d("Starting monitor polling for workout...")
            manager.startMonitorPolling()

            Timber.d("Workout started successfully")
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Failed to start workout")
            Result.failure(e)
        }
    }

    override suspend fun stopWorkout(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            Timber.d("Stopping workout...")

            val manager = bleManager ?: return@withContext Result.failure(Exception("Not connected"))

            // Send RESET command (0x0A) - matches web app behavior
            val resetCommand = ProtocolBuilder.buildResetCommand()
            Timber.d("Sending RESET command (0x0A)...")
            manager.sendCommand(resetCommand).getOrThrow()
            delay(50)

            Timber.d("Workout stopped successfully")
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Failed to stop workout")
            Result.failure(e)
        }
    }

    override suspend fun sendStopCommand(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            Timber.d("Sending stop command (without stopping polling)...")

            val manager = bleManager ?: return@withContext Result.failure(Exception("Not connected"))

            // Send StopPacket (0x50) - official app stop command
            val stopPacket = ProtocolBuilder.buildOfficialStopPacket()
            manager.sendCommand(stopPacket).getOrThrow()

            Timber.d("Stop command sent successfully")
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Failed to send stop command")
            Result.failure(e)
        }
    }

    override suspend fun setColorScheme(schemeIndex: Int): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            Timber.d("Setting color scheme: $schemeIndex")

            val manager = bleManager ?: return@withContext Result.failure(Exception("Not connected"))

            val colorFrame = ProtocolBuilder.buildColorSchemeCommand(schemeIndex)
            manager.sendCommand(colorFrame).getOrThrow()

            Timber.d("Color scheme set successfully")
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Failed to set color scheme")
            Result.failure(e)
        }
    }

    override suspend fun testOfficialAppProtocol(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            Timber.d("Testing official app protocol")
            bleManager?.testOfficialAppProtocol()?.getOrThrow()
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Failed to test official app protocol")
            Result.failure(e)
        }
    }

    override fun enableHandleDetection() {
        Timber.d("Enable handle detection - starting monitor polling for auto-start")
        bleManager?.startMonitorPolling(forAutoStart = true)
    }

    override fun enableJustLiftWaitingMode() {
        Timber.d("Enable Just Lift waiting mode - position-based handle detection")
        bleManager?.enableJustLiftWaitingMode()
    }

    override fun restartMonitorPolling() {
        Timber.d("Restart monitor polling - clearing danger zone alarm state on machine")
        bleManager?.startMonitorPolling()
    }

    /**
     * Cleanup resources when repository is no longer needed.
     */
    fun cleanup() {
        Timber.d("Cleaning up KableBleRepositoryImpl...")
        scope.cancel()
        bleManager?.close()
        bleManager = null
        discoveredDevices.clear()
    }
}
