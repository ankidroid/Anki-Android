// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.preferences

import kotlin.time.Duration
import kotlin.time.DurationUnit
import kotlin.time.toDuration

/**
 * Validated non-zero duration representing the learn ahead limit in minutes.
 *
 * Sourced to Anki Desktop:
 * - Default 20 minutes (1200s): [rslib/src/config/mod.rs#L120]
 * - Valid maximum of 999 minutes: [qt/aqt/forms/preferences.ui#L313]
 * - Setting 0 in backend disables learn ahead: [qt/aqt/preferences.py#L145]
 */
@JvmInline
value class LearnAheadLimit private constructor(
    val minutes: Int,
) {
    fun toDuration(): Duration = minutes.toDuration(DurationUnit.MINUTES)

    companion object {
        const val DEFAULT_LEARN_AHEAD_MINUTES = 20
        const val MIN_LEARN_AHEAD_MINUTES = 1
        const val MAX_LEARN_AHEAD_MINUTES = 999

        val INPUT_RANGE = MIN_LEARN_AHEAD_MINUTES..MAX_LEARN_AHEAD_MINUTES

        val DEFAULT = LearnAheadLimit(DEFAULT_LEARN_AHEAD_MINUTES)

        fun fromMinutes(minutes: Int): LearnAheadLimit? =
            if (minutes in INPUT_RANGE) {
                LearnAheadLimit(minutes)
            } else {
                null
            }
    }
}

fun Duration.toLearnAheadLimit(): LearnAheadLimit? = LearnAheadLimit.fromMinutes(this.toInt(DurationUnit.MINUTES))
