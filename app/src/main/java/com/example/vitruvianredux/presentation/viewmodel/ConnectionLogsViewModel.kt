package com.example.vitruvianredux.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.vitruvianredux.data.local.ConnectionLogDao
import com.example.vitruvianredux.data.local.ConnectionLogEntity
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
    private val connectionLogger: ConnectionLogger
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
        return buildString {
            appendLine("=== Vitruvian Connection Logs ===")
            appendLine("Generated: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US).format(java.util.Date())}")
            appendLine("Total entries: ${logs.size}")
            appendLine()

            logs.forEach { log ->
                val timestamp = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", java.util.Locale.US).format(java.util.Date(log.timestamp))
                appendLine("[$timestamp] [${log.level}] ${log.eventType}")
                appendLine("  Device: ${log.deviceName ?: "N/A"} (${log.deviceAddress ?: "N/A"})")
                appendLine("  Message: ${log.message}")
                if (!log.details.isNullOrEmpty()) {
                    appendLine("  Details: ${log.details}")
                }
                if (!log.metadata.isNullOrEmpty()) {
                    appendLine("  Metadata: ${log.metadata}")
                }
                appendLine()
            }
        }
    }

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
