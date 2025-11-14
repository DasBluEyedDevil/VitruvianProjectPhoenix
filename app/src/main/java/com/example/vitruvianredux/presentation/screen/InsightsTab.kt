package com.example.vitruvianredux.presentation.screen

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.vitruvianredux.domain.model.*
import com.example.vitruvianredux.presentation.components.charts.AreaChart
import com.example.vitruvianredux.presentation.components.charts.GaugeChart
import com.example.vitruvianredux.ui.theme.Spacing
import java.text.SimpleDateFormat
import java.util.*

/**
 * Material 3 Expressive Insights Tab
 * Shows advanced analytics: trends, predictions, comparisons, and anomalies
 */
@Composable
fun InsightsTab(
    prs: List<PersonalRecord>,
    workoutSessions: List<WorkoutSession>,
    modifier: Modifier = Modifier,
    weightUnit: WeightUnit = WeightUnit.KG,
    formatWeight: (Float, WeightUnit) -> String = { w, u -> "${w.toInt()} ${u.name.lowercase()}" }
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(Spacing.medium),
        verticalArrangement = Arrangement.spacedBy(Spacing.medium)
    ) {
        // Title
        item {
            Text(
                text = "Insights & Analytics",
                style = MaterialTheme.typography.headlineLarge, // Material 3 Expressive: Larger title
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = Spacing.small)
            )
        }

        // Strength Trends Section
        item {
            StrengthTrendsSection(
                prs = prs,
                weightUnit = weightUnit,
                formatWeight = formatWeight
            )
        }

        // Volume Trends Section
        item {
            VolumeTrendsSection(
                workoutSessions = workoutSessions,
                weightUnit = weightUnit,
                formatWeight = formatWeight
            )
        }

        // Consistency Analysis Section
        item {
            ConsistencyAnalysisSection(
                workoutSessions = workoutSessions
            )
        }

        // Predictive Analytics Section
        item {
            PredictiveAnalyticsSection(
                prs = prs,
                weightUnit = weightUnit,
                formatWeight = formatWeight
            )
        }

        // Comparative Analytics Section
        item {
            ComparativeAnalyticsSection(
                workoutSessions = workoutSessions,
                weightUnit = weightUnit,
                formatWeight = formatWeight
            )
        }
    }
}

/**
 * Strength Trends Section
 */
@Composable
private fun StrengthTrendsSection(
    prs: List<PersonalRecord>,
    weightUnit: WeightUnit,
    formatWeight: (Float, WeightUnit) -> String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge, // Material 3 Expressive: More rounded
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp), // Material 3 Expressive: Higher elevation
        border = CardDefaults.outlinedCardBorder().copy(width = 2.dp) // Material 3 Expressive: Thicker border
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.medium)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.TrendingUp,
                    contentDescription = "Strength trends",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp) // Material 3 Expressive: Larger icon
                )
                Spacer(modifier = Modifier.width(Spacing.small))
                Text(
                    text = "Strength Trends",
                    style = MaterialTheme.typography.titleLarge, // Material 3 Expressive: Larger text
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(Spacing.medium))

            if (prs.isNotEmpty()) {
                // Group PRs by exercise
                val prsByExercise = prs.groupBy { it.exerciseId }
                
                prsByExercise.forEach { (exerciseId, exercisePRs) ->
                    if (exercisePRs.size >= 2) {
                        val sortedPRs = exercisePRs.sortedBy { it.timestamp }
                        val chartData = sortedPRs.mapIndexed { index, pr ->
                            val date = SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(pr.timestamp))
                            date to pr.weightPerCableKg
                        }

                        Text(
                            text = "Exercise: $exerciseId",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(bottom = Spacing.small)
                        )

                        AreaChart(
                            data = chartData,
                            label = "Weight (kg)",
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp)
                        )

                        Spacer(modifier = Modifier.height(Spacing.medium))
                    }
                }
            } else {
                Text(
                    text = "Not enough data for trend analysis",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Volume Trends Section
 */
@Composable
private fun VolumeTrendsSection(
    workoutSessions: List<WorkoutSession>,
    weightUnit: WeightUnit,
    formatWeight: (Float, WeightUnit) -> String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        border = CardDefaults.outlinedCardBorder().copy(width = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.medium)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.BarChart,
                    contentDescription = "Volume trends",
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(Spacing.small))
                Text(
                    text = "Volume Trends",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(Spacing.medium))

            if (workoutSessions.isNotEmpty()) {
                // Calculate weekly volume
                val weeklyVolume = workoutSessions
                    .groupBy { session ->
                        val calendar = Calendar.getInstance().apply {
                            timeInMillis = session.timestamp
                        }
                        calendar.get(Calendar.WEEK_OF_YEAR)
                    }
                    .mapValues { (_, sessions) ->
                        sessions.sumOf { it.weightPerCableKg * it.totalReps * 2 }.toFloat() // 2 cables
                    }

                val chartData = weeklyVolume.entries.sortedBy { it.key }.map { (week, volume) ->
                    "Week $week" to volume
                }

                AreaChart(
                    data = chartData,
                    label = "Volume (kg)",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                )
            } else {
                Text(
                    text = "Not enough data for volume analysis",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Consistency Analysis Section
 */
@Composable
private fun ConsistencyAnalysisSection(
    workoutSessions: List<WorkoutSession>
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        border = CardDefaults.outlinedCardBorder().copy(width = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.medium)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.CalendarToday,
                    contentDescription = "Consistency analysis",
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(Spacing.small))
                Text(
                    text = "Consistency Analysis",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(Spacing.medium))

            if (workoutSessions.isNotEmpty()) {
                val totalWorkouts = workoutSessions.size
                val daysSinceFirst = if (workoutSessions.isNotEmpty()) {
                    val firstWorkout = workoutSessions.minOf { it.timestamp }
                    val days = ((System.currentTimeMillis() - firstWorkout) / (24 * 60 * 60 * 1000L)).toInt()
                    days.coerceAtLeast(1)
                } else {
                    1
                }

                val workoutsPerWeek = (totalWorkouts.toFloat() / daysSinceFirst) * 7f
                val consistencyScore = (workoutsPerWeek / 5f).coerceIn(0f, 1f) // Target: 5 workouts/week

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    GaugeChart(
                        currentValue = workoutsPerWeek,
                        targetValue = 5f,
                        label = "Workouts/Week",
                        modifier = Modifier.weight(1f)
                    )
                    GaugeChart(
                        currentValue = consistencyScore * 100f,
                        targetValue = 100f,
                        label = "Consistency",
                        modifier = Modifier.weight(1f)
                    )
                }
            } else {
                Text(
                    text = "Not enough data for consistency analysis",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Predictive Analytics Section
 */
@Composable
private fun PredictiveAnalyticsSection(
    prs: List<PersonalRecord>,
    weightUnit: WeightUnit,
    formatWeight: (Float, WeightUnit) -> String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        border = CardDefaults.outlinedCardBorder().copy(width = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.medium)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = "Predictive analytics",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(Spacing.small))
                Text(
                    text = "Predictions",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(Spacing.medium))

            if (prs.size >= 3) {
                // Group by exercise and show predictions
                val prsByExercise = prs.groupBy { it.exerciseId }
                
                prsByExercise.forEach { (exerciseId, exercisePRs) ->
                    if (exercisePRs.size >= 3) {
                        val sortedPRs = exercisePRs.sortedBy { it.timestamp }
                        val latestPR = sortedPRs.last()
                        
                        // Simple linear prediction
                        val firstPR = sortedPRs.first()
                        val timeDiff = latestPR.timestamp - firstPR.timestamp
                        val weightDiff = latestPR.weightPerCableKg - firstPR.weightPerCableKg
                        
                        if (timeDiff > 0) {
                            val weightPerDay = weightDiff / (timeDiff / (24f * 60f * 60f * 1000f))
                            val predictedWeight30Days = latestPR.weightPerCableKg + (weightPerDay * 30f)
                            
                            PredictionCard(
                                title = exerciseId,
                                currentValue = formatWeight(latestPR.weightPerCableKg, weightUnit),
                                predictedValue = formatWeight(predictedWeight30Days, weightUnit),
                                predictedDate = "30 days",
                                confidence = 0.7f
                            )
                        }
                    }
                }
            } else {
                Text(
                    text = "Need at least 3 PRs for predictions",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Comparative Analytics Section
 */
@Composable
private fun ComparativeAnalyticsSection(
    workoutSessions: List<WorkoutSession>,
    weightUnit: WeightUnit,
    formatWeight: (Float, WeightUnit) -> String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        border = CardDefaults.outlinedCardBorder().copy(width = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.medium)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.CompareArrows,
                    contentDescription = "Comparative analytics",
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(Spacing.small))
                Text(
                    text = "Period Comparison",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(Spacing.medium))

            if (workoutSessions.size >= 10) {
                val now = System.currentTimeMillis()
                val weekAgo = now - (7 * 24 * 60 * 60 * 1000L)
                val twoWeeksAgo = weekAgo - (7 * 24 * 60 * 60 * 1000L)

                val thisWeek = workoutSessions.filter { it.timestamp >= weekAgo }
                val lastWeek = workoutSessions.filter { it.timestamp in twoWeeksAgo until weekAgo }

                val thisWeekVolume = thisWeek.sumOf { it.weightPerCableKg * it.totalReps * 2 }.toFloat()
                val lastWeekVolume = lastWeek.sumOf { it.weightPerCableKg * it.totalReps * 2 }.toFloat()

                val change = if (lastWeekVolume > 0) {
                    ((thisWeekVolume - lastWeekVolume) / lastWeekVolume) * 100f
                } else {
                    0f
                }

                ComparisonCard(
                    metric = "Weekly Volume",
                    thisPeriodValue = formatWeight(thisWeekVolume, weightUnit),
                    previousPeriodValue = formatWeight(lastWeekVolume, weightUnit),
                    changePercentage = change
                )
            } else {
                Text(
                    text = "Need at least 10 workouts for comparison",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Prediction Card Component
 */
@Composable
private fun PredictionCard(
    title: String,
    currentValue: String,
    predictedValue: String,
    predictedDate: String,
    confidence: Float
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.small),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
        border = MaterialTheme.shapes.extraLarge.outline.copy(
            width = 1.dp,
            brush = null,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.medium)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(Spacing.small))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "Current",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = currentValue,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                }
                Column {
                    Text(
                        text = "Predicted ($predictedDate)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = predictedValue,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Spacer(modifier = Modifier.height(Spacing.small))
            LinearProgressIndicator(
                progress = confidence,
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
            )
            Text(
                text = "Confidence: ${(confidence * 100).toInt()}%",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.extraSmall)
            )
        }
    }
}

/**
 * Comparison Card Component
 */
@Composable
private fun ComparisonCard(
    metric: String,
    thisPeriodValue: String,
    previousPeriodValue: String,
    changePercentage: Float
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHighest
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.medium)
        ) {
            Text(
                text = metric,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(Spacing.small))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "This Week",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = thisPeriodValue,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                }
                Column {
                    Text(
                        text = "Last Week",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = previousPeriodValue,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "Change",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${if (changePercentage >= 0) "+" else ""}${changePercentage.toInt()}%",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (changePercentage >= 0) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.error
                        }
                    )
                }
            }
        }
    }
}

