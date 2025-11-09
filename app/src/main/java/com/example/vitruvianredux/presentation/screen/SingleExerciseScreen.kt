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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.vitruvianredux.data.repository.ExerciseRepository
import com.example.vitruvianredux.domain.model.*
import com.example.vitruvianredux.presentation.components.ConnectingOverlay
import com.example.vitruvianredux.presentation.components.ConnectionErrorDialog
import com.example.vitruvianredux.presentation.components.ExercisePickerDialog
import com.example.vitruvianredux.presentation.navigation.NavigationRoutes
import com.example.vitruvianredux.presentation.viewmodel.MainViewModel
import com.example.vitruvianredux.ui.theme.Spacing
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SingleExerciseScreen(
    navController: NavController,
    viewModel: MainViewModel = hiltViewModel(),
    exerciseRepository: ExerciseRepository
) {
    val weightUnit by viewModel.weightUnit.collectAsState()
    val isAutoConnecting by viewModel.isAutoConnecting.collectAsState()
    val connectionError by viewModel.connectionError.collectAsState()

    var showExercisePicker by remember { mutableStateOf(true) } // Start with picker shown
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
        // The content of the screen is now primarily the dialog flow
        Box(modifier = Modifier.padding(padding)) {
            if (showExercisePicker) {
                ExercisePickerDialog(
                    showDialog = true,
                    fullScreen = true,
                    onDismiss = { showExercisePicker = false },
                    onExerciseSelected = { selectedExercise ->
                        val exercise = Exercise(
                            name = selectedExercise.name,
                            muscleGroup = selectedExercise.muscleGroups.split(",").firstOrNull()?.trim() ?: "Full Body",
                            equipment = selectedExercise.equipment.split(",").firstOrNull()?.trim() ?: "",
                            defaultCableConfig = CableConfiguration.DOUBLE,
                            id = selectedExercise.id
                        )

                        val newRoutineExercise = RoutineExercise(
                            id = UUID.randomUUID().toString(),
                            exercise = exercise,
                            cableConfig = exercise.resolveDefaultCableConfig(),
                            orderIndex = 0,
                            setReps = listOf(10, 10, 10),
                            weightPerCableKg = 20f,
                            progressionKg = 0f,
                            restSeconds = 60,
                            notes = "",
                            workoutType = WorkoutType.Program(ProgramMode.OldSchool),
                            eccentricLoad = EccentricLoad.LOAD_100,
                            echoLevel = EchoLevel.HARDER
                        )
                        exerciseToConfig = newRoutineExercise
                        showExercisePicker = false
                    },
                    exerciseRepository = exerciseRepository
                )
            }

            exerciseToConfig?.let {
                ExerciseEditBottomSheet(
                    exercise = it,
                    weightUnit = weightUnit,
                    kgToDisplay = viewModel::kgToDisplay,
                    displayToKg = viewModel::displayToKg,
                    exerciseRepository = exerciseRepository,
                    personalRecordRepository = viewModel.personalRecordRepository,
                    formatWeight = viewModel::formatWeight,
                    buttonText = "Start Workout",
                    onSave = { configuredExercise ->
                        // Create a temporary single-exercise routine for proper multi-set support
                        // This ensures rest timers work and sets progress correctly
                        val tempRoutine = Routine(
                            id = "temp_single_exercise_${UUID.randomUUID()}",
                            name = "Single Exercise: ${configuredExercise.exercise.name}",
                            description = "Temporary routine for single exercise mode",
                            exercises = listOf(configuredExercise)
                        )

                        // Load the routine (this sets up all the multi-set tracking)
                        viewModel.loadRoutine(tempRoutine)

                        viewModel.ensureConnection(
                            onConnected = {
                                viewModel.startWorkout()
                                navController.navigate(NavigationRoutes.ActiveWorkout.route) {
                                    popUpTo(NavigationRoutes.Home.route) // Clear back stack to home
                                }
                            },
                            onFailed = { /* Error is shown by the dialog */ }
                        )

                        exerciseToConfig = null
                    },
                    onDismiss = {
                        exerciseToConfig = null
                        showExercisePicker = true // Go back to picker
                    }
                )
            }

            // If both are false, it means the user dismissed the picker without selecting
            if (!showExercisePicker && exerciseToConfig == null) {
                 Column(
                    modifier = Modifier.fillMaxSize(),
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
                    Button(
                        onClick = { showExercisePicker = true },
                        modifier = Modifier.fillMaxWidth(0.8f).height(56.dp)
                    ) {
                        Icon(Icons.Default.Search, contentDescription = null)
                        Spacer(Modifier.width(Spacing.small))
                        Text("Select Exercise")
                    }
                }
            }
        }

        if (isAutoConnecting) {
            ConnectingOverlay()
        }

        connectionError?.let { error ->
            ConnectionErrorDialog(
                message = error,
                onDismiss = { viewModel.clearConnectionError() }
            )
        }
    }
}
