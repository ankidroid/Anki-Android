/*
 Copyright (c) 2021 Tarek Mohamed Abdalla <tarekkma@gmail.com>

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

import android.os.Bundle;

import org.junit.Test;

import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class BundleUtilsTest {

    public static final String KEY = "KEY";


    @Test
    public void test_GetNullableLong_NullBundle_ReturnsNull() {
        Long val = BundleUtils.getNullableLong(null, KEY);
        assertNull(val);
    }


    @Test
    public void test_GetNullableLong_NotFound_ReturnsNull() {
        final Bundle b = mock(Bundle.class);

        when(b.containsKey(anyString())).thenReturn(false);

        Long val = BundleUtils.getNullableLong(b, KEY);

        verify(b, times(0)).getLong(eq(KEY));

        assertNull(val);
    }


    @Test
    public void test_GetNullableLong_Found_ReturnIt() {
        final Long expected = new Random().nextLong();
        final Bundle b = mock(Bundle.class);

        when(b.containsKey(anyString())).thenReturn(true);

        when(b.getLong(anyString())).thenReturn(expected);

        Long val = BundleUtils.getNullableLong(b, KEY);

        verify(b).getLong(eq(KEY));

        assertEquals(expected, val);
    }
}