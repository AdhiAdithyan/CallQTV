package com.softland.callqtv.utils

import android.os.Build
import android.webkit.WebResourceError

object WebViewErrorCompat {
    fun errorCode(error: WebResourceError?): Int {
        if (error == null) return 0
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) error.errorCode else 0
    }

    fun description(error: WebResourceError?): String {
        if (error == null) return "Unknown error"
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            error.description?.toString().orEmpty().ifBlank { "Unknown error" }
        } else {
            "Unknown error"
        }
    }
}
