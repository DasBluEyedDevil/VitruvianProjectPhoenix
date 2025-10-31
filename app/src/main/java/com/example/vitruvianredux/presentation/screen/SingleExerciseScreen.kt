package com.example.vitruvianredux.presentation.screen

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import com.example.vitruvianredux.data.repository.ExerciseRepository
import com.example.vitruvianredux.presentation.viewmodel.MainViewModel
import com.example.vitruvianredux.ui.theme.Spacing

/**
 * Single Exercise screen - choose one exercise and perform workout.
 * This screen allows selection of a single exercise with workout configuration.
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
    val isWorkoutSetupDialogVisible by viewModel.isWorkoutSetupDialogVisible.collectAsState()
    val hapticEvents = viewModel.hapticEvents
    val isAutoConnecting by viewModel.isAutoConnecting.collectAsState()
    val connectionError by viewModel.connectionError.collectAsState()

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
        // Reuse WorkoutTab content without the connection card
        WorkoutTab(
            connectionState = com.example.vitruvianredux.domain.model.ConnectionState.Connected("", ""),
            workoutState = workoutState,
            currentMetric = currentMetric,
            workoutParameters = workoutParameters,
            repCount = repCount,
            autoStopState = autoStopState,
            weightUnit = weightUnit,
            exerciseRepository = exerciseRepository,
            isWorkoutSetupDialogVisible = isWorkoutSetupDialogVisible,
            hapticEvents = hapticEvents,
            loadedRoutine = null,
            currentExerciseIndex = 0,
            kgToDisplay = viewModel::kgToDisplay,
            displayToKg = viewModel::displayToKg,
            formatWeight = viewModel::formatWeight,
            onScan = {},
            onDisconnect = {},
            onStartWorkout = {
                viewModel.ensureConnection(
                    onConnected = { viewModel.startWorkout() },
                    onFailed = { /* Error shown via StateFlow */ }
                )
            },
            onStopWorkout = { viewModel.stopWorkout() },
            onCancelRoutine = { viewModel.cancelRoutine() },
            onSkipRest = { viewModel.skipRest() },
            onResetForNewWorkout = { viewModel.resetForNewWorkout() },
            onUpdateParameters = { viewModel.updateWorkoutParameters(it) },
            onShowWorkoutSetupDialog = { viewModel.showWorkoutSetupDialog() },
            onHideWorkoutSetupDialog = { viewModel.hideWorkoutSetupDialog() },
            showConnectionCard = false,
            modifier = Modifier.padding(padding)
        )

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
