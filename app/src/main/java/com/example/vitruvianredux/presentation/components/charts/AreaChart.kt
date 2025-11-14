package com.example.vitruvianredux.presentation.components.charts

import androidx.compose.animation.core.EaseInOutCubic
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.ehsannarmani.compose_charts.LineChart
import ir.ehsannarmani.compose_charts.models.AnimationMode
import ir.ehsannarmani.compose_charts.models.DrawStyle
import ir.ehsannarmani.compose_charts.models.DotProperties
import ir.ehsannarmani.compose_charts.models.GridProperties
import ir.ehsannarmani.compose_charts.models.HorizontalIndicatorProperties
import ir.ehsannarmani.compose_charts.models.IndicatorPosition
import ir.ehsannarmani.compose_charts.models.LabelHelperProperties
import ir.ehsannarmani.compose_charts.models.LabelProperties
import ir.ehsannarmani.compose_charts.models.Line
import ir.ehsannarmani.compose_charts.models.PopupProperties

/**
 * Material 3 Expressive Area Chart using ComposeCharts
 * Provides animated area/line charts with gradient fills
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
    // Data validation
    if (data.isEmpty()) {
        EmptyChartState(
            message = "No data available",
            modifier = modifier
        )
        return
    }

    val primaryColor = MaterialTheme.colorScheme.primary
    val surfaceColor = MaterialTheme.colorScheme.surface

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (title != null) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        LineChart(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp),
            data = remember(data) {
                listOf(
                    Line(
                        label = label,
                        values = data.map { it.second.toDouble() },
                        color = SolidColor(primaryColor),
                        firstGradientFillColor = primaryColor.copy(alpha = 0.5f),
                        secondGradientFillColor = Color.Transparent,
                        strokeAnimationSpec = tween(animationDuration, easing = EaseInOutCubic),
                        gradientAnimationDelay = 500,
                        drawStyle = DrawStyle.Stroke(width = 3.dp),
                        curvedEdges = true,
                        dotProperties = DotProperties(
                            enabled = true,
                            color = SolidColor(surfaceColor),
                            strokeWidth = 2.dp,
                            radius = 5.dp,
                            strokeColor = SolidColor(primaryColor)
                        )
                    )
                )
            },
            animationMode = AnimationMode.Together(delayBuilder = { it * 200L }),
            gridProperties = GridProperties(
                enabled = showGrid,
                xAxisProperties = GridProperties.AxisProperties(
                    enabled = true,
                    color = SolidColor(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                    thickness = 1.dp
                ),
                yAxisProperties = GridProperties.AxisProperties(
                    enabled = true,
                    color = SolidColor(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                    thickness = 1.dp
                )
            ),
            indicatorProperties = HorizontalIndicatorProperties(
                enabled = showPopup,
                textStyle = TextStyle(
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 12.sp
                ),
                padding = 8.dp,
                position = IndicatorPosition.Horizontal.Start
            ),
            labelProperties = LabelProperties(
                enabled = true,
                textStyle = TextStyle(
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 10.sp
                ),
                labels = data.map { it.first }
            ),
            popupProperties = PopupProperties(
                enabled = showPopup,
                textStyle = TextStyle(
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontSize = 12.sp
                ),
                containerColor = MaterialTheme.colorScheme.primaryContainer
            ),
            labelHelperProperties = LabelHelperProperties(
                enabled = true
            )
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
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
