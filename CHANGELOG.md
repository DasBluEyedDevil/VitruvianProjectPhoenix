# Changelog

All notable changes to the Vitruvian Redux project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/).

## [Unreleased]

### Added - Per-Set Reps Backend Implementation (2025-10-28)

**Complete Backend for Variable Reps Per Set:**

**Database Schema Changes:**
- Changed `RoutineExerciseEntity` from `sets: Int` and `reps: Int` to `setReps: String`
- String format: comma-separated values (e.g., "10,10,10" for standard, "10,8,6,4" for pyramid)
- Migration 3→4: Added `setReps` column, populated from existing sets/reps data
- Old columns left in database for backwards compatibility (Room ignores them)
- Database version incremented to 4

**Type Conversion:**
- Added `Converters.kt` with Room TypeConverters for List<Int> ↔ String conversion
- Registered `@TypeConverters(Converters::class)` in WorkoutDatabase
- Conversion methods handle empty strings and invalid data gracefully

**Migration Strategy:**
- MIGRATION_3_4 uses SQL subquery to convert old format to new
- Preserves all existing routine data (zero data loss)
- Backwards compatible: old columns remain for rollback if needed
- Example: `sets=3, reps=10` converts to `setReps="10,10,10"`

**Repository Updates:**
- `toEntity()`: Converts `List<Int>` to comma-separated String for storage
- `toRoutineExercise()`: Converts String back to `List<Int>` for domain model
- Proper error handling with `mapNotNull` and empty string checks

**Domain Model:**
- `Routine.kt` already updated with `setReps: List<Int>` field (from previous session)
- Computed properties maintain backwards compatibility:
  - `val sets: Int get() = setReps.size`
  - `val reps: Int get() = setReps.firstOrNull() ?: 10`
- Enables pyramid training: [10, 8, 6, 4], reverse pyramid, 5x5, etc.

**UI Compatibility Fixes:**
- `ExerciseEditDialog.kt`: Updated to create `setReps` list from UI inputs
- `RoutineBuilderDialog.kt`: Updated default values to use `listOf(10, 10, 10)`
- Existing UI continues to work with "Sets" and "Reps" fields via computed properties
- Full UI redesign for per-set rep adjustment pending (future session)

**Key Features Enabled:**
- Pyramid sets: Decreasing reps per set (10, 8, 6, 4)
- Reverse pyramid: Increasing reps per set (4, 6, 8, 10)
- Standard sets: Same reps per set (10, 10, 10)
- Custom patterns: Any combination of rep counts

**Build Status:** ✅ SUCCESSFUL - All compilation errors resolved, tests passing

### Added - Cable Configuration System (2025-01-28)

**Complete Cable Tracking Support:**

**New Enum:**
- `CableConfiguration`: SINGLE, DOUBLE, EITHER - tracks how cables are used per exercise
- Machine tracks both cables independently (loadA, loadB from BLE protocol)
- Weight is always specified "per cable" matching official app terminology

**Exercise List Updates (37 exercises, 9 categories):**
- Removed incompatible exercises: Lat Pulldown, Ab Pulldown, Tricep Pushdown (require overhead anchor)
- Reorganized categories: Chest (4), Back (5), Shoulders (4), Biceps (4), Triceps (2), Legs (7), Glutes (3), Core (3), Full Body (1)
- Each exercise has `defaultCableConfig` property
- Added 20+ new compatible exercises including Hamstring Curl (from official app)

**Cable Configuration by Exercise Type:**
- DOUBLE only: Bench Press, Squats, Deadlifts, Hip Thrusts, etc. (bilateral movements)
- SINGLE only: Concentration Curl, Hamstring Curl, Pallof Press, Wood Chop (unilateral movements)
- EITHER: Bicep Curl, Hammer Curl, Shoulder Press, Rows (user can choose single or double)

**Database Changes:**
- Added `cableConfig` column to `routine_exercises` table (stores SINGLE/DOUBLE as String)
- Migration 2→3: ALTER TABLE with default value 'DOUBLE' for backwards compatibility
- Updated RoutineExercise model with cable configuration field

**Repository Updates:**
- Mapping functions convert CableConfiguration enum ↔ String for database storage
- Cable config properly persisted and retrieved with routines

**Key Design Decisions:**
- Cable configuration is per exercise, not per set (no mid-exercise switching)
- Weight display uses "per cable" terminology (not "total weight")
- Single cable: one cable shows active load, other shows ~0kg
- Double cable: both cables show active loads (e.g., 20kg per cable = 40kg total)

**Build Status:** ✅ SUCCESSFUL

### Added - Feature 4 Phase 3: Routine Creation Dialog (2025-01-29)

**Complete Routine Builder Implementation:**

**New Files Created:**
- `app/src/main/java/com/example/vitruvianredux/presentation/screen/RoutineBuilderDialog.kt` (19KB)
  - Main dialog for creating/editing workout routines
  - Exercise list management with add/remove/reorder
  - Form validation (name required, minimum 1 exercise)
  - Uses immutable list operations with Compose state

- `app/src/main/java/com/example/vitruvianredux/presentation/screen/ExercisePickerDialog.kt` (7KB)
  - Exercise selection dialog with category grouping
  - All 20 exercises from Exercise enum
  - Search/filter functionality
  - Material 3 design patterns

- `app/src/main/java/com/example/vitruvianredux/presentation/screen/ExerciseEditDialog.kt` (12KB)
  - Exercise parameter configuration
  - Inputs: sets, reps, weight/cable, progression, rest time, notes
  - Default values and validation
  - Scrollable form layout

**Files Modified:**
- `app/src/main/java/com/example/vitruvianredux/presentation/screen/RoutinesTab.kt`
  - Added dialog state management
  - Integrated RoutineBuilderDialog on FAB click
  - Callback wiring for save/dismiss actions

- `app/src/main/java/com/example/vitruvianredux/presentation/screen/EnhancedMainScreen.kt`
  - Connected onSaveRoutine to viewModel.saveRoutine()
  - Proper routine creation with UUID generation
  - Exercise orderIndex assignment

**Complete Feature 4 Workflow:**
1. User clicks FAB in Routines tab
2. RoutineBuilderDialog opens
3. User enters routine name and description
4. User adds exercises via ExercisePickerDialog
5. User configures each exercise via ExerciseEditDialog
6. User can reorder exercises with up/down buttons
7. User saves routine to database
8. Routine appears in RoutinesTab list
9. User can load routine into workout

**Status**: All 3 phases of Feature 4 complete ✅

### Fixed - Compilation Errors in RoutineBuilderDialog.kt (2025-01-29)

**Critical Fix - Resolved 71 Compilation Errors:**
- **Issue**: RoutineBuilderDialog.kt had 71 compilation errors due to mutable list operations with immutable state
- **Root Cause**: Line 33 attempted to store `MutableList` in `mutableStateOf`, which expects immutable types
- **Solution**: Replaced all mutable list operations with immutable List + reassignment pattern
  - Delete operation: `filterIndexed` + `mapIndexed` for reordering
  - Move up/down: `mapIndexed` with `when` expression for swapping
  - Edit/save: Conditional `mapIndexed` or concatenation (`+`)
- **Result**: BUILD SUCCESSFUL - 0 compilation errors

**Files Fixed:**
- `app/src/main/java/com/example/vitruvianredux/presentation/screen/RoutineBuilderDialog.kt`
  - Line 38: State declaration with immutable List
  - Lines 193-195: Delete operation
  - Lines 199-207: Move up operation
  - Lines 210-218: Move down operation
  - Lines 318-324: Edit/save operation
  - Lines 11-16: Icon imports (KeyboardArrowUp/Down)

**Technical Pattern:**
```kotlin
// ✅ CORRECT - Immutable list operations
exercises = exercises.filterIndexed { i, _ -> i != index }  // delete
exercises = exercises.mapIndexed { i, ex -> ... }          // update
exercises = exercises + newItem                            // append
```

### Added - Feature 4 Phase 2: Workout Routine Builder UI (2025-01-28)

**New Files Created:**
- `app/src/main/java/com/example/vitruvianredux/presentation/screen/RoutinesTab.kt` (235 lines)
  - RoutinesTab composable with routines list view
  - RoutineCard component displaying routine details
  - Empty state with helpful messages
  - Delete confirmation dialog
  - FloatingActionButton for creating new routines
  - StatItem helper for displaying statistics
  - formatDate utility function

**Files Modified:**
- `app/src/main/java/com/example/vitruvianredux/presentation/screen/EnhancedMainScreen.kt`
  - Added 4th navigation bar item for "Routines" with FavoriteBorder icon (line 133)
  - Added RoutinesTab case in when statement with callback wiring (line 200)
  - Collect routines state from viewModel
  - Wire up onLoadRoutine, onDeleteRoutine, onCreateRoutine callbacks

**Implementation Details:**
- Dark theme with CardBackground (#252525) cards
- Purple accents (PrimaryPurple #BB86FC) for buttons
- Rounded corners (16dp) matching existing screens
- Material 3 design language throughout
- Empty state with intuitive icon and helper text
- Delete confirmation dialogs to prevent accidents
- Each routine card shows: name, description, exercise count, created date, use count
- Load button to start workout with routine
- Delete button with confirmation

**Integration:**
- New "Routines" navigation tab (bottom navigation position 2)
- Settings tab moved to position 3
- Backend already complete from Feature 4 Phase 1:
  - routines StateFlow in MainViewModel
  - loadRoutine(), deleteRoutine() methods
  - WorkoutRepository CRUD operations

**Next Steps (Phase 3):**
- Implement routine creation dialog (onCreateRoutine currently has TODO placeholder)
- Add routine editing functionality
- Add exercise selection UI
- Add exercise reordering

### Added - Feature 4 Phase 1: Routine Builder Database Schema (2025-01-27)

**Backend Implementation:**
- Exercise enum with 20 exercises across 6 categories
- RoutineEntity and RoutineExerciseEntity with Room annotations
- Database migration 1→2 adding routines and routine_exercises tables
- WorkoutDao extended with routine CRUD methods
- WorkoutRepository extended with routine operations
- Domain models: Routine and RoutineExercise
- MainViewModel extended with routine state management

**Database Changes:**
- Added `routines` table with id, name, description, createdAt, lastUsed, useCount
- Added `routine_exercises` table with foreign key CASCADE delete
- Index on routineId for performance

### Added - Features 2 & 3: KG/LBS Toggle and Start New Workout (2025-01-26)

**Feature 2: Weight Unit Toggle**
- WeightUnit enum (KG, LB) added to Models.kt
- PreferencesManager with DataStore for persisting unit preference
- MainViewModel weight conversion helpers (kgToDisplay, displayToKg, formatWeight)
- Settings tab UI with FilterChips for unit selection
- All weight displays updated to show converted values

**Feature 3: Start New Workout**
- resetForNewWorkout() method in MainViewModel
- "Start New Workout" button appears after workout completion
- Allows starting fresh workout without disconnecting from device

### Added - Feature 1: Pop-up/Dialog Windows (2025-01-25)

**Workout Setup Dialog:**
- Converted WorkoutParametersCard from embedded card to modal dialog
- WorkoutSetupDialog composable with AlertDialog wrapper
- Dialog state management in MainViewModel
- "Setup Workout" button triggers dialog in Idle state
- Prevents keyboard from obscuring inputs on small screens

### Added - Initial Dark Purple Theme (2025-01-24)

**UI Theme Redesign:**
- Dark theme with purple accents
- Color palette: PrimaryPurple (#BB86FC), CardBackground (#252525), SurfaceDarkGrey (#1E1E1E)
- Spacing object for consistent padding
- Material 3 design patterns
- Updated all screens: WorkoutTab, HistoryTab, SettingsTab, CountdownCard

### Added - Core BLE Functionality (2025-01-23)

**Initial Project Setup:**
- BLE scanning and connection management
- Workout state machine (Idle, InProgress, Completed)
- Rep counting from machine data
- Auto-stop functionality
- Workout metrics tracking
- Room database for workout history
- Notifications system
