package com.example.vitruvianredux.presentation.screen

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import com.example.vitruvianredux.data.repository.ExerciseRepository
import com.example.vitruvianredux.domain.model.*
import com.example.vitruvianredux.presentation.viewmodel.MainViewModel
import com.example.vitruvianredux.ui.theme.Spacing
import kotlinx.coroutines.delay

/**
 * Active Workout screen - displays workout controls and metrics during an active workout.
 * This screen is shown when a workout is in progress.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActiveWorkoutScreen(
    navController: NavController,
    viewModel: MainViewModel,
    exerciseRepository: ExerciseRepository
) {
    val workoutState by viewModel.workoutState.collectAsState()
    val currentMetric by viewModel.currentMetric.collectAsState()
    val workoutParameters by viewModel.workoutParameters.collectAsState()
    val repCount by viewModel.repCount.collectAsState()
    val autoStopState by viewModel.autoStopState.collectAsState()
    val weightUnit by viewModel.weightUnit.collectAsState()
    val loadedRoutine by viewModel.loadedRoutine.collectAsState()
    val currentExerciseIndex by viewModel.currentExerciseIndex.collectAsState()
    val hapticEvents = viewModel.hapticEvents
    val connectionState by viewModel.connectionState.collectAsState()
    val isAutoConnecting by viewModel.isAutoConnecting.collectAsState()
    val connectionError by viewModel.connectionError.collectAsState()

    // State for confirmation dialog
    var showExitConfirmation by remember { mutableStateOf(false) }

    // PR Celebration state
    var prCelebrationEvent by remember { mutableStateOf<PRCelebrationEvent?>(null) }
    LaunchedEffect(Unit) {
        viewModel.prCelebrationEvent.collect { event ->
            prCelebrationEvent = event
        }
    }

    // Dynamic title based on workout type
    val screenTitle = remember(loadedRoutine, workoutParameters.isJustLift) {
        when {
            loadedRoutine != null -> loadedRoutine?.name ?: "Routine"
            workoutParameters.isJustLift -> "Just Lift"
            else -> "Single Exercise"
        }
    }

    // Watch for workout completion and navigate back
    // For Just Lift, navigate back when state becomes Idle (after auto-reset)
    LaunchedEffect(workoutState, workoutParameters) {
        when {
            workoutState is WorkoutState.Completed -> {
                delay(2000)
                navController.navigateUp()
            }
            workoutState is WorkoutState.Idle && workoutParameters.isJustLift -> {
                // Just Lift completed and reset to Idle - navigate back to Just Lift screen
                navController.navigateUp()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(screenTitle)
                        // Show progress indicator for routines
                        loadedRoutine?.let { routine ->
                            val totalExercises = routine.exercises.size
                            val currentExerciseNum = currentExerciseIndex + 1
                            if (totalExercises > 1) {
                                Text(
                                    text = "Exercise $currentExerciseNum of $totalExercises",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            // Show confirmation if workout is active
                            if (workoutState is WorkoutState.Active ||
                                workoutState is WorkoutState.Resting ||
                                workoutState is WorkoutState.Countdown
                            ) {
                                showExitConfirmation = true
                            } else {
                                navController.navigateUp()
                            }
                        }
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { padding ->
        WorkoutTab(
            connectionState = connectionState,
            workoutState = workoutState,
            currentMetric = currentMetric,
            workoutParameters = workoutParameters,
            repCount = repCount,
            autoStopState = autoStopState,
            weightUnit = weightUnit,
            exerciseRepository = exerciseRepository,
            isWorkoutSetupDialogVisible = false,
            hapticEvents = hapticEvents,
            loadedRoutine = loadedRoutine,
            currentExerciseIndex = currentExerciseIndex,
            kgToDisplay = viewModel::kgToDisplay,
            displayToKg = viewModel::displayToKg,
            formatWeight = viewModel::formatWeight,
            onScan = { viewModel.startScanning() },
            onDisconnect = { viewModel.disconnect() },
            onStartWorkout = {
                viewModel.ensureConnection(
                    onConnected = { viewModel.startWorkout() },
                    onFailed = { /* Error shown via StateFlow */ }
                )
            },
            onStopWorkout = { viewModel.stopWorkout() },
            onSkipRest = { viewModel.skipRest() },
            onResetForNewWorkout = { viewModel.resetForNewWorkout() },
            onStartNextExercise = { viewModel.advanceToNextExercise() },
            onUpdateParameters = { viewModel.updateWorkoutParameters(it) },
            onShowWorkoutSetupDialog = { /* Not used in ActiveWorkoutScreen */ },
            onHideWorkoutSetupDialog = { /* Not used in ActiveWorkoutScreen */ },
            showConnectionCard = false,
            showWorkoutSetupCard = false,
            modifier = Modifier.padding(padding)
        )
    }

    // Exit confirmation dialog
    if (showExitConfirmation) {
        AlertDialog(
            onDismissRequest = { showExitConfirmation = false },
            title = { Text("Exit Workout?") },
            text = { Text("The workout is currently active. Are you sure you want to exit?") },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = MaterialTheme.shapes.medium,
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.stopWorkout()
                        showExitConfirmation = false
                        navController.navigateUp()
                    }
                ) {
                    Text("Exit")
                }
            },
            dismissButton = {
                TextButton(onClick = { showExitConfirmation = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Auto-connect UI overlays (same as other screens)
    if (isAutoConnecting) {
        com.example.vitruvianredux.presentation.components.ConnectingOverlay()
    }

    connectionError?.let { error ->
        com.example.vitruvianredux.presentation.components.ConnectionErrorDialog(
            message = error,
            onDismiss = { viewModel.clearConnectionError() }
        )
    }

    // PR Celebration Dialog
    prCelebrationEvent?.let { event ->
        com.example.vitruvianredux.presentation.components.PRCelebrationDialog(
            show = true,
            exerciseName = event.exerciseName,
            weight = "${viewModel.formatWeight(event.weightPerCableKg, weightUnit)}/cable × ${event.reps} reps",
            onDismiss = { prCelebrationEvent = null }
        )
    }
}
