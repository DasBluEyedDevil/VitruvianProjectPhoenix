# Non-UI Differences: Beta 5.1 vs Current Build

This document outlines all non-UI changes between the v0.5.1-beta release and the current build, including data layer, domain logic, BLE communication, and utility changes.

---

## Table of Contents
1. [New Files](#new-files)
2. [Database Changes](#database-changes)
3. [BLE Communication Changes](#ble-communication-changes)
4. [Domain Model Changes](#domain-model-changes)
5. [Repository Changes](#repository-changes)
6. [ViewModel Changes](#viewmodel-changes)
7. [Code Organization/Refactoring](#code-organizationrefactoring)
8. [Bug Fixes and Improvements](#bug-fixes-and-improvements)

---

## 1. New Files

### Data Layer - BLE

#### BleExceptions.kt (NEW)
**Location:** `data/ble/BleExceptions.kt`

New typed exception classes for granular BLE error handling:

| Exception Class | Purpose |
|-----------------|---------|
| `BluetoothDisabledException` | Bluetooth is disabled on device |
| `BluetoothException` | General BLE errors |
| `ConnectionLostException` | Connection unexpectedly lost |
| `ConnectionRejectedException` | Connection attempt rejected |
| `GattRequestRejectedException` | GATT operation rejected |
| `GattStatusException` | GATT operation failed with status code |
| `NotReadyException` | Device not ready for operation |
| `ScanFailedException` | BLE scanning failed |

### Data Layer - Database

#### dao/DiagnosticsDao.kt (NEW)
**Location:** `data/local/dao/DiagnosticsDao.kt`

Room DAO for diagnostics history:
- `insert()` - Insert diagnostics record
- `getRecent(limit)` - Get recent records (default 50)
- `getFaultsOnly()` - Get records with faults
- `deleteOlderThan(cutoffTime)` - Cleanup old records
- `getFaultCount()` - Count total faults

#### dao/PhaseStatisticsDao.kt (NEW)
**Location:** `data/local/dao/PhaseStatisticsDao.kt`

Room DAO for phase statistics:
- `insert()` - Insert phase statistics
- `getBySessionId()` - Get stats for specific session
- `getBySessionIds()` - Get stats for multiple sessions
- `deleteBySessionId()` - Delete stats by session
- `getAll()` - Flow of all statistics

#### entity/DiagnosticsEntity.kt (NEW)
**Location:** `data/local/entity/DiagnosticsEntity.kt`

Entity for storing diagnostic data:
```kotlin
@Entity(tableName = "diagnostics_history")
data class DiagnosticsEntity(
    val id: Long,
    val runtimeSeconds: Int,
    val faultMask: Long,       // Compressed faults bitmask
    val temp1-8: Byte,         // Temperature sensors
    val containsFaults: Boolean,
    val timestamp: Long
)
```

#### entity/PhaseStatisticsEntity.kt (NEW)
**Location:** `data/local/entity/PhaseStatisticsEntity.kt`

Entity for concentric/eccentric phase metrics:
```kotlin
@Entity(tableName = "phase_statistics")
data class PhaseStatisticsEntity(
    // Concentric metrics
    val concentricKgAvg: Float,
    val concentricKgMax: Float,
    val concentricVelAvg: Float,
    val concentricVelMax: Float,
    val concentricWattAvg: Float,
    val concentricWattMax: Float,
    // Eccentric metrics (same fields)
    ...
)
```

#### PhaseStatisticsEntity.kt (data/local/)
Duplicate/alternate location for the entity (possibly legacy support)

### Domain Model - New Files

#### DiagnosticDetails.kt (NEW)
Domain model for diagnostic data from device:
- `seconds: Int`
- `faults: List<Short>`
- `temps: List<Byte>`
- `containsFaults: Boolean`

#### HeuristicPhaseStatistics.kt (NEW)
Per-phase statistics model:
- `kgAvg`, `kgMax` - Force metrics
- `velAvg`, `velMax` - Velocity metrics
- `wattAvg`, `wattMax` - Power metrics

#### HeuristicStatistics.kt (NEW)
Combined statistics for both phases:
```kotlin
data class HeuristicStatistics(
    val concentric: HeuristicPhaseStatistics,
    val eccentric: HeuristicPhaseStatistics,
    val timestamp: Long
)
```

#### SafetyEventSummary.kt (NEW)
Safety event tracking:
```kotlin
data class SafetyEventSummary(
    val safetyFlags: Int = 0,
    val deloadWarnings: Int = 0,
    val romViolations: Int = 0,
    val spotterActivations: Int = 0
) {
    val hasSafetyEvents: Boolean
}
```

#### SampleStatus.kt (NEW)
Enum for device status flags (bit-masked):
```kotlin
enum class SampleStatus(val mask: Int) {
    REP_TOP_READY(0x0001),     // Reached top position
    REP_BOTTOM_READY(0x0002),  // Reached bottom position
    ROM_OUTSIDE_HIGH(0x0004),  // Exceeded max ROM
    ROM_OUTSIDE_LOW(0x0008),   // Below min ROM
    ROM_UNLOAD_ACTIVE(0x0010), // Unloading phase active
    SPOTTER_ACTIVE(0x0020),    // Spotter engaged
    DELOAD_WARN(0x0040),       // Force cap approaching
    DELOAD_OCCURRED(0x8000);   // Force capped/unloaded
}
```

### Utility - New Files

#### BleConstants.kt (NEW - extracted from Constants.kt)
**Location:** `util/BleConstants.kt`

All BLE-related constants moved to dedicated file:
- Service UUIDs (GATT, NUS)
- Characteristic UUIDs (RX, Monitor, Property/Diagnostic, RepNotify, Heuristic, Version)
- `NOTIFY_CHAR_UUIDS` list
- `WORKOUT_CMD_CHAR_UUIDS` list
- Device name prefix ("Vee")
- Timeouts (Connection: 15s, GATT: 5s, Scan: 30s)
- `BLE_QUEUE_DRAIN_DELAY_MS` (250ms)

#### ColorScheme.kt (NEW - extracted from ProtocolBuilder.kt)
Color scheme data class and predefined schemes:
- `ColorScheme(name, brightness, colors)`
- Predefined: BLUE, GREEN, TEAL, YELLOW, PINK, RED, PURPLE

#### RGBColor.kt (NEW - extracted from ProtocolBuilder.kt)
RGB color data class with validation (0-255 range)

#### EchoParams.kt (NEW - extracted from ProtocolBuilder.kt)
Echo mode parameters data class:
- `eccentricPct`, `concentricPct`
- `smoothing`, `floor`, `negLimit`, `gain`, `cap`

### Repository - New Files

#### WorkoutRepositoryMappers.kt (NEW)
**Location:** `data/repository/WorkoutRepositoryMappers.kt`

Extracted mapper functions from WorkoutRepository:
- `WorkoutSessionEntity.toWorkoutSession()`
- `WorkoutSession.toEntity()`
- `WorkoutMetricEntity.toWorkoutMetric()`
- `WorkoutMetric.toEntity()`
- `Routine.toEntity()`
- `RoutineEntity.toRoutine()`
- `RoutineExercise.toEntity()`
- `RoutineExerciseEntity.toRoutineExercise()`
- `HeuristicStatistics.toPhaseStatisticsEntity()`
- Helper functions: `toJsonArray()`, `parseIntListFromJson()`

---

## 2. Database Changes

### WorkoutDatabase.kt
**Version:** 22 → 23

**Changes:**
- Added `PhaseStatisticsEntity` to entities list
- Added `DiagnosticsEntity` to entities list
- New DAOs:
  - `phaseStatisticsDao(): PhaseStatisticsDao`
  - `diagnosticsDao(): DiagnosticsDao`

### WorkoutEntities.kt (WorkoutSessionEntity)
**New fields added:**
```kotlin
// Safety Tracking (added in v23)
val safetyFlags: Int = 0,
val deloadWarningCount: Int = 0,
val romViolationCount: Int = 0,
val spotterActivations: Int = 0
```

### Migration Required
Database version bump from 22 to 23 requires migration for:
- `phase_statistics` table
- `diagnostics_history` table
- Safety tracking columns in `workout_sessions`

---

## 3. BLE Communication Changes

### VitruvianBleManager.kt

#### New Characteristics
```kotlin
private var heuristicCharacteristic: BluetoothGattCharacteristic? = null
private var versionCharacteristic: BluetoothGattCharacteristic? = null
```

#### New State Flows
```kotlin
private val _diagnosticData = MutableStateFlow<DiagnosticDetails?>(null)
val diagnosticData: StateFlow<DiagnosticDetails?>

private val _heuristicData = MutableStateFlow<HeuristicStatistics?>(null)
val heuristicData: StateFlow<HeuristicStatistics?>
```

#### New Features

**Strict Validation Mode:**
```kotlin
fun setStrictValidationEnabled(enabled: Boolean)
// When enabled, large position jumps (>200) are filtered as invalid
```

**Force-Based Handle Detection:**
```kotlin
@Volatile private var forceAboveGrabThresholdSince: Long? = null
@Volatile private var forceBelowReleaseThresholdSince: Long? = null
```

**Heuristic Polling:**
```kotlin
private var heuristicPollingJob: Job? = null
```

#### Changes to startMonitorPolling()
```kotlin
// Old
fun startMonitorPolling()

// New - Added forAutoStart parameter
fun startMonitorPolling(forAutoStart: Boolean = false)
// If true: HandleState.WaitingForRest (for Just Lift auto-start)
// If false: HandleState.Grabbed (for active workout monitoring)
```

#### Polling Interval Change
- Monitor polling: 100ms → 16ms (~60Hz) for smoother data

#### Connection Priority
- Changed to use `ConnectionPriorityRequest.CONNECTION_PRIORITY_HIGH` (proper constant)

#### Property → Diagnostic Rename
- `propertyCharacteristic` now referred to as "Diagnostic" in comments
- Uses `BleConstants.DIAGNOSTIC_CHAR_UUID`

---

## 4. Domain Model Changes

### RepCounterFromMachine.kt

**Major Rewrite - Official App Method:**

The rep counting logic was completely rewritten to match the official Vitruvian app:

**Old Method:**
- Counted reps when `topCounter` incremented (at peak contraction)
- Inferred rep counts from counter deltas

**New Method:**
```kotlin
fun process(
    topCounter: Int,
    completeCounter: Int,
    deviceWarmupReps: Int = 0,    // NEW: repsRomCount from device
    deviceWorkingReps: Int = 0,   // NEW: repsSetCount from device
    posA: Int = 0,
    posB: Int = 0
)
```

- Uses device-provided rep counts directly from the 24-byte Reps packet
- `repsRomCount` (offset 16-17): Warmup reps with proper ROM
- `repsSetCount` (offset 20-21): Working set rep count - displayed to user
- `topCounter/completeCounter` used only for haptic feedback events

**Key Change:** Firmware handles rep counting; app just displays device values.

---

## 5. Repository Changes

### WorkoutRepository.kt

#### Constructor Changes
```kotlin
// Old
@Inject constructor(
    private val workoutDao: WorkoutDao,
    private val personalRecordDao: PersonalRecordDao
)

// New
@Inject constructor(
    private val workoutDao: WorkoutDao,
    private val personalRecordDao: PersonalRecordDao,
    private val phaseStatisticsDao: PhaseStatisticsDao,  // NEW
    private val diagnosticsDao: DiagnosticsDao           // NEW
)
```

#### New Methods
```kotlin
suspend fun savePhaseStatistics(sessionId: String, stats: HeuristicStatistics): Result<Unit>
fun getAllPhaseStatistics(): Flow<List<PhaseStatisticsEntity>>
```

#### Mapper Extraction
All mapper extension functions moved to `WorkoutRepositoryMappers.kt` (~170 lines removed)

### AppModule.kt (DI)
Updated to provide:
- `PhaseStatisticsDao`
- `DiagnosticsDao`

---

## 6. ViewModel Changes

### MainViewModel.kt

#### New State Flows for TopBar
```kotlin
private val _topBarTitle = MutableStateFlow("Vitruvian Project Phoenix")
val topBarTitle: StateFlow<String>

private val _topBarActions = MutableStateFlow<List<TopBarAction>>(emptyList())
val topBarActions: StateFlow<List<TopBarAction>>

private val _topBarBackAction = MutableStateFlow<(() -> Unit)?>(null)
val topBarBackAction: StateFlow<(() -> Unit)?>
```

#### New Methods
```kotlin
fun updateTopBarTitle(title: String)
fun setTopBarActions(actions: List<TopBarAction>)
fun clearTopBarActions()
fun setTopBarBackAction(action: () -> Unit)
fun clearTopBarBackAction()
```

#### Handle State Change
```kotlin
// Old initial state
private var currentHandleState = HandleState.Released

// New initial state
private var currentHandleState = HandleState.WaitingForRest
```

#### Rep Notification Handling Update
```kotlin
// Now passes device-provided rep counts
repCounter.process(
    topCounter = notification.topCounter,
    completeCounter = notification.completeCounter,
    deviceWarmupReps = notification.repsRomCount,   // NEW
    deviceWorkingReps = notification.repsSetCount,  // NEW
    posA = currentPositions?.positionA ?: 0,
    posB = currentPositions?.positionB ?: 0
)
```

#### Auto-Stop Logic Simplified
**Old:** Complex danger zone calculation with cable-specific release detection
**New:** Simple handle rest detection
```kotlin
val HANDLE_REST_THRESHOLD = 2.5
val handlesAtRest = posA < HANDLE_REST_THRESHOLD && posB < HANDLE_REST_THRESHOLD
if (handlesAtRest) { /* Start auto-stop timer */ }
```

#### Bodyweight Exercise Detection Improved
```kotlin
// Old - Required duration
private fun isBodyweightExercise(exercise: RoutineExercise?): Boolean {
    return exercise?.let {
        it.exercise.equipment.isEmpty() && it.duration != null
    } ?: false
}

// New - Equipment-based detection
private fun isBodyweightExercise(exercise: RoutineExercise?): Boolean {
    return exercise?.let {
        val equipment = it.exercise.equipment
        equipment.isEmpty() || equipment.equals("bodyweight", ignoreCase = true)
    } ?: false
}
```

#### Warmup Reps for Bodyweight
Bodyweight exercises now automatically get `warmupReps = 0`

---

## 7. Code Organization/Refactoring

### Constants.kt
**Major Cleanup:**
- BLE constants extracted to `BleConstants.kt` (~52 lines removed)
- Only workout-related constants remain

### ProtocolBuilder.kt
**Cleanup:**
- `EchoParams` data class → `EchoParams.kt`
- `RGBColor` data class → `RGBColor.kt`
- `ColorScheme` and `ColorSchemes` → `ColorScheme.kt`
- ~115 lines removed

---

## 8. Bug Fixes and Improvements

### Handle Detection Fix
- Changed from `HandleState.Released` to `HandleState.WaitingForRest` initial state
- Prevents immediate auto-start if cables already have tension
- Must see handles at rest before arming grab detection

### AMRAP Auto-Stop
```kotlin
// Now includes AMRAP in immediate UI reflection
if (_workoutParameters.value.isJustLift || _workoutParameters.value.isAMRAP) {
    _autoStopState.value = _autoStopState.value.copy(progress = 1f, secondsRemaining = 0, isActive = true)
}
```

### Monitor Polling for AMRAP
Added comment clarifying the "red light fix":
```kotlin
// This is CRITICAL for the "red light fix" to prevent machine hanging in fault state
bleRepository.restartMonitorPolling()
```

### BLE Connection Stability
- Connection priority request uses proper constant
- Improved characteristic discovery across all services
- Better logging for heuristic and version characteristics

---

## Summary of Architecture Changes

1. **Safety Tracking:** Full pipeline from BLE to UI for safety events (deload warnings, ROM violations, spotter activations)

2. **Phase Statistics:** New data model for concentric/eccentric metrics (kg, velocity, watts)

3. **Diagnostics:** Device diagnostic data capture and storage (faults, temperatures, runtime)

4. **Rep Counting:** Complete rewrite to use device-provided counts (official app method)

5. **Code Organization:** Better separation of concerns with extracted mappers, constants, and data classes

6. **Handle Detection:** Improved state machine with WaitingForRest initial state

7. **Database:** Version 23 with new tables and safety tracking columns
