# Kable BLE Migration Plan - EXHAUSTIVE Line-by-Line Analysis

## CRITICAL WARNING

This document provides an **obsessively granular** 1:1 migration analysis. BLE implementations are notoriously fragile - **a single missed detail will cause silent failures**.

---

## Table of Contents

1. [Executive Summary](#1-executive-summary)
2. [Class Structure & Initialization](#2-class-structure--initialization)
3. [Thread Safety Requirements](#3-thread-safety-requirements)
4. [GATT Characteristic Storage](#4-gatt-characteristic-storage)
5. [Coroutine Scopes & Job Management](#5-coroutine-scopes--job-management)
6. [State Flow Definitions](#6-state-flow-definitions)
7. [Handle Detection State Machine](#7-handle-detection-state-machine)
8. [GATT Callback Implementation](#8-gatt-callback-implementation)
9. [Service Discovery](#9-service-discovery)
10. [Firmware Version Detection](#10-firmware-version-detection)
11. [Notification Setup](#11-notification-setup)
12. [Monitor Data Processing](#12-monitor-data-processing)
13. [Sample Validation](#13-sample-validation)
14. [Rep Notification Processing](#14-rep-notification-processing)
15. [Diagnostic & Heuristic Polling](#15-diagnostic--heuristic-polling)
16. [Heartbeat Implementation](#16-heartbeat-implementation)
17. [Command Sending](#17-command-sending)
18. [onServicesInvalidated Workaround](#18-onservicesinvalidated-workaround)
19. [awaitResponse Protocol](#19-awaitresponse-protocol)
20. [UUID Definitions](#20-uuid-definitions)
21. [Repository Layer](#21-repository-layer)
22. [ConnectionLogger Integration](#22-connectionlogger-integration)
23. [Complete Missing Features Summary](#23-complete-missing-features-summary)
24. [Exact Constant Values](#24-exact-constant-values)
25. [Data Class Definitions](#25-data-class-definitions)

---

## 1. Executive Summary

| Metric | Nordic | Kable | Gap |
|--------|--------|-------|-----|
| Lines of code | 1,641 | 435 | **73% missing** |
| State flows | 9 | 6 | 3 missing |
| Polling jobs | 4 | 3 | 1 missing |
| @Volatile fields | 12 | 0 | **ALL missing** |
| Threshold constants | 7 | 0 | **ALL missing** |
| Notification chars | 8 | 2 | 6 missing |
| ConnectionLogger calls | ~25 | 0 | **ALL missing** |

---

## 2. Class Structure & Initialization

### Nordic (Lines 47-51)
```kotlin
@OptIn(ExperimentalStdlibApi::class)
class VitruvianBleManager(
    context: Context,
    private val connectionLogger: com.example.vitruvianredux.data.logger.ConnectionLogger? = null
) : BleManager(context.applicationContext) {  // CRITICAL: Always use applicationContext to prevent memory leaks
```

### Kable (Lines 35-55)
```kotlin
class KableBleManager {
    companion object {
        private const val TAG = "KableBleManager"
        // ... constants
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    // NO connectionLogger parameter - MISSING
```

### MISSING in Kable:
| Item | Nordic Line | Kable Status |
|------|-------------|--------------|
| `connectionLogger` parameter | 50 | ❌ MISSING |
| `@OptIn(ExperimentalStdlibApi::class)` | 47 | ❌ MISSING |
| Application context usage | 51 | N/A (no context needed) |

---

## 3. Thread Safety Requirements

### Nordic @Volatile Fields (Lines 83-97, 147-151)
```kotlin
// CRITICAL: @Volatile ensures visibility across threads
@Volatile private var lastGoodPosA = 0          // Line 84
@Volatile private var lastGoodPosB = 0          // Line 85
@Volatile private var lastPositionA = 0         // Line 88
@Volatile private var lastPositionB = 0         // Line 89
@Volatile private var lastTimestamp = 0L        // Line 90
@Volatile private var strictValidationEnabled = false  // Line 91
@Volatile var detectedFirmwareVersion: String? = null  // Line 94
@Volatile var negotiatedMtu: Int? = null        // Line 96
@Volatile private var monitorNotificationCount = 0L  // Line 151
```

### Kable Status: **ALL @Volatile FIELDS MISSING**

**WHY THIS MATTERS:**
- BLE callbacks run on BLE thread, not Main
- Without @Volatile, position updates may not be visible to other threads
- Race conditions cause intermittent failures that are EXTREMELY hard to debug
- This is a **silent failure mode** - the app appears to work but data is corrupted

---

## 4. GATT Characteristic Storage

### Nordic (Lines 61-71)
```kotlin
// GATT characteristics - nullable, set during service discovery
private var nusRxCharacteristic: BluetoothGattCharacteristic? = null      // Line 62
private var monitorCharacteristic: BluetoothGattCharacteristic? = null    // Line 63
private var propertyCharacteristic: BluetoothGattCharacteristic? = null   // Line 64 (Diagnostic)
private var repNotifyCharacteristic: BluetoothGattCharacteristic? = null  // Line 65
private var heuristicCharacteristic: BluetoothGattCharacteristic? = null  // Line 66
private var versionCharacteristic: BluetoothGattCharacteristic? = null    // Line 67

// Official app workout command characteristics (8 characteristics for testing)
private val workoutCmdCharacteristics = mutableListOf<BluetoothGattCharacteristic>()  // Line 70
```

### Kable (KableVitruvianPeripheral Lines 65-88)
```kotlin
private val nusRxChar: Characteristic = characteristicOf(...)      // ✅
private val monitorChar: Characteristic = characteristicOf(...)    // ✅
private val repsChar: Characteristic = characteristicOf(...)       // ✅
private val diagnosticChar: Characteristic = characteristicOf(...) // ✅
private val heuristicChar: Characteristic = characteristicOf(...)  // ✅
// VERSION_CHAR - MISSING ❌
// workoutCmdCharacteristics - MISSING ❌
```

### MISSING:
| Characteristic | UUID | Nordic Line | Status |
|----------------|------|-------------|--------|
| VERSION | 74e994ac-0e80-4c02-9cd0-76cb31d3959b | 67 | ❌ MISSING |
| WORKOUT_CMD[0] | 6d094aa3-b60d-4916-8a55-8ed73fb9f6a5 | 70 | ❌ MISSING |
| WORKOUT_CMD[1-7] | ...f6a6 through ...f6ac | 70 | ❌ MISSING |

---

## 5. Coroutine Scopes & Job Management

### Nordic (Lines 72-81)
```kotlin
// Monitor polling - MUST be on Main dispatcher for Nordic BLE library
private val pollingScope = CoroutineScope(Dispatchers.Main + SupervisorJob())  // Line 73
private var monitorPollingJob: Job? = null     // Line 74
private var propertyPollingJob: Job? = null    // Line 75
private var heuristicPollingJob: Job? = null   // Line 76
private var heartbeatJob: Job? = null          // Line 77

private val HEARTBEAT_INTERVAL_MS = 2000L              // Line 79
private val HEARTBEAT_READ_TIMEOUT_MS = 1500L          // Line 80 - CRITICAL FOR TIMEOUT
private val HEARTBEAT_NO_OP = byteArrayOf(0x00, 0x00, 0x00, 0x00)  // Line 81 - MUST BE 4 BYTES
```

### Kable (Lines 37-50)
```kotlin
private const val HEARTBEAT_INTERVAL_MS = 2000L    // Line 41 ✅
private const val HEARTBEAT_NO_OP = 0x00.toByte()  // Line 42 - WRONG! SINGLE BYTE

private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)  // Line 45
private var diagnosticPollingJob: Job? = null   // Line 48
private var heuristicPollingJob: Job? = null    // Line 49
private var heartbeatJob: Job? = null           // Line 50
// monitorPollingJob - MISSING (uses Flow observation instead)
```

### CRITICAL BUGS IN KABLE:
1. **`HEARTBEAT_NO_OP` is 1 byte, MUST be 4 bytes**
   - Nordic: `byteArrayOf(0x00, 0x00, 0x00, 0x00)`
   - Kable: `0x00.toByte()` - WRONG SIZE

2. **`HEARTBEAT_READ_TIMEOUT_MS` missing** - heartbeat uses read-then-write pattern with timeout

3. **`monitorPollingJob` missing** - Kable uses Flow but needs polling job for forAutoStart handling

---

## 6. State Flow Definitions

### Nordic (Lines 99-160) - ALL FLOWS

```kotlin
// Connection state
private val _connectionState = MutableStateFlow<ConnectionStatus>(ConnectionStatus.Disconnected)  // Line 100

// Diagnostic data
private val _diagnosticData = MutableStateFlow<DiagnosticDetails?>(null)  // Line 103

// Heuristic data
private val _heuristicData = MutableStateFlow<HeuristicStatistics?>(null)  // Line 106

// Monitor data - CRITICAL: 64-entry buffer for high-frequency emissions
private val _monitorData = MutableSharedFlow<WorkoutMetric>(
    replay = 0,
    extraBufferCapacity = 64,  // Buffer up to 64 emissions (640ms of data at 100ms rate)
    onBufferOverflow = BufferOverflow.DROP_OLDEST
)  // Lines 110-114

// Rep events - Same buffer config
private val _repEvents = MutableSharedFlow<RepNotification>(
    replay = 0,
    extraBufferCapacity = 64,
    onBufferOverflow = BufferOverflow.DROP_OLDEST
)  // Lines 117-121

// Handle state
private val _handleState = MutableStateFlow<HandleState>(HandleState.Released)  // Line 124

// DELOAD EVENT FLOW - CRITICAL for Just Lift safety recovery
private val _deloadOccurredEvents = MutableSharedFlow<Unit>(
    replay = 0,
    extraBufferCapacity = 8,
    onBufferOverflow = BufferOverflow.DROP_OLDEST
)  // Lines 129-133

// RECONNECTION REQUEST FLOW - Android 16 Pixel BLE bug workaround
private val _reconnectionRequested = MutableSharedFlow<ReconnectionRequest>(
    replay = 0,
    extraBufferCapacity = 4,
    onBufferOverflow = BufferOverflow.DROP_OLDEST
)  // Lines 139-143

// Debounce tracking for deload events
private var lastDeloadEventTime = 0L                // Line 147
private val DELOAD_EVENT_DEBOUNCE_MS = 2000L        // Line 148

// Monitor notification counter for diagnostic logging
@Volatile private var monitorNotificationCount = 0L  // Line 151

// Command response flow - for awaitResponse() protocol handshake
private val _commandResponses = MutableSharedFlow<UByte>(
    replay = 0,
    extraBufferCapacity = 16,
    onBufferOverflow = BufferOverflow.DROP_OLDEST
)  // Lines 155-159
```

### Kable Status:

| Flow | Nordic Lines | Kable Status | Impact if Missing |
|------|--------------|--------------|-------------------|
| `_connectionState` | 100-101 | ✅ Present | N/A |
| `_diagnosticData` | 103-104 | ✅ Present | N/A |
| `_heuristicData` | 106-107 | ✅ Present | N/A |
| `_monitorData` (64 buffer) | 110-115 | ✅ Present | N/A |
| `_repEvents` (64 buffer) | 117-122 | ✅ Present | N/A |
| `_handleState` | 124-125 | ✅ Present | N/A |
| `_deloadOccurredEvents` | 129-134 | ❌ **MISSING** | Just Lift safety broken |
| `_reconnectionRequested` | 139-144 | ❌ **MISSING** | No auto-recovery on Pixel |
| `lastDeloadEventTime` | 147 | ❌ **MISSING** | Deload spam possible |
| `DELOAD_EVENT_DEBOUNCE_MS` | 148 | ❌ **MISSING** | Deload spam possible |
| `monitorNotificationCount` | 151 | ❌ **MISSING** | No diagnostic logging |
| `_commandResponses` | 155-160 | ❌ **MISSING** | Protocol handshake fails |

---

## 7. Handle Detection State Machine

### Nordic Constants (Lines 162-174)
```kotlin
// Just Lift detection parameters - v0.5.1-beta values (PROVEN WORKING)
// These values were tuned through real-world testing
private val HANDLE_GRABBED_THRESHOLD = 8.0   // Position > 8.0 = handles grabbed
private val HANDLE_REST_THRESHOLD = 5.0      // Position < 5.0 = handles at rest (Increased from 2.5 to handle drift)
private val VELOCITY_THRESHOLD = 100.0       // Velocity > 100 units/s = significant movement

// Track position range for post-workout tuning diagnostics
private var minPositionSeen = Double.MAX_VALUE
private var maxPositionSeen = Double.MIN_VALUE

// Force-based grab/release timing (not currently used but reserved)
private var forceAboveGrabThresholdStart: Long? = null
private var forceBelowReleaseThresholdStart: Long? = null
```

### Kable Status: **ALL MISSING**

### Handle State Enum (Nordic Lines 1569-1574)
```kotlin
enum class HandleState {
    WaitingForRest,  // Initial state - MUST see handles at rest before arming grab detection
    Released,        // Handles at rest - armed for grab detection
    Grabbed,         // Handles grabbed - position + velocity thresholds met
    Moving           // Handles in motion but not confirmed grab (intermediate state)
}
```

### State Transition Logic (Nordic Lines 1223-1294)

```kotlin
private fun analyzeHandleState(metric: WorkoutMetric): HandleState {
    val posA = metric.positionA.toDouble()
    val posB = metric.positionB.toDouble()
    val velocityA = metric.velocityA
    val velocityB = metric.velocityB

    // Track position range for post-workout tuning
    minPositionSeen = minOf(minPositionSeen, minOf(posA, posB))
    maxPositionSeen = maxOf(maxPositionSeen, maxOf(posA, posB))

    val currentState = _handleState.value

    // Check both handles - support single-handle exercises (Issue #102)
    val handleAGrabbed = posA > HANDLE_GRABBED_THRESHOLD
    val handleBGrabbed = posB > HANDLE_GRABBED_THRESHOLD
    val handleAMoving = velocityA > VELOCITY_THRESHOLD
    val handleBMoving = velocityB > VELOCITY_THRESHOLD

    return when (currentState) {
        HandleState.WaitingForRest -> {
            // MUST see handles at rest before arming grab detection
            // This prevents immediate auto-start if cables already have tension
            if (posA < HANDLE_REST_THRESHOLD && posB < HANDLE_REST_THRESHOLD) {
                Timber.d("Handles at REST (posA=$posA, posB=$posB < $HANDLE_REST_THRESHOLD) - auto-start now ARMED")
                HandleState.Released
            } else {
                HandleState.WaitingForRest
            }
        }
        HandleState.Released, HandleState.Moving -> {
            // Check if EITHER handle is grabbed and moving (for single-handle exercises)
            val aActive = handleAGrabbed && handleAMoving
            val bActive = handleBGrabbed && handleBMoving

            if (aActive || bActive) {
                val activeHandle = if (aActive && bActive) "both" else if (aActive) "A" else "B"
                Timber.i("GRAB CONFIRMED: handle=$activeHandle")
                HandleState.Grabbed
            } else if (handleAGrabbed || handleBGrabbed) {
                // Position extended but no significant movement yet
                HandleState.Moving
            } else {
                HandleState.Released
            }
        }
        HandleState.Grabbed -> {
            // Consider released only if BOTH handles are at rest
            // This prevents false release during single-handle exercises
            val aReleased = posA < HANDLE_REST_THRESHOLD
            val bReleased = posB < HANDLE_REST_THRESHOLD

            if (aReleased && bReleased) {
                Timber.d("RELEASE DETECTED: posA=$posA, posB=$posB < $HANDLE_REST_THRESHOLD")
                HandleState.Released
            } else {
                HandleState.Grabbed
            }
        }
    }
}
```

### Kable Status: **ENTIRE STATE MACHINE MISSING**

This is the **#1 blocker for Just Lift mode**.

---

## 8. GATT Callback Implementation

### Nordic VitruvianGattCallback (Lines 206-711)

This inner class handles all GATT events. Key sections:

1. **isRequiredServiceSupported()** - Service discovery (Lines 211-315)
2. **tryReadFirmwareVersion()** - DIS service reading (Lines 323-416)
3. **tryReadVitruvianVersion()** - VERSION characteristic (Lines 425-473)
4. **onServicesInvalidated()** - Android 16 bug workaround (Lines 476-558)
5. **onDeviceDisconnected()** - Disconnect handling (Lines 561-577)
6. **initialize()** - Notification setup (Lines 580-711)

### Kable Equivalent:
```kotlin
private val peripheral: Peripheral = Peripheral(advertisement) {
    logging {
        engine = SystemLogEngine
        level = Logging.Level.Events
    }
    onServicesDiscovered {
        Timber.tag(TAG).d("Services discovered")
        // MINIMAL - no firmware detection, no characteristic enumeration
    }
}
```

### MISSING in Kable:
- ❌ Service/characteristic enumeration logging
- ❌ Handle ID extraction via reflection
- ❌ Firmware version detection from DIS
- ❌ Vitruvian VERSION characteristic reading
- ❌ Fallback characteristic search across all services
- ❌ onServicesInvalidated() workaround
- ❌ Pending operations tracking during init

---

## 9. Service Discovery

### Nordic (Lines 211-315) - isRequiredServiceSupported()

```kotlin
override fun isRequiredServiceSupported(gatt: BluetoothGatt): Boolean {
    // Log all available services and characteristics for debugging
    Timber.d("=== Discovering BLE Services ===")
    gatt.services.forEach { service ->
        Timber.d("Service: ${service.uuid}")
        service.characteristics.forEach { char ->
            Timber.d("  - Characteristic: ${char.uuid} (props: ${char.properties}, instance: ${char.instanceId})")

            // Get handle by reading characteristic's instance ID (reflection)
            // This helps correlate with HCI logs for debugging
            try {
                val handleField = char.javaClass.getDeclaredField("mHandle")
                handleField.isAccessible = true
                val handle = handleField.getInt(char)
                Timber.d("    HANDLE: 0x${handle.toString(16).uppercase()} = ${char.uuid}")
            } catch (e: Exception) {
                Timber.w("    Could not get handle: ${e.message}")
            }
        }
    }
    Timber.d("=== End Service Discovery ===")

    // DIAGNOSTIC: Try to read firmware version (non-blocking)
    tryReadFirmwareVersion(gatt)
    tryReadVitruvianVersion(gatt)

    // Get NUS service
    val nusService = gatt.getService(BleConstants.NUS_SERVICE_UUID)
    if (nusService == null) {
        Timber.e("NUS service not found")
        return false
    }

    // Get required characteristics
    nusRxCharacteristic = nusService.getCharacteristic(BleConstants.NUS_RX_CHAR_UUID)
    monitorCharacteristic = nusService.getCharacteristic(BleConstants.MONITOR_CHAR_UUID)
    propertyCharacteristic = nusService.getCharacteristic(BleConstants.DIAGNOSTIC_CHAR_UUID)
    repNotifyCharacteristic = nusService.getCharacteristic(BleConstants.REP_NOTIFY_CHAR_UUID)
    heuristicCharacteristic = nusService.getCharacteristic(BleConstants.HEURISTIC_CHAR_UUID)
    versionCharacteristic = nusService.getCharacteristic(BleConstants.VERSION_CHAR_UUID)

    // If characteristics not in NUS service, search ALL services
    if (repNotifyCharacteristic == null) {
        gatt.services.forEach { service ->
            if (repNotifyCharacteristic == null)
                repNotifyCharacteristic = service.getCharacteristic(BleConstants.REP_NOTIFY_CHAR_UUID)
            if (heuristicCharacteristic == null)
                heuristicCharacteristic = service.getCharacteristic(BleConstants.HEURISTIC_CHAR_UUID)
            if (versionCharacteristic == null)
                versionCharacteristic = service.getCharacteristic(BleConstants.VERSION_CHAR_UUID)
            if (propertyCharacteristic == null)
                propertyCharacteristic = service.getCharacteristic(BleConstants.DIAGNOSTIC_CHAR_UUID)
        }
    }

    // Collect ALL characteristics for notifications (matching web app - 8 total)
    notifyCharacteristics.clear()
    val allCharacteristics = gatt.services.flatMap { it.characteristics }
    for (uuid in BleConstants.NOTIFY_CHAR_UUIDS) {
        allCharacteristics.find { it.uuid == uuid }?.let { char ->
            notifyCharacteristics.add(char)
            Timber.d("Found notify characteristic: $uuid")
        }
    }
    Timber.d("Collected ${notifyCharacteristics.size} notify characteristics")

    // Collect workout command characteristics for testing trainer protocol
    workoutCmdCharacteristics.clear()
    for (uuid in BleConstants.WORKOUT_CMD_CHAR_UUIDS) {
        allCharacteristics.find { it.uuid == uuid }?.let { char ->
            workoutCmdCharacteristics.add(char)
        }
    }
    Timber.d("Collected ${workoutCmdCharacteristics.size} workout command characteristics")

    // Validate required characteristics exist
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
```

### Kable: Minimal implementation in peripheral config block

---

## 10. Firmware Version Detection

### Nordic (Lines 323-416) - tryReadFirmwareVersion()

```kotlin
private fun tryReadFirmwareVersion(gatt: BluetoothGatt) {
    try {
        // Device Information Service UUID (standard BLE service)
        val DIS_SERVICE_UUID = UUID.fromString("0000180a-0000-1000-8000-00805f9b34fb")
        val FIRMWARE_REVISION_UUID = UUID.fromString("00002a26-0000-1000-8000-00805f9b34fb")
        val SOFTWARE_REVISION_UUID = UUID.fromString("00002a28-0000-1000-8000-00805f9b34fb")
        val MODEL_NUMBER_UUID = UUID.fromString("00002a24-0000-1000-8000-00805f9b34fb")

        val deviceInfoService = gatt.getService(DIS_SERVICE_UUID) ?: return

        // Read firmware revision (most important)
        val firmwareChar = deviceInfoService.getCharacteristic(FIRMWARE_REVISION_UUID)
        if (firmwareChar != null) {
            readCharacteristic(firmwareChar)
                .with { _, data ->
                    val firmwareVersion = data.getStringValue(0) ?: "Unknown"
                    detectedFirmwareVersion = firmwareVersion  // Store for Protocol Tester
                    Timber.i("🔧 FIRMWARE VERSION: $firmwareVersion")
                    connectionLogger?.log(
                        eventType = "FIRMWARE_DETECTED",
                        level = ConnectionLogger.Level.INFO,
                        message = "Firmware Version: $firmwareVersion"
                    )
                }
                .fail { _, status -> Timber.d("Failed to read firmware (status: $status) - OK") }
                .enqueue()
        }

        // Read model number
        val modelChar = deviceInfoService.getCharacteristic(MODEL_NUMBER_UUID)
        if (modelChar != null) {
            readCharacteristic(modelChar)
                .with { _, data ->
                    val modelNumber = data.getStringValue(0) ?: "Unknown"
                    Timber.i("📱 Model Number: $modelNumber")
                    connectionLogger?.log(...)
                }
                .fail { _, _ -> /* ignore */ }
                .enqueue()
        }

        // Read software revision
        // ... similar pattern
    } catch (e: Exception) {
        Timber.w("Exception while reading firmware version: ${e.message}")
    }
}
```

### Kable Status: **ENTIRELY MISSING**

---

## 11. Notification Setup

### Nordic (Lines 579-711) - initialize()

```kotlin
override fun initialize() {
    super.initialize()

    // Track pending operations: MTU request + connection priority + all notification enables
    val pendingOperations = AtomicInteger(notifyCharacteristics.size + 2)

    fun checkAllOperationsComplete() {
        val remaining = pendingOperations.decrementAndGet()
        Timber.d("Pending operations: $remaining")
        if (remaining == 0) {
            _connectionState.value = ConnectionStatus.Ready
            Timber.d("All initialization operations complete! Device ready.")

            // Start keep-alive polling immediately
            startDiagnosticPolling()  // 500ms interval
            startHeartbeat()          // 2000ms interval
        }
    }

    // REQUEST HIGH CONNECTION PRIORITY - Critical for stability!
    Timber.d("Requesting HIGH connection priority...")
    requestConnectionPriority(ConnectionPriorityRequest.CONNECTION_PRIORITY_HIGH)
        .done { _ ->
            Timber.d("✅ Connection priority set to HIGH")
            checkAllOperationsComplete()
        }
        .fail { _, status ->
            Timber.w("⚠️ Failed to set connection priority (status: $status)")
            checkAllOperationsComplete()
        }
        .enqueue()

    // REQUEST MTU (247 bytes for 96-byte program params)
    requestMtu(247)
        .with { _, mtu ->
            negotiatedMtu = mtu
            Timber.d("MTU successfully changed to $mtu bytes")
        }
        .done { _ -> checkAllOperationsComplete() }
        .enqueue()

    // Enable notifications on ALL required characteristics (8 total)
    for (characteristic in notifyCharacteristics) {
        // Special handler for REP_NOTIFY
        if (characteristic.uuid == BleConstants.REP_NOTIFY_CHAR_UUID) {
            setNotificationCallback(characteristic).with { _, data ->
                Timber.d("🔥 REP NOTIFICATION! Data size: ${data.value?.size ?: 0}")
                handleRepNotification(data)
            }
        }
        // Special handler for MONITOR
        else if (characteristic.uuid == BleConstants.MONITOR_CHAR_UUID) {
            setNotificationCallback(characteristic).with { _, data ->
                if (monitorNotificationCount++ % 100 == 0L) {
                    Timber.i("📊 MONITOR NOTIFICATION #$monitorNotificationCount")
                }
                handleMonitorData(data)
            }
        }
        // Special handler for VERSION - log raw hex for debugging
        else if (characteristic.uuid == BleConstants.VERSION_CHAR_UUID) {
            setNotificationCallback(characteristic).with { _, data ->
                val bytes = data.value
                if (bytes != null && bytes.isNotEmpty()) {
                    val hexString = bytes.joinToString(" ") { "%02X".format(it) }
                    Timber.i("VERSION: Size=${bytes.size}, Hex=$hexString")
                    connectionLogger?.log(
                        eventType = "VERSION_DATA",
                        level = ConnectionLogger.Level.INFO,
                        message = "VERSION characteristic notification",
                        details = "Hex: $hexString"
                    )
                }
            }
        }
        // Generic handler - capture command responses for awaitResponse()
        else {
            setNotificationCallback(characteristic).with { _, data ->
                val bytes = data.value
                if (bytes != null && bytes.isNotEmpty()) {
                    val opcode = bytes[0].toUByte()
                    Timber.d("[notify ${characteristic.uuid}] opcode=0x${opcode.toString(16)}")
                    _commandResponses.tryEmit(opcode)
                }
            }
        }

        enableNotifications(characteristic)
            .done { _ -> checkAllOperationsComplete() }
            .fail { _, _ -> checkAllOperationsComplete() }
            .enqueue()
    }
}
```

### NOTIFY_CHAR_UUIDS (8 characteristics from BleConstants.kt):
```kotlin
val NOTIFY_CHAR_UUIDS = listOf(
    UPDATE_STATE_CHAR_UUID,      // 383f7276-49af-4335-9072-f01b0f8acad6
    VERSION_CHAR_UUID,           // 74e994ac-0e80-4c02-9cd0-76cb31d3959b
    MODE_CHAR_UUID,              // 67d0dae0-5bfc-4ea2-acc9-ac784dee7f29
    REPS_CHAR_UUID,              // 8308f2a6-0875-4a94-a86f-5c5c5e1b068a
    HEURISTIC_CHAR_UUID,         // c7b73007-b245-4503-a1ed-9e4e97eb9802
    BLE_UPDATE_REQUEST_CHAR_UUID,// ef0e485a-8749-4314-b1be-01e57cd1712e
    UNKNOWN_AUTH_CHAR_UUID,      // 36e6c2ee-21c7-404e-aa9b-f74ca4728ad4
    SAMPLE_CHAR_UUID             // 90e991a6-c548-44ed-969b-eb541014eae3 (Monitor)
)
```

### Kable: Only observes 2 characteristics (MONITOR + REPS)

Missing notifications:
- ❌ UPDATE_STATE_CHAR
- ❌ VERSION_CHAR
- ❌ MODE_CHAR
- ❌ HEURISTIC_CHAR (not observed, only polled)
- ❌ BLE_UPDATE_REQUEST_CHAR
- ❌ UNKNOWN_AUTH_CHAR
- ❌ Connection priority request
- ❌ Pending operations tracking
- ❌ Command response capture

---

## 12. Monitor Data Processing

### Nordic (Lines 1296-1452) - handleMonitorData() - COMPLETE

```kotlin
private fun handleMonitorData(data: Data) {
    try {
        val bytes = data.value
        if (bytes == null) {
            Timber.w("Monitor data is null!")
            return
        }
        if (bytes.size < 16) {
            Timber.w("Monitor data too short: ${bytes.size} bytes")
            return
        }

        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)

        // v0.5.1-beta parsing format (PROVEN WORKING)
        val f0 = buffer.getShort(0).toInt() and 0xFFFF   // Offset 0-1 (ticks low)
        val f1 = buffer.getShort(2).toInt() and 0xFFFF   // Offset 2-3 (ticks high)
        val f2 = buffer.getShort(4).toInt() and 0xFFFF   // Offset 4-5 (posA)
        val f4 = buffer.getShort(8).toInt() and 0xFFFF   // Offset 8-9 (loadA*100)
        val f5 = buffer.getShort(10).toInt() and 0xFFFF  // Offset 10-11 (posB)
        val f7 = buffer.getShort(14).toInt() and 0xFFFF  // Offset 14-15 (loadB*100)

        // Reconstruct 32-bit tick counter
        val ticks = f0 + (f1 shl 16)

        var positionA = f2
        var positionB = f5

        // ===== SPIKE FILTERING =====
        // BLE transmission errors produce values > 50000
        // Per trainer documentation, valid range is -1000 to +1000 mm
        if (positionA > WorkoutConstants.POSITION_SPIKE_THRESHOLD) {
            positionA = lastGoodPosA
        } else {
            lastGoodPosA = positionA
        }

        if (positionB > WorkoutConstants.POSITION_SPIKE_THRESHOLD) {
            positionB = lastGoodPosB
        } else {
            lastGoodPosB = positionB
        }

        // Load in kg (device sends kg * 100)
        val loadA = f4 / 100.0f
        val loadB = f7 / 100.0f

        // Status (Bytes 16-17) if available
        var status = 0
        if (bytes.size >= 18) {
            status = buffer.getShort(16).toInt() and 0xFFFF
        }

        // ===== STATUS FLAG PROCESSING =====
        if (status != 0) {
            val isDeloadOccurred = (status and 0x8000) != 0  // Bit 15
            val isDeloadWarn = (status and 0x0040) != 0      // Bit 6
            val isSpotterActive = (status and 0x0020) != 0   // Bit 5

            if (isDeloadOccurred) {
                Timber.w("MACHINE STATUS: DELOAD_OCCURRED flag set - Status: 0x${status.toString(16)}")
                // NOTE: Do NOT return early - this breaks handle detection
                // The flag is informational; machine continues to operate

                // Emit deload event (debounced) for repository to handle
                val now = System.currentTimeMillis()
                if (now - lastDeloadEventTime > DELOAD_EVENT_DEBOUNCE_MS) {
                    lastDeloadEventTime = now
                    pollingScope.launch {
                        Timber.d("DELOAD_OCCURRED: Emitting event")
                        _deloadOccurredEvents.emit(Unit)
                    }
                }
            }
            if (isDeloadWarn) {
                Timber.w("MACHINE STATUS: DELOAD_WARN - Status: 0x${status.toString(16)}")
            }
            if (isSpotterActive) {
                Timber.d("MACHINE STATUS: SPOTTER_ACTIVE - Status: 0x${status.toString(16)}")
            }
        }

        // ===== SAMPLE VALIDATION =====
        if (!validateSample(positionA, loadA, positionB, loadB)) {
            return  // Skip invalid sample
        }

        // Update last good positions after validation
        lastGoodPosA = positionA
        lastGoodPosB = positionB

        // ===== VELOCITY CALCULATION =====
        val currentTime = System.currentTimeMillis()
        val velocityA = if (lastTimestamp > 0L) {
            val deltaTime = (currentTime - lastTimestamp) / 1000.0
            val deltaPos = positionA - lastPositionA
            if (deltaTime > 0) kotlin.math.abs(deltaPos / deltaTime) else 0.0
        } else 0.0

        val velocityB = if (lastTimestamp > 0L) {
            val deltaTime = (currentTime - lastTimestamp) / 1000.0
            val deltaPos = positionB - lastPositionB
            if (deltaTime > 0) kotlin.math.abs(deltaPos / deltaTime) else 0.0
        } else 0.0

        lastPositionA = positionA
        lastPositionB = positionB
        lastTimestamp = currentTime

        // Create metric with CALCULATED velocity
        val metric = WorkoutMetric(
            timestamp = currentTime,
            loadA = loadA,
            loadB = loadB,
            positionA = positionA,
            positionB = positionB,
            ticks = ticks,
            velocityA = velocityA,  // CALCULATED, not 0.0
            velocityB = velocityB,  // CALCULATED, not 0.0
            status = status
        )

        // ===== LOG TO CONNECTION LOGGER =====
        connectionLogger?.logMonitorDataReceived(
            currentDeviceName,
            currentDeviceAddress,
            positionA,
            positionB,
            loadA,
            loadB
        )

        _monitorData.tryEmit(metric)

        // ===== ANALYZE AND UPDATE HANDLE STATE =====
        val newHandleState = analyzeHandleState(metric)
        if (newHandleState != _handleState.value) {
            _handleState.value = newHandleState
            Timber.d("Handle state changed: $newHandleState")
        }

    } catch (e: Exception) {
        Timber.e(e, "Error parsing monitor data")
    }
}
```

### Kable (Lines 247-296) - parseMonitorData() - INCOMPLETE

```kotlin
private fun parseMonitorData(bytes: ByteArray): WorkoutMetric? {
    if (bytes.size < 16) return null

    val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)

    // Parsing matches Nordic format ✅
    val f0 = buffer.getShort(0).toInt() and 0xFFFF
    val f1 = buffer.getShort(2).toInt() and 0xFFFF
    val f2 = buffer.getShort(4).toInt() and 0xFFFF
    val f4 = buffer.getShort(8).toInt() and 0xFFFF
    val f5 = buffer.getShort(10).toInt() and 0xFFFF
    val f7 = buffer.getShort(14).toInt() and 0xFFFF

    val ticks = f0 + (f1 shl 16)
    val positionA = f2  // NO SPIKE FILTERING ❌
    val positionB = f5  // NO SPIKE FILTERING ❌

    val loadA = f4 / 100.0f
    val loadB = f7 / 100.0f

    var status = 0
    if (bytes.size >= 18) {
        status = buffer.getShort(16).toInt() and 0xFFFF
    }
    // NO STATUS FLAG PROCESSING ❌
    // NO DELOAD_OCCURRED DETECTION ❌

    return WorkoutMetric(
        timestamp = System.currentTimeMillis(),
        loadA = loadA,
        loadB = loadB,
        positionA = positionA,
        positionB = positionB,
        ticks = ticks,
        velocityA = 0.0,  // HARDCODED TO 0 ❌ - BREAKS HANDLE DETECTION
        velocityB = 0.0,  // HARDCODED TO 0 ❌ - BREAKS HANDLE DETECTION
        status = status
    )
    // NO validateSample() call ❌
    // NO analyzeHandleState() call ❌
    // NO connectionLogger integration ❌
}
```

### CRITICAL MISSING in Kable parseMonitorData():

| Feature | Nordic Line | Impact |
|---------|-------------|--------|
| Spike filtering (>50000) | 1328-1331 | **Data corruption** |
| lastGoodPosA/B tracking | 1330-1331 | **Data corruption** |
| Status flag parsing | 1347-1375 | Safety feature broken |
| DELOAD_OCCURRED detection | 1352-1367 | **Just Lift safety broken** |
| DELOAD_WARN detection | 1369-1371 | Warning missing |
| SPOTTER_ACTIVE detection | 1372-1374 | Info missing |
| Deload event emission (debounced) | 1360-1367 | **Just Lift safety broken** |
| validateSample() call | 1378-1380 | Invalid data processed |
| Velocity calculation | 1388-1402 | **HANDLE DETECTION BROKEN** |
| lastPositionA/B update | 1400-1401 | Velocity calculation broken |
| lastTimestamp update | 1402 | Velocity calculation broken |
| analyzeHandleState() call | 1443-1447 | **JUST LIFT COMPLETELY BROKEN** |
| ConnectionLogger integration | 1427-1434 | No debugging logs |

---

## 13. Sample Validation

### Nordic (Lines 1192-1212) - validateSample()

```kotlin
private fun validateSample(posA: Int, loadA: Float, posB: Int, loadB: Float): Boolean {
    // Official app range: -1000 to +1000 mm
    // Values outside this range are invalid (but > 50000 already filtered as spikes)
    if ((posA < WorkoutConstants.MIN_POSITION || posA > WorkoutConstants.MAX_POSITION) ||
        (posB < WorkoutConstants.MIN_POSITION || posB > WorkoutConstants.MAX_POSITION)) {
        Timber.w("Position out of range: posA=$posA, posB=$posB (valid: ${WorkoutConstants.MIN_POSITION} to ${WorkoutConstants.MAX_POSITION})")
        return false
    }

    // Strict validation checks position jumps (when enabled)
    // This matches trainer behavior for filtering transmission errors
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

### WorkoutConstants (Constants.kt Lines 18-24):
```kotlin
const val MAX_POSITION = 1000   // Maximum valid position (mm)
const val MIN_POSITION = -1000  // Minimum valid position (mm)
const val POSITION_SPIKE_THRESHOLD = 50000  // BLE error filter
```

### Kable Status: **ENTIRELY MISSING**

---

## 14. Rep Notification Processing

### Nordic (Lines 1467-1515) - handleRepNotification()

Both implementations are similar. Kable correctly parses the 24-byte packet.

---

## 15. Diagnostic & Heuristic Polling

### Nordic (Lines 804-945)

**Diagnostic Polling (500ms):**
```kotlin
fun startDiagnosticPolling() {
    propertyPollingJob?.cancel()
    propertyPollingJob = pollingScope.launch {
        Timber.d("Starting diagnostic polling (500ms interval - matches trainer)")
        var successfulReads = 0
        var failedReads = 0

        while (isActive) {
            try {
                val char = propertyCharacteristic
                if (char == null) {
                    Timber.w("Diagnostic characteristic is null - cannot maintain keep-alive!")
                    delay(500)
                    continue
                }

                readCharacteristic(char)
                    .with { _, data ->
                        successfulReads++
                        val bytes = data.value
                        if (bytes != null) {
                            parseDiagnosticData(bytes)
                        }
                    }
                    .fail { _, status ->
                        failedReads++
                        Timber.w("Diagnostic read failed (status: $status)")
                    }
                    .enqueue()

                delay(500)  // Official app interval
            } catch (e: Exception) {
                failedReads++
                Timber.e(e, "Exception in diagnostic polling")
                delay(500)
            }
        }
    }
}
```

**Heuristic Polling (250ms / 4Hz):**
```kotlin
fun startHeuristicPolling() {
    if (heuristicPollingJob?.isActive == true) return

    heuristicPollingJob = pollingScope.launch {
        Timber.d("Starting heuristic polling (250ms interval / 4Hz - matching trainer)")
        while (isActive) {
            try {
                heuristicCharacteristic?.let { char ->
                    readCharacteristic(char)
                        .with { _, data -> /* parse */ }
                        .enqueue()
                }
                delay(250)  // 4Hz
            } catch (e: Exception) {
                Timber.e(e, "Error in heuristic polling")
            }
        }
    }
}
```

### Kable: Similar implementation ✅

---

## 16. Heartbeat Implementation

### Nordic (Lines 847-918) - COMPLETE

```kotlin
private fun startHeartbeat() {
    heartbeatJob?.cancel()
    heartbeatJob = pollingScope.launch {
        Timber.d("Starting BLE heartbeat (interval=${HEARTBEAT_INTERVAL_MS}ms, read timeout=${HEARTBEAT_READ_TIMEOUT_MS}ms)")
        while (isActive) {
            // ATTEMPT READ FIRST with timeout
            val readSucceeded = try {
                withTimeoutOrNull(HEARTBEAT_READ_TIMEOUT_MS) {  // 1500ms
                    performHeartbeatRead()
                } ?: false
            } catch (e: Exception) {
                Timber.e(e, "Heartbeat read attempt crashed")
                false
            }

            // FALLBACK TO WRITE if read failed
            if (!readSucceeded) {
                sendHeartbeatNoOp()
            }

            delay(HEARTBEAT_INTERVAL_MS)  // 2000ms
        }
    }
}

private suspend fun performHeartbeatRead(): Boolean {
    val rxChar = nusRxCharacteristic
    if (rxChar == null) {
        Timber.w("Heartbeat read skipped - RX characteristic unavailable")
        return false
    }

    return suspendCancellableCoroutine { cont ->
        var resumed = false
        fun resumeOnce(result: Boolean) {
            if (!resumed && cont.isActive) {
                resumed = true
                cont.resume(result)
            }
        }

        try {
            readCharacteristic(rxChar)
                .with { _, _ ->
                    Timber.v("Heartbeat read callback fired")
                    resumeOnce(true)
                }
                .fail { _, status ->
                    Timber.w("Heartbeat read failed (status: $status)")
                    resumeOnce(false)
                }
                .enqueue()
        } catch (e: Exception) {
            Timber.e(e, "Heartbeat read enqueue failed")
            resumeOnce(false)
        }
    }
}

private fun sendHeartbeatNoOp() {
    val rxChar = nusRxCharacteristic
    if (rxChar == null) {
        Timber.w("Heartbeat write skipped - RX characteristic unavailable")
        return
    }

    try {
        // CRITICAL: 4-byte no-op
        writeCharacteristic(rxChar, HEARTBEAT_NO_OP, BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE)
            .fail { _, status -> Timber.w("Heartbeat no-op write failed (status: $status)") }
            .enqueue()
    } catch (e: Exception) {
        Timber.e(e, "Heartbeat no-op write enqueue failed")
    }
}
```

### Kable (Lines 178-186) - INCOMPLETE

```kotlin
private fun startHeartbeat() {
    heartbeatJob = scope.launch {
        while (isActive) {
            delay(HEARTBEAT_INTERVAL_MS)
            // WRONG: Only sends write, no read attempt first
            // MISSING: Read timeout fallback pattern
            peripheral?.sendCommand(byteArrayOf(HEARTBEAT_NO_OP, 0x00, 0x00, 0x00))
        }
    }
}
```

### MISSING in Kable:
- ❌ Read-first-then-write pattern
- ❌ `withTimeoutOrNull` for read attempt
- ❌ `HEARTBEAT_READ_TIMEOUT_MS` constant (1500ms)
- ❌ `suspendCancellableCoroutine` pattern
- ❌ Resume safety (`resumeOnce` pattern)
- ❌ Proper error handling per stage

### BUG:
```kotlin
// Nordic (CORRECT - 4 bytes):
private val HEARTBEAT_NO_OP = byteArrayOf(0x00, 0x00, 0x00, 0x00)

// Kable (constant is single byte, must construct array):
private const val HEARTBEAT_NO_OP = 0x00.toByte()
// Then constructs: byteArrayOf(HEARTBEAT_NO_OP, 0x00, 0x00, 0x00) - works but inconsistent
```

---

## 17. Command Sending

### Nordic (Lines 1071-1117)

```kotlin
fun sendCommand(data: ByteArray): Result<Unit> {
    return try {
        val timestamp = System.currentTimeMillis()

        // DIAGNOSTIC: Log characteristic state
        Timber.d("SEND_COMMAND_DEBUG: [$timestamp] sendCommand() called")
        Timber.d("SEND_COMMAND_DEBUG: nusRxCharacteristic is ${if (nusRxCharacteristic != null) "AVAILABLE" else "NULL"}")
        Timber.d("SEND_COMMAND_DEBUG: isConnected=${isConnected}")
        Timber.d("SEND_COMMAND_DEBUG: connectionState=${_connectionState.value}")

        nusRxCharacteristic?.let { characteristic ->
            // Log detailed hex dump for debugging
            Timber.d("Command size: ${data.size} bytes")
            Timber.d("Full hex: ${data.joinToString(" ") { "0x%02X".format(it) }}")

            // Show first 64 bytes formatted for easy reading
            if (data.isNotEmpty()) {
                val preview = data.take(64)
                val formatted = preview.chunked(16) { bytes ->
                    bytes.joinToString(" ") { "%02x".format(it) }
                }.joinToString("\n  ")
                Timber.d("First ${preview.size} bytes:\n  $formatted")
            }

            // CRITICAL: Use WRITE_TYPE_NO_RESPONSE (Write Command 0x52)
            // This is fire-and-forget - no waiting for acknowledgment
            // CRITICAL: NO .split() - frames must be sent whole!
            writeCharacteristic(characteristic, data, BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE)
                .enqueue()

            Result.success(Unit)
        } ?: Result.failure(Exception("NUS RX characteristic not available"))
    } catch (e: Exception) {
        Timber.e(e, "Failed to send command")
        Result.failure(e)
    }
}
```

### Kable (Lines 201-204) - MINIMAL

```kotlin
suspend fun sendCommand(data: ByteArray): Result<Unit> {
    val p = peripheral ?: return Result.failure(Exception("Not connected"))
    return p.sendCommand(data)
}
```

### MISSING in Kable:
- ❌ Detailed hex logging
- ❌ Formatted byte preview
- ❌ Characteristic state logging
- ❌ Connection state logging
- ❌ Timestamp logging

---

## 18. onServicesInvalidated Workaround

### Nordic (Lines 476-558) - Android 16 Pixel Bug Workaround

```kotlin
override fun onServicesInvalidated() {
    val timestamp = System.currentTimeMillis()
    Timber.w("⚠️ onServicesInvalidated() CALLED! [$timestamp]")

    // CRITICAL FIX for Android 16 Pixel BLE stack bug:
    // The trainer uses raw Android BLE and never gets this callback.
    // If we're still connected at the BLE level, IGNORE this callback and keep
    // existing characteristic references instead of triggering a disconnect loop.

    if (isConnected) {
        Timber.w("⚠️ BLE connection is STILL ACTIVE (isConnected=true)")
        Timber.w("⚠️ IGNORING service invalidation - keeping existing characteristic references")
        Timber.w("⚠️ This mimics trainer behavior (raw BLE has no such callback)")
        connectionLogger?.log(
            eventType = "SERVICES_INVALIDATED_IGNORED",
            level = ConnectionLogger.Level.WARNING,
            message = "onServicesInvalidated() called but isConnected=true - ignoring"
        )
        return  // Keep existing references, don't disconnect
    }

    // Only if we're actually disconnected at BLE level, clean up
    Timber.e("⚠️ BLE connection is DEAD (isConnected=false) - cleaning up")

    // Capture device info BEFORE clearing state (needed for reconnection)
    val deviceName = currentDeviceName
    val deviceAddress = currentDeviceAddress

    connectionLogger?.logError(
        deviceName ?: "Unknown",
        deviceAddress ?: "Unknown",
        "CHARACTERISTICS_INVALIDATED",
        "onServicesInvalidated() called with isConnected=false - cleaning up"
    )

    // NULL all characteristics
    nusRxCharacteristic = null
    monitorCharacteristic = null
    propertyCharacteristic = null
    repNotifyCharacteristic = null
    heuristicCharacteristic = null
    versionCharacteristic = null
    workoutCmdCharacteristics.clear()
    notifyCharacteristics.clear()

    _connectionState.value = ConnectionStatus.Disconnected
    stopPolling()

    // REQUEST AUTO-RECONNECT
    if (deviceAddress != null) {
        Timber.i("🔄 Requesting auto-reconnect to $deviceName ($deviceAddress)")
        pollingScope.launch {
            _reconnectionRequested.emit(
                ReconnectionRequest(
                    deviceName = deviceName,
                    deviceAddress = deviceAddress,
                    reason = "onServicesInvalidated",
                    timestamp = timestamp
                )
            )
        }
    }
}
```

### Kable: Handles disconnection via State flow but:
- ❌ No `isConnected` check workaround
- ❌ No auto-reconnect request flow
- ❌ No `ReconnectionRequest` data class

---

## 19. awaitResponse Protocol

### Nordic (Lines 1525-1543)

```kotlin
suspend fun awaitResponse(expectedOpcode: UByte, timeoutMs: Long = 5000L): Boolean {
    return try {
        val opcodeHex = expectedOpcode.toString(16).uppercase().padStart(2, '0')
        Timber.d("⏳ Waiting for response opcode 0x$opcodeHex (timeout: ${timeoutMs}ms)")

        val result = withTimeoutOrNull(timeoutMs) {
            commandResponses.filter { it == expectedOpcode }.first()
        }

        if (result != null) {
            Timber.d("✅ Received expected response opcode 0x$opcodeHex")
            true
        } else {
            Timber.w("⏱️ Timeout waiting for response opcode 0x$opcodeHex")
            false
        }
    } catch (e: Exception) {
        val opcodeHex = expectedOpcode.toString(16).uppercase().padStart(2, '0')
        Timber.e(e, "Error waiting for response opcode 0x$opcodeHex")
        false
    }
}
```

### Kable Status: **ENTIRELY MISSING**

---

## 20. UUID Definitions

### BleConstants.kt (Nordic) - COMPLETE

```kotlin
// 8 notification characteristics
val NOTIFY_CHAR_UUIDS = listOf(
    UPDATE_STATE_CHAR_UUID,      // 383f7276-49af-4335-9072-f01b0f8acad6
    VERSION_CHAR_UUID,           // 74e994ac-0e80-4c02-9cd0-76cb31d3959b
    MODE_CHAR_UUID,              // 67d0dae0-5bfc-4ea2-acc9-ac784dee7f29
    REPS_CHAR_UUID,              // 8308f2a6-0875-4a94-a86f-5c5c5e1b068a
    HEURISTIC_CHAR_UUID,         // c7b73007-b245-4503-a1ed-9e4e97eb9802
    BLE_UPDATE_REQUEST_CHAR_UUID,// ef0e485a-8749-4314-b1be-01e57cd1712e
    UNKNOWN_AUTH_CHAR_UUID,      // 36e6c2ee-21c7-404e-aa9b-f74ca4728ad4
    SAMPLE_CHAR_UUID             // 90e991a6-c548-44ed-969b-eb541014eae3
)

// 8 workout command characteristics
val WORKOUT_CMD_CHAR_UUIDS = listOf(
    UUID.fromString("6d094aa3-b60d-4916-8a55-8ed73fb9f6a5"),
    UUID.fromString("6d094aa3-b60d-4916-8a55-8ed73fb9f6a6"),
    UUID.fromString("6d094aa3-b60d-4916-8a55-8ed73fb9f6a7"),
    UUID.fromString("6d094aa3-b60d-4916-8a55-8ed73fb9f6a8"),
    UUID.fromString("6d094aa3-b60d-4916-8a55-8ed73fb9f6a9"),
    UUID.fromString("6d094aa3-b60d-4916-8a55-8ed73fb9f6aa"),
    UUID.fromString("6d094aa3-b60d-4916-8a55-8ed73fb9f6ab"),
    UUID.fromString("6d094aa3-b60d-4916-8a55-8ed73fb9f6ac")
)

// Timeouts
const val CONNECTION_TIMEOUT_MS = 15000L
const val GATT_OPERATION_TIMEOUT_MS = 5000L
const val SCAN_TIMEOUT_MS = 30000L
```

### KableUuids.kt - INCOMPLETE

Missing:
- ❌ UPDATE_STATE_CHAR
- ❌ MODE_CHAR
- ❌ BLE_UPDATE_REQUEST_CHAR
- ❌ UNKNOWN_AUTH_CHAR
- ❌ CABLE_LEFT_CHAR
- ❌ CABLE_RIGHT_CHAR
- ❌ WIFI_STATE_CHAR
- ❌ All 8 WORKOUT_CMD_CHAR_UUIDS
- ❌ Connection timeouts
- ❌ NOTIFY_CHAR_UUIDS list

---

## 21. Repository Layer

### BleRepositoryImpl (Nordic) - Key Features:

1. **Connection with retry/timeout** (Lines 423-452)
2. **2-second delay before INIT** (Lines 431-449)
3. **Auto-reconnect handler** (Lines 364-414)
4. **Deload event handler** (Lines 352-362)
5. **Cached workout params** (Line 120)
6. **ConnectionLogger throughout**

### KableBleRepositoryImpl - MISSING:
- ❌ Connection timeout/retry
- ❌ 2-second delay before INIT
- ❌ Auto-reconnect handler
- ❌ Deload event handler
- ❌ Cached workout params for re-arm
- ❌ ConnectionLogger integration

---

## 22. ConnectionLogger Integration

### Nordic calls ConnectionLogger at (~25 locations):

| Location | Method Called |
|----------|--------------|
| Firmware detected | `log("FIRMWARE_DETECTED", ...)` |
| Model number read | `log("MODEL_NUMBER", ...)` |
| Version data received | `log("VERSION_DATA", ...)` |
| Services invalidated (ignored) | `log("SERVICES_INVALIDATED_IGNORED", ...)` |
| Services invalidated (cleanup) | `logError("CHARACTERISTICS_INVALIDATED", ...)` |
| Device disconnected | `logDisconnected(...)` |
| Monitor data received | `logMonitorDataReceived(...)` (every 10th) |
| Reconnection requested | `log("RECONNECT_REQUESTED", ...)` |
| Reconnection success | `log("RECONNECT_SUCCESS", ...)` |
| Reconnection failed | `log("RECONNECT_FAILED", ...)` |
| Scan started/stopped | `logScanStarted/Stopped()` |
| Device found | `logDeviceFound(...)` |
| Connection started | `logConnectionStarted(...)` |
| Connection success | `logConnectionSuccess(...)` |
| Connection failed | `logConnectionFailed(...)` |
| Init started/success/failed | `logInit*()` |
| Command sent | `logCommandSent(...)` |
| Command success/failed | `logCommand*()` |
| Polling started/stopped | `logPolling*()` |

### Kable: **NO ConnectionLogger integration**

---

## 23. Complete Missing Features Summary

### CRITICAL (Blocks core functionality)

| Feature | Lines | Impact |
|---------|-------|--------|
| Handle state machine | 1223-1294 | **Just Lift completely broken** |
| Velocity calculation | 1388-1402 | **Handle detection broken** |
| Spike filtering | 1328-1331 | **Data corruption** |
| Deload event flow | 129-134, 1360-1367 | **Just Lift safety broken** |
| validateSample() | 1192-1212 | Invalid data processed |
| @Volatile annotations | 83-97 | Race conditions |

### HIGH (Affects reliability)

| Feature | Lines | Impact |
|---------|-------|--------|
| Reconnection flow | 139-144, 542-557 | No auto-recovery on Pixel |
| Command response flow | 155-160, 1525-1543 | Protocol handshake fails |
| Heartbeat read-then-write | 847-918 | Premature disconnects |
| HEARTBEAT_READ_TIMEOUT_MS | 80 | Heartbeat hangs |
| Connection priority HIGH | 605-618 | Unstable connection |
| Pending ops tracking | 585-603 | Race in init |

### MEDIUM (Affects diagnostics)

| Feature | Lines | Impact |
|---------|-------|--------|
| Firmware version detection | 323-416 | No version info |
| VERSION characteristic | 425-473 | No device info |
| ConnectionLogger integration | Throughout | No debugging logs |
| 6 additional notify chars | 636-711 | Missing notifications |
| 8 workout cmd chars | 305-312 | Can't test protocol |
| Service enumeration logging | 212-230 | No discovery logs |

---

## 24. Exact Constant Values

```kotlin
// Timing (MUST MATCH EXACTLY)
val HEARTBEAT_INTERVAL_MS = 2000L
val HEARTBEAT_READ_TIMEOUT_MS = 1500L
val DIAGNOSTIC_POLL_INTERVAL_MS = 500L
val HEURISTIC_POLL_INTERVAL_MS = 250L  // 4Hz
val DELOAD_EVENT_DEBOUNCE_MS = 2000L
val CONNECTION_TIMEOUT_MS = 15000L
val INIT_DELAY_AFTER_CONNECT_MS = 2000L
val COMMAND_INTER_DELAY_MS = 50L

// Thresholds (MUST MATCH EXACTLY)
val HANDLE_GRABBED_THRESHOLD = 8.0
val HANDLE_REST_THRESHOLD = 5.0
val VELOCITY_THRESHOLD = 100.0
val POSITION_SPIKE_THRESHOLD = 50000
val MIN_POSITION = -1000
val MAX_POSITION = 1000
val POSITION_JUMP_THRESHOLD = 200  // For strict validation

// Byte arrays (MUST MATCH EXACTLY)
val HEARTBEAT_NO_OP = byteArrayOf(0x00, 0x00, 0x00, 0x00)  // 4 bytes!
```

---

## 25. Data Class Definitions

```kotlin
// RepNotification (Nordic Lines 1590-1629)
data class RepNotification(
    val topCounter: Int,        // u32: Concentric completions
    val completeCounter: Int,   // u32: Eccentric completions
    val repsRomCount: Int = 0,  // u16: Warmup reps (proper ROM)
    val repsSetCount: Int = 0,  // u16: Working set reps
    val rangeTop: Float = 0f,
    val rangeBottom: Float = 0f,
    val rawData: ByteArray,
    val timestamp: Long
)

// ReconnectionRequest (Nordic Lines 1635-1640)
data class ReconnectionRequest(
    val deviceName: String?,
    val deviceAddress: String,
    val reason: String,
    val timestamp: Long
)

// HandleState enum (Nordic Lines 1569-1574)
enum class HandleState {
    WaitingForRest,  // Initial state
    Released,        // Armed for grab detection
    Grabbed,         // Handles grabbed
    Moving           // Intermediate state
}

// ConnectionStatus (Nordic Lines 1563-1567)
sealed class ConnectionStatus {
    object Disconnected : ConnectionStatus()
    object Ready : ConnectionStatus()
    data class Error(val message: String) : ConnectionStatus()
}
```

---

## Conclusion

The Kable implementation requires **~1,200 additional lines of code** to reach feature parity with Nordic. The most critical gaps are:

1. **Handle state machine** - Without this, Just Lift mode is COMPLETELY BROKEN
2. **Velocity calculation** - Required for handle detection
3. **Spike filtering** - Data corruption without this
4. **@Volatile annotations** - Thread safety failures
5. **Deload event flow** - Safety feature broken
6. **Heartbeat read-then-write** - Connection stability issues

Migration MUST proceed in strict priority order, testing each component thoroughly before moving to the next.
