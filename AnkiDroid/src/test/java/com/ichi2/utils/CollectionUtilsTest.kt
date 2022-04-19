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

import com.ichi2.testutils.AnkiAssert
import com.ichi2.utils.CollectionUtils.addAll
import com.ichi2.utils.CollectionUtils.getLastListElement
import org.hamcrest.CoreMatchers
import org.hamcrest.MatcherAssert
import org.junit.Test

class CollectionUtilsTest {
    var testList = arrayListOf(1, 2, 3)

    @Test
    fun testGetLastListElement() {
        MatcherAssert.assertThat(getLastListElement(testList), CoreMatchers.`is`(3))
    }

    @Test(expected = IndexOutOfBoundsException::class)
    fun testGetLastOnEmptyList() {
        val emptyList: List<Int> = ArrayList()
        getLastListElement(emptyList)
    }

    @Test
    fun testAddAll() {
        val toTest = arrayListOf<Int>()
        addAll(toTest, testList)
        AnkiAssert.assertEqualsArrayList(arrayOf(1, 2, 3), toTest)
    }
}
