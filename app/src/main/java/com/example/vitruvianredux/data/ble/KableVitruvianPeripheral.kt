package com.example.vitruvianredux.data.ble

import com.juul.kable.Advertisement
import kotlin.uuid.ExperimentalUuidApi
import com.juul.kable.Characteristic
import com.juul.kable.Peripheral
import com.juul.kable.Priority
import com.juul.kable.State
import com.juul.kable.WriteType
import com.juul.kable.characteristicOf
import com.juul.kable.logs.Logging
import com.juul.kable.logs.SystemLogEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import timber.log.Timber
import java.util.UUID

/**
 * Kable-based implementation of BlePeripheral for Vitruvian Trainer.
 * Wraps Kable's Peripheral with Vitruvian-specific functionality.
 *
 * This implementation is compatible with Kable 0.40.0 API using kotlin.uuid.Uuid.
 */
@OptIn(ExperimentalUuidApi::class)
class KableVitruvianPeripheral(
    private val advertisement: Advertisement
) : BlePeripheral {

    companion object {
        private const val TAG = "KableVitruvian"
        private const val TARGET_MTU = 247

        /**
         * Create a KableVitruvianPeripheral from a DiscoveredDevice.
         */
        fun create(device: DiscoveredDevice): KableVitruvianPeripheral {
            return KableVitruvianPeripheral(device.advertisement)
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // Kable Peripheral instance (Kable 0.40.0 API)
    private val peripheral: Peripheral = Peripheral(advertisement) {
        logging {
            engine = SystemLogEngine
            level = Logging.Level.Events
        }
        onServicesDiscovered {
            Timber.tag(TAG).d("Services discovered")
            // Note: MTU negotiation in Kable 0.40.0 is handled automatically
            // No explicit requestMtu() call needed in onServicesDiscovered
        }
    }

    // Kable Characteristics using kotlin.uuid.Uuid
    private val nusRxChar: Characteristic = characteristicOf(
        service = KableUuids.NUS_SERVICE,
        characteristic = KableUuids.NUS_RX_CHAR
    )

    private val monitorChar: Characteristic = characteristicOf(
        service = KableUuids.NUS_SERVICE,
        characteristic = KableUuids.MONITOR_CHAR
    )

    private val repsChar: Characteristic = characteristicOf(
        service = KableUuids.NUS_SERVICE,
        characteristic = KableUuids.REPS_CHAR
    )

    private val diagnosticChar: Characteristic = characteristicOf(
        service = KableUuids.NUS_SERVICE,
        characteristic = KableUuids.DIAGNOSTIC_CHAR
    )

    private val heuristicChar: Characteristic = characteristicOf(
        service = KableUuids.NUS_SERVICE,
        characteristic = KableUuids.HEURISTIC_CHAR
    )

    // Connection state mapping
    private val _connectionState = MutableStateFlow<BleConnectionState>(BleConnectionState.Disconnected)
    override val connectionState: StateFlow<BleConnectionState> = _connectionState.asStateFlow()

    init {
        // Map Kable state to our abstraction (Kable 0.40.0 State enum)
        peripheral.state
            .onEach { state ->
                _connectionState.value = when (state) {
                    is State.Disconnected -> BleConnectionState.Disconnected
                    is State.Connecting.Bluetooth,
                    is State.Connecting.Services,
                    is State.Connecting.Observes -> BleConnectionState.Connecting
                    is State.Connected -> BleConnectionState.Connected
                    is State.Disconnecting -> {
                        // Kable 0.40.0 State.Disconnecting doesn't have a cause property
                        // Extract cause if available, otherwise null
                        val cause = (state as? State.Disconnecting)?.let { null }
                        BleConnectionState.Disconnecting(cause)
                    }
                }
                Timber.tag(TAG).d("Connection state: $state -> ${_connectionState.value}")
            }
            .catch { e ->
                Timber.tag(TAG).e(e, "State flow error")
                _connectionState.value = BleConnectionState.Error("State error", e)
            }
            .launchIn(scope)
    }

    // Monitor data Flow (notifications)
    override val monitorData: Flow<ByteArray> = peripheral.observe(monitorChar)
        .catch { e ->
            Timber.tag(TAG).e(e, "Monitor observation error")
        }

    // Rep notifications Flow
    override val repNotifications: Flow<ByteArray> = peripheral.observe(repsChar)
        .catch { e ->
            Timber.tag(TAG).e(e, "Rep observation error")
        }

    override val deviceName: String? = advertisement.name
    override val deviceAddress: String = advertisement.identifier.toString()

    override suspend fun connect(): Result<Unit> = runCatching {
        Timber.tag(TAG).d("Connecting to ${advertisement.name}...")
        peripheral.connect()
        Timber.tag(TAG).d("Connected successfully")
    }

    override suspend fun disconnect() {
        Timber.tag(TAG).d("Disconnecting...")
        try {
            peripheral.disconnect()
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "Disconnect error (may already be disconnected)")
        }
    }

    override fun close() {
        Timber.tag(TAG).d("Closing peripheral...")
        scope.cancel()
    }

    override suspend fun write(characteristicUuid: UUID, data: ByteArray): Result<Unit> = runCatching {
        val uuidStr = characteristicUuid.toString()

        val char = when (uuidStr) {
            KableUuids.NUS_RX_CHAR_STR -> nusRxChar
            else -> {
                // Parse UUID string to kotlin.uuid.Uuid
                val kableUuid = kotlin.uuid.Uuid.parse(uuidStr)
                characteristicOf(
                    service = KableUuids.NUS_SERVICE,
                    characteristic = kableUuid
                )
            }
        }

        Timber.tag(TAG).v("Writing ${data.size} bytes to $characteristicUuid")
        peripheral.write(char, data, WriteType.WithoutResponse)
    }

    override suspend fun read(characteristicUuid: UUID): Result<ByteArray> = runCatching {
        val uuidStr = characteristicUuid.toString()

        val char = when (uuidStr) {
            KableUuids.DIAGNOSTIC_CHAR_STR -> diagnosticChar
            KableUuids.HEURISTIC_CHAR_STR -> heuristicChar
            else -> {
                // Parse UUID string to kotlin.uuid.Uuid
                val kableUuid = kotlin.uuid.Uuid.parse(uuidStr)
                characteristicOf(
                    service = KableUuids.NUS_SERVICE,
                    characteristic = kableUuid
                )
            }
        }

        Timber.tag(TAG).v("Reading from $characteristicUuid")
        peripheral.read(char)
    }

    override suspend fun requestMtu(mtu: Int): Result<Int> = runCatching {
        Timber.tag(TAG).d("MTU request: $mtu (handled automatically by Kable)")
        // Kable 0.40.0 handles MTU negotiation automatically
        // Return the target MTU to satisfy the interface
        TARGET_MTU
    }

    /**
     * Request connection priority.
     * @param priority 0=Balanced, 1=High, 2=LowPower
     */
    suspend fun requestConnectionPriority(priority: Int): Result<Unit> = runCatching {
        val kablePriority = when (priority) {
            1 -> Priority.High
            2 -> Priority.LowPower
            else -> Priority.Balanced
        }
        Timber.tag(TAG).d("Requesting connection priority: $kablePriority")
        peripheral.requestConnectionPriority(kablePriority)
    }

    override fun isConnected(): Boolean {
        return peripheral.state.value is State.Connected
    }

    /**
     * Read diagnostic data from the device.
     * Convenience method for reading device diagnostic information.
     */
    suspend fun readDiagnostic(): Result<ByteArray> {
        return read(UUID.fromString(KableUuids.DIAGNOSTIC_CHAR_STR))
    }

    /**
     * Read heuristic data from the device.
     * Convenience method for reading device heuristic information.
     */
    suspend fun readHeuristic(): Result<ByteArray> {
        return read(UUID.fromString(KableUuids.HEURISTIC_CHAR_STR))
    }

    /**
     * Send a command to the device via NUS RX characteristic.
     * Convenience method for sending commands to the device.
     */
    suspend fun sendCommand(data: ByteArray): Result<Unit> {
        return write(UUID.fromString(KableUuids.NUS_RX_CHAR_STR), data)
    }
}
