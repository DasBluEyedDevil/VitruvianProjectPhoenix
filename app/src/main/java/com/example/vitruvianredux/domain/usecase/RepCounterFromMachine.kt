package com.example.vitruvianredux.domain.usecase

import com.example.vitruvianredux.domain.model.RepCount
import com.example.vitruvianredux.domain.model.RepEvent
import com.example.vitruvianredux.domain.model.RepType
import timber.log.Timber
import kotlin.math.max

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
class RepCounterFromMachine {

    private var warmupReps = 0
    private var workingReps = 0
    private var warmupTarget = 3
    private var workingTarget = 0
    private var isJustLift = false
    private var stopAtTop = false
    private var shouldStop = false
    private var isAMRAP = false

    // Track device-provided rep counts (official app method)
    private var lastDeviceWarmupReps = 0
    private var lastDeviceWorkingReps = 0

    private var lastTopCounter: Int? = null
    private var lastCompleteCounter: Int? = null

    private val topPositionsA = mutableListOf<Int>()
    private val topPositionsB = mutableListOf<Int>()
    private val bottomPositionsA = mutableListOf<Int>()
    private val bottomPositionsB = mutableListOf<Int>()

    private var maxRepPosA: Int? = null
    private var minRepPosA: Int? = null
    private var maxRepPosB: Int? = null
    private var minRepPosB: Int? = null

    private var maxRepPosARange: Pair<Int, Int>? = null
    private var minRepPosARange: Pair<Int, Int>? = null
    private var maxRepPosBRange: Pair<Int, Int>? = null
    private var minRepPosBRange: Pair<Int, Int>? = null

    var onRepEvent: ((RepEvent) -> Unit)? = null

    fun configure(
        warmupTarget: Int,
        workingTarget: Int,
        isJustLift: Boolean,
        stopAtTop: Boolean,
        isAMRAP: Boolean = false
    ) {
        this.warmupTarget = warmupTarget
        this.workingTarget = workingTarget
        this.isJustLift = isJustLift
        this.stopAtTop = stopAtTop
        this.isAMRAP = isAMRAP

        // Log RepCounter configuration
        Timber.d("🔧 RepCounter.configure() called:")
        Timber.d("  warmupTarget: $warmupTarget")
        Timber.d("  workingTarget: $workingTarget")
        Timber.d("  isJustLift: $isJustLift")
        Timber.d("  stopAtTop: $stopAtTop")
        Timber.d("  isAMRAP: $isAMRAP")
    }

    fun reset() {
        warmupReps = 0
        workingReps = 0
        shouldStop = false
        lastTopCounter = null
        lastCompleteCounter = null
        lastDeviceWarmupReps = 0
        lastDeviceWorkingReps = 0
        topPositionsA.clear()
        topPositionsB.clear()
        bottomPositionsA.clear()
        bottomPositionsB.clear()
        maxRepPosA = null
        minRepPosA = null
        maxRepPosB = null
        minRepPosB = null
        maxRepPosARange = null
        minRepPosARange = null
        maxRepPosBRange = null
        minRepPosBRange = null
    }

    /**
     * Sets the initial baseline position when the workout starts (after countdown completes).
     * This calibrates the position bars to the starting rope position, so bars show 0% at
     * the starting position rather than showing raw machine values.
     *
     * The baseline will be refined as reps are performed through the sliding window calibration.
     */
    fun setInitialBaseline(posA: Int, posB: Int) {
        // Only set initial baseline if positions are valid and not already calibrated
        if (posA > 0 && minRepPosA == null) {
            minRepPosA = posA
            minRepPosARange = Pair(posA, posA)
        }
        if (posB > 0 && minRepPosB == null) {
            minRepPosB = posB
            minRepPosBRange = Pair(posB, posB)
        }
    }

    /**
     * Process rep notification with device-provided rep counts (OFFICIAL APP METHOD).
     *
     * The device firmware tracks reps accurately and provides:
     * - repsRomCount: Warmup reps completed with proper ROM
     * - repsSetCount: Working set reps completed
     *
     * We use topCounter/completeCounter only for detecting rep EVENTS (haptic feedback),
     * but the actual rep COUNT should come from the device's repsRomCount/repsSetCount.
     *
     * @param topCounter u32 up counter from device (for event detection)
     * @param completeCounter u32 down counter from device (for event detection)
     * @param deviceWarmupReps repsRomCount from device (warmup rep count to display)
     * @param deviceWorkingReps repsSetCount from device (working rep count to display)
     * @param posA Position A for range calibration
     * @param posB Position B for range calibration
     */
    fun process(
        topCounter: Int,
        completeCounter: Int,
        deviceWarmupReps: Int = 0,
        deviceWorkingReps: Int = 0,
        posA: Int = 0,
        posB: Int = 0
    ) {
        // OFFICIAL APP METHOD: Use device-provided rep counts directly
        // This ensures rep counting matches exactly what the firmware reports
        val warmupRepsDelta = deviceWarmupReps - lastDeviceWarmupReps
        val workingRepsDelta = deviceWorkingReps - lastDeviceWorkingReps

        // Detect warmup rep completion from device counter
        if (warmupRepsDelta > 0 && deviceWarmupReps <= warmupTarget) {
            warmupReps = deviceWarmupReps
            Timber.d("🏋️ WARMUP REP from device: $warmupReps/$warmupTarget")
            recordTopPosition(posA, posB)
            onRepEvent?.invoke(
                RepEvent(
                    type = RepType.WARMUP_COMPLETED,
                    warmupCount = warmupReps,
                    workingCount = workingReps
                )
            )
            if (warmupReps == warmupTarget) {
                onRepEvent?.invoke(
                    RepEvent(
                        type = RepType.WARMUP_COMPLETE,
                        warmupCount = warmupReps,
                        workingCount = workingReps
                    )
                )
            }
        }

        // Detect working rep completion from device counter
        if (workingRepsDelta > 0) {
            workingReps = deviceWorkingReps
            Timber.d("💪 WORKING REP from device: $workingReps/$workingTarget")
            recordTopPosition(posA, posB)
            onRepEvent?.invoke(
                RepEvent(
                    type = RepType.WORKING_COMPLETED,
                    warmupCount = warmupReps,
                    workingCount = workingReps
                )
            )

            // Check for workout completion (unless AMRAP or Just Lift)
            if (!isJustLift && !isAMRAP && workingTarget > 0 && workingReps >= workingTarget) {
                Timber.d("⚠️ shouldStop set to TRUE (device reports target reached)")
                Timber.d("  isJustLift=$isJustLift, isAMRAP=$isAMRAP")
                Timber.d("  workingTarget=$workingTarget, workingReps=$workingReps")
                shouldStop = true
                onRepEvent?.invoke(
                    RepEvent(
                        type = RepType.WORKOUT_COMPLETE,
                        warmupCount = warmupReps,
                        workingCount = workingReps
                    )
                )
            }
        }

        // Update last known device rep counts
        lastDeviceWarmupReps = deviceWarmupReps
        lastDeviceWorkingReps = deviceWorkingReps

        // Also track up/down counters for bottom position recording
        if (lastCompleteCounter != null) {
            val delta = calculateDelta(lastCompleteCounter!!, completeCounter)
            if (delta > 0) {
                recordBottomPosition(posA, posB)
            }
        }
        lastTopCounter = topCounter
        lastCompleteCounter = completeCounter
    }

    private fun calculateDelta(last: Int, current: Int): Int {
        return if (current >= last) {
            current - last
        } else {
            0xFFFF - last + current + 1
        }
    }

    private fun recordTopPosition(posA: Int, posB: Int) {
        if (posA <= 0 && posB <= 0) return

        val window = getWindowSize()
        if (posA > 0) {
            topPositionsA.add(posA)
            if (topPositionsA.size > window) topPositionsA.removeAt(0)
        }
        if (posB > 0) {
            topPositionsB.add(posB)
            if (topPositionsB.size > window) topPositionsB.removeAt(0)
        }

        updateRepRanges()
    }

    private fun recordBottomPosition(posA: Int, posB: Int) {
        if (posA <= 0 && posB <= 0) return

        val window = getWindowSize()
        if (posA > 0) {
            bottomPositionsA.add(posA)
            if (bottomPositionsA.size > window) bottomPositionsA.removeAt(0)
        }
        if (posB > 0) {
            bottomPositionsB.add(posB)
            if (bottomPositionsB.size > window) bottomPositionsB.removeAt(0)
        }

        updateRepRanges()
    }

    private fun updateRepRanges() {
        if (topPositionsA.isNotEmpty()) {
            maxRepPosA = topPositionsA.average().toInt()
            maxRepPosARange = Pair(topPositionsA.minOrNull() ?: 0, topPositionsA.maxOrNull() ?: 0)
        }
        if (bottomPositionsA.isNotEmpty()) {
            minRepPosA = bottomPositionsA.average().toInt()
            minRepPosARange = Pair(bottomPositionsA.minOrNull() ?: 0, bottomPositionsA.maxOrNull() ?: 0)
        }
        if (topPositionsB.isNotEmpty()) {
            maxRepPosB = topPositionsB.average().toInt()
            maxRepPosBRange = Pair(topPositionsB.minOrNull() ?: 0, topPositionsB.maxOrNull() ?: 0)
        }
        if (bottomPositionsB.isNotEmpty()) {
            minRepPosB = bottomPositionsB.average().toInt()
            minRepPosBRange = Pair(bottomPositionsB.minOrNull() ?: 0, bottomPositionsB.maxOrNull() ?: 0)
        }
    }

    private fun getWindowSize(): Int {
        val total = warmupReps + workingReps
        return if (total < warmupTarget) 2 else 3
    }

    fun getRepCount(): RepCount {
        val total = workingReps  // Exclude warm-up reps from total count
        return RepCount(
            warmupReps = warmupReps,
            workingReps = workingReps,
            totalReps = total,
            isWarmupComplete = warmupReps >= warmupTarget
        )
    }

    fun shouldStopWorkout(): Boolean = shouldStop

    fun getCurrentRepCount(): RepCount = getRepCount()

    fun getCalibratedTopPosition(): Int? = maxRepPosA

    fun getRepRanges(): RepRanges = RepRanges(
        minPosA = minRepPosA,
        maxPosA = maxRepPosA,
        minPosB = minRepPosB,
        maxPosB = maxRepPosB,
        minRangeA = minRepPosARange,
        maxRangeA = maxRepPosARange,
        minRangeB = minRepPosBRange,
        maxRangeB = maxRepPosBRange
    )

    fun hasMeaningfulRange(minRangeThreshold: Int = 50): Boolean {
        val rangeA = if (minRepPosA != null && maxRepPosA != null) maxRepPosA!! - minRepPosA!! else 0
        val rangeB = if (minRepPosB != null && maxRepPosB != null) maxRepPosB!! - minRepPosB!! else 0
        return rangeA > minRangeThreshold || rangeB > minRangeThreshold
    }

    fun isInDangerZone(posA: Int, posB: Int, minRangeThreshold: Int = 50): Boolean {
        val checkA = minRepPosA != null && maxRepPosA != null
        val checkB = minRepPosB != null && maxRepPosB != null
        if (!checkA && !checkB) return false

        var danger = false
        if (checkA) {
            val rangeA = maxRepPosA!! - minRepPosA!!
            if (rangeA > minRangeThreshold) {
                val thresholdA = minRepPosA!! + (rangeA * 0.05f).toInt()
                danger = danger || posA <= thresholdA
            }
        }
        if (checkB) {
            val rangeB = maxRepPosB!! - minRepPosB!!
            if (rangeB > minRangeThreshold) {
                val thresholdB = minRepPosB!! + (rangeB * 0.05f).toInt()
                danger = danger || posB <= thresholdB
            }
        }
        return danger
    }
}

/**
 * Snapshot of the discovered rep ranges for UI/diagnostics.
 */
data class RepRanges(
    val minPosA: Int?,
    val maxPosA: Int?,
    val minPosB: Int?,
    val maxPosB: Int?,
    val minRangeA: Pair<Int, Int>?,
    val maxRangeA: Pair<Int, Int>?,
    val minRangeB: Pair<Int, Int>?,
    val maxRangeB: Pair<Int, Int>?
) {
    val rangeA: Int?
        get() = if (minPosA != null && maxPosA != null) max(maxPosA!! - minPosA!!, 0) else null
    val rangeB: Int?
        get() = if (minPosB != null && maxPosB != null) max(maxPosB!! - minPosB!!, 0) else null
}
