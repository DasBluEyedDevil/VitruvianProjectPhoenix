package com.example.vitruvianredux.data.ble

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.content.Context
import com.example.vitruvianredux.domain.model.WorkoutMetric
import com.example.vitruvianredux.util.BleConstants
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
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
class VitruvianBleManager(context: Context) : BleManager(context) {

    // GATT characteristics
    private var nusRxCharacteristic: BluetoothGattCharacteristic? = null
    private var monitorCharacteristic: BluetoothGattCharacteristic? = null
    private var propertyCharacteristic: BluetoothGattCharacteristic? = null
    private var repNotifyCharacteristic: BluetoothGattCharacteristic? = null

    // State flows
    private val _connectionState = MutableStateFlow<ConnectionStatus>(ConnectionStatus.Disconnected)
    val connectionState: StateFlow<ConnectionStatus> = _connectionState.asStateFlow()

    private val _monitorData = MutableSharedFlow<WorkoutMetric>(replay = 0)
    val monitorData: SharedFlow<WorkoutMetric> = _monitorData.asSharedFlow()

    private val _repEvents = MutableSharedFlow<RepNotification>(replay = 0)
    val repEvents: SharedFlow<RepNotification> = _repEvents.asSharedFlow()

    private val _statusMessages = MutableSharedFlow<String>(replay = 1)
    val statusMessages: SharedFlow<String> = _statusMessages.asSharedFlow()

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

        @Deprecated("Using deprecated Nordic BLE API")
        override fun isRequiredServiceSupported(gatt: BluetoothGatt): Boolean {
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

            if (nusRxCharacteristic == null) {
                Timber.e("NUS RX characteristic not found")
                return false
            }

            if (monitorCharacteristic == null) {
                Timber.e("Monitor characteristic not found")
                return false
            }

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

            // Enable notifications on monitor characteristic
            monitorCharacteristic?.let { characteristic ->
                setNotificationCallback(characteristic).with { _, data ->
                    handleMonitorData(data)
                }
                enableNotifications(characteristic).enqueue()
            }

            // Enable notifications on rep notify characteristic
            repNotifyCharacteristic?.let { characteristic ->
                setNotificationCallback(characteristic).with { _, data ->
                    handleRepNotification(data)
                }
                enableNotifications(characteristic).enqueue()
            }

            _connectionState.value = ConnectionStatus.Ready
            Timber.d("BLE initialization complete")
        }
    }

    /**
     * Send a command to the device
     */
    @Suppress("DEPRECATION")
    fun sendCommand(data: ByteArray): Result<Unit> {
        return try {
            nusRxCharacteristic?.let { characteristic ->
                writeCharacteristic(characteristic, data)
                    .split()
                    .enqueue()
                Timber.d("Sent command: ${data.toHexString()}")
                Result.success(Unit)
            } ?: Result.failure(Exception("NUS RX characteristic not available"))
        } catch (e: Exception) {
            Timber.e(e, "Failed to send command")
            Result.failure(e)
        }
    }

    /**
     * Handle monitor data notifications (real-time metrics)
     */
    private fun handleMonitorData(data: Data) {
        try {
            val bytes = data.value ?: return
            if (bytes.size < 16) {
                Timber.w("Monitor data too short: ${bytes.size} bytes")
                return
            }

            val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)

            // Parse the monitor data packet
            // Format from device: u32 ticks, f32 loadA, f32 loadB, u16 posA, u16 posB
            val ticks = buffer.getInt(0)
            val loadA = buffer.getFloat(4)
            val loadB = buffer.getFloat(8)
            val positionA = buffer.getShort(12).toInt() and 0xFFFF
            val positionB = buffer.getShort(14).toInt() and 0xFFFF

            val metric = WorkoutMetric(
                timestamp = System.currentTimeMillis(),
                loadA = loadA,
                loadB = loadB,
                positionA = positionA,
                positionB = positionB,
                ticks = ticks
            )

            _monitorData.tryEmit(metric)

        } catch (e: Exception) {
            Timber.e(e, "Error parsing monitor data")
        }
    }

    /**
     * Handle rep notification data
     */
    private fun handleRepNotification(data: Data) {
        try {
            val bytes = data.value ?: return
            Timber.d("Rep notification: ${bytes.toHexString()}")

            // Parse rep notification - format may vary
            // This needs to be validated against actual device behavior
            if (bytes.isNotEmpty()) {
                val repData = RepNotification(
                    rawData = bytes,
                    timestamp = System.currentTimeMillis()
                )
                _repEvents.tryEmit(repData)
            }
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

/**
 * Rep notification data class
 */
data class RepNotification(
    val rawData: ByteArray,
    val timestamp: Long
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as RepNotification

        if (!rawData.contentEquals(other.rawData)) return false
        if (timestamp != other.timestamp) return false

        return true
    }

    override fun hashCode(): Int {
        var result = rawData.contentHashCode()
        result = 31 * result + timestamp.hashCode()
        return result
    }
}

