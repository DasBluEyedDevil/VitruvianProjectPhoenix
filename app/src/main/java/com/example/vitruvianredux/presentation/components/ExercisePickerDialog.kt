package com.example.vitruvianredux.presentation.components

import android.net.Uri
import android.view.ViewGroup
import android.widget.VideoView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.core.net.toUri
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.vitruvianredux.data.local.ExerciseEntity
import com.example.vitruvianredux.data.local.ExerciseVideoEntity
import com.example.vitruvianredux.data.repository.ExerciseRepository
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.launch

/**
 * Map display equipment names back to database values for filtering
 * Handles variations and database naming conventions
 */
private fun getEquipmentDatabaseValues(displayName: String): List<String> {
    return when (displayName) {
        "Long Bar" -> listOf("BAR", "LONG_BAR", "BARBELL")
        "Short Bar" -> listOf("SHORT_BAR")
        "Ankle Strap" -> listOf("ANKLE_STRAP", "STRAPS")
        "Handles" -> listOf("HANDLES", "SINGLE_HANDLE", "BOTH_HANDLES")
        "Bench" -> listOf("BENCH")
        "Rope" -> listOf("ROPE")
        "Belt" -> listOf("BELT")
        "Bodyweight" -> listOf("BODYWEIGHT")
        else -> emptyList()
    }
}

/**
 * Format raw equipment string from database to user-friendly display
 * Filters out technical values like BLACK_CABLES and formats equipment names
 */
private fun formatEquipment(rawEquipment: String): String {
    val equipmentMap = mapOf(
        "BAR" to "Long Bar",           // Database uses 'BAR' for Long Bar
        "LONG_BAR" to "Long Bar",
        "BARBELL" to "Long Bar",
        "SHORT_BAR" to "Short Bar",
        "BENCH" to "Bench",
        "HANDLES" to "Handles",
        "SINGLE_HANDLE" to "Handles",
        "BOTH_HANDLES" to "Handles",
        "STRAPS" to "Ankle Strap",     // Database uses 'STRAPS' for Ankle Strap
        "ANKLE_STRAP" to "Ankle Strap",
        "BELT" to "Belt",
        "ROPE" to "Rope",
        "BODYWEIGHT" to "Bodyweight"
    )
    
    // Filter out technical values and map to display names
    val filteredValues = rawEquipment
        .split(",")
        .map { it.trim().uppercase() }
        .filter { 
            // Exclude technical values that aren't meaningful to users
            it !in listOf("BLACK_CABLES", "RED_CABLES", "GREY_CABLES", "CABLES", "CABLE", "NULL", "", "PUMP_HANDLES", "DUMBBELLS")
        }
        .mapNotNull { equipmentMap[it] }
        .distinct()
    
    return filteredValues.joinToString(", ")
}

/**
 * Exercise Picker Dialog - Streamlined exercise selection component
 *
 * Used in: Routine Builder, Just Lift, Post-Workout Tagging
 * UX: Single tap to select, minimal UI, contextual display
 */
@OptIn(ExperimentalMaterial3Api::class, FlowPreview::class)
@Composable
fun ExercisePickerDialog(
    showDialog: Boolean,
    onDismiss: () -> Unit,
    onExerciseSelected: (ExerciseEntity) -> Unit,
    exerciseRepository: ExerciseRepository,
    modifier: Modifier = Modifier,
    fullScreen: Boolean = false
) {
    if (!showDialog) return

    // Local state for search and filter
    var searchQuery by remember { mutableStateOf("") }
    var selectedMuscleFilter by remember { mutableStateOf("All") }
    var selectedEquipmentFilter by remember { mutableStateOf("All") }
    var showFavoritesOnly by remember { mutableStateOf(false) }

    // Get exercises from repository based on search and muscle filter
    val allExercises by remember(searchQuery, selectedMuscleFilter, showFavoritesOnly) {
        when {
            showFavoritesOnly -> exerciseRepository.getFavorites()
            searchQuery.isNotBlank() -> exerciseRepository.searchExercises(searchQuery)
            selectedMuscleFilter != "All" -> exerciseRepository.filterByMuscleGroup(selectedMuscleFilter)
            else -> exerciseRepository.getAllExercises()
        }
    }.collectAsState(initial = emptyList())

    // Apply equipment filter in UI layer
    val exercises = remember(allExercises, selectedEquipmentFilter) {
        if (selectedEquipmentFilter != "All") {
            allExercises.filter { exercise ->
                // Get database values that match the selected filter display name
                val databaseValues = getEquipmentDatabaseValues(selectedEquipmentFilter)

                // Split equipment string and check for exact matches (not substring)
                val equipmentList = exercise.equipment.uppercase()
                    .split(",")
                    .map { it.trim() }

                databaseValues.any { dbValue ->
                    equipmentList.contains(dbValue.uppercase())
                }
            }
        } else {
            allExercises
        }
    }

    // Trigger import on first access
    LaunchedEffect(Unit) {
        exerciseRepository.importExercises()
    }

    // Content composable to be reused in both ModalBottomSheet and Dialog
    @Composable
    fun PickerContent() {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (fullScreen) Modifier.fillMaxHeight() else Modifier.fillMaxHeight(0.9f))
                .padding(horizontal = 16.dp)
        ) {
            // Title
            Text(
                text = "Select Exercise",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // Search field
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                placeholder = { Text("Search exercises...") },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = "Search")
                },
                singleLine = true
            )

            // Favorites filter toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Show Favorites Only",
                    style = MaterialTheme.typography.labelMedium
                )
                Switch(
                    checked = showFavoritesOnly,
                    onCheckedChange = {
                        showFavoritesOnly = it
                        // Reset other filters when showing favorites
                        if (it) {
                            searchQuery = ""
                            selectedMuscleFilter = "All"
                            selectedEquipmentFilter = "All"
                        }
                    }
                )
            }

            // Muscle group filter chips
            Text(
                text = "Muscle Groups",
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 12.dp)
            ) {
                val muscleFilters = listOf("All", "Chest", "Back", "Legs", "Shoulders", "Arms", "Core")
                items(muscleFilters) { filter ->
                    FilterChip(
                        selected = selectedMuscleFilter == filter,
                        onClick = { selectedMuscleFilter = filter },
                        label = { Text(filter) }
                    )
                }
            }

            // Equipment filter chips - Official Vitruvian equipment
            Text(
                text = "Equipment",
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 16.dp)
            ) {
                val equipmentFilters = listOf(
                    "All",
                    "Long Bar",
                    "Short Bar",
                    "Handles",
                    "Rope",
                    "Belt",
                    "Ankle Strap",
                    "Bench",
                    "Bodyweight"
                )
                items(equipmentFilters) { filter ->
                    FilterChip(
                        selected = selectedEquipmentFilter == filter,
                        onClick = { selectedEquipmentFilter = filter },
                        label = { Text(filter) }
                    )
                }
            }

            // Exercise list
            if (exercises.isEmpty()) {
                // Empty state
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Check if any filter is active
                        val hasActiveFilters = searchQuery.isNotBlank() || 
                                               selectedMuscleFilter != "All" || 
                                               selectedEquipmentFilter != "All"
                        
                        if (hasActiveFilters) {
                            // Show "No exercises found" when filters are active
                            Text(
                                text = "No exercises found",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            // Show loading state only when no filters are active
                            CircularProgressIndicator()
                            Text(
                                text = "Loading exercises...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(exercises) { exercise ->
                        ExerciseListItem(
                            exercise = exercise,
                            exerciseRepository = exerciseRepository,
                            onClick = {
                                onExerciseSelected(exercise)
                                onDismiss()
                            }
                        )
                    }
                }
            }
        }
    }

    // Use Dialog for fullscreen, ModalBottomSheet otherwise
    if (fullScreen) {
        Dialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(
                dismissOnBackPress = true,
                dismissOnClickOutside = false,
                usePlatformDefaultWidth = false
            )
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.surface
            ) {
                PickerContent()
            }
        }
    } else {
        ModalBottomSheet(
            onDismissRequest = onDismiss,
            modifier = modifier
        ) {
            PickerContent()
        }
    }
}

/**
 * Exercise list item - Single exercise in picker list
 */
@Composable
private fun ExerciseListItem(
    exercise: ExerciseEntity,
    exerciseRepository: ExerciseRepository,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    // Load video thumbnail
    var videos by remember { mutableStateOf<List<ExerciseVideoEntity>>(emptyList()) }
    var isLoadingVideo by remember { mutableStateOf(true) }
    var showVideoDialog by remember { mutableStateOf(false) }
    
    LaunchedEffect(exercise.id) {
        try {
            videos = exerciseRepository.getVideos(exercise.id)
            isLoadingVideo = false
        } catch (e: Exception) {
            isLoadingVideo = false
        }
    }
    
    // Get preferred thumbnail (FRONT angle preferred, or first available)
    // Apply Mux crop parameters
    val baseThumbnailUrl = videos.firstOrNull { it.angle == "FRONT" }?.thumbnailUrl
        ?: videos.firstOrNull()?.thumbnailUrl
    val thumbnailUrl = baseThumbnailUrl?.let { url ->
        if (url.contains("image.mux.com") && !url.contains("?")) {
            // Add thumbnail parameters only if URL doesn't already have them
            "$url?width=300&height=300&fit_mode=crop&crop=center&time=2"
        } else {
            // Use URL as-is (already has HD parameters from assets)
            url
        }
    }
    
    // Video dialog
    if (showVideoDialog && videos.isNotEmpty()) {
        ExerciseVideoDialog(
            exerciseName = exercise.name,
            videos = videos,
            onDismiss = { showVideoDialog = false }
        )
    }
    
    ListItem(
        headlineContent = { Text(exercise.name) },
        supportingContent = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                // Muscle groups - always on first line
                if (exercise.muscleGroups.isNotBlank()) {
                    Text(
                        text = "Muscle Group: ${exercise.muscleGroups.split(",").joinToString(", ") { it.trim().lowercase().replaceFirstChar { c -> c.uppercase() } }}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Equipment - always on second line if present
                if (exercise.equipment.isNotBlank() && exercise.equipment.lowercase() != "null") {
                    val formattedEquipment = formatEquipment(exercise.equipment)

                    // Only show equipment if we have meaningful values after formatting
                    if (formattedEquipment.isNotBlank()) {
                        Text(
                            text = "Equipment: $formattedEquipment",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        leadingContent = {
            ExerciseThumbnail(
                thumbnailUrl = thumbnailUrl,
                exerciseName = exercise.name,
                isLoading = isLoadingVideo,
                onClick = if (videos.isNotEmpty()) {
                    { showVideoDialog = true }
                } else null
            )
        },
        trailingContent = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Show performance count only if exercise has been performed
                if (exercise.timesPerformed > 0) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(
                            text = "Performed ${exercise.timesPerformed}x",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }

                // Favorite button
                IconButton(
                    onClick = {
                        coroutineScope.launch {
                            exerciseRepository.toggleFavorite(exercise.id)
                        }
                    }
                ) {
                    Icon(
                        imageVector = if (exercise.isFavorite) Icons.Filled.Star else Icons.Outlined.Star,
                        contentDescription = if (exercise.isFavorite) "Remove from favorites" else "Add to favorites",
                        tint = if (exercise.isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        modifier = modifier.clickable(onClick = onClick)
    )
}

/**
 * Exercise thumbnail - Shows video thumbnail or fallback initial
 */
@Composable
private fun ExerciseThumbnail(
    thumbnailUrl: String?,
    exerciseName: String,
    isLoading: Boolean,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(56.dp)
            .clip(RoundedCornerShape(8.dp))
            .then(
                if (onClick != null) {
                    Modifier.clickable(onClick = onClick)
                } else Modifier
            ),
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
                // Show thumbnail image
                AsyncImage(
                    model = thumbnailUrl,
                    contentDescription = "Exercise demonstration",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
            else -> {
                // No thumbnail - show exercise name initial
                ExerciseInitial(exerciseName)
            }
        }
    }
}

/**
 * Exercise initial - Fallback when no thumbnail available
 */
@Composable
private fun ExerciseInitial(
    exerciseName: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = exerciseName.firstOrNull()?.uppercase() ?: "?",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}

/**
 * Exercise Video Dialog - Full screen video player with angle selection
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseVideoDialog(
    exerciseName: String,
    videos: List<ExerciseVideoEntity>,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    // State for selected angle
    var selectedAngle by remember {
        mutableStateOf(
            videos.firstOrNull { it.angle == "FRONT" }?.angle
                ?: videos.firstOrNull()?.angle
                ?: "FRONT"
        )
    }
    
    val currentVideo = videos.firstOrNull { it.angle == selectedAngle }
        ?: videos.firstOrNull()
    
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f)
                .padding(16.dp)
        ) {
            // Header with title and close button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = exerciseName,
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close"
                    )
                }
            }

            // Video player
            currentVideo?.let { video ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    VideoPlayer(
                        videoUrl = video.videoUrl,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            } ?: run {
                // No video available
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .background(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(12.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Video not available",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/**
 * Video Player - AndroidView wrapper for VideoView
 */
@Composable
fun VideoPlayer(
    videoUrl: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var isLoading by remember { mutableStateOf(true) }
    var hasError by remember { mutableStateOf(false) }
    
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        AndroidView(
            factory = { ctx ->
                VideoView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )

                    // Set video URI (no controls - just loop like a GIF)
                    try {
                        setVideoURI(videoUrl.toUri())
                        
                        // Set up listeners
                        setOnPreparedListener { mp ->
                            isLoading = false
                            mp.isLooping = true
                            start()
                        }
                        
                        setOnErrorListener { _, what, extra ->
                            isLoading = false
                            hasError = true
                            true
                        }
                        
                        setOnCompletionListener {
                            // Loop the video
                            start()
                        }
                    } catch (e: Exception) {
                        isLoading = false
                        hasError = true
                    }
                }
            },
            modifier = Modifier.fillMaxSize()
        )
        
        // Loading indicator
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(48.dp)
            )
        }
        
        // Error state
        if (hasError) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Failed to load video",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
                Text(
                    text = videoUrl,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
