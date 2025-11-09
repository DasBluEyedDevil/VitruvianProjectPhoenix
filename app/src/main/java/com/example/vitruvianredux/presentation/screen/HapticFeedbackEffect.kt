package com.example.vitruvianredux.presentation.screen

import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
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
    val context = LocalContext.current

    // Get AudioManager for audio focus management
    val audioManager = remember {
        context.getSystemService(AudioManager::class.java)
    }

    // Create ToneGenerator for audio cues (80% volume on media stream)
    val toneGenerator = remember {
        try {
            ToneGenerator(AudioManager.STREAM_MUSIC, 80)
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
            performAudioCue(toneGenerator, audioManager, event)
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
            HapticEvent.ERROR -> {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                Timber.e("Haptic feedback: ERROR")
            }
        }
    } catch (e: Exception) {
        Timber.w(e, "Failed to perform haptic feedback")
    }
}

/**
 * Plays audio tone for workout events.
 * Different tones are used for different event types to provide audio cues.
 *
 * Uses audio focus management to allow background music to continue playing
 * at a reduced volume (ducked) while the notification beep plays.
 */
private fun performAudioCue(
    toneGenerator: ToneGenerator?,
    audioManager: AudioManager?,
    event: HapticEvent
) {
    if (toneGenerator == null || audioManager == null) return

    try {
        when (event) {
            HapticEvent.REP_COMPLETED -> {
                // Short high beep for each rep (100ms)
                playToneWithAudioFocus(audioManager, toneGenerator, ToneGenerator.TONE_PROP_BEEP, 100)
                Timber.v("Audio cue: rep completed")
            }
            HapticEvent.WARMUP_COMPLETE -> {
                // Success tone for warmup completion (200ms)
                playToneWithAudioFocus(audioManager, toneGenerator, ToneGenerator.TONE_PROP_ACK, 200)
                Timber.d("Audio cue: warmup complete")
            }
            HapticEvent.WORKOUT_COMPLETE -> {
                // Success tone for workout completion (200ms)
                playToneWithAudioFocus(audioManager, toneGenerator, ToneGenerator.TONE_PROP_ACK, 200)
                Timber.d("Audio cue: workout complete")
            }
            HapticEvent.WORKOUT_START -> {
                // Medium beep for workout start (150ms)
                playToneWithAudioFocus(audioManager, toneGenerator, ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 150)
                Timber.d("Audio cue: workout start")
            }
            HapticEvent.WORKOUT_END -> {
                // Medium beep for workout end (150ms)
                playToneWithAudioFocus(audioManager, toneGenerator, ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 150)
                Timber.d("Audio cue: workout end")
            }
            HapticEvent.ERROR -> {
                // Error tone (400ms)
                playToneWithAudioFocus(audioManager, toneGenerator, ToneGenerator.TONE_SUP_ERROR, 400)
                Timber.e("Audio cue: ERROR")
            }
        }
    } catch (e: Exception) {
        Timber.w(e, "Failed to perform audio cue")
    }
}

/**
 * Plays a tone with proper audio focus management to allow background music to continue.
 *
 * Requests transient audio focus with ducking, which allows other audio (like music)
 * to continue playing at a reduced volume while the notification beep plays.
 *
 * @param audioManager AudioManager for requesting audio focus
 * @param generator ToneGenerator instance
 * @param toneType Type of tone to play (from ToneGenerator constants)
 * @param durationMs Duration of the tone in milliseconds
 */
private fun playToneWithAudioFocus(
    audioManager: AudioManager,
    generator: ToneGenerator,
    toneType: Int,
    durationMs: Int
) {
    var focusRequest: AudioFocusRequest? = null

    try {
        // Request audio focus to duck other audio (like music)
        val focusResult = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Modern API (Android 8.0+) - Use AudioFocusRequest
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()

            focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(audioAttributes)
                .setWillPauseWhenDucked(false)
                .build()

            audioManager.requestAudioFocus(focusRequest)
        } else {
            // Legacy API (pre-Android 8.0)
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                null,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
            )
        }

        if (focusResult == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            // Play the tone
            generator.startTone(toneType, durationMs)

            // Wait for tone to complete before releasing focus
            Thread.sleep(durationMs.toLong())
        } else {
            Timber.w("Audio focus request denied, playing tone anyway")
            generator.startTone(toneType, durationMs)
        }
    } catch (e: Exception) {
        Timber.e(e, "Error playing tone type: $toneType")
    } finally {
        // Release audio focus
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && focusRequest != null) {
                audioManager.abandonAudioFocusRequest(focusRequest)
            } else {
                @Suppress("DEPRECATION")
                audioManager.abandonAudioFocus(null)
            }
        } catch (e: Exception) {
            Timber.w(e, "Error releasing audio focus")
        }
    }
}
