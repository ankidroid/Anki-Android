/*
 * Copyright (c) 2026 Kota Jagadeesh <kota.jagadesh123@gmail.com>
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.ichi2.utils

import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.`is`
import org.hamcrest.Matchers.notNullValue
import org.junit.Test

class HashUtilTest {
    @Test
    fun testHashSetInit() {
        val size = 10
        val set = HashUtil.hashSetInit<String>(size)
        assertThat(set, notNullValue())
        // verify we can add elements
        set.add("test")
        assertThat(set.size, `is`(1))
    }

    @Test
    fun testHashMapInit() {
        val size = 20
        val map = HashUtil.hashMapInit<Int, String>(size)
        assertThat(map, notNullValue())
        // verify we can put elements
        map[1] = "value"
        assertThat(map[1], `is`("value"))
    }

    @Test
    fun testHashVarargs() {
        // test the global hash(vararg values: Int) function
        val h1 = hash(1, 2, 3)
        val h2 = hash(1, 2, 3)
        val h3 = hash(3, 2, 1)

        // same values in the same order should produce same hash
        assertThat(h1, `is`(h2))

        // different order should (usually) produce different hash
        assertThat(h1 == h3, `is`(false))
    }

    @Test
    fun testHashEmpty() {
        // ensure that it doesn't crash with no arguments
        val h = hash()
        assertThat(h, notNullValue())
    }
}
