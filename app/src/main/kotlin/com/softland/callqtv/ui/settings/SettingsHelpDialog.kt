package com.softland.callqtv.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.softland.callqtv.ui.theme.CallQtvDimens
import com.softland.callqtv.ui.theme.CallQtvPickerFocusColors
import com.softland.callqtv.ui.theme.CallQtvSettingsColors
import kotlinx.coroutines.delay

/** One help topic shown as a tile in the settings guide grid. */
data class SettingsHelpItem(
    val title: String,
    val description: String,
)

/** Builds help content grouped by settings tab for the Help dialog. */
private fun settingsHelpSections(): List<Pair<String, List<SettingsHelpItem>>> = listOf(
    "Display" to listOf(
        SettingsHelpItem(
            "App Theme",
            "Changes the overall app accent and header colors on this TV.",
        ),
        SettingsHelpItem(
            "Counter BG",
            "Background color behind counter name labels (local override).",
        ),
        SettingsHelpItem(
            "Token BG",
            "Background color behind token number cells (local override).",
        ),
        SettingsHelpItem(
            "Token blink",
            "When the portal enables blinking on the current token, choose whole-tile flash or text-only pulse.",
        ),
    ),
    "Audios" to listOf(
        SettingsHelpItem(
            "Notification Sound",
            "Sound played when a new token is called or announced.",
        ),
        SettingsHelpItem(
            "Advertisement Sound",
            "Turns audio on or off for advertisement videos in the ad area.",
        ),
    ),
    "Other" to listOf(
        SettingsHelpItem(
            "Help / Settings Guide",
            "Opens this guide with tabbed, grid-layout explanations for every setting.",
        ),
        SettingsHelpItem(
            "24-Hour Format",
            "Shows the clock as 24-hour (13:00) instead of 12-hour (1:00 PM).",
        ),
        SettingsHelpItem(
            "Offline Advertisements",
            "Plays ads from files stored on the device when the network is slow or unavailable.",
        ),
        SettingsHelpItem(
            "Allow YouTube Ads",
            "Includes YouTube URLs in the ad playlist; disable to skip YouTube items.",
        ),
        SettingsHelpItem(
            "YouTube Strict Autoplay",
            "Uses muted autoplay for YouTube ads to improve reliability on TV devices.",
        ),
        SettingsHelpItem(
            "YouTube: Play Until Ended",
            "Waits for the video to finish naturally before advancing to the next ad.",
        ),
        SettingsHelpItem(
            "Clear saved token history",
            "Clears locally stored token/call history and reloads configuration from the server.",
        ),
    ),
    "Portal" to portalSettingsHelpItems(),
    "System" to listOf(
        SettingsHelpItem(
            "Company ID",
            "Your registered Call-Q customer identifier for this installation.",
        ),
        SettingsHelpItem(
            "Device ID",
            "MAC address used to register and identify this TV on the network.",
        ),
        SettingsHelpItem(
            "App Version",
            "Installed Call-Q TV application version.",
        ),
        SettingsHelpItem(
            "License",
            "Product license status and days remaining before expiry.",
        ),
        SettingsHelpItem(
            "Export Logs/Config Snapshot",
            "Saves diagnostics, logs, and configuration to storage for support troubleshooting.",
        ),
    ),
)

/** Portal tab help topics (matches Portal settings tiles). */
fun portalSettingsHelpItems(): List<SettingsHelpItem> = listOf(
    SettingsHelpItem(
        "Token Announcement",
        "Speaks each newly called token number when enabled in the portal.",
    ),
    SettingsHelpItem(
        "Counter Announcement",
        "Speaks the counter or department name when a token is called.",
    ),
    SettingsHelpItem(
        "Show Counter Prefix",
        "Includes the counter prefix in spoken announcements when enabled.",
    ),
    SettingsHelpItem(
        "Company",
        "Hospital or organization name from the Call-Q portal.",
    ),
    SettingsHelpItem(
        "Layout type",
        "How counters and ads are arranged on the TV (e.g. full, split).",
    ),
    SettingsHelpItem(
        "Orientation",
        "Screen orientation assigned in the portal (landscape or portrait).",
    ),
    SettingsHelpItem(
        "Token grid",
        "Rows and columns for token slots on the display.",
    ),
    SettingsHelpItem(
        "Counters (portal)",
        "Number of service counters configured on the portal.",
    ),
    SettingsHelpItem(
        "Tokens per counter",
        "How many token numbers are shown per counter column.",
    ),
    SettingsHelpItem(
        "Ads",
        "Whether advertisements are enabled and where they appear.",
    ),
    SettingsHelpItem(
        "Scroll footer",
        "Bottom marquee text configured in the portal.",
    ),
    SettingsHelpItem(
        "Audio language",
        "Language used for token and counter announcements.",
    ),
    SettingsHelpItem(
        "Counter text color",
        "Portal-assigned counter label color; Display tab can override on this device.",
    ),
    SettingsHelpItem(
        "Current token color",
        "Portal-assigned active token color; Display tab can override on this device.",
    ),
    SettingsHelpItem(
        "Token format",
        "Token numbering style from the portal (e.g. T1, T2).",
    ),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
/**
 * Tabbed help dialog that explains each settings option.
 *
 * On TV devices, focus is intentionally directed to the first tile of the active tab so users
 * can read and navigate details immediately via D-pad.
 */
fun SettingsHelpDialog(
    onDismiss: () -> Unit,
    focusRequester: FocusRequester,
) {
    val palette = CallQtvSettingsColors
    val sections = remember { settingsHelpSections() }
    val tabs = remember(sections) { sections.map { it.first } }
    val firstItemFocusRequesters = remember(tabs.size) { List(tabs.size) { FocusRequester() } }
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val safeTabIndex = selectedTabIndex.coerceIn(0, (tabs.size - 1).coerceAtLeast(0))
    val currentItems = sections.getOrNull(safeTabIndex)?.second.orEmpty()
    LaunchedEffect(Unit) {
        delay(120L)
        try {
            firstItemFocusRequesters[safeTabIndex].requestFocus()
        } catch (_: IllegalStateException) {
        }
    }

    AlertDialog(
        modifier = Modifier.fillMaxWidth(CallQtvDimens.SettingsHelpDialogWidthFraction),
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        title = {
            Text("Settings Help", style = MaterialTheme.typography.titleLarge)
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                SettingsDialogTabRow(
                    tabs = tabs,
                    selectedTabIndex = safeTabIndex,
                    onTabSelected = { selectedTabIndex = it },
                    requestInitialFocus = false,
                    externalFocusRequester = focusRequester,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Tap a tab to see settings for that section. Each tile explains one option.",
                    fontSize = 11.sp,
                    color = palette.MutedText,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(CallQtvDimens.SettingsTabContentHeight),
                ) {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 220.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(CallQtvDimens.SettingsTabContentHeight),
                        horizontalArrangement = Arrangement.spacedBy(CallQtvDimens.SettingsGridSpacing),
                        verticalArrangement = Arrangement.spacedBy(CallQtvDimens.SettingsGridSpacing),
                        contentPadding = PaddingValues(top = 2.dp, bottom = 4.dp),
                    ) {
                        itemsIndexed(currentItems, key = { _, it -> "${safeTabIndex}:${it.title}" }) { index, item ->
                            SettingsHelpTile(
                                item = item,
                                modifier = if (index == 0) {
                                    Modifier.focusRequester(firstItemFocusRequesters[safeTabIndex])
                                } else {
                                    Modifier
                                },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        },
    )
}

@Composable
/** Focusable help tile containing a setting name and a short usage description. */
private fun SettingsHelpTile(
    item: SettingsHelpItem,
    modifier: Modifier = Modifier,
) {
    val palette = CallQtvSettingsColors
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    Card(
        colors = CardDefaults.cardColors(containerColor = palette.Card.copy(alpha = 0.45f)),
        border = BorderStroke(1.dp, palette.Border.copy(alpha = 0.8f)),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
        modifier = modifier
            .fillMaxWidth()
            .focusable(interactionSource = interactionSource)
            .then(
                if (isFocused) {
                    Modifier.border(
                        2.dp,
                        CallQtvPickerFocusColors.FocusRing,
                        androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                    )
                } else {
                    Modifier
                },
            ),
    ) {
        Column(
            modifier = Modifier
                .padding(CallQtvDimens.SettingsCardPadding)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = item.title,
                fontSize = CallQtvDimens.SettingsTabLabelSize,
                fontWeight = FontWeight.Bold,
                color = palette.Primary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = item.description,
                fontSize = 14.sp,
                lineHeight = 18.sp,
                color = palette.Text,
            )
        }
    }
}
