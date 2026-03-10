package com.softland.callqtv.utils

import android.app.Activity
import android.content.Context
import android.view.inputmethod.InputMethodManager

object KeyboardUtils {
    fun hideKeyboard(activity: Activity) {
        val view = activity.currentFocus ?: activity.window?.decorView?.rootView
        val token = view?.windowToken ?: return
        try {
            val imm = activity.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager ?: return
            imm.hideSoftInputFromWindow(token, 0)
        } catch (e: Exception) {
            android.util.Log.w("KeyboardUtils", "hideKeyboard failed: ${e.message}")
        }
    }
}
