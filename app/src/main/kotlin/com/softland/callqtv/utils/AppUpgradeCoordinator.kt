package com.softland.callqtv.utils

import android.content.Context
import com.softland.callqtv.data.local.AppSharedPreferences

/**
 * Detects APK upgrades and triggers a one-time TV config refresh on next main-screen load.
 */
object AppUpgradeCoordinator {

    private const val PREF_LAST_VERSION_CODE = "last_installed_version_code"
    private const val PREF_PENDING_CONFIG_REFRESH = "pending_config_refresh_after_upgrade"

    fun onApplicationStart(context: Context) {
        val prefs = context.getSharedPreferences(AppSharedPreferences.AUTHENTICATION, Context.MODE_PRIVATE)
        val currentCode = ApkUpdateHelper.installedVersionCode(context)
        if (currentCode <= 0) return

        val lastCode = prefs.getLong(PREF_LAST_VERSION_CODE, -1L)
        if (lastCode >= 0 && lastCode != currentCode) {
            prefs.edit()
                .putBoolean(PREF_PENDING_CONFIG_REFRESH, true)
                .apply()
        }
        if (lastCode != currentCode) {
            prefs.edit()
                .putLong(PREF_LAST_VERSION_CODE, currentCode)
                .apply()
        }
    }

    fun consumePendingConfigRefresh(context: Context): Boolean {
        val prefs = context.getSharedPreferences(AppSharedPreferences.AUTHENTICATION, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(PREF_PENDING_CONFIG_REFRESH, false)) return false
        prefs.edit().remove(PREF_PENDING_CONFIG_REFRESH).apply()
        return true
    }
}
