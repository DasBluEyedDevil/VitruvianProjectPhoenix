package com.example.vitruvianredux.presentation.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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

@Composable
fun WorkoutTab(
    connectionState: ConnectionState,
    workoutState: WorkoutState,
    currentMetric: WorkoutMetric?,
    workoutParameters: WorkoutParameters,
    repCount: RepCount,
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
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Connection Card
        ConnectionCard(
            connectionState = connectionState,
            onScan = onScan,
            onDisconnect = onDisconnect
        )

        // Workout Controls (only when connected)
        if (connectionState is ConnectionState.Connected) {
            WorkoutParametersCard(
                workoutParameters = workoutParameters,
                workoutState = workoutState,
                onUpdateParameters = onUpdateParameters,
                onStartWorkout = onStartWorkout,
                onStopWorkout = onStopWorkout
            )

            // Rep Counter (when workout active or counting down)
            if (workoutState is WorkoutState.Active || workoutState is WorkoutState.Countdown) {
                // Countdown overlay
                if (workoutState is WorkoutState.Countdown) {
                    CountdownCard(secondsRemaining = workoutState.secondsRemaining)
                } else {
                    RepCounterCard(repCount = repCount)
                }
            }

            // Live Metrics (when workout active or counting down)
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
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                "Connection",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(12.dp))

            when (connectionState) {
                is ConnectionState.Disconnected -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Not connected", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Button(onClick = onScan) {
                            Icon(Icons.Default.Search, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
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
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Scanning for devices...")
                    }
                }
                is ConnectionState.Connecting -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Connecting...")
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
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    connectionState.deviceAddress,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(onClick = onDisconnect) {
                                Icon(Icons.Default.Close, contentDescription = "Disconnect")
                            }
                        }
                    }
                }
                is ConnectionState.Error -> {
                    Text(
                        "Error: ${connectionState.message}",
                        color = MaterialTheme.colorScheme.error
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
    onUpdateParameters: (WorkoutParameters) -> Unit,
    onStartWorkout: () -> Unit,
    onStopWorkout: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                "Workout Setup",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(12.dp))

            // Mode selector
            var showModeMenu by remember { mutableStateOf(false) }
            var showEchoLevelDialog by remember { mutableStateOf(false) }
            
            ExposedDropdownMenuBox(
                expanded = showModeMenu,
                onExpandedChange = { showModeMenu = !showModeMenu && workoutState is WorkoutState.Idle }
            ) {
                OutlinedTextField(
                    value = workoutParameters.mode.displayName,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Workout Mode") },
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = showModeMenu)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(),
                    enabled = workoutState is WorkoutState.Idle,
                    colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors()
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
            Spacer(modifier = Modifier.height(8.dp))
            
            // Echo Level Selection Dialog
            if (showEchoLevelDialog) {
                AlertDialog(
                    onDismissRequest = { showEchoLevelDialog = false },
                    title = { Text("Select Echo Level") },
                    text = {
                        Column {
                            Text(
                                "Echo mode mirrors your force output for adaptive resistance.",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            // Level 1
                            OutlinedButton(
                                onClick = {
                                    onUpdateParameters(workoutParameters.copy(mode = WorkoutMode.Echo(EchoLevel.LEVEL_1)))
                                    showEchoLevelDialog = false
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Level 1 - Beginner (75% eccentric)")
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            // Level 2
                            OutlinedButton(
                                onClick = {
                                    onUpdateParameters(workoutParameters.copy(mode = WorkoutMode.Echo(EchoLevel.LEVEL_2)))
                                    showEchoLevelDialog = false
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Level 2 - Intermediate")
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            // Level 3
                            OutlinedButton(
                                onClick = {
                                    onUpdateParameters(workoutParameters.copy(mode = WorkoutMode.Echo(EchoLevel.LEVEL_3)))
                                    showEchoLevelDialog = false
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Level 3 - Advanced")
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            // Level 4
                            OutlinedButton(
                                onClick = {
                                    onUpdateParameters(workoutParameters.copy(mode = WorkoutMode.Echo(EchoLevel.LEVEL_4)))
                                    showEchoLevelDialog = false
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Level 4 - Expert")
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { showEchoLevelDialog = false }) {
                            Text("Cancel")
                        }
                    }
                )
            }

            // Weight input
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
                enabled = workoutState is WorkoutState.Idle
            )
            Spacer(modifier = Modifier.height(8.dp))

            // Reps input
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
                enabled = workoutState is WorkoutState.Idle
            )
            Spacer(modifier = Modifier.height(12.dp))

            // Workout control buttons
            when (workoutState) {
                is WorkoutState.Idle -> {
                    Button(
                        onClick = onStartWorkout,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Start Workout")
                    }
                }
                is WorkoutState.Initializing -> {
                    Button(
                        onClick = {},
                        modifier = Modifier.fillMaxWidth(),
                        enabled = false
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Initializing...")
                    }
                }
                is WorkoutState.Active -> {
                    Button(
                        onClick = onStopWorkout,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Icon(Icons.Default.Close, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Stop Workout")
                    }
                }
                is WorkoutState.Completed -> {
                    Text(
                        "Workout completed!",
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
                is WorkoutState.Error -> {
                    Text(
                        "Error: ${workoutState.message}",
                        color = MaterialTheme.colorScheme.error
                    )
                }
                else -> {}
            }
        }
    }
}

@Composable
fun RepCounterCard(repCount: RepCount) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                "Reps",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "Warmup",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        repCount.warmupReps.toString(),
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "Working",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        repCount.workingReps.toString(),
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "Total",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        repCount.totalReps.toString(),
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun LiveMetricsCard(metric: WorkoutMetric) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                "Live Metrics",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(12.dp))

            // Total load (prominent)
            Text(
                "%.1f kg".format(metric.totalLoad),
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text("Total Load", style = MaterialTheme.typography.bodySmall)
            
            Spacer(modifier = Modifier.height(16.dp))

            // Individual cables
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "%.1f kg".format(metric.loadA),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text("Cable A", style = MaterialTheme.typography.bodySmall)
                    LinearProgressIndicator(
                        progress = { (metric.positionA / 1000f).coerceIn(0f, 1f) },
                        modifier = Modifier.width(100.dp).padding(top = 4.dp)
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "%.1f kg".format(metric.loadB),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text("Cable B", style = MaterialTheme.typography.bodySmall)
                    LinearProgressIndicator(
                        progress = { (metric.positionB / 1000f).coerceIn(0f, 1f) },
                        modifier = Modifier.width(100.dp).padding(top = 4.dp)
                    )
                }
            }
        }
    }
}
