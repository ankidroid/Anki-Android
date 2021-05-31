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

package com.ichi2.anki.reviewer;


import android.view.ViewConfiguration;

import com.ichi2.anki.reviewer.GestureMapper.GestureSegment;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import static com.ichi2.anki.reviewer.GestureMapper.GestureSegment.BOTTOM_CENTER;
import static com.ichi2.anki.reviewer.GestureMapper.GestureSegment.MIDDLE_CENTER;
import static com.ichi2.anki.reviewer.GestureMapper.GestureSegment.MIDDLE_LEFT;
import static com.ichi2.anki.reviewer.GestureMapper.GestureSegment.MIDDLE_RIGHT;
import static com.ichi2.anki.reviewer.GestureMapper.GestureSegment.TOP_CENTER;
import static com.ichi2.anki.reviewer.GestureMapper.GestureSegment.fromTap;
import static com.ichi2.anki.cardviewer.ViewerCommand.COMMAND_NOTHING;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;

public class GestureMapperTest {
    private final GestureMapper mSut = new GestureMapper();

    private static MockedStatic<ViewConfiguration> utilities;

    @BeforeClass
    public static void before() {
        utilities = Mockito.mockStatic(ViewConfiguration.class);
        utilities.when(() -> ViewConfiguration.get(any())).thenReturn(mock(ViewConfiguration.class));
    }

    @AfterClass
    public static void after() {
        utilities.close();
    }


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


}
