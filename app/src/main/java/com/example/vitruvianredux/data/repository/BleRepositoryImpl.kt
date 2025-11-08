package com.example.vitruvianredux.data.repository

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
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
import kotlinx.coroutines.delay
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
    suspend fun disconnect()
    suspend fun sendInitSequence(): Result<Unit>
    suspend fun startWorkout(params: WorkoutParameters): Result<Unit>
    suspend fun stopWorkout(): Result<Unit>
    suspend fun setColorScheme(schemeIndex: Int): Result<Unit>
    suspend fun testOfficialAppProtocol(): Result<Unit>
    fun enableHandleDetection() // Start monitor polling for auto-start detection
    fun enableJustLiftWaitingMode() // Enable position-based handle detection for next exercise
}

@Singleton
class BleRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val connectionLogger: com.example.vitruvianredux.data.logger.ConnectionLogger
) : BleRepository {

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter
    private var bleManager: VitruvianBleManager? = null

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

            // Create BLE manager
            bleManager = VitruvianBleManager(context, connectionLogger).apply {
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
                            }
                            is com.example.vitruvianredux.data.ble.ConnectionStatus.Disconnected -> {
                                Timber.d("Device disconnected")
                                connectionLogger.logDisconnected(deviceName, deviceAddress)
                                _connectionState.value = ConnectionState.Disconnected
                            }
                            is com.example.vitruvianredux.data.ble.ConnectionStatus.Error -> {
                                Timber.e("Connection error: ${status.message}")
                                connectionLogger.logConnectionFailed(deviceName, deviceAddress, status.message)
                                _connectionState.value = ConnectionState.Error(status.message)
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
            }

            // Connect to device
            Timber.d("Initiating connection to device...")
            bleManager?.connect(device)
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
                            Timber.e("INIT sequence failed after connection: ${initResult.exceptionOrNull()?.message}")
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

    override suspend fun disconnect() = withContext(Dispatchers.Main) {
        try {
            Timber.d("Disconnecting from device...")
            bleManager?.stopPolling()
            bleManager?.cleanup()  // Clean up resources and cancel polling jobs
            bleManager?.disconnect()?.enqueue()
            bleManager = null
            _connectionState.value = ConnectionState.Disconnected
            Timber.d("Disconnected from device")
        } catch (e: Exception) {
            Timber.e(e, "Error disconnecting")
        }
    }

    override suspend fun sendInitSequence(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val connectedState = _connectionState.value
            val deviceName = if (connectedState is ConnectionState.Connected) connectedState.deviceName else null
            val deviceAddress = if (connectedState is ConnectionState.Connected) connectedState.deviceAddress else null

            Timber.d("=== Starting INIT sequence ===")
            connectionLogger.logInitStarted(deviceName ?: "Unknown", deviceAddress ?: "")

            // Send initial command
            Timber.d("Sending init command (4 bytes)...")
            val initCommand = ProtocolBuilder.buildInitCommand()
            connectionLogger.logCommandSent("INIT_COMMAND", deviceName, deviceAddress, initCommand)
            bleManager?.sendCommand(initCommand)?.getOrThrow()
            delay(200) // Longer delay - web app waits 50ms minimum, we'll be safer

            Timber.d("Init command sent, waiting before preset...")

            // Send init preset
            Timber.d("Sending init preset (34 bytes)...")
            val initPreset = ProtocolBuilder.buildInitPreset()
            connectionLogger.logCommandSent("INIT_PRESET", deviceName, deviceAddress, initPreset)
            bleManager?.sendCommand(initPreset)?.getOrThrow()
            delay(200)

            Timber.d("=== INIT sequence completed successfully ===")
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
            val connectedState = _connectionState.value
            val deviceName = if (connectedState is ConnectionState.Connected) connectedState.deviceName else null
            val deviceAddress = if (connectedState is ConnectionState.Connected) connectedState.deviceAddress else null

            // MATCH WEB APP EXACTLY:
            // - Program modes (Old School, Pump, TUT): Send ONLY program params (96 bytes)
            // - Echo mode: Send ONLY echo control (40 bytes)
            Timber.d("Starting workout with type: ${params.workoutType.displayName}")

            when (params.workoutType) {
                is com.example.vitruvianredux.domain.model.WorkoutType.Echo -> {
                    // Echo mode: Send ONLY echo control frame (web app: device.js line 328)
                    Timber.d("Echo mode: sending ONLY echo control frame (40 bytes)")
                    val echoFrame = ProtocolBuilder.buildEchoControl(
                        level = params.workoutType.level,
                        warmupReps = params.warmupReps,
                        targetReps = params.reps,
                        isJustLift = params.isJustLift,
                        eccentricPct = params.workoutType.eccentricLoad.percentage
                    )
                    connectionLogger.logCommandSent(
                        "START_WORKOUT_ECHO",
                        deviceName,
                        deviceAddress,
                        echoFrame,
                        "Mode=${params.workoutType.displayName}, Level=${params.workoutType.level}, Eccentric=${params.workoutType.eccentricLoad.percentage}%, Reps=${params.reps}, JustLift=${params.isJustLift}"
                    )
                    bleManager?.sendCommand(echoFrame)?.getOrThrow()
                    delay(100)
                }
                is com.example.vitruvianredux.domain.model.WorkoutType.Program -> {
                    // Program mode: Send ONLY program params (web app: device.js line 283)
                    Timber.d("Program mode: sending ONLY program params (96 bytes)")
                    val programFrame = ProtocolBuilder.buildProgramParams(params)
                    connectionLogger.logCommandSent(
                        "START_WORKOUT_PROGRAM",
                        deviceName,
                        deviceAddress,
                        programFrame,
                        "Mode=${params.workoutType.displayName}, Weight=${params.weightPerCableKg}kg, Reps=${params.reps}, JustLift=${params.isJustLift}, Progression=${params.progressionRegressionKg}kg"
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

            Timber.d("STOP_DEBUG: ============================================")
            Timber.d("STOP_DEBUG: stopWorkout() called at timestamp: $timestamp")
            Timber.d("STOP_DEBUG: ============================================")

            // CRITICAL SAFETY: Stop all polling BEFORE sending INIT command
            // This ensures the machine fully exits workout mode
            val beforePollingStop = System.currentTimeMillis()
            Timber.d("STOP_DEBUG: [$beforePollingStop] BEFORE stopping polling jobs")
            Timber.d("STOP_DEBUG: Cancelling polling jobs...")
            connectionLogger.logPollingStopped("ALL", deviceName, deviceAddress)
            bleManager?.stopPolling()
            val afterPollingStop = System.currentTimeMillis()
            Timber.d("STOP_DEBUG: [$afterPollingStop] AFTER stopping polling jobs (took ${afterPollingStop - beforePollingStop}ms)")

            // Send INIT command to stop workout and release resistance
            // NOTE: Web app uses buildInitCommand() to stop, not a separate stop command
            // The device interprets 0x0A contextually based on current state
            val initCommand = ProtocolBuilder.buildInitCommand()
            val beforeInitSend = System.currentTimeMillis()
            Timber.d("STOP_DEBUG: [$beforeInitSend] BEFORE sending INIT command")
            Timber.d("STOP_DEBUG: INIT command bytes: ${initCommand.joinToString(" ") { "0x%02X".format(it) }}")
            Timber.d("STOP_DEBUG: INIT command size: ${initCommand.size} bytes")
            Timber.d("STOP_DEBUG: Sending INIT command to release tension...")
            connectionLogger.logCommandSent("STOP_WORKOUT", deviceName, deviceAddress, initCommand)
            bleManager?.sendCommand(initCommand)?.getOrThrow()
            val afterInitSend = System.currentTimeMillis()
            Timber.d("STOP_DEBUG: [$afterInitSend] AFTER sending INIT command (took ${afterInitSend - beforeInitSend}ms)")
            Timber.d("STOP_DEBUG: INIT command sent successfully")

            val finalTimestamp = System.currentTimeMillis()
            Timber.d("STOP_DEBUG: [$finalTimestamp] Workout stopped - Total stopWorkout() time: ${finalTimestamp - timestamp}ms")
            Timber.d("STOP_DEBUG: ============================================")
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
            Timber.d("Repository: Starting official app protocol test")
            bleManager?.testOfficialAppProtocol()?.getOrThrow()
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Failed to test official app protocol")
            Result.failure(e)
        }
    }

    override fun enableHandleDetection() {
        Timber.d("Enabling handle detection - starting monitor polling for auto-start")
        bleManager?.startMonitorPolling()
    }

    override fun enableJustLiftWaitingMode() {
        Timber.d("Enabling Just Lift waiting mode - position-based handle detection")
        bleManager?.enableJustLiftWaitingMode()
    }
}

