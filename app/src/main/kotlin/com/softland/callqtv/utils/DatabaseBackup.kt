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
 * Utility to back up the application's Room database to the public Download/CALLQTV_CONFIG folder.
 */
object DatabaseBackup {
    private const val TAG = "DatabaseBackup"
    private const val DB_NAME = "callqtv.db"
    private const val BASE_FOLDER = "CALLQTV_CONFIG"

    /**
     * Copies the database file and its sidecars (-shm, -wal) to the backup directory.
     */
    fun backupDatabase(context: Context) {
        try {
            val dbFile = context.getDatabasePath(DB_NAME)
            if (!dbFile.exists()) {
                Log.w(TAG, "Database file does not exist: $DB_NAME")
                return
            }

            val logDir = getLogDirectory(context)
            if (!logDir.exists()) logDir.mkdirs()

            val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            val backupFile = File(logDir, "backup_$dateStr.db")

            // Copy main database file
            dbFile.copyTo(backupFile, overwrite = true)
            Log.i(TAG, "Database backed up to: ${backupFile.absolutePath}")

            // Copy sidecar files if they exist (WAL mode)
            val shmFile = File(dbFile.path + "-shm")
            if (shmFile.exists()) {
                shmFile.copyTo(File(backupFile.path + "-shm"), overwrite = true)
            }
            val walFile = File(dbFile.path + "-wal")
            if (walFile.exists()) {
                walFile.copyTo(File(backupFile.path + "-wal"), overwrite = true)
            }

            performCleanup(logDir)
        } catch (e: Exception) {
            Log.e(TAG, "Database backup failed", e)
            FileLogger.logError(context, TAG, "Backup failed: ${e.message}", e)
        }
    }

    private fun getLogDirectory(context: Context): File {
        val root = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val callqDir = File(root, BASE_FOLDER)
        if (!callqDir.exists()) {
            try {
                callqDir.mkdirs()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to create public backup directory", e)
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
     * Deletes backup files older than 7 days.
     */
    private fun performCleanup(logDir: File) {
        try {
            val files = logDir.listFiles { _, name -> name.startsWith("backup_") && name.endsWith(".db") } ?: return
            val sevenDaysAgo = System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000L)
            
            for (file in files) {
                if (file.lastModified() < sevenDaysAgo) {
                    if (file.delete()) {
                        Log.d(TAG, "Deleted old backup file: ${file.name}")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Backup cleanup failed", e)
        }
    }
}
