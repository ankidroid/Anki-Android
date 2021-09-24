/*
 *  Copyright (c) 2021 Aditya Srivastav <iamaditya2009@gmail.com>
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

package com.ichi2.utils;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static com.ichi2.testutils.AnkiAssert.assertEqualsArrayList;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public class CollectionUtilsTest {

    List<Integer> mTestList = new ArrayList<Integer>() {{
        add(1);
        add(2);
        add(3);
    }};

    @Test
    public void testGetLastListElement() {
        assertThat(CollectionUtils.getLastListElement(mTestList), is(3));
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void testGetLastOnEmptyList() {
        List<Integer> emptyList = new ArrayList<>();
        CollectionUtils.getLastListElement(emptyList);
    }

    @Test
    public void testAddAll() {
        List<Integer> toTest = new ArrayList<>();
        CollectionUtils.addAll(toTest, mTestList);
        assertEqualsArrayList(new Integer [] {1, 2, 3}, toTest);
    }
}
