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
import java.util.concurrent.TimeUnit

object AdDownloader {
    private const val TAG = "AdDownloader"
    private const val AD_FOLDER = "CALLQ_ADV"
    private const val CONVERT_TIMEOUT_MS: Long = 10 * 60 * 1000L // 10 minutes best-effort
    @Volatile
    private var ffmpegAvailableCache: Boolean? = null

    /**
     * Returns true if a `ffmpeg` executable can be found.
     * Cached after the first check to avoid repeated process invocations.
     */
    fun isFfmpegAvailable(): Boolean {
        ffmpegAvailableCache?.let { return it }
        val available = findFfmpegExecutable() != null
        ffmpegAvailableCache = available
        return available
    }

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

        // Downloads can be large (e.g. 4K MP4) and on TV networks can be slow to establish headers.
        // Use longer timeouts specifically for ad downloads to avoid SocketTimeoutException.
        val client = UnsafeOkHttpClient.getUnsafeOkHttpClient()
            .newBuilder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(180, TimeUnit.SECONDS)
            .writeTimeout(180, TimeUnit.SECONDS)
            .callTimeout(240, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()

        // 2. Download and map to local paths
        ads.map { ad ->
            // Skip YouTube ads for local download
            val isYouTube = isYouTubePath(ad.filePath)
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
                    localPath =
                        when {
                            isWmvPath(ad.filePath) && localFile.exists() && localFile.length() > 0L ->
                                // Backend served a WMV: best-effort convert to MP4 for ExoPlayer.
                                tryConvertWmvToMp4(localFile)?.absolutePath ?: localFile.absolutePath

                            isHighResUhdPortraitMp4(ad.filePath) && localFile.exists() && localFile.length() > 0L ->
                                // Backend served a UHD portrait MP4: best-effort downscale to reduce decoder issues.
                                tryConvertUhdMp4ToSupported(localFile)?.absolutePath ?: localFile.absolutePath

                            else -> localFile.absolutePath
                        }
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

    private fun isYouTubePath(path: String): Boolean {
        val lc = path.lowercase()
        val trimmed = path.trim()
        return lc.contains("youtube.com") ||
            lc.contains("youtu.be") ||
            lc.contains("youtube-nocookie.com") ||
            // Raw 11-char YouTube IDs can come directly from API.
            (trimmed.length == 11 && trimmed.all { it.isLetterOrDigit() || it == '_' || it == '-' })
    }

    private fun isWmvPath(path: String): Boolean {
        val lc = path.lowercase()
        return lc.contains("x-ms-wmv") || lc.endsWith(".wmv") || lc.contains(".wmv?")
    }

    /**
     * Heuristic: your problematic stream is named like:
     * - `...uhd_2160_3840_25fps.mp4`
     * We only downscale when we recognize this naming pattern to avoid unnecessary re-encoding.
     */
    private fun isHighResUhdPortraitMp4(path: String): Boolean {
        val lc = path.lowercase()
        return lc.contains("uhd_2160_3840") ||
            lc.contains("uhd_2160x3840") ||
            (lc.contains("2160x3840") || lc.contains("2160_3840")) && lc.endsWith(".mp4")
    }

    /**
     * Converts WMV into MP4 using a system `ffmpeg` binary (if present).
     * Returns converted output file on success, or null on failure.
     */
    private fun tryConvertWmvToMp4(input: File): File? {
        val ffmpeg = findFfmpegExecutable() ?: run {
            Log.w(TAG, "ffmpeg not found; skipping WMV->MP4 conversion for ${input.name}")
            return null
        }

        val baseName = input.nameWithoutExtension
        val output = File(input.parentFile, "${baseName}_converted.mp4")
        if (output.exists()) output.delete()

        // Basic, fast-ish conversion preset.
        val cmd = listOf(
            ffmpeg,
            "-y",
            "-i",
            input.absolutePath,
            "-c:v",
            "libx264",
            "-preset",
            "veryfast",
            "-crf",
            "23",
            "-c:a",
            "aac",
            "-movflags",
            "+faststart",
            output.absolutePath
        )

        return try {
            Log.i(TAG, "Converting WMV->MP4: ${input.name} -> ${output.name}")
            val pb = ProcessBuilder(cmd)
            pb.redirectErrorStream(true)
            val proc = pb.start()

            // Drain some output to prevent the process from blocking due to full buffers.
            try {
                proc.inputStream.bufferedReader().use { reader ->
                    val buf = CharArray(1024)
                    var total = 0
                    while (total < 16 * 1024) {
                        val n = reader.read(buf)
                        if (n <= 0) break
                        total += n
                    }
                }
            } catch (_: Exception) {
            }

            val ok = proc.waitFor(CONVERT_TIMEOUT_MS, TimeUnit.MILLISECONDS) && proc.exitValue() == 0
            if (ok && output.exists() && output.length() > 0L) output else null
        } catch (e: Exception) {
            Log.e(TAG, "WMV->MP4 conversion failed for ${input.absolutePath}", e)
            null
        }
    }

    /**
     * Converts a UHD portrait MP4 to a decoder-friendly MP4 by downscaling to 1080p.
     * Output is encoded with H.264 (libx264), AAC audio, and faststart for better streaming.
     */
    private fun tryConvertUhdMp4ToSupported(input: File): File? {
        val ffmpeg = findFfmpegExecutable() ?: run {
            Log.w(TAG, "ffmpeg not found; skipping UHD MP4 downscale for ${input.name}")
            return null
        }

        val baseName = input.nameWithoutExtension
        val output = File(input.parentFile, "${baseName}_1080p_supported.mp4")
        if (output.exists()) output.delete()

        // Cap height to 1080 while preserving aspect ratio and cap fps to 30.
        val cmd = listOf(
            ffmpeg,
            "-y",
            "-i",
            input.absolutePath,
            "-vf",
            "scale=-2:1080,fps=30",
            "-c:v",
            "libx264",
            "-preset",
            "veryfast",
            "-crf",
            "23",
            "-c:a",
            "aac",
            "-movflags",
            "+faststart",
            output.absolutePath
        )

        return try {
            Log.i(TAG, "Converting UHD->Supported: ${input.name} -> ${output.name}")
            val pb = ProcessBuilder(cmd)
            pb.redirectErrorStream(true)
            val proc = pb.start()

            // Drain some output to avoid full buffer deadlock.
            try {
                proc.inputStream.bufferedReader().use { reader ->
                    val buf = CharArray(1024)
                    var total = 0
                    while (total < 32 * 1024) {
                        val n = reader.read(buf)
                        if (n <= 0) break
                        total += n
                    }
                }
            } catch (_: Exception) {
            }

            val ok = proc.waitFor(CONVERT_TIMEOUT_MS, TimeUnit.MILLISECONDS) && proc.exitValue() == 0
            if (ok && output.exists() && output.length() > 0L) output else null
        } catch (e: Exception) {
            Log.e(TAG, "UHD MP4 downscale failed for ${input.absolutePath}", e)
            null
        }
    }

    private fun findFfmpegExecutable(): String? {
        val candidates = listOf(
            "ffmpeg",
            "/system/bin/ffmpeg",
            "/vendor/bin/ffmpeg",
            "/data/local/tmp/ffmpeg"
        )

        for (c in candidates) {
            try {
                val pb = ProcessBuilder(listOf(c, "-version"))
                pb.redirectErrorStream(true)
                val proc = pb.start()
                val finished = proc.waitFor(5, TimeUnit.SECONDS)
                if (finished && proc.exitValue() == 0) return c
            } catch (_: Exception) {
            }
        }
        return null
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
