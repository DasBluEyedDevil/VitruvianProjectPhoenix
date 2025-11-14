package com.example.vitruvianredux.presentation.components

import android.graphics.Color
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.graphics.toColorInt
import com.example.vitruvianredux.domain.model.WeightUnit
import com.example.vitruvianredux.domain.model.WorkoutMetric
import com.example.vitruvianredux.ui.theme.Spacing
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter

/**
 * Set Summary Card - Shows detailed metrics after completing a set
 * Displays peak power, average power, rep count, and a force graph
 */
@Composable
fun SetSummaryCard(
    metrics: List<WorkoutMetric>,
    peakPower: Float,
    averagePower: Float,
    repCount: Int,
    weightUnit: WeightUnit,
    formatWeight: (Float, WeightUnit) -> String,
    onContinue: () -> Unit,
    autoplayEnabled: Boolean = false,
    modifier: Modifier = Modifier
) {
    // Countdown state for autoplay
    var countdownSeconds by remember { mutableIntStateOf(5) }
    
    // Auto-advance if autoplay is enabled
    // Reset countdown whenever the card is shown (when autoplay is enabled)
    LaunchedEffect(autoplayEnabled, metrics.size) {
        if (autoplayEnabled) {
            countdownSeconds = 5
            while (countdownSeconds > 0) {
                delay(1000)
                countdownSeconds--
            }
            // Auto-advance after countdown
            onContinue()
        }
    }
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(20.dp), // Material 3 Expressive: More rounded (was 16dp)
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp), // Material 3 Expressive: Higher elevation (was 4dp)
        border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)) // Material 3 Expressive: Thicker border (was 1dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.medium),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.medium)
        ) {
            // Header with checkmark
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = "Set completed",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
                Spacer(modifier = Modifier.width(Spacing.small))
                Text(
                    "Set Complete!",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }

            // Stats Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                // Peak Power
                StatCard(
                    label = "Peak",
                    value = formatWeight(peakPower, weightUnit),
                    modifier = Modifier.weight(1f)
                )

                Spacer(modifier = Modifier.width(Spacing.small))

                // Average Power
                StatCard(
                    label = "Average",
                    value = formatWeight(averagePower, weightUnit),
                    modifier = Modifier.weight(1f)
                )

                Spacer(modifier = Modifier.width(Spacing.small))

                // Rep Count
                StatCard(
                    label = "Reps",
                    value = "$repCount",
                    modifier = Modifier.weight(1f)
                )
            }

            // Force Graph
            if (metrics.isNotEmpty()) {
                Text(
                    "Force Over Time",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                ForceGraph(
                    metrics = metrics,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                )
            }

            // Continue Button or Countdown (based on autoplay setting)
            if (autoplayEnabled) {
                // Show countdown when autoplay is enabled
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(Spacing.small)
                ) {
                    Text(
                        text = "$countdownSeconds",
                        style = MaterialTheme.typography.displayLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Continuing automatically...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                // Show continue button when autoplay is disabled
                Button(
                    onClick = onContinue,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text("Continue")
                    Spacer(modifier = Modifier.width(Spacing.small))
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = "Continue to next set",
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

/**
 * Individual stat card for displaying a metric
 */
@Composable
private fun StatCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.small),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

/**
 * Force graph showing load over time during the set
 */
@Composable
private fun ForceGraph(
    metrics: List<WorkoutMetric>,
    modifier: Modifier = Modifier
) {
    val isDark = androidx.compose.foundation.isSystemInDarkTheme()
    val textColor = if (isDark) Color.WHITE else Color.BLACK
    val gridColor = if (isDark) Color.DKGRAY else Color.LTGRAY

    AndroidView(
        factory = { context ->
            LineChart(context).apply {
                description.isEnabled = false
                setTouchEnabled(true)
                isDragEnabled = true
                setScaleEnabled(true)
                setPinchZoom(true)
                setDrawGridBackground(false)
                legend.isEnabled = false

                // Configure X axis (time)
                xAxis.apply {
                    position = XAxis.XAxisPosition.BOTTOM
                    setDrawGridLines(true)
                    this.textColor = textColor
                    this.gridColor = gridColor
                    valueFormatter = object : ValueFormatter() {
                        override fun getFormattedValue(value: Float): String {
                            return "${value.toInt()}s"
                        }
                    }
                }

                // Configure Y axis (force/load)
                axisLeft.apply {
                    setDrawGridLines(true)
                    this.textColor = textColor
                    this.gridColor = gridColor
                    axisMinimum = 0f
                }

                axisRight.isEnabled = false

                setExtraOffsets(8f, 8f, 8f, 8f)
            }
        },
        update = { chart ->
            if (metrics.isEmpty()) {
                chart.clear()
                return@AndroidView
            }

            // Create entries for the chart (time in seconds vs total load)
            val startTime = metrics.first().timestamp
            val entries = metrics.map { metric ->
                val elapsedSeconds = (metric.timestamp - startTime) / 1000f
                Entry(elapsedSeconds, metric.totalLoad)
            }

            // Create dataset
            val dataSet = LineDataSet(entries, "Force").apply {
                color = "#9333EA".toColorInt() // Purple
                setCircleColor("#9333EA".toColorInt())
                lineWidth = 2f
                circleRadius = 0f // No circles for cleaner look
                setDrawCircleHole(false)
                setDrawValues(false) // No values on points
                mode = LineDataSet.Mode.CUBIC_BEZIER
                setDrawFilled(true)
                fillColor = "#9333EA".toColorInt()
                fillAlpha = 50
            }

            chart.data = LineData(dataSet)
            chart.invalidate()
        },
        modifier = modifier
    )
}
