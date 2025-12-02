@file:Suppress("unused")  // Protocol implementation - many methods kept for API completeness

package com.example.vitruvianredux.data.ble

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.content.Context
import com.example.vitruvianredux.domain.model.DiagnosticDetails
import com.example.vitruvianredux.domain.model.HeuristicPhaseStatistics
import com.example.vitruvianredux.domain.model.HeuristicStatistics
import com.example.vitruvianredux.domain.model.SampleStatus
import com.example.vitruvianredux.domain.model.WorkoutMetric
import com.example.vitruvianredux.util.BleConstants
import com.example.vitruvianredux.util.WorkoutConstants
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
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import no.nordicsemi.android.ble.BleManager
import no.nordicsemi.android.ble.ConnectionPriorityRequest
import no.nordicsemi.android.ble.data.Data
import timber.log.Timber
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.resume

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
    private var heartbeatJob: Job? = null

    private val HEARTBEAT_INTERVAL_MS = 2000L
    private val HEARTBEAT_READ_TIMEOUT_MS = 1500L
    private val HEARTBEAT_NO_OP = byteArrayOf(0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte())

    // Last good positions for filtering invalid values (volatile for thread safety)
    // Position values are in mm (-1000.0 to +1000.0)
    @Volatile private var lastGoodPosA = 0.0f
    @Volatile private var lastGoodPosB = 0.0f

    // Position tracking for validation and velocity (volatile for thread safety)
    // Position values are in mm (-1000.0 to +1000.0)
    @Volatile private var lastPositionA = 0.0f
    @Volatile private var lastPositionB = 0.0f
    @Volatile private var lastTimestamp = 0L
    @Volatile private var strictValidationEnabled = false

    // Smoothed velocity using Exponential Moving Average (EMA) for stall detection (Issue #204)
    // SIGNED velocity: sensor jitter oscillates (+2, -3, +1mm), so signed values average toward 0
    // Using abs() would make all jitter positive, averaging to ~20mm/s and preventing stall detection
    @Volatile private var smoothedVelocityA = 0.0
    @Volatile private var smoothedVelocityB = 0.0
    // EMA alpha: 0.15 = aggressive smoothing for high-extension exercises (shoulder press)
    // Lower alpha reduces jitter impact but makes velocity response slightly slower
    private val VELOCITY_SMOOTHING_ALPHA = 0.15

    // Diagnostic info exposed for Protocol Tester
    @Volatile var detectedFirmwareVersion: String? = null
        private set
    @Volatile var negotiatedMtu: Int? = null
        private set

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

    // Deload event flow - emitted when DELOAD_OCCURRED flag is detected
    // The repository should observe this and send STOP to clear the machine's fault state
    private val _deloadOccurredEvents = MutableSharedFlow<Unit>(
        replay = 0,
        extraBufferCapacity = 8,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val deloadOccurredEvents: SharedFlow<Unit> = _deloadOccurredEvents.asSharedFlow()

    // Reconnection request flow - emitted when onServicesInvalidated() is called
    // This handles the Android 16 Pixel BLE stack bug where GATT services get invalidated
    // The repository should observe this and attempt to reconnect after a short delay
    private val _reconnectionRequested = MutableSharedFlow<ReconnectionRequest>(
        replay = 0,
        extraBufferCapacity = 4,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val reconnectionRequested: SharedFlow<ReconnectionRequest> = _reconnectionRequested.asSharedFlow()

    // Debounce for deload events - don't spam STOP commands
    private var lastDeloadEventTime = 0L
    private val DELOAD_EVENT_DEBOUNCE_MS = 2000L  // Only emit once per 2 seconds

    // Counter for monitor notifications (for diagnostic logging)
    @Volatile private var monitorNotificationCount = 0L

    // Command response flow - captures opcodes from incoming notifications
    // Used to wait for specific responses during initialization handshake
    private val _commandResponses = MutableSharedFlow<UByte>(
        replay = 0,
        extraBufferCapacity = 16,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    private val commandResponses: SharedFlow<UByte> = _commandResponses.asSharedFlow()

    // Just Lift detection parameters - v0.5.1-beta values (PROVEN WORKING)
    // Position-based with velocity check for grab confirmation
    private val HANDLE_GRABBED_THRESHOLD = 8.0   // Position > 8.0 = handles grabbed
    private val HANDLE_REST_THRESHOLD = 5.0      // Position < 5.0 = handles at rest (Increased from 2.5 to handle drift)
    private val VELOCITY_THRESHOLD = 50.0        // Velocity > 50 mm/s = significant movement (matches official concentric threshold)

    // Track position range for tuning (logged at workout end)
    private var minPositionSeen = Double.MAX_VALUE
    private var maxPositionSeen = Double.MIN_VALUE

    // Force-based grab/release timing
    private var forceAboveGrabThresholdStart: Long? = null
    private var forceBelowReleaseThresholdStart: Long? = null

    /**
     * Enable or disable strict validation mode (matching trainer).
     * When enabled, large position jumps (>200) are filtered as invalid.
     */
    fun setStrictValidationEnabled(enabled: Boolean) {
        strictValidationEnabled = enabled
        Timber.d("Strict validation enabled: $enabled")
    }

    override fun log(priority: Int, message: String) {
        Timber.tag("VitruvianBLE").log(priority, message)
    }

    // Nordic BLE Library deprecated API overrides
    // Migration: When Nordic releases a stable non-deprecated API (expected in future library versions),
    // these methods should be migrated. Track: https://github.com/NordicSemiconductor/Android-BLE-Library
    @Deprecated("Nordic BLE Library deprecated API - required override until library provides stable alternative")
    @Suppress("OVERRIDE_DEPRECATION")
    override fun getMinLogPriority(): Int {
        return android.util.Log.DEBUG  // Required by Nordic BLE library - maps to Timber via log() override
    }

    @Deprecated("Nordic BLE Library deprecated API - required override until library provides stable alternative")
    override fun getGattCallback(): BleManagerGattCallback {
        return VitruvianGattCallback()
    }

    /**
     * Custom GATT callback for Vitruvian device
     */
    private inner class VitruvianGattCallback : BleManagerGattCallback() {

        private val notifyCharacteristics = mutableListOf<BluetoothGattCharacteristic>()

        @Deprecated("Nordic BLE Library deprecated API - required override until library provides stable alternative")
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

            // DIAGNOSTIC: Try to read Vitruvian VERSION characteristic
            // This contains hardware/firmware info in a proprietary format (undocumented)
            tryReadVitruvianVersion(gatt)

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

            // Collect workout command characteristics for testing trainer protocol
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
                                detectedFirmwareVersion = firmwareVersion  // Store for Protocol Tester
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

        /**
         * Try to read Vitruvian VERSION characteristic
         * This contains hardware/firmware info in a proprietary format.
         * Format is undocumented - we log raw hex to help with debugging.
         *
         * @param gatt The GATT instance with discovered services
         */
        private fun tryReadVitruvianVersion(gatt: BluetoothGatt) {
            try {
                // Find VERSION characteristic across all services
                val versionChar = gatt.services
                    .flatMap { it.characteristics }
                    .find { it.uuid == BleConstants.VERSION_CHAR_UUID }

                if (versionChar == null) {
                    Timber.d("VERSION characteristic not found - cannot read Vitruvian version info")
                    return
                }

                Timber.d("VERSION characteristic found - attempting to read...")

                readCharacteristic(versionChar)
                    .with { _, data ->
                        try {
                            val bytes = data.value
                            if (bytes != null && bytes.isNotEmpty()) {
                                val hexString = bytes.joinToString(" ") { "%02X".format(it) }
                                Timber.i("╔════════════════════════════════════════════════════════════╗")
                                Timber.i("║  VITRUVIAN VERSION CHARACTERISTIC (READ)                   ║")
                                Timber.i("║  Size: ${bytes.size} bytes                                        ║")
                                Timber.i("║  Hex: $hexString")
                                Timber.i("╚════════════════════════════════════════════════════════════╝")

                                // Log to connection logger for user reports
                                connectionLogger?.log(
                                    eventType = "VERSION_DATA",
                                    level = com.example.vitruvianredux.data.logger.ConnectionLogger.Level.INFO,
                                    deviceName = currentDeviceName,
                                    deviceAddress = currentDeviceAddress,
                                    message = "Vitruvian VERSION characteristic read",
                                    details = "Size: ${bytes.size} bytes\nHex: $hexString\n\nNote: Format is proprietary/undocumented. Contains hardware/firmware info."
                                )
                            }
                        } catch (e: Exception) {
                            Timber.w("Failed to parse VERSION characteristic: ${e.message}")
                        }
                    }
                    .fail { _, status ->
                        Timber.d("Failed to read VERSION characteristic (status: $status) - this is OK")
                    }
                    .enqueue()

            } catch (e: Exception) {
                Timber.w("Exception while trying to read VERSION characteristic: ${e.message}")
            }
        }

        @Deprecated("Nordic BLE Library deprecated API - required override until library provides stable alternative")
        override fun onServicesInvalidated() {
            val timestamp = System.currentTimeMillis()
            Timber.w("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
            Timber.w("⚠️ onServicesInvalidated() CALLED! [$timestamp]")

            // CRITICAL FIX for Android 16 Pixel BLE stack bug:
            // The trainer uses raw Android BLE and never gets this callback.
            // It just keeps using existing characteristic references and works fine.
            // If we're still connected at the BLE level, we should do the same - ignore this
            // callback and keep our existing references instead of triggering a disconnect loop.
            // See: https://github.com/NordicSemiconductor/Kotlin-BLE-Library/issues/122

            if (isConnected) {
                Timber.w("⚠️ BLE connection is STILL ACTIVE (isConnected=true)")
                Timber.w("⚠️ IGNORING service invalidation - keeping existing characteristic references")
                Timber.w("⚠️ This mimics trainer behavior (raw BLE has no such callback)")
                Timber.w("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")

                // Log but DON'T disconnect - keep existing references like trainer does
                connectionLogger?.log(
                    eventType = "SERVICES_INVALIDATED_IGNORED",
                    level = com.example.vitruvianredux.data.logger.ConnectionLogger.Level.WARNING,
                    deviceName = currentDeviceName,
                    deviceAddress = currentDeviceAddress,
                    message = "onServicesInvalidated() called but isConnected=true - ignoring (Android 16 Pixel bug workaround)"
                )
                return  // Keep existing references, don't disconnect
            }

            // Only if we're actually disconnected at the BLE level, clean up
            Timber.e("⚠️ BLE connection is DEAD (isConnected=false) - cleaning up")
            Timber.e("⚠️ Stack trace:")
            Thread.currentThread().stackTrace.take(10).forEach {
                Timber.e("   at $it")
            }
            Timber.e("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")

            // Capture device info BEFORE clearing state (needed for reconnection)
            val deviceName = currentDeviceName
            val deviceAddress = currentDeviceAddress

            // Log current connection state
            connectionLogger?.logError(
                deviceName ?: "Unknown",
                deviceAddress ?: "Unknown",
                "CHARACTERISTICS_INVALIDATED",
                "onServicesInvalidated() called with isConnected=false - cleaning up"
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

            // Update connection state to reflect that characteristics are invalid
            Timber.e("⚠️ Updating connection state to Disconnected due to service invalidation")
            _connectionState.value = ConnectionStatus.Disconnected

            // Stop all polling since characteristics are now invalid
            stopPolling()

            // REQUEST AUTO-RECONNECT: Emit event for repository to handle reconnection
            if (deviceAddress != null) {
                Timber.i("🔄 Requesting auto-reconnect to $deviceName ($deviceAddress)")
                pollingScope.launch {
                    _reconnectionRequested.emit(
                        ReconnectionRequest(
                            deviceName = deviceName,
                            deviceAddress = deviceAddress,
                            reason = "onServicesInvalidated",
                            timestamp = timestamp
                        )
                    )
                }
            } else {
                Timber.w("⚠️ Cannot request reconnect - device address is null")
            }
        }

        @Deprecated("Nordic BLE Library deprecated API - required override until library provides stable alternative")
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

        @Deprecated("Nordic BLE Library deprecated API - required override until library provides stable alternative")
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

                    // Start property polling immediately to keep machine alive (keep-alive mechanism)
                    // The trainer does this - property polling at 500ms intervals (matches trainer)
                    // Monitor polling (100ms) only starts when workout begins
                    Timber.d("Starting keep-alive diagnostic polling (500ms - trainer interval)...")
                    startDiagnosticPolling()
                    Timber.d("Starting BLE heartbeat (RX read with no-op fallback)...")
                    startHeartbeat()
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
                    negotiatedMtu = mtu  // Store for Protocol Tester
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
                // NOTE: MONITOR_CHAR_UUID (SAMPLE_CHAR) is NOT a NotifiableCharacteristic!
                // Per trainer analysis, Sample data MUST be polled via readCharacteristic().
                // Attempting to enable notifications on it caused issues on Android 16 Pixel devices.
                } else if (characteristic.uuid == BleConstants.VERSION_CHAR_UUID) {
                    // Special handler for VERSION characteristic - log raw hex for debugging
                    setNotificationCallback(characteristic).with { _, data ->
                        val bytes = data.value
                        if (bytes != null && bytes.isNotEmpty()) {
                            val hexString = bytes.joinToString(" ") { "%02X".format(it) }
                            Timber.i("╔════════════════════════════════════════════════════════════╗")
                            Timber.i("║  VERSION CHARACTERISTIC DATA RECEIVED                      ║")
                            Timber.i("║  Size: ${bytes.size} bytes                                        ║")
                            Timber.i("║  Hex: $hexString")
                            Timber.i("╚════════════════════════════════════════════════════════════╝")

                            // Log to connection logger for user reports - helps with debugging
                            connectionLogger?.log(
                                eventType = "VERSION_DATA",
                                level = com.example.vitruvianredux.data.logger.ConnectionLogger.Level.INFO,
                                deviceName = currentDeviceName,
                                deviceAddress = currentDeviceAddress,
                                message = "VERSION characteristic notification",
                                details = "Size: ${bytes.size} bytes\nHex: $hexString"
                            )
                        }
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
     * Suspend function to read monitor characteristic and wait for completion.
     *
     * CRITICAL: This function WAITS for the BLE read to complete before returning.
     * This prevents queue flooding that caused Android 16 Pixel disconnects.
     *
     * The trainer uses Kotlin coroutines with suspend functions that
     * naturally serialize BLE operations - each read waits for callback before next.
     *
     * ANDROID 16 FIX: Added timeout to prevent hanging if BLE stack doesn't call back.
     * On Android 16 (Pixel 7), reads can fail silently without calling .with or .fail,
     * causing the coroutine to hang forever and blocking all BLE traffic.
     *
     * @return true if read succeeded, false if failed, timed out, or characteristic unavailable
     */
    private suspend fun readMonitorCharacteristicSuspend(): Boolean {
        val char = monitorCharacteristic ?: return false

        // CRITICAL: Timeout prevents hanging on Android 16 where BLE callbacks may not fire
        return withTimeoutOrNull(HEARTBEAT_READ_TIMEOUT_MS) {
            // Single-shot read: wait for callback before issuing the next read.
            // Mirrors trainer behavior and prevents queue flooding.
            suspendCancellableCoroutine { cont ->
                var resumed = false
                fun resumeOnce(result: Boolean) {
                    if (!resumed && cont.isActive) {
                        resumed = true
                        cont.resume(result)
                    }
                }

                try {
                    readCharacteristic(char)
                        .with { _, data ->
                            handleMonitorData(data)
                            resumeOnce(true)
                        }
                        .fail { _, status ->
                            Timber.w("⚠️ Monitor read failed (status: $status)")
                            resumeOnce(false)
                        }
                        .enqueue()
                } catch (e: Exception) {
                    Timber.e(e, "❌ Exception enqueueing monitor read")
                    resumeOnce(false)
                }
            }
        } ?: run {
            Timber.w("⚠️ Monitor read timed out (${HEARTBEAT_READ_TIMEOUT_MS}ms) - Android 16 BLE issue?")
            false
        }
    }

    /**
     * Enable monitor data reception for workouts
     *
     * CRITICAL FIX (Android 16 Pixel disconnect issue):
     * Uses SUSPEND-BASED SEQUENTIAL READS matching the trainer approach.
     *
     * OLD APPROACH (BROKEN on Android 16):
     *   readCharacteristic().enqueue()  // Fire and forget!
     *   delay(100)                       // Next read regardless of completion
     *   Floods BLE queue -> supervision timeout -> disconnect
     *
     * NEW APPROACH (matches trainer):
     *   val success = readMonitorCharacteristicSuspend()  // WAITS for completion
     *   // Next read only after this one finishes
     *   Natural rate limiting -> no queue flooding -> stable connection
     *
     * @param forAutoStart If true, enables handle detection with WaitingForRest state (for Just Lift auto-start).
     *                     If false, skips handle state initialization (for active workout monitoring).
     */
    fun startMonitorPolling(forAutoStart: Boolean = false) {
        // Reset position tracking for new workout
        minPositionSeen = Double.MAX_VALUE
        maxPositionSeen = Double.MIN_VALUE

        // Reset velocity tracking for fresh stall detection (Issue #204)
        lastPositionA = 0.0f
        lastPositionB = 0.0f
        lastTimestamp = 0L
        smoothedVelocityA = 0.0
        smoothedVelocityB = 0.0

        // Reset notification counter for this workout session
        val previousCount = monitorNotificationCount
        monitorNotificationCount = 0L
        Timber.i("📊 Monitor notifications reset (previous session: $previousCount notifications)")

        if (forAutoStart) {
            // Start in WaitingForRest state - must see handles at rest (low position) before arming grab detection
            // This prevents immediate auto-start if cables already have tension
            _handleState.value = HandleState.WaitingForRest
            forceAboveGrabThresholdStart = null
            forceBelowReleaseThresholdStart = null
            Timber.d("Monitor data enabled for AUTO-START - waiting for handles at rest (pos < ${HANDLE_REST_THRESHOLD})")
        } else {
            // Active workout - set to Grabbed since workout is already running
            _handleState.value = HandleState.Grabbed
            Timber.d("Monitor data enabled for ACTIVE WORKOUT")
        }

        // Cancel any existing polling job before starting new one
        monitorPollingJob?.cancel()

        // Start sequential polling using suspend-based reads (trainer approach)
        // Each read WAITS for completion before starting the next one.
        // This prevents BLE queue flooding that caused Android 16 disconnects.
        monitorPollingJob = pollingScope.launch {
            Timber.i("🔄 Starting SEQUENTIAL monitor polling (suspend-based, matches trainer)")
            var successfulReads = 0L
            var failedReads = 0L
            var consecutiveFailures = 0

            while (isActive) {
                try {
                    val char = monitorCharacteristic
                    if (char == null) {
                        Timber.w("⚠️ Monitor characteristic is null - cannot poll!")
                        delay(100)
                        continue
                    }

                    // CRITICAL: This SUSPENDS until the read completes (or fails)
                    // No more fire-and-forget that floods the BLE queue!
                    val success = readMonitorCharacteristicSuspend()

                    if (success) {
                        successfulReads++
                        consecutiveFailures = 0
                        // Log periodically to show polling is working
                        if (successfulReads % 100 == 0L) {
                            Timber.d("📊 Monitor poll #$successfulReads (failed: $failedReads)")
                        }
                    } else {
                        failedReads++
                        consecutiveFailures++
                        if (consecutiveFailures % 10 == 0) {
                            Timber.w("⚠️ $consecutiveFailures consecutive monitor read failures")
                        }
                        // Small delay on failure to avoid tight error loop
                        delay(50)
                    }

                    // Natural rate limiting: next read only starts after this one completes
                    // The actual polling rate depends on BLE response time (~10-20ms typically)
                    // This matches trainer behavior - no fixed timer, callback-driven

                } catch (e: Exception) {
                    failedReads++
                    consecutiveFailures++
                    Timber.e(e, "❌ Exception in monitor polling")
                    delay(100)
                }
            }
            Timber.i("📊 Monitor polling stopped (reads: $successfulReads, failures: $failedReads)")
        }
    }

    private suspend fun readDiagnosticCharacteristicSuspend(): Boolean {
        val char = propertyCharacteristic ?: return false

        // CRITICAL: Timeout prevents hanging on Android 16 where BLE callbacks may not fire
        return withTimeoutOrNull(HEARTBEAT_READ_TIMEOUT_MS) {
            suspendCancellableCoroutine { cont ->
                var resumed = false
                fun resumeOnce(result: Boolean) {
                    if (!resumed && cont.isActive) {
                        resumed = true
                        cont.resume(result)
                    }
                }

                try {
                    readCharacteristic(char)
                        .with { _, data ->
                            val bytes = data.value
                            if (bytes != null) {
                                parseDiagnosticData(bytes)
                            }
                            resumeOnce(true)
                        }
                        .fail { _, status ->
                            Timber.w("⚠️ Diagnostic read failed (status: $status)")
                            resumeOnce(false)
                        }
                        .enqueue()
                } catch (e: Exception) {
                    Timber.e(e, "❌ Exception enqueueing diagnostic read")
                    resumeOnce(false)
                }
            }
        } ?: run {
            Timber.w("⚠️ Diagnostic read timed out (${HEARTBEAT_READ_TIMEOUT_MS}ms) - Android 16 BLE issue?")
            false
        }
    }

    /**
     * Start polling diagnostic characteristic every 500ms (keep-alive + health monitoring)
     * Matches trainer interval - Uses suspend-based reads like trainer
     */
    fun startDiagnosticPolling() {
        propertyPollingJob?.cancel()
        propertyPollingJob = pollingScope.launch {
            Timber.d("🔄 Starting SEQUENTIAL diagnostic polling (500ms interval - matches trainer)")
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

                    // CRITICAL: Suspend until read completes (matches trainer)
                    val success = readDiagnosticCharacteristicSuspend()

                    if (success) {
                        successfulReads++
                    } else {
                        failedReads++
                    }

                    // Diagnostic polling has a fixed interval for keep-alive purposes
                    // But we still wait for completion before scheduling next read
                    delay(500) // Poll every 500ms (Official app interval - verified)

                } catch (e: Exception) {
                    failedReads++
                    Timber.e(e, "❌ Exception in diagnostic polling")
                    delay(500)
                }
            }
        }
    }

    /**
     * Heartbeat to satisfy the device watchdog: attempt an RX read, fall back to a benign no-op write.
     */
    private fun startHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = pollingScope.launch {
            Timber.d("Starting BLE heartbeat (interval=${HEARTBEAT_INTERVAL_MS}ms, read timeout=${HEARTBEAT_READ_TIMEOUT_MS}ms)")
            while (isActive) {
                val readSucceeded = try {
                    withTimeoutOrNull(HEARTBEAT_READ_TIMEOUT_MS) {
                        performHeartbeatRead()
                    } ?: false
                } catch (e: Exception) {
                    Timber.e(e, "Heartbeat read attempt crashed")
                    false
                }

                if (!readSucceeded) {
                    sendHeartbeatNoOp()
                }

                delay(HEARTBEAT_INTERVAL_MS)
            }
        }
    }

    private suspend fun performHeartbeatRead(): Boolean {
        val rxChar = nusRxCharacteristic
        if (rxChar == null) {
            Timber.w("Heartbeat read skipped - RX characteristic unavailable")
            return false
        }

        return suspendCancellableCoroutine { cont ->
            var resumed = false
            fun resumeOnce(result: Boolean) {
                if (!resumed && cont.isActive) {
                    resumed = true
                    cont.resume(result)
                }
            }

            try {
                readCharacteristic(rxChar)
                    .with { _, _ ->
                        Timber.v("Heartbeat read callback fired")
                        resumeOnce(true)
                    }
                    .fail { _, status ->
                        Timber.w("Heartbeat read failed (status: $status)")
                        resumeOnce(false)
                    }
                    .enqueue()
            } catch (e: Exception) {
                Timber.e(e, "Heartbeat read enqueue failed")
                resumeOnce(false)
            }
        }
    }

    private fun sendHeartbeatNoOp() {
        val rxChar = nusRxCharacteristic
        if (rxChar == null) {
            Timber.w("Heartbeat write skipped - RX characteristic unavailable")
            return
        }

        try {
            writeCharacteristic(rxChar, HEARTBEAT_NO_OP, BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE)
                .fail { _, status -> Timber.w("Heartbeat no-op write failed (status: $status)") }
                .enqueue()
        } catch (e: Exception) {
            Timber.e(e, "Heartbeat no-op write enqueue failed")
        }
    }

    /**
     * Suspend function to read heuristic characteristic and wait for completion.
     *
     * @return true if read succeeded, false if failed or characteristic unavailable
     */
    private suspend fun readHeuristicCharacteristicSuspend(): Boolean {
        val char = heuristicCharacteristic ?: return false

        return suspendCancellableCoroutine { cont ->
            var resumed = false
            fun resumeOnce(result: Boolean) {
                if (!resumed && cont.isActive) {
                    resumed = true
                    cont.resume(result)
                }
            }

            try {
                readCharacteristic(char)
                    .with { _, data ->
                        Timber.v("Heuristic data received: ${data.value?.size ?: 0} bytes")
                        // TODO: Parse heuristic data if needed for phase statistics
                        resumeOnce(true)
                    }
                    .fail { _, status ->
                        Timber.w("⚠️ Heuristic read failed (status: $status)")
                        resumeOnce(false)
                    }
                    .enqueue()
            } catch (e: Exception) {
                Timber.e(e, "❌ Exception enqueueing heuristic read")
                resumeOnce(false)
            }
        }
    }

    /**
     * Start polling heuristic characteristic every 250ms (4Hz) - matching trainer.
     * Provides phase statistics for concentric/eccentric analysis.
     * Uses suspend-based reads to prevent BLE queue flooding.
     */
    fun startHeuristicPolling() {
        if (heuristicPollingJob?.isActive == true) return

        heuristicPollingJob = pollingScope.launch {
            Timber.d("Starting SEQUENTIAL heuristic polling (250ms interval / 4Hz - matching trainer)")
            while (isActive) {
                try {
                    val char = heuristicCharacteristic
                    if (char == null) {
                        delay(250)
                        continue
                    }

                    // CRITICAL: Suspend until read completes (matches trainer)
                    readHeuristicCharacteristicSuspend()

                    delay(250) // Poll every 250ms (4Hz) - matching trainer
                } catch (e: Exception) {
                    Timber.e(e, "Error in heuristic polling")
                    delay(250)
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

    @Suppress("unused")  // Available for future heuristic analysis features
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

    /**
     * Stop all polling (matching trainer)
     */
    fun stopPolling() {
        val timestamp = System.currentTimeMillis()
        Timber.d("STOP_DEBUG: [$timestamp] stopPolling() called")

        // Log analysis from workout (position range for diagnostics)
        if (minPositionSeen != Double.MAX_VALUE && maxPositionSeen != Double.MIN_VALUE) {
            Timber.i("========== WORKOUT ANALYSIS ==========")
            Timber.i("Position range: min=$minPositionSeen, max=$maxPositionSeen")
            Timber.i("v0.5.1-beta detection thresholds:")
            Timber.i("  Handle grab: pos > $HANDLE_GRABBED_THRESHOLD + velocity > $VELOCITY_THRESHOLD")
            Timber.i("  Handle release: pos < $HANDLE_REST_THRESHOLD")
            Timber.i("======================================")
        }

        val monitorJobState = monitorPollingJob?.run { "Active=${isActive}, Cancelled=${isCancelled}, Completed=${isCompleted}" } ?: "NULL"
        val propertyJobState = propertyPollingJob?.run { "Active=${isActive}, Cancelled=${isCancelled}, Completed=${isCompleted}" } ?: "NULL"
        val heuristicJobState = heuristicPollingJob?.run { "Active=${isActive}, Cancelled=${isCancelled}, Completed=${isCompleted}" } ?: "NULL"
        val heartbeatJobState = heartbeatJob?.run { "Active=${isActive}, Cancelled=${isCancelled}, Completed=${isCompleted}" } ?: "NULL"

        Timber.d("STOP_DEBUG: Monitor polling job state BEFORE cancel: $monitorJobState")
        Timber.d("STOP_DEBUG: Property polling job state BEFORE cancel: $propertyJobState")
        Timber.d("STOP_DEBUG: Heuristic polling job state BEFORE cancel: $heuristicJobState")
        Timber.d("STOP_DEBUG: Heartbeat job state BEFORE cancel: $heartbeatJobState")

        monitorPollingJob?.cancel()
        propertyPollingJob?.cancel()
        heuristicPollingJob?.cancel()
        heartbeatJob?.cancel()

        monitorPollingJob = null
        propertyPollingJob = null
        heuristicPollingJob = null
        heartbeatJob = null

        // NOTE: Do NOT reset handle state here - it breaks Just Lift auto-start
        // Handle state should only be reset by enableJustLiftWaitingMode() or startMonitorPolling()

        val afterCancel = System.currentTimeMillis()
        Timber.d("STOP_DEBUG: [$afterCancel] Jobs cancelled (took ${afterCancel - timestamp}ms)")
    }

    /**
     * Enable Just Lift waiting mode - call this after workout completion
     * to start watching for force spike indicating user grabbed handles for next exercise.
     * (Matching trainer force-based detection)
     */
    fun enableJustLiftWaitingMode() {
        Timber.i("Enabling Just Lift waiting mode - v0.5.1-beta detection (grab: pos>${HANDLE_GRABBED_THRESHOLD}+vel>${VELOCITY_THRESHOLD}, release: pos<${HANDLE_REST_THRESHOLD})")
        // Reset position tracking for diagnostics
        minPositionSeen = Double.MAX_VALUE
        maxPositionSeen = Double.MIN_VALUE
        // Reset grab/release timers
        forceAboveGrabThresholdStart = null
        forceBelowReleaseThresholdStart = null
        // Start in WaitingForRest state - must see handles at rest (low position) before arming grab detection
        _handleState.value = HandleState.WaitingForRest
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
                // CRITICAL: Use WRITE_TYPE_NO_RESPONSE (Write Command 0x52) like trainer
                // This is fire-and-forget - no waiting for acknowledgment
                // Write Request (0x12) would add round-trip latency waiting for Write Response
                writeCharacteristic(characteristic, data, BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE)
                    // REMOVED .split() - Vitruvian protocol requires exact frame sizes!
                    // .split() was breaking 96-byte program params into chunks
                    .enqueue()

                val afterWrite = System.currentTimeMillis()
                Timber.d("STOP_DEBUG: [$afterWrite] Write enqueued (took ${afterWrite - beforeWrite}ms)")
                Timber.d("STOP_DEBUG: === COMMAND SENT ===")
                Result.success(Unit)
            } ?: Result.failure(Exception("NUS RX characteristic not available"))
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
                return@withContext Result.failure(Exception("No workout command characteristics available"))
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
            Timber.d("PROGRAM frame (first 32 bytes): ${programFrame.take(32).joinToString(" ") { b -> String.format("%02X", b) }}")

            workoutCmdCharacteristics.forEachIndexed { index, char ->
                Timber.d("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                Timber.d("Testing characteristic ${index + 1}/${workoutCmdCharacteristics.size}")
                Timber.d("UUID: ${char.uuid}")
                Timber.d("Properties: ${char.properties}")
                Timber.d("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")

                // Send PROGRAM frame (96 bytes)
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
     * Validate a monitor sample.
     * Filters out invalid position values and optionally large position jumps.
     *
     * Position validation per trainer:
     * - Valid range: -1000.0 to +1000.0 mm (MIN_POSITION to MAX_POSITION)
     * - Position values are now Float in mm (scaled by /10 at parse time)
     */
    private fun validateSample(posA: Float, loadA: Float, posB: Float, loadB: Float): Boolean {
        // Official app range: -1000 to +1000 mm
        if (posA !in WorkoutConstants.MIN_POSITION..WorkoutConstants.MAX_POSITION ||
            posB !in WorkoutConstants.MIN_POSITION..WorkoutConstants.MAX_POSITION) {
            Timber.w("Position out of range: posA=$posA, posB=$posB (valid: ${WorkoutConstants.MIN_POSITION} to ${WorkoutConstants.MAX_POSITION})")
            return false
        }

        // Strict validation checks position jumps (when enabled)
        // Threshold is 20mm (was 200 raw units = 20mm after /10 scaling)
        if (strictValidationEnabled) {
            val deltaA = kotlin.math.abs(posA - lastPositionA)
            val deltaB = kotlin.math.abs(posB - lastPositionB)
            if (deltaA > 20f || deltaB > 20f) {
                Timber.w("Position jump detected: deltaA=$deltaA, deltaB=$deltaB")
                return false
            }
        }

        return true
    }

    /**
     * Analyze handle state using v0.5.1-beta POSITION+VELOCITY detection.
     * This is the proven working approach from the last good release.
     *
     * Thresholds (from v0.5.1-beta):
     * - HANDLE_GRABBED_THRESHOLD = 8.0 (position > 8 = grabbed)
     * - HANDLE_REST_THRESHOLD = 2.5 (position < 2.5 = at rest)
     * - VELOCITY_THRESHOLD = 100.0 (velocity > 100 = significant movement)
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
        // NOTE: Use abs(velocity) since velocity is now signed (Issue #204 fix)
        val handleAGrabbed = posA > HANDLE_GRABBED_THRESHOLD
        val handleBGrabbed = posB > HANDLE_GRABBED_THRESHOLD
        val handleAMoving = kotlin.math.abs(velocityA) > VELOCITY_THRESHOLD
        val handleBMoving = kotlin.math.abs(velocityB) > VELOCITY_THRESHOLD

        // Simple hysteresis with velocity check (v0.5.1-beta approach)
        return when (currentState) {
            HandleState.WaitingForRest -> {
                // Must see handles at rest before arming grab detection
                if (posA < HANDLE_REST_THRESHOLD && posB < HANDLE_REST_THRESHOLD) {
                    Timber.d("Handles at REST (posA=$posA, posB=$posB < $HANDLE_REST_THRESHOLD) - auto-start now ARMED")
                    HandleState.Released
                } else {
                    HandleState.WaitingForRest
                }
            }
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
                    // Log why we are NOT releasing if close to threshold
                    if (posA < 10.0 || posB < 10.0) {
                        Timber.v("Still GRABBED (holding): posA=$posA, posB=$posB, thresh=$HANDLE_REST_THRESHOLD")
                    }
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

            // BLE packet parsing - trainer format
            // Format: u16[0-1]=ticks_lo, u16[2-3]=ticks_hi, s16[4-5]=posA*10, u16[8-9]=loadA*100,
            //         s16[10-11]=posB*10, u16[14-15]=loadB*100
            val ticksLo = buffer.getShort(0).toInt() and 0xFFFF    // Offset 0-1 (unsigned)
            val ticksHi = buffer.getShort(2).toInt() and 0xFFFF    // Offset 2-3 (unsigned)
            val posARaw = buffer.getShort(4)                       // Offset 4-5 (SIGNED - position)
            val loadARaw = buffer.getShort(8).toInt() and 0xFFFF   // Offset 8-9 (unsigned - load*100)
            val posBRaw = buffer.getShort(10)                      // Offset 10-11 (SIGNED - position)
            val loadBRaw = buffer.getShort(14).toInt() and 0xFFFF  // Offset 14-15 (unsigned - load*100)

            // Reconstruct 32-bit tick counter
            val ticks = ticksLo + (ticksHi shl 16)

            // Position values - signed 16-bit with 0.1mm resolution, scale to mm
            // Per trainer: position = buffer.getShort() / 10.0
            // Valid range after scaling: -1000.0 to +1000.0 mm
            var positionA = posARaw / 10.0f
            var positionB = posBRaw / 10.0f

            // Validate position range and use last good value if invalid
            if (positionA !in WorkoutConstants.MIN_POSITION..WorkoutConstants.MAX_POSITION) {
                Timber.w("Position A out of range: $positionA, using last good: $lastGoodPosA")
                positionA = lastGoodPosA
            } else {
                lastGoodPosA = positionA
            }
            if (positionB !in WorkoutConstants.MIN_POSITION..WorkoutConstants.MAX_POSITION) {
                Timber.w("Position B out of range: $positionB, using last good: $lastGoodPosB")
                positionB = lastGoodPosB
            } else {
                lastGoodPosB = positionB
            }

            // Load in kg (device sends kg * 100)
            val loadA = loadARaw / 100.0f
            val loadB = loadBRaw / 100.0f

            // Status (Bytes 16-17) if available
            var status = 0
            if (bytes.size >= 18) {
                status = buffer.getShort(16).toInt() and 0xFFFF
            }

            Timber.v("Parsed ${bytes.size}-byte format: posA=$positionA, posB=$positionB, loadA=$loadA")

            // Log status flags for diagnostics (but do NOT early return - breaks handle detection)
            // The status byte contains safety flags but they don't prevent normal operation
            if (status != 0) {
                val isDeloadOccurred = (status and 0x8000) != 0
                val isDeloadWarn = (status and 0x0040) != 0
                val isSpotterActive = (status and 0x0020) != 0

                if (isDeloadOccurred) {
                    Timber.w("MACHINE STATUS: DELOAD_OCCURRED flag set - Status: 0x${status.toString(16)}")
                    // NOTE: Do NOT return early or emit error - this breaks Just Lift handle detection
                    // The flag is informational; machine continues to operate normally

                    // Emit deload event so repository can send STOP to clear the machine's fault state
                    // This is CRITICAL for Just Lift mode - the machine ignores commands while in fault state
                    // Sending STOP acknowledges the fault and allows new commands to be accepted
                    val now = System.currentTimeMillis()
                    if (now - lastDeloadEventTime > DELOAD_EVENT_DEBOUNCE_MS) {
                        lastDeloadEventTime = now
                        pollingScope.launch {
                            Timber.d("DELOAD_OCCURRED: Emitting event for repository to send STOP")
                            _deloadOccurredEvents.emit(Unit)
                        }
                    }
                }
                if (isDeloadWarn) {
                    Timber.w("MACHINE STATUS: DELOAD_WARN - Status: 0x${status.toString(16)}")
                }
                if (isSpotterActive) {
                    Timber.d("MACHINE STATUS: SPOTTER_ACTIVE - Status: 0x${status.toString(16)}")
                }
            }

            // Validate sample (matching trainer)
            if (!validateSample(positionA, loadA, positionB, loadB)) {
                return
            }

            // Calculate velocity from position delta (mm/s)
            // NOTE: Use SIGNED velocity for EMA smoothing (Issue #204 fix)
            // When bar oscillates due to sensor noise (+2mm, -3mm, +1mm...), signed velocities
            // average toward zero. Using abs() would make all jitter positive, averaging to ~20mm/s.
            val currentTime = System.currentTimeMillis()
            val rawVelocityA = if (lastTimestamp > 0L) {
                val deltaTime = (currentTime - lastTimestamp) / 1000.0
                val deltaPos = positionA - lastPositionA
                if (deltaTime > 0) deltaPos / deltaTime else 0.0
            } else 0.0

            val rawVelocityB = if (lastTimestamp > 0L) {
                val deltaTime = (currentTime - lastTimestamp) / 1000.0
                val deltaPos = positionB - lastPositionB
                if (deltaTime > 0) deltaPos / deltaTime else 0.0
            } else 0.0

            // Apply Exponential Moving Average (EMA) smoothing to filter sensor noise (Issue #204)
            // This prevents false movement detection from position jitter when bar is stationary
            smoothedVelocityA = VELOCITY_SMOOTHING_ALPHA * rawVelocityA + (1 - VELOCITY_SMOOTHING_ALPHA) * smoothedVelocityA
            smoothedVelocityB = VELOCITY_SMOOTHING_ALPHA * rawVelocityB + (1 - VELOCITY_SMOOTHING_ALPHA) * smoothedVelocityB

            // Use smoothed velocity for WorkoutMetric (used by stall detection)
            val velocityA = smoothedVelocityA
            val velocityB = smoothedVelocityB

            lastPositionA = positionA
            lastPositionB = positionB
            lastTimestamp = currentTime

            // Debug logging (sampled to reduce spam)
            if (ticks < 100 || ticks % 500 == 0) {
                Timber.d("=== SAMPLE DATA (${bytes.size} bytes) ===")
                Timber.d("Position: A=$positionA, B=$positionB")
                Timber.d("Velocity: raw=(${String.format("%.1f", rawVelocityA)}, ${String.format("%.1f", rawVelocityB)}) " +
                    "smoothed=(${String.format("%.1f", velocityA)}, ${String.format("%.1f", velocityB)}) mm/s")
                Timber.d("Force: A=$loadA, B=$loadB, Total=${loadA + loadB}")
                Timber.d("Ticks: $ticks, Status: 0x${status.toString(16)}")
                Timber.d("================================")
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
                status = status
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

            // Analyze and update handle state (v0.5.1-beta position+velocity approach)
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
     *
     * Supports TWO packet formats for backwards compatibility (Issue #187):
     *
     * LEGACY FORMAT (6+ bytes, used in Beta 4):
     * - Bytes 0-1: topCounter (u16) - concentric completions
     * - Bytes 2-3: (unused)
     * - Bytes 4-5: completeCounter (u16) - eccentric completions
     *
     * OFFICIAL APP FORMAT (24 bytes, Little Endian):
     * - Bytes 0-3:   up (Int/u32) - up counter (concentric completions)
     * - Bytes 4-7:   down (Int/u32) - down counter (eccentric completions)
     * - Bytes 8-11:  rangeTop (Float) - maximum ROM boundary
     * - Bytes 12-15: rangeBottom (Float) - minimum ROM boundary
     * - Bytes 16-17: repsRomCount (Short/u16) - Warmup reps with proper ROM
     * - Bytes 18-19: repsRomTotal (Short/u16) - Total reps regardless of ROM
     * - Bytes 20-21: repsSetCount (Short/u16) - WORKING SET REP COUNT
     * - Bytes 22-23: repsSetTotal (Short/u16) - Total reps in set
     */
    private fun handleRepNotification(data: Data) {
        try {
            val bytes = data.value ?: return

            if (bytes.size < 6) {
                Timber.w("Rep notification too short: ${bytes.size} bytes (minimum 6)")
                return
            }

            val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)

            val repData: RepNotification

            if (bytes.size >= 24) {
                // FULL 24-byte packet - parse all fields
                val upCounter = buffer.getInt(0)
                val downCounter = buffer.getInt(4)
                val rangeTop = buffer.getFloat(8)
                val rangeBottom = buffer.getFloat(12)
                val repsRomCount = buffer.getShort(16).toInt() and 0xFFFF
                val repsRomTotal = buffer.getShort(18).toInt() and 0xFFFF
                val repsSetCount = buffer.getShort(20).toInt() and 0xFFFF
                val repsSetTotal = buffer.getShort(22).toInt() and 0xFFFF

                Timber.d("Rep notification (24-byte format):")
                Timber.d("  up=$upCounter, down=$downCounter")
                Timber.d("  repsRomCount=$repsRomCount (warmup), repsSetCount=$repsSetCount (working)")
                Timber.d("  hex=${bytes.joinToString(" ") { "%02X".format(it) }}")

                repData = RepNotification(
                    topCounter = upCounter,
                    completeCounter = downCounter,
                    repsRomCount = repsRomCount,
                    repsSetCount = repsSetCount,
                    rangeTop = rangeTop,
                    rangeBottom = rangeBottom,
                    rawData = bytes,
                    timestamp = System.currentTimeMillis(),
                    isLegacyFormat = false
                )
            } else {
                // LEGACY 6-byte packet (Beta 4 format) - parse u16 counters
                // This handles Samsung devices and older firmware that may send shorter packets
                val topCounter = buffer.getShort(0).toInt() and 0xFFFF
                val completeCounter = buffer.getShort(4).toInt() and 0xFFFF

                Timber.w("Rep notification (LEGACY 6-byte format - Issue #187 fallback):")
                Timber.w("  top=$topCounter, complete=$completeCounter")
                Timber.w("  hex=${bytes.joinToString(" ") { "%02X".format(it) }}")

                repData = RepNotification(
                    topCounter = topCounter,
                    completeCounter = completeCounter,
                    repsRomCount = 0,  // Not available in legacy format
                    repsSetCount = 0,  // Not available in legacy format
                    rangeTop = 0f,
                    rangeBottom = 0f,
                    rawData = bytes,
                    timestamp = System.currentTimeMillis(),
                    isLegacyFormat = true
                )
            }

            val emitted = _repEvents.tryEmit(repData)
            Timber.d("🔥 Emitted rep event: success=$emitted, subscribers=${_repEvents.subscriptionCount.value}, legacy=${repData.isLegacyFormat}")
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
        pollingScope.coroutineContext[Job]?.cancel()
    }

}

/**
 * Connection status sealed class
 */
sealed class ConnectionStatus {
    object Disconnected : ConnectionStatus()
    object Ready : ConnectionStatus()
    data class Error(val message: String) : ConnectionStatus()
}

enum class HandleState {
    WaitingForRest,  // Initial state - waiting for handles to be at rest before arming grab detection
    Released,        // Handles at rest - armed for grab detection
    Grabbed,         // Handles grabbed - force > 3kg sustained
    Moving           // Handles in motion
}

/**
 * Rep notification data class
 * Parsed from device notifications on characteristic 8308f2a6-0875-4a94-a86f-5c5c5e1b068a
 *
 * Supports TWO formats (Issue #187):
 *
 * LEGACY FORMAT (6 bytes, Beta 4 compatible):
 * - Uses topCounter/completeCounter for rep counting
 * - isLegacyFormat = true
 *
 * OFFICIAL APP FORMAT (24 bytes):
 * - topCounter (u32): Concentric/up phase completions
 * - completeCounter (u32): Eccentric/down phase completions
 * - rangeTop (float): Maximum ROM boundary
 * - rangeBottom (float): Minimum ROM boundary
 * - repsRomCount (u16): Warmup reps with proper ROM - USE FOR WARMUP DISPLAY
 * - repsRomTotal (u16): Total reps regardless of ROM
 * - repsSetCount (u16): Working set rep count - USE FOR WORKING REPS DISPLAY
 * - repsSetTotal (u16): Total reps in set
 * - isLegacyFormat = false
 */
data class RepNotification(
    val topCounter: Int,        // u32: Concentric completions (up counter)
    val completeCounter: Int,   // u32: Eccentric completions (down counter)
    val repsRomCount: Int = 0,  // u16: Warmup reps (proper ROM) - DISPLAY THIS FOR WARMUP
    val repsSetCount: Int = 0,  // u16: Working set reps - DISPLAY THIS FOR WORKING
    val rangeTop: Float = 0f,   // ROM max boundary
    val rangeBottom: Float = 0f, // ROM min boundary
    val rawData: ByteArray,
    val timestamp: Long,
    val isLegacyFormat: Boolean = false  // True if parsed from 6-byte legacy packet (Issue #187)
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as RepNotification

        if (topCounter != other.topCounter) return false
        if (completeCounter != other.completeCounter) return false
        if (repsRomCount != other.repsRomCount) return false
        if (repsSetCount != other.repsSetCount) return false
        if (rangeTop != other.rangeTop) return false
        if (rangeBottom != other.rangeBottom) return false
        if (!rawData.contentEquals(other.rawData)) return false
        if (timestamp != other.timestamp) return false
        if (isLegacyFormat != other.isLegacyFormat) return false

        return true
    }

    override fun hashCode(): Int {
        var result = topCounter
        result = 31 * result + completeCounter
        result = 31 * result + repsRomCount
        result = 31 * result + repsSetCount
        result = 31 * result + rangeTop.hashCode()
        result = 31 * result + rangeBottom.hashCode()
        result = 31 * result + rawData.contentHashCode()
        result = 31 * result + timestamp.hashCode()
        result = 31 * result + isLegacyFormat.hashCode()
        return result
    }
}

/**
 * Request for auto-reconnection after BLE stack issues
 * Emitted when onServicesInvalidated() is called (Android 16 Pixel BLE bug)
 */
data class ReconnectionRequest(
    val deviceName: String?,
    val deviceAddress: String,
    val reason: String,
    val timestamp: Long
)
