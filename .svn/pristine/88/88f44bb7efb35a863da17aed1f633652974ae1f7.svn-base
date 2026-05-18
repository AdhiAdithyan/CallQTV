package com.softland.callqtv.utils

import android.content.Context
import android.os.Environment
import android.util.Log
import com.softland.callqtv.data.local.AdFileEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import okhttp3.Response
import java.net.URLDecoder
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.charset.StandardCharsets

object AdDownloader {
    private const val TAG = "AdDownloader"
    private const val AD_FOLDER = "CALLQ_ADV"

    /**
     * Synchronizes local ad files with provided entities.
     * 1. Deletes all existing files in CALLQ_ADV (if exists).
     * 2. If offline enabled: Downloads new files and returns local paths.
     * 3. If offline disabled: Deletes folder and returns original paths.
     */
    suspend fun syncAds(context: Context, ads: List<AdFileEntity>): List<AdFileEntity> = withContext(Dispatchers.IO) {
        val isOfflineEnabled = PreferenceHelper.isOfflineAdsEnabled(context)
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val adDir = File(downloadsDir, AD_FOLDER)

        if (!isOfflineEnabled) {
            // Online mode: cleanup folder if it exists and return remote ads
            if (adDir.exists()) {
                adDir.deleteRecursively()
                Log.d(TAG, "Offline ads disabled: Deleted CALLQ_ADV folder")
            }
            return@withContext ads
        }

        // Offline mode: ensure directory exists and sync
        if (!adDir.exists()) adDir.mkdirs()

        if (!NetworkUtil.isNetworkAvailable(context)) {
            Log.w(TAG, "Skipping ad sync download: network unavailable")
            return@withContext ads
        }

        // 1. Delete ALL old files as requested
        adDir.listFiles()?.forEach { 
            Log.d(TAG, "Deleting old ad file: ${it.name}")
            it.delete() 
        }

        val client = UnsafeOkHttpClient.getUnsafeOkHttpClient()

        // 2. Download and map to local paths
        ads.map { ad ->
            // Skip YouTube ads for local download
            val isYouTube = ad.filePath.contains("youtube.com") || ad.filePath.contains("youtu.be")
            if (isYouTube) return@map ad

            try {
                if (!NetworkUtil.isNetworkAvailable(context)) {
                    Log.w(TAG, "Skipping ad download (offline): ${ad.filePath}")
                    return@map ad
                }
                Log.d(TAG, "Downloading ad: ${ad.filePath}")
                val request = Request.Builder().url(ad.filePath).build()
                var localPath: String? = null
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw IOException("Download failed: $response")
                    val body = response.body ?: throw IOException("Empty body")
                    val fileName = getFileName(ad.filePath, response)
                    val localFile = File(adDir, fileName)
                    FileOutputStream(localFile).use { output ->
                        body.byteStream().copyTo(output)
                    }
                    localPath = localFile.absolutePath
                }
                // Return entity with local absolute path
                ad.copy(filePath = localPath ?: ad.filePath)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to download ad: ${ad.filePath}", e)
                FileLogger.logError(context, TAG, "Failed to download ad: ${ad.filePath}", e)
                // Fallback to original URL if download fails
                ad
            }
        }
    }

    private fun getFileName(url: String, response: Response): String {
        // 1) Try Content-Disposition filename first
        val contentDisposition = response.header("Content-Disposition").orEmpty()
        val fromHeader = extractFileNameFromContentDisposition(contentDisposition)
        if (!fromHeader.isNullOrBlank()) {
            return sanitizeFileName(fromHeader)
        }

        // 2) Fall back to URL file name
        val fromUrl = getFileNameFromUrl(url)
        val hasExt = fromUrl.substringAfterLast('.', "").isNotBlank()
        if (hasExt) {
            return sanitizeFileName(fromUrl)
        }

        // 3) If URL had no extension, derive from Content-Type
        val ext = extensionFromContentType(response.header("Content-Type"))
        return sanitizeFileName(
            if (ext != null) "${fromUrl}.$ext" else "${fromUrl}.bin"
        )
    }

    private fun getFileNameFromUrl(url: String): String {
        return try {
            val baseName = URLDecoder.decode(url.substringAfterLast("/"), StandardCharsets.UTF_8.name())
            val cleanName = baseName.substringBefore("?").ifBlank { "${url.hashCode()}_ad_file" }
            if (cleanName.isBlank() || cleanName.length > 100) {
                "${url.hashCode()}_ad_file"
            } else {
                cleanName
            }
        } catch (e: Exception) {
            "${url.hashCode()}_ad_file"
        }
    }

    private fun extractFileNameFromContentDisposition(contentDisposition: String): String? {
        if (contentDisposition.isBlank()) return null
        // Supports: filename="x.mp4" and filename*=UTF-8''x.mp4
        val utf8Part = contentDisposition.substringAfter("filename*=", "")
        if (utf8Part.isNotBlank()) {
            return utf8Part.substringAfter("''", utf8Part).trim().trim('"')
        }
        val basic = contentDisposition.substringAfter("filename=", "")
        return basic.takeIf { it.isNotBlank() }?.trim()?.trim('"')
    }

    private fun extensionFromContentType(contentTypeHeader: String?): String? {
        val contentType = contentTypeHeader?.substringBefore(";")?.trim()?.lowercase() ?: return null
        return when (contentType) {
            "video/mp4" -> "mp4"
            "video/webm" -> "webm"
            "video/quicktime" -> "mov"
            "video/x-msvideo" -> "avi"
            "video/mpeg" -> "mpeg"
            "video/3gpp" -> "3gp"
            "image/jpeg" -> "jpg"
            "image/png" -> "png"
            "image/gif" -> "gif"
            "image/webp" -> "webp"
            "image/bmp" -> "bmp"
            "image/svg+xml" -> "svg"
            else -> null
        }
    }

    private fun sanitizeFileName(fileName: String): String {
        return fileName.replace(Regex("[^A-Za-z0-9._-]"), "_").take(120)
    }
}
