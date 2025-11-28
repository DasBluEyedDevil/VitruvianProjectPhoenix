package com.example.vitruvianredux.presentation.screen

import android.media.AudioAttributes
import android.media.SoundPool
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
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
 *
 * @param hapticEvents Flow of haptic events to respond to
 * @param beepsEnabled Whether to play audio cues (haptic feedback is always enabled)
 */
@Composable
fun HapticFeedbackEffect(
    hapticEvents: SharedFlow<HapticEvent>,
    beepsEnabled: Boolean = true
) {
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current

    // Track which sounds have finished loading (SoundPool.load is async!)
    val loadedSounds = remember { mutableStateOf(setOf<Int>()) }

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
                .build().also { pool ->
                    // Track when sounds finish loading - SoundPool.load() is async!
                    pool.setOnLoadCompleteListener { _, sampleId, status ->
                        if (status == 0) {
                            loadedSounds.value = loadedSounds.value + sampleId
                            Timber.d("Sound $sampleId loaded successfully (${loadedSounds.value.size} total)")
                        } else {
                            Timber.w("Sound $sampleId failed to load with status $status")
                        }
                    }
                }
        } catch (e: Exception) {
            Timber.w(e, "Failed to create SoundPool")
            null
        }
    }

    // Load sounds into memory, mapping each HapticEvent to its sound ID
    // Note: load() returns immediately but actual loading is async - tracked via OnLoadCompleteListener
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
                ).also { ids ->
                    Timber.d("Queued ${ids.size} sounds for loading: $ids")
                }
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

    LaunchedEffect(hapticEvents, beepsEnabled) {
        hapticEvents.collect { event ->
            performHapticFeedback(haptic, event)
            if (beepsEnabled) {
                performAudioCue(soundPool, soundIds, loadedSounds.value, event)
            }
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
 * @param loadedSounds Set of sound IDs that have finished async loading
 * @param event The haptic event to play audio for
 */
private fun performAudioCue(
    soundPool: SoundPool?,
    soundIds: Map<HapticEvent, Int>?,
    loadedSounds: Set<Int>,
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
        Timber.w("No sound mapped for event: $event")
        return
    }

    // Check if sound has finished async loading
    if (soundId !in loadedSounds) {
        Timber.w("Sound $soundId for $event not yet loaded (loaded: ${loadedSounds.size}/${soundIds.size})")
        return
    }

    try {
        val streamId = soundPool.play(
            soundId,
            0.8f,  // left volume (80%)
            0.8f,  // right volume (80%)
            1,     // priority
            0,     // no loop
            1.0f   // normal playback rate
        )
        if (streamId == 0) {
            Timber.w("SoundPool.play() returned 0 for $event - sound may have been unloaded")
        } else {
            Timber.d("Audio cue: $event (soundId=$soundId, streamId=$streamId)")
        }
    } catch (e: Exception) {
        Timber.w(e, "Failed to play sound for $event")
    }
}
