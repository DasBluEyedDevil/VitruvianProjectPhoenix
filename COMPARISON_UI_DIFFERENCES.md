# UI Differences: Beta 5.1 vs Current Build

This document outlines all UI-related changes between the v0.5.1-beta release and the current build.

---

## Table of Contents
1. [New UI Components](#new-ui-components)
2. [Modified Screens](#modified-screens)
3. [Chart/Visualization Changes](#chartvisualization-changes)
4. [Theme and Styling Changes](#theme-and-styling-changes)
5. [Navigation and Layout Changes](#navigation-and-layout-changes)
6. [Component-Level Changes](#component-level-changes)

---

## 1. New UI Components

### SafetyEventsCard.kt (NEW)
**Location:** `presentation/components/SafetyEventsCard.kt`

A new card component for displaying safety-related events during workouts:
- Displays deload warnings (orange color)
- Displays ROM violations (red color)
- Displays spotter activations (blue color)
- Only renders when safety events are present (`hasSafetyEvents`)
- Uses Material 3 error container styling for visual prominence

### ExpressiveComponents.kt (NEW)
**Location:** `presentation/components/ExpressiveComponents.kt`

New Material 3 Expressive UI component library:

**ExpressiveCard:**
- 20dp rounded corners
- Spring animation on press (scale 0.95f)
- Low bouncy damping with low stiffness for organic feel
- Consistent 8dp elevation with 2dp outline border

**ExpressiveSlider:**
- Standard slider with Material 3 Expressive theming
- Configurable track and thumb colors

**ProgressionSlider:**
- Specialized slider for -10 to +10 progression/regression values
- Dynamic color changes based on value:
  - Negative: Error color (red)
  - Positive: OnSurface color
  - Zero: OnSurfaceVariant color
- Shows +/- prefix on value display

---

## 2. Modified Screens

### EnhancedMainScreen.kt
**Major changes:**
- **Global TopBar System:** Added dynamic title and action support
  - New `topBarTitle` state flow from ViewModel
  - New `topBarActions` state flow for screen-specific actions
  - New `topBarBackAction` for custom back button behavior
- **Conditional Back Button:** Shows back button on non-main-tab screens
- **Dynamic Screen Titles:** Title updates based on current screen/state
- **Subtitle Branding:** Added "Vitruvian Project Phoenix" as persistent subtitle with gradient styling

### ActiveWorkoutScreen.kt
**Major changes:**
- **Removed Local TopBar:** Scaffold with TopAppBar removed
- **Global Header Integration:** Now uses global TopBar via `viewModel.updateTopBarTitle()`
- **Custom Back Action:** Registers back action with ViewModel for proper navigation handling
- **System Back Handler:** Added BackHandler for proper workout exit confirmation
- **DisposableEffect:** Cleanup for back action when leaving screen

### RoutinesTab.kt
**Major changes:**
- **Removed "My Routines" Header:** Header text removed (uses global TopBar now)
- **FAB Change:** Changed from `ExtendedFloatingActionButton` to regular `FloatingActionButton`
  - Removed "New Routine" text label
  - Simplified to icon-only (Add icon)
  - Changed shape from 28dp to 16dp rounded corners
- **Card Interaction Overhaul:**
  - Changed from immediate workout start to expandable cards
  - Added `expanded` state for showing/hiding details
  - Removed spring scale animation on press
  - Card now expands to show exercise details
  - Action buttons appear on expansion
- **Text Wrapping:** Limited text wrapping for action buttons

### InsightsTab.kt
**Major changes - Complete visualization overhaul:**

| Old Component | New Component | Change Type |
|---------------|---------------|-------------|
| `TrainingBalanceCard` | `MuscleBalanceRadarCard` | Replaced with radar chart |
| `ProgressVelocityCard` | Removed | Functionality replaced |
| `ConsistencyScoreCard` | `ConsistencyGaugeCard` | Replaced with gauge chart |
| `WeeklyComparisonCard` | `VolumeVsIntensityCard` | New combo chart |
| N/A | `WorkoutModeDistributionCard` | New donut chart |

### HistoryAndSettingsTabs.kt
**Major changes:**
- **Removed Local Headers:** Both "Workout History" and "Settings" headers removed
- **Global Scaffold Integration:** Uses `onSetTitle` callback to set global title
- **Header Row Commented Out:** Refresh button row disabled (potential pull-to-refresh replacement)

### HomeScreen.kt
**Changes:**
- Integration with global header system
- Various Material 3 Expressive styling updates

---

## 3. Chart/Visualization Changes

### ImprovedInsightsComponents.kt
**Complete Rewrite - Line count reduced from ~600 to ~270 lines**

**Old Implementation:**
- Linear progress bars for training balance
- Circular indicators for consistency
- Text-based metrics

**New Implementation:**
- **Radar Chart** for muscle balance visualization
- **Gauge Chart** for workout consistency
- **Chart Library Integration** using custom chart components
- Normalized muscle group names (Chest, Back, Legs, Shoulders, Arms, Core)
- Relative frequency calculations (0.0-1.0 scale relative to max category)

### Chart Component Updates

**AreaChart.kt, ComboChart.kt, GaugeChart.kt, RadarChart.kt:**
- Various refinements for the new insights system
- Integration with the improved data models

**WorkoutMetricsDetailChart.kt:**
- Updates to support new metrics visualization

---

## 4. Theme and Styling Changes

### Material3Expressive.kt
**New additions:**

```kotlin
object ExpressiveMotion {
    // Standard spring for most interactions
    val SpringDefault = spring<Float>(
        dampingRatio = Spring.DampingRatioLowBouncy,
        stiffness = Spring.StiffnessLow
    )

    // Snappy spring for quick transitions
    val SpringSnappy = spring<Float>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMedium
    )

    // Bouncy spring for emphasis
    val SpringBouncy = spring<Float>(
        dampingRatio = Spring.DampingRatioHighBouncy,
        stiffness = Spring.StiffnessLow
    )
}
```

### Theme.kt
- Various Material 3 color scheme refinements
- Integration with expressive motion system

---

## 5. Navigation and Layout Changes

### NavGraph.kt
- Updates to support new global TopBar system
- Route handling improvements

### Global TopBar Architecture
**New Pattern Introduced:**

```kotlin
// In MainViewModel
private val _topBarTitle = MutableStateFlow("Vitruvian Project Phoenix")
private val _topBarActions = MutableStateFlow<List<TopBarAction>>(emptyList())
private val _topBarBackAction = MutableStateFlow<(() -> Unit)?>(null)

// New data class
data class TopBarAction(
    val icon: ImageVector,
    val description: String,
    val onClick: () -> Unit
)
```

Screens now set their titles/actions via ViewModel rather than rendering their own TopBars.

---

## 6. Component-Level Changes

### AnalyticsCharts.kt
- Refinements to chart rendering
- Integration with new insights components

### CompactNumberPicker.kt
- Minor styling updates

### ConnectingOverlay.kt
- UI refinements

### ConnectionErrorDialog.kt
- Styling improvements

### ExercisePickerDialog.kt
- UI updates for Material 3 Expressive

### PRCelebrationAnimation.kt
- Animation refinements

### CountdownCard.kt
- Visual updates

### RestTimerCard.kt
- UI refinements

### ExerciseEditDialog.kt
- Material 3 Expressive styling

### JustLiftScreen.kt
- Integration with global header

### SingleExerciseScreen.kt
- UI updates

### ProgramBuilderScreen.kt
- Various refinements

### WeeklyProgramsScreen.kt
- Material 3 Expressive integration

### DailyRoutinesScreen.kt
- UI updates

### WorkoutTab.kt
- Integration with new components

### ConnectionLogsScreen.kt
- Styling refinements

### HapticFeedbackEffect.kt
- Minor updates

---

## Summary of UI Philosophy Changes

1. **Centralized Navigation:** Move from per-screen TopBars to a global TopBar system
2. **Chart-First Insights:** Replace text/progress bars with visual charts (radar, gauge, donut, combo)
3. **Expressive Motion:** Consistent spring animations across the app
4. **Safety Visibility:** New dedicated UI for safety events during workouts
5. **Simplified Actions:** FABs simplified from extended labels to icon-only
6. **Expandable Cards:** Routine cards now expand for details instead of immediate action
