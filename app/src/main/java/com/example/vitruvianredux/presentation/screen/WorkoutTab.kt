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
    weightUnit: WeightUnit,
    isWorkoutSetupDialogVisible: Boolean = false,
    hapticEvents: kotlinx.coroutines.flow.SharedFlow<HapticEvent>? = null,
    kgToDisplay: (Float, WeightUnit) -> Float,
    displayToKg: (Float, WeightUnit) -> Float,
    formatWeight: (Float, WeightUnit) -> String,
    onScan: () -> Unit,
    onDisconnect: () -> Unit,
    onStartWorkout: () -> Unit,
    onStopWorkout: () -> Unit,
    onResetForNewWorkout: () -> Unit,
    onUpdateParameters: (WorkoutParameters) -> Unit,
    onShowWorkoutSetupDialog: () -> Unit = {},
    onHideWorkoutSetupDialog: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    // Haptic feedback effect
    hapticEvents?.let {
        HapticFeedbackEffect(hapticEvents = it)
    }

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
            // Show setup button when in Idle state, otherwise show workout controls
            when (workoutState) {
                is WorkoutState.Idle -> {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
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
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(Spacing.small))
                            Button(
                                onClick = onShowWorkoutSetupDialog,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Settings, contentDescription = "Configure workout")
                                Spacer(modifier = Modifier.width(Spacing.small))
                                Text("Setup Workout")
                            }
                        }
                    }
                }
                is WorkoutState.Completed -> {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        shape = RoundedCornerShape(16.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(Spacing.medium),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(Spacing.small)
                        ) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(48.dp)
                            )
                            Text(
                                "Workout Completed!",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(Spacing.small))
                            Button(
                                onClick = onResetForNewWorkout,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = "Start new workout")
                                Spacer(modifier = Modifier.width(Spacing.small))
                                Text("Start New Workout")
                            }
                        }
                    }
                }
                is WorkoutState.Active -> {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        shape = RoundedCornerShape(16.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(Spacing.medium)
                        ) {
                            Text(
                                "Workout Active",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(Spacing.small))
                            
                            if (autoStopState.isActive) {
                                JustLiftAutoStopCard(autoStopState = autoStopState)
                                Spacer(modifier = Modifier.height(Spacing.medium))
                            }
                            
                            Button(
                                onClick = onStopWorkout,
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Stop workout")
                                Spacer(modifier = Modifier.width(Spacing.small))
                                Text("Stop Workout")
                            }
                        }
                    }
                }
                else -> {}
            }

            if (workoutState is WorkoutState.Active || workoutState is WorkoutState.Countdown) {
                if (workoutState is WorkoutState.Countdown) {
                    CountdownCard(secondsRemaining = workoutState.secondsRemaining)
                } else {
                    RepCounterCard(repCount = repCount)
                }
            }

            if ((workoutState is WorkoutState.Active || workoutState is WorkoutState.Countdown) && currentMetric != null) {
                LiveMetricsCard(
                    metric = currentMetric,
                    weightUnit = weightUnit,
                    formatWeight = formatWeight
                )
            }
        }
    }

    // Show the workout setup dialog
    if (isWorkoutSetupDialogVisible) {
        WorkoutSetupDialog(
            workoutParameters = workoutParameters,
            weightUnit = weightUnit,
            kgToDisplay = kgToDisplay,
            displayToKg = displayToKg,
            onUpdateParameters = onUpdateParameters,
            onStartWorkout = {
                onStartWorkout()
                onHideWorkoutSetupDialog()
            },
            onDismiss = onHideWorkoutSetupDialog
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutSetupDialog(
    workoutParameters: WorkoutParameters,
    weightUnit: WeightUnit,
    kgToDisplay: (Float, WeightUnit) -> Float,
    displayToKg: (Float, WeightUnit) -> Float,
    onUpdateParameters: (WorkoutParameters) -> Unit,
    onStartWorkout: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp),
        title = {
            Text(
                "Workout Setup",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Spacing.small)
            ) {
                var showModeMenu by remember { mutableStateOf(false) }
                var showEchoLevelDialog by remember { mutableStateOf(false) }

                val modeLabel = if (workoutParameters.isJustLift) "Base Mode (resistance profile)" else "Workout Mode"
                ExposedDropdownMenuBox(
                    expanded = showModeMenu,
                    onExpandedChange = { showModeMenu = !showModeMenu }
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
                        colors = OutlinedTextFieldDefaults.colors()
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

                if (showEchoLevelDialog) {
                    AlertDialog(
                        onDismissRequest = { showEchoLevelDialog = false },
                        title = { Text("Select Echo Level") },
                        containerColor = MaterialTheme.colorScheme.surface,
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
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(label)
                                    }
                                    Spacer(modifier = Modifier.height(Spacing.small))
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

                var weightText by remember(workoutParameters.weightPerCableKg, weightUnit) {
                    mutableStateOf(kgToDisplay(workoutParameters.weightPerCableKg, weightUnit).toString())
                }
                OutlinedTextField(
                    value = weightText,
                    onValueChange = {
                        weightText = it
                        it.toFloatOrNull()?.let { displayWeight ->
                            val kg = displayToKg(displayWeight, weightUnit)
                            onUpdateParameters(workoutParameters.copy(weightPerCableKg = kg))
                        }
                    },
                    label = { Text("Weight per cable (${weightUnit.name.lowercase()})") },
                    modifier = Modifier.fillMaxWidth()
                )

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
                    enabled = !workoutParameters.isJustLift
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Just Lift")
                    Switch(
                        checked = workoutParameters.isJustLift,
                        onCheckedChange = { checked ->
                            onUpdateParameters(workoutParameters.copy(isJustLift = checked))
                        }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Stop at top of final rep")
                    Switch(
                        checked = workoutParameters.stopAtTop,
                        onCheckedChange = { checked ->
                            onUpdateParameters(workoutParameters.copy(stopAtTop = checked))
                        },
                        enabled = !workoutParameters.isJustLift
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onStartWorkout
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = "Start workout")
                Spacer(modifier = Modifier.width(Spacing.small))
                Text("Start Workout")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
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
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
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
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(Spacing.small))

            when (connectionState) {
                is ConnectionState.Disconnected -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Not connected", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Button(
                            onClick = onScan
                        ) {
                            Icon(Icons.Default.Search, contentDescription = "Scan for devices")
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
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(Spacing.small))
                        Text("Scanning for devices...")
                    }
                }
                is ConnectionState.Connecting -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(Spacing.small))
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
    autoStopState: AutoStopUiState,
    weightUnit: WeightUnit,
    kgToDisplay: (Float, WeightUnit) -> Float,
    displayToKg: (Float, WeightUnit) -> Float,
    onUpdateParameters: (WorkoutParameters) -> Unit,
    onStartWorkout: () -> Unit,
    onStopWorkout: () -> Unit,
    onResetForNewWorkout: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
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
                color = MaterialTheme.colorScheme.onSurface
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
                    enabled = workoutState is WorkoutState.Idle
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
                    containerColor = MaterialTheme.colorScheme.surface,
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
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(label)
                                }
                                Spacer(modifier = Modifier.height(Spacing.small))
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

            var weightText by remember(workoutParameters.weightPerCableKg, weightUnit) {
                mutableStateOf(kgToDisplay(workoutParameters.weightPerCableKg, weightUnit).toString())
            }
            OutlinedTextField(
                value = weightText,
                onValueChange = {
                    weightText = it
                    it.toFloatOrNull()?.let { displayWeight ->
                        val kg = displayToKg(displayWeight, weightUnit)
                        onUpdateParameters(workoutParameters.copy(weightPerCableKg = kg))
                    }
                },
                label = { Text("Weight per cable (${weightUnit.name.lowercase()})") },
                modifier = Modifier.fillMaxWidth(),
                enabled = workoutState is WorkoutState.Idle
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
                enabled = workoutState is WorkoutState.Idle && !workoutParameters.isJustLift
            )
            Spacer(modifier = Modifier.height(Spacing.small))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Just Lift")
                Switch(
                    checked = workoutParameters.isJustLift,
                    onCheckedChange = { checked ->
                        if (workoutState is WorkoutState.Idle) {
                            onUpdateParameters(workoutParameters.copy(isJustLift = checked))
                        }
                    },
                    enabled = workoutState is WorkoutState.Idle
                )
            }
            Spacer(modifier = Modifier.height(Spacing.small))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Stop at top of final rep")
                Switch(
                    checked = workoutParameters.stopAtTop,
                    onCheckedChange = { checked ->
                        if (workoutState is WorkoutState.Idle) {
                            onUpdateParameters(workoutParameters.copy(stopAtTop = checked))
                        }
                    },
                    enabled = workoutState is WorkoutState.Idle && !workoutParameters.isJustLift
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
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = "Start workout")
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
                        CircularProgressIndicator(modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(Spacing.small))
                        Text("Initializing...")
                    }
                }
                is WorkoutState.Active -> {
                    Button(
                        onClick = onStopWorkout,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Stop workout")
                        Spacer(modifier = Modifier.width(Spacing.small))
                        Text("Stop Workout")
                    }
                }
                is WorkoutState.Completed -> {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(Spacing.small)
                    ) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(48.dp)
                        )
                        Text(
                            "Workout Completed!",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(Spacing.small))
                        Button(
                            onClick = onResetForNewWorkout,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = "Start new workout")
                            Spacer(modifier = Modifier.width(Spacing.small))
                            Text("Start New Workout")
                        }
                    }
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
fun JustLiftAutoStopCard(autoStopState: AutoStopUiState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
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
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(Spacing.small))
            LinearProgressIndicator(
                progress = { autoStopState.progress },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(Spacing.small))
            Text(
                if (autoStopState.secondsRemaining > 0) {
                    "Stopping in ${autoStopState.secondsRemaining}s"
                } else {
                    "Stopping..."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun RepCounterCard(repCount: RepCount) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
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
                fontWeight = FontWeight.Bold
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
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        repCount.warmupReps.toString(),
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "Working",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        repCount.workingReps.toString(),
                        style = MaterialTheme.typography.displayMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "Total",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        repCount.totalReps.toString(),
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun LiveMetricsCard(
    metric: WorkoutMetric,
    weightUnit: WeightUnit,
    formatWeight: (Float, WeightUnit) -> String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
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
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(Spacing.small))

            // Total load (prominent)
            Text(
                formatWeight(metric.totalLoad, weightUnit),
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text("Total Load", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            
            Spacer(modifier = Modifier.height(Spacing.medium))

            // Individual cables
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        formatWeight(metric.loadA, weightUnit),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text("Cable A", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    LinearProgressIndicator(
                        progress = { (metric.positionA / 1000f).coerceIn(0f, 1f) },
                        modifier = Modifier.width(100.dp).padding(top = Spacing.extraSmall)
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        formatWeight(metric.loadB, weightUnit),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text("Cable B", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    LinearProgressIndicator(
                        progress = { (metric.positionB / 1000f).coerceIn(0f, 1f) },
                        modifier = Modifier.width(100.dp).padding(top = Spacing.extraSmall)
                    )
                }
            }
        }
    }
}
