package com.softland.callqtv.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.focus.FocusRequester
import kotlinx.coroutines.delay

/** Requests TV focus after a short delay once [enabled] is true (dialog/tab open). */
@Composable
fun TvDelayedFocusEffect(
    enabled: Boolean,
    focusRequester: FocusRequester,
    delayMillis: Long = 100L,
) {
    LaunchedEffect(enabled) {
        if (!enabled) return@LaunchedEffect
        delay(delayMillis)
        try {
            focusRequester.requestFocus()
        } catch (_: IllegalStateException) {
        }
    }
}
