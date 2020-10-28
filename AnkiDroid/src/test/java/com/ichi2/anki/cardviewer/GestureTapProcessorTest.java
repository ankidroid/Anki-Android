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

package com.ichi2.anki.cardviewer;

import android.content.SharedPreferences;

import com.ichi2.anki.cardviewer.GestureTapProcessor.GestureSegment;

import org.junit.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

import static com.ichi2.anki.cardviewer.GestureTapProcessor.GestureSegment.BOTTOM_CENTER;
import static com.ichi2.anki.cardviewer.GestureTapProcessor.GestureSegment.MIDDLE_LEFT;
import static com.ichi2.anki.cardviewer.GestureTapProcessor.GestureSegment.MIDDLE_RIGHT;
import static com.ichi2.anki.cardviewer.GestureTapProcessor.GestureSegment.TOP_CENTER;
import static com.ichi2.anki.cardviewer.GestureTapProcessor.GestureSegment.MIDDLE_CENTER;
import static com.ichi2.anki.cardviewer.GestureTapProcessor.GestureSegment.fromTap;
import static com.ichi2.anki.cardviewer.ViewerCommand.COMMAND_NOTHING;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class GestureTapProcessorTest {

    private final GestureTapProcessor mSut = new GestureTapProcessor();

    @Test
    public void zeroWidthReturnsNothing() {
        assertThat(mSut.getCommandFromTap(0, 10, 10, 10), is(COMMAND_NOTHING));
    }

    @Test
    public void zeroHeightReturnsNothing() {
        assertThat(mSut.getCommandFromTap(10, 0, 10, 10), is(COMMAND_NOTHING));
    }

    @Test
    public void testOobTop() {
       GestureSegment res =  fromTap(100, 100, 50, -5);
       assertThat(res, is(TOP_CENTER));
    }

    @Test
    public void testOobLeft() {
        GestureSegment res =  fromTap(100, 100, -10, 50);
        assertThat(res, is(MIDDLE_LEFT));
    }

    @Test
    public void testOobRight() {
        GestureSegment res =  fromTap(100, 100, 200, 50);
        assertThat(res, is(MIDDLE_RIGHT));
    }

    @Test
    public void testOobBottom() {
        GestureSegment res =  fromTap(100, 100, 50, 200);
        assertThat(res, is(BOTTOM_CENTER));
    }

    @Test
    public void testCenter() {
        GestureSegment res =  fromTap(100, 100, 50, 50);
        assertThat(res, is(MIDDLE_CENTER));
    }
    
    @Test
    public void integrationTest() {
        SharedPreferences prefs = mock(SharedPreferences.class, Mockito.RETURNS_DEEP_STUBS);

        when(prefs.getString(ArgumentMatchers.anyString(), ArgumentMatchers.anyString())).thenReturn("0");
        when(prefs.getString(eq("gestureTapCenter"), ArgumentMatchers.anyString())).thenReturn("1");
        when(prefs.getBoolean(eq("gestureCornerTouch"), ArgumentMatchers.anyBoolean())).thenReturn(true);

        mSut.init(prefs);

        int command = mSut.getCommandFromTap(100, 100, 50, 50);
        assertThat(command, is(1));
    }
}
