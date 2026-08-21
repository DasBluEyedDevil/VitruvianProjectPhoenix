package com.ninthlevel.phoenix.presentation.screen

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import com.ninthlevel.phoenix.data.preferences.SingleExerciseDefaults
import com.ninthlevel.phoenix.data.repository.ExerciseRepository
import com.ninthlevel.phoenix.domain.model.*
import com.ninthlevel.phoenix.presentation.components.ConnectingOverlay
import com.ninthlevel.phoenix.presentation.components.ConnectionErrorDialog
import com.ninthlevel.phoenix.presentation.components.ExercisePickerDialog
import com.ninthlevel.phoenix.presentation.navigation.NavigationRoutes
import com.ninthlevel.phoenix.presentation.viewmodel.MainViewModel
import com.ninthlevel.phoenix.presentation.viewmodel.MainViewModel.Companion.TEMP_SINGLE_EXERCISE_PREFIX
import com.ninthlevel.phoenix.ui.theme.Spacing
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import timber.log.Timber
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
    val sessionEccentricLoad by viewModel.sessionEccentricLoad.collectAsState()

    var exerciseToConfig by remember { mutableStateOf<RoutineExercise?>(null) }
    var isLoadingDefaults by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    // Track current loading job to cancel on rapid selection changes
    var loadingJob by remember { mutableStateOf<Job?>(null) }

    // Local state for picker
    var searchQuery by remember { mutableStateOf("") }
    var selectedMuscleFilter by remember { mutableStateOf("All") }
    var selectedEquipmentFilter by remember { mutableStateOf("All") }
    var showFavoritesOnly by remember { mutableStateOf(false) }

    // Get exercises from repository
    val allExercises by remember(searchQuery, selectedMuscleFilter, showFavoritesOnly) {
        when {
            showFavoritesOnly -> exerciseRepository.getFavorites()
            searchQuery.isNotBlank() -> exerciseRepository.searchExercises(searchQuery)
            selectedMuscleFilter != "All" -> exerciseRepository.filterByMuscleGroup(selectedMuscleFilter)
            else -> exerciseRepository.getAllExercises()
        }
    }.collectAsState(initial = emptyList())

    // Apply equipment filter
    val exercises = remember(allExercises, selectedEquipmentFilter) {
        allExercises.filter { matchesEquipmentFilter(it.equipment, selectedEquipmentFilter) }
    }

    // Set global title
    LaunchedEffect(Unit) {
        viewModel.updateTopBarTitle("Single Exercise")
    }

    Scaffold(
        // No local topBar needed
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            // Always show the picker content as the background
            com.ninthlevel.phoenix.presentation.components.ExercisePickerContent(
                exercises = exercises,
                searchQuery = searchQuery,
                onSearchQueryChange = { searchQuery = it },
                showFavoritesOnly = showFavoritesOnly,
                onShowFavoritesOnlyChange = { 
                    showFavoritesOnly = it
                    if (it) {
                        searchQuery = ""
                        selectedMuscleFilter = "All"
                        selectedEquipmentFilter = "All"
                    }
                },
                selectedMuscleFilter = selectedMuscleFilter,
                onMuscleFilterChange = { selectedMuscleFilter = it },
                selectedEquipmentFilter = selectedEquipmentFilter,
                onEquipmentFilterChange = { selectedEquipmentFilter = it },
                onExerciseSelected = { selectedExercise ->
                    val exercise = Exercise(
                        name = selectedExercise.name,
                        muscleGroup = selectedExercise.muscleGroups.split(",").firstOrNull()?.trim() ?: "Full Body",
                        equipment = selectedExercise.equipment.split(",").firstOrNull()?.trim() ?: "",
                        defaultCableConfig = cableConfigurationOf(selectedExercise.defaultCableConfig),
                        id = selectedExercise.id
                    )

                    val defaultCableConfig = exercise.resolveDefaultCableConfig()

                    // Cancel any in-progress loading to prevent race conditions
                    loadingJob?.cancel()

                    // Set loading state to prevent showing dialog before defaults are loaded
                    isLoadingDefaults = true

                    // Load saved defaults for this exercise+cable config asynchronously
                    loadingJob = coroutineScope.launch {
                        try {
                            val savedDefaults = selectedExercise.id?.let { exerciseId ->
                                viewModel.getSingleExerciseDefaults(exerciseId, defaultCableConfig.name)
                            }

                            val newRoutineExercise = if (savedDefaults != null) {
                                // Apply saved defaults using helper methods
                                Timber.d("Loaded saved defaults for ${selectedExercise.name} (${savedDefaults.cableConfig})")

                                RoutineExercise(
                                    id = UUID.randomUUID().toString(),
                                    exercise = exercise,
                                    cableConfig = savedDefaults.getCableConfiguration(),
                                    orderIndex = 0,
                                    setReps = savedDefaults.setReps,
                                    weightPerCableKg = savedDefaults.weightPerCableKg,
                                    setWeightsPerCableKg = savedDefaults.setWeightsPerCableKg,
                                    progressionKg = savedDefaults.progressionKg,
                                    setRestSeconds = savedDefaults.setRestSeconds,
                                    workoutType = savedDefaults.toWorkoutType(),
                                    eccentricLoad = savedDefaults.getEccentricLoad(),
                                    echoLevel = savedDefaults.getEchoLevel(),
                                    duration = savedDefaults.duration,
                                    isAMRAP = savedDefaults.isAMRAP,
                                    perSetRestTime = savedDefaults.perSetRestTime
                                )
                            } else {
                                // No saved defaults - use system defaults with session eccentric load
                                RoutineExercise(
                                    id = UUID.randomUUID().toString(),
                                    exercise = exercise,
                                    cableConfig = defaultCableConfig,
                                    orderIndex = 0,
                                    setReps = listOf(10, 10, 10),
                                    weightPerCableKg = 20f,
                                    progressionKg = 0f,
                                    setRestSeconds = listOf(60, 60, 60),
                                    workoutType = WorkoutType.Program(ProgramMode.OldSchool),
                                    eccentricLoad = sessionEccentricLoad,
                                    echoLevel = EchoLevel.HARDER
                                )
                            }
                            exerciseToConfig = newRoutineExercise
                        } finally {
                            isLoadingDefaults = false
                        }
                    }
                },
                exerciseRepository = exerciseRepository,
                fullScreen = true // Use full screen layout (no local header)
            )

            // Show loading indicator while defaults are being loaded
            if (isLoadingDefaults) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            // Show bottom sheet as overlay when an exercise is selected and defaults are loaded
            if (!isLoadingDefaults) {
                exerciseToConfig?.let {
                    ExerciseEditBottomSheet(
                    exercise = it,
                    weightUnit = weightUnit,
                    kgToDisplay = viewModel::kgToDisplay,
                    displayToKg = viewModel::displayToKg,
                    personalRecordRepository = viewModel.personalRecordRepository,
                    formatWeight = viewModel::formatWeight,
                    buttonText = "Start Workout",
                    onSave = { configuredExercise ->
                        val tempRoutine = Routine(
                            id = "${TEMP_SINGLE_EXERCISE_PREFIX}${UUID.randomUUID()}",
                            name = "Single Exercise: ${configuredExercise.exercise.name}",
                            description = "Temporary routine for single exercise mode",
                            exercises = listOf(configuredExercise)
                        )

                        viewModel.loadRoutine(tempRoutine)

                        viewModel.ensureConnection(
                            onConnected = {
                                viewModel.startWorkout()
                                navController.navigate(NavigationRoutes.ActiveWorkout.route) {
                                    popUpTo(NavigationRoutes.Home.route)
                                }
                            },
                            onFailed = { }
                        )

                        exerciseToConfig = null
                    },
                    onDismiss = {
                        exerciseToConfig = null
                    }
                )
                }
            }
        }

        if (isAutoConnecting) {
            ConnectingOverlay(
                onCancel = { viewModel.cancelAutoConnecting() }
            )
        }

        connectionError?.let { error ->
            ConnectionErrorDialog(
                message = error,
                onDismiss = { viewModel.clearConnectionError() }
            )
        }
    }
}
