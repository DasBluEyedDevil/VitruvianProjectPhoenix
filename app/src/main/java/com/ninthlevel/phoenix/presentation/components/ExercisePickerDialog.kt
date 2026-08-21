package com.ninthlevel.phoenix.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ninthlevel.phoenix.data.local.ExerciseEntity
import com.ninthlevel.phoenix.data.repository.ExerciseRepository
import com.ninthlevel.phoenix.domain.model.Equipment
import com.ninthlevel.phoenix.domain.model.MuscleGroup
import com.ninthlevel.phoenix.domain.model.matchesEquipmentFilter
import com.ninthlevel.phoenix.domain.model.matchesMuscleFilter
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
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

    var searchQuery by remember { mutableStateOf("") }
    var selectedMuscleFilter by remember { mutableStateOf("All") }
    var selectedEquipmentFilter by remember { mutableStateOf("All") }
    var showFavoritesOnly by remember { mutableStateOf(false) }

    val allExercises by remember(searchQuery, showFavoritesOnly) {
        when {
            showFavoritesOnly -> exerciseRepository.getFavorites()
            searchQuery.isNotBlank() -> exerciseRepository.searchExercises(searchQuery)
            else -> exerciseRepository.getAllExercises()
        }
    }.collectAsState(initial = emptyList())

    val exercises = remember(allExercises, selectedEquipmentFilter, selectedMuscleFilter) {
        allExercises.filter {
            matchesEquipmentFilter(it.equipment, selectedEquipmentFilter) &&
                matchesMuscleFilter(it.muscleGroups, selectedMuscleFilter)
        }
    }

    if (fullScreen) {
        Dialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(
                dismissOnBackPress = true,
                dismissOnClickOutside = false,
                usePlatformDefaultWidth = false
            )
        ) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text("Select Exercise") },
                        navigationIcon = {
                            IconButton(onClick = onDismiss) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back"
                                )
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                            titleContentColor = MaterialTheme.colorScheme.onSurface,
                            navigationIconContentColor = MaterialTheme.colorScheme.onSurface
                        )
                    )
                },
                containerColor = MaterialTheme.colorScheme.surface
            ) { paddingValues ->
                Box(modifier = Modifier.padding(paddingValues)) {
                    ExercisePickerContent(
                        exercises = exercises,
                        searchQuery = searchQuery,
                        onSearchQueryChange = { searchQuery = it },
                        showFavoritesOnly = showFavoritesOnly,
                        onShowFavoritesOnlyChange = {
                            showFavoritesOnly = it
                            if (it) {
                                searchQuery = ""
                                selectedMuscleFilter = "All"
                                selectedEquipmentFilter = "All"
                            }
                        },
                        selectedMuscleFilter = selectedMuscleFilter,
                        onMuscleFilterChange = { selectedMuscleFilter = it },
                        selectedEquipmentFilter = selectedEquipmentFilter,
                        onEquipmentFilterChange = { selectedEquipmentFilter = it },
                        onExerciseSelected = {
                            onExerciseSelected(it)
                            onDismiss()
                        },
                        exerciseRepository = exerciseRepository,
                        fullScreen = true
                    )
                }
            }
        }
    } else {
        ModalBottomSheet(
            onDismissRequest = onDismiss,
            modifier = modifier,
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
        ) {
            ExercisePickerContent(
                exercises = exercises,
                searchQuery = searchQuery,
                onSearchQueryChange = { searchQuery = it },
                showFavoritesOnly = showFavoritesOnly,
                onShowFavoritesOnlyChange = {
                    showFavoritesOnly = it
                    if (it) {
                        searchQuery = ""
                        selectedMuscleFilter = "All"
                        selectedEquipmentFilter = "All"
                    }
                },
                selectedMuscleFilter = selectedMuscleFilter,
                onMuscleFilterChange = { selectedMuscleFilter = it },
                selectedEquipmentFilter = selectedEquipmentFilter,
                onEquipmentFilterChange = { selectedEquipmentFilter = it },
                onExerciseSelected = {
                    onExerciseSelected(it)
                    onDismiss()
                },
                exerciseRepository = exerciseRepository,
                fullScreen = false
            )
        }
    }
}

@Composable
fun ExercisePickerContent(
    exercises: List<ExerciseEntity>,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    showFavoritesOnly: Boolean,
    onShowFavoritesOnlyChange: (Boolean) -> Unit,
    selectedMuscleFilter: String,
    onMuscleFilterChange: (String) -> Unit,
    selectedEquipmentFilter: String,
    onEquipmentFilterChange: (String) -> Unit,
    onExerciseSelected: (ExerciseEntity) -> Unit,
    exerciseRepository: ExerciseRepository,
    fullScreen: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (fullScreen) Modifier.fillMaxHeight() else Modifier.fillMaxHeight(0.9f))
            .padding(horizontal = 16.dp)
    ) {
        if (!fullScreen) {
            Text(
                text = "Select Exercise",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 16.dp)
            )
        }

        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            placeholder = { Text("Search exercises...") },
            leadingIcon = {
                Icon(Icons.Default.Search, contentDescription = "Search")
            },
            singleLine = true
        )

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
                onCheckedChange = onShowFavoritesOnlyChange
            )
        }

        Text(
            text = "Muscle Groups",
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(bottom = 12.dp)
        ) {
            val muscleFilters = listOf("All") + MuscleGroup.all
            items(muscleFilters) { filter ->
                FilterChip(
                    selected = selectedMuscleFilter == filter,
                    onClick = { onMuscleFilterChange(filter) },
                    label = { Text(filter) }
                )
            }
        }

        Text(
            text = "Equipment",
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(bottom = 16.dp)
        ) {
            val equipmentFilters = listOf("All") + Equipment.all
            items(equipmentFilters) { filter ->
                FilterChip(
                    selected = selectedEquipmentFilter == filter,
                    onClick = { onEquipmentFilterChange(filter) },
                    label = { Text(filter) }
                )
            }
        }

        if (exercises.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val hasActiveFilters = searchQuery.isNotBlank() ||
                        selectedMuscleFilter != "All" ||
                        selectedEquipmentFilter != "All"

                    if (hasActiveFilters) {
                        Text(
                            text = "No exercises found",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
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
                        onClick = { onExerciseSelected(exercise) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ExerciseListItem(
    exercise: ExerciseEntity,
    exerciseRepository: ExerciseRepository,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()

    ListItem(
        headlineContent = { Text(exercise.name) },
        supportingContent = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                if (exercise.muscleGroups.isNotBlank()) {
                    Text(
                        text = "Muscle Group: ${exercise.muscleGroups}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (exercise.equipment.isNotBlank()) {
                    Text(
                        text = "Equipment: ${exercise.equipment}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        leadingContent = {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                ExerciseInitial(exercise.name)
            }
        },
        trailingContent = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
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
