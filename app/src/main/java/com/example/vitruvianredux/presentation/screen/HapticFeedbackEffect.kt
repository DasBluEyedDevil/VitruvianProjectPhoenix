package com.example.vitruvianredux.presentation.screen

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import com.example.vitruvianredux.R
import com.example.vitruvianredux.domain.model.HapticEvent
import kotlinx.coroutines.flow.SharedFlow
import timber.log.Timber

/**
 * Composable effect that provides haptic and audio feedback in response to workout events.
 *
 * Different haptic patterns and sounds are used for different events:
 * - REP_COMPLETED: Light click + beep.ogg
 * - WARMUP_COMPLETE: Long press + beepboop.ogg
 * - WORKOUT_COMPLETE: Long press + boopbeepbeep.ogg
 * - WORKOUT_START: Light click + chirpchirp.ogg
 * - WORKOUT_END: Light click + chirpchirp.ogg
 * - ERROR: Long press (haptic only, no sound)
 */
@Composable
fun HapticFeedbackEffect(
    hapticEvents: SharedFlow<HapticEvent>
) {
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current

    // Create SoundPool for audio cues
    // Uses USAGE_ASSISTANCE_SONIFICATION for short UI feedback sounds
    // This prevents our beeps from interrupting other media playback (Issue #180)
    val soundPool = remember {
        try {
            SoundPool.Builder()
                .setMaxStreams(2)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .build()
        } catch (e: Exception) {
            Timber.w(e, "Failed to create SoundPool")
            null
        }
    }

    // Load sounds into memory, mapping each HapticEvent to its sound ID
    val soundIds = remember(soundPool) {
        soundPool?.let { pool ->
            try {
                mapOf(
                    HapticEvent.REP_COMPLETED to pool.load(context, R.raw.beep, 1),
                    HapticEvent.WARMUP_COMPLETE to pool.load(context, R.raw.beepboop, 1),
                    HapticEvent.WORKOUT_COMPLETE to pool.load(context, R.raw.boopbeepbeep, 1),
                    HapticEvent.WORKOUT_START to pool.load(context, R.raw.chirpchirp, 1),
                    HapticEvent.WORKOUT_END to pool.load(context, R.raw.chirpchirp, 1),
                    HapticEvent.REST_ENDING to pool.load(context, R.raw.restover, 1)
                    // ERROR: no sound (haptic only)
                )
            } catch (e: Exception) {
                Timber.w(e, "Failed to load sounds")
                null
            }
        }
    }

    // Release SoundPool when composable is disposed
    DisposableEffect(Unit) {
        onDispose {
            try {
                soundPool?.release()
                Timber.v("SoundPool released")
            } catch (e: Exception) {
                Timber.w(e, "Error releasing SoundPool")
            }
        }
    }

    LaunchedEffect(hapticEvents) {
        hapticEvents.collect { event ->
            performHapticFeedback(haptic, event)
            performAudioCue(soundPool, soundIds, event)
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
            HapticEvent.REST_ENDING -> {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                Timber.d("Haptic feedback: rest ending (5 seconds)")
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
 * Plays audio cue for workout events using SoundPool.
 *
 * Uses pre-loaded custom sound files for a better user experience
 * compared to generic system tones.
 *
 * @param soundPool SoundPool instance for playing sounds
 * @param soundIds Map of HapticEvent to loaded sound IDs
 * @param event The haptic event to play audio for
 */
private fun performAudioCue(
    soundPool: SoundPool?,
    soundIds: Map<HapticEvent, Int>?,
    event: HapticEvent
) {
    if (soundPool == null || soundIds == null) return

    // ERROR has no sound - haptic only
    if (event == HapticEvent.ERROR) {
        Timber.e("Audio cue: ERROR (haptic only)")
        return
    }

    val soundId = soundIds[event]
    if (soundId == null || soundId == 0) {
        Timber.w("No sound loaded for event: $event")
        return
    }

    try {
        soundPool.play(
            soundId,
            0.8f,  // left volume (80%)
            0.8f,  // right volume (80%)
            1,     // priority
            0,     // no loop
            1.0f   // normal playback rate
        )
        Timber.v("Audio cue: $event")
    } catch (e: Exception) {
        Timber.w(e, "Failed to play sound for $event")
    }
}
