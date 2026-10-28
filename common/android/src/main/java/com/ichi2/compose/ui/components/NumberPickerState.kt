// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2021 Tushar Bhatt <tbhatt312@gmail.com>

package com.ichi2.compose.ui.components

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.ui.text.TextRange

/** Shared integer editing state. Hosts retain ownership of confirmation and persistence. */
class NumberPickerState(
    val minimum: Int,
    val maximum: Int,
    initialText: String,
    initialSelection: TextRange = TextRange(initialText.length),
) {
    val input = TextFieldState(initialText, initialSelection)
    private var lastValidEntry = initialText.toIntOrNull() ?: minimum

    /** Hosts use this callback to update their confirmation action. */
    var onValidityChanged: (Boolean) -> Unit = {}

    val value: Int? get() =
        input.text
            .toString()
            .toIntOrNull()
            ?.takeIf { it in minimum..maximum }

    internal fun notifyValidity() = onValidityChanged(value != null)

    fun step(increment: Boolean) {
        val current = input.text.toString().toIntOrNull() ?: lastValidEntry
        lastValidEntry = (current.toLong() + if (increment) 1 else -1).coerceIn(minimum.toLong(), maximum.toLong()).toInt()
        input.edit {
            replace(0, length, lastValidEntry.toString())
            selection = TextRange(length)
        }
        notifyValidity()
    }

    companion object {
        enum class ValidationResult { VALID, INVALID, EMPTY, OVERFLOW, UNDERFLOW }

        fun validate(
            text: String,
            value: Int?,
            min: Int,
            max: Int,
        ): ValidationResult =
            when {
                text.isEmpty() -> ValidationResult.EMPTY
                value == null -> ValidationResult.INVALID
                value > max -> ValidationResult.OVERFLOW
                value < min -> ValidationResult.UNDERFLOW
                else -> ValidationResult.VALID
            }
    }
}
