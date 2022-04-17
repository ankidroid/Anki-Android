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
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.Mockito.anyString
import org.mockito.Mockito.eq
import org.mockito.Mockito.mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import java.util.*
import kotlin.test.assertNull

@KotlinCleanup("rename `val` variables")
@KotlinCleanup("`when` -> whenever")
class BundleUtilsTest {
    @Test
    fun test_GetNullableLong_NullBundle_ReturnsNull() {
        val `val` = BundleUtils.getNullableLong(null, KEY)
        assertNull(`val`)
    }

    @Test
    fun test_GetNullableLong_NotFound_ReturnsNull() {
        val b = mock(Bundle::class.java)

        `when`(b.containsKey(anyString())).thenReturn(false)

        val `val` = BundleUtils.getNullableLong(b, KEY)

        verify(b, times(0)).getLong(eq(KEY))

        assertNull(`val`)
    }

    @Test
    @KotlinCleanup("Use Kotlin's Random instead of Java's")
    fun test_GetNullableLong_Found_ReturnIt() {
        val expected = Random().nextLong()
        val b = mock(Bundle::class.java)

        `when`(b.containsKey(anyString())).thenReturn(true)

        `when`(b.getLong(anyString())).thenReturn(expected)

        val `val` = BundleUtils.getNullableLong(b, KEY)

        verify(b).getLong(eq(KEY))

        assertEquals(expected, `val`)
    }

    companion object {
        const val KEY = "KEY"
    }
}
