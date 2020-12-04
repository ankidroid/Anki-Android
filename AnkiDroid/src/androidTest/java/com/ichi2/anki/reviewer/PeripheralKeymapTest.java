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

import android.view.KeyEvent;

import com.ichi2.anki.cardviewer.ViewerCommand;
import com.ichi2.anki.testutil.MockReviewerUi;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;

@RunWith(AndroidJUnit4.class)
public class PeripheralKeymapTest {

    @Test
    public void testNumpadAction() {
        // #7736 Ensures that a numpad key is passed through (mostly testing num lock)
        List<Integer> processed = new ArrayList<>();

        PeripheralKeymap peripheralKeymap = new PeripheralKeymap(MockReviewerUi.displayingAnswer(), processed::add);
        peripheralKeymap.setup();

        peripheralKeymap.onKeyUp(KeyEvent.KEYCODE_NUMPAD_1, getNumpadEvent(KeyEvent.KEYCODE_NUMPAD_1));


        assertThat(processed, hasSize(1));
        assertThat(processed.get(0), is(ViewerCommand.COMMAND_ANSWER_FIRST_BUTTON));
    }


    @NonNull
    protected KeyEvent getNumpadEvent(@SuppressWarnings("SameParameterValue") int keycode) {
        return new KeyEvent(0, 0, KeyEvent.ACTION_UP, keycode, 0, KeyEvent.META_NUM_LOCK_ON);
    }
}
