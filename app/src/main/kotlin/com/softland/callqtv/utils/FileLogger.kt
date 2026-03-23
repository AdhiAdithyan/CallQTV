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

    private fun getLogFile(context: Context): File {
        @Suppress("DEPRECATION")
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val callQtvDir = File(downloadsDir, BASE_FOLDER)
        if (!callQtvDir.exists()) {
            callQtvDir.mkdirs()
        }
        return File(callQtvDir, "log.txt")
    }

    private fun getErrorLogFile(context: Context): File {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val dateString = dateFormat.format(Date())
        @Suppress("DEPRECATION")
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val callQtvDir = File(downloadsDir, BASE_FOLDER)
        if (!callQtvDir.exists()) {
            callQtvDir.mkdirs()
        }
        return File(callQtvDir, "errors_$dateString.txt")
    }

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
        // Automatically clean up old logs before writing new ones
        performCleanup(context)
        
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

    /**
     * Deletes log files in the CallQTV directory that are older than 7 days.
     */
    private fun performCleanup(context: Context) {
        try {
            val logDir = getLogDirectory(context)
            if (!logDir.exists() || !logDir.isDirectory) return

            val files = logDir.listFiles() ?: return
            val now = System.currentTimeMillis()
            val sevenDaysInMillis = 7L * 24 * 60 * 60 * 1000
            val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

            for (file in files) {
                if (file.isFile && file.name.startsWith("errors_") && file.name.endsWith(".txt")) {
                    try {
                        // Extract date string: errors_2026-03-19.txt -> 2026-03-19
                        val datePart = file.name.substringAfter("errors_").substringBefore(".txt")
                        val fileDate = dateFormat.parse(datePart)
                        if (fileDate != null) {
                            val diff = now - fileDate.time
                            if (diff > sevenDaysInMillis) {
                                if (file.delete()) {
                                    Log.d(TAG, "Deleted old log file: ${file.name}")
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to process file for cleanup: ${file.name}", e)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Cleanup failed", e)
        }
    }
}
