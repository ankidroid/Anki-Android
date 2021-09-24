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

package com.ichi2.anki.reviewer;

import android.content.SharedPreferences;
import android.view.KeyEvent;

import com.ichi2.anki.cardviewer.ViewerCommand;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class PeripheralKeymapTest {
    @Test
    public void flagAndAnswerDoNotConflict() {
        List<ViewerCommand> processed = new ArrayList<>();

        PeripheralKeymap peripheralKeymap = new PeripheralKeymap(new MockReviewerUi(), processed::add);
        peripheralKeymap.setup(mock(SharedPreferences.class));
        KeyEvent event = mock(KeyEvent.class);
        when(event.getUnicodeChar()).thenReturn(0);
        when(event.isCtrlPressed()).thenReturn(true);
        when(event.getUnicodeChar(0)).thenReturn(49);
        when(event.getKeyCode()).thenReturn(KeyEvent.KEYCODE_1);

        assertThat((char) event.getUnicodeChar(), is('\0'));
        assertThat((char) event.getUnicodeChar(0), is('1'));
        peripheralKeymap.onKeyDown(KeyEvent.KEYCODE_1, event);

        assertThat(processed, hasSize(1));
        assertThat(processed.get(0), is(ViewerCommand.COMMAND_TOGGLE_FLAG_RED));
    }

    private static class MockReviewerUi implements ReviewerUi {

        @Override
        public ControlBlock getControlBlocked() {
            return null;
        }


        @Override
        public boolean isControlBlocked() {
            return false;
        }


        @Override
        public boolean isDisplayingAnswer() {
            return false;
        }
    }
}
