package com.example.vitruvianredux.presentation.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.RemoveCircle
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
    var progression by remember { mutableStateOf(exercise.progressionKg.toString()) }
    var rest by remember { mutableStateOf(exercise.restSeconds.toString()) }
    var notes by remember { mutableStateOf(exercise.notes) }
    var showError by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f),
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
                    Column {
                        Text(
                            "Configure Exercise",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            exercise.exercise.displayName,
                            style = MaterialTheme.typography.bodyMedium,
                            color = PrimaryPurple
                        )
                    }
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
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(Spacing.medium)
                ) {
                    // Sets and Reps editor
                    SetRepsEditor(
                        setReps = setReps,
                        onSetRepsChange = { setReps = it; showError = false }
                    )

                    // Weight input
                    NumberInputField(
                        value = weight,
                        onValueChange = { weight = it; showError = false },
                        label = "Weight per Cable (kg)",
                        modifier = Modifier.fillMaxWidth(),
                        isDecimal = true,
                        isError = showError && !isValidFloat(weight, min = 0f)
                    )
                    Text(
                        "Total resistance = Weight × 2 (both cables)",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextTertiary,
                        modifier = Modifier.padding(start = Spacing.medium)
                    )

                    // Progression input
                    NumberInputField(
                        value = progression,
                        onValueChange = { progression = it; showError = false },
                        label = "Progression per Cable (kg)",
                        modifier = Modifier.fillMaxWidth(),
                        isDecimal = true,
                        isError = showError && !isValidFloat(progression, min = 0f)
                    )
                    Text(
                        "Weight increase when progressing to next level",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextTertiary,
                        modifier = Modifier.padding(start = Spacing.medium)
                    )

                    // Rest time input
                    NumberInputField(
                        value = rest,
                        onValueChange = { rest = it; showError = false },
                        label = "Rest Time (seconds)",
                        modifier = Modifier.fillMaxWidth(),
                        isError = showError && !isValidInt(rest, min = 0)
                    )

                    // Notes field
                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = { Text("Notes (optional)", color = TextSecondary) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = PrimaryPurple,
                            unfocusedBorderColor = TextTertiary,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            cursorColor = PrimaryPurple
                        ),
                        maxLines = 5,
                        placeholder = { Text("Form cues, tempo, range of motion, etc.", color = TextTertiary) }
                    )

                    // Validation error
                    if (showError) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = ErrorRed.copy(alpha = 0.1f)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                "Please enter valid positive numbers for all fields and at least one set",
                                color = ErrorRed,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(Spacing.medium)
                            )
                        }
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
                            // Validate inputs
                            if (setReps.isNotEmpty() &&
                                isValidFloat(weight, min = 0f) &&
                                isValidFloat(progression, min = 0f) &&
                                isValidInt(rest, min = 0)
                            ) {
                                val updatedExercise = exercise.copy(
                                    setReps = setReps,
                                    weightPerCableKg = weight.toFloat(),
                                    progressionKg = progression.toFloat(),
                                    restSeconds = rest.toInt(),
                                    notes = notes.trim()
                                )
                                onSave(updatedExercise)
                            } else {
                                showError = true
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
}

@Composable
fun NumberInputField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    isDecimal: Boolean = false,
    isError: Boolean = false
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, color = TextSecondary) },
        modifier = modifier,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = PrimaryPurple,
            unfocusedBorderColor = TextTertiary,
            focusedTextColor = TextPrimary,
            unfocusedTextColor = TextPrimary,
            cursorColor = PrimaryPurple,
            errorBorderColor = ErrorRed
        ),
        keyboardOptions = KeyboardOptions(
            keyboardType = if (isDecimal) KeyboardType.Decimal else KeyboardType.Number
        ),
        singleLine = true,
        isError = isError
    )
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
            color = TextPrimary,
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
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = PrimaryPurple
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(20.dp))
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
        colors = CardDefaults.cardColors(containerColor = CardBackground),
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
                color = TextPrimary,
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
                        Icons.Default.RemoveCircle,
                        contentDescription = "Decrease reps",
                        tint = if (reps > 1) PrimaryPurple else TextDisabled
                    )
                }

                Text(
                    text = "$reps",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    modifier = Modifier.width(40.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )

                IconButton(
                    onClick = { onRepsChange(reps + 1) }
                ) {
                    Icon(
                        Icons.Default.AddCircle,
                        contentDescription = "Increase reps",
                        tint = PrimaryPurple
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
                    tint = if (canDelete) ErrorRed else TextDisabled
                )
            }
        }
    }
}
