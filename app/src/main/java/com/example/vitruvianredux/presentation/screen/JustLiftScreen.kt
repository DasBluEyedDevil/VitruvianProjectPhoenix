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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.vitruvianredux.domain.model.*
import com.example.vitruvianredux.presentation.components.CompactNumberPicker
import com.example.vitruvianredux.presentation.navigation.NavigationRoutes
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
    val isAutoConnecting by viewModel.isAutoConnecting.collectAsState()
    val connectionError by viewModel.connectionError.collectAsState()

    var selectedMode by remember { mutableStateOf(workoutParameters.workoutType.toWorkoutMode()) }
    var weightPerCable by remember { mutableStateOf(workoutParameters.weightPerCableKg) }
    var weightChangePerRep by remember { mutableStateOf(workoutParameters.progressionRegressionKg.toInt()) } // Progression/Regression value
    var restTime by remember { mutableStateOf(60) } // Rest time in seconds
    var eccentricLoad by remember { mutableStateOf(EccentricLoad.LOAD_100) }
    var echoLevel by remember { mutableStateOf(EchoLevel.HARDER) }

    LaunchedEffect(workoutParameters.workoutType) {
        val workoutType = workoutParameters.workoutType
        if (workoutType is WorkoutType.Echo) {
            eccentricLoad = workoutType.eccentricLoad
            echoLevel = workoutType.level
        }
    }

    LaunchedEffect(workoutState) {
        if (workoutState is WorkoutState.Active) {
            navController.navigate(NavigationRoutes.ActiveWorkout.route)
        }
    }

    // Enable handle detection for auto-start when screen is shown and connected
    val connectionState by viewModel.connectionState.collectAsState()
    LaunchedEffect(connectionState) {
        if (connectionState is ConnectionState.Connected) {
            viewModel.enableHandleDetection()
        }
    }

    // Reset workout state if entering Just Lift with a completed workout
    LaunchedEffect(workoutState) {
        if (workoutState is WorkoutState.Completed) {
            viewModel.prepareForJustLift()
        }
    }

    LaunchedEffect(selectedMode, weightPerCable, weightChangePerRep, restTime) {
        val weightChangeKg = if (weightUnit == WeightUnit.LB) {
            weightChangePerRep / 2.20462f
        } else {
            weightChangePerRep.toFloat()
        }

        val updatedParameters = workoutParameters.copy(
            workoutType = selectedMode.toWorkoutType(eccentricLoad),
            weightPerCableKg = weightPerCable,
            progressionRegressionKg = weightChangeKg,
            isJustLift = true,
            useAutoStart = true // Enable auto-start for Just Lift
        )
        viewModel.updateWorkoutParameters(updatedParameters)
    }

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
                    .padding(Spacing.large)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Spacing.medium)
            ) {
                // Auto-start/Auto-stop unified card (at top for visibility)
                val autoStartCountdown by viewModel.autoStartCountdown.collectAsState()
                AutoStartStopCard(
                    workoutState = workoutState,
                    autoStartCountdown = autoStartCountdown,
                    autoStopState = autoStopState
                )

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
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(Spacing.medium)
                    ) {
                        Text(
                            "Workout Mode",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(Spacing.small))

                        // Mode chips: Old School, Pump, Echo
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(Spacing.small)
                        ) {
                            FilterChip(
                                selected = selectedMode is WorkoutMode.OldSchool,
                                onClick = { selectedMode = WorkoutMode.OldSchool },
                                label = { Text("Old School") },
                                leadingIcon = if (selectedMode is WorkoutMode.OldSchool) {
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
                                selected = selectedMode is WorkoutMode.Echo,
                                onClick = { selectedMode = WorkoutMode.Echo(echoLevel) },
                                label = { Text("Echo") },
                                leadingIcon = if (selectedMode is WorkoutMode.Echo) {
                                    { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                            } else null
                            )
                        }

                        Spacer(modifier = Modifier.height(Spacing.small))

                        Text(
                            when (selectedMode) {
                                is WorkoutMode.OldSchool -> "Constant resistance throughout the movement."
                                is WorkoutMode.Pump -> "Resistance increases the faster you go."
                                is WorkoutMode.Echo -> "Adaptive resistance with echo feedback."
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

                // Mode-specific options

                // OLD SCHOOL & PUMP: Weight per cable, Progression/Regression, Rest Time
                val isOldSchoolOrPump = selectedMode is WorkoutMode.OldSchool || selectedMode is WorkoutMode.Pump
                if (isOldSchoolOrPump) {
                    // Weight per Cable Card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(16.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(Spacing.medium)
                        ) {
                            val weightSuffix = if (weightUnit == WeightUnit.LB) "lbs" else "kg"
                            val maxWeight = if (weightUnit == WeightUnit.LB) 220 else 100
                            val displayWeight = if (weightUnit == WeightUnit.LB) {
                                (weightPerCable * 2.20462f).toInt() // Convert kg to lbs
                            } else {
                                weightPerCable.toInt()
                            }

                            CompactNumberPicker(
                                value = displayWeight,
                                onValueChange = { newValue ->
                                    weightPerCable = if (weightUnit == WeightUnit.LB) {
                                        newValue / 2.20462f
                                    } else {
                                        newValue.toFloat()
                                    }
                                },
                                range = 1..maxWeight,
                                label = "Weight per Cable",
                                suffix = weightSuffix,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    // Weight Change Per Rep Card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(16.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(Spacing.medium)
                        ) {
                            val weightSuffix = if (weightUnit == WeightUnit.LB) "lbs" else "kg"
                            val maxWeightChange = 10

                            CompactNumberPicker(
                                value = weightChangePerRep,
                                onValueChange = { weightChangePerRep = it },
                                range = -maxWeightChange..maxWeightChange,
                                label = "Weight Change Per Rep",
                                suffix = weightSuffix,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Text(
                                "Negative = Regression, Positive = Progression",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = Spacing.small)
                            )
                        }
                    }
                }

                // ECHO MODE: Eccentric Load, Echo Level, Rest Time
                val isEchoMode = selectedMode is WorkoutMode.Echo
                if (isEchoMode) {
                    // Eccentric Load Card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(16.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(Spacing.medium)
                        ) {
                            Text(
                                "Eccentric Load: ${eccentricLoad.percentage}%",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(Spacing.medium))

                            // Slider with discrete values: 0%, 50%, 75%, 100%, 125%, 150% (machine hardware limit)
                            val eccentricLoadValues = listOf(
                                EccentricLoad.LOAD_0,
                                EccentricLoad.LOAD_50,
                                EccentricLoad.LOAD_75,
                                EccentricLoad.LOAD_100,
                                EccentricLoad.LOAD_125,
                                EccentricLoad.LOAD_150
                            )
                            val currentIndex = eccentricLoadValues.indexOf(eccentricLoad).let {
                                if (it < 0) 3 else it // Default to 100% if not found
                            }

                            Slider(
                                value = currentIndex.toFloat(),
                                onValueChange = { value ->
                                    val index = value.toInt().coerceIn(0, eccentricLoadValues.size - 1)
                                    eccentricLoad = eccentricLoadValues[index]
                                },
                                valueRange = 0f..(eccentricLoadValues.size - 1).toFloat(),
                                steps = eccentricLoadValues.size - 2,
                                modifier = Modifier.fillMaxWidth()
                            )

                            Spacer(modifier = Modifier.height(Spacing.small))

                            Text(
                                "Load percentage applied during eccentric (lowering) phase",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Echo Level Card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(16.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(Spacing.medium)
                        ) {
                            Text(
                                "Echo Level",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(Spacing.small))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(Spacing.small)
                            ) {
                                EchoLevel.entries.forEach { level ->
                                    FilterChip(
                                        selected = echoLevel == level,
                                        onClick = {
                                            echoLevel = level
                                            selectedMode = WorkoutMode.Echo(level)
                                        },
                                        label = {
                                            Text(
                                                level.displayName,
                                                style = MaterialTheme.typography.bodySmall,
                                                maxLines = 1,
                                                softWrap = false
                                            )
                                        },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }
                }

                // Current workout status if active
                if (workoutState !is WorkoutState.Idle) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = Spacing.medium))

                    ActiveStatusCard(
                        workoutState = workoutState,
                        currentMetric = currentMetric,
                        repCount = repCount,
                        weightUnit = weightUnit,
                        formatWeight = viewModel::formatWeight,
                        onStopWorkout = { viewModel.stopWorkout() }
                    )
                }
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
}

/**
 * Simple workout status card showing current state.
 */
@Composable
fun ActiveStatusCard(
    workoutState: WorkoutState,
    currentMetric: WorkoutMetric?,
    repCount: RepCount,
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

/**
 * Unified Auto-Start/Auto-Stop Card for Just Lift Mode
 * Shows auto-start when idle, auto-stop when active
 */
@Composable
fun AutoStartStopCard(
    workoutState: WorkoutState,
    autoStartCountdown: Int?,
    autoStopState: com.example.vitruvianredux.presentation.viewmodel.AutoStopUiState
) {
    val isIdle = workoutState is WorkoutState.Idle
    val isActive = workoutState is WorkoutState.Active

    // Show card when idle (for auto-start) or active (for auto-stop)
    if (isIdle || isActive) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = when {
                    autoStartCountdown != null -> MaterialTheme.colorScheme.primaryContainer
                    autoStopState.isActive -> MaterialTheme.colorScheme.errorContainer
                    isActive -> MaterialTheme.colorScheme.surfaceVariant
                    else -> MaterialTheme.colorScheme.tertiaryContainer // More visible than secondaryContainer
                }
            ),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
            border = BorderStroke(2.dp, if (isIdle) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.outline)
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
                        imageVector = if (isIdle) Icons.Default.PlayCircle else Icons.Default.PanTool,
                        contentDescription = null,
                        modifier = Modifier.size(32.dp),
                        tint = when {
                            autoStartCountdown != null -> MaterialTheme.colorScheme.onPrimaryContainer
                            autoStopState.isActive -> MaterialTheme.colorScheme.onErrorContainer
                            isActive -> MaterialTheme.colorScheme.onSurfaceVariant
                            else -> MaterialTheme.colorScheme.onSecondaryContainer
                        }
                    )
                    Spacer(Modifier.width(Spacing.small))
                    Text(
                        text = when {
                            autoStartCountdown != null -> "Starting..."
                            autoStopState.isActive -> "Stopping in ${autoStopState.secondsRemaining}s..."
                            isActive -> "Auto-Stop Ready"
                            else -> "Auto-Start Ready"
                        },
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = when {
                            autoStartCountdown != null -> MaterialTheme.colorScheme.onPrimaryContainer
                            autoStopState.isActive -> MaterialTheme.colorScheme.onErrorContainer
                            isActive -> MaterialTheme.colorScheme.onSurfaceVariant
                            else -> MaterialTheme.colorScheme.onTertiaryContainer
                        }
                    )
                }

                Spacer(modifier = Modifier.height(Spacing.small))

                // Progress for countdowns
                if (autoStartCountdown != null) {
                    // Auto-start is a short ~1s hold; show indeterminate progress for a smooth feel
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(Spacing.small))
                } else if (autoStopState.isActive) {
                    LinearProgressIndicator(
                        progress = { autoStopState.progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp),
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.height(Spacing.small))
                }

                // Instructions
                val instructionText = if (isIdle) {
                    "Grab and hold handles briefly (~1s) to start"
                } else {
                    "Put handles down for 3 seconds to stop"
                }
                Text(
                    text = instructionText,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = when {
                        autoStartCountdown != null -> MaterialTheme.colorScheme.onPrimaryContainer
                        autoStopState.isActive -> MaterialTheme.colorScheme.onErrorContainer
                        isActive -> MaterialTheme.colorScheme.onSurfaceVariant
                        else -> MaterialTheme.colorScheme.onTertiaryContainer
                    },
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
