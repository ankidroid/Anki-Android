/****************************************************************************************
 * Copyright (c) 2020 Mike Hardy <mike@mikehardy.net>                                   *
 *                                                                                      *
 * This program is free software; you can redistribute it and/or modify it under        *
 * the terms of the GNU General Public License as published by the Free Software        *
 * Foundation; either version 3 of the License, or (at your option) any later           *
 * version.                                                                             *
 *                                                                                      *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY      *
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A      *
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.             *
 *                                                                                      *
 * You should have received a copy of the GNU General Public License along with         *
 * this program.  If not, see <http://www.gnu.org/licenses/>.                           *
 ****************************************************************************************/

package com.ichi2.anki.utils;

import com.ichi2.utils.DiffEngine;

import org.junit.Test;
import org.junit.runner.RunWith;


import androidx.test.ext.junit.runners.AndroidJUnit4;

import static org.junit.Assert.assertArrayEquals;

@RunWith(AndroidJUnit4.class)
public class DiffEngineTest {


    @Test
    public void testSimpleDiff() {
        DiffEngine diffEngine = new DiffEngine();
        String[] diffs = diffEngine.diffedHtmlStrings("typed", "correct");
        String[] expectedDiffs = {
                "<span class=\"typeBad\">corr</span><span class=\"typeGood\">e</span><span class=\"typeBad\">ct</span>",
                "<span class=\"typeMissed\">typ</span><span class=\"typeGood\">e</span><span class=\"typeMissed\">d</span>"
        };
        assertArrayEquals("Diff results were unexpected", expectedDiffs, diffs);
    }
}
