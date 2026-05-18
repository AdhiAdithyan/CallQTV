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
    private const val BASE_FOLDER = "CallQTV"

    /**
     * Generates a date-wise filename. e.g. errors_2026-03-19.log
     */
    private fun getLogFileName(): String {
        val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        return "errors_$dateStr.txt"
    }

    /**
     * Returns the log file path for display.
     */
    fun getLogFilePath(context: Context): String {
        return try {
            val baseDir = getLogDirectory(context)
            File(baseDir, getLogFileName()).absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "getLogFilePath failed", e)
            File(context.filesDir, getLogFileName()).absolutePath
        }
    }

    private fun getLogDirectory(context: Context): File {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // App-specific external library dir (stable on Android 15)
            val downloads = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                ?: context.getExternalFilesDir(null)
                ?: context.filesDir
            File(downloads, BASE_FOLDER)
        } else {
            @Suppress("DEPRECATION")
            val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            File(dir, BASE_FOLDER)
        }
    }

    /**
     * Opens an output stream for appending to the log file.
     */
    private fun getLogOutputStream(context: Context): OutputStream? {
        return try {
            val logDir = getLogDirectory(context)
            if (!logDir.exists()) logDir.mkdirs()
            val file = File(logDir, getLogFileName())
            FileOutputStream(file, true)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open log stream, using filesDir fallback", e)
            try {
                val fallbackFile = File(context.filesDir, getLogFileName())
                FileOutputStream(fallbackFile, true)
            } catch (e2: Exception) {
                Log.e(TAG, "Fallback log stream also failed", e2)
                null
            }
        }
    }

    /**
     * Logs a crash/exception to date-wise log file.
     */
    fun logCrash(context: Context, exception: Throwable) {
        logError(context, "CRASH", exception.message ?: "Unknown error", exception)
    }

    /**
     * Logs an error message and optional throwable.
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
                }
                fos.write("\n-----------------------------------\n".toByteArray())
                fos.flush()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write to log file", e)
        }
    }
}
