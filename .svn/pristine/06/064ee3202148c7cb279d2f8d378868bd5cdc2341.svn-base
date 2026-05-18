package com.softland.callqtv.utils

import android.content.Context
import android.content.ContentValues
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import java.io.File
import java.io.FileInputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object DiagnosticsExporter {
    private const val TAG = "DiagnosticsExporter"
    private const val EXPORT_ROOT = "CALLQTV_EXPORT"
    private const val CONFIG_ROOT = "CALLQTV_CONFIG"

    data class ExportResult(
        val success: Boolean,
        val path: String,
        val message: String
    )

    private data class SharedExportResult(
        val success: Boolean,
        val location: String,
        val message: String
    )

    fun exportConfigSnapshot(context: Context): ExportResult {
        return try {
            // Ensure latest DB backup is generated into CALLQTV_CONFIG/<date>.
            DatabaseBackup.backupDatabase(context)

            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val exportParent = File(context.cacheDir, EXPORT_ROOT).apply { mkdirs() }
            clearDirectoryContents(exportParent)
            clearSharedDownloadsExports(context)
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

            val zipName = "snapshot_$timestamp.zip"
            val sharedExport = exportSnapshotZipToSharedDownloads(context, exportDir, zipName)
            clearDirectoryContents(exportParent)

            ExportResult(
                success = sharedExport.success,
                path = sharedExport.location,
                message = sharedExport.message
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

    private fun clearDirectoryContents(dir: File) {
        if (!dir.exists()) return
        dir.listFiles()?.forEach { child ->
            try {
                if (child.isDirectory) child.deleteRecursively() else child.delete()
            } catch (e: Exception) {
                Log.w(TAG, "Failed to delete old export ${child.absolutePath}: ${e.message}")
            }
        }
    }

    private fun clearSharedDownloadsExports(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        try {
            val resolver = context.contentResolver
            val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
            val relPath = "${Environment.DIRECTORY_DOWNLOADS}/$EXPORT_ROOT/"
            val where = "${MediaStore.Downloads.RELATIVE_PATH} = ?"
            val args = arrayOf(relPath)
            resolver.delete(collection, where, args)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to clear shared Downloads exports: ${e.message}")
        }
    }

    private fun exportSnapshotZipToSharedDownloads(
        context: Context,
        sourceDir: File,
        zipName: String
    ): SharedExportResult {
        return try {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, zipName)
                put(MediaStore.Downloads.MIME_TYPE, "application/zip")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/$EXPORT_ROOT")
                }
            }

            val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
            val itemUri = resolver.insert(collection, values)
                ?: return SharedExportResult(
                    success = false,
                    location = "",
                    message = "Export failed: unable to create shared Downloads entry"
                )

            resolver.openOutputStream(itemUri)?.use { out ->
                zipDirectoryToStream(sourceDir, out)
            } ?: return SharedExportResult(
                success = false,
                location = "",
                message = "Export failed: unable to open shared Downloads output stream"
            )

            SharedExportResult(
                success = true,
                location = "Downloads/$EXPORT_ROOT/$zipName",
                message = "Exported ZIP to shared Downloads/$EXPORT_ROOT/$zipName"
            )
        } catch (e: Exception) {
            Log.w(TAG, "Shared ZIP export failed: ${e.message}")
            SharedExportResult(
                success = false,
                location = "",
                message = "Export failed: ${e.message ?: "shared storage write error"}"
            )
        }
    }

    private fun zipDirectoryToStream(sourceDir: File, outputStream: OutputStream) {
        ZipOutputStream(outputStream).use { zos ->
            sourceDir.walkTopDown()
                .filter { it.isFile }
                .forEach { file ->
                    val relPath = file.relativeTo(sourceDir).invariantSeparatorsPath
                    zos.putNextEntry(ZipEntry(relPath))
                    FileInputStream(file).use { input ->
                        input.copyTo(zos)
                    }
                    zos.closeEntry()
                }
        }
    }
}

