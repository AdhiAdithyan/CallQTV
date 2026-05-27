package com.softland.callqtv.ui.ads

import android.content.Context
import android.net.Uri
import android.util.Log
import com.softland.callqtv.ui.isLikelyHtmlWebPage
import com.softland.callqtv.ui.isLikelyLiveStreamUrl
import com.softland.callqtv.ui.isServerHostedAdMediaPath
import java.io.BufferedInputStream
import java.io.File
import com.softland.callqtv.utils.ThemeColorManager
import java.net.URLConnection

enum class AdMediaType { YouTube, Video, Image, Web }

internal fun getYouTubeAdMaxPlaybackMs(context: Context): Long {
    val safeSeconds = ThemeColorManager.getYouTubeAdMaxSeconds(
        context,
        YOUTUBE_AD_MAX_OPTIONS_SECONDS,
        DEFAULT_YOUTUBE_AD_MAX_SECONDS,
    )
    return safeSeconds * 1000L
}
internal fun isAdVideo(path: String): Boolean {
    val lowerFull = path.lowercase()
    val lc = lowerFull.substringBefore("?").substringBefore("#")
    return lc.endsWith(".mp4") || lc.endsWith(".mkv") || lc.endsWith(".mov") ||
           lc.endsWith(".3gp") || lc.endsWith(".webm") || lc.endsWith(".avi") ||
           lc.endsWith(".flv") || lc.endsWith(".ts") || lc.endsWith(".m4v") ||
           lc.endsWith(".mpg") || lc.endsWith(".mpeg") || lc.endsWith(".m2ts") ||
           lc.endsWith(".m3u8") || lc.endsWith(".mpd") || lc.endsWith(".ism") ||
           lc.endsWith(".isml") || lowerFull.contains(".m3u8") || lowerFull.contains(".mpd")
}

internal fun isAdImage(path: String): Boolean {
    val lc = path.lowercase().substringBefore("?").substringBefore("#")
    return lc.endsWith(".jpg") || lc.endsWith(".jpeg") || lc.endsWith(".png") ||
        lc.endsWith(".gif") || lc.endsWith(".webp") || lc.endsWith(".bmp") ||
        lc.endsWith(".svg")
}

internal const val DEFAULT_YOUTUBE_AD_MAX_SECONDS = 120
internal const val MIN_TRUSTED_YOUTUBE_DURATION_MS = 5_000L
internal val YOUTUBE_AD_MAX_OPTIONS_SECONDS = listOf(30, 60, 90, 120)

internal fun resolveAdMediaType(path: String, context: Context): AdMediaType {
    val lc = path.lowercase()
    val res = when {
        isValidYoutubeVideoIdForEmbed(path.trim()) -> AdMediaType.YouTube
        lc.contains("youtube.com") || lc.contains("youtu.be") || lc.contains("youtube-nocookie.com") -> AdMediaType.YouTube
        isAdVideo(path) -> AdMediaType.Video
        isAdImage(path) -> AdMediaType.Image
        isServerHostedAdMediaPath(path) && !isLikelyHtmlWebPage(path) -> AdMediaType.Video
        lc.startsWith("http://") || lc.startsWith("https://") -> AdMediaType.Web
        else -> {
            val mime = detectMimeType(path, context)
            when {
                mime?.startsWith("video/") == true -> AdMediaType.Video
                mime?.startsWith("image/") == true -> AdMediaType.Image
                lc.startsWith("http://") || lc.startsWith("https://") -> AdMediaType.Web
                else -> AdMediaType.Image
            }
        }
    }
    // Log for debugging (only to logcat, not FileLogger yet to avoid spam)
    Log.d("AdMediaType", "Resolved '$path' to $res")
    return res
}

internal fun detectMimeType(path: String, context: Context): String? {
    return try {
        val cleaned = path.substringBefore("?").substringBefore("#")

        if (cleaned.startsWith("content://")) {
            return context.contentResolver.getType(Uri.parse(cleaned))
        }

        val filePath = when {
            cleaned.startsWith("file://") -> Uri.parse(cleaned).path
            else -> cleaned
        }

        // 1) Guess from name first
        URLConnection.guessContentTypeFromName(cleaned)?.let { return it.lowercase() }

        // 2) If local file exists, sniff first bytes
        val localFile = filePath?.let { File(it) }
        if (localFile != null && localFile.exists() && localFile.isFile) {
            BufferedInputStream(localFile.inputStream()).use { input ->
                URLConnection.guessContentTypeFromStream(input)?.let { return it.lowercase() }
            }
        }
        null
    } catch (_: Exception) {
        null
    }
}

internal fun isValidYoutubeVideoIdForEmbed(id: String): Boolean =
    id.length == 11 && id.all { it.isLetterOrDigit() || it == '_' || it == '-' }
