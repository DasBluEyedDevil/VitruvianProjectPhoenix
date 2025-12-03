package com.example.vitruvianredux.presentation.screen

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
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

    // Map of HapticEvent to raw resource ID
    // Using MediaPlayer instead of SoundPool to prevent interrupting music playback
    // Root cause analysis (Issue #180):
    // - SoundPool with various USAGE types still caused Spotify to pause over Bluetooth
    // - MediaPlayer with USAGE_NOTIFICATION_EVENT is designed to play alongside music
    // - Key: We do NOT request audio focus, allowing music to continue playing
    val soundResources = remember {
        mapOf(
            HapticEvent.REP_COMPLETED to R.raw.beep,
            HapticEvent.WARMUP_COMPLETE to R.raw.beepboop,
            HapticEvent.WORKOUT_COMPLETE to R.raw.boopbeepbeep,
            HapticEvent.WORKOUT_START to R.raw.chirpchirp,
            HapticEvent.WORKOUT_END to R.raw.chirpchirp,
            HapticEvent.REST_ENDING to R.raw.restover
            // ERROR: no sound (haptic only)
        )
    }

    // Track active MediaPlayers for cleanup
    val activePlayers = remember { mutableListOf<MediaPlayer>() }

    // Release all MediaPlayers when composable is disposed
    DisposableEffect(Unit) {
        onDispose {
            activePlayers.forEach { player ->
                try {
                    player.release()
                } catch (e: Exception) {
                    Timber.w(e, "Error releasing MediaPlayer")
                }
            }
            activePlayers.clear()
            Timber.v("All MediaPlayers released")
        }
    }

    LaunchedEffect(hapticEvents, beepsEnabled) {
        hapticEvents.collect { event ->
            performHapticFeedback(haptic, event)
            if (beepsEnabled) {
                playAudioCue(context, soundResources, activePlayers, event)
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
 * Plays audio cue for workout events using MediaPlayer.
 *
 * Uses MediaPlayer with USAGE_NOTIFICATION_EVENT to play sounds without
 * interrupting music playback. The key is NOT requesting audio focus.
 *
 * @param context Android context for creating MediaPlayer
 * @param soundResources Map of HapticEvent to raw resource IDs
 * @param activePlayers List to track active players for cleanup
 * @param event The haptic event to play audio for
 */
private fun playAudioCue(
    context: Context,
    soundResources: Map<HapticEvent, Int>,
    activePlayers: MutableList<MediaPlayer>,
    event: HapticEvent
) {
    // ERROR has no sound - haptic only
    if (event == HapticEvent.ERROR) {
        Timber.e("Audio cue: ERROR (haptic only)")
        return
    }

    val resourceId = soundResources[event]
    if (resourceId == null) {
        Timber.w("No sound mapped for event: $event")
        return
    }

    try {
        // Create MediaPlayer for this sound
        // Using USAGE_NOTIFICATION_EVENT which is specifically designed to
        // play alongside music without interrupting it
        val player = MediaPlayer.create(context, resourceId)?.apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            setVolume(0.8f, 0.8f)

            // Auto-release when playback completes
            setOnCompletionListener { mp ->
                try {
                    mp.release()
                    activePlayers.remove(mp)
                    Timber.v("MediaPlayer released after completion for $event")
                } catch (e: Exception) {
                    Timber.w(e, "Error releasing MediaPlayer on completion")
                }
            }

            setOnErrorListener { mp, what, extra ->
                Timber.w("MediaPlayer error for $event: what=$what, extra=$extra")
                try {
                    mp.release()
                    activePlayers.remove(mp)
                } catch (e: Exception) {
                    Timber.w(e, "Error releasing MediaPlayer on error")
                }
                true
            }
        }

        if (player != null) {
            activePlayers.add(player)
            player.start()
            Timber.d("Audio cue: $event (MediaPlayer started)")
        } else {
            Timber.w("Failed to create MediaPlayer for $event")
        }
    } catch (e: Exception) {
        Timber.w(e, "Failed to play sound for $event")
    }
}
