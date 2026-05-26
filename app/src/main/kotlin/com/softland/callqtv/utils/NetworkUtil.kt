package com.softland.callqtv.utils

import android.content.Context

object NetworkUtil {
    fun isNetworkAvailable(context: Context): Boolean = NetworkCompat.isNetworkAvailable(context)
}
