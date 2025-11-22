package com.example.vitruvianredux.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.vitruvianredux.data.local.ConnectionLogDao
import com.example.vitruvianredux.data.local.entity.ConnectionLogEntity
import com.example.vitruvianredux.data.logger.ConnectionLogger
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for viewing and managing connection logs
 */
@HiltViewModel
class ConnectionLogsViewModel @Inject constructor(
    private val connectionLogDao: ConnectionLogDao,
    private val connectionLogger: ConnectionLogger,
    private val workoutRepository: com.example.vitruvianredux.data.repository.WorkoutRepository,
    private val exerciseRepository: com.example.vitruvianredux.data.repository.ExerciseRepository
) : ViewModel() {

    // Filter state
    private val _selectedLevelFilter = MutableStateFlow<String?>(null)
    val selectedLevelFilter: StateFlow<String?> = _selectedLevelFilter

    private val _selectedEventTypeFilter = MutableStateFlow<String?>(null)
    val selectedEventTypeFilter: StateFlow<String?> = _selectedEventTypeFilter

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    // All logs from database
    private val allLogs = connectionLogDao.getAllLogs()

    // Filtered logs based on current filters
    val filteredLogs: StateFlow<List<ConnectionLogEntity>> = combine(
        allLogs,
        _selectedLevelFilter,
        _selectedEventTypeFilter,
        _searchQuery
    ) { logs, levelFilter, eventTypeFilter, query ->
        logs.filter { log ->
            // Apply level filter
            val matchesLevel = levelFilter == null || log.level == levelFilter

            // Apply event type filter
            val matchesEventType = eventTypeFilter == null || log.eventType == eventTypeFilter

            // Apply search query
            val matchesSearch = query.isBlank() ||
                log.message.contains(query, ignoreCase = true) ||
                log.deviceName?.contains(query, ignoreCase = true) == true ||
                log.eventType.contains(query, ignoreCase = true) ||
                log.details?.contains(query, ignoreCase = true) == true

            matchesLevel && matchesEventType && matchesSearch
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // Log statistics
    val logStats: StateFlow<LogStats> = allLogs.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    ).combine(_selectedLevelFilter) { logs, _ ->
        LogStats(
            total = logs.size,
            errors = logs.count { it.level == "ERROR" },
            warnings = logs.count { it.level == "WARNING" },
            info = logs.count { it.level == "INFO" },
            debug = logs.count { it.level == "DEBUG" }
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = LogStats(0, 0, 0, 0, 0)
    )

    /**
     * Set level filter (ERROR, WARNING, INFO, DEBUG)
     */
    fun setLevelFilter(level: String?) {
        _selectedLevelFilter.value = level
    }

    /**
     * Set event type filter
     */
    fun setEventTypeFilter(eventType: String?) {
        _selectedEventTypeFilter.value = eventType
    }

    /**
     * Set search query
     */
    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    /**
     * Clear all logs
     */
    fun clearAllLogs() {
        viewModelScope.launch {
            connectionLogger.clearAllLogs()
        }
    }

    /**
     * Export logs as text
     */
    suspend fun exportLogsAsText(): String {
        val logs = connectionLogDao.getAllLogsForExport()

        // Find system info and Vitruvian device info from logs
        val systemInfo = logs.firstOrNull { it.eventType == "SYSTEM_INFO" }
        val vitruvianInfo = logs.firstOrNull { it.eventType == "VITRUVIAN_DEVICE_INFO" }

        return buildString {
            appendLine("═══════════════════════════════════════════════════════")
            appendLine("       VITRUVIAN CONNECTION DEBUG LOG EXPORT")
            appendLine("═══════════════════════════════════════════════════════")
            appendLine()
            appendLine("Generated: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US).format(java.util.Date())}")
            appendLine("Total entries: ${logs.size}")
            appendLine()

            // Android device info
            appendLine("─── Android Device Information ───")
            if (systemInfo != null) {
                appendLine(systemInfo.details ?: "Not available")
            } else {
                appendLine("Device info not logged")
            }
            appendLine()

            // Vitruvian device info
            appendLine("─── Vitruvian Trainer Information ───")
            if (vitruvianInfo != null) {
                appendLine(vitruvianInfo.details ?: "Not connected")
            } else {
                appendLine("No Vitruvian device connected during this session")
            }
            appendLine()
            appendLine("═══════════════════════════════════════════════════════")
            appendLine("                    EVENT LOG")
            appendLine("═══════════════════════════════════════════════════════")
            appendLine()

            logs.forEach { log ->
                val timestamp = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", java.util.Locale.US).format(java.util.Date(log.timestamp))
                appendLine("[$timestamp] [${log.level}] ${log.eventType}")
                if (log.deviceName != null || log.deviceAddress != null) {
                    appendLine("  Device: ${log.deviceName ?: "N/A"} (${log.deviceAddress ?: "N/A"})")
                }
                appendLine("  Message: ${log.message}")
                if (!log.details.isNullOrEmpty()) {
                    // Indent multi-line details
                    val indentedDetails = log.details.lines().joinToString("\n") { "    $it" }
                    appendLine("  Details:")
                    appendLine(indentedDetails)
                }
                if (!log.metadata.isNullOrEmpty()) {
                    appendLine("  Metadata: ${log.metadata}")
                }
                appendLine()
            }

            appendLine("═══════════════════════════════════════════════════════")
            appendLine("                  END OF LOG")
            appendLine("═══════════════════════════════════════════════════════")
            appendLine()
            
            // Add force data from most recent 3 exercises
            appendLine("═══════════════════════════════════════════════════════")
            appendLine("         FORCE DATA - RECENT 3 EXERCISES")
            appendLine("═══════════════════════════════════════════════════════")
            appendLine()
            
            try {
                val recentSessions = workoutRepository.getRecentSessionsSync(limit = 3)
                if (recentSessions.isEmpty()) {
                    appendLine("No recent workout sessions found.")
                } else {
                    recentSessions.forEachIndexed { index, session ->
                        val exerciseName = session.exerciseId?.let { exerciseId ->
                            try {
                                exerciseRepository.getExerciseById(exerciseId)?.name ?: exerciseId
                            } catch (e: Exception) {
                                exerciseId
                            }
                        } ?: "Unknown Exercise"
                        
                        appendLine("─── Exercise ${index + 1}: $exerciseName ───")
                        appendLine("Session ID: ${session.id}")
                        appendLine("Timestamp: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US).format(java.util.Date(session.timestamp))}")
                        appendLine("Mode: ${session.mode}")
                        appendLine("Total Reps: ${session.totalReps}")
                        appendLine()
                        
                        val metrics = workoutRepository.getMetricsForSessionSync(session.id)
                        if (metrics.isEmpty()) {
                            appendLine("  No metrics data available for this session.")
                        } else {
                            val repForceData = analyzeRepForces(metrics)
                            if (repForceData.isEmpty()) {
                                appendLine("  Unable to detect rep boundaries from position data.")
                            } else {
                                repForceData.forEachIndexed { repIndex, repData ->
                                    appendLine("  Rep ${repIndex + 1}:")
                                    appendLine("    Upward Force (Cable A): min=${"%.2f".format(repData.upwardMinA)} kg, max=${"%.2f".format(repData.upwardMaxA)} kg, avg=${"%.2f".format(repData.upwardAvgA)} kg")
                                    appendLine("    Upward Force (Cable B): min=${"%.2f".format(repData.upwardMinB)} kg, max=${"%.2f".format(repData.upwardMaxB)} kg, avg=${"%.2f".format(repData.upwardAvgB)} kg")
                                    appendLine("    Downward Force (Cable A): min=${"%.2f".format(repData.downwardMinA)} kg, max=${"%.2f".format(repData.downwardMaxA)} kg, avg=${"%.2f".format(repData.downwardAvgA)} kg")
                                    appendLine("    Downward Force (Cable B): min=${"%.2f".format(repData.downwardMinB)} kg, max=${"%.2f".format(repData.downwardMaxB)} kg, avg=${"%.2f".format(repData.downwardAvgB)} kg")
                                    appendLine("    Total Upward Force: min=${"%.2f".format(repData.upwardMinA + repData.upwardMinB)} kg, max=${"%.2f".format(repData.upwardMaxA + repData.upwardMaxB)} kg")
                                    appendLine("    Total Downward Force: min=${"%.2f".format(repData.downwardMinA + repData.downwardMinB)} kg, max=${"%.2f".format(repData.downwardMaxA + repData.downwardMaxB)} kg")
                                }
                            }
                        }
                        appendLine()
                    }
                }
            } catch (e: Exception) {
                appendLine("Error retrieving force data: ${e.message}")
                appendLine()
            }
            
            appendLine("═══════════════════════════════════════════════════════")
            appendLine("              END OF FORCE DATA SECTION")
            appendLine("═══════════════════════════════════════════════════════")
        }
    }
    
    /**
     * Analyze metrics to extract rep-level force data
     * Detects reps by position changes (upward = position increasing, downward = position decreasing)
     */
    private fun analyzeRepForces(metrics: List<com.example.vitruvianredux.domain.model.WorkoutMetric>): List<RepForceData> {
        if (metrics.size < 10) return emptyList() // Need minimum data points
        
        val repForceDataList = mutableListOf<RepForceData>()
        
        // Use average position (A and B) to detect movement direction
        val avgPositions = metrics.map { (it.positionA + it.positionB) / 2 }
        
        // Find rep boundaries by detecting position peaks and valleys
        val repBoundaries = mutableListOf<Int>()
        repBoundaries.add(0) // Start of first rep
        
        for (i in 1 until avgPositions.size - 1) {
            val prevPos = avgPositions[i - 1]
            val currPos = avgPositions[i]
            val nextPos = avgPositions[i + 1]
            
            // Detect peak (local maximum) - end of upward phase, start of downward
            if (currPos > prevPos && currPos > nextPos && currPos > 500) {
                repBoundaries.add(i)
            }
            // Detect valley (local minimum) - end of downward phase, start of upward
            else if (currPos < prevPos && currPos < nextPos && currPos > 0) {
                repBoundaries.add(i)
            }
        }
        
        repBoundaries.add(metrics.size - 1) // End of last rep
        
        // Process each rep
        for (repIndex in 0 until repBoundaries.size - 1) {
            val repStart = repBoundaries[repIndex]
            val repEnd = repBoundaries[repIndex + 1]
            
            if (repEnd - repStart < 5) continue // Skip very short segments
            
            val repMetrics = metrics.subList(repStart, repEnd)
            val repPositions = repMetrics.map { (it.positionA + it.positionB) / 2 }
            
            if (repPositions.isEmpty()) continue
            
            // Split rep into upward (concentric) and downward (eccentric) phases
            val upwardMetrics = mutableListOf<com.example.vitruvianredux.domain.model.WorkoutMetric>()
            val downwardMetrics = mutableListOf<com.example.vitruvianredux.domain.model.WorkoutMetric>()
            
            var wasIncreasing = false
            for (i in 1 until repMetrics.size) {
                val prevPos = repPositions[i - 1]
                val currPos = repPositions[i]
                val isIncreasing = currPos > prevPos
                
                if (isIncreasing && !wasIncreasing) {
                    // Transition to upward phase
                    upwardMetrics.add(repMetrics[i])
                } else if (!isIncreasing && wasIncreasing) {
                    // Transition to downward phase
                    downwardMetrics.add(repMetrics[i])
                } else if (isIncreasing) {
                    upwardMetrics.add(repMetrics[i])
                } else {
                    downwardMetrics.add(repMetrics[i])
                }
                
                wasIncreasing = isIncreasing
            }
            
            // Calculate force statistics for upward phase
            val upwardForcesA = upwardMetrics.map { it.loadA }
            val upwardForcesB = upwardMetrics.map { it.loadB }
            
            // Calculate force statistics for downward phase
            val downwardForcesA = downwardMetrics.map { it.loadA }
            val downwardForcesB = downwardMetrics.map { it.loadB }
            
            if (upwardForcesA.isNotEmpty() || downwardForcesA.isNotEmpty()) {
                repForceDataList.add(
                    RepForceData(
                        upwardMinA = upwardForcesA.minOrNull() ?: 0f,
                        upwardMaxA = upwardForcesA.maxOrNull() ?: 0f,
                        upwardAvgA = upwardForcesA.average().toFloat(),
                        upwardMinB = upwardForcesB.minOrNull() ?: 0f,
                        upwardMaxB = upwardForcesB.maxOrNull() ?: 0f,
                        upwardAvgB = upwardForcesB.average().toFloat(),
                        downwardMinA = downwardForcesA.minOrNull() ?: 0f,
                        downwardMaxA = downwardForcesA.maxOrNull() ?: 0f,
                        downwardAvgA = downwardForcesA.average().toFloat(),
                        downwardMinB = downwardForcesB.minOrNull() ?: 0f,
                        downwardMaxB = downwardForcesB.maxOrNull() ?: 0f,
                        downwardAvgB = downwardForcesB.average().toFloat()
                    )
                )
            }
        }
        
        return repForceDataList
    }
    
    /**
     * Data class for rep-level force statistics
     */
    private data class RepForceData(
        val upwardMinA: Float,
        val upwardMaxA: Float,
        val upwardAvgA: Float,
        val upwardMinB: Float,
        val upwardMaxB: Float,
        val upwardAvgB: Float,
        val downwardMinA: Float,
        val downwardMaxA: Float,
        val downwardAvgA: Float,
        val downwardMinB: Float,
        val downwardMaxB: Float,
        val downwardAvgB: Float
    )

    /**
     * Clean up old logs (older than specified days)
     */
    fun cleanupOldLogs(daysToKeep: Int = 7) {
        viewModelScope.launch {
            connectionLogger.cleanupOldLogs(daysToKeep)
        }
    }
}

data class LogStats(
    val total: Int,
    val errors: Int,
    val warnings: Int,
    val info: Int,
    val debug: Int
)
