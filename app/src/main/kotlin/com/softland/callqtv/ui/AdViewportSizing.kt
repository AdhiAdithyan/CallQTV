package com.softland.callqtv.ui

import android.content.Context
import android.util.Log
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import coil.request.ImageRequest
import kotlin.math.roundToInt

/** Pixel size of the on-screen ad pane (after layout). */
data class AdViewportPx(
    val width: Int,
    val height: Int,
) {
    fun isValid(): Boolean = width > 0 && height > 0
}

/**
 * Maps the physical ad area to decode / track-selection targets so any source resolution
 * scales efficiently into the pane without decoding full 4K frames on a small strip.
 */
object AdViewportSizing {
    private const val TAG = "AdViewportSizing"

    @Volatile
    var lastViewport: AdViewportPx = AdViewportPx(1280, 720)

    fun fromConstraints(maxWidthPx: Int, maxHeightPx: Int, context: Context): AdViewportPx {
        val dm = context.resources.displayMetrics
        val capW = dm.widthPixels.coerceAtLeast(480)
        val capH = dm.heightPixels.coerceAtLeast(360)
        val w = maxWidthPx.coerceIn(1, capW)
        val h = maxHeightPx.coerceIn(1, capH)
        return AdViewportPx(w, h).also { lastViewport = it }
    }

    /**
     * Target decode size with modest headroom for sharpness on high-DPI TVs.
     * Floors avoid selecting a tiny HLS/MP4 rendition that looks grainy when letterboxed up.
     */
    fun decodeTarget(viewport: AdViewportPx, context: Context): AdViewportPx {
        if (!viewport.isValid()) return lastViewport
        val dm = context.resources.displayMetrics
        val headroom = if (dm.density >= 2f) 1.08f else 1.04f
        val minW = (viewport.width * 0.9f).roundToInt().coerceAtLeast(640)
        val minH = (viewport.height * 0.9f).roundToInt().coerceAtLeast(360)
        val w = (viewport.width * headroom).roundToInt()
            .coerceIn(minW, dm.widthPixels)
        val h = (viewport.height * headroom).roundToInt()
            .coerceIn(minH, dm.heightPixels)
        return AdViewportPx(w, h)
    }

    fun maxVideoBitrateForViewport(
        target: AdViewportPx,
        isLowBandwidth: Boolean,
        isLikelyLive: Boolean,
        videoUrl: String = "",
    ): Int {
        val isLocalFile = videoUrl.isNotBlank() &&
            !videoUrl.startsWith("http://", ignoreCase = true) &&
            !videoUrl.startsWith("https://", ignoreCase = true)
        if (isLocalFile && !isLowBandwidth) {
            return 12_000_000
        }
        if (isLowBandwidth && isLikelyLive) return 1_800_000
        if (isLowBandwidth) return 3_000_000
        val area = target.width.toLong() * target.height
        val refArea = 1920L * 1080L
        val areaScale = (area.toDouble() / refArea).coerceIn(0.45, 1.0)
        return (6_500_000 * areaScale).roundToInt().coerceIn(2_000_000, 12_000_000)
    }

    @UnstableApi
    fun applyVideoTrackConstraints(
        player: ExoPlayer,
        viewport: AdViewportPx,
        context: Context,
        videoUrl: String,
        isLowBandwidth: Boolean,
    ) {
        val target = decodeTarget(viewport, context)
        val lower = videoUrl.lowercase()
        val isLikelyLive = lower.contains(".m3u8") ||
            lower.contains(".mpd") ||
            lower.contains("live")
        val maxBitrate = maxVideoBitrateForViewport(target, isLowBandwidth, isLikelyLive, videoUrl)
        val minBitrate = when {
            isLowBandwidth -> 0
            isLikelyLive -> 800_000
            else -> 1_500_000
        }
        try {
            val updated = player.trackSelectionParameters.buildUpon()
                .setMaxVideoSize(target.width, target.height)
                .setMaxVideoFrameRate(60)
                .setMinVideoBitrate(minBitrate)
                .setMaxVideoBitrate(maxBitrate)
                .build()
            player.trackSelectionParameters = updated
            Log.i(
                TAG,
                "track cap ${target.width}x${target.height} bitrate=${maxBitrate}bps " +
                    "viewport=${viewport.width}x${viewport.height} live=$isLikelyLive"
            )
        } catch (e: Exception) {
            Log.w(TAG, "applyVideoTrackConstraints failed: ${e.message}")
        }
    }

    fun coilImageRequestBuilder(
        context: Context,
        data: Any?,
        viewport: AdViewportPx,
    ): ImageRequest.Builder {
        val target = decodeTarget(viewport, context)
        return ImageRequest.Builder(context)
            .data(data)
            .size(target.width, target.height)
            .crossfade(true)
            .allowHardware(true)
    }
}
