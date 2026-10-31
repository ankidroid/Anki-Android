// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Ashish Yadav <mailtoashish693@gmail.com>

package com.ichi2.anki.noteeditor

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import kotlin.math.max

@Composable
internal fun RecordingWaveform(
    levels: List<Float?>,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier) {
        val barWidth = 3.dp.toPx()
        val step = barWidth + 2.dp.toPx()
        val slots = max(1, ((size.width + step - barWidth) / step).toInt())
        val shown = levels.takeLast(slots)
        val fadeSlots = if (levels.size > slots) slots * 0.35f else 0f
        val centerY = size.height / 2
        val usableHeight = size.height - barWidth
        for (slot in 0 until slots) {
            val x = barWidth / 2 + slot * step
            val fade = if (slot < fadeSlots) 0.2f + 0.8f * slot / fadeSlots else 1f
            val level = shown.getOrNull(slot)
            if (level == null) {
                drawCircle(color.copy(alpha = 0.38f * fade), radius = barWidth / 2, center = Offset(x, centerY))
                continue
            }
            val halfHeight = level.coerceIn(0f, 1f) * usableHeight / 2
            drawLine(
                color = color.copy(alpha = color.alpha * fade),
                start = Offset(x, centerY - halfHeight),
                end = Offset(x, centerY + halfHeight),
                strokeWidth = barWidth,
                cap = StrokeCap.Round,
            )
        }
    }
}
