package com.softland.callqtv.utils

import android.content.Context
import android.os.Build
import android.os.Environment
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Utility to log errors and API responses to date-organized folders on device storage.
 *
 * Android 10+: primary location is shared Downloads/CALLQTV_CONFIG/(date)/ via MediaStore
 * so logs are visible in the Files app and can be copied to USB / SD / another device.
 * If that fails, logs fall back to app-specific external files.
 * Older APIs: public Download folder when writable, otherwise app-specific.
 */
object FileLogger {
    private const val TAG = "FileLogger"

    /** Today's date string, e.g. "2026-03-24" */
    private fun todayDateStr(): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

    /** App-specific base (always allowed under scoped storage). */
    private fun getAppScopedBaseDirectory(context: Context): File {
        val ext = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
            ?: context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: context.getExternalFilesDir(null)
            ?: context.filesDir
        val baseDir = File(ext, PublicCallqtvConfigStorage.BASE_FOLDER)
        if (!baseDir.exists()) {
            try {
                baseDir.mkdirs()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to create app-scoped base directory", e)
            }
        }
        return baseDir
    }

    /**
     * Returns the base CALLQTV_CONFIG directory for **File**-based access (backups, diagnostics).
     * API 29+: app-specific mirror; user-visible logs use [PublicCallqtvConfigStorage].
     * Older APIs: public Download when writable, otherwise app-specific.
     */
    private fun getBaseDirectory(context: Context): File {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return getAppScopedBaseDirectory(context)
        }

        val root = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val baseDir = File(root, PublicCallqtvConfigStorage.BASE_FOLDER)
        if (!baseDir.exists()) {
            try {
                baseDir.mkdirs()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to create base directory", e)
            }
        }
        return if (baseDir.exists() && baseDir.canWrite()) {
            baseDir
        } else {
            getAppScopedBaseDirectory(context)
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

    /** User-facing location hint for today's error log (shared Downloads path on API 29+). */
    fun getLogFilePath(context: Context): String {
        val date = todayDateStr()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            "${Environment.DIRECTORY_DOWNLOADS}/${PublicCallqtvConfigStorage.BASE_FOLDER}/$date/errors_$date.txt"
        } else {
            File(getLogDirectory(context), "errors_$date.txt").absolutePath
        }
    }

    private data class AppendResult(val success: Boolean, val pathForLog: String?)

    /**
     * Writes [entry] to [datedFileName] under today's date folder.
     * API 29+: shared Downloads via MediaStore first, then app-specific files.
     * Older APIs: public Download then app-specific.
     */
    private fun appendToDailyLogFile(context: Context, datedFileName: String, entry: String): AppendResult {
        val date = todayDateStr()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (PublicCallqtvConfigStorage.appendUtf8Text(context, date, datedFileName, entry)) {
                val hint =
                    "${Environment.DIRECTORY_DOWNLOADS}/${PublicCallqtvConfigStorage.BASE_FOLDER}/$date/$datedFileName"
                return AppendResult(true, hint)
            }
        }

        val roots = LinkedHashSet<File>()
        roots.add(getBaseDirectory(context))
        roots.add(getAppScopedBaseDirectory(context))
        var lastError: Exception? = null
        for (base in roots) {
            try {
                val dateDir = File(base, date)
                if (!dateDir.exists()) dateDir.mkdirs()
                val logFile = File(dateDir, datedFileName)
                logFile.appendText(entry)
                return AppendResult(true, logFile.absolutePath)
            } catch (e: Exception) {
                lastError = e
            }
        }
        Log.e(TAG, "Failed to write log file $datedFileName", lastError)
        return AppendResult(false, null)
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

        val written = appendToDailyLogFile(context, "errors_${todayDateStr()}.txt", logEntry)
        if (written.success) {
            Log.d(TAG, "Logging error to: ${written.pathForLog}")
        }
    }

    /** Logs an API response with timestamp and API name to today's api_responses file. */
    fun logResponse(context: Context, apiName: String, response: String) {
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        val logEntry = "[$timestamp] [$apiName] $response\n"
        appendToDailyLogFile(context, "api_responses_${todayDateStr()}.txt", logEntry)
    }

    /**
     * Deletes date subdirectories inside CALLQTV_CONFIG that are older than 7 days.
     */
    private fun performCleanup(context: Context) {
        try {
            val sevenDaysAgo = System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000L)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                PublicCallqtvConfigStorage.deleteDateFoldersOlderThan(context, sevenDaysAgo)
            }
            val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val bases = LinkedHashSet<File>().apply {
                add(getBaseDirectory(context))
                add(getAppScopedBaseDirectory(context))
            }
            for (baseDir in bases) {
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
            }
        } catch (e: Exception) {
            Log.e(TAG, "Cleanup failed", e)
        }
    }
}
