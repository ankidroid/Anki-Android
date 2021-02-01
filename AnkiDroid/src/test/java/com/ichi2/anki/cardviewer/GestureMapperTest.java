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
import android.view.View;
import android.view.ViewConfiguration;

import com.ichi2.anki.reviewer.Binding;
import com.ichi2.anki.reviewer.GestureMapper;
import com.ichi2.anki.reviewer.GestureProcessor;

import org.junit.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class GestureMapperTest {

    private final GestureMapper mSut = new GestureMapper();

    @Test
    public void zeroWidthReturnsNothing() {
        assertEquals(null, mSut.gesture(0, 10, 10, 10));
    }

    @Test
    public void zeroHeightReturnsNothing() {
        assertEquals(null, mSut.gesture(10, 0, 10, 10));
    }

    @Test
    public void testOobTop() {
       assertEquals(Gesture.TAP_TOP, mSut.gesture(100, 100, 50, -5));
    }

    @Test
    public void testOobLeft() {
        assertEquals(Gesture.TAP_LEFT, mSut.gesture(100, 100, -10, 50));
    }

    @Test
    public void testOobRight() {
        assertEquals(Gesture.TAP_RIGHT, mSut.gesture(100, 100, 200, 50));
    }

    @Test
    public void testOobBottom() {
        assertEquals(Gesture.TAP_BOTTOM, mSut.gesture(100, 100, 50, 200));
    }

    @Test
    public void testCenter() {
        assertEquals(Gesture.TAP_CENTER, mSut.gesture(100, 100, 50, 50));
    }
}
