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
    private const val BASE_FOLDER = "CALLQTV_CONFIG"

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

    private fun getApiResponseLogFileName(): String {
        val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        return "api_responses_$dateStr.txt"
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
        val root = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val callqDir = File(root, BASE_FOLDER)
        if (!callqDir.exists()) {
            try {
                callqDir.mkdirs()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to create public log directory", e)
            }
        }
        
        return if (callqDir.exists() && callqDir.canWrite()) {
            callqDir
        } else {
            // Fallback to app-specific external dir or internal filesDir
            val downloads = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                ?: context.getExternalFilesDir(null)
                ?: context.filesDir
            val fallbackDir = File(downloads, BASE_FOLDER)
            if (!fallbackDir.exists()) fallbackDir.mkdirs()
            fallbackDir
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
    fun logError(context: Context, tag: String, message: String, exception: Throwable? = null) {
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        val logEntry = if (exception != null) {
            "[$timestamp] [$tag] $message\n${Log.getStackTraceString(exception)}\n"
        } else {
            "[$timestamp] [$tag] $message\n"
        }
        
        // Trigger cleanup before writing new log
        performCleanup(context)

        try {
            val logDir = getLogDirectory(context)
            if (!logDir.exists()) logDir.mkdirs()
            val logFile = File(logDir, getLogFileName()) // Use date-wise file
            Log.d(TAG, "Logging error to: ${logFile.absolutePath}")
            logFile.appendText(logEntry)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write to log file", e)
        }
    }

    /**
     * Logs an API response with timestamp and API name.
     */
    fun logResponse(context: Context, apiName: String, response: String) {
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        val logEntry = "[$timestamp] [$apiName] $response\n"
        
        try {
            val logDir = getLogDirectory(context)
            if (!logDir.exists()) logDir.mkdirs()
            val logFile = File(logDir, getApiResponseLogFileName())
            logFile.appendText(logEntry)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write API response to log file", e)
        }
    }

    /**
     * Deletes log files in the CallQTV directory that are older than 7 days.
     */
    private fun performCleanup(context: Context) {
        try {
            val logDir = getLogDirectory(context) ?: return
            val files = logDir.listFiles { _, name -> 
                (name.startsWith("errors_") || name.startsWith("api_responses_") || name.startsWith("backup_")) && name.endsWith(".txt") || name.endsWith(".db") 
            } ?: return
            
            val sevenDaysAgo = System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000L)
            val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

            for (file in files) {
                try {
                    // Extract date string: errors_2026-03-19.txt -> 2026-03-19
                    val datePart = file.name.substringAfter("errors_").substringBefore(".txt")
                    val fileDate = dateFormat.parse(datePart)
                    if (fileDate != null) {
                        if (fileDate.time < sevenDaysAgo) {
                            if (file.delete()) {
                                Log.d(TAG, "Deleted old log file: ${file.name}")
                            }
                        }
                    }
                } catch (e: Exception) {
                    // Fallback to last modified time if parsing fails
                    if (file.lastModified() < sevenDaysAgo) {
                        file.delete()
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Cleanup failed", e)
        }
    }
}
