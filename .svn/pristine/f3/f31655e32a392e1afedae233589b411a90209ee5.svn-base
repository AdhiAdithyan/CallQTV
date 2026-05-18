package com.softland.callqtv.utils

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Utility to back up the application's Room database to the
 * public Download/CALLQTV_CONFIG/<date>/ folder.
 *
 * Example: Download/CALLQTV_CONFIG/2026-03-24/backup_2026-03-24.db
 */
object DatabaseBackup {
    private const val TAG = "DatabaseBackup"
    private const val DB_NAME = "callqtv.db"

    /** Today's date string, e.g. "2026-03-24" */
    private fun todayDateStr(): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

    /**
     * Copies the database file and its sidecars (-shm, -wal) to today's date folder.
     */
    fun backupDatabase(context: Context) {
        try {
            val dbFile = context.getDatabasePath(DB_NAME)
            if (!dbFile.exists()) {
                Log.w(TAG, "Database file does not exist: $DB_NAME")
                return
            }

            // Reuse FileLogger's date-based directory for consistency
            val backupDir = FileLogger.getLogDirectory(context)
            if (!backupDir.exists()) backupDir.mkdirs()

            val dateStr = todayDateStr()
            val backupFile = File(backupDir, "backup_$dateStr.db")

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
        } catch (e: Exception) {
            Log.e(TAG, "Database backup failed", e)
            FileLogger.logError(context, TAG, "Backup failed: ${e.message}", e)
        }
    }
}
