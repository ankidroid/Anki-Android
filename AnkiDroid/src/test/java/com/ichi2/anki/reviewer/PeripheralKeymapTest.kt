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

import android.content.SharedPreferences
import android.view.KeyEvent
import com.ichi2.anki.cardviewer.ViewerCommand
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.hasSize
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.kotlin.whenever

class PeripheralKeymapTest {
    @Test
    fun flagAndAnswerDoNotConflict() {
        val processed: MutableList<ViewerCommand> = ArrayList()

        val peripheralKeymap = PeripheralKeymap(MockReviewerUi()) { e: ViewerCommand, _ -> processed.add(e) }
        peripheralKeymap.setup(mock(SharedPreferences::class.java))
        val event = mock(KeyEvent::class.java)
        whenever(event.unicodeChar).thenReturn(0)
        whenever(event.isCtrlPressed).thenReturn(true)
        whenever(event.getUnicodeChar(0)).thenReturn(49)
        whenever(event.keyCode).thenReturn(KeyEvent.KEYCODE_1)

        assertThat(event.unicodeChar.toChar(), equalTo('\u0000'))
        assertThat(event.getUnicodeChar(0).toChar(), equalTo('1'))
        peripheralKeymap.onKeyDown(KeyEvent.KEYCODE_1, event)

        assertThat<List<ViewerCommand>>(processed, hasSize(1))
        assertThat(processed[0], equalTo(ViewerCommand.TOGGLE_FLAG_RED))
    }

    private class MockReviewerUi : ReviewerUi {
        override val isDisplayingAnswer: Boolean
            get() = false
    }
}
