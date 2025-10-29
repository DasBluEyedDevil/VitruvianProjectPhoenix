package com.example.vitruvianredux.presentation.screen

import android.media.AudioManager
import android.media.ToneGenerator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import com.example.vitruvianredux.domain.model.HapticEvent
import kotlinx.coroutines.flow.SharedFlow
import timber.log.Timber

/**
 * Composable effect that provides haptic and audio feedback in response to workout events.
 * 
 * Different haptic patterns and tones are used for different events:
 * - REP_COMPLETED: Light click + short high beep
 * - WARMUP_COMPLETE: Long press + success tone
 * - WORKOUT_COMPLETE: Long press + success tone
 * - WORKOUT_START: Light click + medium beep
 * - WORKOUT_END: Light click + medium beep
 */
@Composable
fun HapticFeedbackEffect(
    hapticEvents: SharedFlow<HapticEvent>
) {
    val haptic = LocalHapticFeedback.current
    
    // Create ToneGenerator for audio cues (80% volume on notification stream)
    val toneGenerator = remember {
        try {
            ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80)
        } catch (e: Exception) {
            Timber.w(e, "Failed to create ToneGenerator")
            null
        }
    }
    
    // Release ToneGenerator when composable is disposed
    DisposableEffect(Unit) {
        onDispose {
            try {
                toneGenerator?.release()
                Timber.v("ToneGenerator released")
            } catch (e: Exception) {
                Timber.w(e, "Error releasing ToneGenerator")
            }
        }
    }
    
    LaunchedEffect(hapticEvents) {
        hapticEvents.collect { event ->
            performHapticFeedback(haptic, event)
            performAudioCue(toneGenerator, event)
        }
    }
}

private fun performHapticFeedback(haptic: HapticFeedback, event: HapticEvent) {
    try {
        when (event) {
            HapticEvent.REP_COMPLETED -> {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                Timber.v("Haptic feedback: rep completed")
            }
            HapticEvent.WARMUP_COMPLETE -> {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                Timber.d("Haptic feedback: warmup complete")
            }
            HapticEvent.WORKOUT_COMPLETE -> {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                Timber.d("Haptic feedback: workout complete")
            }
            HapticEvent.WORKOUT_START -> {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                Timber.d("Haptic feedback: workout start")
            }
            HapticEvent.WORKOUT_END -> {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                Timber.d("Haptic feedback: workout end")
            }
        }
    } catch (e: Exception) {
        Timber.w(e, "Failed to perform haptic feedback")
    }
}

/**
 * Plays audio tone for workout events.
 * Different tones are used for different event types to provide audio cues.
 */
private fun performAudioCue(toneGenerator: ToneGenerator?, event: HapticEvent) {
    if (toneGenerator == null) return
    
    try {
        when (event) {
            HapticEvent.REP_COMPLETED -> {
                // Short high beep for each rep (100ms)
                playTone(toneGenerator, ToneGenerator.TONE_PROP_BEEP, 100)
                Timber.v("Audio cue: rep completed")
            }
            HapticEvent.WARMUP_COMPLETE -> {
                // Success tone for warmup completion (200ms)
                playTone(toneGenerator, ToneGenerator.TONE_PROP_ACK, 200)
                Timber.d("Audio cue: warmup complete")
            }
            HapticEvent.WORKOUT_COMPLETE -> {
                // Success tone for workout completion (200ms)
                playTone(toneGenerator, ToneGenerator.TONE_PROP_ACK, 200)
                Timber.d("Audio cue: workout complete")
            }
            HapticEvent.WORKOUT_START -> {
                // Medium beep for workout start (150ms)
                playTone(toneGenerator, ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 150)
                Timber.d("Audio cue: workout start")
            }
            HapticEvent.WORKOUT_END -> {
                // Medium beep for workout end (150ms)
                playTone(toneGenerator, ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 150)
                Timber.d("Audio cue: workout end")
            }
        }
    } catch (e: Exception) {
        Timber.w(e, "Failed to perform audio cue")
    }
}

/**
 * Helper function to safely play a tone with the ToneGenerator.
 * 
 * @param generator ToneGenerator instance
 * @param toneType Type of tone to play (from ToneGenerator constants)
 * @param durationMs Duration of the tone in milliseconds
 */
private fun playTone(generator: ToneGenerator, toneType: Int, durationMs: Int) {
    try {
        generator.startTone(toneType, durationMs)
    } catch (e: Exception) {
        Timber.e(e, "Error playing tone type: $toneType")
    }
}
