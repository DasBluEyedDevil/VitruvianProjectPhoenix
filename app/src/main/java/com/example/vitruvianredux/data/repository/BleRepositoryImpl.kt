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
    val scannedDevices: Flow<ScanResult>

    suspend fun startScanning(): Result<Unit>
    suspend fun stopScanning()
    suspend fun connectToDevice(deviceAddress: String): Result<Unit>
    suspend fun disconnect()
    suspend fun sendInitSequence(): Result<Unit>
    suspend fun startWorkout(params: WorkoutParameters): Result<Unit>
    suspend fun stopWorkout(): Result<Unit>
    suspend fun setColorScheme(schemeIndex: Int): Result<Unit>
}

@Singleton
class BleRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : BleRepository {

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter
    private var bleManager: VitruvianBleManager? = null

    private val scope = CoroutineScope(Dispatchers.IO)

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    override val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _monitorData = MutableSharedFlow<WorkoutMetric>(replay = 0)
    override val monitorData: Flow<WorkoutMetric> = _monitorData.asSharedFlow()

    private val _scannedDevices = MutableSharedFlow<ScanResult>(replay = 10)
    override val scannedDevices: Flow<ScanResult> = _scannedDevices.asSharedFlow()

    private var isScanning = false

    @SuppressLint("MissingPermission")
    override suspend fun startScanning(): Result<Unit> = withContext(Dispatchers.Main) {
        try {
            Timber.d("startScanning() called")
            
            if (bluetoothAdapter == null) {
                Timber.e("Bluetooth adapter is null")
                return@withContext Result.failure(Exception("Bluetooth not available"))
            }

            if (!bluetoothAdapter.isEnabled) {
                Timber.e("Bluetooth is disabled")
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
        } catch (e: Exception) {
            Timber.e(e, "Error stopping scan")
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
                return@withContext Result.failure(Exception("Device not found"))
            }

            Timber.d("Got remote device: ${device.name} ($deviceAddress)")
            _connectionState.value = ConnectionState.Connecting
            Timber.d("Connection state set to Connecting")

            // Create BLE manager
            bleManager = VitruvianBleManager(context).apply {
                Timber.d("Created VitruvianBleManager")

                // Set up connection observer
                scope.launch {
                    connectionState.collect { status ->
                        Timber.d("BLE Manager connection status changed: $status")
                        when (status) {
                            is com.example.vitruvianredux.data.ble.ConnectionStatus.Ready -> {
                                Timber.d("Device ready! Setting state to Connected")
                                _connectionState.value = ConnectionState.Connected(
                                    deviceName = device.name ?: "Vitruvian",
                                    deviceAddress = device.address
                                )
                            }
                            is com.example.vitruvianredux.data.ble.ConnectionStatus.Disconnected -> {
                                Timber.d("Device disconnected")
                                _connectionState.value = ConnectionState.Disconnected
                            }
                            is com.example.vitruvianredux.data.ble.ConnectionStatus.Error -> {
                                Timber.e("Connection error: ${status.message}")
                                _connectionState.value = ConnectionState.Error(status.message)
                            }
                            else -> {
                                Timber.d("Other connection status: $status")
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
            }

            // Connect to device
            Timber.d("Initiating connection to device...")
            bleManager?.connect(device)
                ?.timeout(BleConstants.CONNECTION_TIMEOUT_MS)
                ?.retry(3, 100)
                ?.useAutoConnect(false)
                ?.done {
                    // Device connected successfully
                    // Give device time to stabilize before sending commands
                    Timber.d("Device connected! Waiting 2 seconds before sending INIT...")
                    scope.launch {
                        delay(2000) // Wait 2 seconds (web app waits for user action)
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
            Timber.d("=== Starting INIT sequence ===")
            
            // Send initial command
            Timber.d("Sending init command (4 bytes)...")
            bleManager?.sendCommand(ProtocolBuilder.buildInitCommand())?.getOrThrow()
            delay(200) // Longer delay - web app waits 50ms minimum, we'll be safer
            
            Timber.d("Init command sent, waiting before preset...")

            // Send init preset
            Timber.d("Sending init preset (34 bytes)...")
            bleManager?.sendCommand(ProtocolBuilder.buildInitPreset())?.getOrThrow()
            delay(200)

            Timber.d("=== INIT sequence completed successfully ===")
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Failed to send init sequence")
            Result.failure(e)
        }
    }

    override suspend fun startWorkout(params: WorkoutParameters): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // Device should already be initialized from connection
            Timber.d("Starting workout - sending program parameters...")
            
            // Build and send program parameters
            val programFrame = ProtocolBuilder.buildProgramParams(params)
            bleManager?.sendCommand(programFrame)?.getOrThrow()
            delay(100)

            // If Echo mode, send echo control frame
            if (params.mode is com.example.vitruvianredux.domain.model.WorkoutMode.Echo) {
                val echoFrame = ProtocolBuilder.buildEchoControl(
                    level = params.mode.level,
                    warmupReps = params.warmupReps,
                    targetReps = params.reps,
                    isJustLift = params.isJustLift
                )
                bleManager?.sendCommand(echoFrame)?.getOrThrow()
                delay(100)
            }

            Timber.d("Workout started with mode: ${params.mode.displayName}")
            
            // NOW start polling (matching web app behavior)
            Timber.d("Starting polling...")
            bleManager?.startPropertyPolling()
            bleManager?.startMonitorPolling()
            
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Failed to start workout")
            Result.failure(e)
        }
    }

    override suspend fun stopWorkout(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // Send stop command (init command stops current workout)
            bleManager?.sendCommand(ProtocolBuilder.buildInitCommand())?.getOrThrow()

            Timber.d("Workout stopped")
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Failed to stop workout")
            Result.failure(e)
        }
    }

    override suspend fun setColorScheme(schemeIndex: Int): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val schemes = com.example.vitruvianredux.util.ColorSchemes.ALL
            if (schemeIndex !in schemes.indices) {
                return@withContext Result.failure(Exception("Invalid color scheme index"))
            }

            val scheme = schemes[schemeIndex]
            val colorFrame = ProtocolBuilder.buildColorScheme(scheme.brightness, scheme.colors)
            bleManager?.sendCommand(colorFrame)?.getOrThrow()

            Timber.d("Color scheme set to: ${scheme.name}")
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Failed to set color scheme")
            Result.failure(e)
        }
    }
}

