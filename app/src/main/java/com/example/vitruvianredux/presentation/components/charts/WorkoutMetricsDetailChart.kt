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
import com.example.vitruvianredux.domain.model.WorkoutMetric
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.common.component.rememberLineComponent
import com.patrykandpatrick.vico.compose.common.fill
import com.patrykandpatrick.vico.core.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.m3.common.rememberM3VicoTheme
import com.patrykandpatrick.vico.compose.common.ProvideVicoTheme
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.lineSeries
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.Alignment
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.size
import kotlin.math.abs

/**
 * Material 3 Expressive Workout Metrics Detail Chart
 * Visualizes time-series workout data: Load A & B, Position A & B, Power
 * Uses Vico for multi-line chart with Material 3 theming
 */
@Composable
fun WorkoutMetricsDetailChart(
    metrics: List<WorkoutMetric>,
    modifier: Modifier = Modifier,
    showLoad: Boolean = true,
    showPosition: Boolean = true,
    showPower: Boolean = true
) {
    // Data validation - Material 3 Expressive: Handle empty/invalid data gracefully
    if (metrics.isEmpty()) {
        EmptyChartState(
            message = "No workout metrics available",
            modifier = modifier
        )
        return
    }

    val modelProducer = remember { CartesianChartModelProducer() }

    LaunchedEffect(metrics) {

        modelProducer.runTransaction {
            val timePoints = metrics.mapIndexed { index, _ -> index.toFloat() }
            
            if (showLoad) {
                // Load A & B series
                lineSeries {
                    series(
                        x = timePoints,
                        y = metrics.map { it.loadA }
                    )
                    series(
                        x = timePoints,
                        y = metrics.map { it.loadB }
                    )
                }
            }
            
            if (showPosition) {
                // Position A & B series (normalized to 0-100 for visualization)
                lineSeries {
                    series(
                        x = timePoints,
                        y = metrics.map { (it.positionA / 100f) } // Normalize position
                    )
                    series(
                        x = timePoints,
                        y = metrics.map { (it.positionB / 100f) }
                    )
                }
            }
            
            if (showPower) {
                // Calculate power: load * velocity (derived from position changes)
                val powerA = calculatePower(metrics.map { it.loadA }, metrics.map { it.positionA })
                val powerB = calculatePower(metrics.map { it.loadB }, metrics.map { it.positionB })
                
                lineSeries {
                    series(
                        x = timePoints.take(powerA.size),
                        y = powerA
                    )
                    series(
                        x = timePoints.take(powerB.size),
                        y = powerB
                    )
                }
            }
        }
    }

    ProvideVicoTheme(rememberM3VicoTheme()) {
        CartesianChartHost(
            chart = rememberCartesianChart(
                rememberLineCartesianLayer()
            ),
            modelProducer = modelProducer,
            modifier = modifier
                .fillMaxWidth()
                .height(320.dp) // Material 3 Expressive: Taller chart for detail view
                .padding(16.dp)
        )
    }
}

/**
 * Calculate power from load and position data
 * Power = Load × Velocity (velocity derived from position changes)
 */
private fun calculatePower(loads: List<Float>, positions: List<Int>): List<Float> {
    if (loads.size < 2 || positions.size < 2) return emptyList()
    
    val power = mutableListOf<Float>()
    
    for (i in 1 until loads.size) {
        val load = loads[i]
        val positionChange = abs(positions[i] - positions[i - 1]).toFloat()
        // Power approximation: load × position change rate
        val calculatedPower = load * positionChange
        power.add(calculatedPower)
    }
    
    return power
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
            .height(320.dp)
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.ShowChart,
                contentDescription = "No workout metrics available",
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

