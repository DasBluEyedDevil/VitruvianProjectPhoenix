package com.example.vitruvianredux.presentation.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import com.example.vitruvianredux.data.local.ProgramDayEntity
import com.example.vitruvianredux.data.local.WeeklyProgramEntity
import com.example.vitruvianredux.data.local.WeeklyProgramWithDays
import com.example.vitruvianredux.data.repository.ExerciseRepository
import com.example.vitruvianredux.domain.model.Routine
import com.example.vitruvianredux.presentation.viewmodel.MainViewModel
import com.example.vitruvianredux.ui.theme.Spacing
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.*

/**
 * Program Builder screen - create or edit a weekly program.
 * Allows user to assign routines to each day of the week.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProgramBuilderScreen(
    navController: NavController,
    viewModel: MainViewModel,
    programId: String,
    exerciseRepository: ExerciseRepository
) {
    val routines by viewModel.routines.collectAsState()
    val isAutoConnecting by viewModel.isAutoConnecting.collectAsState()
    val connectionError by viewModel.connectionError.collectAsState()

    var programName by remember { mutableStateOf("New Program") }
    var isEditingName by remember { mutableStateOf(false) }
    var showRoutinePicker by remember { mutableStateOf(false) }
    var selectedDay by remember { mutableStateOf<DayOfWeek?>(null) }

    // Map of day to selected routine
    var dailyRoutines by remember {
        mutableStateOf<Map<DayOfWeek, Routine?>>(
            DayOfWeek.entries.associateWith { null }
        )
    }

    // Load existing program data if editing (programId != "new")
    val programs by viewModel.weeklyPrograms.collectAsState()
    LaunchedEffect(programId, programs, routines) {
        if (programId != "new") {
            val existingProgram = programs.find { it.program.id == programId }
            existingProgram?.let { program ->
                // Set program name
                programName = program.program.title

                // Convert ProgramDayEntity list back to Map<DayOfWeek, Routine?>
                val routineMap = mutableMapOf<DayOfWeek, Routine?>()

                // Initialize all days as rest days
                DayOfWeek.entries.forEach { day ->
                    routineMap[day] = null
                }

                // Fill in workout days from program
                program.days.forEach { programDay ->
                    // programDay.dayOfWeek is Int (1=MONDAY, 7=SUNDAY)
                    val dayOfWeek = DayOfWeek.of(programDay.dayOfWeek)
                    val routine = routines.find { it.id == programDay.routineId }
                    routineMap[dayOfWeek] = routine
                }

                dailyRoutines = routineMap
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (isEditingName) {
                        TextField(
                            value = programName,
                            onValueChange = { programName = it },
                            singleLine = true,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.primary,
                                unfocusedContainerColor = MaterialTheme.colorScheme.primary,
                                focusedTextColor = MaterialTheme.colorScheme.onPrimary,
                                unfocusedTextColor = MaterialTheme.colorScheme.onPrimary
                            )
                        )
                    } else {
                        Text(programName)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { isEditingName = !isEditingName }) {
                        Icon(
                            if (isEditingName) Icons.Default.Check else Icons.Default.Edit,
                            contentDescription = if (isEditingName) "Save name" else "Edit name"
                        )
                    }
                    IconButton(onClick = {
                        // Collect program data and save to database
                        val programEntity = WeeklyProgramEntity(
                            id = if (programId == "new") UUID.randomUUID().toString() else programId,
                            title = programName,
                            notes = null,
                            isActive = false,
                            createdAt = System.currentTimeMillis()
                        )

                        // Create ProgramDayEntity for each day with an assigned routine
                        val programDays = dailyRoutines.entries
                            .filter { (_, routine) -> routine != null }
                            .map { (day, routine) ->
                                ProgramDayEntity(
                                    programId = programEntity.id,
                                    dayOfWeek = day.value, // DayOfWeek.value: MONDAY=1 to SUNDAY=7
                                    routineId = routine!!.id
                                )
                            }

                        // Save program with days
                        val programWithDays = WeeklyProgramWithDays(
                            program = programEntity,
                            days = programDays
                        )
                        viewModel.saveProgram(programWithDays)

                        navController.navigateUp()
                    }) {
                        Icon(Icons.Default.Done, contentDescription = "Save program")
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFFF8FAFC),
                            Color(0xFFF5F3FF),
                            Color(0xFFEFF6FF)
                        )
                    )
                )
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(Spacing.medium),
                verticalArrangement = Arrangement.spacedBy(Spacing.medium)
            ) {
            item {
                Text(
                    "Schedule workouts for each day",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            // 7 day cards
            itemsIndexed(DayOfWeek.entries.toList()) { index, day ->
                DayRoutineCard(
                    day = day,
                    routine = dailyRoutines[day],
                    onSelectRoutine = {
                        selectedDay = day
                        showRoutinePicker = true
                    },
                    onClearRoutine = {
                        dailyRoutines = dailyRoutines.toMutableMap().apply {
                            put(day, null)
                        }
                    }
                )
            }

            item {
                Spacer(modifier = Modifier.height(Spacing.medium))

                // Summary card
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
                        Text(
                            "Program Summary",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(Spacing.small))

                        val workoutDays = dailyRoutines.values.filterNotNull().size
                        val restDays = 7 - workoutDays

                        Text(
                            "$workoutDays workout days, $restDays rest days",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
            }
        }
    }

    // Routine picker dialog
    if (showRoutinePicker && selectedDay != null) {
        AlertDialog(
            onDismissRequest = { showRoutinePicker = false },
            title = { Text("Select Routine for ${selectedDay!!.getDisplayName(TextStyle.FULL, Locale.getDefault())}") },
            text = {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(Spacing.small)
                ) {
                    if (routines.isEmpty()) {
                        item {
                            Text(
                                "No routines available. Create a routine first.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        itemsIndexed(routines) { _, routine ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        dailyRoutines = dailyRoutines.toMutableMap().apply {
                                            put(selectedDay!!, routine)
                                        }
                                        showRoutinePicker = false
                                    },
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(Spacing.medium)
                                ) {
                                    Text(
                                        routine.name,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        "${routine.exercises.size} exercises",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showRoutinePicker = false }) {
                    Text("Cancel")
                }
            }
        )
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

/**
 * Card for selecting a routine for a specific day.
 */
@Composable
fun DayRoutineCard(
    day: DayOfWeek,
    routine: Routine?,
    onSelectRoutine: () -> Unit,
    onClearRoutine: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelectRoutine),
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
                    day.getDisplayName(TextStyle.FULL, Locale.getDefault()),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                if (routine != null) {
                    Spacer(modifier = Modifier.height(Spacing.extraSmall))
                    Text(
                        routine.name,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        "${routine.exercises.size} exercises",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Spacer(modifier = Modifier.height(Spacing.extraSmall))
                    Text(
                        "Rest day",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (routine != null) {
                IconButton(onClick = onClearRoutine) {
                    Icon(
                        Icons.Default.Clear,
                        contentDescription = "Clear routine",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            } else {
                Icon(
                    Icons.Default.Add,
                    contentDescription = "Add routine",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
