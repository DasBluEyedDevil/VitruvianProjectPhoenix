package com.example.vitruvianredux.domain.usecase

import com.example.vitruvianredux.domain.model.RepCount
import com.example.vitruvianredux.domain.model.RepEvent
import com.example.vitruvianredux.domain.model.RepType
import kotlin.math.max

/**
 * Handles rep counting based on notifications emitted by the Vitruvian machine.
 *
 * This is a direct port of the logic used by the reference web application. Rather than trying to
 * infer reps from position data, we track the counters supplied by the hardware (u16 values) and
 * supplement them with light position tracking for range calibration and auto-stop support.
 */
class RepCounterFromMachine {

    private var warmupReps = 0
    private var workingReps = 0
    private var warmupTarget = 3
    private var workingTarget = 0
    private var isJustLift = false
    private var stopAtTop = false
    private var shouldStop = false

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
        stopAtTop: Boolean
    ) {
        this.warmupTarget = warmupTarget
        this.workingTarget = workingTarget
        this.isJustLift = isJustLift
        this.stopAtTop = stopAtTop
    }

    fun reset() {
        warmupReps = 0
        workingReps = 0
        shouldStop = false
        lastTopCounter = null
        lastCompleteCounter = null
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

    fun process(topCounter: Int, completeCounter: Int, posA: Int = 0, posB: Int = 0) {

        if (lastTopCounter != null) {
            val topDelta = calculateDelta(lastTopCounter!!, topCounter)
            if (topDelta > 0) {
                recordTopPosition(posA, posB)

                // SAFETY FIX: Stop at top position AFTER completing target reps
                // This ensures full tension through both concentric and eccentric of final rep
                // Changed from (workingTarget - 1) to (workingTarget) so we don't release early
                if (stopAtTop && !isJustLift && workingTarget > 0 && workingReps >= workingTarget) {
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
        }
        lastTopCounter = topCounter

        if (lastCompleteCounter == null) {
            lastCompleteCounter = completeCounter
            return  // Skip first signal to establish baseline
        }

        val delta = calculateDelta(lastCompleteCounter!!, completeCounter)
        if (delta <= 0) {
            return
        }

        lastCompleteCounter = completeCounter

        recordBottomPosition(posA, posB)

        val totalReps = warmupReps + workingReps + 1
        if (totalReps <= warmupTarget) {
            warmupReps++
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
        } else {
            workingReps++
            onRepEvent?.invoke(
                RepEvent(
                    type = RepType.WORKING_COMPLETED,
                    warmupCount = warmupReps,
                    workingCount = workingReps
                )
            )

            // Only stop at bottom if stopAtTop is disabled
            // Most users should use stopAtTop (safer) but this preserves old behavior for those who want it
            if (!stopAtTop && !isJustLift && workingTarget > 0 && workingReps >= workingTarget) {
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
