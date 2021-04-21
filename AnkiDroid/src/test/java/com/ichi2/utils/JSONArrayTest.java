/****************************************************************************************
 * Copyright (c) 2021 Ammar Hamed <ammarhamed64@gmail.com>                              *
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

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import androidx.test.ext.junit.runners.AndroidJUnit4;

@RunWith(AndroidJUnit4.class)
public class JSONArrayTest {
    JSONArray mArray;
    int mInitialArrayLength;


    @Before
    public void initializeNewObject() {
        mArray = new JSONArray();
    }


    public void initializeArray() {
        for (int i = 0, value = 1; i < 100; i++) {
            mArray.put(i, value);
        }
        mInitialArrayLength = 100;
    }


    @Test
    public void testPut_existingArray() {
        initializeArray();
        for (int i = 10; i < 20; i++) {
            mArray.put(i, 20);
        }
        for (int i = 10; i < 20; i++) {
            Assert.assertEquals(20, mArray.get(i));
        }
        for (int i = 0, j = 20; i < 10; i++, j++) {
            Assert.assertEquals(1, mArray.get(i));
        }
        Assert.assertEquals(mInitialArrayLength, mArray.length());

    }


    @Test
    public void testPut_emptyArray() {
        mArray.put(200, null);
        Assert.assertEquals(201, mArray.length());
    }


    /**
     * Actually the array will not expand as this value is set to the in the index in arraylist not add to .
     */
    @Test(expected = AssertionError.class)
    public void testPut_atTheBeginningAndNotExpand() {
        initializeArray();
        mArray.put(0, 200);
        Assert.assertEquals(mInitialArrayLength + 1, mArray.length());
    }


    /**
     * Actually the array will not expand as this value is set to the in the index in arraylist not add to .
     */
    @Test(expected = AssertionError.class)
    public void testPut_atTheMiddleAndNotExpand() {
        initializeArray();
        mArray.put(mArray.length() / 2, 1);
        Assert.assertEquals(mInitialArrayLength + 1, mArray.length());
    }


    /**
     * Actually the array will  expand as this value is set to the in the index in arraylist,
     * but he check first if the array.length < index , then fill every empty indexes with null until reach index  .
     */
    @Test
    public void testPut_atTheEndOAndExpand() {
        initializeArray();
        mArray.put(102, 1);
        Assert.assertEquals(103, mArray.length());
    }


    @Test(expected = IndexOutOfBoundsException.class)
    public void testPut_indexOutOfBoundary() {
        mArray.put(-1, 100);
    }


    @Test
    public void testGet_existingValue() {
        mArray.put(200, 1150);
        Assert.assertEquals(1150, mArray.get(200));

    }


    @Test(expected = JSONException.class)
    public void testGet_null() {
        mArray.put(199, null);
        mArray.get(199);
    }


    @Test(expected = JSONException.class)
    public void testGet_valueOutOfIndex() {
        mArray.get(200);
    }
}
