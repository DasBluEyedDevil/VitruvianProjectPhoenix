package com.example.vitruvianredux.presentation.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.vitruvianredux.domain.model.PersonalRecord
import com.example.vitruvianredux.domain.model.WeightUnit
import com.example.vitruvianredux.domain.model.WorkoutSession
import com.example.vitruvianredux.ui.theme.Spacing
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Training Balance Card - Shows which muscle groups need attention
 */
@Composable
fun TrainingBalanceCard(
    personalRecords: List<PersonalRecord>,
    exerciseRepository: com.example.vitruvianredux.data.repository.ExerciseRepository,
    modifier: Modifier = Modifier
) {
    val muscleGroupCounts = remember { mutableStateMapOf<String, Int>() }
    
    LaunchedEffect(personalRecords) {
        val counts = mutableMapOf<String, Int>()
        personalRecords.groupBy { it.exerciseId }.forEach { (exerciseId, prs) ->
            @Suppress("SwallowedException")
            try {
                val exercise = exerciseRepository.getExerciseById(exerciseId)
                exercise?.muscleGroups?.split(",")?.forEach { group ->
                    val trimmed = group.trim()
                    if (trimmed.isNotBlank()) {
                        counts[trimmed] = counts.getOrDefault(trimmed, 0) + prs.size
                    }
                }
            } catch (e: Exception) {
                // Skip if exercise not found
            }
        }
        muscleGroupCounts.clear()
        muscleGroupCounts.putAll(counts)
    }

    if (muscleGroupCounts.isEmpty()) return

    val totalPRs = muscleGroupCounts.values.sum()
    val sortedGroups = muscleGroupCounts.entries.sortedByDescending { it.value }.take(5)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .shadow(8.dp, RoundedCornerShape(20.dp)),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
        ),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.FitnessCenter,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "Training Balance",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Which muscle groups you're focusing on",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            sortedGroups.forEach { (group, count) ->
                val percentage = (count.toFloat() / totalPRs * 100).roundToInt()
                
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = group,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "$percentage%",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { count.toFloat() / totalPRs },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }

            // Recommendation
            val leastTrained = muscleGroupCounts.entries.minByOrNull { it.value }
            if (leastTrained != null && sortedGroups.size > 2) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.TipsAndUpdates,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Consider adding more ${leastTrained.key} exercises",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                }
            }
        }
    }
}

/**
 * Progress Velocity Card - Shows how fast you're improving
 */
@Composable
fun ProgressVelocityCard(
    personalRecords: List<PersonalRecord>,
    modifier: Modifier = Modifier
) {
    val prsByMonth = remember(personalRecords) {
        personalRecords
            .groupBy {
                val instant = Instant.ofEpochMilli(it.timestamp)
                val date = instant.atZone(ZoneId.systemDefault()).toLocalDate()
                "${date.year}-${date.monthValue.toString().padStart(2, '0')}"
            }
            .mapValues { it.value.size }
            .entries
            .sortedBy { it.key }
            .takeLast(6)
    }

    val avgPRsPerMonth = if (prsByMonth.isNotEmpty()) {
        prsByMonth.map { it.value }.average()
    } else 0.0

    val trend = if (prsByMonth.size >= 2) {
        val recent = prsByMonth.takeLast(3).map { it.value }.average()
        val older = prsByMonth.take(3).map { it.value }.average()
        when {
            recent > older * 1.1 -> "Accelerating"
            recent < older * 0.9 -> "Slowing"
            else -> "Steady"
        }
    } else "Too early"

    Card(
        modifier = modifier
            .fillMaxWidth()
            .shadow(8.dp, RoundedCornerShape(20.dp)),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
        ),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Speed,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "Progress Velocity",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "How fast you're improving",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = String.format(Locale.getDefault(), "%.1f", avgPRsPerMonth),
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "PRs/Month",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Divider(
                    modifier = Modifier
                        .height(60.dp)
                        .width(1.dp)
                )

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val trendIcon = when (trend) {
                        "Accelerating" -> Icons.AutoMirrored.Filled.TrendingUp
                        "Slowing" -> Icons.AutoMirrored.Filled.TrendingDown
                        else -> Icons.AutoMirrored.Filled.TrendingFlat
                    }
                    val trendColor = when (trend) {
                        "Accelerating" -> Color(0xFF10B981)
                        "Slowing" -> Color(0xFFEF4444)
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                    
                    Icon(
                        trendIcon,
                        contentDescription = null,
                        tint = trendColor,
                        modifier = Modifier.size(40.dp)
                    )
                    Text(
                        text = trend,
                        style = MaterialTheme.typography.bodyMedium,
                        color = trendColor,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            if (prsByMonth.size >= 3) {
            Spacer(modifier = Modifier.height(16.dp))
            @Suppress("NAME_SHADOWING")
            Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Insights,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (trend == "Accelerating") {
                                "Great momentum! Keep it up."
                            } else if (trend == "Slowing") {
                                "Try mixing up your routine for fresh stimulus"
                            } else {
                                "Consistent progress is sustainable progress"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
        }
    }
}

/**
 * Consistency Score Card - Actual meaningful consistency metric
 */
@Composable
fun ConsistencyScoreCard(
    workoutSessions: List<WorkoutSession>,
    modifier: Modifier = Modifier
) {
    val last30Days = remember(workoutSessions) {
        val thirtyDaysAgo = System.currentTimeMillis() - (30 * 24 * 60 * 60 * 1000)
        workoutSessions.filter { it.timestamp >= thirtyDaysAgo }
    }

    val workoutsThisMonth = last30Days.size
    val targetWorkoutsPerMonth = 12 // 3 per week
    val consistencyScore = ((workoutsThisMonth.toFloat() / targetWorkoutsPerMonth) * 100)
        .coerceAtMost(100f)
        .roundToInt()

    val color = when {
        consistencyScore >= 80 -> Color(0xFF10B981)
        consistencyScore >= 50 -> Color(0xFFF59E0B)
        else -> Color(0xFFEF4444)
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .shadow(8.dp, RoundedCornerShape(20.dp)),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
        ),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.CalendarMonth,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "Consistency Score",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Based on $workoutsThisMonth workouts in last 30 days",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    progress = { consistencyScore / 100f },
                    modifier = Modifier.size(140.dp),
                    color = color,
                    strokeWidth = 12.dp,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "$consistencyScore%",
                        style = MaterialTheme.typography.displayMedium,
                        fontWeight = FontWeight.Bold,
                        color = color
                    )
                    Text(
                        text = "Consistency",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = color.copy(alpha = 0.1f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Target",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "$targetWorkoutsPerMonth workouts/month",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Actual",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "$workoutsThisMonth workouts",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = color
                        )
                    }
                }
            }
        }
    }
}

/**
 * Weekly Comparison Card - Clear this week vs last week
 */
@Composable
fun WeeklyComparisonCard(
    workoutSessions: List<WorkoutSession>,
    weightUnit: WeightUnit,
    formatWeight: (Float, WeightUnit) -> String,
    modifier: Modifier = Modifier
) {
    val now = Instant.now().atZone(ZoneId.systemDefault())
    val thisWeekStart = now.truncatedTo(ChronoUnit.DAYS)
        .with(java.time.DayOfWeek.MONDAY).toInstant().toEpochMilli()
    val lastWeekStart = now.minusWeeks(1).truncatedTo(ChronoUnit.DAYS)
        .with(java.time.DayOfWeek.MONDAY).toInstant().toEpochMilli()

    val thisWeekSessions = workoutSessions.filter { it.timestamp >= thisWeekStart }
    val lastWeekSessions = workoutSessions.filter { 
        it.timestamp >= lastWeekStart && it.timestamp < thisWeekStart 
    }

    val thisWeekVolume = thisWeekSessions.sumOf { 
        (it.weightPerCableKg * it.totalReps * 2).toDouble() 
    }.toFloat()
    val lastWeekVolume = lastWeekSessions.sumOf { 
        (it.weightPerCableKg * it.totalReps * 2).toDouble() 
    }.toFloat()

    val change = if (lastWeekVolume > 0) {
        ((thisWeekVolume - lastWeekVolume) / lastWeekVolume * 100).roundToInt()
    } else if (thisWeekVolume > 0) {
        100
    } else {
        0
    }

    val changeColor = when {
        change > 0 -> Color(0xFF10B981)
        change < 0 -> Color(0xFFEF4444)
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .shadow(8.dp, RoundedCornerShape(20.dp)),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
        ),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.AutoMirrored.Filled.CompareArrows,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "Weekly Comparison",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "This week vs last week",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "This Week",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = formatWeight(thisWeekVolume, weightUnit),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${thisWeekSessions.size} workouts",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Last Week",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = formatWeight(lastWeekVolume, weightUnit),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                    Text(
                        text = "${lastWeekSessions.size} workouts",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (lastWeekVolume > 0 || thisWeekVolume > 0) {
                Spacer(modifier = Modifier.height(16.dp))
                
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = changeColor.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            if (change > 0) Icons.AutoMirrored.Filled.TrendingUp 
                            else if (change < 0) Icons.AutoMirrored.Filled.TrendingDown 
                            else Icons.AutoMirrored.Filled.TrendingFlat,
                            contentDescription = null,
                            tint = changeColor,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "${if (change > 0) "+" else ""}$change% vs last week",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = changeColor
                        )
                    }
                }
            }
        }
    }
}

