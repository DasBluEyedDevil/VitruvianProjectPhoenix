package com.example.vitruvianredux.presentation.screen

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.vitruvianredux.domain.model.*
import com.example.vitruvianredux.presentation.viewmodel.MainViewModel
import com.example.vitruvianredux.ui.theme.Spacing

/**
 * Just Lift screen - quick workout configuration.
 * Allows user to select mode, eccentric load percentage, and progression/regression.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JustLiftScreen(
    navController: NavController,
    viewModel: MainViewModel,
    themeMode: com.example.vitruvianredux.ui.theme.ThemeMode
) {
    val workoutState by viewModel.workoutState.collectAsState()
    val workoutParameters by viewModel.workoutParameters.collectAsState()
    val currentMetric by viewModel.currentMetric.collectAsState()
    val repCount by viewModel.repCount.collectAsState()
    val autoStopState by viewModel.autoStopState.collectAsState()
    val weightUnit by viewModel.weightUnit.collectAsState()
    val isWorkoutSetupDialogVisible by viewModel.isWorkoutSetupDialogVisible.collectAsState()
    val isAutoConnecting by viewModel.isAutoConnecting.collectAsState()
    val connectionError by viewModel.connectionError.collectAsState()

    var selectedMode by remember { mutableStateOf<WorkoutMode>(WorkoutMode.EccentricOnly) }
    var eccentricLoad by remember { mutableStateOf(EccentricLoad.LOAD_100) }
    var useProgression by remember { mutableStateOf(false) }
    var useRegression by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Just Lift") },
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
        val backgroundGradient = if (themeMode == com.example.vitruvianredux.ui.theme.ThemeMode.DARK) {
            Brush.verticalGradient(
                colors = listOf(
                    Color(0xFF0F172A), // slate-900
                    Color(0xFF1E1B4B), // indigo-950
                    Color(0xFF172554)  // blue-950
                )
            )
        } else {
            Brush.verticalGradient(
                colors = listOf(
                    Color(0xFFE0E7FF), // indigo-200 - soft lavender
                    Color(0xFFFCE7F3), // pink-100 - soft pink
                    Color(0xFFDDD6FE)  // violet-200 - soft violet
                )
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(backgroundGradient)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
            // Mode Selection Card
            var isModePressed by remember { mutableStateOf(false) }
            val modeScale by animateFloatAsState(
                targetValue = if (isModePressed) 0.99f else 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = 400f
                ),
                label = "modeScale"
            )
            Card(
                onClick = { isModePressed = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .scale(modeScale),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = if (isModePressed) 2.dp else 4.dp),
                border = BorderStroke(1.dp, Color(0xFFF5F3FF))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        "Workout Mode",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    // Mode chips
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = selectedMode is WorkoutMode.EccentricOnly,
                            onClick = { selectedMode = WorkoutMode.EccentricOnly },
                            label = { Text("Eccentric Only") },
                            leadingIcon = if (selectedMode is WorkoutMode.EccentricOnly) {
                                { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                            } else null
                        )
                        FilterChip(
                            selected = selectedMode is WorkoutMode.Pump,
                            onClick = { selectedMode = WorkoutMode.Pump },
                            label = { Text("Pump") },
                            leadingIcon = if (selectedMode is WorkoutMode.Pump) {
                                { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                            } else null
                        )
                        FilterChip(
                            selected = selectedMode is WorkoutMode.OldSchool,
                            onClick = { selectedMode = WorkoutMode.OldSchool },
                            label = { Text("Old School") },
                            leadingIcon = if (selectedMode is WorkoutMode.OldSchool) {
                                { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                            } else null
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        when (selectedMode) {
                            is WorkoutMode.EccentricOnly -> "Resistance only on the eccentric (lowering) phase."
                            is WorkoutMode.Pump -> "Resistance increases the faster you go."
                            is WorkoutMode.OldSchool -> "Constant resistance throughout the movement."
                            else -> selectedMode.displayName
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            LaunchedEffect(isModePressed) {
                if (isModePressed) {
                    kotlinx.coroutines.delay(100)
                    isModePressed = false
                }
            }

            // Eccentric Load Percentage Card
            var isLoadPressed by remember { mutableStateOf(false) }
            val loadScale by animateFloatAsState(
                targetValue = if (isLoadPressed) 0.99f else 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = 400f
                ),
                label = "loadScale"
            )
            Card(
                onClick = { isLoadPressed = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .scale(loadScale),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = if (isLoadPressed) 2.dp else 4.dp),
                border = BorderStroke(1.dp, Color(0xFFF5F3FF))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        "Eccentric Load: ${eccentricLoad.percentage}%",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    // Discrete slider with 7 values: 50%, 75%, 100%, 125%, 150%, 175%, 200%
                    val eccentricLoadValues = EccentricLoad.values()
                    val sliderPosition = eccentricLoadValues.indexOf(eccentricLoad).toFloat()

                    Slider(
                        value = sliderPosition,
                        onValueChange = { value ->
                            eccentricLoad = eccentricLoadValues[value.toInt()]
                        },
                        valueRange = 0f..(eccentricLoadValues.size - 1).toFloat(),
                        steps = eccentricLoadValues.size - 2,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Value labels
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        eccentricLoadValues.forEach {
                            Text(it.displayName, style = MaterialTheme.typography.bodySmall)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        "Load percentage applied during eccentric (lowering) phase",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            LaunchedEffect(isLoadPressed) {
                if (isLoadPressed) {
                    kotlinx.coroutines.delay(100)
                    isLoadPressed = false
                }
            }

            // Progression/Regression Card
            var isAdaptivePressed by remember { mutableStateOf(false) }
            val adaptiveScale by animateFloatAsState(
                targetValue = if (isAdaptivePressed) 0.99f else 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = 400f
                ),
                label = "adaptiveScale"
            )
            Card(
                onClick = { isAdaptivePressed = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .scale(adaptiveScale),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = if (isAdaptivePressed) 2.dp else 4.dp),
                border = BorderStroke(1.dp, Color(0xFFF5F3FF))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        "Adaptive Load",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = useProgression,
                            onCheckedChange = {
                                useProgression = it
                                if (it) useRegression = false
                            }
                        )
                        Spacer(modifier = Modifier.width(Spacing.small))
                        Column {
                            Text(
                                "Progression",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                "Increase load by 5% each rep",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = useRegression,
                            onCheckedChange = {
                                useRegression = it
                                if (it) useProgression = false
                            }
                        )
                        Spacer(modifier = Modifier.width(Spacing.small))
                        Column {
                            Text(
                                "Regression",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                "Decrease load by 5% each rep",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            LaunchedEffect(isAdaptivePressed) {
                if (isAdaptivePressed) {
                    kotlinx.coroutines.delay(100)
                    isAdaptivePressed = false
                }
            }

            // Start Workout Button
            Button(
                onClick = {
                    viewModel.ensureConnection(
                        onConnected = {
                            val updatedParameters = workoutParameters.copy(
                                mode = selectedMode,
                                eccentricLoad = eccentricLoad,
                                progressionRegressionKg = when {
                                    useProgression -> 2.5f
                                    useRegression -> -2.5f
                                    else -> 0f
                                }
                            )
                            viewModel.updateWorkoutParameters(updatedParameters)
                            viewModel.showWorkoutSetupDialog()
                        },
                        onFailed = { /* Error shown via StateFlow */ }
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                enabled = workoutState is WorkoutState.Idle,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Start Workout", style = MaterialTheme.typography.titleMedium)
            }

            // Current workout status if active
            if (workoutState !is WorkoutState.Idle) {
                Divider(modifier = Modifier.padding(vertical = Spacing.medium))

                WorkoutStatusCard(
                    workoutState = workoutState,
                    currentMetric = currentMetric,
                    repCount = repCount,
                    autoStopState = autoStopState,
                    weightUnit = weightUnit,
                    formatWeight = viewModel::formatWeight,
                    onStopWorkout = { viewModel.stopWorkout() }
                )
            }
            }
        }

        // Workout Setup Dialog
        if (isWorkoutSetupDialogVisible) {
            WorkoutSetupDialog(
                workoutParameters = workoutParameters,
                weightUnit = weightUnit,
                kgToDisplay = viewModel::kgToDisplay,
                displayToKg = viewModel::displayToKg,
                onUpdateParameters = { viewModel.updateWorkoutParameters(it) },
                onStartWorkout = {
                    viewModel.hideWorkoutSetupDialog()
                    viewModel.startWorkout()
                },
                onDismiss = { viewModel.hideWorkoutSetupDialog() },
                exerciseRepository = viewModel.exerciseRepository
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

/**
 * Simple workout status card showing current state.
 */
@Composable
fun WorkoutStatusCard(
    workoutState: WorkoutState,
    currentMetric: WorkoutMetric?,
    repCount: RepCount,
    autoStopState: com.example.vitruvianredux.presentation.viewmodel.AutoStopUiState,
    weightUnit: WeightUnit,
    formatWeight: (Float, WeightUnit) -> String,
    onStopWorkout: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.medium)
        ) {
            Text(
                when (workoutState) {
                    is WorkoutState.Countdown -> "Get Ready: ${workoutState.secondsRemaining}s"
                    is WorkoutState.Active -> "Workout Active"
                    is WorkoutState.Resting -> "Resting: ${workoutState.restSecondsRemaining}s"
                    is WorkoutState.Completed -> "Workout Complete"
                    else -> "Workout Status"
                },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )

            if (workoutState is WorkoutState.Active) {
                Spacer(modifier = Modifier.height(Spacing.small))
                Text(
                    "Reps: ${repCount.totalReps}",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )

                currentMetric?.let { metric ->
                    Text(
                        "Load: ${formatWeight(metric.totalLoad, weightUnit)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }

                Spacer(modifier = Modifier.height(Spacing.medium))

                Button(
                    onClick = onStopWorkout,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(Icons.Default.Close, contentDescription = null)
                    Spacer(modifier = Modifier.width(Spacing.small))
                    Text("Stop Workout")
                }
            }
        }
    }
}
