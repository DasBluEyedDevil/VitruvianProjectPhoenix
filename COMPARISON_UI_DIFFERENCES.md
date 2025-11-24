# UI Differences: Beta 5.1 vs Current Build (Comprehensive)

This document provides granular code-level differences for ALL UI changes between v0.5.1-beta and the current build.

---

## Table of Contents
1. [New UI Components](#1-new-ui-components)
2. [Global Navigation Architecture](#2-global-navigation-architecture)
3. [HomeScreen Changes](#3-homescreen-changes)
4. [JustLiftScreen Changes](#4-justliftscreen-changes)
5. [SingleExerciseScreen Changes](#5-singleexercisescreen-changes)
6. [ActiveWorkoutScreen Changes](#6-activeworkoutscreen-changes)
7. [WorkoutTab Changes](#7-workouttab-changes)
8. [RoutinesTab Changes](#8-routinestab-changes)
9. [WeeklyProgramsScreen Changes](#9-weeklyprogramsscreen-changes)
10. [ProgramBuilderScreen Changes](#10-programbuilderscreen-changes)
11. [InsightsTab Complete Overhaul](#11-insightstab-complete-overhaul)
12. [ExerciseEditDialog Changes](#12-exerciseeditdialog-changes)
13. [ExercisePickerDialog Refactoring](#13-exercisepickerdialog-refactoring)
14. [HistoryAndSettingsTabs Changes](#14-historyandsettingstabs-changes)
15. [ConnectionLogsScreen Changes](#15-connectionlogsscreen-changes)
16. [Chart Component Updates](#16-chart-component-updates)

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

## 2. Global Navigation Architecture

### 2.1 New ViewModel State (MainViewModel.kt)

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

## 3. HomeScreen Changes

### 3.1 Layout: Column → LazyVerticalGrid

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

### 3.2 WorkoutCard Layout: Horizontal → Vertical

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

## 4. JustLiftScreen Changes

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

## 5. SingleExerciseScreen Changes

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

## 6. ActiveWorkoutScreen Changes

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

## 7. WorkoutTab Changes

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

## 8. RoutinesTab Changes

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

## 9. WeeklyProgramsScreen Changes

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

## 10. ProgramBuilderScreen Changes

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

## 11. InsightsTab Complete Overhaul

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

## 12. ExerciseEditDialog Changes

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

## 13. ExercisePickerDialog Refactoring

### 13.1 PickerContent Extraction

**BEFORE:** `PickerContent()` was a local composable inside `ExercisePickerDialog`
**AFTER:** `ExercisePickerContent()` is a public composable that can be used standalone

This allows SingleExerciseScreen to embed the picker content directly without using a dialog.

---

## 14. HistoryAndSettingsTabs Changes

### 14.1 Header Removal

Both HistoryTab and SettingsTab had local headers removed. SettingsTab now takes `onSetTitle: (String) -> Unit` callback to set global title.

---

## 15. ConnectionLogsScreen Changes

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

## 16. Chart Component Updates

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

## Summary of UI Philosophy Changes

| Aspect | Beta 5.1 | Current |
|--------|----------|---------|
| **Navigation** | Per-screen TopBars | Global Smart TopBar system |
| **Bottom Bar** | Custom FAB-centric BottomAppBar | Standard NavigationBar |
| **Layout** | Vertical scrolling columns | Responsive grids (landscape support) |
| **Input Controls** | CompactNumberPicker | ExpressiveSlider, ProgressionSlider |
| **Mode Selection** | FilterChips | SegmentedButtonRow |
| **Cards** | Manual scale animations | ExpressiveCard with built-in animation |
| **List Items** | Immediate actions | Expandable with details |
| **Insights Data** | Text + progress bars | Visual charts (radar, gauge, donut) |
| **Safety Feedback** | None | SafetyEventsCard component |
| **Back Handling** | Per-screen implementation | Centralized ViewModel-based |
