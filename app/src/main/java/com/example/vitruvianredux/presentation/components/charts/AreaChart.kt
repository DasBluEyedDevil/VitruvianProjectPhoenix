package com.example.vitruvianredux.presentation.components.charts

import androidx.compose.animation.core.EaseInOutCubic
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.StrokeStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.ehsannarmani.composecharts.charts.LineChart
import io.github.ehsannarmani.composecharts.core.AnimationMode
import io.github.ehsannarmani.composecharts.core.Line
import io.github.ehsannarmani.composecharts.core.constants.DrawStyle
import io.github.ehsannarmani.composecharts.core.constants.IndicatorPosition
import io.github.ehsannarmani.composecharts.core.constants.PopupProperties
import io.github.ehsannarmani.composecharts.core.model.DotProperties
import io.github.ehsannarmani.composecharts.core.model.GridProperties
import io.github.ehsannarmani.composecharts.core.model.HorizontalIndicatorProperties
import io.github.ehsannarmani.composecharts.core.model.LabelHelperProperties
import io.github.ehsannarmani.composecharts.core.model.LabelProperties
import androidx.compose.ui.text.TextStyle
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart

/**
 * Material 3 Expressive Area Chart with gradient fills
 * Uses Compose Charts library for beautiful gradient area visualizations
 */
@Composable
fun AreaChart(
    data: List<Pair<String, Float>>, // Label to value pairs
    modifier: Modifier = Modifier,
    title: String? = null,
    label: String = "Value",
    showGrid: Boolean = true,
    showPopup: Boolean = true,
    animationDuration: Int = 2000
) {
    // Data validation - Material 3 Expressive: Handle empty/invalid data gracefully
    if (data.isEmpty()) {
        EmptyChartState(
            message = "No data available",
            modifier = modifier
        )
        return
    }

    val chartData = remember(data) {
        listOf(
            Line(
                label = label,
                values = data.map { it.second.toDouble() },
                color = SolidColor(MaterialTheme.colorScheme.primary),
                firstGradientFillColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                secondGradientFillColor = Color.Transparent,
                strokeAnimationSpec = tween(animationDuration, easing = EaseInOutCubic),
                gradientAnimationDelay = 1000,
                drawStyle = DrawStyle.Stroke(width = 3.dp), // Material 3 Expressive: Thicker line
                curvedEdges = true, // Smooth curves
                popupProperties = if (showPopup) {
                    PopupProperties(
                        textStyle = TextStyle(
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        contentBuilder = { "${it.value.format(1)}" },
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
                    )
                } else null,
                dotProperties = DotProperties(
                    enabled = true,
                    color = SolidColor(MaterialTheme.colorScheme.primary),
                    radius = 5.dp, // Material 3 Expressive: Larger dots
                    strokeWidth = 2.dp,
                    strokeColor = SolidColor(MaterialTheme.colorScheme.primaryContainer)
                )
            )
        )
    }

    val labels = remember(data) {
        data.map { it.first }
    }

    val maxValue = remember(data) {
        (data.maxOfOrNull { it.second }?.times(1.1f) ?: 100f).toDouble()
    }

    LineChart(
        modifier = modifier
            .fillMaxWidth()
            .height(280.dp) // Material 3 Expressive: Taller charts
            .padding(16.dp),
        data = chartData,
        animationMode = AnimationMode.Together(delayBuilder = { it * 200L }),
        curvedEdges = true,
        indicatorProperties = HorizontalIndicatorProperties(
            enabled = true,
            textStyle = TextStyle(
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            ),
            contentBuilder = { "${it.format(1)}" },
            position = IndicatorPosition.Horizontal.Start
        ),
        gridProperties = if (showGrid) {
            GridProperties(
                enabled = true,
                xAxisProperties = GridProperties.AxisProperties(
                    thickness = 1.dp,
                    color = SolidColor(
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                    ),
                    style = StrokeStyle.Dashed(intervals = floatArrayOf(10f, 10f))
                ),
                yAxisProperties = GridProperties.AxisProperties(
                    thickness = 1.dp,
                    color = SolidColor(
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                    ),
                    style = StrokeStyle.Dashed(intervals = floatArrayOf(10f, 10f))
                )
            )
        } else {
            GridProperties(enabled = false)
        },
        labelProperties = LabelProperties(
            enabled = true,
            labels = labels,
            textStyle = TextStyle(
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        ),
        labelHelperProperties = LabelHelperProperties(
            enabled = true,
            textStyle = TextStyle(
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
        ),
        maxValue = maxValue,
        minValue = 0.0
    )
}

/**
 * Extension function to format Double values
 */
private fun Double.format(decimals: Int): String {
    return "%.${decimals}f".format(this)
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
                imageVector = androidx.compose.material.icons.Icons.Default.BarChart,
                contentDescription = "No data available",
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(48.dp)
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

