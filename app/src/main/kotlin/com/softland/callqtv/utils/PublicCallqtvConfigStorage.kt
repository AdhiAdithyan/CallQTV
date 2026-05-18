package com.softland.callqtv.utils

import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.annotation.RequiresApi
import java.io.File
import java.io.FileInputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Writes under the shared **Downloads** volume so [BASE_FOLDER] is visible in the system Files app
 * and can be copied to USB / SD / another device (scoped-storage–safe on API 29+).
 *
 * Layout: `Download/CALLQTV_CONFIG/<yyyy-MM-dd>/<file>`
 */
object PublicCallqtvConfigStorage {
    const val BASE_FOLDER = "CALLQTV_CONFIG"
    private const val TAG = "PublicCallqtvConfig"

    fun relativePathForDate(dateFolder: String): String =
        "${Environment.DIRECTORY_DOWNLOADS}/$BASE_FOLDER/$dateFolder/"

    fun appendUtf8Text(context: Context, dateFolder: String, displayName: String, text: String): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
        val resolver = context.applicationContext.contentResolver
        val rel = relativePathForDate(dateFolder)
        return try {
            val matches = queryAllDownloadsUris(resolver, displayName, rel)
            val (uri, mode) = when {
                matches.isEmpty() -> {
                    tryRemovePlainFileInPublicDownload(dateFolder, displayName)
                    deleteDownloadsRowsMatching(resolver, displayName, rel)
                    val inserted = insertDownloadsEntryImmediate(resolver, displayName, rel, "text/plain")
                        ?: return false
                    inserted to "w"
                }
                else -> {
                    matches.drop(1).forEach { u ->
                        try {
                            resolver.delete(u, null, null)
                        } catch (_: Exception) {
                        }
                    }
                    matches.first() to "wa"
                }
            }
            resolver.openOutputStream(uri, mode)?.use { out ->
                out.write(text.toByteArray(Charsets.UTF_8))
            } != null
        } catch (e: Exception) {
            Log.e(TAG, "appendUtf8Text failed: $displayName", e)
            false
        }
    }

    fun copyFileIntoDownloads(
        context: Context,
        dateFolder: String,
        displayName: String,
        source: File,
        mimeType: String
    ): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
        if (!source.exists()) return false
        val resolver = context.applicationContext.contentResolver
        val rel = relativePathForDate(dateFolder)
        return try {
            deleteDownloadsRowsMatching(resolver, displayName, rel)
            tryRemovePlainFileInPublicDownload(dateFolder, displayName)
            val uri = insertDownloadsEntryImmediate(resolver, displayName, rel, mimeType)
                ?: return false
            resolver.openOutputStream(uri, "w")?.use { out ->
                FileInputStream(source).use { it.copyTo(out) }
            } != null
        } catch (e: Exception) {
            Log.e(TAG, "copyFileIntoDownloads failed: $displayName", e)
            false
        }
    }

    /**
     * Copies all files under [BASE_FOLDER] in shared Downloads into [destRoot] (flat buckets by leaf name).
     * Used so diagnostics ZIP includes MediaStore-only logs.
     */
    fun copyPublicConfigTreeInto(
        context: Context,
        destRoot: File,
        copiedFiles: MutableList<String>
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        val resolver = context.applicationContext.contentResolver
        val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
        val likeArg = "${Environment.DIRECTORY_DOWNLOADS}/$BASE_FOLDER/%"
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.RELATIVE_PATH
        )
        val selection = "${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?"
        val args = arrayOf(likeArg)
        resolver.query(collection, projection, selection, null, null)?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
            val pathCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.RELATIVE_PATH)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val name = cursor.getString(nameCol) ?: continue
                val rel = cursor.getString(pathCol) ?: continue
                val uri = ContentUris.withAppendedId(collection, id)
                try {
                    val safeRel = rel.trimEnd('/').replace(':', '_')
                    val outFile = File(destRoot, "media_store/$safeRel/$name")
                    outFile.parentFile?.mkdirs()
                    resolver.openInputStream(uri)?.use { input ->
                        outFile.outputStream().use { input.copyTo(it) }
                    }
                    copiedFiles.add(outFile.absolutePath)
                } catch (e: Exception) {
                    Log.w(TAG, "Skip copy $name: ${e.message}")
                }
            }
        }
    }

    fun deleteDateFoldersOlderThan(context: Context, cutoffMillis: Long) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        val resolver = context.applicationContext.contentResolver
        val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
        val likeArg = "${Environment.DIRECTORY_DOWNLOADS}/$BASE_FOLDER/%"
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.RELATIVE_PATH
        )
        val selection = "${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?"
        val args = arrayOf(likeArg)
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val toDelete = mutableListOf<Uri>()
        resolver.query(collection, projection, selection, null, null)?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val pathCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.RELATIVE_PATH)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val rel = cursor.getString(pathCol) ?: continue
                val datePart = extractDateFolder(rel) ?: continue
                val parsedTime = try {
                    dateFormat.parse(datePart)?.time
                } catch (_: Exception) {
                    null
                } ?: continue
                if (parsedTime < cutoffMillis) {
                    toDelete.add(ContentUris.withAppendedId(collection, id))
                }
            }
        }
        toDelete.forEach { uri ->
            try {
                resolver.delete(uri, null, null)
            } catch (e: Exception) {
                Log.w(TAG, "delete old entry failed: ${e.message}")
            }
        }
    }

    private fun extractDateFolder(relativePath: String): String? {
        val prefix = "${Environment.DIRECTORY_DOWNLOADS}/$BASE_FOLDER/"
        if (!relativePath.startsWith(prefix)) return null
        val rest = relativePath.removePrefix(prefix).trimEnd('/')
        val segment = rest.substringBefore('/').ifBlank { rest }
        return segment.takeIf { it.matches(Regex("\\d{4}-\\d{2}-\\d{2}")) }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun queryAllDownloadsUris(
        resolver: ContentResolver,
        displayName: String,
        relativePath: String
    ): List<Uri> {
        val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
        val projection = arrayOf(MediaStore.MediaColumns._ID)
        val selection = "${MediaStore.MediaColumns.DISPLAY_NAME} = ? AND ${MediaStore.MediaColumns.RELATIVE_PATH} = ?"
        val args = arrayOf(displayName, relativePath)
        val out = mutableListOf<Uri>()
        resolver.query(collection, projection, selection, null, null)?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            while (c.moveToNext()) {
                out.add(ContentUris.withAppendedId(collection, c.getLong(idCol)))
            }
        }
        return out
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun deleteDownloadsRowsMatching(
        resolver: ContentResolver,
        displayName: String,
        relativePath: String
    ): Int {
        val selection = "${MediaStore.MediaColumns.DISPLAY_NAME} = ? AND ${MediaStore.MediaColumns.RELATIVE_PATH} = ?"
        val args = arrayOf(displayName, relativePath)
        return resolver.delete(MediaStore.Downloads.EXTERNAL_CONTENT_URI, selection, args)
    }

    /**
     * Removes a direct java.io.File under public Download left by older builds.
     * That file is not always visible to [queryAllDownloadsUris] but still blocks MediaStore
     * publishing to the same path ("Failed to build unique file").
     */
    private fun tryRemovePlainFileInPublicDownload(dateFolder: String, displayName: String) {
        try {
            val root = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val legacy = File(root, "$BASE_FOLDER/$dateFolder/$displayName")
            if (legacy.isFile) legacy.delete()
        } catch (_: Exception) {
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun insertDownloadsEntryImmediate(
        resolver: ContentResolver,
        displayName: String,
        relativePath: String,
        mimeType: String
    ): Uri? {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
        }
        return resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
    }
}
