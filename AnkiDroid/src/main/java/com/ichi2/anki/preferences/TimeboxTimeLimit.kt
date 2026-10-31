// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.preferences

import kotlin.time.Duration
import kotlin.time.DurationUnit
import kotlin.time.toDuration

/**
 * Domain representation of a review timebox time limit.
 *
 * A limit of 0 minutes represents disabled timeboxing.
 * A limit greater than 0 represents active timeboxing with the given duration in minutes.
 *
 * Reference: Anki Desktop configuration and preferences:
 * - Default disabled (0s): [rslib/src/config/mod.rs#L118](https://github.com/ankitects/anki/blob/main/rslib/src/config/mod.rs#L118)
 * - Maximum limit (9999 mins): [qt/aqt/forms/preferences.ui#L368-L370](https://github.com/ankitects/anki/blob/main/qt/aqt/forms/preferences.ui#L368-L370)
 * - Reviewer preference mapping: [qt/aqt/preferences.py#L147](https://github.com/ankitects/anki/blob/main/qt/aqt/preferences.py#L147)
 */
@JvmInline
value class TimeboxTimeLimit(
    val minutes: Int,
) {
    init {
        require(minutes >= 0) { "Timebox limit minutes must be non-negative: $minutes" }
    }

    val isEnabled: Boolean
        get() = minutes > 0

    val duration: Duration
        get() = minutes.toDuration(DurationUnit.MINUTES)

    companion object {
        /**
         * Arbitrary initial duration (in minutes) chosen by AnkiDroid when timeboxing is toggled
         * from disabled (0 minutes) to enabled.
         */
        const val DEFAULT_TIMEBOX_MINUTES = 20

        /**
         * Minimum active timebox duration in minutes (0 represents disabled).
         */
        const val MIN_TIMEBOX_MINUTES = 1

        /**
         * Maximum allowed timebox duration in minutes.
         *
         * Sourced from Anki Desktop's QSpinBox configuration:
         * [qt/aqt/forms/preferences.ui#L368-L370](https://github.com/ankitects/anki/blob/main/qt/aqt/forms/preferences.ui#L368-L370)
         */
        const val MAX_TIMEBOX_MINUTES = 9999

        /** Valid input range for user-entered active timebox minutes. */
        val INPUT_RANGE = MIN_TIMEBOX_MINUTES..MAX_TIMEBOX_MINUTES

        val DEFAULT = TimeboxTimeLimit(DEFAULT_TIMEBOX_MINUTES)
        val DISABLED = TimeboxTimeLimit(0)

        fun fromDuration(duration: Duration): TimeboxTimeLimit {
            val mins = duration.toInt(DurationUnit.MINUTES)
            return TimeboxTimeLimit(mins)
        }

        /**
         * Creates a [TimeboxTimeLimit] if [minutes] falls within [INPUT_RANGE],
         * or returns `null` if the value is out of bounds.
         */
        fun fromMinutes(minutes: Int): TimeboxTimeLimit? {
            if (minutes !in INPUT_RANGE) return null
            return TimeboxTimeLimit(minutes)
        }

        /** Returns `true` if [minutes] is within [INPUT_RANGE]. */
        fun isValid(minutes: Int): Boolean = minutes in INPUT_RANGE

        /**
         * Creates a [TimeboxTimeLimit] if [minutes] falls within [INPUT_RANGE],
         * or returns `null` if the value is out of bounds.
         */
        fun fromValidInput(minutes: Int): TimeboxTimeLimit? = fromMinutes(minutes)
    }
}

/** Converts this [Duration] to a [TimeboxTimeLimit]. */
fun Duration.toTimeboxTimeLimit(): TimeboxTimeLimit = TimeboxTimeLimit.fromDuration(this)
