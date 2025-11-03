package com.example.vitruvianredux.presentation.screen

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.vitruvianredux.data.repository.ExerciseRepository
import com.example.vitruvianredux.domain.model.*
import com.example.vitruvianredux.presentation.components.ExercisePickerDialog
import com.example.vitruvianredux.presentation.screen.ExerciseEditBottomSheet
import com.example.vitruvianredux.presentation.viewmodel.MainViewModel
import com.example.vitruvianredux.ui.theme.Spacing
import com.example.vitruvianredux.presentation.navigation.NavigationRoutes
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
    val weightUnit by viewModel.weightUnit.collectAsState()
    val isAutoConnecting by viewModel.isAutoConnecting.collectAsState()
    val connectionError by viewModel.connectionError.collectAsState()

    // State for exercise selection and configuration flow
    var showExercisePicker by remember { mutableStateOf(false) }
    var exerciseToConfig by remember { mutableStateOf<RoutineExercise?>(null) }

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
        // Landing page - choose exercise to begin
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.FitnessCenter,
                contentDescription = null,
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

            Text(
                "Select from our library of exercises",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = Spacing.large)
            )

            Spacer(Modifier.height(Spacing.extraLarge))

            Button(
                onClick = { showExercisePicker = true },
                modifier = Modifier
                    .fillMaxWidth(0.8f)
                    .height(56.dp)
            ) {
                Icon(Icons.Default.Search, contentDescription = null)
                Spacer(Modifier.width(Spacing.small))
                Text("Select Exercise")
            }
        }

        // Exercise picker dialog
        ExercisePickerDialog(
            showDialog = showExercisePicker,
            fullScreen = true,
            onDismiss = {
                // Just close picker, stay on screen - don't navigate back
                showExercisePicker = false
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
                    workoutType = WorkoutType.Program(ProgramMode.OldSchool), // Default mode
                    eccentricLoad = EccentricLoad.LOAD_100, // Default eccentric load
                    echoLevel = EchoLevel.HARDER // Default echo level
                )
                exerciseToConfig = newRoutineExercise
                showExercisePicker = false
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
                        workoutType = configuredExercise.workoutType,
                        reps = configuredExercise.setReps.firstOrNull() ?: 10,
                        weightPerCableKg = configuredExercise.weightPerCableKg,
                        progressionRegressionKg = configuredExercise.progressionKg,
                        isJustLift = false,
                        stopAtTop = false,
                        warmupReps = 0,
                        selectedExerciseId = configuredExercise.exercise.id
                    )

                    // Update parameters
                    viewModel.updateWorkoutParameters(parameters)

                    // Start workout and navigate to ActiveWorkoutScreen
                    viewModel.ensureConnection(
                        onConnected = {
                            viewModel.startWorkout()
                            navController.navigate(NavigationRoutes.ActiveWorkout.route)
                        },
                        onFailed = { /* Error shown via StateFlow */ }
                    )

                    exerciseToConfig = null
                },
                onDismiss = {
                    exerciseToConfig = null
                    showExercisePicker = true
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
