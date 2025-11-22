package com.example.vitruvianredux.presentation.screen

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
    val enableVideoPlayback by viewModel.enableVideoPlayback.collectAsState()
    val isAutoConnecting by viewModel.isAutoConnecting.collectAsState()
    val connectionError by viewModel.connectionError.collectAsState()

    var exerciseToConfig by remember { mutableStateOf<RoutineExercise?>(null) }

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
        if (selectedEquipmentFilter != "All") {
            allExercises.filter { exercise ->
                val databaseValues = when (selectedEquipmentFilter) {
                    "Long Bar" -> listOf("BAR", "LONG_BAR", "BARBELL")
                    "Short Bar" -> listOf("SHORT_BAR")
                    "Ankle Strap" -> listOf("ANKLE_STRAP", "STRAPS")
                    "Handles" -> listOf("HANDLES", "SINGLE_HANDLE", "BOTH_HANDLES")
                    "Bench" -> listOf("BENCH")
                    "Rope" -> listOf("ROPE")
                    "Belt" -> listOf("BELT")
                    "Bodyweight" -> listOf("BODYWEIGHT")
                    else -> emptyList()
                }
                val equipmentList = exercise.equipment.uppercase().split(",").map { it.trim() }
                databaseValues.any { dbValue -> equipmentList.contains(dbValue.uppercase()) }
            }
        } else {
            allExercises
        }
    }

    // Trigger import
    LaunchedEffect(Unit) {
        exerciseRepository.importExercises()
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
            com.example.vitruvianredux.presentation.components.ExercisePickerContent(
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
                        setRestSeconds = listOf(60, 60, 60),
                        workoutType = WorkoutType.Program(ProgramMode.OldSchool),
                        eccentricLoad = EccentricLoad.LOAD_100,
                        echoLevel = EchoLevel.HARDER
                    )
                    exerciseToConfig = newRoutineExercise
                },
                exerciseRepository = exerciseRepository,
                enableVideoPlayback = enableVideoPlayback,
                fullScreen = true // Use full screen layout (no local header)
            )

            // Show bottom sheet as overlay when an exercise is selected
            exerciseToConfig?.let {
                ExerciseEditBottomSheet(
                    exercise = it,
                    weightUnit = weightUnit,
                    enableVideoPlayback = enableVideoPlayback,
                    kgToDisplay = viewModel::kgToDisplay,
                    displayToKg = viewModel::displayToKg,
                    exerciseRepository = exerciseRepository,
                    personalRecordRepository = viewModel.personalRecordRepository,
                    formatWeight = viewModel::formatWeight,
                    buttonText = "Start Workout",
                    onSave = { configuredExercise ->
                        val tempRoutine = Routine(
                            id = "temp_single_exercise_${UUID.randomUUID()}",
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
