package com.example.vitruvianredux.presentation.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
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
    var sets by remember { mutableStateOf(exercise.sets.toString()) }
    var reps by remember { mutableStateOf(exercise.reps.toString()) }
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
                    // Sets and Reps row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.small)
                    ) {
                        NumberInputField(
                            value = sets,
                            onValueChange = { sets = it; showError = false },
                            label = "Sets",
                            modifier = Modifier.weight(1f),
                            isError = showError && !isValidInt(sets, min = 1)
                        )
                        NumberInputField(
                            value = reps,
                            onValueChange = { reps = it; showError = false },
                            label = "Reps",
                            modifier = Modifier.weight(1f),
                            isError = showError && !isValidInt(reps, min = 1)
                        )
                    }

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
                                "Please enter valid positive numbers for all fields",
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
                            if (isValidInt(sets, min = 1) &&
                                isValidInt(reps, min = 1) &&
                                isValidFloat(weight, min = 0f) &&
                                isValidFloat(progression, min = 0f) &&
                                isValidInt(rest, min = 0)
                            ) {
                                val updatedExercise = exercise.copy(
                                    setReps = List(sets.toInt()) { reps.toInt() },
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
