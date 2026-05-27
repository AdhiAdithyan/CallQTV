@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])

package com.softland.callqtv.ui.ads

import android.content.Context
import android.view.TextureView
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import com.softland.callqtv.ui.AdViewportSizing
import com.softland.callqtv.ui.ads.isAdLowBandwidthNetwork
import com.softland.callqtv.utils.ThemeColorManager
import com.softland.callqtv.utils.UnsafeOkHttpClient
import java.io.File
import java.util.concurrent.TimeUnit
/**
 * On some MediaTek devices `c2.mtk.*` Codec2 components fail allocation with NO_MEMORY when
 * graphic/ION memory is already under pressure (WebView, multiple surfaces, another decoder).
 * Prefer other decoders first so ExoPlayer avoids a doomed init + noisy stack traces; MTK
 * remains in the list as a last resort when no alternative exists.
 */
private object DeprioritizeMtkCodecSelector : MediaCodecSelector {
    override fun getDecoderInfos(
        mimeType: String,
        requiresSecureDecoder: Boolean,
        requiresTunnelingDecoder: Boolean
    ) = MediaCodecSelector.DEFAULT.getDecoderInfos(
        mimeType,
        requiresSecureDecoder,
        requiresTunnelingDecoder
    ).let { base ->
        if (mimeType != MimeTypes.VIDEO_H264 && mimeType != MimeTypes.VIDEO_H265) {
            return@let base
        }
        val nonMtk = base.filterNot { it.name.contains("mtk", ignoreCase = true) }
        val mtk = base.filter { it.name.contains("mtk", ignoreCase = true) }
        if (nonMtk.isNotEmpty() && mtk.isNotEmpty()) nonMtk + mtk else base
    }
}

object MediaEngine {
    private var player1: ExoPlayer? = null
    private var player2: ExoPlayer? = null
    private var simpleCache: androidx.media3.datasource.cache.Cache? = null
    private var cacheFactory: DataSource.Factory? = null

    /** Snapshot of Exo volumes before ducking for token speech (null = not ducked). */
    private var announcementDuckSavedVolumes: Pair<Float, Float>? = null

    private const val ANNOUNCEMENT_DUCK_VOLUME = 0.12f

    private fun createPlayer(context: Context): ExoPlayer {
        // Large/remote ad videos can be slow to establish connections on TV networks.
        // ExoPlayer's HTTP stack uses OkHttp underneath, so we must set longer timeouts here too.
        val httpClient = UnsafeOkHttpClient.getUnsafeOkHttpClient()
            .newBuilder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(180, TimeUnit.SECONDS)
            .writeTimeout(180, TimeUnit.SECONDS)
            .callTimeout(240, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
        val okHttpFactory = OkHttpDataSource.Factory(httpClient)
        
        if (simpleCache == null) {
            val cacheDir = File(context.cacheDir, "ad_video_cache")
            // Increase cache size to 500MB for higher resolution videos
            val evictor = androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor(500 * 1024 * 1024)
            simpleCache = androidx.media3.datasource.cache.SimpleCache(cacheDir, evictor, androidx.media3.database.StandaloneDatabaseProvider(context))
        }
        
        if (cacheFactory == null) {
            val upstreamFactory = DefaultDataSource.Factory(context, okHttpFactory)
            cacheFactory = androidx.media3.datasource.cache.CacheDataSource.Factory()
                .setCache(simpleCache!!)
                .setUpstreamDataSourceFactory(upstreamFactory)
                .setFlags(androidx.media3.datasource.cache.CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
        }

        val loadControl = DefaultLoadControl.Builder()
            // Enough buffer for high-bitrate files; reduces rebuffer stutter / apparent "hangs".
            .setBufferDurationsMs(4_000, 45_000, 1_500, 2_500)
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        // Enable decoder fallback to improve compatibility on heterogeneous Android TV/SoC decoders.
        // Some devices fail MediaCodec.configure() for certain AVC streams; fallback lets ExoPlayer
        // try alternative initialization paths instead of getting stuck on a single codec.
        val renderersFactory = androidx.media3.exoplayer.DefaultRenderersFactory(context.applicationContext)
            .setMediaCodecSelector(DeprioritizeMtkCodecSelector)
            .setEnableDecoderFallback(true)

        // Cap to the measured ad viewport (updated from [AdArea]); avoids decoding 4K into a narrow strip.
        val initialTarget = AdViewportSizing.decodeTarget(AdViewportSizing.lastViewport, context)
        val trackSelector = DefaultTrackSelector(context.applicationContext).apply {
            setParameters(
                buildUponParameters()
                    .setMaxVideoSize(initialTarget.width, initialTarget.height)
                    .setMaxVideoFrameRate(60)
                    .setExceedVideoConstraintsIfNecessary(true)
                    .setExceedRendererCapabilitiesIfNecessary(true)
                    .build()
            )
        }

        return ExoPlayer.Builder(context.applicationContext)
            .setRenderersFactory(renderersFactory)
            .setTrackSelector(trackSelector)
            .setMediaSourceFactory(DefaultMediaSourceFactory(cacheFactory!!))
            .setLoadControl(loadControl)
            .build().apply {
                repeatMode = Player.REPEAT_MODE_OFF
                videoScalingMode = androidx.media3.common.C.VIDEO_SCALING_MODE_SCALE_TO_FIT
                volume = if (ThemeColorManager.isAdSoundEnabled(context)) 1f else 0f
            }
    }

    fun get(context: Context, index: Int = 0): ExoPlayer {
        return if (index == 0) {
            if (player1 == null) player1 = createPlayer(context)
            player1!!
        } else {
            if (player2 == null) player2 = createPlayer(context)
            player2!!
        }
    }

    /**
     * Pre-initialize the active ad player and cache to avoid heavy work on the first ad.
     * Only player index 0 is used in production (`AdArea`); avoid allocating a second decoder
     * on memory-constrained TV SoCs (MTK c2.mtk.avc.decoder NO_MEMORY).
     */
    fun warmUp(context: Context) {
        if (player1 == null) player1 = createPlayer(context)
    }

    /** Second player for off-screen preload (avoids black handoff between video ads). */
    fun ensureSecondPlayer(context: Context) {
        if (player2 == null) player2 = createPlayer(context)
    }

    /**
     * Called when the ad pane is laid out. Track caps are applied only while [ExoPlayer] is idle
     * so a resize mid-playback does not reconfigure the decoder (grain / crack / freeze).
     */
    fun detachTextureFromAllPlayers(texture: TextureView) {
        try {
            player1?.clearVideoTextureView(texture)
        } catch (_: Exception) {
        }
        try {
            player2?.clearVideoTextureView(texture)
        } catch (_: Exception) {
        }
    }

    fun updateViewport(context: Context, widthPx: Int, heightPx: Int) {
        val viewport = AdViewportSizing.fromConstraints(widthPx, heightPx, context)
        listOfNotNull(player1, player2).forEach { player ->
            if (player.playbackState != Player.STATE_IDLE) return@forEach
            AdViewportSizing.applyVideoTrackConstraints(
                player = player,
                viewport = viewport,
                context = context.applicationContext,
                videoUrl = "",
                isLowBandwidth = isAdLowBandwidthNetwork(context),
            )
        }
    }

    fun updateVolume(context: Context) {
        val vol = if (ThemeColorManager.isAdSoundEnabled(context)) 1f else 0f
        player1?.volume = vol
        player2?.volume = vol
    }

    fun duckForAnnouncement() {
        val a = player1?.volume ?: 1f
        val b = player2?.volume ?: 1f
        announcementDuckSavedVolumes = a to b
        player1?.volume = ANNOUNCEMENT_DUCK_VOLUME
        player2?.volume = ANNOUNCEMENT_DUCK_VOLUME
    }

    fun restoreAfterAnnouncement(context: Context) {
        val saved = announcementDuckSavedVolumes
        announcementDuckSavedVolumes = null
        val wantSound = ThemeColorManager.isAdSoundEnabled(context)
        val target = when {
            saved == null -> if (wantSound) 1f else 0f
            wantSound -> saved.first
            else -> 0f
        }
        player1?.volume = target
        player2?.volume = target
    }

    fun shutdown() {
        announcementDuckSavedVolumes = null
        player1?.release()
        player2?.release()
        player1 = null
        player2 = null
    }
}
