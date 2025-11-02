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
import com.example.vitruvianredux.presentation.components.ExercisePickerDialog
import com.example.vitruvianredux.presentation.screen.ExerciseEditBottomSheet
import com.example.vitruvianredux.presentation.viewmodel.MainViewModel
import java.util.UUID

/**
 * Single Exercise screen - choose one exercise and perform workout.
 * New flow: Exercise Picker → Exercise Config (same as Routine Builder) → Workout
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SingleExerciseScreen(
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
    val hapticEvents = viewModel.hapticEvents
    val isAutoConnecting by viewModel.isAutoConnecting.collectAsState()
    val connectionError by viewModel.connectionError.collectAsState()

    // State for exercise selection and configuration flow
    var showExercisePicker by remember { mutableStateOf(workoutState is WorkoutState.Idle) }
    var exerciseToConfig by remember { mutableStateOf<RoutineExercise?>(null) }
    var hasStartedConfig by remember { mutableStateOf(false) } // Track if user has started configuring

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Single Exercise") },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
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
        // Only show WorkoutTab after an exercise has been selected (not while showing picker)
        if (!showExercisePicker || hasStartedConfig) {
            WorkoutTab(
                connectionState = ConnectionState.Connected("", ""),
                workoutState = workoutState,
                currentMetric = currentMetric,
                workoutParameters = workoutParameters,
                repCount = repCount,
                autoStopState = autoStopState,
                weightUnit = weightUnit,
                exerciseRepository = exerciseRepository,
                isWorkoutSetupDialogVisible = false, // Don't show old setup dialog
                hapticEvents = hapticEvents,
                loadedRoutine = null,
                currentExerciseIndex = 0,
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
                onCancelRoutine = { viewModel.cancelRoutine() },
                onSkipRest = { viewModel.skipRest() },
                onResetForNewWorkout = {
                    viewModel.resetForNewWorkout()
                    showExercisePicker = true // Show exercise picker again for new workout
                },
                onUpdateParameters = { viewModel.updateWorkoutParameters(it) },
                onShowWorkoutSetupDialog = { showExercisePicker = true }, // Trigger exercise picker instead
                onHideWorkoutSetupDialog = { showExercisePicker = false },
                showConnectionCard = false,
                showWorkoutSetupCard = false,
                modifier = Modifier.padding(padding)
            )
        }

        // Exercise picker dialog
        ExercisePickerDialog(
            showDialog = showExercisePicker,
            fullScreen = true,
            onDismiss = {
                showExercisePicker = false
                // Only go back if user never started configuring (dismissed at exercise picker stage)
                if (workoutState is WorkoutState.Idle && !hasStartedConfig) {
                    navController.navigateUp()
                }
            },
            onExerciseSelected = { selectedExercise ->
                // Convert ExerciseEntity to Exercise domain model
                val exercise = Exercise(
                    name = selectedExercise.name,
                    muscleGroup = selectedExercise.muscleGroups.split(",").firstOrNull()?.trim() ?: "Full Body",
                    equipment = selectedExercise.equipment.split(",").firstOrNull()?.trim() ?: "",
                    defaultCableConfig = CableConfiguration.DOUBLE,
                    id = selectedExercise.id
                )

                // Create default RoutineExercise with sensible defaults
                val newRoutineExercise = RoutineExercise(
                    id = UUID.randomUUID().toString(),
                    exercise = exercise,
                    cableConfig = exercise.resolveDefaultCableConfig(),
                    orderIndex = 0,
                    setReps = listOf(10, 10, 10), // 3 sets of 10 reps default
                    weightPerCableKg = 20f, // 20kg default
                    progressionKg = 0f, // No progression by default
                    restSeconds = 60, // 60 seconds rest
                    notes = "",
                    mode = WorkoutMode.OldSchool, // Default mode
                    eccentricLoad = EccentricLoad.LOAD_100, // Default eccentric load
                    echoLevel = EchoLevel.HARDER // Default echo level
                )
                exerciseToConfig = newRoutineExercise
                showExercisePicker = false
                hasStartedConfig = true // Mark that user has started the config flow
            },
            exerciseRepository = exerciseRepository
        )

        // Exercise configuration bottom sheet (same as Routine Builder)
        exerciseToConfig?.let { exercise ->
            ExerciseEditBottomSheet(
                exercise = exercise,
                weightUnit = weightUnit,
                kgToDisplay = viewModel::kgToDisplay,
                displayToKg = viewModel::displayToKg,
                exerciseRepository = exerciseRepository,
                buttonText = "Start",
                onSave = { configuredExercise ->
                    // Convert RoutineExercise to WorkoutParameters
                    val parameters = WorkoutParameters(
                        mode = configuredExercise.mode,
                        reps = configuredExercise.setReps.firstOrNull() ?: 10,
                        weightPerCableKg = configuredExercise.weightPerCableKg,
                        progressionRegressionKg = configuredExercise.progressionKg,
                        isJustLift = false,
                        stopAtTop = false,
                        warmupReps = 0,
                        selectedExerciseId = configuredExercise.exercise.id,
                        eccentricLoad = configuredExercise.eccentricLoad
                    )

                    // Update parameters and start workout
                    viewModel.updateWorkoutParameters(parameters)
                    viewModel.ensureConnection(
                        onConnected = { viewModel.startWorkout() },
                        onFailed = { /* Error shown via StateFlow */ }
                    )

                    exerciseToConfig = null
                },
                onDismiss = {
                    exerciseToConfig = null
                    // If dismissed before workout started, show exercise picker again (let them pick different exercise)
                    if (workoutState is WorkoutState.Idle) {
                        showExercisePicker = true
                    }
                }
            )
        }

        // Auto-connect UI overlays
        if (isAutoConnecting) {
            com.example.vitruvianredux.presentation.components.ConnectingOverlay()
        }

        connectionError?.let { error ->
            com.example.vitruvianredux.presentation.components.ConnectionErrorDialog(
                message = error,
                onDismiss = { viewModel.clearConnectionError() }
            )
        }
    }
}
