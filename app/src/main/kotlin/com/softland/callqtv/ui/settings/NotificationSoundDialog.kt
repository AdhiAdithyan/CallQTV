package com.softland.callqtv.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.softland.callqtv.utils.playTokenChime
import com.softland.callqtv.ui.theme.CallQtvPickerFocusColors
import com.softland.callqtv.utils.ThemeColorManager
import kotlinx.coroutines.launch

@Composable
/**
 * TV-optimized sound picker dialog for token notification chimes.
 *
 * Highlights the selected sound, supports D-pad navigation/focus, and plays an immediate
 * preview sample when the user selects an option.
 */
fun NotificationSoundDialog(
    title: String,
    selectedKey: String,
    onSoundSelected: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val soundOptions = ThemeColorManager.notificationSoundOptions
    val scope = rememberCoroutineScope()
    val gridState = rememberLazyGridState()
    val focusRing = CallQtvPickerFocusColors.FocusRing
    val selectedIndex = remember(soundOptions, selectedKey) {
        soundOptions.indexOfFirst { it.first == selectedKey }.let { if (it >= 0) it else 0 }
    }
    val selectedFocusRequester = remember { FocusRequester() }
    var focusedIndex by remember { mutableIntStateOf(-1) }

    TvPickerFocusEffect(
        gridState = gridState,
        selectedIndex = selectedIndex,
        focusRequester = selectedFocusRequester,
        onFocusedIndex = { focusedIndex = it },
    )

    AlertDialog(
        modifier = Modifier.fillMaxWidth(0.98f),
        onDismissRequest = onDismiss,
        title = { Text(title, style = MaterialTheme.typography.titleSmall) },
        text = {
            LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .heightIn(max = 420.dp)
                    .focusGroup(),
            ) {
                itemsIndexed(soundOptions, key = { index, item -> "${item.first}_$index" }) { index, (key, label) ->
                    val isSelected = index == selectedIndex
                    val isFocused = index == focusedIndex
                    val interactionSource = remember { MutableInteractionSource() }
                    val interactionFocused by interactionSource.collectIsFocusedAsState()
                    val showFocus = isFocused || interactionFocused
                    OutlinedButton(
                        onClick = {
                            onSoundSelected(key)
                            scope.launch { playTokenChime(context, key) }
                        },
                        interactionSource = interactionSource,
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(
                                if (isSelected) Modifier.focusRequester(selectedFocusRequester) else Modifier,
                            )
                            .onFocusChanged { state ->
                                if (state.isFocused) focusedIndex = index
                            }
                            .scale(if (showFocus) 1.06f else 1f),
                        border = when {
                            showFocus -> BorderStroke(5.dp, focusRing)
                            isSelected -> BorderStroke(3.dp, MaterialTheme.colorScheme.primary)
                            else -> BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                        },
                    ) {
                        Text(
                            label,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            color = if (showFocus) focusRing else MaterialTheme.colorScheme.onSurface,
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
