// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.ui.windows.reviewer.whiteboard

import android.graphics.Path

class StrokePoints private constructor(
    private val kinds: ByteArray,
    private val coordinates: FloatArray,
) {
    enum class Kind { MOVE, SAMPLE, LINE }

    val size: Int
        get() = kinds.size

    fun kind(index: Int): Kind = Kind.entries[kinds[index].toInt()]

    fun x(index: Int): Float = coordinates[index * 2]

    fun y(index: Int): Float = coordinates[index * 2 + 1]

    fun toPath(): Path {
        val path = Path()
        var lastX = 0f
        var lastY = 0f
        for (index in 0 until size) {
            val x = x(index)
            val y = y(index)
            when (kind(index)) {
                Kind.MOVE -> path.moveTo(x, y)
                Kind.SAMPLE -> path.quadTo(lastX, lastY, (lastX + x) / 2f, (lastY + y) / 2f)
                Kind.LINE -> path.lineTo(x, y)
            }
            lastX = x
            lastY = y
        }
        return path
    }

    class Builder {
        private var kinds = ByteArray(16)
        private var coordinates = FloatArray(32)
        private var size = 0

        fun moveTo(
            x: Float,
            y: Float,
        ) = add(Kind.MOVE, x, y)

        fun sample(
            x: Float,
            y: Float,
        ) = add(Kind.SAMPLE, x, y)

        fun lineTo(
            x: Float,
            y: Float,
        ) = add(Kind.LINE, x, y)

        fun clear() {
            size = 0
        }

        fun build() = StrokePoints(kinds.copyOf(size), coordinates.copyOf(size * 2))

        private fun add(
            kind: Kind,
            x: Float,
            y: Float,
        ) {
            if (size == kinds.size) {
                kinds = kinds.copyOf(size * 2)
                coordinates = coordinates.copyOf(size * 4)
            }
            kinds[size] = kind.ordinal.toByte()
            coordinates[size * 2] = x
            coordinates[size * 2 + 1] = y
            size++
        }
    }
}

class StrokePath(
    path: Path,
    val points: StrokePoints,
) : Path(path)
