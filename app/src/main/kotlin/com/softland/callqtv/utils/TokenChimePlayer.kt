package com.softland.callqtv.utils

import android.content.Context
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.ToneGenerator
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

private const val MAX_CUSTOM_CHIME_STARTUP_MS = 1200L
private const val MAX_CUSTOM_CHIME_PLAYBACK_MS = 1500L

/**
 * Plays a short built-in chime or a custom audio URL before announcing a token.
 * Returns when the cue starts; [onAudioStart] is the right place to publish the current token tile.
 */
suspend fun playTokenChime(
    context: Context,
    soundKey: String,
    tokenAudioUrl: String? = null,
    counterAudioUrl: String? = null,
    onAudioStart: (() -> Unit)? = null,
) {
    withContext(Dispatchers.Main.immediate) {
        val playedCustomCue = when {
            !counterAudioUrl.isNullOrBlank() -> playMediaUrl(context, counterAudioUrl, onAudioStart)
            !tokenAudioUrl.isNullOrBlank() -> playMediaUrl(context, tokenAudioUrl, onAudioStart)
            else -> false
        }
        if (!playedCustomCue) {
            playSystemTone(soundKey, onAudioStart)
        }
    }
}

private suspend fun playMediaUrl(
    context: Context,
    url: String,
    onAudioStart: (() -> Unit)? = null,
): Boolean {
    return withContext(Dispatchers.IO) {
        val mediaPlayer = MediaPlayer()
        var releaseAfterPlayback = false
        val releasePlayer: (MediaPlayer) -> Unit = { mp ->
            runCatching {
                mp.setOnPreparedListener(null)
                mp.setOnCompletionListener(null)
                mp.setOnErrorListener(null)
                if (mp.isPlaying) mp.stop()
                mp.release()
            }
        }
        try {
            mediaPlayer.setDataSource(context, Uri.parse(url))
            mediaPlayer.setAudioAttributes(
                android.media.AudioAttributes.Builder()
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .setUsage(android.media.AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .build(),
            )
            val started = withTimeoutOrNull(MAX_CUSTOM_CHIME_STARTUP_MS) {
                suspendCancellableCoroutine { continuation ->
                    mediaPlayer.setOnPreparedListener { preparedPlayer ->
                        try {
                            preparedPlayer.setOnCompletionListener { releasePlayer(it) }
                            preparedPlayer.setOnErrorListener { mp, _, _ ->
                                releasePlayer(mp)
                                true
                            }
                            preparedPlayer.start()
                            releaseAfterPlayback = true
                            onAudioStart?.invoke()
                            if (continuation.isActive) continuation.resume(true)
                        } catch (e: Exception) {
                            Log.e("TokenChime", "Failed starting chime URL: $url", e)
                            if (continuation.isActive) continuation.resume(false)
                        }
                    }
                    mediaPlayer.setOnErrorListener { _, what, extra ->
                        Log.w("TokenChime", "Error preparing chime URL: $url what=$what extra=$extra")
                        if (continuation.isActive) continuation.resume(false)
                        true
                    }
                    mediaPlayer.prepareAsync()
                }
            } ?: false
            if (!started) {
                Log.w("TokenChime", "Timed out starting chime URL: $url")
                return@withContext false
            }
            true
        } catch (e: Exception) {
            Log.e("TokenChime", "Error playing chime URL: $url", e)
            false
        } finally {
            if (!releaseAfterPlayback) {
                releasePlayer(mediaPlayer)
            }
        }
    }
}

/** Rough audible length of the built-in chime so TTS can start after the tone tail. */
fun estimatedChimeAudibleMs(soundKey: String, hasCustomAudioUrl: Boolean): Long {
    if (hasCustomAudioUrl) return 450L
    return systemToneForKey(soundKey).second.toLong() + 100L
}

/** Keys must match [ThemeColorManager.notificationSoundOptions]. */
internal fun playSystemTone(soundKey: String, onAudioStart: (() -> Unit)? = null) {
    val (tone, durationMs) = systemToneForKey(soundKey)
    val toneGen = ToneGenerator(AudioManager.STREAM_MUSIC, 80)
    try {
        toneGen.startTone(tone, durationMs)
        onAudioStart?.invoke()
    } catch (_: Exception) {
        onAudioStart?.invoke()
        toneGen.release()
        return
    }
    Handler(Looper.getMainLooper()).postDelayed(
        { runCatching { toneGen.release() } },
        durationMs.toLong() + 50L,
    )
}

private fun systemToneForKey(soundKey: String): Pair<Int, Int> = when (soundKey) {
    "ding" -> ToneGenerator.TONE_PROP_BEEP2 to 120
    "ding2" -> ToneGenerator.TONE_PROP_BEEP2 to 280
    "ding3" -> ToneGenerator.TONE_PROP_BEEP2 to 350
    "ding4" -> ToneGenerator.TONE_PROP_BEEP2 to 420
    "ding5" -> ToneGenerator.TONE_PROP_BEEP2 to 500
    "double" -> ToneGenerator.TONE_PROP_ACK to 240
    "double2" -> ToneGenerator.TONE_PROP_ACK to 320
    "double3" -> ToneGenerator.TONE_PROP_ACK to 400
    "double4" -> ToneGenerator.TONE_PROP_ACK to 480
    "soft" -> ToneGenerator.TONE_PROP_BEEP to 150
    "soft2" -> ToneGenerator.TONE_PROP_BEEP to 240
    "soft3" -> ToneGenerator.TONE_PROP_BEEP to 320
    "soft4" -> ToneGenerator.TONE_PROP_BEEP to 400
    "alert" -> ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD to 260
    "alert2" -> ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD to 360
    "alert3" -> ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD to 450
    "alert4" -> ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD to 520
    "bell" -> ToneGenerator.TONE_SUP_PIP to 240
    "bell2" -> ToneGenerator.TONE_SUP_PIP to 320
    "bell3" -> ToneGenerator.TONE_SUP_PIP to 400
    "bell4" -> ToneGenerator.TONE_SUP_PIP to 480
    "church1" -> ToneGenerator.TONE_CDMA_CALL_SIGNAL_ISDN_NORMAL to 360
    "church2" -> ToneGenerator.TONE_CDMA_HIGH_L to 390
    "church3" -> ToneGenerator.TONE_CDMA_HIGH_PBX_L to 450
    "ping" -> ToneGenerator.TONE_PROP_PROMPT to 180
    "ping2" -> ToneGenerator.TONE_PROP_PROMPT to 260
    "ping3" -> ToneGenerator.TONE_PROP_PROMPT to 340
    "ping4" -> ToneGenerator.TONE_PROP_PROMPT to 420
    "long" -> ToneGenerator.TONE_CDMA_CALL_SIGNAL_ISDN_PING_RING to 420
    "long2" -> ToneGenerator.TONE_CDMA_CALL_SIGNAL_ISDN_PING_RING to 540
    "long3" -> ToneGenerator.TONE_CDMA_CALL_SIGNAL_ISDN_PING_RING to 660
    "long4" -> ToneGenerator.TONE_CDMA_CALL_SIGNAL_ISDN_PING_RING to 780
    "chime1" -> ToneGenerator.TONE_SUP_RINGTONE to 360
    "chime2" -> ToneGenerator.TONE_SUP_RINGTONE to 480
    "chime3" -> ToneGenerator.TONE_SUP_RINGTONE to 600
    "chime4" -> ToneGenerator.TONE_SUP_RINGTONE to 720
    "hi1" -> ToneGenerator.TONE_SUP_RADIO_ACK to 180
    "hi2" -> ToneGenerator.TONE_SUP_RADIO_ACK to 260
    "hi3" -> ToneGenerator.TONE_SUP_RADIO_ACK to 340
    "hi4" -> ToneGenerator.TONE_SUP_RADIO_ACK to 420
    "low1" -> ToneGenerator.TONE_SUP_CONGESTION to 180
    "low2" -> ToneGenerator.TONE_SUP_CONGESTION to 260
    "low3" -> ToneGenerator.TONE_SUP_CONGESTION to 340
    "low4" -> ToneGenerator.TONE_SUP_CONGESTION to 420
    "tone1" -> ToneGenerator.TONE_SUP_DIAL to 210
    "tone2" -> ToneGenerator.TONE_SUP_BUSY to 210
    "tone3" -> ToneGenerator.TONE_SUP_CALL_WAITING to 300
    "tone4" -> ToneGenerator.TONE_SUP_CONFIRM to 300
    "tone5" -> ToneGenerator.TONE_SUP_ERROR to 300
    "tone6" -> ToneGenerator.TONE_SUP_INTERCEPT to 300
    "tone7" -> ToneGenerator.TONE_SUP_CALL_WAITING to 390
    else -> ToneGenerator.TONE_PROP_BEEP2 to 240
}
