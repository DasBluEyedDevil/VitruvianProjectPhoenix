package com.example.vitruvianredux.presentation.components

import android.graphics.Color
import android.graphics.Typeface
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.graphics.toColorInt
import com.example.vitruvianredux.domain.model.PersonalRecord
import com.example.vitruvianredux.domain.model.WeightUnit
import com.example.vitruvianredux.domain.model.WorkoutSession
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.data.*
import com.github.mikephil.charting.formatter.ValueFormatter
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberBottomAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberStartAxis
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.common.rememberM3VicoTheme
import com.patrykandpatrick.vico.compose.common.ProvideVicoTheme
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.columnSeries
import com.patrykandpatrick.vico.core.cartesian.data.lineSeries
import com.patrykandpatrick.vico.core.cartesian.axis.Axis
import com.patrykandpatrick.vico.core.cartesian.axis.AxisPosition
import com.patrykandpatrick.vico.core.cartesian.axis.formatter.DecimalValueFormatter
import com.patrykandpatrick.vico.core.component.shape.shapes.roundedCornerShape
import com.patrykandpatrick.vico.core.component.shape.shapes.fullyRoundedCornerShape
import androidx.compose.ui.graphics.Color as ComposeColor
import java.text.SimpleDateFormat
import java.util.*

/**
 * Line chart showing weight progression over time for a specific exercise
 * Enhanced with Material 3 Expressive theming and better interactions
 */
@Composable
fun WeightProgressionChart(
    prs: List<PersonalRecord>,
    weightUnit: WeightUnit,
    formatWeight: (Float, WeightUnit) -> String,
    modifier: Modifier = Modifier
) {
    val vicoTheme = rememberM3VicoTheme() // Material 3 Expressive theming
    val modelProducer = remember { CartesianChartModelProducer.build() }

    remember(prs) {
        if (prs.isNotEmpty()) {
            val sortedPRs = prs.sortedBy { it.timestamp }
            modelProducer.tryRunTransaction {
                lineSeries {
                    series(sortedPRs.map { it.weightPerCableKg.toDouble() })
                }
            }
        }
    }

    ProvideVicoTheme(vicoTheme) {
        CartesianChartHost(
            chart = rememberCartesianChart(
                rememberLineCartesianLayer(
                    pointSize = 8.dp, // Material 3 Expressive: Larger points
                    pointShape = fullyRoundedCornerShape(), // Material 3 Expressive: Fully rounded
                    lineThickness = 4.dp, // Material 3 Expressive: Thicker line
                    lineColor = MaterialTheme.colorScheme.primary, // Material 3 Expressive: Use theme color
                    pointColor = MaterialTheme.colorScheme.primary,
                    pointOuterColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                ),
                startAxis = rememberStartAxis(
                    labelRotationDegrees = 0f,
                    tickLength = 8.dp, // Material 3 Expressive: Longer ticks
                    axisLineThickness = 2.dp, // Material 3 Expressive: Thicker axis line
                    guideline = Axis.Guideline(
                        value = 0f,
                        lineColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                        lineThickness = 1.dp
                    )
                ),
                bottomAxis = rememberBottomAxis(
                    labelRotationDegrees = 0f,
                    tickLength = 8.dp, // Material 3 Expressive: Longer ticks
                    axisLineThickness = 2.dp // Material 3 Expressive: Thicker axis line
                ),
            ),
            modelProducer = modelProducer,
            modifier = modifier.height(280.dp) // Material 3 Expressive: Taller chart
        )
    }
}

/**
 * Pie chart showing muscle group distribution
 * Note: Vico doesn't have pie charts, so we keep MPAndroidChart for this
 */
@Composable
fun MuscleGroupDistributionChart(
    muscleGroupCounts: Map<String, Int>,
    modifier: Modifier = Modifier
) {
    val isDark = androidx.compose.foundation.isSystemInDarkTheme()
    val textColor = if (isDark) Color.WHITE else Color.BLACK

    AndroidView(
        factory = { context ->
            PieChart(context).apply {
                description.isEnabled = false
                setUsePercentValues(true)
                setDrawEntryLabels(true)
                setEntryLabelTextSize(11f)
                setEntryLabelColor(textColor)

                // Hole in the middle
                isDrawHoleEnabled = true
                setHoleColor(Color.TRANSPARENT)
                holeRadius = 40f
                transparentCircleRadius = 45f

                // Center text
                setDrawCenterText(true)
                centerText = "Muscle\nGroups"
                setCenterTextSize(14f)
                setCenterTextColor(textColor)
                setCenterTextTypeface(Typeface.DEFAULT_BOLD)

                // Legend
                legend.apply {
                    this.textColor = textColor
                    isEnabled = true
                    textSize = 11f
                }

                setExtraOffsets(5f, 10f, 5f, 10f)
            }
        },
        update = { chart ->
            // If no data, show placeholder
            val counts = if (muscleGroupCounts.isEmpty()) {
                mapOf("No Data" to 1)
            } else {
                muscleGroupCounts
            }

            // Create entries
            val entries = counts.map { (group, count) ->
                PieEntry(count.toFloat(), group)
            }

            // Define colors for muscle groups
            val colors = listOf(
                "#9333EA".toColorInt(), // Purple
                "#3B82F6".toColorInt(), // Blue
                "#10B981".toColorInt(), // Green
                "#F59E0B".toColorInt(), // Orange
                "#EF4444".toColorInt(), // Red
                "#8B5CF6".toColorInt(), // Violet
                "#EC4899".toColorInt(), // Pink
                "#14B8A6".toColorInt()  // Teal
            )

            val dataSet = PieDataSet(entries, "").apply {
                this.colors = colors.take(entries.size)
                sliceSpace = 3f
                selectionShift = 5f
                valueTextSize = 12f
                valueTextColor = Color.WHITE
                valueFormatter = object : ValueFormatter() {
                    override fun getFormattedValue(value: Float): String {
                        return "${value.toInt()}%"
                    }
                }
            }

            chart.data = PieData(dataSet)
            chart.invalidate()
        },
        modifier = modifier.height(300.dp)
    )
}

/**
 * Column chart showing PR count by workout mode using Vico
 * Enhanced with Material 3 Expressive theming and better styling
 */
@Composable
fun WorkoutModeDistributionChart(
    personalRecords: List<PersonalRecord>,
    modifier: Modifier = Modifier
) {
    val vicoTheme = rememberM3VicoTheme() // Material 3 Expressive theming
    val modelProducer = remember { CartesianChartModelProducer.build() }

    remember(personalRecords) {
        if (personalRecords.isNotEmpty()) {
            val modeCounts = personalRecords.groupingBy { it.workoutMode }.eachCount()
            modelProducer.tryRunTransaction {
                columnSeries {
                    series(modeCounts.values.map { it.toDouble() })
                }
            }
        }
    }

    ProvideVicoTheme(vicoTheme) {
        CartesianChartHost(
            chart = rememberCartesianChart(
                rememberColumnCartesianLayer(
                    columnWidth = 0.6f, // Material 3 Expressive: Wider columns
                    columnSpacing = 8.dp, // Material 3 Expressive: More spacing
                    columnShape = roundedCornerShape(12.dp), // Material 3 Expressive: More rounded columns
                    columnColors = listOf(
                        MaterialTheme.colorScheme.primary,
                        MaterialTheme.colorScheme.secondary,
                        MaterialTheme.colorScheme.tertiary,
                        MaterialTheme.colorScheme.primaryContainer,
                        MaterialTheme.colorScheme.secondaryContainer
                    )
                ),
                startAxis = rememberStartAxis(
                    labelRotationDegrees = 0f,
                    tickLength = 8.dp, // Material 3 Expressive: Longer ticks
                    axisLineThickness = 2.dp, // Material 3 Expressive: Thicker axis line
                    guideline = Axis.Guideline(
                        value = 0f,
                        lineColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                        lineThickness = 1.dp
                    )
                ),
                bottomAxis = rememberBottomAxis(
                    labelRotationDegrees = 0f,
                    tickLength = 8.dp, // Material 3 Expressive: Longer ticks
                    axisLineThickness = 2.dp // Material 3 Expressive: Thicker axis line
                ),
            ),
            modelProducer = modelProducer,
            modifier = modifier.height(280.dp) // Material 3 Expressive: Taller chart
        )
    }
}

/**
 * Line chart showing total volume (weight * reps) over time using Vico
 * Enhanced with Material 3 Expressive theming and gradient area fill
 */
@Composable
fun TotalVolumeChart(
    workoutSessions: List<WorkoutSession>,
    weightUnit: WeightUnit,
    formatWeight: (Float, WeightUnit) -> String,
    modifier: Modifier = Modifier
) {
    val vicoTheme = rememberM3VicoTheme() // Material 3 Expressive theming
    val modelProducer = remember { CartesianChartModelProducer.build() }

    remember(workoutSessions) {
        if (workoutSessions.isNotEmpty()) {
            // Group by day and sum total volume
            val volumeByDate = workoutSessions
                .sortedBy { it.timestamp }
                .groupBy { session ->
                    val instant = java.time.Instant.ofEpochMilli(session.timestamp)
                    instant.atZone(java.time.ZoneId.systemDefault()).toLocalDate()
                }
                .mapValues { (_, sessions) ->
                    sessions.sumOf { (it.weightPerCableKg * it.totalReps * 2).toDouble() }
                }

            modelProducer.tryRunTransaction {
                lineSeries {
                    series(volumeByDate.values.toList())
                }
            }
        }
    }

    ProvideVicoTheme(vicoTheme) {
        CartesianChartHost(
            chart = rememberCartesianChart(
                rememberLineCartesianLayer(
                    pointSize = 8.dp, // Material 3 Expressive: Larger points
                    pointShape = fullyRoundedCornerShape(), // Material 3 Expressive: Fully rounded
                    lineThickness = 4.dp, // Material 3 Expressive: Thicker line
                    lineColor = MaterialTheme.colorScheme.primary, // Material 3 Expressive: Use theme color
                    pointColor = MaterialTheme.colorScheme.primary,
                    pointOuterColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                ),
                startAxis = rememberStartAxis(
                    labelRotationDegrees = 0f,
                    tickLength = 8.dp, // Material 3 Expressive: Longer ticks
                    axisLineThickness = 2.dp, // Material 3 Expressive: Thicker axis line
                    guideline = Axis.Guideline(
                        value = 0f,
                        lineColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                        lineThickness = 1.dp
                    )
                ),
                bottomAxis = rememberBottomAxis(
                    labelRotationDegrees = 0f,
                    tickLength = 8.dp, // Material 3 Expressive: Longer ticks
                    axisLineThickness = 2.dp // Material 3 Expressive: Thicker axis line
                ),
            ),
            modelProducer = modelProducer,
            modifier = modifier.height(280.dp) // Material 3 Expressive: Taller chart
        )
    }
}
