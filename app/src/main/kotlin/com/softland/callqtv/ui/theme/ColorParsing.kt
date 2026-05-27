package com.softland.callqtv.ui.theme

import androidx.compose.ui.graphics.Color
import android.graphics.Color as AndroidColor

fun parseColorOrDefault(colorString: String?, default: Color): Color {
    return try {
        if (colorString.isNullOrBlank()) default else Color(AndroidColor.parseColor(colorString))
    } catch (_: Exception) {
        default
    }
}

fun parseHexColorOrNull(colorString: String?): Int? {
    if (colorString.isNullOrBlank()) return null
    return try {
        AndroidColor.parseColor(colorString.trim())
    } catch (_: Exception) {
        null
    }
}
