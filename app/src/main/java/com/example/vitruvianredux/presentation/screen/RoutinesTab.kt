package com.example.vitruvianredux.presentation.screen

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.vitruvianredux.data.repository.ExerciseRepository
import com.example.vitruvianredux.domain.model.Routine
import com.example.vitruvianredux.domain.model.WeightUnit
import com.example.vitruvianredux.presentation.components.EmptyState
import com.example.vitruvianredux.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun RoutinesTab(
    routines: List<Routine>,
    exerciseRepository: ExerciseRepository,
    personalRecordRepository: com.example.vitruvianredux.data.repository.PersonalRecordRepository,
    formatWeight: (Float, WeightUnit) -> String,
    weightUnit: WeightUnit,
    kgToDisplay: (Float, WeightUnit) -> Float,
    displayToKg: (Float, WeightUnit) -> Float,
    onStartWorkout: (Routine) -> Unit,
    onDeleteRoutine: (String) -> Unit,
    onCreateRoutine: () -> Unit,
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
            Text(
                "My Routines",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(Spacing.medium))

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
                    verticalArrangement = Arrangement.spacedBy(Spacing.small)
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
                                val newRoutineId = java.util.UUID.randomUUID().toString()
                                val newExercises = routine.exercises.map { exercise ->
                                    exercise.copy(
                                        id = java.util.UUID.randomUUID().toString(),
                                        // Deep copy the Exercise object to avoid any shared references
                                        exercise = exercise.exercise.copy()
                                    )
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

        // Extended Floating Action Button for creating new routine
        ExtendedFloatingActionButton(
            onClick = {
                routineToEdit = null
                showRoutineBuilder = true
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(Spacing.medium),
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        ) {
            Icon(
                Icons.Default.Add,
                contentDescription = null // Icon is decorative, text label provides accessibility
            )
            Spacer(modifier = Modifier.width(Spacing.small))
            Text("New Routine")
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
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.99f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = 400f
        ),
        label = "scale"
    )

    Card(
        onClick = onStartWorkout,
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isPressed) 2.dp else 4.dp
        ),
        border = BorderStroke(1.dp, Color(0xFFF5F3FF))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 64dp Gradient Icon (purple gradient)
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .shadow(4.dp, RoundedCornerShape(12.dp))
                    .background(
                        Brush.linearGradient(
                            colors = listOf(Color(0xFF9333EA), Color(0xFF7E22CE))
                        ),
                        RoundedCornerShape(12.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.FitnessCenter,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(32.dp)
                )
            }

            // Content Column
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = routine.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = routine.description.ifEmpty { "${routine.exercises.size} exercises" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Exercise list with set/rep configuration
                val exercisesToShow = routine.exercises.take(4)
                val remainingCount = (routine.exercises.size - exercisesToShow.size).coerceAtLeast(0)

                Column(
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    exercisesToShow.forEach { routineExercise ->
                        Text(
                            text = "${routineExercise.exercise.name} - ${formatSetReps(routineExercise.setReps)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (remainingCount > 0) {
                        Text(
                            text = "+ $remainingCount more",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Text(
                    text = "${routine.exercises.size} exercises • ${formatEstimatedDuration(routine)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            // Arrow Icon
            Surface(
                shape = RoundedCornerShape(50),
                color = Color(0xFFF5F3FF),
                modifier = Modifier.size(36.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "Navigate",
                        tint = Color(0xFF9333EA),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        // Overflow menu (top-right) for edit/duplicate/delete
        var showMenu by remember { mutableStateOf(false) }
        Box(modifier = Modifier.fillMaxWidth()) {
            IconButton(
                onClick = { showMenu = !showMenu },
                modifier = Modifier.align(Alignment.TopEnd)
            ) {
                Icon(Icons.Default.MoreVert, contentDescription = "Menu")
            }

            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false }
            ) {
                DropdownMenuItem(
                    text = { Text("Edit") },
                    onClick = { showMenu = false; onEdit() },
                    leadingIcon = { Icon(Icons.Default.Edit, null) }
                )
                DropdownMenuItem(
                    text = { Text("Duplicate") },
                    onClick = { showMenu = false; onDuplicate() },
                    leadingIcon = { Icon(Icons.Default.ContentCopy, null) }
                )
                DropdownMenuItem(
                    text = { Text("Delete") },
                    onClick = { showMenu = false; onDelete() },
                    leadingIcon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) }
                )
            }
        }
    }

    LaunchedEffect(isPressed) {
        if (isPressed) {
            kotlinx.coroutines.delay(100)
            isPressed = false
        }
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

private fun formatSetReps(setReps: List<Int>): String {
    if (setReps.isEmpty()) return "0 sets"

    // Group consecutive identical reps
    val groups = mutableListOf<Pair<Int, Int>>() // Pair of (count, reps)
    var currentReps = setReps[0]
    var currentCount = 1

    for (i in 1 until setReps.size) {
        if (setReps[i] == currentReps) {
            currentCount++
        } else {
            groups.add(Pair(currentCount, currentReps))
            currentReps = setReps[i]
            currentCount = 1
        }
    }
    groups.add(Pair(currentCount, currentReps))

    // Format as "3×10, 2×8"
    return groups.joinToString(", ") { (count, reps) -> "${count}×${reps}" }
}

private fun formatDate(timestamp: Long): String {
    val sdf = SimpleDateFormat("MMM dd", Locale.getDefault())
    return sdf.format(Date(timestamp))
}

private fun formatEstimatedDuration(routine: Routine): String {
    // Estimate: 30 seconds per rep + rest time
    val totalSets = routine.exercises.sumOf { it.setReps.size }
    val totalReps = routine.exercises.sumOf { exercise -> exercise.setReps.sum() }
    val totalRestSeconds = routine.exercises.sumOf { it.restSeconds * (it.setReps.size - 1) }
    
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
