# Non-UI Differences: Beta 5.1 vs Current Build (Detailed)

This document provides granular code-level differences for all non-UI changes between v0.5.1-beta and the current build.

---

## Table of Contents
1. [BLE Communication - VitruvianBleManager.kt](#1-ble-communication---vitruvianblemagerkt)
2. [Rep Counter Complete Rewrite](#2-rep-counter-complete-rewrite)
3. [MainViewModel Changes](#3-mainviewmodel-changes)
4. [New Domain Models](#4-new-domain-models)
5. [Database Changes](#5-database-changes)
6. [Repository Changes](#6-repository-changes)
7. [Code Organization - Extracted Files](#7-code-organization---extracted-files)

---

## 1. BLE Communication - VitruvianBleManager.kt

### 1.1 New Characteristics and State Flows

**BEFORE:**
```kotlin
// GATT characteristics (Beta 5.1)
private var nusRxCharacteristic: BluetoothGattCharacteristic? = null
private var monitorCharacteristic: BluetoothGattCharacteristic? = null
private var propertyCharacteristic: BluetoothGattCharacteristic? = null
private var repNotifyCharacteristic: BluetoothGattCharacteristic? = null

// State flows
private val _connectionState = MutableStateFlow<ConnectionStatus>(ConnectionStatus.Disconnected)
private val _monitorData = MutableSharedFlow<WorkoutMetric>(...)
```

**AFTER:**
```kotlin
// GATT characteristics (Current) - 2 new characteristics added
private var nusRxCharacteristic: BluetoothGattCharacteristic? = null
private var monitorCharacteristic: BluetoothGattCharacteristic? = null
private var propertyCharacteristic: BluetoothGattCharacteristic? = null // Diagnostic
private var repNotifyCharacteristic: BluetoothGattCharacteristic? = null
private var heuristicCharacteristic: BluetoothGattCharacteristic? = null  // NEW
private var versionCharacteristic: BluetoothGattCharacteristic? = null    // NEW

// NEW: Polling job for heuristic data
private var heuristicPollingJob: Job? = null

// NEW: Force-based handle detection (matching official app)
@Volatile private var forceAboveGrabThresholdSince: Long? = null
@Volatile private var forceBelowReleaseThresholdSince: Long? = null

// NEW: Strict validation mode
@Volatile private var strictValidationEnabled = false

// NEW: State flows for diagnostic and heuristic data
private val _diagnosticData = MutableStateFlow<DiagnosticDetails?>(null)
val diagnosticData: StateFlow<DiagnosticDetails?> = _diagnosticData.asStateFlow()

private val _heuristicData = MutableStateFlow<HeuristicStatistics?>(null)
val heuristicData: StateFlow<HeuristicStatistics?> = _heuristicData.asStateFlow()
```

**Why:** Added support for reading device diagnostics (faults, temps) and heuristic data (phase statistics). Force-based handle detection matches official app behavior.

---

### 1.2 Strict Validation Mode

**NEW FUNCTION:**
```kotlin
/**
 * Enable or disable strict validation mode (matching official app).
 * When enabled, large position jumps (>200) are filtered as invalid.
 */
fun setStrictValidationEnabled(enabled: Boolean) {
    strictValidationEnabled = enabled
    Timber.d("Strict validation enabled: $enabled")
}

/**
 * Validate a monitor sample (matching official app).
 * Filters out invalid position values and optionally large position jumps.
 */
private fun validateSample(posA: Int, loadA: Float, posB: Int, loadB: Float): Boolean {
    // Basic range validation (positions should be 0-3000)
    if (posA < 0 || posA > 3000 || posB < 0 || posB > 3000) {
        Timber.w("Position out of range: posA=$posA, posB=$posB")
        return false
    }

    // Strict validation checks position jumps (when enabled)
    if (strictValidationEnabled) {
        val deltaA = kotlin.math.abs(posA - lastPositionA)
        val deltaB = kotlin.math.abs(posB - lastPositionB)
        if (deltaA > 200 || deltaB > 200) {
            Timber.w("Position jump detected: deltaA=$deltaA, deltaB=$deltaB")
            return false
        }
    }
    return true
}
```

**Why:** Filters out spurious position spikes that could cause erratic UI behavior or false rep counts.

---

### 1.3 Monitor Polling Changes

**BEFORE:**
```kotlin
fun startMonitorPolling() {
    // Reset position tracking for new workout
    minPositionSeen = Double.MAX_VALUE
    maxPositionSeen = Double.MIN_VALUE

    // Start with handles released; wait for actual grab detection from data
    _handleState.value = HandleState.Released

    monitorPollingJob?.cancel()
    monitorPollingJob = pollingScope.launch {
        Timber.d("Starting monitor polling (100ms interval)")
        while (isActive) {
            try {
                monitorCharacteristic?.let { char ->
                    readCharacteristic(char)
                        .with { _, data ->
                            Timber.d("Monitor read callback fired!")
                            handleMonitorData(data)
                        }
                        .enqueue()
                }
                delay(100) // Poll every 100ms  <-- OLD INTERVAL
            } catch (e: Exception) {
                Timber.e(e, "Error in monitor polling")
            }
        }
    }
}
```

**AFTER:**
```kotlin
/**
 * Start polling monitor characteristic every 16ms (~60Hz)
 *
 * @param forAutoStart If true, enables handle detection with WaitingForRest state.
 *                     If false, skips handle state initialization (for active workout).
 */
fun startMonitorPolling(forAutoStart: Boolean = false) {
    // Reset position tracking for new workout
    minPositionSeen = Double.MAX_VALUE
    maxPositionSeen = Double.MIN_VALUE

    if (forAutoStart) {
        // Start in WaitingForRest state - must see handles at rest before arming grab detection
        // This prevents immediate auto-start if cables already have tension
        _handleState.value = HandleState.WaitingForRest
        forceAboveGrabThresholdSince = null
        forceBelowReleaseThresholdSince = null
        Timber.d("Starting monitor polling for AUTO-START - waiting for handles at rest")
    } else {
        // Active workout - set to Grabbed since workout is already running
        _handleState.value = HandleState.Grabbed
        Timber.d("Starting monitor polling for ACTIVE WORKOUT")
    }

    monitorPollingJob?.cancel()
    monitorPollingJob = pollingScope.launch {
        Timber.d("Starting monitor polling (16ms interval / ~60Hz)")  // <-- NEW INTERVAL
        while (isActive) {
            try {
                monitorCharacteristic?.let { char ->
                    readCharacteristic(char)
                        .with { _, data ->
                            Timber.v("Monitor read callback fired!")  // Changed to verbose
                            handleMonitorData(data)
                        }
                        .enqueue()
                }
                delay(16) // Poll every 16ms (~60Hz) - matching official app  <-- NEW
            } catch (e: Exception) {
                Timber.e(e, "Error in monitor polling")
            }
        }
    }
}
```

**Key Changes:**
| Aspect | Beta 5.1 | Current |
|--------|----------|---------|
| Polling interval | 100ms (10Hz) | 16ms (~60Hz) |
| Initial handle state | `Released` | `WaitingForRest` or `Grabbed` based on parameter |
| Auto-start parameter | N/A | `forAutoStart: Boolean` |

**Why:**
- 60Hz polling provides smoother real-time data visualization
- `WaitingForRest` initial state prevents premature auto-start if cables have pre-existing tension
- Parameter allows different behavior for auto-start vs active workout scenarios

---

### 1.4 Property Polling → Diagnostic Polling Rename

**BEFORE:**
```kotlin
fun startPropertyPolling() {
    propertyPollingJob?.cancel()
    propertyPollingJob = pollingScope.launch {
        Timber.d("🔄 Starting keep-alive property polling (500ms interval)")
        // ... reads property characteristic
        delay(500) // Poll every 500ms (matches web app)
    }
}
```

**AFTER:**
```kotlin
/**
 * Start polling diagnostic characteristic every 500ms (keep-alive + health monitoring)
 * Matches official app interval - Renamed from startPropertyPolling
 */
fun startDiagnosticPolling() {
    propertyPollingJob?.cancel()
    propertyPollingJob = pollingScope.launch {
        Timber.d("🔄 Starting diagnostic polling (500ms interval - matches official app)")
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
                            parseDiagnosticData(bytes)  // NEW: Actually parse the data
                        }
                    }
                    .enqueue()

                delay(500) // Poll every 500ms (Official app interval - verified)
            } catch (e: Exception) {
                Timber.e(e, "❌ Exception in diagnostic polling")
                delay(500)
            }
        }
    }
}

// NEW: Parse diagnostic data from device
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
```

**Why:** Previously, property polling was only used as keep-alive. Now it also parses and exposes diagnostic data (runtime, faults, temperatures).

---

### 1.5 Handle State Analysis Changes

**BEFORE:**
```kotlin
private fun analyzeHandleState(metric: WorkoutMetric): HandleState {
    // ... analysis logic
    return when (currentState) {
        HandleState.Released, HandleState.Moving -> { /* ... */ HandleState.Grabbed }
        HandleState.Grabbed -> { /* ... */ HandleState.Released }
    }
}

// Called from handleMonitorData:
val newHandleState = analyzeHandleState(metric)
if (newHandleState != _handleState.value) {
    _handleState.value = newHandleState
    Timber.d("Handle state changed: $newHandleState")
}
```

**AFTER:**
```kotlin
private fun analyzeHandleState(metric: WorkoutMetric) {  // Now returns Unit
    val totalLoad = metric.loadA + metric.loadB
    val now = System.currentTimeMillis()
    val currentState = _handleState.value

    when (currentState) {
        HandleState.WaitingForRest -> {  // NEW STATE
            // Must see handles at rest (position < 2.5) before arming grab detection
            // This prevents immediate auto-start if cables already have tension
            if (posA < HANDLE_REST_THRESHOLD && posB < HANDLE_REST_THRESHOLD) {
                _handleState.value = HandleState.Released
                forceAboveGrabThresholdSince = null
                forceBelowReleaseThresholdSince = null
                Timber.d("Handles at REST - auto-start now ARMED")
            }
            // If position is above threshold, stay in WaitingForRest - don't arm yet
        }
        HandleState.Released, HandleState.Moving -> {
            // ... grab detection logic
            if (aActive || bActive) {
                _handleState.value = HandleState.Grabbed  // Direct assignment
            } else if (handleAGrabbed || handleBGrabbed) {
                _handleState.value = HandleState.Moving
            } else {
                _handleState.value = HandleState.Released
            }
        }
        HandleState.Grabbed -> {
            if (aReleased && bReleased) {
                _handleState.value = HandleState.Released
            }
            // else stay Grabbed
        }
    }
}

// Called from handleMonitorData - no return value check needed:
analyzeHandleState(metric)
```

**Key Change:** Added `HandleState.WaitingForRest` as initial state. Device must see handles at rest position before arming grab detection. This fixes false auto-starts when cables already have tension.

---

### 1.6 Rep Notification Parsing - 24-byte Packet

**BEFORE:**
```kotlin
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

        // Emit notification
        _repNotification.tryEmit(RepNotification(topCounter, completeCounter))
    }
}
```

**AFTER:**
```kotlin
/**
 * Handle rep notification data
 *
 * Official App Reps Packet Structure (24 bytes, Little Endian):
 * - Bytes 0-3:   up (Int/u32) - up counter (concentric completions)
 * - Bytes 4-7:   down (Int/u32) - down counter (eccentric completions)
 * - Bytes 8-11:  rangeTop (Float) - maximum ROM boundary
 * - Bytes 12-15: rangeBottom (Float) - minimum ROM boundary
 * - Bytes 16-17: repsRomCount (Short/u16) - Warmup reps with proper ROM
 * - Bytes 18-19: repsRomTotal (Short/u16) - Total reps regardless of ROM
 * - Bytes 20-21: repsSetCount (Short/u16) - WORKING SET REP COUNT - display this!
 * - Bytes 22-23: repsSetTotal (Short/u16) - Total reps in set
 */
private fun handleRepNotification(data: Data) {
    try {
        val bytes = data.value ?: return

        if (bytes.size < 24) {
            Timber.w("Rep notification too short: ${bytes.size} bytes (expected 24)")
            return
        }

        // Parse full 24-byte packet according to official app structure
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)

        val topCounter = buffer.getInt()      // Bytes 0-3 (u32, not u16!)
        val completeCounter = buffer.getInt() // Bytes 4-7 (u32, not u16!)
        val rangeTop = buffer.getFloat()      // Bytes 8-11
        val rangeBottom = buffer.getFloat()   // Bytes 12-15
        val repsRomCount = buffer.getShort().toInt() and 0xFFFF   // Bytes 16-17
        val repsRomTotal = buffer.getShort().toInt() and 0xFFFF   // Bytes 18-19
        val repsSetCount = buffer.getShort().toInt() and 0xFFFF   // Bytes 20-21 - DISPLAY THIS
        val repsSetTotal = buffer.getShort().toInt() and 0xFFFF   // Bytes 22-23

        // Emit notification with all parsed fields
        _repNotification.tryEmit(RepNotification(
            topCounter = topCounter,
            completeCounter = completeCounter,
            rangeTop = rangeTop,
            rangeBottom = rangeBottom,
            repsRomCount = repsRomCount,
            repsRomTotal = repsRomTotal,
            repsSetCount = repsSetCount,
            repsSetTotal = repsSetTotal
        ))
    }
}
```

**Why:** The official app uses a 24-byte packet with device-provided rep counts. Previously, the app only parsed 6 bytes and inferred rep counts from counter deltas. Now it uses the device's actual rep count (`repsSetCount`).

---

## 2. Rep Counter Complete Rewrite

### 2.1 RepCounterFromMachine.kt - Philosophy Change

**BEFORE (Beta 5.1) - App-side rep counting:**
```kotlin
/**
 * Handles rep counting based on notifications emitted by the Vitruvian machine.
 *
 * This is a direct port of the logic used by the reference web application. Rather than trying to
 * infer reps from position data, we track the counters supplied by the hardware (u16 values) and
 * supplement them with light position tracking for range calibration and auto-stop support.
 *
 * Rep Counting Logic (matches official app):
 * - Reps are counted when topCounter increments (at peak contraction/top of movement)
 * - This provides intuitive feedback - rep counts when you "complete" the concentric phase
 * - For the final rep, completeCounter is used to ensure full eccentric phase before release
 */

fun process(topCounter: Int, completeCounter: Int, posA: Int = 0, posB: Int = 0) {
    if (lastTopCounter != null) {
        val topDelta = calculateDelta(lastTopCounter!!, topCounter)
        if (topDelta > 0) {
            recordTopPosition(posA, posB)

            // Count the rep at TOP of movement (matches official app behavior)
            val totalReps = warmupReps + workingReps + 1
            if (totalReps <= warmupTarget) {
                warmupReps++
                onRepEvent?.invoke(RepEvent(type = RepType.WARMUP_COMPLETED, ...))
                if (warmupReps == warmupTarget) {
                    onRepEvent?.invoke(RepEvent(type = RepType.WARMUP_COMPLETE, ...))
                }
            } else {
                workingReps++
                onRepEvent?.invoke(RepEvent(type = RepType.WORKING_COMPLETED, ...))

                // If "Stop At Top" is enabled and target reached, stop NOW
                if (stopAtTop && !isJustLift && !isAMRAP && workingTarget > 0 && workingReps >= workingTarget) {
                    shouldStop = true
                    onRepEvent?.invoke(RepEvent(type = RepType.WORKOUT_COMPLETE, ...))
                }
            }
        }
    }
    lastTopCounter = topCounter
    // ... similar logic for completeCounter
}
```

**AFTER (Current) - Device-provided rep counts:**
```kotlin
/**
 * Handles rep counting based on notifications emitted by the Vitruvian machine.
 *
 * CRITICAL: The official app uses device-provided rep counts directly from the 24-byte Reps packet:
 * - repsRomCount (offset 16-17): Warmup reps with proper ROM
 * - repsSetCount (offset 20-21): Working set rep count - THIS IS WHAT TO DISPLAY
 *
 * The up/down counters (topCounter/completeCounter) are used for detecting rep events (for haptics)
 * but the DISPLAYED rep count should come from repsRomCount and repsSetCount.
 *
 * This matches the official app behavior exactly - firmware handles rep counting, app just displays.
 */

// NEW: Track device-provided rep counts
private var lastDeviceWarmupReps = 0
private var lastDeviceWorkingReps = 0

fun process(
    topCounter: Int,
    completeCounter: Int,
    deviceWarmupReps: Int = 0,    // NEW: repsRomCount from device
    deviceWorkingReps: Int = 0,   // NEW: repsSetCount from device
    posA: Int = 0,
    posB: Int = 0
) {
    // OFFICIAL APP METHOD: Use device-provided rep counts directly
    // This ensures rep counting matches exactly what the firmware reports
    val warmupRepsDelta = deviceWarmupReps - lastDeviceWarmupReps
    val workingRepsDelta = deviceWorkingReps - lastDeviceWorkingReps

    // Detect warmup rep completion from device counter
    if (warmupRepsDelta > 0 && deviceWarmupReps <= warmupTarget) {
        warmupReps = deviceWarmupReps  // Direct assignment from device
        Timber.d("🏋️ WARMUP REP from device: $warmupReps/$warmupTarget")
        recordTopPosition(posA, posB)
        onRepEvent?.invoke(RepEvent(type = RepType.WARMUP_COMPLETED, ...))
        if (warmupReps == warmupTarget) {
            onRepEvent?.invoke(RepEvent(type = RepType.WARMUP_COMPLETE, ...))
        }
    }

    // Detect working rep completion from device counter
    if (workingRepsDelta > 0) {
        workingReps = deviceWorkingReps  // Direct assignment from device
        Timber.d("💪 WORKING REP from device: $workingReps/$workingTarget")
        recordTopPosition(posA, posB)
        onRepEvent?.invoke(RepEvent(type = RepType.WORKING_COMPLETED, ...))

        // Check for workout completion (unless AMRAP or Just Lift)
        if (!isJustLift && !isAMRAP && workingTarget > 0 && workingReps >= workingTarget) {
            shouldStop = true
            onRepEvent?.invoke(RepEvent(type = RepType.WORKOUT_COMPLETE, ...))
        }
    }

    // Update last known device rep counts
    lastDeviceWarmupReps = deviceWarmupReps
    lastDeviceWorkingReps = deviceWorkingReps

    // Also track up/down counters for bottom position recording (for range calibration)
    if (lastCompleteCounter != null) {
        val delta = calculateDelta(lastCompleteCounter!!, completeCounter)
        if (delta > 0) {
            recordBottomPosition(posA, posB)
        }
    }
    lastTopCounter = topCounter
    lastCompleteCounter = completeCounter
}
```

**Key Differences:**

| Aspect | Beta 5.1 | Current |
|--------|----------|---------|
| **Source of truth** | App-side counter deltas | Device-provided `repsSetCount` |
| **Rep assignment** | `workingReps++` (increment) | `workingReps = deviceWorkingReps` (direct) |
| **`stopAtTop` handling** | Complex conditional | Removed (simplified to just target check) |
| **Counter purpose** | Rep counting | Haptic event detection only |

**Why:** The firmware has better visibility into actual rep completion (ROM validation, timing). App-side counting could drift from device state. Direct assignment ensures perfect sync.

---

## 3. MainViewModel Changes

### 3.1 Auto-Stop Logic Simplification

**BEFORE (Complex danger zone calculation):**
```kotlin
private fun checkAutoStop(metric: WorkoutMetric) {
    val hasMeaningful = repCounter.hasMeaningfulRange()
    val params = _workoutParameters.value

    if (!hasMeaningful) {
        if (params.isAMRAP || params.isJustLift) {
            Timber.d("⚠️ auto-stop blocked: NO meaningful range yet")
        }
        resetAutoStopTimer()
        return
    }

    val inDangerZone = repCounter.isInDangerZone(metric.positionA, metric.positionB)
    val repRanges = repCounter.getRepRanges()

    // Check cable A: is it in danger zone AND released?
    if (repRanges.minPosA != null && repRanges.maxPosA != null) {
        val rangeA = repRanges.maxPosA!! - repRanges.minPosA!!
        if (rangeA > 50) {
            val thresholdA = repRanges.minPosA!! + (rangeA * 0.05f).toInt()
            val cableAInDanger = metric.positionA <= thresholdA
            val cableAReleased = metric.positionA.toDouble() < HANDLE_REST_THRESHOLD ||
                                 (metric.positionA - repRanges.minPosA!!) < 10
            if (cableAInDanger && cableAReleased) {
                cableInDangerAndReleased = true
            }
        }
    }
    // ... similar logic for cable B

    // Auto-stop triggers when BOTH conditions met
    if (inDangerZone && cableInDangerAndReleased) {
        // Start timer...
    }
}
```

**AFTER (Simple handle rest detection):**
```kotlin
private fun checkAutoStop(metric: WorkoutMetric) {
    val params = _workoutParameters.value

    // Just Lift / AMRAP Auto-Stop Logic
    // Stop if handles are put down (position < 2.5)
    // This uses the same threshold as the official app for "rest" detection
    val HANDLE_REST_THRESHOLD = 2.5
    val posA = metric.positionA.toDouble()
    val posB = metric.positionB.toDouble()

    // Check if BOTH handles are at rest
    val handlesAtRest = posA < HANDLE_REST_THRESHOLD && posB < HANDLE_REST_THRESHOLD

    if (handlesAtRest) {
        val startTime = autoStopStartTime ?: run {
            autoStopStartTime = System.currentTimeMillis()
            Timber.d("🔴 Auto-stop timer STARTED - handles at rest (posA=$posA, posB=$posB)")
            System.currentTimeMillis()
        }

        val elapsed = System.currentTimeMillis() - startTime
        val progress = (elapsed.toFloat() / AUTO_STOP_DELAY_MS).coerceIn(0f, 1f)
        _autoStopState.value = AutoStopUiState(
            isActive = true,
            progress = progress,
            secondsRemaining = ceil((AUTO_STOP_DELAY_MS - elapsed) / 1000f).toInt().coerceAtLeast(0)
        )

        if (elapsed >= AUTO_STOP_DELAY_MS) {
            triggerAutoStop()
        }
    } else {
        if (autoStopStartTime != null) {
            Timber.d("🟢 Auto-stop timer RESET (handles moved: posA=$posA, posB=$posB)")
        }
        resetAutoStopTimer()
    }
}
```

**Why:** The danger zone calculation was complex and error-prone. Simple handle rest detection (position < 2.5 for BOTH handles) is more reliable and matches the official app.

---

### 3.2 Bodyweight Exercise Detection

**BEFORE:**
```kotlin
/**
 * Check if the given exercise is a bodyweight exercise with duration mode.
 * Bodyweight exercises are identified by empty equipment field and must have duration set.
 */
private fun isBodyweightExercise(exercise: RoutineExercise?): Boolean {
    return exercise?.let {
        it.exercise.equipment.isEmpty() && it.duration != null  // Required duration
    } ?: false
}
```

**AFTER:**
```kotlin
/**
 * Check if the given exercise is a bodyweight exercise.
 * Bodyweight exercises don't use cables and should skip BLE commands and warmup sets.
 * Identified by empty equipment field OR equipment = "bodyweight".
 */
private fun isBodyweightExercise(exercise: RoutineExercise?): Boolean {
    return exercise?.let {
        val equipment = it.exercise.equipment
        equipment.isEmpty() || equipment.equals("bodyweight", ignoreCase = true)  // No duration required
    } ?: false
}
```

**Why:** Previously, bodyweight exercises required a duration to be detected. Now they're detected purely by equipment field, allowing non-timed bodyweight exercises.

---

### 3.3 Per-Set Weight Support (Issue #147)

**BEFORE:**
```kotlin
// When moving to next set
_currentSetIndex.value++
val targetReps = currentExercise.setReps[_currentSetIndex.value]
_workoutParameters.value = workoutParameters.value.copy(
    reps = targetReps ?: 0,
    // Weight stays the same as previous set
    weightPerCableKg = workoutParameters.value.weightPerCableKg,
    ...
)
```

**AFTER:**
```kotlin
// When moving to next set
_currentSetIndex.value++
val targetReps = currentExercise.setReps[_currentSetIndex.value]
// NEW: Get per-set weight, falling back to exercise default (Issue #147)
val setWeight = currentExercise.setWeightsPerCableKg.getOrNull(_currentSetIndex.value)
    ?: currentExercise.weightPerCableKg
Timber.d("  Set weight: $setWeight kg")
_workoutParameters.value = workoutParameters.value.copy(
    reps = targetReps ?: 0,
    weightPerCableKg = setWeight,  // Use per-set weight
    ...
)
```

**Why:** Supports pyramid sets and drop sets where each set has a different weight.

---

## 4. New Domain Models

### 4.1 SampleStatus.kt (Status Bit Flags)

```kotlin
/**
 * Status flags from the Vitruvian monitor characteristic.
 * These flags indicate various states and safety events during exercise.
 */
enum class SampleStatus(val mask: Int) {
    REP_TOP_READY(0x0001),     // Reached top position
    REP_BOTTOM_READY(0x0002),  // Reached bottom position
    ROM_OUTSIDE_HIGH(0x0004),  // Exceeded max ROM
    ROM_OUTSIDE_LOW(0x0008),   // Below min ROM
    ROM_UNLOAD_ACTIVE(0x0010), // Unloading phase active
    SPOTTER_ACTIVE(0x0020),    // Spotter engaged
    DELOAD_WARN(0x0040),       // Force cap approaching
    DELOAD_OCCURRED(0x8000);   // Force capped/unloaded

    fun isSet(flags: Int): Boolean = (flags and mask) != 0
}
```

### 4.2 SafetyEventSummary.kt

```kotlin
/**
 * Summary of safety events that occurred during a workout.
 */
data class SafetyEventSummary(
    val safetyFlags: Int = 0,
    val deloadWarnings: Int = 0,
    val romViolations: Int = 0,
    val spotterActivations: Int = 0
) {
    val hasSafetyEvents: Boolean
        get() = deloadWarnings > 0 || romViolations > 0 || spotterActivations > 0
}
```

### 4.3 HeuristicStatistics.kt (Phase Metrics)

```kotlin
data class HeuristicPhaseStatistics(
    val kgAvg: Float,   // Average force in kg
    val kgMax: Float,   // Max force in kg
    val velAvg: Float,  // Average velocity
    val velMax: Float,  // Max velocity
    val wattAvg: Float, // Average power in watts
    val wattMax: Float  // Max power in watts
)

data class HeuristicStatistics(
    val concentric: HeuristicPhaseStatistics,
    val eccentric: HeuristicPhaseStatistics,
    val timestamp: Long = System.currentTimeMillis()
)
```

---

## 5. Database Changes

### 5.1 Version 22 → 23

**WorkoutDatabase.kt Changes:**
```kotlin
@Database(
    entities = [
        WorkoutSessionEntity::class,
        WorkoutMetricEntity::class,
        RoutineEntity::class,
        RoutineExerciseEntity::class,
        WeeklyProgramEntity::class,
        ProgramDayEntity::class,
        PersonalRecordEntity::class,
        PhaseStatisticsEntity::class,   // NEW
        DiagnosticsEntity::class         // NEW
    ],
    version = 23,  // Changed from 22
    ...
)
abstract class WorkoutDatabase : RoomDatabase() {
    abstract fun workoutDao(): WorkoutDao
    abstract fun personalRecordDao(): PersonalRecordDao
    abstract fun phaseStatisticsDao(): PhaseStatisticsDao  // NEW
    abstract fun diagnosticsDao(): DiagnosticsDao          // NEW
}
```

### 5.2 WorkoutSessionEntity - Safety Tracking Fields

**BEFORE:**
```kotlin
@Entity(tableName = "workout_sessions")
data class WorkoutSessionEntity(
    @PrimaryKey val id: String,
    val timestamp: Long,
    val mode: String,
    val reps: Int,
    val weightPerCableKg: Float,
    // ... other fields
)
```

**AFTER:**
```kotlin
@Entity(tableName = "workout_sessions")
data class WorkoutSessionEntity(
    @PrimaryKey val id: String,
    val timestamp: Long,
    val mode: String,
    val reps: Int,
    val weightPerCableKg: Float,
    // ... other fields

    // Safety Tracking (added in v23)
    val safetyFlags: Int = 0,
    val deloadWarningCount: Int = 0,
    val romViolationCount: Int = 0,
    val spotterActivations: Int = 0
)
```

---

## 6. Repository Changes

### 6.1 WorkoutRepository Constructor

**BEFORE:**
```kotlin
@Singleton
class WorkoutRepository @Inject constructor(
    private val workoutDao: WorkoutDao,
    private val personalRecordDao: PersonalRecordDao
)
```

**AFTER:**
```kotlin
@Singleton
class WorkoutRepository @Inject constructor(
    private val workoutDao: WorkoutDao,
    private val personalRecordDao: PersonalRecordDao,
    private val phaseStatisticsDao: PhaseStatisticsDao,  // NEW
    private val diagnosticsDao: DiagnosticsDao           // NEW
)
```

### 6.2 saveMetrics() Simplified

**BEFORE:**
```kotlin
suspend fun saveMetrics(sessionId: String, metrics: List<WorkoutMetric>): Result<Unit> {
    return try {
        val entities = metrics.map { metric ->
            WorkoutMetricEntity(
                sessionId = sessionId,
                timestamp = metric.timestamp,
                loadA = metric.loadA,
                loadB = metric.loadB,
                positionA = metric.positionA,
                positionB = metric.positionB,
                ticks = metric.ticks
            )
        }
        workoutDao.insertMetrics(entities)
        Timber.d("Saved ${entities.size} metrics for session $sessionId")
        Result.success(Unit)
    } catch (e: Exception) {
        Timber.e(e, "Failed to save workout metrics")
        Result.failure(e)
    }
}
```

**AFTER:**
```kotlin
suspend fun saveMetrics(sessionId: String, metrics: List<WorkoutMetric>): Result<Unit> {
    return try {
        // Uses mapper extension function
        workoutDao.insertMetrics(metrics.mapIndexed { index, metric ->
            metric.toEntity(sessionId, index)
        })
        Result.success(Unit)
    } catch (e: Exception) {
        Timber.e(e, "Failed to save workout metrics")
        Result.failure(e)
    }
}
```

---

## 7. Code Organization - Extracted Files

### 7.1 BleConstants.kt (from Constants.kt)

```kotlin
// NEW FILE: util/BleConstants.kt

object BleConstants {
    // UUIDs
    val GATT_SERVICE_UUID = UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e")
    val NUS_RX_CHAR_UUID = UUID.fromString("6e400002-b5a3-f393-e0a9-e50e24dcca9e")
    val MONITOR_CHAR_UUID = UUID.fromString("6e400003-b5a3-f393-e0a9-e50e24dcca9e")
    val DIAGNOSTIC_CHAR_UUID = UUID.fromString("6e400004-b5a3-f393-e0a9-e50e24dcca9e")
    val REP_NOTIFY_CHAR_UUID = UUID.fromString("6e400005-b5a3-f393-e0a9-e50e24dcca9e")
    val HEURISTIC_CHAR_UUID = UUID.fromString("6e400006-b5a3-f393-e0a9-e50e24dcca9e")  // NEW
    val VERSION_CHAR_UUID = UUID.fromString("6e400007-b5a3-f393-e0a9-e50e24dcca9e")    // NEW

    val NOTIFY_CHAR_UUIDS = listOf(MONITOR_CHAR_UUID, REP_NOTIFY_CHAR_UUID)

    // Device identification
    const val DEVICE_NAME_PREFIX = "Vee"

    // Timeouts
    const val CONNECTION_TIMEOUT_MS = 15000L
    const val GATT_OPERATION_TIMEOUT_MS = 5000L
    const val SCAN_TIMEOUT_MS = 30000L
    const val BLE_QUEUE_DRAIN_DELAY_MS = 250L
}
```

### 7.2 WorkoutRepositoryMappers.kt (from WorkoutRepository.kt)

~170 lines of mapper functions extracted to separate file for better organization:

```kotlin
// NEW FILE: data/repository/WorkoutRepositoryMappers.kt

fun WorkoutSessionEntity.toWorkoutSession() = WorkoutSession(...)
fun WorkoutSession.toEntity() = WorkoutSessionEntity(...)
fun WorkoutMetricEntity.toWorkoutMetric() = WorkoutMetric(...)
fun WorkoutMetric.toEntity(sessionId: String, index: Int) = WorkoutMetricEntity(...)
fun Routine.toEntity() = RoutineEntity(...)
fun RoutineEntity.toRoutine(exercises: List<RoutineExercise>) = Routine(...)
fun RoutineExercise.toEntity(routineId: String) = RoutineExerciseEntity(...)
fun RoutineExerciseEntity.toRoutineExercise() = RoutineExercise(...)
fun HeuristicStatistics.toPhaseStatisticsEntity(sessionId: String) = PhaseStatisticsEntity(...)

// Helper functions
fun List<Int?>.toJsonArray(): String = ...
fun parseIntListFromJson(json: String): List<Int?> = ...
```

---

## Summary of Architecture Changes

| Change | Impact |
|--------|--------|
| **Rep counting via device** | Perfect sync with firmware, no drift |
| **60Hz monitor polling** | Smoother real-time data visualization |
| **WaitingForRest state** | Prevents false auto-starts |
| **Diagnostic parsing** | Exposes device health data (faults, temps) |
| **Phase statistics** | Concentric/eccentric power metrics |
| **Safety tracking** | Visibility into protective interventions |
| **Per-set weights** | Support for pyramid/drop sets |
| **Code extraction** | Better separation of concerns |
