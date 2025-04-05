/*
 *  Copyright (c) 2023 David Allison <davidallisongithub@gmail.com>
 *
 *  This program is free software; you can redistribute it and/or modify it under
 *  the terms of the GNU General Public License as published by the Free Software
 *  Foundation; either version 3 of the License, or (at your option) any later
 *  version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY
 *  WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 *  PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along with
 *  this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.ichi2.anki.reviewer

import android.content.Context
import android.view.MotionEvent
import com.ichi2.anki.cardviewer.ViewerCommand

data class SingleAxisDetector(
    val axis: Axis,
    val command: ViewerCommand,
    val threshold: Float,
) {
    constructor(command: ViewerCommand, binding: Binding.AxisButtonBinding) : this(
        command = command,
        axis = binding.axis,
        threshold = binding.threshold,
    )

    /** If the command has been executed and we have not returned lower than the threshold */
    private var sentCommand: Boolean = false

    /**
     * If multiple events above the threshold are obtained, only return 1 command
     * until the value is under the threshold
     */
    private val debouncedCommand: ViewerCommand?
        get() {
            if (sentCommand) return null
            sentCommand = true
            return command
        }

    /**
     * Given a [MotionEvent], determine whether we've reached [threshold].
     *
     * If we have, and [command] has not been sent in the period that we reached the threshold
     * then send the command once and wait for the value to go back under the threshold
     *
     * @return [command] or `null`
     */
    fun getCommand(ev: MotionEvent): ViewerCommand? {
        // TODO: We may need to handle historical events as well
        val value = ev.getAxisValue(axis.motionEventValue)
        when {
            threshold > 0 -> {
                if (value >= threshold) return debouncedCommand
            }
            threshold < 0 -> {
                if (value <= threshold) return debouncedCommand
            }
        }
        sentCommand = false
        return null
    }

    fun toDisplayString(context: Context) = Binding.AxisButtonBinding(axis, threshold).toDisplayString(context)
}
