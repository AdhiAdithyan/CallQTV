package com.softland.callqtv.utils

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.softland.callqtv.data.model.CheckDeviceStatusResponse
import java.io.File

/**
 * Download path, install intent, and version comparison for in-app APK updates.
 */
object ApkUpdateHelper {

    private const val APK_FILE_PREFIX = "CallQTV_Version"

    fun apkFileForVersion(context: Context, versionName: String): File {
        val dir = context.getExternalFilesDir(null) ?: context.filesDir
        return File(dir, "$APK_FILE_PREFIX$versionName.apk")
    }

    fun normalizeVersionName(raw: String?): String? {
        val trimmed = raw?.trim().orEmpty()
        if (trimmed.isBlank()) return null
        return trimmed.removePrefix("v").removePrefix("V")
    }

    fun installedVersionName(context: Context): String? {
        return try {
            @Suppress("DEPRECATION")
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            info.versionName
        } catch (_: Exception) {
            null
        }
    }

    fun installedVersionCode(context: Context): Long {
        return try {
            val info = context.packageManager.getPackageInfo(
                context.packageName,
                PackageManager.GET_ACTIVITIES,
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                info.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                info.versionCode.toLong()
            }
        } catch (_: Exception) {
            -1L
        }
    }

    /**
     * Returns true when the server indicates a newer build than this APK.
     */
    fun isUpdateAvailable(context: Context, statusRes: CheckDeviceStatusResponse): Boolean {
        val serverCode = statusRes.apkVersionCode?.trim()?.toLongOrNull()
        val installedCode = installedVersionCode(context)
        if (serverCode != null && serverCode > 0 && installedCode > 0) {
            return serverCode > installedCode
        }

        val serverVersion = normalizeVersionName(statusRes.apkVersion)
        val installedVersion = normalizeVersionName(installedVersionName(context))
        if (serverVersion.isNullOrBlank() || installedVersion.isNullOrBlank()) return false
        return serverVersion != installedVersion
    }

    fun needsInstallPermissionSettings(context: Context): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !context.packageManager.canRequestPackageInstalls()
    }

    fun createInstallUnknownAppsIntent(context: Context): Intent {
        // Only called when [needsInstallPermissionSettings] is true (API 26+).
        val action = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES
        } else {
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS
        }
        return Intent(action).apply {
            data = Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    /**
     * Starts the package installer for [apkFile]. Returns false when the file is missing
     * or unknown-sources permission is required (caller should open settings).
     */
    fun startPackageInstall(context: Context, apkFile: File): Boolean {
        if (!apkFile.exists()) return false
        if (needsInstallPermissionSettings(context)) return false

        val apkUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile,
            )
        } else {
            Uri.fromFile(apkFile)
        }

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        return true
    }
}
