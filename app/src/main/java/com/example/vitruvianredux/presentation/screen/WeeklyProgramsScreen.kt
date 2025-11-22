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
    val routines by viewModel.routines.collectAsState()

    val isAutoConnecting by viewModel.isAutoConnecting.collectAsState()
    val connectionError by viewModel.connectionError.collectAsState()


    // Set global title
    LaunchedEffect(Unit) {
        viewModel.updateTopBarTitle("Weekly Programs")
    }

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
                Color(0xFFE0E7FF), // soft indigo
                Color(0xFFEDE9FE), // soft violet
                Color(0xFFDFF6FF)  // soft sky blue
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
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest), // Material 3 Expressive: Higher contrast
                        shape = RoundedCornerShape(20.dp), // Material 3 Expressive: More rounded (was 16dp)
                        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp), // Material 3 Expressive: Higher elevation (was 4dp)
                        border = androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)) // Material 3 Expressive: Thicker border (was 1dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(Spacing.large),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                Icons.Default.Info,
                                contentDescription = "No programs available",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(56.dp) // Material 3 Expressive: Larger icon (was 48dp)
                            )
                            Spacer(modifier = Modifier.height(Spacing.small))
                            Text(
                                "No active program",
                                style = MaterialTheme.typography.titleLarge, // Material 3 Expressive: Larger (was titleMedium)
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "Create a program or activate an existing one",
                                style = MaterialTheme.typography.bodyMedium, // Material 3 Expressive: Larger (was bodySmall)
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
                        style = MaterialTheme.typography.titleLarge, // Material 3 Expressive: Larger (was titleMedium)
                        fontWeight = FontWeight.Bold
                    )
                    OutlinedButton(
                        onClick = {
                            navController.navigate(NavigationRoutes.ProgramBuilder.createRoute())
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp), // Material 3 Expressive: Taller button
                        shape = RoundedCornerShape(20.dp) // Material 3 Expressive: More rounded (was 16dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Create program", modifier = Modifier.size(24.dp)) // Material 3 Expressive: Larger icon
                        Spacer(modifier = Modifier.width(Spacing.small))
                        Text(
                            "Create Program",
                            style = MaterialTheme.typography.titleLarge, // Material 3 Expressive: Larger text
                            fontWeight = FontWeight.Bold
                        )
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
                        routineNameLookup = { routineId ->
                            routines.find { it.id == routineId }?.name ?: "Unknown Routine"
                        },
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
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer), // Material 3 Expressive: Use primary container for emphasis
        shape = RoundedCornerShape(20.dp), // Material 3 Expressive: More rounded (was 16dp)
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp), // Material 3 Expressive: Higher elevation (was 4dp)
        border = androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)) // Material 3 Expressive: Thicker border (was 1dp)
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
                        style = MaterialTheme.typography.labelLarge, // Material 3 Expressive: Larger (was labelMedium)
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    )
                    Text(
                        program.program.title,
                        style = MaterialTheme.typography.headlineSmall, // Material 3 Expressive: Larger (was titleLarge)
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp), // Material 3 Expressive: Taller button
                    shape = RoundedCornerShape(20.dp), // Material 3 Expressive: More rounded (was 16dp)
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    ),
                    elevation = ButtonDefaults.buttonElevation(
                        defaultElevation = 4.dp,
                        pressedElevation = 2.dp
                    )
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = "Start program", modifier = Modifier.size(24.dp)) // Material 3 Expressive: Larger icon
                    Spacer(modifier = Modifier.width(Spacing.small))
                    Text(
                        "Start Today's Workout",
                        style = MaterialTheme.typography.titleLarge, // Material 3 Expressive: Larger text
                        fontWeight = FontWeight.Bold
                    )
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
/**
 * List item for a program.
 */
@Composable
fun ProgramListItem(
    program: com.example.vitruvianredux.data.local.WeeklyProgramWithDays,
    isActive: Boolean,
    routineNameLookup: (String) -> String,
    onClick: () -> Unit,
    onActivate: () -> Unit,
    onDelete: () -> Unit
) {
    var showDeleteDialog by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = { expanded = !expanded }),
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) {
                MaterialTheme.colorScheme.primaryContainer // Material 3 Expressive: Use primary container for active
            } else {
                MaterialTheme.colorScheme.surfaceContainerHighest // Material 3 Expressive: Higher contrast
            }
        ),
        shape = RoundedCornerShape(20.dp), // Material 3 Expressive: More rounded (was 16dp)
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp), // Material 3 Expressive: Higher elevation (was 4dp)
        border = androidx.compose.foundation.BorderStroke(
            2.dp,
            if (isActive) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
            } else {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
            }
        ) // Material 3 Expressive: Thicker border (was 1dp)
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
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        program.program.title,
                        style = MaterialTheme.typography.titleLarge, // Material 3 Expressive: Larger (was titleMedium)
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "${program.days.size} workout days",
                        style = MaterialTheme.typography.bodyMedium, // Material 3 Expressive: Larger (was bodySmall)
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.small),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Edit button
                    IconButton(onClick = onClick) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = "Edit program",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    // Activate/Active status - Material 3 Expressive
                    if (!isActive) {
                        TextButton(
                            onClick = onActivate,
                            modifier = Modifier.height(48.dp), // Material 3 Expressive: Taller button
                            shape = RoundedCornerShape(20.dp) // Material 3 Expressive: More rounded (was 16dp)
                        ) {
                            Text(
                                "Activate",
                                style = MaterialTheme.typography.titleMedium, // Material 3 Expressive: Larger text
                                fontWeight = FontWeight.Bold
                            )
                        }
                    } else {
                        Surface(
                            color = MaterialTheme.colorScheme.primary,
                            shape = RoundedCornerShape(12.dp) // Material 3 Expressive: More rounded (was 8dp)
                        ) {
                            Text(
                                "Active",
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), // Material 3 Expressive: More padding
                                style = MaterialTheme.typography.labelLarge, // Material 3 Expressive: Larger (was labelMedium)
                                fontWeight = FontWeight.Bold, // Material 3 Expressive: Bolder
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }
                    
                    // Expand icon
                    Icon(
                        if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = if (expanded) "Collapse" else "Expand",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            // Expanded Content: Schedule
            androidx.compose.animation.AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = Spacing.medium)
                ) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(modifier = Modifier.height(Spacing.medium))
                    
                    Text(
                        "Schedule",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(Spacing.small))
                    
                    val sortedDays = program.days.sortedBy { it.dayOfWeek }
                    if (sortedDays.isEmpty()) {
                        Text(
                            "No workouts scheduled",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                        )
                    } else {
                        sortedDays.forEach { day ->
                            val dayName = DayOfWeek.of(day.dayOfWeek).getDisplayName(TextStyle.SHORT, Locale.getDefault())
                            val routineName = routineNameLookup(day.routineId)
                            
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    dayName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.width(60.dp)
                                )
                                Text(
                                    routineName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(Spacing.medium))
                    
                    // Delete button (only visible when expanded to avoid clutter)
                    OutlinedButton(
                        onClick = { showDeleteDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete")
                        Spacer(modifier = Modifier.width(Spacing.small))
                        Text("Delete Program")
                    }
                }
            }
        }
    }

    // Delete confirmation dialog - Material 3 Expressive
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { 
                Text(
                    "Delete Program",
                    style = MaterialTheme.typography.headlineSmall, // Material 3 Expressive: Larger
                    fontWeight = FontWeight.Bold
                ) 
            },
            text = { 
                Text(
                    "Are you sure you want to delete \"${program.program.title}\"? This action cannot be undone.",
                    style = MaterialTheme.typography.bodyLarge // Material 3 Expressive: Larger
                ) 
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete()
                        showDeleteDialog = false
                    },
                    modifier = Modifier.height(56.dp), // Material 3 Expressive: Taller button
                    shape = RoundedCornerShape(20.dp), // Material 3 Expressive: More rounded
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