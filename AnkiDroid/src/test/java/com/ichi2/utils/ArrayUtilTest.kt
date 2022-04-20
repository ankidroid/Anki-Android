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
package com.ichi2.utils

import com.ichi2.utils.ArrayUtil.toArrayList
import org.hamcrest.CoreMatchers
import org.hamcrest.MatcherAssert
import org.junit.Test
import java.util.*

class ArrayUtilTest {
    private val mSampleItems = arrayOf(1, 2, 3, 4, 5, 6)
    @Test
    fun arrayToArrayList() {
        val list = arrayListOf<Int>()
        Collections.addAll(list, *mSampleItems)
        MatcherAssert.assertThat(toArrayList(mSampleItems), CoreMatchers.`is`(list))
    }
}
