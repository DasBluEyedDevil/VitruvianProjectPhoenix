package com.example.vitruvianredux.presentation.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.vitruvianredux.domain.model.RoutineExercise
import com.example.vitruvianredux.ui.theme.*

@Composable
fun ExerciseEditDialog(
    exercise: RoutineExercise,
    onSave: (RoutineExercise) -> Unit,
    onDismiss: () -> Unit
) {
    var setReps by remember { mutableStateOf(exercise.setReps.ifEmpty { listOf(10) }) }
    var weight by remember { mutableStateOf(exercise.weightPerCableKg.toString()) }
    var weightError by remember { mutableStateOf<String?>(null) }
    var progression by remember { mutableStateOf(exercise.progressionKg.toString()) }
    var progressionError by remember { mutableStateOf<String?>(null) }
    var rest by remember { mutableStateOf(exercise.restSeconds.toString()) }
    var restError by remember { mutableStateOf<String?>(null) }
    var notes by remember { mutableStateOf(exercise.notes) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 600.dp),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface
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

                Spacer(modifier = Modifier.height(Spacing.medium))

                // Scrollable content
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(Spacing.medium)
                ) {
                    // Sets and Reps editor
                    SetRepsEditor(
                        setReps = setReps,
                        onSetRepsChange = { setReps = it }
                    )

                    // Weight input
                    NumberInputField(
                        value = weight,
                        onValueChange = { newValue ->
                            weight = newValue
                            weightError = when {
                                newValue.isBlank() -> "Weight is required"
                                newValue.toFloatOrNull() == null -> "Must be a valid number"
                                newValue.toFloatOrNull()!! <= 0f -> "Must be a positive number"
                                else -> null
                            }
                        },
                        label = "Weight per Cable (kg)",
                        modifier = Modifier.fillMaxWidth(),
                        isDecimal = true,
                        error = weightError
                    )
                    Text(
                        "Total resistance = Weight × 2 (both cables)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(start = Spacing.medium)
                    )

                    // Progression input
                    NumberInputField(
                        value = progression,
                        onValueChange = { newValue ->
                            progression = newValue
                            progressionError = when {
                                newValue.isBlank() -> "Progression is required"
                                newValue.toFloatOrNull() == null -> "Must be a valid number"
                                newValue.toFloatOrNull()!! < 0f -> "Must be a positive number or zero"
                                else -> null
                            }
                        },
                        label = "Progression per Cable (kg)",
                        modifier = Modifier.fillMaxWidth(),
                        isDecimal = true,
                        error = progressionError
                    )
                    Text(
                        "Weight increase when progressing to next level",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(start = Spacing.medium)
                    )

                    // Rest time input
                    NumberInputField(
                        value = rest,
                        onValueChange = { newValue ->
                            rest = newValue
                            restError = when {
                                newValue.isBlank() -> "Rest time is required"
                                newValue.toIntOrNull() == null -> "Must be a valid number"
                                newValue.toIntOrNull()!! < 0 -> "Must be zero or greater"
                                else -> null
                            }
                        },
                        label = "Rest Time (seconds)",
                        modifier = Modifier.fillMaxWidth(),
                        error = restError
                    )

                    // Notes field
                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = { Text("Notes (optional)") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp),
                        maxLines = 5,
                        placeholder = { Text("Form cues, tempo, range of motion, etc.") }
                    )
                }

                Spacer(modifier = Modifier.height(Spacing.medium))

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
                            // Validate inputs
                            val hasErrors = weightError != null || progressionError != null || restError != null || setReps.isEmpty()
                            
                            if (!hasErrors) {
                                val updatedExercise = exercise.copy(
                                    setReps = setReps,
                                    weightPerCableKg = weight.toFloatOrNull() ?: 0f,
                                    progressionKg = progression.toFloatOrNull() ?: 0f,
                                    restSeconds = rest.toIntOrNull() ?: 0,
                                    notes = notes.trim()
                                )
                                onSave(updatedExercise)
                            } else {
                                // Trigger validation on all fields
                                if (weight.isBlank() || weight.toFloatOrNull() == null || weight.toFloatOrNull()!! <= 0f) {
                                    weightError = "Must be a positive number"
                                }
                                if (progression.isBlank() || progression.toFloatOrNull() == null || progression.toFloatOrNull()!! < 0f) {
                                    progressionError = "Must be a positive number or zero"
                                }
                                if (rest.isBlank() || rest.toIntOrNull() == null || rest.toIntOrNull()!! < 0) {
                                    restError = "Must be zero or greater"
                                }
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Save")
                    }
                }
            }
        }
    }
}

@Composable
fun NumberInputField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    isDecimal: Boolean = false,
    error: String? = null
) {
    Column(modifier = modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(
                keyboardType = if (isDecimal) KeyboardType.Decimal else KeyboardType.Number
            ),
            singleLine = true,
            isError = error != null,
            supportingText = if (error != null) {
                { Text(error, color = MaterialTheme.colorScheme.error) }
            } else null
        )
    }
}

private fun isValidInt(value: String, min: Int = Int.MIN_VALUE, max: Int = Int.MAX_VALUE): Boolean {
    return value.toIntOrNull()?.let { it in min..max } ?: false
}

private fun isValidFloat(value: String, min: Float = Float.MIN_VALUE, max: Float = Float.MAX_VALUE): Boolean {
    return value.toFloatOrNull()?.let { it in min..max } ?: false
}

@Composable
fun SetRepsEditor(
    setReps: List<Int>,
    onSetRepsChange: (List<Int>) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.small)
    ) {
        // Header
        Text(
            "Sets & Reps",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = Spacing.extraSmall)
        )

        // Set rows
        setReps.forEachIndexed { index, reps ->
            SetRow(
                setNumber = index + 1,
                reps = reps,
                canDelete = setReps.size > 1,
                onRepsChange = { newReps ->
                    val updatedList = setReps.toMutableList()
                    updatedList[index] = newReps
                    onSetRepsChange(updatedList)
                },
                onDelete = {
                    onSetRepsChange(setReps.filterIndexed { i, _ -> i != index })
                }
            )
        }

        // Add Set button
        OutlinedButton(
            onClick = {
                val lastReps = setReps.lastOrNull() ?: 10
                onSetRepsChange(setReps + lastReps)
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = "Add set", modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(Spacing.small))
            Text("Add Set")
        }
    }
}

@Composable
fun SetRow(
    setNumber: Int,
    reps: Int,
    canDelete: Boolean,
    onRepsChange: (Int) -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.medium),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Set label
            Text(
                "Set $setNumber",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.width(60.dp)
            )

            // Rep adjuster
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.medium),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                IconButton(
                    onClick = { if (reps > 1) onRepsChange(reps - 1) },
                    enabled = reps > 1
                ) {
                    Icon(
                        Icons.Default.KeyboardArrowDown,
                        contentDescription = "Decrease reps",
                        tint = if (reps > 1) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                    )
                }

                Text(
                    text = "$reps",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.width(40.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )

                IconButton(
                    onClick = { onRepsChange(reps + 1) }
                ) {
                    Icon(
                        Icons.Default.KeyboardArrowUp,
                        contentDescription = "Increase reps",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // Delete button
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
    }
}
