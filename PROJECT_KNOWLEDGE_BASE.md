# Vitruvian Redux - Comprehensive Project Knowledge Base
**AI Working Document - Complete End-to-End Understanding**

**Last Updated:** 2025-11-03  
**Project Status:** Beta Release - Fully Functional

---

## Executive Summary

### What This Project Is
Vitruvian Redux is a **native Android app that rescues $2,000+ smart fitness machines from becoming e-waste** after the manufacturer (Vitruvian) went bankrupt. The app provides local Bluetooth control, completely replacing the defunct cloud-based system.

**Key Achievement:** Successfully reverse-engineered proprietary BLE protocol and built production-ready native Android replacement for web app.

### Technology Stack
- **Language:** Kotlin
- **UI:** Jetpack Compose + Material 3
- **Architecture:** MVVM + Clean Architecture + Repository Pattern
- **DI:** Hilt/Dagger
- **BLE:** Nordic BLE Library v2.7.1 (industry standard)
- **Database:** Room (workout history, routines)
- **Async:** Kotlin Coroutines + StateFlow/SharedFlow
- **Min SDK:** API 26 (Android 8.0)

---

## Project Architecture Overview

### Layer Structure (Clean Architecture)

```
┌─────────────────────────────────────────────────────────────┐
│                    PRESENTATION LAYER                        │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐     │
│  │  Compose UI  │  │  ViewModels  │  │  Navigation  │     │
│  └──────────────┘  └──────────────┘  └──────────────┘     │
└─────────────────────────────────────────────────────────────┘
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                      DOMAIN LAYER                            │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐     │
│  │   Models     │  │  Use Cases   │  │  Interfaces  │     │
│  └──────────────┘  └──────────────┘  └──────────────┘     │
└─────────────────────────────────────────────────────────────┘
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                       DATA LAYER                             │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐     │
│  │ Repositories │  │  BLE Manager │  │  Room DB     │     │
│  └──────────────┘  └──────────────┘  └──────────────┘     │
└─────────────────────────────────────────────────────────────┘
```

### Directory Structure
```
app/src/main/java/com/example/vitruvianredux/
├── VitruvianApp.kt                      # Application class (Hilt)
├── MainActivity.kt                      # Single activity + Compose
│
├── data/                                # Data layer
│   ├── ble/
│   │   └── VitruvianBleManager.kt      # BLE communication (Nordic lib)
│   ├── local/
│   │   ├── WorkoutDatabase.kt          # Room database
│   │   ├── ExerciseDao.kt              # Exercise library DAO
│   │   └── WorkoutDao.kt               # Workout history DAO
│   └── repository/
│       ├── BleRepositoryImpl.kt        # BLE operations
│       ├── ExerciseRepositoryImpl.kt   # Exercise library
│       └── WorkoutRepositoryImpl.kt    # Workout history
│
├── domain/                              # Domain layer
│   └── model/
│       ├── Models.kt                   # Core domain models
│       ├── Exercise.kt                 # Exercise definitions
│       ├── Routine.kt                  # Workout routines
│       └── WorkoutSession.kt           # Workout tracking
│
├── presentation/                        # Presentation layer
│   ├── screen/                         # Compose screens
│   │   ├── EnhancedMainScreen.kt      # Main navigation tabs
│   │   ├── HomeScreen.kt              # Home/Quick workout
│   │   ├── ActiveWorkoutScreen.kt     # Live workout view
│   │   ├── JustLiftScreen.kt          # Just Lift mode
│   │   ├── SingleExerciseScreen.kt    # Single exercise flow
│   │   ├── DailyRoutinesScreen.kt     # Daily routines
│   │   ├── WeeklyProgramsScreen.kt    # Weekly programs
│   │   ├── ProgramBuilderScreen.kt    # Program creation
│   │   ├── RoutineBuilderDialog.kt    # Routine editor
│   │   ├── ExerciseEditDialog.kt      # Exercise configuration
│   │   ├── AnalyticsScreen.kt         # Stats dashboard
│   │   └── HistoryAndSettingsTabs.kt  # History + Settings
│   ├── components/                     # Reusable UI components
│   │   ├── CompactNumberPicker.kt     # Native Android picker
│   │   ├── ExercisePickerDialog.kt    # Exercise selection
│   │   ├── VideoPlayer.kt             # Exercise videos
│   │   ├── ConnectionStatusBanner.kt   # BLE status UI
│   │   └── StatsCard.kt               # Analytics cards
│   ├── viewmodel/
│   │   └── MainViewModel.kt           # Centralized state management
│   └── navigation/
│       └── NavigationRoutes.kt        # Navigation graph
│
├── service/                            # Background services
│   └── WorkoutForegroundService.kt    # Keeps BLE alive
│
├── util/                               # Utilities
│   ├── BleConstants.kt                # UUIDs, constants
│   ├── ProtocolBuilder.kt             # Binary protocol frames
│   └── RepDetectionEngine.kt          # Rep counting logic
│
└── di/                                 # Dependency injection
    └── AppModule.kt                   # Hilt modules
```

---

## BLE Protocol Deep Dive

### Critical Understanding: Byte-Perfect Protocol
**THIS IS THE MOST IMPORTANT PART OF THE PROJECT.**

The BLE protocol was reverse-engineered from packet captures. **Every byte matters**. Any deviation breaks communication with the machine.

### BLE Service Structure
```
Nordic UART Service (NUS)
├── Service UUID: 6e400001-b5a3-f393-e0a9-e50e24dcca9e
├── RX Characteristic (app writes to device)
│   └── UUID: 6e400002-b5a3-f393-e0a9-e50e24dcca9e
└── TX Characteristic (device writes to app)
    └── UUID: 6e400003-b5a3-f393-e0a9-e50e24dcca9e

GATT Service (Device Info)
├── Service UUID: 00001801-0000-1000-8000-00805f9b34fb
└── (Property/monitor characteristics)
```

### Protocol Commands (Binary Frames)

#### 1. Init Command (4 bytes)
```kotlin
byteArrayOf(0x0A, 0x00, 0x00, 0x00)
```
- Sent first to wake up device
- Always 4 bytes
- Command code: 0x0A

#### 2. Init Preset (34 bytes)
```kotlin
// Contains coefficient table for machine calibration
// Includes 0.4 as float32 at offset 12
// Pattern-based frame with specific hex values
```
- Sent after init command
- Configures machine baseline parameters

#### 3. Program Params (96 bytes)
**Most complex and critical frame**
```
Byte Offset | Field           | Type    | Notes
------------|-----------------|---------|---------------------------
0x00-0x03   | Command header  | 4 bytes | 0x04 0x00 0x00 0x00
0x04        | Reps            | 1 byte  | reps+3 (or 0xFF for Just Lift)
0x05-0x07   | Constants       | 3 bytes | 0x03 0x03 0x00
0x08        | Float constant  | float32 | 5.0 (little endian)
0x0C        | Float constant  | float32 | 5.0
0x1C        | Float constant  | float32 | 5.0
0x30-0x4F   | Mode profile    | 32 bytes| Defines workout behavior
0x54        | Effective kg    | float32 | Total weight (both cables)
0x58        | Per-cable kg    | float32 | Weight per individual cable
0x5C        | Progression kg  | float32 | Weight change per rep
```

**Mode Profiles (32-byte blocks):**
- **Old School:** Constant resistance
- **Pump:** Decreasing resistance
- **TUT:** Time under tension control
- **TUT Beast:** Harder TUT variant
- **Eccentric Only:** Negative-only movement
- **Echo:** Adaptive resistance based on fatigue

#### 4. Echo Control (40 bytes)
```
Byte Offset | Field              | Type    | Notes
------------|--------------------|---------|---------------------------
0x00-0x03   | Command header     | 4 bytes | 0x05 0x00 0x00 0x00
0x04        | Level              | 1 byte  | 0=easy, 1=moderate, 2=hard
0x05        | Warmup reps        | 1 byte  | Number of warmup reps
0x06        | Target reps        | 1 byte  | Total reps to perform
0x07        | Just Lift flag     | 1 byte  | 0x01=true, 0x00=false
0x08        | Eccentric %        | 1 byte  | 0, 50, 75, 100, 125, 150
```

#### 5. Color Scheme (34 bytes)
```kotlin
// Sets LED colors on machine
// RGB values for various UI elements
// User-customizable via settings
```

### Critical Protocol Rules

1. **Command Queue:** Only one command in flight at a time
   - Use `Channel` to serialize writes
   - Wait for each command to complete before next

2. **Timing:**
   - 100ms delay after each command
   - Init sequence: initCommand → delay → initPreset
   - Startup: init sequence → delay → programParams OR echoControl

3. **Float Encoding:**
   - Little endian byte order
   - IEEE 754 single precision (4 bytes)
   ```kotlin
   ByteBuffer.wrap(array).order(ByteOrder.LITTLE_ENDIAN).putFloat(offset, value)
   ```

4. **Characteristics to Enable:**
   ```kotlin
   // These 7 characteristics MUST have notifications enabled:
   - NUS RX (commands in)
   - NUS TX (responses out)
   - Monitor (real-time metrics @ 100Hz)
   - Property (device state)
   - Rep Notify (rep count events)
   - Workout Command 1
   - Workout Command 2
   ```

---

## Workout Flow End-to-End

### 1. Device Discovery & Connection
```
User Action → BLE Scan → Device List → User Selects → Connection Flow
```

**Implementation:**
```kotlin
// MainViewModel.kt
fun scanForDevices() {
    viewModelScope.launch {
        bleRepository.scanForDevices()
            .collect { devices ->
                _availableDevices.value = devices
                    .filter { it.name?.startsWith("Vee") == true }
            }
    }
}

fun connectToDevice(address: String) {
    viewModelScope.launch {
        _isAutoConnecting.value = true
        bleRepository.connect(address)
            .onSuccess {
                _connectionState.value = ConnectionState.Connected
                initializeDevice() // Send init sequence
            }
            .onFailure { error ->
                _connectionError.value = error.message
            }
        _isAutoConnecting.value = false
    }
}
```

### 2. Workout Configuration
```
Screen             → User Configures       → Data Model
─────────────────────────────────────────────────────────
JustLiftScreen     → Weight, reps         → WorkoutParameters
SingleExerciseScreen → Exercise + config  → RoutineExercise
DailyRoutinesScreen → Multi-exercise      → DailyRoutine
WeeklyPrograms     → Full program         → WeeklyProgram
```

**Key Components:**

**ExerciseEditBottomSheet:** Universal configuration UI
- Sets configuration (reps, weight per set)
- Workout mode selection (Old School, Pump, TUT, Echo)
- Echo-specific: eccentric load %, difficulty level
- Weight progression/regression per rep
- Rest time between sets
- Duration-based vs rep-based sets

**State Management (Critical Fix Applied):**
```kotlin
// FIXED: Use exercise.id as key to prevent state reset on recomposition
var sets by remember(exercise.id) { 
    mutableStateOf(/* initial sets */) 
}
var selectedMode by remember(exercise.id) { 
    mutableStateOf(/* mode */)
}

// FIXED: Use stable IDs for list items to prevent picker reset
sets.forEachIndexed { index, setConfig ->
    key(setConfig.id) {  // Stable UUID per set
        SetRow(setConfig = setConfig.copy(setNumber = index + 1))
    }
}
```

**Recent Bug Fix (2025-11-03):**
- **Problem:** Scrolling picker controls was resetting all other pickers and restoring deleted sets
- **Root Cause:** Missing Compose keys caused UI confusion during recompositions
- **Solution:** 
  1. Added unique IDs to SetConfiguration
  2. Used `remember(exercise.id)` for all state
  3. Wrapped SetRow in `key(setConfig.id)` blocks
  4. Stabilized function references in parent composables
- **Impact:** None on BLE communication - purely UI-layer fix

### 3. Workout Start Sequence
```
User Taps "Start" → Convert to WorkoutParameters → Send Protocol Commands
```

**Conversion Chain:**
```kotlin
// UI State → Domain Model
SetConfiguration(id, setNumber, reps, weight, duration)
  ↓
RoutineExercise(exercise, setReps, weightPerCableKg, workoutType, etc.)
  ↓
WorkoutParameters(workoutType, reps, weightPerCableKg, progressionKg, etc.)
  ↓
Binary Protocol Frame (96 bytes for Program, 40 bytes for Echo)
```

**Start Workout Implementation:**
```kotlin
// BleRepositoryImpl.kt
override suspend fun startWorkout(params: WorkoutParameters): Result<Unit> {
    return when (params.workoutType) {
        is WorkoutType.Echo -> {
            // Echo mode: Send ONLY echo control frame
            val echoFrame = ProtocolBuilder.buildEchoControl(
                level = params.workoutType.level,
                warmupReps = params.warmupReps,
                targetReps = params.reps,
                isJustLift = params.isJustLift,
                eccentricPct = params.workoutType.eccentricLoad.percentage
            )
            bleManager.sendCommand(echoFrame)
        }
        is WorkoutType.Program -> {
            // Program mode: Send ONLY program params
            val programFrame = ProtocolBuilder.buildProgramParams(params)
            bleManager.sendCommand(programFrame)
        }
    }
}
```

### 4. Real-Time Monitoring (During Workout)
```
Device sends data @ 100Hz → BLE Notifications → Processing → UI Update
```

**Monitor Data Flow:**
```kotlin
// VitruvianBleManager.kt - Runs at 100Hz!
private fun startMonitorPolling() {
    monitorPollingJob = pollingScope.launch {
        while (isActive) {
            monitorCharacteristic?.let { char ->
                readCharacteristic(char)
                    .with { device, data ->
                        val metric = parseMonitorData(data)
                        _monitorData.tryEmit(metric) // SharedFlow with buffer
                    }
                    .enqueue()
            }
            delay(10) // 100Hz = 10ms interval
        }
    }
}

private fun parseMonitorData(data: Data): WorkoutMetric {
    val bytes = data.value ?: return WorkoutMetric(0, 0f, 0f, 0, 0)
    val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
    
    return WorkoutMetric(
        timestamp = System.currentTimeMillis(),
        loadA = buffer.getFloat(0),      // Load on cable A (kg)
        loadB = buffer.getFloat(4),      // Load on cable B (kg)
        positionA = buffer.getInt(8),    // Position A (encoder ticks)
        positionB = buffer.getInt(12),   // Position B (encoder ticks)
        ticks = buffer.getInt(16)        // Total ticks counter
    )
}
```

**Rep Detection Engine:**
```kotlin
// RepDetectionEngine.kt
class RepDetectionEngine {
    fun processMetric(metric: WorkoutMetric): RepEvent? {
        val totalLoad = metric.loadA + metric.loadB
        
        when (state) {
            RepState.IDLE -> {
                if (totalLoad > CONCENTRIC_THRESHOLD) {
                    state = RepState.CONCENTRIC
                    currentRep++
                }
            }
            RepState.CONCENTRIC -> {
                if (totalLoad < ECCENTRIC_THRESHOLD) {
                    state = RepState.ECCENTRIC
                    return RepEvent.ConcentricComplete(currentRep)
                }
            }
            RepState.ECCENTRIC -> {
                if (totalLoad < IDLE_THRESHOLD) {
                    state = RepState.IDLE
                    return RepEvent.RepComplete(currentRep)
                }
            }
        }
        return null
    }
}
```

### 5. Workout Completion & History
```
User Stops → Save to Room DB → Display in History → Analytics Dashboard
```

**Data Persistence:**
```kotlin
// WorkoutDao.kt
@Entity(tableName = "workouts")
data class WorkoutEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val exerciseId: String,
    val exerciseName: String,
    val timestamp: Long,
    val duration: Long,
    val totalReps: Int,
    val avgLoadKg: Float,
    val maxLoadKg: Float,
    val workoutMode: String,
    val metricsJson: String  // Serialized WorkoutMetric list
)

@Dao
interface WorkoutDao {
    @Insert suspend fun insertWorkout(workout: WorkoutEntity): Long
    @Query("SELECT * FROM workouts ORDER BY timestamp DESC")
    fun getAllWorkouts(): Flow<List<WorkoutEntity>>
    @Query("SELECT * FROM workouts WHERE exerciseId = :exerciseId ORDER BY timestamp DESC")
    fun getWorkoutsByExercise(exerciseId: String): Flow<List<WorkoutEntity>>
}
```

---

## Key UI Screens & Their Responsibilities

### 1. EnhancedMainScreen.kt
**Purpose:** Main navigation hub with bottom tabs

**Tabs:**
- Home: Quick workout access
- Routines: Daily routines list
- Programs: Weekly programs
- Analytics: Stats dashboard
- History: Workout history + Settings

**State:**
```kotlin
val selectedTab by viewModel.selectedMainTab.collectAsState()
val connectionState by viewModel.connectionState.collectAsState()
```

### 2. HomeScreen.kt
**Purpose:** Landing page with quick workout options

**Features:**
- Device connection status banner
- Quick workout buttons:
  - "Just Lift" → JustLiftScreen
  - "Single Exercise" → SingleExerciseScreen
  - "Start Routine" → Routine selection
  - "Start Program" → Program selection
- Recent workouts preview
- Quick stats cards

### 3. JustLiftScreen.kt
**Purpose:** Simplified workout mode - set weight and go

**Workflow:**
1. Select workout mode (Old School, Pump, TUT, Echo)
2. Configure weight per cable
3. Start immediately
4. Machine adapts resistance automatically

**Implementation:**
```kotlin
@Composable
fun JustLiftScreen(
    navController: NavController,
    viewModel: MainViewModel
) {
    val workoutParameters by viewModel.workoutParameters.collectAsState()
    val isWorkoutActive by viewModel.isWorkoutActive.collectAsState()
    
    // Configuration UI
    WorkoutModeSelector(
        selectedMode = workoutParameters.workoutType,
        onModeChange = { mode ->
            viewModel.updateWorkoutParameters(
                workoutParameters.copy(workoutType = mode)
            )
        }
    )
    
    WeightSelector(
        weightKg = workoutParameters.weightPerCableKg,
        onWeightChange = { weight ->
            viewModel.updateWorkoutParameters(
                workoutParameters.copy(weightPerCableKg = weight)
            )
        }
    )
    
    Button(
        onClick = {
            viewModel.startWorkout()
            navController.navigate("active_workout")
        }
    ) {
        Text("Start Workout")
    }
}
```

### 4. SingleExerciseScreen.kt
**Purpose:** Full exercise configuration with set-by-set control

**Workflow:**
1. Show exercise picker dialog (searchable, filterable)
2. User selects exercise from library
3. Show ExerciseEditBottomSheet for configuration
4. User configures sets, reps, weight, mode
5. Start workout with configured parameters

**Recent Changes:**
```kotlin
// FIXED: Stabilized function references to prevent state reset
exerciseToConfig?.let { exercise ->
    val kgToDisplay = remember { viewModel::kgToDisplay }
    val displayToKg = remember { viewModel::displayToKg }
    
    ExerciseEditBottomSheet(
        exercise = exercise,
        kgToDisplay = kgToDisplay,
        displayToKg = displayToKg,
        onSave = { configured ->
            val params = WorkoutParameters(/* convert to params */)
            viewModel.updateWorkoutParameters(params)
            viewModel.startWorkout()
        }
    )
}
```

### 5. ActiveWorkoutScreen.kt
**Purpose:** Live workout monitoring and control

**Real-Time Display:**
- Current rep count (from RepDetectionEngine)
- Live load graphs (cable A, cable B, total)
- Position indicators (cable extension)
- Elapsed time
- Current set / total sets
- Force readouts (kg and lb)

**Controls:**
- Pause/Resume
- Stop workout
- Emergency stop (immediate)
- Next set (for multi-set workouts)

**Implementation:**
```kotlin
val monitorData by viewModel.monitorData.collectAsState()
val repCount by viewModel.currentReps.collectAsState()

// Live force display
Text(
    text = "${(monitorData.loadA + monitorData.loadB).format(1)} kg",
    style = MaterialTheme.typography.displayLarge
)

// Position indicators
LinearProgressIndicator(
    progress = monitorData.positionA / MAX_POSITION.toFloat(),
    modifier = Modifier.fillMaxWidth()
)
```

### 6. ExerciseEditBottomSheet.kt
**Purpose:** Universal exercise configuration UI

**Critical Understanding:**
This is the most complex UI component because it handles:
- Multiple workout modes with different parameters
- Per-set weight configuration
- Dynamic set management (add/delete sets)
- Unit conversion (kg ↔ lb)
- Input validation
- State persistence during recompositions

**State Management Pattern (CRITICAL):**
```kotlin
// SetConfiguration is UI-only - never sent to machine
data class SetConfiguration(
    val id: String = UUID.randomUUID().toString(),  // For Compose key()
    val setNumber: Int,                             // Display number
    val reps: Int = 10,
    val weightPerCable: Float = 15.0f,             // In display units
    val duration: Int = 30
)

// On save, convert to RoutineExercise (domain model)
val updatedExercise = exercise.copy(
    setReps = sets.map { it.reps },                           // Extract reps
    weightPerCableKg = displayToKg(sets.first().weightPerCable), // Convert to kg
    setWeightsPerCableKg = sets.map { displayToKg(it.weightPerCable) }
)
```

**Recent Fix Details:**
- Added `id: String` field to SetConfiguration for stable Compose keys
- All state uses `remember(exercise.id)` to survive recompositions
- Each SetRow wrapped in `key(setConfig.id)` for proper list tracking
- Set numbers renumbered for display: `setConfig.copy(setNumber = index + 1)`

### 7. DailyRoutinesScreen.kt
**Purpose:** Manage daily workout routines

**Features:**
- Create/edit/delete routines
- Multi-exercise routines with set sequences
- Reorder exercises via drag & drop
- Start routine with one tap
- Track routine completion history

### 8. WeeklyProgramsScreen.kt
**Purpose:** Structured weekly training programs

**Features:**
- 7-day program builder
- Assign routines to specific days
- Rest days
- Progressive overload tracking
- Program templates (PPL, Upper/Lower, etc.)

### 9. AnalyticsScreen.kt
**Purpose:** Stats dashboard and progress tracking

**Metrics:**
- Total workouts
- Total volume (kg × reps)
- Personal records per exercise
- Volume trends (daily, weekly, monthly)
- Workout frequency
- Muscle group distribution

**Charts:**
- Volume over time (line chart)
- Exercise frequency (pie chart)
- PR progression (bar chart)

---

## Data Models Deep Dive

### Core Domain Models

#### WorkoutParameters
```kotlin
data class WorkoutParameters(
    val workoutType: WorkoutType,
    val reps: Int,
    val weightPerCableKg: Float = 0f,
    val progressionRegressionKg: Float = 0f,
    val isJustLift: Boolean = false,
    val stopAtTop: Boolean = false,
    val warmupReps: Int = 3,
    val selectedExerciseId: String? = null
)
```
**Purpose:** Intermediate model between UI and protocol  
**Lifecycle:** Created from UI state → Converted to binary protocol → Sent to machine

#### WorkoutType (Sealed Class)
```kotlin
sealed class WorkoutType {
    data class Program(val mode: ProgramMode) : WorkoutType()
    data class Echo(
        val level: EchoLevel,
        val eccentricLoad: EccentricLoad
    ) : WorkoutType()
}

enum class ProgramMode {
    OldSchool,    // Constant resistance
    Pump,         // Decreasing resistance
    TUT,          // Time under tension
    TUTBeast,     // Harder TUT
    EccentricOnly // Negative-only
}

enum class EchoLevel { EASY, MODERATE, HARDER }

enum class EccentricLoad(val percentage: Int) {
    LOAD_0(0),
    LOAD_50(50),
    LOAD_75(75),
    LOAD_100(100),
    LOAD_125(125),
    LOAD_150(150)
}
```

#### WorkoutMetric
```kotlin
data class WorkoutMetric(
    val timestamp: Long,
    val loadA: Float,      // kg on cable A
    val loadB: Float,      // kg on cable B
    val positionA: Int,    // encoder ticks
    val positionB: Int,
    val ticks: Int = 0
)
```
**Usage:** Real-time data from machine @ 100Hz  
**Processing:** Fed into RepDetectionEngine, displayed in UI, stored for charts

#### Exercise
```kotlin
data class Exercise(
    val id: String,
    val name: String,
    val displayName: String = name,
    val muscleGroup: String,
    val equipment: String,
    val defaultCableConfig: CableConfiguration
)
```

#### RoutineExercise
```kotlin
data class RoutineExercise(
    val id: String,
    val exercise: Exercise,
    val cableConfig: CableConfiguration,
    val orderIndex: Int,
    val setReps: List<Int> = listOf(10, 10, 10),
    val weightPerCableKg: Float,
    val setWeightsPerCableKg: List<Float> = emptyList(),
    val workoutType: WorkoutType,
    val eccentricLoad: EccentricLoad,
    val echoLevel: EchoLevel,
    val progressionKg: Float = 0f,
    val restSeconds: Int = 60,
    val notes: String = "",
    val duration: Int? = null  // For duration-based sets
)
```
**Purpose:** Complete exercise configuration for routines/programs  
**Contains:** Everything needed to execute an exercise workout

---

## State Management Architecture

### MainViewModel - Centralized State
**Single source of truth for entire app**

```kotlin
class MainViewModel @Inject constructor(
    private val bleRepository: BleRepository,
    private val workoutRepository: WorkoutRepository,
    private val exerciseRepository: ExerciseRepository,
    private val repDetectionEngine: RepDetectionEngine
) : ViewModel() {
    
    // Connection state
    private val _connectionState = MutableStateFlow<ConnectionState>(Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState
    
    // Workout state
    private val _isWorkoutActive = MutableStateFlow(false)
    val isWorkoutActive: StateFlow<Boolean> = _isWorkoutActive
    
    private val _workoutParameters = MutableStateFlow(WorkoutParameters(/* defaults */))
    val workoutParameters: StateFlow<WorkoutParameters> = _workoutParameters
    
    // Real-time monitoring (100Hz from device)
    val monitorData: SharedFlow<WorkoutMetric> = bleRepository.monitorData
    
    // Rep tracking
    private val _currentReps = MutableStateFlow(0)
    val currentReps: StateFlow<Int> = _currentReps
    
    private val _repEvents = MutableSharedFlow<RepEvent>()
    val repEvents: SharedFlow<RepEvent> = _repEvents
    
    // UI preferences
    private val _weightUnit = MutableStateFlow(WeightUnit.KG)
    val weightUnit: StateFlow<WeightUnit> = _weightUnit
    
    init {
        // Process real-time metrics for rep detection
        viewModelScope.launch {
            monitorData.collect { metric ->
                repDetectionEngine.processMetric(metric)?.let { event ->
                    when (event) {
                        is RepEvent.RepComplete -> {
                            _currentReps.value = event.repNumber
                            _repEvents.emit(event)
                            playHapticFeedback()
                        }
                    }
                }
            }
        }
    }
    
    fun startWorkout() {
        viewModelScope.launch {
            bleRepository.startWorkout(_workoutParameters.value)
                .onSuccess {
                    _isWorkoutActive.value = true
                    repDetectionEngine.reset()
                    _currentReps.value = 0
                }
        }
    }
}
```

### State Flow Patterns

**StateFlow vs SharedFlow:**
- `StateFlow`: For state that has a current value (connection status, workout parameters)
- `SharedFlow`: For events that don't have a "current" value (rep completed events, notifications)

**Hot vs Cold Flows:**
- Hot flows (SharedFlow): Always active, multiple collectors
- Cold flows (Flow): Start on collection, single collector

**Buffer Strategy for High-Frequency Data:**
```kotlin
// CRITICAL: Monitor data comes at 100Hz!
private val _monitorData = MutableSharedFlow<WorkoutMetric>(
    replay = 0,                              // No replay
    extraBufferCapacity = 64,                // Buffer 64 emissions (640ms)
    onBufferOverflow = BufferOverflow.DROP_OLDEST  // Drop old, keep new
)
```

---

## Exercise Library System

### Database Structure
```kotlin
@Entity(tableName = "exercises")
data class ExerciseEntity(
    @PrimaryKey val id: String,
    val name: String,
    val displayName: String,
    val muscleGroups: String,      // Comma-separated
    val equipment: String,          // Comma-separated
    val difficulty: String,
    val instructions: String,
    val defaultCableConfig: String
)

@Entity(tableName = "exercise_videos")
data class ExerciseVideoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val exerciseId: String,
    val videoUrl: String,
    val angle: String,              // FRONT, SIDE, etc.
    val thumbnailUrl: String?
)
```

### Exercise Picker Implementation
```kotlin
@Composable
fun ExercisePickerDialog(
    showDialog: Boolean,
    fullScreen: Boolean = false,
    onDismiss: () -> Unit,
    onExerciseSelected: (ExerciseEntity) -> Unit,
    exerciseRepository: ExerciseRepository
) {
    val exercises by exerciseRepository.getAllExercises()
        .collectAsState(initial = emptyList())
    
    var searchQuery by remember { mutableStateOf("") }
    var selectedEquipment by remember { mutableStateOf<String?>(null) }
    var selectedMuscleGroup by remember { mutableStateOf<String?>(null) }
    
    val filteredExercises = remember(exercises, searchQuery, selectedEquipment, selectedMuscleGroup) {
        exercises.filter { exercise ->
            // Search by name
            (searchQuery.isEmpty() || exercise.name.contains(searchQuery, ignoreCase = true)) &&
            // Filter by equipment
            (selectedEquipment == null || exercise.equipment.contains(selectedEquipment!!)) &&
            // Filter by muscle group
            (selectedMuscleGroup == null || exercise.muscleGroups.contains(selectedMuscleGroup!!))
        }
    }
    
    // UI: Search bar, filter chips, scrollable list
}
```

### Video Player Integration
```kotlin
@Composable
fun VideoPlayer(
    videoUrl: String,
    modifier: Modifier = Modifier
) {
    AndroidView(
        factory = { context ->
            PlayerView(context).apply {
                player = ExoPlayer.Builder(context).build().apply {
                    setMediaItem(MediaItem.fromUri(videoUrl))
                    prepare()
                    playWhenReady = true
                    repeatMode = Player.REPEAT_MODE_ALL
                }
            }
        },
        modifier = modifier
    )
}
```

---

## Workout Routines & Programs

### Daily Routines
```kotlin
@Entity(tableName = "daily_routines")
data class DailyRoutine(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val description: String = "",
    val exercisesJson: String,  // Serialized List<RoutineExercise>
    val createdAt: Long = System.currentTimeMillis(),
    val lastUsed: Long? = null
)
```

**Usage:**
1. Create routine with multiple exercises
2. Configure each exercise (sets, reps, weight, mode)
3. Save routine
4. One-tap start from routines list

### Weekly Programs
```kotlin
@Entity(tableName = "weekly_programs")
data class WeeklyProgram(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val description: String = "",
    val dayRoutinesJson: String,  // Map<DayOfWeek, DailyRoutine?>
    val createdAt: Long = System.currentTimeMillis(),
    val currentWeek: Int = 1,
    val progressionScheme: String = "LINEAR"
)
```

**Progressive Overload:**
```kotlin
enum class ProgressionScheme {
    LINEAR,      // Add fixed weight each week
    PERCENTAGE,  // Add percentage each week
    DOUBLE_PROGRESSION,  // Reps first, then weight
    WAVE_LOADING  // Vary intensity by week
}
```

---

## Background Service & Foreground Notification

### WorkoutForegroundService
**Purpose:** Keep BLE connection alive during workouts

```kotlin
class WorkoutForegroundService : Service() {
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = createWorkoutNotification()
        startForeground(NOTIFICATION_ID, notification)
        
        // Keep running until workout stops
        return START_STICKY
    }
    
    private fun createWorkoutNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Vitruvian Workout Active")
            .setContentText("Tap to return to workout")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setContentIntent(createPendingIntent())
            .build()
    }
}
```

**Lifecycle:**
```
Start Workout → Start Service → Show Notification
Stop Workout → Stop Service → Remove Notification
```

---

## Testing Strategy

### Unit Tests (Planned)
```kotlin
class ProtocolBuilderTest {
    @Test
    fun `buildProgramParams creates correct 96-byte frame`() {
        val params = WorkoutParameters(
            workoutType = WorkoutType.Program(ProgramMode.OldSchool),
            reps = 10,
            weightPerCableKg = 20f
        )
        val frame = ProtocolBuilder.buildProgramParams(params)
        
        assertEquals(96, frame.size)
        assertEquals(0x04, frame[0])  // Command byte
        assertEquals(13, frame[4])    // Reps + 3
    }
}
```

### Integration Tests
```kotlin
class BleRepositoryTest {
    @Test
    fun `startWorkout sends correct protocol sequence`() = runTest {
        // Mock BLE manager
        // Verify init sequence
        // Verify program params frame
    }
}
```

### Manual Testing Checklist
See `docs/TESTING_GUIDE.md` for comprehensive manual test cases

---

## Performance Considerations

### BLE Performance
- **Challenge:** Device sends data at 100Hz (10ms intervals)
- **Solution:** SharedFlow with 64-item buffer + DROP_OLDEST strategy
- **Monitoring:** Log buffer overflows to detect processing bottlenecks

### UI Performance
- **Challenge:** Real-time charts with 100Hz data
- **Solution:** Downsample to 10Hz for chart display, keep full resolution in DB
- **Compose:** Use `derivedStateOf` for expensive calculations

### Database Performance
- **Challenge:** Storing high-frequency workout metrics
- **Solution:** Batch inserts (100 metrics at a time), background thread
- **Indexes:** On timestamp, exerciseId for fast queries

---

## Common Issues & Solutions

### Issue 1: BLE Disconnects During Workout
**Symptom:** Connection drops randomly  
**Causes:**
1. No foreground service (Android kills background BLE)
2. Command queue overflow
3. Phone screen sleep

**Solutions:**
1. Always use WorkoutForegroundService
2. Serialize commands with Channel
3. Keep screen on during workout (FLAG_KEEP_SCREEN_ON)

### Issue 2: Picker State Resets on Scroll
**Symptom:** Scrolling number picker resets all other pickers  
**Root Cause:** Missing Compose keys, unstable function references  
**Solution:** (Applied 2025-11-03)
1. Add stable IDs to data classes
2. Use `remember(key)` for all state
3. Wrap list items in `key()` blocks
4. Stabilize function references with `remember`

### Issue 3: Rep Count Doesn't Match Reality
**Symptom:** Rep counter off by 1-2 reps  
**Causes:**
1. Threshold too high/low
2. Machine load spikes (noise)
3. Eccentric phase not detected

**Solutions:**
1. Tune RepDetectionEngine thresholds per exercise
2. Filter position spikes (keep last good position)
3. Use state machine pattern (IDLE → CONCENTRIC → ECCENTRIC)

### Issue 4: Workout Data Not Saving
**Symptom:** History shows empty or incomplete workouts  
**Causes:**
1. Room transaction not committed
2. Metrics JSON serialization failed
3. App killed before save completed

**Solutions:**
1. Save immediately on stop, not on app close
2. Use try-catch around JSON serialization
3. Use foreground service to prevent app kill

---

## Development Workflow

### Code Style
- Kotlin coding conventions
- Compose best practices
- Clean Architecture principles
- Single Responsibility Principle

### Git Workflow
- Feature branches: `feature/description`
- Bug fixes: `fix/description`
- Working branch: `working_branch` (current active branch)
- Commit format: Conventional Commits

### Build & Deploy
```bash
# Clean build
./gradlew clean assembleDebug

# Install on connected device
./gradlew installDebug

# Generate signed release APK
./gradlew assembleRelease
```

### Logging Strategy
```kotlin
// Use Timber for structured logging
Timber.d("Connection state: $state")
Timber.e(exception, "Failed to send command")

// BLE protocol logging (hex dump)
Timber.d("Sending frame: ${frame.toHexString()}")
```

---

## Future Enhancements

### Phase 3 Features
- [ ] Advanced analytics (volume load, tonnage)
- [ ] Exercise video library expansion
- [ ] Social features (share workouts)
- [ ] Cloud backup (optional)
- [ ] Wear OS companion app
- [ ] Auto-pause detection (IR sensors)

### Performance Optimizations
- [ ] Lazy loading for exercise library
- [ ] Image caching for exercise thumbnails
- [ ] Chart data downsampling
- [ ] Background sync for history

### UX Improvements
- [ ] Onboarding flow for new users
- [ ] Tutorial mode
- [ ] Exercise form tips
- [ ] Voice commands during workout
- [ ] Apple Watch integration (via companion iPhone app)

---

## Critical Knowledge Transfer

### If You Need to Debug BLE Issues
1. Enable BLE HCI snoop logging on Android
2. Capture packets with Wireshark
3. Compare with reference web app packets
4. **Every byte must match exactly**

### If You Need to Add New Workout Mode
1. Add enum to `ProgramMode` or new `WorkoutType` subclass
2. Define 32-byte mode profile in `ProtocolBuilder`
3. Add UI in mode selector
4. Test with real hardware (emulator won't work)

### If You Need to Add New Exercise
1. Insert into Room database (`ExerciseEntity`)
2. Add video URLs if available
3. Set default cable configuration
4. Verify muscle group tags for filtering

### If You Need to Modify Protocol
**DON'T.** The protocol is reverse-engineered and working.  
Any changes will break compatibility with machines.  
If you MUST change it:
1. Capture packets from official app
2. Document byte-by-byte changes
3. Test extensively with real hardware
4. Update `ProtocolBuilder.kt` comments

---

## Contact & Resources

### Documentation
- This file: Comprehensive knowledge base
- `docs/`: All technical documentation
- `reference/`: Original web app code
- `CHANGELOG.md`: Version history
- `LAST_SESSION.md`: Recent work summary

### External Resources
- Nordic BLE Library: https://github.com/NordicSemiconductor/Android-BLE-Library
- Jetpack Compose: https://developer.android.com/jetpack/compose
- Material 3: https://m3.material.io/

### Testing
- Minimum hardware: Android 8.0 device with BLE
- Recommended: Android 12+ for best BLE experience
- Vitruvian Trainer machine required for full testing

---

## Project Status

**Current Version:** 0.1.0-beta  
**Status:** Production-ready, beta testing  
**Last Major Update:** 2025-11-03 (Picker state fix)  
**Next Milestone:** Public beta release

**Completed:**
- ✅ Full BLE protocol implementation
- ✅ All workout modes (Program + Echo)
- ✅ Rep detection engine
- ✅ Exercise library with videos
- ✅ Workout history with Room
- ✅ Daily routines & weekly programs
- ✅ Analytics dashboard
- ✅ Unit conversion (kg/lb)
- ✅ Modern Material 3 UI
- ✅ State management fixes (pickers)

**In Progress:**
- 🔄 Beta testing with real users
- 🔄 Bug fixes based on feedback
- 🔄 Performance optimizations

**Planned:**
- 📋 Advanced analytics
- 📋 Social features
- 📋 Cloud backup (optional)
- 📋 Wear OS app

---

**END OF KNOWLEDGE BASE**

This document contains everything needed to understand, maintain, and extend the Vitruvian Redux project. Treat the BLE protocol as sacred - it's the result of reverse-engineering and must remain byte-perfect. Everything else can be improved and evolved.
