package com.softland.callqtv.utils

import android.content.Context
import android.os.Build
import android.os.Environment
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.io.PrintWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Utility to log errors to a physical file on the device storage for remote debugging.
 * File is stored in the Download folder: Download/CallQTV_errors.txt
 */
object FileLogger {
    private const val TAG = "FileLogger"
    private const val LOG_FILE_NAME = "CallQTV_errors.txt"

    /**
     * Returns the log file path for display.
     * Avoids deprecated getExternalStorageDirectory() on Android 10+ (can cause issues on Android 15).
     */
    fun getLogFilePath(context: Context): String {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val downloads = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                    ?: context.getExternalFilesDir(null)
                    ?: context.filesDir
                File(downloads, LOG_FILE_NAME).absolutePath
            } else {
                @Suppress("DEPRECATION")
                val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                File(dir, LOG_FILE_NAME).absolutePath
            }
        } catch (e: Exception) {
            Log.e(TAG, "getLogFilePath failed", e)
            File(context.filesDir, LOG_FILE_NAME).absolutePath
        }
    }

    /**
     * Opens an output stream for appending to the log file.
     * Uses app-specific storage on Android 10+ to avoid deprecated APIs and Android 15 issues.
     */
    private fun getLogOutputStream(context: Context): OutputStream? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Prefer app-specific external dir (no permissions needed, stable on Android 15)
                val appDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                    ?: context.getExternalFilesDir(null)
                    ?: context.filesDir
                val file = File(appDir, LOG_FILE_NAME)
                file.parentFile?.mkdirs()
                FileOutputStream(file, true)
            } else {
                @Suppress("DEPRECATION")
                val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                if (!dir.exists()) dir.mkdirs()
                FileOutputStream(File(dir, LOG_FILE_NAME), true)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open log stream, using filesDir fallback", e)
            try {
                val fallbackDir = context.filesDir
                FileOutputStream(File(fallbackDir, LOG_FILE_NAME), true)
            } catch (e2: Exception) {
                Log.e(TAG, "Fallback log stream also failed", e2)
                null
            }
        }
    }

    /**
     * Logs a crash/exception to Download/CallQTV_errors.txt.
     */
    fun logCrash(context: Context, exception: Throwable) {
        logError(context, "CRASH", exception.message ?: "Unknown error", exception)
    }

    /**
     * Logs an error message and optional throwable to Download/CallQTV_errors.txt.
     */
    fun logError(context: Context, tag: String, message: String, throwable: Throwable? = null) {
        try {
            getLogOutputStream(context)?.use { fos ->
                val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
                val logEntry = "[$timestamp] [$tag] $message\n"
                fos.write(logEntry.toByteArray())
                throwable?.let {
                    val writer = PrintWriter(fos)
                    it.printStackTrace(writer)
                    writer.flush()
                    // Don't close the writer here as it will close the underlying OutputStream 'fos'
                }
                fos.write("\n-----------------------------------\n".toByteArray())
                fos.flush()
            }
            Log.d(TAG, "Log written to: ${getLogFilePath(context)}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write to log file", e)
        }
    }
}
