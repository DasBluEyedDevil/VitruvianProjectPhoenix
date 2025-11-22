package com.example.vitruvianredux.data.ble

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.content.Context
import com.example.vitruvianredux.data.ble.GattRequestRejectedException
import com.example.vitruvianredux.data.ble.GattStatusException
import com.example.vitruvianredux.data.ble.NotReadyException
import com.example.vitruvianredux.domain.model.DiagnosticDetails
import com.example.vitruvianredux.domain.model.HeuristicPhaseStatistics
import com.example.vitruvianredux.domain.model.HeuristicStatistics
import com.example.vitruvianredux.domain.model.SampleStatus
import com.example.vitruvianredux.domain.model.WorkoutMetric
import com.example.vitruvianredux.util.BleConstants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import no.nordicsemi.android.ble.ConnectionPriorityRequest
import no.nordicsemi.android.ble.BleManager
import no.nordicsemi.android.ble.data.Data
import timber.log.Timber
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

/**
 * Vitruvian BLE Manager - Handles BLE communication with Vitruvian device
 * Uses Nordic BLE Library for robust BLE operations
 */
@OptIn(ExperimentalStdlibApi::class)
class VitruvianBleManager(
    context: Context,
    private val connectionLogger: com.example.vitruvianredux.data.logger.ConnectionLogger? = null
) : BleManager(context.applicationContext) {  // Always use application context to prevent leaks

    private var currentDeviceName: String? = null
    private var currentDeviceAddress: String? = null

    fun setDeviceInfo(name: String?, address: String?) {
        currentDeviceName = name
        currentDeviceAddress = address
    }

    /**
     * Enable or disable strict validation mode for sample data.
     * When enabled, position jumps greater than 200 units will be rejected.
     */
    fun setStrictValidationEnabled(enabled: Boolean) {
        strictValidationEnabled = enabled
        Timber.d("Strict validation enabled: $enabled")
    }

    // GATT characteristics
    private var nusRxCharacteristic: BluetoothGattCharacteristic? = null
    private var monitorCharacteristic: BluetoothGattCharacteristic? = null
    private var propertyCharacteristic: BluetoothGattCharacteristic? = null // Diagnostic
    private var repNotifyCharacteristic: BluetoothGattCharacteristic? = null
    private var heuristicCharacteristic: BluetoothGattCharacteristic? = null
    private var versionCharacteristic: BluetoothGattCharacteristic? = null

    // Official app workout command characteristics (for testing)
    private val workoutCmdCharacteristics = mutableListOf<BluetoothGattCharacteristic>()

    // Monitor polling - MUST be on Main dispatcher for Nordic BLE library
    private val pollingScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var monitorPollingJob: Job? = null
    private var propertyPollingJob: Job? = null
    private var heuristicPollingJob: Job? = null

    // Last good positions for filtering spikes (volatile for thread safety)
    @Volatile private var lastGoodPosA = 0
    @Volatile private var lastGoodPosB = 0

    // Velocity calculation for handle detection (volatile for thread safety)
    @Volatile private var lastPositionA = 0
    @Volatile private var lastPositionB = 0
    @Volatile private var lastTimestamp = 0L

    // Validation mode
    @Volatile private var strictValidationEnabled: Boolean = false

    // State flows
    private val _connectionState = MutableStateFlow<ConnectionStatus>(ConnectionStatus.Disconnected)
    val connectionState: StateFlow<ConnectionStatus> = _connectionState.asStateFlow()

    private val _diagnosticData = MutableStateFlow<DiagnosticDetails?>(null)
    val diagnosticData: StateFlow<DiagnosticDetails?> = _diagnosticData.asStateFlow()

    private val _heuristicData = MutableStateFlow<HeuristicStatistics?>(null)
    val heuristicData: StateFlow<HeuristicStatistics?> = _heuristicData.asStateFlow()

    // Monitor data flow - CRITICAL: Need buffer for high-frequency emissions!
    private val _monitorData = MutableSharedFlow<WorkoutMetric>(
        replay = 0,
        extraBufferCapacity = 64, // Buffer up to 64 emissions (640ms of data)
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val monitorData: SharedFlow<WorkoutMetric> = _monitorData.asSharedFlow()

    private val _repEvents = MutableSharedFlow<RepNotification>(
        replay = 0,
        extraBufferCapacity = 64,  // Buffer for rep notifications
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val repEvents: SharedFlow<RepNotification> = _repEvents.asSharedFlow()

    private val _handleState = MutableStateFlow<HandleState>(HandleState.Released)
    val handleState: StateFlow<HandleState> = _handleState.asStateFlow()

    // Command response flow - captures opcodes from incoming notifications
    // Used to wait for specific responses during initialization handshake
    private val _commandResponses = MutableSharedFlow<UByte>(
        replay = 0,
        extraBufferCapacity = 16,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    private val commandResponses: SharedFlow<UByte> = _commandResponses.asSharedFlow()

    // Just Lift detection parameters - Simple position-based
    private val HANDLE_GRABBED_THRESHOLD = 8.0  // Position > 8.0 = handles grabbed
    private val HANDLE_REST_THRESHOLD = 2.5     // Position < 2.5 = handles at rest
    private val VELOCITY_THRESHOLD = 100.0      // Velocity > 100 units/s = significant movement

    // Handle detection constants - matching official app (AUTO_START_SAFETY_STATE_COMPLETE.md)
    private val HANDLE_GRAB_FORCE_KG = 3.0f           // Force spike indicating grab
    private val HANDLE_GRAB_VELOCITY_THRESHOLD = 0.1f
    private val HANDLE_GRAB_DURATION_MS = 200L        // Official: 200ms sustained force
    private val HANDLE_RELEASE_FORCE_KG = 1.0f        // Force drop indicating release
    private val HANDLE_RELEASE_DURATION_MS = 500L     // Official: 500ms sustained low force

    // Track position range for tuning (logged at workout end)
    private var minPositionSeen = Double.MAX_VALUE
    private var maxPositionSeen = Double.MIN_VALUE

    override fun log(priority: Int, message: String) {
        Timber.tag("VitruvianBLE").log(priority, message)
    }

    @Deprecated("Override of deprecated base class method")
    override fun getMinLogPriority(): Int {
        return android.util.Log.DEBUG
    }

    @Deprecated("Override of deprecated base class method")
    override fun getGattCallback(): BleManagerGattCallback {
        return VitruvianGattCallback()
    }

    /**
     * Custom GATT callback for Vitruvian device
     */
    private inner class VitruvianGattCallback : BleManagerGattCallback() {

        private val notifyCharacteristics = mutableListOf<BluetoothGattCharacteristic>()

        @Deprecated("Using deprecated Nordic BLE API")
        override fun isRequiredServiceSupported(gatt: BluetoothGatt): Boolean {
            // Log all available services and characteristics for debugging
            Timber.d("=== Discovering BLE Services ===")
            gatt.services.forEach { service ->
                Timber.d("Service: ${service.uuid}")
                service.characteristics.forEach { char ->
                    Timber.d("  - Characteristic: ${char.uuid} (props: ${char.properties}, instance: ${char.instanceId})")

                    // Get the handle by reading the characteristic's instance ID
                    try {
                        val handleField = char.javaClass.getDeclaredField("mHandle")
                        handleField.isAccessible = true
                        val handle = handleField.getInt(char)
                        Timber.d("    HANDLE: 0x${handle.toString(16).uppercase()} = ${char.uuid}")
                    } catch (e: Exception) {
                        Timber.w("    Could not get handle: ${e.message}")
                    }
                }
            }
            Timber.d("=== End Service Discovery ===")

            // DIAGNOSTIC: Try to read firmware version from Device Information Service (DIS)
            // This is NON-BLOCKING and purely for logging/diagnostics
            tryReadFirmwareVersion(gatt)

            // Get the NUS service
            val nusService = gatt.getService(BleConstants.NUS_SERVICE_UUID)
            if (nusService == null) {
                Timber.e("NUS service not found")
                return false
            }

            // Get required characteristics
            nusRxCharacteristic = nusService.getCharacteristic(BleConstants.NUS_RX_CHAR_UUID)
            monitorCharacteristic = nusService.getCharacteristic(BleConstants.MONITOR_CHAR_UUID)
            propertyCharacteristic = nusService.getCharacteristic(BleConstants.DIAGNOSTIC_CHAR_UUID) // Also known as PROPERTY_CHAR
            repNotifyCharacteristic = nusService.getCharacteristic(BleConstants.REP_NOTIFY_CHAR_UUID)
            heuristicCharacteristic = nusService.getCharacteristic(BleConstants.HEURISTIC_CHAR_UUID)
            versionCharacteristic = nusService.getCharacteristic(BleConstants.VERSION_CHAR_UUID)

            // DIAGNOSTIC: Log characteristic discovery with timestamp
            val timestamp = System.currentTimeMillis()
            Timber.i("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
            Timber.i("✅ CHARACTERISTICS DISCOVERED! [$timestamp]")
            Timber.i("✅ RX=${nusRxCharacteristic != null}, Monitor=${monitorCharacteristic != null}, Diagnostic=${propertyCharacteristic != null}, RepNotify=${repNotifyCharacteristic != null}")
            Timber.i("✅ Heuristic=${heuristicCharacteristic != null}, Version=${versionCharacteristic != null}")
            if (nusRxCharacteristic != null) {
                Timber.i("✅ nusRxCharacteristic UUID: ${nusRxCharacteristic?.uuid}, instance: ${nusRxCharacteristic?.instanceId}")
            }
            Timber.i("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")

            Timber.d("Found characteristics in NUS service: RX=${nusRxCharacteristic != null}, Monitor=${monitorCharacteristic != null}, Diagnostic=${propertyCharacteristic != null}, RepNotify=${repNotifyCharacteristic != null}")

            // If characteristics not in NUS service, search all services
            if (repNotifyCharacteristic == null) {
                gatt.services.forEach { service ->
                    if (repNotifyCharacteristic == null) repNotifyCharacteristic = service.getCharacteristic(BleConstants.REP_NOTIFY_CHAR_UUID)
                    if (heuristicCharacteristic == null) heuristicCharacteristic = service.getCharacteristic(BleConstants.HEURISTIC_CHAR_UUID)
                    if (versionCharacteristic == null) versionCharacteristic = service.getCharacteristic(BleConstants.VERSION_CHAR_UUID)
                    if (propertyCharacteristic == null) propertyCharacteristic = service.getCharacteristic(BleConstants.DIAGNOSTIC_CHAR_UUID)
                }
            }

            if (nusRxCharacteristic == null) {
                Timber.e("NUS RX characteristic not found")
                return false
            }

            if (monitorCharacteristic == null) {
                Timber.e("Monitor characteristic not found")
                return false
            }

            // Rep notify is optional - warn but don't fail
            if (repNotifyCharacteristic == null) {
                Timber.w("⚠️ Rep notify characteristic not found - rep counting may not work!")
            }

            // Collect ALL characteristics for notifications (matching web app)
            notifyCharacteristics.clear()
            val allCharacteristics = gatt.services.flatMap { it.characteristics }
            for (uuid in BleConstants.NOTIFY_CHAR_UUIDS) {
                allCharacteristics.find { it.uuid == uuid }?.let { char ->
                    notifyCharacteristics.add(char)
                    Timber.d("Found notify characteristic: $uuid")
                }
            }
            Timber.d("Collected ${notifyCharacteristics.size} notify characteristics")

            // Collect workout command characteristics for testing official app protocol
            workoutCmdCharacteristics.clear()
            for (uuid in BleConstants.WORKOUT_CMD_CHAR_UUIDS) {
                allCharacteristics.find { it.uuid == uuid }?.let { char ->
                    workoutCmdCharacteristics.add(char)
                    Timber.d("Found workout command characteristic: $uuid")
                }
            }
            Timber.d("Collected ${workoutCmdCharacteristics.size} workout command characteristics")

            return true
        }

        /**
         * Try to read firmware version from Device Information Service (DIS)
         * This is purely diagnostic - failures are logged but don't affect connection
         *
         * @param gatt The GATT instance with discovered services
         */
        private fun tryReadFirmwareVersion(gatt: BluetoothGatt) {
            try {
                // Device Information Service UUID (standard BLE service)
                val DIS_SERVICE_UUID = UUID.fromString("0000180a-0000-1000-8000-00805f9b34fb")
                val FIRMWARE_REVISION_UUID = UUID.fromString("00002a26-0000-1000-8000-00805f9b34fb")
                val SOFTWARE_REVISION_UUID = UUID.fromString("00002a28-0000-1000-8000-00805f9b34fb")
                val MODEL_NUMBER_UUID = UUID.fromString("00002a24-0000-1000-8000-00805f9b34fb")

                // Try to get Device Information Service
                val deviceInfoService = gatt.getService(DIS_SERVICE_UUID)
                if (deviceInfoService == null) {
                    Timber.d("Device Information Service (DIS) not available - cannot read firmware version")
                    return
                }

                Timber.d("Device Information Service found - attempting to read firmware/model info...")

                // Try to read firmware revision (most important for diagnostics)
                val firmwareChar = deviceInfoService.getCharacteristic(FIRMWARE_REVISION_UUID)
                if (firmwareChar != null) {
                    readCharacteristic(firmwareChar)
                        .with { _, data ->
                            try {
                                val firmwareVersion = data.getStringValue(0) ?: "Unknown"
                                Timber.i("╔════════════════════════════════════════╗")
                                Timber.i("║  🔧 FIRMWARE VERSION: $firmwareVersion")
                                Timber.i("╚════════════════════════════════════════╝")

                                // Log to connection logger for user reports
                                connectionLogger?.log(
                                    eventType = "FIRMWARE_DETECTED",
                                    level = com.example.vitruvianredux.data.logger.ConnectionLogger.Level.INFO,
                                    deviceName = currentDeviceName,
                                    deviceAddress = currentDeviceAddress,
                                    message = "Firmware Version: $firmwareVersion"
                                )
                            } catch (e: Exception) {
                                Timber.w("Failed to parse firmware version: ${e.message}")
                            }
                        }
                        .fail { _, status ->
                            Timber.d("Failed to read firmware version (status: $status) - this is OK, continuing")
                        }
                        .enqueue()
                } else {
                    Timber.d("Firmware revision characteristic not found in DIS")
                }

                // Try to read model number (helpful for identifying device variant)
                val modelChar = deviceInfoService.getCharacteristic(MODEL_NUMBER_UUID)
                if (modelChar != null) {
                    readCharacteristic(modelChar)
                        .with { _, data ->
                            try {
                                val modelNumber = data.getStringValue(0) ?: "Unknown"
                                Timber.i("📱 Model Number: $modelNumber")
                                connectionLogger?.log(
                                    eventType = "MODEL_NUMBER",
                                    level = com.example.vitruvianredux.data.logger.ConnectionLogger.Level.INFO,
                                    deviceName = currentDeviceName,
                                    deviceAddress = currentDeviceAddress,
                                    message = "Model: $modelNumber"
                                )
                            } catch (e: Exception) {
                                Timber.w("Failed to parse model number: ${e.message}")
                            }
                        }
                        .fail { _, _ -> /* Silently ignore */ }
                        .enqueue()
                }

                // Try to read software revision (additional diagnostic info)
                val softwareChar = deviceInfoService.getCharacteristic(SOFTWARE_REVISION_UUID)
                if (softwareChar != null) {
                    readCharacteristic(softwareChar)
                        .with { _, data ->
                            try {
                                val softwareVersion = data.getStringValue(0) ?: "Unknown"
                                Timber.i("💾 Software Revision: $softwareVersion")
                            } catch (e: Exception) {
                                Timber.w("Failed to parse software revision: ${e.message}")
                            }
                        }
                        .fail { _, _ -> /* Silently ignore */ }
                        .enqueue()
                }

            } catch (e: Exception) {
                // Catch any unexpected errors to ensure this diagnostic code never crashes
                Timber.w("Exception while trying to read firmware version: ${e.message}")
                Timber.w("This is OK - firmware detection is optional")
            }
        }

        @Deprecated("Using deprecated Nordic BLE API")
        override fun onServicesInvalidated() {
            val timestamp = System.currentTimeMillis()
            Timber.e("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
            Timber.e("⚠️ onServicesInvalidated() CALLED! [$timestamp]")
            Timber.e("⚠️ This will NULL all characteristic references!")
            Timber.e("⚠️ CRITICAL: This usually indicates the device unexpectedly reset or disconnected")
            Timber.e("⚠️ This is the root cause of mid-workout disconnections on Android 16")
            Timber.e("⚠️ Stack trace:")
            Thread.currentThread().stackTrace.take(10).forEach {
                Timber.e("   at $it")
            }
            Timber.e("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")

            // Enhanced logging with device state information
            val deviceInfo = buildString {
                appendLine("Device Name: ${currentDeviceName ?: "Unknown"}")
                appendLine("Device Address: ${currentDeviceAddress ?: "Unknown"}")
                appendLine("Connection State: ${_connectionState.value}")
                appendLine("Monitor Polling Active: ${monitorPollingJob?.isActive}")
                appendLine("Property Polling Active: ${propertyPollingJob?.isActive}")
                appendLine("Characteristics Status BEFORE null:")
                appendLine("  - nusRxCharacteristic: ${nusRxCharacteristic != null}")
                appendLine("  - monitorCharacteristic: ${monitorCharacteristic != null}")
                appendLine("  - propertyCharacteristic: ${propertyCharacteristic != null}")
                appendLine("  - repNotifyCharacteristic: ${repNotifyCharacteristic != null}")
            }
            Timber.e("Device state at time of invalidation:\n$deviceInfo")

            connectionLogger?.logError(
                currentDeviceName ?: "Unknown",
                currentDeviceAddress ?: "Unknown",
                "CHARACTERISTICS_INVALIDATED",
                "onServicesInvalidated() called - all characteristics will be nulled. This is a critical BLE failure that typically indicates the device reset or connection was lost. $deviceInfo"
            )

            // NULL all characteristics
            nusRxCharacteristic = null
            monitorCharacteristic = null
            propertyCharacteristic = null
            repNotifyCharacteristic = null
            heuristicCharacteristic = null
            versionCharacteristic = null
            workoutCmdCharacteristics.clear()
            notifyCharacteristics.clear()

            // CRITICAL FIX: Update connection state to reflect that characteristics are invalid
            // This prevents the app from trying to send commands with null characteristics
            Timber.e("⚠️ Updating connection state to Disconnected due to service invalidation")
            _connectionState.value = ConnectionStatus.Disconnected

            // Stop all polling since characteristics are now invalid
            stopPolling()

            Timber.e("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
            Timber.e("⚠️ onServicesInvalidated() handling complete")
            Timber.e("⚠️ User must reconnect to device to continue")
            Timber.e("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        }

        @Deprecated("Using deprecated Nordic BLE API")
        override fun onDeviceDisconnected() {
            val timestamp = System.currentTimeMillis()
            Timber.w("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
            Timber.w("🔌 onDeviceDisconnected() CALLED! [$timestamp]")
            Timber.w("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")

            connectionLogger?.logDisconnected(
                currentDeviceName ?: "Unknown",
                currentDeviceAddress ?: "Unknown"
            )

            // Update connection state
            _connectionState.value = ConnectionStatus.Disconnected

            // Stop all polling
            stopPolling()
        }

        @Deprecated("Using deprecated Nordic BLE API")
        @Suppress("DEPRECATION")
        override fun initialize() {
            super.initialize()

            // Track pending operations: MTU request + connection priority + all notification enables
            val pendingOperations = AtomicInteger(notifyCharacteristics.size + 2)

            // Helper to check if all operations complete
            fun checkAllOperationsComplete() {
                val remaining = pendingOperations.decrementAndGet()
                Timber.d("Pending operations: $remaining")
                if (remaining == 0) {
                    _connectionState.value = ConnectionStatus.Ready
                    Timber.d("All initialization operations complete! Device ready.")

                    // Log enabled characteristics to ConnectionLogger for debugging
                    val uuidList = notifyCharacteristics.joinToString(", ") { it.uuid.toString().take(8) + "..." }
                    connectionLogger?.log(
                        eventType = "NOTIFICATIONS_ACTIVE",
                        level = com.example.vitruvianredux.data.logger.ConnectionLogger.Level.INFO,
                        deviceName = currentDeviceName,
                        deviceAddress = currentDeviceAddress,
                        message = "Notifications enabled on: $uuidList"
                    )

                    // Start property polling immediately to keep machine alive (keep-alive mechanism)
                    // The official app does this - property polling at 500ms intervals (matches official app)
                    // Monitor polling (100ms) only starts when workout begins
                    Timber.d("Starting keep-alive diagnostic polling (500ms - official app interval)...")
                    startDiagnosticPolling()
                }
            }

            // REQUEST HIGH CONNECTION PRIORITY FIRST - Critical for connection stability!
            // This ensures fast, reliable communication and prevents early disconnections
            // that were causing 5-second disconnect issues (Issue #131)
            Timber.d("Requesting HIGH connection priority for stable connection...")
            requestConnectionPriority(ConnectionPriorityRequest.CONNECTION_PRIORITY_HIGH)
                .done { _ ->
                    Timber.d("✅ Connection priority set to HIGH")
                    checkAllOperationsComplete()
                }
                .fail { _, status ->
                    Timber.w("⚠️ Failed to set connection priority (status: $status) - continuing anyway")
                    checkAllOperationsComplete()
                }
                .enqueue()

            // REQUEST MTU - Critical for large frames (96 bytes)!
            // Default MTU is 23 bytes, we need at least 100 bytes for program params
            requestMtu(247)
                .with { _, mtu ->
                    Timber.d("MTU successfully changed to $mtu bytes")
                }
                .fail { _, status ->
                    Timber.e("MTU request failed with status: $status (continuing anyway)")
                }
                .done { _ ->
                    checkAllOperationsComplete()
                }
                .enqueue()

            // Enable notifications on ALL required characteristics (matching web app behavior)
            // The machine requires all these to be enabled for proper operation
            Timber.d("Enabling core BLE notifications on ${notifyCharacteristics.size} characteristics...")

            for (characteristic in notifyCharacteristics) {
                Timber.d("  Enabling notifications on ${characteristic.uuid}...")

                if (characteristic.uuid == BleConstants.REP_NOTIFY_CHAR_UUID) {
                    // Special handler for rep notifications
                    setNotificationCallback(characteristic).with { _, data ->
                        Timber.d("🔥 REP NOTIFICATION CALLBACK FIRED! Data size: ${data.value?.size ?: 0} bytes")
                        handleRepNotification(data)
                    }
                } else {
                    // Generic handler for other notifications - capture command responses
                    setNotificationCallback(characteristic).with { _, data ->
                        val bytes = data.value
                        if (bytes != null && bytes.isNotEmpty()) {
                            // Parse opcode from first byte (command responses start with opcode)
                            val opcode = bytes[0].toUByte()
                            Timber.d("[notify ${characteristic.uuid}] ${bytes.size} bytes, opcode=0x${opcode.toString(16).uppercase().padStart(2, '0')}")

                            // Emit opcode to response flow for awaitResponse() to catch
                            _commandResponses.tryEmit(opcode)
                        } else {
                            Timber.d("[notify ${characteristic.uuid}] empty data")
                        }
                    }
                }

                enableNotifications(characteristic)
                    .done { _ ->
                        Timber.d("    -> Notifications active on ${characteristic.uuid}")
                        checkAllOperationsComplete()
                    }
                    .fail { _, status ->
                        Timber.w("    -> Failed to enable notifications on ${characteristic.uuid}: status=$status")
                        checkAllOperationsComplete()
                    }
                    .enqueue()
            }
        }
    }

    /**
     * Start polling monitor characteristic every 100ms
     * This is how the official app reads position/force data
     * Called when workout starts
     */
    fun startMonitorPolling() {
        // Reset position tracking for new workout
        minPositionSeen = Double.MAX_VALUE
        maxPositionSeen = Double.MIN_VALUE

        // Start with handles released; wait for actual grab detection from data
        _handleState.value = HandleState.Released

        // Start heuristic polling alongside monitor
        startHeuristicPolling()

        monitorPollingJob?.cancel()
        monitorPollingJob = pollingScope.launch {
            Timber.d("Starting monitor polling (100ms interval)")
            while (isActive) {
                try {
                    monitorCharacteristic?.let { char ->
                        // MUST use .with() and .enqueue() together
                        readCharacteristic(char)
                            .with { _, data ->
                                Timber.d("Monitor read callback fired!")
                                handleMonitorData(data)
                            }
                            .enqueue()
                    }
                    delay(100) // Poll every 100ms
                } catch (e: Exception) {
                    Timber.e(e, "Error in monitor polling")
                }
            }
        }
    }

    /**
     * Start polling diagnostic characteristic every 500ms (keep-alive + health monitoring)
     * Matches official app interval - Renamed from startPropertyPolling
     */
    fun startDiagnosticPolling() {
        propertyPollingJob?.cancel()
        propertyPollingJob = pollingScope.launch {
            Timber.d("🔄 Starting diagnostic polling (500ms interval - matches official app)")
            var successfulReads = 0
            var failedReads = 0

            while (isActive) {
                try {
                    val char = propertyCharacteristic
                    if (char == null) {
                        Timber.w("⚠️ Diagnostic characteristic is null - cannot maintain keep-alive!")
                        delay(500)
                        continue
                    }

                    readCharacteristic(char)
                        .with { _, data ->
                            successfulReads++
                            val bytes = data.value
                            if (bytes != null) {
                                parseDiagnosticData(bytes)
                            }
                        }
                        .fail { _, status ->
                            failedReads++
                            Timber.w("⚠️ Diagnostic read failed (status: $status)")
                        }
                        .enqueue()

                    delay(500) // Poll every 500ms (Official app interval - verified)
                } catch (e: Exception) {
                    failedReads++
                    Timber.e(e, "❌ Exception in diagnostic polling")
                    delay(500)
                }
            }
        }
    }

    fun startHeuristicPolling() {
        heuristicPollingJob?.cancel()
        heuristicPollingJob = pollingScope.launch {
            Timber.d("Starting heuristic polling (1000ms interval)")
            while (isActive) {
                try {
                    heuristicCharacteristic?.let { char ->
                        readCharacteristic(char)
                            .with { _, data ->
                                val bytes = data.value
                                if (bytes != null) {
                                    parseHeuristicData(bytes)
                                }
                            }
                            .enqueue()
                    }
                    delay(1000) // Poll every 1s
                } catch (e: Exception) {
                    Timber.e(e, "Error in heuristic polling")
                }
            }
        }
    }

    private fun parseDiagnosticData(bytes: ByteArray) {
        try {
            if (bytes.size < 20) return

            val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            val seconds = buffer.getInt()

            val faults = mutableListOf<Short>()
            repeat(4) { faults.add(buffer.getShort()) }

            val temps = mutableListOf<Byte>()
            repeat(8) { temps.add(buffer.get()) }

            val containsFaults = faults.any { it != 0.toShort() }

            _diagnosticData.value = DiagnosticDetails(
                seconds = seconds,
                faults = faults,
                temps = temps,
                containsFaults = containsFaults
            )
        } catch (e: Exception) {
            Timber.e(e, "Failed to parse diagnostic data")
        }
    }

    private fun parseHeuristicData(bytes: ByteArray) {
        try {
            if (bytes.size < 48) return

            val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)

            // Concentric
            val concentric = HeuristicPhaseStatistics(
                kgAvg = buffer.getFloat(),
                kgMax = buffer.getFloat(),
                velAvg = buffer.getFloat(),
                velMax = buffer.getFloat(),
                wattAvg = buffer.getFloat(),
                wattMax = buffer.getFloat()
            )

            // Eccentric
            val eccentric = HeuristicPhaseStatistics(
                kgAvg = buffer.getFloat(),
                kgMax = buffer.getFloat(),
                velAvg = buffer.getFloat(),
                velMax = buffer.getFloat(),
                wattAvg = buffer.getFloat(),
                wattMax = buffer.getFloat()
            )

            _heuristicData.value = HeuristicStatistics(concentric, eccentric)
        } catch (e: Exception) {
            Timber.e(e, "Failed to parse heuristic data")
        }
    }

    private fun validateSample(posA: Short, loadA: Short, posB: Short, loadB: Short): Boolean {
        if (!strictValidationEnabled) return true

        // Official App Validation Ranges
        // Position: -1000.0 to 1000.0
        // Force: 0.0 to 100.0

        val posADbl = posA / 10.0
        val posBDbl = posB / 10.0
        val loadADbl = loadA / 100.0
        val loadBDbl = loadB / 100.0

        if (posADbl !in -1000.0..1000.0) return false
        if (posBDbl !in -1000.0..1000.0) return false
        if (loadADbl !in 0.0..100.0) return false
        if (loadBDbl !in 0.0..100.0) return false

        return true
    }

    /**
     * Stop all polling
     */
    fun stopPolling() {
        val timestamp = System.currentTimeMillis()
        Timber.d("STOP_DEBUG: [$timestamp] stopPolling() called")

        // Log position range seen during workout for threshold tuning
        if (minPositionSeen != Double.MAX_VALUE && maxPositionSeen != Double.MIN_VALUE) {
            Timber.i("========== POSITION RANGE ANALYSIS ==========")
            Timber.i("Min position seen: $minPositionSeen")
            Timber.i("Max position seen: $maxPositionSeen")
            Timber.i("Handle grabbed threshold: $HANDLE_GRABBED_THRESHOLD (pos > 8.0 = grabbed)")
            Timber.i("Handle rest threshold: $HANDLE_REST_THRESHOLD (pos < 2.5 = at rest)")
            Timber.i("Velocity threshold: $VELOCITY_THRESHOLD (vel > 100 = moving)")
            Timber.i("===========================================")
        }

        val monitorJobState = monitorPollingJob?.run { "Active=${isActive}, Cancelled=${isCancelled}, Completed=${isCompleted}" } ?: "NULL"
        val diagnosticJobState = propertyPollingJob?.run { "Active=${isActive}, Cancelled=${isCancelled}, Completed=${isCompleted}" } ?: "NULL"
        val heuristicJobState = heuristicPollingJob?.run { "Active=${isActive}, Cancelled=${isCancelled}, Completed=${isCompleted}" } ?: "NULL"

        Timber.d("STOP_DEBUG: Monitor polling job state BEFORE cancel: $monitorJobState")
        Timber.d("STOP_DEBUG: Diagnostic polling job state BEFORE cancel: $diagnosticJobState")
        Timber.d("STOP_DEBUG: Heuristic polling job state BEFORE cancel: $heuristicJobState")

        monitorPollingJob?.cancel()
        propertyPollingJob?.cancel()
        heuristicPollingJob?.cancel()

        val afterCancel = System.currentTimeMillis()
        Timber.d("STOP_DEBUG: [$afterCancel] Jobs cancelled (took ${afterCancel - timestamp}ms)")
        Timber.d("STOP_DEBUG: Monitor job cancelled: ${monitorPollingJob?.isCancelled}")
        Timber.d("STOP_DEBUG: Diagnostic job cancelled: ${propertyPollingJob?.isCancelled}")
        Timber.d("STOP_DEBUG: Heuristic job cancelled: ${heuristicPollingJob?.isCancelled}")
    }

    /**
     * Enable Just Lift waiting mode - call this after workout completion
     * to start watching for velocity spike indicating user grabbed handles for next exercise
     */
    fun enableJustLiftWaitingMode() {
        Timber.i("Enabling Just Lift waiting mode - position hysteresis with velocity confirmation (vel>100)")
        _handleState.value = HandleState.Released
    }

    /**
     * Send a command to the device
     * CRITICAL: Do NOT use .split() - frames must be sent whole!
     */
    @Suppress("DEPRECATION")
    fun sendCommand(data: ByteArray): Result<Unit> {
        return try {
            val timestamp = System.currentTimeMillis()

            // DIAGNOSTIC: Log characteristic state at time of command
            Timber.d("SEND_COMMAND_DEBUG: [$timestamp] sendCommand() called")
            Timber.d("SEND_COMMAND_DEBUG: nusRxCharacteristic is ${if (nusRxCharacteristic != null) "AVAILABLE" else "NULL"}")
            Timber.d("SEND_COMMAND_DEBUG: isConnected=${isConnected}")
            Timber.d("SEND_COMMAND_DEBUG: connectionState=${_connectionState.value}")

            nusRxCharacteristic?.let { characteristic ->
                // Log detailed hex dump for debugging
                Timber.d("STOP_DEBUG: [$timestamp] === SENDING COMMAND ===")
                Timber.d("STOP_DEBUG: Command size: ${data.size} bytes")
                Timber.d("STOP_DEBUG: Full hex: ${data.joinToString(" ") { "0x%02X".format(it) }}")
                Timber.d("STOP_DEBUG: Hex string: ${data.joinToString(" ") { "%02X".format(it) }}")

                // Show first 64 bytes formatted for easy reading
                if (data.size > 0) {
                    val preview = data.take(64)
                    val formatted = preview.chunked(16) { bytes ->
                        bytes.joinToString(" ") { "%02x".format(it) }
                    }.joinToString("\n  ")
                    Timber.d("STOP_DEBUG: First ${preview.size} bytes:\n  $formatted")
                }

                val beforeWrite = System.currentTimeMillis()
                Timber.d("STOP_DEBUG: [$beforeWrite] About to write to characteristic ${characteristic.uuid}")
                writeCharacteristic(characteristic, data)
                    // REMOVED .split() - Vitruvian protocol requires exact frame sizes!
                    // .split() was breaking 96-byte program params into chunks
                    .enqueue()

                val afterWrite = System.currentTimeMillis()
                Timber.d("STOP_DEBUG: [$afterWrite] Write enqueued (took ${afterWrite - beforeWrite}ms)")
                Timber.d("STOP_DEBUG: === COMMAND SENT ===")
                Result.success(Unit)
            } ?: Result.failure(NotReadyException("NUS RX characteristic not available"))
        } catch (e: Exception) {
            Timber.e(e, "STOP_DEBUG: Failed to send command")
            Result.failure(e)
        }
    }

    /**
     * Test PROGRAM frame on all workout characteristics
     * Sends the 96-byte PROGRAM frame (Old School mode) to each characteristic
     */
    @Suppress("DEPRECATION")
    suspend fun testOfficialAppProtocol(): Result<Unit> = kotlinx.coroutines.withContext(Dispatchers.Main) {
        try {
            Timber.d("=== TESTING PROGRAM FRAME ON ALL CHARACTERISTICS ===")
            Timber.d("Found ${workoutCmdCharacteristics.size} workout command characteristics to test")

            if (workoutCmdCharacteristics.isEmpty()) {
                Timber.e("No workout command characteristics found!")
                return@withContext Result.failure(NotReadyException("No workout command characteristics available"))
            }

            // Build PROGRAM frame for Old School workout: 20kg per cable, 5 reps
            val programFrame = com.example.vitruvianredux.util.ProtocolBuilder.buildProgramParams(
                com.example.vitruvianredux.domain.model.WorkoutParameters(
                    workoutType = com.example.vitruvianredux.domain.model.WorkoutType.Program(
                        com.example.vitruvianredux.domain.model.ProgramMode.OldSchool
                    ),
                    weightPerCableKg = 20f,
                    reps = 5
                )
            )

            Timber.d("PROGRAM frame size: ${programFrame.size} bytes")
            Timber.d("PROGRAM frame (first 32 bytes): ${programFrame.take(32).joinToString(" ") { "%02X".format(it) }}")

            workoutCmdCharacteristics.forEachIndexed { index, char ->
                Timber.d("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                Timber.d("Testing characteristic ${index + 1}/${workoutCmdCharacteristics.size}")
                Timber.d("UUID: ${char.uuid}")
                Timber.d("Properties: ${char.properties}")
                Timber.d("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")

                // Send PROGRAM frame
                Timber.d("→ Sending 96-byte PROGRAM frame...")
                writeCharacteristic(char, programFrame, BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE)
                    .enqueue()

                Timber.d("✓ PROGRAM frame sent to ${char.uuid}")
                Timber.d("→ Waiting 10 seconds for response...")
                Timber.d("→ WATCH FOR: Cable engagement and workout start")
                Timber.d("→ WATCH FOR: Rep notifications on UUID 8308f2a6")

                delay(10000)

                Timber.d("Moving to next characteristic...\n")
            }

            Timber.d("=== TESTING COMPLETE ===")
            Timber.d("Total characteristics tested: ${workoutCmdCharacteristics.size}")
            Timber.d("If cables engaged during test, that characteristic is the workout command channel!")

            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Failed to test PROGRAM frame")
            Result.failure(e)
        }
    }

    /**
     * Analyze handle state using simple position-based hysteresis
     * with velocity confirmation for Just Lift mode
     */
    private fun analyzeHandleState(metric: WorkoutMetric): HandleState {
        val posA = metric.positionA.toDouble()
        val posB = metric.positionB.toDouble()
        val velocityA = metric.velocityA
        val velocityB = metric.velocityB

        // Track position range for post-workout tuning (use max of both handles)
        minPositionSeen = minOf(minPositionSeen, minOf(posA, posB))
        maxPositionSeen = maxOf(maxPositionSeen, maxOf(posA, posB))

        val currentState = _handleState.value

        // Check both handles - support single-handle exercises (Issue #102)
        val handleAGrabbed = posA > HANDLE_GRABBED_THRESHOLD
        val handleBGrabbed = posB > HANDLE_GRABBED_THRESHOLD
        val handleAMoving = velocityA > VELOCITY_THRESHOLD
        val handleBMoving = velocityB > VELOCITY_THRESHOLD

        // Simple hysteresis with velocity check
        return when (currentState) {
            HandleState.Released, HandleState.Moving -> {
                // Check if EITHER handle is grabbed and moving (for single-handle exercises)
                val aActive = handleAGrabbed && handleAMoving
                val bActive = handleBGrabbed && handleBMoving

                if (aActive || bActive) {
                    val activeHandle = if (aActive && bActive) "both" else if (aActive) "A" else "B"
                    if (activeHandle == "both") {
                        Timber.d("GRAB CHECK: handle=both, posA=$posA > $HANDLE_GRABBED_THRESHOLD, velA=$velocityA > $VELOCITY_THRESHOLD, posB=$posB > $HANDLE_GRABBED_THRESHOLD, velB=$velocityB > $VELOCITY_THRESHOLD")
                        Timber.i("GRAB CONFIRMED: handle=both, posA=$posA, velA=$velocityA, posB=$posB, velB=$velocityB")
                    } else {
                        val activePos = if (aActive) posA else posB
                        val activeVel = if (aActive) velocityA else velocityB
                        Timber.d("GRAB CHECK: handle=$activeHandle, pos=$activePos > $HANDLE_GRABBED_THRESHOLD, vel=$activeVel > $VELOCITY_THRESHOLD")
                        Timber.i("GRAB CONFIRMED: handle=$activeHandle, pos=$activePos, vel=$activeVel")
                    }
                    HandleState.Grabbed
                } else if (handleAGrabbed || handleBGrabbed) {
                    // Position extended but no significant movement yet
                    HandleState.Moving
                } else {
                    HandleState.Released
                }
            }

            HandleState.Grabbed -> {
                // Consider released only if BOTH handles are at rest
                val aReleased = posA < HANDLE_REST_THRESHOLD
                val bReleased = posB < HANDLE_REST_THRESHOLD

                if (aReleased && bReleased) {
                    Timber.d("RELEASE DETECTED: posA=$posA, posB=$posB < $HANDLE_REST_THRESHOLD")
                    HandleState.Released
                } else {
                    HandleState.Grabbed
                }
            }
        }
    }

    private fun handleMonitorData(data: Data) {
        try {
            Timber.v("handleMonitorData called")
            val bytes = data.value
            if (bytes == null) {
                Timber.w("Monitor data is null!")
                return
            }

            if (bytes.size < 16) {
                Timber.w("Monitor data too short: ${bytes.size} bytes")
                return
            }

            val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)

            // Parse the monitor data packet (matching device.js parseMonitorData)
            // Format: u16[0-1]=ticks, u16[2]=posA, u16[4]=loadA*100, u16[5]=posB, u16[7]=loadB*100

            // Strict Validation (Official App Logic)
            val sPosA = buffer.getShort(4)
            val sLoadA = buffer.getShort(8)
            val sPosB = buffer.getShort(10)
            val sLoadB = buffer.getShort(14)

            if (!validateSample(sPosA, sLoadA, sPosB, sLoadB)) {
                Timber.w("Strict Validation: Sample rejected. PosA=$sPosA, LoadA=$sLoadA, PosB=$sPosB, LoadB=$sLoadB")
                return
            }

            val f0 = buffer.getShort(0).toInt() and 0xFFFF
            val f1 = buffer.getShort(2).toInt() and 0xFFFF
            val f2 = buffer.getShort(4).toInt() and 0xFFFF
            val f4 = buffer.getShort(8).toInt() and 0xFFFF
            val f5 = buffer.getShort(10).toInt() and 0xFFFF
            val f7 = buffer.getShort(14).toInt() and 0xFFFF

            // Reconstruct 32-bit tick counter
            val ticks = f0 + (f1 shl 16)

            // Position values (filter spikes > 50000)
            var positionA = f2
            var positionB = f5
            if (positionA > 50000) {
                positionA = lastGoodPosA
            } else {
                lastGoodPosA = positionA
            }
            if (positionB > 50000) {
                positionB = lastGoodPosB
            } else {
                lastGoodPosB = positionB
            }

            // Load in kg (device sends kg * 100)
            val loadA = f4 / 100.0f
            val loadB = f7 / 100.0f

            // Parse Status Flags (Safety) - Bytes 16-17
            val statusFlags = if (bytes.size >= 18) {
                val rawFlags = buffer.getShort(16).toInt() and 0xFFFF
                SampleStatus.fromBitfield(rawFlags)
            } else {
                emptySet()
            }

            // Calculate velocity for handle detection (official app protocol)
            val currentTime = System.currentTimeMillis()
            val velocityA = if (lastTimestamp > 0L) {
                val deltaTime = (currentTime - lastTimestamp) / 1000.0  // Convert to seconds
                val deltaPos = positionA - lastPositionA
                if (deltaTime > 0) {
                    Math.abs(deltaPos / deltaTime)  // Absolute velocity
                } else {
                    0.0
                }
            } else {
                0.0
            }

            // Calculate velocity for right handle (for single-handle exercises)
            val velocityB = if (lastTimestamp > 0L) {
                val deltaTime = (currentTime - lastTimestamp) / 1000.0  // Convert to seconds
                val deltaPos = positionB - lastPositionB
                if (deltaTime > 0) {
                    Math.abs(deltaPos / deltaTime)  // Absolute velocity
                } else {
                    0.0
                }
            } else {
                0.0
            }

            lastPositionA = positionA
            lastPositionB = positionB
            lastTimestamp = currentTime

            // ENHANCED LOGGING FOR FORCE DISPLAY DEBUGGING
            // Always log first few, then reduce spam
            if (ticks < 1000 || ticks % 100 == 0) {
                Timber.d("=== MONITOR DATA DEBUG ===")
                Timber.d("Raw bytes[8-9]: ${bytes[8].toUByte()}, ${bytes[9].toUByte()}")
                Timber.d("Raw bytes[14-15]: ${bytes[14].toUByte()}, ${bytes[15].toUByte()}")
                Timber.d("Parsed f4 (loadA*100): $f4")
                Timber.d("Parsed f7 (loadB*100): $f7")
                Timber.d("LoadA (kg): $loadA")
                Timber.d("LoadB (kg): $loadB")
                Timber.d("Total Load: ${loadA + loadB} kg")
                Timber.d("PositionA: $positionA, PositionB: $positionB")
                Timber.d("VelocityA: $velocityA, VelocityB: $velocityB")
                Timber.d("Ticks: $ticks")
                Timber.d("==========================")
            }

            val metric = WorkoutMetric(
                timestamp = currentTime,
                loadA = loadA,
                loadB = loadB,
                positionA = positionA,
                positionB = positionB,
                ticks = ticks,
                velocityA = velocityA,
                velocityB = velocityB,
                statusFlags = statusFlags
            )

            // Log monitor data to ConnectionLogger (sampled)
            connectionLogger?.logMonitorDataReceived(
                currentDeviceName,
                currentDeviceAddress,
                positionA,
                positionB,
                loadA,
                loadB
            )

            val emitted = _monitorData.tryEmit(metric)
            Timber.v("Emitted metric to flow: success=$emitted, subscribers=${_monitorData.subscriptionCount.value}")
            if (!emitted && ticks % 100 == 0) {
                Timber.w("Failed to emit metric - no collectors? Subscribers: ${_monitorData.subscriptionCount.value}")
            }

            // Analyze and update handle state
            val newHandleState = analyzeHandleState(metric)
            if (newHandleState != _handleState.value) {
                _handleState.value = newHandleState
                Timber.d("Handle state changed: $newHandleState")
            }

        } catch (e: Exception) {
            Timber.e(e, "Error parsing monitor data")
        }
    }

    /**
     * Handle rep notification data
     * Parses full 24-byte Reps characteristic structure (matches official app)
     *
     * Structure (Little-Endian):
     * - Bytes 0-3: up (int32) - upward movement counter
     * - Bytes 4-7: down (int32) - downward movement counter
     * - Bytes 8-11: rangeTop (float) - upper range threshold (default 300.0)
     * - Bytes 12-15: rangeBottom (float) - lower range threshold (default 0.0)
     * - Bytes 16-17: repsRomCount (short) - Current ROM (Range of Motion) rep count
     * - Bytes 18-19: repsRomTotal (short) - Total ROM reps
     * - Bytes 20-21: repsSetCount (short) - Current set rep count
     * - Bytes 22-23: repsSetTotal (short) - Total set reps
     */
    private fun handleRepNotification(data: Data) {
        try {
            val bytes = data.value ?: return

            if (bytes.size < 24) {
                Timber.w("Rep notification too short: ${bytes.size} bytes (expected 24)")
                return
            }

            val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)

            // Parse full structure
            val up = buffer.getInt(0)
            val down = buffer.getInt(4)
            val rangeTop = buffer.getFloat(8)
            val rangeBottom = buffer.getFloat(12)
            val repsRomCount = buffer.getShort(16)
            val repsRomTotal = buffer.getShort(18)
            val repsSetCount = buffer.getShort(20)
            val repsSetTotal = buffer.getShort(22)

            Timber.d("Rep notification: up=$up, down=$down, rangeTop=$rangeTop, rangeBottom=$rangeBottom")
            Timber.d("  ROM: $repsRomCount/$repsRomTotal, Set: $repsSetCount/$repsSetTotal")

            val repData = RepNotification(
                up = up,
                down = down,
                rangeTop = rangeTop,
                rangeBottom = rangeBottom,
                repsRomCount = repsRomCount,
                repsRomTotal = repsRomTotal,
                repsSetCount = repsSetCount,
                repsSetTotal = repsSetTotal,
                rawData = bytes,
                timestamp = System.currentTimeMillis()
            )
            val emitted = _repEvents.tryEmit(repData)
            Timber.d("🔥 Emitted rep event: success=$emitted, subscribers=${_repEvents.subscriptionCount.value}")
        } catch (e: Exception) {
            Timber.e(e, "Error parsing rep notification")
        }
    }

    /**
     * Wait for a specific command response opcode
     * Used during initialization handshake to ensure proper protocol ordering
     *
     * @param expectedOpcode The opcode to wait for (e.g., 0x0Bu for INIT_RESPONSE)
     * @param timeoutMs Timeout in milliseconds (default 5 seconds)
     * @return true if response received, false if timeout
     */
    @Suppress("unused")
    suspend fun awaitResponse(expectedOpcode: UByte, timeoutMs: Long = 5000L): Boolean {
        return try {
            val opcodeHex = expectedOpcode.toString(16).uppercase().padStart(2, '0')
            Timber.d("⏳ Waiting for response opcode 0x$opcodeHex (timeout: ${timeoutMs}ms)")
            val result = withTimeoutOrNull(timeoutMs) {
                commandResponses.filter { it == expectedOpcode }.first()
            }
            if (result != null) {
                Timber.d("✅ Received expected response opcode 0x$opcodeHex")
                true
            } else {
                Timber.w("⏱️ Timeout waiting for response opcode 0x$opcodeHex")
                false
            }
        } catch (e: Exception) {
            val opcodeHex = expectedOpcode.toString(16).uppercase().padStart(2, '0')
            Timber.e(e, "Error waiting for response opcode 0x$opcodeHex")
            false
        }
    }

    /**
     * Clean up resources and cancel all polling jobs
     * Should be called when the BleManager is no longer needed
     */
    fun cleanup() {
        Timber.d("Cleaning up BleManager resources")
        monitorPollingJob?.cancel()
        propertyPollingJob?.cancel()
        heuristicPollingJob?.cancel()
        // Do NOT cancel the scope itself, as it needs to be reused for future connections
        // pollingScope.coroutineContext[Job]?.cancel()
    }

}
