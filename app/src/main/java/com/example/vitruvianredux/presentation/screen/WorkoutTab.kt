package com.example.vitruvianredux.presentation.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import android.view.ViewGroup
import androidx.core.net.toUri
import android.widget.VideoView
import com.example.vitruvianredux.data.local.entity.ExerciseEntity
import com.example.vitruvianredux.data.local.entity.ExerciseVideoEntity
import com.example.vitruvianredux.data.repository.ExerciseRepository
import com.example.vitruvianredux.domain.model.*
import com.example.vitruvianredux.domain.model.HeuristicStatistics
import com.example.vitruvianredux.presentation.components.ExercisePickerDialog
import com.example.vitruvianredux.presentation.viewmodel.AutoStopUiState
import com.example.vitruvianredux.ui.theme.*
import kotlin.math.abs

@Composable
fun WorkoutTab(
    connectionState: ConnectionState,
    workoutState: WorkoutState,
    currentMetric: WorkoutMetric?,
    workoutParameters: WorkoutParameters,
    repCount: RepCount,
    repRanges: com.example.vitruvianredux.domain.usecase.RepRanges?,
    autoStopState: AutoStopUiState,
    weightUnit: WeightUnit,
    enableVideoPlayback: Boolean,
    exerciseRepository: ExerciseRepository,
    isWorkoutSetupDialogVisible: Boolean = false,
    hapticEvents: kotlinx.coroutines.flow.SharedFlow<HapticEvent>? = null,
    loadedRoutine: Routine? = null,
    currentExerciseIndex: Int = 0,
    autoplayEnabled: Boolean = false,
    kgToDisplay: (Float, WeightUnit) -> Float,
    displayToKg: (Float, WeightUnit) -> Float,
    formatWeight: (Float, WeightUnit) -> String,
    onScan: () -> Unit,
    onDisconnect: () -> Unit,
    onStartWorkout: () -> Unit,
    onStopWorkout: () -> Unit,
    onSkipRest: () -> Unit,
    onProceedFromSummary: () -> Unit = {},
    onResetForNewWorkout: () -> Unit,
    onStartNextExercise: () -> Unit = {},
    onUpdateParameters: (WorkoutParameters) -> Unit,
    onShowWorkoutSetupDialog: () -> Unit = {},
    onHideWorkoutSetupDialog: () -> Unit = {},
    modifier: Modifier = Modifier,
    showConnectionCard: Boolean = true,
    showWorkoutSetupCard: Boolean = true,
    heuristicStatistics: HeuristicStatistics? = null,
    safetyEventSummary: SafetyEventSummary? = null
) {
    // Haptic feedback effect
    hapticEvents?.let {
        HapticFeedbackEffect(hapticEvents = it)
    }

    // Gradient backgrounds (light and dark)
    val isDarkMode = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val lightGradient = Brush.verticalGradient(
        colors = listOf(
            Color(0xFFF8FAFC), // slate-50
            Color(0xFFF5F3FF), // purple-50
            Color(0xFFEFF6FF)  // blue-50
        )
    )
    val darkGradient = Brush.verticalGradient(
        colors = listOf(
            Color(0xFF0F172A), // slate-950
            Color(0xFF312E81), // purple-950
            Color(0xFF0F172A)  // slate-900
        )
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(if (isDarkMode) darkGradient else lightGradient)
    ) {
        // Show position bars at edges only when workout is active and metric is available
        val showPositionBars = connectionState is ConnectionState.Connected &&
            workoutState is WorkoutState.Active &&
            currentMetric != null

        // Left edge bar (Cable A / Left hand) - positioned absolutely at left edge
        if (showPositionBars) {
            VerticalCablePositionBar(
                label = "L",
                currentPosition = currentMetric.positionA,
                minPosition = repRanges?.minPosA,
                maxPosition = repRanges?.maxPosA,
                isActive = currentMetric.positionA > 0,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .width(40.dp)
                    .fillMaxHeight()
                    .padding(vertical = 8.dp, horizontal = 4.dp)
            )
        }

        // Right edge bar (Cable B / Right hand) - positioned absolutely at right edge
        if (showPositionBars) {
            VerticalCablePositionBar(
                label = "R",
                currentPosition = currentMetric.positionB,
                minPosition = repRanges?.minPosB,
                maxPosition = repRanges?.maxPosB,
                isActive = currentMetric.positionB > 0,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .width(40.dp)
                    .fillMaxHeight()
                    .padding(vertical = 8.dp, horizontal = 4.dp)
            )
        }

        // Center content column - with horizontal padding to avoid overlapping bars
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    start = if (showPositionBars) 56.dp else 20.dp, // 40dp bar + 4dp padding + 12dp spacing
                    end = if (showPositionBars) 56.dp else 20.dp,   // 40dp bar + 4dp padding + 12dp spacing
                    top = 0.dp,
                    bottom = 0.dp
                )
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
        // Connection Card (conditionally shown)
        if (showConnectionCard) {
            ConnectionCard(
                connectionState = connectionState,
                onScan = onScan,
                onDisconnect = onDisconnect
            )
        }

        if (connectionState is ConnectionState.Connected) {
            // Show safety warnings if present
            currentMetric?.let { metric ->
                if (metric.statusFlags.isNotEmpty()) {
                    SafetyStatusCard(metric.statusFlags)
                }
            }

            // Show setup button when in Idle state, otherwise show workout controls
            when (workoutState) {
                is WorkoutState.Idle -> {
                    if (showWorkoutSetupCard) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest), // Material 3 Expressive: Higher contrast
                            shape = RoundedCornerShape(20.dp), // Material 3 Expressive: More rounded (was 16dp)
                            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp), // Material 3 Expressive: Higher elevation (was 4dp)
                            border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)) // Material 3 Expressive: Thicker border (was 1dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(Spacing.medium)
                            ) {
                                Text(
                                    "Workout Setup",
                                    style = MaterialTheme.typography.titleLarge, // Material 3 Expressive: Larger (was titleMedium)
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(Spacing.small))
                                Button(
                                    onClick = onShowWorkoutSetupDialog,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(56.dp), // Material 3 Expressive: Taller button
                                    shape = RoundedCornerShape(20.dp), // Material 3 Expressive: More rounded (was 16dp)
                                    elevation = ButtonDefaults.buttonElevation(
                                        defaultElevation = 4.dp,
                                        pressedElevation = 2.dp
                                    )
                                ) {
                                    Icon(Icons.Default.Settings, contentDescription = "Configure workout settings")
                                    Spacer(modifier = Modifier.width(Spacing.small))
                                    Text(
                                        "Setup Workout",
                                        style = MaterialTheme.typography.titleLarge, // Material 3 Expressive: Larger text
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
                is WorkoutState.Error -> {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                        shape = RoundedCornerShape(20.dp), // Material 3 Expressive: More rounded (was 16dp)
                        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp), // Material 3 Expressive: Higher elevation (was 4dp)
                        border = BorderStroke(2.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.6f)) // Material 3 Expressive: Thicker border (was 1dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(Spacing.medium),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(Spacing.small)
                        ) {
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = "Workout error",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(48.dp)
                            )
                            Text(
                                "Workout Failed to Start",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(Spacing.small))
                            Text(
                                workoutState.message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(Spacing.small))
                            Text(
                                "Returning to previous screen...",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.7f),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
                is WorkoutState.Completed -> {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest), // Material 3 Expressive: Higher contrast
                        shape = RoundedCornerShape(20.dp), // Material 3 Expressive: More rounded (was 16dp)
                        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp), // Material 3 Expressive: Higher elevation (was 4dp)
                        border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)) // Material 3 Expressive: Thicker border (was 1dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(Spacing.medium),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(Spacing.small)
                        ) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = "Workout completed",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(48.dp)
                            )
                            Text(
                                "Workout Completed!",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(Spacing.small))

                            // Check if this is a routine with more exercises
                            val hasMoreExercises = loadedRoutine != null &&
                                currentExerciseIndex < (loadedRoutine.exercises.size - 1)

                            if (hasMoreExercises) {
                                // Show next exercise preview
                                val nextExercise = loadedRoutine.exercises[currentExerciseIndex + 1]

                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.primaryContainer
                                    ),
                                    shape = RoundedCornerShape(12.dp),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                                ) {
                                    Column(modifier = Modifier.padding(Spacing.medium)) {
                                        Text(
                                            "Next Exercise",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )

                                        Spacer(Modifier.height(Spacing.small))

                                        Text(
                                            nextExercise.exercise.name,
                                            style = MaterialTheme.typography.headlineSmall,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )

                                        Text(
                                            if (nextExercise.setReps.isEmpty()) {
                                                "AMRAP - As Many Reps As Possible"
                                            } else {
                                                "${nextExercise.setReps.size} sets x ${nextExercise.setReps.first()} reps"
                                            },
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )

                                        Spacer(Modifier.height(Spacing.medium))

                                        Button(
                                            onClick = onStartNextExercise,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(56.dp), // Material 3 Expressive: Taller button
                                            shape = RoundedCornerShape(20.dp), // Material 3 Expressive: More rounded (was 16dp)
                                            elevation = ButtonDefaults.buttonElevation(
                                                defaultElevation = 4.dp,
                                                pressedElevation = 2.dp
                                            )
                                        ) {
                                            Text(
                                                "Start Next Exercise",
                                                style = MaterialTheme.typography.titleLarge, // Material 3 Expressive: Larger text
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            } else {
                                // Last exercise or not a routine - show "Start New Workout"
                                Button(
                                    onClick = onResetForNewWorkout,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(56.dp), // Material 3 Expressive: Taller button
                                    shape = RoundedCornerShape(20.dp), // Material 3 Expressive: More rounded (was 16dp)
                                    elevation = ButtonDefaults.buttonElevation(
                                        defaultElevation = 4.dp,
                                        pressedElevation = 2.dp
                                    )
                                ) {
                                    Icon(Icons.Default.Refresh, contentDescription = "Start new workout")
                                    Spacer(modifier = Modifier.width(Spacing.small))
                                    Text(
                                        "Start New Workout",
                                        style = MaterialTheme.typography.titleLarge, // Material 3 Expressive: Larger text
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
                is WorkoutState.Active -> {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer), // Material 3 Expressive: Use primary container for active state
                        shape = RoundedCornerShape(20.dp), // Material 3 Expressive: More rounded (was 16dp)
                        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp), // Material 3 Expressive: Higher elevation (was 4dp)
                        border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)) // Material 3 Expressive: Thicker border (was 1dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(Spacing.medium)
                        ) {
                            Text(
                                "Workout Active",
                                style = MaterialTheme.typography.titleLarge, // Material 3 Expressive: Larger (was titleMedium)
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Spacer(modifier = Modifier.height(Spacing.small))

                            if (workoutParameters.isJustLift) {
                                JustLiftAutoStopCard(autoStopState = autoStopState)
                                Spacer(modifier = Modifier.height(Spacing.medium))
                            }

                            Button(
                                onClick = onStopWorkout,
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Stop workout")
                                Spacer(modifier = Modifier.width(Spacing.small))
                                Text("Stop Workout")
                            }
                        }
                    }
                }
                else -> {}
            }

            // Display state-specific cards (only non-overlay cards)
            when (workoutState) {
                is WorkoutState.Active -> {
                    // Show rep counter first (docked at top) so it's always visible
                    RepCounterCard(repCount = repCount, workoutParameters = workoutParameters)

                    // Safety HUD - prominently displayed above tabs when warnings present
                    currentMetric?.let { metric ->
                        if (metric.statusFlags.isNotEmpty()) {
                            SafetyHUD(statusFlags = metric.statusFlags)
                        }
                    }

                    // Tab state for Video/Data switching
                    var selectedTabIndex by remember { mutableIntStateOf(0) }
                    val hapticFeedback = LocalHapticFeedback.current
                    val tabs = listOf("Video", "Data")

                    // TabRow for Video/Data switching
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest),
                        shape = RoundedCornerShape(20.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            // Exercise header (always visible above tabs)
                            val currentExercise = loadedRoutine?.exercises?.getOrNull(currentExerciseIndex)
                            var exerciseEntity by remember { mutableStateOf<ExerciseEntity?>(null) }

                            LaunchedEffect(currentExercise?.exercise?.id, workoutParameters.selectedExerciseId) {
                                val exerciseId = currentExercise?.exercise?.id ?: workoutParameters.selectedExerciseId
                                if (exerciseId != null) {
                                    exerciseEntity = exerciseRepository.getExerciseById(exerciseId)
                                }
                            }

                            Text(
                                text = currentExercise?.exercise?.name ?: exerciseEntity?.name ?: "Exercise",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(Spacing.medium)
                            )

                            // Tab Row
                            TabRow(
                                selectedTabIndex = selectedTabIndex,
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                contentColor = MaterialTheme.colorScheme.onSurface,
                                indicator = { tabPositions ->
                                    if (selectedTabIndex < tabPositions.size) {
                                        TabRowDefaults.SecondaryIndicator(
                                            modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTabIndex]),
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            ) {
                                tabs.forEachIndexed { index, title ->
                                    Tab(
                                        selected = selectedTabIndex == index,
                                        onClick = {
                                            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                            selectedTabIndex = index
                                        },
                                        text = {
                                            Text(
                                                text = title,
                                                fontWeight = if (selectedTabIndex == index) FontWeight.Bold else FontWeight.Normal
                                            )
                                        },
                                        icon = {
                                            Icon(
                                                imageVector = if (index == 0) Icons.Default.PlayArrow else Icons.Default.Analytics,
                                                contentDescription = title
                                            )
                                        }
                                    )
                                }
                            }

                            // Tab content
                            when (selectedTabIndex) {
                                0 -> {
                                    // Video Tab - Show video player
                                    VideoTabContent(
                                        loadedRoutine = loadedRoutine,
                                        currentExerciseIndex = currentExerciseIndex,
                                        workoutParameters = workoutParameters,
                                        exerciseRepository = exerciseRepository,
                                        enableVideoPlayback = enableVideoPlayback,
                                        formatWeight = { weight -> formatWeight(weight, weightUnit) },
                                        kgToDisplay = { weight -> kgToDisplay(weight, weightUnit) },
                                        weightUnit = weightUnit
                                    )
                                }
                                1 -> {
                                    // Data Tab - Show live metrics
                                    DataTabContent(
                                        currentMetric = currentMetric,
                                        repCount = repCount,
                                        weightUnit = weightUnit,
                                        formatWeight = formatWeight
                                    )
                                }
                            }
                        }
                    }
                }
                else -> {}
            }
        }

        // OVERLAYS - These float on top of all content, always visible without scrolling
        // Don't show countdown overlay for Just Lift mode
        when (workoutState) {
            is WorkoutState.Countdown -> {
                if (!workoutParameters.isJustLift) {
                    CountdownCard(secondsRemaining = workoutState.secondsRemaining)
                }
            }
            is WorkoutState.SetSummary -> {
                com.example.vitruvianredux.presentation.components.SetSummaryCard(
                    metrics = workoutState.metrics,
                    peakPower = workoutState.peakPower,
                    averagePower = workoutState.averagePower,
                    repCount = workoutState.repCount,
                    weightUnit = weightUnit,
                    formatWeight = formatWeight,
                    onContinue = onProceedFromSummary,
                    autoplayEnabled = autoplayEnabled,
                    configuredPerCableKg = workoutParameters.weightPerCableKg,
                    heuristics = heuristicStatistics,
                    safetyEvents = safetyEventSummary
                )
            }
            is WorkoutState.Resting -> {
                RestTimerCard(
                    restSecondsRemaining = workoutState.restSecondsRemaining,
                    nextExerciseName = workoutState.nextExerciseName,
                    isLastExercise = workoutState.isLastExercise,
                    currentSet = workoutState.currentSet,
                    totalSets = workoutState.totalSets,
                    nextExerciseWeight = workoutParameters.weightPerCableKg,
                    nextExerciseReps = workoutParameters.reps,
                    nextExerciseMode = workoutParameters.workoutType.displayName,
                    currentExerciseIndex = if (loadedRoutine != null) currentExerciseIndex else null,
                    totalExercises = loadedRoutine?.exercises?.size,
                    formatWeight = { weight -> formatWeight(weight, weightUnit) },
                    onSkipRest = onSkipRest,
                    onEndWorkout = onStopWorkout
                )
            }
            else -> {}
        }
        }
    }

    // Show the workout setup dialog
    if (isWorkoutSetupDialogVisible) {
        WorkoutSetupDialog(
            workoutParameters = workoutParameters,
            weightUnit = weightUnit,
            exerciseRepository = exerciseRepository,
            kgToDisplay = kgToDisplay,
            displayToKg = displayToKg,
            onUpdateParameters = onUpdateParameters,
            onStartWorkout = {
                onStartWorkout()
                onHideWorkoutSetupDialog()
            },
            onDismiss = onHideWorkoutSetupDialog
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutSetupDialog(
    workoutParameters: WorkoutParameters,
    weightUnit: WeightUnit,
    exerciseRepository: ExerciseRepository,
    kgToDisplay: (Float, WeightUnit) -> Float,
    displayToKg: (Float, WeightUnit) -> Float,
    onUpdateParameters: (WorkoutParameters) -> Unit,
    onStartWorkout: () -> Unit,
    onDismiss: () -> Unit
) {
    // State for exercise selection
    var selectedExercise by remember { mutableStateOf<ExerciseEntity?>(null) }
    var showExercisePicker by remember { mutableStateOf(false) }

    // State for mode selection
    var showModeMenu by remember { mutableStateOf(false) }
    var showModeSubSelector by remember { mutableStateOf(false) }
    var modeSubSelectorType by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(workoutParameters.selectedExerciseId) {
        workoutParameters.selectedExerciseId?.let { id ->
            exerciseRepository.getExerciseById(id).also { selectedExercise = it }
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest, // Material 3 Expressive: Higher contrast
        shape = RoundedCornerShape(28.dp), // Material 3 Expressive: Very rounded for dialogs (was 16dp)
        title = {
            Text(
                "Workout Setup",
                style = MaterialTheme.typography.headlineSmall, // Material 3 Expressive: Larger (was titleLarge)
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Spacing.small)
            ) {
                // Exercise Selection Card - Material 3 Expressive
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest), // Material 3 Expressive: Higher contrast
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp), // Material 3 Expressive: Higher elevation (was 4dp)
                    shape = RoundedCornerShape(20.dp), // Material 3 Expressive: More rounded (was 16dp)
                    border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)) // Material 3 Expressive: Thicker border (was 1dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text(
                            "Exercise",
                            style = MaterialTheme.typography.titleLarge, // Material 3 Expressive: Larger (was titleMedium)
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = { showExercisePicker = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(selectedExercise?.name ?: "Select Exercise")
                        }
                    }
                }

                val modeLabel = if (workoutParameters.isJustLift) "Base Mode (resistance profile)" else "Workout Mode"
                ExposedDropdownMenuBox(
                    expanded = showModeMenu,
                    onExpandedChange = { showModeMenu = !showModeMenu }
                ) {
                    OutlinedTextField(
                        value = workoutParameters.workoutType.displayName,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(modeLabel) },
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = showModeMenu)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(type = ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                        colors = OutlinedTextFieldDefaults.colors()
                    )
                    ExposedDropdownMenu(
                        expanded = showModeMenu,
                        onDismissRequest = { showModeMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Old School") },
                            onClick = {
                                onUpdateParameters(workoutParameters.copy(workoutType = WorkoutType.Program(ProgramMode.OldSchool)))
                                showModeMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Pump") },
                            onClick = {
                                onUpdateParameters(workoutParameters.copy(workoutType = WorkoutType.Program(ProgramMode.Pump)))
                                showModeMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Eccentric Only") },
                            onClick = {
                                onUpdateParameters(workoutParameters.copy(workoutType = WorkoutType.Program(ProgramMode.EccentricOnly)))
                                showModeMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = {
                                Row(
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Echo Mode")
                                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Navigate to next exercise")
                                }
                            },
                            onClick = {
                                showModeMenu = false
                                modeSubSelectorType = "Echo"
                                showModeSubSelector = true
                            }
                        )
                        DropdownMenuItem(
                            text = {
                                Row(
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("TUT")
                                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Navigate to next exercise")
                                }
                            },
                            onClick = {
                                showModeMenu = false
                                modeSubSelectorType = "TUT"
                                showModeSubSelector = true
                            }
                        )
                    }
                }

                // Weight Picker - Material 3 Expressive
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest), // Material 3 Expressive: Higher contrast
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp), // Material 3 Expressive: Higher elevation (was 4dp)
                    shape = RoundedCornerShape(20.dp), // Material 3 Expressive: More rounded (was 16dp)
                    border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)) // Material 3 Expressive: Thicker border (was 1dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        if (workoutParameters.workoutType is WorkoutType.Echo) {
                            Text(
                                "Weight per cable",
                                style = MaterialTheme.typography.titleLarge, // Material 3 Expressive: Larger (was titleMedium)
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "Adaptive",
                                style = MaterialTheme.typography.displaySmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                "Echo mode adapts weight to your output",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            val weightRange = if (weightUnit == WeightUnit.LB) 1..220 else 1..100
                            CompactNumberPicker(
                                value = kgToDisplay(workoutParameters.weightPerCableKg, weightUnit).toInt(),
                                onValueChange = { displayValue ->
                                    val kg = displayToKg(displayValue.toFloat(), weightUnit)
                                    onUpdateParameters(workoutParameters.copy(weightPerCableKg = kg))
                                },
                                range = weightRange,
                                label = "Weight per cable (${weightUnit.name.lowercase()})"
                            )
                        }
                    }
                }

                // Reps Picker - Material 3 Expressive
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest), // Material 3 Expressive: Higher contrast
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp), // Material 3 Expressive: Higher elevation (was 4dp)
                    shape = RoundedCornerShape(20.dp), // Material 3 Expressive: More rounded (was 16dp)
                    border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)) // Material 3 Expressive: Thicker border (was 1dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        if (!workoutParameters.isJustLift) {
                            CompactNumberPicker(
                                value = workoutParameters.reps,
                                onValueChange = { reps ->
                                    onUpdateParameters(workoutParameters.copy(reps = reps))
                                },
                                range = 1..50,
                                label = "Target reps"
                            )
                        } else {
                            Text(
                                "Target reps",
                                style = MaterialTheme.typography.titleLarge, // Material 3 Expressive: Larger (was titleMedium)
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "N/A",
                                style = MaterialTheme.typography.displaySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                "Just Lift mode doesn't use target reps",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Progression/Regression UI (only for certain modes)
                val programMode = (workoutParameters.workoutType as? WorkoutType.Program)?.mode
                val isProgramMode = programMode != null
                if (isProgramMode && (programMode == ProgramMode.Pump ||
                    programMode == ProgramMode.OldSchool ||
                    programMode == ProgramMode.EccentricOnly ||
                    programMode == ProgramMode.TUT ||
                    programMode == ProgramMode.TUTBeast)
                ) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest), // Material 3 Expressive: Higher contrast
                        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp), // Material 3 Expressive: Higher elevation (was 4dp)
                        shape = RoundedCornerShape(20.dp), // Material 3 Expressive: More rounded (was 16dp)
                        border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)) // Material 3 Expressive: Thicker border (was 1dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            Text(
                                "Progression/Regression",
                                style = MaterialTheme.typography.titleLarge, // Material 3 Expressive: Larger (was titleMedium)
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            // FilterChips for Progression/Regression
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                FilterChip(
                                    selected = workoutParameters.progressionRegressionKg >= 0,
                                    onClick = {
                                        onUpdateParameters(
                                            workoutParameters.copy(
                                                progressionRegressionKg = abs(workoutParameters.progressionRegressionKg)
                                            )
                                        )
                                    },
                                    label = { Text("Prog") },
                                    leadingIcon = if (workoutParameters.progressionRegressionKg >= 0) {
                                        { Icon(Icons.Default.KeyboardArrowUp, null, Modifier.size(16.dp)) }
                                    } else null
                                )

                                FilterChip(
                                    selected = workoutParameters.progressionRegressionKg < 0,
                                    onClick = {
                                        onUpdateParameters(
                                            workoutParameters.copy(
                                                progressionRegressionKg = -abs(workoutParameters.progressionRegressionKg)
                                            )
                                        )
                                    },
                                    label = { Text("Regr") },
                                    leadingIcon = if (workoutParameters.progressionRegressionKg < 0) {
                                        { Icon(Icons.Default.KeyboardArrowDown, null, Modifier.size(16.dp)) }
                                    } else null
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Amount picker
                            val progRegressionRange = if (weightUnit == WeightUnit.LB) 0..6 else 0..3
                            CompactNumberPicker(
                                value = kgToDisplay(abs(workoutParameters.progressionRegressionKg), weightUnit).toInt(),
                                onValueChange = { displayValue ->
                                    val kg = displayToKg(displayValue.toFloat(), weightUnit)
                                    val isProgression = workoutParameters.progressionRegressionKg >= 0
                                    onUpdateParameters(
                                        workoutParameters.copy(
                                            progressionRegressionKg = if (isProgression) kg else -kg
                                        )
                                    )
                                },
                                range = progRegressionRange,
                                label = "Amount (${weightUnit.name.lowercase()})"
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Just Lift")
                    Switch(
                        checked = workoutParameters.isJustLift,
                        onCheckedChange = { checked ->
                            onUpdateParameters(workoutParameters.copy(isJustLift = checked))
                        }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Finish At Top")
                    Switch(
                        checked = workoutParameters.stopAtTop,
                        onCheckedChange = { checked ->
                            onUpdateParameters(workoutParameters.copy(stopAtTop = checked))
                        },
                        enabled = !workoutParameters.isJustLift
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onStartWorkout,
                enabled = selectedExercise != null
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = "Start workout")
                Spacer(modifier = Modifier.width(Spacing.small))
                Text("Start Workout")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )

    // Exercise Picker Dialog
    ExercisePickerDialog(
        showDialog = showExercisePicker,
        onDismiss = { showExercisePicker = false },
        onExerciseSelected = { exercise ->
            onUpdateParameters(workoutParameters.copy(selectedExerciseId = exercise.id))
        },
        exerciseRepository = exerciseRepository
    )

    // Mode Sub-Selector Dialog
    if (showModeSubSelector && modeSubSelectorType != null) {
        ModeSubSelectorDialog(
            type = modeSubSelectorType!!,
            workoutParameters = workoutParameters,
            onDismiss = { showModeSubSelector = false },
            onSelect = { mode, eccentricLoad ->
                val workoutType = mode.toWorkoutType(eccentricLoad ?: EccentricLoad.LOAD_100)
                onUpdateParameters(workoutParameters.copy(workoutType = workoutType))
            }
        )
    }
}

/**
 * Compact Number Picker with +/- buttons and drag support
 * Height: 80dp for easy interaction
 */
@Composable
fun CompactNumberPicker(
    value: Int,
    onValueChange: (Int) -> Unit,
    range: IntRange,
    modifier: Modifier = Modifier,
    label: String = ""
) {
    val haptic = LocalHapticFeedback.current
    var accumulatedDrag by remember { mutableStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (label.isNotEmpty()) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Minus button
            IconButton(
                onClick = {
                    if (value > range.first) {
                        onValueChange(value - 1)
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    }
                },
                enabled = value > range.first
            ) {
                Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Decrease")
            }

            // Center draggable area with value
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = {
                                isDragging = true
                                accumulatedDrag = 0f
                            },
                            onDragEnd = {
                                isDragging = false
                                accumulatedDrag = 0f
                            },
                            onDragCancel = {
                                isDragging = false
                                accumulatedDrag = 0f
                            },
                            onDrag = { _, dragAmount ->
                                accumulatedDrag -= dragAmount.y
                                if (abs(accumulatedDrag) >= 3f) {
                                    val steps = (accumulatedDrag / 3f).toInt()
                                    val newValue = (value + steps).coerceIn(range)
                                    if (newValue != value) {
                                        onValueChange(newValue)
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    }
                                    accumulatedDrag = 0f
                                }
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Show adjacent numbers only while dragging
                    if (isDragging && value < range.last) {
                        Text(
                            text = (value + 1).toString(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        )
                    }
                    
                    // Current value
                    Text(
                        text = value.toString(),
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    
                    // Show adjacent numbers only while dragging
                    if (isDragging && value > range.first) {
                        Text(
                            text = (value - 1).toString(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        )
                    }
                }
            }

            // Plus button
            IconButton(
                onClick = {
                    if (value < range.last) {
                        onValueChange(value + 1)
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    }
                },
                enabled = value < range.last
            ) {
                Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Increase")
            }
        }
    }
}

/**
 * Mode Sub-Selector Dialog for hierarchical workout modes (TUT and Echo)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModeSubSelectorDialog(
    type: String,
    workoutParameters: WorkoutParameters,
    onDismiss: () -> Unit,
    onSelect: (WorkoutMode, EccentricLoad?) -> Unit
) {
    when (type) {
        "TUT" -> {
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text("Select TUT Variant", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) }, // Material 3 Expressive: Larger
                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest, // Material 3 Expressive: Higher contrast
                shape = RoundedCornerShape(28.dp), // Material 3 Expressive: Very rounded for dialogs (was 16dp)
                text = {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(Spacing.small)
                    ) {
                        OutlinedButton(
                            onClick = { onSelect(WorkoutMode.TUT, null) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("TUT")
                        }
                        OutlinedButton(
                            onClick = { onSelect(WorkoutMode.TUTBeast, null) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("TUT Beast")
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                }
            )
        }
        "Echo" -> {
            var selectedEchoLevel by remember { 
                mutableStateOf(
                    if (workoutParameters.workoutType is WorkoutType.Echo) {
                        workoutParameters.workoutType.level
                    } else {
                        EchoLevel.HARD
                    }
                )
            }
            var selectedEccentricLoad by remember { 
                mutableStateOf(
                    if (workoutParameters.workoutType is WorkoutType.Echo) {
                        workoutParameters.workoutType.eccentricLoad
                    } else {
                        EccentricLoad.LOAD_100
                    }
                )
            }
            var showEchoLevelMenu by remember { mutableStateOf(false) }
            var showEccentricMenu by remember { mutableStateOf(false) }

            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text("Echo Mode Configuration", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) }, // Material 3 Expressive: Larger
                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest, // Material 3 Expressive: Higher contrast
                shape = RoundedCornerShape(28.dp), // Material 3 Expressive: Very rounded for dialogs (was 16dp)
                text = {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(Spacing.small)
                    ) {
                        Text(
                            "Echo adapts resistance to your output",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(Spacing.small))

                        // Echo Level Dropdown
                        ExposedDropdownMenuBox(
                            expanded = showEchoLevelMenu,
                            onExpandedChange = { showEchoLevelMenu = !showEchoLevelMenu }
                        ) {
                            OutlinedTextField(
                                value = selectedEchoLevel.displayName,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Echo Level") },
                                trailingIcon = {
                                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = showEchoLevelMenu)
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor(type = ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                            )
                            ExposedDropdownMenu(
                                expanded = showEchoLevelMenu,
                                onDismissRequest = { showEchoLevelMenu = false }
                            ) {
                                listOf(EchoLevel.HARD, EchoLevel.HARDER, EchoLevel.HARDEST, EchoLevel.EPIC).forEach { level ->
                                    DropdownMenuItem(
                                        text = { Text(level.displayName) },
                                        onClick = {
                                            selectedEchoLevel = level
                                            showEchoLevelMenu = false
                                        }
                                    )
                                }
                            }
                        }

                        // Eccentric Load Dropdown
                        ExposedDropdownMenuBox(
                            expanded = showEccentricMenu,
                            onExpandedChange = { showEccentricMenu = !showEccentricMenu }
                        ) {
                            OutlinedTextField(
                                value = selectedEccentricLoad.displayName,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Eccentric Load") },
                                trailingIcon = {
                                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = showEccentricMenu)
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor(type = ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                            )
                            ExposedDropdownMenu(
                                expanded = showEccentricMenu,
                                onDismissRequest = { showEccentricMenu = false }
                            ) {
                                listOf(
                                    EccentricLoad.LOAD_0,
                                    EccentricLoad.LOAD_50,
                                    EccentricLoad.LOAD_75,
                                    EccentricLoad.LOAD_100,
                                    EccentricLoad.LOAD_125,
                                    EccentricLoad.LOAD_150
                                ).forEach { load ->
                                    DropdownMenuItem(
                                        text = { Text(load.displayName) },
                                        onClick = {
                                            selectedEccentricLoad = load
                                            showEccentricMenu = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            // Update both mode and eccentric load
                            onSelect(WorkoutMode.Echo(selectedEchoLevel), selectedEccentricLoad)
                        }
                    ) {
                        Text("Select")
                    }
                },
                dismissButton = {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectionCard(
    connectionState: ConnectionState,
    onScan: () -> Unit,
    onDisconnect: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest), // Material 3 Expressive: Higher contrast
        shape = RoundedCornerShape(20.dp), // Material 3 Expressive: More rounded (was 16dp)
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp), // Material 3 Expressive: Higher elevation (was 4dp)
        border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)) // Material 3 Expressive: Thicker border (was 1dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.medium)
        ) {
            Text(
                "Connection",
                style = MaterialTheme.typography.titleLarge, // Material 3 Expressive: Larger (was titleMedium)
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(Spacing.small))

            when (connectionState) {
                is ConnectionState.Disconnected -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Not connected", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Button(
                            onClick = onScan
                        ) {
                            Icon(Icons.Default.Search, contentDescription = "Scan for devices")
                            Spacer(modifier = Modifier.width(Spacing.small))
                            Text("Scan")
                        }
                    }
                }
                is ConnectionState.Scanning -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(Spacing.small))
                        Text("Scanning for devices...")
                    }
                }
                is ConnectionState.Connecting -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(Spacing.small))
                        Text("Connecting...")
                    }
                }
                is ConnectionState.Disconnecting -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(Spacing.small))
                        Text("Disconnecting...")
                    }
                }
                is ConnectionState.Connected -> {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    connectionState.deviceName,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    connectionState.deviceAddress,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(onClick = onDisconnect) {
                                Icon(Icons.Default.Close, contentDescription = "Disconnect")
                            }
                        }
                    }
                }
                is ConnectionState.Error -> {
                    Text(
                        "Error: ${connectionState.message}",
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
fun JustLiftAutoStopCard(autoStopState: AutoStopUiState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (autoStopState.isActive) {
                MaterialTheme.colorScheme.errorContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerHighest // Material 3 Expressive: Higher contrast
            }
        ),
        shape = RoundedCornerShape(20.dp), // Material 3 Expressive: More rounded (was 16dp)
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp) // Material 3 Expressive: Higher elevation (was 2dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.medium),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PanTool,
                    contentDescription = "Hands on handles indicator",
                    tint = if (autoStopState.isActive) {
                        MaterialTheme.colorScheme.onErrorContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
                Spacer(Modifier.width(Spacing.small))
                Text(
                    text = if (autoStopState.isActive) {
                        "Stopping in ${autoStopState.secondsRemaining}s..."
                    } else {
                        "Auto-Stop Ready"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (autoStopState.isActive) {
                        MaterialTheme.colorScheme.onErrorContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }

            Spacer(modifier = Modifier.height(Spacing.small))

            LinearProgressIndicator(
                progress = { autoStopState.progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp),
                color = if (autoStopState.isActive) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.outline
                }
            )

            Spacer(modifier = Modifier.height(Spacing.small))

            Text(
                text = "Put handles down for 5 seconds to stop",
                style = MaterialTheme.typography.bodySmall,
                color = if (autoStopState.isActive) {
                    MaterialTheme.colorScheme.onErrorContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * Video Player - AndroidView wrapper for VideoView
 * Displays exercise demonstration videos in a loop
 */
@Composable
fun VideoPlayer(
    videoUrl: String,
    modifier: Modifier = Modifier
) {
    var isLoading by remember { mutableStateOf(true) }
    var hasError by remember { mutableStateOf(false) }

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        AndroidView(
            factory = { ctx ->
                VideoView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )

                    // Set video URI (no controls - just loop like a GIF)
                    try {
                        setVideoURI(videoUrl.toUri())

                        // Set up listeners
                        setOnPreparedListener { mp ->
                            isLoading = false
                            mp.isLooping = true
                            start()
                        }

                        setOnErrorListener { _, _, _ ->
                            isLoading = false
                            hasError = true
                            true
                        }
                    } catch (_e: Exception) {
                        isLoading = false
                        hasError = true
                    }
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Loading indicator
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(32.dp),
                color = MaterialTheme.colorScheme.primary
            )
        }

        // Error placeholder
        if (hasError) {
            Icon(
                imageVector = Icons.Default.PlayCircle,
                contentDescription = "Video unavailable",
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(48.dp)
            )
        }
    }
}

/**
 * Current Exercise Card - Shows exercise details during active workout
 * Displays exercise name, reps, weight, mode, and video if available
 */
@Composable
fun CurrentExerciseCard(
    loadedRoutine: Routine?,
    currentExerciseIndex: Int,
    workoutParameters: WorkoutParameters,
    exerciseRepository: ExerciseRepository,
    enableVideoPlayback: Boolean,
    formatWeight: (Float) -> String,
    kgToDisplay: (Float) -> Float,
    weightUnit: WeightUnit
) {
    // Get current exercise from routine if available
    val currentExercise = loadedRoutine?.exercises?.getOrNull(currentExerciseIndex)

    // Get exercise entity for video
    var exerciseEntity by remember { mutableStateOf<ExerciseEntity?>(null) }
    var videoEntity by remember { mutableStateOf<ExerciseVideoEntity?>(null) }

    // Load exercise and video data
    LaunchedEffect(currentExercise?.exercise?.id, workoutParameters.selectedExerciseId) {
        val exerciseId = currentExercise?.exercise?.id ?: workoutParameters.selectedExerciseId
        if (exerciseId != null) {
            exerciseEntity = exerciseRepository.getExerciseById(exerciseId)
            val videos = exerciseRepository.getVideos(exerciseId)
            videoEntity = videos.firstOrNull()
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest), // Material 3 Expressive: Higher contrast
        shape = RoundedCornerShape(20.dp), // Material 3 Expressive: More rounded (was 16dp)
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp), // Material 3 Expressive: Higher elevation (was 4dp)
        border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)) // Material 3 Expressive: Thicker border (was 1dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.medium)
        ) {
            // Exercise name
            Text(
                text = currentExercise?.exercise?.name ?: exerciseEntity?.name ?: "Exercise",
                style = MaterialTheme.typography.headlineSmall, // Material 3 Expressive: Larger (was titleLarge)
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(Spacing.small))

            // Exercise details (reps, weight, mode)
            if (currentExercise != null) {
                // Routine exercise - show full details
                val repsText = if (currentExercise.setReps.isEmpty()) {
                    "No sets configured"
                } else if (currentExercise.setReps.all { it == currentExercise.setReps.first() }) {
                    "${currentExercise.setReps.size}x${currentExercise.setReps.first()}"
                } else {
                    currentExercise.setReps.joinToString(", ")
                }

                // For Echo mode, show "Adaptive" instead of weight (Issue #109)
                val descriptionText = if (currentExercise.workoutType is WorkoutType.Echo) {
                    // Echo mode: Show reps, cable config, and mode - no weight
                    val cableText = when (currentExercise.cableConfig) {
                        CableConfiguration.SINGLE -> " (Single)"
                        CableConfiguration.DOUBLE -> " (Double)"
                        else -> ""
                    }
                    "$repsText reps$cableText - ${currentExercise.workoutType.displayName} - Adaptive"
                } else {
                    // Non-Echo mode: Show reps, weight, cable config, and mode
                    val baseWeightText = if (currentExercise.setWeightsPerCableKg.isNotEmpty()) {
                        val displayWeights = currentExercise.setWeightsPerCableKg.map { kgToDisplay(it) }
                        val minWeight = displayWeights.minOrNull() ?: 0f
                        val maxWeight = displayWeights.maxOrNull() ?: 0f
                        val weightSuffix = if (weightUnit == WeightUnit.LB) "lbs" else "kg"

                        if (minWeight == maxWeight) {
                            "%.1f %s".format(minWeight, weightSuffix)
                        } else {
                            "%.1f-%.1f %s".format(minWeight, maxWeight, weightSuffix)
                        }
                    } else {
                        formatWeight(currentExercise.weightPerCableKg)
                    }

                    val weightText = when (currentExercise.cableConfig) {
                        CableConfiguration.SINGLE -> "$baseWeightText (Single)"
                        CableConfiguration.DOUBLE -> "$baseWeightText/cable (Double)"
                        else -> baseWeightText
                    }

                    "$repsText @ $weightText - ${currentExercise.workoutType.displayName}"
                }

                Text(
                    text = descriptionText,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            } else {
                // Just Lift mode - show basic details
                // For Echo mode, show "Adaptive" instead of weight (Issue #109)
                val descriptionText = if (workoutParameters.workoutType is WorkoutType.Echo) {
                    "${workoutParameters.reps} reps - ${workoutParameters.workoutType.displayName} - Adaptive"
                } else {
                    "${workoutParameters.reps} reps @ ${formatWeight(workoutParameters.weightPerCableKg)}/cable - ${workoutParameters.workoutType.displayName}"
                }

                Text(
                    text = descriptionText,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            // Video player (if video available)
            if (enableVideoPlayback) {
                videoEntity?.let { video ->
                    Spacer(modifier = Modifier.height(Spacing.medium))

                    VideoPlayer(
                        videoUrl = video.videoUrl,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .clip(RoundedCornerShape(12.dp))
                    )
                }
            }
        }
    }
}

@Composable
fun RepCounterCard(repCount: RepCount, workoutParameters: WorkoutParameters) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer), // Material 3 Expressive: Use primary container for emphasis
        shape = RoundedCornerShape(24.dp), // Material 3 Expressive: Very rounded for important card (was 16dp)
        elevation = CardDefaults.cardElevation(defaultElevation = 12.dp), // Material 3 Expressive: Very high elevation for emphasis (was 4dp)
        border = BorderStroke(3.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)) // Material 3 Expressive: Thicker border for emphasis (was 1dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.large),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val countText = if (repCount.isWarmupComplete) {
                repCount.workingReps.toString()
            } else {
                "${repCount.warmupReps} / ${workoutParameters.warmupReps}"
            }

            // Show AMRAP indicator when in AMRAP mode and warmup is complete
            if (workoutParameters.isAMRAP && repCount.isWarmupComplete) {
                Badge(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.padding(bottom = Spacing.small)
                ) {
                    Text(
                        text = "AMRAP",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            val labelText = when {
                !repCount.isWarmupComplete -> "WARMUP"
                workoutParameters.isAMRAP -> "REPS (As Many As Possible)"
                else -> "REPS"
            }

            Text(
                text = labelText,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(modifier = Modifier.height(Spacing.medium))

            Text(
                text = countText,
                style = MaterialTheme.typography.displayLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

@Composable
fun LiveMetricsCard(
    metric: WorkoutMetric,
    weightUnit: WeightUnit,
    formatWeight: (Float, WeightUnit) -> String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest), // Material 3 Expressive: Higher contrast
        shape = RoundedCornerShape(20.dp), // Material 3 Expressive: More rounded (was 16dp)
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp), // Material 3 Expressive: Higher elevation (was 4dp)
        border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)) // Material 3 Expressive: Thicker border (was 1dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.medium)
        ) {
            Text(
                "Live Metrics",
                style = MaterialTheme.typography.titleLarge, // Material 3 Expressive: Larger (was titleMedium)
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(Spacing.small))

            // Current Load - show per-cable resistance (totalLoad / 2)
            // For cable machines, each cable provides independent resistance
            Text(
                formatWeight(metric.totalLoad / 2f, weightUnit),
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text("Per Cable", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

            Spacer(modifier = Modifier.height(Spacing.medium))

            // Cable Position Bars - showing individual cable positions
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "Cable Positions",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = Spacing.extraSmall)
                )

                // Cable A Position Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "A",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(20.dp)
                    )
                    LinearProgressIndicator(
                        progress = { (metric.positionA / 1000f).coerceIn(0f, 1f) },
                        modifier = Modifier
                            .weight(1f)
                            .height(8.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                    )
                    Text(
                        "${metric.positionA}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(50.dp).padding(start = Spacing.extraSmall),
                        textAlign = TextAlign.End
                    )
                }

                Spacer(modifier = Modifier.height(Spacing.extraSmall))

                // Cable B Position Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "B",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(20.dp)
                    )
                    LinearProgressIndicator(
                        progress = { (metric.positionB / 1000f).coerceIn(0f, 1f) },
                        modifier = Modifier
                            .weight(1f)
                            .height(8.dp),
                        color = MaterialTheme.colorScheme.secondary,
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                    )
                    Text(
                        "${metric.positionB}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(50.dp).padding(start = Spacing.extraSmall),
                        textAlign = TextAlign.End
                    )
                }
            }
        }
    }
}

/**
 * Vertical cable position bar for left/right side display
 * Shows current position filling from bottom up, with range markers
 */
@Composable
fun VerticalCablePositionBar(
    label: String,
    currentPosition: Int,
    minPosition: Int?,
    maxPosition: Int?,
    isActive: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxHeight(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Label at top
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 4.dp)
        )

        // Vertical bar container - fills available height between label and value
        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .width(40.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            val barHeight = maxHeight
            
            // Calculate positions as fractions (0.0 to 1.0) from bottom
            val maxPos = 1000 // Typical max position value
            val currentProgress = (currentPosition / maxPos.toFloat()).coerceIn(0f, 1f)
            val minProgress = minPosition?.let { (it / maxPos.toFloat()).coerceIn(0f, 1f) }
            val maxProgress = maxPosition?.let { (it / maxPos.toFloat()).coerceIn(0f, 1f) }

            // Range zone visualization (if min/max are available)
            // This shows the calibrated workout range with subtle color
            if (minProgress != null && maxProgress != null && maxProgress > minProgress) {
                val rangeHeight = maxProgress - minProgress
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(barHeight * rangeHeight)
                        .align(Alignment.BottomCenter)
                        .offset(y = -barHeight * minProgress)
                        .background(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                        )
                )
            }

            // Current position fill (from bottom up)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(barHeight * currentProgress)
                    .align(Alignment.BottomCenter)
                    .background(
                        if (isActive) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                        }
                    )
            )

            // Range markers (subtle indicators at min/max positions)
            if (minProgress != null && maxProgress != null && maxProgress > minProgress) {
                // Min position marker
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp)
                        .align(Alignment.BottomCenter)
                        .offset(y = -barHeight * minProgress)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.6f))
                )
                
                // Max position marker
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp)
                        .align(Alignment.BottomCenter)
                        .offset(y = -barHeight * maxProgress)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.6f))
                )
            }
        }

        // Position value at bottom
        Text(
            text = "${currentPosition / 10}%",
            style = MaterialTheme.typography.labelSmall,
            color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

@Composable
fun SafetyStatusCard(statusFlags: Set<SampleStatus>) {
    val hasCritical = statusFlags.any { 
        it == SampleStatus.DELOAD_OCCURRED 
    }
    
    val cardColor = if (hasCritical) {
        MaterialTheme.colorScheme.errorContainer
    } else {
        MaterialTheme.colorScheme.tertiaryContainer
    }
    
    val contentColor = if (hasCritical) {
        MaterialTheme.colorScheme.onErrorContainer
    } else {
        MaterialTheme.colorScheme.onTertiaryContainer
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = cardColor),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (hasCritical) Icons.Default.Warning else Icons.Default.Info,
                    contentDescription = "Safety Alert",
                    tint = contentColor
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (hasCritical) "Safety Intervention" else "Safety Warning",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = contentColor
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            statusFlags.forEach { flag ->
                val message = when(flag) {
                    SampleStatus.DELOAD_WARN -> "Force cap approaching - Ease off!"
                    SampleStatus.DELOAD_OCCURRED -> "Force released due to overload/safety."
                    SampleStatus.ROM_OUTSIDE_HIGH -> "Extension limit reached (Top)."
                    SampleStatus.ROM_OUTSIDE_LOW -> "Retraction limit reached (Bottom)."
                    SampleStatus.SPOTTER_ACTIVE -> "Spotter active."
                    SampleStatus.ROM_UNLOAD_ACTIVE -> "Unload active."
                    else -> null
                }
                
                if (message != null) {
                    Text(
                        text = "• $message",
                        style = MaterialTheme.typography.bodyMedium,
                        color = contentColor
                    )
                }
            }
        }
    }
}

/**
 * Safety HUD - Prominently displays safety warnings during active workout.
 * Shows above the Video/Data tabs for maximum visibility.
 */
@Composable
fun SafetyHUD(statusFlags: Set<SampleStatus>) {
    val hasCritical = statusFlags.any {
        it == SampleStatus.DELOAD_OCCURRED
    }

    val backgroundColor = if (hasCritical) {
        MaterialTheme.colorScheme.errorContainer
    } else {
        MaterialTheme.colorScheme.tertiaryContainer
    }

    val contentColor = if (hasCritical) {
        MaterialTheme.colorScheme.onErrorContainer
    } else {
        MaterialTheme.colorScheme.onTertiaryContainer
    }

    val borderColor = if (hasCritical) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.tertiary
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(2.dp, borderColor.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Warning icon with animation potential
            Icon(
                imageVector = if (hasCritical) Icons.Default.Warning else Icons.Default.Info,
                contentDescription = "Safety Alert",
                tint = contentColor,
                modifier = Modifier.size(32.dp)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (hasCritical) "SAFETY ALERT" else "NOTICE",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = contentColor
                )

                // Show only the most important message for HUD brevity
                val primaryMessage = statusFlags.firstNotNullOfOrNull { flag ->
                    when (flag) {
                        SampleStatus.DELOAD_OCCURRED -> "Force released - overload protection"
                        SampleStatus.DELOAD_WARN -> "Ease off - approaching force limit"
                        SampleStatus.ROM_OUTSIDE_HIGH -> "Extension limit reached"
                        SampleStatus.ROM_OUTSIDE_LOW -> "Retraction limit reached"
                        SampleStatus.SPOTTER_ACTIVE -> "Spotter active"
                        SampleStatus.ROM_UNLOAD_ACTIVE -> "Unload active"
                        else -> null
                    }
                }

                primaryMessage?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = contentColor
                    )
                }

                // Show count if multiple warnings
                if (statusFlags.size > 1) {
                    Text(
                        text = "+${statusFlags.size - 1} more",
                        style = MaterialTheme.typography.bodySmall,
                        color = contentColor.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}

/**
 * Video tab content - shows exercise details and instructional video
 */
@Composable
fun VideoTabContent(
    loadedRoutine: Routine?,
    currentExerciseIndex: Int,
    workoutParameters: WorkoutParameters,
    exerciseRepository: ExerciseRepository,
    enableVideoPlayback: Boolean,
    formatWeight: (Float) -> String,
    kgToDisplay: (Float) -> Float,
    weightUnit: WeightUnit
) {
    val currentExercise = loadedRoutine?.exercises?.getOrNull(currentExerciseIndex)
    var videoEntity by remember { mutableStateOf<ExerciseVideoEntity?>(null) }

    LaunchedEffect(currentExercise?.exercise?.id, workoutParameters.selectedExerciseId) {
        val exerciseId = currentExercise?.exercise?.id ?: workoutParameters.selectedExerciseId
        if (exerciseId != null) {
            val videos = exerciseRepository.getVideos(exerciseId)
            videoEntity = videos.firstOrNull()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(Spacing.medium)
    ) {
        // Exercise details (reps, weight, mode)
        if (currentExercise != null) {
            val repsText = if (currentExercise.setReps.isEmpty()) {
                "No sets configured"
            } else if (currentExercise.setReps.all { it == currentExercise.setReps.first() }) {
                "${currentExercise.setReps.size}x${currentExercise.setReps.first()}"
            } else {
                currentExercise.setReps.joinToString(", ")
            }

            val descriptionText = if (currentExercise.workoutType is WorkoutType.Echo) {
                val cableText = when (currentExercise.cableConfig) {
                    CableConfiguration.SINGLE -> " (Single)"
                    CableConfiguration.DOUBLE -> " (Double)"
                    else -> ""
                }
                "$repsText reps$cableText - ${currentExercise.workoutType.displayName} - Adaptive"
            } else {
                val baseWeightText = if (currentExercise.setWeightsPerCableKg.isNotEmpty()) {
                    val displayWeights = currentExercise.setWeightsPerCableKg.map { kgToDisplay(it) }
                    val minWeight = displayWeights.minOrNull() ?: 0f
                    val maxWeight = displayWeights.maxOrNull() ?: 0f
                    val weightSuffix = if (weightUnit == WeightUnit.LB) "lbs" else "kg"
                    if (minWeight == maxWeight) {
                        "%.1f %s".format(minWeight, weightSuffix)
                    } else {
                        "%.1f-%.1f %s".format(minWeight, maxWeight, weightSuffix)
                    }
                } else {
                    formatWeight(currentExercise.weightPerCableKg)
                }
                val weightText = when (currentExercise.cableConfig) {
                    CableConfiguration.SINGLE -> "$baseWeightText (Single)"
                    CableConfiguration.DOUBLE -> "$baseWeightText/cable (Double)"
                    else -> baseWeightText
                }
                "$repsText @ $weightText - ${currentExercise.workoutType.displayName}"
            }

            Text(
                text = descriptionText,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        } else {
            val descriptionText = if (workoutParameters.workoutType is WorkoutType.Echo) {
                "${workoutParameters.reps} reps - ${workoutParameters.workoutType.displayName} - Adaptive"
            } else {
                "${workoutParameters.reps} reps @ ${formatWeight(workoutParameters.weightPerCableKg)}/cable - ${workoutParameters.workoutType.displayName}"
            }
            Text(
                text = descriptionText,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        // Video player
        if (enableVideoPlayback) {
            videoEntity?.let { video ->
                Spacer(modifier = Modifier.height(Spacing.medium))
                VideoPlayer(
                    videoUrl = video.videoUrl,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .clip(RoundedCornerShape(12.dp))
                )
            } ?: run {
                // No video available placeholder
                Spacer(modifier = Modifier.height(Spacing.medium))
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "No video",
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "No video available",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Data tab content - shows live metrics during workout
 */
@Composable
fun DataTabContent(
    currentMetric: WorkoutMetric?,
    repCount: RepCount,
    weightUnit: WeightUnit,
    formatWeight: (Float, WeightUnit) -> String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(Spacing.medium)
    ) {
        if (currentMetric != null && repCount.isWarmupComplete) {
            // Live metrics display
            Text(
                "Live Metrics",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(Spacing.small))

            // Per-cable load (large display)
            Text(
                formatWeight(currentMetric.totalLoad / 2f, weightUnit),
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                "Per Cable",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(Spacing.medium))

            // Cable positions
            Text(
                "Cable Positions",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(Spacing.extraSmall))

            // Cable A
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("A", style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(20.dp))
                LinearProgressIndicator(
                    progress = { (currentMetric.positionA / 1000f).coerceIn(0f, 1f) },
                    modifier = Modifier.weight(1f).height(8.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                )
                Text(
                    "${currentMetric.positionA}",
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.width(50.dp).padding(start = 4.dp),
                    textAlign = TextAlign.End
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Cable B
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("B", style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(20.dp))
                LinearProgressIndicator(
                    progress = { (currentMetric.positionB / 1000f).coerceIn(0f, 1f) },
                    modifier = Modifier.weight(1f).height(8.dp),
                    color = MaterialTheme.colorScheme.secondary,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                )
                Text(
                    "${currentMetric.positionB}",
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.width(50.dp).padding(start = 4.dp),
                    textAlign = TextAlign.End
                )
            }

            Spacer(modifier = Modifier.height(Spacing.medium))

            // Velocity (both cables)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Velocity A", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        "%.2f m/s".format(currentMetric.velocityA),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Velocity B", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        "%.2f m/s".format(currentMetric.velocityB),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        } else {
            // Waiting for warmup to complete
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(48.dp),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(Spacing.medium))
                    Text(
                        text = if (currentMetric == null) "Waiting for data..." else "Complete warmup reps to see live metrics",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}
