package com.softland.callqtv.ui

import android.content.Context
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import android.widget.TextView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.softland.callqtv.ui.theme.parseHexColorOrNull
import com.softland.callqtv.ui.widget.MarqueeScrollAnimator
import com.softland.callqtv.utils.ThemeColorManager
@Composable
/**
 * Footer ticker that scrolls provided text lines seamlessly using an Android `TextView`
 * marquee-like implementation.
 */
fun ScrollingFooter(
    textLines: List<String>,
    scale: Float,
    isPortrait: Boolean,
    appThemeHex: String,
    scrollTextColorHex: String? = null,
) {
    val scrollText = remember(textLines) {
        textLines.filter { it.isNotBlank() }.joinToString(separator = "  •  ")
    }
    if (scrollText.isEmpty()) return

    val textColor = remember(scrollTextColorHex) {
        parseHexColorOrNull(scrollTextColorHex) ?: android.graphics.Color.WHITE
    }
    val textSizeSp = if (isPortrait) 12f else 14f
    // Use a compact repeating unit so ticker flow feels continuous (no visible block reset).
    val marqueeText = remember(scrollText) { "$scrollText   \u2022   " }
    val footerHeight = if (isPortrait) 24.dp else 28.dp
    val footerBrush = remember(appThemeHex) {
        ThemeColorManager.getTickerStripBackgroundBrush(appThemeHex)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(footerBrush)
            .height(footerHeight),
        contentAlignment = Alignment.CenterStart
    ) {
        AndroidView(
            factory = { ctx ->
                SeamlessTickerView(ctx)
            },
            update = { ticker ->
                ticker.bind(
                    text = marqueeText,
                    textColor = textColor,
                    textSizeSp = textSizeSp,
                    isBold = true,
                    speedDpPerSec = if (isPortrait) 24f else 28f
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .clipToBounds()
        )
    }
}

/** Applies typography/scroll-friendly settings to an ad ticker `TextView`. */
private fun configureAdTickerTextView(tv: TextView, color: Int, sizeSp: Float, isBold: Boolean) {
    tv.setTextColor(color)
    tv.textSize = sizeSp
    tv.setTypeface(tv.typeface, if (isBold) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
    tv.isSingleLine = true
    tv.includeFontPadding = false
    tv.gravity = android.view.Gravity.CENTER_VERTICAL
    tv.setHorizontallyScrolling(true)
    tv.setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)
}

/** Pause at the start of each counter-name marquee loop so the label can be read before scrolling again. */
private const val COUNTER_NAME_MARQUEE_RESTART_PAUSE_MS = 3_000L

/** Counter header text must never ellipsize; width is set from paint measure and laid out in [CounterNameTickerView]. */
private fun configureCounterNameTextView(tv: TextView, color: Int, sizeSp: Float) {
    tv.setTextColor(color)
    tv.textSize = sizeSp
    tv.setTypeface(tv.typeface, android.graphics.Typeface.BOLD)
    tv.isSingleLine = true
    tv.maxLines = 1
    tv.includeFontPadding = false
    tv.gravity = android.view.Gravity.CENTER_VERTICAL
    tv.setHorizontallyScrolling(true)
    tv.ellipsize = null
    tv.maxWidth = Int.MAX_VALUE
    tv.setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)
}

/**
 * Counter name header: static centered label when it fits; otherwise the same seamless
 * horizontal scroll used by the footer ticker so long names (e.g. CARDIOLOGY) stay readable.
 */
internal class CounterNameTickerView(context: Context) : FrameLayout(context) {
    private val text1 = TextView(context)
    private val text2 = TextView(context)
    private var cachedName: String = ""
    private var cachedTextColor: Int = Int.MIN_VALUE
    private var cachedTextSizeSp: Float = -1f
    private var currentOffset: Float = 0f
    private var marqueeActive: Boolean = false
    private var marqueePauseRunnable: Runnable? = null
    private val scrollAnimator = MarqueeScrollAnimator(
        applyOffset = { offset -> applyMarqueeOffset(offset) },
        onScrollCycleEnd = { scheduleMarqueePause() },
    )

    init {
        clipToPadding = true
        clipChildren = true
        setWillNotDraw(true)
        val lp = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT)
        text1.layoutParams = lp
        text2.layoutParams = lp
        addView(text1)
        addView(text2)
        text2.visibility = GONE
    }

    /** Binds the counter name header and updates layout/animation when name/color/size changes. */
    fun bind(name: String, textColor: Int, textSizeSp: Float) {
        val colorChanged = cachedTextColor != textColor
        val sizeChanged = cachedTextSizeSp != textSizeSp
        if (colorChanged || sizeChanged) {
            cachedTextColor = textColor
            cachedTextSizeSp = textSizeSp
            configureCounterNameTextView(text1, textColor, textSizeSp)
            configureCounterNameTextView(text2, textColor, textSizeSp)
        }
        val nameChanged = cachedName != name
        if (nameChanged) {
            cachedName = name
            restartLayout(preservePhase = false)
            return
        }
        if (sizeChanged) {
            restartLayout(preservePhase = true)
            return
        }
        if (marqueeActive && marqueePauseRunnable == null) {
            restartLayout(preservePhase = true)
        }
    }

    /** Cancels the current marquee scroll animation and pauses any delayed runnable. */
    private fun cancelMarqueeAnimation() {
        scrollAnimator.cancel()
        marqueePauseRunnable?.let { removeCallbacks(it) }
        marqueePauseRunnable = null
    }

    /** Applies horizontal translation for both marquee TextViews for the given [offset]. */
    private fun applyMarqueeOffset(offset: Float) {
        currentOffset = offset
        text1.translationX = -offset
        text2.translationX = scrollAnimator.scrollDistance - offset
    }

    /** Schedules a brief pause at the start of each marquee loop for readability. */
    private fun scheduleMarqueePause() {
        if (!marqueeActive) return
        applyMarqueeOffset(0f)
        marqueePauseRunnable = Runnable {
            if (marqueeActive) runMarqueeScroll(0f)
        }
        postDelayed(marqueePauseRunnable!!, COUNTER_NAME_MARQUEE_RESTART_PAUSE_MS)
    }

    /** Starts marquee scroll from [fromOffset] when the view is active. */
    private fun runMarqueeScroll(fromOffset: Float) {
        if (!marqueeActive) return
        scrollAnimator.runScroll(fromOffset)
    }

    /** Measure children at full text width so Android does not ellipsize inside a narrow tile. */
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val viewportWidth = MeasureSpec.getSize(widthMeasureSpec)
        val childWidthSpec = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
        val parentHeight = MeasureSpec.getSize(heightMeasureSpec)
        val childHeightSpec = when (MeasureSpec.getMode(heightMeasureSpec)) {
            MeasureSpec.EXACTLY -> MeasureSpec.makeMeasureSpec(parentHeight, MeasureSpec.EXACTLY)
            MeasureSpec.AT_MOST -> MeasureSpec.makeMeasureSpec(parentHeight.coerceAtLeast(0), MeasureSpec.AT_MOST)
            else -> MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
        }
        var maxChildHeight = 0
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            if (child.visibility == GONE) continue
            child.measure(childWidthSpec, childHeightSpec)
            maxChildHeight = maxOf(maxChildHeight, child.measuredHeight)
        }
        val resolvedHeight = when (MeasureSpec.getMode(heightMeasureSpec)) {
            MeasureSpec.EXACTLY -> parentHeight
            MeasureSpec.AT_MOST -> minOf(maxChildHeight, parentHeight)
            else -> maxChildHeight
        }
        setMeasuredDimension(viewportWidth, resolvedHeight.coerceAtLeast(0))
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        val viewportHeight = bottom - top
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            if (child.visibility == GONE) continue
            val childTop = ((viewportHeight - child.measuredHeight) / 2).coerceAtLeast(0)
            child.layout(0, childTop, child.measuredWidth, childTop + child.measuredHeight)
        }
    }

    /** Measures text width in pixels so we can size/layout without ellipsizing. */
    private fun measureContentWidth(content: String): Float {
        return text1.paint.measureText(content).coerceAtLeast(1f)
    }

    /** Updates the given TextView content and ensures its width matches the text. */
    private fun applyContent(tv: TextView, content: String) {
        tv.text = content
        val contentWidth = kotlin.math.ceil(measureContentWidth(content).toDouble()).toInt().coerceAtLeast(1)
        val lp = tv.layoutParams as LayoutParams
        if (lp.width != contentWidth) {
            lp.width = contentWidth
            tv.layoutParams = lp
        }
    }

    /**
     * Rebuilds the static/marquee layout from current state.
     *
     * When [preservePhase] is true, the marquee starts from a fraction of the previous cycle.
     */
    private fun restartLayout(preservePhase: Boolean) {
        post {
            val previousOffset = currentOffset
            cancelMarqueeAnimation()

            val viewWidth = width.toFloat()
            if (viewWidth <= 0f) return@post

            val label = cachedName
            applyContent(text1, label)
            applyContent(text2, "")
            text2.visibility = GONE
            requestLayout()
            post {
                applyMeasuredLayout(viewWidth, label, previousOffset, preservePhase)
            }
        }
    }

    /**
     * Decide static vs marquee from **measured** text width. Never center with negative translationX
     * (that clips the middle of long names, e.g. "RDIOLOG" instead of "CARDIOLOGY 1").
     */
    private fun applyMeasuredLayout(
        viewWidth: Float,
        label: String,
        previousOffset: Float,
        preservePhase: Boolean,
    ) {
        val horizontalPad = 4f * resources.displayMetrics.density
        val laidOutWidth = text1.measuredWidth.toFloat().coerceAtLeast(measureContentWidth(label))
        val needsMarquee = laidOutWidth > viewWidth - horizontalPad

        if (!needsMarquee) {
            marqueeActive = false
            text2.visibility = GONE
            text1.translationX = ((viewWidth - laidOutWidth) / 2f).coerceAtLeast(0f)
            text2.translationX = viewWidth
            currentOffset = 0f
            return
        }

        marqueeActive = true
        text2.visibility = VISIBLE
        val loopText = "$label   "
        applyContent(text1, loopText)
        applyContent(text2, loopText)
        requestLayout()
        post {
            startMarquee(viewWidth, loopText, previousOffset, preservePhase)
        }
    }

    /** Computes scroll distance/duration and starts marquee animation for the header text. */
    private fun startMarquee(
        viewWidth: Float,
        loopText: String,
        previousOffset: Float,
        preservePhase: Boolean,
    ) {
        cancelMarqueeAnimation()

        val gap = 20f * resources.displayMetrics.density
        val loopWidth = text1.measuredWidth.toFloat().coerceAtLeast(measureContentWidth(loopText))
        val distance = loopWidth + gap
        val speedDpPerSec = 18f
        val speedPxPerSec = speedDpPerSec * resources.displayMetrics.density
        val durationMs = ((distance / speedPxPerSec) * 1000f).toLong().coerceAtLeast(1L)
        val startFraction = if (preservePhase && distance > 0f) {
            ((previousOffset % distance) / distance).coerceIn(0f, 1f)
        } else {
            0f
        }

        scrollAnimator.configure(distance, durationMs)

        if (startFraction > 0f && preservePhase) {
            runMarqueeScroll(distance * startFraction)
        } else {
            scheduleMarqueePause()
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w > 0) {
            restartLayout(preservePhase = true)
        }
    }

    override fun onDetachedFromWindow() {
        cancelMarqueeAnimation()
        super.onDetachedFromWindow()
    }
}

private class SeamlessTickerView(context: Context) : FrameLayout(context) {
    private val text1 = TextView(context)
    private val text2 = TextView(context)
    private var cachedText: String = ""
    private var cachedSpeedDpPerSec: Float = -1f
    private var cachedTextColor: Int = Int.MIN_VALUE
    private var cachedTextSizeSp: Float = -1f
    private var cachedIsBold: Boolean = false
    private var currentOffset: Float = 0f
    private lateinit var scrollAnimator: MarqueeScrollAnimator

    init {
        clipToPadding = true
        clipChildren = true
        setWillNotDraw(true)

        val lp = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT)
        text1.layoutParams = lp
        text2.layoutParams = lp
        addView(text1)
        addView(text2)
        scrollAnimator = MarqueeScrollAnimator(
            applyOffset = { offset -> applyMarqueeOffset(offset) },
            onScrollCycleEnd = { onFooterMarqueeCycleEnd() },
        )
    }

    /** Called by [MarqueeScrollAnimator] when a full scroll cycle ends (restarts if attached). */
    private fun onFooterMarqueeCycleEnd() {
        if (isAttachedToWindow) scrollAnimator.runScroll(0f)
    }

    /** Binds footer ticker text and updates animation parameters when values change. */
    fun bind(
        text: String,
        textColor: Int,
        textSizeSp: Float,
        isBold: Boolean,
        speedDpPerSec: Float
    ) {
        val colorChanged = cachedTextColor != textColor
        val sizeChanged = cachedTextSizeSp != textSizeSp
        val boldChanged = cachedIsBold != isBold
        if (colorChanged || sizeChanged || boldChanged) {
            cachedTextColor = textColor
            cachedTextSizeSp = textSizeSp
            cachedIsBold = isBold
            configureAdTickerTextView(text1, textColor, textSizeSp, isBold)
            configureAdTickerTextView(text2, textColor, textSizeSp, isBold)
        }

        val textChanged = cachedText != text
        if (textChanged) {
            cachedText = text
            text1.text = text
            text2.text = text
        }
        val speedChanged = cachedSpeedDpPerSec != speedDpPerSec
        if (speedChanged) {
            cachedSpeedDpPerSec = speedDpPerSec
        }

        when {
            textChanged -> restartAnimation(preservePhase = false)
            sizeChanged || speedChanged -> restartAnimation(preservePhase = true)
            !scrollAnimator.isRunning() -> restartAnimation(preservePhase = true)
        }
    }

    /** Cancels footer marquee animation. */
    private fun cancelMarqueeAnimation() {
        scrollAnimator.cancel()
    }

    /** Applies marquee translation for the given [offset] to both TextViews. */
    private fun applyMarqueeOffset(offset: Float) {
        currentOffset = offset
        text1.translationX = -offset
        text2.translationX = scrollAnimator.scrollDistance - offset
    }

    /** Runs marquee scroll starting from [fromOffset] if the view is attached. */
    private fun runMarqueeScroll(fromOffset: Float) {
        if (!isAttachedToWindow) return
        scrollAnimator.runScroll(fromOffset)
    }

    /** Recalculates layout and restarts marquee animation for the current cached text/speed. */
    private fun restartAnimation(preservePhase: Boolean) {
        post {
            if (!isAttachedToWindow) return@post
            val previousOffset = currentOffset
            cancelMarqueeAnimation()
            val w = width.toFloat()
            if (w <= 0f) return@post

            val textWidth = text1.paint.measureText(text1.text?.toString().orEmpty()).coerceAtLeast(1f)
            val gap = 16f * resources.displayMetrics.density
            val distance = textWidth + gap
            val speedPxPerSec = (cachedSpeedDpPerSec.coerceAtLeast(8f)) * resources.displayMetrics.density
            val durationMs = ((distance / speedPxPerSec) * 1000f).toLong().coerceAtLeast(1L)
            scrollAnimator.configure(distance, durationMs)
            val startFraction = if (preservePhase && distance > 0f) {
                ((previousOffset % distance) / distance).coerceIn(0f, 1f)
            } else {
                0f
            }

            runMarqueeScroll(distance * startFraction)
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w > 0 && oldw > 0 && w != oldw) {
            restartAnimation(preservePhase = true)
        }
    }

    override fun onDetachedFromWindow() {
        cancelMarqueeAnimation()
        super.onDetachedFromWindow()
    }
}
