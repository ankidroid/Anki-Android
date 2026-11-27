// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.widget

import android.content.Context
import android.util.SizeF
import android.util.TypedValue
import android.view.View
import android.view.View.MeasureSpec
import android.widget.RemoteViews
import android.widget.TextView
import androidx.core.util.TypedValueCompat
import java.text.BreakIterator
import kotlin.math.roundToInt

internal class OffscreenWidget(
    context: Context,
    views: RemoteViews,
    size: SizeF,
) {
    private val density = context.resources.displayMetrics.density
    private val widthSpec =
        MeasureSpec.makeMeasureSpec(
            (size.width * density).roundToInt(),
            if (size.width > 0) MeasureSpec.EXACTLY else MeasureSpec.UNSPECIFIED,
        )
    private val availableHeight = if (size.height > 0) size.height * density else Float.POSITIVE_INFINITY
    val root: View = views.apply(context, null)

    fun fitsHeight(): Boolean {
        root.measure(widthSpec, MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED))
        return root.measuredHeight <= availableHeight
    }
}

/**
 * Fits the message in [size] without splitting a word: first by trimming the padding,
 * then by switching to [shorterText], and only then by shrinking the text.
 *
 * @param context used to inflate and measure the layout
 * @param size the widget size in dp, unbounded where a side is 0
 * @param text shown when it fits, trimming the padding if needed
 * @param shorterText shown when [text] does not fit even without padding, shrunk if it still does not fit
 * @param findMessage returns the message's view from the inflated layout
 */
internal fun RemoteViews.fitMessage(
    context: Context,
    size: SizeF,
    text: String,
    shorterText: String,
    findMessage: (View) -> TextView,
) {
    val widget = OffscreenWidget(context, this, size)
    val message = findMessage(widget.root)
    val textSize = message.textSize
    val padding = message.paddings

    fun fits(
        paddingScale: Float,
        textScale: Float = 1f,
    ): Boolean {
        message.setTextSize(TypedValue.COMPLEX_UNIT_PX, textSize * textScale)
        message.setScaledPadding(padding, paddingScale)
        return widget.fitsHeight() && message.breaksOnlyBetweenWords()
    }

    fun fitsWithLessPadding(candidate: String): Boolean {
        message.text = candidate
        return when {
            fits(paddingScale = 1f) -> true
            !fits(paddingScale = 0f) -> false
            else -> {
                fits(largestFittingScale(padding.max().toFloat()) { fits(paddingScale = it) })
                true
            }
        }
    }

    val shrunk = !fitsWithLessPadding(text) && !fitsWithLessPadding(shorterText)
    if (shrunk) fits(paddingScale = 0f, textScale = largestFittingScale(textSize) { fits(paddingScale = 0f, textScale = it) })
    copyTextSize(context, message, shrunk)
    setTextViewText(message.id, message.text)
    copyPadding(message)
}

/** Binary search for the largest scale that still fits, to within 1px of [largestSize]. */
internal inline fun largestFittingScale(
    largestSize: Float,
    fitsAtScale: (Float) -> Boolean,
): Float {
    var minScale = 0f
    var maxScale = 1f
    while ((maxScale - minScale) * largestSize > 1f) {
        val scale = (minScale + maxScale) / 2
        if (fitsAtScale(scale)) minScale = scale else maxScale = scale
    }
    return minScale
}

internal fun RemoteViews.copyTextSize(
    context: Context,
    view: TextView,
    shrunk: Boolean,
) = if (shrunk) {
    setTextViewTextSize(view.id, TypedValue.COMPLEX_UNIT_PX, view.textSize)
} else {
    setTextViewTextSize(
        view.id,
        TypedValue.COMPLEX_UNIT_SP,
        TypedValueCompat.pxToSp(view.textSize, context.resources.displayMetrics),
    )
}

internal fun RemoteViews.copyPadding(view: View) =
    setViewPadding(view.id, view.paddingLeft, view.paddingTop, view.paddingRight, view.paddingBottom)

internal val View.paddings get() = listOf(paddingLeft, paddingTop, paddingRight, paddingBottom)

internal fun View.setScaledPadding(
    padding: List<Int>,
    scale: Float,
) {
    val (left, top, right, bottom) = padding.map { (it * scale).roundToInt() }
    setPadding(left, top, right, bottom)
}

internal fun TextView.breaksOnlyBetweenWords(): Boolean {
    val layout = layout ?: return true
    val lineBreaks = BreakIterator.getLineInstance()
    lineBreaks.setText(text.toString())
    return (0 until layout.lineCount - 1).all { lineBreaks.isBoundary(layout.getLineEnd(it)) }
}
