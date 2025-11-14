package com.example.vitruvianredux.presentation.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.vitruvianredux.domain.model.PersonalRecord
import com.example.vitruvianredux.domain.model.WeightUnit
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.*
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.common.ProvideVicoTheme
import com.patrykandpatrick.vico.compose.m3.common.rememberM3VicoTheme
import com.patrykandpatrick.vico.core.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.core.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.lineSeries
import java.text.SimpleDateFormat
import java.util.*

/**
 * Exercise-specific PR tracking component
 * Shows progression over time for a selected exercise with detailed PR history
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExercisePRTracker(
    personalRecords: List<PersonalRecord>,
    exerciseNames: Map<String, String>, // exerciseId -> exercise name
    weightUnit: WeightUnit,
    formatWeight: (Float, WeightUnit) -> String,
    modifier: Modifier = Modifier
) {
    // Group PRs by exercise (don't filter by exerciseNames - they load async!)
    val prsByExercise = remember(personalRecords) {
        personalRecords.groupBy { it.exerciseId }
    }

    // Selected exercise state
    var selectedExerciseId by remember {
        mutableStateOf(prsByExercise.keys.firstOrNull() ?: "")
    }
    var showExerciseSelector by remember { mutableStateOf(false) }

    // Get PRs for selected exercise, sorted by date
    val selectedExercisePRs = remember(selectedExerciseId, personalRecords) {
        prsByExercise[selectedExerciseId]?.sortedBy { it.timestamp } ?: emptyList()
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        // Header with exercise selector
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.TrendingUp,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Exercise PR Tracker",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Exercise selector button
                ExposedDropdownMenuBox(
                    expanded = showExerciseSelector,
                    onExpandedChange = { showExerciseSelector = it }
                ) {
                    OutlinedButton(
                        onClick = { showExerciseSelector = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(type = ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Text(
                            text = exerciseNames[selectedExerciseId] ?: "Select Exercise",
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = "Select exercise"
                        )
                    }

                    ExposedDropdownMenu(
                        expanded = showExerciseSelector,
                        onDismissRequest = { showExerciseSelector = false }
                    ) {
                        prsByExercise.keys.sortedBy { exerciseNames[it] }.forEach { exerciseId ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(
                                            text = exerciseNames[exerciseId] ?: exerciseId,
                                            style = MaterialTheme.typography.bodyLarge
                                        )
                                        Text(
                                            text = "${prsByExercise[exerciseId]?.size} PRs",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                },
                                onClick = {
                                    selectedExerciseId = exerciseId
                                    showExerciseSelector = false
                                }
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (selectedExercisePRs.isEmpty()) {
            // No data state
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "No PRs Yet",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Start working out to track your progress!",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            // PR Chart
            ExercisePRChart(
                prs = selectedExercisePRs,
                exerciseName = exerciseNames[selectedExerciseId] ?: "",
                weightUnit = weightUnit,
                formatWeight = formatWeight
            )

            Spacer(modifier = Modifier.height(16.dp))

            // PR History List
            ExercisePRHistory(
                prs = selectedExercisePRs,
                weightUnit = weightUnit,
                formatWeight = formatWeight
            )
        }
    }
}

/**
 * Chart showing PR progression over time for a specific exercise
 */
@Composable
private fun ExercisePRChart(
    prs: List<PersonalRecord>,
    exerciseName: String,
    weightUnit: WeightUnit,
    formatWeight: (Float, WeightUnit) -> String
) {
    val vicoTheme = rememberM3VicoTheme()
    val modelProducer = remember { CartesianChartModelProducer() }

    LaunchedEffect(prs) {
        if (prs.isNotEmpty()) {
            modelProducer.runTransaction {
                lineSeries {
                    series(prs.map { it.weightPerCableKg.toDouble() })
                }
            }
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Stats row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "Latest PR",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = formatWeight(prs.last().weightPerCableKg, weightUnit),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "All-Time Best",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    val best = prs.maxByOrNull { it.weightPerCableKg }
                    Text(
                        text = formatWeight(best?.weightPerCableKg ?: 0f, weightUnit),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "Total PRs",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = prs.size.toString(),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Chart
            ProvideVicoTheme(vicoTheme) {
                CartesianChartHost(
                    chart = rememberCartesianChart(
                        rememberLineCartesianLayer(),
                        startAxis = VerticalAxis.rememberStart(
                            label = rememberAxisLabelComponent(
                                color = MaterialTheme.colorScheme.onSurface,
                                textSize = 12.sp
                            )
                        ),
                        bottomAxis = HorizontalAxis.rememberBottom(
                            label = rememberAxisLabelComponent(
                                color = MaterialTheme.colorScheme.onSurface,
                                textSize = 12.sp
                            )
                        )
                    ),
                    modelProducer = modelProducer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                )
            }
        }
    }
}

/**
 * List showing detailed PR history for a specific exercise
 */
@Composable
private fun ExercisePRHistory(
    prs: List<PersonalRecord>,
    weightUnit: WeightUnit,
    formatWeight: (Float, WeightUnit) -> String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "PR History",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))

            LazyColumn(
                modifier = Modifier.heightIn(max = 400.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(prs.reversed()) { pr ->
                    PRHistoryItem(
                        pr = pr,
                        weightUnit = weightUnit,
                        formatWeight = formatWeight,
                        isLatest = pr == prs.last(),
                        isBest = pr.weightPerCableKg == prs.maxOf { it.weightPerCableKg }
                    )
                }
            }
        }
    }
}

@Composable
private fun PRHistoryItem(
    pr: PersonalRecord,
    weightUnit: WeightUnit,
    formatWeight: (Float, WeightUnit) -> String,
    isLatest: Boolean,
    isBest: Boolean
) {
    val dateFormat = remember { SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()) }
    val backgroundColor = when {
        isBest -> MaterialTheme.colorScheme.tertiaryContainer
        isLatest -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = backgroundColor,
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = formatWeight(pr.weightPerCableKg, weightUnit),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${pr.reps} reps · ${pr.workoutMode}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                if (isBest) {
                    Text(
                        text = "🏆 Best",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                } else if (isLatest) {
                    Text(
                        text = "Latest",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Text(
                    text = dateFormat.format(Date(pr.timestamp)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

