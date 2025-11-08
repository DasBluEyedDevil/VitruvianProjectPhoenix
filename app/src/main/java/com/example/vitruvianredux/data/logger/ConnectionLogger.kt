package com.example.vitruvianredux.data.logger

import com.example.vitruvianredux.data.local.ConnectionLogDao
import com.example.vitruvianredux.data.local.ConnectionLogEntity
import com.example.vitruvianredux.util.DeviceInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Centralized logging service for BLE connection debugging
 *
 * Logs events to both:
 * - Timber (console/logcat)
 * - Room database (for persistent history and export)
 */
@Singleton
class ConnectionLogger @Inject constructor(
    private val connectionLogDao: ConnectionLogDao
) {
    private val loggerScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Sample counter for monitor data logging (to avoid flooding)
    @Volatile private var monitorDataSampleCounter = 0

    // Log device info once at startup
    init {
        loggerScope.launch {
            log(
                EventType.SYSTEM_INFO,
                Level.INFO,
                "App started",
                details = DeviceInfo.getFormattedInfo(),
                metadata = DeviceInfo.toJson()
            )
        }
    }

    /**
     * Log levels matching standard logging practices
     */
    enum class Level {
        DEBUG, INFO, WARNING, ERROR
    }

    /**
     * Standard BLE event types for categorization
     */
    object EventType {
        // System info
        const val SYSTEM_INFO = "SYSTEM_INFO"
        const val VITRUVIAN_DEVICE_INFO = "VITRUVIAN_DEVICE_INFO"

        // Connection events
        const val SCAN_STARTED = "SCAN_STARTED"
        const val SCAN_STOPPED = "SCAN_STOPPED"
        const val DEVICE_FOUND = "DEVICE_FOUND"
        const val CONNECTION_STARTED = "CONNECTION_STARTED"
        const val CONNECTION_SUCCESS = "CONNECTION_SUCCESS"
        const val CONNECTION_FAILED = "CONNECTION_FAILED"
        const val DISCONNECTION_STARTED = "DISCONNECTION_STARTED"
        const val DISCONNECTED = "DISCONNECTED"
        const val CONNECTION_LOST = "CONNECTION_LOST"

        // Service discovery
        const val SERVICES_DISCOVERING = "SERVICES_DISCOVERING"
        const val SERVICES_DISCOVERED = "SERVICES_DISCOVERED"
        const val SERVICES_DISCOVERY_FAILED = "SERVICES_DISCOVERY_FAILED"

        // Initialization
        const val INIT_STARTED = "INIT_STARTED"
        const val INIT_SUCCESS = "INIT_SUCCESS"
        const val INIT_FAILED = "INIT_FAILED"

        // Commands
        const val COMMAND_SENT = "COMMAND_SENT"
        const val COMMAND_SUCCESS = "COMMAND_SUCCESS"
        const val COMMAND_FAILED = "COMMAND_FAILED"

        // Data polling
        const val POLLING_STARTED = "POLLING_STARTED"
        const val POLLING_STOPPED = "POLLING_STOPPED"
        const val DATA_RECEIVED = "DATA_RECEIVED"
        const val DATA_PARSE_ERROR = "DATA_PARSE_ERROR"

        // Errors
        const val TIMEOUT = "TIMEOUT"
        const val WRITE_ERROR = "WRITE_ERROR"
        const val READ_ERROR = "READ_ERROR"
        const val UNKNOWN_ERROR = "UNKNOWN_ERROR"
    }

    /**
     * Log a connection event
     */
    fun log(
        eventType: String,
        level: Level,
        message: String,
        deviceAddress: String? = null,
        deviceName: String? = null,
        details: String? = null,
        metadata: String? = null
    ) {
        // Log to Timber for real-time debugging
        val timberMessage = buildString {
            append("[BLE] ")
            append(eventType)
            if (deviceName != null) append(" [$deviceName]")
            append(": ")
            append(message)
            if (details != null) append(" | $details")
        }

        when (level) {
            Level.DEBUG -> Timber.d(timberMessage)
            Level.INFO -> Timber.i(timberMessage)
            Level.WARNING -> Timber.w(timberMessage)
            Level.ERROR -> Timber.e(timberMessage)
        }

        // Persist to database asynchronously
        loggerScope.launch {
            try {
                val logEntity = ConnectionLogEntity(
                    timestamp = System.currentTimeMillis(),
                    eventType = eventType,
                    level = level.name,
                    deviceAddress = deviceAddress,
                    deviceName = deviceName,
                    message = message,
                    details = details,
                    metadata = metadata
                )
                connectionLogDao.insert(logEntity)
            } catch (e: Exception) {
                // Don't let logging errors crash the app
                Timber.e(e, "Failed to persist connection log")
            }
        }
    }

    // Convenience methods for common scenarios

    fun logScanStarted() {
        log(EventType.SCAN_STARTED, Level.INFO, "BLE scan started")
    }

    fun logScanStopped() {
        log(EventType.SCAN_STOPPED, Level.INFO, "BLE scan stopped")
    }

    fun logDeviceFound(deviceName: String, deviceAddress: String) {
        log(
            EventType.DEVICE_FOUND,
            Level.INFO,
            "Device discovered",
            deviceAddress = deviceAddress,
            deviceName = deviceName
        )
    }

    fun logConnectionStarted(deviceName: String, deviceAddress: String) {
        log(
            EventType.CONNECTION_STARTED,
            Level.INFO,
            "Attempting to connect",
            deviceAddress = deviceAddress,
            deviceName = deviceName
        )
    }

    fun logConnectionSuccess(deviceName: String, deviceAddress: String) {
        log(
            EventType.CONNECTION_SUCCESS,
            Level.INFO,
            "Successfully connected",
            deviceAddress = deviceAddress,
            deviceName = deviceName,
            details = buildString {
                appendLine("Vitruvian Device: $deviceName")
                appendLine("MAC Address: $deviceAddress")
                appendLine()
                appendLine("Android Device: ${DeviceInfo.getCompactInfo()}")
            }
        )

        // Also log Vitruvian device info separately for easy filtering
        log(
            EventType.VITRUVIAN_DEVICE_INFO,
            Level.INFO,
            "Connected to Vitruvian device",
            deviceAddress = deviceAddress,
            deviceName = deviceName,
            details = buildString {
                appendLine("Device Name: $deviceName")
                appendLine("MAC Address: $deviceAddress")
                appendLine("Model: ${extractVitruvianModel(deviceName)}")
                appendLine()
                appendLine("Note: Firmware version not available via BLE")
                appendLine("To check firmware: Settings → About on Vitruvian touchscreen")
            },
            metadata = """{"deviceName":"$deviceName","address":"$deviceAddress","model":"${extractVitruvianModel(deviceName)}"}"""
        )
    }

    /**
     * Extract Vitruvian model from device name
     * Device names typically follow pattern "Vee123" or "Vitruvian-XXX"
     */
    private fun extractVitruvianModel(deviceName: String): String {
        return when {
            deviceName.startsWith("Vee") -> "Vitruvian V1/V1+ (${deviceName})"
            deviceName.startsWith("Vitruvian") -> deviceName
            else -> "Unknown Model ($deviceName)"
        }
    }

    fun logConnectionFailed(deviceName: String, deviceAddress: String, error: String) {
        log(
            EventType.CONNECTION_FAILED,
            Level.ERROR,
            "Connection failed",
            deviceAddress = deviceAddress,
            deviceName = deviceName,
            details = error
        )
    }

    fun logDisconnected(deviceName: String?, deviceAddress: String?, reason: String? = null) {
        log(
            EventType.DISCONNECTED,
            Level.WARNING,
            "Device disconnected",
            deviceAddress = deviceAddress,
            deviceName = deviceName,
            details = reason
        )
    }

    fun logConnectionLost(deviceName: String?, deviceAddress: String?) {
        log(
            EventType.CONNECTION_LOST,
            Level.ERROR,
            "Connection lost unexpectedly",
            deviceAddress = deviceAddress,
            deviceName = deviceName
        )
    }

    fun logInitStarted(deviceName: String, deviceAddress: String) {
        log(
            EventType.INIT_STARTED,
            Level.INFO,
            "Starting initialization sequence",
            deviceAddress = deviceAddress,
            deviceName = deviceName
        )
    }

    fun logInitSuccess(deviceName: String, deviceAddress: String) {
        log(
            EventType.INIT_SUCCESS,
            Level.INFO,
            "Initialization completed successfully",
            deviceAddress = deviceAddress,
            deviceName = deviceName
        )
    }

    fun logInitFailed(deviceName: String, deviceAddress: String, error: String) {
        log(
            EventType.INIT_FAILED,
            Level.ERROR,
            "Initialization failed",
            deviceAddress = deviceAddress,
            deviceName = deviceName,
            details = error
        )
    }

    fun logCommandSent(
        commandName: String,
        deviceName: String?,
        deviceAddress: String?,
        commandData: ByteArray? = null,
        additionalInfo: String? = null
    ) {
        val hexDump = commandData?.let {
            buildString {
                append("Size: ${it.size} bytes\n")
                append("Hex: ${it.toHexString()}\n")
                if (additionalInfo != null) {
                    append("Info: $additionalInfo")
                }
            }
        }

        log(
            EventType.COMMAND_SENT,
            Level.INFO, // Changed to INFO so it shows by default
            "Command sent: $commandName",
            deviceAddress = deviceAddress,
            deviceName = deviceName,
            details = hexDump
        )
    }

    fun logCommandSuccess(
        commandName: String,
        deviceName: String?,
        deviceAddress: String?
    ) {
        log(
            EventType.COMMAND_SUCCESS,
            Level.DEBUG,
            "Command successful: $commandName",
            deviceAddress = deviceAddress,
            deviceName = deviceName
        )
    }

    fun logCommandFailed(
        commandName: String,
        deviceName: String?,
        deviceAddress: String?,
        error: String
    ) {
        log(
            EventType.COMMAND_FAILED,
            Level.ERROR,
            "Command failed: $commandName",
            deviceAddress = deviceAddress,
            deviceName = deviceName,
            details = error
        )
    }

    fun logPollingStarted(pollingType: String, deviceName: String?, deviceAddress: String?) {
        log(
            EventType.POLLING_STARTED,
            Level.DEBUG,
            "Started polling: $pollingType",
            deviceAddress = deviceAddress,
            deviceName = deviceName
        )
    }

    fun logPollingStopped(pollingType: String, deviceName: String?, deviceAddress: String?) {
        log(
            EventType.POLLING_STOPPED,
            Level.DEBUG,
            "Stopped polling: $pollingType",
            deviceAddress = deviceAddress,
            deviceName = deviceName
        )
    }

    fun logDataReceived(dataType: String, deviceName: String?, deviceAddress: String?, summary: String? = null) {
        log(
            EventType.DATA_RECEIVED,
            Level.DEBUG,
            "Data received: $dataType",
            deviceAddress = deviceAddress,
            deviceName = deviceName,
            details = summary
        )
    }

    fun logDataParseError(dataType: String, deviceName: String?, deviceAddress: String?, error: String) {
        log(
            EventType.DATA_PARSE_ERROR,
            Level.ERROR,
            "Failed to parse $dataType",
            deviceAddress = deviceAddress,
            deviceName = deviceName,
            details = error
        )
    }

    fun logTimeout(operation: String, deviceName: String?, deviceAddress: String?) {
        log(
            EventType.TIMEOUT,
            Level.ERROR,
            "Operation timed out: $operation",
            deviceAddress = deviceAddress,
            deviceName = deviceName
        )
    }

    fun logError(operation: String, deviceName: String?, deviceAddress: String?, error: String) {
        log(
            EventType.UNKNOWN_ERROR,
            Level.ERROR,
            "Error during $operation",
            deviceAddress = deviceAddress,
            deviceName = deviceName,
            details = error
        )
    }

    fun logMonitorDataReceived(
        deviceName: String?,
        deviceAddress: String?,
        positionA: Int,
        positionB: Int,
        loadA: Float,
        loadB: Float
    ) {
        // Only log every 10th sample to avoid flooding (100ms polling = log every 1 second)
        if (monitorDataSampleCounter++ % 10 == 0) {
            log(
                EventType.DATA_RECEIVED,
                Level.DEBUG,
                "Monitor data",
                deviceAddress = deviceAddress,
                deviceName = deviceName,
                details = "PosA=$positionA, PosB=$positionB, LoadA=${loadA}kg, LoadB=${loadB}kg"
            )
        }
    }

    fun logCharacteristicWrite(
        characteristicUuid: String,
        deviceName: String?,
        deviceAddress: String?,
        data: ByteArray,
        success: Boolean
    ) {
        log(
            if (success) EventType.COMMAND_SUCCESS else EventType.WRITE_ERROR,
            if (success) Level.INFO else Level.ERROR,
            "${if (success) "Successfully wrote" else "Failed to write"} to characteristic",
            deviceAddress = deviceAddress,
            deviceName = deviceName,
            details = buildString {
                append("UUID: $characteristicUuid\n")
                append("Data: ${data.toHexString()}\n")
                append("Size: ${data.size} bytes")
            }
        )
    }

    fun logCharacteristicRead(
        characteristicUuid: String,
        deviceName: String?,
        deviceAddress: String?,
        data: ByteArray?
    ) {
        log(
            EventType.DATA_RECEIVED,
            Level.DEBUG,
            "Read characteristic",
            deviceAddress = deviceAddress,
            deviceName = deviceName,
            details = buildString {
                append("UUID: $characteristicUuid\n")
                if (data != null) {
                    append("Data: ${data.toHexString()}\n")
                    append("Size: ${data.size} bytes")
                } else {
                    append("Data: null")
                }
            }
        )
    }

    fun logHandleDetection(
        deviceName: String?,
        deviceAddress: String?,
        baselineA: Int?,
        baselineB: Int?,
        currentA: Int,
        currentB: Int,
        deltaA: Int,
        deltaB: Int,
        threshold: Int,
        grabbed: Boolean
    ) {
        log(
            EventType.DATA_RECEIVED,
            Level.DEBUG,
            "Handle detection: ${if (grabbed) "GRABBED" else "RELEASED"}",
            deviceAddress = deviceAddress,
            deviceName = deviceName,
            details = buildString {
                append("BaselineA=$baselineA, BaselineB=$baselineB\n")
                append("CurrentA=$currentA, CurrentB=$currentB\n")
                append("DeltaA=$deltaA, DeltaB=$deltaB\n")
                append("Threshold=$threshold\n")
                append("Status: ${if (grabbed) "GRABBED" else "RELEASED"}")
            }
        )
    }

    /**
     * Clean up old logs (e.g., older than 7 days)
     */
    suspend fun cleanupOldLogs(daysToKeep: Int = 7) {
        val cutoffTime = System.currentTimeMillis() - (daysToKeep * 24 * 60 * 60 * 1000L)
        val deletedCount = connectionLogDao.deleteOlderThan(cutoffTime)
        Timber.i("Cleaned up $deletedCount old connection logs")
    }

    /**
     * Clear all logs
     */
    suspend fun clearAllLogs() {
        connectionLogDao.deleteAll()
        Timber.i("Cleared all connection logs")
    }

    /**
     * Convert byte array to hex string for logging
     */
    private fun ByteArray.toHexString(): String {
        return joinToString(" ") { byte -> "%02X".format(byte) }
    }
}
