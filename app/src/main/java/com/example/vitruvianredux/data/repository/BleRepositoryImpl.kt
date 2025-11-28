package com.example.vitruvianredux.data.repository

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import com.example.vitruvianredux.data.ble.VitruvianBleManager
import com.example.vitruvianredux.domain.model.ConnectionState
import com.example.vitruvianredux.domain.model.WorkoutMetric
import com.example.vitruvianredux.domain.model.WorkoutParameters
import com.example.vitruvianredux.util.BleConstants
import com.example.vitruvianredux.util.ProtocolBuilder
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * BLE Repository - Manages Bluetooth communication with Vitruvian device
 */
interface BleRepository {
    val connectionState: StateFlow<ConnectionState>
    val monitorData: Flow<WorkoutMetric>
    val repEvents: Flow<com.example.vitruvianredux.data.ble.RepNotification>
    val scannedDevices: Flow<ScanResult>
    val handleState: StateFlow<com.example.vitruvianredux.data.ble.HandleState>

    suspend fun startScanning(): Result<Unit>
    suspend fun stopScanning()
    suspend fun connectToDevice(deviceAddress: String): Result<Unit>
    suspend fun cancelConnection() // Cancel an in-progress connection attempt
    suspend fun disconnect()
    suspend fun sendInitSequence(): Result<Unit>
    suspend fun startWorkout(params: WorkoutParameters): Result<Unit>
    suspend fun stopWorkout(): Result<Unit>

    /**
     * Send stop command to machine WITHOUT stopping polling.
     * Use this for Just Lift mode where we need continuous polling for auto-start detection.
     * The machine needs active polling to process commands quickly.
     */
    suspend fun sendStopCommand(): Result<Unit>

    suspend fun setColorScheme(schemeIndex: Int): Result<Unit>
    suspend fun testOfficialAppProtocol(): Result<Unit>
    fun enableHandleDetection() // Start monitor polling for auto-start detection
    fun enableJustLiftWaitingMode() // Enable position-based handle detection for next exercise

    /**
     * Restart monitor polling to clear the machine's danger zone alarm state.
     *
     * This sends monitor commands to the Vitruvian device, which causes it to exit
     * danger zone alarm mode (red flashing lights). Unlike enableHandleDetection(),
     * this method is NOT intended to enable auto-start behavior.
     *
     * Use cases:
     * - After AMRAP set completion to clear danger zone lights
     * - After any workout mode that needs to clear machine alarm state without enabling auto-start
     *
     * Note: This calls the same underlying startMonitorPolling() as enableHandleDetection(),
     * but the semantic separation makes the intent clear at call sites.
     */
    fun restartMonitorPolling()
}

@Singleton
class BleRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val connectionLogger: com.example.vitruvianredux.data.logger.ConnectionLogger
) : BleRepository {

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter
    private var bleManager: VitruvianBleManager? = null
    private var connectingBleManager: VitruvianBleManager? = null  // Track manager being created during connection

    private val scope = CoroutineScope(Dispatchers.IO)

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    override val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _monitorData = MutableSharedFlow<WorkoutMetric>(replay = 0)
    override val monitorData: Flow<WorkoutMetric> = _monitorData.asSharedFlow()

    // CRITICAL: Use MutableSharedFlow with buffer so ViewModel can collect before connection
    private val _repEvents = MutableSharedFlow<com.example.vitruvianredux.data.ble.RepNotification>(
        replay = 0,
        extraBufferCapacity = 64,  // Buffer for rep notifications
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    override val repEvents: Flow<com.example.vitruvianredux.data.ble.RepNotification> = _repEvents.asSharedFlow()

    private val _scannedDevices = MutableSharedFlow<ScanResult>(replay = 10)
    override val scannedDevices: Flow<ScanResult> = _scannedDevices.asSharedFlow()

    private val _handleState = MutableStateFlow(com.example.vitruvianredux.data.ble.HandleState.Released)
    override val handleState: StateFlow<com.example.vitruvianredux.data.ble.HandleState> = _handleState.asStateFlow()

    private var isScanning = false
    
    // Cache the last workout parameters to allow re-arming the machine after stop
    // This is critical for "Just Lift" seamless recovery
    private var cachedWorkoutParams: WorkoutParameters? = null

    @SuppressLint("MissingPermission")
    override suspend fun startScanning(): Result<Unit> = withContext(Dispatchers.Main) {
        try {
            Timber.d("startScanning() called")
            connectionLogger.logScanStarted()

            if (bluetoothAdapter == null) {
                Timber.e("Bluetooth adapter is null")
                connectionLogger.logError("startScanning", null, null, "Bluetooth adapter is null")
                return@withContext Result.failure(Exception("Bluetooth not available"))
            }

            if (!bluetoothAdapter.isEnabled) {
                Timber.e("Bluetooth is disabled")
                connectionLogger.logError("startScanning", null, null, "Bluetooth is disabled")
                return@withContext Result.failure(Exception("Bluetooth is disabled"))
            }

            if (isScanning) {
                Timber.d("Already scanning, returning")
                return@withContext Result.success(Unit)
            }

            _connectionState.value = ConnectionState.Scanning
            Timber.d("Set connection state to Scanning")

            val scanner = bluetoothAdapter.bluetoothLeScanner
            if (scanner == null) {
                Timber.e("BLE scanner is null")
                return@withContext Result.failure(Exception("BLE scanner not available"))
            }

            // Scan without filters to find all BLE devices (more permissive)
            val scanSettings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
                .build()

            Timber.d("Starting BLE scan with no filters (will show all devices)")
            scanner.startScan(null, scanSettings, scanCallback)
            isScanning = true

            Timber.d("BLE scan started successfully - looking for devices starting with '${BleConstants.DEVICE_NAME_PREFIX}'")

            // Auto-stop scanning after timeout
            scope.launch {
                delay(BleConstants.SCAN_TIMEOUT_MS)
                if (isScanning) {
                    Timber.d("Scan timeout reached (${BleConstants.SCAN_TIMEOUT_MS}ms), stopping scan")
                    stopScanning()
                }
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Failed to start scanning")
            _connectionState.value = ConnectionState.Error("Failed to start scanning: ${e.message}")
            Result.failure(e)
        }
    }

    @SuppressLint("MissingPermission")
    override suspend fun stopScanning() = withContext(Dispatchers.Main) {
        try {
            if (!isScanning) return@withContext

            bluetoothAdapter?.bluetoothLeScanner?.stopScan(scanCallback)
            isScanning = false

            if (_connectionState.value == ConnectionState.Scanning) {
                _connectionState.value = ConnectionState.Disconnected
            }

            Timber.d("Stopped BLE scanning")
            connectionLogger.logScanStopped()
        } catch (e: Exception) {
            Timber.e(e, "Error stopping scan")
            connectionLogger.logError("stopScanning", null, null, e.message ?: "Unknown error")
        }
    }

    private val scanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val deviceName = result.device.name ?: "Unknown"
            val deviceAddress = result.device.address
            Timber.d("BLE device found: name='$deviceName', address=$deviceAddress, rssi=${result.rssi}")

            // Only emit devices that match our filter
            if (deviceName.startsWith(BleConstants.DEVICE_NAME_PREFIX)) {
                connectionLogger.logDeviceFound(deviceName, deviceAddress)
                Timber.d("Device matches filter, adding to list")
                val emitted = _scannedDevices.tryEmit(result)
                Timber.d("tryEmit result: $emitted (subscribers: ${_scannedDevices.subscriptionCount.value})")
            }
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>) {
            Timber.d("Batch scan results: ${results.size} devices")
            results.forEach { result ->
                onScanResult(ScanSettings.CALLBACK_TYPE_ALL_MATCHES, result)
            }
        }

        override fun onScanFailed(errorCode: Int) {
            val errorMsg = when (errorCode) {
                SCAN_FAILED_ALREADY_STARTED -> "Scan already started"
                SCAN_FAILED_APPLICATION_REGISTRATION_FAILED -> "App registration failed"
                SCAN_FAILED_INTERNAL_ERROR -> "Internal error"
                SCAN_FAILED_FEATURE_UNSUPPORTED -> "Feature unsupported"
                else -> "Unknown error $errorCode"
            }
            Timber.e("BLE scan failed: $errorMsg")
            _connectionState.value = ConnectionState.Error("Scan failed: $errorMsg")
            isScanning = false
        }
    }

    @SuppressLint("MissingPermission")
    override suspend fun connectToDevice(deviceAddress: String): Result<Unit> = withContext(Dispatchers.Main) {
        try {
            Timber.d("connectToDevice() called for address: $deviceAddress")
            stopScanning()

            val device = bluetoothAdapter?.getRemoteDevice(deviceAddress)
            if (device == null) {
                Timber.e("Failed to get remote device for address: $deviceAddress")
                connectionLogger.logConnectionFailed("Unknown", deviceAddress, "Device not found")
                return@withContext Result.failure(Exception("Device not found"))
            }

            val deviceName = device.name ?: "Vitruvian"
            Timber.d("Got remote device: $deviceName ($deviceAddress)")
            connectionLogger.logConnectionStarted(deviceName, deviceAddress)
            _connectionState.value = ConnectionState.Connecting
            Timber.d("Connection state set to Connecting")

            // CRITICAL FIX: Close existing BLE manager before creating a new one
            // This prevents dangling GATT connections that cause onServicesInvalidated loops
            // on Pixel 7/Android 16 devices
            bleManager?.let { oldManager ->
                Timber.w("⚠️ Closing existing BLE manager before reconnection")
                try {
                    oldManager.stopPolling()
                    oldManager.cleanup()
                    oldManager.close()
                    Timber.d("Old BLE manager closed successfully")
                } catch (e: Exception) {
                    Timber.w(e, "Error closing old BLE manager (continuing anyway)")
                }
                bleManager = null
            }
            connectingBleManager?.let { oldConnecting ->
                if (oldConnecting !== bleManager) {
                    Timber.w("⚠️ Closing connecting BLE manager")
                    try {
                        oldConnecting.close()
                    } catch (e: Exception) {
                        Timber.w(e, "Error closing connecting BLE manager")
                    }
                    connectingBleManager = null
                }
            }

            // Small delay to let BLE stack settle after closing old connection
            delay(100)

            // Create BLE manager and track it for potential cancellation
            val newBleManager = VitruvianBleManager(context, connectionLogger).apply {
                setDeviceInfo(device.name, device.address)
                Timber.d("Created VitruvianBleManager")

                // Set up connection observer
                scope.launch {
                    connectionState.collect { status ->
                        Timber.d("BLE Manager connection status changed: $status")
                        when (status) {
                            is com.example.vitruvianredux.data.ble.ConnectionStatus.Ready -> {
                                Timber.d("Device ready! Setting state to Connected")
                                connectionLogger.logConnectionSuccess(deviceName, deviceAddress)
                                _connectionState.value = ConnectionState.Connected(
                                    deviceName = device.name ?: "Vitruvian",
                                    deviceAddress = device.address
                                )
                                // Connection succeeded, clear the connecting reference
                                connectingBleManager = null
                            }
                            is com.example.vitruvianredux.data.ble.ConnectionStatus.Disconnected -> {
                                Timber.d("Device disconnected")
                                connectionLogger.logDisconnected(deviceName, deviceAddress)
                                _connectionState.value = ConnectionState.Disconnected
                                // Connection failed, clear the connecting reference
                                connectingBleManager = null
                            }
                            is com.example.vitruvianredux.data.ble.ConnectionStatus.Error -> {
                                Timber.e("Connection error: ${status.message}")
                                connectionLogger.logConnectionFailed(deviceName, deviceAddress, status.message)
                                _connectionState.value = ConnectionState.Error(status.message)
                                // Connection failed, clear the connecting reference
                                connectingBleManager = null
                            }
                        }
                    }
                }

                // Collect monitor data and forward to repository flow
                scope.launch {
                    Timber.d("Starting monitor data collection from BleManager")
                    monitorData.collect { metric ->
                        Timber.d("BleRepository forwarding monitor metric: pos=(${metric.positionA},${metric.positionB})")
                        _monitorData.emit(metric)
                    }
                }

                // Collect rep events and forward to repository flow
                scope.launch {
                    Timber.d("?? Starting rep event collection from BleManager")
                    repEvents.collect { repNotification ->
                        Timber.d("?? BleRepository forwarding rep event: top=${repNotification.topCounter}, complete=${repNotification.completeCounter}")
                        _repEvents.emit(repNotification)
                    }
                }

                // Collect handle state and forward to repository flow
                scope.launch {
                    handleState.collect { state ->
                        _handleState.value = state
                    }
                }

                // NOTE: DELOAD_OCCURRED is a SAFETY HOLDING STATE, not an error to clear
                // - Deload means the machine reduced load to protect the user
                // - Exercise should RESUME when user picks handles back up into ROM
                // - Only auto-stop timer expiry OR manual Stop should end the exercise
                // - DO NOT send StopPacket automatically on deload - this was the root cause
                //   of Just Lift mode failing (premature exercise termination)
                scope.launch {
                    deloadOccurredEvents.collect {
                        Timber.d("DELOAD_EVENT: Deload occurred (safety holding state) - NOT sending stop, exercise can resume")
                    }
                }

                // AUTO-RECONNECT: Handle Android 16 Pixel BLE stack bug (onServicesInvalidated)
                // When GATT services are invalidated, attempt automatic reconnection
                scope.launch {
                    reconnectionRequested.collect { request ->
                        Timber.i("🔄 RECONNECT_REQUEST: ${request.reason} - device=${request.deviceName} (${request.deviceAddress})")
                        connectionLogger.log(
                            eventType = "RECONNECT_REQUESTED",
                            level = com.example.vitruvianredux.data.logger.ConnectionLogger.Level.WARNING,
                            deviceName = request.deviceName,
                            deviceAddress = request.deviceAddress,
                            message = "Auto-reconnect requested: ${request.reason}"
                        )

                        // Wait before reconnecting (allow BLE stack to settle)
                        delay(1500L)

                        // Attempt reconnection
                        Timber.i("🔄 Attempting auto-reconnect to ${request.deviceAddress}...")
                        try {
                            val result = connectToDevice(request.deviceAddress)
                            if (result.isSuccess) {
                                Timber.i("✅ Auto-reconnect successful!")
                                connectionLogger.log(
                                    eventType = "RECONNECT_SUCCESS",
                                    level = com.example.vitruvianredux.data.logger.ConnectionLogger.Level.INFO,
                                    deviceName = request.deviceName,
                                    deviceAddress = request.deviceAddress,
                                    message = "Auto-reconnect succeeded"
                                )
                            } else {
                                Timber.w("⚠️ Auto-reconnect failed: ${result.exceptionOrNull()?.message}")
                                connectionLogger.log(
                                    eventType = "RECONNECT_FAILED",
                                    level = com.example.vitruvianredux.data.logger.ConnectionLogger.Level.ERROR,
                                    deviceName = request.deviceName,
                                    deviceAddress = request.deviceAddress,
                                    message = "Auto-reconnect failed: ${result.exceptionOrNull()?.message}"
                                )
                            }
                        } catch (e: Exception) {
                            Timber.e(e, "❌ Auto-reconnect exception")
                            connectionLogger.log(
                                eventType = "RECONNECT_ERROR",
                                level = com.example.vitruvianredux.data.logger.ConnectionLogger.Level.ERROR,
                                deviceName = request.deviceName,
                                deviceAddress = request.deviceAddress,
                                message = "Auto-reconnect error: ${e.message}"
                            )
                        }
                    }
                }
            }

            // Store references to the new BLE manager
            bleManager = newBleManager
            connectingBleManager = newBleManager

            // Connect to device
            // NOTE: Nordic BLE Library automatically uses TRANSPORT_LE on Android 6.0+ (API 23+)
            // This matches the trainer which uses:
            // connectGatt(context, false, callback, TRANSPORT_LE, PHY_LE_1M, handler)
            Timber.d("Initiating connection to device...")
            newBleManager.connect(device)
                ?.timeout(BleConstants.CONNECTION_TIMEOUT_MS)
                ?.retry(3, 100)
                ?.useAutoConnect(false)
                ?.done {
                    // Device connected successfully
                    // Send INIT sequence after connection (LEDs acknowledge connection)
                    Timber.d("Device connected! Waiting 2 seconds before sending INIT...")
                    scope.launch {
                        delay(2000) // Wait 2 seconds (matching web app behavior)
                        Timber.d("Now sending INIT sequence...")
                        val initResult = sendInitSequence()
                        if (initResult.isSuccess) {
                            Timber.d("Device fully initialized and ready!")
                        } else {
                            // FIX FOR ISSUE #124: If initialization fails, disconnect to prevent
                            // workout from starting on an uninitialized device which causes
                            // "onServicesInvalidated" disconnect ~5 seconds after workout start
                            Timber.e("INIT sequence failed after connection: ${initResult.exceptionOrNull()?.message}")
                            Timber.e("Disconnecting device due to failed initialization...")
                            _connectionState.value = ConnectionState.Error(
                                "Device initialization failed: ${initResult.exceptionOrNull()?.message}",
                                initResult.exceptionOrNull()
                            )
                            // Disconnect the device to force user to reconnect
                            newBleManager.disconnect().enqueue()
                        }
                    }
                }
                ?.enqueue()

            Timber.d("Connecting to device: ${device.name} (${device.address})")
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Failed to connect to device")
            _connectionState.value = ConnectionState.Error("Connection failed: ${e.message}")
            Result.failure(e)
        }
    }

    override suspend fun cancelConnection() = withContext(Dispatchers.Main) {
        try {
            Timber.d("Cancelling in-progress connection...")

            // Cancel the connecting BLE manager if one exists
            val managerToCancel = connectingBleManager
            if (managerToCancel != null) {
                Timber.d("Cleaning up connecting BLE manager...")
                managerToCancel.stopPolling()
                managerToCancel.cleanup()
                managerToCancel.disconnect()?.enqueue()

                // Only clear bleManager if it's the same instance we're cancelling
                // (i.e., connection hasn't succeeded yet)
                if (bleManager === managerToCancel) {
                    bleManager = null
                }
                connectingBleManager = null
            } else {
                Timber.d("No connecting BLE manager to cancel")
            }

            // Reset connection state only if we're still connecting
            if (_connectionState.value is ConnectionState.Connecting ||
                _connectionState.value is ConnectionState.Scanning) {
                _connectionState.value = ConnectionState.Disconnected
            }

            Timber.d("Connection cancelled successfully")
        } catch (e: Exception) {
            Timber.e(e, "Error cancelling connection")
        }
    }

    override suspend fun disconnect() = withContext(Dispatchers.Main) {
        try {
            Timber.d("Disconnecting from device...")
            val manager = bleManager
            if (manager != null) {
                // Stop polling first to prevent callbacks during disconnect
                manager.stopPolling()
                manager.cleanup()  // Clean up coroutine jobs

                try {
                    // Use withTimeout to prevent hanging on disconnect
                    withTimeout(3000L) {
                        manager.disconnect().await()
                    }
                    Timber.d("BLE disconnect completed via await()")
                } catch (e: TimeoutCancellationException) {
                    Timber.w("Disconnect timed out, forcing close")
                } catch (e: Exception) {
                    Timber.w(e, "Disconnect await failed, forcing close")
                }

                // Always call close() to ensure GATT resources are released
                // This is the nuclear option that truly closes the connection
                try {
                    manager.close()
                    Timber.d("BLE manager closed")
                } catch (e: Exception) {
                    Timber.w(e, "Error closing BLE manager")
                }
            }

            // Also clean up any connecting manager
            connectingBleManager?.let { connecting ->
                try {
                    connecting.close()
                } catch (e: Exception) {
                    Timber.w(e, "Error closing connecting BLE manager")
                }
            }

            bleManager = null
            connectingBleManager = null
            _connectionState.value = ConnectionState.Disconnected
            Timber.d("Disconnected from device - state updated, resources released")
        } catch (e: Exception) {
            Timber.e(e, "Error during disconnect")
            // Force cleanup even on error
            try {
                bleManager?.close()
                connectingBleManager?.close()
            } catch (ignored: Exception) {}
            bleManager = null
            connectingBleManager = null
            _connectionState.value = ConnectionState.Disconnected
        }
    }

    override suspend fun sendInitSequence(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val connectedState = _connectionState.value
            val deviceName = if (connectedState is ConnectionState.Connected) connectedState.deviceName else null
            val deviceAddress = if (connectedState is ConnectionState.Connected) connectedState.deviceAddress else null

            Timber.d("=== Starting INIT sequence (matching trainer) ===")
            connectionLogger.logInitStarted(deviceName ?: "Unknown", deviceAddress ?: "")

            val manager = bleManager
            if (manager == null) {
                Timber.e("BLE manager is null, cannot send INIT sequence")
                return@withContext Result.failure(Exception("BLE manager not available"))
            }

            // DEPRECATED: The trainer does not use the 0x0A handshake.
            // Per patch analysis, sendInitSequence should be a no-op.
            // The 0x0A/0x11 init sequence is legacy web app protocol that causes issues.
            Timber.w("sendInitSequence called but is deprecated - trainer doesn't use 0x0A handshake")
            Timber.d("=== INIT sequence skipped (deprecated) ===")
            connectionLogger.logInitSuccess(deviceName ?: "Unknown", deviceAddress ?: "")
            Result.success(Unit)
        } catch (e: Exception) {
            val connectedState = _connectionState.value
            val deviceName = if (connectedState is ConnectionState.Connected) connectedState.deviceName else null
            val deviceAddress = if (connectedState is ConnectionState.Connected) connectedState.deviceAddress else null
            Timber.e(e, "Failed to send init sequence")
            connectionLogger.logInitFailed(deviceName ?: "Unknown", deviceAddress ?: "", e.message ?: "Unknown error")
            Result.failure(e)
        }
    }

    override suspend fun startWorkout(params: WorkoutParameters): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // Cache parameters for re-arming after stop (Just Lift seamless recovery)
            cachedWorkoutParams = params
            
            val connectedState = _connectionState.value
            val deviceName = if (connectedState is ConnectionState.Connected) connectedState.deviceName else null
            val deviceAddress = if (connectedState is ConnectionState.Connected) connectedState.deviceAddress else null

            // WEB APP STARTUP SEQUENCE (verified from [removed] & [removed]):
            // 1. Send INIT command (0x0A) - same command used for stop, resets machine state
            // 2. Wait 50ms
            // 3. (Optional: Send INIT preset - we skip this as program params contain full config)
            // Previous 0x50 approach did NOT match web app behavior and caused delayed starts

            Timber.d("WORKOUT_START: Sending INIT command (0x0A) to reset machine state...")
            val initCommand = ProtocolBuilder.buildInitCommand()
            connectionLogger.logCommandSent("INIT_RESET", deviceName, deviceAddress, initCommand, "INIT 0x0A to reset before workout")
            bleManager?.sendCommand(initCommand)?.getOrThrow()
            delay(50) // Web app uses 50ms between init and program commands

            // MATCH WEB APP EXACTLY:
            // - Program modes (Old School, Pump, TUT): Send ONLY program params (96 bytes)
            // - Echo mode: Send ONLY echo control (40 bytes)
            Timber.d("Starting workout with type: ${params.workoutType.displayName}")

            when (params.workoutType) {
                is com.example.vitruvianredux.domain.model.WorkoutType.Echo -> {
                    // Echo mode: Send ONLY echo control frame (web app: device line 328)
                    Timber.d("Echo mode: sending ONLY echo control frame (40 bytes)")
                    val echoFrame = ProtocolBuilder.buildEchoControl(
                        level = params.workoutType.level,
                        warmupReps = params.warmupReps,
                        targetReps = params.reps,
                        isJustLift = params.isJustLift,
                        isAMRAP = params.isAMRAP,
                        eccentricPct = params.workoutType.eccentricLoad.percentage
                    )
                    connectionLogger.logCommandSent(
                        "START_WORKOUT_ECHO",
                        deviceName,
                        deviceAddress,
                        echoFrame,
                        "Mode=${params.workoutType.displayName}, Level=${params.workoutType.level}, Eccentric=${params.workoutType.eccentricLoad.percentage}%, Reps=${params.reps}, JustLift=${params.isJustLift}, AMRAP=${params.isAMRAP}"
                    )
                    bleManager?.sendCommand(echoFrame)?.getOrThrow()
                    delay(100)
                }
                is com.example.vitruvianredux.domain.model.WorkoutType.Program -> {
                    // Program mode: Send ONLY REGULAR packet (28 bytes) - Command 0x4F
                    // Matches trainer 'RegularPacket'
                    Timber.d("Program mode: sending REGULAR packet (28 bytes)")
                    val programFrame = ProtocolBuilder.buildProgramParams(params)

                    val additionalInfo = buildString {
                        append("Mode=${params.workoutType.displayName}, ")
                        append("Weight=${params.weightPerCableKg}kg, ")
                        append("Reps=${params.reps}, ")
                        append("JustLift=${params.isJustLift}, ")
                        append("Progression=${params.progressionRegressionKg}kg")
                        if (params.workoutType.mode == com.example.vitruvianredux.domain.model.ProgramMode.EccentricOnly) {
                            append("\n⚠️ ECCENTRIC-ONLY MODE - Please verify resistance applies ONLY during lowering phase")
                        }
                    }

                    connectionLogger.logCommandSent(
                        "START_WORKOUT_PROGRAM",
                        deviceName,
                        deviceAddress,
                        programFrame,
                        additionalInfo
                    )
                    bleManager?.sendCommand(programFrame)?.getOrThrow()
                    delay(100)
                }
            }

            Timber.d("Workout command sent successfully!")
            connectionLogger.logCommandSuccess("START_WORKOUT", deviceName, deviceAddress)

            // Start monitor polling for workout data (100ms interval)
            // Property polling already running as keep-alive from connection time
            Timber.d("Starting monitor polling for workout...")
            connectionLogger.logPollingStarted("MONITOR", deviceName, deviceAddress)
            bleManager?.startMonitorPolling()

            Result.success(Unit)
        } catch (e: Exception) {
            val connectedState = _connectionState.value
            val deviceName = if (connectedState is ConnectionState.Connected) connectedState.deviceName else null
            val deviceAddress = if (connectedState is ConnectionState.Connected) connectedState.deviceAddress else null
            Timber.e(e, "Failed to start workout")
            connectionLogger.logCommandFailed("START_WORKOUT", deviceName, deviceAddress, e.message ?: "Unknown error")
            Result.failure(e)
        }
    }

    override suspend fun stopWorkout(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val timestamp = System.currentTimeMillis()
            val connectedState = _connectionState.value
            val deviceName = if (connectedState is ConnectionState.Connected) connectedState.deviceName else null
            val deviceAddress = if (connectedState is ConnectionState.Connected) connectedState.deviceAddress else null

            Timber.w("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!")
            Timber.w("AUTOSTOP_TRACE: BleRepositoryImpl.stopWorkout() ENTERED")
            Timber.w("AUTOSTOP_TRACE: bleManager is ${if (bleManager != null) "NOT NULL" else "NULL"}")
            Timber.w("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!")

            // WEB APP STOP STRATEGY (verified from [removed] & [removed]):
            // Send INIT/RESET command (0x0A) - this is SAME command used for init AND stop
            // The web app comment: "Stop command is same as init command"
            // Previous 0x50/0x05 approach did NOT match web app behavior

            val resetCommand = ProtocolBuilder.buildResetCommand() // 0x0A 0x00 0x00 0x00
            Timber.d("STOP_DEBUG: Sending RESET/INIT command (0x0A) - matches web app stop...")
            connectionLogger.logCommandSent("STOP_RESET", deviceName, deviceAddress, resetCommand, "0x0A INIT/RESET (web app stop)")
            bleManager?.sendCommand(resetCommand)?.getOrThrow()

            // Brief delay to allow firmware to process (web app uses 50ms between commands)
            delay(50)

            Timber.d("STOP_DEBUG: RESET command sent - machine should return to Ready state")

            // Now stop polling AFTER the commands have been sent
            Timber.d("STOP_DEBUG: Stopping polling jobs...")
            connectionLogger.logPollingStopped("ALL", deviceName, deviceAddress)
            bleManager?.stopPolling()

            val finalTimestamp = System.currentTimeMillis()
            Timber.d("STOP_DEBUG: [$finalTimestamp] Workout stopped - Total stopWorkout() time: ${finalTimestamp - timestamp}ms")
            connectionLogger.logCommandSuccess("STOP_WORKOUT", deviceName, deviceAddress)
            Result.success(Unit)
        } catch (e: Exception) {
            val connectedState = _connectionState.value
            val deviceName = if (connectedState is ConnectionState.Connected) connectedState.deviceName else null
            val deviceAddress = if (connectedState is ConnectionState.Connected) connectedState.deviceAddress else null
            Timber.e(e, "STOP_DEBUG: FAILED to stop workout")
            connectionLogger.logCommandFailed("STOP_WORKOUT", deviceName, deviceAddress, e.message ?: "Unknown error")
            Result.failure(e)
        }
    }

    /**
     * Send stop command to machine WITHOUT stopping polling.
     * Use this for Just Lift mode where we need continuous polling for auto-start detection.
     */
    override suspend fun sendStopCommand(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val connectedState = _connectionState.value
            val deviceName = if (connectedState is ConnectionState.Connected) connectedState.deviceName else null
            val deviceAddress = if (connectedState is ConnectionState.Connected) connectedState.deviceAddress else null

            Timber.w("AUTOSTOP_TRACE: sendStopCommand() - sending StopPacket WITHOUT stopping polling")

            val stopPacket = ProtocolBuilder.buildOfficialStopPacket()
            Timber.w("AUTOSTOP_TRACE: StopPacket bytes: ${stopPacket.joinToString(" ") { "0x%02X".format(it) }}")
            connectionLogger.logCommandSent("SEND_STOP_CMD", deviceName, deviceAddress, stopPacket, "StopPacket 0x50 (polling continues)")

            val sendResult = bleManager?.sendCommand(stopPacket)
            Timber.w("AUTOSTOP_TRACE: sendCommand returned: $sendResult")
            sendResult?.getOrThrow()

            Timber.w("AUTOSTOP_TRACE: StopPacket sent - polling still active for quick machine response")
            connectionLogger.logCommandSuccess("SEND_STOP_CMD", deviceName, deviceAddress)
            Result.success(Unit)
        } catch (e: Exception) {
            val connectedState = _connectionState.value
            val deviceName = if (connectedState is ConnectionState.Connected) connectedState.deviceName else null
            val deviceAddress = if (connectedState is ConnectionState.Connected) connectedState.deviceAddress else null
            Timber.e(e, "AUTOSTOP_TRACE: FAILED to send stop command")
            connectionLogger.logCommandFailed("SEND_STOP_CMD", deviceName, deviceAddress, e.message ?: "Unknown error")
            Result.failure(e)
        }
    }

    override suspend fun setColorScheme(schemeIndex: Int): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val connectedState = _connectionState.value
            val deviceName = if (connectedState is ConnectionState.Connected) connectedState.deviceName else null
            val deviceAddress = if (connectedState is ConnectionState.Connected) connectedState.deviceAddress else null

            val schemes = com.example.vitruvianredux.util.ColorSchemes.ALL
            if (schemeIndex !in schemes.indices) {
                connectionLogger.logCommandFailed("SET_LED_COLOR", deviceName, deviceAddress, "Invalid color scheme index: $schemeIndex")
                return@withContext Result.failure(Exception("Invalid color scheme index"))
            }

            val scheme = schemes[schemeIndex]
            val colorFrame = ProtocolBuilder.buildColorScheme(scheme.brightness, scheme.colors)
            connectionLogger.logCommandSent(
                "SET_LED_COLOR",
                deviceName,
                deviceAddress,
                colorFrame,
                "Scheme=${scheme.name}, Brightness=${scheme.brightness}, Colors=${scheme.colors.size}"
            )
            bleManager?.sendCommand(colorFrame)?.getOrThrow()

            Timber.d("Color scheme set to: ${scheme.name}")
            connectionLogger.logCommandSuccess("SET_LED_COLOR", deviceName, deviceAddress)
            Result.success(Unit)
        } catch (e: Exception) {
            val connectedState = _connectionState.value
            val deviceName = if (connectedState is ConnectionState.Connected) connectedState.deviceName else null
            val deviceAddress = if (connectedState is ConnectionState.Connected) connectedState.deviceAddress else null
            Timber.e(e, "Failed to set color scheme")
            connectionLogger.logCommandFailed("SET_LED_COLOR", deviceName, deviceAddress, e.message ?: "Unknown error")
            Result.failure(e)
        }
    }

    override suspend fun testOfficialAppProtocol(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            Timber.d("Repository: Starting trainer protocol test")
            bleManager?.testOfficialAppProtocol()?.getOrThrow()
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Failed to test trainer protocol")
            Result.failure(e)
        }
    }

    override fun enableHandleDetection() {
        Timber.d("Enabling handle detection - starting monitor polling for auto-start")
        bleManager?.startMonitorPolling(forAutoStart = true)
    }

    override fun enableJustLiftWaitingMode() {
        Timber.d("Enabling Just Lift waiting mode - position-based handle detection")
        bleManager?.enableJustLiftWaitingMode()
    }

    override fun restartMonitorPolling() {
        if (bleManager == null) {
            Timber.w("Cannot restart monitor polling - BLE manager is null")
        } else {
            Timber.d("Restarting monitor polling - clearing danger zone alarm state on machine")
            bleManager?.startMonitorPolling()
        }
    }
}

