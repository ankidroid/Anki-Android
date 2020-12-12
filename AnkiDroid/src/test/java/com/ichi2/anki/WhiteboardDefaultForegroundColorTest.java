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

package com.ichi2.anki;

import android.content.Intent;
import android.graphics.Color;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.ParameterizedRobolectricTestRunner;

import java.util.Arrays;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@RunWith(ParameterizedRobolectricTestRunner.class)
public class WhiteboardDefaultForegroundColorTest extends RobolectricTest {

    @ParameterizedRobolectricTestRunner.Parameter()
    public boolean mIsInverted;

    @ParameterizedRobolectricTestRunner.Parameter(1)
    public int mExpectedResult;

    @ParameterizedRobolectricTestRunner.Parameters
    public static java.util.Collection<Object[]> initParameters() {
        return Arrays.asList(new Object[][] {
                { true, Color.WHITE },
                { false, Color.BLACK } });
    }

    @Test
    public void testDefaultForegroundColor() {
        assertThat(getForegroundColor(), is(mExpectedResult));
    }


    protected int getForegroundColor() {
        AbstractFlashcardViewer mock = super.startActivityNormallyOpenCollectionWithIntent(Reviewer.class, new Intent());
        return new Whiteboard(mock, mIsInverted).getForegroundColor();
    }
}
