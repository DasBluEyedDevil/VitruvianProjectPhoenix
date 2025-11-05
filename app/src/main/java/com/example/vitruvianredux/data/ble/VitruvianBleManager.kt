package com.example.vitruvianredux.data.ble

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.content.Context
import com.example.vitruvianredux.domain.model.WorkoutMetric
import com.example.vitruvianredux.util.BleConstants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import no.nordicsemi.android.ble.BleManager
import no.nordicsemi.android.ble.data.Data
import timber.log.Timber
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Vitruvian BLE Manager - Handles BLE communication with Vitruvian device
 * Uses Nordic BLE Library for robust BLE operations
 */
@OptIn(ExperimentalStdlibApi::class)
class VitruvianBleManager(
    context: Context,
    private val connectionLogger: com.example.vitruvianredux.data.logger.ConnectionLogger? = null
) : BleManager(context) {

    private var currentDeviceName: String? = null
    private var currentDeviceAddress: String? = null

    fun setDeviceInfo(name: String?, address: String?) {
        currentDeviceName = name
        currentDeviceAddress = address
    }

    // GATT characteristics
    private var nusRxCharacteristic: BluetoothGattCharacteristic? = null
    private var monitorCharacteristic: BluetoothGattCharacteristic? = null
    private var propertyCharacteristic: BluetoothGattCharacteristic? = null
    private var repNotifyCharacteristic: BluetoothGattCharacteristic? = null

    // Official app workout command characteristics (for testing)
    private val workoutCmdCharacteristics = mutableListOf<BluetoothGattCharacteristic>()

    // Monitor polling - MUST be on Main dispatcher for Nordic BLE library
    private val pollingScope = CoroutineScope(Dispatchers.Main)
    private var monitorPollingJob: Job? = null
    private var propertyPollingJob: Job? = null
    
    // Last good positions for filtering spikes
    private var lastGoodPosA = 0
    private var lastGoodPosB = 0

    // State flows
    private val _connectionState = MutableStateFlow<ConnectionStatus>(ConnectionStatus.Disconnected)
    val connectionState: StateFlow<ConnectionStatus> = _connectionState.asStateFlow()

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

    private val _statusMessages = MutableSharedFlow<String>(replay = 1)
    val statusMessages: SharedFlow<String> = _statusMessages.asSharedFlow()

    private val _handleState = MutableStateFlow<HandleState>(HandleState.Released)
    val handleState: StateFlow<HandleState> = _handleState.asStateFlow()

    // Baseline position tracking for handle grab detection
    private var baselinePositionA: Int? = null
    private var baselinePositionB: Int? = null
    private var baselineSampleCount = 0
    private val BASELINE_SAMPLES_NEEDED = 10 // Average first 10 samples to establish baseline
    private val GRAB_THRESHOLD = 100 // Position must increase by 100+ units from baseline to be "grabbed"

    override fun log(priority: Int, message: String) {
        Timber.tag("VitruvianBLE").log(priority, message)
    }

    override fun getMinLogPriority(): Int {
        return android.util.Log.DEBUG
    }

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

            // Get the NUS service
            val nusService = gatt.getService(BleConstants.NUS_SERVICE_UUID)
            if (nusService == null) {
                Timber.e("NUS service not found")
                return false
            }

            // Get required characteristics
            nusRxCharacteristic = nusService.getCharacteristic(BleConstants.NUS_RX_CHAR_UUID)
            monitorCharacteristic = nusService.getCharacteristic(BleConstants.MONITOR_CHAR_UUID)
            propertyCharacteristic = nusService.getCharacteristic(BleConstants.PROPERTY_CHAR_UUID)
            repNotifyCharacteristic = nusService.getCharacteristic(BleConstants.REP_NOTIFY_CHAR_UUID)

            Timber.d("Found characteristics in NUS service: RX=${nusRxCharacteristic != null}, Monitor=${monitorCharacteristic != null}, Property=${propertyCharacteristic != null}, RepNotify=${repNotifyCharacteristic != null}")

            // If rep notify not in NUS service, search all services
            if (repNotifyCharacteristic == null) {
                Timber.w("Rep notify characteristic not found in NUS service, searching all services...")
                gatt.services.forEach { service ->
                    val found = service.getCharacteristic(BleConstants.REP_NOTIFY_CHAR_UUID)
                    if (found != null) {
                        repNotifyCharacteristic = found
                        Timber.d("Found rep notify characteristic in service: ${service.uuid}")
                        return@forEach
                    }
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

        @Deprecated("Using deprecated Nordic BLE API")
        override fun onServicesInvalidated() {
            nusRxCharacteristic = null
            monitorCharacteristic = null
            propertyCharacteristic = null
            repNotifyCharacteristic = null
        }

        @Deprecated("Using deprecated Nordic BLE API")
        @Suppress("DEPRECATION")
        override fun initialize() {
            super.initialize()

            // REQUEST MTU FIRST - Critical for large frames (96 bytes)!
            // Default MTU is 23 bytes, we need at least 100 bytes for program params
            requestMtu(247)
                .with { device, mtu ->
                    Timber.d("MTU successfully changed to $mtu bytes")
                }
                .fail { device, status ->
                    Timber.e("MTU request failed with status: $status (continuing anyway)")
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
                    // Generic handler for other notifications (just log them)
                    setNotificationCallback(characteristic).with { _, data ->
                        Timber.d("[notify ${characteristic.uuid}] ${data.value?.size ?: 0} bytes")
                    }
                }

                enableNotifications(characteristic)
                    .done { device ->
                        Timber.d("    -> Notifications active on ${characteristic.uuid}")
                    }
                    .fail { device, status ->
                        Timber.w("    -> Failed to enable notifications on ${characteristic.uuid}: status=$status")
                    }
                    .enqueue()
            }

            _connectionState.value = ConnectionStatus.Ready
            Timber.d("Core notifications enabled! Device ready.")

            // Start property polling immediately to keep machine alive (keep-alive mechanism)
            // The official app/web app does this - property polling at 500ms intervals
            // Monitor polling (100ms) only starts when workout begins
            Timber.d("Starting keep-alive property polling (500ms)...")
            startPropertyPolling()
        }
    }
    
    /**
     * Start polling monitor characteristic every 100ms
     * This is how the official app reads position/force data
     * Called when workout starts
     */
    fun startMonitorPolling() {
        // Reset baseline when starting monitor polling for auto-start detection
        resetHandleBaseline()
        
        monitorPollingJob?.cancel()
        monitorPollingJob = pollingScope.launch {
            Timber.d("Starting monitor polling (100ms interval)")
            while (isActive) {
                try {
                    monitorCharacteristic?.let { char ->
                        // MUST use .with() and .enqueue() together
                        readCharacteristic(char)
                            .with { device, data ->
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
     * Start polling property characteristic every 500ms
     * Called when workout starts  
     */
    fun startPropertyPolling() {
        propertyPollingJob?.cancel()
        propertyPollingJob = pollingScope.launch {
            Timber.d("Starting property polling (500ms interval)")
            while (isActive) {
                try {
                    propertyCharacteristic?.let { char ->
                        readCharacteristic(char)
                            .with { device, data ->
                                Timber.v("Property data: ${data.value?.toHexString()}")
                            }
                            .enqueue()
                    }
                    delay(500) // Poll every 500ms (matches web app)
                } catch (e: Exception) {
                    Timber.e(e, "Error in property polling")
                }
            }
        }
    }
    
    /**
     * Stop all polling
     */
    fun stopPolling() {
        Timber.d("Stopping all polling")
        monitorPollingJob?.cancel()
        propertyPollingJob?.cancel()
    }

    /**
     * Send a command to the device
     * CRITICAL: Do NOT use .split() - frames must be sent whole!
     */
    @Suppress("DEPRECATION")
    fun sendCommand(data: ByteArray): Result<Unit> {
        return try {
            nusRxCharacteristic?.let { characteristic ->
                // Log detailed hex dump for debugging
                Timber.d("=== SENDING COMMAND ===")
                Timber.d("Size: ${data.size} bytes")
                Timber.d("Full hex: ${data.toHexString()}")

                // Show first 64 bytes formatted for easy reading
                if (data.size > 0) {
                    val preview = data.take(64)
                    val formatted = preview.chunked(16) { bytes ->
                        bytes.joinToString(" ") { "%02x".format(it) }
                    }.joinToString("\n  ")
                    Timber.d("First ${preview.size} bytes:\n  $formatted")
                }

                writeCharacteristic(characteristic, data)
                    // REMOVED .split() - Vitruvian protocol requires exact frame sizes!
                    // .split() was breaking 96-byte program params into chunks
                    .enqueue()

                Timber.d("=== COMMAND SENT ===")
                Result.success(Unit)
            } ?: Result.failure(Exception("NUS RX characteristic not available"))
        } catch (e: Exception) {
            Timber.e(e, "Failed to send command")
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
     * Handle monitor data notifications (real-time metrics)
     */
    private fun analyzeHandleState(metric: WorkoutMetric): HandleState {
        // First, establish baseline position (resting position when handles hang freely)
        if (baselineSampleCount < BASELINE_SAMPLES_NEEDED) {
            if (baselinePositionA == null) {
                baselinePositionA = metric.positionA
                baselinePositionB = metric.positionB
            } else {
                // Running average
                baselinePositionA = ((baselinePositionA!! * baselineSampleCount) + metric.positionA) / (baselineSampleCount + 1)
                baselinePositionB = ((baselinePositionB!! * baselineSampleCount) + metric.positionB) / (baselineSampleCount + 1)
            }
            baselineSampleCount++
            Timber.d("Establishing baseline: sample $baselineSampleCount/$BASELINE_SAMPLES_NEEDED, baselineA=$baselinePositionA, baselineB=$baselinePositionB")
            return HandleState.Released
        }

        // After baseline is established, detect if handles are lifted significantly above baseline
        val deltaA = metric.positionA - (baselinePositionA ?: 0)
        val deltaB = metric.positionB - (baselinePositionB ?: 0)
        val isGrabbed = deltaA > GRAB_THRESHOLD || deltaB > GRAB_THRESHOLD

        Timber.v("Handle state: posA=${metric.positionA} (Δ$deltaA), posB=${metric.positionB} (Δ$deltaB), grabbed=$isGrabbed")

        // Log to ConnectionLogger when state changes or periodically
        if (System.currentTimeMillis() % 50 == 0L || isGrabbed != (_handleState.value == HandleState.Grabbed)) {
            connectionLogger?.logHandleDetection(
                currentDeviceName,
                currentDeviceAddress,
                baselinePositionA,
                baselinePositionB,
                metric.positionA,
                metric.positionB,
                deltaA,
                deltaB,
                GRAB_THRESHOLD,
                isGrabbed
            )
        }

        return when {
            isGrabbed -> HandleState.Grabbed
            else -> HandleState.Released
        }
    }
    
    /**
     * Reset baseline tracking (call when disconnecting or starting fresh)
     */
    fun resetHandleBaseline() {
        baselinePositionA = null
        baselinePositionB = null
        baselineSampleCount = 0
        _handleState.value = HandleState.Released
        Timber.d("Handle baseline reset")
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
                Timber.d("Ticks: $ticks")
                Timber.d("==========================")
            }

            val metric = WorkoutMetric(
                timestamp = System.currentTimeMillis(),
                loadA = loadA,
                loadB = loadB,
                positionA = positionA,
                positionB = positionB,
                ticks = ticks
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
     * Based on reference web app: parses u16 array with top counter and complete counter
     * u16[0] = top counter (reached top of range)
     * u16[2] = complete counter (rep complete at bottom)
     */
    private fun handleRepNotification(data: Data) {
        try {
            val bytes = data.value ?: return
            
            if (bytes.size < 6) {
                Timber.w("Rep notification too short: ${bytes.size} bytes")
                return
            }

            // Parse as u16 little-endian array
            val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            val topCounter = buffer.getShort(0).toInt() and 0xFFFF
            val completeCounter = buffer.getShort(4).toInt() and 0xFFFF
            
            Timber.d("Rep notification: top=$topCounter, complete=$completeCounter, hex=${bytes.toHexString()}")

            val repData = RepNotification(
                topCounter = topCounter,
                completeCounter = completeCounter,
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
     * Helper function to convert Data to hex string
     */
    private fun Data.toHexString(): String {
        return value?.joinToString(" ") { "%02X".format(it) } ?: "null"
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
    Released,
    Grabbed,
    Moving
}

/**
 * Rep notification data class
 * Parsed from device notifications on characteristic 0x0036
 * Format: u16 array with [topCounter, ?, completeCounter, ...]
 */
data class RepNotification(
    val topCounter: Int,        // Counter increments when reaching top of range
    val completeCounter: Int,   // Counter increments when rep completes (bottom)
    val rawData: ByteArray,
    val timestamp: Long
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as RepNotification

        if (topCounter != other.topCounter) return false
        if (completeCounter != other.completeCounter) return false
        if (!rawData.contentEquals(other.rawData)) return false
        if (timestamp != other.timestamp) return false

        return true
    }

    override fun hashCode(): Int {
        var result = topCounter
        result = 31 * result + completeCounter
        result = 31 * result + rawData.contentHashCode()
        result = 31 * result + timestamp.hashCode()
        return result
    }
}

