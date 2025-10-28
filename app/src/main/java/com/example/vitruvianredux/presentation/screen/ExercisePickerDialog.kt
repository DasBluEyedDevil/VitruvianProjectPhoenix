package com.example.vitruvianredux.presentation.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.vitruvianredux.domain.model.Exercise
import com.example.vitruvianredux.domain.model.ExerciseCategory
import com.example.vitruvianredux.ui.theme.*
import java.util.*

@Composable
fun ExercisePickerDialog(
    onExerciseSelected: (Exercise) -> Unit,
    onDismiss: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    
    val filteredExercises = remember(searchQuery) {
        if (searchQuery.isBlank()) {
            Exercise.entries
        } else {
            Exercise.entries.filter { exercise ->
                exercise.displayName.contains(searchQuery, ignoreCase = true) ||
                exercise.category.name.contains(searchQuery, ignoreCase = true)
            }
        }
    }
    
    val exercisesByCategory = remember(filteredExercises) {
        filteredExercises.groupBy { it.category }
            .toSortedMap(compareBy { it.name })
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.8f),
            shape = RoundedCornerShape(16.dp),
            color = SurfaceDarkGrey
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
                        "Select Exercise",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Close",
                            tint = TextSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(Spacing.medium))

                // Search field
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = { Text("Search exercises", color = TextSecondary) },
                    leadingIcon = {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = "Search",
                            tint = TextTertiary
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PrimaryPurple,
                        unfocusedBorderColor = TextTertiary,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        cursorColor = PrimaryPurple
                    ),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(Spacing.medium))

                // Exercise list grouped by category
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(Spacing.small)
                ) {
                    if (exercisesByCategory.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(Spacing.large),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "No exercises found",
                                    color = TextTertiary,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    } else {
                        exercisesByCategory.forEach { (category, exercises) ->
                            item {
                                CategoryHeader(category)
                            }
                            items(exercises) { exercise ->
                                ExerciseItem(
                                    exercise = exercise,
                                    onClick = { onExerciseSelected(exercise) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CategoryHeader(category: ExerciseCategory) {
    Text(
        text = formatCategoryName(category),
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = PrimaryPurple,
        modifier = Modifier.padding(vertical = Spacing.small)
    )
}

@Composable
fun ExerciseItem(
    exercise: Exercise,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = CardBackground),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.medium),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                exercise.displayName,
                style = MaterialTheme.typography.bodyLarge,
                color = TextPrimary
            )
            Text(
                formatCategoryName(exercise.category),
                style = MaterialTheme.typography.bodySmall,
                color = TextTertiary
            )
        }
    }
}

private fun formatCategoryName(category: ExerciseCategory): String {
    return category.name
        .split('_')
        .joinToString(" ") { word ->
            word.lowercase().replaceFirstChar { 
                if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() 
            }
        }
}
