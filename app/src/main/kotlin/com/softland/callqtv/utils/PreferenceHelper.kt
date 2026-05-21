package com.softland.callqtv.utils

import android.content.Context
import android.content.SharedPreferences
import com.softland.callqtv.data.local.AppSharedPreferences

object PreferenceHelper {
    const val base_url = "base_url"
    const val product_license_end = "product_license_end"
    const val customer_id = "CustomerID"
    const val customer_id_text = "CustomerIDText"
    const val project_code = "ProjectCode"
    const val TOKEN_VOICE = "token_voice"
    const val OFFLINE_ADS = "offline_ads"
    const val device_registration_id = "device_registration_id"
    const val product_registration_id = "product_registration_id"
    const val device_unique_id = "device_unique_id"
    const val project_name = "project_name"
    /** Last license API base (scheme + host + path) that succeeded; prefer it on next launch. */
    const val license_transport_base_last_ok = "license_transport_base_last_ok"
    private const val CURRENT_DATE = "current_date"

    private fun getAuthPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(AppSharedPreferences.AUTHENTICATION, Context.MODE_PRIVATE)
    }

    private fun getLoginPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(AppSharedPreferences.Login, Context.MODE_PRIVATE)
    }

    fun getCurrentDate(context: Context): String {
        return getAuthPrefs(context).getString(CURRENT_DATE, "") ?: ""
    }

    fun saveCurrentDate(context: Context, date: String) {
        getAuthPrefs(context).edit().putString(CURRENT_DATE, date).apply()
    }

    fun clearStringList(context: Context) {
        // Assuming this clears some specific transaction or cached data
        context.getSharedPreferences(AppSharedPreferences.TRANSACTION, Context.MODE_PRIVATE).edit().clear().apply()
    }

    fun UpdateLoginRespDetails(context: Context, key: String, value: String) {
        getLoginPrefs(context).edit().putString(key, value).apply()
    }

    fun isOfflineAdsEnabled(context: Context): Boolean {
        // Default ON for first launch unless user explicitly disables it.
        return getAuthPrefs(context).getBoolean(OFFLINE_ADS, true)
    }

    fun setOfflineAdsEnabled(context: Context, enabled: Boolean) {
        getAuthPrefs(context).edit().putBoolean(OFFLINE_ADS, enabled).apply()
    }

    fun getLastSuccessfulLicenseBase(context: Context): String? {
        return getAuthPrefs(context).getString(license_transport_base_last_ok, null)?.trim()?.takeIf { it.isNotBlank() }
    }

    fun setLastSuccessfulLicenseBase(context: Context, baseUrl: String) {
        val trimmed = baseUrl.trim()
        if (trimmed.isBlank()) return
        getAuthPrefs(context).edit().putString(license_transport_base_last_ok, trimmed).apply()
    }
}
