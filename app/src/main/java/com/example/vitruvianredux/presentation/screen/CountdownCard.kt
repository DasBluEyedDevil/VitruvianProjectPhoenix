package com.example.vitruvianredux.presentation.screen

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.vitruvianredux.ui.theme.*

/**
 * Countdown Card with enhanced animations.
 *
 * Features:
 * - Continuous subtle pulse animation for visual interest
 * - Pop effect when the countdown number changes for impactful transitions
 */
@Composable
fun CountdownCard(secondsRemaining: Int) {
    // Continuous subtle pulse animation
    val infinite = rememberInfiniteTransition(label = "countdown-pulse")
    val pulse by infinite.animateFloat(
        initialValue = 1f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    // Track previous value to detect changes
    var previousSeconds by remember { mutableIntStateOf(secondsRemaining) }
    var triggerPop by remember { mutableStateOf(false) }

    // Detect when seconds change
    LaunchedEffect(secondsRemaining) {
        if (secondsRemaining != previousSeconds) {
            triggerPop = true
            previousSeconds = secondsRemaining
        }
    }

    // Reset pop state after animation
    LaunchedEffect(triggerPop) {
        if (triggerPop) {
            kotlinx.coroutines.delay(200)
            triggerPop = false
        }
    }

    // Pop animation when number changes - starts large and settles
    val popScale by animateFloatAsState(
        targetValue = if (triggerPop) 1.25f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "countdownPop"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.surface,
                        MaterialTheme.colorScheme.surfaceVariant
                    )
                )
            )
            .padding(20.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Get Ready!",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Huge number with combined pulse + pop animation
            Surface(
                modifier = Modifier.scale(pulse * popScale),
                color = Color.Transparent
            ) {
                Text(
                    text = "$secondsRemaining",
                    style = MaterialTheme.typography.displayLarge.copy(fontSize = 96.sp),
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Starting in...",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
