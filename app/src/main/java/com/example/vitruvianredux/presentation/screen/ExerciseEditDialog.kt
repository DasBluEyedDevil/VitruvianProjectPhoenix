package com.example.vitruvianredux.presentation.screen

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
import com.example.vitruvianredux.domain.model.RoutineExercise
import com.example.vitruvianredux.domain.model.WeightUnit
import com.example.vitruvianredux.presentation.components.CompactNumberPicker
import com.example.vitruvianredux.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseEditBottomSheet(
    exercise: RoutineExercise,
    weightUnit: WeightUnit,
    kgToDisplay: (Float, WeightUnit) -> Float,
    displayToKg: (Float, WeightUnit) -> Float,
    onSave: (RoutineExercise) -> Unit,
    onDismiss: () -> Unit
) {
    // Calculate dynamic ranges and suffix based on unit system
    val weightSuffix = if (weightUnit == WeightUnit.LB) "lb" else "kg"
    val maxWeight = if (weightUnit == WeightUnit.LB) 220 else 100
    val maxWeightChange = if (weightUnit == WeightUnit.LB) 10 else 10

    var setReps by remember { mutableStateOf(exercise.setReps.ifEmpty { listOf(10) }) }
    var weight by remember {
        val displayValue = kgToDisplay(exercise.weightPerCableKg, weightUnit).toInt()
        mutableStateOf(displayValue.coerceIn(1, maxWeight))
    }
    // Weight change per rep: negative = regression, positive = progression
    // Default to 0 (no change) for new exercises, or load existing value
    var weightChange by remember {
        val displayValue = if (exercise.progressionKg == 0f) 0 else kgToDisplay(exercise.progressionKg, weightUnit).toInt()
        mutableStateOf(displayValue.coerceIn(-maxWeightChange, maxWeightChange))
    }
    var rest by remember { mutableStateOf(exercise.restSeconds.coerceIn(0, 300)) }
    var notes by remember { mutableStateOf(exercise.notes) }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
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
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Spacing.medium)
            ) {
                // Sets and Reps editor
                SetRepsEditor(
                    setReps = setReps,
                    onSetRepsChange = { setReps = it }
                )

                // Weight picker
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Column(modifier = Modifier.padding(Spacing.medium)) {
                        CompactNumberPicker(
                            value = weight,
                            onValueChange = { weight = it },
                            range = 1..maxWeight,
                            label = "Weight per Cable",
                            suffix = weightSuffix,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            "Total resistance = Weight × 2 (both cables)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.padding(top = Spacing.small)
                        )
                    }
                }

                // Weight Change per Rep picker (negative = regression, positive = progression)
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Column(modifier = Modifier.padding(Spacing.medium)) {
                        CompactNumberPicker(
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
                            modifier = Modifier.padding(top = Spacing.small)
                        )
                    }
                }

                // Rest time picker
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Column(modifier = Modifier.padding(Spacing.medium)) {
                        CompactNumberPicker(
                            value = rest,
                            onValueChange = { rest = it },
                            range = 0..300,
                            label = "Rest Time",
                            suffix = "sec",
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

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
                        val updatedExercise = exercise.copy(
                            setReps = setReps,
                            weightPerCableKg = displayToKg(weight.toFloat(), weightUnit),
                            progressionKg = displayToKg(weightChange.toFloat(), weightUnit),
                            restSeconds = rest,
                            notes = notes.trim()
                        )
                        onSave(updatedExercise)
                    },
                    modifier = Modifier.weight(1f),
                    enabled = setReps.isNotEmpty()
                ) {
                    Text("Save")
                }
            }
        }
    }
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

            // Rep picker
            CompactNumberPicker(
                value = reps,
                onValueChange = onRepsChange,
                range = 1..50,
                suffix = "reps",
                modifier = Modifier
                    .weight(1f)
                    .height(60.dp)
            )

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
