# UI Differences: Beta 5.1 vs Current Build (Comprehensive)

This document provides granular code-level differences for ALL UI changes between v0.5.1-beta and the current build.

---

## Table of Contents
1. [New UI Components](#1-new-ui-components)
2. [Global Navigation Architecture - Dynamic TopBar System](#2-global-navigation-architecture---dynamic-topbar-system)
3. [Redundant Screen Title Removal](#3-redundant-screen-title-removal)
4. [SingleExerciseScreen - Redundant Screen Removal](#4-singleexercisescreen---redundant-screen-removal)
5. [Vertical Space Preservation via Collapsibility](#5-vertical-space-preservation-via-collapsibility)
6. [Weight Change Per Rep - Horizontal Slider Transformation](#6-weight-change-per-rep---horizontal-slider-transformation)
7. [Exercise Configuration Buttons - Improved Touch Targets](#7-exercise-configuration-buttons---improved-touch-targets)
8. [HomeScreen Changes](#8-homescreen-changes)
9. [JustLiftScreen Changes](#9-justliftscreen-changes)
10. [SingleExerciseScreen Changes (Additional Details)](#10-singleexercisescreen-changes-additional-details)
11. [ActiveWorkoutScreen Changes](#11-activeworkoutscreen-changes)
12. [WorkoutTab Changes](#12-workouttab-changes)
13. [RoutinesTab Changes](#13-routinestab-changes)
14. [WeeklyProgramsScreen Changes](#14-weeklyprogramsscreen-changes)
15. [ProgramBuilderScreen Changes](#15-programbuilderscreen-changes)
16. [InsightsTab Complete Overhaul](#16-insightstab-complete-overhaul)
17. [ExerciseEditDialog Full Breakdown](#17-exerciseeditdialog-full-breakdown)
18. [ExercisePickerDialog Refactoring](#18-exercisepickerdialog-refactoring)
19. [HistoryAndSettingsTabs Changes](#19-historyandsettingstabs-changes)
20. [ConnectionLogsScreen Changes](#20-connectionlogsscreen-changes)
21. [Chart Component Updates](#21-chart-component-updates)

---

## 1. New UI Components

### 1.1 ExpressiveComponents.kt (NEW FILE)

**Purpose:** Reusable Material 3 Expressive UI components with consistent spring animations.

```kotlin
// NEW FILE: presentation/components/ExpressiveComponents.kt

/**
 * ExpressiveCard - A card with spring animation on press
 */
@Composable
fun ExpressiveCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = RoundedCornerShape(20.dp),  // Larger corners
    colors: CardColors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest),
    elevation: CardElevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
    border: BorderStroke? = BorderStroke(2.dp, MaterialTheme.colorScheme.outlineVariant),
    ...
) {
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessLow
        )
    )
    Card(onClick, modifier.scale(scale), ...)
}

/**
 * ExpressiveSlider - Standard slider with Material 3 Expressive styling
 */
@Composable
fun ExpressiveSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int = 0,
    trackColor: Color = MaterialTheme.colorScheme.onSurface,
    thumbColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Slider(value, onValueChange, valueRange = valueRange, steps = steps,
        colors = SliderDefaults.colors(
            thumbColor = thumbColor,
            activeTrackColor = trackColor,
            inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest
        )
    )
}

/**
 * ProgressionSlider - Specialized slider for -10 to +10 values
 * Changes color based on value (red for negative, neutral for positive)
 */
@Composable
fun ProgressionSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float> = -10f..10f,
    ...
) {
    val activeColor = when {
        value < 0 -> MaterialTheme.colorScheme.error      // Red for regression
        value > 0 -> MaterialTheme.colorScheme.onSurface  // Normal for progression
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Column {
        Row(horizontalArrangement = Arrangement.SpaceBetween) {
            Text("${valueRange.start.toInt()}")
            Text(if (value > 0) "+${value.toInt()}" else "${value.toInt()}", fontWeight = FontWeight.Bold, color = activeColor)
            Text("+${valueRange.endInclusive.toInt()}")
        }
        ExpressiveSlider(value, onValueChange, valueRange, trackColor = activeColor, thumbColor = activeColor)
    }
}
```

**Why this was added:** Provides consistent Material 3 Expressive styling across the app. The spring animations give an organic, responsive feel. ProgressionSlider provides intuitive visual feedback for weight progression/regression settings.

---

### 1.2 SafetyEventsCard.kt (NEW FILE)

**Purpose:** Displays safety-related events (deload warnings, ROM violations, spotter activations) during workouts.

```kotlin
@Composable
fun SafetyEventsCard(summary: SafetyEventSummary, modifier: Modifier = Modifier) {
    if (!summary.hasSafetyEvents) return

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
        )
    ) {
        Column(...) {
            Row(...) {
                Icon(Icons.Default.Warning, tint = MaterialTheme.colorScheme.error)
                Text("Safety Events", color = MaterialTheme.colorScheme.error)
            }
            // Color-coded event rows:
            // - Deload Warnings: Orange (0xFFFF9800)
            // - ROM Violations: Red (0xFFF44336)
            // - Spotter Activations: Blue (0xFF2196F3)
        }
    }
}
```

---

## 2. Global Navigation Architecture - Dynamic TopBar System

This is one of the **most significant architectural changes** in the entire UI update. The app moved from per-screen local TopBars to a centralized, dynamic TopBar managed by the MainViewModel.

### 2.1 The Problem with Beta 5.1 Approach

In Beta 5.1, **every screen** had its own local `Scaffold` with `TopAppBar`:
- **JustLiftScreen**: Had `TopAppBar(title = { Text("Just Lift") }, navigationIcon = { IconButton { Icon(ArrowBack) } })`
- **ActiveWorkoutScreen**: Had `TopAppBar(title = { Column { Text(screenTitle); Text("Exercise $x of $y") } }, navigationIcon = { ... })`
- **SingleExerciseScreen**: Had `TopAppBar(title = { Text("Single Exercise") }, navigationIcon = { ... })`
- **WeeklyProgramsScreen**: Had `TopAppBar(title = { Text("Weekly Programs") }, ...)`
- **ProgramBuilderScreen**: Had `TopAppBar(title = { Text(programName) }, actions = { Save button })`
- **ConnectionLogsScreen**: Had `TopAppBar(title = { Text("Connection Logs") }, actions = { Share, Delete })`

**Problems:**
1. Redundant navigation icon implementation on each screen
2. Inconsistent back handling behavior
3. No way for screens to inject custom actions into a unified header
4. Visual inconsistency as each screen styled its TopAppBar slightly differently
5. Wasted vertical space from repeated TopBar structures

### 2.2 The Solution: ViewModel-Managed Dynamic TopBar

**Key Files Changed:**
- `MainViewModel.kt` - Added TopBar state management
- `EnhancedMainScreen.kt` - Single global TopBar implementation
- All screen files - Removed local Scaffolds, now use `viewModel.updateTopBarTitle()`

### 2.3 New ViewModel State (MainViewModel.kt)

```kotlin
// NEW: TopBar state management
data class TopBarAction(
    val icon: ImageVector,
    val description: String,
    val onClick: () -> Unit
)

// In MainViewModel:
private val _topBarTitle = MutableStateFlow("Vitruvian Project Phoenix")
val topBarTitle: StateFlow<String> = _topBarTitle.asStateFlow()

private val _topBarActions = MutableStateFlow<List<TopBarAction>>(emptyList())
val topBarActions: StateFlow<List<TopBarAction>> = _topBarActions.asStateFlow()

private val _topBarBackAction = MutableStateFlow<(() -> Unit)?>(null)
val topBarBackAction: StateFlow<(() -> Unit)?> = _topBarBackAction.asStateFlow()

fun updateTopBarTitle(title: String) { _topBarTitle.value = title }
fun setTopBarActions(actions: List<TopBarAction>) { _topBarActions.value = actions }
fun clearTopBarActions() { _topBarActions.value = emptyList() }
fun setTopBarBackAction(action: () -> Unit) { _topBarBackAction.value = action }
fun clearTopBarBackAction() { _topBarBackAction.value = null }
```

### 2.2 EnhancedMainScreen TopBar Overhaul

**BEFORE (Beta 5.1):** Static branded title, custom BottomAppBar with FAB
```kotlin
TopAppBar(
    title = {
        Column {
            Text("Vitruvian", style = ..., brush = Brush.linearGradient(gold colors))
            Text("Project Phoenix", style = ..., brush = Brush.linearGradient(orange colors))
        }
    }
    // No navigation icon
)

// Custom BottomAppBar with FloatingActionButton
Surface { Column {
    BottomAppBar { Row {
        // Analytics (small)
        Column { IconButton { Icon(Icons.Outlined.BarChart) }; Text("Analytics") }
        // Workouts (large FAB)
        FloatingActionButton(modifier = Modifier.size(64.dp)) {
            Column { Icon(Icons.Outlined.Home); Text("Workouts") }
        }
        // Settings (small)
        Column { IconButton { Icon(Icons.Outlined.Settings) }; Text("Settings") }
    }}
}}
```

**AFTER (Current):** Dynamic title, conditional back button, standard NavigationBar
```kotlin
val topBarTitle by viewModel.topBarTitle.collectAsState()
val topBarActions by viewModel.topBarActions.collectAsState()
val topBarBackAction by viewModel.topBarBackAction.collectAsState()

val showBackButton = currentRoute != NavigationRoutes.Home.route &&
                     currentRoute != NavigationRoutes.Analytics.route &&
                     currentRoute != NavigationRoutes.Settings.route

TopAppBar(
    title = {
        Column {
            Text(topBarTitle ?: getScreenTitle(currentRoute), style = titleMedium, fontWeight = Bold)
            Text("Vitruvian Project Phoenix", style = labelSmall, brush = gradient)
        }
    },
    navigationIcon = {
        if (showBackButton) {
            IconButton(onClick = { topBarBackAction?.invoke() ?: navController.navigateUp() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack)
            }
        }
    },
    actions = {
        topBarActions.forEach { action ->
            IconButton(onClick = action.onClick) {
                Icon(action.icon, contentDescription = action.description)
            }
        }
        // BLE status indicator
    }
)

// Standard Material 3 NavigationBar (replaces custom BottomAppBar)
NavigationBar(containerColor = if (isDarkMode) Color(0xFF1C1B1F) else Color(0xFFF3F3F3)) {
    NavigationBarItem(icon = { Icon(Icons.Default.BarChart) }, label = { Text("Analytics") }, ...)
    NavigationBarItem(icon = { Icon(Icons.Default.Home) }, label = { Text("Workouts") }, ...)
    NavigationBarItem(icon = { Icon(Icons.Default.Settings) }, label = { Text("Settings") }, ...)
}
```

**Why this changed:**
1. Dynamic titles give contextual feedback about current screen
2. Conditional back button only shows when navigation makes sense
3. Screens can inject custom actions (e.g., Save button in ProgramBuilder)
4. Standard NavigationBar follows Material 3 guidelines vs custom FAB-centric design
5. BottomBar only shows on main tabs, hidden during workout screens

---

## 3. Redundant Screen Title Removal

### 3.1 Overview

In Beta 5.1, each screen had **BOTH** a local TopAppBar title AND often an additional text header within the screen content. The current build removes this redundancy by using only the global TopBar.

### 3.2 Screens with Removed Redundant Titles

#### JustLiftScreen
**BEFORE (Beta 5.1):**
```kotlin
Scaffold(
    topBar = {
        TopAppBar(
            title = { Text("Just Lift") },  // <-- REMOVED
            navigationIcon = {
                IconButton(onClick = { navController.navigateUp() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(...)
        )
    }
) { padding -> ... }
```

**AFTER (Current):**
```kotlin
// Set global title - NO local TopAppBar
LaunchedEffect(Unit) {
    viewModel.updateTopBarTitle("Just Lift")
}

Scaffold(
    // No local topBar needed - uses EnhancedMainScreen's global TopBar
) { padding -> ... }
```

#### ActiveWorkoutScreen
**BEFORE:**
```kotlin
Scaffold(
    topBar = {
        TopAppBar(
            title = {
                Column {
                    Text(screenTitle)  // <-- Was redundant local title
                    loadedRoutine?.let { routine ->
                        val totalExercises = routine.exercises.size
                        val currentExerciseNum = currentExerciseIndex + 1
                        if (totalExercises > 1) {
                            Text(
                                text = "Exercise $currentExerciseNum of $totalExercises",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            },
            navigationIcon = { IconButton(onClick = { ... }) { Icon(ArrowBack) } }
        )
    }
) { padding ->
    WorkoutTab(modifier = Modifier.padding(padding), ...)
}
```

**AFTER:**
```kotlin
// Dynamic title updates as exercise changes
LaunchedEffect(screenTitle) {
    viewModel.updateTopBarTitle(screenTitle)
}

// Back action with confirmation for active workouts
LaunchedEffect(Unit) {
    val onBack: () -> Unit = {
        if (workoutState is WorkoutState.Active || ...) {
            showExitConfirmation = true
        } else {
            navController.navigateUp()
        }
    }
    viewModel.setTopBarBackAction(onBack)
}

// NO Scaffold - WorkoutTab directly
WorkoutTab(...)  // No padding from removed Scaffold
```

#### SingleExerciseScreen
**BEFORE:**
```kotlin
Scaffold(
    topBar = {
        TopAppBar(
            title = { Text("Single Exercise") },  // <-- REMOVED
            navigationIcon = { IconButton { Icon(ArrowBack) } },
            colors = TopAppBarDefaults.topAppBarColors(...)
        )
    }
) { padding -> ... }
```

**AFTER:**
```kotlin
LaunchedEffect(Unit) {
    viewModel.updateTopBarTitle("Single Exercise")
}

Scaffold(
    // No local topBar needed
) { padding -> ... }
```

### 3.3 Vertical Space Savings

By removing local TopAppBars, each screen gains approximately **56dp** of vertical space (standard TopAppBar height). This is significant for:
- Mobile devices with limited screen real estate
- Exercise configuration dialogs where every pixel matters
- Workout screens where the focus should be on metrics, not chrome

---

## 4. SingleExerciseScreen - Redundant Screen Removal

### 4.1 The Problem

In Beta 5.1, SingleExerciseScreen had **THREE possible states**:
1. Exercise picker dialog (fullscreen)
2. Exercise configuration bottom sheet
3. **EMPTY STATE** (when picker was dismissed without selection)

The empty state was completely redundant - it just showed a placeholder screen with a "Select Exercise" button that reopened the picker.

### 4.2 Beta 5.1 Implementation (Redundant Empty State)

```kotlin
var showExercisePicker by remember { mutableStateOf(true) }  // Start with picker shown
var exerciseToConfig by remember { mutableStateOf<RoutineExercise?>(null) }

Scaffold(topBar = { TopAppBar(title = { Text("Single Exercise") }, ...) }) { padding ->
    Box(modifier = Modifier.padding(padding)) {
        // State 1: Picker Dialog
        if (showExercisePicker) {
            ExercisePickerDialog(
                fullScreen = true,
                onDismiss = { showExercisePicker = false },  // <-- Allows dismissing to empty state
                onExerciseSelected = { selectedExercise ->
                    exerciseToConfig = createRoutineExercise(selectedExercise)
                    showExercisePicker = false
                },
                ...
            )
        }

        // State 2: Configuration Sheet
        exerciseToConfig?.let {
            ExerciseEditBottomSheet(
                exercise = it,
                onSave = { ... },
                onDismiss = {
                    exerciseToConfig = null
                    showExercisePicker = true  // Go back to picker
                }
            )
        }

        // State 3: REDUNDANT EMPTY STATE
        if (!showExercisePicker && exerciseToConfig == null) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.FitnessCenter,
                    contentDescription = "Exercise icon",
                    modifier = Modifier.size(80.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(Spacing.large))
                Text(
                    "Choose an exercise to begin",
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(Spacing.medium))
                Button(
                    onClick = { showExercisePicker = true },  // Just reopens the picker!
                    modifier = Modifier.fillMaxWidth(0.8f).height(56.dp),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Icon(Icons.Default.Search)
                    Spacer(Modifier.width(Spacing.small))
                    Text("Select Exercise", style = MaterialTheme.typography.titleLarge)
                }
            }
        }
    }
}
```

### 4.3 Current Implementation (No Empty State)

```kotlin
// Local state for picker (managed directly, no dialog state needed)
var searchQuery by remember { mutableStateOf("") }
var selectedMuscleFilter by remember { mutableStateOf("All") }
var selectedEquipmentFilter by remember { mutableStateOf("All") }
var showFavoritesOnly by remember { mutableStateOf(false) }
var exerciseToConfig by remember { mutableStateOf<RoutineExercise?>(null) }

// Exercises loaded directly (no dialog managing this)
val allExercises by remember(searchQuery, selectedMuscleFilter, showFavoritesOnly) {
    when {
        showFavoritesOnly -> exerciseRepository.getFavorites()
        searchQuery.isNotBlank() -> exerciseRepository.searchExercises(searchQuery)
        selectedMuscleFilter != "All" -> exerciseRepository.filterByMuscleGroup(selectedMuscleFilter)
        else -> exerciseRepository.getAllExercises()
    }
}.collectAsState(initial = emptyList())

LaunchedEffect(Unit) {
    viewModel.updateTopBarTitle("Single Exercise")
}

Scaffold { padding ->
    Box(modifier = Modifier.padding(padding)) {
        // ALWAYS show the picker content as the background - NO EMPTY STATE POSSIBLE
        ExercisePickerContent(
            exercises = exercises,
            searchQuery = searchQuery,
            onSearchQueryChange = { searchQuery = it },
            showFavoritesOnly = showFavoritesOnly,
            onShowFavoritesOnlyChange = { ... },
            selectedMuscleFilter = selectedMuscleFilter,
            onMuscleFilterChange = { selectedMuscleFilter = it },
            selectedEquipmentFilter = selectedEquipmentFilter,
            onEquipmentFilterChange = { selectedEquipmentFilter = it },
            onExerciseSelected = { selectedExercise ->
                exerciseToConfig = createRoutineExercise(selectedExercise)
                // NO showExercisePicker state to toggle - picker stays visible
            },
            exerciseRepository = exerciseRepository,
            enableVideoPlayback = enableVideoPlayback,
            fullScreen = true
        )

        // Show bottom sheet as OVERLAY when exercise selected
        exerciseToConfig?.let {
            ExerciseEditBottomSheet(
                exercise = it,
                onSave = { configuredExercise -> ... },
                onDismiss = {
                    exerciseToConfig = null
                    // Picker is ALWAYS visible - no navigation needed
                }
            )
        }
    }
}
```

### 4.4 Benefits

1. **Eliminates unnecessary UI state** - Only 2 states instead of 3
2. **Faster workflow** - User never sees a useless placeholder screen
3. **Simpler code** - No `showExercisePicker` state variable to manage
4. **Better UX** - Dismissing config sheet returns directly to browse/search
5. **Reduced cognitive load** - One less screen for users to understand

---

## 5. Vertical Space Preservation via Collapsibility

### 5.1 Overview

The current build adds collapsibility and conditional visibility to multiple UI components to maximize vertical space for content.

### 5.2 AutoStartStopCard - Conditional Display

**BEFORE (Always Visible):**
```kotlin
// JustLiftScreen - always showed AutoStartStopCard regardless of workout state
val autoStartCountdown by viewModel.autoStartCountdown.collectAsState()
AutoStartStopCard(
    workoutState = workoutState,
    autoStartCountdown = autoStartCountdown,
    autoStopState = autoStopState
)
```

**AFTER (Only When Idle):**
```kotlin
// Only show Auto-Start/Stop Card when IDLE
// When Active, status is displayed in ActiveStatusCard instead
if (workoutState is WorkoutState.Idle) {
    val autoStartCountdown by viewModel.autoStartCountdown.collectAsState()
    AutoStartStopCard(
        workoutState = workoutState,
        autoStartCountdown = autoStartCountdown,
        autoStopState = autoStopState
    )
}
```

**Why:** During active workout, the AutoStartStopCard's information is redundant with ActiveStatusCard. Hiding it saves ~100dp of vertical space.

### 5.3 RoutineCard - Expandable Instead of Always Expanded

**BEFORE:** All routine details always visible
```kotlin
Card(onClick = onStartWorkout) {
    Row {
        // Icon, content (name, muscle groups, exercise count), arrow
        // Actions menu always in header
    }
}
```

**AFTER:** Details only shown when expanded
```kotlin
var expanded by remember { mutableStateOf(false) }

Card(onClick = { expanded = !expanded }) {
    Column {
        Row { /* Compact header with expand icon */ }

        AnimatedVisibility(visible = expanded) {
            Column {
                HorizontalDivider()
                // Exercise list with set details
                // Action buttons (Start, Edit, Copy, Delete)
            }
        }
    }
}
```

**Space Savings:** Each collapsed routine card takes ~80dp instead of ~200dp+ when expanded

### 5.4 ProgramListItem - Expandable Schedule Preview

**BEFORE:** No schedule preview, click navigates away
**AFTER:** Schedule shown only when expanded

```kotlin
var expanded by remember { mutableStateOf(false) }

Card(modifier = Modifier.clickable { expanded = !expanded }) {
    Column {
        Row { /* Header with title, day count, expand icon */ }

        AnimatedVisibility(visible = expanded) {
            Column {
                HorizontalDivider()
                Text("Schedule", style = titleSmall)
                program.days.sortedBy { it.dayOfWeek }.forEach { day ->
                    Row {
                        Text(dayName, fontWeight = Bold)
                        Text(routineName)
                    }
                }
                OutlinedButton(onClick = { showDeleteDialog = true }) {
                    Text("Delete Program")
                }
            }
        }
    }
}
```

### 5.5 ExercisePickerContent - Header Conditional

**BEFORE:** ExercisePickerDialog always showed "Select Exercise" header
```kotlin
// Inside PickerContent()
if (!fullScreen) {
    Text("Select Exercise", style = headlineMedium, fontWeight = Bold)
}
```

**AFTER:** ExercisePickerContent has `fullScreen` parameter controlling header visibility
```kotlin
@Composable
fun ExercisePickerContent(
    ...
    fullScreen: Boolean = false  // When true, no local header (uses global TopBar)
)
```

---

## 6. Weight Change Per Rep - Horizontal Slider Transformation

### 6.1 The Original CompactNumberPicker

Beta 5.1 used a native Android `NumberPicker` widget wrapped in `AndroidView`:

```kotlin
// CompactNumberPicker.kt - Beta 5.1
@Composable
fun CompactNumberPicker(
    value: Float,
    onValueChange: (Float) -> Unit,
    range: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
    label: String = "",
    suffix: String = "",
    step: Float = 1.0f
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        if (label.isNotEmpty()) {
            Text(label, style = labelMedium, fontWeight = Bold)
        }

        Row(horizontalArrangement = Arrangement.Center, verticalAlignment = CenterVertically) {
            // Decrease button
            IconButton(onClick = { /* decrement */ }, enabled = currentIndex > 0) {
                Icon(Icons.Default.Remove, contentDescription = "Decrease $label")
            }

            // Native Android NumberPicker (wheel-style)
            AndroidView(
                factory = { context ->
                    NumberPicker(context).apply {
                        minValue = 0
                        maxValue = values.size - 1
                        displayedValues = formattedValues
                        wrapSelectorWheel = false
                        setOnValueChangedListener { _, _, newIndex -> onValueChange(values[newIndex]) }
                    }
                },
                modifier = Modifier.weight(1f).height(120.dp)  // Takes 120dp vertical space!
            )

            // Increase button
            IconButton(onClick = { /* increment */ }, enabled = currentIndex < values.size - 1) {
                Icon(Icons.Default.Add, contentDescription = "Increase $label")
            }
        }
    }
}
```

**Problems:**
1. **120dp height** for a simple value selector
2. **Non-native feel** - NumberPicker looks out of place in Compose UI
3. **Poor bipolar visualization** - No visual feedback for negative vs positive values
4. **Platform inconsistency** - Different behavior on different Android versions

### 6.2 The New ProgressionSlider

```kotlin
// ExpressiveComponents.kt - Current
@Composable
fun ProgressionSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float> = -10f..10f,
    modifier: Modifier = Modifier
) {
    // Color changes based on value direction
    val activeColor = when {
        value < 0 -> MaterialTheme.colorScheme.error      // RED for regression
        value > 0 -> MaterialTheme.colorScheme.onSurface  // Normal for progression
        else -> MaterialTheme.colorScheme.onSurfaceVariant // Neutral for zero
    }

    Column(modifier = modifier.fillMaxWidth()) {
        // Value indicators row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "${valueRange.start.toInt()}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = if (value > 0) "+${value.toInt()}" else "${value.toInt()}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = activeColor  // Dynamic color!
            )
            Text(
                text = "+${valueRange.endInclusive.toInt()}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // The slider itself
        ExpressiveSlider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            trackColor = activeColor,
            thumbColor = activeColor
        )
    }
}
```

### 6.3 Usage Comparison

#### ExerciseEditDialog

**BEFORE:**
```kotlin
Surface(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(12.dp),
    color = MaterialTheme.colorScheme.surface,
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant),
    shadowElevation = 2.dp
) {
    Column(modifier = Modifier.padding(Spacing.small)) {
        com.example.vitruvianredux.presentation.components.CompactNumberPicker(
            value = weightChange,
            onValueChange = viewModel::onWeightChange,
            range = -maxWeightChange..maxWeightChange,
            label = "Weight Change Per Rep",
            suffix = weightSuffix,
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            "Negative = Regression, Positive = Progression",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline,
            modifier = Modifier.padding(top = Spacing.extraSmall)
        )
    }
}
```

**AFTER:**
```kotlin
ExpressiveCard(
    onClick = {},
    enabled = false,
    modifier = Modifier.fillMaxWidth(),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest),
    border = BorderStroke(2.dp, MaterialTheme.colorScheme.outlineVariant)
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(Spacing.medium)
    ) {
        Text(
            "Weight Change Per Rep",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(Spacing.medium))

        ProgressionSlider(
            value = weightChange.toFloat(),
            onValueChange = { viewModel.onWeightChange(it.toInt()) },
            valueRange = -maxWeightChange.toFloat()..maxWeightChange.toFloat(),
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            "Negative = Regression, Positive = Progression",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Spacing.small)
        )
    }
}
```

### 6.4 Benefits

| Aspect | CompactNumberPicker | ProgressionSlider |
|--------|---------------------|-------------------|
| **Height** | ~150dp (label + 120dp picker) | ~60dp (compact slider) |
| **Visual Feedback** | None | Red/neutral color coding |
| **Touch Target** | Small +/- buttons | Full-width slider track |
| **Compose Native** | No (AndroidView wrapper) | Yes (pure Compose) |
| **Bipolar Intuition** | Numbers only | Color + position |

---

## 7. Exercise Configuration Buttons - Improved Touch Targets

### 7.1 Overview

The current build significantly improves touch targets and visual hierarchy in exercise configuration dialogs.

### 7.2 Echo Level Selection

**BEFORE (FilterChips):**
```kotlin
Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(8.dp)
) {
    EchoLevel.entries.forEach { echoLevel ->
        FilterChip(
            selected = level == echoLevel,
            onClick = { onLevelChange(echoLevel) },
            label = {
                Text(
                    echoLevel.displayName,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1
                )
            },
            modifier = Modifier.weight(1f)  // Shared width
        )
    }
}
```

**AFTER (SegmentedButtonRow):**
```kotlin
SingleChoiceSegmentedButtonRow(
    modifier = Modifier.fillMaxWidth()
) {
    val levels = EchoLevel.entries
    levels.forEachIndexed { index, echoLevel ->
        SegmentedButton(
            shape = SegmentedButtonDefaults.itemShape(index = index, count = levels.size),
            onClick = { onLevelChange(echoLevel) },
            selected = level == echoLevel
        ) {
            Text(echoLevel.displayName, maxLines = 1)
        }
    }
}
```

**Improvements:**
- SegmentedButton has minimum 48dp height (Material 3 touch target)
- Connected visual appearance makes selection clearer
- No ambiguous gaps between options

### 7.3 Rest Time Selection

**BEFORE (NumberPicker):**
```kotlin
com.example.vitruvianredux.presentation.components.CompactNumberPicker(
    value = rest,
    onValueChange = viewModel::onRestChange,
    range = 0..300,
    label = "Rest Time",
    suffix = "sec",
    modifier = Modifier.fillMaxWidth()
)
```

**AFTER (ExpressiveSlider):**
```kotlin
Text(
    "Rest Time: ${rest}s",
    style = MaterialTheme.typography.titleSmall,
    fontWeight = FontWeight.Bold,
    modifier = Modifier.padding(bottom = Spacing.extraSmall)
)
ExpressiveSlider(
    value = rest.toFloat(),
    onValueChange = { viewModel.onRestChange(it.toInt()) },
    valueRange = 0f..300f,
    steps = 59,  // 5-second increments
    modifier = Modifier.fillMaxWidth()
)
```

**Improvements:**
- Full-width slider track is easier to tap
- Current value displayed prominently above
- Steps provide tactile feedback at 5-second intervals

### 7.4 Eccentric Load Selection

**BEFORE:**
```kotlin
Column(modifier = Modifier.fillMaxWidth()) {
    Text("Eccentric Load", style = titleMedium, fontWeight = Bold)

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = surfaceContainerHighest,
        border = BorderStroke(2.dp, primary.copy(alpha = 0.2f)),
        shadowElevation = 8.dp
    ) {
        Column(modifier = Modifier.padding(Spacing.medium)) {
            Row(horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Eccentric Load: ${eccentricLoad.percentage}%", style = titleLarge, fontWeight = Bold)
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Standard Slider with no endpoint labels
            Slider(
                value = currentIndex.toFloat(),
                onValueChange = { value ->
                    val index = value.toInt().coerceIn(0, eccentricLoadValues.size - 1)
                    onLoadChange(eccentricLoadValues[index])
                },
                valueRange = 0f..(eccentricLoadValues.size - 1).toFloat(),
                steps = eccentricLoadValues.size - 2,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text("Percentage of concentric load applied during eccentric phase", style = bodySmall)
        }
    }
}
```

**AFTER:**
```kotlin
ExpressiveCard(
    onClick = {},
    enabled = false,
    modifier = Modifier.fillMaxWidth(),
    colors = CardDefaults.cardColors(containerColor = surfaceContainerHighest),
    border = BorderStroke(2.dp, outlineVariant)
) {
    Column(modifier = Modifier.fillMaxWidth().padding(Spacing.medium)) {
        Text(
            "Eccentric Load: ${eccentricLoad.percentage}%",
            style = titleMedium,
            fontWeight = Bold,
            color = onSurface
        )
        Spacer(modifier = Modifier.height(Spacing.medium))

        // NEW: Endpoint labels for clarity
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = "0%", style = labelSmall, color = onSurfaceVariant)
            Text(text = "150%", style = labelSmall, color = onSurfaceVariant)
        }

        ExpressiveSlider(
            value = currentIndex.toFloat(),
            onValueChange = { value ->
                val index = value.toInt().coerceIn(0, eccentricLoadValues.size - 1)
                onLoadChange(eccentricLoadValues[index])
            },
            valueRange = 0f..(eccentricLoadValues.size - 1).toFloat(),
            steps = eccentricLoadValues.size - 2,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(Spacing.small))

        Text(
            "Load percentage applied during eccentric (lowering) phase",
            style = bodySmall,
            color = onSurfaceVariant
        )
    }
}
```

**Improvements:**
- Added "0%" and "150%" endpoint labels for immediate understanding
- ExpressiveCard provides consistent styling with other controls
- Simplified description text ("lowering" clarifies "eccentric")

### 7.5 Summary of Touch Target Improvements

| Control | Beta 5.1 | Current | Improvement |
|---------|----------|---------|-------------|
| Mode Selection | FilterChip (~40dp height) | SegmentedButton (48dp min) | +20% touch area |
| Echo Level | FilterChip row | SegmentedButtonRow | Continuous touch surface |
| Weight Change | +/- buttons (48dp each) | Full-width slider | 400%+ touch area |
| Rest Time | NumberPicker scroll | Slider track | Full-width target |
| Eccentric Load | Slider only | Slider + labels | Better orientation |

---

## 8. HomeScreen Changes

### 8.1 Layout: Column → LazyVerticalGrid

**BEFORE:**
```kotlin
Column(
    modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
    verticalArrangement = Arrangement.spacedBy(18.dp)
) {
    Text("Start a workout", style = headlineSmall, fontWeight = Bold)
    activeProgram?.let { HomeActiveProgramCard(...) }
    WorkoutCard(title = "Just Lift", ...)
    WorkoutCard(title = "Single Exercise", ...)
    WorkoutCard(title = "Daily Routines", ...)
    WorkoutCard(title = "Weekly Programs", ...)
}
```

**AFTER:**
```kotlin
val configuration = LocalConfiguration.current
val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
val gridColumns = if (isLandscape) 4 else 2

LaunchedEffect(Unit) { viewModel.updateTopBarTitle("") }  // Clear title to show branding

LazyVerticalGrid(
    columns = GridCells.Fixed(gridColumns),
    modifier = Modifier.fillMaxSize(),
    contentPadding = PaddingValues(20.dp),
    verticalArrangement = Arrangement.spacedBy(18.dp),
    horizontalArrangement = Arrangement.spacedBy(18.dp)
) {
    if (activeProgram != null) {
        item(span = { GridItemSpan(maxLineSpan) }) {  // Full-width active program card
            HomeActiveProgramCard(...)
        }
    }
    item { WorkoutCard(title = "Just Lift", ...) }
    item { WorkoutCard(title = "Single Exercise", ...) }
    item { WorkoutCard(title = "Daily Routines", ...) }
    item { WorkoutCard(title = "Weekly Programs", ...) }
}
```

**Why:** Grid layout utilizes screen space better, especially in landscape. 4 columns in landscape, 2 in portrait.

### 8.2 WorkoutCard Layout: Horizontal → Vertical

**BEFORE:**
```kotlin
Card(onClick, ...) {
    Row(modifier = Modifier.padding(20.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Box { Icon(...) }  // Gradient icon box
        Column(Modifier.weight(1f)) {
            Text(title, style = titleLarge)
            Text(description, style = bodyMedium)
        }
        Surface(shape = RoundedCornerShape(50)) {
            Icon(Icons.AutoMirrored.Filled.ArrowForward)  // Arrow icon
        }
    }
}
```

**AFTER:**
```kotlin
Card(onClick, ...) {
    Column(
        modifier = Modifier.padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box { Icon(...) }  // Gradient icon box (centered)
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, style = titleMedium)  // Smaller for grid
            Text(description, style = bodySmall, textAlign = TextAlign.Center)
        }
        // Arrow removed - card click is self-evident
    }
}
```

**Why:** Vertical layout works better in grid. Arrow removed to reduce visual clutter.

---

## 9. JustLiftScreen Changes

### 4.1 Mode Selection: FilterChips → SegmentedButtonRow

**BEFORE:**
```kotlin
Row(horizontalArrangement = Arrangement.spacedBy(Spacing.small)) {
    FilterChip(selected = selectedMode is WorkoutMode.OldSchool, onClick = { selectedMode = WorkoutMode.OldSchool },
               label = { Text("Old School") }, leadingIcon = if (...) { Icon(Icons.Default.Check) } else null)
    FilterChip(selected = selectedMode is WorkoutMode.Pump, onClick = { selectedMode = WorkoutMode.Pump },
               label = { Text("Pump") }, leadingIcon = if (...) { Icon(Icons.Default.Check) } else null)
    FilterChip(selected = selectedMode is WorkoutMode.Echo, onClick = { selectedMode = WorkoutMode.Echo(echoLevel) },
               label = { Text("Echo") }, leadingIcon = if (...) { Icon(Icons.Default.Check) } else null)
}
```

**AFTER:**
```kotlin
SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
    val modes = listOf(
        Triple("Old School", WorkoutMode.OldSchool, 0),
        Triple("Pump", WorkoutMode.Pump, 1),
        Triple("Echo", WorkoutMode.Echo(echoLevel), 2)
    )
    modes.forEachIndexed { index, (label, mode, _) ->
        SegmentedButton(
            shape = SegmentedButtonDefaults.itemShape(index = index, count = modes.size),
            onClick = { selectedMode = mode },
            selected = selectedMode::class == mode::class,
            icon = {}
        ) { Text(label, maxLines = 1) }
    }
}
```

**Why:** SegmentedButtonRow is standard Material 3 component for exclusive selection. Cleaner visual appearance and consistent behavior.

### 4.2 Cards: Card → ExpressiveCard

**BEFORE:**
```kotlin
Card(
    onClick = { isModePressed = true },
    modifier = Modifier.fillMaxWidth().scale(modeScale),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest),
    shape = RoundedCornerShape(20.dp),
    elevation = CardDefaults.cardElevation(defaultElevation = if (isModePressed) 8.dp else 12.dp),
    border = BorderStroke(2.dp, MaterialTheme.colorScheme.outlineVariant)
) { ... }
```

**AFTER:**
```kotlin
ExpressiveCard(
    onClick = { isModePressed = true },
    modifier = Modifier.fillMaxWidth(),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest),
    elevation = CardDefaults.cardElevation(defaultElevation = if (isModePressed) 8.dp else 12.dp),
    border = BorderStroke(2.dp, MaterialTheme.colorScheme.outlineVariant)
) { ... }
```

**Why:** ExpressiveCard encapsulates spring animation logic, reducing boilerplate.

### 4.3 Weight Change: CompactNumberPicker → ProgressionSlider

**BEFORE:**
```kotlin
CompactNumberPicker(
    value = weightChangePerRep,
    onValueChange = { weightChangePerRep = it },
    range = -maxWeightChange..maxWeightChange,
    label = "Weight Change Per Rep",
    suffix = weightSuffix,
    modifier = Modifier.fillMaxWidth()
)
```

**AFTER:**
```kotlin
Text("Weight Change Per Rep", style = titleMedium, fontWeight = Bold)
Spacer(modifier = Modifier.height(Spacing.medium))
ProgressionSlider(
    value = weightChangePerRep.toFloat(),
    onValueChange = { weightChangePerRep = it.toInt() },
    valueRange = -maxWeightChange..maxWeightChange,
    modifier = Modifier.fillMaxWidth()
)
Text("Negative = Regression, Positive = Progression", style = bodySmall, color = onSurfaceVariant)
```

**Why:** Visual slider with color feedback is more intuitive than number picker for bipolar values.

### 4.4 Echo Level: FilterChips → SegmentedButtonRow

**BEFORE:**
```kotlin
Row(horizontalArrangement = Arrangement.spacedBy(Spacing.small)) {
    EchoLevel.entries.forEach { level ->
        FilterChip(selected = echoLevel == level, onClick = { echoLevel = level; selectedMode = WorkoutMode.Echo(level) },
                   label = { Text(level.displayName, style = bodySmall, maxLines = 1, softWrap = false) },
                   modifier = Modifier.weight(1f))
    }
}
```

**AFTER:**
```kotlin
SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
    val levels = EchoLevel.entries
    levels.forEachIndexed { index, level ->
        SegmentedButton(
            shape = SegmentedButtonDefaults.itemShape(index = index, count = levels.size),
            onClick = { echoLevel = level; selectedMode = WorkoutMode.Echo(level) },
            selected = echoLevel == level
        ) { Text(level.displayName, maxLines = 1) }
    }
}
```

### 4.5 ActiveStatusCard: Enhanced with Live Indicator and Big Rep Counter

**BEFORE:**
```kotlin
Card(colors = CardDefaults.cardColors(containerColor = primaryContainer)) {
    Column {
        Text(when (workoutState) { ... }, style = titleLarge, fontWeight = Bold)
        if (workoutState is WorkoutState.Active) {
            Text("Reps: ${repCount.totalReps}", style = bodyLarge)
            currentMetric?.let { Text("Load: ${formatWeight(it.totalLoad)}", style = bodyMedium) }
        }
    }
}
```

**AFTER:**
```kotlin
// Live pulse animation
val infiniteTransition = rememberInfiniteTransition()
val alpha by infiniteTransition.animateFloat(0.5f, 1f, infiniteRepeatable(tween(1000), RepeatMode.Reverse))

Card(colors = CardDefaults.cardColors(
    containerColor = if (workoutState is WorkoutState.Active)
        primaryContainer.copy(alpha = 0.9f) else surfaceVariant
)) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(statusText, style = headlineSmall, fontWeight = Bold)
            Spacer(Modifier.weight(1f))
            if (workoutState is WorkoutState.Active) {
                Box(Modifier.size(12.dp).background(Color.Green.copy(alpha = alpha), CircleShape))
                Spacer(Modifier.width(4.dp))
                Text("LIVE", style = labelSmall, color = Color.Green, fontWeight = Bold)
            }
        }

        if (workoutState is WorkoutState.Active) {
            // BIG Rep Counter (centered, prominent)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("${repCount.totalReps}", style = displayLarge, fontWeight = FontWeight.Black)
                Text("REPS", style = labelLarge, fontWeight = Bold)
            }
            // Metric Grid
            Row(horizontalArrangement = Arrangement.SpaceEvenly) {
                Column(horizontalAlignment = CenterHorizontally) {
                    Text("Load", style = labelMedium)
                    Text(loadText, style = titleMedium, fontWeight = Bold)
                }
            }
        }
    }
}
```

**Why:** Live indicator provides visual feedback that workout is active. Large rep counter is the primary metric users care about.

### 4.6 AutoStartStopCard: Conditional Display

**BEFORE:** Always shown
```kotlin
AutoStartStopCard(workoutState, autoStartCountdown, autoStopState)
```

**AFTER:** Only shown when idle (active status shown via ActiveStatusCard)
```kotlin
if (workoutState is WorkoutState.Idle) {
    AutoStartStopCard(workoutState, autoStartCountdown, autoStopState)
}
```

### 4.7 Background: Theme-Specific → Material Theme Colors

**BEFORE:**
```kotlin
val backgroundGradient = if (themeMode == ThemeMode.DARK) {
    Brush.verticalGradient(listOf(Color(0xFF0F172A), Color(0xFF1E1B4B), Color(0xFF172554)))
} else {
    Brush.verticalGradient(listOf(Color(0xFFE0E7FF), Color(0xFFFCE7F3), Color(0xFFDDD6FE)))
}
```

**AFTER:**
```kotlin
val backgroundGradient = Brush.verticalGradient(listOf(
    MaterialTheme.colorScheme.surface,
    MaterialTheme.colorScheme.surfaceContainer,
    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
))
```

**Why:** Uses dynamic Material color tokens for better theme consistency.

### 4.8 Scaffold Removal

**BEFORE:**
```kotlin
Scaffold(
    topBar = {
        TopAppBar(title = { Text("Just Lift") }, navigationIcon = { IconButton { Icon(ArrowBack) } })
    }
) { padding -> Box(Modifier.padding(padding)) { ... } }
```

**AFTER:**
```kotlin
LaunchedEffect(Unit) { viewModel.updateTopBarTitle("Just Lift") }
Scaffold { padding -> ... }  // No local topBar
```

---

## 10. SingleExerciseScreen Changes (Additional Details)

### 5.1 Architecture: Dialog Flow → Embedded Picker

**BEFORE:** ExercisePickerDialog shown conditionally, empty state when closed
```kotlin
var showExercisePicker by remember { mutableStateOf(true) }

Scaffold(topBar = { TopAppBar(title = { Text("Single Exercise") }, ...) }) { padding ->
    Box(modifier = Modifier.padding(padding)) {
        if (showExercisePicker) {
            ExercisePickerDialog(fullScreen = true, onDismiss = { showExercisePicker = false }, ...)
        }
        exerciseToConfig?.let { ExerciseEditBottomSheet(...) }

        // Empty state shown when picker dismissed without selection
        if (!showExercisePicker && exerciseToConfig == null) {
            Column(horizontalAlignment = CenterHorizontally) {
                Icon(Icons.Default.FitnessCenter, modifier = Modifier.size(80.dp))
                Text("Choose an exercise to begin")
                Button(onClick = { showExercisePicker = true }) {
                    Icon(Icons.Default.Search)
                    Text("Select Exercise")
                }
            }
        }
    }
}
```

**AFTER:** ExercisePickerContent always visible, bottom sheet overlays when exercise selected
```kotlin
// Local state for picker (no dialog state needed)
var searchQuery by remember { mutableStateOf("") }
var selectedMuscleFilter by remember { mutableStateOf("All") }
...

LaunchedEffect(Unit) { viewModel.updateTopBarTitle("Single Exercise") }

Scaffold { padding ->  // No local topBar
    Box(modifier = Modifier.padding(padding)) {
        // Always show picker content as background
        ExercisePickerContent(
            exercises = exercises,
            searchQuery = searchQuery,
            onSearchQueryChange = { searchQuery = it },
            ...
            fullScreen = true
        )

        // Bottom sheet overlays when exercise selected
        exerciseToConfig?.let {
            ExerciseEditBottomSheet(
                exercise = it,
                onSave = { ... },
                onDismiss = { exerciseToConfig = null }  // Returns to picker
            )
        }
    }
}
```

**Why:** Eliminates empty state. Picker is always visible so user doesn't need extra tap to "return" to it.

---

## 11. ActiveWorkoutScreen Changes

### 6.1 Scaffold Removal and Global TopBar Integration

**BEFORE:**
```kotlin
Scaffold(
    topBar = {
        TopAppBar(
            title = {
                Column {
                    Text(screenTitle)
                    loadedRoutine?.let { routine ->
                        Text("Exercise $currentExerciseNum of $totalExercises", style = bodySmall)
                    }
                }
            },
            navigationIcon = {
                IconButton(onClick = { if (workoutActive) showExitConfirmation = true else navController.navigateUp() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack)
                }
            }
        )
    }
) { padding ->
    WorkoutTab(modifier = Modifier.padding(padding), ...)
}
```

**AFTER:**
```kotlin
// Set global title
LaunchedEffect(screenTitle) { viewModel.updateTopBarTitle(screenTitle) }

// Handle Back Button (System + Top Bar)
LaunchedEffect(Unit) {
    viewModel.setTopBarBackAction {
        if (workoutState in listOf(WorkoutState.Active, WorkoutState.Resting, WorkoutState.Countdown)) {
            showExitConfirmation = true
        } else navController.navigateUp()
    }
}
DisposableEffect(Unit) { onDispose { viewModel.clearTopBarBackAction() } }

// System Back Handler
BackHandler(enabled = true) {
    if (workoutActive) showExitConfirmation = true else navController.navigateUp()
}

// No Scaffold - WorkoutTab directly
WorkoutTab(...)
```

**Why:** Unified back handling ensures exit confirmation works from both system back and TopBar back.

---

## 12. WorkoutTab Changes

### 7.1 Target Reps: CompactNumberPicker → ExpressiveSlider

**BEFORE:**
```kotlin
if (!workoutParameters.isJustLift) {
    CompactNumberPicker(
        value = workoutParameters.reps,
        onValueChange = { reps -> onUpdateParameters(workoutParameters.copy(reps = reps)) },
        range = 1..50,
        label = "Target reps"
    )
}
```

**AFTER:**
```kotlin
if (!workoutParameters.isJustLift) {
    Text("Target reps: ${workoutParameters.reps}", style = titleMedium, fontWeight = Bold)
    ExpressiveSlider(
        value = workoutParameters.reps.toFloat(),
        onValueChange = { reps -> onUpdateParameters(workoutParameters.copy(reps = reps.toInt())) },
        valueRange = 1f..50f,
        steps = 49,
        modifier = Modifier.fillMaxWidth()
    )
}
```

### 7.2 Progression/Regression: FilterChips + NumberPicker → ProgressionSlider

**BEFORE:**
```kotlin
// FilterChips for direction selection
Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    FilterChip(selected = params.progressionRegressionKg >= 0,
               onClick = { onUpdateParameters(params.copy(progressionRegressionKg = abs(params.progressionRegressionKg))) },
               label = { Text("Prog") }, leadingIcon = { Icon(Icons.Default.KeyboardArrowUp) })
    FilterChip(selected = params.progressionRegressionKg < 0,
               onClick = { onUpdateParameters(params.copy(progressionRegressionKg = -abs(params.progressionRegressionKg))) },
               label = { Text("Regr") }, leadingIcon = { Icon(Icons.Default.KeyboardArrowDown) })
}
// Number picker for amount
CompactNumberPicker(value = abs(params.progressionRegressionKg).toInt(), onValueChange = { ... },
                    range = 0..maxWeightChange, label = "Amount (${weightUnit})")
```

**AFTER:**
```kotlin
val maxProgression = if (weightUnit == WeightUnit.LB) 6f else 3f
val currentProgression = kgToDisplay(workoutParameters.progressionRegressionKg, weightUnit)

ProgressionSlider(
    value = currentProgression,
    onValueChange = { displayValue ->
        val kg = displayToKg(displayValue, weightUnit)
        onUpdateParameters(workoutParameters.copy(progressionRegressionKg = kg))
    },
    valueRange = -maxProgression..maxProgression,
    modifier = Modifier.fillMaxWidth()
)
Text("Negative = Regression, Positive = Progression", style = bodySmall, color = onSurfaceVariant)
```

**Why:** Single slider is more intuitive than separate direction chips + amount picker. Color coding provides immediate visual feedback.

---

## 13. RoutinesTab Changes

### 8.1 Header Removal

**BEFORE:**
```kotlin
Text("My Routines", style = headlineLarge, fontWeight = Bold)
Spacer(modifier = Modifier.height(Spacing.medium))
```

**AFTER:** Header removed (uses global TopBar)

### 8.2 FAB: Extended → Simple

**BEFORE:**
```kotlin
ExtendedFloatingActionButton(onClick = { showRoutineBuilder = true },
    modifier = Modifier.height(56.dp), shape = RoundedCornerShape(28.dp)) {
    Icon(Icons.Default.Add, modifier = Modifier.size(24.dp))
    Spacer(modifier = Modifier.width(Spacing.small))
    Text("New Routine", style = titleLarge, fontWeight = Bold)
}
```

**AFTER:**
```kotlin
FloatingActionButton(onClick = { showRoutineBuilder = true }, shape = RoundedCornerShape(16.dp)) {
    Icon(Icons.Default.Add, modifier = Modifier.size(28.dp))
}
```

### 8.3 RoutineCard: Immediate Action → Expandable

**BEFORE:** Click starts workout, uses scale animation, overflow menu for actions
```kotlin
var isPressed by remember { mutableStateOf(false) }
val scale by animateFloatAsState(if (isPressed) 0.95f else 1f, spring(...))

Card(onClick = onStartWorkout, modifier = Modifier.scale(scale)) {
    Row { /* Icon, content, arrow button */ }
    // Overflow menu (top-right) for edit/duplicate/delete
    Box { IconButton(onClick = { showMenu = !showMenu }) { Icon(Icons.Default.MoreVert) }
        DropdownMenu { /* Edit, Duplicate, Delete */ }
    }
}
```

**AFTER:** Click toggles expansion, actions visible when expanded
```kotlin
var expanded by remember { mutableStateOf(false) }
var showDeleteDialog by remember { mutableStateOf(false) }

Card(onClick = { expanded = !expanded },
     elevation = CardDefaults.cardElevation(defaultElevation = if (expanded) 8.dp else 2.dp)) {
    Column {
        Row { /* Icon, header, expand icon */ }

        AnimatedVisibility(visible = expanded) {
            Column {
                HorizontalDivider()
                // Exercise list with set details
                routine.exercises.forEachIndexed { index, exercise ->
                    Row { Text("${index + 1}. ${exercise.exercise.name}"); Text(formatSetReps(exercise.setReps)) }
                }
                // Action buttons
                Button(onClick = onStartWorkout) { Icon(Icons.Default.PlayArrow); Text("Start Workout") }
                Row(horizontalArrangement = Arrangement.SpaceEvenly) {
                    OutlinedButton(onClick = onEdit) { Icon(Edit); Text("Edit") }
                    OutlinedButton(onClick = onDuplicate) { Icon(ContentCopy); Text("Copy") }
                    OutlinedButton(onClick = { showDeleteDialog = true }, colors = errorColors) {
                        Icon(Delete); Text("Delete")
                    }
                }
            }
        }
    }
}

if (showDeleteDialog) { AlertDialog(title = "Delete Routine", ...) }
```

**Why:** Prevents accidental workout starts. Users can review routine details before starting.

---

## 14. WeeklyProgramsScreen Changes

### 9.1 Scaffold Removal and Title Integration

**BEFORE:**
```kotlin
Scaffold(
    topBar = {
        TopAppBar(title = { Text("Weekly Programs") }, navigationIcon = { IconButton { Icon(ArrowBack) } })
    }
) { padding -> ... }
```

**AFTER:**
```kotlin
LaunchedEffect(Unit) { viewModel.updateTopBarTitle("Weekly Programs") }
Box(modifier = Modifier.fillMaxSize().background(backgroundGradient)) { ... }  // No Scaffold
```

### 9.2 ProgramListItem: Expandable with Schedule Preview

**BEFORE:** Click navigates to builder, no schedule preview
```kotlin
Card(modifier = Modifier.clickable(onClick = onClick)) {
    Row {
        Column { Text(program.title); Text("${program.days.size} workout days") }
        Row { IconButton(onDelete); if (!isActive) TextButton(onActivate) else Surface { Text("Active") } }
    }
}
```

**AFTER:** Click toggles expansion, shows weekly schedule
```kotlin
var expanded by remember { mutableStateOf(false) }

Card(modifier = Modifier.clickable(onClick = { expanded = !expanded })) {
    Column {
        Row {
            Column { Text(program.title); Text("${program.days.size} workout days") }
            Row {
                IconButton(onClick) { Icon(Icons.Default.Edit) }  // Edit button
                if (!isActive) TextButton(onActivate) { Text("Activate") } else Surface { Text("Active") }
                Icon(if (expanded) KeyboardArrowUp else KeyboardArrowDown)
            }
        }

        AnimatedVisibility(visible = expanded) {
            Column {
                HorizontalDivider()
                Text("Schedule", style = titleSmall, color = primary)
                // Show each day's routine
                program.days.sortedBy { it.dayOfWeek }.forEach { day ->
                    val dayName = DayOfWeek.of(day.dayOfWeek).getDisplayName(SHORT, Locale.getDefault())
                    val routineName = routineNameLookup(day.routineId)
                    Row { Text(dayName, fontWeight = Bold); Text(routineName) }
                }
                OutlinedButton(onClick = { showDeleteDialog = true }, colors = errorColors) {
                    Icon(Delete); Text("Delete Program")
                }
            }
        }
    }
}
```

**Why:** Users can preview program schedule without navigating away. Delete button moved to expanded state to avoid accidental taps.

---

## 15. ProgramBuilderScreen Changes

### 10.1 Complete Scaffold Removal and TopBar Action Integration

**BEFORE:**
```kotlin
var isEditingName by remember { mutableStateOf(false) }

Scaffold(
    topBar = {
        TopAppBar(
            title = {
                if (isEditingName) TextField(value = programName, onValueChange = { programName = it })
                else Text(programName)
            },
            navigationIcon = { IconButton(onClick = { navController.navigateUp() }) { Icon(ArrowBack) } },
            actions = {
                IconButton(onClick = { isEditingName = !isEditingName }) {
                    Icon(if (isEditingName) Icons.Default.Check else Icons.Default.Edit)
                }
                IconButton(onClick = { saveProgram(); navController.navigateUp() }) {
                    Icon(Icons.Default.Done)
                }
            }
        )
    }
) { padding -> ... }
```

**AFTER:**
```kotlin
LaunchedEffect(programId) { viewModel.updateTopBarTitle(if (programId == "new") "New Program" else "Edit Program") }

LaunchedEffect(programName, dailyRoutines) {
    viewModel.setTopBarActions(listOf(
        TopBarAction(icon = Icons.Default.Done, description = "Save Program", onClick = { saveProgram(); navController.navigateUp() })
    ))
}
DisposableEffect(Unit) { onDispose { viewModel.clearTopBarActions() } }

Box(modifier = Modifier.fillMaxSize().background(backgroundGradient)) {  // No Scaffold
    LazyColumn {
        item {
            OutlinedTextField(
                value = programName,
                onValueChange = { programName = it },
                label = { Text("Program Name") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )
        }
        // ... rest of content
    }
}
```

**Why:** Inline name editing via TextField is clearer than toggle-based editing. Save action injected via global TopBar.

---

## 16. InsightsTab Complete Overhaul

### 11.1 Component Replacement Overview

| Beta 5.1 Component | Current Component | Visualization Type |
|-------------------|-------------------|-------------------|
| `TrainingBalanceCard` | `MuscleBalanceRadarCard` | Radar Chart |
| `ProgressVelocityCard` | **Removed** | N/A |
| `ConsistencyScoreCard` | `ConsistencyGaugeCard` | Gauge Chart |
| `WeeklyComparisonCard` | `VolumeVsIntensityCard` | Combo Chart |
| N/A | `WorkoutModeDistributionCard` | Donut Chart |

### 11.2 MuscleBalanceRadarCard (Replaces TrainingBalanceCard)

**BEFORE:** LinearProgressIndicator for each muscle group
**AFTER:** RadarChart with 6 normalized categories (Chest, Back, Legs, Shoulders, Arms, Core)

### 11.3 ConsistencyGaugeCard (Replaces ConsistencyScoreCard)

**BEFORE:** Circular progress with streak text
**AFTER:** GaugeChart showing workouts in last 30 days vs target (12)

---

## 17. ExerciseEditDialog Full Breakdown

### 12.1 Weight Change: Surface + CompactNumberPicker → ExpressiveCard + ProgressionSlider

**BEFORE:**
```kotlin
Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, surfaceVariant)) {
    Column(modifier = Modifier.padding(Spacing.small)) {
        CompactNumberPicker(value = weightChange, onValueChange = viewModel::onWeightChange,
                            range = -maxWeightChange..maxWeightChange, label = "Weight Change Per Rep", suffix = weightSuffix)
        Text("Negative = Regression, Positive = Progression", style = bodySmall)
    }
}
```

**AFTER:**
```kotlin
ExpressiveCard(onClick = {}, enabled = false, border = BorderStroke(2.dp, outlineVariant)) {
    Column(modifier = Modifier.padding(Spacing.medium)) {
        Text("Weight Change Per Rep", style = titleMedium, fontWeight = Bold)
        Spacer(modifier = Modifier.height(Spacing.medium))
        ProgressionSlider(
            value = weightChange.toFloat(),
            onValueChange = { viewModel.onWeightChange(it.toInt()) },
            valueRange = -maxWeightChange.toFloat()..maxWeightChange.toFloat()
        )
        Text("Negative = Regression, Positive = Progression", style = bodySmall, color = onSurfaceVariant)
    }
}
```

### 12.2 Echo Level: FilterChips → SegmentedButtonRow

Similar pattern to JustLiftScreen changes.

### 12.3 Rest Time: CompactNumberPicker → ExpressiveSlider

**BEFORE:**
```kotlin
CompactNumberPicker(value = rest, onValueChange = viewModel::onRestChange, range = 0..300, label = "Rest Time", suffix = "sec")
```

**AFTER:**
```kotlin
Text("Rest Time: ${rest}s", style = titleSmall, fontWeight = Bold)
ExpressiveSlider(value = rest.toFloat(), onValueChange = { viewModel.onRestChange(it.toInt()) }, valueRange = 0f..300f, steps = 59)
```

---

## 18. ExercisePickerDialog Refactoring

### 13.1 PickerContent Extraction

**BEFORE:** `PickerContent()` was a local composable inside `ExercisePickerDialog`
**AFTER:** `ExercisePickerContent()` is a public composable that can be used standalone

This allows SingleExerciseScreen to embed the picker content directly without using a dialog.

---

## 19. HistoryAndSettingsTabs Changes

### 14.1 Header Removal

Both HistoryTab and SettingsTab had local headers removed. SettingsTab now takes `onSetTitle: (String) -> Unit` callback to set global title.

---

## 20. ConnectionLogsScreen Changes

### 15.1 Scaffold Removal

**BEFORE:**
```kotlin
Scaffold(
    topBar = {
        TopAppBar(title = { Text("Connection Logs") }, navigationIcon = { IconButton { Icon(ArrowBack) } },
                  actions = { IconButton { Icon(Share) }; IconButton { Icon(Delete) } })
    }
) { padding -> ... }
```

**AFTER:**
```kotlin
LaunchedEffect(Unit) { mainViewModel.updateTopBarTitle("Connection Logs") }

Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
    // Actions Row (replaces TopBar actions)
    Row(horizontalArrangement = Arrangement.End) {
        TextButton(onClick = { showExportDialog = true }) { Icon(Share); Text("Export") }
        TextButton(onClick = { showClearDialog = true }) { Icon(Delete); Text("Clear") }
    }
    // ... content
}
```

---

## 21. Chart Component Updates

### 16.1 GaugeChart Icon Fix

**BEFORE:**
```kotlin
Icon(imageVector = Icons.Default.TrendingUp, ...)
```

**AFTER:**
```kotlin
Icon(imageVector = Icons.AutoMirrored.Filled.TrendingUp, ...)
```

**Why:** Proper Material Icons mirroring for RTL support.

### 16.2 Unused Import Cleanup

RadarChart, GaugeChart, and other chart files had unused imports removed (DrawScope, Offset, etc.)

---

## 22. Summary of UI Philosophy Changes

### Architectural Changes

| Aspect | Beta 5.1 | Current | Benefit |
|--------|----------|---------|---------|
| **Navigation** | Per-screen TopBars (redundant code) | Global Smart TopBar system (MainViewModel) | Consistent behavior, less code duplication |
| **Screen Titles** | Local Scaffold TopAppBar per screen | LaunchedEffect + viewModel.updateTopBarTitle() | ~56dp saved per screen |
| **Bottom Bar** | Custom FAB-centric BottomAppBar | Standard NavigationBar | Material 3 compliance |
| **Back Handling** | Per-screen IconButton implementation | Centralized topBarBackAction via ViewModel | Unified exit confirmation |

### Layout Changes

| Aspect | Beta 5.1 | Current | Benefit |
|--------|----------|---------|---------|
| **HomeScreen** | Vertical Column layout | LazyVerticalGrid (2 or 4 cols) | Landscape support, better space usage |
| **Card Layouts** | Horizontal Row with arrow | Vertical Column centered | Better for grid cells |
| **List Items** | Always expanded | Expandable AnimatedVisibility | ~120dp saved per collapsed item |
| **Empty States** | Placeholder screens (SingleExercise) | Always-visible content | Eliminates unnecessary screens |

### Input Control Migration

| Control | Beta 5.1 | Current | Improvement |
|---------|----------|---------|-------------|
| **Weight Change Per Rep** | CompactNumberPicker (120dp height) | ProgressionSlider (60dp) | 50% space reduction + color feedback |
| **Rest Time** | CompactNumberPicker (wheel) | ExpressiveSlider (horizontal) | Full-width touch target |
| **Eccentric Load** | Slider without labels | Slider with 0%/150% labels | Immediate orientation |
| **Mode Selection** | FilterChip row | SingleChoiceSegmentedButtonRow | Continuous touch surface |
| **Echo Level** | FilterChip row | SegmentedButtonRow | Material 3 standard, better touch |

### Visual Enhancement

| Component | Beta 5.1 | Current | Benefit |
|-----------|----------|---------|---------|
| **Cards** | Manual scale animation (boilerplate) | ExpressiveCard (encapsulated) | Consistent spring physics |
| **Workout Status** | Text-only status | Live indicator + Big Rep Counter | Better at-a-glance feedback |
| **Insights** | LinearProgressIndicator bars | RadarChart, GaugeChart, DonutChart | Professional data visualization |
| **Safety Events** | None | SafetyEventsCard (color-coded) | Critical user feedback |

### Touch Target Improvements

| Element | Beta 5.1 Size | Current Size | Change |
|---------|---------------|--------------|--------|
| Mode chips | ~40dp height | 48dp min (SegmentedButton) | +20% |
| Weight +/- buttons | 48dp each | Full-width slider track | +400% |
| Rest time scroll | 48dp wheel | Full-width slider | +400% |
| Echo level chips | ~40dp each | 48dp connected buttons | Continuous |

### Code Organization

| Area | Beta 5.1 | Current | Files Affected |
|------|----------|---------|----------------|
| TopBar | Duplicated in each screen | Centralized in EnhancedMainScreen | 10+ screens |
| Input Components | Mixed (NumberPicker, FilterChip) | Unified (ExpressiveSlider, SegmentedButton) | ExpressiveComponents.kt |
| ExercisePicker | Dialog-internal PickerContent | Extracted ExercisePickerContent | Reusable in SingleExerciseScreen |
| Back Navigation | Per-screen onClick | ViewModel topBarBackAction | Consistent confirmation handling |
