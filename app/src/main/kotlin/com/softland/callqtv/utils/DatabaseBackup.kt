package com.softland.callqtv.utils

import android.content.Context
import android.os.Build
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Backs up the Room database into CALLQTV_CONFIG/(date)/ next to API/error logs.
 * API 29+: shared Downloads via MediaStore. Older devices: direct file under public Download.
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

            val dateStr = todayDateStr()
            val backupName = "backup_$dateStr.db"

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val mime = "application/vnd.sqlite3"
                if (PublicCallqtvConfigStorage.copyFileIntoDownloads(
                        context, dateStr, backupName, dbFile, mime
                    )
                ) {
                    Log.i(
                        TAG,
                        "Database backed up to Downloads/${PublicCallqtvConfigStorage.BASE_FOLDER}/$dateStr/$backupName"
                    )
                    val shmFile = File(dbFile.path + "-shm")
                    if (shmFile.exists()) {
                        PublicCallqtvConfigStorage.copyFileIntoDownloads(
                            context, dateStr, "$backupName-shm", shmFile, mime
                        )
                    }
                    val walFile = File(dbFile.path + "-wal")
                    if (walFile.exists()) {
                        PublicCallqtvConfigStorage.copyFileIntoDownloads(
                            context, dateStr, "$backupName-wal", walFile, mime
                        )
                    }
                    return
                }
                Log.e(TAG, "MediaStore DB backup failed; falling back to app-accessible file path")
            }

            val backupDir = FileLogger.getLogDirectory(context)
            val backupFile = File(backupDir, backupName)
            dbFile.copyTo(backupFile, overwrite = true)
            Log.i(TAG, "Database backed up to: ${backupFile.absolutePath}")

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
