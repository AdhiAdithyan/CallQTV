package com.softland.callqtv.utils

import android.content.Context
import android.os.Environment
import android.util.Log
import com.softland.callqtv.data.local.AdFileEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

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

            val fileName = getFileNameFromUrl(ad.filePath)
            val localFile = File(adDir, fileName)

            try {
                Log.d(TAG, "Downloading ad to $localFile: ${ad.filePath}")
                val request = Request.Builder().url(ad.filePath).build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw IOException("Download failed: $response")
                    val body = response.body ?: throw IOException("Empty body")
                    FileOutputStream(localFile).use { output ->
                        body.byteStream().copyTo(output)
                    }
                }
                // Return entity with local absolute path
                ad.copy(filePath = localFile.absolutePath)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to download ad: ${ad.filePath}", e)
                FileLogger.logError(context, TAG, "Failed to download ad: ${ad.filePath}", e)
                // Fallback to original URL if download fails
                ad
            }
        }
    }

    private fun getFileNameFromUrl(url: String): String {
        return try {
            val baseName = url.substringAfterLast("/")
            val cleanName = baseName.substringBefore("?")
            if (cleanName.isBlank() || cleanName.length > 100) {
                "${url.hashCode()}_ad_file"
            } else {
                cleanName
            }
        } catch (e: Exception) {
            "${url.hashCode()}_ad_file"
        }
    }
}
