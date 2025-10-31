package com.example.vitruvianredux.presentation.screen

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.example.vitruvianredux.presentation.components.StatsCard
import com.example.vitruvianredux.presentation.components.ThemeToggle
import com.example.vitruvianredux.presentation.navigation.NavigationRoutes
import com.example.vitruvianredux.presentation.viewmodel.MainViewModel
import com.example.vitruvianredux.ui.theme.ThemeMode

/**
 * Home screen showing workout type selection with modern gradient card design.
 * This is the main landing screen when user opens the app.
 */
@Composable
fun HomeScreen(
    navController: NavController,
    viewModel: MainViewModel,
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit
) {
    // Collect stats from ViewModel
    val workoutStreak by viewModel.workoutStreak.collectAsState()
    val completedWorkouts by viewModel.completedWorkouts.collectAsState()
    val progressPercentage by viewModel.progressPercentage.collectAsState()

    // Collect connection state
    val connectionState by viewModel.connectionState.collectAsState()

    // Determine if we have any stats to show
    val hasStats = workoutStreak != null || completedWorkouts != null || progressPercentage != null

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
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundGradient)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // Header with title and icon controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Welcome back", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Vitruvian Control", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Connection status icon
                    IconButton(
                        onClick = {
                            // TODO: Toggle connection when clicked
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Bluetooth,
                            contentDescription = "Connection status",
                            modifier = Modifier.size(24.dp),
                            tint = when (connectionState) {
                                is ConnectionState.Connected -> Color(0xFF22C55E) // green-500
                                is ConnectionState.Connecting -> Color(0xFFFBBF24) // yellow-400
                                is ConnectionState.Disconnected -> Color(0xFFEF4444) // red-500
                                is ConnectionState.Scanning -> Color(0xFF3B82F6) // blue-500
                                is ConnectionState.Error -> Color(0xFFEF4444) // red-500
                            }
                        )
                    }

                    // Theme toggle icon
                    ThemeToggle(mode = themeMode, onModeChange = onThemeModeChange)
                }
            }

            // Stats overview - only show if there is data
            if (hasStats) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Streak card - only show if data exists
                    workoutStreak?.let { streak ->
                        StatsCard(
                            label = "Streak",
                            value = "$streak ${if (streak == 1) "day" else "days"}",
                            icon = Icons.Default.LocalFireDepartment,
                            iconColor = Color(0xFFF97316), // orange-500
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // Completed workouts card - only show if data exists
                    completedWorkouts?.let { completed ->
                        StatsCard(
                            label = "Completed",
                            value = completed.toString(),
                            icon = Icons.Default.CheckCircle,
                            iconColor = Color(0xFF22C55E), // green-500
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // Progress card - only show if data exists
                    progressPercentage?.let { progress ->
                        StatsCard(
                            label = "Progress",
                            value = "${if (progress > 0) "+" else ""}$progress%",
                            icon = Icons.AutoMirrored.Filled.TrendingUp,
                            iconColor = Color(0xFF3B82F6), // blue-500
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // Section title
            Text("Start a workout", style = MaterialTheme.typography.titleMedium)

            WorkoutCard(
                title = "Just Lift",
                description = "Quick workout session",
                icon = Icons.Default.FitnessCenter,
                gradient = Brush.linearGradient(
                    colors = listOf(Color(0xFF9333EA), Color(0xFF7E22CE)) // purple-500 to purple-700
                ),
                onClick = { navController.navigate(NavigationRoutes.JustLift.route) }
            )

            WorkoutCard(
                title = "Single Exercises",
                description = "Build custom routines",
                icon = Icons.Default.PlayArrow,
                gradient = Brush.linearGradient(
                    colors = listOf(Color(0xFF8B5CF6), Color(0xFF9333EA)) // violet-500 to purple-600
                ),
                onClick = { navController.navigate(NavigationRoutes.SingleExercise.route) }
            )

            WorkoutCard(
                title = "Daily Routines",
                description = "Structured daily plans",
                icon = Icons.Default.CalendarToday,
                gradient = Brush.linearGradient(
                    colors = listOf(Color(0xFF6366F1), Color(0xFF8B5CF6)) // indigo-500 to violet-600
                ),
                onClick = { navController.navigate(NavigationRoutes.DailyRoutines.route) }
            )

            WorkoutCard(
                title = "Weekly Programs",
                description = "Complete training programs",
                icon = Icons.Default.DateRange,
                gradient = Brush.linearGradient(
                    colors = listOf(Color(0xFF3B82F6), Color(0xFF6366F1)) // blue-500 to indigo-600
                ),
                onClick = { navController.navigate(NavigationRoutes.WeeklyPrograms.route) }
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
        targetValue = if (isPressed) 0.99f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = 400f
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
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isPressed) 2.dp else 4.dp
        ),
        border = BorderStroke(1.dp, Color(0xFFF5F3FF)) // purple-50 border
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Compact Gradient Icon Container (64dp)
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .shadow(4.dp, RoundedCornerShape(12.dp))
                    .background(gradient, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(32.dp)
                )
            }

            // Content Column - Only title and description
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Compact Arrow Icon
            Surface(
                shape = RoundedCornerShape(50),
                color = Color(0xFFF5F3FF), // purple-50
                modifier = Modifier.size(36.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "Navigate",
                        tint = Color(0xFF9333EA), // purple-500
                        modifier = Modifier.size(16.dp)
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
