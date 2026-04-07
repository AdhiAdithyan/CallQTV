package com.softland.callqtv.ui

import android.content.Context
import android.util.Log
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebViewClient
import android.webkit.WebView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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

@Composable
fun AdUnifiedPlayer(
    ad: AdFileEntity,
    mediaType: AdMediaType,
    allowYoutubeAds: Boolean,
    sharedYoutubeWebView: WebView,
    activePlayerIdx: Int,
    isWmvUnsupportedVideo: (String) -> Boolean,
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
                    // Avoid synchronous disk I/O on the main thread (reduces frame skips).
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
                // Embedded YouTube WebView playback can cause global UI jank on TV devices.
                // Keep disabled by default unless explicitly enabled from settings.
                Log.i("YouTubeAdFlow", "Skipping YouTube ad: disabled by setting allow_youtube_ads=false")
                FileLogger.logError(context, "YouTubeAdFlow", "Skipping YouTube ad: disabled by setting allow_youtube_ads=false")
                LaunchedEffect(ad.filePath) {
                    delay(300)
                    onError()
                }
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Skipping YouTube ad (disabled in settings)",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        AdMediaType.Video -> {
            if (isWmvUnsupportedVideo(ad.filePath)) {
                LaunchedEffect(ad.filePath) {
                    Log.w("AdPlayer", "Skipping unsupported WMV ad: ${ad.filePath}")
                    FileLogger.logError(context, "AdPlayer", "Skipping unsupported WMV ad: ${ad.filePath}")
                    onError()
                }
                Box(modifier = Modifier.fillMaxSize())
            } else {
                AdVideoPlayer(
                    videoUrl = ad.filePath,
                    player = MediaEngine.get(context, activePlayerIdx),
                    onVideoEnded = onEnded,
                    onReady = onReady,
                    onError = onError
                )
            }
        }
        AdMediaType.Image -> {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(ad.filePath)
                    .crossfade(false)
                    .build(),
                contentDescription = "Ad",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
                onSuccess = { onReady() },
                onError = { onError() }
            )
        }
        AdMediaType.Web -> {
            if (isLikelyLiveStreamUrl(ad.filePath)) {
                // Route stream-like links through ExoPlayer for reliable HLS/DASH playback.
                AdVideoPlayer(
                    videoUrl = ad.filePath,
                    player = MediaEngine.get(context, activePlayerIdx),
                    onVideoEnded = onEnded,
                    onReady = onReady,
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

private fun isLikelyLiveStreamUrl(url: String): Boolean {
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
    val readySent = androidx.compose.runtime.remember(url) { AtomicBoolean(false) }
    val errorSent = androidx.compose.runtime.remember(url) { AtomicBoolean(false) }
    val reloadedOnce = androidx.compose.runtime.remember(url) { AtomicBoolean(false) }
    val isLowBandwidth = androidx.compose.runtime.remember(url) { isAdLowBandwidthNetwork(context) }

    LaunchedEffect(url) {
        val timeoutMs = if (isLowBandwidth) 70_000L else 45_000L
        delay(timeoutMs)
        if (!readySent.get() && !errorSent.get()) {
            errorSent.set(true)
            onError()
        }
    }

    AndroidView(
        factory = {
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.mediaPlaybackRequiresUserGesture = false
                settings.cacheMode = android.webkit.WebSettings.LOAD_DEFAULT
                isFocusable = false
                isFocusableInTouchMode = false
                setBackgroundColor(android.graphics.Color.BLACK)
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
                loadUrl(url)
            }
        },
        update = { view ->
            if (view.url != url) view.loadUrl(url)
        },
        modifier = Modifier.fillMaxSize()
    )
}
