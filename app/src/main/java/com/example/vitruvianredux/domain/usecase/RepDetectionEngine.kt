package com.example.vitruvianredux.domain.usecase

import com.example.vitruvianredux.domain.model.RepEvent
import com.example.vitruvianredux.domain.model.RepType
import timber.log.Timber

/**
 * Rep detection engine ported from web app app
 * Tracks position data and detects rep transitions using rolling windows
 */
class RepDetectionEngine {
    
    // Rolling windows for position tracking
    private val topPositionsA = mutableListOf<Int>()
    private val bottomPositionsA = mutableListOf<Int>()
    private val topPositionsB = mutableListOf<Int>()
    private val bottomPositionsB = mutableListOf<Int>()
    
    // Discovered rep ranges (rolling averages)
    private var minRepPosA: Int? = null
    private var maxRepPosA: Int? = null
    private var minRepPosB: Int? = null
    private var maxRepPosB: Int? = null
    
    // Range uncertainty (for visualization)
    private var minRepPosARange: Pair<Int, Int>? = null
    private var maxRepPosARange: Pair<Int, Int>? = null
    private var minRepPosBRange: Pair<Int, Int>? = null
    private var maxRepPosBRange: Pair<Int, Int>? = null
    
    // Rep counters
    private var warmupReps = 0
    private var workingReps = 0
    private var warmupTarget = 3
    private var targetReps = 0
    
    // Calibration state - track warmup ranges
    private var warmupMaxPositions = mutableListOf<Int>()
    private var warmupMinPositions = mutableListOf<Int>()
    private var isCalibrated = false
    private var isWarmupsComplete = false
    
    // State tracking
    private var lastTopCounter: Int? = null
    private var isAtTop = false
    private var isAtBottom = true
    
    // Window size for rolling averages
    private val windowSize = 10
    
    // Position threshold for detecting movement
    private val positionChangeThreshold = 50
    
    // Minimum range required before rep detection starts (prevents false triggers at rest)
    private val minimumRangeRequired = 100
    
    /**
     * Process new position data and detect rep events
     * @param posA Cable A position (0-1000)
     * @param posB Cable B position (0-1000)
     * @param ticks Tick counter
     * @return RepEvent if a rep transition occurred, null otherwise
     */
    fun processPosition(posA: Int, posB: Int, ticks: Int): RepEvent? {
        // Update position windows during warmup reps to collect calibration data
        // Once warmups are complete, lock to the averaged calibration values
        if (!isWarmupsComplete) {
            updatePositionWindows(posA, posB)
            if (ticks % 50 == 0) {
                Timber.v("Updating windows: warmupReps=$warmupReps, isWarmupsComplete=$isWarmupsComplete, maxPos=${maxRepPosA}, minPos=${minRepPosA}")
            }
        } else {
            if (ticks % 50 == 0) {
                Timber.v("Windows LOCKED: warmupReps=$warmupReps, maxPos=${maxRepPosA}, minPos=${minRepPosA}")
            }
        }
        
        // Detect rep transitions
        return detectRepTransition(posA, posB, ticks)
    }
    
    private fun updatePositionWindows(posA: Int, posB: Int) {
        // Cable A - Track positions near the top and bottom
        val currentMax = maxRepPosA
        val currentMin = minRepPosA
        
        // Bootstrap: On first call, just track the position
        if (currentMax == null && currentMin == null) {
            topPositionsA.add(posA)
            bottomPositionsA.add(posA)
            maxRepPosA = posA
            minRepPosA = posA
            maxRepPosARange = Pair(posA, posA)
            minRepPosARange = Pair(posA, posA)
            return
        }
        
        // Update top window if near or above current max
        if (currentMax != null && posA >= currentMax - positionChangeThreshold) {
            topPositionsA.add(posA)
            if (topPositionsA.size > windowSize) {
                topPositionsA.removeAt(0)
            }
            maxRepPosA = topPositionsA.average().toInt()
            maxRepPosARange = Pair(topPositionsA.minOrNull() ?: 0, topPositionsA.maxOrNull() ?: 0)
        }
        
        // Update bottom window if near or below current min
        if (currentMin != null && posA <= currentMin + positionChangeThreshold) {
            bottomPositionsA.add(posA)
            if (bottomPositionsA.size > windowSize) {
                bottomPositionsA.removeAt(0)
            }
            minRepPosA = bottomPositionsA.average().toInt()
            minRepPosARange = Pair(bottomPositionsA.minOrNull() ?: 0, bottomPositionsA.maxOrNull() ?: 0)
        }
        
        // Cable B - same logic
        val currentMaxB = maxRepPosB ?: 0
        val currentMinB = minRepPosB ?: Int.MAX_VALUE
        
        if (posB > currentMaxB || (currentMaxB > 0 && posB >= currentMaxB - positionChangeThreshold)) {
            topPositionsB.add(posB)
            if (topPositionsB.size > windowSize) {
                topPositionsB.removeAt(0)
            }
            maxRepPosB = topPositionsB.average().toInt()
            maxRepPosBRange = Pair(topPositionsB.minOrNull() ?: 0, topPositionsB.maxOrNull() ?: 0)
        }
        
        if (posB < currentMinB || (currentMinB < Int.MAX_VALUE && posB <= currentMinB + positionChangeThreshold)) {
            bottomPositionsB.add(posB)
            if (bottomPositionsB.size > windowSize) {
                bottomPositionsB.removeAt(0)
            }
            minRepPosB = bottomPositionsB.average().toInt()
            minRepPosBRange = Pair(bottomPositionsB.minOrNull() ?: 0, bottomPositionsB.maxOrNull() ?: 0)
        }
    }
    
    private fun detectRepTransition(posA: Int, posB: Int, @Suppress("UNUSED_PARAMETER") ticks: Int): RepEvent? {
        val avgPos = (posA + posB) / 2
        
        // Wait until we have learned some position data
        val maxPos = maxRepPosA
        val minPos = minRepPosA
        
        if (maxPos == null || minPos == null) {
            // Still learning initial positions
            return null
        }
        
        // Require significant movement before counting reps (prevents false triggers)
        // Only check this during initial calibration
        if (!isCalibrated) {
            val currentRange = maxPos - minPos
            if (currentRange < minimumRangeRequired) {
                // Not enough movement yet, still calibrating
                return null
            }
            // We have enough range now, mark as calibrated
            isCalibrated = true
            Timber.d("RepDetection calibrated: range=$currentRange (min=$minPos, max=$maxPos)")
        }
        
        // Detect at top position
        val atTop = avgPos >= (maxPos - positionChangeThreshold)
        
        // Detect at bottom position
        val atBottom = avgPos <= (minPos + positionChangeThreshold)
        
        // Debug logging - log every single evaluation!
        if (ticks % 10 == 0 || atTop || atBottom) {
            Timber.v("RepState: pos=$avgPos, atTop=$atTop($maxPos), atBottom=$atBottom($minPos), stateTop=$isAtTop, stateBot=$isAtBottom, warmup=$warmupReps, working=$workingReps")
        }
        
        // Detect transition from bottom to top (rep start)
        if (atTop && !isAtTop && isAtBottom) {
            isAtTop = true
            isAtBottom = false
            
            Timber.d("🔥 REP DETECTED! Transitioning from bottom to top")
            
            // Count as warmup or working rep
            return if (warmupReps < warmupTarget) {
                warmupReps++
                
                // Capture this warmup rep's range for calibration
                val currentMax = maxRepPosA ?: 0
                val currentMin = minRepPosA ?: 0
                warmupMaxPositions.add(currentMax)
                warmupMinPositions.add(currentMin)
                
                Timber.d("Warmup rep completed: $warmupReps/$warmupTarget (max=$currentMax, min=$currentMin)")
                
                // If warmups are complete, calculate average calibration
                if (warmupReps == warmupTarget) {
                    isWarmupsComplete = true
                    
                    // Average the warmup ranges
                    val avgMax = warmupMaxPositions.average().toInt()
                    val avgMin = warmupMinPositions.average().toInt()
                    
                    // Lock to averaged calibration values
                    maxRepPosA = avgMax
                    minRepPosA = avgMin
                    
                    Timber.d("🎯 WARMUP CALIBRATION COMPLETE! Averaged ${warmupMaxPositions.size} reps:")
                    Timber.d("   Max: ${warmupMaxPositions.joinToString(", ")} → $avgMax")
                    Timber.d("   Min: ${warmupMinPositions.joinToString(", ")} → $avgMin")
                    Timber.d("   Range locked at: $avgMin - $avgMax")
                }
                
                RepEvent(
                    type = RepType.WARMUP_COMPLETED,
                    warmupCount = warmupReps,
                    workingCount = workingReps,
                    timestamp = System.currentTimeMillis()
                )
            } else {
                workingReps++
                Timber.d("Working rep completed: $workingReps/$targetReps")
                RepEvent(
                    type = RepType.WORKING_COMPLETED,
                    warmupCount = warmupReps,
                    workingCount = workingReps,
                    timestamp = System.currentTimeMillis()
                )
            }
        }
        
        // Detect transition from top to bottom
        if (atBottom && !isAtBottom && isAtTop) {
            isAtBottom = true
            isAtTop = false
            Timber.d("✅ Transitioned to bottom, ready for next rep (avgPos=$avgPos, minPos=$minPos)")
        }
        
        return null
    }
    
    /**
     * Check if target reps have been reached (for auto-stop in Just Lift mode)
     */
    fun shouldAutoStop(isJustLift: Boolean): Boolean {
        if (!isJustLift) return false
        return workingReps >= targetReps
    }
    
    /**
     * Set warmup target
     */
    fun setWarmupTarget(target: Int) {
        warmupTarget = target
        Timber.d("RepDetectionEngine: Warmup target set to $target")
    }
    
    /**
     * Set target working reps
     */
    fun setTargetReps(target: Int) {
        targetReps = target
        Timber.d("RepDetectionEngine: Working reps target set to $target")
    }
    
    /**
     * Get current rep counts
     */
    fun getRepCounts(): Pair<Int, Int> {
        return Pair(warmupReps, workingReps)
    }
    
    /**
     * Get rep ranges for visualization
     */
    fun getRepRanges(): RepRanges {
        return RepRanges(
            minPosA = minRepPosA ?: 0,
            maxPosA = maxRepPosA ?: 1000,
            minPosB = minRepPosB ?: 0,
            maxPosB = maxRepPosB ?: 1000,
            minRangeA = minRepPosARange,
            maxRangeA = maxRepPosARange,
            minRangeB = minRepPosBRange,
            maxRangeB = maxRepPosBRange
        )
    }
    
    /**
     * Reset all rep tracking
     */
    fun reset() {
        topPositionsA.clear()
        bottomPositionsA.clear()
        topPositionsB.clear()
        bottomPositionsB.clear()
        
        minRepPosA = null
        maxRepPosA = null
        minRepPosB = null
        maxRepPosB = null
        
        minRepPosARange = null
        maxRepPosARange = null
        minRepPosBRange = null
        maxRepPosBRange = null
        
        warmupReps = 0
        workingReps = 0
        
        lastTopCounter = null
        isAtTop = false
        isAtBottom = true
        isCalibrated = false
        
        Timber.d("Rep detection engine reset")
    }
}

/**
 * Rep ranges data class for visualization
 */
data class RepRanges(
    val minPosA: Int,
    val maxPosA: Int,
    val minPosB: Int,
    val maxPosB: Int,
    val minRangeA: Pair<Int, Int>?,
    val maxRangeA: Pair<Int, Int>?,
    val minRangeB: Pair<Int, Int>?,
    val maxRangeB: Pair<Int, Int>?
)
