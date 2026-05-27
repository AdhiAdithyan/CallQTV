package com.softland.callqtv.utils

import android.content.Context
import android.content.ContentValues
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.annotation.RequiresApi
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object DiagnosticsExporter {
    private const val TAG = "DiagnosticsExporter"
    private const val EXPORT_ROOT = "CALLQTV_EXPORT"

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
                        File(exportDir, "all_config_sources/$sourceTag/${PublicCallqtvConfigStorage.BASE_FOLDER}"),
                        copiedFiles
                    )
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                PublicCallqtvConfigStorage.copyPublicConfigTreeInto(
                    context,
                    File(exportDir, "shared_downloads_CALLQTV_CONFIG"),
                    copiedFiles
                )
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
            // Prefer real external volumes (SD/USB app dirs) when mounted — no SAF, always writable for the app.
            val externalVol = exportSnapshotZipToExternalVolumes(context, exportDir, zipName)
            val sharedExport = when {
                externalVol.success -> externalVol
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> {
                    val primary = exportSnapshotZipToSharedDownloads(context, exportDir, zipName)
                    if (primary.success) {
                        primary
                    } else {
                        Log.w(
                            TAG,
                            "Shared Downloads ZIP failed (${primary.message}); falling back to app-writable storage"
                        )
                        exportSnapshotZipToLegacyStorage(context, exportDir, zipName)
                    }
                }
                else -> exportSnapshotZipToLegacyStorage(context, exportDir, zipName)
            }
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
            PublicCallqtvConfigStorage.BASE_FOLDER
        )
        val appSpecificRoot = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
            ?: context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: context.getExternalFilesDir(null)
            ?: context.filesDir
        val appSpecificBase = File(appSpecificRoot, PublicCallqtvConfigStorage.BASE_FOLDER)
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

    @RequiresApi(Build.VERSION_CODES.Q)
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
                put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/$EXPORT_ROOT")
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

    /**
     * Writes the ZIP under [EXPORT_ROOT] on **removable** app-specific external dirs only
     * (SD card, USB storage when exposed to the app). Primary internal emulated storage is
     * not used here so we still prefer shared Downloads / legacy paths on normal devices.
     */
    private fun exportSnapshotZipToExternalVolumes(
        context: Context,
        sourceDir: File,
        zipName: String,
    ): SharedExportResult {
        val triedPaths = linkedSetOf<String>()
        for ((root, label) in listRemovableExternalExportRootsWithLabels(context)) {
            val key = try {
                root.absoluteFile.canonicalPath
            } catch (_: Exception) {
                root.absolutePath
            }
            if (!triedPaths.add(key)) continue
            val r = tryWriteSnapshotZip(sourceDir, zipName, root, label)
            if (r.success) return r
            Log.d(TAG, "Removable volume export skipped ($label): ${r.message}")
        }
        return SharedExportResult(false, "", "no writable removable external volume")
    }

    private fun listRemovableExternalExportRootsWithLabels(context: Context): List<Pair<File, String>> {
        val bases: List<File> =
            context.getExternalFilesDirs(Environment.DIRECTORY_DOWNLOADS)
                .filterNotNull()
                .filter { it.exists() || it.mkdirs() }
        return bases
            .filter { base ->
                try {
                    Environment.isExternalStorageRemovable(base)
                } catch (_: Exception) {
                    false
                }
            }
            .map { base ->
                val root = File(base, EXPORT_ROOT)
                root to "external removable (SD/USB)"
            }
    }

    private fun tryWriteSnapshotZip(
        sourceDir: File,
        zipName: String,
        exportRoot: File,
        label: String,
    ): SharedExportResult {
        return try {
            if (!exportRoot.exists()) exportRoot.mkdirs()
            if (!exportRoot.canWrite()) {
                return SharedExportResult(false, "", "not writable: ${exportRoot.absolutePath}")
            }
            val outFile = File(exportRoot, zipName)
            FileOutputStream(outFile).use { out ->
                zipDirectoryToStream(sourceDir, out)
            }
            SharedExportResult(
                success = true,
                location = outFile.absolutePath,
                message = "Exported ZIP ($label): ${outFile.absolutePath}",
            )
        } catch (e: Exception) {
            SharedExportResult(false, "", e.message ?: "zip write error")
        }
    }

    private fun exportSnapshotZipToLegacyStorage(
        context: Context,
        sourceDir: File,
        zipName: String
    ): SharedExportResult {
        return try {
            val first = tryWriteSnapshotZip(
                sourceDir,
                zipName,
                getExportParent(context),
                "public or preferred folder",
            )
            if (first.success) {
                first
            } else {
                Log.w(TAG, "Preferred export failed (${first.message}); retrying app-scoped storage")
                tryWriteSnapshotZip(
                    sourceDir,
                    zipName,
                    getAppScopedExportRoot(context),
                    "app storage",
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Legacy ZIP export failed: ${e.message}")
            try {
                val appRoot = getAppScopedExportRoot(context)
                Log.i(TAG, "Retrying ZIP export under app-scoped storage: ${appRoot.absolutePath}")
                tryWriteSnapshotZip(sourceDir, zipName, appRoot, "app storage")
            } catch (e2: Exception) {
                Log.e(TAG, "App-scoped ZIP export also failed", e2)
                SharedExportResult(
                    success = false,
                    location = "",
                    message = "Export failed: ${e2.message ?: e.message ?: "storage write error"}"
                )
            }
        }
    }

    /** Always writable under scoped storage (no MediaStore). */
    private fun getAppScopedExportRoot(context: Context): File {
        val base = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
            ?: context.getExternalFilesDir(null)
            ?: context.filesDir
        return File(base, EXPORT_ROOT).apply { mkdirs() }
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

