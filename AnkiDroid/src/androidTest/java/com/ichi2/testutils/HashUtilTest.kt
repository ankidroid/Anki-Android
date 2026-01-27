package com.ichi2.testutils

import com.ichi2.utils.HashUtil
import com.ichi2.utils.hash
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
        // verifyy we can add elements
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
