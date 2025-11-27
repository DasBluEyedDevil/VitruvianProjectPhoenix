package com.example.vitruvianredux.data.ble

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import java.util.UUID

/**
 * Abstraction over BLE peripheral operations.
 * Allows swapping between Nordic BLE and Kable implementations.
 */
interface BlePeripheral {
    /** Current connection state */
    val connectionState: StateFlow<BleConnectionState>

    /** Monitor data stream (workout metrics) */
    val monitorData: Flow<ByteArray>

    /** Rep notification stream */
    val repNotifications: Flow<ByteArray>

    /** Device name */
    val deviceName: String?

    /** Device address */
    val deviceAddress: String

    /** Connect to the peripheral */
    suspend fun connect(): Result<Unit>

    /** Disconnect from the peripheral */
    suspend fun disconnect()

    /** Close and release resources */
    fun close()

    /** Write data to a characteristic */
    suspend fun write(characteristicUuid: UUID, data: ByteArray): Result<Unit>

    /** Read data from a characteristic */
    suspend fun read(characteristicUuid: UUID): Result<ByteArray>

    /** Request MTU size */
    suspend fun requestMtu(mtu: Int): Result<Int>

    /** Check if connected */
    fun isConnected(): Boolean
}

/** Connection state for BLE peripherals */
sealed class BleConnectionState {
    object Disconnected : BleConnectionState()
    object Connecting : BleConnectionState()
    object Connected : BleConnectionState()
    data class Disconnecting(val cause: Throwable? = null) : BleConnectionState()
    data class Error(val message: String, val cause: Throwable? = null) : BleConnectionState()
}
