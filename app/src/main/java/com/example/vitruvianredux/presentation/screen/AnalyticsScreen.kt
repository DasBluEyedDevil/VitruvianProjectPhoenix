package com.example.vitruvianredux.presentation.screen

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateMap
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
import com.example.vitruvianredux.presentation.components.WeightProgressionChart
import com.example.vitruvianredux.presentation.components.MuscleGroupDistributionChart
import com.example.vitruvianredux.presentation.components.charts.MuscleGroupCircleChart
import com.example.vitruvianredux.presentation.components.WorkoutModeDistributionChart
import com.example.vitruvianredux.presentation.components.TotalVolumeChart
import com.example.vitruvianredux.presentation.screen.InsightsTab
import com.example.vitruvianredux.util.CsvExporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.LocalContext

/**
 * Analytics screen with three tabs: Dashboard, Trends, and History.
 * Provides comprehensive view of workout data and progress.
 */
@Composable
fun AnalyticsScreen(
    viewModel: MainViewModel,
    themeMode: com.example.vitruvianredux.ui.theme.ThemeMode
) {
    val workoutHistory by viewModel.workoutHistory.collectAsState()
    val groupedWorkoutHistory by viewModel.groupedWorkoutHistory.collectAsState()
    val allWorkoutSessions by viewModel.allWorkoutSessions.collectAsState()
    val personalRecords by viewModel.allPersonalRecords.collectAsState()
    val weightUnit by viewModel.weightUnit.collectAsState()
    val isAutoConnecting by viewModel.isAutoConnecting.collectAsState()
    val connectionError by viewModel.connectionError.collectAsState()

    // Pager state for swipe gestures
    val pagerState = rememberPagerState(pageCount = { 3 })
    var showExportMenu by remember { mutableStateOf(false) }
    var exportMessage by remember { mutableStateOf<String?>(null) }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Sync pager with tab selection
    LaunchedEffect(pagerState.currentPage) {
        // Update occurs when user swipes
    }

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
            // Tab Row with gradient indicator and swipe support - Material 3 Expressive
            TabRow(
                selectedTabIndex = pagerState.currentPage,
                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest, // Material 3 Expressive: Higher contrast
                contentColor = MaterialTheme.colorScheme.primary,
                indicator = { tabPositions ->
                    val currentTab = tabPositions.getOrNull(pagerState.currentPage)
                    if (currentTab != null) {
                        Box(
                            modifier = Modifier
                                .tabIndicatorOffset(currentTab)
                                .height(8.dp) // Material 3 Expressive: Thicker indicator (was 6dp)
                                .shadow(4.dp, RoundedCornerShape(4.dp)) // Material 3 Expressive: More shadow (was 2dp, 3dp)
                                .background(
                                    Brush.linearGradient(
                                        colors = listOf(Color(0xFF9333EA), Color(0xFF7E22CE))
                                    ),
                                    RoundedCornerShape(4.dp) // Material 3 Expressive: More rounded (was 3dp)
                                )
                        )
                    }
                }
            ) {
                Tab(
                    selected = pagerState.currentPage == 0,
                    onClick = { scope.launch { pagerState.animateScrollToPage(0) } },
                    text = { Text("Dashboard") },
                    icon = { Icon(Icons.Default.Dashboard, contentDescription = "Dashboard overview") }
                )
                Tab(
                    selected = pagerState.currentPage == 1,
                    onClick = { scope.launch { pagerState.animateScrollToPage(1) } },
                    text = { Text("Progression") },
                    icon = { Icon(Icons.Default.TrendingUp, contentDescription = "PR progression") }
                )
                Tab(
                    selected = pagerState.currentPage == 2,
                    onClick = { scope.launch { pagerState.animateScrollToPage(2) } },
                    text = { Text("History") },
                    icon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = "Workout history") }
                )
                Tab(
                    selected = pagerState.currentPage == 3,
                    onClick = { scope.launch { pagerState.animateScrollToPage(3) } },
                    text = { Text("Insights") },
                    icon = { Icon(Icons.Default.AutoAwesome, contentDescription = "Analytics insights") }
                )
            }

            // Tab Content with Swipe Support
            HorizontalPager(
                state = pagerState,
                pageCount = 4, // Material 3 Expressive: Added Insights tab
                modifier = Modifier.fillMaxSize()
            ) { page ->
                when (page) {
                    0 -> DashboardTab(
                        viewModel = viewModel,
                        personalRecords = personalRecords,
                        workoutHistory = workoutHistory,
                        weightUnit = weightUnit,
                        formatWeight = viewModel::formatWeight,
                        modifier = Modifier.fillMaxSize()
                    )
                    1 -> ProgressionTab(
                        personalRecords = personalRecords,
                        exerciseRepository = viewModel.exerciseRepository,
                        weightUnit = weightUnit,
                        formatWeight = viewModel::formatWeight,
                        modifier = Modifier.fillMaxSize()
                    )
                    2 -> HistoryTab(
                        groupedWorkoutHistory = groupedWorkoutHistory,
                        weightUnit = weightUnit,
                        formatWeight = viewModel::formatWeight,
                        onDeleteWorkout = { viewModel.deleteWorkout(it) },
                        exerciseRepository = viewModel.exerciseRepository,
                        onRefresh = { /* Workout history refreshes automatically via StateFlow */ },
                        modifier = Modifier.fillMaxSize()
                    )
                    3 -> InsightsTab(
                        prs = personalRecords,
                        workoutSessions = workoutHistory,
                        weightUnit = weightUnit,
                        formatWeight = viewModel::formatWeight,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }

        // Auto-connect UI overlays (same as other screens)
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

        // Export FAB - Material 3 Expressive
        FloatingActionButton(
            onClick = { showExportMenu = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(Spacing.large),
            containerColor = MaterialTheme.colorScheme.primary,
            shape = RoundedCornerShape(28.dp), // Material 3 Expressive: Very rounded FAB
            elevation = FloatingActionButtonDefaults.elevation(
                defaultElevation = 8.dp, // Material 3 Expressive: Higher elevation
                pressedElevation = 4.dp
            )
        ) {
            Icon(
                Icons.Default.Share,
                contentDescription = "Export data",
                modifier = Modifier.size(28.dp) // Material 3 Expressive: Larger icon (was default)
            )
        }
    }

    // Export options dialog
    if (showExportMenu) {
        AlertDialog(
            onDismissRequest = { showExportMenu = false },
            title = { Text("Export Data") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
                    Text("Choose what to export:", style = MaterialTheme.typography.bodyMedium)

                    // Export Personal Records button - Material 3 Expressive
                    Button(
                        onClick = {
                            scope.launch {
                                // Fetch exercise names
                                val exerciseNames = mutableMapOf<String, String>()
                                personalRecords.forEach { pr ->
                                    withContext(Dispatchers.IO) {
                                        try {
                                            val exercise = viewModel.exerciseRepository.getExerciseById(pr.exerciseId)
                                            exercise?.let { exerciseNames[pr.exerciseId] = it.name }
                                        } catch (e: Exception) {
                                            exerciseNames[pr.exerciseId] = "Unknown Exercise"
                                        }
                                    }
                                }

                                val result = CsvExporter.exportPersonalRecords(
                                    context,
                                    personalRecords,
                                    exerciseNames,
                                    weightUnit,
                                    viewModel::formatWeight
                                )

                                result.onSuccess { uri ->
                                    CsvExporter.shareCSV(context, uri, "personal_records.csv")
                                    exportMessage = "Personal records exported successfully"
                                    showExportMenu = false
                                }.onFailure {
                                    exportMessage = "Failed to export personal records"
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp), // Material 3 Expressive: Taller button
                        shape = RoundedCornerShape(20.dp), // Material 3 Expressive: More rounded
                        elevation = ButtonDefaults.buttonElevation(
                            defaultElevation = 4.dp,
                            pressedElevation = 2.dp
                        )
                    ) {
                        Icon(Icons.Default.Star, contentDescription = "Personal record", modifier = Modifier.size(24.dp)) // Material 3 Expressive: Larger icon
                        Spacer(modifier = Modifier.width(Spacing.small))
                        Text(
                            "Export Personal Records",
                            style = MaterialTheme.typography.titleLarge, // Material 3 Expressive: Larger text
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Export Workout History button - Material 3 Expressive
                    Button(
                        onClick = {
                            scope.launch {
                                // Fetch exercise names
                                val exerciseNames = mutableMapOf<String, String>()
                                workoutHistory.forEach { session ->
                                    session.exerciseId?.let { exerciseId ->
                                        withContext(Dispatchers.IO) {
                                            try {
                                                val exercise = viewModel.exerciseRepository.getExerciseById(exerciseId)
                                                exercise?.let { exerciseNames[exerciseId] = it.name }
                                            } catch (e: Exception) {
                                                exerciseNames[exerciseId] = "Unknown Exercise"
                                            }
                                        }
                                    }
                                }

                                val result = CsvExporter.exportWorkoutHistory(
                                    context,
                                    workoutHistory,
                                    exerciseNames,
                                    weightUnit,
                                    viewModel::formatWeight
                                )

                                result.onSuccess { uri ->
                                    CsvExporter.shareCSV(context, uri, "workout_history.csv")
                                    exportMessage = "Workout history exported successfully"
                                    showExportMenu = false
                                }.onFailure {
                                    exportMessage = "Failed to export workout history"
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp), // Material 3 Expressive: Taller button
                        shape = RoundedCornerShape(20.dp), // Material 3 Expressive: More rounded
                        elevation = ButtonDefaults.buttonElevation(
                            defaultElevation = 4.dp,
                            pressedElevation = 2.dp
                        )
                    ) {
                        Icon(Icons.AutoMirrored.Filled.List, contentDescription = "Workout history", modifier = Modifier.size(24.dp)) // Material 3 Expressive: Larger icon
                        Spacer(modifier = Modifier.width(Spacing.small))
                        Text(
                            "Export Workout History",
                            style = MaterialTheme.typography.titleLarge, // Material 3 Expressive: Larger text
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Export PR Progression button - Material 3 Expressive
                    Button(
                        onClick = {
                            scope.launch {
                                // Fetch exercise names
                                val exerciseNames = mutableMapOf<String, String>()
                                personalRecords.forEach { pr ->
                                    withContext(Dispatchers.IO) {
                                        try {
                                            val exercise = viewModel.exerciseRepository.getExerciseById(pr.exerciseId)
                                            exercise?.let { exerciseNames[pr.exerciseId] = it.name }
                                        } catch (e: Exception) {
                                            exerciseNames[pr.exerciseId] = "Unknown Exercise"
                                        }
                                    }
                                }

                                val result = CsvExporter.exportPRProgression(
                                    context,
                                    personalRecords,
                                    exerciseNames,
                                    weightUnit,
                                    viewModel::formatWeight
                                )

                                result.onSuccess { uri ->
                                    CsvExporter.shareCSV(context, uri, "pr_progression.csv")
                                    exportMessage = "PR progression exported successfully"
                                    showExportMenu = false
                                }.onFailure {
                                    exportMessage = "Failed to export PR progression"
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp), // Material 3 Expressive: Taller button
                        shape = RoundedCornerShape(20.dp), // Material 3 Expressive: More rounded
                        elevation = ButtonDefaults.buttonElevation(
                            defaultElevation = 4.dp,
                            pressedElevation = 2.dp
                        )
                    ) {
                        Icon(Icons.Default.Info, contentDescription = "Information", modifier = Modifier.size(24.dp)) // Material 3 Expressive: Larger icon
                        Spacer(modifier = Modifier.width(Spacing.small))
                        Text(
                            "Export PR Progression",
                            style = MaterialTheme.typography.titleLarge, // Material 3 Expressive: Larger text
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { showExportMenu = false },
                    modifier = Modifier.height(56.dp), // Material 3 Expressive: Taller button
                    shape = RoundedCornerShape(20.dp) // Material 3 Expressive: More rounded
                ) {
                    Text(
                        "Cancel",
                        style = MaterialTheme.typography.titleMedium, // Material 3 Expressive: Larger text
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest, // Material 3 Expressive: Higher contrast
            shape = RoundedCornerShape(28.dp) // Material 3 Expressive: Very rounded for dialogs
        )
    }

    // Export success/error message - Material 3 Expressive
    exportMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { exportMessage = null },
            title = { 
                Text(
                    "Export",
                    style = MaterialTheme.typography.headlineSmall, // Material 3 Expressive: Larger
                    fontWeight = FontWeight.Bold
                ) 
            },
            text = { 
                Text(
                    message,
                    style = MaterialTheme.typography.bodyLarge // Material 3 Expressive: Larger
                ) 
            },
            confirmButton = {
                Button(
                    onClick = { exportMessage = null },
                    modifier = Modifier.height(56.dp), // Material 3 Expressive: Taller button
                    shape = RoundedCornerShape(20.dp), // Material 3 Expressive: More rounded
                    elevation = ButtonDefaults.buttonElevation(
                        defaultElevation = 4.dp,
                        pressedElevation = 2.dp
                    )
                ) {
                    Text(
                        "OK",
                        style = MaterialTheme.typography.titleLarge, // Material 3 Expressive: Larger text
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest, // Material 3 Expressive: Higher contrast
            shape = RoundedCornerShape(28.dp) // Material 3 Expressive: Very rounded for dialogs
        )
    }
}

/**
 * Dashboard tab - shows key statistics, calendar heatmap, and muscle group visualization.
 */
@Composable
fun DashboardTab(
    viewModel: MainViewModel,
    personalRecords: List<com.example.vitruvianredux.domain.model.PersonalRecord>,
    workoutHistory: List<WorkoutSession>,
    weightUnit: WeightUnit,
    formatWeight: (Float, WeightUnit) -> String,
    modifier: Modifier = Modifier
) {
    val completedWorkouts by viewModel.completedWorkouts.collectAsState()
    val workoutStreak by viewModel.workoutStreak.collectAsState()
    val allWorkoutSessions by viewModel.allWorkoutSessions.collectAsState()

    // Calculate total volume (weight * reps)
    val totalVolume = remember(allWorkoutSessions) {
        allWorkoutSessions.sumOf { session ->
            (session.weightPerCableKg * session.totalReps * 2).toDouble() // *2 for both cables
        }.toFloat()
    }

    // Fetch exercise repository for muscle group calculations
    val exerciseRepository = viewModel.exerciseRepository

    // Calculate muscle group distribution
    val muscleGroupCounts = remember { mutableStateMapOf<String, Int>() }
    LaunchedEffect(personalRecords) {
        val counts = mutableMapOf<String, Int>()
        personalRecords.groupBy { it.exerciseId }.forEach { (exerciseId, _) ->
            val exercise = withContext(Dispatchers.IO) {
                try {
                    exerciseRepository.getExerciseById(exerciseId)
                } catch (e: Exception) {
                    null
                }
            }
            exercise?.muscleGroups?.split(",")?.forEach { group ->
                val trimmedGroup = group.trim()
                if (trimmedGroup.isNotBlank()) {
                    counts[trimmedGroup] = counts.getOrDefault(trimmedGroup, 0) + 1
                }
            }
        }
        muscleGroupCounts.clear()
        muscleGroupCounts.putAll(counts)
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(Spacing.medium),
        verticalArrangement = Arrangement.spacedBy(Spacing.medium)
    ) {
        item {
            Text(
                "Dashboard",
                style = MaterialTheme.typography.headlineLarge, // Material 3 Expressive: Larger (was headlineSmall)
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(Spacing.small))
        }

        // Key Statistics - 2x2 Grid
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.small)
            ) {
                StatCard(
                    label = "Total Workouts",
                    value = completedWorkouts.toString(),
                    icon = Icons.Default.FitnessCenter,
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    label = "Workout Streak",
                    value = "$workoutStreak days",
                    icon = Icons.Default.LocalFireDepartment,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.small)
            ) {
                StatCard(
                    label = "Total Volume",
                    value = formatWeight(totalVolume, weightUnit),
                    icon = Icons.Default.TrendingUp,
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    label = "Total Sets",
                    value = allWorkoutSessions.size.toString(),
                    icon = Icons.Default.FitnessCenter,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Calendar Heatmap
        item {
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
                        Icon(
                            Icons.Default.CalendarToday,
                            contentDescription = "Workout consistency calendar",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp) // Material 3 Expressive: Larger icon (was 24dp)
                        )
                        Spacer(modifier = Modifier.width(Spacing.small))
                        Text(
                            "Workout Consistency",
                            style = MaterialTheme.typography.titleLarge, // Material 3 Expressive: Larger (was titleMedium)
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Spacer(modifier = Modifier.height(Spacing.medium))
                    WorkoutCalendarHeatmap(
                        workoutSessions = allWorkoutSessions,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        // Muscle Group Distribution - Material 3 Expressive
        if (personalRecords.isNotEmpty() && muscleGroupCounts.isNotEmpty()) {
            item {
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
                            Icon(
                                Icons.Default.Accessibility,
                                contentDescription = "Muscle group distribution",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(28.dp) // Material 3 Expressive: Larger icon (was 24dp)
                            )
                            Spacer(modifier = Modifier.width(Spacing.small))
                            Text(
                                "Muscle Group Distribution",
                                style = MaterialTheme.typography.titleLarge, // Material 3 Expressive: Larger (was titleMedium)
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Spacer(modifier = Modifier.height(Spacing.small))
                        // Use Material 3 Expressive Circle Chart instead of MPAndroidChart
                        if (muscleGroupCounts.isNotEmpty()) {
                            MuscleGroupCircleChart(
                                data = muscleGroupCounts.map { (group, count) ->
                                    group to count.toFloat()
                                },
                                modifier = Modifier.fillMaxWidth(),
                                onSegmentClick = { group, value ->
                                    // Optional: Handle segment click for drill-down
                                }
                            )
                        } else {
                            // Fallback to MPAndroidChart if needed for compatibility
                            MuscleGroupDistributionChart(
                                muscleGroupCounts = muscleGroupCounts,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }

        // Total Volume Over Time Chart - Material 3 Expressive
        if (allWorkoutSessions.isNotEmpty()) {
            item {
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
                            Icon(
                                Icons.Default.BarChart,
                                contentDescription = "Total volume over time",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(28.dp) // Material 3 Expressive: Larger icon (was 24dp)
                            )
                            Spacer(modifier = Modifier.width(Spacing.small))
                            Text(
                                "Volume Over Time",
                                style = MaterialTheme.typography.titleLarge, // Material 3 Expressive: Larger (was titleMedium)
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Spacer(modifier = Modifier.height(Spacing.small))
                        TotalVolumeChart(
                            workoutSessions = allWorkoutSessions,
                            weightUnit = weightUnit,
                            formatWeight = formatWeight,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }

        // Workout Mode Distribution Chart - Material 3 Expressive
        if (personalRecords.isNotEmpty()) {
            item {
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
                            Icon(
                                Icons.Default.Equalizer,
                                contentDescription = "Workout mode distribution",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(28.dp) // Material 3 Expressive: Larger icon (was 24dp)
                            )
                            Spacer(modifier = Modifier.width(Spacing.small))
                            Text(
                                "Workout Mode Distribution",
                                style = MaterialTheme.typography.titleLarge, // Material 3 Expressive: Larger (was titleMedium)
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Spacer(modifier = Modifier.height(Spacing.small))
                        WorkoutModeDistributionChart(
                            personalRecords = personalRecords,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}

/**
 * Stat card for the dashboard.
 */
@Composable
fun StatCard(
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .shadow(8.dp, RoundedCornerShape(20.dp)), // Material 3 Expressive: More shadow, more rounded
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest), // Material 3 Expressive: Higher contrast
        shape = RoundedCornerShape(20.dp), // Material 3 Expressive: More rounded (was 16dp)
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp), // Material 3 Expressive: Higher elevation (was 4dp)
        border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)) // Material 3 Expressive: Thicker border (was 1dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.medium),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                icon,
                contentDescription = "Personal record",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(40.dp) // Material 3 Expressive: Larger icon (was 32dp)
            )
            Spacer(modifier = Modifier.height(Spacing.small))
            Text(
                value,
                style = MaterialTheme.typography.headlineLarge, // Material 3 Expressive: Larger (was headlineMedium)
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium, // Material 3 Expressive: Larger (was bodySmall)
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

/**
 * Card showing a personal best record.
 */
@Composable
fun PersonalRecordCard(
    rank: Int,
    exerciseName: String,
    pr: com.example.vitruvianredux.domain.model.PersonalRecord,
    weightUnit: WeightUnit,
    formatWeight: (Float, WeightUnit) -> String
) {
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
        onClick = { isPressed = true },
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .shadow(8.dp, RoundedCornerShape(20.dp)), // Material 3 Expressive: More shadow, more rounded
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest // Material 3 Expressive: Higher contrast
        ),
        shape = RoundedCornerShape(20.dp), // Material 3 Expressive: More rounded (was 16dp)
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp), // Material 3 Expressive: Higher elevation (was 4dp)
        border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)) // Material 3 Expressive: Thicker border (was 1dp)
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
                // Rank badge - Material 3 Expressive
                Surface(
                    color = when (rank) {
                        1 -> MaterialTheme.colorScheme.tertiary
                        2, 3 -> MaterialTheme.colorScheme.secondary
                        else -> MaterialTheme.colorScheme.primaryContainer
                    },
                    shape = RoundedCornerShape(12.dp) // Material 3 Expressive: More rounded (was 8dp)
                ) {
                    Text(
                        "#$rank",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), // Material 3 Expressive: More padding
                        style = MaterialTheme.typography.labelLarge, // Material 3 Expressive: Larger (was labelMedium)
                        fontWeight = FontWeight.Bold,
                        color = when (rank) {
                            1 -> MaterialTheme.colorScheme.onTertiary
                            2, 3 -> MaterialTheme.colorScheme.onSecondary
                            else -> MaterialTheme.colorScheme.onPrimaryContainer
                        }
                    )
                }

                Spacer(modifier = Modifier.width(Spacing.medium))

                Column {
                    Text(
                        exerciseName,
                        style = MaterialTheme.typography.titleLarge, // Material 3 Expressive: Larger (was titleMedium)
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "${formatWeight(pr.weightPerCableKg, weightUnit)} per cable",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium, // Material 3 Expressive: Bolder
                        color = MaterialTheme.colorScheme.primary
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(Spacing.extraSmall)
                    ) {
                        Text(
                            "${pr.reps} reps",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text("•", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            pr.workoutMode,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        Text("•", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            java.text.SimpleDateFormat("MMM d, yyyy", java.util.Locale.getDefault()).format(pr.timestamp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (rank == 1) {
                Icon(
                    Icons.Default.Star,
                    contentDescription = "Top record",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(40.dp) // Material 3 Expressive: Larger icon (was 32dp)
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
 * Progression tab - shows PR progression for each exercise.
 */
@Composable
fun ProgressionTab(
    personalRecords: List<com.example.vitruvianredux.domain.model.PersonalRecord>,
    exerciseRepository: com.example.vitruvianredux.data.repository.ExerciseRepository,
    weightUnit: WeightUnit,
    formatWeight: (Float, WeightUnit) -> String,
    modifier: Modifier = Modifier
) {
    // Group PRs by exercise to show progression
    val prsByExercise = remember(personalRecords) {
        personalRecords.groupBy { it.exerciseId }
            .mapValues { (_, prs) -> prs.sortedByDescending { it.timestamp } }
            .filter { it.value.isNotEmpty() }
    }

    // Fetch exercise names for display
    val exerciseNames = remember { mutableStateMapOf<String, String>() }
    LaunchedEffect(prsByExercise) {
        prsByExercise.keys.forEach { exerciseId ->
            if (!exerciseNames.contains(exerciseId)) {
                try {
                    val exercise = exerciseRepository.getExerciseById(exerciseId)
                    exerciseNames[exerciseId] = exercise?.name ?: "Unknown Exercise"
                } catch (e: Exception) {
                    exerciseNames[exerciseId] = "Unknown Exercise"
                }
            }
        }
    }


    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(Spacing.medium),
        verticalArrangement = Arrangement.spacedBy(Spacing.medium)
    ) {
        item {
            Text(
                "Personal Records",
                style = MaterialTheme.typography.headlineLarge, // Material 3 Expressive: Larger (was headlineSmall)
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(Spacing.small))
        }

        // Personal Records List (ranked by weight)
        if (prsByExercise.isNotEmpty()) {
            // Get ranked PRs (highest weight per exercise)
            val rankedPrs = prsByExercise.toList()
                .sortedByDescending { (_, prs) -> prs.maxOf { it.weightPerCableKg } }

            items(rankedPrs.size) { index ->
                val (exerciseId, prs) = rankedPrs[index]
                val bestPr = prs.maxWith(compareBy({ it.weightPerCableKg }, { it.reps }))
                val exerciseName = exerciseNames[exerciseId] ?: "Loading..."
                PersonalRecordCard(
                    rank = index + 1,
                    exerciseName = exerciseName,
                    pr = bestPr,
                    weightUnit = weightUnit,
                    formatWeight = formatWeight
                )
            }

            item {
                Spacer(modifier = Modifier.height(Spacing.medium))
                Text(
                    "Progression Details",
                    style = MaterialTheme.typography.headlineMedium, // Material 3 Expressive: Larger (was titleLarge)
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(Spacing.small))
            }
        }

        if (prsByExercise.isEmpty()) {
            item {
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
                            .padding(Spacing.large),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = "No PR history available",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(56.dp) // Material 3 Expressive: Larger icon (was 48dp)
                        )
                        Spacer(modifier = Modifier.height(Spacing.small))
                        Text(
                            "No PR history yet",
                            style = MaterialTheme.typography.titleLarge, // Material 3 Expressive: Larger (was titleMedium)
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Complete workouts to track your progress over time",
                            style = MaterialTheme.typography.bodyMedium, // Material 3 Expressive: Larger (was bodySmall)
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            // Show PR progression by exercise
            prsByExercise.forEach { (exerciseId, prs) ->
                item {
                    ExerciseProgressionCard(
                        exerciseName = exerciseNames[exerciseId] ?: "Loading...",
                        prs = prs,
                        weightUnit = weightUnit,
                        formatWeight = formatWeight
                    )
                }
            }
        }
    }
}

/**
 * Card showing PR progression for a specific exercise
 */
@Composable
fun ExerciseProgressionCard(
    exerciseName: String,
    prs: List<com.example.vitruvianredux.domain.model.PersonalRecord>,
    weightUnit: WeightUnit,
    formatWeight: (Float, WeightUnit) -> String
) {
    var showChart by remember { mutableStateOf(false) }

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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    exerciseName,
                    style = MaterialTheme.typography.titleLarge, // Material 3 Expressive: Larger (was titleMedium)
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                // Toggle button for chart view - Material 3 Expressive
                if (prs.size >= 2) {
                    IconButton(
                        onClick = { showChart = !showChart },
                        modifier = Modifier.size(48.dp) // Material 3 Expressive: Larger button (was default)
                    ) {
                        Icon(
                            imageVector = if (showChart) Icons.AutoMirrored.Filled.List else Icons.Default.Info,
                            contentDescription = if (showChart) "Show list" else "Show chart",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp) // Material 3 Expressive: Larger icon (was default)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(Spacing.small))

            // Show chart or timeline based on toggle
            if (showChart && prs.size >= 2) {
                WeightProgressionChart(
                    prs = prs,
                    weightUnit = weightUnit,
                    formatWeight = formatWeight,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(Spacing.medium))
            }

            // Show progression timeline
            prs.forEachIndexed { index, pr ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = Spacing.extraSmall),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Timeline indicator - Material 3 Expressive
                    Surface(
                        color = if (index == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                        shape = RoundedCornerShape(6.dp), // Material 3 Expressive: More rounded (was 4dp)
                        modifier = Modifier.size(12.dp) // Material 3 Expressive: Larger indicator (was 8dp)
                    ) {}

                    Spacer(modifier = Modifier.width(Spacing.small))

                    // PR details - Material 3 Expressive
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "${formatWeight(pr.weightPerCableKg, weightUnit)}/cable",
                            style = MaterialTheme.typography.bodyLarge, // Material 3 Expressive: Larger (was bodyMedium)
                            fontWeight = if (index == 0) FontWeight.Bold else FontWeight.Medium, // Material 3 Expressive: Bolder
                            color = if (index == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                        Row {
                            Text(
                                "${pr.reps} reps • ${pr.workoutMode}",
                                style = MaterialTheme.typography.bodyMedium, // Material 3 Expressive: Larger (was bodySmall)
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Date - Material 3 Expressive
                    Text(
                        java.text.SimpleDateFormat("MMM d", java.util.Locale.getDefault()).format(pr.timestamp),
                        style = MaterialTheme.typography.bodyMedium, // Material 3 Expressive: Larger (was bodySmall)
                        fontWeight = FontWeight.Medium, // Material 3 Expressive: Bolder
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Show improvement arrow if not the oldest PR
                if (index < prs.size - 1) {
                    val currentWeight = pr.weightPerCableKg
                    val previousWeight = prs[index + 1].weightPerCableKg
                    val improvement = ((currentWeight - previousWeight) / previousWeight * 100).toInt()

                    if (improvement > 0) {
                        Row(
                            modifier = Modifier.padding(start = 18.dp, top = 2.dp, bottom = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.KeyboardArrowUp,
                                contentDescription = "Improvement",
                                tint = MaterialTheme.colorScheme.primary, // Material 3 Expressive: Use theme color
                                modifier = Modifier.size(20.dp) // Material 3 Expressive: Larger icon (was 16dp)
                            )
                            Text(
                                "+$improvement%",
                                style = MaterialTheme.typography.bodyMedium, // Material 3 Expressive: Larger (was bodySmall)
                                color = MaterialTheme.colorScheme.primary, // Material 3 Expressive: Use theme color
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
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
            contentDescription = "Exercise progression",
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

/**
 * Calendar heatmap showing workout consistency over the last 12 weeks
 */
@Composable
fun WorkoutCalendarHeatmap(
    workoutSessions: List<WorkoutSession>,
    modifier: Modifier = Modifier
) {
    // Group workouts by date
    val workoutsByDate = remember(workoutSessions) {
        workoutSessions.groupBy { session ->
            val instant = java.time.Instant.ofEpochMilli(session.timestamp)
            val date = instant.atZone(java.time.ZoneId.systemDefault()).toLocalDate()
            date
        }.mapValues { it.value.size }
    }

    // Calculate max workouts per day for color intensity
    val maxWorkoutsPerDay = workoutsByDate.values.maxOrNull() ?: 1

    // Get the last 12 weeks of dates
    val today = java.time.LocalDate.now()
    val startDate = today.minusWeeks(11).with(java.time.DayOfWeek.SUNDAY)

    // Group dates by week
    val weeks = (0..11).map { weekOffset ->
        val weekStart = startDate.plusWeeks(weekOffset.toLong())
        (0..6).map { dayOffset ->
            weekStart.plusDays(dayOffset.toLong())
        }
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // Legend
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Less",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(4.dp))
            // Show 5 levels of intensity
            (0..4).forEach { level ->
                val color = when (level) {
                    0 -> MaterialTheme.colorScheme.surfaceVariant
                    1 -> MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                    2 -> MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                    3 -> MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                    else -> MaterialTheme.colorScheme.primary
                }
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .background(color, RoundedCornerShape(2.dp))
                )
                if (level < 4) Spacer(modifier = Modifier.width(2.dp))
            }
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                "More",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Calendar grid
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            weeks.forEach { week ->
                Column(
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    week.forEach { date ->
                        val workoutCount = workoutsByDate[date] ?: 0
                        val color = if (workoutCount == 0) {
                            MaterialTheme.colorScheme.surfaceVariant
                        } else {
                            val intensity = (workoutCount.toFloat() / maxWorkoutsPerDay.toFloat()).coerceIn(0f, 1f)
                            when {
                                intensity <= 0.25f -> MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                                intensity <= 0.5f -> MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                                intensity <= 0.75f -> MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                                else -> MaterialTheme.colorScheme.primary
                            }
                        }

                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .background(color, RoundedCornerShape(2.dp))
                        )
                    }
                }
            }
        }

        // Current streak info
        Spacer(modifier = Modifier.height(8.dp))
        val streak = calculateCurrentStreak(workoutsByDate)
        if (streak > 0) {
            Text(
                "🔥 $streak day streak!",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/**
 * Calculate current workout streak in days
 */
private fun calculateCurrentStreak(workoutsByDate: Map<java.time.LocalDate, Int>): Int {
    val today = java.time.LocalDate.now()
    var streak = 0
    var currentDate = today

    while (workoutsByDate.containsKey(currentDate)) {
        streak++
        currentDate = currentDate.minusDays(1)
    }

    return streak
}
