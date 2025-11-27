package com.example.vitruvianredux.data.ble

import com.example.vitruvianredux.domain.model.DiagnosticDetails
import com.example.vitruvianredux.domain.model.HeuristicPhaseStatistics
import com.example.vitruvianredux.domain.model.HeuristicStatistics
import com.example.vitruvianredux.domain.model.WorkoutMetric
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Kable-based BLE Manager for Vitruvian Trainer.
 * Provides the same API as VitruvianBleManager but uses Kable internally.
 *
 * This is NOT a @Singleton - created by the repository when needed.
 */
class KableBleManager {

    companion object {
        private const val TAG = "KableBleManager"
        private const val DIAGNOSTIC_POLL_INTERVAL_MS = 500L
        private const val HEURISTIC_POLL_INTERVAL_MS = 250L
        private const val HEARTBEAT_INTERVAL_MS = 2000L
        private const val HEARTBEAT_NO_OP = 0x00.toByte()
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var peripheral: KableVitruvianPeripheral? = null
    private var diagnosticPollingJob: Job? = null
    private var heuristicPollingJob: Job? = null
    private var heartbeatJob: Job? = null

    // Device info
    private var deviceName: String? = null
    private var deviceAddress: String? = null

    // Connection state
    private val _connectionState = MutableStateFlow<ConnectionStatus>(ConnectionStatus.Disconnected)
    val connectionState: StateFlow<ConnectionStatus> = _connectionState.asStateFlow()

    // Monitor data (64-entry buffer for workout metrics)
    private val _monitorData = MutableSharedFlow<WorkoutMetric>(
        replay = 0,
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val monitorData: SharedFlow<WorkoutMetric> = _monitorData.asSharedFlow()

    // Rep events
    private val _repEvents = MutableSharedFlow<RepNotification>(
        replay = 0,
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val repEvents: SharedFlow<RepNotification> = _repEvents.asSharedFlow()

    // Diagnostic data
    private val _diagnosticData = MutableStateFlow<DiagnosticDetails?>(null)
    val diagnosticData: StateFlow<DiagnosticDetails?> = _diagnosticData.asStateFlow()

    // Heuristic data
    private val _heuristicData = MutableStateFlow<HeuristicStatistics?>(null)
    val heuristicData: StateFlow<HeuristicStatistics?> = _heuristicData.asStateFlow()

    // Handle state
    private val _handleState = MutableStateFlow(HandleState.Released)
    val handleState: StateFlow<HandleState> = _handleState.asStateFlow()

    /**
     * Connect to a discovered device.
     */
    suspend fun connect(device: DiscoveredDevice): Result<Unit> {
        Timber.tag(TAG).d("Connecting to ${device.name}...")

        // Cleanup any existing connection
        disconnect()

        val newPeripheral = KableVitruvianPeripheral.create(device)
        peripheral = newPeripheral
        deviceName = device.name
        deviceAddress = device.address

        // Observe connection state
        newPeripheral.connectionState
            .onEach { state ->
                _connectionState.value = when (state) {
                    is BleConnectionState.Disconnected -> ConnectionStatus.Disconnected
                    is BleConnectionState.Connecting -> ConnectionStatus.Disconnected // No "connecting" in ConnectionStatus
                    is BleConnectionState.Connected -> ConnectionStatus.Ready
                    is BleConnectionState.Disconnecting -> ConnectionStatus.Disconnected
                    is BleConnectionState.Error -> ConnectionStatus.Error(state.message)
                }
            }
            .launchIn(scope)

        // Connect
        val result = newPeripheral.connect()

        if (result.isSuccess) {
            // Start observing data streams
            startDataObservation(newPeripheral)
            // Start polling jobs
            startPolling()
            // Start heartbeat
            startHeartbeat()
        }

        return result
    }

    private fun startDataObservation(peripheral: KableVitruvianPeripheral) {
        // Observe monitor data
        peripheral.monitorData
            .onEach { bytes ->
                parseMonitorData(bytes)?.let { metric ->
                    _monitorData.emit(metric)
                }
            }
            .catch { e -> Timber.tag(TAG).e(e, "Monitor data error") }
            .launchIn(scope)

        // Observe rep notifications
        peripheral.repNotifications
            .onEach { bytes ->
                parseRepData(bytes)?.let { rep ->
                    _repEvents.emit(rep)
                }
            }
            .catch { e -> Timber.tag(TAG).e(e, "Rep notification error") }
            .launchIn(scope)
    }

    private fun startPolling() {
        // Diagnostic polling (500ms - matches official app)
        diagnosticPollingJob = scope.launch {
            while (isActive) {
                peripheral?.readDiagnostic()?.onSuccess { bytes ->
                    parseDiagnosticData(bytes)?.let { diagnostic ->
                        _diagnosticData.value = diagnostic
                    }
                }
                delay(DIAGNOSTIC_POLL_INTERVAL_MS)
            }
        }

        // Heuristic polling (250ms / 4Hz - matches official app)
        heuristicPollingJob = scope.launch {
            while (isActive) {
                peripheral?.readHeuristic()?.onSuccess { bytes ->
                    parseHeuristicData(bytes)?.let { heuristic ->
                        _heuristicData.value = heuristic
                    }
                }
                delay(HEURISTIC_POLL_INTERVAL_MS)
            }
        }
    }

    private fun startHeartbeat() {
        heartbeatJob = scope.launch {
            while (isActive) {
                delay(HEARTBEAT_INTERVAL_MS)
                // Send no-op heartbeat to keep connection alive
                peripheral?.sendCommand(byteArrayOf(HEARTBEAT_NO_OP, 0x00, 0x00, 0x00))
            }
        }
    }

    private fun stopPolling() {
        Timber.tag(TAG).d("Stopping polling jobs...")
        diagnosticPollingJob?.cancel()
        heuristicPollingJob?.cancel()
        heartbeatJob?.cancel()
        diagnosticPollingJob = null
        heuristicPollingJob = null
        heartbeatJob = null
    }

    /**
     * Send a command to the device.
     */
    suspend fun sendCommand(data: ByteArray): Result<Unit> {
        val p = peripheral ?: return Result.failure(Exception("Not connected"))
        return p.sendCommand(data)
    }

    /**
     * Disconnect from the device.
     */
    suspend fun disconnect() {
        Timber.tag(TAG).d("Disconnecting...")
        stopPolling()
        peripheral?.disconnect()
        peripheral?.close()
        peripheral = null
        _connectionState.value = ConnectionStatus.Disconnected
    }

    /**
     * Close and cleanup resources.
     */
    fun close() {
        Timber.tag(TAG).d("Closing KableBleManager...")
        stopPolling()
        peripheral?.close()
        peripheral = null
        scope.cancel()
    }

    /**
     * Check if connected.
     */
    fun isConnected(): Boolean = peripheral?.isConnected() == true

    // ==================== Data Parsing ====================

    /**
     * Parse monitor data (16+ bytes).
     * Format (from VitruvianBleManager):
     * - u16[0-1]: ticks (low 16 bits)
     * - u16[2]: ticks (high 16 bits)
     * - u16[4]: positionA
     * - u16[8]: loadA * 100
     * - u16[10]: positionB
     * - u16[14]: loadB * 100
     * - u16[16-17]: status flags (optional)
     */
    private fun parseMonitorData(bytes: ByteArray): WorkoutMetric? {
        if (bytes.size < 16) {
            Timber.tag(TAG).w("Monitor data too short: ${bytes.size} bytes")
            return null
        }

        return try {
            val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)

            // Parse according to VitruvianBleManager format
            val f0 = buffer.getShort(0).toInt() and 0xFFFF      // Offset 0-1
            val f1 = buffer.getShort(2).toInt() and 0xFFFF      // Offset 2-3
            val f2 = buffer.getShort(4).toInt() and 0xFFFF      // Offset 4-5 (posA)
            val f4 = buffer.getShort(8).toInt() and 0xFFFF      // Offset 8-9 (loadA*100)
            val f5 = buffer.getShort(10).toInt() and 0xFFFF     // Offset 10-11 (posB)
            val f7 = buffer.getShort(14).toInt() and 0xFFFF     // Offset 14-15 (loadB*100)

            // Reconstruct 32-bit tick counter
            val ticks = f0 + (f1 shl 16)

            // Position values
            val positionA = f2
            val positionB = f5

            // Load in kg (device sends kg * 100)
            val loadA = f4 / 100.0f
            val loadB = f7 / 100.0f

            // Status (Bytes 16-17) if available
            var status = 0
            if (bytes.size >= 18) {
                status = buffer.getShort(16).toInt() and 0xFFFF
            }

            WorkoutMetric(
                timestamp = System.currentTimeMillis(),
                loadA = loadA,
                loadB = loadB,
                positionA = positionA,
                positionB = positionB,
                ticks = ticks,
                velocityA = 0.0,  // Calculated by repository/ViewModel if needed
                velocityB = 0.0,
                status = status
            )
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "Failed to parse monitor data")
            null
        }
    }

    /**
     * Parse rep notification (24 bytes).
     * Official App Reps Packet Structure:
     * - Bytes 0-3:   up (Int/u32) - up counter (concentric completions)
     * - Bytes 4-7:   down (Int/u32) - down counter (eccentric completions)
     * - Bytes 8-11:  rangeTop (Float) - maximum ROM boundary
     * - Bytes 12-15: rangeBottom (Float) - minimum ROM boundary
     * - Bytes 16-17: repsRomCount (Short/u16) - Warmup reps with proper ROM
     * - Bytes 18-19: repsRomTotal (Short/u16) - Total reps regardless of ROM
     * - Bytes 20-21: repsSetCount (Short/u16) - Working set rep count
     * - Bytes 22-23: repsSetTotal (Short/u16) - Total reps in set
     */
    private fun parseRepData(bytes: ByteArray): RepNotification? {
        if (bytes.size < 24) {
            Timber.tag(TAG).w("Rep notification too short: ${bytes.size} bytes (expected 24)")
            return null
        }

        return try {
            val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)

            // u32 counters at offsets 0 and 4
            val upCounter = buffer.getInt(0)
            val downCounter = buffer.getInt(4)

            // Float ROM boundaries at offsets 8 and 12
            val rangeTop = buffer.getFloat(8)
            val rangeBottom = buffer.getFloat(12)

            // u16 rep counts at offsets 16, 18, 20, 22
            val repsRomCount = buffer.getShort(16).toInt() and 0xFFFF   // Warmup reps (proper ROM)
            val repsRomTotal = buffer.getShort(18).toInt() and 0xFFFF  // Total reps (any ROM)
            val repsSetCount = buffer.getShort(20).toInt() and 0xFFFF  // Working set reps
            val repsSetTotal = buffer.getShort(22).toInt() and 0xFFFF  // Total set reps

            Timber.tag(TAG).d("Rep notification: up=$upCounter, down=$downCounter, " +
                    "repsRomCount=$repsRomCount, repsSetCount=$repsSetCount")

            RepNotification(
                topCounter = upCounter,          // Use full u32 up counter
                completeCounter = downCounter,   // Use full u32 down counter
                repsRomCount = repsRomCount,     // Warmup reps (proper ROM)
                repsSetCount = repsSetCount,     // Working set reps
                rangeTop = rangeTop,
                rangeBottom = rangeBottom,
                rawData = bytes,
                timestamp = System.currentTimeMillis()
            )
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "Failed to parse rep data")
            null
        }
    }

    /**
     * Parse diagnostic data (20+ bytes).
     * Format (from VitruvianBleManager):
     * - Int (4 bytes): seconds
     * - Short[4] (8 bytes): faults
     * - Byte[8] (8 bytes): temps
     */
    private fun parseDiagnosticData(bytes: ByteArray): DiagnosticDetails? {
        if (bytes.size < 20) {
            Timber.tag(TAG).w("Diagnostic data too short: ${bytes.size} bytes")
            return null
        }

        return try {
            val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            val seconds = buffer.getInt()

            val faults = mutableListOf<Short>()
            repeat(4) { faults.add(buffer.getShort()) }

            val temps = mutableListOf<Byte>()
            repeat(8) { temps.add(buffer.get()) }

            val containsFaults = faults.any { it != 0.toShort() }

            DiagnosticDetails(
                seconds = seconds,
                faults = faults,
                temps = temps,
                containsFaults = containsFaults,
                timestamp = System.currentTimeMillis()
            )
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "Failed to parse diagnostic data")
            null
        }
    }

    /**
     * Parse heuristic data (48 bytes).
     * Format (from VitruvianBleManager):
     * - Concentric phase (6 floats = 24 bytes): kgAvg, kgMax, velAvg, velMax, wattAvg, wattMax
     * - Eccentric phase (6 floats = 24 bytes): kgAvg, kgMax, velAvg, velMax, wattAvg, wattMax
     */
    private fun parseHeuristicData(bytes: ByteArray): HeuristicStatistics? {
        if (bytes.size < 48) {
            Timber.tag(TAG).w("Heuristic data too short: ${bytes.size} bytes")
            return null
        }

        return try {
            val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)

            // Concentric phase (first 6 floats)
            val concentric = HeuristicPhaseStatistics(
                kgAvg = buffer.getFloat(),
                kgMax = buffer.getFloat(),
                velAvg = buffer.getFloat(),
                velMax = buffer.getFloat(),
                wattAvg = buffer.getFloat(),
                wattMax = buffer.getFloat()
            )

            // Eccentric phase (next 6 floats)
            val eccentric = HeuristicPhaseStatistics(
                kgAvg = buffer.getFloat(),
                kgMax = buffer.getFloat(),
                velAvg = buffer.getFloat(),
                velMax = buffer.getFloat(),
                wattAvg = buffer.getFloat(),
                wattMax = buffer.getFloat()
            )

            HeuristicStatistics(
                concentric = concentric,
                eccentric = eccentric,
                timestamp = System.currentTimeMillis()
            )
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "Failed to parse heuristic data")
            null
        }
    }
}
