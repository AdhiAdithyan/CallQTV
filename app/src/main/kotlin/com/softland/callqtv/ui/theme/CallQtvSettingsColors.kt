package com.softland.callqtv.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Fixed palette for settings dialogs and overlays (independent of app Material theme).
 */
object CallQtvSettingsColors {
    val Primary = Color(0xFF4FC3F7)
    val Text = Color(0xFFECEFF1)
    val MutedText = Color(0xFFB0BEC5)
    val Card = Color(0xFF263238)
    val Border = Color(0xFF607D8B)
    val Error = Color(0xFFEF5350)
}

/** Full-screen error / config-unavailable overlays. */
object CallQtvOverlayColors {
    val ScreenBackground = Color(0xFF121212)
    val BodyText = Color(0xFFE0E0E0)
    val MutedLabel = Color(0xFF9E9E9E)
}

/** TV-friendly focus rings for preset grids (color + sound pickers). */
object CallQtvPickerFocusColors {
    val FocusRing = Color(0xFFFFD600)
    val FocusOutline = Color(0xFF000000)
    val SelectedRing = Color.White
}
