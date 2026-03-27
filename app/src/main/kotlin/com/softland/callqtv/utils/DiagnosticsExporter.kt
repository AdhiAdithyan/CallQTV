package com.softland.callqtv.utils

import android.content.Context
import android.os.Environment
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DiagnosticsExporter {
    private const val TAG = "DiagnosticsExporter"
    private const val EXPORT_ROOT = "CALLQTV_EXPORT"
    private const val CONFIG_ROOT = "CALLQTV_CONFIG"

    data class ExportResult(
        val success: Boolean,
        val path: String,
        val message: String
    )

    fun exportConfigSnapshot(context: Context): ExportResult {
        return try {
            // Ensure latest DB backup is generated into CALLQTV_CONFIG/<date>.
            DatabaseBackup.backupDatabase(context)

            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val exportParent = getExportParent(context)
            val exportDir = File(exportParent, "snapshot_$timestamp").apply { mkdirs() }
            val copiedFiles = mutableListOf<String>()

            // Collect ALL possible config/log sources (public + app-specific fallback).
            val configSources = getConfigSourceDirs(context)
            configSources.forEachIndexed { idx, src ->
                if (src.exists()) {
                    val sourceTag = if (idx == 0) "public_or_active" else "source_$idx"
                    copyDirectory(
                        src,
                        File(exportDir, "all_config_sources/$sourceTag/$CONFIG_ROOT"),
                        copiedFiles
                    )
                }
            }

            // Explicitly export ALL error and API response logs (not only folder copy).
            val allLogFiles = configSources
                .flatMap { src -> listFilesRecursively(src) }
                .filter {
                    it.isFile && (
                        it.name.startsWith("errors_") ||
                            it.name.startsWith("api_responses_") ||
                            it.name.startsWith("backup_")
                        )
                }
                .distinctBy { it.absolutePath }

            allLogFiles.forEach { file ->
                val bucket = when {
                    file.name.startsWith("errors_") -> "error_logs"
                    file.name.startsWith("api_responses_") -> "api_responses"
                    else -> "db_backups"
                }
                copyFile(file, File(exportDir, "extracted/$bucket/${file.name}"), copiedFiles)
            }

            // Add current live DB files directly in case they are newer than backup copy.
            val dbFile = context.getDatabasePath("callqtv.db")
            if (dbFile.exists()) {
                copyFile(dbFile, File(exportDir, "database_live/${dbFile.name}"), copiedFiles)
            }
            val wal = File(dbFile.path + "-wal")
            if (wal.exists()) copyFile(wal, File(exportDir, "database_live/${wal.name}"), copiedFiles)
            val shm = File(dbFile.path + "-shm")
            if (shm.exists()) copyFile(shm, File(exportDir, "database_live/${shm.name}"), copiedFiles)

            val summary = File(exportDir, "export_summary.txt")
            summary.writeText(
                buildString {
                    appendLine("CallQTV diagnostics snapshot")
                    appendLine("Created: ${Date()}")
                    appendLine("Export path: ${exportDir.absolutePath}")
                    appendLine("Config sources:")
                    configSources.forEach { appendLine("- ${it.absolutePath}") }
                    appendLine("DB source: ${dbFile.absolutePath}")
                    appendLine("Total copied files: ${copiedFiles.size}")
                    appendLine()
                    appendLine("Copied file list:")
                    copiedFiles.sorted().forEach { appendLine(it) }
                }
            )

            ExportResult(
                success = true,
                path = exportDir.absolutePath,
                message = "Export created at ${exportDir.absolutePath}"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Export failed", e)
            ExportResult(
                success = false,
                path = "",
                message = "Export failed: ${e.message ?: "unknown error"}"
            )
        }
    }

    private fun getExportParent(context: Context): File {
        val publicRoot = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val publicExport = File(publicRoot, EXPORT_ROOT)
        if ((publicExport.exists() || publicExport.mkdirs()) && publicExport.canWrite()) {
            return publicExport
        }

        val fallback = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: context.getExternalFilesDir(null)
            ?: context.filesDir
        return File(fallback, EXPORT_ROOT).apply { mkdirs() }
    }

    private fun getConfigSourceDirs(context: Context): List<File> {
        val activeDateDir = FileLogger.getLogDirectory(context)
        val activeBase = activeDateDir.parentFile ?: activeDateDir
        val publicBase = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            CONFIG_ROOT
        )
        val appSpecificBase = File(
            context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                ?: context.getExternalFilesDir(null)
                ?: context.filesDir,
            CONFIG_ROOT
        )
        return listOf(activeBase, publicBase, appSpecificBase).distinctBy { it.absolutePath }
    }

    private fun listFilesRecursively(root: File): List<File> {
        if (!root.exists()) return emptyList()
        val out = mutableListOf<File>()
        root.walkTopDown().forEach { out.add(it) }
        return out
    }

    private fun copyDirectory(source: File, destination: File, copiedFiles: MutableList<String>) {
        if (!destination.exists()) destination.mkdirs()
        source.listFiles()?.forEach { child ->
            val destChild = File(destination, child.name)
            if (child.isDirectory) copyDirectory(child, destChild, copiedFiles) else copyFile(child, destChild, copiedFiles)
        }
    }

    private fun copyFile(source: File, destination: File, copiedFiles: MutableList<String>) {
        destination.parentFile?.mkdirs()
        source.copyTo(destination, overwrite = true)
        copiedFiles.add(destination.absolutePath)
    }
}

