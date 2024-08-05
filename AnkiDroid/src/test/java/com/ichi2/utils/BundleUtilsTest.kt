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
package com.ichi2.utils

import android.os.Bundle
import com.ichi2.utils.BundleUtils.getNullableLong
import com.ichi2.utils.BundleUtils.requireLong
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.Mockito.anyString
import org.mockito.Mockito.eq
import org.mockito.Mockito.mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.kotlin.whenever
import kotlin.random.Random
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class BundleUtilsTest {
    @Test
    fun test_GetNullableLong_NotFound_ReturnsNull() {
        val b = mock(Bundle::class.java)

        whenever(b.containsKey(anyString())).thenReturn(false)

        val value = b.getNullableLong(KEY)

        verify(b, times(0)).getLong(eq(KEY))

        assertNull(value)
    }

    @Test
    fun test_GetNullableLong_Found_ReturnIt() {
        val expected = Random.Default.nextLong()
        val b = mock(Bundle::class.java)

        whenever(b.containsKey(anyString())).thenReturn(true)

        whenever(b.getLong(anyString())).thenReturn(expected)

        val value = b.getNullableLong(KEY)

        verify(b).getLong(eq(KEY))

        assertEquals(expected, value)
    }

    @Test
    fun test_RequireLong_NotFound_ThrowsException() {
        val mockedBundle = mock(Bundle::class.java)

        whenever(mockedBundle.containsKey(anyString())).thenReturn(false)

        assertFailsWith<IllegalStateException> { mockedBundle.requireLong(KEY) }

        verify(mockedBundle).containsKey(eq(KEY))
    }

    @Test
    fun test_RequireLong_Found_ReturnIt() {
        val expected = Random.Default.nextLong()
        val mockedBundle = mock(Bundle::class.java)

        whenever(mockedBundle.containsKey(anyString())).thenReturn(true)
        whenever(mockedBundle.getLong(anyString())).thenReturn(expected)

        val value = mockedBundle.requireLong(KEY)

        verify(mockedBundle).containsKey(eq(KEY))
        verify(mockedBundle).getLong(eq(KEY))

        assertEquals(expected, value)
    }

    companion object {
        const val KEY = "KEY"
    }
}
