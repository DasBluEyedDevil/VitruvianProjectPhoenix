package com.example.vitruvianredux.presentation.components

import android.graphics.Color
import android.graphics.Typeface
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.graphics.toColorInt
import com.example.vitruvianredux.domain.model.PersonalRecord
import com.example.vitruvianredux.domain.model.WeightUnit
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.*
import com.github.mikephil.charting.formatter.ValueFormatter
import java.text.SimpleDateFormat
import java.util.*

/**
 * Line chart showing weight progression over time for a specific exercise
 */
@Composable
fun WeightProgressionChart(
    prs: List<PersonalRecord>,
    weightUnit: WeightUnit,
    formatWeight: (Float, WeightUnit) -> String,
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

                // Configure X axis (time)
                xAxis.apply {
                    position = XAxis.XAxisPosition.BOTTOM
                    setDrawGridLines(true)
                    granularity = 1f
                    this.textColor = textColor
                    this.gridColor = gridColor
                    valueFormatter = object : ValueFormatter() {
                        private val dateFormat = SimpleDateFormat("MMM d", Locale.getDefault())
                        override fun getFormattedValue(value: Float): String {
                            return dateFormat.format(Date(value.toLong()))
                        }
                    }
                }

                // Configure Y axis (weight)
                axisLeft.apply {
                    setDrawGridLines(true)
                    this.textColor = textColor
                    this.gridColor = gridColor
                    axisMinimum = 0f
                }

                axisRight.isEnabled = false

                // Legend
                legend.apply {
                    this.textColor = textColor
                    isEnabled = true
                }

                setExtraOffsets(8f, 8f, 8f, 8f)
            }
        },
        update = { chart ->
            // Sort PRs by timestamp (oldest to newest)
            val sortedPRs = prs.sortedBy { it.timestamp }

            // Create entries for the chart
            val entries = sortedPRs.map { pr ->
                Entry(pr.timestamp.toFloat(), pr.weightPerCableKg)
            }

            if (entries.isEmpty()) {
                chart.clear()
                return@AndroidView
            }

            // Create dataset
            val dataSet = LineDataSet(entries, "Weight Per Cable (${weightUnit.name})").apply {
                color = "#9333EA".toColorInt() // Purple
                setCircleColor("#9333EA".toColorInt())
                lineWidth = 3f
                circleRadius = 5f
                setDrawCircleHole(false)
                valueTextSize = 10f
                valueTextColor = textColor
                setDrawValues(true)
                mode = LineDataSet.Mode.CUBIC_BEZIER
                setDrawFilled(true)
                fillColor = "#9333EA".toColorInt()
                fillAlpha = 50

                // Custom value formatter to show weight in correct unit
                valueFormatter = object : ValueFormatter() {
                    override fun getFormattedValue(value: Float): String {
                        return formatWeight(value, weightUnit)
                    }
                }
            }

            chart.data = LineData(dataSet)
            chart.invalidate()
        },
        modifier = modifier.height(250.dp)
    )
}

/**
 * Pie chart showing muscle group distribution
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
 * Bar chart showing PR count by workout mode
 */
@Composable
fun WorkoutModeDistributionChart(
    personalRecords: List<PersonalRecord>,
    modifier: Modifier = Modifier
) {
    val isDark = androidx.compose.foundation.isSystemInDarkTheme()
    val textColor = if (isDark) Color.WHITE else Color.BLACK
    val gridColor = if (isDark) Color.DKGRAY else Color.LTGRAY

    AndroidView(
        factory = { context ->
            com.github.mikephil.charting.charts.BarChart(context).apply {
                description.isEnabled = false
                setDrawGridBackground(false)
                setDrawBarShadow(false)
                setDrawValueAboveBar(true)

                // Configure X axis
                xAxis.apply {
                    position = XAxis.XAxisPosition.BOTTOM
                    setDrawGridLines(false)
                    this.textColor = textColor
                    granularity = 1f
                }

                // Configure Y axis
                axisLeft.apply {
                    this.textColor = textColor
                    this.gridColor = gridColor
                    axisMinimum = 0f
                }

                axisRight.isEnabled = false

                // Legend
                legend.apply {
                    this.textColor = textColor
                    isEnabled = true
                }

                setExtraOffsets(8f, 8f, 8f, 8f)
            }
        },
        update = { chart ->
            // Count PRs by workout mode
            val modeCounts = personalRecords.groupingBy { it.workoutMode }.eachCount()

            if (modeCounts.isEmpty()) {
                chart.clear()
                return@AndroidView
            }

            // Create entries
            val entries = modeCounts.entries.mapIndexed { index, (mode, count) ->
                BarEntry(index.toFloat(), count.toFloat())
            }

            val dataSet = BarDataSet(entries, "PRs by Mode").apply {
                color = "#9333EA".toColorInt()
                valueTextSize = 12f
                valueTextColor = textColor
            }

            // Configure X axis labels
            chart.xAxis.valueFormatter = object : ValueFormatter() {
                private val modes = modeCounts.keys.toList()
                override fun getFormattedValue(value: Float): String {
                    return modes.getOrNull(value.toInt()) ?: ""
                }
            }

            chart.data = BarData(dataSet)
            chart.invalidate()
        },
        modifier = modifier.height(250.dp)
    )
}
