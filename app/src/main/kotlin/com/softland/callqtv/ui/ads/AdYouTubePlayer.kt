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
import com.softland.callqtv.ui.AdViewportSizing
import com.softland.callqtv.ui.isLikelyLiveStreamUrl
import com.softland.callqtv.ui.ads.AdMediaType
import com.softland.callqtv.ui.ads.MediaEngine
import com.softland.callqtv.ui.ads.MIN_TRUSTED_YOUTUBE_DURATION_MS
import com.softland.callqtv.ui.ads.getYouTubeAdMaxPlaybackMs
import com.softland.callqtv.ui.ads.isValidYoutubeVideoIdForEmbed
import com.softland.callqtv.utils.ThemeColorManager
import com.softland.callqtv.utils.TokenAnnouncer
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import com.softland.callqtv.utils.FileLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.lang.ref.WeakReference
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
@Composable
fun YouTubeAdPlayer(
    url: String,
    sharedWebView: WebView? = null,
    onVideoEnded: () -> Unit,
    onReady: () -> Unit = {},
    onError: () -> Unit = {}
) {
    val context = LocalContext.current
    val latestOnVideoEnded by rememberUpdatedState(onVideoEnded)
    val latestOnReady by rememberUpdatedState(onReady)
    val latestOnError by rememberUpdatedState(onError)

    val originalUrl = remember(url) { url.trim() }
    // Prefer original URL first. Some videos block embedded playback (Error 152/153)
    // but still work as regular watch/shorts pages inside WebView.
    // Normalize short/share links (e.g., youtu.be/...) to watch URLs for better TV WebView compatibility.
    val initialPlaybackUrl = remember(originalUrl) { normalizeYouTubePlaybackUrl(originalUrl) }
    val expectedVideoId = remember(originalUrl) { extractYoutubeIdForAnyUrl(originalUrl) }
    val expectedWatchUrl = remember(expectedVideoId) {
        expectedVideoId?.let { "https://www.youtube.com/watch?v=$it" }
    }
    var activeUrl by remember(initialPlaybackUrl) { mutableStateOf(initialPlaybackUrl) }
    var currentLoadToken by remember { mutableIntStateOf(0) }

    var readyReported by remember(activeUrl) { mutableStateOf(false) }
    var terminalEventReported by remember(activeUrl) { mutableStateOf(false) }
    var loadStartedAtMs by remember(activeUrl) { mutableStateOf(0L) }
    var reportedDurationMs by remember(activeUrl) { mutableStateOf<Long?>(null) }
    var dnsFallbackTried by remember(activeUrl) { mutableStateOf(false) }
    var embedFallbackTried by remember(activeUrl) { mutableStateOf(false) }
    var embedRestrictionFallbackTried by remember(activeUrl) { mutableStateOf(false) }
    var iframeFallbackTried by remember(originalUrl) { mutableStateOf(false) }
    var mismatchedVideoRetried by remember(activeUrl) { mutableStateOf(false) }
    val hardMaxPlaybackMs = remember(activeUrl) {
        com.softland.callqtv.ui.ads.getYouTubeAdMaxPlaybackMs(context)
    }
    val playUntilEnded = remember(activeUrl) {
        ThemeColorManager.isYouTubePlayUntilEnded(context, defaultIfUnset = true)
    }
    val isLowBandwidth = remember(activeUrl) { isAdLowBandwidthNetwork(context) }

    // Safety timeout: if ad doesn't report ready/ended within 45s, skip it.
    val mainHandler = remember { Handler(Looper.getMainLooper()) }

    LaunchedEffect(activeUrl) {
        loadStartedAtMs = System.currentTimeMillis()
        Log.i("YouTubeAdPerf", "Start load for url=${activeUrl.take(120)}")
    }
    LaunchedEffect(initialPlaybackUrl) {
        if (!initialPlaybackUrl.equals(originalUrl, ignoreCase = true)) {
            Log.i(
                "YouTubeAdPerf",
                "Normalized YouTube URL from=${originalUrl.take(120)} to=${initialPlaybackUrl.take(120)}"
            )
        }
    }

    // Early fallback: embed URLs can be flaky too, so do not force watch/shorts pages
    // into embed mode. Keep watch playback path stable on TVs where embeds are restricted.
    LaunchedEffect(activeUrl, readyReported, terminalEventReported) {
        if (readyReported || terminalEventReported || embedFallbackTried) return@LaunchedEffect
        val looksLikeEmbed = activeUrl.contains("/embed/", ignoreCase = true)
        if (!looksLikeEmbed) return@LaunchedEffect
        delay(12000)
        if (readyReported || terminalEventReported || embedFallbackTried) return@LaunchedEffect
        val embedUrl = nextYouTubeEmbedFallbackUrl(activeUrl)
        if (!embedUrl.isNullOrBlank() && !embedUrl.equals(activeUrl, ignoreCase = true)) {
            embedFallbackTried = true
            Log.w(
                "YouTubeAdPerf",
                "Early fallback to embed URL after slow ready: ${embedUrl.take(120)}"
            )
            activeUrl = embedUrl
            readyReported = false
            terminalEventReported = false
            reportedDurationMs = null
            loadStartedAtMs = System.currentTimeMillis()
        }
    }

    // If YouTube page never becomes visible, skip.
    // Use a more tolerant timeout for low-network scenarios.
    LaunchedEffect(activeUrl, readyReported, terminalEventReported) {
        if (readyReported || terminalEventReported) return@LaunchedEffect
        val readyTimeoutMs = if (isLowBandwidth) 90_000L else 70_000L
        delay(readyTimeoutMs)
        // Extra grace window to avoid boundary races where ready lands
        // right around timeout expiry.
        if (!readyReported && !terminalEventReported) {
            delay(1500)
        }
        if (!readyReported && !terminalEventReported) {
            Log.w("YouTubeAdPlayer", "Ready timeout (${readyTimeoutMs / 1000}s) for YouTube ad: $activeUrl")
            withContext(Dispatchers.IO) {
                FileLogger.logError(
                    context,
                    "YouTubeAdPlayer",
                    "Ready timeout (${readyTimeoutMs / 1000}s) for: $activeUrl"
                )
            }
            Log.w(
                "YouTubeAdPerf",
                "Ready timeout after ${System.currentTimeMillis() - loadStartedAtMs} ms for url=${activeUrl.take(120)}"
            )
            terminalEventReported = true
            latestOnError()
        }
    }

    // Duration-based safety fallback:
    // For direct YouTube pages, JS-reported duration can be unreliable (often 10s previews),
    // so use only the configured hard cap to avoid premature ad switching.
    LaunchedEffect(activeUrl, readyReported, terminalEventReported, reportedDurationMs) {
        if (!readyReported || terminalEventReported) return@LaunchedEffect
        val fallbackDelayMs = if (playUntilEnded) {
            // Prefer natural "ended" event; keep a long hard-stop to avoid infinite hangs.
            hardMaxPlaybackMs.coerceAtLeast(10 * 60 * 1000L)
        } else {
            hardMaxPlaybackMs
        }
        delay(fallbackDelayMs)
        if (!terminalEventReported) {
            terminalEventReported = true
            Log.i(
                "YouTubeAdPerf",
                "Duration safety auto-advance after ${fallbackDelayMs} ms for url=${activeUrl.take(120)}"
            )
            latestOnVideoEnded()
        }
    }

    val webView = sharedWebView ?: remember(context) { buildBaseYoutubeWebView(context) }

    DisposableEffect(webView, sharedWebView) {
        onDispose {
            if (sharedWebView == null) {
                releaseYouTubeWebView(webView, destroy = true)
            } else {
                // Keep shared WebView alive between ad transitions to avoid recreate churn
                // and avoid transient black frames from forcing about:blank.
                try {
                    webView.onPause()
                    webView.pauseTimers()
                    webView.stopLoading()
                } catch (_: Exception) {
                }
                // Shared WebView may be reattached by a new AndroidView host.
                // Detach from any old parent to prevent "specified child already has a parent".
                (webView.parent as? android.view.ViewGroup)?.removeView(webView)
            }
        }
    }

    AndroidView(
        factory = {
            // Defensive detach for shared instance reuse across Compose hosts.
            (webView.parent as? android.view.ViewGroup)?.removeView(webView)
            webView
        },
        update = { view ->
            // Shared WebView can be paused during previous ad disposal.
            // Resume before loading next YouTube ad; otherwise page JS/timers may never run.
            try {
                view.onResume()
                view.resumeTimers()
            } catch (_: Exception) {
            }
            try {
                view.removeJavascriptInterface("CallQTVBridge")
            } catch (_: Exception) {
            }
            val jsBridge = object {
                @JavascriptInterface
                fun onAdEnded() {
                    mainHandler.post {
                        if (!terminalEventReported) {
                            terminalEventReported = true
                            latestOnVideoEnded()
                        }
                    }
                }

                @JavascriptInterface
                fun onAdReady() {
                    mainHandler.post {
                        if (!readyReported) {
                            readyReported = true
                            latestOnReady()
                        }
                    }
                }

                @JavascriptInterface
                fun onAdDuration(seconds: Double) {
                    if (!seconds.isFinite() || seconds <= 0.0) return
                    mainHandler.post {
                        val ms = (seconds * 1000.0).toLong()
                        // Shorts/watch pages can report transient preview duration (~10s).
                        // Ignore very short values so they do not influence ad timing decisions.
                        if (ms < MIN_TRUSTED_YOUTUBE_DURATION_MS) {
                            Log.i(
                                "YouTubeAdPerf",
                                "Ignoring short YouTube duration=${ms}ms for url=${activeUrl.take(120)}"
                            )
                            return@post
                        }
                        if (reportedDurationMs == null || kotlin.math.abs((reportedDurationMs ?: 0L) - ms) > 1000L) {
                            reportedDurationMs = ms
                            Log.i("YouTubeAdPerf", "Detected YouTube duration=${ms}ms for url=${activeUrl.take(120)}")
                        }
                    }
                }
            }
            view.addJavascriptInterface(jsBridge, "CallQTVBridge")

            fun handleYouTubeRestrictionToken(webView: WebView, token: String) {
                val normalized = token.lowercase()
                val hasEmbedConfigError = normalized == "152" || normalized == "153" || normalized == "config"
                if (!hasEmbedConfigError) return
                val watchFallbackUrl = nextYouTubeWatchFallbackUrl(activeUrl)
                if (!embedRestrictionFallbackTried &&
                    !watchFallbackUrl.isNullOrBlank() &&
                    !watchFallbackUrl.equals(activeUrl, ignoreCase = true)
                ) {
                    embedRestrictionFallbackTried = true
                    Log.w(
                        "YouTubeAdPerf",
                        "Embed restriction detected; retrying with watch URL: ${watchFallbackUrl.take(120)}"
                    )
                    activeUrl = watchFallbackUrl
                    readyReported = false
                    terminalEventReported = false
                    reportedDurationMs = null
                    loadStartedAtMs = System.currentTimeMillis()
                    webView.tag = null
                    loadYouTubeUrlWithHeaders(webView, watchFallbackUrl)
                    return
                }
                // Avoid repeated iframe/loadData retries on restricted videos.
                // On IMG GPUs this can trigger heavy surface churn and RTS exhaustion.
                if (!iframeFallbackTried && activeUrl.contains("/embed/", ignoreCase = true)) {
                    iframeFallbackTried = true
                    Log.w(
                        "YouTubeAdPerf",
                        "Embed restriction persists on url=${activeUrl.take(120)}; skipping without iframe fallback"
                    )
                }
                if (!terminalEventReported) {
                    terminalEventReported = true
                    latestOnError()
                }
            }

            view.webViewClient = object : WebViewClient() {
                private val readyPosted = AtomicBoolean(false)
                private var mainFrameFailed = false

                private fun reportReadyOnce(urlHint: String?) {
                    if (!readyPosted.compareAndSet(false, true)) return
                    if (mainFrameFailed) return
                    val finalUrl = (urlHint ?: activeUrl)
                    val u = finalUrl.lowercase()
                    if (u.startsWith("chrome-error://")) return
                    val loadedVideoId = extractYoutubeIdForAnyUrl(finalUrl)
                    if (!mismatchedVideoRetried &&
                        !expectedVideoId.isNullOrBlank() &&
                        !loadedVideoId.isNullOrBlank() &&
                        !loadedVideoId.equals(expectedVideoId, ignoreCase = true)
                    ) {
                        val canonicalTarget = expectedWatchUrl
                        if (!canonicalTarget.isNullOrBlank()) {
                            mismatchedVideoRetried = true
                            Log.w(
                                "YouTubeAdPerf",
                                "Detected mismatched YouTube video id loaded=$loadedVideoId expected=$expectedVideoId; retrying $canonicalTarget"
                            )
                            activeUrl = canonicalTarget
                            readyReported = false
                            terminalEventReported = false
                            reportedDurationMs = null
                            loadStartedAtMs = System.currentTimeMillis()
                            return
                        }
                    }
                    if (!readyReported) {
                        readyReported = true
                        Log.i(
                            "YouTubeAdPerf",
                            "Ready in ${System.currentTimeMillis() - loadStartedAtMs} ms, url=${(urlHint ?: activeUrl).take(120)}"
                        )
                        latestOnReady()
                    }
                }

                override fun onPageStarted(view: WebView?, urlStr: String?, favicon: android.graphics.Bitmap?) {
                    mainFrameFailed = false
                }

                override fun onPageCommitVisible(view: WebView?, url: String?) {
                    val isAdSoundEnabled = context.getSharedPreferences("ThemePrefs", Context.MODE_PRIVATE)
                        .getBoolean("enable_ad_sound", false)
                    view?.let { applyYouTubeKioskMode(it, isAdSoundEnabled) }
                    reportReadyOnce(url)
                }

                override fun onPageFinished(view: WebView?, urlStr: String?) {
                    val isAdSoundEnabled = context.getSharedPreferences("ThemePrefs", Context.MODE_PRIVATE)
                        .getBoolean("enable_ad_sound", false)
                    view?.let { applyYouTubeKioskMode(it, isAdSoundEnabled) }
                    reportReadyOnce(urlStr)
                    // Detect embed restriction/player config errors (e.g., Error 152/153).
                    // If we are on an embed URL, fallback once to watch/shorts URL before skipping.
                    view?.evaluateJavascript(
                        """
                        (function() {
                          try {
                            var txt = (document.body && document.body.innerText ? document.body.innerText : '').toLowerCase();
                            var has152 = txt.indexOf('error 152') >= 0 || txt.indexOf('error code: 152') >= 0;
                            var has153 = txt.indexOf('error 153') >= 0 || txt.indexOf('error code: 153') >= 0;
                            var hasConfig = txt.indexOf('video player configuration error') >= 0;
                            if (has152) return '152';
                            if (has153) return '153';
                            if (hasConfig) return 'config';
                            return 'none';
                          } catch (e) {
                            return 'none';
                          }
                        })();
                        """.trimIndent()
                    ) { result ->
                        val token = result.trim().trim('"')
                        view?.let { handleYouTubeRestrictionToken(it, token) }
                    }
                    Log.i(
                        "YouTubeAdPerf",
                        "Page finished in ${System.currentTimeMillis() - loadStartedAtMs} ms, url=${(urlStr ?: activeUrl).take(120)}"
                    )
                }

                override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                    val description = com.softland.callqtv.utils.WebViewErrorCompat.description(error)
                    val errorCode = com.softland.callqtv.utils.WebViewErrorCompat.errorCode(error)
                    val requestUrl = request?.url?.toString().orEmpty()
                    // Only treat main-frame failures as terminal for ad switching.
                    // Subresource failures are common and should not interrupt active playback.
                    val shouldHandleAsPrimary = request == null || request.isForMainFrame
                    if (!shouldHandleAsPrimary) return
                    mainFrameFailed = true
                    Log.e("YouTubeAdPlayer", "WebView error ($errorCode) for ${activeUrl.take(60)}...: $description")
                    Log.e(
                        "YouTubeAdPerf",
                        "WebView error ($errorCode) after ${System.currentTimeMillis() - loadStartedAtMs} ms: $description"
                    )
                    
                    val isSslError = errorCode == ERROR_FAILED_SSL_HANDSHAKE || 
                                    errorCode == ERROR_PROXY_AUTHENTICATION ||
                                    description.contains("SSL", ignoreCase = true) ||
                                    description.contains("handshake", ignoreCase = true)
                                    
                    val isConnectionError = errorCode == ERROR_CONNECT ||
                                           errorCode == ERROR_TIMEOUT ||
                                           errorCode == ERROR_HOST_LOOKUP ||
                                           description.contains("ERR_NAME_NOT_RESOLV", ignoreCase = true) ||
                                           description.contains("ERR_FAILED", ignoreCase = true) ||
                                           description.contains("ERR_CONNECTION", ignoreCase = true) ||
                                           description.contains("ERR_SOCKET", ignoreCase = true) ||
                                           description.contains("ERR_TIMED_OUT", ignoreCase = true)

                    // One-shot fallback host retry for transient DNS/SSL path issues.
                    if ((isSslError || isConnectionError) && !dnsFallbackTried) {
                        val fallbackUrl = nextYouTubeDnsFallbackUrl(activeUrl)
                        if (!fallbackUrl.isNullOrBlank() && !fallbackUrl.equals(activeUrl, ignoreCase = true)) {
                            dnsFallbackTried = true
                            Log.w(
                                "YouTubeAdPerf",
                                "Retrying failed YouTube load with fallback URL: ${fallbackUrl.take(120)}"
                            )
                            activeUrl = fallbackUrl
                            readyReported = false
                            terminalEventReported = false
                            reportedDurationMs = null
                            loadStartedAtMs = System.currentTimeMillis()
                            view?.tag = null
                            view?.let { loadYouTubeUrlWithHeaders(it, fallbackUrl) }
                            return
                        }
                    }

                    // If fallback is exhausted/unavailable, skip this ad.
                    if (!terminalEventReported) {
                        terminalEventReported = true
                        latestOnError()
                    }
                }

                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                    if (request == null || !request.isForMainFrame) return false
                    val requestedUrl = request.url?.toString().orEmpty()
                    val requestedId = extractYoutubeIdForAnyUrl(requestedUrl)
                    if (!expectedVideoId.isNullOrBlank() &&
                        !requestedId.isNullOrBlank() &&
                        !requestedId.equals(expectedVideoId, ignoreCase = true)
                    ) {
                        val canonicalTarget = expectedWatchUrl
                        if (!canonicalTarget.isNullOrBlank()) {
                            Log.w(
                                "YouTubeAdPerf",
                                "Blocked navigation to different YouTube id=$requestedId; keeping id=$expectedVideoId"
                            )
                            view?.tag = canonicalTarget
                            loadYouTubeUrlWithHeaders(view ?: return true, canonicalTarget)
                            return true
                        }
                    }
                    return false
                }
            }
            
            view.webChromeClient = object : WebChromeClient() {
                override fun onPermissionRequest(request: PermissionRequest?) {
                    request?.deny()
                }
                override fun onReceivedTitle(view: WebView?, title: String?) {
                    if (title == "AD_ENDED") {
                        Log.i("YouTubeAdPlayer", "Detected JS signal: AD_ENDED; triggering next ad.")
                        if (!terminalEventReported) {
                            terminalEventReported = true
                            latestOnVideoEnded()
                        }
                        return
                    }
                    val t = title.orEmpty().lowercase()
                    val restrictionFromTitle =
                        when {
                            t.contains("error 152") || t.contains("error code: 152") -> "152"
                            t.contains("error 153") || t.contains("error code: 153") -> "153"
                            t.contains("watch video on youtube") || t.contains("video player configuration error") -> "config"
                            else -> null
                        }
                    if (view != null && !restrictionFromTitle.isNullOrBlank()) {
                        Log.w(
                            "YouTubeAdPerf",
                            "Embed restriction inferred from title='$title' for url=${activeUrl.take(120)}"
                        )
                        handleYouTubeRestrictionToken(view, restrictionFromTitle)
                    }
                }
            }

            val preferredLoadUrl = activeUrl

            // Avoid reloading same page on recomposition.
            if (view.tag as? String != preferredLoadUrl) {
                view.tag = preferredLoadUrl
                currentLoadToken += 1
                val tokenForLoad = currentLoadToken
                mainHandler.post {
                    if (view.tag != preferredLoadUrl) return@post
                    if (tokenForLoad != currentLoadToken) return@post
                    loadYouTubeUrlWithHeaders(view, preferredLoadUrl)
                }
            }
        },
        modifier = Modifier
            .fillMaxSize()
            .focusProperties { canFocus = false }
            .clip(androidx.compose.ui.graphics.RectangleShape)
            .clipToBounds(),
    )
}

internal fun buildBaseYoutubeWebView(context: Context): WebView {
    return WebView(context).apply {
        layoutParams = android.view.ViewGroup.LayoutParams(
            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            android.view.ViewGroup.LayoutParams.MATCH_PARENT
        )
        settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            @Suppress("DEPRECATION")
            databaseEnabled = true
            cacheMode = WebSettings.LOAD_DEFAULT
            mediaPlaybackRequiresUserGesture = false
            useWideViewPort = true
            loadWithOverviewMode = true
            // TV-like UA for better compatibility.
            userAgentString = "Mozilla/5.0 (Linux; Android 10; BRAVIA 4K VH2 Build/PG1.200115.002) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/88.0.4324.7 Mobile Safari/537.36"
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        }
        isVerticalScrollBarEnabled = false
        isHorizontalScrollBarEnabled = false
        isFocusable = false
        isFocusableInTouchMode = false
        isClickable = false
        isLongClickable = false
        isHapticFeedbackEnabled = false
        setOnLongClickListener { true }
        // Ads are display-only; consume touch so WebView does not capture gestures/focus.
        setOnTouchListener { _, _ -> true }
        setBackgroundColor(android.graphics.Color.TRANSPARENT)
        
        webChromeClient = object : WebChromeClient() {
            override fun onPermissionRequest(request: PermissionRequest?) {
                // Ads do not require mic/camera recording; deny capture requests to avoid
                // WebView/Chromium recording capability warnings.
                request?.deny()
            }
        }
    }
}

internal fun releaseYouTubeWebView(webView: WebView, destroy: Boolean) {
    try {
        webView.stopLoading()
    } catch (_: Exception) {
    }
    try {
        webView.removeJavascriptInterface("CallQTVBridge")
    } catch (_: Exception) {
    }
    try {
        // Prevent late chromium callbacks from targeting stale handlers.
        webView.webViewClient = WebViewClient()
        webView.webChromeClient = WebChromeClient()
    } catch (_: Exception) {
    }
    try {
        webView.loadUrl("about:blank")
    } catch (_: Exception) {
    }
    try {
        webView.onPause()
        webView.pauseTimers()
    } catch (_: Exception) {
    }
    try {
        (webView.parent as? android.view.ViewGroup)?.removeView(webView)
    } catch (_: Exception) {
    }
    if (!destroy) return
    try {
        // Delay actual destroy slightly so queued chromium callbacks drain first.
        webView.post {
            try {
                webView.destroy()
            } catch (_: Exception) {
            }
        }
    } catch (_: Exception) {
        try {
            webView.destroy()
        } catch (_: Exception) {
        }
    }
}

private fun applyYouTubeKioskMode(webView: WebView, isAdSoundEnabled: Boolean) {
    // Best-effort DOM cleanup for YouTube shorts/watch pages rendered directly in WebView.
    // Keeps video area while hiding surrounding page chrome/action panels.
    val strictAutoplay = ThemeColorManager.isYouTubeStrictAutoplay(webView.context)
    val effectiveMuted = strictAutoplay || !isAdSoundEnabled
    val mutedJs = if (effectiveMuted) "true" else "false"
    val volumeJs = if (effectiveMuted) "0.0" else "1.0"
    val restoreSoundAfterAutoplayJs = if (effectiveMuted) "false" else "true"
    val js = """
        (function() {
          try {
            var styleId = 'callqtv-kiosk-style';
            if (!document.getElementById(styleId)) {
              var s = document.createElement('style');
              s.id = styleId;
              s.textContent = `
                html, body { margin:0 !important; padding:0 !important; overflow:hidden !important; background:#000 !important; }
                body * { -webkit-user-select: none !important; user-select: none !important; }
                #page-manager, #content, #container, #player-container, #player-container-id,
                #player, #movie_player, .html5-video-player, .ytp-player-content, .player-api,
                ytd-app, ytd-watch-flexy, ytd-player, ytd-page-manager,
                ytm-app, ytm-watch, ytm-watch-page, ytm-player, ytm-single-column-watch-next-results-renderer {
                  width: 100vw !important;
                  height: 100vh !important;
                  max-width: 100vw !important;
                  max-height: 100vh !important;
                  margin: 0 !important;
                  padding: 0 !important;
                  position: relative !important;
                  inset: 0 !important;
                  transform: none !important;
                  background: #000 !important;
                  overflow: hidden !important;
                }
                ytm-header-renderer, ytd-masthead, #header, #topbar, #guide-button,
                ytm-sub-header-renderer, #ytm-navigation-bar,
                ytm-mobile-topbar-renderer, ytm-topbar-logo-renderer, ytm-topbar-menu-button-renderer,
                ytm-topbar-search-button-renderer, ytm-open-app-button-renderer, .mobile-topbar-header,
                .ytm-topbar-menu-button, .ytm-topbar-logo, .ytm-header-container,
                #comments, ytd-comments, ytm-comments-entry-point-header-renderer,
                ytd-reel-player-overlay-renderer, ytm-reel-player-overlay-renderer,
                #actions, #menu, #related, #secondary, #below, #info, .metadata-container,
                ytd-watch-next-secondary-results-renderer, ytd-watch-next-results-renderer,
                ytm-pivot-bar-renderer, ytm-item-section-renderer, ytm-browse,
                ytm-watch-metadata, ytm-slim-owner-renderer, ytm-expandable-video-description-body-renderer,
                ytm-watch-next-secondary-results-renderer, ytm-structured-description-content-renderer,
                .ytp-show-cards-title, .ytp-pause-overlay, .ytp-ce-element,
                .ytp-chrome-top, .ytp-chrome-bottom,
                .ytp-title, .ytp-title-channel, .ytp-title-link,
                .ytp-endscreen-content, .ytp-endscreen-element,
                .branding-img-container, .iv-branding,
                .ytp-unmute, .ytp-unmute-button, .ytp-unmute-inner, .ytp-mute-button,
                .ytp-controls, .ytp-progress-bar-container, .ytp-time-display,
                .ytp-button, .ytp-right-controls, .ytp-left-controls,
                .ytp-gradient-top, .ytp-gradient-bottom, .ytp-cued-thumbnail-overlay,
                .html5-endscreen, .ytp-player-content ytp-ce-element {
                  display:none !important;
                }
                video, .video-stream, .html5-main-video {
                  width: 100% !important;
                  height: 100% !important;
                  max-width: 100vw !important;
                  max-height: 100vh !important;
                  object-fit: cover !important;
                  background: #000 !important;
                  position: absolute !important;
                  top: 50% !important;
                  left: 50% !important;
                  transform: translate(-50%, -50%) !important;
                  z-index: 2147483646 !important;
                }
                video::-webkit-media-controls,
                video::-webkit-media-controls-panel,
                video::-webkit-media-controls-play-button,
                video::-webkit-media-controls-start-playback-button,
                video::-webkit-media-controls-timeline,
                video::-webkit-media-controls-current-time-display,
                video::-webkit-media-controls-time-remaining-display,
                video::-webkit-media-controls-mute-button,
                video::-webkit-media-controls-fullscreen-button {
                  display: none !important;
                  -webkit-appearance: none !important;
                }
              `;
              (document.head || document.documentElement).appendChild(s);
            }
            function removeMobileChrome() {
              try {
                var selectors = [
                  'ytm-mobile-topbar-renderer',
                  'ytm-topbar-logo-renderer',
                  'ytm-topbar-menu-button-renderer',
                  'ytm-topbar-search-button-renderer',
                  'ytm-open-app-button-renderer',
                  '.mobile-topbar-header',
                  '.ytm-header-container',
                  '#header',
                  '#topbar'
                ];
                selectors.forEach(function(sel) {
                  document.querySelectorAll(sel).forEach(function(node) {
                    try { node.remove(); } catch (e) {
                      try {
                        node.style.setProperty('display', 'none', 'important');
                        node.style.setProperty('visibility', 'hidden', 'important');
                        node.style.setProperty('opacity', '0', 'important');
                        node.style.setProperty('pointer-events', 'none', 'important');
                      } catch (e2) {}
                    }
                  });
                });
              } catch (e) {}
            }
            removeMobileChrome();
            
            // Auto-play + ended detection for watch/shorts pages without URL params
            var v = document.querySelector('video');
            if (v) {
              try {
                try {
                  v.controls = false;
                  v.removeAttribute('controls');
                  v.setAttribute('playsinline', 'true');
                  v.setAttribute('webkit-playsinline', 'true');
                  v.setAttribute('controlsList', 'nodownload nofullscreen noplaybackrate noremoteplayback');
                  v.disablePictureInPicture = true;
                } catch (e) {}
                // Start muted for autoplay reliability, then optionally restore sound.
                v.muted = true;
                v.volume = 0.0;
                var playPromise = v.play();
                if (playPromise && typeof playPromise.then === 'function') {
                  playPromise.then(function() {
                    if ($restoreSoundAfterAutoplayJs) {
                      try {
                        v.muted = false;
                        v.volume = 1.0;
                      } catch (e) {}
                    } else {
                      v.muted = $mutedJs;
                      v.volume = $volumeJs;
                    }
                  }).catch(function(e){ console.warn('Autoplay blocked:', e); });
                } else {
                  if ($restoreSoundAfterAutoplayJs) {
                    try {
                      v.muted = false;
                      v.volume = 1.0;
                    } catch (e) {}
                  } else {
                    v.muted = $mutedJs;
                    v.volume = $volumeJs;
                  }
                }
                // Best-effort user-gesture simulation for TVs where autoplay policy is stricter.
                try {
                  var clickEv = new MouseEvent('click', { bubbles: true, cancelable: true, view: window });
                  v.dispatchEvent(clickEv);
                } catch (e) {}
                // Also poke known play buttons when present.
                try {
                  var playBtn = document.querySelector('.ytp-large-play-button, .ytp-play-button, button[aria-label*="Play"], button[aria-label*="play"]');
                  if (playBtn) {
                    playBtn.click();
                  }
                } catch (e) {}
                if (isFinite(v.duration) && v.duration > 0) {
                  if (window.CallQTVBridge && window.CallQTVBridge.onAdDuration) {
                    window.CallQTVBridge.onAdDuration(v.duration);
                  }
                } else {
                  v.addEventListener('loadedmetadata', function() {
                    try {
                      if (isFinite(v.duration) && v.duration > 0 &&
                          window.CallQTVBridge && window.CallQTVBridge.onAdDuration) {
                        window.CallQTVBridge.onAdDuration(v.duration);
                      }
                    } catch (e) {}
                  }, { once: true });
                }
                if (window.CallQTVBridge && window.CallQTVBridge.onAdReady) {
                  window.CallQTVBridge.onAdReady();
                }
              } catch (e) {}
              // Set up a one-time ended listener to trigger the next ad
              if (!v._callqtv_hooked) {
                v._callqtv_hooked = true;
                v.addEventListener('ended', function() {
                  console.log('CallQTV: Video ended');
                  if (window.CallQTVBridge && window.CallQTVBridge.onAdEnded) {
                    window.CallQTVBridge.onAdEnded();
                  }
                  document.title = 'AD_ENDED';
                });
              }
              // Keep re-applying autoplay and ended detection because YouTube DOM/video
              // can be recreated during shorts/watch transitions.
              if (!window._callqtv_autoplay_interval) {
                window._callqtv_autoplay_interval = setInterval(function() {
                  try {
                    var vv = document.querySelector('video');
                    if (!vv) return;
                    try {
                      vv.controls = false;
                      vv.removeAttribute('controls');
                      vv.setAttribute('playsinline', 'true');
                      vv.setAttribute('webkit-playsinline', 'true');
                      vv.setAttribute('controlsList', 'nodownload nofullscreen noplaybackrate noremoteplayback');
                      vv.disablePictureInPicture = true;
                    } catch (e) {}
                    if (vv.ended) {
                      if (window.CallQTVBridge && window.CallQTVBridge.onAdEnded) {
                        window.CallQTVBridge.onAdEnded();
                      }
                      document.title = 'AD_ENDED';
                      return;
                    }
                    if (vv.paused) {
                      // Re-attempt autoplay in muted mode first to satisfy policy.
                      vv.muted = true;
                      vv.volume = 0.0;
                      var resumePromise = vv.play();
                      if (resumePromise && typeof resumePromise.then === 'function') {
                        resumePromise.then(function() {
                          if ($restoreSoundAfterAutoplayJs) {
                            try {
                              vv.muted = false;
                              vv.volume = 1.0;
                            } catch (e) {}
                          } else {
                            vv.muted = $mutedJs;
                            vv.volume = $volumeJs;
                          }
                        }).catch(function(){});
                      }
                    }
                    if (isFinite(vv.duration) && vv.duration > 0 &&
                        window.CallQTVBridge && window.CallQTVBridge.onAdDuration) {
                      window.CallQTVBridge.onAdDuration(vv.duration);
                    }
                    // Re-assert current app sound setting in case YouTube DOM mutates.
                    if (!$restoreSoundAfterAutoplayJs) {
                      vv.muted = $mutedJs;
                      vv.volume = $volumeJs;
                    }
                    if (window.CallQTVBridge && window.CallQTVBridge.onAdReady) {
                      window.CallQTVBridge.onAdReady();
                    }
                    removeMobileChrome();
                  } catch (e) {}
                }, 1200);
              }
            }
          } catch (e) {}
        })();
    """.trimIndent()
    try {
        webView.evaluateJavascript(js, null)
    } catch (_: Exception) {
        // Ignore JS injection failures; playback should continue.
    }
}

private fun nextYouTubeDnsFallbackUrl(currentUrl: String): String? {
    return try {
        val uri = Uri.parse(currentUrl)
        val host = (uri.host ?: return null).lowercase()
        val id = extractYoutubeIdForFallback(currentUrl)
        when (host) {
            "youtube.com", "www.youtube.com" -> {
                // Prefer short-link fallback over host swap for /shorts and /watch URLs.
                // Swapping to youtube-nocookie with /shorts path is unreliable on some TVs.
                if (id != null) "https://youtu.be/$id" else uri.buildUpon().authority("m.youtube.com").build().toString()
            }
            "m.youtube.com" -> {
                if (id != null) "https://youtu.be/$id" else uri.buildUpon().authority("www.youtube.com").build().toString()
            }
            "youtu.be" -> {
                if (id != null) "https://www.youtube.com/watch?v=$id" else null
            }
            "www.youtube-nocookie.com", "youtube-nocookie.com" -> {
                // Move back to standard host for page mode.
                if (id != null) "https://www.youtube.com/watch?v=$id" else null
            }
            else -> null
        }
    } catch (_: Exception) {
        null
    }
}

private fun extractYoutubeIdForFallback(url: String): String? {
    val u = url.trim()
    Regex("youtube\\.com/shorts/([a-zA-Z0-9_-]{11})").find(u)?.groupValues?.get(1)?.let { return it }
    Regex("youtu\\.be/([a-zA-Z0-9_-]{11})").find(u)?.groupValues?.get(1)?.let { return it }
    Regex("[?&]v=([a-zA-Z0-9_-]{11})").find(u)?.groupValues?.get(1)?.let { return it }
    return null
}

private fun nextYouTubeEmbedFallbackUrl(currentUrl: String): String? {
    val id = extractYoutubeIdForFallback(currentUrl) ?: return null
    val lower = currentUrl.lowercase()
    if (lower.contains("/embed/$id")) return null
    return "https://www.youtube.com/embed/$id?autoplay=1&mute=1&playsinline=1&controls=0&rel=0&enablejsapi=1&origin=https%3A%2F%2Fwww.youtube.com&widget_referrer=https%3A%2F%2Fwww.youtube.com"
}

private fun nextYouTubeWatchFallbackUrl(currentUrl: String): String? {
    val id = extractYoutubeIdForAnyUrl(currentUrl) ?: return null
    val target = "https://www.youtube.com/watch?v=$id"
    return if (currentUrl.equals(target, ignoreCase = true)) null else target
}

private fun normalizeYouTubePlaybackUrl(url: String): String {
    val trimmed = url.trim()
    return nextYouTubeWatchFallbackUrl(trimmed) ?: trimmed
}

private fun extractYoutubeIdForAnyUrl(url: String): String? {
    val u = url.trim()
    Regex("youtube\\.com/embed/([a-zA-Z0-9_-]{11})").find(u)?.groupValues?.get(1)?.let { return it }
    return extractYoutubeIdForFallback(u)
}

private fun loadYouTubeUrlWithHeaders(webView: WebView, url: String) {
    val headers = mapOf(
        "Referer" to "https://www.youtube.com/",
        "Origin" to "https://www.youtube.com",
        // Clearing this header improves compatibility with some embedded providers.
        "X-Requested-With" to ""
    )
    try {
        webView.loadUrl(url, headers)
    } catch (_: Exception) {
        webView.loadUrl(url)
    }
}

private fun loadYouTubeIframeFallback(webView: WebView, sourceUrl: String): Boolean {
    val id = extractYoutubeIdForAnyUrl(sourceUrl) ?: return false
    val embedUrl =
        "https://www.youtube.com/embed/$id?autoplay=1&mute=1&playsinline=1&controls=0&rel=0&enablejsapi=1&origin=https%3A%2F%2Fwww.youtube.com&widget_referrer=https%3A%2F%2Fwww.youtube.com"
    val html = """
        <!doctype html>
        <html>
        <head>
          <meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover">
          <style>
            html, body { margin: 0; padding: 0; background: #000; width: 100%; height: 100%; overflow: hidden; }
            iframe { border: 0; width: 100%; height: 100%; display: block; }
          </style>
        </head>
        <body>
          <iframe
            src="$embedUrl"
            allow="autoplay; encrypted-media; picture-in-picture"
            referrerpolicy="origin"
            allowfullscreen>
          </iframe>
        </body>
        </html>
    """.trimIndent()
    return try {
        webView.loadDataWithBaseURL("https://www.youtube.com", html, "text/html", "utf-8", null)
        true
    } catch (_: Exception) {
        false
    }
}


internal object WebViewWarmup {
    private val warmed = AtomicBoolean(false)

    suspend fun warmUp(context: Context) {
        if (!warmed.compareAndSet(false, true)) return
        try {
            withContext(Dispatchers.Main) {
                val webView = WebView(context.applicationContext)
                try {
                    webView.settings.javaScriptEnabled = true
                    webView.loadDataWithBaseURL(
                        "https://www.youtube.com",
                        "<html><body style='background:#000'></body></html>",
                        "text/html",
                        "UTF-8",
                        null
                    )
                } finally {
                    webView.stopLoading()
                    webView.destroy()
                }
            }
            Log.i("YouTubeAdPerf", "WebView warm-up completed")
        } catch (e: Exception) {
            Log.w("YouTubeAdPerf", "WebView warm-up failed: ${e.message}")
        }
    }
}
