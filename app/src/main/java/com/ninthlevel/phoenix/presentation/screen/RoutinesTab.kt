package com.ninthlevel.phoenix.presentation.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.shadow
import java.text.SimpleDateFormat
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ninthlevel.phoenix.data.repository.ExerciseRepository
import com.ninthlevel.phoenix.data.repository.PersonalRecordRepository
import com.ninthlevel.phoenix.domain.model.Routine
import com.ninthlevel.phoenix.domain.model.WeightUnit
import com.ninthlevel.phoenix.presentation.components.EmptyState
import com.ninthlevel.phoenix.ui.theme.*
import timber.log.Timber
import java.util.*

@Suppress("UNUSED_PARAMETER")  // Some parameters kept for API compatibility
@Composable
fun RoutinesTab(
    routines: List<Routine>,
    exerciseRepository: ExerciseRepository,
    personalRecordRepository: PersonalRecordRepository,
    formatWeight: (Float, WeightUnit) -> String,
    weightUnit: WeightUnit,
    kgToDisplay: (Float, WeightUnit) -> Float,
    displayToKg: (Float, WeightUnit) -> Float,
    onStartWorkout: (Routine) -> Unit,
    onDeleteRoutine: (String) -> Unit,
    onSaveRoutine: (Routine) -> Unit,
    onUpdateRoutine: (Routine) -> Unit = onSaveRoutine,
    themeMode: ThemeMode,
    modifier: Modifier = Modifier
) {
    var showRoutineBuilder by remember { mutableStateOf(false) }
    var routineToEdit by remember { mutableStateOf<Routine?>(null) }

    val backgroundGradient = if (themeMode == ThemeMode.DARK) {
        Brush.verticalGradient(
            colors = listOf(
                Color(0xFF0F172A), // slate-900
                Color(0xFF1E1B4B), // indigo-950
                Color(0xFF172554)  // blue-950
            )
        )
    } else {
        Brush.verticalGradient(
            colors = listOf(
                Color(0xFFE0E7FF), // indigo-200 - soft lavender
                Color(0xFFFCE7F3), // pink-100 - soft pink
                Color(0xFFDDD6FE)  // violet-200 - soft violet
            )
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundGradient)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp)
        ) {
            if (routines.isEmpty()) {
                EmptyState(
                    icon = Icons.Default.FitnessCenter,
                    title = "No Routines Yet",
                    message = "Create your first workout routine to get started",
                    actionText = "Create Your First Routine",
                    onAction = {
                        routineToEdit = null
                        showRoutineBuilder = true
                    }
                )
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(Spacing.small),
                    contentPadding = PaddingValues(bottom = 80.dp) // Space for FAB (56dp + padding)
                ) {
                    items(routines, key = { it.id }) { routine ->
                        RoutineCard(
                            routine = routine,
                            onStartWorkout = { onStartWorkout(routine) },
                            onEdit = {
                                routineToEdit = routine
                                showRoutineBuilder = true
                            },
                            onDelete = { onDeleteRoutine(routine.id) },
                            onDuplicate = {
                                // Generate new IDs explicitly and create deep copies
                                val newRoutineId = UUID.randomUUID().toString()
                                val newExercises = routine.exercises.map { exercise ->
                                    Timber.d("📋 Original exercise '${exercise.exercise.name}': setReps=${exercise.setReps}, setWeights=${exercise.setWeightsPerCableKg}, setRest=${exercise.setRestSeconds}")
                                    val copied = exercise.copy(
                                        id = UUID.randomUUID().toString(),
                                        // Deep copy the Exercise object to avoid any shared references
                                        exercise = exercise.exercise.copy()
                                    )
                                    Timber.d("📋 Copied exercise '${copied.exercise.name}': setReps=${copied.setReps}, setWeights=${copied.setWeightsPerCableKg}, setRest=${copied.setRestSeconds}")
                                    copied
                                }

                                // Smart duplicate naming: extract base name and find next copy number
                                val baseName = routine.name.replace(Regex(""" \(Copy( \d+)?\)$"""), "")
                                val copyPattern = Regex("""^${Regex.escape(baseName)} \(Copy( (\d+))?\)$""")
                                val existingCopyNumbers = routines
                                    .mapNotNull { r ->
                                        when {
                                            r.name == baseName -> 0 // Original has number 0
                                            r.name == "$baseName (Copy)" -> 1 // First copy is 1
                                            else -> copyPattern.find(r.name)?.groups?.get(2)?.value?.toIntOrNull()
                                        }
                                    }
                                val nextCopyNumber = (existingCopyNumbers.maxOrNull() ?: 0) + 1
                                val newName = if (nextCopyNumber == 1) {
                                    "$baseName (Copy)"
                                } else {
                                    "$baseName (Copy $nextCopyNumber)"
                                }

                                val duplicated = routine.copy(
                                    id = newRoutineId,
                                    name = newName,
                                    createdAt = System.currentTimeMillis(),
                                    useCount = 0,
                                    lastUsed = null,
                                    exercises = newExercises
                                )
                                onSaveRoutine(duplicated)
                            }
                        )
                    }
                }
            }
        }

        // Floating Action Button for creating new routine
        FloatingActionButton(
            onClick = {
                routineToEdit = null
                showRoutineBuilder = true
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(Spacing.medium),
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            shape = RoundedCornerShape(16.dp)
        ) {
            Icon(
                Icons.Default.Add,
                contentDescription = "Add new routine",
                modifier = Modifier.size(28.dp)
            )
        }
    }

    // Show routine builder dialog
    if (showRoutineBuilder) {
        RoutineBuilderDialog(
            routine = routineToEdit,
            onSave = { routine ->
                if (routineToEdit != null) {
                    onUpdateRoutine(routine)
                } else {
                    onSaveRoutine(routine)
                }
                showRoutineBuilder = false
                routineToEdit = null
            },
            onDismiss = {
                showRoutineBuilder = false
                routineToEdit = null
            },
            exerciseRepository = exerciseRepository,
            personalRecordRepository = personalRecordRepository,
            formatWeight = formatWeight,
            weightUnit = weightUnit,
            kgToDisplay = kgToDisplay,
            displayToKg = displayToKg,
            themeMode = themeMode
        )
    }
}

@Composable
fun RoutineCard(
    routine: Routine,
    onStartWorkout: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onDuplicate: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    Card(
        onClick = { expanded = !expanded },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp), // Material 3 Expressive: More rounded
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest // Material 3 Expressive: Higher contrast
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (expanded) 8.dp else 2.dp
        ),
        border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Icon Box
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .shadow(8.dp, RoundedCornerShape(20.dp))
                        .background(
                            Brush.linearGradient(
                                colors = listOf(Color(0xFF9333EA), Color(0xFF7E22CE))
                            ),
                            RoundedCornerShape(20.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.FitnessCenter,
                        contentDescription = "Fitness routine",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(32.dp)
                    )
                }

                // Header Content
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = routine.name,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${routine.exercises.size} exercises • ${formatEstimatedDuration(routine)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Expand Icon
                Icon(
                    imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Expanded Content
            androidx.compose.animation.AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp)
                ) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(modifier = Modifier.height(16.dp))

                    // Exercise List
                    routine.exercises.forEachIndexed { index, exercise ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "${index + 1}. ${exercise.exercise.name}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = formatSetReps(exercise.setReps),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Action Buttons
                    Button(
                        onClick = onStartWorkout,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Start Workout", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        OutlinedButton(
                            onClick = onEdit,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Edit", maxLines = 1, softWrap = false)
                        }
                        Spacer(Modifier.width(6.dp))
                        OutlinedButton(
                            onClick = onDuplicate,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Copy", maxLines = 1, softWrap = false)
                        }
                        Spacer(Modifier.width(6.dp))
                        OutlinedButton(
                            onClick = { showDeleteDialog = true },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Delete", maxLines = 1, softWrap = false)
                        }
                    }
                }
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Routine") },
            text = { Text("Are you sure you want to delete '${routine.name}'?") },
            confirmButton = {
                TextButton(onClick = { onDelete(); showDeleteDialog = false }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun StatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun formatSetReps(setReps: List<Int?>): String {
    if (setReps.isEmpty()) return "0 sets"

    // Group consecutive identical reps
    val groups = mutableListOf<Pair<Int, String>>() // Pair of (count, reps as string)
    var currentReps = setReps[0]
    var currentCount = 1

    for (i in 1 until setReps.size) {
        if (setReps[i] == currentReps) {
            currentCount++
        } else {
            groups.add(Pair(currentCount, currentReps?.toString() ?: "AMRAP"))
            currentReps = setReps[i]
            currentCount = 1
        }
    }
    groups.add(Pair(currentCount, currentReps?.toString() ?: "AMRAP"))

    // Format as "3×10, 2×8" or "3×AMRAP"
    return groups.joinToString(", ") { (count, reps) -> "${count}×${reps}" }
}

private fun formatDate(timestamp: Long): String {
    val sdf = SimpleDateFormat("MMM dd", Locale.getDefault())
    return sdf.format(Date(timestamp))
}

private fun formatEstimatedDuration(routine: Routine): String {
    // Estimate: 30 seconds per rep + rest time
    val totalSets = routine.exercises.sumOf { it.setReps.size }
    val totalReps = routine.exercises.sumOf { exercise -> exercise.setReps.filterNotNull().sum() }
    val totalRestSeconds = routine.exercises.sumOf { exercise ->
        // Sum all rest times between sets (one less rest than number of sets)
        val restCount = maxOf(0, exercise.setReps.size - 1)
        exercise.setRestSeconds.take(restCount).sum()
    }
    
    val estimatedSeconds = (totalReps * 3) + totalRestSeconds // 3 seconds per rep estimate
    val minutes = estimatedSeconds / 60
    
    return if (minutes < 60) {
        "${minutes} min"
    } else {
        val hours = minutes / 60
        val remainingMinutes = minutes % 60
        "${hours}h ${remainingMinutes}m"
    }
}
