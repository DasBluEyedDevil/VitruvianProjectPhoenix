# UI Differences: Beta 5.1 vs Current Build (Detailed)

This document provides granular code-level differences for all UI changes between v0.5.1-beta and the current build.

---

## Table of Contents
1. [New UI Components](#1-new-ui-components)
2. [InsightsTab Complete Overhaul](#2-insightstab-complete-overhaul)
3. [RoutinesTab Changes](#3-routinestab-changes)
4. [Global TopBar System](#4-global-topbar-system)
5. [ActiveWorkoutScreen Changes](#5-activeworkoutscreen-changes)
6. [HistoryAndSettingsTabs Changes](#6-historyandsettingstabs-changes)
7. [Theme and Motion Changes](#7-theme-and-motion-changes)

---

## 1. New UI Components

### 1.1 SafetyEventsCard.kt (NEW FILE)

**Purpose:** Displays safety-related events (deload warnings, ROM violations, spotter activations) during workouts. This provides users with feedback about protective interventions by the machine.

```kotlin
// NEW FILE: presentation/components/SafetyEventsCard.kt

@Composable
fun SafetyEventsCard(
    summary: SafetyEventSummary,
    modifier: Modifier = Modifier
) {
    // Only renders when there are actual safety events
    if (!summary.hasSafetyEvents) return

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            // Uses error container with transparency for visual prominence
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
        )
    ) {
        Column(...) {
            // Header with warning icon
            Row(...) {
                Icon(Icons.Default.Warning, tint = MaterialTheme.colorScheme.error)
                Text("Safety Events", color = MaterialTheme.colorScheme.error)
            }

            // Individual event rows with color coding:
            // - Deload Warnings: Orange (0xFFFF9800)
            // - ROM Violations: Red (0xFFF44336)
            // - Spotter Activations: Blue (0xFF2196F3)
            if (summary.deloadWarnings > 0) {
                SafetyEventRow(label = "Deload Warnings", count = summary.deloadWarnings, color = Color(0xFFFF9800))
            }
            if (summary.romViolations > 0) {
                SafetyEventRow(label = "ROM Violations", count = summary.romViolations, color = Color(0xFFF44336))
            }
            if (summary.spotterActivations > 0) {
                SafetyEventRow(label = "Spotter Activations", count = summary.spotterActivations, color = Color(0xFF2196F3))
            }
        }
    }
}
```

**Why this was added:** The Vitruvian machine has built-in safety features (force limiting, ROM boundaries, spotter assist). Previously, users had no visibility into when these activated. This component surfaces that data so users can understand their workout quality and adjust if needed.

---

### 1.2 ExpressiveComponents.kt (NEW FILE)

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
    shape: Shape = RoundedCornerShape(20.dp),  // Larger corners than standard
    elevation: CardElevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
    border: BorderStroke? = BorderStroke(2.dp, MaterialTheme.colorScheme.outlineVariant),
    ...
) {
    val isPressed by interactionSource.collectIsPressedAsState()

    // Spring animation for organic, bouncy feel
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,  // More bounce
            stiffness = Spring.StiffnessLow              // Slower, more organic
        )
    )

    Card(
        onClick = onClick,
        modifier = modifier.scale(scale),  // Apply scale animation
        ...
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
    // Dynamic color based on value
    val activeColor = when {
        value < 0 -> MaterialTheme.colorScheme.error      // Red for regression
        value > 0 -> MaterialTheme.colorScheme.onSurface  // Normal for progression
        else -> MaterialTheme.colorScheme.onSurfaceVariant // Neutral for zero
    }

    Column {
        Row(horizontalArrangement = Arrangement.SpaceBetween) {
            Text("${valueRange.start.toInt()}")  // "-10"
            Text(if (value > 0) "+${value.toInt()}" else "${value.toInt()}")  // "+5" or "-3"
            Text("+${valueRange.endInclusive.toInt()}")  // "+10"
        }
        ExpressiveSlider(value, onValueChange, valueRange, trackColor = activeColor)
    }
}
```

**Why this was added:** Provides consistent Material 3 Expressive styling across the app with reusable components. The spring animations give a more organic, responsive feel to interactions.

---

## 2. InsightsTab Complete Overhaul

### 2.1 Component Replacement Overview

| Beta 5.1 Component | Current Component | Visualization Type |
|-------------------|-------------------|-------------------|
| `TrainingBalanceCard` | `MuscleBalanceRadarCard` | Radar Chart |
| `ProgressVelocityCard` | **Removed** | N/A |
| `ConsistencyScoreCard` | `ConsistencyGaugeCard` | Gauge Chart |
| `WeeklyComparisonCard` | `VolumeVsIntensityCard` | Combo Chart |
| N/A | `WorkoutModeDistributionCard` | Donut Chart |

### 2.2 TrainingBalanceCard → MuscleBalanceRadarCard

**BEFORE (Beta 5.1):** Linear progress bars showing muscle group percentages

```kotlin
// BEFORE: Used LinearProgressIndicator for each muscle group
@Composable
fun TrainingBalanceCard(...) {
    val muscleGroupCounts = remember { mutableStateMapOf<String, Int>() }

    // Grouped by exercise ID, counted PRs per muscle group
    personalRecords.groupBy { it.exerciseId }.forEach { (exerciseId, prs) ->
        val exercise = exerciseRepository.getExerciseById(exerciseId)
        exercise?.muscleGroups?.split(",")?.forEach { group ->
            counts[trimmed] = counts.getOrDefault(trimmed, 0) + prs.size
        }
    }

    // Displayed as horizontal progress bars
    sortedGroups.forEach { (group, count) ->
        val percentage = (count.toFloat() / totalPRs * 100).roundToInt()
        Row(...) {
            Text(group)
            Text("$percentage%")
        }
        LinearProgressIndicator(progress = { count.toFloat() / totalPRs })
    }

    // Text recommendation for least trained muscle
    Text("Consider adding more ${leastTrained.key} exercises")
}
```

**AFTER (Current):** Radar chart with normalized muscle group categories

```kotlin
// AFTER: Uses RadarChart component for visual balance display
@Composable
fun MuscleBalanceRadarCard(...) {
    var radarData by remember { mutableStateOf<List<Pair<String, Float>>>(emptyList()) }

    LaunchedEffect(personalRecords) {
        personalRecords.forEach { pr ->
            val exercise = exerciseRepository.getExerciseById(pr.exerciseId)
            val groups = exercise?.muscleGroups?.split(",")

            groups.forEach { group ->
                // NORMALIZATION: Maps specific muscles to 6 standard categories
                val normalizedGroup = when {
                    group.contains("Chest", ignoreCase = true) -> "Chest"
                    group.contains("Back", ignoreCase = true) -> "Back"
                    group.contains("Leg", ignoreCase = true) ||
                        group.contains("Quadriceps") ||
                        group.contains("Hamstrings") -> "Legs"
                    group.contains("Shoulder", ignoreCase = true) -> "Shoulders"
                    group.contains("Arm", ignoreCase = true) ||
                        group.contains("Bicep") ||
                        group.contains("Tricep") -> "Arms"
                    group.contains("Core", ignoreCase = true) ||
                        group.contains("Abs") -> "Core"
                    else -> "Other"
                }
                counts[normalizedGroup] = counts.getOrDefault(normalizedGroup, 0) + 1
            }
        }

        // Convert to relative frequency (0.0-1.0) relative to MAX category
        // This ensures the chart looks full even with low absolute counts
        val maxCount = counts.values.maxOrNull()?.toFloat() ?: 1f
        val standardGroups = listOf("Chest", "Back", "Legs", "Shoulders", "Arms", "Core")
        radarData = standardGroups.map { group ->
            group to (counts[group]?.toFloat()?.div(maxCount) ?: 0f)
        }
    }

    Card(...) {
        Text("Muscle Balance")
        Text("Relative training focus by body part")

        // Radar chart instead of progress bars
        RadarChart(data = radarData, maxValue = 1.0f, modifier = Modifier.height(300.dp))
    }
}
```

**Why this changed:**
1. Radar charts provide an intuitive "at-a-glance" view of balance across multiple dimensions
2. Normalized categories reduce noise (e.g., "Biceps" and "Arms" now map to same category)
3. Relative scaling ensures the chart is useful even for users with few workouts

---

### 2.3 ConsistencyScoreCard → ConsistencyGaugeCard

**BEFORE:** Circular progress indicator with text metrics

```kotlin
// BEFORE: Circular progress and text-based display
@Composable
fun ConsistencyScoreCard(workoutSessions: List<WorkoutSession>, ...) {
    // Complex streak calculation
    val (currentStreak, longestStreak) = calculateStreaks(workoutSessions)
    val consistencyScore = calculateConsistencyScore(workoutSessions)

    CircularProgressIndicator(progress = consistencyScore / 100f)
    Text("$consistencyScore%")
    Text("Current Streak: $currentStreak days")
    Text("Longest Streak: $longestStreak days")
}
```

**AFTER:** Gauge chart showing workouts in last 30 days

```kotlin
// AFTER: Simple gauge chart with monthly workout count
@Composable
fun ConsistencyGaugeCard(workoutSessions: List<WorkoutSession>, ...) {
    val stats = remember(workoutSessions) {
        val thirtyDaysAgo = System.currentTimeMillis() - (30L * 24 * 60 * 60 * 1000)
        workoutSessions.count { it.timestamp >= thirtyDaysAgo }
    }

    val target = 12f  // ~3 workouts per week

    Card(...) {
        Text("Monthly Consistency")
        Text("Workouts in the last 30 days")

        // Visual gauge instead of circular progress
        GaugeChart(
            value = stats.toFloat(),
            maxValue = target,
            modifier = Modifier.height(200.dp)
        )
    }
}
```

**Why this changed:** Simpler metric (workouts in 30 days) is more actionable than abstract "consistency score." Gauge chart provides clear visual feedback against a target.

---

## 3. RoutinesTab Changes

### 3.1 Header Removal

**BEFORE:**
```kotlin
Column(...) {
    Text(
        "My Routines",
        style = MaterialTheme.typography.headlineLarge,
        fontWeight = FontWeight.Bold
    )
    Spacer(modifier = Modifier.height(Spacing.medium))
    // ... content
}
```

**AFTER:**
```kotlin
Column(...) {
    // Header removed - now uses global TopBar
    if (routines.isEmpty()) {
        EmptyState(...)
    }
    // ... content
}
```

**Why:** Global TopBar system now handles screen titles consistently.

---

### 3.2 FAB Change: Extended → Simple

**BEFORE:**
```kotlin
ExtendedFloatingActionButton(
    onClick = { showRoutineBuilder = true },
    modifier = Modifier.height(56.dp),
    shape = RoundedCornerShape(28.dp)
) {
    Icon(Icons.Default.Add, modifier = Modifier.size(24.dp))
    Spacer(modifier = Modifier.width(Spacing.small))
    Text("New Routine", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
}
```

**AFTER:**
```kotlin
FloatingActionButton(
    onClick = { showRoutineBuilder = true },
    shape = RoundedCornerShape(16.dp)
) {
    Icon(Icons.Default.Add, modifier = Modifier.size(28.dp))
    // No text - icon only
}
```

**Why:** Simpler FAB reduces visual clutter. The action is self-evident from context.

---

### 3.3 Routine Card: Immediate Action → Expandable

**BEFORE:** Clicking card immediately starts workout with spring scale animation
```kotlin
Card(
    onClick = onStartWorkout,  // Direct action
    modifier = Modifier.scale(scale)  // Spring animation on press
) {
    // Always shows exercise list, action buttons visible
    Row {
        // Icon, content, action buttons all visible
    }
}
```

**AFTER:** Clicking card expands/collapses to show details and actions
```kotlin
var expanded by remember { mutableStateOf(false) }

Card(
    onClick = { expanded = !expanded },  // Toggle expansion
    elevation = CardDefaults.cardElevation(
        defaultElevation = if (expanded) 8.dp else 2.dp  // Dynamic elevation
    )
) {
    Column {
        // Header always visible
        Row { /* Icon, name, exercise count */ }

        // Details only shown when expanded
        if (expanded) {
            // Exercise list
            // Action buttons (Start, Edit, Delete, Duplicate)
        }
    }
}
```

**Why:** Prevents accidental workout starts. Users can review routine details before committing to start.

---

## 4. Global TopBar System

### 4.1 New ViewModel State (MainViewModel.kt)

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

// Methods for screens to use
fun updateTopBarTitle(title: String) { _topBarTitle.value = title }
fun setTopBarActions(actions: List<TopBarAction>) { _topBarActions.value = actions }
fun clearTopBarActions() { _topBarActions.value = emptyList() }
fun setTopBarBackAction(action: () -> Unit) { _topBarBackAction.value = action }
fun clearTopBarBackAction() { _topBarBackAction.value = null }
```

### 4.2 EnhancedMainScreen Changes

**BEFORE:** Static title "Vitruvian Project Phoenix"
```kotlin
TopAppBar(
    title = {
        Column {
            Text("Vitruvian", style = ...)
            Text("Project Phoenix", style = ...)
        }
    }
)
```

**AFTER:** Dynamic title with conditional back button and actions
```kotlin
val topBarTitle by viewModel.topBarTitle.collectAsState()
val topBarActions by viewModel.topBarActions.collectAsState()
val topBarBackAction by viewModel.topBarBackAction.collectAsState()

// Determine if back button should show
val showBackButton = currentRoute != NavigationRoutes.Home.route &&
                     currentRoute != NavigationRoutes.Analytics.route &&
                     currentRoute != NavigationRoutes.Settings.route

TopAppBar(
    title = {
        Column {
            Text(topBarTitle ?: getScreenTitle(currentRoute))  // Dynamic title
            Text("Vitruvian Project Phoenix", style = ...)     // Subtitle always shows
        }
    },
    navigationIcon = {
        if (showBackButton) {
            IconButton(onClick = {
                topBarBackAction?.invoke() ?: navController.navigateUp()
            }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack)
            }
        }
    },
    actions = {
        // Dynamic actions from current screen
        topBarActions.forEach { action ->
            IconButton(onClick = action.onClick) {
                Icon(action.icon, contentDescription = action.description)
            }
        }
    }
)
```

**Why:** Centralized TopBar management ensures consistent navigation UX. Screens can customize title and actions without duplicating TopBar code.

---

## 5. ActiveWorkoutScreen Changes

### 5.1 Scaffold Removal

**BEFORE:** Screen had its own Scaffold with TopAppBar
```kotlin
Scaffold(
    topBar = {
        TopAppBar(
            title = { Text(screenTitle) },
            navigationIcon = {
                IconButton(onClick = { /* back handling */ }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack)
                }
            }
        )
    }
) { padding ->
    WorkoutTab(modifier = Modifier.padding(padding), ...)
}
```

**AFTER:** Uses global TopBar via ViewModel
```kotlin
// Set title for global TopBar
LaunchedEffect(screenTitle) {
    viewModel.updateTopBarTitle(screenTitle)
}

// Register custom back action
LaunchedEffect(Unit) {
    viewModel.setTopBarBackAction {
        if (workoutIsActive) showExitConfirmation = true
        else navController.navigateUp()
    }
}

// Cleanup on dispose
DisposableEffect(Unit) {
    onDispose { viewModel.clearTopBarBackAction() }
}

// System back handler for consistency
BackHandler(enabled = true) {
    if (workoutIsActive) showExitConfirmation = true
    else navController.navigateUp()
}

// No Scaffold - just WorkoutTab directly
WorkoutTab(...)
```

**Why:** Unified navigation behavior. Custom back action ensures workout exit confirmation works from both system back button and TopBar back button.

---

## 6. HistoryAndSettingsTabs Changes

### 6.1 Header Removal and Global Integration

**BEFORE:**
```kotlin
@Composable
fun HistoryTab(...) {
    Column {
        Row {
            Text("Workout History", style = headlineLarge)
            IconButton(onClick = onRefresh) { Icon(Icons.Default.Refresh) }
        }
        // ... content
    }
}

@Composable
fun SettingsTab(...) {
    Column {
        Text("Settings", style = headlineLarge)
        // ... content
    }
}
```

**AFTER:**
```kotlin
@Composable
fun HistoryTab(...) {
    Column {
        // Header removed - uses global TopBar
        // Refresh functionality commented out (potential pull-to-refresh)
        // ... content
    }
}

@Composable
fun SettingsTab(
    onSetTitle: (String) -> Unit,  // NEW: callback to set global title
    ...
) {
    LaunchedEffect(Unit) {
        onSetTitle("Settings")  // Set global title
    }

    Column {
        // Header removed
        // ... content
    }
}
```

---

## 7. Theme and Motion Changes

### 7.1 Material3Expressive.kt Additions

```kotlin
// NEW: Expressive motion specifications
object ExpressiveMotion {
    /**
     * Standard spring for most interactions (buttons, cards)
     * - Low stiffness = slower, more organic movement
     * - Low bouncy damping = playful but controlled bounce
     */
    val SpringDefault = spring<Float>(
        dampingRatio = Spring.DampingRatioLowBouncy,
        stiffness = Spring.StiffnessLow
    )

    /**
     * Snappy spring for quick transitions (toggles, checkboxes)
     * - No bounce = direct, immediate feedback
     * - Medium stiffness = quick but not jarring
     */
    val SpringSnappy = spring<Float>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMedium
    )

    /**
     * Bouncy spring for emphasis (errors, celebrations)
     * - High bounce = attention-grabbing
     * - Low stiffness = dramatic effect
     */
    val SpringBouncy = spring<Float>(
        dampingRatio = Spring.DampingRatioHighBouncy,
        stiffness = Spring.StiffnessLow
    )
}
```

**Why:** Standardized motion specs ensure consistent animation feel across the app. Material 3 Expressive design emphasizes organic, playful motion.

---

## Summary of UI Philosophy Changes

| Aspect | Beta 5.1 | Current |
|--------|----------|---------|
| **Screen Headers** | Per-screen headers | Global TopBar system |
| **Insights Data** | Text + progress bars | Visual charts (radar, gauge, donut) |
| **Routine Cards** | Immediate action on click | Expandable with details |
| **FAB Style** | Extended with text | Simple icon-only |
| **Animations** | Ad-hoc spring values | Centralized ExpressiveMotion specs |
| **Safety Feedback** | None | SafetyEventsCard component |
| **Navigation** | Per-screen back handling | Centralized ViewModel-based |
