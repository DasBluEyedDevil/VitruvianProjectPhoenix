package com.example.vitruvianredux.presentation.components.charts

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.Alignment
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.ui.text.style.TextAlign
import me.himanshu.charty.charts.circle.CircleChart
import me.himanshu.charty.charts.circle.CircleChartConfig
import me.himanshu.charty.charts.circle.CircleData
import me.himanshu.charty.charts.common.asSolidChartColor

/**
 * Material 3 Expressive Circle Chart
 * Uses Charty library for beautiful circle/progress visualizations
 * Perfect for muscle group distribution and progress tracking
 */
@Composable
fun MuscleGroupCircleChart(
    data: List<Pair<String, Float>>, // Label to value pairs
    modifier: Modifier = Modifier,
    onSegmentClick: ((String, Float) -> Unit)? = null
) {
    // Data validation - Material 3 Expressive: Handle empty/invalid data gracefully
    if (data.isEmpty() || data.all { it.second <= 0f }) {
        EmptyChartState(
            message = "No data available",
            modifier = modifier
        )
        return
    }

    val chartData = remember(data) {
        // Material 3 Expressive color palette
        val colors = listOf(
            MaterialTheme.colorScheme.primary,
            MaterialTheme.colorScheme.secondary,
            MaterialTheme.colorScheme.tertiary,
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.secondaryContainer,
            MaterialTheme.colorScheme.tertiaryContainer,
            MaterialTheme.colorScheme.error,
            MaterialTheme.colorScheme.errorContainer
        )

        data.mapIndexed { index, (label, value) ->
            CircleData(
                value = value,
                label = label,
                color = colors[index % colors.size].asSolidChartColor(),
                trackColor = colors[index % colors.size]
                    .copy(alpha = 0.3f)
                    .asSolidChartColor()
            )
        }
    }

    val config = remember {
        CircleChartConfig.default().copy(
            strokeWidth = 24.dp, // Material 3 Expressive: Thicker strokes
            spacing = 8.dp, // Material 3 Expressive: More spacing
            innerRadius = 0.4f, // Donut chart style
            animationDurationMillis = 1500 // Smooth animation
        )
    }

    CircleChart(
        data = { chartData },
        modifier = modifier
            .fillMaxWidth()
            .height(280.dp) // Material 3 Expressive: Taller charts
            .padding(16.dp),
        circleChartConfig = config,
        onCircleClick = { circleData ->
            onSegmentClick?.invoke(circleData.label, circleData.value)
        }
    )
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
                imageVector = Icons.Default.PieChart,
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

