package com.softland.callqtv.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.softland.callqtv.ui.theme.CallQtvDimens
import com.softland.callqtv.ui.theme.CallQtvPickerFocusColors
import com.softland.callqtv.utils.ThemeColorManager
import com.softland.callqtv.utils.ThemeOption
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * TV-friendly color swatch: [clickable] + [focusable] on one node so D-pad focus updates reliably.
 */
@Composable
private fun PresetColorSwatchTile(
    brush: Brush,
    isSelected: Boolean,
    isFocused: Boolean,
    selectedBorderWidth: Dp,
    focusRequester: FocusRequester?,
    onClick: () -> Unit,
    onFocused: () -> Unit,
) {
    val focus = CallQtvPickerFocusColors
    val interactionSource = remember { MutableInteractionSource() }
    val interactionFocused by interactionSource.collectIsFocusedAsState()
    val showFocus = isFocused || interactionFocused
    val shape = RoundedCornerShape(8.dp)

    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .padding(2.dp)
            .then(focusRequester?.let { Modifier.focusRequester(it) } ?: Modifier)
            .onFocusChanged { state ->
                if (state.isFocused) onFocused()
            }
            .focusable(interactionSource = interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .scale(if (showFocus) 1.16f else 1f)
            .then(
                if (showFocus) {
                    Modifier.border(3.dp, focus.FocusOutline, RoundedCornerShape(10.dp))
                } else {
                    Modifier
                },
            )
            .border(
                width = when {
                    showFocus -> 5.dp
                    isSelected -> selectedBorderWidth
                    else -> 1.dp
                },
                color = when {
                    showFocus -> focus.FocusRing
                    isSelected -> focus.SelectedRing
                    else -> Color.Gray.copy(alpha = 0.55f)
                },
                shape = if (showFocus) RoundedCornerShape(10.dp) else shape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(if (showFocus) 2.dp else 0.dp)
                .clip(shape)
                .background(brush),
        )
        if (showFocus) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(3.dp)
                    .border(2.dp, Color.White.copy(alpha = 0.85f), shape),
            )
        }
        if (isSelected && !showFocus) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(10.dp)
                    .background(focus.SelectedRing, CircleShape)
                    .border(1.dp, focus.FocusOutline, CircleShape),
            )
        }
    }
}

@Composable
/**
 * Color selection dialog used for theme/counter/token background preferences.
 *
 * Pre-warms gradient brushes off the main thread to avoid jank on TV devices and keeps D-pad
 * focus anchored to the selected swatch for predictable navigation.
 */
fun PresetColorDialog(
    title: String,
    options: List<ThemeOption>,
    selectedHex: String,
    onColorSelected: (String) -> Unit,
    onDismiss: () -> Unit,
    selectedBorderWidth: Dp = 3.dp,
) {
    val gridState = rememberLazyGridState()
    val selectedIndex = remember(options, selectedHex) {
        val trimmed = selectedHex.trim()
        val idx = options.indexOfFirst { it.hexCode.trim() == trimmed }
        if (idx >= 0) idx else 0
    }
    val selectedFocusRequester = remember { FocusRequester() }
    var focusedIndex by remember { mutableIntStateOf(-1) }

    var brushesReady by remember(options) { mutableStateOf(false) }
    LaunchedEffect(options) {
        brushesReady = false
        val initialWarmCount = minOf(options.size, 35)
        withContext(Dispatchers.Default) {
            for (i in 0 until initialWarmCount) {
                ThemeColorManager.getBackgroundBrush(options[i].hexCode)
                if (i % 7 == 6) kotlinx.coroutines.yield()
            }
        }
        brushesReady = true
        if (initialWarmCount < options.size) {
            withContext(Dispatchers.Default) {
                for (i in initialWarmCount until options.size) {
                    ThemeColorManager.getBackgroundBrush(options[i].hexCode)
                    if (i % 7 == 6) {
                        kotlinx.coroutines.yield()
                        delay(1)
                    }
                }
            }
        }
    }

    TvPickerFocusEffect(
        gridState = gridState,
        selectedIndex = selectedIndex,
        focusRequester = selectedFocusRequester,
        onFocusedIndex = { focusedIndex = it },
        enabled = brushesReady,
    )

    AlertDialog(
        modifier = Modifier.fillMaxWidth(0.98f),
        onDismissRequest = onDismiss,
        title = { Text(title, style = MaterialTheme.typography.titleSmall) },
        text = {
            if (!brushesReady) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 200.dp, max = CallQtvDimens.PresetColorGridMaxHeight),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(36.dp))
                }
            } else {
                LazyVerticalGrid(
                    state = gridState,
                    columns = GridCells.Fixed(CallQtvDimens.PresetColorGridColumns),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    modifier = Modifier
                        .heightIn(max = CallQtvDimens.PresetColorGridMaxHeight)
                        .focusGroup(),
                ) {
                    itemsIndexed(
                        items = options,
                        key = { index, option -> "${option.name}_$index" },
                    ) { index, option ->
                        val swatchBrush = remember(option.hexCode) {
                            ThemeColorManager.getBackgroundBrush(option.hexCode)
                        }
                        val isSelected = index == selectedIndex
                        val isFocused = index == focusedIndex
                        PresetColorSwatchTile(
                            brush = swatchBrush,
                            isSelected = isSelected,
                            isFocused = isFocused,
                            selectedBorderWidth = selectedBorderWidth,
                            focusRequester = if (isSelected) selectedFocusRequester else null,
                            onClick = { onColorSelected(option.hexCode) },
                            onFocused = { focusedIndex = index },
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        },
    )
}
