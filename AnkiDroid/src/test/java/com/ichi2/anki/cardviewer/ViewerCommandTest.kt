/*
 *  Copyright (c) 2021 David Allison <davidallisongithub@gmail.com>
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

package com.ichi2.anki.cardviewer

import org.junit.Assert.assertEquals
import org.junit.Test

class ViewerCommandTest {
    @Test
    fun preference_keys_are_not_changed() {
        val names = ViewerCommand.entries.joinToString(", ") { x -> x.preferenceKey }

        // NONE OF THESE SHOULD BE CHANGED OR A USER WILL LOSE THE ASSOCIATED PREFERENCES
        // Adds are acceptable
        // Deletes are acceptable if the binding should no longer exist
        assertEquals(
            "binding_SHOW_ANSWER, binding_FLIP_OR_ANSWER_EASE1, " +
                "binding_FLIP_OR_ANSWER_EASE2, binding_FLIP_OR_ANSWER_EASE3, binding_FLIP_OR_ANSWER_EASE4, " +
                "binding_UNDO, binding_REDO, binding_EDIT, binding_MARK, binding_BURY_CARD, " +
                "binding_SUSPEND_CARD, binding_DELETE, binding_PLAY_MEDIA, " +
                "binding_EXIT, binding_BURY_NOTE, binding_SUSPEND_NOTE, binding_TOGGLE_FLAG_RED, " +
                "binding_TOGGLE_FLAG_ORANGE, binding_TOGGLE_FLAG_GREEN, binding_TOGGLE_FLAG_BLUE, " +
                "binding_TOGGLE_FLAG_PINK, binding_TOGGLE_FLAG_TURQUOISE, binding_TOGGLE_FLAG_PURPLE, " +
                "binding_UNSET_FLAG, binding_PAGE_UP, binding_PAGE_DOWN, binding_TAG, binding_CARD_INFO, binding_ABORT_AND_SYNC, " +
                "binding_RECORD_VOICE, binding_REPLAY_VOICE, binding_TOGGLE_WHITEBOARD, binding_CLEAR_WHITEBOARD, " +
                "binding_CHANGE_WHITEBOARD_PEN_COLOR, binding_SHOW_HINT, binding_SHOW_ALL_HINTS, binding_ADD_NOTE, " +
                "binding_RESCHEDULE_NOTE",
            names,
        )
    }
}
