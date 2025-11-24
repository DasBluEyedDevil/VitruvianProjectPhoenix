# BLE and UUID Protocol Changes Timeline

**Generated:** 2025-11-24
**Repository:** VitruvianProjectPhoenix
**Total Commits Analyzed:** 314

This document tracks all changes to BLE (Bluetooth Low Energy) and UUID protocols from the initial commit to present, organized chronologically.

---

## Timeline of Changes

### Phase 1: Initial Implementation (Oct 27-28, 2025)

#### Commit `5dcd944` - Oct 27, 2025 - **INITIAL PROJECT STRUCTURE**
**Files:** `VitruvianBleManager.kt`, `BleRepositoryImpl.kt`, `ProtocolBuilder.kt`, `Constants.kt`

**Initial BLE UUIDs Defined:**
- `NUS_SERVICE_UUID`: `6e400001-b5a3-f393-e0a9-e50e24dcca9e` (Nordic UART Service)
- `NUS_RX_CHAR_UUID`: `6e400002-b5a3-f393-e0a9-e50e24dcca9e`
- `MONITOR_CHAR_UUID`: `90e991a6-c548-44ed-969b-eb541014eae3`
- `PROPERTY_CHAR_UUID`: `5fa538ec-d041-42f6-bbd6-c30d475387b7`
- `REP_NOTIFY_CHAR_UUID`: `8308f2a6-0875-4a94-a86f-5c5c5e1b068a`

**Initial Protocol Commands:**
- `0x0A` - INIT command (4 bytes)
- `0x11` - INIT_PRESET (34 bytes with coefficient table)
- `0x04` - PROGRAM parameters frame (96 bytes)

**Initial Configuration:**
- Scan timeout: 10 seconds
- Connection timeout: 15 seconds

---

#### Commit `76b2d1d` - Oct 27, 2025 - **ENHANCED BLE SCANNING**

**Changes:**
- Increased scan timeout from 10s to **30s** for better device discovery
- Added detailed logging for BLE scanning and connection processes
- Improved error handling for Bluetooth adapter availability
- Implemented polling for monitor and property characteristics during workouts

**Impact:** More reliable device discovery but introduced potential issues with long connection attempts.

---

### Phase 2: Protocol Refinements (Oct 28 - Nov 5, 2025)

#### Commit `dd7dc75` - Oct 29, 2025 - **CRITICAL WEIGHT PROTOCOL BUG FIX** 🔴

**Bug:** Machine was providing only 50% of the requested resistance (100 lbs input → 50 lbs actual)

**Root Cause:** Protocol offset `0x58` expected TOTAL weight (for both cables), but app was sending per-cable weight.

**Fix Applied:**
```kotlin
// BEFORE (buggy):
buffer.putFloat(0x58, params.weightPerCableKg)

// AFTER (fixed):
val totalWeightKg = params.weightPerCableKg * 2.0f
buffer.putFloat(0x58, totalWeightKg)
```

**Protocol Offsets Documented:**
- `0x54`: Effective weight
- `0x58`: Total weight (machine splits between cables)
- `0x5C`: Progression/regression kg per rep

---

#### Commit `87a65a8` - Oct 31, 2025 - **ECHO MODE PARAMETER CHANGES**

**Echo Level Gain/Cap Modifications:**
| Level | BEFORE | AFTER |
|-------|--------|-------|
| HARD | gain=1.0, cap=50 | gain=0.75, cap=55 |
| HARDER | gain=1.25, cap=40 | gain=1.0, cap=50 |
| HARDEST | gain=1.667, cap=30 | gain=1.25, cap=40 |
| EPIC | gain=3.333, cap=15 | gain=1.667, cap=30 |

---

#### Commit `b543d78` - Nov 2, 2025 - **WORKOUT TYPE REFACTORING**

**Changes:**
- Refactored `WorkoutMode` to `ProgramMode`
- Added firmware quirk compensation: subtract progression from base weight when sending
- Echo mode parameters reverted to original values

---

#### Commit `d42aeba` - Nov 5, 2025 - **COMPREHENSIVE BLE DEBUGGING SYSTEM** (Issue #18)

**New Infrastructure:**
- `ConnectionLogEntity` and `ConnectionLogDao` for persistent logging
- `ConnectionLogger` singleton with categorized event types
- Database migration v13 → v14 for connection_logs table

**Event Types Logged:**
- Connection lifecycle (scan, connect, disconnect, errors)
- Command sends (LED color, workout start, init sequence)
- Data polling events
- Device discovery with RSSI

---

### Phase 3: Connection Stability Issues (Nov 5-13, 2025)

#### Commit `a855072` - Nov 5, 2025 - **ENHANCED CONNECTION LOGS**

Added detailed hex dumps and real-time data logging for debugging connectivity issues.

---

#### Commit `ce29854` - Nov 8, 2025 - **BLE DISCONNECTION DETECTION**

**Fix:** Detect BLE disconnection during workouts and prevent screen lock.

---

#### Commit `c891132` - Nov 12, 2025 - **BLE MANAGER INITIALIZATION ENHANCEMENT**

**Major Changes:**
- Added `AtomicInteger` tracking for pending BLE operations
- MTU request + notification enables tracked before marking device "Ready"
- Property polling started immediately after initialization as keep-alive mechanism

**New Pattern:**
```kotlin
val pendingOperations = AtomicInteger(notifyCharacteristics.size + 1)
// Operations decrement counter; Ready state set when counter reaches 0
```

---

#### Commit `fb16254` - Nov 12, 2025 - **BLE CONNECTION CANCEL FIX** (Issue #96)

**Problem:** Cancel button during connection didn't stop BLE connection attempts.

**Fix:**
- Added `cancelConnection()` method to `BleRepository`
- Track connecting BLE manager separately for cancellation
- Proper cleanup of BLE manager on timeout or user cancellation

---

#### Commit `57efb4c` - Nov 13, 2025 - **CHARACTERISTIC INVALIDATION FIX** (Issue #91) 🔴

**Critical Bug:** `onServicesInvalidated()` was nulling characteristics without updating connection state, causing silent command failures.

**Root Cause:**
1. `onServicesInvalidated()` called ~5 seconds after connection
2. All characteristics nulled but state still showed "Connected"
3. Commands failed silently with "NUS RX characteristic not available"

**Fix:**
- `onServicesInvalidated()` now updates state to Disconnected
- Added `onDeviceDisconnected()` callback implementation
- Added `onDeviceNotSupported()` handling

---

### Phase 4: Android 16 Compatibility & 5-Second Disconnect (Nov 13-22, 2025)

#### Commit `9587b9f` - Nov 18, 2025 - **5-SECOND DISCONNECT FIX** (Issue #131) 🔴

**Problem:** Devices disconnecting exactly ~5 seconds after successful connection.

**Root Cause Analysis:**
1. Device connects successfully
2. Initialization completes
3. ~5 seconds later: `onServicesInvalidated()` called
4. Without HIGH connection priority, Android BLE uses intervals that may be too long

**Fix:**
- Request HIGH connection priority during initialization:
```kotlin
requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH)
```
- Enhanced property polling with comprehensive error handling
- Added success/failure tracking with periodic logging

---

#### Commit `2713144` - Nov 18, 2025 - **BLE QUEUE DRAIN DELAY** (Issue #124)

**Problem:** Connection drops when exercise timer reaches zero and warmup completes.

**Root Cause:** Race condition where INIT command sent while BLE queue still processing.

**Fix:** Added 250ms delay after `stopPolling()` before sending INIT command:
```kotlin
delay(250) // BLE_QUEUE_DRAIN_DELAY_MS
```

**Note:** This was later **reverted** in commit `e582e90` after root cause analysis.

---

#### Commit `e582e90` - Nov 18, 2025 - **INIT FAILURE DISCONNECT FIX** (Issue #124) 🔴

**Correct Root Cause Analysis from Logs:**
1. Device connects → ConnectionState.Connected
2. `sendInitSequence()` called after 2 seconds
3. INIT command (0x0A) sent, device never responds with 0x0B
4. Init times out after 5 seconds → INIT_FAILED logged
5. **BUG:** Connection state stayed "Connected" despite init failure
6. User starts workout on uninitialized device
7. Device disconnects ~5 seconds later

**Fix:**
- Set connection state to Error on init failure
- Call `disconnect()` to force device disconnection
- User must reconnect (which retries initialization)
- **Reverted** the 250ms delay from previous commit

---

#### Commit `7a319a4` - Nov 19, 2025 - **INIT RETRY LOGIC FOR ANDROID 16**

**New Configuration Constants:**
```kotlin
INIT_RESPONSE_TIMEOUT_MS = 8000L  // Increased from 5000ms
INIT_MAX_RETRIES = 2
INIT_RETRY_DELAY_MS = 1000L  // Uses exponential backoff
```

**Retry Logic:**
- Up to 3 attempts (initial + 2 retries)
- Exponential backoff: 1s, 2s between retries
- Enhanced error logging with device state information

---

### Phase 5: BLE Constants Refactoring (Nov 21-22, 2025)

#### Commit `63082db` - Nov 21, 2025 - **DOMAIN MODEL & BLE MANAGER UPDATES**

**New Characteristics Added:**
- `heuristicCharacteristic` for phase statistics
- `versionCharacteristic` for firmware version

**New Data Parsing:**
- `parseDiagnosticData()` - Parses 20-byte diagnostic packets
- `parseHeuristicData()` - Parses 48-byte heuristic statistics

**Renamed Functions:**
- `startPropertyPolling()` → `startDiagnosticPolling()`

**Sample Validation Added:**
```kotlin
// Position: -1000.0 to 1000.0
// Force: 0.0 to 100.0
```

---

#### Commit `d850693` - Nov 22, 2025 - **BLECONSTANTS FILE DELETED**

BleConstants.kt temporarily deleted during entity refactoring. All UUIDs were lost.

---

#### Commit `f9cd37b` - Nov 22, 2025 - **BLECONSTANTS RECREATED** (Current State)

**Final UUID Definitions:**

| UUID Name | Value | Purpose |
|-----------|-------|---------|
| `GATT_SERVICE_UUID` | `00001801-0000-1000-8000-00805f9b34fb` | Standard GATT service |
| `NUS_SERVICE_UUID` | `6e400001-b5a3-f393-e0a9-e50e24dcca9e` | Nordic UART Service |
| `NUS_RX_CHAR_UUID` | `6e400002-b5a3-f393-e0a9-e50e24dcca9e` | NUS RX characteristic |
| `MONITOR_CHAR_UUID` | `90e991a6-c548-44ed-969b-eb541014eae3` | Real-time position/force data |
| `PROPERTY_CHAR_UUID` | `5fa538ec-d041-42f6-bbd6-c30d475387b7` | Property/keep-alive |
| `DIAGNOSTIC_CHAR_UUID` | `5fa538ec-d041-42f6-bbd6-c30d475387b7` | Alias for property |
| `REP_NOTIFY_CHAR_UUID` | `8308f2a6-0875-4a94-a86f-5c5c5e1b068a` | Rep count notifications |
| `HEURISTIC_CHAR_UUID` | `c7b73007-b245-4503-a1ed-9e4e97eb9802` | Phase statistics |
| `VERSION_CHAR_UUID` | `36e6c2ee-21c7-404e-aa9b-f74ca4728ad4` | Firmware version |

**⚠️ VERSION_CHAR_UUID DISCREPANCY:**
- Initially in commit `63082db`: `74e994ac-0e80-4c02-9cd0-76cb31d3959b`
- In final version `f9cd37b`: `36e6c2ee-21c7-404e-aa9b-f74ca4728ad4`

---

## Summary of Critical Issues Found

### 1. Weight Protocol Bug (Fixed Oct 29)
- **Symptom:** 50% resistance
- **Cause:** Wrong value at offset 0x58
- **Status:** ✅ Fixed

### 2. 5-Second Disconnect (Fixed Nov 18)
- **Symptom:** Device disconnects ~5s after connection
- **Cause:** Init failures not properly handled + low connection priority
- **Status:** ✅ Fixed with HIGH priority + init failure detection

### 3. Characteristic Invalidation (Fixed Nov 13)
- **Symptom:** Commands fail silently
- **Cause:** State not updated when characteristics invalidated
- **Status:** ✅ Fixed

### 4. Android 16 BLE Compatibility (Fixed Nov 18-19)
- **Symptom:** Connection instability on Android 16
- **Cause:** Stricter BLE timing enforcement
- **Status:** ✅ Fixed with retry logic + increased timeouts

### 5. VERSION_CHAR_UUID Change (Nov 21-22)
- **Symptom:** Unknown - potential version reading issues
- **Cause:** UUID changed during refactoring
- **Status:** ⚠️ Needs verification

---

## Protocol Commands Reference

| Opcode | Command | Response | Size | Purpose |
|--------|---------|----------|------|---------|
| `0x0A` | INIT_COMMAND | `0x0B` | 4 bytes | Initialize device |
| `0x11` | INIT_PRESET | `0x12` | 34 bytes | Set coefficient table |
| `0x03` | START | - | 4 bytes | Start workout |
| `0x04` | PROGRAM_PARAMS | - | 96 bytes | Set workout parameters |
| `0x05` | STOP | - | 4 bytes | Stop workout |
| `0x4E` | ECHO_CONTROL | - | 32 bytes | Echo mode settings |

---

## Configuration Constants Evolution

| Constant | Initial | Current | Notes |
|----------|---------|---------|-------|
| `SCAN_TIMEOUT_MS` | 10,000 | 30,000 | Increased for reliability |
| `CONNECTION_TIMEOUT_MS` | 15,000 | 15,000 | Unchanged |
| `INIT_RESPONSE_TIMEOUT_MS` | 5,000 | 8,000 | Increased for Android 16 |
| `INIT_MAX_RETRIES` | 0 | 2 | Added retry logic |
| `BLE_QUEUE_DRAIN_DELAY_MS` | - | 250 | Added for race condition prevention |

---

## Recommendations for Debugging

1. **If device won't connect:** Check scan timeout, verify device name starts with "Vee"
2. **If disconnects after 5s:** Verify HIGH connection priority, check init sequence success
3. **If wrong resistance:** Verify weight calculation at offset 0x58 (should be total, not per-cable)
4. **If commands fail silently:** Check characteristic validity after connection
5. **If Android 16 issues:** Ensure retry logic is active, check BLE queue drain delays

---

*This timeline was compiled from analysis of 314 commits. For specific commit details, use:*
```bash
git show <commit-hash> -p -- "*.kt"
```
