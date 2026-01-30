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
import kotlin.test.assertFalse

class HashUtilTest {
    @Test
    fun testHashSetInitCapacityLogic() {
        val setSmall = HashUtil.hashSetInit<String>(4)
        assertThat(setSmall, notNullValue())

        val setLarge = HashUtil.hashSetInit<String>(30)
        assertThat(setLarge, notNullValue())

        // verify the functional integrity
        setLarge.add("item")
        assertThat(setLarge.size, `is`(1))
    }

    @Test
    fun testHashMapInit() {
        // this ensures the factory method correctly instantiates a hashmap with the optimized capacity
        val map = HashUtil.hashMapInit<Int, String>(20)
        assertThat(map, notNullValue())

        map[100] = "optimized"
        assertThat(map[100], `is`("optimized"))
    }

    @Test
    fun testHashConsistencyAndOrder() {
        // tests the standalone hash(vararg values: Int) functionn
        val first = hash(10, 20, 30)
        val second = hash(10, 20, 30)
        val reversed = hash(30, 20, 10)

        // verify the same inputs produce identical results
        assertThat("Consistent inputs should yield identical hash", first, `is`(second))

        // Verify that the hash is sensitive to the order of elements
        assertFalse(first == reversed, "Different order should produce a different hash")
    }

    @Test
    fun testHashEmptyVarargs() {
        // boundary test: ensures the hash function handles zero arguments gracefully
        val emptyHash = hash()
        assertThat(emptyHash, notNullValue())
    }
}
