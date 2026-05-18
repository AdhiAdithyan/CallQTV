@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])

package com.softland.callqtv.ui

import android.content.Context
import android.util.Log
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebViewClient
import android.webkit.WebView
import android.view.TextureView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.softland.callqtv.data.local.AdFileEntity
import com.softland.callqtv.utils.FileLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

internal fun detachViewFromParent(view: android.view.View) {
    (view.parent as? android.view.ViewGroup)?.removeView(view)
}

internal fun isWmvAdPath(path: String): Boolean {
    val lc = path.lowercase()
    return lc.contains(".wmv") || lc.contains("x-ms-wmv") || lc.endsWith("/wmv")
}

@Composable
fun AdUnifiedPlayer(
    ad: AdFileEntity,
    mediaType: AdMediaType,
    allowYoutubeAds: Boolean,
    viewport: AdViewportPx = AdViewportSizing.lastViewport,
    sharedYoutubeWebView: WebView,
    sharedVideoTextureView: TextureView? = null,
    activePlayerIdx: Int,
    /** When false, ExoPlayer buffers the next clip off-screen (no second TextureView). */
    videoAttachToTexture: Boolean = true,
    videoSurfaceReadySignal: Int = 0,
    onReady: () -> Unit = {},
    onEnded: () -> Unit = {},
    onError: () -> Unit = {}
) {
    val context = LocalContext.current
    LaunchedEffect(ad.filePath, mediaType) {
        Log.i("AdClassifier", "ROUTE type=$mediaType url=${ad.filePath.take(140)}")
    }
    when (mediaType) {
        AdMediaType.YouTube -> {
            if (allowYoutubeAds) {
                LaunchedEffect(ad.filePath) {
                    Log.i("YouTubeAdFlow", "Loading YouTube ad as-is: ${ad.filePath}")
                    withContext(Dispatchers.IO) {
                        FileLogger.logError(
                            context,
                            "YouTubeAdFlow",
                            "Loading YouTube ad as-is: ${ad.filePath}"
                        )
                    }
                }
                YouTubeAdPlayer(
                    url = ad.filePath,
                    sharedWebView = sharedYoutubeWebView,
                    onVideoEnded = onEnded,
                    onReady = onReady,
                    onError = onError
                )
            } else {
                Log.i("YouTubeAdFlow", "Skipping YouTube ad: disabled by setting allow_youtube_ads=false")
                FileLogger.logError(context, "YouTubeAdFlow", "Skipping YouTube ad: disabled by setting allow_youtube_ads=false")
                LaunchedEffect(ad.filePath) {
                    delay(300)
                    onError()
                }
                AdFormatUnavailableHint(
                    message = "YouTube ads are disabled in settings"
                )
            }
        }
        AdMediaType.Video -> {
            AdVideoWithWebFallback(
                path = ad.filePath,
                preferWebFirst = isWmvAdPath(ad.filePath),
                viewport = viewport,
                sharedVideoTextureView = sharedVideoTextureView,
                activePlayerIdx = activePlayerIdx,
                attachToTexture = videoAttachToTexture,
                videoSurfaceReadySignal = videoSurfaceReadySignal,
                onReady = onReady,
                onEnded = onEnded,
                onError = onError
            )
        }
        AdMediaType.Image -> {
            var coilFailed by remember(ad.filePath) { mutableStateOf(false) }
            val canWebFallback = ad.filePath.startsWith("http://", ignoreCase = true) ||
                ad.filePath.startsWith("https://", ignoreCase = true)
            if (coilFailed && canWebFallback) {
                WebLinkAdPlayer(url = ad.filePath, onReady = onReady, onError = onError)
            } else {
                AsyncImage(
                    model = AdViewportSizing.coilImageRequestBuilder(context, ad.filePath, viewport)
                        .build(),
                    contentDescription = "Ad",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                    onSuccess = { onReady() },
                    onError = {
                        Log.w("AdPlayer", "Image load failed, trying WebView: ${ad.filePath}")
                        if (canWebFallback) coilFailed = true else onError()
                    }
                )
            }
        }
        AdMediaType.Web -> {
            if (isLikelyLiveStreamUrl(ad.filePath)) {
                AdVideoWithWebFallback(
                    path = ad.filePath,
                    preferWebFirst = false,
                    viewport = viewport,
                    sharedVideoTextureView = sharedVideoTextureView,
                    activePlayerIdx = activePlayerIdx,
                    attachToTexture = videoAttachToTexture,
                    videoSurfaceReadySignal = videoSurfaceReadySignal,
                    onReady = onReady,
                    onEnded = onEnded,
                    onError = onError
                )
            } else {
                WebLinkAdPlayer(
                    url = ad.filePath,
                    onReady = onReady,
                    onError = onError
                )
            }
        }
    }
}

/** Tries ExoPlayer first; on failure falls back to WebView so WMV/odd URLs still show something. */
@Composable
private fun AdVideoWithWebFallback(
    path: String,
    preferWebFirst: Boolean,
    viewport: AdViewportPx,
    sharedVideoTextureView: TextureView?,
    activePlayerIdx: Int,
    attachToTexture: Boolean,
    videoSurfaceReadySignal: Int,
    onReady: () -> Unit,
    onEnded: () -> Unit,
    onError: () -> Unit
) {
    val context = LocalContext.current
    var useWeb by remember(path) { mutableStateOf(preferWebFirst) }
    if (useWeb) {
        WebLinkAdPlayer(
            url = path,
            onReady = onReady,
            onError = onError
        )
    } else {
        AdVideoPlayer(
            videoUrl = path,
            player = MediaEngine.get(context, activePlayerIdx),
            viewport = viewport,
            sharedTextureView = sharedVideoTextureView,
            attachToTexture = attachToTexture,
            videoSurfaceReadySignal = videoSurfaceReadySignal,
            onVideoEnded = onEnded,
            onReady = onReady,
            onError = {
                Log.w("AdPlayer", "ExoPlayer failed; WebView fallback for: $path")
                useWeb = true
            }
        )
    }
}

@Composable
private fun AdFormatUnavailableHint(message: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = message,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

internal fun isLikelyLiveStreamUrl(url: String): Boolean {
    val u = url.lowercase()
    return u.contains(".m3u8") ||
        u.contains(".mpd") ||
        u.contains("/hls") ||
        u.contains("manifest") ||
        u.contains("playlist") ||
        u.contains("format=m3u8") ||
        u.contains("type=live") ||
        u.contains("stream")
}

@Composable
private fun WebLinkAdPlayer(
    url: String,
    onReady: () -> Unit,
    onError: () -> Unit
) {
    val context = LocalContext.current
    val readySent = remember(url) { AtomicBoolean(false) }
    val errorSent = remember(url) { AtomicBoolean(false) }
    val reloadedOnce = remember(url) { AtomicBoolean(false) }
    val isLowBandwidth = remember(url) { isAdLowBandwidthNetwork(context) }
    val webView = remember(url) {
        WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            settings.cacheMode = android.webkit.WebSettings.LOAD_DEFAULT
            isFocusable = false
            isFocusableInTouchMode = false
            setBackgroundColor(android.graphics.Color.WHITE)
            webChromeClient = object : WebChromeClient() {}
            webViewClient = object : WebViewClient() {
                override fun onPageCommitVisible(view: WebView?, pageUrl: String?) {
                    if (readySent.compareAndSet(false, true)) onReady()
                }

                override fun onPageFinished(view: WebView?, pageUrl: String?) {
                    if (readySent.compareAndSet(false, true)) onReady()
                }

                override fun onReceivedError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    error: WebResourceError?
                ) {
                    if (request != null && !request.isForMainFrame) return
                    if (!reloadedOnce.get() && view != null) {
                        reloadedOnce.set(true)
                        view.postDelayed({ view.reload() }, 1200)
                        return
                    }
                    if (errorSent.compareAndSet(false, true)) onError()
                }
            }
        }
    }

    LaunchedEffect(url) {
        val timeoutMs = if (isLowBandwidth) 70_000L else 45_000L
        delay(timeoutMs)
        if (!readySent.get() && !errorSent.get()) {
            errorSent.set(true)
            onError()
        }
    }

    DisposableEffect(webView) {
        onDispose {
            try {
                webView.stopLoading()
                webView.loadUrl("about:blank")
                (webView.parent as? android.view.ViewGroup)?.removeView(webView)
                webView.destroy()
            } catch (_: Exception) {
            }
        }
    }

    AndroidView(
        factory = {
            detachViewFromParent(webView)
            webView
        },
        update = { view ->
            if (view.url != url) view.loadUrl(url)
        },
        modifier = Modifier.fillMaxSize()
    )
}
