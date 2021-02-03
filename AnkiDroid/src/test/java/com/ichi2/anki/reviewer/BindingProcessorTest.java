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

import com.ichi2.anki.cardviewer.Gesture;
import com.ichi2.anki.cardviewer.ViewerCommand;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;

public class BindingProcessorTest {
    @Test
    public void tabs() {
        List<ViewerCommand> processed = new ArrayList<>();

        GestureProcessor processor = new GestureProcessor(processed::add);
        processor.add(Binding.gesture(Gesture.DOUBLE_TAP), ViewerCommand.TOGGLE_FLAG_RED);
        processor.add(Binding.gesture(Gesture.LONG_TAP), ViewerCommand.TOGGLE_FLAG_GREEN);

        processor.onDoubleTab();
        assertThat(processed, hasSize(1));
        assertThat(processed.get(0), is(ViewerCommand.TOGGLE_FLAG_RED));

        processor.onLongTap();
        assertThat(processed, hasSize(2));
        assertThat(processed.get(1), is(ViewerCommand.TOGGLE_FLAG_GREEN));
    }
}
