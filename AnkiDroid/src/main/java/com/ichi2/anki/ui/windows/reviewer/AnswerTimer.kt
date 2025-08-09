/*
 * Copyright (c) 2025 Brayan Oliveira <69634269+brayandso@users.noreply.github.com>
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program. If not, see <http://www.gnu.org/licenses/>.
 */
package com.ichi2.anki.ui.windows.reviewer

import android.content.Context
import android.os.Parcelable
import android.os.SystemClock
import android.util.AttributeSet
import android.widget.Chronometer
import androidx.appcompat.widget.ThemeUtils
import com.ichi2.anki.R
import kotlinx.parcelize.Parcelize

/**
 * Improved version of a [Chronometer] aimed at handling Anki Decks' `Timer` configurations.
 *
 * Compared to a default [Chronometer], it can:
 * - Restore its status after configuration changes
 * - Stop the timer after [limitInMs] is reached.
 */
class AnswerTimer(
    context: Context,
    attributeSet: AttributeSet?,
) : Chronometer(context, attributeSet) {
    var limitInMs = Int.MAX_VALUE
    private var elapsedMillisBeforeStop = 0L
    private var isRunning = false

    init {
        setOnChronometerTickListener {
            if (hasReachedLimit()) {
                setTextColor(ThemeUtils.getThemeAttrColor(context, R.attr.maxTimerColor))
                stop()
            }
        }
    }

    override fun start() {
        super.start()
        isRunning = true
    }

    override fun stop() {
        elapsedMillisBeforeStop = SystemClock.elapsedRealtime() - base
        super.stop()
        isRunning = false
    }

    fun resume() {
        base = SystemClock.elapsedRealtime() - elapsedMillisBeforeStop
        start()
    }

    fun restart() {
        elapsedMillisBeforeStop = 0
        base = SystemClock.elapsedRealtime()
        setTextColor(ThemeUtils.getThemeAttrColor(context, android.R.attr.textColor))
        start()
    }

    private fun hasReachedLimit() = SystemClock.elapsedRealtime() - base >= limitInMs

    override fun onSaveInstanceState(): Parcelable {
        val elapsedMillis = if (isRunning) SystemClock.elapsedRealtime() - base else elapsedMillisBeforeStop
        return SavedState(
            state = super.onSaveInstanceState(),
            elapsedMs = elapsedMillis,
            isRunning = isRunning,
            limitInMs = limitInMs,
        )
    }

    override fun onRestoreInstanceState(state: Parcelable?) {
        if (state !is SavedState) {
            super.onRestoreInstanceState(state)
            return
        }
        super.onRestoreInstanceState(state.superState)

        elapsedMillisBeforeStop = state.elapsedMs
        isRunning = state.isRunning
        limitInMs = state.limitInMs

        base = SystemClock.elapsedRealtime() - elapsedMillisBeforeStop
        if (isRunning && !hasReachedLimit()) {
            super.start()
        }
    }

    @Parcelize
    private data class SavedState(
        val state: Parcelable?,
        val elapsedMs: Long,
        val isRunning: Boolean,
        val limitInMs: Int,
    ) : BaseSavedState(state)
}
