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
            if (bluetoothAdapter == null) {
                return@withContext Result.failure(Exception("Bluetooth not available"))
            }

            if (!bluetoothAdapter.isEnabled) {
                return@withContext Result.failure(Exception("Bluetooth is disabled"))
            }

            if (isScanning) {
                return@withContext Result.success(Unit)
            }

            _connectionState.value = ConnectionState.Scanning

            val scanner = bluetoothAdapter.bluetoothLeScanner
            val scanFilter = ScanFilter.Builder()
                .setDeviceName(BleConstants.DEVICE_NAME_PREFIX)
                .build()

            val scanSettings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build()

            scanner.startScan(listOf(scanFilter), scanSettings, scanCallback)
            isScanning = true

            Timber.d("Started BLE scanning for devices with prefix: ${BleConstants.DEVICE_NAME_PREFIX}")

            // Auto-stop scanning after timeout
            scope.launch {
                delay(BleConstants.SCAN_TIMEOUT_MS)
                stopScanning()
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
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            Timber.d("Found device: ${result.device.name} (${result.device.address})")
            _scannedDevices.tryEmit(result)
        }

        override fun onScanFailed(errorCode: Int) {
            Timber.e("Scan failed with error code: $errorCode")
            _connectionState.value = ConnectionState.Error("Scan failed: error $errorCode")
            isScanning = false
        }
    }

    @SuppressLint("MissingPermission")
    override suspend fun connectToDevice(deviceAddress: String): Result<Unit> = withContext(Dispatchers.Main) {
        try {
            stopScanning()

            val device = bluetoothAdapter?.getRemoteDevice(deviceAddress)
                ?: return@withContext Result.failure(Exception("Device not found"))

            _connectionState.value = ConnectionState.Connecting

            // Create BLE manager
            bleManager = VitruvianBleManager(context).apply {

                // Set up connection observer
                scope.launch {
                    connectionState.collect { status ->
                        when (status) {
                            is com.example.vitruvianredux.data.ble.ConnectionStatus.Ready -> {
                                _connectionState.value = ConnectionState.Connected(
                                    deviceName = device.name ?: "Vitruvian",
                                    deviceAddress = device.address
                                )
                            }
                            is com.example.vitruvianredux.data.ble.ConnectionStatus.Disconnected -> {
                                _connectionState.value = ConnectionState.Disconnected
                            }
                            is com.example.vitruvianredux.data.ble.ConnectionStatus.Error -> {
                                _connectionState.value = ConnectionState.Error(status.message)
                            }
                            else -> {}
                        }
                    }
                }

                // Collect monitor data
                scope.launch {
                    monitorData.collect { metric ->
                        _monitorData.emit(metric)
                    }
                }
            }

            // Connect to device
            bleManager?.connect(device)
                ?.timeout(BleConstants.CONNECTION_TIMEOUT_MS)
                ?.retry(3, 100)
                ?.useAutoConnect(false)
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
            // Send initial command
            bleManager?.sendCommand(ProtocolBuilder.buildInitCommand())?.getOrThrow()
            delay(100)

            // Send init preset
            bleManager?.sendCommand(ProtocolBuilder.buildInitPreset())?.getOrThrow()
            delay(100)

            Timber.d("Init sequence sent successfully")
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Failed to send init sequence")
            Result.failure(e)
        }
    }

    override suspend fun startWorkout(params: WorkoutParameters): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // Send init sequence first
            sendInitSequence().getOrThrow()

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

