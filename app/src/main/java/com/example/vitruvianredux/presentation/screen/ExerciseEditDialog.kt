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
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.vitruvianredux.data.local.ExerciseVideoEntity
import com.example.vitruvianredux.data.repository.ExerciseRepository
import com.example.vitruvianredux.domain.model.EccentricLoad
import com.example.vitruvianredux.domain.model.EchoLevel
import com.example.vitruvianredux.domain.model.RoutineExercise
import com.example.vitruvianredux.domain.model.WeightUnit
import com.example.vitruvianredux.domain.model.WorkoutMode
import com.example.vitruvianredux.presentation.components.VideoPlayer
import com.example.vitruvianredux.presentation.viewmodel.ExerciseConfigViewModel
import com.example.vitruvianredux.presentation.viewmodel.ExerciseType
import com.example.vitruvianredux.presentation.viewmodel.SetConfiguration
import com.example.vitruvianredux.presentation.viewmodel.SetMode
import com.example.vitruvianredux.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseEditBottomSheet(
    exercise: RoutineExercise,
    weightUnit: WeightUnit,
    kgToDisplay: (Float, WeightUnit) -> Float,
    displayToKg: (Float, WeightUnit) -> Float,
    exerciseRepository: ExerciseRepository,
    personalRecordRepository: com.example.vitruvianredux.data.repository.PersonalRecordRepository,
    formatWeight: (Float, WeightUnit) -> String,
    onSave: (RoutineExercise) -> Unit,
    onDismiss: () -> Unit,
    buttonText: String = "Save",
    viewModel: ExerciseConfigViewModel = hiltViewModel()
) {
    // UI-specific state that doesn't need to be in the ViewModel
    var videos by remember { mutableStateOf<List<ExerciseVideoEntity>>(emptyList()) }
    LaunchedEffect(exercise.exercise.id) {
        exercise.exercise.id?.let { exerciseId ->
            try {
                videos = exerciseRepository.getVideos(exerciseId)
            } catch (_: Exception) {
                // Handle error
            }
        }
    }
    val preferredVideo = videos.firstOrNull { it.angle == "FRONT" } ?: videos.firstOrNull()

    // Fetch initial PR for exercise (based on workout type from exercise config)
    var initialPR by remember { mutableStateOf<com.example.vitruvianredux.domain.model.PersonalRecord?>(null) }
    LaunchedEffect(exercise.exercise.id, exercise.workoutType) {
        exercise.exercise.id?.let { exerciseId ->
            val workoutMode = exercise.workoutType.toWorkoutMode()
            if (workoutMode !is WorkoutMode.Echo) {
                try {
                    val modeString = when (workoutMode) {
                        is WorkoutMode.OldSchool -> "Old School"
                        is WorkoutMode.Pump -> "Pump"
                        is WorkoutMode.TUT -> "TUT"
                        is WorkoutMode.TUTBeast -> "TUT Beast"
                        is WorkoutMode.EccentricOnly -> "Eccentric Only"
                        else -> null
                    }
                    modeString?.let { mode ->
                        initialPR = personalRecordRepository.getLatestPR(exerciseId, mode)
                    }
                } catch (_: Exception) {
                    initialPR = null
                }
            }
        }
    }

    // Initialize the ViewModel with the exercise data and PR weight.
    // This will only run once for a given exercise ID, preventing state wipes on recomposition.
    LaunchedEffect(exercise, weightUnit, initialPR) {
        viewModel.initialize(exercise, weightUnit, kgToDisplay, displayToKg, initialPR?.weightPerCableKg)
    }

    // Collect state from the ViewModel
    val exerciseType by viewModel.exerciseType.collectAsState()
    val setMode by viewModel.setMode.collectAsState()
    val sets by viewModel.sets.collectAsState()
    val selectedMode by viewModel.selectedMode.collectAsState()
    val weightChange by viewModel.weightChange.collectAsState()
    val rest by viewModel.rest.collectAsState()
    val notes by viewModel.notes.collectAsState()
    val eccentricLoad by viewModel.eccentricLoad.collectAsState()
    val echoLevel by viewModel.echoLevel.collectAsState()

    // Fetch current PR for selected mode (for display in PR card)
    var currentPR by remember { mutableStateOf<com.example.vitruvianredux.domain.model.PersonalRecord?>(null) }
    LaunchedEffect(exercise.exercise.id, selectedMode) {
        exercise.exercise.id?.let { exerciseId ->
            // Don't fetch PR for Echo mode
            if (selectedMode !is WorkoutMode.Echo) {
                try {
                    val modeString = when (selectedMode) {
                        is WorkoutMode.OldSchool -> "Old School"
                        is WorkoutMode.Pump -> "Pump"
                        is WorkoutMode.TUT -> "TUT"
                        is WorkoutMode.TUTBeast -> "TUT Beast"
                        is WorkoutMode.EccentricOnly -> "Eccentric Only"
                        else -> null
                    }
                    modeString?.let { mode ->
                        currentPR = personalRecordRepository.getLatestPR(exerciseId, mode)
                    }
                } catch (_: Exception) {
                    currentPR = null
                }
            } else {
                currentPR = null
            }
        }
    }

    val weightSuffix = if (weightUnit == WeightUnit.LB) "lbs" else "kg"
    val maxWeight = if (weightUnit == WeightUnit.LB) 220 else 100
    val maxWeightChange = if (weightUnit == WeightUnit.LB) 10 else 10

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = {
            viewModel.onDismiss()
            onDismiss()
        },
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
                IconButton(onClick = {
                    viewModel.onDismiss()
                    onDismiss()
                }) {
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

                // Personal Record Display
                currentPR?.let { pr ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(Spacing.medium),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(Spacing.small)
                            ) {
                                Icon(
                                    Icons.Default.Star,
                                    contentDescription = "Personal Record",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                                Column {
                                    Text(
                                        "Personal Record",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    Text(
                                        "${formatWeight(pr.weightPerCableKg, weightUnit)}/cable × ${pr.reps} reps",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            Text(
                                java.text.SimpleDateFormat("MMM d", java.util.Locale.getDefault()).format(pr.timestamp),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }

                if (exerciseType == ExerciseType.STANDARD) {
                    ModeSelector(
                        selectedMode = selectedMode,
                        onModeChange = viewModel::onSelectedModeChange
                    )
                }

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
                                    viewModel.onSelectedModeChange(if (isBeast) WorkoutMode.TUTBeast else WorkoutMode.TUT)
                                }
                            )
                        }
                    }
                }

                val isEchoMode = selectedMode is WorkoutMode.Echo
                if (isEchoMode) {
                    EccentricLoadSelector(
                        eccentricLoad = eccentricLoad,
                        onLoadChange = viewModel::onEccentricLoadChange
                    )
                    EchoLevelSelector(
                        level = echoLevel,
                        onLevelChange = viewModel::onEchoLevelChange
                    )
                }

                if (exerciseType == ExerciseType.STANDARD && !isEchoMode) {
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
                                onValueChange = viewModel::onWeightChange,
                                range = -maxWeightChange..maxWeightChange,
                                label = "Weight Change Per Rep",
                                suffix = weightSuffix,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Text(
                                "Negative = Regression, Positive = Progression",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.padding(top = Spacing.extraSmall)
                            )
                        }
                    }
                }

                if (!isEchoMode) {
                    SetModeToggle(
                        setMode = setMode,
                        onModeChange = viewModel::onSetModeChange
                    )
                }

                SetsConfiguration(
                    sets = sets,
                    setMode = setMode,
                    exerciseType = exerciseType,
                    weightSuffix = weightSuffix,
                    maxWeight = maxWeight,
                    isEchoMode = isEchoMode,
                    onRepsChange = viewModel::updateReps,
                    onWeightChange = viewModel::updateWeight,
                    onDurationChange = viewModel::updateDuration,
                    onAddSet = viewModel::addSet,
                    onDeleteSet = viewModel::deleteSet
                )

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
                            onValueChange = viewModel::onRestChange,
                            range = 0..300,
                            label = "Rest Time",
                            suffix = "sec",
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                OutlinedTextField(
                    value = notes,
                    onValueChange = viewModel::onNotesChange,
                    label = { Text("Notes (optional)") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp),
                    maxLines = 4,
                    placeholder = { Text("Form cues, tempo, etc.") }
                )
            }

            Spacer(modifier = Modifier.height(Spacing.small))

            // Bottom actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.small)
            ) {
                TextButton(
                    onClick = {
                        viewModel.onDismiss()
                        onDismiss()
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Cancel")
                }
                Button(
                    onClick = {
                        viewModel.onSave(onSave)
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp),
                    enabled = sets.isNotEmpty(),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(
                        buttonText,
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
    isEchoMode: Boolean = false,
    onRepsChange: (Int, Int) -> Unit,
    onWeightChange: (Int, Float) -> Unit,
    onDurationChange: (Int, Int) -> Unit,
    onAddSet: () -> Unit,
    onDeleteSet: (Int) -> Unit
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
            key(setConfig.id) { // Use stable ID as key
                SetRow(
                    setConfig = setConfig,
                    setMode = setMode,
                    exerciseType = exerciseType,
                    weightSuffix = weightSuffix,
                    maxWeight = maxWeight,
                    isEchoMode = isEchoMode,
                    canDelete = sets.size > 1,
                    onRepsChange = { newReps -> onRepsChange(index, newReps) },
                    onWeightChange = { newWeight -> onWeightChange(index, newWeight) },
                    onDurationChange = { newDuration -> onDurationChange(index, newDuration) },
                    onDelete = { onDeleteSet(index) }
                )
            }
        }

        // Add Set button
        OutlinedButton(
            onClick = onAddSet,
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
    isEchoMode: Boolean = false,
    canDelete: Boolean,
    onRepsChange: (Int) -> Unit,
    onWeightChange: (Float) -> Unit,
    onDurationChange: (Int) -> Unit,
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
                            onValueChange = onRepsChange,
                            range = 1..50,
                            label = if (setConfig.setNumber == 1) "Reps" else "",
                            suffix = "reps",
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        com.example.vitruvianredux.presentation.components.CompactNumberPicker(
                            value = setConfig.duration,
                            onValueChange = onDurationChange,
                            range = 10..300,
                            label = if (setConfig.setNumber == 1) "Duration" else "",
                            suffix = "sec",
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                // Weight picker (for standard exercises) OR Adaptive label (for Echo mode) OR Bodyweight label
                Box(modifier = Modifier.weight(1f)) {
                    when {
                        isEchoMode -> {
                            // Echo mode: Show "Adaptive" label instead of weight picker
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                if (setConfig.setNumber == 1) {
                                    Text(
                                        "Force per Cable",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(bottom = 8.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(if (setConfig.setNumber == 1) 60.dp else 80.dp))
                                Text(
                                    "Adaptive",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        exerciseType == ExerciseType.STANDARD -> {
                            // Standard exercises: Show weight picker
                            com.example.vitruvianredux.presentation.components.CompactNumberPicker(
                                value = setConfig.weightPerCable.toInt(),
                                onValueChange = { onWeightChange(it.toFloat()) },
                                range = 1..maxWeight,
                                label = if (setConfig.setNumber == 1) "Weight per Cable" else "",
                                suffix = weightSuffix,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        else -> {
                            // Bodyweight exercises: Show "Bodyweight" label
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
                        .menuAnchor(type = MenuAnchorType.PrimaryNotEditable),
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
                // Display current percentage value
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Eccentric Load: ${eccentricLoad.percentage}%",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))

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
                        onLoadChange(eccentricLoadValues[index])
                    },
                    valueRange = 0f..(eccentricLoadValues.size - 1).toFloat(),
                    steps = eccentricLoadValues.size - 2,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

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
                    EchoLevel.entries.forEach { echoLevel ->
                        FilterChip(
                            selected = level == echoLevel,
                            onClick = { onLevelChange(echoLevel) },
                            label = { 
                                Text(
                                    echoLevel.displayName, 
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1
                                ) 
                            },
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
