@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])

package com.softland.callqtv.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.softland.callqtv.data.local.CounterEntity
import com.softland.callqtv.data.local.TvConfigEntity
import com.softland.callqtv.ui.theme.parseColorOrDefault
import com.softland.callqtv.utils.ThemeColorManager
import com.softland.callqtv.utils.TokenBlinkMode
import kotlinx.coroutines.delay

@Composable
/**
 * Renders the entire token counter area as one or two grids/sections.
 *
 * The layout is driven by `config.layoutType`, `isPortrait`, and `rows/columns`, with optional
 * support for ad-related UI spacing and VIP/emergency token prefix overlay.
 */
fun CountersArea(
    counters: List<CounterEntity>,
    tokensPerCounter: Map<String, List<String>>,
    config: TvConfigEntity,
    rows: Int,
    columns: Int,
    layoutType: String,
    scale: Float,
    counterBgHex: String,
    tokenBgHex: String,
    isPortrait: Boolean = false,
    hasAds: Boolean = false,
    blinkTriggers: Map<String, Long> = emptyMap(),
    tokenBlinkMode: TokenBlinkMode = TokenBlinkMode.WHOLE_TILE,
    vipEmergencyTokensByKey: Map<String, Set<String>> = emptyMap(),
) {
    Box(modifier = Modifier.fillMaxSize().padding(1.dp)) {
        val numCounters = counters.size

        // Use layoutType to influence splitting. 
        // type "1" (default) = automated split
        // type "2" = force no split (single grid/column)
        // type "3" = force split even for few counters
        val splitThreshold = when (layoutType.trim().lowercase()) {
            "2", "full" -> 999
            "3" -> 0
            else -> if (isPortrait) 4 else 2
        }

        if (numCounters > splitThreshold && numCounters > 1) {
            val firstHalfCount = (numCounters + 1) / 2
            val firstHalf = counters.take(firstHalfCount)
            val secondHalf = counters.drop(firstHalfCount)

            if (isPortrait) {
                // Portrait: split horizontally (top/bottom)
                Column(modifier = Modifier.fillMaxSize().padding(1.dp), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(1.dp)) {
                        firstHalf.forEach { counter ->
                            val sk = counterStorageLookupKey(counter)
                            val tokens = remember(tokensPerCounter, counter) { getTokensForCounter(counter, tokensPerCounter) }
                            val vipTokens = vipEmergencyTokensByKey[sk].orEmpty()
                            CounterBoard(counter, tokens, config, rows, columns, Modifier.weight(1f).fillMaxHeight(), scale, counterBgHex, tokenBgHex, isPortrait, hasAds, blinkTriggers[sk] ?: 0L, tokenBlinkMode = tokenBlinkMode, layoutType = layoutType, vipEmergencyRawTokens = vipTokens)
                        }
                    }
                    Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(1.dp)) {
                        secondHalf.forEach { counter ->
                            val sk = counterStorageLookupKey(counter)
                            val tokens = remember(tokensPerCounter, counter) { getTokensForCounter(counter, tokensPerCounter) }
                            val vipTokens = vipEmergencyTokensByKey[sk].orEmpty()
                            CounterBoard(counter, tokens, config, rows, columns, Modifier.weight(1f).fillMaxHeight(), scale, counterBgHex, tokenBgHex, isPortrait, hasAds, blinkTriggers[sk] ?: 0L, tokenBlinkMode = tokenBlinkMode, layoutType = layoutType, vipEmergencyRawTokens = vipTokens)
                        }
                        if (secondHalf.size < firstHalf.size) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            } else {
                // Landscape: split vertically (left/right)
                Row(modifier = Modifier.fillMaxSize().padding(1.dp), horizontalArrangement = Arrangement.spacedBy(1.dp)) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        firstHalf.forEach { counter ->
                            val sk = counterStorageLookupKey(counter)
                            val tokens = remember(tokensPerCounter, counter) { getTokensForCounter(counter, tokensPerCounter) }
                            val vipTokens = vipEmergencyTokensByKey[sk].orEmpty()
                            CounterBoard(counter, tokens, config, rows, columns, Modifier.weight(1f).fillMaxWidth(), scale, counterBgHex, tokenBgHex, isPortrait, hasAds, blinkTriggers[sk] ?: 0L, tokenBlinkMode = tokenBlinkMode, layoutType = layoutType, vipEmergencyRawTokens = vipTokens)
                        }
                    }
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        secondHalf.forEach { counter ->
                            val sk = counterStorageLookupKey(counter)
                            val tokens = remember(tokensPerCounter, counter) { getTokensForCounter(counter, tokensPerCounter) }
                            val vipTokens = vipEmergencyTokensByKey[sk].orEmpty()
                            CounterBoard(counter, tokens, config, rows, columns, Modifier.weight(1f).fillMaxWidth(), scale, counterBgHex, tokenBgHex, isPortrait, hasAds, blinkTriggers[sk] ?: 0L, tokenBlinkMode = tokenBlinkMode, layoutType = layoutType, vipEmergencyRawTokens = vipTokens)
                        }
                        if (secondHalf.size < firstHalf.size) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        } else {
            if (isPortrait) {
                // Portrait: single vertical stack of counters
                Column(modifier = Modifier.fillMaxSize().padding(1.dp), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    counters.forEach { counter ->
                        val sk = counterStorageLookupKey(counter)
                        val tokens = remember(tokensPerCounter, counter) { getTokensForCounter(counter, tokensPerCounter) }
                        val vipTokens = vipEmergencyTokensByKey[sk].orEmpty()
                        CounterBoard(counter, tokens, config, rows, columns, Modifier.weight(1f).fillMaxWidth(), scale, counterBgHex, tokenBgHex, isPortrait, hasAds, blinkTriggers[sk] ?: 0L, tokenBlinkMode = tokenBlinkMode, layoutType = layoutType, vipEmergencyRawTokens = vipTokens)
                    }
                }
            } else {
                // Landscape: single horizontal row of counters
                Row(modifier = Modifier.fillMaxSize().padding(1.dp), horizontalArrangement = Arrangement.spacedBy(1.dp)) {
                    counters.forEach { counter ->
                        val sk = counterStorageLookupKey(counter)
                        val tokens = remember(tokensPerCounter, counter) { getTokensForCounter(counter, tokensPerCounter) }
                        val vipTokens = vipEmergencyTokensByKey[sk].orEmpty()
                        CounterBoard(counter, tokens, config, rows, columns, Modifier.weight(1f).fillMaxHeight(), scale, counterBgHex, tokenBgHex, isPortrait, hasAds, blinkTriggers[sk] ?: 0L, tokenBlinkMode = tokenBlinkMode, layoutType = layoutType, vipEmergencyRawTokens = vipTokens)
                    }
                }
            }
        }
    }
}

/**
 * Fills the token area with a fixed grid: each cell gets equal width/height so no empty
 * space remains inside the counter token region (slots scale with [totalSlots] and [columns]).
 */
@Composable
private fun CounterTokenSlotsGrid(
    modifier: Modifier = Modifier,
    totalSlots: Int,
    columns: Int,
    tokens: List<String>,
    usePrefix: Boolean,
    counterCode: String,
    config: TvConfigEntity,
    scale: Float,
    tokenFontSize: Float,
    currentTokenTextColor: Color,
    previousTokenTextColor: Color,
    tokenBgBrush: Brush,
    shouldBlink: Boolean,
    isInverted: Boolean,
    tokenBlinkMode: TokenBlinkMode = TokenBlinkMode.WHOLE_TILE,
    vipEmergencyRawTokens: Set<String> = emptySet(),
) {
    val cols = columns.coerceAtLeast(1)
    if (totalSlots <= 0) {
        Box(modifier = modifier.fillMaxSize())
        return
    }
    val rowCount = (totalSlots + cols - 1) / cols
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(1.dp)
    ) {
        for (r in 0 until rowCount) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(1.dp)
            ) {
                for (c in 0 until cols) {
                    val index = r * cols + c
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    ) {
                        if (index < totalSlots) {
                            CounterTokenSlot(
                                index = index,
                                tokens = tokens,
                                usePrefix = usePrefix,
                                counterCode = counterCode,
                                config = config,
                                scale = scale,
                                tokenFontSize = tokenFontSize,
                                currentTokenTextColor = currentTokenTextColor,
                                previousTokenTextColor = previousTokenTextColor,
                                tokenBgBrush = tokenBgBrush,
                                shouldBlink = shouldBlink,
                                isInverted = isInverted,
                                tokenBlinkMode = tokenBlinkMode,
                                vipEmergencyRawTokens = vipEmergencyRawTokens,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
/** Renders a single token slot (cell) inside the counter grid. */
private fun CounterTokenSlot(
    index: Int,
    tokens: List<String>,
    usePrefix: Boolean,
    counterCode: String,
    config: TvConfigEntity,
    scale: Float,
    tokenFontSize: Float,
    currentTokenTextColor: Color,
    previousTokenTextColor: Color,
    tokenBgBrush: Brush,
    shouldBlink: Boolean,
    isInverted: Boolean,
    tokenBlinkMode: TokenBlinkMode = TokenBlinkMode.WHOLE_TILE,
    vipEmergencyRawTokens: Set<String> = emptySet(),
) {
    val token = tokens.getOrNull(index)
    val isFirst = index == 0
    val formattedToken = remember(token, config.tokenFormat) {
        formatTokenByPattern(token, config.tokenFormat)
    }
    val isVipEmergencyToken = tokenUsesVipEmergencyPrefix(token, vipEmergencyRawTokens)
    val prefixForSlot = when {
        isVipEmergencyToken -> VIP_EMERGENCY_COUNTER_PREFIX
        else -> counterCode
    }
    val displayToken = when {
        formattedToken == null -> null
        isSpecialMessageToken(formattedToken) -> decodeSpecialMessageToken(formattedToken)
        isVipEmergencyToken -> "$VIP_EMERGENCY_COUNTER_PREFIX-$formattedToken"
        usePrefix && prefixForSlot.isNotBlank() -> "$prefixForSlot-$formattedToken"
        else -> formattedToken
    }
    val textColorToUse = if (isFirst) currentTokenTextColor else previousTokenTextColor
    val isSpecialMsg = isSpecialMessageToken(token) || isSpecialMessageToken(formattedToken)
    TokenCard(
        token = displayToken,
        isPrimary = isFirst,
        scale = scale,
        textColor = textColorToUse,
        bgBrush = tokenBgBrush,
        fontSize = tokenFontSize,
        isInverted = if (isFirst && shouldBlink && token != null) isInverted else false,
        blinkMode = tokenBlinkMode,
        fullSize = true,
        multiline = isSpecialMsg,
        specialCounterMessage = isSpecialMsg,
    )
}

/** Counter header: centered when it fits; seamless horizontal marquee when the name is wider than the tile. */
@Composable
private fun CounterNameLabel(
    name: String,
    fontSize: androidx.compose.ui.unit.TextUnit,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val textColorInt = color.toArgb()
    val fontSizeSp = fontSize.value
    AndroidView(
        factory = { CounterNameTickerView(it) },
        update = { view -> view.bind(name, textColorInt, fontSizeSp) },
        modifier = modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .clipToBounds(),
    )
}

@Composable
/** Renders one counter board (title/code + token slot grid) including blink/VIP behaviors. */
fun CounterBoard(
    counter: CounterEntity,
    tokens: List<String>,
    config: TvConfigEntity,
    rows: Int,
    columns: Int,
    modifier: Modifier,
    scale: Float,
    counterBgHex: String,
    tokenBgHex: String,
    isPortrait: Boolean,
    hasAds: Boolean,
    blinkTrigger: Long = 0L,
    tokenBlinkMode: TokenBlinkMode = TokenBlinkMode.WHOLE_TILE,
    layoutType: String = "1",
    vipEmergencyRawTokens: Set<String> = emptySet(),
) {
    val counterName = remember(counter.name, counter.defaultName) { 
        (counter.name.orEmpty().ifBlank { counter.defaultName.orEmpty().ifBlank { "Counter" } }).uppercase()
    }
    

    // Counter code from CounterConfig - used to prefix token for display (e.g. "A-36")
    val counterCode = remember(counter.code, counter.defaultCode) {
        counter.code.orEmpty().trim().ifBlank { counter.defaultCode.orEmpty().trim() }
    }

    val counterColor = remember(config.counterTextColor) { 
        parseColorOrDefault(config.counterTextColor, Color.Black) 
    }
    val currentTokenTextColor = remember(config.currentTokenColor, config.tokenTextColor) { 
        parseColorOrDefault(config.currentTokenColor, parseColorOrDefault(config.tokenTextColor, Color.Black)) 
    }
    val previousTokenTextColor = remember(config.previousTokenColor, config.tokenTextColor) { 
        parseColorOrDefault(config.previousTokenColor, parseColorOrDefault(config.tokenTextColor, Color.Gray)) 
    }
    
    // Parse Custom BGs
    val counterBgBrush = remember(counterBgHex) { ThemeColorManager.getBackgroundBrush(counterBgHex) }
    val tokenBgBrush = remember(tokenBgHex) { ThemeColorManager.getBackgroundBrush(tokenBgHex) }

    val counterFontSize = (config.counterFontSize ?: config.fontSize ?: 20).toFloat()
    val tokenFontSize = (config.tokenFontSize ?: config.fontSize ?: 24).toFloat()
    val shouldBlink = config.blinkCurrentToken ?: false
    val blinkSeconds = config.blinkSeconds ?: 0

    // Blink only after a live token call (blinkTrigger); not when tokens are restored from DB on refresh.
    var blinkActive by remember { mutableStateOf(false) }
    LaunchedEffect(shouldBlink, blinkSeconds, blinkTrigger) {
        if (!shouldBlink || blinkTrigger == 0L) {
            blinkActive = false
            return@LaunchedEffect
        }
        blinkActive = true
        if (blinkSeconds > 0) {
            delay(blinkSeconds * 1000L)
            blinkActive = false
        }
    }

    val isInverted by if (shouldBlink && blinkActive) {
        val transition = rememberInfiniteTransition(label = "counter_invert")
        val step by transition.animateFloat(
            initialValue = 0f,
            targetValue = 2f,
            animationSpec = infiniteRepeatable(
                // Reduce inversion phase duration by 1/3 (1000ms -> ~667ms)
                animation = tween(667, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "invert_step"
        )
        // Step 0..1 = Inverted, 1..2 = Normal
        remember(step) { mutableStateOf(step < 1f) }
    } else {
        remember { mutableStateOf(false) }
    }

    Card(
        modifier = modifier.clip(RoundedCornerShape(12.dp)),
        elevation = CardDefaults.cardElevation(4.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Box(modifier = Modifier.fillMaxSize().background(counterBgBrush)) {
            val usePrefix = config.enableCounterPrefix != false
            val primaryRawToken = tokens.firstOrNull()
            val isSpecialMessage = isSpecialMessageToken(primaryRawToken)
            val specialMessage = decodeSpecialMessageToken(primaryRawToken)
            if (isPortrait) {
                Row(modifier = Modifier.fillMaxSize().padding(1.dp)) {
                    // If ads exist, use 30% for name and 70% for tokens.
                    // If no ads, keep name compact and give 80% to tokens.
                    val nameWeight = if (hasAds) 0.30f else 0.2f
                    val tokenWeight = if (hasAds) 0.70f else 0.8f

                    // Counter name area (same layout for normal tokens and __MSG__ / protocol C)
                    BoxWithConstraints(
                        modifier = Modifier
                            .weight(nameWeight)
                            .fillMaxHeight(),
                        contentAlignment = Alignment.Center
                    ) {
                        CounterNameLabel(
                            name = counterName,
                            fontSize = (counterFontSize * scale).sp,
                            color = counterColor,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = (counterFontSize * scale * 1.35f).dp.coerceAtLeast(20.dp)),
                        )
                    }

                    Spacer(modifier = Modifier.width(2.dp))

                    // Token matrix area
                    Box(
                        modifier = Modifier
                            .weight(tokenWeight)
                            .fillMaxHeight()
                    ) {
                        if (isSpecialMessage) {
                            TokenCard(
                                modifier = Modifier.fillMaxSize(),
                                token = specialMessage,
                                isPrimary = true,
                                scale = scale,
                                textColor = currentTokenTextColor,
                                bgBrush = tokenBgBrush,
                                fontSize = tokenFontSize,
                                isInverted = shouldBlink && blinkActive && specialMessage != null && isInverted,
                                blinkMode = tokenBlinkMode,
                                fullSize = true,
                                multiline = true,
                                specialCounterMessage = true,
                            )
                        } else {
                            val totalSlots = config.tokensPerCounter ?: (rows * columns)
                            CounterTokenSlotsGrid(
                                modifier = Modifier.fillMaxSize(),
                                totalSlots = totalSlots,
                                columns = columns,
                                tokens = tokens,
                                usePrefix = usePrefix,
                                counterCode = counterCode,
                                config = config,
                                scale = scale,
                                tokenFontSize = tokenFontSize,
                                currentTokenTextColor = currentTokenTextColor,
                                previousTokenTextColor = previousTokenTextColor,
                                tokenBgBrush = tokenBgBrush,
                                shouldBlink = shouldBlink,
                                isInverted = isInverted,
                                tokenBlinkMode = tokenBlinkMode,
                                vipEmergencyRawTokens = vipEmergencyRawTokens,
                            )
                        }
                    }
                }
            } else {
                // Cap header height: fillMaxHeight on the name row steals the whole column and hides tokens.
                val landscapeHeaderHeight = (counterFontSize * scale * 1.45f).dp.coerceIn(22.dp, 52.dp)
                Column(modifier = Modifier.fillMaxSize().padding(1.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    BoxWithConstraints(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(landscapeHeaderHeight)
                            .padding(horizontal = 2.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CounterNameLabel(
                            name = counterName,
                            fontSize = (counterFontSize * scale).sp,
                            color = counterColor,
                            modifier = Modifier
                                .fillMaxWidth()
                                .fillMaxHeight(),
                        )
                    }
                    Spacer(modifier = Modifier.height(1.dp))

                    if (isSpecialMessage) {
                        TokenCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .fillMaxHeight(),
                            token = specialMessage,
                            isPrimary = true,
                            scale = scale,
                            textColor = currentTokenTextColor,
                            bgBrush = tokenBgBrush,
                            fontSize = tokenFontSize,
                            isInverted = shouldBlink && blinkActive && specialMessage != null && isInverted,
                            blinkMode = tokenBlinkMode,
                            fullSize = true,
                            multiline = true,
                            specialCounterMessage = true,
                        )
                    } else {
                        val totalSlots = config.tokensPerCounter ?: (rows * columns)
                        CounterTokenSlotsGrid(
                            modifier = Modifier.fillMaxWidth().weight(1f),
                            totalSlots = totalSlots,
                            columns = columns,
                            tokens = tokens,
                            usePrefix = usePrefix,
                            counterCode = counterCode,
                            config = config,
                            scale = scale,
                            tokenFontSize = tokenFontSize,
                            currentTokenTextColor = currentTokenTextColor,
                            previousTokenTextColor = previousTokenTextColor,
                            tokenBgBrush = tokenBgBrush,
                            shouldBlink = shouldBlink,
                            isInverted = isInverted,
                            tokenBlinkMode = tokenBlinkMode,
                            vipEmergencyRawTokens = vipEmergencyRawTokens,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Largest font size in **sp** (given [maxSp] cap) so measured text fits in [maxW]Ã—[maxH] px.
 */
private fun measureTokenAutoFitSp(
    textMeasurer: TextMeasurer,
    text: String,
    maxW: Float,
    maxH: Float,
    maxSp: Float,
    minSp: Float,
    multiline: Boolean,
    maxMultilineLines: Int = 4,
    /** When > 1, used for [TextStyle.lineHeight] so auto-fit height matches on-screen multiline text. */
    lineHeightEm: Float = 1f,
): Float {
    if (text.isEmpty()) return maxSp.coerceAtLeast(minSp)
    if (maxW <= 0f || maxH <= 0f) return minSp
    var lo = minSp
    var hi = maxSp.coerceAtLeast(minSp)
    if (hi <= lo) return lo
    var best = lo
    val maxWInt = maxW.toInt().coerceAtLeast(1)
    val maxHInt = maxH.toInt().coerceAtLeast(1)
    val measureConstraints = if (multiline) {
        Constraints(maxWidth = maxWInt, maxHeight = maxHInt)
    } else {
        Constraints(maxWidth = maxWInt)
    }
    fun styleFor(mid: Float): TextStyle {
        val base = TextStyle(
            fontWeight = FontWeight.Bold,
            fontSize = mid.sp,
        )
        return if (lineHeightEm > 1.02f) {
            base.copy(lineHeight = (mid * lineHeightEm).sp)
        } else {
            base
        }
    }
    repeat(24) {
        val mid = (lo + hi) / 2f
        val layout = textMeasurer.measure(
            text = AnnotatedString(text),
            style = styleFor(mid),
            overflow = TextOverflow.Clip,
            softWrap = multiline,
            maxLines = if (multiline) maxMultilineLines else 1,
            constraints = measureConstraints,
        )
        val w = layout.size.width.toFloat()
        val h = layout.size.height.toFloat()
        val fits = w <= maxW && h <= maxH
        if (fits) {
            best = mid
            lo = mid
        } else {
            hi = mid
        }
    }
    return best
}

/** Compose [Constraints] reject very large dimensions (e.g. Int.MAX_VALUE / 4). */
private fun composeMeasureMaxPx(px: Float): Int = px.toInt().coerceIn(1, 8192)

/** Largest single-line font (sp) that fits in [maxH] px (for horizontal marquee). */
private fun measureTokenAutoFitHeightSp(
    textMeasurer: TextMeasurer,
    text: String,
    maxH: Float,
    maxSp: Float,
    minSp: Float,
    maxWpx: Float = 8192f,
    lineHeightEm: Float = 1f,
): Float {
    if (text.isEmpty()) return maxSp.coerceAtLeast(minSp)
    if (maxH <= 0f) return minSp
    var lo = minSp
    var hi = maxSp.coerceAtLeast(minSp)
    if (hi <= lo) return lo
    var best = lo
    val maxHInt = composeMeasureMaxPx(maxH)
    val maxWInt = composeMeasureMaxPx(maxWpx)
    fun styleFor(mid: Float): TextStyle {
        val base = TextStyle(fontWeight = FontWeight.Bold, fontSize = mid.sp)
        return if (lineHeightEm > 1.02f) {
            base.copy(lineHeight = (mid * lineHeightEm).sp)
        } else {
            base
        }
    }
    repeat(24) {
        val mid = (lo + hi) / 2f
        val layout = textMeasurer.measure(
            text = AnnotatedString(text),
            style = styleFor(mid),
            overflow = TextOverflow.Clip,
            softWrap = false,
            maxLines = 1,
            constraints = Constraints(maxWidth = maxWInt, maxHeight = maxHInt),
        )
        val fits = layout.size.height.toFloat() <= maxH
        if (fits) {
            best = mid
            lo = mid
        } else {
            hi = mid
        }
    }
    return best
}

/** Pack [text] onto lines at word boundaries so Compose never breaks mid-word. */
private fun buildWordWrappedMessage(
    textMeasurer: TextMeasurer,
    text: String,
    maxWpx: Float,
    fontSp: Float,
    lineHeightEm: Float,
): String {
    val words = text.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    if (words.size <= 1) return text.trim()
    val maxWInt = maxWpx.toInt().coerceAtLeast(1)
    val style = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = fontSp.sp,
        lineHeight = if (lineHeightEm > 1.02f) (fontSp * lineHeightEm).sp else TextUnit.Unspecified,
    )
    fun lineWidth(line: String): Float =
        textMeasurer.measure(
            text = AnnotatedString(line),
            style = style,
            softWrap = false,
            maxLines = 1,
            constraints = Constraints(maxWidth = maxWInt),
        ).size.width.toFloat()

    val lines = mutableListOf<String>()
    var current = StringBuilder()
    for (word in words) {
        val candidate = if (current.isEmpty()) word else "${current} $word"
        if (lineWidth(candidate) <= maxWpx) {
            current = StringBuilder(candidate)
        } else {
            if (current.isNotEmpty()) {
                lines.add(current.toString())
                current = StringBuilder()
            }
            if (lineWidth(word) <= maxWpx) {
                current = StringBuilder(word)
            } else {
                lines.add(word)
            }
        }
    }
    if (current.isNotEmpty()) lines.add(current.toString())
    return lines.joinToString("\n")
}

@Composable
/**
 * Draws a token tile/card used for both normal tokens and protocol `C` / `__MSG__` special
 * messages.
 */
fun TokenCard(
    token: String?,
    isPrimary: Boolean,
    scale: Float,
    textColor: Color,
    bgBrush: Brush,
    fontSize: Float,
    isInverted: Boolean = false,
    blinkMode: TokenBlinkMode = TokenBlinkMode.WHOLE_TILE,
    fullSize: Boolean = false,
    multiline: Boolean = false,
    /** Full-bleed layout for protocol `C` / `__MSG__` counter messages (fills token region, no code prefix). */
    specialCounterMessage: Boolean = false,
    modifier: Modifier = Modifier,
) {
    // Dynamic height based on font size to prevent clipping
    // Reduced height multiplier to tighten padding around the text
    val cardHeight = (fontSize * 1.6f * scale).coerceIn(32f, 120f).dp
    val textMeasurer = rememberTextMeasurer()
    val effectiveMultiline = multiline || specialCounterMessage
    val maxMultilineLines = if (specialCounterMessage) 24 else 4
    val lineHeightEm =
        if (specialCounterMessage && effectiveMultiline) SPECIAL_COUNTER_MSG_LINE_HEIGHT_EM else 1f
    // Extra insets so multiline protocol C / __MSG__ text is not clipped and lines are not cramped.
    val outerPad = if (specialCounterMessage) (4f * scale).dp.coerceIn(3.dp, 8.dp) else 1.dp

    Card(
        modifier = modifier
            .fillMaxWidth()
            .then(if (fullSize) Modifier.fillMaxHeight() else Modifier.height(cardHeight))
            .padding(outerPad),
        shape = RoundedCornerShape(if (specialCounterMessage) 10.dp else 8.dp),
        elevation = CardDefaults.cardElevation(2.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        // Swap colors if inverted (whole tile), or pulse text only while keeping the tile background.
        val invertWholeTile = isInverted && blinkMode == TokenBlinkMode.WHOLE_TILE
        val invertTextOnly = isInverted && blinkMode == TokenBlinkMode.TEXT_ONLY
        val finalBg = if (invertWholeTile) {
            SolidColor(textColor)
        } else {
            bgBrush
        }

        val finalTextColor = when {
            invertWholeTile -> Color.White
            invertTextOnly -> textColor.copy(alpha = 0.4f)
            else -> textColor
        }

        Box(
            modifier = Modifier.fillMaxSize().background(finalBg),
            contentAlignment = Alignment.Center
        ) {
            if (fullSize) {
                val density = LocalDensity.current
                BoxWithConstraints(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    val maxWpx = with(density) { maxWidth.toPx() }.coerceAtLeast(1f)
                    val maxHpx = with(density) { maxHeight.toPx() }.coerceAtLeast(1f)
                    val minSidePx = minOf(maxWpx, maxHpx)
                    val padScale = if (specialCounterMessage) {
                        (minSidePx / (56f * density.density)).coerceIn(0.35f, 1f)
                    } else {
                        1f
                    }
                    val innerPadH = if (specialCounterMessage) {
                        (12f * scale * padScale).dp.coerceIn(2.dp, 20.dp)
                    } else {
                        4.dp
                    }
                    val innerPadV = if (specialCounterMessage) {
                        (10f * scale * padScale).dp.coerceIn(2.dp, 18.dp)
                    } else {
                        2.dp
                    }
                    val innerWpx = (maxWpx - with(density) { (innerPadH * 2).toPx() }).coerceAtLeast(1f)
                    val innerHpx = (maxHpx - with(density) { (innerPadV * 2).toPx() }).coerceAtLeast(1f)
                    val display = token ?: ""
                    val refTileAreaPx = 220f * 72f
                    val areaScale = kotlin.math.sqrt((innerWpx * innerHpx) / refTileAreaPx)
                        .coerceIn(0.32f, 1f)
                    val maxFontSp = if (specialCounterMessage) {
                        (fontSize * scale * areaScale).coerceAtLeast(8f)
                    } else {
                        fontSize * scale
                    }
                    val minFontSp = if (specialCounterMessage) 3.5f else 6f
                    // Narrow tiles: one scrolling line (avoids "NURS"/"E"/"CALLI" mid-word breaks).
                    val useSpecialMessageTicker =
                        specialCounterMessage && innerWpx < innerHpx * 0.85f
                    if (useSpecialMessageTicker) {
                        val tickerSp = remember(
                            display,
                            innerWpx,
                            innerHpx,
                            maxFontSp,
                            minFontSp,
                            lineHeightEm,
                        ) {
                            measureTokenAutoFitHeightSp(
                                textMeasurer,
                                display,
                                innerHpx,
                                maxFontSp,
                                minFontSp,
                                maxWpx = (innerWpx * 12f).coerceAtLeast(512f),
                                lineHeightEm = lineHeightEm,
                            )
                        }
                        val messageColorInt = finalTextColor.toArgb()
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = innerPadH, vertical = innerPadV)
                                .clipToBounds(),
                            contentAlignment = Alignment.Center,
                        ) {
                            AndroidView(
                                factory = { CounterNameTickerView(it) },
                                update = { view ->
                                    // finalTextColor changes every blink frame; bind updates color only.
                                    view.bind(display, messageColorInt, tickerSp)
                                },
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    } else {
                        val displayForFit = remember(
                            display,
                            innerWpx,
                            maxFontSp,
                            specialCounterMessage,
                        ) {
                            if (specialCounterMessage) {
                                buildWordWrappedMessage(
                                    textMeasurer,
                                    display,
                                    innerWpx,
                                    maxFontSp,
                                    lineHeightEm,
                                )
                            } else {
                                display
                            }
                        }
                        val fittedSp = remember(
                            displayForFit,
                            innerWpx,
                            innerHpx,
                            maxFontSp,
                            minFontSp,
                            effectiveMultiline,
                            maxMultilineLines,
                            lineHeightEm,
                        ) {
                            measureTokenAutoFitSp(
                                textMeasurer,
                                displayForFit,
                                innerWpx,
                                innerHpx,
                                maxFontSp,
                                minFontSp,
                                effectiveMultiline,
                                maxMultilineLines,
                                lineHeightEm = lineHeightEm,
                            )
                        }
                        val messageStyle = TextStyle(
                            fontWeight = FontWeight.Bold,
                            fontSize = fittedSp.sp,
                            color = finalTextColor,
                            textAlign = TextAlign.Center,
                            lineHeight = if (lineHeightEm > 1.02f) {
                                (fittedSp * lineHeightEm).sp
                            } else {
                                TextUnit.Unspecified
                            },
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = innerPadH, vertical = innerPadV),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = displayForFit,
                                style = messageStyle,
                                maxLines = if (effectiveMultiline) maxMultilineLines else 1,
                                softWrap = effectiveMultiline,
                                overflow = TextOverflow.Clip,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            } else {
                Text(
                    text = token ?: "",
                    fontWeight = FontWeight.Bold,
                    fontSize = (fontSize * scale).sp,
                    color = finalTextColor,
                    textAlign = TextAlign.Center,
                    maxLines = if (effectiveMultiline) maxMultilineLines else 1,
                    softWrap = effectiveMultiline,
                )
            }
        }
    }
}


@Composable
/**
 * Renders the bottom footer area (device/app identifiers + license expiry line).
 *
 * When `daysExpiry` is null/invalid, the footer uses a generic “License Valid” label.
 */
fun FooterArea(macAddress: String, appVersion: String, daysExpiry: Int?, padding: androidx.compose.ui.unit.Dp, isPortrait: Boolean, scale: Float) {
    val licenseText = when {
        daysExpiry == null -> "License Valid"
        daysExpiry < 0 -> "License Expired"
        else -> "License expires in $daysExpiry days"
    }
    
    val infiniteTransition = rememberInfiniteTransition(label = "license_pulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 1f, 
        targetValue = 0.5f, 
        animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse),
        label = "alpha"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White.copy(alpha = 0.9f))
            .padding(horizontal = padding, vertical = 2.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val footerFontSize = (if (isPortrait) 10 else 12).sp * scale
            
            Text(
                text = "Device: $macAddress", 
                fontSize = footerFontSize, 
                color = Color.Blue,
                textAlign = TextAlign.Center,
                lineHeight = footerFontSize // Tighten line height
            )
            Text(
                text = "v$appVersion", 
                fontSize = footerFontSize, 
                color = Color.Gray,
                textAlign = TextAlign.Center,
                lineHeight = footerFontSize
            )

            // Only show license text if it's expired or expires soon (< 10 days) as the third line
            if (daysExpiry != null && daysExpiry <= 10) {
                Text(
                    text = licenseText, 
                    fontSize = footerFontSize, 
                    color = Color.Red.copy(alpha = alpha), 
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    lineHeight = footerFontSize
                )
            }
        }
    }
}
