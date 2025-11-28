package com.example.vitruvianredux.data.ble

import com.example.vitruvianredux.domain.model.DiagnosticDetails
import com.example.vitruvianredux.domain.model.HeuristicPhaseStatistics
import com.example.vitruvianredux.domain.model.HeuristicStatistics
import com.example.vitruvianredux.domain.model.WorkoutMetric
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
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.max
import kotlin.math.min

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
        private val HEARTBEAT_NO_OP = byteArrayOf(0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte())
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

    // Monitor data (64-entry buffer for high-frequency emissions)
    private val _monitorData = MutableSharedFlow<WorkoutMetric>(
        replay = 0,
        extraBufferCapacity = 64, // Buffer up to 64 emissions (640ms of data)
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val monitorData: SharedFlow<WorkoutMetric> = _monitorData.asSharedFlow()

    // Rep events
    private val _repEvents = MutableSharedFlow<RepNotification>(
        replay = 0,
        extraBufferCapacity = 64,  // Buffer for rep notifications
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

    // Deload event flow - emitted when DELOAD_OCCURRED flag is detected
    private val _deloadOccurredEvents = MutableSharedFlow<Unit>(
        replay = 0,
        extraBufferCapacity = 8,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val deloadOccurredEvents: SharedFlow<Unit> = _deloadOccurredEvents.asSharedFlow()

    // Reconnection request flow - emitted when Kable detects an issue
    // This allows the repository to handle reconnection logic
    private val _reconnectionRequested = MutableSharedFlow<ReconnectionRequest>(
        replay = 0,
        extraBufferCapacity = 4,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val reconnectionRequested: SharedFlow<ReconnectionRequest> = _reconnectionRequested.asSharedFlow()

    // Debounce for deload events
    private var lastDeloadEventTime = 0L
    private val DELOAD_EVENT_DEBOUNCE_MS = 2000L

    // Counter for monitor notifications
    @Volatile private var monitorNotificationCount = 0L

    // Command response flow
    private val _commandResponses = MutableSharedFlow<UByte>(
        replay = 0,
        extraBufferCapacity = 16,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    // Just Lift detection parameters - v0.5.1-beta values (PROVEN WORKING)
    private val HANDLE_GRABBED_THRESHOLD = 8.0   // Position > 8.0 = handles grabbed
    private val HANDLE_REST_THRESHOLD = 5.0      // Position < 5.0 = handles at rest
    private val VELOCITY_THRESHOLD = 100.0       // Velocity > 100 units/s = significant movement

    // Last good positions for filtering spikes (volatile for thread safety)
    @Volatile private var lastGoodPosA = 0
    @Volatile private var lastGoodPosB = 0

    // Position tracking for validation and velocity (volatile for thread safety)
    @Volatile private var lastPositionA = 0
    @Volatile private var lastPositionB = 0
    @Volatile private var lastTimestamp = 0L
    @Volatile private var strictValidationEnabled = false

    // Track position range for tuning (logged at workout end)
    private var minPositionSeen = Double.MAX_VALUE
    private var maxPositionSeen = Double.MIN_VALUE

    // Force-based grab/release timing
    private var forceAboveGrabThresholdStart: Long? = null
    private var forceBelowReleaseThresholdStart: Long? = null

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
                    is BleConnectionState.Connecting -> ConnectionStatus.Disconnected // Kable uses connecting states, but we map to Disconnected until Ready
                    is BleConnectionState.Connected -> ConnectionStatus.Ready
                    is BleConnectionState.Disconnecting -> ConnectionStatus.Disconnected
                    is BleConnectionState.Error -> ConnectionStatus.Error(state.message)
                }
            }
            .launchIn(scope)

        // Connect
        val result = newPeripheral.connect()

        if (result.isSuccess) {
            // Request high connection priority for stability (matching VitruvianBleManager)
            newPeripheral.requestConnectionPriority(1) // HIGH = 1 (SCAN_BALANCED=0, LOW_POWER=2)

            // Kable handles MTU negotiation automatically

            // Start observing data streams
            startDataObservation(newPeripheral)
            // Start polling jobs (Diagnostic & Heuristic)
            startDiagnosticPolling()
            startHeuristicPolling()
            // Start heartbeat
            startHeartbeat()
        }

        return result
    }

    private fun startDataObservation(peripheral: KableVitruvianPeripheral) {
        // Observe monitor data
        peripheral.monitorData
            .onEach { bytes ->
                // Log at INFO level initially to verify notifications are working
                if (monitorNotificationCount++ % 100 == 0L) {
                     Timber.tag(TAG).i("📊 MONITOR NOTIFICATION #$monitorNotificationCount (${bytes.size} bytes)")
                }
                handleMonitorData(bytes)
            }
            .catch { e -> Timber.tag(TAG).e(e, "Monitor data error") }
            .launchIn(scope)

        // Observe rep notifications
        peripheral.repNotifications
            .onEach { bytes ->
                Timber.tag(TAG).d("🔥 REP NOTIFICATION CALLBACK FIRED! Data size: ${bytes.size} bytes")
                handleRepNotification(bytes)
            }
            .catch { e -> Timber.tag(TAG).e(e, "Rep notification error") }
            .launchIn(scope)
    }

    /**
     * Start polling diagnostic characteristic (keep-alive + health monitoring)
     */
    fun startDiagnosticPolling() {
        diagnosticPollingJob?.cancel()
        diagnosticPollingJob = scope.launch {
            Timber.tag(TAG).d("🔄 Starting diagnostic polling (500ms interval - matches official app)")
            while (isActive) {
                peripheral?.readDiagnostic()?.onSuccess { bytes ->
                    parseDiagnosticData(bytes)
                }
                delay(DIAGNOSTIC_POLL_INTERVAL_MS)
            }
        }
    }

    /**
     * Start polling heuristic characteristic
     */
    fun startHeuristicPolling() {
        heuristicPollingJob?.cancel()
        heuristicPollingJob = scope.launch {
            Timber.tag(TAG).d("Starting heuristic polling (250ms interval / 4Hz - matching official app)")
            while (isActive) {
                peripheral?.readHeuristic()?.onSuccess { bytes ->
                    parseHeuristicData(bytes)
                }
                delay(HEURISTIC_POLL_INTERVAL_MS)
            }
        }
    }

    private fun startHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch {
            Timber.tag(TAG).d("Starting BLE heartbeat (interval=${HEARTBEAT_INTERVAL_MS}ms)")
            while (isActive) {
                // Heartbeat logic from VitruvianBleManager: attempt RX read, fall back to benign no-op write.
                // Kable doesn't support reading from NUS RX (it's write-only usually, but Nordic allowed trying).
                // If the characteristic properties don't support read, Kable will fail.
                // For now, we'll stick to the write heartbeat which is the fallback.

                delay(HEARTBEAT_INTERVAL_MS)

                // Send no-op heartbeat to keep connection alive
                peripheral?.sendCommand(HEARTBEAT_NO_OP)
                    ?.onFailure { e -> Timber.tag(TAG).w("Heartbeat no-op write failed: ${e.message}") }
            }
        }
    }

    /**
     * Enable monitor data reception/handle detection for workouts.
     * Note: Monitor data flows via notifications observed in connect().
     * This method resets state and configured handle detection mode.
     */
    fun startMonitorPolling(forAutoStart: Boolean = false) {
        // Reset position tracking for new workout
        minPositionSeen = Double.MAX_VALUE
        maxPositionSeen = Double.MIN_VALUE

        // Reset notification counter for this workout session
        val previousCount = monitorNotificationCount
        monitorNotificationCount = 0L
        Timber.tag(TAG).i("📊 Monitor notifications reset (previous session: $previousCount notifications)")

        if (forAutoStart) {
            // Start in WaitingForRest state
            _handleState.value = HandleState.WaitingForRest
            forceAboveGrabThresholdStart = null
            forceBelowReleaseThresholdStart = null
            Timber.tag(TAG).d("Monitor data enabled for AUTO-START - waiting for handles at rest (pos < ${HANDLE_REST_THRESHOLD})")
        } else {
            // Active workout - set to Grabbed since workout is already running
            _handleState.value = HandleState.Grabbed
            Timber.tag(TAG).d("Monitor data enabled for ACTIVE WORKOUT")
        }
    }

    private fun stopPolling() {
        val timestamp = System.currentTimeMillis()
        Timber.tag(TAG).d("STOP_DEBUG: [$timestamp] stopPolling() called")

        // Log analysis from workout
        if (minPositionSeen != Double.MAX_VALUE && maxPositionSeen != Double.MIN_VALUE) {
            Timber.tag(TAG).i("========== WORKOUT ANALYSIS ==========")
            Timber.tag(TAG).i("Position range: min=$minPositionSeen, max=$maxPositionSeen")
            Timber.tag(TAG).i("======================================")
        }

        diagnosticPollingJob?.cancel()
        heuristicPollingJob?.cancel()
        heartbeatJob?.cancel()
        diagnosticPollingJob = null
        heuristicPollingJob = null
        heartbeatJob = null
    }

    /**
     * Enable Just Lift waiting mode.
     */
    fun enableJustLiftWaitingMode() {
        Timber.tag(TAG).i("Enabling Just Lift waiting mode")
        // Reset position tracking
        minPositionSeen = Double.MAX_VALUE
        maxPositionSeen = Double.MIN_VALUE
        // Reset grab/release timers
        forceAboveGrabThresholdStart = null
        forceBelowReleaseThresholdStart = null
        // Start in WaitingForRest state
        _handleState.value = HandleState.WaitingForRest
    }

    /**
     * Enable or disable strict validation mode.
     */
    fun setStrictValidationEnabled(enabled: Boolean) {
        strictValidationEnabled = enabled
        Timber.tag(TAG).d("Strict validation enabled: $enabled")
    }

    /**
     * Send a command to the device.
     */
    suspend fun sendCommand(data: ByteArray): Result<Unit> {
        val p = peripheral ?: return Result.failure(Exception("Not connected"))

        // Log detailed hex dump for debugging (matching VitruvianBleManager)
        Timber.tag(TAG).d("STOP_DEBUG: Command size: ${data.size} bytes")
        Timber.tag(TAG).d("STOP_DEBUG: Hex string: ${data.joinToString(" ") { "%02X".format(it) }}")

        return p.sendCommand(data)
    }

    /**
     * Test PROGRAM frame on all workout characteristics
     */
    suspend fun testOfficialAppProtocol(): Result<Unit> = withContext(Dispatchers.Main) {
        // Since we are using Kable, we need to adapt this logic.
        // VitruvianBleManager iterates over WORKOUT_CMD_CHAR_UUIDS and writes to each.
        // We will need to implement a way to write to arbitrary UUIDs in KableVitruvianPeripheral
        // or add a method to test these.
        // For strict 1:1 migration, I will implement the logic here, assuming peripheral can write to arbitrary chars.

        try {
            val p = peripheral ?: return@withContext Result.failure(Exception("Not connected"))

            Timber.tag(TAG).d("=== TESTING PROGRAM FRAME ON ALL CHARACTERISTICS ===")
            val workoutCmdUuids = com.example.vitruvianredux.util.BleConstants.WORKOUT_CMD_CHAR_UUIDS
            Timber.tag(TAG).d("Found ${workoutCmdUuids.size} workout command characteristics to test")

            if (workoutCmdUuids.isEmpty()) {
                Timber.tag(TAG).e("No workout command characteristics found!")
                return@withContext Result.failure(Exception("No workout command characteristics available"))
            }

            // Build PROGRAM frame
            val programFrame = com.example.vitruvianredux.util.ProtocolBuilder.buildProgramParams(
                com.example.vitruvianredux.domain.model.WorkoutParameters(
                    workoutType = com.example.vitruvianredux.domain.model.WorkoutType.Program(
                        com.example.vitruvianredux.domain.model.ProgramMode.OldSchool
                    ),
                    weightPerCableKg = 20f,
                    reps = 5
                )
            )

            workoutCmdUuids.forEachIndexed { index, uuid ->
                Timber.tag(TAG).d("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                Timber.tag(TAG).d("Testing characteristic ${index + 1}/${workoutCmdUuids.size}")
                Timber.tag(TAG).d("UUID: $uuid")

                // Kable write
                p.write(uuid, programFrame)
                    .onSuccess {
                        Timber.tag(TAG).d("✓ PROGRAM frame sent to $uuid")
                    }
                    .onFailure {
                        Timber.tag(TAG).w("Failed to write to $uuid: ${it.message}")
                    }

                delay(10000)
            }
             Result.success(Unit)
        } catch (e: Exception) {
             Timber.tag(TAG).e(e, "Failed to test PROGRAM frame")
             Result.failure(e)
        }
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

    // ==================== Data Parsing & Logic ====================

    /**
     * Validate a monitor sample.
     */
    private fun validateSample(posA: Int, loadA: Float, posB: Int, loadB: Float): Boolean {
        // Official app range: -1000 to +1000 mm
        if ((posA < WorkoutConstants.MIN_POSITION || posA > WorkoutConstants.MAX_POSITION) ||
            (posB < WorkoutConstants.MIN_POSITION || posB > WorkoutConstants.MAX_POSITION)) {
            Timber.tag(TAG).w("Position out of range: posA=$posA, posB=$posB (valid: ${WorkoutConstants.MIN_POSITION} to ${WorkoutConstants.MAX_POSITION})")
            return false
        }

        // Strict validation checks position jumps (when enabled)
        if (strictValidationEnabled) {
            val deltaA = kotlin.math.abs(posA - lastPositionA)
            val deltaB = kotlin.math.abs(posB - lastPositionB)
            if (deltaA > 200 || deltaB > 200) {
                Timber.tag(TAG).w("Position jump detected: deltaA=$deltaA, deltaB=$deltaB")
                return false
            }
        }

        return true
    }

    /**
     * Analyze handle state using v0.5.1-beta POSITION+VELOCITY detection.
     */
    private fun analyzeHandleState(metric: WorkoutMetric): HandleState {
        val posA = metric.positionA.toDouble()
        val posB = metric.positionB.toDouble()
        val velocityA = metric.velocityA
        val velocityB = metric.velocityB

        // Track position range for post-workout tuning (use max of both handles)
        minPositionSeen = min(minPositionSeen, min(posA, posB))
        maxPositionSeen = max(maxPositionSeen, max(posA, posB))

        val currentState = _handleState.value

        // Check both handles - support single-handle exercises
        val handleAGrabbed = posA > HANDLE_GRABBED_THRESHOLD
        val handleBGrabbed = posB > HANDLE_GRABBED_THRESHOLD
        val handleAMoving = velocityA > VELOCITY_THRESHOLD
        val handleBMoving = velocityB > VELOCITY_THRESHOLD

        // Simple hysteresis with velocity check
        return when (currentState) {
            HandleState.WaitingForRest -> {
                // Must see handles at rest before arming grab detection
                if (posA < HANDLE_REST_THRESHOLD && posB < HANDLE_REST_THRESHOLD) {
                    Timber.tag(TAG).d("Handles at REST (posA=$posA, posB=$posB < $HANDLE_REST_THRESHOLD) - auto-start now ARMED")
                    HandleState.Released
                } else {
                    HandleState.WaitingForRest
                }
            }
            HandleState.Released, HandleState.Moving -> {
                // Check if EITHER handle is grabbed and moving
                val aActive = handleAGrabbed && handleAMoving
                val bActive = handleBGrabbed && handleBMoving

                if (aActive || bActive) {
                    HandleState.Grabbed
                } else if (handleAGrabbed || handleBGrabbed) {
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
                    HandleState.Released
                } else {
                    HandleState.Grabbed
                }
            }
        }
    }

    private fun handleMonitorData(bytes: ByteArray) {
        try {
            if (bytes.size < 16) {
                Timber.tag(TAG).w("Monitor data too short: ${bytes.size} bytes")
                return
            }

            val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)

            // v0.5.1-beta parsing
            val f0 = buffer.getShort(0).toInt() and 0xFFFF
            val f1 = buffer.getShort(2).toInt() and 0xFFFF
            val f2 = buffer.getShort(4).toInt() and 0xFFFF      // posA
            val f4 = buffer.getShort(8).toInt() and 0xFFFF      // loadA*100
            val f5 = buffer.getShort(10).toInt() and 0xFFFF     // posB
            val f7 = buffer.getShort(14).toInt() and 0xFFFF     // loadB*100

            // Reconstruct 32-bit tick counter
            val ticks = f0 + (f1 shl 16)

            // Position values
            var positionA = f2
            var positionB = f5

            // Spike filtering - BLE transmission errors produce values > 50000
            if (positionA > WorkoutConstants.POSITION_SPIKE_THRESHOLD) positionA = lastGoodPosA else lastGoodPosA = positionA
            if (positionB > WorkoutConstants.POSITION_SPIKE_THRESHOLD) positionB = lastGoodPosB else lastGoodPosB = positionB

            // Load in kg (device sends kg * 100)
            val loadA = f4 / 100.0f
            val loadB = f7 / 100.0f

            // Status (Bytes 16-17) if available
            var status = 0
            if (bytes.size >= 18) {
                status = buffer.getShort(16).toInt() and 0xFFFF
            }

            // Status flags logic
            if (status != 0) {
                val isDeloadOccurred = (status and 0x8000) != 0

                if (isDeloadOccurred) {
                    // Emit deload event so repository can send STOP to clear fault state
                    val now = System.currentTimeMillis()
                    if (now - lastDeloadEventTime > DELOAD_EVENT_DEBOUNCE_MS) {
                        lastDeloadEventTime = now
                        scope.launch {
                            Timber.tag(TAG).d("DELOAD_OCCURRED: Emitting event for repository to send STOP")
                            _deloadOccurredEvents.emit(Unit)
                        }
                    }
                }
            }

            // Validate sample
            if (!validateSample(positionA, loadA, positionB, loadB)) {
                return
            }

            // Update last good positions
            lastGoodPosA = positionA
            lastGoodPosB = positionB

            // Calculate velocity
            val currentTime = System.currentTimeMillis()
            val velocityA = if (lastTimestamp > 0L) {
                val deltaTime = (currentTime - lastTimestamp) / 1000.0
                val deltaPos = positionA - lastPositionA
                if (deltaTime > 0) kotlin.math.abs(deltaPos / deltaTime) else 0.0
            } else 0.0

            val velocityB = if (lastTimestamp > 0L) {
                val deltaTime = (currentTime - lastTimestamp) / 1000.0
                val deltaPos = positionB - lastPositionB
                if (deltaTime > 0) kotlin.math.abs(deltaPos / deltaTime) else 0.0
            } else 0.0

            lastPositionA = positionA
            lastPositionB = positionB
            lastTimestamp = currentTime

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

            // Emit to flow
            _monitorData.tryEmit(metric)

            // Analyze handle state
            val newHandleState = analyzeHandleState(metric)
            if (newHandleState != _handleState.value) {
                _handleState.value = newHandleState
                Timber.tag(TAG).d("Handle state changed: $newHandleState")
            }

        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error parsing monitor data")
        }
    }

    private fun handleRepNotification(bytes: ByteArray) {
        try {
            if (bytes.size < 24) {
                Timber.tag(TAG).w("Rep notification too short: ${bytes.size} bytes (expected 24)")
                return
            }

            val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)

            // u32 counters at offsets 0 and 4
            val upCounter = buffer.getInt(0)
            val downCounter = buffer.getInt(4)

            // Float ROM boundaries at offsets 8 and 12
            val rangeTop = buffer.getFloat(8)
            val rangeBottom = buffer.getFloat(12)

            // u16 rep counts
            val repsRomCount = buffer.getShort(16).toInt() and 0xFFFF
            val repsRomTotal = buffer.getShort(18).toInt() and 0xFFFF
            val repsSetCount = buffer.getShort(20).toInt() and 0xFFFF
            val repsSetTotal = buffer.getShort(22).toInt() and 0xFFFF

            val repData = RepNotification(
                topCounter = upCounter,
                completeCounter = downCounter,
                repsRomCount = repsRomCount,
                repsSetCount = repsSetCount,
                rangeTop = rangeTop,
                rangeBottom = rangeBottom,
                rawData = bytes,
                timestamp = System.currentTimeMillis()
            )

            _repEvents.tryEmit(repData)

        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error parsing rep notification")
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
                containsFaults = containsFaults,
                timestamp = System.currentTimeMillis()
            )
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to parse diagnostic data")
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

            _heuristicData.value = HeuristicStatistics(concentric, eccentric, System.currentTimeMillis())
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to parse heuristic data")
        }
    }
}
