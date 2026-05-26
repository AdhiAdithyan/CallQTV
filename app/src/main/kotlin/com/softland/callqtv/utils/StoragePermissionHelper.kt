package com.softland.callqtv.utils

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat

/**
 * Coordinates storage-related permission prompts so Splash and TokenDisplay do not each
 * open multiple system dialogs on the same cold start.
 */
object StoragePermissionHelper {

    @Volatile
    private var runtimeRequestInFlight = false

    @Volatile
    private var manageAllFilesDialogShownThisProcess = false

    @Volatile
    private var pendingOnGranted: (() -> Unit)? = null

    fun missingRuntimePermissions(context: Context): Array<String> {
        val candidates = mutableListOf<String>()
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                // Android 13–16
                candidates += Manifest.permission.READ_MEDIA_VIDEO
                candidates += Manifest.permission.POST_NOTIFICATIONS
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> {
                // Android 10–12L (scoped storage; no broad WRITE on 29+)
                candidates += Manifest.permission.READ_EXTERNAL_STORAGE
            }
            else -> {
                // Android 5–9
                candidates += Manifest.permission.READ_EXTERNAL_STORAGE
                candidates += Manifest.permission.WRITE_EXTERNAL_STORAGE
            }
        }
        return candidates
            .filter {
                ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
            }
            .toTypedArray()
    }

    fun needsManageAllFilesAccess(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
            !Environment.isExternalStorageManager()
    }

    fun hasFullStorageAccess(context: Context): Boolean {
        if (missingRuntimePermissions(context).isNotEmpty()) return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return Environment.isExternalStorageManager()
        }
        return true
    }

    /**
     * Single entry point per process: runtime permissions first, then optional
     * "All files access" dialog on API 30+.
     */
    fun ensureStorageAccess(
        activity: ComponentActivity,
        permissionLauncher: ActivityResultLauncher<Array<String>>,
    ) {
        if (hasFullStorageAccess(activity)) return

        val missing = missingRuntimePermissions(activity)
        if (missing.isNotEmpty() && requestRuntimePermissions(permissionLauncher, missing)) {
            return
        }
        promptManageAllFilesAccessIfNeeded(activity)
    }

    /**
     * Runs [onGranted] only after runtime permissions (and all-files access on API 30+) are granted.
     * If not granted, requests are shown and [onGranted] runs when access becomes available.
     */
    fun runWhenStorageAccessReady(
        activity: ComponentActivity,
        permissionLauncher: ActivityResultLauncher<Array<String>>,
        onGranted: () -> Unit,
    ) {
        if (hasFullStorageAccess(activity)) {
            onGranted()
            return
        }
        pendingOnGranted = onGranted
        ensureStorageAccess(activity, permissionLauncher)
        tryRunPendingBlock(activity)
    }

    fun onRuntimePermissionResult(activity: ComponentActivity) {
        runtimeRequestInFlight = false
        if (hasFullStorageAccess(activity)) {
            tryRunPendingBlock(activity)
        } else {
            promptManageAllFilesAccessIfNeeded(activity)
        }
    }

    /**
     * Re-check after returning from system permission screens (e.g. All files access settings).
     */
    fun onActivityResumed(
        activity: ComponentActivity,
        permissionLauncher: ActivityResultLauncher<Array<String>>,
    ) {
        if (hasFullStorageAccess(activity)) {
            tryRunPendingBlock(activity)
        } else {
            if (needsManageAllFilesAccess()) {
                manageAllFilesDialogShownThisProcess = false
            }
            ensureStorageAccess(activity, permissionLauncher)
        }
    }

    private fun tryRunPendingBlock(activity: ComponentActivity) {
        if (!hasFullStorageAccess(activity)) return
        val block = pendingOnGranted ?: return
        pendingOnGranted = null
        block()
    }

    private fun requestRuntimePermissions(
        launcher: ActivityResultLauncher<Array<String>>,
        permissions: Array<String>,
    ): Boolean {
        if (runtimeRequestInFlight || permissions.isEmpty()) return false
        runtimeRequestInFlight = true
        launcher.launch(permissions)
        return true
    }

    fun promptManageAllFilesAccessIfNeeded(activity: ComponentActivity): Boolean {
        if (!needsManageAllFilesAccess()) return false
        if (manageAllFilesDialogShownThisProcess) return false
        manageAllFilesDialogShownThisProcess = true

        AlertDialog.Builder(activity)
            .setTitle("Storage Permission Required")
            .setMessage(
                "To manage, delete, and download advertisements in the Download folder, " +
                    "the app needs 'All Files Access' permission. Please enable it on the next screen.",
            )
            .setPositiveButton("Grant") { _, _ ->
                openManageAllFilesSettings(activity)
            }
            .setNegativeButton("Cancel", null)
            .show()
        return true
    }

    fun openManageAllFilesSettings(activity: Activity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        try {
            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                addCategory("android.intent.category.DEFAULT")
                data = Uri.parse("package:${activity.packageName}")
            }
            activity.startActivity(intent)
        } catch (_: Exception) {
            activity.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
        }
    }
}
