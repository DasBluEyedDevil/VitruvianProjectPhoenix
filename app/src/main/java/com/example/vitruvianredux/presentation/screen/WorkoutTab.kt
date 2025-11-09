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
import android.net.Uri
import android.view.ViewGroup
import androidx.core.net.toUri
import android.widget.VideoView
import com.example.vitruvianredux.data.local.ExerciseEntity
import com.example.vitruvianredux.data.repository.ExerciseRepository
import com.example.vitruvianredux.domain.model.*
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
    autoStopState: AutoStopUiState,
    weightUnit: WeightUnit,
    exerciseRepository: ExerciseRepository,
    isWorkoutSetupDialogVisible: Boolean = false,
    hapticEvents: kotlinx.coroutines.flow.SharedFlow<HapticEvent>? = null,
    loadedRoutine: Routine? = null,
    currentExerciseIndex: Int = 0,
    kgToDisplay: (Float, WeightUnit) -> Float,
    displayToKg: (Float, WeightUnit) -> Float,
    formatWeight: (Float, WeightUnit) -> String,
    onScan: () -> Unit,
    onDisconnect: () -> Unit,
    onStartWorkout: () -> Unit,
    onStopWorkout: () -> Unit,
    onSkipRest: () -> Unit,
    onResetForNewWorkout: () -> Unit,
    onStartNextExercise: () -> Unit = {},
    onUpdateParameters: (WorkoutParameters) -> Unit,
    onShowWorkoutSetupDialog: () -> Unit = {},
    onHideWorkoutSetupDialog: () -> Unit = {},
    modifier: Modifier = Modifier,
    showConnectionCard: Boolean = true,
    showWorkoutSetupCard: Boolean = true
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp)
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
            // Show setup button when in Idle state, otherwise show workout controls
            when (workoutState) {
                is WorkoutState.Idle -> {
                    if (showWorkoutSetupCard) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            shape = RoundedCornerShape(16.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(Spacing.medium)
                            ) {
                                Text(
                                    "Workout Setup",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(Spacing.small))
                                Button(
                                    onClick = onShowWorkoutSetupDialog,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.Settings, contentDescription = "Configure workout")
                                    Spacer(modifier = Modifier.width(Spacing.small))
                                    Text("Setup Workout")
                                }
                            }
                        }
                    }
                }
                is WorkoutState.Completed -> {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(16.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
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
                                contentDescription = null,
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
                                            "${nextExercise.setReps.size} sets x ${nextExercise.setReps.first()} reps",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )

                                        Spacer(Modifier.height(Spacing.medium))

                                        Button(
                                            onClick = onStartNextExercise,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text("Start Next Exercise")
                                        }
                                    }
                                }
                            } else {
                                // Last exercise or not a routine - show "Start New Workout"
                                Button(
                                    onClick = onResetForNewWorkout,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.Refresh, contentDescription = "Start new workout")
                                    Spacer(modifier = Modifier.width(Spacing.small))
                                    Text("Start New Workout")
                                }
                            }
                        }
                    }
                }
                is WorkoutState.Active -> {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(16.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(Spacing.medium)
                        ) {
                            Text(
                                "Workout Active",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
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
                    // Show rep counter first (above video) so it's always visible
                    RepCounterCard(repCount = repCount, workoutParameters = workoutParameters)

                    // Show current exercise details
                    CurrentExerciseCard(
                        loadedRoutine = loadedRoutine,
                        currentExerciseIndex = currentExerciseIndex,
                        workoutParameters = workoutParameters,
                        exerciseRepository = exerciseRepository,
                        formatWeight = { weight -> formatWeight(weight, weightUnit) }
                    )
                }
                else -> {}
            }

            // Only show live metrics after warmup is complete (matches official app behavior)
            if (workoutState is WorkoutState.Active
                && currentMetric != null
                && repCount.isWarmupComplete) {
                LiveMetricsCard(
                    metric = currentMetric,
                    weightUnit = weightUnit,
                    formatWeight = formatWeight
                )
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
            selectedExercise = exerciseRepository.getExerciseById(id)
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp),
        title = {
            Text(
                "Workout Setup",
                style = MaterialTheme.typography.titleLarge,
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
                // Exercise Selection Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text(
                            "Exercise",
                            style = MaterialTheme.typography.titleMedium,
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
                            .menuAnchor(type = MenuAnchorType.PrimaryNotEditable),
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
                                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
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
                                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
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

                // Weight Picker - Show "Adaptive" for Echo mode, otherwise CompactNumberPicker
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        if (workoutParameters.workoutType is WorkoutType.Echo) {
                            Text(
                                "Weight per cable",
                                style = MaterialTheme.typography.titleMedium,
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

                // Reps Picker
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
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
                                style = MaterialTheme.typography.titleMedium,
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
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            Text(
                                "Progression/Regression",
                                style = MaterialTheme.typography.titleMedium,
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
                title = { Text("Select TUT Variant") },
                containerColor = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(16.dp),
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
                title = { Text("Echo Mode Configuration") },
                containerColor = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(16.dp),
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
                                    .menuAnchor(type = MenuAnchorType.PrimaryNotEditable)
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
                                    .menuAnchor(type = MenuAnchorType.PrimaryNotEditable)
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
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.medium)
        ) {
            Text(
                "Connection",
                style = MaterialTheme.typography.titleMedium,
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
                MaterialTheme.colorScheme.surfaceVariant
            }
        ),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
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
                    contentDescription = null,
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
    val context = LocalContext.current
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
                    } catch (e: Exception) {
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
    formatWeight: (Float) -> String
) {
    // Get current exercise from routine if available
    val currentExercise = loadedRoutine?.exercises?.getOrNull(currentExerciseIndex)

    // Get exercise entity for video
    var exerciseEntity by remember { mutableStateOf<ExerciseEntity?>(null) }
    var videoEntity by remember { mutableStateOf<com.example.vitruvianredux.data.local.ExerciseVideoEntity?>(null) }

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
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.medium)
        ) {
            // Exercise name
            Text(
                text = currentExercise?.exercise?.name ?: exerciseEntity?.name ?: "Exercise",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(Spacing.small))

            // Exercise details (reps, weight, mode)
            if (currentExercise != null) {
                // Routine exercise - show full details
                val repsText = if (currentExercise.setReps.all { it == currentExercise.setReps.first() }) {
                    "${currentExercise.setReps.size}x${currentExercise.setReps.first()}"
                } else {
                    currentExercise.setReps.joinToString(", ")
                }

                val weightText = when (currentExercise.cableConfig) {
                    CableConfiguration.SINGLE -> "${formatWeight(currentExercise.weightPerCableKg)} (Single)"
                    CableConfiguration.DOUBLE -> "${formatWeight(currentExercise.weightPerCableKg)}/cable (Double)"
                    else -> formatWeight(currentExercise.weightPerCableKg)
                }

                Text(
                    text = "$repsText @ $weightText - ${currentExercise.workoutType.displayName}",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            } else {
                // Just Lift mode - show basic details
                Text(
                    text = "${workoutParameters.reps} reps @ ${formatWeight(workoutParameters.weightPerCableKg)}/cable - ${workoutParameters.workoutType.displayName}",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            // Video player (if video available)
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

@Composable
fun RepCounterCard(repCount: RepCount, workoutParameters: WorkoutParameters) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
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
            
            val labelText = if (repCount.isWarmupComplete) "REPS" else "WARMUP"
            
            Text(
                text = labelText,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(Spacing.medium))

            Text(
                text = countText,
                style = MaterialTheme.typography.displayLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
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
    // Determine active position (max of both cables for progress indicator)
    val maxPosition = maxOf(metric.positionA, metric.positionB)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.medium)
        ) {
            Text(
                "Live Metrics",
                style = MaterialTheme.typography.titleMedium,
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

            // Position indicator
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "Range of Motion",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                LinearProgressIndicator(
                    progress = { (maxPosition / 1000f).coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth(0.8f)
                        .padding(top = Spacing.extraSmall)
                        .height(8.dp)
                )
            }
        }
    }
}
