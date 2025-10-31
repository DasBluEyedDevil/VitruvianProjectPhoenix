package com.example.vitruvianredux.presentation.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.vitruvianredux.data.local.ExerciseVideoEntity
import com.example.vitruvianredux.data.repository.ExerciseRepository
import com.example.vitruvianredux.domain.model.EccentricLoad
import com.example.vitruvianredux.domain.model.EchoLevel
import com.example.vitruvianredux.domain.model.RoutineExercise
import com.example.vitruvianredux.domain.model.WeightUnit
import com.example.vitruvianredux.domain.model.WorkoutMode
import com.example.vitruvianredux.presentation.components.CustomNumberPicker
import com.example.vitruvianredux.presentation.components.VideoPlayer
import com.example.vitruvianredux.ui.theme.*

/**
 * Exercise type classification
 */
enum class ExerciseType {
    BODYWEIGHT,  // No equipment - no mode selection, show reps/duration toggle, rest time only
    STANDARD     // Has equipment - show mode selection which determines other controls
}

/**
 * Set configuration mode
 */
enum class SetMode {
    REPS,       // Count reps per set
    DURATION    // Time-based sets
}

/**
 * Per-set configuration data
 */
data class SetConfiguration(
    val setNumber: Int,
    val reps: Int = 10,
    val weightPerCable: Float = 15.0f,
    val duration: Int = 30 // Duration in seconds
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseEditBottomSheet(
    exercise: RoutineExercise,
    weightUnit: WeightUnit,
    kgToDisplay: (Float, WeightUnit) -> Float,
    displayToKg: (Float, WeightUnit) -> Float,
    exerciseRepository: ExerciseRepository,
    onSave: (RoutineExercise) -> Unit,
    onDismiss: () -> Unit
) {
    // Load demonstration videos
    var videos by remember { mutableStateOf<List<ExerciseVideoEntity>>(emptyList()) }
    var isLoadingVideo by remember { mutableStateOf(true) }

    LaunchedEffect(exercise.exercise.id) {
        exercise.exercise.id?.let { exerciseId ->
            try {
                videos = exerciseRepository.getVideos(exerciseId)
                isLoadingVideo = false
            } catch (e: Exception) {
                isLoadingVideo = false
            }
        } ?: run {
            isLoadingVideo = false
        }
    }

    // Get preferred video (FRONT angle preferred, or first available)
    val preferredVideo = videos.firstOrNull { it.angle == "FRONT" }
        ?: videos.firstOrNull()

    // Determine exercise type (bodyweight vs equipment-based)
    val exerciseType = remember(exercise) {
        if (exercise.exercise.equipment.isEmpty() ||
            exercise.exercise.equipment.equals("bodyweight", ignoreCase = true)) {
            ExerciseType.BODYWEIGHT
        } else {
            ExerciseType.STANDARD
        }
    }

    // Calculate dynamic ranges based on unit system
    val weightSuffix = if (weightUnit == WeightUnit.LB) "lbs" else "kg"
    val maxWeight = if (weightUnit == WeightUnit.LB) 220 else 100
    val maxWeightChange = if (weightUnit == WeightUnit.LB) 10 else 10

    // State variables
    var setMode by remember { mutableStateOf(if (exercise.duration != null) SetMode.DURATION else SetMode.REPS) }
    var sets by remember {
        mutableStateOf(
            exercise.setReps.mapIndexed { index, reps ->
                val perSetWeightKg = exercise.setWeightsPerCableKg.getOrNull(index) ?: exercise.weightPerCableKg
                SetConfiguration(
                    setNumber = index + 1,
                    reps = reps,
                    weightPerCable = kgToDisplay(perSetWeightKg, weightUnit),
                    duration = exercise.duration ?: 30
                )
            }.ifEmpty {
                listOf(
                    SetConfiguration(1, 10, kgToDisplay(15f, weightUnit)),
                    SetConfiguration(2, 10, kgToDisplay(15f, weightUnit)),
                    SetConfiguration(3, 10, kgToDisplay(15f, weightUnit))
                )
            }
        )
    }
    var selectedMode by remember { mutableStateOf<WorkoutMode>(exercise.mode) }
    var weightChange by remember {
        // Always default to 0 for weight change per rep
        mutableStateOf(0)
    }
    var rest by remember { mutableStateOf(exercise.restSeconds.coerceIn(0, 300)) }
    var notes by remember { mutableStateOf(exercise.notes) }
    var eccentricLoad by remember { mutableStateOf(exercise.eccentricLoad) }
    var echoLevel by remember { mutableStateOf(exercise.echoLevel) }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.small)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "Configure Exercise",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        exercise.exercise.displayName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Close",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(Spacing.small))

            // Scrollable content
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Spacing.small)
            ) {
                // DEMONSTRATION VIDEO (if available)
                preferredVideo?.let { video ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        VideoPlayer(
                            videoUrl = video.videoUrl,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
                // 1. MODE SELECTION (first, only for non-bodyweight exercises)
                if (exerciseType == ExerciseType.STANDARD) {
                    ModeSelector(
                        selectedMode = selectedMode,
                        onModeChange = { selectedMode = it }
                    )
                }

                // 1a. TUT BEAST MODE TOGGLE (if TUT mode selected)
                val isTutMode = selectedMode is WorkoutMode.TUT || selectedMode is WorkoutMode.TUTBeast
                if (isTutMode) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant),
                        shadowElevation = 2.dp
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(Spacing.small),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Beast Mode",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Switch(
                                checked = selectedMode is WorkoutMode.TUTBeast,
                                onCheckedChange = { isBeast ->
                                    selectedMode = if (isBeast) {
                                        WorkoutMode.TUTBeast
                                    } else {
                                        WorkoutMode.TUT
                                    }
                                }
                            )
                        }
                    }
                }

                // 2a. ECHO MODE CONTROLS (if Echo mode selected)
                val isEchoMode = selectedMode is WorkoutMode.Echo
                if (isEchoMode) {
                    // Eccentric Load Selector
                    EccentricLoadSelector(
                        eccentricLoad = eccentricLoad,
                        onLoadChange = { eccentricLoad = it }
                    )

                    // Level Selector
                    EchoLevelSelector(
                        level = echoLevel,
                        onLevelChange = { echoLevel = it }
                    )
                }

                // 2b. WEIGHT PROGRESSION/REGRESSION (for non-bodyweight, non-Echo, non-TUT modes)
                if (exerciseType == ExerciseType.STANDARD && !isEchoMode && !isTutMode) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant),
                        shadowElevation = 2.dp
                    ) {
                        Column(modifier = Modifier.padding(Spacing.small)) {
                            com.example.vitruvianredux.presentation.components.CompactNumberPicker(
                                value = weightChange,
                                onValueChange = { weightChange = it },
                                range = -maxWeightChange..maxWeightChange,
                                label = "Weight Change Per Rep",
                                suffix = weightSuffix,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Text(
                                "Negative = Regression (decrease weight), Positive = Progression (increase weight)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.padding(top = Spacing.extraSmall)
                            )
                        }
                    }
                }

                // 3. REPS vs DURATION TOGGLE (for non-Echo modes)
                if (!isEchoMode) {
                    SetModeToggle(
                        setMode = setMode,
                        onModeChange = { setMode = it }
                    )
                }

                // 4. SETS CONFIGURATION (always shown)
                SetsConfiguration(
                    sets = sets,
                    setMode = setMode,
                    exerciseType = exerciseType,
                    weightSuffix = weightSuffix,
                    maxWeight = maxWeight,
                    onSetsChange = { sets = it }
                )

                // 5. REST TIME (always shown)
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant),
                    shadowElevation = 2.dp
                ) {
                    Column(modifier = Modifier.padding(Spacing.small)) {
                        com.example.vitruvianredux.presentation.components.CompactNumberPicker(
                            value = rest,
                            onValueChange = { rest = it },
                            range = 0..300,
                            label = "Rest Time",
                            suffix = "sec",
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                // 6. NOTES FIELD (always shown)
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes (optional)") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp),
                    maxLines = 4,
                    placeholder = { Text("Form cues, tempo, range of motion, etc.") }
                )
            }

            Spacer(modifier = Modifier.height(Spacing.small))

            // Bottom actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.small)
            ) {
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Cancel")
                }
                Button(
                    onClick = {
                        val updatedExercise = exercise.copy(
                            setReps = sets.map { it.reps },
                            weightPerCableKg = displayToKg(sets.first().weightPerCable, weightUnit),
                            setWeightsPerCableKg = sets.map { displayToKg(it.weightPerCable, weightUnit) },
                            mode = selectedMode,
                            eccentricLoad = eccentricLoad,
                            echoLevel = echoLevel,
                            progressionKg = displayToKg(weightChange.toFloat(), weightUnit),
                            restSeconds = rest,
                            notes = notes.trim(),
                            duration = if (setMode == SetMode.DURATION) sets.firstOrNull()?.duration else null
                        )
                        onSave(updatedExercise)
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp),
                    enabled = sets.isNotEmpty(),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(
                        "Save",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun SetModeToggle(
    setMode: SetMode,
    onModeChange: (SetMode) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.small)
    ) {
        Text(
            "Set Mode",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = Spacing.extraSmall)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = setMode == SetMode.REPS,
                onClick = { onModeChange(SetMode.REPS) },
                label = { Text("Reps") },
                modifier = Modifier.weight(1f)
            )
            FilterChip(
                selected = setMode == SetMode.DURATION,
                onClick = { onModeChange(SetMode.DURATION) },
                label = { Text("Duration") },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
fun SetsConfiguration(
    sets: List<SetConfiguration>,
    setMode: SetMode,
    exerciseType: ExerciseType,
    weightSuffix: String,
    maxWeight: Int,
    onSetsChange: (List<SetConfiguration>) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.small)
    ) {
        Text(
            "Sets & ${if (setMode == SetMode.REPS) "Reps" else "Duration"}",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = Spacing.extraSmall)
        )

        sets.forEachIndexed { index, setConfig ->
            SetRow(
                setConfig = setConfig,
                setMode = setMode,
                exerciseType = exerciseType,
                weightSuffix = weightSuffix,
                maxWeight = maxWeight,
                canDelete = sets.size > 1,
                onSetChange = { updated ->
                    val updatedSets = sets.toMutableList()
                    updatedSets[index] = updated
                    onSetsChange(updatedSets)
                },
                onDelete = {
                    onSetsChange(sets.filterIndexed { i, _ -> i != index })
                }
            )
        }

        // Add Set button
        OutlinedButton(
            onClick = {
                val lastSet = sets.lastOrNull()
                val newSet = SetConfiguration(
                    setNumber = sets.size + 1,
                    reps = lastSet?.reps ?: 10,
                    weightPerCable = lastSet?.weightPerCable ?: 15f,
                    duration = lastSet?.duration ?: 30
                )
                onSetsChange(sets + newSet)
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = "Add set", modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(Spacing.small))
            Text("Add Set")
        }
    }
}

@Composable
fun SetRow(
    setConfig: SetConfiguration,
    setMode: SetMode,
    exerciseType: ExerciseType,
    weightSuffix: String,
    maxWeight: Int,
    canDelete: Boolean,
    onSetChange: (SetConfiguration) -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.medium)
        ) {
            // Set label and Delete button row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Set ${setConfig.setNumber}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
                IconButton(
                    onClick = onDelete,
                    enabled = canDelete
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete set",
                        tint = if (canDelete) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outlineVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(Spacing.small))

            // Reps/Duration and Weight (or Bodyweight label) side-by-side
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.small)
            ) {
                // Reps or Duration picker
                Box(modifier = Modifier.weight(1f)) {
                    if (setMode == SetMode.REPS) {
                        com.example.vitruvianredux.presentation.components.CompactNumberPicker(
                            value = setConfig.reps,
                            onValueChange = { newReps ->
                                onSetChange(setConfig.copy(reps = newReps))
                            },
                            range = 1..50,
                            label = if (setConfig.setNumber == 1) "Reps" else "",
                            suffix = "reps",
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        com.example.vitruvianredux.presentation.components.CompactNumberPicker(
                            value = setConfig.duration,
                            onValueChange = { newDuration ->
                                onSetChange(setConfig.copy(duration = newDuration))
                            },
                            range = 10..300,
                            label = if (setConfig.setNumber == 1) "Duration" else "",
                            suffix = "sec",
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                // Weight picker (for standard exercises) OR Bodyweight label (for bodyweight exercises)
                Box(modifier = Modifier.weight(1f)) {
                    if (exerciseType == ExerciseType.STANDARD) {
                        com.example.vitruvianredux.presentation.components.CompactNumberPicker(
                            value = setConfig.weightPerCable.toInt(),
                            onValueChange = { newWeight ->
                                onSetChange(setConfig.copy(weightPerCable = newWeight.toFloat()))
                            },
                            range = 1..maxWeight,
                            label = if (setConfig.setNumber == 1) "Weight per Cable" else "",
                            suffix = weightSuffix,
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        // Bodyweight label aligned horizontally
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            if (setConfig.setNumber == 1) {
                                Text(
                                    "Weight",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(if (setConfig.setNumber == 1) 60.dp else 80.dp))
                            Text(
                                "Bodyweight",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModeSelector(
    selectedMode: WorkoutMode,
    onModeChange: (WorkoutMode) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    // All available workout modes (TUTBeast excluded - handled as toggle in TUT mode)
    val allModes = listOf(
        WorkoutMode.OldSchool,
        WorkoutMode.Pump,
        WorkoutMode.TUT,
        WorkoutMode.EccentricOnly,
        WorkoutMode.Echo(EchoLevel.HARDER) // Default Echo level
    )

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant),
        shadowElevation = 4.dp
    ) {
        Column(modifier = Modifier.padding(Spacing.medium)) {
            Text(
                "Workout Mode",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = Spacing.small)
            )

            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = !expanded }
            ) {
                OutlinedTextField(
                    value = selectedMode.displayName,
                    onValueChange = {},
                    readOnly = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(),
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                    },
                    colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors()
                )

                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    allModes.forEach { mode ->
                        DropdownMenuItem(
                            text = { Text(mode.displayName) },
                            onClick = {
                                onModeChange(mode)
                                expanded = false
                            },
                            contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun EccentricLoadSelector(
    eccentricLoad: EccentricLoad,
    onLoadChange: (EccentricLoad) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.small)
    ) {
        Text(
            "Eccentric Load",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = Spacing.extraSmall)
        )

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant),
            shadowElevation = 4.dp
        ) {
            Column(modifier = Modifier.padding(Spacing.medium)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        EccentricLoad.LOAD_50,
                        EccentricLoad.LOAD_75,
                        EccentricLoad.LOAD_100
                    ).forEach { load ->
                        FilterChip(
                            selected = eccentricLoad == load,
                            onClick = { onLoadChange(load) },
                            label = { Text(load.displayName) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        EccentricLoad.LOAD_125,
                        EccentricLoad.LOAD_150
                    ).forEach { load ->
                        FilterChip(
                            selected = eccentricLoad == load,
                            onClick = { onLoadChange(load) },
                            label = { Text(load.displayName) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(modifier = Modifier.weight(1f))
                }

                Text(
                    "Percentage of concentric load applied during eccentric phase",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(top = Spacing.small)
                )
            }
        }
    }
}

@Composable
fun EchoLevelSelector(
    level: EchoLevel,
    onLevelChange: (EchoLevel) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.small)
    ) {
        Text(
            "Difficulty Level",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = Spacing.extraSmall)
        )

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant),
            shadowElevation = 4.dp
        ) {
            Column(modifier = Modifier.padding(Spacing.medium)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    EchoLevel.values().forEach { echoLevel ->
                        FilterChip(
                            selected = level == echoLevel,
                            onClick = { onLevelChange(echoLevel) },
                            label = { Text(echoLevel.displayName, style = MaterialTheme.typography.bodySmall) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Text(
                    "Select difficulty level for Echo mode training",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(top = Spacing.small)
                )
            }
        }
    }
}
