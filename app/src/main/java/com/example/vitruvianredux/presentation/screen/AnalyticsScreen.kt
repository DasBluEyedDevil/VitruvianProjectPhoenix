package com.example.vitruvianredux.presentation.screen

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import com.example.vitruvianredux.presentation.components.WorkoutModeDistributionChart
import com.example.vitruvianredux.util.CsvExporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.LocalContext

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
            // Tab Row with gradient indicator and swipe support
            TabRow(
                selectedTabIndex = pagerState.currentPage,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary,
                indicator = { tabPositions ->
                    val currentTab = tabPositions.getOrNull(pagerState.currentPage)
                    if (currentTab != null) {
                        Box(
                            modifier = Modifier
                                .tabIndicatorOffset(currentTab)
                                .height(6.dp)
                                .shadow(2.dp, RoundedCornerShape(3.dp))
                                .background(
                                    Brush.linearGradient(
                                        colors = listOf(Color(0xFF9333EA), Color(0xFF7E22CE))
                                    ),
                                    RoundedCornerShape(3.dp)
                                )
                        )
                    }
                }
            ) {
                Tab(
                    selected = pagerState.currentPage == 0,
                    onClick = { scope.launch { pagerState.animateScrollToPage(0) } },
                    text = { Text("History") },
                    icon = { Icon(Icons.Default.List, contentDescription = "Workout history") }
                )
                Tab(
                    selected = pagerState.currentPage == 1,
                    onClick = { scope.launch { pagerState.animateScrollToPage(1) } },
                    text = { Text("Personal Bests") },
                    icon = { Icon(Icons.Default.Star, contentDescription = "Personal records") }
                )
                Tab(
                    selected = pagerState.currentPage == 2,
                    onClick = { scope.launch { pagerState.animateScrollToPage(2) } },
                    text = { Text("Trends") },
                    icon = { Icon(Icons.Default.Info, contentDescription = "Workout trends") }
                )
            }

            // Tab Content with Swipe Support
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                when (page) {
                    0 -> HistoryTab(
                        workoutHistory = workoutHistory,
                        weightUnit = weightUnit,
                        formatWeight = viewModel::formatWeight,
                        onDeleteWorkout = { viewModel.deleteWorkout(it) },
                        onRefresh = { /* Workout history refreshes automatically via StateFlow */ },
                        modifier = Modifier.fillMaxSize()
                    )
                    1 -> PersonalBestsTab(
                        personalRecords = personalRecords,
                        exerciseRepository = viewModel.exerciseRepository,
                        weightUnit = weightUnit,
                        formatWeight = viewModel::formatWeight,
                        modifier = Modifier.fillMaxSize()
                    )
                    2 -> TrendsTab(
                        personalRecords = personalRecords,
                        exerciseRepository = viewModel.exerciseRepository,
                        weightUnit = weightUnit,
                        formatWeight = viewModel::formatWeight,
                        modifier = Modifier.fillMaxSize()
                    )
                }
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

        // Export FAB
        FloatingActionButton(
            onClick = { showExportMenu = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(Spacing.large),
            containerColor = MaterialTheme.colorScheme.primary
        ) {
            Icon(Icons.Default.Share, contentDescription = "Export data")
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

                    // Export Personal Records button
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
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Star, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(Spacing.small))
                        Text("Export Personal Records")
                    }

                    // Export Workout History button
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
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.List, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(Spacing.small))
                        Text("Export Workout History")
                    }

                    // Export PR Progression button
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
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(Spacing.small))
                        Text("Export PR Progression")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showExportMenu = false }) {
                    Text("Cancel")
                }
            },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = MaterialTheme.shapes.medium
        )
    }

    // Export success/error message
    exportMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { exportMessage = null },
            title = { Text("Export") },
            text = { Text(message) },
            confirmButton = {
                Button(onClick = { exportMessage = null }) {
                    Text("OK")
                }
            },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = MaterialTheme.shapes.medium
        )
    }
}

/**
 * Personal Bests tab - shows maximum weight lifted for each exercise.
 */
@Composable
fun PersonalBestsTab(
    personalRecords: List<com.example.vitruvianredux.domain.model.PersonalRecord>,
    exerciseRepository: com.example.vitruvianredux.data.repository.ExerciseRepository,
    weightUnit: WeightUnit,
    formatWeight: (Float, WeightUnit) -> String,
    modifier: Modifier = Modifier
) {
    // Group PRs by exercise and get exercise names
    val prsByExercise = remember(personalRecords) {
        personalRecords.groupBy { it.exerciseId }
            .mapValues { (_, prs) ->
                // Get the best PR for this exercise (highest weight, then highest reps)
                prs.maxWith(compareBy({ it.weightPerCableKg }, { it.reps }))
            }
            .toList()
            .sortedByDescending { (_, pr) -> pr.weightPerCableKg }
    }

    // Fetch exercise names for display
    val exerciseNames = remember { mutableStateMapOf<String, String>() }
    LaunchedEffect(prsByExercise) {
        prsByExercise.forEach { (exerciseId, _) ->
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

    // Calculate muscle group distribution
    val muscleGroupCounts = remember { mutableStateMapOf<String, Int>() }
    LaunchedEffect(prsByExercise) {
        val counts = mutableMapOf<String, Int>()
        prsByExercise.forEach { (exerciseId, _) ->
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

        // Muscle Group Distribution Chart
        if (prsByExercise.isNotEmpty() && muscleGroupCounts.isNotEmpty()) {
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
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Star,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(Spacing.small))
                            Text(
                                "Muscle Group Distribution",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Spacer(modifier = Modifier.height(Spacing.small))
                        MuscleGroupDistributionChart(
                            muscleGroupCounts = muscleGroupCounts,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            // Workout Mode Distribution Chart
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
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(Spacing.small))
                            Text(
                                "PRs by Workout Mode",
                                style = MaterialTheme.typography.titleMedium,
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

        if (prsByExercise.isEmpty()) {
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
            items(prsByExercise.size) { index ->
                val (exerciseId, pr) = prsByExercise[index]
                val exerciseName = exerciseNames[exerciseId] ?: "Loading..."
                PersonalRecordCard(
                    rank = index + 1,
                    exerciseName = exerciseName,
                    pr = pr,
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
fun PersonalRecordCard(
    rank: Int,
    exerciseName: String,
    pr: com.example.vitruvianredux.domain.model.PersonalRecord,
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
                        "${formatWeight(pr.weightPerCableKg, weightUnit)} per cable",
                        style = MaterialTheme.typography.bodyLarge,
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
        modifier = modifier.padding(Spacing.medium),
        verticalArrangement = Arrangement.spacedBy(Spacing.medium)
    ) {
        item {
            Text(
                "PR Progression",
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
                        "Overall Stats",
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
                            label = "Total PRs",
                            value = personalRecords.size.toString(),
                            icon = Icons.Default.Star
                        )
                        StatItem(
                            label = "Exercises",
                            value = prsByExercise.size.toString(),
                            icon = Icons.Default.Check
                        )
                        StatItem(
                            label = "Max Per Cable",
                            value = formatWeight(
                                personalRecords.maxOfOrNull { it.weightPerCableKg } ?: 0f,
                                weightUnit
                            ),
                            icon = Icons.Default.Star
                        )
                    }
                }
            }
        }

        if (prsByExercise.isEmpty()) {
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
                            "No PR history yet",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Complete workouts to track your progress over time",
                            style = MaterialTheme.typography.bodySmall,
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    exerciseName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                // Toggle button for chart view
                if (prs.size >= 2) {
                    IconButton(onClick = { showChart = !showChart }) {
                        Icon(
                            if (showChart) Icons.Default.List else Icons.Default.Info,
                            contentDescription = if (showChart) "Show list" else "Show chart",
                            tint = MaterialTheme.colorScheme.primary
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
                    // Timeline indicator
                    Surface(
                        color = if (index == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier.size(8.dp)
                    ) {}

                    Spacer(modifier = Modifier.width(Spacing.small))

                    // PR details
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "${formatWeight(pr.weightPerCableKg, weightUnit)}/cable",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (index == 0) FontWeight.Bold else FontWeight.Normal,
                            color = if (index == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                        Row {
                            Text(
                                "${pr.reps} reps • ${pr.workoutMode}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Date
                    Text(
                        java.text.SimpleDateFormat("MMM d", java.util.Locale.getDefault()).format(pr.timestamp),
                        style = MaterialTheme.typography.bodySmall,
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
                                tint = Color(0xFF10B981),
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                "+$improvement%",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF10B981),
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
