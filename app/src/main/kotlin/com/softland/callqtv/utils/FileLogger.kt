package com.softland.callqtv.utils

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
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
     */
    fun getLogFilePath(context: Context): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            Environment.getExternalStorageDirectory().path + "/Download/$LOG_FILE_NAME"
        } else {
            @Suppress("DEPRECATION")
            val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            File(dir, LOG_FILE_NAME).absolutePath
        }
    }

    /**
     * Opens an output stream for appending to the log file.
     * Location: Download/CallQTV_errors.txt
     */
    private fun getLogOutputStream(context: Context): OutputStream? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val projection = arrayOf(MediaStore.Downloads._ID)
                val selection = "${MediaStore.Downloads.DISPLAY_NAME}=?"
                val selectionArgs = arrayOf(LOG_FILE_NAME)
                context.contentResolver.query(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                    projection,
                    selection,
                    selectionArgs,
                    null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val id = cursor.getLong(0)
                        val uri = ContentUris.withAppendedId(MediaStore.Downloads.EXTERNAL_CONTENT_URI, id)
                        context.contentResolver.openOutputStream(uri, "wa")
                    } else {
                        val values = ContentValues().apply {
                            put(MediaStore.Downloads.DISPLAY_NAME, LOG_FILE_NAME)
                            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                        }
                        context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                            ?.let { context.contentResolver.openOutputStream(it, "wa") }
                    }
                }
            } else {
                @Suppress("DEPRECATION")
                val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                if (!dir.exists()) dir.mkdirs()
                FileOutputStream(File(dir, LOG_FILE_NAME), true)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open Download log stream, falling back to app dir", e)
            val fallbackDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.getExternalFilesDir(null) ?: context.filesDir
            FileOutputStream(File(fallbackDir!!, LOG_FILE_NAME), true)
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
                    PrintWriter(fos).use { writer ->
                        it.printStackTrace(writer)
                        writer.flush()
                    }
                }
                fos.write("\n-----------------------------------\n".toByteArray())
            }
            Log.d(TAG, "Log written to: ${getLogFilePath(context)}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write to log file", e)
        }
    }
}
