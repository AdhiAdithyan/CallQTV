package com.softland.callqtv.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.focusable
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import kotlinx.coroutines.delay
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.softland.callqtv.data.local.TvConfigEntity
import com.softland.callqtv.ui.theme.CallQtvDimens
import com.softland.callqtv.ui.theme.CallQtvPickerFocusColors
import com.softland.callqtv.ui.theme.CallQtvSettingsColors
import com.softland.callqtv.R
import com.softland.callqtv.utils.LicenseDateUtils
import com.softland.callqtv.utils.ThemeColorManager

/**
 * Equal-width tabs aligned with dialog content (place in [AlertDialog] `text`, not `title`).
 * Each tab is one column: centered label + underline drawn on that column only.
 */
@Composable
fun SettingsDialogTabRow(
    tabs: List<String>,
    selectedTabIndex: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    requestInitialFocus: Boolean = true,
    externalFocusRequester: FocusRequester? = null,
) {
    val palette = CallQtvSettingsColors
    val focusRing = CallQtvPickerFocusColors.FocusRing
    if (tabs.isEmpty()) return
    val safeIndex = selectedTabIndex.coerceIn(0, tabs.lastIndex)
    val titleSize = if (tabs.size >= 5) {
        CallQtvDimens.SettingsTabTitleCompactSize
    } else {
        CallQtvDimens.SettingsTabTitleSize
    }
    val indicatorStroke = 3.dp
    val tabFocusRequesters = remember(tabs.size) { List(tabs.size) { FocusRequester() } }
    LaunchedEffect(requestInitialFocus, safeIndex, tabs.size, externalFocusRequester) {
        if (!requestInitialFocus) return@LaunchedEffect
        delay(100L)
        val target = externalFocusRequester ?: tabFocusRequesters[safeIndex]
        try {
            target.requestFocus()
        } catch (_: IllegalStateException) {
        }
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(CallQtvDimens.SettingsTabRowHeight),
        verticalAlignment = Alignment.Bottom,
    ) {
        tabs.forEachIndexed { index, title ->
            val selected = index == safeIndex
            val interactionSource = remember(index) { MutableInteractionSource() }
            val isFocused by interactionSource.collectIsFocusedAsState()
            val tabFocusRequester = when {
                index == safeIndex && externalFocusRequester != null -> externalFocusRequester
                else -> tabFocusRequesters[index]
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(horizontal = CallQtvDimens.SettingsTabHorizontalPadding)
                    .focusRequester(tabFocusRequester)
                    .onFocusChanged { state ->
                        if (state.isFocused) onTabSelected(index)
                    }
                    .focusable(interactionSource = interactionSource)
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        role = Role.Tab,
                        onClick = { onTabSelected(index) },
                    )
                    .then(
                        if (isFocused) {
                            Modifier.border(2.dp, focusRing, RoundedCornerShape(4.dp))
                        } else {
                            Modifier
                        },
                    )
                    .drawBehind {
                        if (!selected) return@drawBehind
                        val strokePx = indicatorStroke.toPx()
                        val y = size.height - strokePx / 2f
                        drawLine(
                            color = palette.Primary,
                            start = Offset(0f, y),
                            end = Offset(size.width, y),
                            strokeWidth = strokePx,
                        )
                    },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = title,
                    fontSize = titleSize,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    color = if (selected) palette.Primary else palette.MutedText,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Clip,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
/** Renders the settings launcher icon button with optional size/tint overrides. */
fun SettingsIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 28.dp,
    tint: Color = MaterialTheme.colorScheme.primary,
    contentDescription: String = "Settings",
) {
    IconButton(onClick = onClick, modifier = modifier) {
        Icon(
            painter = painterResource(id = R.drawable.ic_settings),
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(size),
        )
    }
}

@Composable
/** Displays a single label/value row used by the System information section. */
fun InfoRow(
    label: String,
    value: String,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            label,
            fontSize = 12.sp,
            lineHeight = 14.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(CallQtvDimens.InfoRowLabelWidth),
        )
        Text(
            value,
            fontSize = 12.sp,
            lineHeight = 14.sp,
            color = valueColor,
            modifier = Modifier
                .weight(1f)
                .padding(start = 8.dp),
        )
    }
}

private const val LICENSE_EXPIRY_WARNING_DAYS = 10

@Composable
/**
 * Shows license metadata rows in the System tab.
 *
 * Always displays "License valid till" when available, and shows "Days remaining" only
 * for expired/today/near-expiry states (<= [LICENSE_EXPIRY_WARNING_DAYS]).
 */
fun SystemLicenseInfoRows(
    licenseEndRaw: String?,
    daysUntilExpiry: Int?,
) {
    val palette = CallQtvSettingsColors
    val validTill = LicenseDateUtils.formatLicenseEndDateForDisplay(licenseEndRaw)
    val days = daysUntilExpiry ?: LicenseDateUtils.daysUntilExpiry(licenseEndRaw)

    if (!validTill.isNullOrBlank()) {
        InfoRow("License valid till", validTill)
    }

    when (days) {
        null -> {
            if (validTill.isNullOrBlank()) {
                InfoRow("License", "Not available", palette.MutedText)
            }
        }
        in Int.MIN_VALUE..-1 -> {
            InfoRow("Days remaining", "Expired", palette.Error)
            LicenseExpiryWarningText("License has expired. Renew to restore queue display and announcements.")
        }
        0 -> {
            InfoRow("Days remaining", "Expires today", palette.Error)
            LicenseExpiryWarningText("License expires today. Please renew immediately.")
        }
        in 1..LICENSE_EXPIRY_WARNING_DAYS -> {
            val dayLabel = if (days == 1) "1 day" else "$days days"
            InfoRow("Days remaining", dayLabel, palette.Error)
            LicenseExpiryWarningText(
                "License expires in $LICENSE_EXPIRY_WARNING_DAYS days or less ($dayLabel left). Please renew soon.",
            )
        }
        else -> Unit
    }
}

@Composable
/** Renders red warning copy under license rows for urgent expiry states. */
private fun LicenseExpiryWarningText(message: String) {
    Text(
        text = message,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        color = CallQtvSettingsColors.Error,
        modifier = Modifier.padding(top = 4.dp, bottom = 2.dp),
    )
}

@Composable
/** Focusable pill-like button that opens a color picker for a themed setting. */
fun ColorPickerButton(
    label: String,
    hex: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = CallQtvSettingsColors
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(2.dp, palette.Border),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .background(ThemeColorManager.getBackgroundBrush(hex), RoundedCornerShape(4.dp))
                    .border(2.dp, Color.Gray, RoundedCornerShape(4.dp)),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(label, fontSize = CallQtvDimens.SettingsTabLabelSize, color = palette.Text)
        }
    }
}

@Composable
/**
 * Reusable focusable settings tile with optional click behavior and custom content body.
 *
 * Used across Display/Audios/Other/Portal/Help grids to keep visual and focus behavior consistent.
 */
fun GridSettingsItem(
    title: String,
    onClick: (() -> Unit)? = null,
    titleColor: Color = CallQtvSettingsColors.Primary,
    cardColor: Color = CallQtvSettingsColors.Card,
    borderColor: Color = CallQtvSettingsColors.Border,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val focusRing = CallQtvPickerFocusColors.FocusRing
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    Card(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .focusable(interactionSource = interactionSource)
            .then(
                if (isFocused) Modifier.border(2.dp, focusRing, RoundedCornerShape(8.dp))
                else Modifier,
            ),
        colors = CardDefaults.cardColors(containerColor = cardColor.copy(alpha = 0.45f)),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, borderColor.copy(alpha = 0.8f)),
    ) {
        Column(
            modifier = Modifier
                .padding(CallQtvDimens.SettingsCardPadding)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                title,
                fontSize = CallQtvDimens.SettingsTabLabelSize,
                fontWeight = FontWeight.Bold,
                color = titleColor,
                modifier = Modifier.fillMaxWidth(),
            )
            content()
        }
    }
}

/** One read-only portal setting shown as a grid tile in the Portal settings tab. */
data class PortalConfigItem(
    val label: String,
    val value: String,
    val colorHex: String? = null,
)

/** Normalizes optional color strings into `#RRGGBB` format; returns null for invalid values. */
private fun String?.toPortalColorHexOrNull(): String? {
    val raw = this?.trim()?.ifBlank { null } ?: return null
    val normalized = if (raw.startsWith("#")) raw else "#$raw"
    return normalized.takeIf { it.matches(Regex("^#[0-9A-Fa-f]{6}$")) }
}

/** Builds all portal tiles: announcements (when provided) plus server [TvConfigEntity] fields. */
fun buildPortalConfigItems(
    config: TvConfigEntity,
    isTokenAnnouncementEnabled: Boolean? = null,
    isCounterAnnouncementEnabled: Boolean? = null,
    isCounterPrefixEnabled: Boolean? = null,
): List<PortalConfigItem> {
    val items = mutableListOf<PortalConfigItem>()
    if (isTokenAnnouncementEnabled != null) {
        items += PortalConfigItem(
            label = "Token Announcement",
            value = if (isTokenAnnouncementEnabled) "Enabled" else "Disabled",
        )
    }
    if (isCounterAnnouncementEnabled != null) {
        items += PortalConfigItem(
            label = "Counter Announcement",
            value = if (isCounterAnnouncementEnabled) "Enabled" else "Disabled",
        )
    }
    if (isCounterPrefixEnabled != null) {
        items += PortalConfigItem(
            label = "Show Counter Prefix",
            value = if (isCounterPrefixEnabled) "Enabled" else "Disabled",
        )
    }

    val rows = config.displayRows ?: 0
    val cols = config.displayColumns ?: 0
    val grid = if (rows > 0 && cols > 0) "${rows} × $cols" else "—"
    val portalRows = listOf(
        "Company" to config.companyName.ifBlank { "—" },
        "Layout type" to (config.layoutType?.trim()?.ifBlank { null } ?: "—"),
        "Orientation" to (config.orientation?.trim()?.ifBlank { null } ?: "—"),
        "Token grid" to grid,
        "Counters (portal)" to (config.noOfCounters?.toString() ?: "—"),
        "Tokens per counter" to (config.tokensPerCounter?.toString() ?: "—"),
        "Ads" to when {
            config.showAds.equals("on", ignoreCase = true) ->
                "On (${config.adPlacement?.trim()?.ifBlank { "default" } ?: "default"})"
            config.showAds.equals("off", ignoreCase = true) -> "Off"
            else -> config.showAds?.trim()?.ifBlank { "—" } ?: "—"
        },
        "Scroll footer" to when {
            config.scrollEnabled.equals("on", ignoreCase = true) -> "On"
            config.scrollEnabled.equals("off", ignoreCase = true) -> "Off"
            else -> config.scrollEnabled?.trim()?.ifBlank { "—" } ?: "—"
        },
        "Audio language" to (config.audioLanguage?.trim()?.ifBlank { null } ?: "—"),
        "Counter text color" to (config.counterTextColor?.trim()?.ifBlank { null } ?: "—"),
        "Current token color" to (config.currentTokenColor?.trim()?.ifBlank { null } ?: "—"),
        "Token format" to (config.tokenFormat?.trim()?.ifBlank { null } ?: "—"),
    )
    portalRows.forEach { (label, value) ->
        val displayValue = value ?: "—"
        items += PortalConfigItem(
            label = label,
            value = displayValue,
            colorHex = when (label) {
                "Counter text color" -> config.counterTextColor.toPortalColorHexOrNull()
                "Current token color" -> config.currentTokenColor.toPortalColorHexOrNull()
                else -> null
            },
        )
    }
    return items
}

@Composable
/**
 * Displays portal-derived read-only settings in a two-column grid.
 *
 * Accepts an optional [firstItemFocusRequester] so TV remote users land on content first.
 */
fun PortalConfigurationGrid(
    items: List<PortalConfigItem>,
    modifier: Modifier = Modifier,
    firstItemFocusRequester: FocusRequester? = null,
) {
    val palette = CallQtvSettingsColors
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "Read-only values from the Call-Q portal. Display tab colors apply only on this device.",
            fontSize = 11.sp,
            color = palette.MutedText,
            modifier = Modifier.padding(bottom = 6.dp),
        )
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 220.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(CallQtvDimens.SettingsTabContentHeight),
            horizontalArrangement = Arrangement.spacedBy(CallQtvDimens.SettingsGridSpacing),
            verticalArrangement = Arrangement.spacedBy(CallQtvDimens.SettingsGridSpacing),
            contentPadding = PaddingValues(top = 2.dp, bottom = 4.dp),
        ) {
            itemsIndexed(items, key = { _, item -> item.label }) { index, item ->
                PortalConfigTile(
                    item = item,
                    modifier = if (index == 0 && firstItemFocusRequester != null) {
                        Modifier.focusRequester(firstItemFocusRequester)
                    } else {
                        Modifier
                    },
                )
            }
        }
    }
}

@Composable
/** Renders a single portal configuration tile, including optional color swatch preview. */
private fun PortalConfigTile(
    item: PortalConfigItem,
    modifier: Modifier = Modifier,
) {
    val palette = CallQtvSettingsColors
    GridSettingsItem(
        title = item.label,
        titleColor = palette.Primary,
        cardColor = palette.Card,
        borderColor = palette.Border,
        onClick = null,
        modifier = modifier,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item.colorHex?.let { hex ->
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .background(ThemeColorManager.getBackgroundBrush(hex), RoundedCornerShape(4.dp))
                        .border(1.dp, palette.Border, RoundedCornerShape(4.dp)),
                )
            }
            Text(
                text = item.value,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = palette.Text,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
        }
    }
}
