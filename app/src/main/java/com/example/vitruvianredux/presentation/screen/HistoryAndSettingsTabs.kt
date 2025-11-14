package com.example.vitruvianredux.presentation.screen

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.vitruvianredux.domain.model.WeightUnit
import com.example.vitruvianredux.domain.model.WorkoutSession
import kotlinx.coroutines.launch
import com.example.vitruvianredux.presentation.components.EmptyState
import com.example.vitruvianredux.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun HistoryTab(
    groupedWorkoutHistory: List<com.example.vitruvianredux.presentation.viewmodel.HistoryItem>,
    weightUnit: WeightUnit,
    formatWeight: (Float, WeightUnit) -> String,
    onDeleteWorkout: (String) -> Unit,
    exerciseRepository: com.example.vitruvianredux.data.repository.ExerciseRepository,
    onRefresh: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var isRefreshing by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(Spacing.medium)
    ) {
        // Header with refresh button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Workout History",
                style = MaterialTheme.typography.headlineLarge, // Material 3 Expressive: Larger (was headlineMedium)
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            IconButton(
                onClick = {
                    isRefreshing = true
                    onRefresh()
                    // Reset after a short delay
                    kotlinx.coroutines.MainScope().launch {
                        kotlinx.coroutines.delay(1000)
                        isRefreshing = false
                    }
                }
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Refresh workout history",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = if (isRefreshing) {
                        Modifier.rotate(360f)
                    } else {
                        Modifier
                    }
                )
            }
        }
        Spacer(modifier = Modifier.height(Spacing.medium))

        if (groupedWorkoutHistory.isEmpty()) {
            EmptyState(
                icon = Icons.Default.History,
                title = "No Workout History Yet",
                message = "Complete your first workout to see it here"
            )
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(Spacing.small)
            ) {
                items(groupedWorkoutHistory.size, key = { index ->
                    when (val item = groupedWorkoutHistory[index]) {
                        is com.example.vitruvianredux.presentation.viewmodel.SingleSessionHistoryItem -> item.session.id
                        is com.example.vitruvianredux.presentation.viewmodel.GroupedRoutineHistoryItem -> item.routineSessionId
                    }
                }) { index ->
                    when (val item = groupedWorkoutHistory[index]) {
                        is com.example.vitruvianredux.presentation.viewmodel.SingleSessionHistoryItem -> {
                            WorkoutHistoryCard(
                                session = item.session,
                                weightUnit = weightUnit,
                                formatWeight = formatWeight,
                                exerciseRepository = exerciseRepository,
                                onDelete = { onDeleteWorkout(item.session.id) }
                            )
                        }
                        is com.example.vitruvianredux.presentation.viewmodel.GroupedRoutineHistoryItem -> {
                            GroupedRoutineCard(
                                groupedItem = item,
                                weightUnit = weightUnit,
                                formatWeight = formatWeight,
                                exerciseRepository = exerciseRepository,
                                onDelete = { sessionId -> onDeleteWorkout(sessionId) }
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
fun WorkoutHistoryCard(
    session: WorkoutSession,
    weightUnit: WeightUnit,
    formatWeight: (Float, WeightUnit) -> String,
    exerciseRepository: com.example.vitruvianredux.data.repository.ExerciseRepository,
    onDelete: () -> Unit
) {
    var showDeleteDialog by remember { mutableStateOf(false) }
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f, // Material 3 Expressive: More scale (was 0.98f)
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy, // Material 3 Expressive: More bouncy (was MediumBouncy)
            stiffness = Spring.StiffnessLow // Material 3 Expressive: Springy feel (was 400f)
        ),
        label = "scale"
    )

    // Fetch exercise name if exerciseId is available
    var exerciseName by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(session.exerciseId) {
        session.exerciseId?.let { id ->
            exerciseName = exerciseRepository.getExerciseById(id)?.name
        }
    }

    Card(
        onClick = { isPressed = true },
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .shadow(8.dp, RoundedCornerShape(20.dp)), // Material 3 Expressive: More shadow, more rounded
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest), // Material 3 Expressive: Higher contrast
        shape = RoundedCornerShape(20.dp), // Material 3 Expressive: More rounded (was 16dp)
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp), // Material 3 Expressive: Higher elevation (was 4dp)
        border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)) // Material 3 Expressive: Thicker border (was 1dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.medium)
        ) {
            // Header Section
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    // Material 3 Expressive: Larger Gradient Icon Box
                    Box(
                        modifier = Modifier
                            .size(56.dp) // Material 3 Expressive: Larger (was 48dp)
                            .shadow(8.dp, RoundedCornerShape(20.dp)) // Material 3 Expressive: More shadow, more rounded (was 16dp)
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(Color(0xFF9333EA), Color(0xFF7E22CE))
                                ),
                                RoundedCornerShape(20.dp) // Material 3 Expressive: More rounded (was 16dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.FitnessCenter,
                            contentDescription = "Workout routine",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(28.dp) // Material 3 Expressive: Larger icon (was default)
                        )
                    }
                    Spacer(modifier = Modifier.width(Spacing.medium))
                    Column(modifier = Modifier.weight(1f)) {
                        // Exercise name (or "Just Lift" if no exercise selected)
                        Text(
                            exerciseName ?: "Just Lift",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(Spacing.extraSmall))
                        // Date/time
                        Text(
                            formatRelativeTimestamp(session.timestamp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                // Material 3 Expressive: Duration badge
                Surface(
                    shape = RoundedCornerShape(12.dp), // Material 3 Expressive: More rounded (was 8dp)
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.padding(start = Spacing.small)
                ) {
                    Text(
                        formatDuration(session.duration),
                        style = MaterialTheme.typography.labelLarge, // Material 3 Expressive: Larger (was labelMedium)
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp) // Material 3 Expressive: More padding
                    )
                }
            }

            Spacer(modifier = Modifier.height(Spacing.medium))

            // Progress bar showing progress through last/current set
            val progressValue = if (session.workingReps > 0 && session.reps > 0) {
                val repsInCurrentSet = session.workingReps % session.reps
                if (repsInCurrentSet == 0) 1f else repsInCurrentSet.toFloat() / session.reps
            } else 0f
            LinearProgressIndicator(
                progress = { progressValue },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            )

            Spacer(modifier = Modifier.height(Spacing.medium))

            // Stats Grid (2x2 layout)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                EnhancedMetricItem(
                    icon = Icons.Default.Check,
                    label = "Total Reps",
                    value = session.totalReps.toString(),
                    modifier = Modifier.weight(1f)
                )
                EnhancedMetricItem(
                    icon = Icons.AutoMirrored.Filled.List,
                    label = "Sets",
                    value = if (session.workingReps > 0) (session.workingReps / session.reps.coerceAtLeast(1)).toString() else "0",
                    modifier = Modifier.weight(1f)
                )
            }
            
            Spacer(modifier = Modifier.height(Spacing.small))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                EnhancedMetricItem(
                    icon = Icons.Default.Info,
                    label = "Weight/Cable",
                    value = formatWeight(session.weightPerCableKg, weightUnit),
                    modifier = Modifier.weight(1f)
                )
                EnhancedMetricItem(
                    icon = Icons.Default.Settings,
                    label = "Mode",
                    value = session.mode,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(Spacing.small))

            // Divider
            HorizontalDivider(
                thickness = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant
            )

            Spacer(modifier = Modifier.height(Spacing.small))

            // Action Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = { showDeleteDialog = true },
                    modifier = Modifier.height(48.dp), // Material 3 Expressive: Taller button
                    shape = RoundedCornerShape(20.dp), // Material 3 Expressive: More rounded (was 16dp)
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete workout",
                        modifier = Modifier.size(20.dp) // Material 3 Expressive: Larger icon (was 18dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        "Delete",
                        style = MaterialTheme.typography.titleMedium, // Material 3 Expressive: Larger text
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }

    // Material 3 Expressive: Delete dialog
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { 
                Text(
                    "Delete Workout?",
                    style = MaterialTheme.typography.headlineSmall, // Material 3 Expressive: Larger
                    fontWeight = FontWeight.Bold
                ) 
            },
            text = { 
                Text(
                    "This action cannot be undone.",
                    style = MaterialTheme.typography.bodyLarge // Material 3 Expressive: Larger
                ) 
            },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest, // Material 3 Expressive: Higher contrast
            shape = RoundedCornerShape(28.dp), // Material 3 Expressive: Very rounded for dialogs (was 16dp)
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete()
                        showDeleteDialog = false
                    },
                    modifier = Modifier.height(56.dp), // Material 3 Expressive: Taller button
                    shape = RoundedCornerShape(20.dp) // Material 3 Expressive: More rounded
                ) {
                    Text(
                        "Delete",
                        style = MaterialTheme.typography.titleLarge, // Material 3 Expressive: Larger text
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDeleteDialog = false },
                    modifier = Modifier.height(56.dp), // Material 3 Expressive: Taller button
                    shape = RoundedCornerShape(20.dp) // Material 3 Expressive: More rounded
                ) {
                    Text(
                        "Cancel",
                        style = MaterialTheme.typography.titleMedium, // Material 3 Expressive: Larger text
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        )
    }

    LaunchedEffect(isPressed) {
        if (isPressed) {
            kotlinx.coroutines.delay(100)
            isPressed = false
        }
    }
}

/**
 * Card showing a grouped routine session with multiple exercises
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupedRoutineCard(
    groupedItem: com.example.vitruvianredux.presentation.viewmodel.GroupedRoutineHistoryItem,
    weightUnit: WeightUnit,
    formatWeight: (Float, WeightUnit) -> String,
    exerciseRepository: com.example.vitruvianredux.data.repository.ExerciseRepository,
    onDelete: (String) -> Unit
) {
    var isExpanded by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f, // Material 3 Expressive: More scale (was 0.98f)
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy, // Material 3 Expressive: More bouncy (was MediumBouncy)
            stiffness = Spring.StiffnessLow // Material 3 Expressive: Springy feel (was 400f)
        ),
        label = "scale"
    )

    Card(
        onClick = { isExpanded = !isExpanded },
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .shadow(8.dp, RoundedCornerShape(20.dp)), // Material 3 Expressive: More shadow, more rounded
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest), // Material 3 Expressive: Higher contrast
        shape = RoundedCornerShape(20.dp), // Material 3 Expressive: More rounded (was 16dp)
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp), // Material 3 Expressive: Higher elevation (was 4dp)
        border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)) // Material 3 Expressive: Thicker border (was 1dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.medium)
        ) {
            // Header Section
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    // Material 3 Expressive: Larger Gradient Icon Box
                    Box(
                        modifier = Modifier
                            .size(56.dp) // Material 3 Expressive: Larger (was 48dp)
                            .shadow(8.dp, RoundedCornerShape(20.dp)) // Material 3 Expressive: More shadow, more rounded (was 16dp)
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(Color(0xFF9333EA), Color(0xFF7E22CE))
                                ),
                                RoundedCornerShape(20.dp) // Material 3 Expressive: More rounded (was 16dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.List,
                            contentDescription = "Workout history",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(28.dp) // Material 3 Expressive: Larger icon (was default)
                        )
                    }
                    Spacer(modifier = Modifier.width(Spacing.medium))
                    Column(modifier = Modifier.weight(1f)) {
                        // Routine name
                        Text(
                            groupedItem.routineName,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(Spacing.extraSmall))
                        // Date/time
                        Text(
                            formatRelativeTimestamp(groupedItem.timestamp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Material 3 Expressive: Duration badge
                Surface(
                    shape = RoundedCornerShape(12.dp), // Material 3 Expressive: More rounded (was 8dp)
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.padding(start = Spacing.small)
                ) {
                    Text(
                        formatDuration(groupedItem.totalDuration),
                        style = MaterialTheme.typography.labelLarge, // Material 3 Expressive: Larger (was labelMedium)
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp) // Material 3 Expressive: More padding
                    )
                }
            }

            Spacer(modifier = Modifier.height(Spacing.medium))

            // Stats Grid (2x2 layout)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                EnhancedMetricItem(
                    icon = Icons.Default.Check,
                    label = "Total Reps",
                    value = groupedItem.totalReps.toString(),
                    modifier = Modifier.weight(1f)
                )
                EnhancedMetricItem(
                    icon = Icons.Default.FitnessCenter,
                    label = "Exercises",
                    value = groupedItem.exerciseCount.toString(),
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(Spacing.small))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                EnhancedMetricItem(
                    icon = Icons.AutoMirrored.Filled.List,
                    label = "Sets",
                    value = groupedItem.sessions.size.toString(),
                    modifier = Modifier.weight(1f)
                )
                EnhancedMetricItem(
                    icon = Icons.Default.Info,
                    label = "Expand",
                    value = if (isExpanded) "▲" else "▼",
                    modifier = Modifier.weight(1f)
                )
            }

            // Expanded section showing individual sets
            if (isExpanded) {
                Spacer(modifier = Modifier.height(Spacing.medium))
                HorizontalDivider(
                    thickness = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant
                )
                Spacer(modifier = Modifier.height(Spacing.medium))

                Text(
                    "Individual Sets (${groupedItem.sessions.size})",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(Spacing.small))

                groupedItem.sessions.forEach { session ->
                    WorkoutSessionCard(
                        session = session,
                        weightUnit = weightUnit,
                        formatWeight = formatWeight,
                        exerciseRepository = exerciseRepository,
                        onDelete = { onDelete(session.id) }
                    )
                    Spacer(modifier = Modifier.height(Spacing.small))
                }
            }

            Spacer(modifier = Modifier.height(Spacing.small))

            // Divider
            HorizontalDivider(
                thickness = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant
            )

            Spacer(modifier = Modifier.height(Spacing.small))

            // Action Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = { showDeleteDialog = true },
                    modifier = Modifier.height(48.dp), // Material 3 Expressive: Taller button
                    shape = RoundedCornerShape(20.dp), // Material 3 Expressive: More rounded (was 16dp)
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete routine session",
                        modifier = Modifier.size(20.dp) // Material 3 Expressive: Larger icon (was 18dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        "Delete All Sets",
                        style = MaterialTheme.typography.titleMedium, // Material 3 Expressive: Larger text
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }

    // Material 3 Expressive: Delete dialog
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { 
                Text(
                    "Delete Routine Session?",
                    style = MaterialTheme.typography.headlineSmall, // Material 3 Expressive: Larger
                    fontWeight = FontWeight.Bold
                ) 
            },
            text = { 
                Text(
                    "This will delete all ${groupedItem.sessions.size} sets from this routine. This action cannot be undone.",
                    style = MaterialTheme.typography.bodyLarge // Material 3 Expressive: Larger
                ) 
            },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest, // Material 3 Expressive: Higher contrast
            shape = RoundedCornerShape(28.dp), // Material 3 Expressive: Very rounded for dialogs (was 16dp)
            confirmButton = {
                TextButton(
                    onClick = {
                        // Delete all sessions in the routine
                        groupedItem.sessions.forEach { session ->
                            onDelete(session.id)
                        }
                        showDeleteDialog = false
                    },
                    modifier = Modifier.height(56.dp), // Material 3 Expressive: Taller button
                    shape = RoundedCornerShape(20.dp) // Material 3 Expressive: More rounded
                ) {
                    Text(
                        "Delete",
                        style = MaterialTheme.typography.titleLarge, // Material 3 Expressive: Larger text
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDeleteDialog = false },
                    modifier = Modifier.height(56.dp), // Material 3 Expressive: Taller button
                    shape = RoundedCornerShape(20.dp) // Material 3 Expressive: More rounded
                ) {
                    Text(
                        "Cancel",
                        style = MaterialTheme.typography.titleMedium, // Material 3 Expressive: Larger text
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        )
    }

    LaunchedEffect(isPressed) {
        if (isPressed) {
            kotlinx.coroutines.delay(100)
            isPressed = false
        }
    }
}

/**
 * Compact version of WorkoutHistoryCard for displaying within the expanded GroupedRoutineCard
 */
@Composable
fun WorkoutSessionCard(
    session: WorkoutSession,
    weightUnit: WeightUnit,
    formatWeight: (Float, WeightUnit) -> String,
    exerciseRepository: com.example.vitruvianredux.data.repository.ExerciseRepository,
    onDelete: () -> Unit
) {
    var exerciseName by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(session.exerciseId) {
        session.exerciseId?.let { id ->
            exerciseName = exerciseRepository.getExerciseById(id)?.name
        }
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.small),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    exerciseName ?: "Just Lift",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    "${formatWeight(session.weightPerCableKg, weightUnit)}/cable • ${session.totalReps} reps • ${session.mode}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                formatDuration(session.duration),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun MetricItem(label: String, value: String) {
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

@Composable
fun SettingsTab(
    weightUnit: WeightUnit,
    autoplayEnabled: Boolean,
    stopAtTop: Boolean,
    enableVideoPlayback: Boolean,
    onWeightUnitChange: (WeightUnit) -> Unit,
    onAutoplayChange: (Boolean) -> Unit,
    onStopAtTopChange: (Boolean) -> Unit,
    onEnableVideoPlaybackChange: (Boolean) -> Unit,
    onColorSchemeChange: (Int) -> Unit,
    onDeleteAllWorkouts: () -> Unit,
    onNavigateToConnectionLogs: () -> Unit = {},
    isAutoConnecting: Boolean = false,
    connectionError: String? = null,
    onClearConnectionError: () -> Unit = {},
    onCancelAutoConnecting: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var showDeleteAllDialog by remember { mutableStateOf(false) }
    // Optimistic UI state for immediate visual feedback
    var localWeightUnit by remember(weightUnit) { mutableStateOf(weightUnit) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Spacing.medium),
        verticalArrangement = Arrangement.spacedBy(Spacing.medium)
    ) {
        Text(
            "Settings",
            style = MaterialTheme.typography.headlineLarge, // Material 3 Expressive: Larger (was headlineMedium)
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

    // Weight Unit Section - Material 3 Expressive
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(8.dp, RoundedCornerShape(20.dp)), // Material 3 Expressive: More shadow, more rounded
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest), // Material 3 Expressive: Higher contrast
        shape = RoundedCornerShape(20.dp), // Material 3 Expressive: More rounded (was 16dp)
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp), // Material 3 Expressive: Higher elevation (was 4dp)
        border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)) // Material 3 Expressive: Thicker border (was 1dp)
    ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Spacing.medium)
            ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(48.dp) // Material 3 Expressive: Larger (was 40dp)
                        .shadow(8.dp, RoundedCornerShape(20.dp)) // Material 3 Expressive: More shadow, more rounded (was 16dp)
                        .background(
                            Brush.linearGradient(
                                colors = listOf(Color(0xFF8B5CF6), Color(0xFF9333EA))
                            ),
                            RoundedCornerShape(20.dp) // Material 3 Expressive: More rounded (was 16dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Scale,
                        contentDescription = "Weight unit settings",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(24.dp) // Material 3 Expressive: Larger icon
                    )
                }
                Spacer(modifier = Modifier.width(Spacing.medium))
                Text(
                    "Weight Unit",
                    style = MaterialTheme.typography.titleLarge, // Material 3 Expressive: Larger (was titleMedium)
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
                Spacer(modifier = Modifier.height(Spacing.small))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.small)
                ) {
                    FilterChip(
                        selected = localWeightUnit == WeightUnit.KG,
                        onClick = { 
                            localWeightUnit = WeightUnit.KG
                            onWeightUnitChange(WeightUnit.KG) 
                        },
                        label = { Text("kg") },
                        modifier = Modifier.weight(1f),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                            containerColor = MaterialTheme.colorScheme.surface,
                            labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                    FilterChip(
                        selected = localWeightUnit == WeightUnit.LB,
                        onClick = { 
                            localWeightUnit = WeightUnit.LB
                            onWeightUnitChange(WeightUnit.LB) 
                        },
                        label = { Text("lbs") },
                        modifier = Modifier.weight(1f),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                            containerColor = MaterialTheme.colorScheme.surface,
                            labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
            }
        }

    // Workout Preferences Section - Material 3 Expressive
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(8.dp, RoundedCornerShape(20.dp)), // Material 3 Expressive: More shadow, more rounded
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest), // Material 3 Expressive: Higher contrast
        shape = RoundedCornerShape(20.dp), // Material 3 Expressive: More rounded (was 16dp)
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp), // Material 3 Expressive: Higher elevation (was 4dp)
        border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)) // Material 3 Expressive: Thicker border (was 1dp)
    ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Spacing.medium)
            ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(48.dp) // Material 3 Expressive: Larger (was 40dp)
                        .shadow(8.dp, RoundedCornerShape(20.dp)) // Material 3 Expressive: More shadow, more rounded (was 16dp)
                        .background(
                            Brush.linearGradient(
                                colors = listOf(Color(0xFF6366F1), Color(0xFF8B5CF6))
                            ),
                            RoundedCornerShape(20.dp) // Material 3 Expressive: More rounded (was 16dp)
                        ),
                    contentAlignment = Alignment.Center
                ) { 
                    Icon(
                        Icons.Default.Tune,
                        contentDescription = "Advanced settings",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(24.dp) // Material 3 Expressive: Larger icon
                    ) 
                }
                Spacer(modifier = Modifier.width(Spacing.medium))
                Text(
                    "Workout Preferences",
                    style = MaterialTheme.typography.titleLarge, // Material 3 Expressive: Larger (was titleMedium)
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
                Spacer(modifier = Modifier.height(Spacing.small))

                // Autoplay toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            "Autoplay Routines",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Automatically advance to next exercise after rest timer",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = autoplayEnabled,
                        onCheckedChange = onAutoplayChange
                    )
                }

                Spacer(modifier = Modifier.height(Spacing.medium))

                // Stop At Top toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            "Stop At Top",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Release tension at contracted position instead of extended position",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = stopAtTop,
                        onCheckedChange = onStopAtTopChange
                    )
                }

                Spacer(modifier = Modifier.height(Spacing.medium))

                // Enable Video Playback toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            "Show Exercise Videos",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Display exercise demonstration videos (disable to avoid slow loading)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = enableVideoPlayback,
                        onCheckedChange = onEnableVideoPlaybackChange
                    )
                }
            }
        }

    // Color Scheme Section - Material 3 Expressive
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(8.dp, RoundedCornerShape(20.dp)), // Material 3 Expressive: More shadow, more rounded
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest), // Material 3 Expressive: Higher contrast
        shape = RoundedCornerShape(20.dp), // Material 3 Expressive: More rounded (was 16dp)
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp), // Material 3 Expressive: Higher elevation (was 4dp)
        border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)) // Material 3 Expressive: Thicker border (was 1dp)
    ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Spacing.medium)
            ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(48.dp) // Material 3 Expressive: Larger (was 40dp)
                        .shadow(8.dp, RoundedCornerShape(20.dp)) // Material 3 Expressive: More shadow, more rounded (was 16dp)
                        .background(
                            Brush.linearGradient(
                                colors = listOf(Color(0xFF3B82F6), Color(0xFF6366F1))
                            ),
                            RoundedCornerShape(20.dp) // Material 3 Expressive: More rounded (was 16dp)
                        ),
                    contentAlignment = Alignment.Center
                ) { 
                    Icon(
                        Icons.Default.ColorLens,
                        contentDescription = "Theme color settings",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(24.dp) // Material 3 Expressive: Larger icon
                    ) 
                }
                Spacer(modifier = Modifier.width(Spacing.medium))
                Text(
                    "LED Color Scheme",
                    style = MaterialTheme.typography.titleLarge, // Material 3 Expressive: Larger (was titleMedium)
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
                Spacer(modifier = Modifier.height(Spacing.small))

                val colorSchemes = listOf(
                    "Blue", "Green", "Teal", "Yellow", "Pink", "Red", "Purple"
                )

                colorSchemes.forEachIndexed { index, name ->
                    TextButton(
                        onClick = { onColorSchemeChange(index) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(name, color = MaterialTheme.colorScheme.onSurface)
                            Icon(
                                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = "Navigate to settings",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }

    // Data Management Section - Material 3 Expressive
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(8.dp, RoundedCornerShape(20.dp)), // Material 3 Expressive: More shadow, more rounded
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest), // Material 3 Expressive: Higher contrast
        shape = RoundedCornerShape(20.dp), // Material 3 Expressive: More rounded (was 16dp)
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp), // Material 3 Expressive: Higher elevation (was 4dp)
        border = BorderStroke(2.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.2f)) // Material 3 Expressive: Thicker border, error color for destructive action
    ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Spacing.medium)
            ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(48.dp) // Material 3 Expressive: Larger (was 40dp)
                        .shadow(8.dp, RoundedCornerShape(20.dp)) // Material 3 Expressive: More shadow, more rounded (was 16dp)
                        .background(
                            Brush.linearGradient(
                                colors = listOf(Color(0xFFF97316), Color(0xFFEF4444))
                            ),
                            RoundedCornerShape(20.dp) // Material 3 Expressive: More rounded (was 16dp)
                        ),
                    contentAlignment = Alignment.Center
                ) { 
                    Icon(
                        Icons.Default.DeleteForever,
                        contentDescription = "Clear workout history",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(24.dp) // Material 3 Expressive: Larger icon
                    ) 
                }
                Spacer(modifier = Modifier.width(Spacing.medium))
                Text(
                    "Data Management",
                    style = MaterialTheme.typography.titleLarge, // Material 3 Expressive: Larger (was titleMedium)
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
                Spacer(modifier = Modifier.height(Spacing.small))

                Button(
                    onClick = { showDeleteAllDialog = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp), // Material 3 Expressive: Taller button
                    shape = RoundedCornerShape(20.dp), // Material 3 Expressive: More rounded
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    elevation = ButtonDefaults.buttonElevation(
                        defaultElevation = 4.dp,
                        pressedElevation = 2.dp
                    )
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete all workouts",
                        modifier = Modifier.size(24.dp) // Material 3 Expressive: Larger icon
                    )
                    Spacer(modifier = Modifier.width(Spacing.small))
                    Text(
                        "Delete All Workouts",
                        style = MaterialTheme.typography.titleLarge, // Material 3 Expressive: Larger text
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

    // Developer Tools Section - Material 3 Expressive
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(8.dp, RoundedCornerShape(20.dp)), // Material 3 Expressive: More shadow, more rounded
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest), // Material 3 Expressive: Higher contrast
        shape = RoundedCornerShape(20.dp), // Material 3 Expressive: More rounded (was 16dp)
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp), // Material 3 Expressive: Higher elevation (was 4dp)
        border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)) // Material 3 Expressive: Thicker border (was 1dp)
    ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Spacing.medium)
            ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(48.dp) // Material 3 Expressive: Larger (was 40dp)
                        .shadow(8.dp, RoundedCornerShape(20.dp)) // Material 3 Expressive: More shadow, more rounded (was 16dp)
                        .background(
                            Brush.linearGradient(
                                colors = listOf(Color(0xFFF59E0B), Color(0xFFEF4444))
                            ),
                            RoundedCornerShape(20.dp) // Material 3 Expressive: More rounded (was 16dp)
                        ),
                    contentAlignment = Alignment.Center
                ) { 
                    Icon(
                        Icons.Default.BugReport,
                        contentDescription = "View connection logs",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(24.dp) // Material 3 Expressive: Larger icon
                    ) 
                }
                Spacer(modifier = Modifier.width(Spacing.medium))
                Text(
                    "Developer Tools",
                    style = MaterialTheme.typography.titleLarge, // Material 3 Expressive: Larger (was titleMedium)
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
                Spacer(modifier = Modifier.height(Spacing.small))

                OutlinedButton(
                    onClick = onNavigateToConnectionLogs,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp), // Material 3 Expressive: Taller button
                    shape = RoundedCornerShape(20.dp) // Material 3 Expressive: More rounded
                ) {
                    Icon(
                        Icons.Default.Timeline,
                        contentDescription = "Connection logs",
                        modifier = Modifier.size(24.dp) // Material 3 Expressive: Larger icon
                    )
                    Spacer(modifier = Modifier.width(Spacing.small))
                    Text(
                        "Connection Logs",
                        style = MaterialTheme.typography.titleLarge, // Material 3 Expressive: Larger text
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "View Bluetooth connection debug logs to diagnose connectivity issues",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

    // App Info Section - Material 3 Expressive
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(8.dp, RoundedCornerShape(20.dp)), // Material 3 Expressive: More shadow, more rounded
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest), // Material 3 Expressive: Higher contrast
        shape = RoundedCornerShape(20.dp), // Material 3 Expressive: More rounded (was 16dp)
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp), // Material 3 Expressive: Higher elevation (was 4dp)
        border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)) // Material 3 Expressive: Thicker border (was 1dp)
    ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Spacing.medium)
            ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(48.dp) // Material 3 Expressive: Larger (was 40dp)
                        .shadow(8.dp, RoundedCornerShape(20.dp)) // Material 3 Expressive: More shadow, more rounded (was 16dp)
                        .background(
                            Brush.linearGradient(
                                colors = listOf(Color(0xFF22C55E), Color(0xFF3B82F6))
                            ),
                            RoundedCornerShape(20.dp) // Material 3 Expressive: More rounded (was 16dp)
                        ),
                    contentAlignment = Alignment.Center
                ) { 
                    Icon(
                        Icons.Default.Info,
                        contentDescription = "App information",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(24.dp) // Material 3 Expressive: Larger icon
                    ) 
                }
                Spacer(modifier = Modifier.width(Spacing.medium))
                Text(
                    "App Info",
                    style = MaterialTheme.typography.titleLarge, // Material 3 Expressive: Larger (was titleMedium)
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
                Spacer(modifier = Modifier.height(Spacing.small))
                Text("Version: 0.4.0-beta", color = MaterialTheme.colorScheme.onSurface)
                Text("Build: Beta 4", color = MaterialTheme.colorScheme.onSurface)
                Spacer(modifier = Modifier.height(Spacing.small))
                Text(
                    "Open source community project to control Vitruvian Trainer machines locally.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    // Material 3 Expressive: Delete All dialog
    if (showDeleteAllDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteAllDialog = false },
            title = { 
                Text(
                    "Delete All Workouts?",
                    style = MaterialTheme.typography.headlineSmall, // Material 3 Expressive: Larger
                    fontWeight = FontWeight.Bold
                ) 
            },
            text = { 
                Text(
                    "This will permanently delete all workout history. This action cannot be undone.",
                    style = MaterialTheme.typography.bodyLarge // Material 3 Expressive: Larger
                ) 
            },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest, // Material 3 Expressive: Higher contrast
            shape = RoundedCornerShape(28.dp), // Material 3 Expressive: Very rounded for dialogs (was 16dp)
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteAllWorkouts()
                        showDeleteAllDialog = false
                    },
                    modifier = Modifier.height(56.dp), // Material 3 Expressive: Taller button
                    shape = RoundedCornerShape(20.dp) // Material 3 Expressive: More rounded
                ) {
                    Text(
                        "Delete All",
                        style = MaterialTheme.typography.titleLarge, // Material 3 Expressive: Larger text
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDeleteAllDialog = false },
                    modifier = Modifier.height(56.dp), // Material 3 Expressive: Taller button
                    shape = RoundedCornerShape(20.dp) // Material 3 Expressive: More rounded
                ) {
                    Text(
                        "Cancel",
                        style = MaterialTheme.typography.titleMedium, // Material 3 Expressive: Larger text
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        )
    }

    // Auto-connect UI overlays (same as other screens)
    if (isAutoConnecting) {
        com.example.vitruvianredux.presentation.components.ConnectingOverlay(
            onCancel = onCancelAutoConnecting
        )
    }

    connectionError?.let { error ->
        com.example.vitruvianredux.presentation.components.ConnectionErrorDialog(
            message = error,
            onDismiss = onClearConnectionError
        )
    }
}

private fun formatTimestamp(timestamp: Long): String {
    val sdf = SimpleDateFormat("MMM dd, yyyy 'at' HH:mm", Locale.getDefault())
    return sdf.format(Date(timestamp))
}

private fun formatRelativeTimestamp(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    val daysDiff = diff / (24 * 60 * 60 * 1000)
    
    val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
    val dateFormat = SimpleDateFormat("MMM dd", Locale.getDefault())
    
    return when {
        daysDiff == 0L -> "Today at ${timeFormat.format(Date(timestamp))}"
        daysDiff == 1L -> "Yesterday at ${timeFormat.format(Date(timestamp))}"
        daysDiff < 7 -> "${dateFormat.format(Date(timestamp))} at ${timeFormat.format(Date(timestamp))}"
        else -> dateFormat.format(Date(timestamp))
    }
}

@Composable
fun EnhancedMetricItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = "Workout session icon",
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.width(Spacing.extraSmall))
        Column(horizontalAlignment = Alignment.Start) {
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
}

private fun formatDuration(millis: Long): String {
    val totalSeconds = millis / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}
