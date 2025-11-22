package com.example.vitruvianredux.presentation.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.zIndex
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
    exerciseRepository: ExerciseRepository,
    themeMode: com.example.vitruvianredux.ui.theme.ThemeMode
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

        // Track scroll state to show scroll indicator
        val listState = rememberLazyListState()

        // Determine if we can scroll down (more content below)
        val canScrollDown by remember {
            derivedStateOf {
                val layoutInfo = listState.layoutInfo
                val lastVisibleItem = layoutInfo.visibleItemsInfo.lastOrNull()

                // Can scroll down if the last visible item is not the last item in the list
                lastVisibleItem?.let {
                    it.index < layoutInfo.totalItemsCount - 1
                } ?: false
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(backgroundGradient)
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(Spacing.medium),
                verticalArrangement = Arrangement.spacedBy(Spacing.medium)
            ) {
            item {
                Text(
                    "Schedule workouts for each day",
                    style = MaterialTheme.typography.titleLarge, // Material 3 Expressive: Larger (was titleMedium)
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

                // Summary card - Material 3 Expressive
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
                            .padding(Spacing.medium)
                    ) {
                        Text(
                            "Program Summary",
                            style = MaterialTheme.typography.titleLarge, // Material 3 Expressive: Larger (was titleMedium)
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(Spacing.small))

                        val workoutDays = dailyRoutines.values.filterNotNull().size
                        val restDays = 7 - workoutDays

                        Text(
                            "$workoutDays workout days, $restDays rest days",
                            style = MaterialTheme.typography.bodyLarge, // Material 3 Expressive: Larger (was bodyMedium)
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
            }

            // Scroll indicator - gradient fade at bottom when more content is available
            if (canScrollDown) {
                val bottomColor = if (useDarkColors) Color(0xFF172554) else Color(0xFFDFF6FF)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp)
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    bottomColor.copy(alpha = 0.85f),
                                    bottomColor
                                )
                            )
                        )
                        .zIndex(1f)
                ) {
                    // Down arrow icon to indicate more content
                    Surface(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 12.dp),
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = "Scroll down for more",
                            modifier = Modifier
                                .padding(8.dp)
                                .size(28.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }

    // Routine picker dialog - Material 3 Expressive
    if (showRoutinePicker && selectedDay != null) {
        AlertDialog(
            onDismissRequest = { showRoutinePicker = false },
            title = { 
                Text(
                    "Select Routine for ${selectedDay!!.getDisplayName(TextStyle.FULL, Locale.getDefault())}",
                    style = MaterialTheme.typography.headlineSmall, // Material 3 Expressive: Larger
                    fontWeight = FontWeight.Bold
                ) 
            },
            text = {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(Spacing.small)
                ) {
                    if (routines.isEmpty()) {
                        item {
                            Text(
                                "No routines available. Create a routine first.",
                                style = MaterialTheme.typography.bodyLarge, // Material 3 Expressive: Larger (was bodyMedium)
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
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest), // Material 3 Expressive: Higher contrast
                                shape = RoundedCornerShape(20.dp) // Material 3 Expressive: More rounded (was 12dp)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(Spacing.medium)
                                ) {
                                    Text(
                                        routine.name,
                                        style = MaterialTheme.typography.titleMedium, // Material 3 Expressive: Larger (was bodyLarge)
                                        fontWeight = FontWeight.Bold // Material 3 Expressive: Bolder (was Medium)
                                    )
                                    Text(
                                        "${routine.exercises.size} exercises",
                                        style = MaterialTheme.typography.bodyMedium, // Material 3 Expressive: Larger (was bodySmall)
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
                TextButton(
                    onClick = { showRoutinePicker = false },
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
            containerColor = if (routine != null) {
                MaterialTheme.colorScheme.primaryContainer // Material 3 Expressive: Use primary container when routine assigned
            } else {
                MaterialTheme.colorScheme.surfaceContainerHighest // Material 3 Expressive: Higher contrast
            }
        ),
        shape = RoundedCornerShape(20.dp), // Material 3 Expressive: More rounded (was 16dp)
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp), // Material 3 Expressive: Higher elevation (was 4dp)
        border = androidx.compose.foundation.BorderStroke(
            2.dp,
            if (routine != null) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
            } else {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
            }
        ) // Material 3 Expressive: Thicker border (was 1dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp), // Material 3 Expressive: More padding (was Spacing.medium)
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    day.getDisplayName(TextStyle.FULL, Locale.getDefault()),
                    style = MaterialTheme.typography.titleLarge, // Material 3 Expressive: Larger (was titleMedium)
                    fontWeight = FontWeight.Bold
                )

                if (routine != null) {
                    Spacer(modifier = Modifier.height(Spacing.extraSmall))
                    Text(
                        routine.name,
                        style = MaterialTheme.typography.bodyLarge // Material 3 Expressive: Larger (was bodyMedium)
                    )
                    Text(
                        "${routine.exercises.size} exercises",
                        style = MaterialTheme.typography.bodyMedium, // Material 3 Expressive: Larger (was bodySmall)
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Spacer(modifier = Modifier.height(Spacing.extraSmall))
                    Text(
                        "Rest day",
                        style = MaterialTheme.typography.bodyLarge, // Material 3 Expressive: Larger (was bodyMedium)
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
