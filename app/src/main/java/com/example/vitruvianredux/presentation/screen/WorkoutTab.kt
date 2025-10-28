package com.example.vitruvianredux.presentation.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.vitruvianredux.domain.model.*
import com.example.vitruvianredux.presentation.viewmodel.AutoStopUiState
import com.example.vitruvianredux.ui.theme.*

@Composable
fun WorkoutTab(
    connectionState: ConnectionState,
    workoutState: WorkoutState,
    currentMetric: WorkoutMetric?,
    workoutParameters: WorkoutParameters,
    repCount: RepCount,
    autoStopState: AutoStopUiState,
    onScan: () -> Unit,
    onDisconnect: () -> Unit,
    onStartWorkout: () -> Unit,
    onStopWorkout: () -> Unit,
    onUpdateParameters: (WorkoutParameters) -> Unit,
    modifier: Modifier = Modifier
) {
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
            WorkoutParametersCard(
                workoutParameters = workoutParameters,
                workoutState = workoutState,
                autoStopState = autoStopState,
                onUpdateParameters = onUpdateParameters,
                onStartWorkout = onStartWorkout,
                onStopWorkout = onStopWorkout
            )

            if (workoutState is WorkoutState.Active || workoutState is WorkoutState.Countdown) {
                if (workoutState is WorkoutState.Countdown) {
                    CountdownCard(secondsRemaining = workoutState.secondsRemaining)
                } else {
                    RepCounterCard(repCount = repCount)
                }
            }

            if ((workoutState is WorkoutState.Active || workoutState is WorkoutState.Countdown) && currentMetric != null) {
                LiveMetricsCard(metric = currentMetric)
            }
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
        colors = CardDefaults.cardColors(containerColor = CardBackground),
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
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(Spacing.small))

            when (connectionState) {
                is ConnectionState.Disconnected -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Not connected", color = TextSecondary)
                        Button(
                            onClick = onScan,
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryPurple)
                        ) {
                            Icon(Icons.Default.Search, contentDescription = null)
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
                            modifier = Modifier.size(24.dp),
                            color = PrimaryPurple
                        )
                        Spacer(modifier = Modifier.width(Spacing.small))
                        Text("Scanning for devices...", color = TextPrimary)
                    }
                }
                is ConnectionState.Connecting -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = PrimaryPurple
                        )
                        Spacer(modifier = Modifier.width(Spacing.small))
                        Text("Connecting...", color = TextPrimary)
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
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                                Text(
                                    connectionState.deviceAddress,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary
                                )
                            }
                            IconButton(onClick = onDisconnect) {
                                Icon(Icons.Default.Close, contentDescription = "Disconnect", tint = TextPrimary)
                            }
                        }
                    }
                }
                is ConnectionState.Error -> {
                    Text(
                        "Error: ${connectionState.message}",
                        color = ErrorRed
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
    onUpdateParameters: (WorkoutParameters) -> Unit,
    onStartWorkout: () -> Unit,
    onStopWorkout: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CardBackground),
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
                color = TextPrimary
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
                    enabled = workoutState is WorkoutState.Idle,
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedContainerColor = SurfaceDarkGrey,
                        focusedContainerColor = SurfaceDarkGrey,
                        unfocusedBorderColor = TextTertiary,
                        focusedBorderColor = PrimaryPurple,
                        unfocusedLabelColor = TextSecondary,
                        focusedLabelColor = PrimaryPurple,
                        unfocusedTextColor = TextPrimary,
                        focusedTextColor = TextPrimary
                    )
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
                    containerColor = SurfaceDarkGrey,
                    shape = RoundedCornerShape(16.dp),
                    text = {
                        Column {
                            Text(
                                "Echo adapts to your output. Select a level:",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Spacer(modifier = Modifier.height(Spacing.medium))
                            listOf(
                                EchoLevel.LEVEL_1 to "Level 1 - Beginner (75% eccentric)",
                                EchoLevel.LEVEL_2 to "Level 2 - Intermediate",
                                EchoLevel.LEVEL_3 to "Level 3 - Advanced",
                                EchoLevel.LEVEL_4 to "Level 4 - Expert"
                            ).forEach { (level, label) ->
                                OutlinedButton(
                                    onClick = {
                                        onUpdateParameters(workoutParameters.copy(mode = WorkoutMode.Echo(level)))
                                        showEchoLevelDialog = false
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = PrimaryPurple
                                    ),
                                    border = BorderStroke(1.dp, PrimaryPurple)
                                ) {
                                    Text(label)
                                }
                                Spacer(modifier = Modifier.height(Spacing.small))
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { showEchoLevelDialog = false }) {
                            Text("Cancel", color = TextSecondary)
                        }
                    }
                )
            }

            var weightText by remember(workoutParameters) {
                mutableStateOf(workoutParameters.weightPerCableKg.toString())
            }
            OutlinedTextField(
                value = weightText,
                onValueChange = {
                    weightText = it
                    it.toFloatOrNull()?.let { weight ->
                        onUpdateParameters(workoutParameters.copy(weightPerCableKg = weight))
                    }
                },
                label = { Text("Weight per cable (kg)") },
                modifier = Modifier.fillMaxWidth(),
                enabled = workoutState is WorkoutState.Idle,
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = SurfaceDarkGrey,
                    focusedContainerColor = SurfaceDarkGrey,
                    unfocusedBorderColor = TextTertiary,
                    focusedBorderColor = PrimaryPurple,
                    unfocusedLabelColor = TextSecondary,
                    focusedLabelColor = PrimaryPurple,
                    unfocusedTextColor = TextPrimary,
                    focusedTextColor = TextPrimary
                )
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
                enabled = workoutState is WorkoutState.Idle && !workoutParameters.isJustLift,
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = SurfaceDarkGrey,
                    focusedContainerColor = SurfaceDarkGrey,
                    unfocusedBorderColor = TextTertiary,
                    focusedBorderColor = PrimaryPurple,
                    unfocusedLabelColor = TextSecondary,
                    focusedLabelColor = PrimaryPurple,
                    unfocusedTextColor = TextPrimary,
                    focusedTextColor = TextPrimary
                )
            )
            Spacer(modifier = Modifier.height(Spacing.small))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Just Lift", color = TextPrimary)
                Switch(
                    checked = workoutParameters.isJustLift,
                    onCheckedChange = { checked ->
                        if (workoutState is WorkoutState.Idle) {
                            onUpdateParameters(workoutParameters.copy(isJustLift = checked))
                        }
                    },
                    enabled = workoutState is WorkoutState.Idle,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = PrimaryPurple,
                        checkedTrackColor = SecondaryPurple,
                        uncheckedThumbColor = TextTertiary,
                        uncheckedTrackColor = SurfaceDarkGrey
                    )
                )
            }
            Spacer(modifier = Modifier.height(Spacing.small))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Stop at top of final rep", color = TextPrimary)
                Switch(
                    checked = workoutParameters.stopAtTop,
                    onCheckedChange = { checked ->
                        if (workoutState is WorkoutState.Idle) {
                            onUpdateParameters(workoutParameters.copy(stopAtTop = checked))
                        }
                    },
                    enabled = workoutState is WorkoutState.Idle && !workoutParameters.isJustLift,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = PrimaryPurple,
                        checkedTrackColor = SecondaryPurple,
                        uncheckedThumbColor = TextTertiary,
                        uncheckedTrackColor = SurfaceDarkGrey
                    )
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
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryPurple)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
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
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = PrimaryPurple)
                        Spacer(modifier = Modifier.width(Spacing.small))
                        Text("Initializing...")
                    }
                }
                is WorkoutState.Active -> {
                    Button(
                        onClick = onStopWorkout,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = ErrorRed)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = null)
                        Spacer(modifier = Modifier.width(Spacing.small))
                        Text("Stop Workout")
                    }
                }
                is WorkoutState.Completed -> {
                    Text(
                        "Workout completed!",
                        color = PrimaryPurple,
                        fontWeight = FontWeight.Bold
                    )
                }
                is WorkoutState.Error -> {
                    Text(
                        "Error: ${workoutState.message}",
                        color = ErrorRed
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
        colors = CardDefaults.cardColors(containerColor = CardBackground),
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
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(Spacing.small))
            LinearProgressIndicator(
                progress = { autoStopState.progress },
                modifier = Modifier.fillMaxWidth(),
                color = PrimaryPurple
            )
            Spacer(modifier = Modifier.height(Spacing.small))
            Text(
                if (autoStopState.secondsRemaining > 0) {
                    "Stopping in ${autoStopState.secondsRemaining}s"
                } else {
                    "Stopping..."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )
        }
    }
}

@Composable
fun RepCounterCard(repCount: RepCount) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, PrimaryPurple, RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(containerColor = CardBackground),
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
                fontWeight = FontWeight.Bold,
                color = TextPrimary
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
                        color = TextSecondary
                    )
                    Text(
                        repCount.warmupReps.toString(),
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "Working",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                    Text(
                        repCount.workingReps.toString(),
                        style = MaterialTheme.typography.displayMedium,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryPurple
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "Total",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                    Text(
                        repCount.totalReps.toString(),
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }
            }
        }
    }
}

@Composable
fun LiveMetricsCard(metric: WorkoutMetric) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CardBackground),
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
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(Spacing.small))

            // Total load (prominent)
            Text(
                "%.1f kg".format(metric.totalLoad),
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Bold,
                color = PrimaryPurple
            )
            Text("Total Load", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            
            Spacer(modifier = Modifier.height(Spacing.medium))

            // Individual cables
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "%.1f kg".format(metric.loadA),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text("Cable A", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    LinearProgressIndicator(
                        progress = { (metric.positionA / 1000f).coerceIn(0f, 1f) },
                        modifier = Modifier.width(100.dp).padding(top = Spacing.extraSmall),
                        color = PrimaryPurple
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "%.1f kg".format(metric.loadB),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text("Cable B", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    LinearProgressIndicator(
                        progress = { (metric.positionB / 1000f).coerceIn(0f, 1f) },
                        modifier = Modifier.width(100.dp).padding(top = Spacing.extraSmall),
                        color = PrimaryPurple
                    )
                }
            }
        }
    }
}
