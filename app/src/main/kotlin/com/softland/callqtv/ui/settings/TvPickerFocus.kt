package com.softland.callqtv.ui.settings

import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.focus.FocusRequester
import kotlinx.coroutines.delay

/** Scrolls a TV picker grid to [selectedIndex] and requests focus (D-pad friendly). */
@Composable
fun TvPickerFocusEffect(
    gridState: LazyGridState,
    selectedIndex: Int,
    focusRequester: FocusRequester,
    onFocusedIndex: (Int) -> Unit,
    enabled: Boolean = true,
) {
    LaunchedEffect(enabled, selectedIndex) {
        if (!enabled) return@LaunchedEffect
        delay(150)
        gridState.animateScrollToItem(selectedIndex)
        delay(80)
        onFocusedIndex(selectedIndex)
        try {
            focusRequester.requestFocus()
        } catch (_: IllegalStateException) {
        }
    }
}
