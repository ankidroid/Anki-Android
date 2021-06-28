/****************************************************************************************
 *                                                                                      *
 * Copyright (c) 2021 Shridhar Goel <shridhar.goel@gmail.com>                           *
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

package com.ichi2.utils;

import org.junit.Test;
import org.junit.runner.RunWith;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.lessThan;

@RunWith(AndroidJUnit4.class)
public class NamedJSONComparatorTest {

    @Test
    public void checkIfReturnsCorrectValueForSameNames() {
        JSONObject firstObject = new JSONObject();
        firstObject.put("name", "TestName");

        JSONObject secondObject = new JSONObject();
        secondObject.put("name", "TestName");

        assertThat(NamedJSONComparator.INSTANCE.compare(firstObject, secondObject), equalTo(0));
    }

    @Test
    public void checkIfReturnsCorrectValueForDifferentNames() {
        JSONObject firstObject = new JSONObject();
        firstObject.put("name", "TestName1");

        JSONObject secondObject = new JSONObject();
        secondObject.put("name", "TestName2");

        assertThat(NamedJSONComparator.INSTANCE.compare(firstObject, secondObject), lessThan(0));
    }
}
