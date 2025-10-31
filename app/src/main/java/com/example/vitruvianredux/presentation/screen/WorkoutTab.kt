package com.example.vitruvianredux.presentation.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
    onCancelRoutine: () -> Unit,
    onSkipRest: () -> Unit,
    onResetForNewWorkout: () -> Unit,
    onUpdateParameters: (WorkoutParameters) -> Unit,
    onShowWorkoutSetupDialog: () -> Unit = {},
    onHideWorkoutSetupDialog: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    // Haptic feedback effect
    hapticEvents?.let {
        HapticFeedbackEffect(hapticEvents = it)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(Spacing.medium)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(Spacing.medium)
    ) {
        // Connection Card
        ConnectionCard(
            connectionState = connectionState,
            onScan = onScan,
            onDisconnect = onDisconnect
        )

        if (connectionState is ConnectionState.Connected) {
            // Show setup button when in Idle state, otherwise show workout controls
            when (workoutState) {
                is WorkoutState.Idle -> {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        shape = RoundedCornerShape(16.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
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
                is WorkoutState.Completed -> {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        shape = RoundedCornerShape(16.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
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
                is WorkoutState.Active -> {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        shape = RoundedCornerShape(16.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
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
                            
                            if (autoStopState.isActive) {
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

            // Display state-specific cards
            when (workoutState) {
                is WorkoutState.Countdown -> {
                    CountdownCard(secondsRemaining = workoutState.secondsRemaining)
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
                        nextExerciseMode = workoutParameters.mode.displayName,
                        currentExerciseIndex = if (loadedRoutine != null) currentExerciseIndex else null,
                        totalExercises = loadedRoutine?.exercises?.size,
                        formatWeight = { weight -> formatWeight(weight, weightUnit) },
                        onSkipRest = onSkipRest,
                        onEndWorkout = onCancelRoutine
                    )
                }
                is WorkoutState.Active -> {
                    RepCounterCard(repCount = repCount)
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
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
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
                        value = workoutParameters.mode.displayName,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(modeLabel) },
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = showModeMenu)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(),
                        colors = OutlinedTextFieldDefaults.colors()
                    )
                    ExposedDropdownMenu(
                        expanded = showModeMenu,
                        onDismissRequest = { showModeMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Old School") },
                            onClick = {
                                onUpdateParameters(workoutParameters.copy(mode = WorkoutMode.OldSchool))
                                showModeMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Pump") },
                            onClick = {
                                onUpdateParameters(workoutParameters.copy(mode = WorkoutMode.Pump))
                                showModeMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Eccentric Only") },
                            onClick = {
                                onUpdateParameters(workoutParameters.copy(mode = WorkoutMode.EccentricOnly))
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
                                    Icon(Icons.Default.KeyboardArrowRight, contentDescription = null)
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
                                    Icon(Icons.Default.KeyboardArrowRight, contentDescription = null)
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
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        if (workoutParameters.mode is WorkoutMode.Echo) {
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
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
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
                if (workoutParameters.mode is WorkoutMode.Pump ||
                    workoutParameters.mode is WorkoutMode.OldSchool ||
                    workoutParameters.mode is WorkoutMode.EccentricOnly ||
                    workoutParameters.mode is WorkoutMode.TUT ||
                    workoutParameters.mode is WorkoutMode.TUTBeast
                ) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
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
            selectedExercise = exercise
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
                onUpdateParameters(workoutParameters.copy(mode = mode, eccentricLoad = eccentricLoad))
                showModeSubSelector = false
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
    label: String = "",
    modifier: Modifier = Modifier
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
                                if (kotlin.math.abs(accumulatedDrag) >= 3f) {
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
                    if (workoutParameters.mode is WorkoutMode.Echo) {
                        (workoutParameters.mode as WorkoutMode.Echo).level
                    } else {
                        EchoLevel.HARD
                    }
                )
            }
            var selectedEccentricLoad by remember { 
                mutableStateOf(workoutParameters.eccentricLoad ?: EccentricLoad.LOAD_100) 
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
                                    .menuAnchor()
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
                                    .menuAnchor()
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
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutParametersCard(
    workoutParameters: WorkoutParameters,
    workoutState: WorkoutState,
    autoStopState: AutoStopUiState,
    weightUnit: WeightUnit,
    kgToDisplay: (Float, WeightUnit) -> Float,
    displayToKg: (Float, WeightUnit) -> Float,
    onUpdateParameters: (WorkoutParameters) -> Unit,
    onStartWorkout: () -> Unit,
    onStopWorkout: () -> Unit,
    onResetForNewWorkout: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
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

            var showModeMenu by remember { mutableStateOf(false) }
            var showEchoLevelDialog by remember { mutableStateOf(false) }

            val modeLabel = if (workoutParameters.isJustLift) "Base Mode (resistance profile)" else "Workout Mode"
            ExposedDropdownMenuBox(
                expanded = showModeMenu,
                onExpandedChange = { showModeMenu = !showModeMenu && workoutState is WorkoutState.Idle }
            ) {
                OutlinedTextField(
                    value = workoutParameters.mode.displayName,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(modeLabel) },
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = showModeMenu)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(),
                    enabled = workoutState is WorkoutState.Idle
                )
                ExposedDropdownMenu(
                    expanded = showModeMenu,
                    onDismissRequest = { showModeMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Old School") },
                        onClick = {
                            onUpdateParameters(workoutParameters.copy(mode = WorkoutMode.OldSchool))
                            showModeMenu = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Pump") },
                        onClick = {
                            onUpdateParameters(workoutParameters.copy(mode = WorkoutMode.Pump))
                            showModeMenu = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("TUT") },
                        onClick = {
                            onUpdateParameters(workoutParameters.copy(mode = WorkoutMode.TUT))
                            showModeMenu = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("TUT Beast") },
                        onClick = {
                            onUpdateParameters(workoutParameters.copy(mode = WorkoutMode.TUTBeast))
                            showModeMenu = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Eccentric Only") },
                        onClick = {
                            onUpdateParameters(workoutParameters.copy(mode = WorkoutMode.EccentricOnly))
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
                                Icon(Icons.Default.KeyboardArrowRight, contentDescription = null)
                            }
                        },
                        onClick = {
                            showModeMenu = false
                            showEchoLevelDialog = true
                        }
                    )
                }
            }
            Spacer(modifier = Modifier.height(Spacing.small))

            if (showEchoLevelDialog) {
                AlertDialog(
                    onDismissRequest = { showEchoLevelDialog = false },
                    title = { Text("Select Echo Level") },
                    containerColor = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(16.dp),
                    text = {
                        Column {
                            Text(
                                "Echo adapts to your output. Select a level:",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Spacer(modifier = Modifier.height(Spacing.medium))
                            listOf(
                                EchoLevel.HARD to "Hard",
                                EchoLevel.HARDER to "Harder",
                                EchoLevel.HARDEST to "Hardest",
                                EchoLevel.EPIC to "Epic"
                            ).forEach { (level, label) ->
                                OutlinedButton(
                                    onClick = {
                                        onUpdateParameters(workoutParameters.copy(mode = WorkoutMode.Echo(level)))
                                        showEchoLevelDialog = false
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(label)
                                }
                                Spacer(modifier = Modifier.height(Spacing.small))
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { showEchoLevelDialog = false }) {
                            Text("Cancel")
                        }
                    }
                )
            }

            var weightText by remember(workoutParameters.weightPerCableKg, weightUnit) {
                mutableStateOf(kgToDisplay(workoutParameters.weightPerCableKg, weightUnit).toString())
            }
            OutlinedTextField(
                value = weightText,
                onValueChange = {
                    weightText = it
                    it.toFloatOrNull()?.let { displayWeight ->
                        val kg = displayToKg(displayWeight, weightUnit)
                        onUpdateParameters(workoutParameters.copy(weightPerCableKg = kg))
                    }
                },
                label = { Text("Weight per cable (${weightUnit.name.lowercase()})") },
                modifier = Modifier.fillMaxWidth(),
                enabled = workoutState is WorkoutState.Idle
            )
            Spacer(modifier = Modifier.height(Spacing.small))

            var repsText by remember(workoutParameters) {
                mutableStateOf(workoutParameters.reps.toString())
            }
            OutlinedTextField(
                value = repsText,
                onValueChange = {
                    repsText = it
                    it.toIntOrNull()?.let { reps ->
                        onUpdateParameters(workoutParameters.copy(reps = reps))
                    }
                },
                label = { Text("Target reps") },
                modifier = Modifier.fillMaxWidth(),
                enabled = workoutState is WorkoutState.Idle && !workoutParameters.isJustLift
            )
            Spacer(modifier = Modifier.height(Spacing.small))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Just Lift")
                Switch(
                    checked = workoutParameters.isJustLift,
                    onCheckedChange = { checked ->
                        if (workoutState is WorkoutState.Idle) {
                            onUpdateParameters(workoutParameters.copy(isJustLift = checked))
                        }
                    },
                    enabled = workoutState is WorkoutState.Idle
                )
            }
            Spacer(modifier = Modifier.height(Spacing.small))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Finish At Top")
                Switch(
                    checked = workoutParameters.stopAtTop,
                    onCheckedChange = { checked ->
                        if (workoutState is WorkoutState.Idle) {
                            onUpdateParameters(workoutParameters.copy(stopAtTop = checked))
                        }
                    },
                    enabled = workoutState is WorkoutState.Idle && !workoutParameters.isJustLift
                )
            }

            if (autoStopState.isActive) {
                Spacer(modifier = Modifier.height(Spacing.medium))
                JustLiftAutoStopCard(autoStopState = autoStopState)
            }

            Spacer(modifier = Modifier.height(Spacing.medium))

            when (workoutState) {
                is WorkoutState.Idle -> {
                    Button(
                        onClick = onStartWorkout,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = "Start workout")
                        Spacer(modifier = Modifier.width(Spacing.small))
                        Text("Start Workout")
                    }
                }
                is WorkoutState.Initializing -> {
                    Button(
                        onClick = {},
                        modifier = Modifier.fillMaxWidth(),
                        enabled = false
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(Spacing.small))
                        Text("Initializing...")
                    }
                }
                is WorkoutState.Active -> {
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
                is WorkoutState.Completed -> {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
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
                is WorkoutState.Error -> {
                    Text(
                        "Error: ${workoutState.message}",
                        color = MaterialTheme.colorScheme.error
                    )
                }
                else -> {}
            }
        }
    }
}

@Composable
fun JustLiftAutoStopCard(autoStopState: AutoStopUiState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.medium),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "Just Lift Auto-Stop",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(Spacing.small))
            LinearProgressIndicator(
                progress = { autoStopState.progress },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(Spacing.small))
            Text(
                if (autoStopState.secondsRemaining > 0) {
                    "Stopping in ${autoStopState.secondsRemaining}s"
                } else {
                    "Stopping..."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun RepCounterCard(repCount: RepCount) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.large)
        ) {
            Text(
                "Reps",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(Spacing.medium))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "Warmup",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        repCount.warmupReps.toString(),
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "Working",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        repCount.workingReps.toString(),
                        style = MaterialTheme.typography.displayMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "Total",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    // Show working reps only (matches official app - warmup doesn't count)
                    Text(
                        repCount.workingReps.toString(),
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
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
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
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
