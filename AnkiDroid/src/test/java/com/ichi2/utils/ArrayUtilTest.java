/*
 Copyright (c) 2021 Mrudul Tora <mrudultora@gmail.com>

 This program is free software; you can redistribute it and/or modify it under
 the terms of the GNU General Public License as published by the Free Software
 Foundation; either version 3 of the License, or (at your option) any later
 version.

 This program is distributed in the hope that it will be useful, but WITHOUT ANY
 WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 PARTICULAR PURPOSE. See the GNU General Public License for more details.

 You should have received a copy of the GNU General Public License along with
 this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.utils;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public class ArrayUtilTest {
    private final Integer[] mSampleItems = new Integer[] {1, 2, 3, 4, 5, 6};


    @Test
    public void arrayToArrayList() {
        List<Integer> list = new ArrayList<>();
        Collections.addAll(list, mSampleItems);
        assertThat(ArrayUtil.toArrayList(mSampleItems), is(list));
    }
}