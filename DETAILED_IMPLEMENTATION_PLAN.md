# Vitruvian Redux - Detailed Implementation Plan
## User Feature Requests - Ordered by Feasibility (Easiest → Hardest)

**Created:** 2025-11-13
**Analysis Method:** DevilMCP + Quadrumvirate + Codebase Analysis
**Database Version:** 16 (current)

---

## Executive Summary

This document provides implementation-ready specifications for 10 user feature requests, ordered from easiest to hardest based on:
- **Technical Complexity** (code impact, architecture changes)
- **Database Migration Risk** (schema changes, data safety)
- **Testing Requirements** (unit, integration, E2E)
- **External Dependencies** (APIs, hardware, protocols)

**Total Estimated Effort:** 12-20 weeks across 3 phases

---

# TIER 1: QUICK WINS (1-3 days each)

## 1. ⭐ AMRAP Sets (EASIEST)
**Effort:** 1-2 days | **Complexity:** 2/10 | **Risk:** LOW

### Why This Is Easiest
- No database migration required (can use existing nullable field pattern)
- Minimal UI changes (single toggle + badge)
- No BLE protocol changes
- Isolated feature with no dependencies

### Implementation Details

#### **Files to Modify: 5**

1. **Data Model** (`app/src/main/java/com/example/vitruvianredux/data/local/`)
   - `RoutineExerciseEntity.kt` - Add `isAMRAP: Boolean = false` field
   - No migration needed if using embedded JSON or nullable pattern

2. **Repository** (`app/src/main/java/com/example/vitruvianredux/data/repository/`)
   - `WorkoutRepository.kt` - Pass through AMRAP flag (no logic changes)

3. **ViewModel** (`app/src/main/java/com/example/vitruvianredux/presentation/viewmodel/`)
   - `WorkoutViewModel.kt` - Disable auto-stop logic for AMRAP sets
   - Disable rep progress tracking for AMRAP

4. **UI - Routine Builder** (`app/src/main/java/com/example/vitruvianredux/presentation/screen/`)
   - `CreateRoutineScreen.kt` - Add AMRAP toggle switch per set
   - Hide rep count input when AMRAP enabled

5. **UI - Active Workout** (`app/src/main/java/com/example/vitruvianredux/presentation/screen/`)
   - `EnhancedMainScreen.kt` - Show "AMRAP" badge instead of rep progress
   - Manual set completion only (no auto-stop)

#### **Code Changes**

**RoutineExerciseEntity.kt** (No migration if using embedded):
```kotlin
data class RoutineSet(
    val targetReps: Int? = null,  // Nullable for AMRAP
    val targetWeight: Double,
    val isAMRAP: Boolean = false   // NEW FIELD
)
```

**CreateRoutineScreen.kt**:
```kotlin
Row {
    if (!set.isAMRAP) {
        TextField(
            value = set.targetReps?.toString() ?: "",
            label = { Text("Reps") }
        )
    }

    Switch(
        checked = set.isAMRAP,
        onCheckedChange = { isAMRAP ->
            updateSet(set.copy(isAMRAP = isAMRAP, targetReps = if (isAMRAP) null else 10))
        }
    )
    Text("AMRAP")
}
```

**EnhancedMainScreen.kt**:
```kotlin
if (currentSet.isAMRAP) {
    Badge(
        backgroundColor = MaterialTheme.colorScheme.tertiary
    ) {
        Text("AMRAP")
    }
} else {
    Text("${currentRep}/${currentSet.targetReps} reps")
}
```

#### **Testing**
- Unit: ViewModel logic for AMRAP auto-stop disable
- UI: Routine builder AMRAP toggle
- E2E: Complete AMRAP set, verify rep count saved

#### **Estimated Hours**
- Backend: 2 hours
- UI: 4 hours
- Testing: 2 hours
- **Total: 8 hours (1 day)**

---

## 2. ⭐ 0.5lb Weight Increments
**Effort:** 1-3 days | **Complexity:** 3/10 | **Risk:** MEDIUM (protocol verification required)

### Why This Is Easy
- Data model already uses `Double` for weight (supports decimals)
- UI change is trivial (step size adjustment)
- **BLOCKER:** Must verify BLE protocol supports 0.5lb precision

### Implementation Details

#### **Files to Modify: 4-6**

1. **Protocol Verification FIRST**
   - `app/src/main/java/com/example/vitruvianredux/data/ble/ProtocolBuilder.kt`
   - Check if weight field is integer or float in BLE frames
   - **If protocol uses integers:** Feature NOT feasible without firmware change
   - **If protocol uses floats:** Proceed with implementation

2. **UI - Weight Pickers** (assuming protocol supports it)
   - Find all weight input UI components (likely in multiple screens)
   - Change step size from `1.0` to `0.5`
   - Update display formatting to show `.5` properly

3. **Data Validation**
   - Add validation: weight must be multiple of 0.5
   - Round to nearest 0.5 if user enters arbitrary decimal

#### **Critical Decision Point**

**BEFORE IMPLEMENTATION:**
```kotlin
// Check ProtocolBuilder.kt - look for weight encoding
fun buildProgramParams(..., weight: Int/Float/Double, ...): ByteArray {
    buffer.putInt(weight.toInt())  // ❌ Integer = NOT SUPPORTED
    // vs
    buffer.putFloat(weight.toFloat())  // ✅ Float = SUPPORTED
}
```

If protocol uses `putInt()`, this feature requires:
- Firmware update (impossible for users)
- OR always round to nearest whole pound (defeats purpose)

**Recommendation:** Verify protocol FIRST before committing to user

#### **Code Changes** (if supported)

**Weight picker UI:**
```kotlin
// Find in routine builder, quick workout, etc.
NumberPicker(
    value = weight,
    onValueChange = { newWeight ->
        // Round to nearest 0.5
        val rounded = (newWeight * 2).roundToInt() / 2.0
        updateWeight(rounded)
    },
    step = 0.5,  // Changed from 1.0
    valueRange = 0.5..500.0
)

// Display formatting
Text("%.1f lbs".format(weight))  // Shows "135.5 lbs" instead of "135.0 lbs"
```

#### **Testing**
- Protocol: Send 0.5lb increment to machine, verify it accepts it
- Unit: Weight validation (multiples of 0.5)
- UI: Weight picker allows 0.5 steps
- E2E: Complete workout with 135.5 lbs, verify saved and displayed correctly

#### **Estimated Hours**
- Protocol verification: 2 hours (CRITICAL)
- UI changes: 3 hours
- Testing: 3 hours
- **Total: 8 hours (1 day)** if protocol supports it
- **Total: 0 hours (NOT FEASIBLE)** if protocol doesn't support it

---

# TIER 2: MODERATE FEATURES (1-2 weeks each)

## 3. Routine History Grouping
**Effort:** 1-2 weeks | **Complexity:** 5/10 | **Risk:** MEDIUM

### Why Moderate Complexity
- Database schema ALREADY supports this (v16 added `routineSessionId` and `routineName`)
- Main work is UI for grouped/expandable cards
- Requires new DAO queries for grouping
- Medium UI complexity (expandable lists, state management)

### Implementation Details

#### **Files to Modify: 8-10**

1. **Database - Already Done!** ✅
   - `WorkoutDatabase.kt` - v16 already has fields
   - `WorkoutSessionEntity.kt` - `routineSessionId: String?` and `routineName: String?` exist

2. **DAO Queries** (`app/src/main/java/com/example/vitruvianredux/data/local/`)
   - `WorkoutDao.kt` - Add new query:
   ```kotlin
   @Query("""
       SELECT * FROM workout_sessions
       WHERE routineSessionId IS NOT NULL
       GROUP BY routineSessionId
       ORDER BY timestamp DESC
   """)
   fun getGroupedRoutineSessions(): Flow<List<RoutineSessionGroup>>

   @Query("""
       SELECT * FROM workout_sessions
       WHERE routineSessionId = :sessionId
       ORDER BY timestamp ASC
   """)
   fun getRoutineSessionExercises(sessionId: String): Flow<List<WorkoutSessionEntity>>
   ```

3. **Data Model**
   - Create `RoutineSessionGroup.kt`:
   ```kotlin
   data class RoutineSessionGroup(
       val routineSessionId: String,
       val routineName: String,
       val timestamp: Long,
       val totalSets: Int,
       val totalReps: Int,
       val exercises: List<WorkoutSessionEntity>
   )
   ```

4. **Repository** (`WorkoutRepository.kt`)
   - Add methods to fetch grouped routine sessions
   - Transform DB entities to UI models

5. **ViewModel** (`WorkoutHistoryViewModel.kt` or create new)
   - Fetch grouped routines
   - Maintain expansion state (which routines are expanded)
   - Filter: show routines vs individual exercises

6. **UI - History Screen** (`app/src/main/java/com/example/vitruvianredux/presentation/screen/`)
   - `WorkoutHistoryScreen.kt` (or create new) - Expandable routine cards
   - Design:
     ```
     ┌─────────────────────────────────┐
     │ 📋 Push Day - Nov 13, 2025      │ ← Collapsed routine card
     │ 3 exercises • 12 sets • 85 reps │
     │ [▼] Tap to expand               │
     └─────────────────────────────────┘

     ┌─────────────────────────────────┐
     │ 📋 Push Day - Nov 13, 2025      │ ← Expanded routine card
     │ 3 exercises • 12 sets • 85 reps │
     │ [▲] Tap to collapse             │
     ├─────────────────────────────────┤
     │   💪 Bench Press                │
     │   4 sets • 32 reps • 185 lbs    │
     │                                 │
     │   💪 Overhead Press              │
     │   3 sets • 24 reps • 95 lbs     │
     │                                 │
     │   💪 Tricep Extensions           │
     │   5 sets • 29 reps • 45 lbs     │
     └─────────────────────────────────┘
     ```

#### **Code Changes**

**WorkoutDao.kt**:
```kotlin
// New query for grouped routine sessions
@Query("""
    SELECT
        routineSessionId,
        routineName,
        MIN(timestamp) as timestamp,
        COUNT(*) as totalSets,
        SUM(repsCompleted) as totalReps
    FROM workout_sessions
    WHERE routineSessionId IS NOT NULL
    GROUP BY routineSessionId
    ORDER BY timestamp DESC
""")
fun getRoutineSessionsSummary(): Flow<List<RoutineSessionSummary>>

@Query("""
    SELECT * FROM workout_sessions
    WHERE routineSessionId = :sessionId
    ORDER BY timestamp ASC
""")
suspend fun getRoutineSessionExercises(sessionId: String): List<WorkoutSessionEntity>
```

**WorkoutHistoryScreen.kt**:
```kotlin
@Composable
fun WorkoutHistoryScreen(viewModel: WorkoutHistoryViewModel) {
    val routineSessions by viewModel.routineSessions.collectAsState()
    val expandedSessions by viewModel.expandedSessions.collectAsState()

    LazyColumn {
        items(routineSessions) { session ->
            RoutineSessionCard(
                session = session,
                isExpanded = session.id in expandedSessions,
                onToggleExpand = { viewModel.toggleExpand(session.id) }
            )
        }
    }
}

@Composable
fun RoutineSessionCard(
    session: RoutineSessionGroup,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggleExpand() }
    ) {
        Column {
            // Header (always visible)
            Row {
                Icon(Icons.Default.List, "Routine")
                Text(session.routineName, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.weight(1f))
                Icon(
                    if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    "Expand"
                )
            }
            Text("${session.exercises.size} exercises • ${session.totalSets} sets • ${session.totalReps} reps")

            // Expanded content
            AnimatedVisibility(visible = isExpanded) {
                Column {
                    session.exercises.forEach { exercise ->
                        ExerciseSummaryRow(exercise)
                    }
                }
            }
        }
    }
}
```

#### **Testing**
- Unit: DAO queries return correct grouped data
- Unit: ViewModel expansion state management
- UI: Expandable cards expand/collapse correctly
- E2E: Complete routine, verify it appears as grouped entry in history

#### **Estimated Hours**
- Backend (DAO/Repository): 8 hours
- Data models: 4 hours
- ViewModel: 6 hours
- UI: 16 hours (expandable cards, animations, styling)
- Testing: 6 hours
- **Total: 40 hours (1 week)**

---

## 4. Per-Set Rest Times
**Effort:** 1 week | **Complexity:** 4/10 | **Risk:** LOW

### Implementation Details

#### **Database Migration Required**

**MIGRATION_16_17:**
```kotlin
val MIGRATION_16_17 = object : Migration(16, 17) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // Add restTimeSeconds column to routine_exercises
        database.execSQL("""
            ALTER TABLE routine_exercises
            ADD COLUMN restTimeSeconds INTEGER
        """)
        // Nullable for backward compatibility - null = use default rest time
    }
}
```

**AppModule.kt**:
```kotlin
Room.databaseBuilder(...)
    .addMigrations(
        MIGRATION_1_2, ..., MIGRATION_14_15,
        MIGRATION_16_17  // NEW
    )
    .fallbackToDestructiveMigration()
    .build()
```

#### **Files to Modify: 7**

1. **Database**
   - `RoutineExerciseEntity.kt` - Add `restTimeSeconds: Int?` field
   - `AppModule.kt` - Add MIGRATION_16_17

2. **UI - Routine Builder**
   - `CreateRoutineScreen.kt` - Add rest time input per set
   - Toggle: "Custom Rest Time" with TextField

3. **UI - Active Workout**
   - `EnhancedMainScreen.kt` - Use set-specific rest time if set
   - Display: "Rest: 60s" vs "Rest: 90s (default)"

4. **ViewModel**
   - `WorkoutViewModel.kt` - Read `restTimeSeconds` from set, fall back to default

#### **Code Changes**

**RoutineExerciseEntity.kt**:
```kotlin
data class RoutineExerciseEntity(
    // ... existing fields
    val restTimeSeconds: Int? = null  // null = use default
)
```

**CreateRoutineScreen.kt**:
```kotlin
Column {
    Text("Set ${setIndex + 1}")

    // Weight input
    WeightPicker(...)

    // Reps input
    RepsPicker(...)

    // Custom rest time (NEW)
    var useCustomRest by remember { mutableStateOf(set.restTimeSeconds != null) }

    Row {
        Checkbox(
            checked = useCustomRest,
            onCheckedChange = { useCustomRest = it }
        )
        Text("Custom rest time")
    }

    if (useCustomRest) {
        TextField(
            value = set.restTimeSeconds?.toString() ?: "60",
            label = { Text("Rest (seconds)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            onValueChange = { value ->
                updateSet(set.copy(restTimeSeconds = value.toIntOrNull()))
            }
        )
    }
}
```

**WorkoutViewModel.kt**:
```kotlin
fun getRestTimeForSet(set: RoutineSet): Int {
    return set.restTimeSeconds ?: DEFAULT_REST_TIME_SECONDS
}
```

#### **Testing**
- Migration: v16 → v17 without data loss
- Unit: Rest time fallback logic
- UI: Custom rest time input in routine builder
- E2E: Create routine with custom rest times, verify used during workout

#### **Estimated Hours**
- Migration: 3 hours
- Backend: 4 hours
- UI: 8 hours
- Testing: 5 hours
- **Total: 20 hours (2.5 days)**

---

# TIER 3: COMPLEX FEATURES (2-3 weeks each)

## 5. Custom Exercises
**Effort:** 2-3 weeks | **Complexity:** 7/10 | **Risk:** MEDIUM

### Implementation Details

#### **Database Migration Required**

**MIGRATION_17_18:** (or v16→v17 if per-set rest not done first)
```kotlin
val MIGRATION_17_18 = object : Migration(17, 18) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("""
            ALTER TABLE exercises
            ADD COLUMN isCustom INTEGER NOT NULL DEFAULT 0
        """)
        database.execSQL("""
            ALTER TABLE exercises
            ADD COLUMN createdByUserId TEXT
        """)
    }
}
```

#### **Files to Modify: 15+**

1. **Database**
   - `ExerciseEntity.kt` - Add `isCustom: Boolean`, `createdByUserId: String?`
   - `ExerciseDao.kt` - Add queries for custom exercises

2. **Repository**
   - `ExerciseRepository.kt` - CRUD operations for custom exercises

3. **ViewModel**
   - `ExerciseLibraryViewModel.kt` - Add custom exercise creation
   - New: `CustomExerciseViewModel.kt`

4. **UI** (NEW SCREEN REQUIRED)
   - `CustomExerciseCreationScreen.kt` - Form for creating custom exercise:
     - Name (TextField)
     - Muscle group (Dropdown)
     - Equipment (Dropdown)
     - Default cable config (Dropdown)
     - Notes (TextField, multiline)

5. **UI - Exercise Library**
   - `ExerciseLibraryScreen.kt` - Show custom exercises with badge
   - Add FAB for "Create Custom Exercise"
   - Long-press to edit/delete custom exercises

6. **Video Handling**
   - Custom exercises won't have videos - need placeholder
   - `ExerciseVideoEntity.kt` - Handle null video gracefully

#### **Code Changes**

**ExerciseEntity.kt**:
```kotlin
@Entity(tableName = "exercises")
data class ExerciseEntity(
    @PrimaryKey val id: String,
    val name: String,
    val muscleGroup: String,
    val equipment: String,
    val defaultCableConfig: String,
    val isCustom: Boolean = false,          // NEW
    val createdByUserId: String? = null      // NEW
)
```

**ExerciseDao.kt**:
```kotlin
@Query("SELECT * FROM exercises WHERE isCustom = 1")
fun getCustomExercises(): Flow<List<ExerciseEntity>>

@Insert
suspend fun insertCustomExercise(exercise: ExerciseEntity)

@Delete
suspend fun deleteCustomExercise(exercise: ExerciseEntity)
```

**CustomExerciseCreationScreen.kt**:
```kotlin
@Composable
fun CustomExerciseCreationScreen(
    viewModel: CustomExerciseViewModel,
    onNavigateBack: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var muscleGroup by remember { mutableStateOf("") }
    var equipment by remember { mutableStateOf("") }
    var cableConfig by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Create Custom Exercise") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // Name
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Exercise Name") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(16.dp))

            // Muscle Group Dropdown
            ExposedDropdownMenuBox(...) {
                // Muscle groups: Chest, Back, Shoulders, Arms, Legs, Core
            }

            Spacer(Modifier.height(16.dp))

            // Equipment Dropdown
            ExposedDropdownMenuBox(...) {
                // Equipment: Barbell, Dumbbell, Cable, Bodyweight, etc.
            }

            Spacer(Modifier.height(16.dp))

            // Cable Config (if equipment = Cable)
            if (equipment == "Cable") {
                ExposedDropdownMenuBox(...) {
                    // Low, Mid, High
                }
            }

            Spacer(Modifier.height(16.dp))

            // Notes
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Notes (optional)") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3
            )

            Spacer(Modifier.weight(1f))

            // Save Button
            Button(
                onClick = {
                    viewModel.createCustomExercise(
                        name = name,
                        muscleGroup = muscleGroup,
                        equipment = equipment,
                        cableConfig = cableConfig,
                        notes = notes
                    )
                    onNavigateBack()
                },
                enabled = name.isNotBlank() && muscleGroup.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Create Exercise")
            }
        }
    }
}
```

**ExerciseLibraryScreen.kt**:
```kotlin
@Composable
fun ExerciseLibraryScreen(...) {
    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = { navController.navigate("custom-exercise-creation") }
            ) {
                Icon(Icons.Default.Add, "Create Custom Exercise")
            }
        }
    ) {
        LazyColumn {
            // Official exercises
            items(officialExercises) { exercise ->
                ExerciseCard(exercise)
            }

            // Custom exercises with badge
            items(customExercises) { exercise ->
                ExerciseCard(
                    exercise = exercise,
                    badge = {
                        Badge { Text("Custom") }
                    },
                    onLongPress = {
                        // Show edit/delete dialog
                    }
                )
            }
        }
    }
}
```

#### **Testing**
- Migration: v17 → v18 without data loss
- Unit: Custom exercise CRUD operations
- UI: Custom exercise creation form
- E2E: Create custom exercise, use in routine, complete workout

#### **Estimated Hours**
- Migration: 4 hours
- Backend (DAO/Repository): 10 hours
- New screen UI: 20 hours
- Exercise library integration: 8 hours
- Testing: 8 hours
- **Total: 50 hours (1.25 weeks)**

---

## 6. Progressive Overload / Warm-up Sets
**Effort:** 2-3 weeks | **Complexity:** 7/10 | **Risk:** MEDIUM-HIGH

### Implementation Details

#### **Database Migration Required**

**MIGRATION_18_19:** (or earlier if other features skipped)
```kotlin
val MIGRATION_18_19 = object : Migration(18, 19) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("""
            ALTER TABLE routine_exercises
            ADD COLUMN setType TEXT DEFAULT 'working'
        """)
        database.execSQL("""
            ALTER TABLE routine_exercises
            ADD COLUMN workingWeightPercentage REAL
        """)
    }
}
```

#### **Files to Modify: 10+**

1. **Database**
   - `RoutineExerciseEntity.kt` - Add `setType: String` ("warmup", "working", "backoff")
   - `RoutineExerciseEntity.kt` - Add `workingWeightPercentage: Float?`

2. **UI - Routine Builder**
   - Add set type dropdown per set
   - If warmup/backoff: show percentage calculator
   - Example: "This set is 60% of working weight (135 lbs)"

3. **Workout Logic**
   - Calculate actual weight from percentage during workout
   - Need to know "working weight" - either:
     - User manually enters working weight for exercise
     - OR use PR tracking to auto-suggest

4. **PR Tracking Integration** (may already exist)
   - Fetch user's PR for this exercise
   - Suggest working weight as % of PR

#### **Code Changes**

**RoutineExerciseEntity.kt**:
```kotlin
data class RoutineSet(
    val targetReps: Int?,
    val targetWeight: Double?,  // null if using percentage
    val setType: String = "working",  // "warmup", "working", "backoff"
    val workingWeightPercentage: Float? = null,  // null if using absolute weight
    val restTimeSeconds: Int? = null,
    val isAMRAP: Boolean = false
)
```

**CreateRoutineScreen.kt**:
```kotlin
Column {
    // Set type dropdown
    ExposedDropdownMenuBox(...) {
        ExposedDropdownMenu(...) {
            DropdownMenuItem(
                text = { Text("Warm-up Set") },
                onClick = { updateSet(set.copy(setType = "warmup")) }
            )
            DropdownMenuItem(
                text = { Text("Working Set") },
                onClick = { updateSet(set.copy(setType = "working")) }
            )
            DropdownMenuItem(
                text = { Text("Back-off Set") },
                onClick = { updateSet(set.copy(setType = "backoff")) }
            )
        }
    }

    // Weight input
    if (set.setType == "working") {
        // Absolute weight
        WeightPicker(
            weight = set.targetWeight ?: 0.0,
            onWeightChange = { updateSet(set.copy(targetWeight = it, workingWeightPercentage = null)) }
        )
    } else {
        // Percentage-based
        Row {
            Slider(
                value = set.workingWeightPercentage ?: 70f,
                onValueChange = { percentage ->
                    updateSet(set.copy(workingWeightPercentage = percentage, targetWeight = null))
                },
                valueRange = 40f..100f
            )
            Text("${set.workingWeightPercentage?.toInt()}%")
        }

        // Show calculated weight
        val workingWeight = getWorkingWeightForExercise(exerciseId)
        val calculatedWeight = workingWeight * (set.workingWeightPercentage ?: 70f) / 100f
        Text("= ${calculatedWeight.toInt()} lbs", style = MaterialTheme.typography.bodySmall)
    }
}
```

**WorkoutViewModel.kt**:
```kotlin
fun getActualWeightForSet(set: RoutineSet, exerciseId: String): Double {
    return if (set.targetWeight != null) {
        set.targetWeight  // Absolute weight
    } else if (set.workingWeightPercentage != null) {
        // Calculate from percentage
        val workingWeight = getWorkingWeightForExercise(exerciseId)
        workingWeight * set.workingWeightPercentage / 100.0
    } else {
        0.0
    }
}

private fun getWorkingWeightForExercise(exerciseId: String): Double {
    // Option 1: User manually entered working weight
    // Option 2: Use PR from personal_records table
    // Option 3: Use heaviest weight from this routine
    return personalRecordRepository.getPRForExercise(exerciseId)?.weight ?: 0.0
}
```

#### **Testing**
- Migration: v18 → v19 without data loss
- Unit: Weight percentage calculation
- Unit: Working weight determination logic
- UI: Set type dropdown and percentage slider
- E2E: Create routine with warm-up progression (60% → 80% → 100%), verify weights calculated correctly

#### **Estimated Hours**
- Migration: 4 hours
- Backend (percentage calculation logic): 12 hours
- PR integration: 8 hours
- UI: 20 hours
- Testing: 8 hours
- **Total: 52 hours (1.3 weeks)**

---

# TIER 4: ADVANCED FEATURES (3-4 weeks each)

## 7. Health Connect Integration
**Effort:** 3-4 weeks | **Complexity:** 8/10 | **Risk:** HIGH

### Why This Is Complex
- External API integration (Android Health Connect)
- Requires Android 14+ (API 34+) - current min API is 26
- Need conditional feature gating for older devices
- Complex data mapping: Workout → ExerciseSession
- Permissions handling
- Background sync with WorkManager
- Conflict resolution (what if data exists in both?)

### Implementation Details

#### **Build Configuration Changes**

**build.gradle.kts** (app level):
```kotlin
android {
    defaultConfig {
        // Health Connect requires API 34+
        // But we still support API 26+
        minSdk = 26
        targetSdk = 36
    }
}

dependencies {
    // Health Connect dependency (only available API 34+)
    implementation("androidx.health.connect:connect-client:1.1.0-alpha09")

    // WorkManager for background sync
    implementation("androidx.work:work-runtime-ktx:2.9.0")
}
```

#### **Permissions**

**AndroidManifest.xml**:
```xml
<!-- Health Connect permissions -->
<uses-permission android:name="android.permission.health.READ_EXERCISE" />
<uses-permission android:name="android.permission.health.WRITE_EXERCISE" />

<!-- Only request on Android 14+ -->
<uses-permission-sdk-23 android:name="android.permission.health.READ_EXERCISE" />
<uses-permission-sdk-23 android:name="android.permission.health.WRITE_EXERCISE" />
```

#### **Files to Create/Modify: 20+**

1. **Health Connect Manager** (NEW)
   - `app/src/main/java/com/example/vitruvianredux/data/healthconnect/`
   - `HealthConnectManager.kt` - Wrapper for Health Connect API
   - `HealthConnectPermissionsManager.kt` - Handle permission requests
   - `WorkoutToHealthConnectMapper.kt` - Map Workout → ExerciseSession

2. **Background Sync Worker** (NEW)
   - `HealthConnectSyncWorker.kt` - Background sync with WorkManager
   - Periodic sync (every 6 hours?)
   - Manual sync trigger

3. **Repository**
   - `WorkoutRepository.kt` - Add methods to sync with Health Connect

4. **ViewModel**
   - `SettingsViewModel.kt` - Add Health Connect toggle
   - `HealthConnectSyncViewModel.kt` - Show sync status

5. **UI - Settings**
   - `SettingsScreen.kt` - Add Health Connect section:
     - Toggle to enable/disable sync
     - "Sync Now" button
     - Last sync timestamp
     - Permissions status

6. **Feature Gating**
   - `HealthConnectAvailability.kt` - Check if device supports Health Connect (API 34+)
   - Gracefully hide feature on older devices

#### **Code Changes**

**HealthConnectManager.kt**:
```kotlin
class HealthConnectManager @Inject constructor(
    private val context: Context
) {
    private val healthConnectClient by lazy {
        HealthConnectClient.getOrCreate(context)
    }

    suspend fun isAvailable(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
               HealthConnectClient.isAvailable(context)
    }

    suspend fun hasPermissions(): Boolean {
        val permissions = setOf(
            HealthPermission.createReadPermission(ExerciseSessionRecord::class),
            HealthPermission.createWritePermission(ExerciseSessionRecord::class)
        )
        return healthConnectClient.permissionController.getGrantedPermissions(permissions)
            .containsAll(permissions)
    }

    suspend fun requestPermissions(activity: Activity) {
        val permissions = setOf(
            HealthPermission.createReadPermission(ExerciseSessionRecord::class),
            HealthPermission.createWritePermission(ExerciseSessionRecord::class),
            HealthPermission.createWritePermission(ExerciseRepetitionRecord::class)
        )
        activity.requestPermissions(permissions.toTypedArray(), PERMISSION_REQUEST_CODE)
    }

    suspend fun syncWorkout(workout: WorkoutSession) {
        if (!hasPermissions()) {
            Timber.w("Health Connect permissions not granted")
            return
        }

        // Map workout to ExerciseSessionRecord
        val exerciseSession = ExerciseSessionRecord(
            startTime = Instant.ofEpochMilli(workout.timestamp),
            endTime = Instant.ofEpochMilli(workout.timestamp + workout.durationMs),
            exerciseType = mapExerciseType(workout.exerciseId),
            title = workout.exerciseName,
            notes = "Completed via Vitruvian Redux"
        )

        // Insert reps data
        val reps = workout.sets.map { set ->
            ExerciseRepetitionRecord(
                count = set.repsCompleted,
                type = ExerciseRepetitionRecord.REPETITION_TYPE_UNKNOWN,
                startTime = exerciseSession.startTime,
                endTime = exerciseSession.endTime
            )
        }

        try {
            healthConnectClient.insertRecords(listOf(exerciseSession) + reps)
            Timber.i("Successfully synced workout to Health Connect")
        } catch (e: Exception) {
            Timber.e(e, "Failed to sync workout to Health Connect")
            throw e
        }
    }

    private fun mapExerciseType(exerciseId: String): Int {
        // Map Vitruvian exercises to Health Connect exercise types
        return when (exerciseId) {
            "bench_press" -> ExerciseSessionRecord.EXERCISE_TYPE_BENCH_PRESS
            "squat" -> ExerciseSessionRecord.EXERCISE_TYPE_BACK_SQUAT
            "deadlift" -> ExerciseSessionRecord.EXERCISE_TYPE_DEADLIFT
            // ... more mappings
            else -> ExerciseSessionRecord.EXERCISE_TYPE_WEIGHTLIFTING
        }
    }
}
```

**HealthConnectSyncWorker.kt**:
```kotlin
class HealthConnectSyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val healthConnectManager = // ... inject
        val workoutRepository = // ... inject

        if (!healthConnectManager.isAvailable()) {
            return Result.success()  // Not available on this device
        }

        if (!healthConnectManager.hasPermissions()) {
            return Result.failure()  // Permissions not granted
        }

        // Get unsynced workouts
        val unsyncedWorkouts = workoutRepository.getUnsyncedWorkouts()

        unsyncedWorkouts.forEach { workout ->
            try {
                healthConnectManager.syncWorkout(workout)
                workoutRepository.markWorkoutAsSynced(workout.id)
            } catch (e: Exception) {
                Timber.e(e, "Failed to sync workout ${workout.id}")
                // Continue with next workout
            }
        }

        return Result.success()
    }
}
```

**SettingsScreen.kt**:
```kotlin
@Composable
fun SettingsScreen(viewModel: SettingsViewModel) {
    val healthConnectAvailable by viewModel.healthConnectAvailable.collectAsState()
    val healthConnectEnabled by viewModel.healthConnectEnabled.collectAsState()
    val lastSyncTime by viewModel.lastHealthConnectSync.collectAsState()

    if (healthConnectAvailable) {
        Section(title = "Health Connect") {
            SwitchPreference(
                title = "Sync with Health Connect",
                summary = "Automatically sync workouts to Android Health Connect",
                checked = healthConnectEnabled,
                onCheckedChange = { enabled ->
                    if (enabled) {
                        viewModel.requestHealthConnectPermissions()
                    } else {
                        viewModel.disableHealthConnect()
                    }
                }
            )

            if (healthConnectEnabled) {
                Preference(
                    title = "Sync Now",
                    summary = lastSyncTime?.let { "Last synced: ${formatTimestamp(it)}" }
                        ?: "Never synced",
                    onClick = { viewModel.triggerManualSync() }
                )
            }
        }
    }
}
```

#### **Testing**
- Unit: Workout → ExerciseSession mapping
- Unit: Sync worker logic
- Integration: Sync to actual Health Connect (requires device with API 34+)
- E2E: Complete workout, verify it syncs to Health Connect, verify visible in Google Fit

#### **Estimated Hours**
- Health Connect API integration: 20 hours
- Permissions handling: 8 hours
- Data mapping: 12 hours
- Background sync worker: 12 hours
- UI: 10 hours
- Testing (requires API 34+ device): 12 hours
- **Total: 74 hours (1.8 weeks)**

---

## 8. Gamification - Badges and Streaks
**Effort:** 3-4 weeks | **Complexity:** 6/10 | **Risk:** MEDIUM

### Implementation Details

#### **Database Migration Required**

**MIGRATION_19_20:**
```kotlin
val MIGRATION_19_20 = object : Migration(19, 20) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // Achievement entity
        database.execSQL("""
            CREATE TABLE IF NOT EXISTS achievements (
                id TEXT PRIMARY KEY NOT NULL,
                name TEXT NOT NULL,
                description TEXT NOT NULL,
                iconName TEXT NOT NULL,
                category TEXT NOT NULL,
                requirement INTEGER NOT NULL
            )
        """)

        // User achievements (earned badges)
        database.execSQL("""
            CREATE TABLE IF NOT EXISTS user_achievements (
                achievementId TEXT PRIMARY KEY NOT NULL,
                earnedAt INTEGER NOT NULL,
                FOREIGN KEY(achievementId) REFERENCES achievements(id)
            )
        """)

        // Workout streak tracking
        database.execSQL("""
            CREATE TABLE IF NOT EXISTS workout_streaks (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                startDate INTEGER NOT NULL,
                endDate INTEGER,
                streakCount INTEGER NOT NULL
            )
        """)
    }
}
```

#### **Files to Create/Modify: 25+**

1. **Database Entities** (NEW)
   - `AchievementEntity.kt` - Achievement definitions
   - `UserAchievementEntity.kt` - User's earned achievements
   - `WorkoutStreakEntity.kt` - Streak tracking

2. **Achievement Definitions** (NEW)
   - `Achievements.kt` - Define all achievements:
     ```kotlin
     val ACHIEVEMENTS = listOf(
         Achievement(
             id = "first_workout",
             name = "Getting Started",
             description = "Complete your first workout",
             iconName = "ic_trophy_bronze",
             category = "Milestone",
             requirement = 1
         ),
         Achievement(
             id = "workout_streak_7",
             name = "Week Warrior",
             description = "Workout 7 days in a row",
             iconName = "ic_fire",
             category = "Streak",
             requirement = 7
         ),
         Achievement(
             id = "total_workouts_100",
             name = "Century Club",
             description = "Complete 100 total workouts",
             iconName = "ic_trophy_gold",
             category = "Milestone",
             requirement = 100
         ),
         Achievement(
             id = "total_reps_10000",
             name = "Rep Master",
             description = "Complete 10,000 total reps",
             iconName = "ic_muscle",
             category = "Volume",
             requirement = 10000
         ),
         Achievement(
             id = "pr_bench_press",
             name = "Bench Beast",
             description = "Set a new bench press PR",
             iconName = "ic_chest",
             category = "PR",
             requirement = 1
         )
         // ... 20-30 more achievements
     )
     ```

3. **Achievement Tracker** (NEW)
   - `AchievementTracker.kt` - Check for newly earned achievements
   - Called after every workout completion
   - Emits events when achievements earned

4. **Streak Tracker** (NEW)
   - `StreakTracker.kt` - Track daily workout streaks
   - Updates after each workout
   - Detects broken streaks

5. **UI - Profile/Achievements Screen** (NEW)
   - `AchievementsScreen.kt` - Grid of earned badges
   - Show locked achievements (grayed out)
   - Progress bars for in-progress achievements

6. **UI - Notifications**
   - Show notification when badge earned
   - Show notification when streak milestone hit

7. **UI - Main Screen Widget**
   - Current streak counter
   - Next achievement progress

#### **Code Changes**

**AchievementTracker.kt**:
```kotlin
class AchievementTracker @Inject constructor(
    private val workoutRepository: WorkoutRepository,
    private val achievementRepository: AchievementRepository,
    private val notificationManager: NotificationManager
) {
    suspend fun checkAchievementsAfterWorkout() {
        val totalWorkouts = workoutRepository.getTotalWorkoutCount()
        val totalReps = workoutRepository.getTotalRepsCount()
        val currentStreak = streakTracker.getCurrentStreak()

        // Check each achievement
        ACHIEVEMENTS.forEach { achievement ->
            if (!achievementRepository.isEarned(achievement.id)) {
                val earned = when (achievement.category) {
                    "Milestone" -> totalWorkouts >= achievement.requirement
                    "Volume" -> totalReps >= achievement.requirement
                    "Streak" -> currentStreak >= achievement.requirement
                    // ... more categories
                    else -> false
                }

                if (earned) {
                    achievementRepository.markAsEarned(achievement.id)
                    showAchievementNotification(achievement)
                }
            }
        }
    }

    private fun showAchievementNotification(achievement: Achievement) {
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_trophy)
            .setContentTitle("Achievement Unlocked!")
            .setContentText("${achievement.name}: ${achievement.description}")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(achievement.id.hashCode(), notification)
    }
}
```

**StreakTracker.kt**:
```kotlin
class StreakTracker @Inject constructor(
    private val workoutRepository: WorkoutRepository,
    private val streakRepository: StreakRepository
) {
    suspend fun updateStreakAfterWorkout() {
        val today = LocalDate.now()
        val lastWorkoutDate = workoutRepository.getLastWorkoutDate()

        val currentStreak = streakRepository.getCurrentStreak()

        when {
            lastWorkoutDate == null -> {
                // First workout ever
                streakRepository.startNewStreak(today, count = 1)
            }
            lastWorkoutDate == today -> {
                // Already worked out today, no change
            }
            lastWorkoutDate == today.minusDays(1) -> {
                // Worked out yesterday - extend streak
                streakRepository.extendStreak(currentStreak.id, count = currentStreak.streakCount + 1)
            }
            else -> {
                // Streak broken - start new one
                streakRepository.endStreak(currentStreak.id, endDate = lastWorkoutDate)
                streakRepository.startNewStreak(today, count = 1)
            }
        }
    }

    fun getCurrentStreak(): Int {
        return streakRepository.getCurrentStreak()?.streakCount ?: 0
    }
}
```

**AchievementsScreen.kt**:
```kotlin
@Composable
fun AchievementsScreen(viewModel: AchievementsViewModel) {
    val earnedAchievements by viewModel.earnedAchievements.collectAsState()
    val allAchievements by viewModel.allAchievements.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Achievements") })
        }
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.padding(padding)
        ) {
            items(allAchievements) { achievement ->
                val isEarned = achievement.id in earnedAchievements.map { it.achievementId }

                AchievementCard(
                    achievement = achievement,
                    isEarned = isEarned,
                    progress = if (!isEarned) {
                        viewModel.getProgressToward(achievement.id)
                    } else null
                )
            }
        }
    }
}

@Composable
fun AchievementCard(
    achievement: Achievement,
    isEarned: Boolean,
    progress: Float? = null
) {
    Card(
        modifier = Modifier
            .padding(8.dp)
            .aspectRatio(1f),
        colors = CardDefaults.cardColors(
            containerColor = if (isEarned)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxSize().padding(8.dp)
        ) {
            Icon(
                painter = painterResource(
                    if (isEarned) achievement.iconResId else R.drawable.ic_lock
                ),
                contentDescription = achievement.name,
                modifier = Modifier.size(48.dp),
                tint = if (isEarned)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
            )

            Spacer(Modifier.height(4.dp))

            Text(
                text = achievement.name,
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            if (!isEarned && progress != null) {
                LinearProgressIndicator(
                    progress = progress,
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                )
            }
        }
    }
}
```

#### **Design Assets Needed**
- Badge icons (20-30 SVG/PNG icons)
- Trophy icons (bronze, silver, gold)
- Streak fire icon
- Lock icon for locked achievements

#### **Testing**
- Unit: Achievement logic (when should badge be earned?)
- Unit: Streak calculation (daily, weekly)
- UI: Achievements screen grid layout
- E2E: Complete workout, verify streak increments, verify badge earned

#### **Estimated Hours**
- Database migration: 6 hours
- Achievement definitions: 8 hours
- Achievement tracker logic: 12 hours
- Streak tracker logic: 10 hours
- Achievements screen UI: 16 hours
- Icon/asset creation: 8 hours
- Notifications: 6 hours
- Testing: 10 hours
- **Total: 76 hours (1.9 weeks)**

---

# TIER 5: DEFERRED / OUT OF SCOPE

## 9. Program Import from Other Apps
**Effort:** 8-12 weeks | **Complexity:** 10/10 | **Risk:** VERY HIGH

### Why This Is Hardest
- No public APIs for TrainHeroic, SugarWOD, etc.
- Would require reverse engineering proprietary formats
- Screenshot OCR/AI parsing is extremely complex
- High error rate - incorrect data could cause bad workouts
- Legal/ToS concerns (scraping other apps' data)
- Maintenance burden (formats change over time)

### Recommendation
**DEFER INDEFINITELY** or implement simpler alternatives:

1. **CSV/JSON Import** (2-3 weeks)
   - Allow users to export programs as CSV/JSON
   - Provide import parser for standard format
   - Example CSV format:
     ```csv
     Exercise,Sets,Reps,Weight,Rest
     Bench Press,4,8,185,90
     Squat,4,6,225,120
     ```

2. **Copy/Paste Text Format** (1 week)
   - Users paste text like:
     ```
     Bench Press: 3x8 @ 185 lbs
     Squat: 4x6 @ 225 lbs
     ```
   - Simple regex parser extracts data

3. **Manual Entry Only** (0 weeks)
   - Status quo - users manually create routines
   - Provide good UX for fast manual entry

**If user insists on screenshot parsing:**
- Requires AI/ML integration (TensorFlow Lite, ML Kit)
- OCR for text extraction
- NLP for parsing exercise names, sets, reps
- Very high error rate (60-70% accuracy)
- **Effort:** 8-12 weeks, **Risk:** VERY HIGH

---

## 10. Routine Grouping (Needs Clarification)
**Effort:** TBD | **Complexity:** TBD | **Risk:** TBD

### Status
This request lacks description. Possible interpretations:

1. **Duplicate of #1** (Routine History Grouping) - Already covered
2. **Routine Categories/Folders** - Organize routines into folders (e.g., "Push", "Pull", "Legs")
3. **Superset/Circuit Support** - Group exercises to perform back-to-back with no rest

**Action Required:** Contact user for clarification before planning

---

# SUMMARY: IMPLEMENTATION ORDER

## Recommended Order (Easiest → Hardest)

| Rank | Feature | Effort | Complexity | Risk | Total Hours |
|------|---------|--------|------------|------|-------------|
| 1 | AMRAP Sets | 1-2 days | 2/10 | LOW | 8h |
| 2 | 0.5lb Increments | 1-3 days | 3/10 | MEDIUM | 8h |
| 3 | Per-Set Rest Times | 1 week | 4/10 | LOW | 20h |
| 4 | Routine History Grouping | 1-2 weeks | 5/10 | MEDIUM | 40h |
| 5 | Custom Exercises | 2-3 weeks | 7/10 | MEDIUM | 50h |
| 6 | Progressive Overload | 2-3 weeks | 7/10 | MEDIUM-HIGH | 52h |
| 7 | Health Connect | 3-4 weeks | 8/10 | HIGH | 74h |
| 8 | Gamification | 3-4 weeks | 6/10 | MEDIUM | 76h |
| 9 | Routine Grouping | TBD | TBD | TBD | TBD |
| 10 | Program Import | 8-12 weeks | 10/10 | VERY HIGH | 320h+ |

**Total for Features 1-8:** 328 hours = ~8-9 weeks with single developer

---

# PHASE BREAKDOWN

## Phase 1: Quick Wins (1 week)
- ✅ AMRAP Sets (1 day)
- ✅ 0.5lb Increments (1 day, if protocol supports)
- ✅ Per-Set Rest Times (3 days)

**Deliverable:** 3 user-visible features, 1 database migration

---

## Phase 2: Core Features (4 weeks)
- ✅ Routine History Grouping (1.5 weeks)
- ✅ Custom Exercises (1.5 weeks)
- ✅ Progressive Overload (1 week)

**Deliverable:** 3 major features, 2 database migrations

---

## Phase 3: Advanced Features (4 weeks)
- ✅ Health Connect Integration (2 weeks)
- ✅ Gamification (2 weeks)

**Deliverable:** 2 complex features, 1 database migration

---

## Total Timeline
**9 weeks** for features 1-8 (excludes Program Import)

---

# RISK MITIGATION

## High-Risk Items

1. **0.5lb Increments** - Verify BLE protocol FIRST
   - **Mitigation:** Test protocol before committing to user

2. **Health Connect** - Android 14+ only
   - **Mitigation:** Feature gating, graceful degradation on older devices

3. **Database Migrations** - Data loss risk
   - **Mitigation:** Fallback to destructive migration (acceptable for beta)
   - **Future:** Add proper migrations before 1.0 release

4. **Program Import** - Too complex, low ROI
   - **Mitigation:** DEFER or implement CSV import as alternative

---

# TESTING STRATEGY

## For Each Feature

1. **Unit Tests** (ViewModel, Repository, logic)
   - JUnit + MockK
   - Test edge cases, null handling, calculation logic

2. **Integration Tests** (Database, DAOs)
   - Room database queries
   - Migration testing

3. **UI Tests** (Compose)
   - Compose testing library
   - Screen navigation, user input

4. **E2E Tests** (Requires device)
   - Complete user flows
   - Verify data persistence

5. **Hardware Tests** (Requires Vitruvian machine)
   - BLE protocol verification
   - Weight/rep tracking accuracy

---

# DEPENDENCIES

## External
- Android Health Connect API (API 34+) - for feature #7
- BLE protocol specification - for feature #2
- Icon/asset design - for feature #8

## Internal
- Room database migrations (v16 → v17 → v18 → v19 → v20)
- Existing BLE stack (VitruvianBleManager)
- Existing UI components (reusable composables)

---

# CONCLUSION

**Recommended Implementation Order:**
1. AMRAP (1 day)
2. 0.5lb increments (1 day, after protocol verification)
3. Per-set rest times (3 days)
4. Routine history grouping (1.5 weeks)
5. Custom exercises (1.5 weeks)
6. Progressive overload (1 week)
7. Health Connect (2 weeks)
8. Gamification (2 weeks)

**Total:** ~9 weeks for features 1-8

**Defer:** Program import (#10) - too complex, low ROI

**Clarify:** Routine grouping (#9) - ambiguous request

---

**Document Version:** 1.0
**Last Updated:** 2025-11-13
**Author:** Claude Code (AI Analysis with DevilMCP tracking)
