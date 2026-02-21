package com.softland.callqtv.utils

import android.content.Context
import android.content.SharedPreferences
import com.softland.callqtv.data.local.AppSharedPreferences

object PreferenceHelper {
    const val base_url = "base_url"
    const val product_license_end = "product_license_end"
    const val customer_id = "CustomerID"
    const val project_code = "ProjectCode"
    const val TOKEN_VOICE = "token_voice"
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
}
