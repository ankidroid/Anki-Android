/*
 Copyright (c) 2020 David Allison <davidallisongithub@gmail.com>

 This program is free software; you can redistribute it and/or modify it under
 the terms of the GNU General Public License as published by the Free Software
 Foundation; either version 3 of the License, or (at your option) any later
 version.

 This program is distributed in the hope that it will be useful, but WITHOUT ANY
 WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 PARTICULAR PURPOSE. See the GNU General Public License for more details.

 You should have received a copy of the GNU General Public License along with
 this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.anki.reviewer
import androidx.annotation.IdRes

interface ReviewerUi {
    /** How to block UI buttons.  */
    enum class ControlBlock {
        /** Buttons are functional */
        UNBLOCKED,

        /**Don't record click; as it's ambiguous whether it would apply to next or previous card.
         * We expect the next card load quickly, so no need to give visual feedback to user,
         * which would be considered as flickering.  */
        QUICK,

        /**Don't record click; as it's ambiguous whether it would apply to next or previous card.
         * We expect the next card may take time to load, as scheduler needs to recompute its queues;
         * so we show the button get deactivated.  */
        SLOW
    }

    val controlBlocked: ControlBlock?
    val isControlBlocked: Boolean
    val isDisplayingAnswer: Boolean
    fun isActionButton(@IdRes id: Int): Boolean
}
