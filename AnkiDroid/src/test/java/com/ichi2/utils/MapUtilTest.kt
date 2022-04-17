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

import com.ichi2.utils.MapUtil.getKeyByValue
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.`is`
import org.hamcrest.Matchers.not
import org.junit.Before
import org.junit.Test
import java.util.HashMap

@KotlinCleanup("`is` -> equalTo")
@KotlinCleanup("Use Kotlin's HashMap instead of Java's")
class MapUtilTest {
    private lateinit var mMap: MutableMap<Int, String>
    @Before
    fun initializeMap() {
        mMap = HashMap()
        mMap[12] = "Anki"
        mMap[5] = "AnkiMobile"
        mMap[20] = "AnkiDroid"
        mMap[30] = "AnkiDesktop"
    }

    @Test
    fun getKeyByValueIsEqualTest() {
        assertThat(getKeyByValue(mMap, "AnkiDroid"), `is`(20))
    }

    @Test
    fun getKeyByValueIsNotEqualTest() {
        assertThat(getKeyByValue(mMap, "AnkiDesktop"), not(5))
    }
}
