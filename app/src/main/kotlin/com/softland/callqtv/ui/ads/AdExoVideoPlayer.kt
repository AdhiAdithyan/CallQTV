@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])

package com.softland.callqtv.ui.ads

import android.content.Context
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.TextureView
import android.view.animation.LinearInterpolator
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import com.softland.callqtv.ui.AdViewportPx
import com.softland.callqtv.ui.AdViewportSizing
import com.softland.callqtv.ui.detachViewFromParent
import com.softland.callqtv.ui.isLikelyLiveStreamUrl
import com.softland.callqtv.ui.ads.AdMediaType
import com.softland.callqtv.ui.ads.MediaEngine
import com.softland.callqtv.ui.ads.MIN_TRUSTED_YOUTUBE_DURATION_MS
import com.softland.callqtv.ui.ads.getYouTubeAdMaxPlaybackMs
import com.softland.callqtv.ui.ads.isValidYoutubeVideoIdForEmbed
import com.softland.callqtv.utils.ThemeColorManager
import com.softland.callqtv.utils.TokenAnnouncer
import com.softland.callqtv.utils.FileLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.lang.ref.WeakReference
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import com.softland.callqtv.ui.ads.resolveAdMediaType
internal fun isAdLowBandwidthNetwork(context: Context): Boolean =
    com.softland.callqtv.utils.NetworkCompat.isLowBandwidthNetwork(context)


/** Stops playback and clears the queue so the next ad can bind a fresh decoder (avoids MTK NO_MEMORY). */
private fun releaseExoVideoDecoder(player: ExoPlayer, texture: TextureView? = null) {
    try {
        player.playWhenReady = false
        player.stop()
        if (texture != null) {
            player.clearVideoTextureView(texture)
        }
        player.clearMediaItems()
    } catch (_: Exception) {
    }
}

private fun prepareAdVideoOnPlayer(
    player: ExoPlayer,
    texture: TextureView?,
    attachToTexture: Boolean,
    mediaItem: MediaItem,
    videoUrl: String,
    isLowBandwidth: Boolean,
    context: Context,
    viewport: AdViewportPx,
) {
    if (viewport.isValid()) {
        AdViewportSizing.lastViewport = viewport
    }
    if (attachToTexture && texture != null) {
        com.softland.callqtv.ui.ads.MediaEngine.detachTextureFromAllPlayers(texture)
        texture.setTransform(null)
        player.setVideoTextureView(texture)
    }
    applyAdaptiveVideoTrackCap(
        player = player,
        videoUrl = videoUrl,
        isLowBandwidth = isLowBandwidth,
        context = context,
        viewport = viewport,
    )
    player.setMediaItem(mediaItem)
    player.prepare()
    player.playWhenReady = true
}

@Composable
fun AdVideoPlayer(
    videoUrl: String,
    player: ExoPlayer,
    viewport: AdViewportPx = AdViewportSizing.lastViewport,
    sharedTextureView: TextureView? = null,
    attachToTexture: Boolean = true,
    videoSurfaceReadySignal: Int = 0,
    onVideoEnded: () -> Unit,
    onReady: () -> Unit = {},
    onError: () -> Unit = {}
) {
    val context = LocalContext.current
    
    val latestOnVideoEnded by rememberUpdatedState(onVideoEnded)
    val latestOnReady by rememberUpdatedState(onReady)
    val latestOnError by rememberUpdatedState(onError)

    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    val externallyHosted = sharedTextureView != null && attachToTexture
    val videoTextureRef = remember(sharedTextureView, attachToTexture) {
        mutableStateOf(if (externallyHosted) sharedTextureView else null)
    }
    var textureSurfaceGeneration by remember { mutableIntStateOf(0) }
    var latestVideoSize by remember { mutableStateOf(androidx.media3.common.VideoSize.UNKNOWN) }

    var readyReported by remember(videoUrl) { mutableStateOf(false) }
    var terminalEventReported by remember(videoUrl) { mutableStateOf(false) }
    var loadStartedAtMs by remember(videoUrl) { mutableStateOf(0L) }
    var lastSurfaceFrameMs by remember(videoUrl) { mutableLongStateOf(0L) }
    var surfaceRecoveryAttempts by remember(videoUrl) { mutableIntStateOf(0) }
    val isLowBandwidth = remember(videoUrl) { isAdLowBandwidthNetwork(context) }

    DisposableEffect(videoUrl, player) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                when (state) {
                    Player.STATE_READY -> {
                        Log.i(
                            "AdVideoPerf",
                            "STATE_READY in ${System.currentTimeMillis() - loadStartedAtMs} ms for url=${videoUrl.take(120)}"
                        )
                        if (!readyReported) {
                            readyReported = true
                            mainHandler.post { latestOnReady() }
                        }
                    }
                    Player.STATE_ENDED -> {
                        terminalEventReported = true
                        mainHandler.post { latestOnVideoEnded() }
                    }
                    Player.STATE_IDLE,
                    Player.STATE_BUFFERING -> Unit
                }
            }
            override fun onPlayerError(e: androidx.media3.common.PlaybackException) {
                Log.e("AdVideoPlayer", "ExoPlayer error playing $videoUrl: ${e.message}", e)
                Log.e(
                    "AdVideoPerf",
                    "Player error after ${System.currentTimeMillis() - loadStartedAtMs} ms for url=${videoUrl.take(120)}"
                )
                // ExoPlayer/Media3 enforces main-thread access for player methods.
                mainHandler.post {
                    try {
                        player.stop()
                        player.clearMediaItems()
                    } catch (_: Exception) {
                        // Ignore cleanup failures; we still trigger the ad skip.
                    }
                    terminalEventReported = true
                    latestOnError()
                }
            }

            override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) {
                latestVideoSize = videoSize
                lastSurfaceFrameMs = System.currentTimeMillis()
                videoTextureRef.value?.post {
                    applyFitCenterTransform(videoTextureRef.value, videoSize)
                }
            }

            override fun onRenderedFirstFrame() {
                lastSurfaceFrameMs = System.currentTimeMillis()
                if (!readyReported) {
                    readyReported = true
                    Log.i(
                        "AdVideoPerf",
                        "FIRST_FRAME in ${System.currentTimeMillis() - loadStartedAtMs} ms for url=${videoUrl.take(120)}"
                    )
                    mainHandler.post { latestOnReady() }
                }
            }
        }
        
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            // Pause only — clearing the decoder on every Compose dispose caused black frames
            // when the shared TextureView was rebound (prepare ran before surface was ready).
            try {
                val pauseOnly: () -> Unit = {
                    player.playWhenReady = false
                    player.pause()
                }
                val detachTexture: () -> Unit = {
                    videoTextureRef.value?.let { texture ->
                        player.clearVideoTextureView(texture)
                        if (!externallyHosted) {
                            detachViewFromParent(texture)
                            videoTextureRef.value = null
                        }
                    }
                }
                if (Looper.myLooper() == Looper.getMainLooper()) {
                    pauseOnly()
                    detachTexture()
                } else {
                    mainHandler.post {
                        try {
                            pauseOnly()
                            detachTexture()
                        } catch (_: Exception) {
                        }
                    }
                }
            } catch (_: Exception) {
            }
        }
    }

    LaunchedEffect(viewport.width, viewport.height, latestVideoSize) {
        videoTextureRef.value?.post {
            applyFitCenterTransform(videoTextureRef.value, latestVideoSize)
        }
    }

    LaunchedEffect(videoUrl, player, textureSurfaceGeneration, attachToTexture, externallyHosted, videoSurfaceReadySignal) {
        readyReported = false
        terminalEventReported = false
        loadStartedAtMs = System.currentTimeMillis()
        lastSurfaceFrameMs = 0L
        surfaceRecoveryAttempts = 0
        val loadUrl = videoUrl
        val texture = when {
            !attachToTexture -> null
            externallyHosted -> sharedTextureView
            else -> videoTextureRef.value
        }
        if (attachToTexture && (texture == null || !texture.isAvailable)) {
            return@LaunchedEffect
        }
        val mediaItem = try {
            withContext(Dispatchers.Default) {
                buildAdVideoMediaItem(loadUrl)
            }
        } catch (e: Exception) {
            Log.e("AdVideoPlayer", "Failed to create media item: $loadUrl", e)
            null
        }
        if (mediaItem == null) {
            terminalEventReported = true
            latestOnError()
            return@LaunchedEffect
        }
        try {
            val canHandoffFromEnded =
                attachToTexture &&
                    texture != null &&
                    player.playbackState == Player.STATE_ENDED
            if (canHandoffFromEnded) {
                // Reuse the same decoder/surface for the next clip to reduce black frames between ads.
                if (viewport.isValid()) {
                    AdViewportSizing.lastViewport = viewport
                }
                com.softland.callqtv.ui.ads.MediaEngine.detachTextureFromAllPlayers(texture)
                texture.setTransform(null)
                player.setVideoTextureView(texture)
                applyAdaptiveVideoTrackCap(
                    player = player,
                    videoUrl = loadUrl,
                    isLowBandwidth = isLowBandwidth,
                    context = context,
                    viewport = viewport,
                )
                player.setMediaItem(mediaItem)
                player.prepare()
                player.playWhenReady = true
            } else {
                releaseExoVideoDecoder(player, texture)
                prepareAdVideoOnPlayer(
                    player = player,
                    texture = texture,
                    attachToTexture = attachToTexture,
                    mediaItem = mediaItem,
                    videoUrl = loadUrl,
                    isLowBandwidth = isLowBandwidth,
                    context = context,
                    viewport = viewport,
                )
            }
            Log.i(
                "AdVideoPerf",
                "Prepared ${if (attachToTexture) "with" else "without"} surface url=${loadUrl.take(120)}"
            )
        } catch (e: Exception) {
            Log.e("AdVideoPlayer", "Failed to prepare video: $loadUrl", e)
            terminalEventReported = true
            latestOnError()
        }
    }

    // Detect frozen TextureView (audio continues but last frame sticks).
    LaunchedEffect(videoUrl, player, attachToTexture) {
        if (!attachToTexture) return@LaunchedEffect
        while (true) {
            delay(2_000L)
            if (terminalEventReported) return@LaunchedEffect
            if (!player.isPlaying || player.playbackState != Player.STATE_READY) continue
            val lastFrame = lastSurfaceFrameMs
            if (lastFrame <= 0L) continue
            val staleMs = System.currentTimeMillis() - lastFrame
            if (staleMs < 3_500L) continue

            val texture = videoTextureRef.value ?: sharedTextureView
            if (surfaceRecoveryAttempts < 2) {
                surfaceRecoveryAttempts += 1
                Log.w(
                    "AdVideoPlayer",
                    "Video surface stale ${staleMs}ms; rebinding surface (attempt $surfaceRecoveryAttempts) url=${videoUrl.take(90)}",
                )
                mainHandler.post {
                    try {
                        texture?.setTransform(null)
                        if (texture != null) {
                            com.softland.callqtv.ui.ads.MediaEngine.detachTextureFromAllPlayers(texture)
                            player.setVideoTextureView(texture)
                        }
                        val pos = player.currentPosition.coerceAtLeast(1L)
                        player.seekTo(pos)
                        player.playWhenReady = true
                        lastSurfaceFrameMs = System.currentTimeMillis()
                    } catch (e: Exception) {
                        Log.w("AdVideoPlayer", "Surface recovery failed: ${e.message}")
                    }
                }
            } else {
                Log.e(
                    "AdVideoPlayer",
                    "Video surface frozen after recovery; skipping url=${videoUrl.take(90)}",
                )
                terminalEventReported = true
                latestOnError()
                return@LaunchedEffect
            }
        }
    }

    // If ExoPlayer never reaches STATE_READY, treat it as a failed ad and skip.
    // Keep a longer timeout for low-bandwidth/live links.
    LaunchedEffect(videoUrl) {
        val isLikelyLive = videoUrl.lowercase().contains(".m3u8") ||
            videoUrl.lowercase().contains(".mpd") ||
            videoUrl.lowercase().contains("live")
        val timeoutMs = when {
            isLikelyLive && isLowBandwidth -> 120_000L
            isLikelyLive -> 90_000L
            isLowBandwidth -> 45_000L
            else -> 35_000L
        }
        delay(timeoutMs)
        if (!readyReported && !terminalEventReported) {
            Log.w("AdVideoPlayer", "Video not ready within timeout; skipping. url=$videoUrl")
            FileLogger.logError(context, "AdVideoPlayer", "Video not ready within timeout; skipping. url=$videoUrl")
            terminalEventReported = true
            latestOnError()
        }
    }

    if (externallyHosted || !attachToTexture) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .focusProperties { canFocus = false }
        )
    } else {
        AndroidView(
            factory = {
                val texture = buildSharedAdTextureView(context)
                detachViewFromParent(texture)
                bindAdVideoTextureView(
                    texture = texture,
                    player = player,
                    onSurfaceReady = { textureSurfaceGeneration += 1 },
                    onLayout = { applyFitCenterTransform(texture, latestVideoSize) },
                    onSurfaceFrame = { lastSurfaceFrameMs = System.currentTimeMillis() },
                )
                videoTextureRef.value = texture
                texture
            },
            update = { view ->
                detachViewFromParent(view)
                if (videoTextureRef.value !== view) {
                    videoTextureRef.value?.let { oldTexture ->
                        if (oldTexture !== view) player.clearVideoTextureView(oldTexture)
                    }
                    videoTextureRef.value = view
                }
                bindAdVideoTextureView(
                    texture = view,
                    player = player,
                    onSurfaceReady = { textureSurfaceGeneration += 1 },
                    onLayout = { applyFitCenterTransform(view, latestVideoSize) },
                    onSurfaceFrame = { lastSurfaceFrameMs = System.currentTimeMillis() },
                )
            },
            modifier = Modifier
                .fillMaxSize()
                .focusProperties { canFocus = false }
        )
    }
}

private fun buildAdVideoMediaItem(loadUrl: String): MediaItem {
    val lower = loadUrl.lowercase()
    val mimeType = when {
        lower.contains(".m3u8") || lower.contains("format=m3u8") -> androidx.media3.common.MimeTypes.APPLICATION_M3U8
        lower.contains(".mpd") -> androidx.media3.common.MimeTypes.APPLICATION_MPD
        else -> null
    }
    return if (mimeType != null) {
        MediaItem.Builder()
            .setUri(loadUrl)
            .setMimeType(mimeType)
            .build()
    } else {
        MediaItem.fromUri(loadUrl)
    }
}

/**
 * Binds ExoPlayer to [texture] when the surface exists. Many TV GPUs report decoder READY
 * while the TextureView surface is still unavailable — preparing earlier shows a black panel.
 */
internal fun bindAdTextureSurfaceOnly(
    texture: TextureView,
    onSurfaceReady: () -> Unit,
) {
    fun notify() {
        if (texture.isAvailable) onSurfaceReady()
    }
    if (texture.isAvailable) {
        notify()
        return
    }
    texture.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
        override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
            texture.surfaceTextureListener = null
            notify()
        }

        override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) = Unit

        override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
            texture.surfaceTextureListener = null
            return true
        }

        override fun onSurfaceTextureUpdated(surface: SurfaceTexture) = Unit
    }
}

private class AdTextureCallbackState(
    var onLayout: () -> Unit,
    var onSurfaceFrame: () -> Unit,
    var onSurfaceReady: () -> Unit,
)

private fun bindAdVideoTextureView(
    texture: TextureView,
    player: ExoPlayer,
    onSurfaceReady: () -> Unit,
    onLayout: () -> Unit,
    onSurfaceFrame: () -> Unit = {},
) {
    @Suppress("UNCHECKED_CAST")
    val state = (texture.tag as? AdTextureCallbackState)
        ?: AdTextureCallbackState(onLayout, onSurfaceFrame, onSurfaceReady).also { created ->
            texture.tag = created
            val layoutHandler = Handler(Looper.getMainLooper())
            var layoutRunnable: Runnable? = null
            texture.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
                layoutRunnable?.let { layoutHandler.removeCallbacks(it) }
                layoutRunnable = Runnable { created.onLayout() }
                layoutHandler.postDelayed(layoutRunnable!!, 32L)
            }
            texture.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
                    created.onSurfaceReady()
                    created.onLayout()
                }

                override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
                    created.onLayout()
                }

                override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                    texture.surfaceTextureListener = null
                    return true
                }

                override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {
                    created.onSurfaceFrame()
                }
            }
        }
    state.onLayout = onLayout
    state.onSurfaceFrame = onSurfaceFrame
    state.onSurfaceReady = onSurfaceReady

    if (texture.isAvailable) {
        onSurfaceReady()
        onLayout()
    }
}

internal fun buildSharedAdTextureView(context: Context): TextureView {
    return TextureView(context).apply {
        setOpaque(true)
        isFocusable = false
        isFocusableInTouchMode = false
        isClickable = false
        isLongClickable = false
        layoutParams = android.view.ViewGroup.LayoutParams(
            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            android.view.ViewGroup.LayoutParams.MATCH_PARENT
        )
    }
}

private fun applyAdaptiveVideoTrackCap(
    player: ExoPlayer,
    videoUrl: String,
    isLowBandwidth: Boolean,
    context: Context,
    viewport: AdViewportPx = AdViewportSizing.lastViewport,
) {
    AdViewportSizing.applyVideoTrackConstraints(
        player = player,
        viewport = viewport,
        context = context,
        videoUrl = videoUrl,
        isLowBandwidth = isLowBandwidth,
    )
}

private fun applyFitCenterTransform(
    textureView: TextureView?,
    videoSize: androidx.media3.common.VideoSize
) {
    if (textureView == null) return
    val viewWidth = textureView.width.toFloat()
    val viewHeight = textureView.height.toFloat()
    if (viewWidth <= 0f || viewHeight <= 0f) return

    val rawVideoWidth = videoSize.width
    val rawVideoHeight = videoSize.height
    if (rawVideoWidth <= 0 || rawVideoHeight <= 0) {
        textureView.setTransform(null)
        return
    }

    val pixelRatio = if (videoSize.pixelWidthHeightRatio > 0f) videoSize.pixelWidthHeightRatio else 1f
    val videoWidth = rawVideoWidth.toFloat() * pixelRatio
    val videoHeight = rawVideoHeight.toFloat()
    if (videoWidth <= 0f || videoHeight <= 0f) {
        textureView.setTransform(null)
        return
    }

    val viewAspect = viewWidth / viewHeight
    val videoAspect = videoWidth / videoHeight
    val matrix = Matrix()

    val scaleX: Float
    val scaleY: Float
    if (videoAspect > viewAspect) {
        // Wider than container: fit width, letterbox top/bottom.
        scaleX = 1f
        scaleY = viewAspect / videoAspect
    } else {
        // Taller than container: fit height, letterbox left/right.
        scaleX = videoAspect / viewAspect
        scaleY = 1f
    }

    matrix.setScale(scaleX, scaleY, viewWidth / 2f, viewHeight / 2f)
    val current = Matrix()
    textureView.getTransform(current)
    if (!matrixAlmostEqual(current, matrix)) {
        textureView.setTransform(matrix)
        textureView.invalidate()
    }
}

private fun matrixAlmostEqual(a: Matrix, b: Matrix, epsilon: Float = 0.001f): Boolean {
    val av = FloatArray(9)
    val bv = FloatArray(9)
    a.getValues(av)
    b.getValues(bv)
    for (i in 0 until 9) {
        if (kotlin.math.abs(av[i] - bv[i]) > epsilon) return false
    }
    return true
}

