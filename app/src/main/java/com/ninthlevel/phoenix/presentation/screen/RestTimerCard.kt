package com.ninthlevel.phoenix.presentation.screen

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ninthlevel.phoenix.domain.model.WeightUnit
import com.ninthlevel.phoenix.domain.model.WorkoutParameters
import com.ninthlevel.phoenix.domain.model.WorkoutType
import com.ninthlevel.phoenix.presentation.components.ExpressiveSlider
import com.ninthlevel.phoenix.ui.theme.*

/**
 * Rest Timer Card Component
 * 
 * Displays during rest periods between sets/exercises in autoplay mode.
 * Shows countdown timer, next exercise info, and action buttons.
 */
@Composable
fun RestTimerCard(
    restSecondsRemaining: Int,
    nextExerciseName: String,
    isLastExercise: Boolean,
    currentSet: Int,
    totalSets: Int,
    nextExerciseWeight: Float? = null,
    nextExerciseReps: Int? = null,
    nextExerciseMode: String? = null,
    currentExerciseIndex: Int? = null,
    totalExercises: Int? = null,
    formatWeight: ((Float) -> String)? = null,
    workoutParameters: WorkoutParameters? = null,
    weightUnit: WeightUnit = WeightUnit.KG,
    kgToDisplay: ((Float, WeightUnit) -> Float)? = null,
    displayToKg: ((Float, WeightUnit) -> Float)? = null,
    onUpdateParameters: ((WorkoutParameters) -> Unit)? = null,
    onSkipRest: () -> Unit,
    onEndWorkout: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Background gradient - respects theme mode
    Box(
        modifier = modifier
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
        // Subtle pulsing overlay to create an immersive feel
        val infinite = rememberInfiniteTransition(label = "rest-pulse")
        val pulse by infinite.animateFloat(
            initialValue = 1f,
            targetValue = 1.06f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1600, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "pulse"
        )

        Column(
            modifier = Modifier
                .fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Spacer(modifier = Modifier.height(8.dp))
            // REST TIME Header - Material 3 Expressive
            Text(
                text = "REST TIME",
                style = MaterialTheme.typography.titleMedium, // Material 3 Expressive: Larger (was labelLarge)
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 1.5.sp
            )

            // Countdown timer - large centered text with pulsing animation
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp),
                contentAlignment = Alignment.Center
            ) {
                // Circular background with pulse effect
                Box(
                    modifier = Modifier
                        .size(220.dp)
                        .scale(pulse)
                        .background(
                            color = MaterialTheme.colorScheme.surfaceContainerHighest,
                            shape = RoundedCornerShape(200.dp)
                        )
                )

                // Timer text
                Text(
                    text = formatRestTime(restSecondsRemaining),
                    style = MaterialTheme.typography.displayLarge.copy(fontSize = 80.sp),
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            // UP NEXT section - Material 3 Expressive
            Text(
                text = "UP NEXT",
                style = MaterialTheme.typography.titleMedium, // Material 3 Expressive: Larger (was labelMedium)
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 1.2.sp
            )

            // Next exercise name or completion message - Material 3 Expressive
            Text(
                text = if (isLastExercise) "Workout Complete" else nextExerciseName,
                style = MaterialTheme.typography.headlineSmall, // Material 3 Expressive: Larger (was titleLarge)
                fontWeight = FontWeight.Bold,
                color = if (isLastExercise)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.onSurface
            )

            // Mode display (non-editable, informational only)
            if (!isLastExercise && nextExerciseMode != null) {
                Text(
                    text = nextExerciseMode,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            // Set progress indicator - shows NEXT set number (currentSet + 1)
            // currentSet represents the set just completed, so we add 1 to show the upcoming set
            if (!isLastExercise && totalSets > 0) {
                Text(
                    text = "Set ${currentSet + 1} of $totalSets",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Editable workout configuration (if parameters available for editing)
            if (!isLastExercise && workoutParameters != null && onUpdateParameters != null) {
                Spacer(modifier = Modifier.height(Spacing.small))

                // Local state for editing
                val isEchoMode = workoutParameters.workoutType is WorkoutType.Echo
                var editableWeight by remember(workoutParameters.weightPerCableKg) {
                    mutableFloatStateOf(
                        kgToDisplay?.invoke(workoutParameters.weightPerCableKg, weightUnit)
                            ?: workoutParameters.weightPerCableKg
                    )
                }
                var editableReps by remember(workoutParameters.reps) {
                    mutableIntStateOf(workoutParameters.reps)
                }

                // Configuration card - Material 3 Expressive
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(Spacing.medium),
                        verticalArrangement = Arrangement.spacedBy(Spacing.small)
                    ) {
                        Text(
                            "ADJUST FOR NEXT SET",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            letterSpacing = 1.sp
                        )

                        // Weight slider (or "Adaptive" label for Echo mode)
                        if (isEchoMode) {
                            // Echo mode: Weight is adaptive, show info
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "Weight",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    "Adaptive",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        } else {
                            // Non-Echo mode: Editable weight
                            val weightSuffix = if (weightUnit == WeightUnit.LB) "lbs" else "kg"
                            val maxWeight = if (weightUnit == WeightUnit.LB) 220f else 100f

                            Text(
                                "Weight: ${editableWeight.toInt()} $weightSuffix/cable",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            ExpressiveSlider(
                                value = editableWeight,
                                onValueChange = { newWeight ->
                                    editableWeight = newWeight
                                    val kg = displayToKg?.invoke(newWeight, weightUnit) ?: newWeight
                                    onUpdateParameters(workoutParameters.copy(weightPerCableKg = kg))
                                },
                                valueRange = 1f..maxWeight,
                                steps = (maxWeight - 1).toInt(),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        // Reps slider
                        Text(
                            "Target Reps: $editableReps",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        ExpressiveSlider(
                            value = editableReps.toFloat(),
                            onValueChange = { newReps ->
                                editableReps = newReps.toInt()
                                onUpdateParameters(workoutParameters.copy(reps = newReps.toInt()))
                            },
                            valueRange = 1f..50f,
                            steps = 49,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            } else if (!isLastExercise && (nextExerciseWeight != null || nextExerciseReps != null)) {
                // Fallback: Read-only display when editing is not available
                Spacer(modifier = Modifier.height(Spacing.small))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(Spacing.medium),
                        verticalArrangement = Arrangement.spacedBy(Spacing.small)
                    ) {
                        Text(
                            "NEXT SET PARAMETERS",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            letterSpacing = 1.sp
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            if (nextExerciseWeight != null && formatWeight != null) {
                                WorkoutParamItem(
                                    icon = Icons.Default.FitnessCenter,
                                    label = "Weight",
                                    value = formatWeight(nextExerciseWeight)
                                )
                            }
                            if (nextExerciseReps != null) {
                                WorkoutParamItem(
                                    icon = Icons.Default.Repeat,
                                    label = "Target Reps",
                                    value = nextExerciseReps.toString()
                                )
                            }
                        }
                    }
                }
            }

            // Progress through routine (if multi-exercise)
            if (currentExerciseIndex != null && totalExercises != null && totalExercises > 1) {
                Spacer(modifier = Modifier.height(Spacing.small))

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "Exercise ${currentExerciseIndex + 1} of $totalExercises",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { (currentExerciseIndex + 1).toFloat() / totalExercises },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    )
                }
            }
            Spacer(modifier = Modifier.height(Spacing.medium))

            // Action buttons
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(Spacing.small)
            ) {
                // Skip Rest button (primary action) - Material 3 Expressive
                Button(
                    onClick = onSkipRest,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp), // Material 3 Expressive: Taller button
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    shape = RoundedCornerShape(20.dp), // Material 3 Expressive: More rounded (was 16dp)
                    elevation = ButtonDefaults.buttonElevation(
                        defaultElevation = 4.dp, // Material 3 Expressive: Higher elevation
                        pressedElevation = 2.dp
                    )
                ) {
                    Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = "Skip rest",
                        modifier = Modifier.size(24.dp) // Material 3 Expressive: Larger icon (was 20dp)
                    )
                    Spacer(modifier = Modifier.width(Spacing.small))
                    Text(
                        text = if (isLastExercise) "Continue" else "Skip Rest",
                        style = MaterialTheme.typography.titleLarge, // Material 3 Expressive: Larger (was labelLarge)
                        fontWeight = FontWeight.Bold
                    )
                }

                // End Workout button (secondary/destructive action) - Material 3 Expressive
                TextButton(
                    onClick = onEndWorkout,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp), // Material 3 Expressive: Taller button
                    shape = RoundedCornerShape(20.dp) // Material 3 Expressive: More rounded (was 16dp)
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "End workout",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.width(Spacing.small))
                    Text(
                        text = "End Workout",
                        style = MaterialTheme.typography.titleMedium, // Material 3 Expressive: Larger (was labelMedium)
                        fontWeight = FontWeight.Bold, // Material 3 Expressive: Bolder
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

/**
 * Formats rest time in seconds to MM:SS format
 */
private fun formatRestTime(seconds: Int): String {
    val minutes = seconds / 60
    val remainingSeconds = seconds % 60
    return "%d:%02d".format(minutes, remainingSeconds)
}

@Composable
fun WorkoutParamItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            icon,
            contentDescription = "Rest timer status",
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Text(
            value,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
