/*
 *  Copyright (c) 2026 David Allison <davidallisongithub@gmail.com>
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

package com.ichi2.anki.common.utils.ext

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ListTest {
    @Test
    fun `indexOfOrNull returns first entry`() = assertEquals(0, listOf(1, 2, 1).indexOfOrNull(1))

    @Test
    fun `indexOfOrNull returns null if not found`() = assertNull(listOf(1, 2, 1).indexOfOrNull(3))

    @Test
    fun `indexOfOrNull returns null if list is empty`() = assertNull(listOf(1, 2, 1).indexOfOrNull(3))

    @Test
    fun `indexOfOrNull using block`() = assertEquals(1, listOf(1, 2, 1, 2).indexOfOrNull { it % 2 == 0 })

    @Test
    fun `indexOfOrNull using block returning null`() = assertNull(listOf(1, 2, 1).indexOfOrNull { it == 3 })
}
