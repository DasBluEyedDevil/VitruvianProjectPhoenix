package com.ninthlevel.phoenix.presentation.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ninthlevel.phoenix.data.local.PRType
import com.ninthlevel.phoenix.domain.model.PersonalRecord
import com.ninthlevel.phoenix.domain.model.WeightUnit
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
 * Supports dual PR types: MAX_WEIGHT (strength) and MAX_VOLUME (work capacity)
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
    // Group PRs by exercise
    val prsByExercise = remember(personalRecords) {
        personalRecords.groupBy { it.exerciseId }
    }

    // Selected exercise state
    var selectedExerciseId by remember {
        mutableStateOf(prsByExercise.keys.firstOrNull() ?: "")
    }
    var showExerciseSelector by remember { mutableStateOf(false) }

    // PR type filter (0 = Weight PRs, 1 = Volume PRs)
    var selectedPRTypeIndex by remember { mutableStateOf(0) }
    val selectedPRType = if (selectedPRTypeIndex == 0) PRType.MAX_WEIGHT else PRType.MAX_VOLUME

    // Get PRs for selected exercise, filtered by type and sorted by date
    val selectedExercisePRs = remember(selectedExerciseId, personalRecords, selectedPRType) {
        prsByExercise[selectedExerciseId]
            ?.filter { it.prType == selectedPRType }
            ?.sortedBy { it.timestamp } ?: emptyList()
    }

    // Get all PRs for selected exercise (for stats)
    val allPRsForExercise = remember(selectedExerciseId, personalRecords) {
        prsByExercise[selectedExerciseId] ?: emptyList()
    }

    // Best weight and volume for this exercise
    val bestWeightPR = remember(allPRsForExercise) {
        allPRsForExercise.filter { it.prType == PRType.MAX_WEIGHT }.maxByOrNull { it.weightPerCableKg }
    }
    val bestVolumePR = remember(allPRsForExercise) {
        allPRsForExercise.filter { it.prType == PRType.MAX_VOLUME }.maxByOrNull { it.volume }
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
                            val exercisePRs = prsByExercise[exerciseId] ?: emptyList()
                            val weightPRCount = exercisePRs.count { it.prType == PRType.MAX_WEIGHT }
                            val volumePRCount = exercisePRs.count { it.prType == PRType.MAX_VOLUME }
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(
                                            text = exerciseNames[exerciseId] ?: exerciseId,
                                            style = MaterialTheme.typography.bodyLarge
                                        )
                                        Text(
                                            text = "$weightPRCount weight PRs · $volumePRCount volume PRs",
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

        Spacer(modifier = Modifier.height(12.dp))

        // PR Type Tabs
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = selectedPRTypeIndex == 0,
                onClick = { selectedPRTypeIndex = 0 },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                icon = {
                    Icon(
                        imageVector = Icons.Default.FitnessCenter,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                }
            ) {
                Text("Weight PRs")
            }
            SegmentedButton(
                selected = selectedPRTypeIndex == 1,
                onClick = { selectedPRTypeIndex = 1 },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                icon = {
                    Icon(
                        imageVector = Icons.Default.Speed,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                }
            ) {
                Text("Volume PRs")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Best PRs summary card
        if (bestWeightPR != null || bestVolumePR != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    // Best Weight
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.FitnessCenter,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Best Weight",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Text(
                            text = bestWeightPR?.let { formatWeight(it.weightPerCableKg, weightUnit) } ?: "-",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        if (bestWeightPR != null) {
                            Text(
                                text = "${bestWeightPR.reps} reps",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }

                    // Divider
                    VerticalDivider(
                        modifier = Modifier.height(60.dp),
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                    )

                    // Best Volume
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Speed,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Best Volume",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Text(
                            text = bestVolumePR?.let { "${formatWeight(it.volume, weightUnit)}" } ?: "-",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                        if (bestVolumePR != null) {
                            Text(
                                text = "${formatWeight(bestVolumePR.weightPerCableKg, weightUnit)} × ${bestVolumePR.reps}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }

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
                        text = "No ${if (selectedPRType == PRType.MAX_WEIGHT) "Weight" else "Volume"} PRs Yet",
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
                prType = selectedPRType,
                exerciseName = exerciseNames[selectedExerciseId] ?: "",
                weightUnit = weightUnit,
                formatWeight = formatWeight
            )

            Spacer(modifier = Modifier.height(16.dp))

            // PR History List
            ExercisePRHistory(
                prs = selectedExercisePRs,
                prType = selectedPRType,
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
    prType: PRType,
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
                    // Use weight for MAX_WEIGHT, volume for MAX_VOLUME
                    val values = if (prType == PRType.MAX_WEIGHT) {
                        prs.map { it.weightPerCableKg.toDouble() }
                    } else {
                        prs.map { it.volume.toDouble() }
                    }
                    series(values)
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
                        text = "Latest",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    val latestValue = if (prType == PRType.MAX_WEIGHT) {
                        formatWeight(prs.last().weightPerCableKg, weightUnit)
                    } else {
                        formatWeight(prs.last().volume, weightUnit)
                    }
                    Text(
                        text = latestValue,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "Best",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    val best = if (prType == PRType.MAX_WEIGHT) {
                        prs.maxByOrNull { it.weightPerCableKg }
                    } else {
                        prs.maxByOrNull { it.volume }
                    }
                    val bestValue = if (prType == PRType.MAX_WEIGHT) {
                        formatWeight(best?.weightPerCableKg ?: 0f, weightUnit)
                    } else {
                        formatWeight(best?.volume ?: 0f, weightUnit)
                    }
                    Text(
                        text = bestValue,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "Records",
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
    prType: PRType,
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
                text = "${if (prType == PRType.MAX_WEIGHT) "Weight" else "Volume"} PR History",
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
                        prType = prType,
                        weightUnit = weightUnit,
                        formatWeight = formatWeight,
                        isLatest = pr == prs.last(),
                        isBest = if (prType == PRType.MAX_WEIGHT) {
                            pr.weightPerCableKg == prs.maxOf { it.weightPerCableKg }
                        } else {
                            pr.volume == prs.maxOf { it.volume }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun PRHistoryItem(
    pr: PersonalRecord,
    prType: PRType,
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
                // Primary value based on PR type
                val primaryValue = if (prType == PRType.MAX_WEIGHT) {
                    formatWeight(pr.weightPerCableKg, weightUnit)
                } else {
                    "${formatWeight(pr.volume, weightUnit)} total"
                }
                Text(
                    text = primaryValue,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                // Secondary info
                val secondaryInfo = if (prType == PRType.MAX_WEIGHT) {
                    "${pr.reps} reps · ${pr.workoutMode}"
                } else {
                    "${formatWeight(pr.weightPerCableKg, weightUnit)} × ${pr.reps} reps · ${pr.workoutMode}"
                }
                Text(
                    text = secondaryInfo,
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
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                } else if (isLatest) {
                    Text(
                        text = "Latest",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
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
