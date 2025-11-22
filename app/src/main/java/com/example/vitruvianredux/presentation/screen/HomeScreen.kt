package com.example.vitruvianredux.presentation.screen

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.vitruvianredux.domain.model.ConnectionState
import com.example.vitruvianredux.domain.model.WeightUnit
import com.example.vitruvianredux.presentation.components.StatsCard
import com.example.vitruvianredux.presentation.navigation.NavigationRoutes
import com.example.vitruvianredux.presentation.viewmodel.MainViewModel
import com.example.vitruvianredux.ui.theme.Spacing
import com.example.vitruvianredux.ui.theme.ThemeMode
import java.time.LocalDate

/**
 * Home screen showing workout type selection with modern gradient card design.
 * This is the main landing screen when user opens the app.
 */
@Composable
fun HomeScreen(
    navController: NavController,
    viewModel: MainViewModel,
    themeMode: ThemeMode
) {
    // Collect stats from ViewModel
    val workoutStreak by viewModel.workoutStreak.collectAsState()
    val completedWorkouts by viewModel.completedWorkouts.collectAsState()
    val progressPercentage by viewModel.progressPercentage.collectAsState()

    // Collect connection state
    val connectionState by viewModel.connectionState.collectAsState()
    val isAutoConnecting by viewModel.isAutoConnecting.collectAsState()
    val connectionError by viewModel.connectionError.collectAsState()

    // Collect active program and routines for Active Program Widget
    val activeProgram by viewModel.activeProgram.collectAsState()
    val routines by viewModel.routines.collectAsState()
    val weightUnit by viewModel.weightUnit.collectAsState()

    // Determine if we have any stats to show
    val hasStats = workoutStreak != null || completedWorkouts != null || progressPercentage != null

    // Determine actual theme (matching Theme.kt logic)
    val useDarkColors = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    val backgroundGradient = if (useDarkColors) {
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
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundGradient)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // Simple header with just "Start a workout" title
            Text(
                "Start a workout",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            // Active Program Widget - shows today's routine from active program
            activeProgram?.let { program ->
                HomeActiveProgramCard(
                    program = program,
                    routines = routines,
                    weightUnit = weightUnit,
                    formatWeight = viewModel::formatWeight,
                    kgToDisplay = viewModel::kgToDisplay,
                    onStartRoutine = { routineId ->
                        viewModel.ensureConnection(
                            onConnected = {
                                viewModel.loadRoutineById(routineId)
                                viewModel.startWorkout()
                                navController.navigate(NavigationRoutes.DailyRoutines.route)
                            },
                            onFailed = { /* Error shown via StateFlow */ }
                        )
                    }
                )
            }

            // Stats overview hidden (not needed per user request)
            // Future: can be re-enabled by changing condition to: if (hasStats)

            WorkoutCard(
                title = "Just Lift",
                description = "Quick setup, start lifting immediately",
                icon = Icons.Default.FitnessCenter,
                gradient = Brush.linearGradient(
                    colors = listOf(Color(0xFF9333EA), Color(0xFF7E22CE)) // purple-500 to purple-700
                ),
                onClick = { navController.navigate(NavigationRoutes.JustLift.route) }
            )

            WorkoutCard(
                title = "Single Exercise",
                description = "Perform one exercise with custom configuration",
                icon = Icons.Default.PlayArrow,
                gradient = Brush.linearGradient(
                    colors = listOf(Color(0xFF8B5CF6), Color(0xFF9333EA)) // violet-500 to purple-600
                ),
                onClick = { navController.navigate(NavigationRoutes.SingleExercise.route) }
            )

            WorkoutCard(
                title = "Daily Routines",
                description = "Choose from your saved multi-exercise routines",
                icon = Icons.Default.CalendarToday,
                gradient = Brush.linearGradient(
                    colors = listOf(Color(0xFF6366F1), Color(0xFF8B5CF6)) // indigo-500 to violet-600
                ),
                onClick = { navController.navigate(NavigationRoutes.DailyRoutines.route) }
            )

            WorkoutCard(
                title = "Weekly Programs",
                description = "Follow a structured weekly training schedule",
                icon = Icons.Default.DateRange,
                gradient = Brush.linearGradient(
                    colors = listOf(Color(0xFF3B82F6), Color(0xFF6366F1)) // blue-500 to indigo-600
                ),
                onClick = { navController.navigate(NavigationRoutes.WeeklyPrograms.route) }
            )
        }

        // Auto-connect UI overlays (same as exercise start screens)
        if (isAutoConnecting) {
            com.example.vitruvianredux.presentation.components.ConnectingOverlay(
                onCancel = { viewModel.cancelAutoConnecting() }
            )
        }

        connectionError?.let { error ->
            com.example.vitruvianredux.presentation.components.ConnectionErrorDialog(
                message = error,
                onDismiss = { viewModel.clearConnectionError() }
            )
        }
    }
}

/**
 * Compact workout card matching reference design.
 * Features: 64dp icon, title, description, smooth animations.
 * No dummy stats displayed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutCard(
    title: String,
    description: String,
    icon: ImageVector,
    gradient: Brush,
    onClick: () -> Unit
) {
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f, // Material 3 Expressive: More scale (was 0.97f)
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy, // Material 3 Expressive: More bouncy (was MediumBouncy)
            stiffness = Spring.StiffnessLow // Material 3 Expressive: Lower stiffness for springy feel (was 400f)
        ),
        label = "scale"
    )

    Card(
        onClick = {
            isPressed = true
            onClick()
        },
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale),
        shape = RoundedCornerShape(20.dp), // Material 3 Expressive: More rounded (was 16dp)
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest // Expressive: Higher contrast
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isPressed) 4.dp else 8.dp // Material 3 Expressive: Higher elevation (was 2/4dp)
        ),
        border = BorderStroke(2.dp, Color(0xFFF5F3FF)) // Material 3 Expressive: Thicker border (was 1dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp), // Material 3 Expressive: More padding (was 16dp)
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Material 3 Expressive: Larger Gradient Icon Container (72dp)
            Box(
                modifier = Modifier
                    .size(72.dp) // Material 3 Expressive: Larger (was 64dp)
                    .shadow(8.dp, RoundedCornerShape(20.dp)) // Material 3 Expressive: More shadow, more rounded (was 16dp)
                    .background(gradient, RoundedCornerShape(20.dp)), // Material 3 Expressive: More rounded (was 16dp)
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = "Select $title workout",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(36.dp) // Material 3 Expressive: Larger icon (was 32dp)
                )
            }

            // Content Column - Only title and description
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge, // Material 3 Expressive: Larger (was titleMedium)
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium, // Material 3 Expressive: Larger (was bodySmall)
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Material 3 Expressive: Larger Arrow Icon
            Surface(
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.primaryContainer, // Material 3 Expressive: Use theme color
                modifier = Modifier.size(40.dp) // Material 3 Expressive: Larger (was 36dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "Navigate",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer, // Use theme color
                        modifier = Modifier.size(20.dp) // Material 3 Expressive: Larger icon (was 16dp)
                    )
                }
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

/**
 * Compact Active Program Card for HomeScreen.
 * Shows today's workout exercises with expandable/collapsible format.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeActiveProgramCard(
    program: com.example.vitruvianredux.data.local.entity.WeeklyProgramWithDays,
    routines: List<com.example.vitruvianredux.domain.model.Routine>,
    weightUnit: WeightUnit,
    formatWeight: (Float, WeightUnit) -> String,
    kgToDisplay: (Float, WeightUnit) -> Float,
    onStartRoutine: (String) -> Unit
) {
    var isExpanded by remember { mutableStateOf(false) }
    
    val today = LocalDate.now().dayOfWeek
    // Use Java DayOfWeek.value directly (MONDAY=1, TUESDAY=2, ..., SUNDAY=7)
    // This matches what ProgramBuilder saves: day.value
    val todayDayValue = today.value

    // Find today's routine ID from program days
    val todayRoutineId = program.days.find { it.dayOfWeek == todayDayValue }?.routineId
    val todayRoutine = todayRoutineId?.let { routineId ->
        routines.find { it.id == routineId }
    }
    val hasWorkoutToday = todayRoutineId != null

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        border = BorderStroke(2.dp, Color(0xFFF5F3FF))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.medium)
        ) {
            if (hasWorkoutToday) {
                // Show exercises if routine is loaded
                todayRoutine?.let { routine ->
                    // Expand/Collapse button
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { isExpanded = !isExpanded },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isExpanded) "Today's Routine" else "Today's Routine",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Icon(
                            imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = if (isExpanded) "Collapse" else "Expand",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(Spacing.small))
                    
                    if (isExpanded) {
                        // Expanded view: Show all exercises with detailed format
                        Column(
                            verticalArrangement = Arrangement.spacedBy(Spacing.medium)
                        ) {
                            routine.exercises.forEach { exercise ->
                                Column(
                                    verticalArrangement = Arrangement.spacedBy(Spacing.extraSmall)
                                ) {
                                    // Exercise name
                                    Text(
                                        text = exercise.exercise.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    
                                    // Mode
                                    Text(
                                        text = "(${exercise.workoutType.displayName} Mode)",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    
                                    // Sets with weights
                                    val weightSuffix = if (weightUnit == WeightUnit.LB) "lbs" else "kg"
                                    exercise.setReps.forEachIndexed { index, reps ->
                                        val weight = if (exercise.setWeightsPerCableKg.isNotEmpty() && index < exercise.setWeightsPerCableKg.size) {
                                            kgToDisplay(exercise.setWeightsPerCableKg[index], weightUnit)
                                        } else {
                                            kgToDisplay(exercise.weightPerCableKg, weightUnit)
                                        }
                                        Text(
                                            text = "${reps ?: "AMRAP"} x ${"%.1f".format(weight)} $weightSuffix",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        // Collapsed view: Show only exercise names
                        Column(
                            verticalArrangement = Arrangement.spacedBy(Spacing.extraSmall)
                        ) {
                            routine.exercises.forEach { exercise ->
                                Text(
                                    text = exercise.exercise.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(Spacing.medium))

                    // Start Routine button
                    Button(
                        onClick = { onStartRoutine(todayRoutineId) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape = RoundedCornerShape(20.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        ),
                        elevation = ButtonDefaults.buttonElevation(
                            defaultElevation = 4.dp,
                            pressedElevation = 2.dp
                        )
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = "Start routine")
                        Spacer(modifier = Modifier.width(Spacing.small))
                        Text(
                            "Start Routine",
                            style = MaterialTheme.typography.titleMedium, // Reduced from titleLarge
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            } else {
                // Rest day
                Text(
                    text = "Rest day",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
