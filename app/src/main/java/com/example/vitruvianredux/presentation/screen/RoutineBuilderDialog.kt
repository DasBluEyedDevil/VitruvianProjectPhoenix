package com.example.vitruvianredux.presentation.screen

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.vitruvianredux.data.local.ExerciseEntity
import com.example.vitruvianredux.data.repository.ExerciseRepository
import com.example.vitruvianredux.domain.model.CableConfiguration
import com.example.vitruvianredux.domain.model.Exercise
import com.example.vitruvianredux.domain.model.Routine
import com.example.vitruvianredux.domain.model.RoutineExercise
import com.example.vitruvianredux.domain.model.WeightUnit
import com.example.vitruvianredux.domain.model.resolveDefaultCableConfig
import com.example.vitruvianredux.presentation.components.ExercisePickerDialog
import com.example.vitruvianredux.ui.theme.*
import timber.log.Timber
import java.util.*

@Composable
fun RoutineBuilderDialog(
    routine: Routine? = null,
    onSave: (Routine) -> Unit,
    onDismiss: () -> Unit,
    exerciseRepository: ExerciseRepository,
    weightUnit: WeightUnit,
    kgToDisplay: (Float, WeightUnit) -> Float,
    displayToKg: (Float, WeightUnit) -> Float,
    themeMode: ThemeMode
) {
    var name by remember { mutableStateOf(routine?.name ?: "") }
    var description by remember { mutableStateOf(routine?.description ?: "") }
    var exercises by remember { mutableStateOf(routine?.exercises ?: emptyList<RoutineExercise>()) }
    var showError by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    var showExercisePicker by remember { mutableStateOf(false) }
    var exerciseToEdit by remember { mutableStateOf<Pair<Int, RoutineExercise>?>(null) }

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

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f),
            shape = RoundedCornerShape(16.dp),
            color = Color.Transparent
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(backgroundGradient)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(Spacing.medium)
                ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (routine == null) "Create Routine" else "Edit Routine",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Close",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(Spacing.medium))

                // Scrollable content
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                ) {
                    // Name field
                    OutlinedTextField(
                        value = name,
                        onValueChange = {
                            name = it
                            showError = false
                        },
                        label = { Text("Routine Name *") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(),
                        singleLine = true,
                        isError = showError && name.isBlank()
                    )

                    if (showError && name.isBlank()) {
                        Text(
                            "Routine name is required",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(start = Spacing.medium, top = Spacing.extraSmall)
                        )
                    }

                    Spacer(modifier = Modifier.height(Spacing.medium))

                    // Description field
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text("Description (optional)") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp),
                        colors = OutlinedTextFieldDefaults.colors(),
                        maxLines = 4
                    )

                    Spacer(modifier = Modifier.height(Spacing.large))

                    // Exercises section header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Exercises",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            "${exercises.size} exercises",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (showError && exercises.isEmpty()) {
                        Text(
                            "Add at least one exercise",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = Spacing.extraSmall)
                        )
                    }

                    Spacer(modifier = Modifier.height(Spacing.small))

                    // Exercise list
                    if (exercises.isEmpty()) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = Spacing.small),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            shape = RoundedCornerShape(16.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                            border = BorderStroke(1.dp, Color(0xFFF5F3FF))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(Spacing.large),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "No exercises added yet",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(Spacing.small),
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)  // Take remaining space, prevents infinity constraints
                        ) {
                            itemsIndexed(
                                items = exercises,
                                key = { _, item -> item.id }
                            ) { index, exercise ->
                                ExerciseListItem(
                                        exercise = exercise,
                                        index = index,
                                        isFirst = index == 0,
                                        isLast = index == exercises.lastIndex,
                                        exerciseRepository = exerciseRepository,
                                        weightUnit = weightUnit,
                                        kgToDisplay = kgToDisplay,
                                        onEdit = { exerciseToEdit = Pair(index, exercise) },
                                        onDelete = {
                                            exercises = exercises.filterIndexed { i, _ -> i != index }
                                                .mapIndexed { i, ex -> ex.copy(orderIndex = i) }
                                            showError = false
                                        },
                                        onMoveUp = {
                                            if (index > 0) {
                                                exercises = exercises.mapIndexed { i, ex ->
                                                    when (i) {
                                                        index -> exercises[index - 1].copy(orderIndex = index)
                                                        index - 1 -> exercises[index].copy(orderIndex = index - 1)
                                                        else -> ex
                                                    }
                                                }
                                            }
                                        },
                                        onMoveDown = {
                                            if (index < exercises.lastIndex) {
                                                exercises = exercises.mapIndexed { i, ex ->
                                                    when (i) {
                                                        index -> exercises[index + 1].copy(orderIndex = index)
                                                        index + 1 -> exercises[index].copy(orderIndex = index + 1)
                                                        else -> ex
                                                    }
                                                }
                                            }
                                        }
                                    )
                            }
                        }
                    }

                    // Add exercise button
                    OutlinedButton(
                        onClick = { showExercisePicker = true },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.primary
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Add exercise")
                        Spacer(modifier = Modifier.width(Spacing.small))
                        Text("Add Exercise")
                    }
                }

                Spacer(modifier = Modifier.height(Spacing.medium))

                // Bottom actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.small)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    ) {
                        Text("Cancel")
                    }
                    Button(
                        onClick = {
                            if (name.isBlank() || exercises.isEmpty()) {
                                showError = true
                                errorMessage = when {
                                    name.isBlank() -> "Routine name is required"
                                    exercises.isEmpty() -> "Add at least one exercise"
                                    else -> ""
                                }
                            } else {
                                val newRoutine = Routine(
                                    id = routine?.id ?: UUID.randomUUID().toString(),
                                    name = name.trim(),
                                    description = description.trim(),
                                    exercises = exercises,
                                    createdAt = routine?.createdAt ?: System.currentTimeMillis(),
                                    lastUsed = routine?.lastUsed,
                                    useCount = routine?.useCount ?: 0
                                )
                                onSave(newRoutine)
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        ),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text(
                            "Save",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            }
        }
    }

    // Exercise picker dialog
    ExercisePickerDialog(
        showDialog = showExercisePicker,
        onDismiss = { showExercisePicker = false },
        onExerciseSelected = { selectedExercise ->
            // Convert ExerciseEntity to Exercise domain model
            val exercise = Exercise(
                name = selectedExercise.name,
                muscleGroup = selectedExercise.muscleGroups.split(",").firstOrNull()?.trim() ?: "Full Body",
                equipment = selectedExercise.equipment.split(",").firstOrNull()?.trim() ?: "",
                defaultCableConfig = CableConfiguration.DOUBLE, // Default to DOUBLE, user can override
                id = selectedExercise.id  // Store exercise library ID for loading videos/thumbnails
            )

            // Open exercise edit dialog with default values
            val newRoutineExercise = RoutineExercise(
                id = UUID.randomUUID().toString(),
                exercise = exercise,
                cableConfig = exercise.resolveDefaultCableConfig(), // Use helper to resolve EITHER ? DOUBLE
                orderIndex = exercises.size,
                setReps = listOf(10, 10, 10),
                weightPerCableKg = 20f,
                progressionKg = 2.5f,
                restSeconds = 60,
                notes = ""
            )
            exerciseToEdit = Pair(exercises.size, newRoutineExercise)
            showExercisePicker = false
        },
        exerciseRepository = exerciseRepository
    )

    // Exercise edit bottom sheet
    exerciseToEdit?.let { (index, exercise) ->
        ExerciseEditBottomSheet(
            exercise = exercise,
            weightUnit = weightUnit,
            kgToDisplay = kgToDisplay,
            displayToKg = displayToKg,
            exerciseRepository = exerciseRepository,
            onSave = { updatedExercise ->
                exercises = if (index < exercises.size) {
                    exercises.mapIndexed { i, ex -> if (i == index) updatedExercise else ex }
                } else {
                    exercises + updatedExercise
                }
                exerciseToEdit = null
                showError = false
            },
            onDismiss = {
                exerciseToEdit = null
                // If we were adding a new exercise, don't keep the picker open
            }
        )
    }
}

@Composable
fun ExerciseListItem(
    exercise: RoutineExercise,
    index: Int,
    isFirst: Boolean,
    isLast: Boolean,
    exerciseRepository: ExerciseRepository,
    weightUnit: WeightUnit,
    kgToDisplay: (Float, WeightUnit) -> Float,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit
) {
    // Load video thumbnail for exercise
    var thumbnailUrl by remember { mutableStateOf<String?>(null) }
    var isLoadingVideo by remember { mutableStateOf(true) }

    LaunchedEffect(exercise.exercise.id) {
        try {
            // Load videos using exercise library ID
            val exerciseId = exercise.exercise.id
            if (exerciseId != null) {
                Timber.d("Loading thumbnail for ${exercise.exercise.name} (ID: $exerciseId)")
                val videos = exerciseRepository.getVideos(exerciseId)
                Timber.d("Found ${videos.size} videos for ${exercise.exercise.name}")
                val baseThumbnailUrl = videos.firstOrNull { it.angle == "FRONT" }?.thumbnailUrl
                    ?: videos.firstOrNull()?.thumbnailUrl

                // Apply Mux thumbnail parameters if not already present
                thumbnailUrl = baseThumbnailUrl?.let { url ->
                    if (url.contains("image.mux.com") && !url.contains("?")) {
                        // Add thumbnail parameters only if URL doesn't already have them
                        "$url?width=400&height=400&fit_mode=crop&crop=center&time=2"
                    } else {
                        // Use URL as-is (already has HD parameters from assets)
                        url
                    }
                }
                Timber.d("Thumbnail URL: $thumbnailUrl")
            } else {
                Timber.w("No exercise ID for ${exercise.exercise.name}, cannot load thumbnail")
            }
            isLoadingVideo = false
        } catch (e: Exception) {
            Timber.e(e, "Failed to load thumbnail for ${exercise.exercise.name}")
            isLoadingVideo = false
        }
    }

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
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        border = BorderStroke(1.dp, Color(0xFFF5F3FF))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.medium),
            horizontalArrangement = Arrangement.spacedBy(Spacing.small),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left: Move controls (vertical stack)
            Column(
                verticalArrangement = Arrangement.spacedBy(2.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                IconButton(
                    onClick = onMoveUp,
                    enabled = !isFirst,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Default.KeyboardArrowUp,
                        contentDescription = "Move Up",
                        tint = if (isFirst) MaterialTheme.colorScheme.outlineVariant else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
                IconButton(
                    onClick = onMoveDown,
                    enabled = !isLast,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Default.KeyboardArrowDown,
                        contentDescription = "Move Down",
                        tint = if (isLast) MaterialTheme.colorScheme.outlineVariant else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Middle: Exercise details
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(Spacing.small)
            ) {
                // Calculate display values once
                val weightSuffix = if (weightUnit == WeightUnit.LB) "lbs" else "kg"
                val displayWeight = kgToDisplay(exercise.weightPerCableKg, weightUnit)

                // Exercise name
                Text(
                    exercise.exercise.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                // First row of stats: Sets/Reps and Weight
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.small),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Sets/Reps badge
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                    ) {
                        Text(
                            formatReps(exercise.setReps),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            fontWeight = FontWeight.Medium
                        )
                    }

                    // Weight badge
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f)
                    ) {
                        Text(
                            "${displayWeight.toInt()}$weightSuffix",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                // Second row of stats: Progression and Rest time
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.small),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Progression/Regression badge (only if non-zero)
                    if (exercise.progressionKg != 0f) {
                        val displayProgression = kgToDisplay(exercise.progressionKg, weightUnit)
                        val progressionText = if (displayProgression > 0) {
                            "+${displayProgression.toInt()}$weightSuffix per rep"
                        } else {
                            "${displayProgression.toInt()}$weightSuffix per rep"
                        }
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (exercise.progressionKg > 0)
                                MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f)
                            else
                                MaterialTheme.colorScheme.error.copy(alpha = 0.15f)
                        ) {
                            Text(
                                progressionText,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (exercise.progressionKg > 0)
                                    MaterialTheme.colorScheme.tertiary
                                else
                                    MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    // Rest time badge
                    if (exercise.restSeconds > 0) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Text(
                                "${exercise.restSeconds}s rest",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                // Notes if present
                if (exercise.notes.isNotEmpty()) {
                    Text(
                        exercise.notes,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                        maxLines = 2
                    )
                }
            }

            // Right side: Action buttons
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                IconButton(
                    onClick = {
                        isPressed = true
                        onEdit()
                    },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = "Edit",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp)
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
 * Exercise thumbnail - Shows video thumbnail or fallback initial
 * (Extracted from ExercisePickerDialog for reuse)
 * Large size (150dp) for better visibility - compensates for small people in Mux videos
 */
@Composable
private fun ExerciseThumbnail(
    thumbnailUrl: String?,
    exerciseName: String,
    isLoading: Boolean,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(150.dp)
            .clip(RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center
    ) {
        when {
            isLoading -> {
                // Loading state - show shimmer effect
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                )
            }
            !thumbnailUrl.isNullOrBlank() -> {
                // Show thumbnail image - Crop maintains aspect ratio while filling frame
                AsyncImage(
                    model = thumbnailUrl,
                    contentDescription = "Exercise demonstration",
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Crop,
                    onError = { error ->
                        Timber.e("Failed to load thumbnail: ${thumbnailUrl}, error: ${error.result.throwable}")
                    },
                    onSuccess = {
                        Timber.d("Successfully loaded thumbnail: $thumbnailUrl")
                    }
                )
            }
            else -> {
                // No thumbnail - show exercise name initial
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = exerciseName.firstOrNull()?.uppercase() ?: "?",
                        style = MaterialTheme.typography.headlineLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

/**
 * Format the setReps list for display in the exercise list
 * Examples:
 * - [10, 10, 10] -> "3 x 10 reps"
 * - [10, 8, 6, 4] -> "4 sets: 10/8/6/4"
 * - [12] -> "1 x 12 reps"
 */
private fun formatReps(setReps: List<Int>): String {
    if (setReps.isEmpty()) return "0 sets"

    val allSame = setReps.all { it == setReps.first() }
    return if (allSame) {
        "${setReps.size} x ${setReps.first()} reps"
    } else {
        "${setReps.size} sets: ${setReps.joinToString("/")}"
    }
}
