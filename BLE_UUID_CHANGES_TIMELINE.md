# BLE, UUID, and Protocol Changes Timeline

**Generated:** 2025-11-24
**Repository:** VitruvianProjectPhoenix
**Total Commits Analyzed:** 314

This document tracks all changes to BLE communication, UUID definitions, signal interpretation, protocol commands, and workout logic from the initial commit to present.

---

## Table of Contents
1. [UUID Definitions](#uuid-definitions)
2. [Monitor Data Parsing (Position/Force/Ticks)](#monitor-data-parsing)
3. [Rep Counting and Notification Protocol](#rep-counting-protocol)
4. [Handle State Detection and Cable Sensing](#handle-state-detection)
5. [Just Lift Auto-Start/Auto-Stop Logic](#just-lift-logic)
6. [Workout Command Protocol](#workout-command-protocol)
7. [Echo Mode Protocol](#echo-mode-protocol)
8. [Connection Stability Fixes](#connection-stability)
9. [Critical Safety Issues](#safety-issues)

---

## UUID Definitions

### Current UUIDs (as of Nov 22, 2025)

| UUID Name | Value | Purpose |
|-----------|-------|---------|
| `GATT_SERVICE_UUID` | `00001801-0000-1000-8000-00805f9b34fb` | Standard GATT service |
| `NUS_SERVICE_UUID` | `6e400001-b5a3-f393-e0a9-e50e24dcca9e` | Nordic UART Service (main communication) |
| `NUS_RX_CHAR_UUID` | `6e400002-b5a3-f393-e0a9-e50e24dcca9e` | Write commands to device |
| `MONITOR_CHAR_UUID` | `90e991a6-c548-44ed-969b-eb541014eae3` | Real-time position/force data (polled 100ms) |
| `PROPERTY_CHAR_UUID` | `5fa538ec-d041-42f6-bbd6-c30d475387b7` | Keep-alive polling (500ms) |
| `DIAGNOSTIC_CHAR_UUID` | `5fa538ec-d041-42f6-bbd6-c30d475387b7` | Alias for property |
| `REP_NOTIFY_CHAR_UUID` | `8308f2a6-0875-4a94-a86f-5c5c5e1b068a` | Rep count notifications |
| `HEURISTIC_CHAR_UUID` | `c7b73007-b245-4503-a1ed-9e4e97eb9802` | Phase statistics |
| `VERSION_CHAR_UUID` | `36e6c2ee-21c7-404e-aa9b-f74ca4728ad4` | Firmware version |

### UUID Changes Timeline

| Date | Commit | Change |
|------|--------|--------|
| Oct 27 | `5dcd944` | Initial UUIDs: NUS_SERVICE, NUS_RX, MONITOR, PROPERTY, REP_NOTIFY |
| Nov 21 | `63082db` | Added `HEURISTIC_CHAR_UUID` and `VERSION_CHAR_UUID` (`74e994ac...`) |
| Nov 22 | `d850693` | BleConstants.kt deleted during refactoring |
| Nov 22 | `f9cd37b` | BleConstants.kt recreated - **VERSION_CHAR_UUID changed to `36e6c2ee...`** |

---

## Monitor Data Parsing

### Data Format (16 bytes from MONITOR_CHAR_UUID)

```
Offset  Type    Field
0x00    u32     ticks (timestamp counter)
0x04    f32     loadA (force cable A, raw / 100 = kg)
0x08    f32     loadB (force cable B, raw / 100 = kg)
0x0C    u16     positionA (cable A position)
0x0E    u16     positionB (cable B position)
```

### Parsing Code (VitruvianBleManager.kt)
```kotlin
val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
val ticks = buffer.getInt(0)
val loadA = buffer.getFloat(4) / 100.0f  // Convert to kg
val loadB = buffer.getFloat(8) / 100.0f  // Convert to kg
val positionA = buffer.getShort(12).toInt() and 0xFFFF
val positionB = buffer.getShort(14).toInt() and 0xFFFF
```

### Monitor Data Changes Timeline

| Date | Commit | Change |
|------|--------|--------|
| Oct 27 | `5dcd944` | Initial parsing: ticks, loadA, loadB, positionA, positionB |
| Nov 5 | `25ef4db` | Added `velocityA` calculation for handle detection |
| Nov 13 | `f821126` | Added `velocityB` for single-handle operation |
| Nov 21 | `63082db` | Added `parseDiagnosticData()` (20 bytes) and `parseHeuristicData()` (48 bytes) |

### Velocity Calculation (Added Nov 5 - `25ef4db`)
```kotlin
val velocityA = if (lastTimestamp > 0L) {
    val deltaTime = (currentTime - lastTimestamp) / 1000.0  // seconds
    val deltaPos = positionA - lastPositionA
    if (deltaTime > 0) Math.abs(deltaPos / deltaTime) else 0.0
} else 0.0
```

---

## Rep Counting Protocol

### Rep Notification Data Format (from REP_NOTIFY_CHAR_UUID)

```
Offset  Type    Field
0x00    u16     topCounter (increments at peak contraction)
0x04    u16     completeCounter (increments at concentric START)
```

### Critical Discovery: Counter Increment Timing

**Machine behavior discovered Nov 5 (Issue #18):**
- `completeCounter` increments at **START** of concentric phase (not end!)
- This caused machine to release tension when user BEGINS final rep

### Rep Counting Changes Timeline

| Date | Commit | Change | Impact |
|------|--------|--------|--------|
| Oct 27 | `f187088` | Initial `RepCounterFromMachine` - counted on `completeCounter` | Counted at wrong moment |
| Nov 5 | `3ff2af1` | **CRITICAL:** Send reps+1 to machine to compensate | Prevents early release |
| Nov 11 | `5e9142f` | Changed rep counting to trigger on `topCounter` | Intuitive counting at peak |

### The reps+1 Fix (`3ff2af1`)

**Problem:** Machine releases tension when you BEGIN the final rep.

**Example with 4 working reps:**
- Before fix: Send 7 (4+3) → Counter hits 7 at START of rep 4 → RELEASES IMMEDIATELY
- After fix: Send 8 (4+3+1) → User completes rep 4 fully before release

**Solution:** Send one extra rep to machine:
```kotlin
// Program mode: offset 0x04
frame[0x04] = if (params.isJustLift) 0xFF.toByte() else (params.reps + 3 + 1).toByte()

// Echo mode: offset 0x05
frame[0x05] = if (isJustLift) 0xFF.toByte() else (targetReps + 1).toByte()
```

### Rep Counting Location Fix (`5e9142f`)

Changed rep counting from `completeCounter` to `topCounter`:
- **Before:** Reps counted at bottom (start of concentric) - felt "backwards"
- **After:** Reps counted at top (peak contraction) - matches trainer

---

## Handle State Detection

### Detection Parameters (Current Values)

| Constant | Value | Purpose |
|----------|-------|---------|
| `HANDLE_GRABBED_THRESHOLD` | 8.0 | Position > 8.0 = handles grabbed |
| `HANDLE_REST_THRESHOLD` | 2.5 | Position < 2.5 = handles at rest |
| `VELOCITY_THRESHOLD` | 100.0 | Velocity > 100 units/s = moving |

### Handle State Enum
```kotlin
enum class HandleState {
    Released,  // Handles at rest (pos < 2.5)
    Grabbed,   // Handles lifted with movement (pos > 8.0 AND vel > 100)
    Moving     // Position up but no velocity (intermediate state)
}
```

### Handle Detection Changes Timeline

| Date | Commit | Change |
|------|--------|--------|
| Nov 3 | `c1647e4` | Initial: baseline averaging (10 samples), delta threshold of 100 |
| Nov 5 | `25ef4db` | Changed to position hysteresis + velocity confirmation |
| Nov 13 | `f821126` | **Single-handle support:** Check BOTH handles, start if EITHER active |

### Initial Detection (`c1647e4`)
```kotlin
// Baseline averaging approach
baselinePositionA = ((baselinePositionA * count) + positionA) / (count + 1)
val deltaA = positionA - baselinePositionA
val isGrabbed = deltaA > GRAB_THRESHOLD // GRAB_THRESHOLD = 100
```

### Position-Based Detection (`25ef4db`)
```kotlin
// Simple position hysteresis with velocity confirmation
val posA = metric.positionA.toDouble()
val velocity = Math.abs(metric.velocityA)

return when (currentState) {
    HandleState.Released -> {
        if (posA > HANDLE_GRABBED_THRESHOLD && velocity > VELOCITY_THRESHOLD) {
            HandleState.Grabbed
        } else HandleState.Released
    }
    HandleState.Grabbed -> {
        if (posA < HANDLE_REST_THRESHOLD) HandleState.Released
        else HandleState.Grabbed
    }
}
```

### Single-Handle Detection (`f821126`)
```kotlin
// Workout starts if EITHER handle is grabbed and moving
val aActive = handleAGrabbed && handleAMoving
val bActive = handleBGrabbed && handleBMoving
if (aActive || bActive) HandleState.Grabbed

// Workout stops only if BOTH handles are at rest
val aReleased = posA < HANDLE_REST_THRESHOLD
val bReleased = posB < HANDLE_REST_THRESHOLD
if (aReleased && bReleased) HandleState.Released
```

---

## Just Lift Auto-Start/Auto-Stop Logic

### Auto-Start Timeline

| Date | Commit | Timer Duration | Notes |
|------|--------|----------------|-------|
| Nov 3 | `c1647e4` | 5 seconds | Initial implementation |
| Nov 5 | `5d2ff4b` | 1.2 seconds | Match trainer |
| Nov 11 | `5727c92` | **3 seconds** | User preference |

### Auto-Start Flow (Current)
1. User grabs handles → `HandleState.Grabbed`
2. Timer starts: 3, 2, 1 countdown (visible to user)
3. If handles released before timer → cancel
4. Timer completes → `startWorkout(isJustLiftMode=true)`

### Auto-Stop Timeline

| Date | Commit | Change | Impact |
|------|--------|--------|--------|
| Nov 3 | `c1647e4` | Initial: 5-second timer when handles released |
| Nov 12 | `dc6f5c0` | **CRITICAL FIX:** Require both position AND handles released | Fixed false triggers |

### Auto-Stop Bug Fix (`dc6f5c0`)

**Problem:** Auto-stop triggered during normal eccentric (lowering) phase.

**Root Cause:** Only checked if cable position was in "danger zone" (within 5% of bottom), reached during every rep.

**Solution:** Require BOTH conditions:
```kotlin
val inDangerZone = repCounter.isInDangerZone(metric.positionA, metric.positionB)
val handlesReleased = currentHandleState == HandleState.Released

// Only trigger if BOTH conditions met
if (inDangerZone && handlesReleased) {
    // Start auto-stop timer (5 seconds)
}
```

### Danger Zone Light Fix (`f3f372a`)

**Problem:** Red lights stayed on after AMRAP auto-stop.

**Solution:** Restart monitor polling after set completion to clear alarm state:
```kotlin
// AMRAP mode: Restart monitor polling to clear danger zone alarm
bleRepository.restartMonitorPolling()
```

---

## Workout Command Protocol

### Command Opcodes

| Opcode | Command | Response | Size | Purpose |
|--------|---------|----------|------|---------|
| `0x0A` | INIT_COMMAND | `0x0B` | 4 bytes | Initialize device / stop workout |
| `0x11` | INIT_PRESET | `0x12` | 34 bytes | Set coefficient table |
| `0x03` | START | - | 4 bytes | Start workout |
| `0x04` | PROGRAM_PARAMS | - | 96 bytes | Set workout parameters |
| `0x05` | STOP | - | 4 bytes | Stop workout (unused - use 0x0A) |
| `0x4E` | ECHO_CONTROL | - | 32 bytes | Echo mode settings |

### PROGRAM_PARAMS Frame (96 bytes at 0x04)

```
Offset  Type    Field                   Notes
0x00    u32     Command ID (0x04)
0x04    u8      Total reps              0xFF for Just Lift, else reps+warmup+1
0x05-07 u8[3]   Constants (0x03,0x03,0x00)
0x08    f32     Constant (5.0)
0x0C    f32     Constant (5.0)
0x30-4F byte[32] Mode profile block      32 bytes of mode-specific parameters
0x54    f32     Effective weight (kg)
0x58    f32     Total weight (kg)       CRITICAL: Machine splits between cables
0x5C    f32     Progression (kg/rep)
```

### Weight Protocol Bug (Fixed Oct 29 - `dd7dc75`)

**Bug:** Machine gave 50% of requested resistance.

**Root Cause:** Offset 0x58 expects TOTAL weight; app was sending per-cable.

**Fix:**
```kotlin
// BEFORE (wrong):
buffer.putFloat(0x58, params.weightPerCableKg)

// AFTER (correct):
val totalWeightKg = params.weightPerCableKg * 2.0f
buffer.putFloat(0x58, totalWeightKg)
```

### stopWorkout() Protocol Change (`25ef4db`)

**Discovery:** Web app uses INIT_COMMAND (0x0A) to stop, not STOP (0x05).

```kotlin
suspend fun stopWorkout() {
    // 1. Stop polling first
    bleManager?.stopPolling()

    // 2. Send INIT command (0x0A) - contextual based on current state
    val initCommand = ProtocolBuilder.buildInitCommand() // [0x0A, 0x00, 0x00, 0x00]
    bleManager?.sendCommand(initCommand)
}
```

---

## Echo Mode Protocol

### Echo Control Frame (32 bytes at 0x4E)

```
Offset  Type    Field
0x00    u32     Command ID (0x4E = 78 decimal)
0x04    u8      Warmup reps
0x05    u8      Target reps (0xFF for Just Lift, else targetReps+1)
0x06-07 u16     Reserved (0)
0x08    u16     Eccentric percentage
0x0A    u16     Concentric percentage (always 50)
0x0C    f32     Smoothing
0x10    f32     Gain
0x14    f32     Cap
0x18    f32     Floor
0x1C    f32     Neg limit
```

### Echo Level Parameters

| Level | levelValue | Gain | Cap |
|-------|------------|------|-----|
| HARD | 0 | 1.0 | 50.0 |
| HARDER | 1 | 1.25 | 40.0 |
| HARDEST | 2 | 1.667 | 30.0 |
| EPIC | 3 | 3.333 | 15.0 |

### Echo Mode Changes Timeline

| Date | Commit | Change |
|------|--------|--------|
| Oct 31 | `87a65a8` | Changed gain/cap values (HARD: 0.75/55) |
| Nov 2 | `b543d78` | Reverted to original values |
| Nov 12 | `4531828` | Added detailed logging for frame construction |
| Nov 12 | `4531828` | **Fixed echoLevel mapping:** use `levelValue` not array index |

### EchoLevel Mapping Fix (`4531828`)

**Bug:** Database stored `echoLevel` as array index, but enum has `levelValue`.

**Fix:**
```kotlin
// BEFORE (wrong - treated as array index):
EchoLevel.values().getOrNull(echoLevel)

// AFTER (correct - find by levelValue):
EchoLevel.values().find { it.levelValue == echoLevel }
```

---

## Connection Stability

### Connection Stability Fixes Timeline

| Date | Commit | Issue | Fix |
|------|--------|-------|-----|
| Oct 27 | `76b2d1d` | Scan timeout too short | Increased 10s → 30s |
| Nov 5 | `d42aeba` | No visibility into issues | Added ConnectionLogger |
| Nov 12 | `c891132` | Premature "Ready" state | Track pending ops with AtomicInteger |
| Nov 12 | `fb16254` | Cancel button didn't work | Track connecting manager separately |
| Nov 13 | `57efb4c` | Commands failed silently | Update state on `onServicesInvalidated()` |
| Nov 13 | `d4e673e` | Unknown firmware version | Added DIS firmware version reading |
| Nov 18 | `9587b9f` | 5-second disconnect | Request HIGH connection priority |
| Nov 18 | `e582e90` | Init failure allowed workout | Disconnect on init failure |
| Nov 19 | `7a319a4` | Init fails on Android 16 | Added retry logic (3 attempts, 8s timeout) |

### 5-Second Disconnect Fix (`9587b9f`)

**Problem:** Devices disconnecting exactly ~5 seconds after connection.

**Solution:**
```kotlin
// Request HIGH connection priority during initialization
requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH)
```

### Init Retry Configuration (`7a319a4`)

```kotlin
INIT_RESPONSE_TIMEOUT_MS = 8000L  // Increased from 5000ms
INIT_MAX_RETRIES = 2              // Total 3 attempts
INIT_RETRY_DELAY_MS = 1000L       // Exponential backoff: 1s, 2s
```

---

## Critical Safety Issues

### Critical Safety Fixes

| Date | Commit | Issue | Severity | Fix |
|------|--------|-------|----------|-----|
| Oct 29 | `dd7dc75` | 50% resistance | HIGH | Fixed weight at offset 0x58 |
| Nov 5 | `1c5a18a` | Early tension release on final rep | **CRITICAL** | stopAtTop=true default |
| Nov 5 | `3ff2af1` | Release when starting final rep | **CRITICAL** | Send reps+1 to machine |
| Nov 12 | `dc6f5c0` | Auto-stop during eccentric | MEDIUM | Require handles released |

### Early Tension Release Fix (`1c5a18a`)

**Problem:** Machine's counter increments at START of concentric, releasing tension mid-rep.

**Original buggy behavior:**
```kotlin
// Checked (workingReps == workingTarget - 1)
// Released when you START the final rep
```

**Fix:** Changed comparison and defaulted `stopAtTop` to `true`:
```kotlin
// Now checks (workingReps >= workingTarget)
// Releases AFTER completing all target reps

data class WorkoutParameters(
    val stopAtTop: Boolean = true,  // SAFETY: Maintain tension through full final rep
)
```

### stopAtTop Behavior
- **true (default):** Stop at top (contracted) position after final rep - safer
- **false:** Stop at bottom after final rep - old behavior preserved as option

---

## Polling Intervals

| Characteristic | Interval | Purpose | Started When |
|----------------|----------|---------|--------------|
| Monitor | 100ms | Position/force data | Workout starts |
| Property/Diagnostic | 500ms | Keep-alive | After initialization |
| Heuristic | 1000ms | Phase statistics | Workout starts |

---

## Configuration Constants Evolution

| Constant | Initial | Current | Notes |
|----------|---------|---------|-------|
| `SCAN_TIMEOUT_MS` | 10,000 | 30,000 | Better device discovery |
| `CONNECTION_TIMEOUT_MS` | 15,000 | 15,000 | Unchanged |
| `INIT_RESPONSE_TIMEOUT_MS` | 5,000 | 8,000 | Android 16 compatibility |
| `INIT_MAX_RETRIES` | 0 | 2 | Added for stability |
| `BLE_QUEUE_DRAIN_DELAY_MS` | - | 250 | Race condition prevention |
| `HANDLE_GRABBED_THRESHOLD` | 100 (delta) | 8.0 (absolute) | Changed detection method |
| `HANDLE_REST_THRESHOLD` | - | 2.5 | Added for hysteresis |
| `VELOCITY_THRESHOLD` | - | 100.0 | Added for grab confirmation |
| `AUTO_START_TIMER` | 5s → 1.2s | 3s | User preference |

---

## Debugging Recommendations

1. **Commands fail silently:** Check if characteristics are null after `onServicesInvalidated()`
2. **Wrong resistance:** Verify weight calculation at offset 0x58 (total, not per-cable)
3. **Reps count wrong:** Check if counting on topCounter (correct) vs completeCounter (wrong)
4. **Final rep releases early:** Verify reps+1 compensation is being sent
5. **Auto-stop during exercise:** Check handle state AND position conditions
6. **5-second disconnect:** Verify HIGH connection priority and init success
7. **Single-handle exercises fail:** Verify velocityB being calculated
8. **Echo mode wrong level:** Check levelValue vs array index mapping
9. **Danger zone lights stay on:** Verify monitor polling restarts after set completion

---

*This timeline was compiled from analysis of 314 commits. For specific commit details, use:*
```bash
git show <commit-hash> -p -- "*.kt"
```
