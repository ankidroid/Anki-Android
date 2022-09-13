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
package com.ichi2.utils

import com.ichi2.testutils.AnkiAssert.assertEqualsArrayList
import com.ichi2.utils.CollectionUtils.combinations
import org.junit.Test
import kotlin.test.assertEquals

class CollectionUtilsTest {
    var testList = arrayListOf(1, 2, 3)

    @Test
    fun testAddAll() {
        val toTest = arrayListOf<Int>()
        CollectionUtils.addAll(toTest, testList)
        assertEqualsArrayList(arrayOf(1, 2, 3), toTest)
    }

    @Test
    fun testCombinations() {
        val seq = testList.combinations().toList()
        assertEquals(seq[0], Pair(1, 2))
        assertEquals(seq[1], Pair(1, 3))
        assertEquals(seq[2], Pair(2, 3))

        val seq2 = listOf<Int>().combinations().toList()
        assertEquals(seq2.size, 0, "empty list returns nothing")

        val seq3 = listOf(1).combinations().toList()
        assertEquals(seq3.size, 0, "singleton list returns nothing")
    }
}
