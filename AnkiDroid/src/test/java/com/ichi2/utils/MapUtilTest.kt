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

import org.junit.Before;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static com.ichi2.utils.MapUtil.getKeyByValue;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;

public class MapUtilTest {
    private Map<Integer, String> mMap;


    @Before
    public void initializeMap() {
        mMap = new HashMap<>();
        mMap.put(12, "Anki");
        mMap.put(5, "AnkiMobile");
        mMap.put(20, "AnkiDroid");
        mMap.put(30, "AnkiDesktop");
    }


    @Test
    public void getKeyByValueIsEqualTest() {
        assertThat(getKeyByValue(mMap, "AnkiDroid"), is(20));
    }


    @Test
    public void getKeyByValueIsNotEqualTest() {
        assertThat(getKeyByValue(mMap, "AnkiDesktop"), not(5));
    }
}