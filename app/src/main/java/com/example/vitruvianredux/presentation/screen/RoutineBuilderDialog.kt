package com.example.vitruvianredux.presentation.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import com.example.vitruvianredux.domain.model.Exercise
import com.example.vitruvianredux.domain.model.Routine
import com.example.vitruvianredux.domain.model.RoutineExercise
import com.example.vitruvianredux.ui.theme.*
import java.util.*

@Composable
fun RoutineBuilderDialog(
    routine: Routine? = null,
    onSave: (Routine) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(routine?.name ?: "") }
    var description by remember { mutableStateOf(routine?.description ?: "") }
    var exercises by remember { mutableStateOf(routine?.exercises ?: emptyList<RoutineExercise>()) }
    var showError by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    var showExercisePicker by remember { mutableStateOf(false) }
    var exerciseToEdit by remember { mutableStateOf<Pair<Int, RoutineExercise>?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f),
            shape = RoundedCornerShape(16.dp),
            color = SurfaceDarkGrey
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(Spacing.medium)
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
                        color = TextPrimary
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Close",
                            tint = TextSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(Spacing.medium))

                // Scrollable content
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                ) {
                    // Name field
                    OutlinedTextField(
                        value = name,
                        onValueChange = {
                            name = it
                            showError = false
                        },
                        label = { Text("Routine Name *", color = TextSecondary) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = PrimaryPurple,
                            unfocusedBorderColor = TextTertiary,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            cursorColor = PrimaryPurple
                        ),
                        singleLine = true,
                        isError = showError && name.isBlank()
                    )

                    if (showError && name.isBlank()) {
                        Text(
                            "Routine name is required",
                            color = ErrorRed,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(start = Spacing.medium, top = Spacing.extraSmall)
                        )
                    }

                    Spacer(modifier = Modifier.height(Spacing.medium))

                    // Description field
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text("Description (optional)", color = TextSecondary) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = PrimaryPurple,
                            unfocusedBorderColor = TextTertiary,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            cursorColor = PrimaryPurple
                        ),
                        maxLines = 4
                    )

                    Spacer(modifier = Modifier.height(Spacing.large))

                    // Exercises section header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Exercises",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            "${exercises.size} exercises",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }

                    if (showError && exercises.isEmpty()) {
                        Text(
                            "Add at least one exercise",
                            color = ErrorRed,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = Spacing.extraSmall)
                        )
                    }

                    Spacer(modifier = Modifier.height(Spacing.small))

                    // Exercise list
                    if (exercises.isEmpty()) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = Spacing.small),
                            colors = CardDefaults.cardColors(containerColor = CardBackground),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(Spacing.large),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "No exercises added yet",
                                    color = TextTertiary,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    } else {
                        exercises.forEachIndexed { index, exercise ->
                            ExerciseListItem(
                                exercise = exercise,
                                index = index,
                                isFirst = index == 0,
                                isLast = index == exercises.lastIndex,
                                onEdit = { exerciseToEdit = Pair(index, exercise) },
                                onDelete = {
                                    exercises = exercises.filterIndexed { i, _ -> i != index }
                                        .mapIndexed { i, ex -> ex.copy(orderIndex = i) }
                                    showError = false
                                },
                                onMoveUp = {
                                    if (index > 0) {
                                        exercises = exercises.mapIndexed { i, ex ->
                                            when (i) {
                                                index -> exercises[index - 1].copy(orderIndex = index)
                                                index - 1 -> exercises[index].copy(orderIndex = index - 1)
                                                else -> ex
                                            }
                                        }
                                    }
                                },
                                onMoveDown = {
                                    if (index < exercises.lastIndex) {
                                        exercises = exercises.mapIndexed { i, ex ->
                                            when (i) {
                                                index -> exercises[index + 1].copy(orderIndex = index)
                                                index + 1 -> exercises[index].copy(orderIndex = index + 1)
                                                else -> ex
                                            }
                                        }
                                    }
                                }
                            )
                            Spacer(modifier = Modifier.height(Spacing.small))
                        }
                    }

                    // Add exercise button
                    OutlinedButton(
                        onClick = { showExercisePicker = true },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = PrimaryPurple
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(Spacing.small))
                        Text("Add Exercise")
                    }
                }

                Spacer(modifier = Modifier.height(Spacing.medium))

                // Bottom actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.small)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = TextSecondary
                        )
                    ) {
                        Text("Cancel")
                    }
                    Button(
                        onClick = {
                            if (name.isBlank() || exercises.isEmpty()) {
                                showError = true
                                errorMessage = when {
                                    name.isBlank() -> "Routine name is required"
                                    exercises.isEmpty() -> "Add at least one exercise"
                                    else -> ""
                                }
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
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PrimaryPurple,
                            contentColor = TextPrimary
                        )
                    ) {
                        Text("Save")
                    }
                }
            }
        }
    }

    // Exercise picker dialog
    if (showExercisePicker) {
        ExercisePickerDialog(
            onExerciseSelected = { selectedExercise ->
                // Open exercise edit dialog with default values
                val newExercise = RoutineExercise(
                    id = UUID.randomUUID().toString(),
                    exercise = selectedExercise,
                    orderIndex = exercises.size,
                    sets = 3,
                    reps = 10,
                    weightPerCableKg = 20f,
                    progressionKg = 2.5f,
                    restSeconds = 60,
                    notes = ""
                )
                exerciseToEdit = Pair(exercises.size, newExercise)
                showExercisePicker = false
            },
            onDismiss = { showExercisePicker = false }
        )
    }

    // Exercise edit dialog
    exerciseToEdit?.let { (index, exercise) ->
        ExerciseEditDialog(
            exercise = exercise,
            onSave = { updatedExercise ->
                exercises = if (index < exercises.size) {
                    exercises.mapIndexed { i, ex -> if (i == index) updatedExercise else ex }
                } else {
                    exercises + updatedExercise
                }
                exerciseToEdit = null
                showError = false
            },
            onDismiss = {
                exerciseToEdit = null
                // If we were adding a new exercise, don't keep the picker open
            }
        )
    }
}

@Composable
fun ExerciseListItem(
    exercise: RoutineExercise,
    index: Int,
    isFirst: Boolean,
    isLast: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CardBackground),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.medium)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "${index + 1}.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextTertiary,
                            modifier = Modifier.width(24.dp)
                        )
                        Text(
                            exercise.exercise.displayName,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    }
                    Spacer(modifier = Modifier.height(Spacing.extraSmall))
                    Text(
                        "${exercise.sets} sets × ${exercise.reps} reps @ ${exercise.weightPerCableKg}kg/cable",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                    if (exercise.notes.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(Spacing.extraSmall))
                        Text(
                            exercise.notes,
                            style = MaterialTheme.typography.bodySmall,
                            color = TextTertiary,
                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                        )
                    }
                }

                // Action buttons
                Row {
                    IconButton(
                        onClick = onMoveUp,
                        enabled = !isFirst
                    ) {
                        Icon(
                            Icons.Default.KeyboardArrowUp,
                            contentDescription = "Move Up",
                            tint = if (isFirst) TextDisabled else TextSecondary
                        )
                    }
                    IconButton(
                        onClick = onMoveDown,
                        enabled = !isLast
                    ) {
                        Icon(
                            Icons.Default.KeyboardArrowDown,
                            contentDescription = "Move Down",
                            tint = if (isLast) TextDisabled else TextSecondary
                        )
                    }
                    IconButton(onClick = onEdit) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = "Edit",
                            tint = PrimaryPurple
                        )
                    }
                    IconButton(onClick = onDelete) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Delete",
                            tint = ErrorRed
                        )
                    }
                }
            }
        }
    }
}
