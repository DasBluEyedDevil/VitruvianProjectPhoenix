package com.example.vitruvianredux.presentation.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.vitruvianredux.presentation.components.EmptyState
import com.example.vitruvianredux.presentation.navigation.NavigationRoutes
import com.example.vitruvianredux.presentation.viewmodel.MainViewModel
import com.example.vitruvianredux.ui.theme.Spacing
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.*

/**
 * Weekly Programs screen - view and manage weekly workout programs.
 * Shows active program, today's workout, and list of all programs.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeeklyProgramsScreen(
    navController: NavController,
    viewModel: MainViewModel,
    themeMode: com.example.vitruvianredux.ui.theme.ThemeMode
) {
    // Get programs from ViewModel's database StateFlows
    val programs by viewModel.weeklyPrograms.collectAsState()
    val activeProgram by viewModel.activeProgram.collectAsState()

    val isAutoConnecting by viewModel.isAutoConnecting.collectAsState()
    val connectionError by viewModel.connectionError.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Weekly Programs") },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                    actionIconContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { padding ->
        // Determine actual theme (matching Theme.kt logic)
        val useDarkColors = when (themeMode) {
            com.example.vitruvianredux.ui.theme.ThemeMode.SYSTEM -> isSystemInDarkTheme()
            com.example.vitruvianredux.ui.theme.ThemeMode.LIGHT -> false
            com.example.vitruvianredux.ui.theme.ThemeMode.DARK -> true
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
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(Spacing.medium),
                verticalArrangement = Arrangement.spacedBy(Spacing.medium)
            ) {
            // Active Program Card
            if (activeProgram != null) {
                item {
                    val today = java.time.LocalDate.now().dayOfWeek
                    val todayDayValue = today.value
                    val todayRoutineId = activeProgram!!.days.find { it.dayOfWeek == todayDayValue }?.routineId

                    ActiveProgramCard(
                        program = activeProgram!!,
                        onStartTodayWorkout = {
                            todayRoutineId?.let { routineId ->
                                viewModel.ensureConnection(
                                    onConnected = {
                                        viewModel.loadRoutineById(routineId)
                                        viewModel.startWorkout()
                                    },
                                    onFailed = { /* Error shown via StateFlow */ }
                                )
                            }
                        },
                        onViewProgram = {
                            navController.navigate(
                                NavigationRoutes.ProgramBuilder.createRoute(activeProgram!!.program.id)
                            )
                        }
                    )
                }
            } else {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(16.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF5F3FF))
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
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(Spacing.small))
                            Text(
                                "No active program",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "Create a program or activate an existing one",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Programs List Header
            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(Spacing.medium)
                ) {
                    Text(
                        "All Programs",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    OutlinedButton(
                        onClick = {
                            navController.navigate(NavigationRoutes.ProgramBuilder.createRoute())
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Create program", modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(Spacing.small))
                        Text("Create Program")
                    }
                }
            }

            // Programs List
            if (programs.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Default.DateRange,
                        title = "No Programs Yet",
                        message = "Create your first weekly program to follow a structured training schedule",
                        actionText = "Create Your First Program",
                        onAction = {
                            navController.navigate(NavigationRoutes.ProgramBuilder.createRoute())
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            } else {
                items(programs) { program ->
                    ProgramListItem(
                        program = program,
                        isActive = program.program.id == activeProgram?.program?.id,
                        onClick = {
                            navController.navigate(
                                NavigationRoutes.ProgramBuilder.createRoute(program.program.id)
                            )
                        },
                        onActivate = {
                            viewModel.activateProgram(program.program.id)
                        },
                        onDelete = {
                            viewModel.deleteProgram(program.program.id)
                        }
                    )
                }
            }
            }

            // Auto-connect UI overlays
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
}

/**
 * Card showing the active program with today's workout.
 */
@Composable
fun ActiveProgramCard(
    program: com.example.vitruvianredux.data.local.WeeklyProgramWithDays,
    onStartTodayWorkout: () -> Unit,
    onViewProgram: () -> Unit
) {
    val today = LocalDate.now().dayOfWeek
    // Use Java DayOfWeek.value directly (MONDAY=1, TUESDAY=2, ..., SUNDAY=7)
    // This matches what ProgramBuilder saves: day.value
    val todayDayValue = today.value

    // Find today's routine ID from program days
    val todayRoutineId = program.days.find { it.dayOfWeek == todayDayValue }?.routineId
    val hasWorkoutToday = todayRoutineId != null

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF5F3FF))
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
                Column {
                    Text(
                        "Active Program",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    )
                    Text(
                        program.program.title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                IconButton(onClick = onViewProgram) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = "View program",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            Spacer(modifier = Modifier.height(Spacing.medium))

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            Spacer(modifier = Modifier.height(Spacing.medium))

            Text(
                "Today: ${today.getDisplayName(TextStyle.FULL, Locale.getDefault())}",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(Spacing.small))

            if (hasWorkoutToday) {
                Text(
                    "Workout scheduled",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(Spacing.medium))

                Button(
                    onClick = onStartTodayWorkout,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(modifier = Modifier.width(Spacing.small))
                    Text("Start Today's Workout")
                }
            } else {
                Text(
                    "Rest day",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * List item for a program.
 */
@Composable
fun ProgramListItem(
    program: com.example.vitruvianredux.data.local.WeeklyProgramWithDays,
    isActive: Boolean,
    onClick: () -> Unit,
    onActivate: () -> Unit,
    onDelete: () -> Unit
) {
    var showDeleteDialog by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF5F3FF))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.medium),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    program.program.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "${program.days.size} workout days",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.small),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Delete button
                IconButton(onClick = { showDeleteDialog = true }) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete program",
                        tint = MaterialTheme.colorScheme.error
                    )
                }

                // Activate/Active status
                if (!isActive) {
                    TextButton(onClick = onActivate) {
                        Text("Activate")
                    }
                } else {
                    Surface(
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            "Active",
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            }
        }
    }

    // Delete confirmation dialog
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Program") },
            text = { Text("Are you sure you want to delete \"${program.program.title}\"? This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete()
                        showDeleteDialog = false
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Delete")
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

/**
 * DEPRECATED: This mock data class is no longer used.
 * Use WeeklyProgramWithDays from data.local instead.
 */
@Deprecated(
    message = "Use WeeklyProgramWithDays from data.local package",
    replaceWith = ReplaceWith("com.example.vitruvianredux.data.local.WeeklyProgramWithDays")
)
data class WeeklyProgram(
    val id: String,
    val name: String,
    val dailyRoutines: Map<DayOfWeek, com.example.vitruvianredux.domain.model.Routine?>
)
