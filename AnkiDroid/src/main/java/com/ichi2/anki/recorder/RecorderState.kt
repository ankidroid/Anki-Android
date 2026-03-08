/*
 * Copyright (c) 2025 Ashish Yadav <mailtoashish693@gmail.com>
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.anki.recorder

/**
 * Represents the various states of an audio recording lifecycle.
 */
sealed interface RecorderState {
    /**
     * The initial or terminal state where the recorder is inactive.
     * * In this state, no resources are being used for recording. This state is
     * reached before a recording starts or after it has been fully stopped.
     */
    object Idle : RecorderState

    /**
     * The state indicating that audio is currently being captured and processed.
     * * Transitions to [Paused] or [Idle] are typically valid from this state.
     */
    object Recording : RecorderState

    /**
     * The state where the recording session is kept alive but audio capture is suspended.
     * * From here, the recorder can typically be resumed back to [Recording]
     * or stopped to return to [Idle].
     */
    object Paused : RecorderState
}
