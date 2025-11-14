package com.example.vitruvianredux.presentation.components.charts

import android.graphics.Paint
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.Alignment
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Radar
import androidx.compose.ui.text.style.TextAlign
import kotlin.math.cos
import kotlin.math.sin

/**
 * Material 3 Expressive Radar/Spider Chart
 * Visualizes muscle group balance and distribution
 * Shows relative strength/volume across different muscle groups
 */
@Composable
fun RadarChart(
    data: List<Pair<String, Float>>, // Label to normalized value (0.0 to 1.0)
    modifier: Modifier = Modifier,
    maxValue: Float = 1f,
    showLabels: Boolean = true
) {
    // Data validation - Material 3 Expressive: Handle empty/invalid data gracefully
    if (data.isEmpty() || maxValue <= 0f) {
        EmptyChartState(
            message = "No data available",
            modifier = modifier
        )
        return
    }

    val animatedData = data.map { (label, value) ->
        val animatedValue by animateFloatAsState(
            targetValue = value.coerceIn(0f, maxValue),
            animationSpec = tween(durationMillis = 1500),
            label = "RadarValue_$label"
        )
        label to animatedValue
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(320.dp) // Material 3 Expressive: Taller chart
            .padding(24.dp)
    ) {
        val centerX = size.width / 2
        val centerY = size.height / 2
        val radius = size.width.coerceAtMost(size.height) / 2.5f
        val numPoints = animatedData.size
        val angleStep = (2 * Math.PI) / numPoints

        // Draw grid circles (concentric)
        for (i in 1..5) {
            val gridRadius = radius * (i / 5f)
            drawCircle(
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                radius = gridRadius,
                center = Offset(centerX, centerY),
                style = Stroke(width = 1.dp.toPx())
            )
        }

        // Draw grid lines (radial)
        for (i in 0 until numPoints) {
            val angle = i * angleStep - Math.PI / 2 // Start from top
            val x = centerX + radius * cos(angle).toFloat()
            val y = centerY + radius * sin(angle).toFloat()
            
            drawLine(
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                start = Offset(centerX, centerY),
                end = Offset(x, y),
                strokeWidth = 1.dp.toPx()
            )
        }

        // Draw data area
        val dataPath = Path().apply {
            animatedData.forEachIndexed { index, (_, value) ->
                val angle = index * angleStep - Math.PI / 2
                val distance = radius * (value / maxValue)
                val x = centerX + distance * cos(angle).toFloat()
                val y = centerY + distance * sin(angle).toFloat()
                
                if (index == 0) {
                    moveTo(x, y)
                } else {
                    lineTo(x, y)
                }
            }
            close()
        }

        // Fill area with gradient-like effect
        drawPath(
            path = dataPath,
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
            style = Fill
        )

        // Draw data outline
        drawPath(
            path = dataPath,
            color = MaterialTheme.colorScheme.primary,
            style = Stroke(
                width = 4.dp.toPx(), // Material 3 Expressive: Thicker line
                cap = androidx.compose.ui.graphics.StrokeCap.Round,
                join = androidx.compose.ui.graphics.StrokeJoin.Round
            )
        )

        // Draw data points
        animatedData.forEachIndexed { index, (_, value) ->
            val angle = index * angleStep - Math.PI / 2
            val distance = radius * (value / maxValue)
            val x = centerX + distance * cos(angle).toFloat()
            val y = centerY + distance * sin(angle).toFloat()
            
            drawCircle(
                color = MaterialTheme.colorScheme.primary,
                radius = 8.dp.toPx(), // Material 3 Expressive: Larger points
                center = Offset(x, y)
            )
            drawCircle(
                color = MaterialTheme.colorScheme.primaryContainer,
                radius = 4.dp.toPx(),
                center = Offset(x, y)
            )
        }

        // Draw labels
        if (showLabels) {
            animatedData.forEachIndexed { index, (label, value) ->
                val angle = index * angleStep - Math.PI / 2
                val labelRadius = radius * 1.15f // Position labels outside chart
                val x = centerX + labelRadius * cos(angle).toFloat()
                val y = centerY + labelRadius * sin(angle).toFloat()
                
                drawContext.canvas.nativeCanvas.apply {
                    val textPaint = Paint().apply {
                        color = MaterialTheme.colorScheme.onSurface.toArgb()
                        textSize = 12.sp.toPx()
                        textAlign = Paint.Align.CENTER
                        isFakeBoldText = true
                    }
                    drawText(
                        label,
                        x,
                        y + 12.sp.toPx() / 2,
                        textPaint
                    )
                }
            }
        }
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
            .height(320.dp)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Radar,
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

