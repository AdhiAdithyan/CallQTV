package com.softland.callqtv.ui

import android.content.Context
import android.util.Log
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
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.softland.callqtv.data.local.AdFileEntity
import com.softland.callqtv.utils.FileLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

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
    }
}
