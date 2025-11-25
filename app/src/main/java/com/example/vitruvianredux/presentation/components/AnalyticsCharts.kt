@file:Suppress("unused")  // Chart components - exported for reuse

package com.example.vitruvianredux.presentation.components

import android.graphics.Typeface
import androidx.core.graphics.toColorInt
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.vitruvianredux.domain.model.PersonalRecord
import com.example.vitruvianredux.domain.model.WeightUnit
import com.example.vitruvianredux.domain.model.WorkoutSession
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.github.mikephil.charting.formatter.ValueFormatter
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberBottom
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberStart
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoScrollState
import com.patrykandpatrick.vico.compose.common.component.rememberLineComponent
import com.patrykandpatrick.vico.compose.common.fill
import com.patrykandpatrick.vico.compose.m3.common.rememberM3VicoTheme
import com.patrykandpatrick.vico.compose.common.ProvideVicoTheme
import com.patrykandpatrick.vico.core.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.core.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.core.cartesian.data.columnSeries
import com.patrykandpatrick.vico.core.cartesian.data.lineSeries
import com.patrykandpatrick.vico.core.cartesian.layer.ColumnCartesianLayer
import com.patrykandpatrick.vico.core.cartesian.layer.LineCartesianLayer
import java.text.SimpleDateFormat
import java.util.*

/**
 * Tonal-Style Weight Progression Chart
 * - Uses Cubic Bezier curves for smooth "Strength Score" look
 * - Date formatted X-axis
 */
@Suppress("UNUSED_PARAMETER")  // formatWeight kept for future tooltip implementation
@Composable
fun WeightProgressionChart(
    prs: List<PersonalRecord>,
    weightUnit: WeightUnit,
    formatWeight: (Float, WeightUnit) -> String,
    modifier: Modifier = Modifier
) {
    val modelProducer = remember { CartesianChartModelProducer() }
    val primaryColor = MaterialTheme.colorScheme.primary
    
    // Process Data
    val sortedPRs = remember(prs) { prs.sortedBy { it.timestamp } }
    
    LaunchedEffect(sortedPRs) {
        if (sortedPRs.isNotEmpty()) {
            modelProducer.runTransaction {
                lineSeries {
                    series(
                        x = sortedPRs.map { it.timestamp.toFloat() },
                        y = sortedPRs.map { it.weightPerCableKg.toDouble() }
                    )
                }
            }
        }
    }

    // Axis Formatter for Dates
    val dateFormatter = remember {
        CartesianValueFormatter { x, _, _ ->
            val date = Date((x as Number).toLong())
            SimpleDateFormat("MMM d", Locale.getDefault()).format(date)
        }
    }

    ProvideVicoTheme(rememberM3VicoTheme()) {
        CartesianChartHost(
            chart = rememberCartesianChart(
                rememberLineCartesianLayer(
                    lineProvider = LineCartesianLayer.LineProvider.series(
                        LineCartesianLayer.Line(
                            fill = LineCartesianLayer.LineFill.single(fill(primaryColor)),
                            areaFill = LineCartesianLayer.AreaFill.single(
                                fill(primaryColor.copy(alpha = 0.2f))
                            ),
                            pointConnector = LineCartesianLayer.PointConnector.cubic(curvature = 0.2f)
                        )
                    )
                ),
                startAxis = VerticalAxis.rememberStart(
                    itemPlacer = VerticalAxis.ItemPlacer.step(step = { 5.0 })
                ),
                bottomAxis = HorizontalAxis.rememberBottom(
                    valueFormatter = dateFormatter,
                    labelRotationDegrees = 0f
                )
            ),
            modelProducer = modelProducer,
            scrollState = rememberVicoScrollState(scrollEnabled = true),
            modifier = modifier.height(280.dp)
        )
    }
}

/**
 * Tonal-Style Volume Trend Chart
 * - Uses Columns (Bars) to show work capacity per session/day
 */
@Suppress("UNUSED_PARAMETER")  // formatWeight kept for future tooltip implementation
@Composable
fun VolumeTrendChart(
    workoutSessions: List<WorkoutSession>,
    weightUnit: WeightUnit,
    formatWeight: (Float, WeightUnit) -> String,
    modifier: Modifier = Modifier
) {
    val modelProducer = remember { CartesianChartModelProducer() }
    val primaryColor = MaterialTheme.colorScheme.tertiary

    // Group by Date
    LaunchedEffect(workoutSessions) {
        if (workoutSessions.isNotEmpty()) {
            val volumeByDate = workoutSessions
                .sortedBy { it.timestamp }
                .groupBy { session ->
                    // Group by day
                    val calendar = Calendar.getInstance()
                    calendar.timeInMillis = session.timestamp
                    calendar.set(Calendar.HOUR_OF_DAY, 0)
                    calendar.set(Calendar.MINUTE, 0)
                    calendar.set(Calendar.SECOND, 0)
                    calendar.set(Calendar.MILLISECOND, 0)
                    calendar.timeInMillis
                }
                .map { (date, sessions) ->
                    // Sum volume for the day
                    val totalVolume = sessions.sumOf { (it.weightPerCableKg * it.totalReps * 2).toDouble() }
                    date.toFloat() to totalVolume
                }
                .sortedBy { it.first }

            modelProducer.runTransaction {
                columnSeries {
                    series(
                        x = volumeByDate.map { it.first },
                        y = volumeByDate.map { it.second }
                    )
                }
            }
        }
    }

    val dateFormatter = remember {
        CartesianValueFormatter { x, _, _ ->
            val date = Date((x as Number).toLong())
            SimpleDateFormat("MMM d", Locale.getDefault()).format(date)
        }
    }

    ProvideVicoTheme(rememberM3VicoTheme()) {
        CartesianChartHost(
            chart = rememberCartesianChart(
                rememberColumnCartesianLayer(
                    columnProvider = ColumnCartesianLayer.ColumnProvider.series(
                        rememberLineComponent(
                            fill(primaryColor),
                            12.dp
                        )
                    )
                ),
                startAxis = VerticalAxis.rememberStart(),
                bottomAxis = HorizontalAxis.rememberBottom(
                    valueFormatter = dateFormatter
                )
            ),
            modelProducer = modelProducer,
            scrollState = rememberVicoScrollState(scrollEnabled = true),
            modifier = modifier.height(280.dp)
        )
    }
}

/**
 * Pie chart showing muscle group distribution (MPAndroidChart)
 */
@Composable
fun MuscleGroupDistributionChart(
    muscleGroupCounts: Map<String, Int>,
    modifier: Modifier = Modifier
) {
    val isDark = isSystemInDarkTheme()
    val textColor = if (isDark) android.graphics.Color.WHITE else android.graphics.Color.BLACK

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
                setHoleColor(android.graphics.Color.TRANSPARENT)
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
            val counts = if (muscleGroupCounts.isEmpty()) {
                mapOf("No Data" to 1)
            } else {
                muscleGroupCounts
            }

            val total = counts.values.sum().toFloat()
            val entries = counts.map { (group, count) ->
                val percentage = (count.toFloat() / total) * 100f
                PieEntry(percentage, group)
            }

            // Vibrant colors for distribution
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
                sliceSpace = 2f
                selectionShift = 8f
                valueTextSize = 14f
                valueTextColor = android.graphics.Color.WHITE
                valueFormatter = object : ValueFormatter() {
                    override fun getFormattedValue(value: Float): String {
                        return if (value >= 5f) "${value.toInt()}%" else ""
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
 * Workout Mode Distribution Chart
 * Shows simple count of workout types
 */
@Composable
fun WorkoutModeDistributionChart(
    personalRecords: List<PersonalRecord>,
    modifier: Modifier = Modifier
) {
    val modelProducer = remember { CartesianChartModelProducer() }
    
    LaunchedEffect(personalRecords) {
        if (personalRecords.isNotEmpty()) {
            val modeCounts = personalRecords.groupingBy { it.workoutMode }.eachCount()
            val values = modeCounts.values.map { it.toDouble() }
            
            modelProducer.runTransaction {
                columnSeries {
                    series(values)
                }
            }
        }
    }

    ProvideVicoTheme(rememberM3VicoTheme()) {
        CartesianChartHost(
            chart = rememberCartesianChart(
                rememberColumnCartesianLayer(
                    columnProvider = ColumnCartesianLayer.ColumnProvider.series(
                        rememberLineComponent(
                            fill(MaterialTheme.colorScheme.secondary),
                            20.dp
                        )
                    )
                ),
                startAxis = VerticalAxis.rememberStart(
                    itemPlacer = VerticalAxis.ItemPlacer.step(step = { 1.0 })
                ),
                bottomAxis = HorizontalAxis.rememberBottom(),
            ),
            modelProducer = modelProducer,
            modifier = modifier.height(280.dp)
        )
    }
}
