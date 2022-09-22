/*
 *  Copyright (c) 2020 David Allison <davidallisongithub@gmail.com>
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

import android.view.KeyEvent
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.cardviewer.Gesture
import com.ichi2.anki.cardviewer.ViewerCommand
import com.ichi2.anki.testutil.MockReviewerUi
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.hasSize
import org.junit.Test
import org.junit.runner.RunWith
@RunWith(AndroidJUnit4::class)
class PeripheralKeymapTest {
    @Test
    fun testNumpadAction() {
        // #7736 Ensures that a numpad key is passed through (mostly testing num lock)
        val processed: MutableList<ViewerCommand> = ArrayList()

        val peripheralKeymap =
            PeripheralKeymap(MockReviewerUi.displayingAnswer()) { e: ViewerCommand, _: Gesture? -> processed.add(e) }
        peripheralKeymap.setup()

        peripheralKeymap.onKeyDown(
            KeyEvent.KEYCODE_NUMPAD_1,
            getNumpadEvent(KeyEvent.KEYCODE_NUMPAD_1)
        )
        peripheralKeymap.onKeyUp(
            KeyEvent.KEYCODE_NUMPAD_1,
            getNumpadEvent(KeyEvent.KEYCODE_NUMPAD_1)
        )
        assertThat<List<ViewerCommand>>(processed, hasSize(1))
        assertThat(
            processed[0],
            equalTo(ViewerCommand.FLIP_OR_ANSWER_EASE1)
        )
    }

    protected fun getNumpadEvent(keycode: Int): KeyEvent {
        return KeyEvent(0, 0, KeyEvent.ACTION_UP, keycode, 0, KeyEvent.META_NUM_LOCK_ON)
    }
}
