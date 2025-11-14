package com.example.vitruvianredux.presentation.components.charts

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
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
import com.patrykandpatrick.vico.core.cartesian.axis.formatter.DecimalValueFormatter
import com.patrykandpatrick.vico.core.component.shape.shapes.roundedCornerShape
import com.patrykandpatrick.vico.core.component.shape.shapes.fullyRoundedCornerShape
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Alignment
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.ui.text.style.TextAlign

/**
 * Material 3 Expressive Combo Chart
 * Combines column and line charts for multi-metric comparisons
 * Perfect for comparing volume (columns) with weight progression (line)
 */
@Composable
fun ComboChart(
    columnData: List<Pair<String, Float>>, // Label to value pairs for columns
    lineData: List<Pair<String, Float>>, // Label to value pairs for line
    modifier: Modifier = Modifier,
    columnLabel: String = "Volume",
    lineLabel: String = "Weight"
) {
    // Data validation - Material 3 Expressive: Handle empty/invalid data gracefully
    if (columnData.isEmpty() && lineData.isEmpty()) {
        EmptyChartState(
            message = "No data available",
            modifier = modifier
        )
        return
    }

    val modelProducer = remember { CartesianChartModelProducer() }

    LaunchedEffect(columnData, lineData) {
        if (columnData.isEmpty() && lineData.isEmpty()) return@LaunchedEffect

        modelProducer.runTransaction {
            // Column series (e.g., volume)
            if (columnData.isNotEmpty()) {
                columnSeries {
                    series(columnData.map { it.second })
                }
            }
            
            // Line series (e.g., weight progression)
            if (lineData.isNotEmpty()) {
                lineSeries {
                    series(lineData.map { it.second })
                }
            }
        }
    }

    ProvideVicoTheme(rememberM3VicoTheme()) {
        CartesianChartHost(
            chart = rememberCartesianChart(
                // Column layer
                rememberColumnCartesianLayer(
                    columnWidth = 0.6f,
                    columnSpacing = 8.dp,
                    columnShape = roundedCornerShape(12.dp), // Material 3 Expressive: More rounded
                    columnColors = listOf(
                        MaterialTheme.colorScheme.primary,
                        MaterialTheme.colorScheme.secondary
                    )
                ),
                // Line layer
                rememberLineCartesianLayer(
                    pointSize = 8.dp, // Material 3 Expressive: Larger points
                    pointShape = fullyRoundedCornerShape(),
                    lineThickness = 4.dp, // Material 3 Expressive: Thicker line
                    lineColor = MaterialTheme.colorScheme.tertiary,
                    pointColor = MaterialTheme.colorScheme.tertiary,
                    pointOuterColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f)
                ),
                startAxis = rememberStartAxis(
                    tickLength = 8.dp,
                    axisLineThickness = 2.dp,
                    guideline = Axis.Guideline(
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                        thickness = 1.dp
                    ),
                    valueFormatter = DecimalValueFormatter(maxFractionDigits = 1)
                ),
                bottomAxis = rememberBottomAxis(
                    tickLength = 8.dp,
                    axisLineThickness = 2.dp,
                    guideline = Axis.Guideline(
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                        thickness = 1.dp
                    ),
                    valueFormatter = DecimalValueFormatter(maxFractionDigits = 0)
                )
            ),
            modelProducer = modelProducer,
            modifier = modifier
                .fillMaxWidth()
                .height(280.dp) // Material 3 Expressive: Taller charts
                .padding(16.dp)
        )
    }
}

/**
 * Empty state for charts when no data is available
 */
@Composable
private fun EmptyChartState(
    message: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(280.dp)
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.BarChart,
                contentDescription = "No data available",
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(48.dp)
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

