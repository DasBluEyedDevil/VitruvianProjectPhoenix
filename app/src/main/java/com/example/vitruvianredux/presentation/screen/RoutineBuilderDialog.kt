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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.vitruvianredux.data.repository.ExerciseRepository
import com.example.vitruvianredux.domain.model.*
import com.example.vitruvianredux.presentation.components.ExercisePickerDialog
import com.example.vitruvianredux.ui.theme.*
import java.util.*

@Composable
fun RoutineBuilderDialog(
    routine: Routine? = null,
    onSave: (Routine) -> Unit,
    onDismiss: () -> Unit,
    exerciseRepository: ExerciseRepository,
    personalRecordRepository: com.example.vitruvianredux.data.repository.PersonalRecordRepository,
    formatWeight: (Float, WeightUnit) -> String,
    weightUnit: WeightUnit,
    kgToDisplay: (Float, WeightUnit) -> Float,
    displayToKg: (Float, WeightUnit) -> Float,
    themeMode: ThemeMode
) {
    var name by remember { mutableStateOf(routine?.name ?: "") }
    var description by remember { mutableStateOf(routine?.description ?: "") }
    var exercises by remember { mutableStateOf(routine?.exercises ?: emptyList<RoutineExercise>()) }
    var showError by remember { mutableStateOf(false) }
    var showExercisePicker by remember { mutableStateOf(false) }
    var exerciseToEdit by remember { mutableStateOf<Pair<Int, RoutineExercise>?>(null) }

    val backgroundGradient = if (themeMode == ThemeMode.DARK) {
        Brush.verticalGradient(colors = listOf(Color(0xFF0F172A), Color(0xFF1E1B4B), Color(0xFF172554)))
    } else {
        Brush.verticalGradient(colors = listOf(Color(0xFFE0E7FF), Color(0xFFFCE7F3), Color(0xFFDDD6FE)))
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth().fillMaxHeight(0.9f),
            shape = RoundedCornerShape(16.dp),
            color = Color.Transparent
        ) {
            Box(modifier = Modifier.fillMaxSize().background(backgroundGradient)) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(Spacing.medium)
                ) {
                    // Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (routine == null) "Create Routine" else "Edit Routine",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, "Close", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    Spacer(modifier = Modifier.height(Spacing.medium))

                    // Scrollable content
                    Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it; showError = false },
                            label = { Text("Routine Name *") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            isError = showError && name.isBlank()
                        )

                        if (showError && name.isBlank()) {
                            Text("Routine name is required", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = Spacing.medium, top = Spacing.extraSmall))
                        }

                        Spacer(modifier = Modifier.height(Spacing.medium))

                        OutlinedTextField(
                            value = description,
                            onValueChange = { description = it },
                            label = { Text("Description (optional)") },
                            modifier = Modifier.fillMaxWidth().height(100.dp),
                            maxLines = 4
                        )

                        Spacer(modifier = Modifier.height(Spacing.large))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Exercises", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                            Text("${exercises.size} exercises", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }

                        if (showError && exercises.isEmpty()) {
                            Text("Add at least one exercise", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = Spacing.extraSmall))
                        }

                        Spacer(modifier = Modifier.height(Spacing.small))

                        if (exercises.isEmpty()) {
                            Card(
                                modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.small),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                shape = RoundedCornerShape(16.dp),
                                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                                border = BorderStroke(1.dp, Color(0xFFF5F3FF))
                            ) {
                                Box(modifier = Modifier.fillMaxWidth().padding(Spacing.large), contentAlignment = Alignment.Center) {
                                    Text("No exercises added yet", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
                                exercises.forEachIndexed { index, exercise ->
                                    key(exercise.id) {
                                        ExerciseListItem(
                                            exercise = exercise,
                                            isFirst = index == 0,
                                            isLast = index == exercises.lastIndex,
                                            weightUnit = weightUnit,
                                            kgToDisplay = kgToDisplay,
                                            onEdit = { exerciseToEdit = Pair(index, exercise) },
                                            onDelete = {
                                                exercises = exercises.filterIndexed { i, _ -> i != index }.mapIndexed { i, ex -> ex.copy(orderIndex = i) }
                                                showError = false
                                            },
                                            onMoveUp = {
                                                if (index > 0) {
                                                    exercises = exercises.toMutableList().apply {
                                                        removeAt(index).also { add(index - 1, it) }
                                                    }.mapIndexed { i, ex -> ex.copy(orderIndex = i) }
                                                }
                                            },
                                            onMoveDown = {
                                                if (index < exercises.lastIndex) {
                                                    exercises = exercises.toMutableList().apply {
                                                        removeAt(index).also { add(index + 1, it) }
                                                    }.mapIndexed { i, ex -> ex.copy(orderIndex = i) }
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        OutlinedButton(
                            onClick = { showExercisePicker = true },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Add, "Add exercise")
                            Spacer(modifier = Modifier.width(Spacing.small))
                            Text("Add Exercise")
                        }
                    }

                    Spacer(modifier = Modifier.height(Spacing.medium))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.small)) {
                        OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f), colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant)) {
                            Text("Cancel")
                        }
                        Button(
                            onClick = {
                                if (name.isBlank() || exercises.isEmpty()) {
                                    showError = true
                                } else {
                                    val newRoutine = Routine(
                                        id = routine?.id ?: UUID.randomUUID().toString(),
                                        name = name.trim(),
                                        description = description.trim(),
                                        exercises = exercises,
                                        createdAt = routine?.createdAt ?: System.currentTimeMillis(),
                                        lastUsed = routine?.lastUsed,
                                        useCount = routine?.useCount ?: 0
                                    )
                                    onSave(newRoutine)
                                }
                            },
                            modifier = Modifier.weight(1f).height(56.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Text("Save", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    if (showExercisePicker) {
        ExercisePickerDialog(
            showDialog = true,
            onDismiss = { showExercisePicker = false },
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
                    orderIndex = exercises.size,
                    setReps = listOf(10, 10, 10),
                    weightPerCableKg = 20f,
                    progressionKg = 0f,
                    restSeconds = 60,
                    notes = "",
                    workoutType = WorkoutType.Program(ProgramMode.OldSchool),
                    eccentricLoad = EccentricLoad.LOAD_100,
                    echoLevel = EchoLevel.HARDER
                )
                exerciseToEdit = Pair(exercises.size, newRoutineExercise)
                showExercisePicker = false
            },
            exerciseRepository = exerciseRepository
        )
    }

    exerciseToEdit?.let { (index, exercise) ->
        ExerciseEditBottomSheet(
            exercise = exercise,
            weightUnit = weightUnit,
            kgToDisplay = kgToDisplay,
            displayToKg = displayToKg,
            exerciseRepository = exerciseRepository,
            personalRecordRepository = personalRecordRepository,
            formatWeight = formatWeight,
            onSave = { updatedExercise ->
                exercises = exercises.toMutableList().apply {
                    if (index < size) {
                        set(index, updatedExercise)
                    } else {
                        add(updatedExercise)
                    }
                }.mapIndexed { i, ex -> ex.copy(orderIndex = i) }
                exerciseToEdit = null
                showError = false
            },
            onDismiss = { exerciseToEdit = null }
        )
    }
}

@Composable
fun ExerciseListItem(
    exercise: RoutineExercise,
    isFirst: Boolean,
    isLast: Boolean,
    weightUnit: WeightUnit,
    kgToDisplay: (Float, WeightUnit) -> Float,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit
) {

    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (isPressed) 0.99f else 1f, spring(Spring.DampingRatioMediumBouncy, 400f), label = "scale")

    Card(
        modifier = Modifier.fillMaxWidth().scale(scale),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        border = BorderStroke(1.dp, Color(0xFFF5F3FF))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(Spacing.medium),
            horizontalArrangement = Arrangement.spacedBy(Spacing.small),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                IconButton(onClick = onMoveUp, enabled = !isFirst, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.KeyboardArrowUp, "Move Up", tint = if (isFirst) MaterialTheme.colorScheme.outlineVariant else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                }
                IconButton(onClick = onMoveDown, enabled = !isLast, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.KeyboardArrowDown, "Move Down", tint = if (isLast) MaterialTheme.colorScheme.outlineVariant else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                }
            }

            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
                val weightSuffix = if (weightUnit == WeightUnit.LB) "lbs" else "kg"
                val displayWeight = kgToDisplay(exercise.weightPerCableKg, weightUnit)

                Text(exercise.exercise.displayName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)

                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.small), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Surface(shape = RoundedCornerShape(6.dp), color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)) {
                        Text(formatReps(exercise.setReps), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), fontWeight = FontWeight.Medium)
                    }
                    Surface(shape = RoundedCornerShape(6.dp), color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f)) {
                        Text("${displayWeight.toInt()}$weightSuffix", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), fontWeight = FontWeight.Medium)
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.small), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    if (exercise.progressionKg != 0f) {
                        val displayProgression = kgToDisplay(exercise.progressionKg, weightUnit)
                        val progressionText = if (displayProgression > 0) "+${displayProgression.toInt()}$weightSuffix per rep" else "${displayProgression.toInt()}$weightSuffix per rep"
                        Surface(shape = RoundedCornerShape(6.dp), color = if (exercise.progressionKg > 0) MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.error.copy(alpha = 0.15f)) {
                            Text(progressionText, style = MaterialTheme.typography.bodySmall, color = if (exercise.progressionKg > 0) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), fontWeight = FontWeight.Medium)
                        }
                    }
                    if (exercise.restSeconds > 0) {
                        Surface(shape = RoundedCornerShape(6.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                            Text("${exercise.restSeconds}s rest", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), fontWeight = FontWeight.Medium)
                        }
                    }
                }

                if (exercise.notes.isNotEmpty()) {
                    Text(exercise.notes, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic, maxLines = 2)
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                IconButton(onClick = { isPressed = true; onEdit() }, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Edit, "Edit", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Delete, "Delete", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                }
            }
        }
    }

    LaunchedEffect(isPressed) {
        if (isPressed) { kotlinx.coroutines.delay(100); isPressed = false }
    }
}

private fun formatReps(setReps: List<Int>): String {
    if (setReps.isEmpty()) return "0 sets"
    val allSame = setReps.all { it == setReps.first() }
    return if (allSame) "${setReps.size} x ${setReps.first()} reps" else "${setReps.size} sets: ${setReps.joinToString("/")}"
}
