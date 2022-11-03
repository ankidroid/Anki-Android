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

import com.ichi2.utils.CollectionUtils.combinations
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test

class CollectionUtilsTest {
    var testList = arrayListOf(1, 2, 3)

    @Test
    fun testCombinations() {
        val seq = testList.combinations().toList()
        assertThat(seq[0], equalTo(Pair(1, 2)))
        assertThat(seq[1], equalTo(Pair(1, 3)))
        assertThat(seq[2], equalTo(Pair(2, 3)))

        val seq2 = listOf<Int>().combinations().toList()
        assertThat("empty list returns nothing", seq2.size, equalTo(0))

        val seq3 = listOf(1).combinations().toList()
        assertThat("singleton list returns nothing", seq3.size, equalTo(0))
    }
}
