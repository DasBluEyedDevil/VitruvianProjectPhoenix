# Kable BLE Migration Plan - 1:1 Line-by-Line Analysis

## Executive Summary

This document provides a comprehensive 1:1 migration plan from the Nordic BLE library to Kable BLE for the Vitruvian Trainer app. The migration covers all BLE functionality to ensure zero loss of functionality.

**Current State:**
- Nordic BLE: `VitruvianBleManager.kt` (1,641 lines) - Production
- Kable BLE: `KableBleManager.kt` (435 lines) - Partial implementation

**Key Finding:** The Kable implementation is approximately 26% complete. Critical functionality is missing.

---

## File-by-File Migration Matrix

### 1. BLE Manager Core

| Component | Nordic File | Nordic Lines | Kable File | Kable Lines | Status |
|-----------|-------------|--------------|------------|-------------|--------|
| BLE Manager | `VitruvianBleManager.kt` | 1-1641 | `KableBleManager.kt` | 1-435 | **PARTIAL** |
| Peripheral Wrapper | N/A (embedded) | - | `KableVitruvianPeripheral.kt` | 1-228 | **PARTIAL** |
| Scanner | `BleRepositoryImpl.kt` | 123-238 | `KableBleScanner.kt` | 1-76 | **COMPLETE** |
| UUIDs | `BleConstants.kt` | 1-109 | `KableUuids.kt` | 1-33 | **PARTIAL** |
| Exceptions | `BleExceptions.kt` | 1-79 | `BleExceptions.kt` | 1-79 | **SHARED** |
| Repository | `BleRepositoryImpl.kt` | 1-829 | `KableBleRepositoryImpl.kt` | 1-401 | **PARTIAL** |

---

## Detailed Line-by-Line Comparison

### 2. VitruvianBleManager.kt → KableBleManager.kt

#### 2.1 Class Definition & Dependencies

| Nordic (Lines 1-51) | Kable Equivalent | Status |
|---------------------|------------------|--------|
| `class VitruvianBleManager(context: Context, connectionLogger) : BleManager(context)` | `class KableBleManager` | ✅ |
| `private var currentDeviceName: String?` (line 53) | `private var deviceName: String?` (line 53) | ✅ |
| `private var currentDeviceAddress: String?` (line 54) | `private var deviceAddress: String?` (line 54) | ✅ |
| `fun setDeviceInfo(name, address)` (lines 56-59) | N/A - set during connect | ⚠️ Refactor |

#### 2.2 GATT Characteristics Storage

| Nordic (Lines 61-71) | Kable Equivalent | Status |
|---------------------|------------------|--------|
| `private var nusRxCharacteristic: BluetoothGattCharacteristic?` | Handled by `KableVitruvianPeripheral.nusRxChar` | ✅ |
| `private var monitorCharacteristic: BluetoothGattCharacteristic?` | `KableVitruvianPeripheral.monitorChar` | ✅ |
| `private var propertyCharacteristic: BluetoothGattCharacteristic?` | `KableVitruvianPeripheral.diagnosticChar` | ✅ |
| `private var repNotifyCharacteristic: BluetoothGattCharacteristic?` | `KableVitruvianPeripheral.repsChar` | ✅ |
| `private var heuristicCharacteristic: BluetoothGattCharacteristic?` | `KableVitruvianPeripheral.heuristicChar` | ✅ |
| `private var versionCharacteristic: BluetoothGattCharacteristic?` | ❌ **MISSING** | ❌ |
| `private val workoutCmdCharacteristics` (line 70-71) | ❌ **MISSING** | ❌ |

#### 2.3 Polling Jobs & Coroutines

| Nordic (Lines 73-81) | Kable Equivalent | Status |
|---------------------|------------------|--------|
| `private val pollingScope = CoroutineScope(Dispatchers.Main + SupervisorJob())` | `private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)` (line 45) | ✅ |
| `private var monitorPollingJob: Job?` | N/A - uses Flow observation instead | ⚠️ Different |
| `private var propertyPollingJob: Job?` | `private var diagnosticPollingJob: Job?` (line 48) | ✅ |
| `private var heuristicPollingJob: Job?` | `private var heuristicPollingJob: Job?` (line 49) | ✅ |
| `private var heartbeatJob: Job?` | `private var heartbeatJob: Job?` (line 50) | ✅ |
| `HEARTBEAT_INTERVAL_MS = 2000L` | `HEARTBEAT_INTERVAL_MS = 2000L` (line 41) | ✅ |
| `HEARTBEAT_READ_TIMEOUT_MS = 1500L` | ❌ **MISSING** | ❌ |
| `HEARTBEAT_NO_OP = byteArrayOf(0x00, 0x00, 0x00, 0x00)` | `HEARTBEAT_NO_OP = 0x00.toByte()` - **WRONG** | ❌ |

#### 2.4 Position Tracking & Validation

| Nordic (Lines 84-98) | Kable Equivalent | Status |
|---------------------|------------------|--------|
| `@Volatile private var lastGoodPosA = 0` | ❌ **MISSING** | ❌ |
| `@Volatile private var lastGoodPosB = 0` | ❌ **MISSING** | ❌ |
| `@Volatile private var lastPositionA = 0` | ❌ **MISSING** | ❌ |
| `@Volatile private var lastPositionB = 0` | ❌ **MISSING** | ❌ |
| `@Volatile private var lastTimestamp = 0L` | ❌ **MISSING** | ❌ |
| `@Volatile private var strictValidationEnabled = false` | ❌ **MISSING** | ❌ |
| `var detectedFirmwareVersion: String?` | ❌ **MISSING** | ❌ |
| `var negotiatedMtu: Int?` | ❌ **MISSING** | ❌ |

#### 2.5 State Flows

| Nordic (Lines 100-161) | Kable Equivalent | Status |
|---------------------|------------------|--------|
| `_connectionState = MutableStateFlow<ConnectionStatus>` | `_connectionState = MutableStateFlow<ConnectionStatus>` (line 57) | ✅ |
| `_diagnosticData = MutableStateFlow<DiagnosticDetails?>` | `_diagnosticData = MutableStateFlow<DiagnosticDetails?>` (line 77) | ✅ |
| `_heuristicData = MutableStateFlow<HeuristicStatistics?>` | `_heuristicData = MutableStateFlow<HeuristicStatistics?>` (line 81) | ✅ |
| `_monitorData = MutableSharedFlow<WorkoutMetric>` with buffer 64 | `_monitorData = MutableSharedFlow<WorkoutMetric>` with buffer 64 (line 61) | ✅ |
| `_repEvents = MutableSharedFlow<RepNotification>` | `_repEvents = MutableSharedFlow<RepNotification>` (line 69) | ✅ |
| `_handleState = MutableStateFlow<HandleState>` | `_handleState = MutableStateFlow<HandleState>` (line 85) | ✅ |
| `_deloadOccurredEvents = MutableSharedFlow<Unit>` | ❌ **MISSING** - Critical for Just Lift | ❌ |
| `_reconnectionRequested = MutableSharedFlow<ReconnectionRequest>` | ❌ **MISSING** - Critical for stability | ❌ |
| `_commandResponses = MutableSharedFlow<UByte>` | ❌ **MISSING** | ❌ |

#### 2.6 Handle Detection Thresholds

| Nordic (Lines 163-175) | Kable Equivalent | Status |
|---------------------|------------------|--------|
| `HANDLE_GRABBED_THRESHOLD = 8.0` | ❌ **MISSING** | ❌ |
| `HANDLE_REST_THRESHOLD = 5.0` | ❌ **MISSING** | ❌ |
| `VELOCITY_THRESHOLD = 100.0` | ❌ **MISSING** | ❌ |
| `minPositionSeen = Double.MAX_VALUE` | ❌ **MISSING** | ❌ |
| `maxPositionSeen = Double.MIN_VALUE` | ❌ **MISSING** | ❌ |
| `forceAboveGrabThresholdStart: Long?` | ❌ **MISSING** | ❌ |
| `forceBelowReleaseThresholdStart: Long?` | ❌ **MISSING** | ❌ |

---

### 3. GATT Callback → Kable Peripheral Configuration

#### 3.1 Service Discovery

| Nordic (Lines 206-315) `VitruvianGattCallback` | Kable Equivalent | Status |
|---------------------|------------------|--------|
| `isRequiredServiceSupported(gatt)` | `Peripheral(advertisement) { onServicesDiscovered { ... } }` | ⚠️ Partial |
| Service/characteristic logging | ❌ **MISSING** | ❌ |
| `tryReadFirmwareVersion(gatt)` (lines 323-416) | ❌ **MISSING** | ❌ |
| `tryReadVitruvianVersion(gatt)` (lines 425-473) | ❌ **MISSING** | ❌ |
| `onServicesInvalidated()` (lines 476-558) | Kable handles via State.Disconnected | ⚠️ Different |
| `onDeviceDisconnected()` (lines 561-577) | Kable handles via State.Disconnected | ✅ |

#### 3.2 Initialization

| Nordic (Lines 580-711) `initialize()` | Kable Equivalent | Status |
|---------------------|------------------|--------|
| `requestConnectionPriority(HIGH)` | Kable doesn't expose this directly | ⚠️ Missing |
| `requestMtu(247)` | Kable handles automatically | ✅ |
| Enable notifications for all characteristics | `peripheral.observe(char)` | ✅ |
| Special handler for REP_NOTIFY | `repNotifications` Flow | ✅ |
| Special handler for MONITOR | `monitorData` Flow | ✅ |
| Special handler for VERSION | ❌ **MISSING** | ❌ |
| Generic handler capturing command opcodes | ❌ **MISSING** | ❌ |

---

### 4. Monitor Polling → Kable Flow Observation

#### 4.1 Monitor Data Collection

| Nordic (Lines 728-798) `startMonitorPolling()` | Kable Equivalent | Status |
|---------------------|------------------|--------|
| Reset position tracking for new workout | ❌ **MISSING** | ❌ |
| Reset notification counter | ❌ **MISSING** | ❌ |
| Set handleState based on forAutoStart | ❌ **MISSING** | ❌ |
| Cancel existing job before starting new | Uses Flow observation, different approach | ⚠️ |
| 100ms polling with readCharacteristic | Uses `peripheral.observe()` (notification-based) | ✅ Better |
| Log periodic success/failure stats | ❌ **MISSING** | ❌ |

```kotlin
// Nordic approach (polling):
monitorPollingJob = pollingScope.launch {
    while (isActive) {
        readCharacteristic(char).with { _, data ->
            handleMonitorData(data)
        }.enqueue()
        delay(100)
    }
}

// Kable approach (Flow observation):
peripheral.monitorData
    .onEach { bytes -> parseMonitorData(bytes)?.let { _monitorData.emit(it) } }
    .launchIn(scope)
```

**Kable advantage:** Notification-based is more efficient than polling.

#### 4.2 Diagnostic Polling

| Nordic (Lines 804-842) `startDiagnosticPolling()` | Kable Equivalent (Lines 152-163) | Status |
|---------------------|------------------|--------|
| 500ms polling interval | 500ms polling interval | ✅ |
| Read diagnostic characteristic | `peripheral?.readDiagnostic()` | ✅ |
| Parse and update `_diagnosticData` | Same pattern | ✅ |
| Error handling with failure count | ❌ **MISSING** | ❌ |

#### 4.3 Heuristic Polling

| Nordic (Lines 924-945) `startHeuristicPolling()` | Kable Equivalent (Lines 165-176) | Status |
|---------------------|------------------|--------|
| 250ms polling interval (4Hz) | 250ms polling interval | ✅ |
| Read heuristic characteristic | `peripheral?.readHeuristic()` | ✅ |
| Parse and update `_heuristicData` | Same pattern | ✅ |

#### 4.4 Heartbeat

| Nordic (Lines 847-918) | Kable Equivalent (Lines 178-186) | Status |
|---------------------|------------------|--------|
| 2000ms interval | 2000ms interval | ✅ |
| Attempt RX read with timeout | ❌ **MISSING** - just sends no-op | ❌ |
| Fall back to no-op write | `sendCommand(byteArrayOf(HEARTBEAT_NO_OP, 0x00, 0x00, 0x00))` | ⚠️ Partial |
| `HEARTBEAT_NO_OP = byteArrayOf(0x00, 0x00, 0x00, 0x00)` | Uses single byte - **BUG** | ❌ |

---

### 5. Data Parsing

#### 5.1 Diagnostic Data Parsing

| Nordic (Lines 947-971) `parseDiagnosticData()` | Kable (Lines 359-388) | Status |
|---------------------|------------------|--------|
| Check bytes.size >= 20 | Check bytes.size < 20 | ✅ |
| Little-endian ByteBuffer | Little-endian ByteBuffer | ✅ |
| Parse seconds (Int) | Same | ✅ |
| Parse 4 faults (Short) | Same | ✅ |
| Parse 8 temps (Byte) | Same | ✅ |
| Create DiagnosticDetails | Same, but Kable adds timestamp | ✅ Better |

#### 5.2 Heuristic Data Parsing

| Nordic (Lines 973-1004) `parseHeuristicData()` | Kable (Lines 396-434) | Status |
|---------------------|------------------|--------|
| Check bytes.size >= 48 | Same | ✅ |
| Parse 6 concentric floats | Same | ✅ |
| Parse 6 eccentric floats | Same | ✅ |
| Create HeuristicStatistics | Same, Kable adds timestamp | ✅ Better |

#### 5.3 Monitor Data Parsing

| Nordic (Lines 1296-1452) `handleMonitorData()` | Kable (Lines 247-296) `parseMonitorData()` | Status |
|---------------------|------------------|--------|
| Check bytes.size >= 16 | Same | ✅ |
| Parse ticks (u32 from 2x u16) | Same | ✅ |
| Parse positionA, positionB | Same | ✅ |
| Parse loadA, loadB (÷100) | Same | ✅ |
| Parse status (bytes 16-17) | Same | ✅ |
| **Spike filtering** (>50000) | ❌ **MISSING** | ❌ |
| **Status flag logging** | ❌ **MISSING** | ❌ |
| **DELOAD_OCCURRED detection** | ❌ **MISSING** - Critical | ❌ |
| **validateSample()** | ❌ **MISSING** | ❌ |
| **Velocity calculation** | ❌ **MISSING** (set to 0.0) | ❌ |
| **analyzeHandleState()** | ❌ **MISSING** | ❌ |
| Log to ConnectionLogger | ❌ **MISSING** | ❌ |

```kotlin
// Critical missing code from Nordic (lines 1326-1331):
// Spike filtering - BLE transmission errors produce values > 50000
if (positionA > WorkoutConstants.POSITION_SPIKE_THRESHOLD)
    positionA = lastGoodPosA
else
    lastGoodPosA = positionA
```

#### 5.4 Rep Notification Parsing

| Nordic (Lines 1467-1515) `handleRepNotification()` | Kable (Lines 310-350) `parseRepData()` | Status |
|---------------------|------------------|--------|
| Check bytes.size >= 24 | Same | ✅ |
| Parse upCounter (u32 @ 0) | Same | ✅ |
| Parse downCounter (u32 @ 4) | Same | ✅ |
| Parse rangeTop (float @ 8) | Same | ✅ |
| Parse rangeBottom (float @ 12) | Same | ✅ |
| Parse repsRomCount (u16 @ 16) | Same | ✅ |
| Parse repsRomTotal (u16 @ 18) | ❌ **MISSING** - not used but should parse | ⚠️ |
| Parse repsSetCount (u16 @ 20) | Same | ✅ |
| Parse repsSetTotal (u16 @ 22) | ❌ **MISSING** - not used but should parse | ⚠️ |
| Detailed logging | Less detailed in Kable | ⚠️ |

---

### 6. Handle State Detection

| Nordic (Lines 1223-1294) `analyzeHandleState()` | Kable Equivalent | Status |
|---------------------|------------------|--------|
| Track min/max position for tuning | ❌ **MISSING** | ❌ |
| WaitingForRest → Released transition | ❌ **MISSING** | ❌ |
| Released/Moving → Grabbed (position + velocity) | ❌ **MISSING** | ❌ |
| Grabbed → Released (both handles at rest) | ❌ **MISSING** | ❌ |
| Support single-handle exercises | ❌ **MISSING** | ❌ |

```kotlin
// CRITICAL: This entire state machine is missing from Kable
private fun analyzeHandleState(metric: WorkoutMetric): HandleState {
    val posA = metric.positionA.toDouble()
    val posB = metric.positionB.toDouble()
    val velocityA = metric.velocityA
    val velocityB = metric.velocityB

    return when (_handleState.value) {
        HandleState.WaitingForRest -> {
            if (posA < HANDLE_REST_THRESHOLD && posB < HANDLE_REST_THRESHOLD) {
                HandleState.Released
            } else {
                HandleState.WaitingForRest
            }
        }
        HandleState.Released, HandleState.Moving -> {
            val aActive = (posA > HANDLE_GRABBED_THRESHOLD) && (velocityA > VELOCITY_THRESHOLD)
            val bActive = (posB > HANDLE_GRABBED_THRESHOLD) && (velocityB > VELOCITY_THRESHOLD)

            if (aActive || bActive) HandleState.Grabbed
            else if (posA > HANDLE_GRABBED_THRESHOLD || posB > HANDLE_GRABBED_THRESHOLD) HandleState.Moving
            else HandleState.Released
        }
        HandleState.Grabbed -> {
            if (posA < HANDLE_REST_THRESHOLD && posB < HANDLE_REST_THRESHOLD) {
                HandleState.Released
            } else {
                HandleState.Grabbed
            }
        }
    }
}
```

---

### 7. Command Sending

| Nordic (Lines 1072-1117) `sendCommand()` | Kable (Lines 201-204) | Status |
|---------------------|------------------|--------|
| Null check for characteristic | Null check for peripheral | ✅ |
| Detailed hex logging | ❌ **MISSING** | ❌ |
| Write with WRITE_TYPE_NO_RESPONSE | `WriteType.WithoutResponse` | ✅ |
| No .split() for frame integrity | N/A - Kable doesn't split | ✅ |

---

### 8. Connection Management

#### 8.1 Connect

| Nordic (`BleRepositoryImpl.kt` lines 241-461) | Kable (Lines 91-128) | Status |
|---------------------|------------------|--------|
| Stop scanning | Same | ✅ |
| Clean up existing connection | `disconnect()` call | ✅ |
| Create BleManager with ConnectionLogger | ❌ **MISSING** - no logging | ❌ |
| Set device info | Set during connection | ✅ |
| Observe connection state | Same pattern | ✅ |
| Forward monitor data | Same pattern | ✅ |
| Forward rep events | Same pattern | ✅ |
| Forward handle state | Same pattern | ✅ |
| **Handle deload events** | ❌ **MISSING** | ❌ |
| **Handle reconnection requests** | ❌ **MISSING** | ❌ |
| Timeout with retry | ❌ **MISSING** | ❌ |
| Auto-connect option | ❌ **MISSING** | ❌ |
| Send INIT after connection | ❌ **MISSING** | ❌ |

#### 8.2 Disconnect

| Nordic (Lines 497-552) | Kable (Lines 209-216) | Status |
|---------------------|------------------|--------|
| Stop polling first | `stopPolling()` | ✅ |
| Cleanup coroutine jobs | ✅ | ✅ |
| Timeout protection | ❌ **MISSING** | ❌ |
| Force close on timeout | `close()` call | ✅ |

---

### 9. UUID Definitions

| BleConstants.kt | KableUuids.kt | Status |
|-----------------|---------------|--------|
| `NUS_SERVICE_UUID` | `NUS_SERVICE` | ✅ |
| `NUS_RX_CHAR_UUID` | `NUS_RX_CHAR` | ✅ |
| `MONITOR_CHAR_UUID` | `MONITOR_CHAR` | ✅ |
| `DIAGNOSTIC_CHAR_UUID` | `DIAGNOSTIC_CHAR` | ✅ |
| `REP_NOTIFY_CHAR_UUID` | `REPS_CHAR` | ✅ |
| `HEURISTIC_CHAR_UUID` | `HEURISTIC_CHAR` | ✅ |
| `VERSION_CHAR_UUID` | ❌ **MISSING** | ❌ |
| `NOTIFY_CHAR_UUIDS` list | ❌ **MISSING** | ❌ |
| `WORKOUT_CMD_CHAR_UUIDS` list | ❌ **MISSING** | ❌ |
| `UNKNOWN_AUTH_CHAR_UUID` | ❌ **MISSING** | ❌ |
| `CABLE_LEFT_CHAR_UUID` | ❌ **MISSING** | ❌ |
| `CABLE_RIGHT_CHAR_UUID` | ❌ **MISSING** | ❌ |
| `MODE_CHAR_UUID` | ❌ **MISSING** | ❌ |
| `WIFI_STATE_CHAR_UUID` | ❌ **MISSING** | ❌ |
| `UPDATE_STATE_CHAR_UUID` | ❌ **MISSING** | ❌ |
| `BLE_UPDATE_REQUEST_CHAR_UUID` | ❌ **MISSING** | ❌ |
| `CCCD` | `CCCD` | ✅ |
| Connection timeouts | ❌ **MISSING** | ❌ |

---

### 10. Repository Implementation

| BleRepositoryImpl (Nordic) | KableBleRepositoryImpl | Status |
|---------------------------|------------------------|--------|
| Lines: 829 | Lines: 401 | 48% |
| Scan with ScanSettings | Kable Scanner Flow | ⚠️ Different |
| Filter by name prefix | Same filter | ✅ |
| Connect with retry/timeout | Simple connect | ❌ |
| Send INIT after connect | ❌ **MISSING** | ❌ |
| Handle reconnection requests | ❌ **MISSING** | ❌ |
| Handle deload events | ❌ **MISSING** | ❌ |
| `startWorkout()` | Same structure | ✅ |
| `stopWorkout()` | Same structure | ✅ |
| `sendStopCommand()` | Same structure | ✅ |
| `setColorScheme()` | Different implementation | ⚠️ |
| `enableHandleDetection()` | Stub only | ❌ |
| `enableJustLiftWaitingMode()` | Stub only | ❌ |
| `restartMonitorPolling()` | Stub only | ❌ |
| `testOfficialAppProtocol()` | Not implemented | ❌ |
| ConnectionLogger integration | ❌ **MISSING** | ❌ |

---

## Critical Missing Features Summary

### 🔴 CRITICAL (Blocks basic functionality)

1. **Handle State Detection** (Lines 1223-1294)
   - `analyzeHandleState()` - Entire state machine missing
   - Position thresholds and velocity checks
   - Support for single-handle exercises

2. **Deload Event Handling** (Lines 1360-1368)
   - `_deloadOccurredEvents` flow
   - DELOAD_OCCURRED flag detection in status
   - Debouncing logic

3. **Position Spike Filtering** (Lines 1326-1331)
   - `lastGoodPosA/B` tracking
   - Filter values > 50000

4. **Velocity Calculation** (Lines 1388-1402)
   - Calculate velocity from position delta
   - Required for handle grab detection

5. **Heartbeat Implementation** (Lines 847-918)
   - RX read with timeout fallback
   - Correct 4-byte no-op

### 🟡 IMPORTANT (Affects reliability)

6. **Reconnection Request Flow** (Lines 139-144, 543-557)
   - Auto-reconnect after service invalidation
   - Android 16/Pixel BLE bug workaround

7. **Connection Logger Integration**
   - All logging calls throughout

8. **Sample Validation** (Lines 1192-1212)
   - `validateSample()` method
   - Position range checks
   - Strict validation mode

9. **Command Response Flow** (Lines 155-160, 1525-1543)
   - `awaitResponse()` for protocol handshake

10. **Just Lift Waiting Mode** (Lines 1055-1065)
    - `enableJustLiftWaitingMode()`
    - Reset position tracking

### 🟢 NICE TO HAVE (Future features)

11. **Firmware/Version Detection** (Lines 323-473)
    - Read DIS service
    - VERSION characteristic parsing

12. **Official App Protocol Test** (Lines 1124-1179)
    - `testOfficialAppProtocol()`

13. **Workout Command Characteristics** (Lines 70-71, 305-313)
    - 8 additional write characteristics

---

## Migration Action Plan

### Phase 1: Core Data Flow (Week 1)
1. Add missing position tracking variables
2. Implement spike filtering in `parseMonitorData()`
3. Implement velocity calculation
4. Add `analyzeHandleState()` state machine
5. Fix heartbeat to use 4-byte no-op

### Phase 2: Event Handling (Week 1-2)
1. Add `_deloadOccurredEvents` flow
2. Implement DELOAD_OCCURRED detection in status parsing
3. Add `_reconnectionRequested` flow
4. Add `_commandResponses` flow

### Phase 3: Connection Robustness (Week 2)
1. Add connection timeout/retry logic
2. Implement reconnection handling
3. Add disconnect timeout protection
4. Integrate ConnectionLogger

### Phase 4: Feature Parity (Week 2-3)
1. Implement `validateSample()`
2. Add `enableJustLiftWaitingMode()`
3. Implement `awaitResponse()`
4. Add missing UUIDs to KableUuids

### Phase 5: Testing & Validation (Week 3)
1. Unit tests for data parsing
2. Integration tests with real device
3. Performance comparison with Nordic
4. Regression testing for all workout modes

---

## Code Templates for Missing Features

### Template 1: Handle State Detection

```kotlin
// Add to KableBleManager.kt

private val HANDLE_GRABBED_THRESHOLD = 8.0
private val HANDLE_REST_THRESHOLD = 5.0
private val VELOCITY_THRESHOLD = 100.0

@Volatile private var lastGoodPosA = 0
@Volatile private var lastGoodPosB = 0
@Volatile private var lastPositionA = 0
@Volatile private var lastPositionB = 0
@Volatile private var lastTimestamp = 0L

private fun analyzeHandleState(metric: WorkoutMetric): HandleState {
    val posA = metric.positionA.toDouble()
    val posB = metric.positionB.toDouble()
    val velocityA = metric.velocityA
    val velocityB = metric.velocityB

    return when (_handleState.value) {
        HandleState.WaitingForRest -> {
            if (posA < HANDLE_REST_THRESHOLD && posB < HANDLE_REST_THRESHOLD) {
                Timber.d("Handles at REST - auto-start ARMED")
                HandleState.Released
            } else {
                HandleState.WaitingForRest
            }
        }
        HandleState.Released, HandleState.Moving -> {
            val aActive = (posA > HANDLE_GRABBED_THRESHOLD) && (velocityA > VELOCITY_THRESHOLD)
            val bActive = (posB > HANDLE_GRABBED_THRESHOLD) && (velocityB > VELOCITY_THRESHOLD)

            when {
                aActive || bActive -> {
                    Timber.i("GRAB CONFIRMED")
                    HandleState.Grabbed
                }
                posA > HANDLE_GRABBED_THRESHOLD || posB > HANDLE_GRABBED_THRESHOLD -> HandleState.Moving
                else -> HandleState.Released
            }
        }
        HandleState.Grabbed -> {
            if (posA < HANDLE_REST_THRESHOLD && posB < HANDLE_REST_THRESHOLD) {
                Timber.d("RELEASE DETECTED")
                HandleState.Released
            } else {
                HandleState.Grabbed
            }
        }
    }
}
```

### Template 2: Velocity Calculation

```kotlin
// Add to parseMonitorData()

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
```

### Template 3: Spike Filtering

```kotlin
// Add to parseMonitorData()

val POSITION_SPIKE_THRESHOLD = 50000

var positionA = f2
var positionB = f5

// Spike filtering - BLE transmission errors produce values > 50000
if (positionA > POSITION_SPIKE_THRESHOLD) {
    positionA = lastGoodPosA
} else {
    lastGoodPosA = positionA
}

if (positionB > POSITION_SPIKE_THRESHOLD) {
    positionB = lastGoodPosB
} else {
    lastGoodPosB = positionB
}
```

### Template 4: Deload Event Handling

```kotlin
// Add to KableBleManager.kt

private val _deloadOccurredEvents = MutableSharedFlow<Unit>(
    replay = 0,
    extraBufferCapacity = 8,
    onBufferOverflow = BufferOverflow.DROP_OLDEST
)
val deloadOccurredEvents: SharedFlow<Unit> = _deloadOccurredEvents.asSharedFlow()

private var lastDeloadEventTime = 0L
private val DELOAD_EVENT_DEBOUNCE_MS = 2000L

// In parseMonitorData(), after parsing status:
if (status != 0) {
    val isDeloadOccurred = (status and 0x8000) != 0
    if (isDeloadOccurred) {
        Timber.w("DELOAD_OCCURRED flag detected")
        val now = System.currentTimeMillis()
        if (now - lastDeloadEventTime > DELOAD_EVENT_DEBOUNCE_MS) {
            lastDeloadEventTime = now
            scope.launch { _deloadOccurredEvents.emit(Unit) }
        }
    }
}
```

---

## Testing Checklist

### Unit Tests
- [ ] `parseMonitorData()` with valid data
- [ ] `parseMonitorData()` with spike values
- [ ] `parseRepData()` with full 24-byte packet
- [ ] `parseDiagnosticData()` with faults
- [ ] `parseHeuristicData()` with phase statistics
- [ ] `analyzeHandleState()` state transitions
- [ ] Velocity calculation accuracy

### Integration Tests
- [ ] Connect to real device
- [ ] Monitor data flow during workout
- [ ] Rep counting accuracy
- [ ] Handle detection for Just Lift
- [ ] Deload event handling
- [ ] Reconnection after disconnect
- [ ] Heartbeat keeps connection alive

### Regression Tests
- [ ] Old School mode workout
- [ ] Echo mode workout
- [ ] Just Lift auto-start
- [ ] AMRAP mode
- [ ] Progression/Regression
- [ ] Color scheme changes
- [ ] Multiple device connections

---

## Conclusion

The Kable implementation is approximately **26% complete**. The core data parsing is in place, but critical features for Just Lift mode, handle detection, and connection reliability are missing.

**Recommended Priority:**
1. Handle state detection (blocks Just Lift)
2. Velocity calculation (required for handle detection)
3. Spike filtering (data quality)
4. Deload event handling (safety)
5. Reconnection logic (stability)

The migration can be completed incrementally while keeping the Nordic implementation as fallback via `FeatureFlags.useKableBle`.
