package com.softland.callqtv.ui.widget

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.view.animation.LinearInterpolator

/**
 * Shared horizontal marquee scroll for dual-[android.widget.TextView] tickers.
 */
internal class MarqueeScrollAnimator(
    private val applyOffset: (Float) -> Unit,
    private val onScrollCycleEnd: () -> Unit,
) {
    private var animator: ValueAnimator? = null

    var scrollDistance: Float = 0f
        private set
    var scrollDurationMs: Long = 1L
        private set

    fun configure(distance: Float, durationMs: Long) {
        scrollDistance = distance.coerceAtLeast(1f)
        scrollDurationMs = durationMs.coerceAtLeast(1L)
    }

    fun isRunning(): Boolean = animator?.isRunning == true

    fun cancel() {
        animator?.cancel()
        animator = null
    }

    fun runScroll(fromOffset: Float) {
        val distance = scrollDistance
        val clampedFrom = fromOffset.coerceIn(0f, distance)
        applyOffset(clampedFrom)
        val remainingFraction = if (distance > 0f) 1f - (clampedFrom / distance) else 0f
        val animDuration = (scrollDurationMs * remainingFraction).toLong().coerceAtLeast(1L)
        animator = ValueAnimator.ofFloat(clampedFrom, distance).apply {
            duration = animDuration
            interpolator = LinearInterpolator()
            addUpdateListener { va ->
                applyOffset(va.animatedValue as Float)
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    applyOffset(0f)
                    onScrollCycleEnd()
                }

                override fun onAnimationCancel(animation: Animator) {
                    animator = null
                }
            })
            start()
        }
    }
}
