package com.example.vitruvianredux.presentation.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.vitruvianredux.domain.model.PersonalRecord
import com.example.vitruvianredux.domain.model.WeightUnit
import com.example.vitruvianredux.domain.model.WorkoutSession
import com.example.vitruvianredux.ui.theme.Spacing
import com.example.vitruvianredux.presentation.components.*

/**
 * Improved Insights Tab - Clear, actionable analytics with proper formatting
 */
@Composable
fun InsightsTab(
    prs: List<PersonalRecord>,
    workoutSessions: List<WorkoutSession>,
    exerciseRepository: com.example.vitruvianredux.data.repository.ExerciseRepository,
    modifier: Modifier = Modifier,
    weightUnit: WeightUnit = WeightUnit.KG,
    formatWeight: (Float, WeightUnit) -> String = { w, u -> "${w.toInt()} ${u.name.lowercase()}" }
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(Spacing.medium),
        verticalArrangement = Arrangement.spacedBy(Spacing.medium)
    ) {
        item {
            Text(
                text = "Insights & Analytics",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Deep analysis of your training patterns",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Training Balance - Which muscles need attention
        if (prs.isNotEmpty()) {
            item {
                TrainingBalanceCard(
                    personalRecords = prs,
                    exerciseRepository = exerciseRepository,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // Progress Velocity - How fast you're improving
        if (prs.isNotEmpty()) {
            item {
                ProgressVelocityCard(
                    personalRecords = prs,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // Consistency Score - Based on actual workout frequency
        item {
            ConsistencyScoreCard(
                workoutSessions = workoutSessions,
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Weekly Comparison - Clear week-over-week metrics
        if (workoutSessions.isNotEmpty()) {
            item {
                WeeklyComparisonCard(
                    workoutSessions = workoutSessions,
                    weightUnit = weightUnit,
                    formatWeight = formatWeight,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // Empty state
        if (prs.isEmpty() && workoutSessions.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.Insights,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "No Insights Yet",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Complete workouts to unlock insights",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
