// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.ui.windows.reviewer.whiteboard

import android.graphics.Color
import androidx.annotation.ColorInt
import com.ichi2.anki.ui.windows.reviewer.whiteboard.StrokePoints.Kind
import kotlin.math.abs

class Drawing(
    val width: Int,
    val height: Int,
    @ColorInt val background: Int,
    val actions: List<DrawingAction>,
)

object DrawingSvg {
    private const val FORMAT_VERSION = 1
    private const val MAX_PIXELS = 100_000
    private const val MAX_TWENTIETHS = MAX_PIXELS * 20L

    private val HEADER =
        Regex(
            """<svg xmlns="http://www\.w3\.org/2000/svg" data-ankidroid-drawing="$FORMAT_VERSION" """ +
                """width="(\d{1,7})" height="(\d{1,7})" .*>""",
        )
    private val BACKGROUND = Regex("""<rect width="\d{1,7}" height="\d{1,7}" fill="#([0-9a-f]{6})"/>""")
    private val GROUP =
        Regex(
            """<g( class="eraser")? stroke="#([0-9a-f]{6})"(?: stroke-opacity="0\.(\d{3})")? """ +
                """stroke-width="([0-9.]{1,12})">""",
        )
    private val PATH = Regex("""<path d="([^"]*)"/>""")

    fun write(drawing: Drawing): String {
        require(Color.alpha(drawing.background) == 255) { "The background must be opaque" }
        val width = drawing.width
        val height = drawing.height
        return buildString {
            append("<svg xmlns=\"http://www.w3.org/2000/svg\" data-ankidroid-drawing=\"$FORMAT_VERSION\"")
            append(" width=\"$width\" height=\"$height\" viewBox=\"0 0 $width $height\"")
            append(" fill=\"none\" stroke-linecap=\"round\" stroke-linejoin=\"round\">\n")
            append("<rect width=\"$width\" height=\"$height\" fill=\"")
            appendColor(drawing.background)
            append("\"/>\n")
            var group: GroupStyle? = null
            for (action in drawing.actions) {
                val points = requireNotNull((action.path as? StrokePath)?.points) { "The stroke has no points" }
                val style = GroupStyle.of(action, drawing.background)
                if (style != group) {
                    if (group != null) append("</g>\n")
                    appendGroup(style)
                    group = style
                }
                append("<path d=\"")
                appendPathData(points)
                append("\"/>\n")
            }
            if (group != null) append("</g>\n")
            append("</svg>\n")
        }
    }

    fun read(text: String): Drawing? {
        val drawing = parse(text) ?: return null
        return drawing.takeIf { write(it) == text }
    }

    private fun parse(text: String): Drawing? {
        val lines = text.split('\n').iterator()
        val header = HEADER.matchEntire(lines.nextOrNull() ?: return null) ?: return null
        val background = BACKGROUND.matchEntire(lines.nextOrNull() ?: return null) ?: return null
        val actions = mutableListOf<DrawingAction>()
        while (true) {
            val line = lines.nextOrNull() ?: return null
            if (line == "</svg>") break
            val (eraserClass, rgb, opacity, width) = GROUP.matchEntire(line)?.destructured ?: return null
            val isEraser = eraserClass.isNotEmpty()
            val alpha = if (opacity.isEmpty()) 255 else (opacity.toInt() * 255 + 500) / 1000
            val color = if (isEraser) Color.BLACK else (alpha shl 24) or rgb.toInt(16)
            val strokeWidth = parseWidth(width) ?: return null
            while (true) {
                val pathLine = lines.nextOrNull() ?: return null
                if (pathLine == "</g>") break
                val data = PATH.matchEntire(pathLine)?.groupValues?.get(1) ?: return null
                val points = parsePathData(data) ?: return null
                actions += DrawingAction(StrokePath(points.toPath(), points), color, strokeWidth, isEraser)
            }
        }
        return Drawing(
            width = header.groupValues[1].toInt(),
            height = header.groupValues[2].toInt(),
            background = background.groupValues[1].toInt(16) or Color.BLACK,
            actions = actions,
        )
    }

    private fun parseWidth(text: String): Float? {
        val reader = NumberReader(text)
        val twentieths = reader.readNumber() ?: return null
        return if (reader.isAtEnd && twentieths >= 0) twentieths / 20f else null
    }

    private fun parsePathData(data: String): StrokePoints? {
        val reader = NumberReader(data)
        val points = StrokePoints.Builder()
        var command: Char? = null
        var currentX = 0L
        var currentY = 0L
        var lastX = 0L
        var lastY = 0L
        while (!reader.isAtEnd) {
            val next = reader.peek()
            if (next == 'M' || next == 't' || next == 'l') {
                command = next
                reader.skip()
            } else if (command != 't' && command != 'l') {
                return null
            }
            val x = reader.readNumber() ?: return null
            val y = reader.readNumber() ?: return null
            val pointX: Long
            val pointY: Long
            when (command) {
                'M' -> {
                    currentX = x
                    currentY = y
                    pointX = x
                    pointY = y
                }
                't' -> {
                    currentX += x
                    currentY += y
                    pointX = 2 * currentX - lastX
                    pointY = 2 * currentY - lastY
                }
                else -> {
                    currentX += x
                    currentY += y
                    pointX = currentX
                    pointY = currentY
                }
            }
            if (currentX.isOutOfRange() || currentY.isOutOfRange() || pointX.isOutOfRange() || pointY.isOutOfRange()) return null
            when (command) {
                'M' -> points.moveTo(pointX / 20f, pointY / 20f)
                't' -> points.sample(pointX / 20f, pointY / 20f)
                else -> points.lineTo(pointX / 20f, pointY / 20f)
            }
            lastX = pointX
            lastY = pointY
        }
        return points.build()
    }

    private fun StringBuilder.appendPathData(points: StrokePoints) {
        var currentX = 0
        var currentY = 0
        var lastX = 0
        var lastY = 0
        var previous: Kind? = null
        for (index in 0 until points.size) {
            val kind = points.kind(index)
            val x: Int
            val y: Int
            when (kind) {
                Kind.MOVE -> {
                    x = wholePixels(points.x(index))
                    y = wholePixels(points.y(index))
                    append('M')
                    appendTwentieths(x)
                    appendSeparated(y)
                    currentX = x
                    currentY = y
                }
                Kind.SAMPLE -> {
                    x = wholePixels(points.x(index))
                    y = wholePixels(points.y(index))
                    val endX = (lastX + x) / 2
                    val endY = (lastY + y) / 2
                    appendSegment('t', continuesRun = previous == Kind.SAMPLE, endX - currentX, endY - currentY)
                    currentX = endX
                    currentY = endY
                }
                Kind.LINE -> {
                    x = tenthsOfPixels(points.x(index))
                    y = tenthsOfPixels(points.y(index))
                    appendSegment('l', continuesRun = previous == Kind.LINE, x - currentX, y - currentY)
                    currentX = x
                    currentY = y
                }
            }
            lastX = x
            lastY = y
            previous = kind
        }
    }

    private fun StringBuilder.appendSegment(
        command: Char,
        continuesRun: Boolean,
        dx: Int,
        dy: Int,
    ) {
        if (continuesRun) {
            appendSeparated(dx)
        } else {
            append(command)
            appendTwentieths(dx)
        }
        appendSeparated(dy)
    }

    private fun StringBuilder.appendGroup(style: GroupStyle) {
        append("<g")
        if (style.isEraser) append(" class=\"eraser\"")
        append(" stroke=\"")
        appendColor(style.rgb)
        append('"')
        if (style.alpha != 255) {
            append(" stroke-opacity=\"0.")
            append(((style.alpha * 1000 + 127) / 255).toString().padStart(3, '0'))
            append('"')
        }
        append(" stroke-width=\"")
        appendTwentieths(style.width)
        append("\">\n")
    }

    private fun StringBuilder.appendColor(color: Int) {
        append('#')
        append(Integer.toHexString(color and 0xFFFFFF).padStart(6, '0'))
    }

    private fun StringBuilder.appendSeparated(twentieths: Int) {
        if (twentieths >= 0) append(' ')
        appendTwentieths(twentieths)
    }

    private fun StringBuilder.appendTwentieths(twentieths: Int) {
        if (twentieths < 0) append('-')
        val magnitude = abs(twentieths)
        val whole = magnitude / 20
        val hundredths = magnitude % 20 * 5
        if (whole != 0 || hundredths == 0) append(whole)
        if (hundredths != 0) {
            append('.')
            append(hundredths / 10)
            if (hundredths % 10 != 0) append(hundredths % 10)
        }
    }

    private fun wholePixels(value: Float): Int =
        Math.round(value.toDouble()).coerceIn(-MAX_PIXELS.toLong(), MAX_PIXELS.toLong()).toInt() * 20

    private fun tenthsOfPixels(value: Float): Int =
        Math.round(value.toDouble() * 10).coerceIn(-MAX_PIXELS * 10L, MAX_PIXELS * 10L).toInt() * 2

    private fun twentieths(value: Float): Int = Math.round(value.toDouble() * 20).coerceIn(0, MAX_TWENTIETHS).toInt()

    private fun Long.isOutOfRange() = abs(this) > MAX_TWENTIETHS

    private fun <T> Iterator<T>.nextOrNull(): T? = if (hasNext()) next() else null

    private data class GroupStyle(
        val isEraser: Boolean,
        val rgb: Int,
        val alpha: Int,
        val width: Int,
    ) {
        companion object {
            fun of(
                action: DrawingAction,
                background: Int,
            ) = if (action.isEraser) {
                GroupStyle(isEraser = true, rgb = background and 0xFFFFFF, alpha = 255, width = twentieths(action.strokeWidth))
            } else {
                GroupStyle(
                    isEraser = false,
                    rgb = action.color and 0xFFFFFF,
                    alpha = Color.alpha(action.color),
                    width = twentieths(action.strokeWidth),
                )
            }
        }
    }

    private class NumberReader(
        private val text: String,
    ) {
        private var position = 0

        val isAtEnd: Boolean
            get() = position == text.length

        fun peek(): Char? = text.getOrNull(position)

        fun skip() {
            position++
        }

        fun readNumber(): Long? {
            if (peek() == ' ') skip()
            val isNegative = peek() == '-'
            if (isNegative) skip()
            var whole = 0L
            var wholeDigits = 0
            while (true) {
                val digit = peek()?.asciiDigit() ?: break
                if (wholeDigits == 7) return null
                whole = whole * 10 + digit
                wholeDigits++
                skip()
            }
            var hundredths = 0
            if (peek() == '.') {
                skip()
                var fractionDigits = 0
                while (true) {
                    val digit = peek()?.asciiDigit() ?: break
                    if (fractionDigits == 2) return null
                    hundredths = hundredths * 10 + digit
                    fractionDigits++
                    skip()
                }
                when (fractionDigits) {
                    0 -> return null
                    1 -> hundredths *= 10
                }
            } else if (wholeDigits == 0) {
                return null
            }
            if (hundredths % 5 != 0) return null
            val magnitude = whole * 20 + hundredths / 5
            return if (isNegative) -magnitude else magnitude
        }

        private fun Char.asciiDigit(): Int? = if (this in '0'..'9') this - '0' else null
    }
}
