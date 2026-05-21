package com.softland.callqtv.utils

import android.content.Context
import com.softland.callqtv.data.local.AppSharedPreferences
import com.softland.callqtv.data.model.CheckDeviceStatusRequest
import com.softland.callqtv.data.repository.ProjectRepository

/**
 * Silent APK update check for devices that already completed registration.
 */
object AppUpdateChecker {

    data class UpdateInfo(
        val apkVersion: String,
        val downloadUrl: String,
        val isMandatoryUpdate: Int,
        val projectCode: String,
    )

    suspend fun checkForUpdate(context: Context): UpdateInfo? {
        val authPrefs = context.getSharedPreferences(AppSharedPreferences.AUTHENTICATION, Context.MODE_PRIVATE)
        val customerId = authPrefs.getInt(PreferenceHelper.customer_id, 0)
        val deviceRegId = authPrefs.getInt(PreferenceHelper.device_registration_id, 0)
        val productRegId = authPrefs.getInt(PreferenceHelper.product_registration_id, 0)
        if (customerId == 0 || deviceRegId == 0) return null

        val statusReq = CheckDeviceStatusRequest().apply {
            uniqueIDentifier = authPrefs.getString(PreferenceHelper.device_unique_id, null)
            this.customerId = customerId.toString()
            productRegistrationId = productRegId.toString()
            deviceRegistrationId = deviceRegId
            projectNAme = authPrefs.getString(PreferenceHelper.project_name, null)
        }

        val statusRes = try {
            ProjectRepository(context).getCheckDeviceStatus(
                "CheckDeviceStatus",
                statusReq,
            )
        } catch (e: Exception) {
            FileLogger.logError(context, "AppUpdateChecker", "Update check failed", e)
            return null
        }

        if (statusRes.status != 1 && statusRes.status != 3) return null
        if (!ApkUpdateHelper.isUpdateAvailable(context, statusRes)) return null

        val apkVersion = ApkUpdateHelper.normalizeVersionName(statusRes.apkVersion) ?: return null
        val downloadUrl = statusRes.downloadUrl?.trim().orEmpty()
        if (downloadUrl.isBlank()) return null

        return UpdateInfo(
            apkVersion = apkVersion,
            downloadUrl = downloadUrl,
            isMandatoryUpdate = statusRes.isMandatoryUpdate?.toIntOrNull() ?: 0,
            projectCode = statusRes.projectCode.orEmpty(),
        )
    }
}
