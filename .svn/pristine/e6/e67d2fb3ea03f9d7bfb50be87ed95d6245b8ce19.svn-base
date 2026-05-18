package com.softland.callqtv.utils

import android.content.Context
import android.os.Environment
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Utility to log errors and API responses to date-organized folders on device storage.
 *
 * Structure:
 *   Download/CALLQTV_CONFIG/
 *       2026-03-24/
 *           errors_2026-03-24.txt
 *           api_responses_2026-03-24.txt
 *       2026-03-23/
 *           ...
 */
object FileLogger {
    private const val TAG = "FileLogger"
    private const val BASE_FOLDER = "CALLQTV_CONFIG"

    /** Today's date string, e.g. "2026-03-24" */
    private fun todayDateStr(): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

    /**
     * Returns the base CALLQTV_CONFIG directory, using the public Download folder
     * with an app-specific fallback.
     */
    private fun getBaseDirectory(context: Context): File {
        val root = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val baseDir = File(root, BASE_FOLDER)
        if (!baseDir.exists()) {
            try { baseDir.mkdirs() } catch (e: Exception) {
                Log.e(TAG, "Failed to create base directory", e)
            }
        }
        return if (baseDir.exists() && baseDir.canWrite()) {
            baseDir
        } else {
            // Fallback to app-specific external or internal filesDir
            val ext = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                ?: context.getExternalFilesDir(null)
                ?: context.filesDir
            val fallback = File(ext, BASE_FOLDER)
            if (!fallback.exists()) fallback.mkdirs()
            fallback
        }
    }

    /**
     * Returns (and creates) the date-keyed subfolder inside CALLQTV_CONFIG.
     * e.g. CALLQTV_CONFIG/2026-03-24/
     */
    fun getLogDirectory(context: Context, date: String = todayDateStr()): File {
        val dateDir = File(getBaseDirectory(context), date)
        if (!dateDir.exists()) dateDir.mkdirs()
        return dateDir
    }

    /** Full absolute path of today's error log file. */
    fun getLogFilePath(context: Context): String =
        File(getLogDirectory(context), "errors_${todayDateStr()}.txt").absolutePath

    /**
     * Opens an append output stream to today's error log file.
     */
    private fun getLogOutputStream(context: Context): OutputStream? {
        return try {
            val dir = getLogDirectory(context)
            FileOutputStream(File(dir, "errors_${todayDateStr()}.txt"), true)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open log stream", e)
            try {
                FileOutputStream(File(context.filesDir, "errors_${todayDateStr()}.txt"), true)
            } catch (e2: Exception) {
                Log.e(TAG, "Fallback log stream also failed", e2)
                null
            }
        }
    }

    /** Logs a crash/exception. */
    fun logCrash(context: Context, exception: Throwable) {
        logError(context, "CRASH", exception.message ?: "Unknown error", exception)
    }

    /** Logs an error message and optional throwable to today's error file. */
    fun logError(context: Context, tag: String, message: String, exception: Throwable? = null) {
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        val logEntry = if (exception != null) {
            "[$timestamp] [$tag] $message\n${Log.getStackTraceString(exception)}\n"
        } else {
            "[$timestamp] [$tag] $message\n"
        }

        // Trigger cleanup of old date folders (older than 7 days)
        performCleanup(context)

        try {
            val logDir = getLogDirectory(context)
            val logFile = File(logDir, "errors_${todayDateStr()}.txt")
            Log.d(TAG, "Logging error to: ${logFile.absolutePath}")
            logFile.appendText(logEntry)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write to log file", e)
        }
    }

    /** Logs an API response with timestamp and API name to today's api_responses file. */
    fun logResponse(context: Context, apiName: String, response: String) {
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        val logEntry = "[$timestamp] [$apiName] $response\n"

        try {
            val logDir = getLogDirectory(context)
            val logFile = File(logDir, "api_responses_${todayDateStr()}.txt")
            logFile.appendText(logEntry)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write API response to log file", e)
        }
    }

    /**
     * Deletes date subdirectories inside CALLQTV_CONFIG that are older than 7 days.
     */
    private fun performCleanup(context: Context) {
        try {
            val baseDir = getBaseDirectory(context)
            val sevenDaysAgo = System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000L)
            val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

            baseDir.listFiles { file -> file.isDirectory }?.forEach { dateDir ->
                try {
                    val parsed = dateFormat.parse(dateDir.name)
                    if (parsed != null && parsed.time < sevenDaysAgo) {
                        dateDir.deleteRecursively()
                        Log.d(TAG, "Deleted old date folder: ${dateDir.name}")
                    }
                } catch (_: Exception) {
                    // Not a date folder — skip
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Cleanup failed", e)
        }
    }
}
