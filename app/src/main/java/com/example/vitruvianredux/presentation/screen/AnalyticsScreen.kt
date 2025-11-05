package com.example.vitruvianredux.presentation.screen

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.vitruvianredux.domain.model.WeightUnit
import com.example.vitruvianredux.domain.model.WorkoutSession
import com.example.vitruvianredux.presentation.viewmodel.MainViewModel
import com.example.vitruvianredux.ui.theme.Spacing

/**
 * Analytics screen with three tabs: History, Personal Bests, and Trends.
 * Provides comprehensive view of workout data and progress.
 */
@Composable
fun AnalyticsScreen(
    viewModel: MainViewModel,
    themeMode: com.example.vitruvianredux.ui.theme.ThemeMode
) {
    val workoutHistory by viewModel.workoutHistory.collectAsState()
    val weightUnit by viewModel.weightUnit.collectAsState()
    val isAutoConnecting by viewModel.isAutoConnecting.collectAsState()
    val connectionError by viewModel.connectionError.collectAsState()

    var selectedTab by remember { mutableStateOf(0) }

    val backgroundGradient = if (themeMode == com.example.vitruvianredux.ui.theme.ThemeMode.DARK) {
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
        ) {
            // Tab Row with gradient indicator
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary,
                indicator = { tabPositions ->
                    val currentTab = tabPositions.getOrNull(selectedTab)
                    if (currentTab != null) {
                        Box(
                            modifier = Modifier
                                .tabIndicatorOffset(currentTab)
                                .height(4.dp)
                                .shadow(2.dp, RoundedCornerShape(2.dp))
                                .background(
                                    Brush.linearGradient(
                                        colors = listOf(Color(0xFF9333EA), Color(0xFF7E22CE))
                                    ),
                                    RoundedCornerShape(2.dp)
                                )
                        )
                    }
                }
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("History") },
                    icon = { Icon(Icons.Default.List, contentDescription = null) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Personal Bests") },
                    icon = { Icon(Icons.Default.Star, contentDescription = null) }
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = { Text("Trends") },
                    icon = { Icon(Icons.Default.Info, contentDescription = null) }
                )
            }

            // Tab Content
            when (selectedTab) {
                0 -> HistoryTab(
                    workoutHistory = workoutHistory,
                    weightUnit = weightUnit,
                    formatWeight = viewModel::formatWeight,
                    onDeleteWorkout = { viewModel.deleteWorkout(it) },
                    modifier = Modifier.fillMaxSize()
                )
                1 -> PersonalBestsTab(
                    workoutHistory = workoutHistory,
                    weightUnit = weightUnit,
                    formatWeight = viewModel::formatWeight,
                    modifier = Modifier.fillMaxSize()
                )
                2 -> TrendsTab(
                    workoutHistory = workoutHistory,
                    weightUnit = weightUnit,
                    formatWeight = viewModel::formatWeight,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        // Auto-connect UI overlays (same as other screens)
        if (isAutoConnecting) {
            com.example.vitruvianredux.presentation.components.ConnectingOverlay()
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
 * Personal Bests tab - shows maximum weight lifted for each exercise.
 */
@Composable
fun PersonalBestsTab(
    workoutHistory: List<WorkoutSession>,
    weightUnit: WeightUnit,
    formatWeight: (Float, WeightUnit) -> String,
    modifier: Modifier = Modifier
) {
    // Calculate personal bests from workout history
    // Note: WorkoutSession doesn't have maxLoad or exerciseName fields
    // This is a placeholder implementation until those fields are added
    val personalBests = remember(workoutHistory) {
        emptyList<Pair<String, WorkoutSession>>()
    }

    LazyColumn(
        modifier = modifier.padding(Spacing.medium),
        verticalArrangement = Arrangement.spacedBy(Spacing.medium)
    ) {
        item {
            Text(
                "Your Personal Records",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(Spacing.small))
        }

        if (personalBests.isEmpty()) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(4.dp, RoundedCornerShape(16.dp)),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                    border = BorderStroke(1.dp, Color(0xFFF5F3FF))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(Spacing.large),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(Spacing.small))
                        Text(
                            "No personal records yet",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Complete workouts to see your PRs",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(personalBests.size) { index ->
                val (exerciseName, workout) = personalBests[index]
                PersonalBestCard(
                    rank = index + 1,
                    exerciseName = exerciseName,
                    workout = workout,
                    weightUnit = weightUnit,
                    formatWeight = formatWeight
                )
            }
        }
    }
}

/**
 * Card showing a personal best record.
 */
@Composable
fun PersonalBestCard(
    rank: Int,
    exerciseName: String,
    workout: WorkoutSession,
    weightUnit: WeightUnit,
    formatWeight: (Float, WeightUnit) -> String
) {
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = 400f
        ),
        label = "scale"
    )

    Card(
        onClick = { isPressed = true },
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        border = BorderStroke(1.dp, Color(0xFFF5F3FF))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.medium),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                // Rank badge
                Surface(
                    color = when (rank) {
                        1 -> MaterialTheme.colorScheme.tertiary
                        2, 3 -> MaterialTheme.colorScheme.secondary
                        else -> Color(0xFFF5F3FF)
                    },
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        "#$rank",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = when (rank) {
                            1 -> MaterialTheme.colorScheme.onTertiary
                            2, 3 -> MaterialTheme.colorScheme.onSecondary
                            else -> Color(0xFF9333EA)
                        }
                    )
                }

                Spacer(modifier = Modifier.width(Spacing.medium))

                Column {
                    Text(
                        exerciseName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Max: ${formatWeight(workout.weightPerCableKg * 2, weightUnit)}",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        "${workout.totalReps} reps ? ${java.text.SimpleDateFormat("MMM d, yyyy", java.util.Locale.getDefault()).format(workout.timestamp)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (rank == 1) {
                Icon(
                    Icons.Default.Star,
                    contentDescription = "Top record",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
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

/**
 * Trends tab - shows workout trends over time.
 */
@Composable
fun TrendsTab(
    workoutHistory: List<WorkoutSession>,
    weightUnit: WeightUnit,
    formatWeight: (Float, WeightUnit) -> String,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.padding(Spacing.medium),
        verticalArrangement = Arrangement.spacedBy(Spacing.medium)
    ) {
        item {
            Text(
                "Workout Trends",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(Spacing.small))
        }

        // Summary statistics
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(4.dp, RoundedCornerShape(16.dp)),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                border = BorderStroke(1.dp, Color(0xFFF5F3FF))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(Spacing.medium)
                ) {
                    Text(
                        "All-Time Stats",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(Spacing.medium))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        StatItem(
                            label = "Total Workouts",
                            value = workoutHistory.size.toString(),
                            icon = Icons.Default.Check
                        )
                        StatItem(
                            label = "Total Reps",
                            value = workoutHistory.sumOf { it.totalReps }.toString(),
                            icon = Icons.Default.Refresh
                        )
                        StatItem(
                            label = "Max Load",
                            value = formatWeight(
                                workoutHistory.maxOfOrNull { it.weightPerCableKg * 2 } ?: 0f,
                                weightUnit
                            ),
                            icon = Icons.Default.Star
                        )
                    }
                }
            }
        }

        // Placeholder for future charts
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(4.dp, RoundedCornerShape(16.dp)),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                border = BorderStroke(1.dp, Color(0xFFF5F3FF))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(Spacing.large),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(Spacing.small))
                    Text(
                        "Charts Coming Soon",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Progress charts and detailed analytics will be added in a future update",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/**
 * Stat item for trends tab.
 */
@Composable
fun StatItem(
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.height(Spacing.extraSmall))
        Text(
            value,
            style = MaterialTheme.typography.titleLarge,
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
