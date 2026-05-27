@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])

package com.softland.callqtv.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.Toast
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.Canvas
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.ExperimentalFoundationApi
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import com.softland.callqtv.AppBackgroundCoordinator
import com.softland.callqtv.R
import com.softland.callqtv.ui.display.TokenDisplayBlockingOverlays
import com.softland.callqtv.ui.settings.AppearanceSettingsLauncher
import com.softland.callqtv.ui.settings.SettingsIconButton
import com.softland.callqtv.utils.playTokenChime
import com.softland.callqtv.ui.theme.parseColorOrDefault
import com.softland.callqtv.ui.theme.parseHexColorOrNull
import com.softland.callqtv.utils.*
import com.softland.callqtv.viewmodel.MqttViewModel
import com.softland.callqtv.viewmodel.findCounterEntityForMqttRoute
import com.softland.callqtv.viewmodel.mqttRouteMatchesButtonIndex
import com.softland.callqtv.viewmodel.mqttRouteMatchesKeypadIndex
import com.softland.callqtv.viewmodel.TokenDisplayViewModel
import com.softland.callqtv.data.local.AdFileEntity
import com.softland.callqtv.data.local.AppSharedPreferences
import com.softland.callqtv.data.local.CounterEntity
import com.softland.callqtv.data.local.TvConfigEntity
import com.softland.callqtv.utils.PreferenceHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.launch
import android.graphics.Color as AndroidColor
import android.graphics.SurfaceTexture
import android.view.TextureView
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector.ParametersBuilder
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.common.MimeTypes
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.PermissionRequest
import java.io.File
import java.io.BufferedInputStream
import android.media.MediaPlayer
import android.media.AudioManager
import android.media.ToneGenerator
import android.graphics.Matrix
import android.util.Log
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.resume
import java.net.URLConnection
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.lang.ref.WeakReference
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import com.softland.callqtv.ui.ads.AdMediaType
import com.softland.callqtv.ui.ads.MediaEngine
import com.softland.callqtv.ui.ads.isAdVideo
import com.softland.callqtv.ui.ads.isAdImage
import com.softland.callqtv.ui.ads.resolveAdMediaType
import com.softland.callqtv.ui.ads.buildBaseYoutubeWebView
import com.softland.callqtv.ui.ads.buildSharedAdTextureView
import com.softland.callqtv.ui.ads.bindAdTextureSurfaceOnly
import com.softland.callqtv.ui.ads.isValidYoutubeVideoIdForEmbed
import com.softland.callqtv.ui.ads.releaseYouTubeWebView
import com.softland.callqtv.ui.detachViewFromParent
import com.softland.callqtv.ui.ads.MIN_TRUSTED_YOUTUBE_DURATION_MS

internal object UiPerfProbe {
    private val lastTokenUiUpdateAtMs = AtomicLong(0L)

    fun markTokenUiUpdated(counterKey: String, tokenLabel: String) {
        val now = System.currentTimeMillis()
        lastTokenUiUpdateAtMs.set(now)
        Log.i("UiPerfProbe", "TOKEN_UI_UPDATED counter=$counterKey token=$tokenLabel at=$now")
    }

    fun logAdEvent(event: String, adPath: String) {
        val now = System.currentTimeMillis()
        val lastTokenAt = lastTokenUiUpdateAtMs.get()
        val delta = if (lastTokenAt > 0L) now - lastTokenAt else -1L
        Log.i(
            "UiPerfProbe",
            "AD_EVENT event=$event deltaFromLastTokenMs=$delta at=$now ad=${adPath.take(120)}"
        )
    }
}

private const val MAX_CUSTOM_CHIME_STARTUP_MS = 1200L
private const val MAX_CUSTOM_CHIME_PLAYBACK_MS = 1500L

// Play a short built-in chime or a custom audio URL before announcing a token.

@Composable
fun AdArea(adFiles: List<AdFileEntity>, config: TvConfigEntity, counterBgHex: String) {
    val orderedAds = remember(adFiles) { adFiles.sortedBy { it.position } }
    val adAreaBgBrush = remember(counterBgHex) { ThemeColorManager.getBackgroundBrush(counterBgHex) }
    val context = LocalContext.current
    val allowYoutubeAds = ThemeColorManager.isYouTubeAdsEnabled(context)
    val intervalSeconds = (config.adInterval ?: 5).coerceAtLeast(1)

    // visibleAd stays on-screen until next ad is actually ready.
    var visibleAd by remember(orderedAds) { mutableStateOf(orderedAds.getOrNull(0)) }
    // Strict round-robin cursor for predictable 1->2->3->1 ordering.
    var visibleAdIdx by remember(orderedAds) { mutableIntStateOf(0) }
    // candidateAd is preloaded in background; we switch only after its onReady callback.
    var candidateAd by remember(orderedAds) { mutableStateOf<AdFileEntity?>(null) }
    var candidateAdIdx by remember(orderedAds) { mutableIntStateOf(-1) }
    var candidateLoadToken by remember(orderedAds) { mutableIntStateOf(0) }
    var visibleReplayToken by remember(orderedAds) { mutableIntStateOf(0) }
    val adSwitchScope = rememberCoroutineScope()
    val transitionHoldMs = 180L
    var visibleAdReady by remember(orderedAds) { mutableStateOf(false) }
    // Single ExoPlayer + shared TextureView (alternating two decoders caused frozen frames with audio).
    val exoPlayerSlot = 0
    val mediaTypeCache = remember { mutableStateMapOf<String, AdMediaType>() }
    val sharedYoutubeWebView = remember(context) {
        buildBaseYoutubeWebView(context)
    }
    val sharedAdTextureView = remember(context) {
        buildSharedAdTextureView(context)
    }

    // Off-screen Exo preload uses a second MediaCodec and often hangs on TV SoCs with HQ files.

    DisposableEffect(sharedYoutubeWebView) {
        TokenAnnouncementAdAudio.setYoutubeWebView(sharedYoutubeWebView)
        onDispose {
            TokenAnnouncementAdAudio.setYoutubeWebView(null)
            releaseYouTubeWebView(sharedYoutubeWebView, destroy = true)
        }
    }

    LaunchedEffect(orderedAds) {
        if (orderedAds.isEmpty()) {
            visibleAd = null
            visibleAdIdx = 0
            candidateAd = null
            candidateAdIdx = -1
        } else {
            // Keep current visible ad stable while DB rows refresh (e.g., remote -> local path swap).
            // Restarting the same slot mid-play causes repeated codec/surface churn.
            val current = visibleAd
            if (current != null) {
                val sameAdIdx = orderedAds.indexOfFirst {
                    // Prefer stable identity; falling back to slot-only can pin loop to first ad
                    // when multiple ads share the same position.
                    val idMatch = current.id == it.id && current.id != 0L
                    val pathMatch = current.filePath.equals(it.filePath, ignoreCase = true)
                    val positionMatch = current.position == it.position
                    idMatch || (pathMatch && positionMatch) || pathMatch
                }
                if (sameAdIdx >= 0) {
                    visibleAdIdx = sameAdIdx
                    visibleAd = orderedAds[sameAdIdx]
                } else {
                    visibleAdIdx = visibleAdIdx.coerceIn(0, orderedAds.lastIndex)
                    visibleAd = orderedAds[visibleAdIdx]
                }
            } else {
                visibleAdIdx = visibleAdIdx.coerceIn(0, orderedAds.lastIndex)
                visibleAd = orderedAds[visibleAdIdx]
            }
            candidateAd = null
            candidateAdIdx = -1
        }
    }

    // Resolve media types off-main and cache them so composition remains lightweight.
    LaunchedEffect(orderedAds) {
        orderedAds.forEach { ad ->
            val key = ad.filePath
            if (mediaTypeCache[key] == null) {
                val resolved = withContext(Dispatchers.Default) {
                    com.softland.callqtv.ui.ads.resolveAdMediaType(key, context)
                }
                mediaTypeCache[key] = resolved
            }
        }
    }

    fun fastInferMediaType(path: String): AdMediaType {
        val lc = path.lowercase()
        return when {
            com.softland.callqtv.ui.ads.isValidYoutubeVideoIdForEmbed(path.trim()) ||
                lc.contains("youtube.com") ||
                lc.contains("youtu.be") ||
                lc.contains("youtube-nocookie.com") -> com.softland.callqtv.ui.ads.AdMediaType.YouTube
            com.softland.callqtv.ui.ads.isAdVideo(path) -> com.softland.callqtv.ui.ads.AdMediaType.Video
            com.softland.callqtv.ui.ads.isAdImage(path) -> com.softland.callqtv.ui.ads.AdMediaType.Image
            isServerHostedAdMediaPath(path) && !isLikelyHtmlWebPage(path) -> com.softland.callqtv.ui.ads.AdMediaType.Video
            lc.startsWith("http://") || lc.startsWith("https://") -> com.softland.callqtv.ui.ads.AdMediaType.Web
            else -> com.softland.callqtv.ui.ads.AdMediaType.Image
        }
    }

    fun visibleReadyTimeoutFor(ad: AdFileEntity): Long {
        val type = mediaTypeCache[ad.filePath] ?: fastInferMediaType(ad.filePath)
        return when (type) {
            com.softland.callqtv.ui.ads.AdMediaType.Video, com.softland.callqtv.ui.ads.AdMediaType.YouTube -> 45_000L
            com.softland.callqtv.ui.ads.AdMediaType.Web -> if (isLikelyLiveStreamUrl(ad.filePath)) 90_000L else 30_000L
            else -> 25_000L
        }
    }

    fun applyVisibleAd(ad: AdFileEntity, idx: Int, reason: String) {
        Log.i(
            "AdLoop",
            "applyVisibleAd reason=$reason idx=$idx url=${ad.filePath.take(90)}"
        )
        visibleAd = ad
        visibleAdIdx = idx.coerceAtLeast(0)
        visibleAdReady = false
        visibleReplayToken += 1
        candidateAd = null
        candidateAdIdx = -1
        candidateLoadToken += 1
    }

    /** Immediately advance away from a failed or unsupported visible ad (does not wait for preload). */
    fun skipVisibleAd(failedAd: AdFileEntity?) {
        if (orderedAds.isEmpty()) return
        if (failedAd != null) {
            val stillSame = visibleAd?.id == failedAd.id &&
                visibleAd?.position == failedAd.position
            if (!stillSame) return
        }
        val fromIdx = failedAd?.let { ad ->
            orderedAds.indexOfFirst { it.id == ad.id && it.position == ad.position }
                .takeIf { it >= 0 }
        } ?: visibleAdIdx.coerceIn(0, orderedAds.lastIndex.coerceAtLeast(0))

        if (orderedAds.size == 1) {
            visibleAdReady = false
            candidateAd = null
            candidateAdIdx = -1
            candidateLoadToken += 1
            visibleReplayToken += 1
            Log.w(
                "AdLoop",
                "Single ad unplayable/failed; remounting player token=$visibleReplayToken path=${orderedAds[0].filePath.take(90)}"
            )
            return
        }

        val nextIdx = (fromIdx + 1) % orderedAds.size
        applyVisibleAd(orderedAds[nextIdx], nextIdx, "skip_failed")
    }

    fun promoteCandidateAfterHold(preloadTokenSnapshot: Int, nextAd: AdFileEntity, nextIdx: Int) {
        adSwitchScope.launch {
            delay(transitionHoldMs)
            if (preloadTokenSnapshot != candidateLoadToken) return@launch
            candidateAd = null
            candidateAdIdx = -1
            // Let preload composables dispose before the shared TextureView is reattached.
            delay(48)
            if (preloadTokenSnapshot != candidateLoadToken) return@launch
            applyVisibleAd(nextAd, nextIdx, "preload_ready")
        }
    }

    /** Hands off to the next ad after a short hold (keeps last frame visible during Exo/WebView teardown). */
    fun scheduleExoOrYoutubeHandoff(nextAd: AdFileEntity, nextIdx: Int, reason: String) {
        val token = candidateLoadToken
        adSwitchScope.launch {
            delay(transitionHoldMs)
            if (token != candidateLoadToken) return@launch
            if (candidateAd?.id != nextAd.id || candidateAd?.position != nextAd.position) return@launch
            candidateAd = null
            candidateAdIdx = -1
            delay(48)
            if (token != candidateLoadToken) return@launch
            applyVisibleAd(nextAd, nextIdx, reason)
        }
    }

    fun triggerNext(expectedCurrentAd: AdFileEntity? = null) {
        if (orderedAds.isEmpty()) return
        if (expectedCurrentAd != null) {
            // Ignore stale callbacks (old ad ended after a new ad is already visible).
            val stillSame = visibleAd?.id == expectedCurrentAd.id &&
                visibleAd?.position == expectedCurrentAd.position
            if (!stillSame) return
        }

        val currentIdx = visibleAdIdx.coerceIn(0, orderedAds.lastIndex)
        val nextIdx = (currentIdx + 1) % orderedAds.size
        val next = orderedAds[nextIdx]
        Log.i(
            "AdLoop",
            "triggerNext current[${currentIdx.coerceAtLeast(0)}]=${visibleAd?.filePath?.take(90)} -> next[$nextIdx]=${next.filePath.take(90)}"
        )
        candidateLoadToken += 1
        candidateAd = next
        candidateAdIdx = nextIdx
        val nextType = mediaTypeCache[next.filePath] ?: fastInferMediaType(next.filePath)
        when {
            nextType == com.softland.callqtv.ui.ads.AdMediaType.YouTube -> {
                scheduleExoOrYoutubeHandoff(next, nextIdx, "trigger_next_youtube")
            }
            usesExoTexturePlayback(nextType, next.filePath) -> {
                // Exo preload was disabled on TV SoCs; advance explicitly so the playlist never stalls.
                scheduleExoOrYoutubeHandoff(next, nextIdx, "trigger_next_exo")
            }
            // Image / static Web: off-screen preload composable calls promoteCandidateAfterHold on ready.
        }
    }

    fun triggerNextFrom(ad: AdFileEntity, expectedCurrentAd: AdFileEntity? = null) {
        if (orderedAds.isEmpty()) return
        if (expectedCurrentAd != null) {
            val stillSame = visibleAd?.id == expectedCurrentAd.id &&
                visibleAd?.position == expectedCurrentAd.position
            if (!stillSame) return
        }
        val fromIdx = orderedAds.indexOfFirst { it.id == ad.id && it.position == ad.position }
            .takeIf { it >= 0 } ?: visibleAdIdx.coerceIn(0, orderedAds.lastIndex)
        val nextIdx = (fromIdx + 1) % orderedAds.size
        val next = orderedAds[nextIdx]
        candidateLoadToken += 1
        candidateAd = next
        candidateAdIdx = nextIdx
        val nextType = mediaTypeCache[next.filePath] ?: fastInferMediaType(next.filePath)
        when {
            nextType == com.softland.callqtv.ui.ads.AdMediaType.YouTube -> {
                scheduleExoOrYoutubeHandoff(next, nextIdx, "trigger_next_from_youtube")
            }
            usesExoTexturePlayback(nextType, next.filePath) -> {
                scheduleExoOrYoutubeHandoff(next, nextIdx, "trigger_next_from_exo")
            }
        }
    }

    // Ensure non-ended media types always advance by configured interval so the full ad list
    // loops continuously (videos/YouTube already advance via ended/error callbacks).
    // For remote URLs with ambiguous extensions, wait for resolved media type before applying
    // interval-based switching so video ads are not prematurely cut as "web".
    LaunchedEffect(visibleAd?.id, visibleAd?.position, orderedAds.size, intervalSeconds) {
        val current = visibleAd ?: return@LaunchedEffect
        if (orderedAds.isEmpty()) return@LaunchedEffect
        val cachedType = mediaTypeCache[current.filePath]
        val currentType = cachedType ?: fastInferMediaType(current.filePath)
        val hasAmbiguousRemotePath = cachedType == null &&
            current.filePath.startsWith("http", ignoreCase = true) &&
            !com.softland.callqtv.ui.ads.isAdVideo(current.filePath) &&
            !com.softland.callqtv.ui.ads.isAdImage(current.filePath) &&
            !current.filePath.contains("youtube.com", ignoreCase = true) &&
            !current.filePath.contains("youtu.be", ignoreCase = true) &&
            !com.softland.callqtv.ui.ads.isValidYoutubeVideoIdForEmbed(current.filePath.trim())
        if (hasAmbiguousRemotePath) return@LaunchedEffect
        if (currentType == com.softland.callqtv.ui.ads.AdMediaType.Video || currentType == com.softland.callqtv.ui.ads.AdMediaType.YouTube) return@LaunchedEffect

        delay(intervalSeconds * 1000L)
        triggerNext(expectedCurrentAd = current)
    }

    // Watchdog: if a visible ad never becomes ready, skip to the next item.
    LaunchedEffect(visibleAd?.id, visibleAd?.position) {
        visibleAdReady = false
    }
    LaunchedEffect(visibleAd?.id, visibleAd?.position, visibleAdReady, visibleReplayToken) {
        val current = visibleAd ?: return@LaunchedEffect
        if (visibleAdReady) return@LaunchedEffect
        val timeoutMs = visibleReadyTimeoutFor(current)
        delay(timeoutMs)
        val stillSame = visibleAd?.id == current.id && visibleAd?.position == current.position
        if (stillSame && !visibleAdReady) {
            Log.w(
                "AdLoop",
                "Visible ad not ready within ${timeoutMs / 1000}s; skipping ${current.filePath.take(120)}"
            )
            skipVisibleAd(current)
        }
    }

    val density = LocalDensity.current
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .clipToBounds()
            .focusProperties { canFocus = false }
            .background(adAreaBgBrush)
            .padding(4.dp),
    ) {
        val viewportPx = remember(maxWidth, maxHeight, density) {
            AdViewportSizing.fromConstraints(
                with(density) { maxWidth.roundToPx() },
                with(density) { maxHeight.roundToPx() },
                context,
            )
        }
        LaunchedEffect(viewportPx.width, viewportPx.height) {
            AdViewportSizing.lastViewport = viewportPx
            delay(120)
            com.softland.callqtv.ui.ads.MediaEngine.updateViewport(context, viewportPx.width, viewportPx.height)
        }
        LaunchedEffect(orderedAds, viewportPx) {
            if (!viewportPx.isValid()) return@LaunchedEffect
            val loader = context.imageLoader
            orderedAds.forEach { ad ->
                val type = mediaTypeCache[ad.filePath] ?: fastInferMediaType(ad.filePath)
                if (type == com.softland.callqtv.ui.ads.AdMediaType.Image) {
                    loader.enqueue(
                        AdViewportSizing.coilImageRequestBuilder(context, ad.filePath, viewportPx).build()
                    )
                }
            }
        }
        LaunchedEffect(candidateAd?.id, candidateAd?.filePath, viewportPx) {
            val ad = candidateAd ?: return@LaunchedEffect
            if (!viewportPx.isValid()) return@LaunchedEffect
            val type = mediaTypeCache[ad.filePath] ?: fastInferMediaType(ad.filePath)
            if (type == com.softland.callqtv.ui.ads.AdMediaType.Image) {
                context.imageLoader.enqueue(
                    AdViewportSizing.coilImageRequestBuilder(context, ad.filePath, viewportPx).build()
                )
            }
        }

        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
        if (orderedAds.isEmpty()) {
            Text("No Ads", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            val visibleAdSnapshot = visibleAd
            val preloadAd = candidateAd
            val visibleType = visibleAdSnapshot?.let { ad ->
                mediaTypeCache[ad.filePath] ?: fastInferMediaType(ad.filePath)
            }
            val preloadType = preloadAd?.let { ad ->
                mediaTypeCache[ad.filePath] ?: fastInferMediaType(ad.filePath)
            }
            val showVideoSurface =
                (visibleAdSnapshot != null &&
                    usesExoTexturePlayback(visibleType!!, visibleAdSnapshot.filePath)) ||
                (preloadAd != null && preloadType != null &&
                    usesExoTexturePlayback(preloadType, preloadAd.filePath))

            Box(Modifier.fillMaxSize()) {
                var sharedSurfaceGeneration by remember { mutableIntStateOf(0) }
                if (showVideoSurface) {
                    AdTextureSurfaceHost(
                        textureView = sharedAdTextureView,
                        onSurfaceReady = { sharedSurfaceGeneration += 1 },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                if (visibleAdSnapshot != null && visibleType != null) {
                    key(visibleAdSnapshot.id, visibleAdSnapshot.position, visibleReplayToken) {
                        Log.i(
                            "AdClassifier",
                            "VISIBLE type=$visibleType url=${visibleAdSnapshot.filePath.take(140)}"
                        )
                        AdUnifiedPlayer(
                            ad = visibleAdSnapshot,
                            mediaType = visibleType,
                            allowYoutubeAds = allowYoutubeAds,
                            viewport = viewportPx,
                            sharedYoutubeWebView = sharedYoutubeWebView,
                            sharedVideoTextureView = if (usesExoTexturePlayback(visibleType, visibleAdSnapshot.filePath)) {
                                sharedAdTextureView
                            } else {
                                null
                            },
                            activePlayerIdx = exoPlayerSlot,
                            videoAttachToTexture = true,
                            videoSurfaceReadySignal = sharedSurfaceGeneration,
                            onReady = {
                                visibleAdReady = true
                                UiPerfProbe.logAdEvent("READY", visibleAdSnapshot.filePath)
                            },
                            onEnded = {
                                UiPerfProbe.logAdEvent("ENDED", visibleAdSnapshot.filePath)
                                triggerNext(expectedCurrentAd = visibleAdSnapshot)
                                if (orderedAds.size <= 1) {
                                    visibleReplayToken += 1
                                }
                            },
                            onError = {
                                UiPerfProbe.logAdEvent("ERROR", visibleAdSnapshot.filePath)
                                skipVisibleAd(visibleAdSnapshot)
                            }
                        )
                    }
                }

                // Preload next item off-screen; video uses headless ExoPlayer (no second TextureView).
                if (preloadAd != null && preloadType != null &&
                    (visibleAd == null ||
                        preloadAd.filePath != visibleAd?.filePath ||
                        preloadAd.id != visibleAd?.id)
                ) {
                    val preloadTokenSnapshot = candidateLoadToken
                    val preloadUsesExo = usesExoTexturePlayback(preloadType, preloadAd.filePath)
                    if (preloadType == com.softland.callqtv.ui.ads.AdMediaType.YouTube) {
                        // Shared YouTube WebView cannot dual-buffer; triggerNext handles handoff.
                    } else if (preloadUsesExo) {
                        // Skip second Exo decoder preload — prevents hangs and grainy reconfigure on MTK TVs.
                        Log.d("AdLoop", "Skipping off-screen Exo preload for ${preloadAd.filePath.take(90)}")
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .alpha(0.01f)
                                .clipToBounds()
                        ) {
                            Log.i(
                                "AdClassifier",
                                "PRELOAD type=$preloadType url=${preloadAd.filePath.take(140)}"
                            )
                            AdUnifiedPlayer(
                                ad = preloadAd,
                                mediaType = preloadType,
                                allowYoutubeAds = allowYoutubeAds,
                                viewport = viewportPx,
                                sharedYoutubeWebView = sharedYoutubeWebView,
                                sharedVideoTextureView = null,
                                activePlayerIdx = exoPlayerSlot,
                                videoAttachToTexture = true,
                                onReady = {
                                    if (preloadTokenSnapshot == candidateLoadToken) {
                                        UiPerfProbe.logAdEvent("PRELOAD_READY", preloadAd.filePath)
                                        promoteCandidateAfterHold(
                                            preloadTokenSnapshot = preloadTokenSnapshot,
                                            nextAd = preloadAd,
                                            nextIdx = candidateAdIdx
                                        )
                                    }
                                },
                                onEnded = { },
                                onError = {
                                    if (preloadTokenSnapshot == candidateLoadToken) {
                                        UiPerfProbe.logAdEvent("PRELOAD_ERROR", preloadAd.filePath)
                                        triggerNextFrom(preloadAd, expectedCurrentAd = visibleAd)
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
        }
    }
}

@Composable
private fun AdTextureSurfaceHost(
    textureView: TextureView,
    onSurfaceReady: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AndroidView(
        factory = {
            detachViewFromParent(textureView)
            bindAdTextureSurfaceOnly(
                texture = textureView,
                onSurfaceReady = onSurfaceReady,
            )
            textureView
        },
        update = { view ->
            detachViewFromParent(view)
            bindAdTextureSurfaceOnly(texture = view, onSurfaceReady = onSurfaceReady)
        },
        modifier = modifier.focusProperties { canFocus = false }
    )
}

private fun usesExoTexturePlayback(type: AdMediaType, path: String): Boolean {
    return type == com.softland.callqtv.ui.ads.AdMediaType.Video ||
        (type == com.softland.callqtv.ui.ads.AdMediaType.Web && isLikelyLiveStreamUrl(path))
}

// YouTube components replaced by direct WebView implementation per user request to use exact links.

