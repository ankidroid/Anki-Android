/*
 * Copyright (c) 2020 David Allison <davidallisongithub@gmail.com>
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

import android.annotation.SuppressLint
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.testutils.EmptyApplication
import junit.framework.TestCase.*
import org.json.JSONObject
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * This black box test was written without inspecting the non-free org.json sourcecode.
 */
@RunWith(AndroidJUnit4::class)
@Config(application = EmptyApplication::class)
@SuppressLint("CheckResult") // many usages: checking exceptions
class JSONObjectTest {
    private val mCorrectJsonBasic = "{\"key1\":\"value1\"}"
    private val mCorrectJsonNested = "{\"key1\":{\"key1a\":\"value1a\",\"key1b\":\"value1b\"},\"key2\":\"value2\"}"
    private val mCorrectJsonWithArray = "{\"key1\":\"value1\",\"key2\":[{\"key2a\":\"value2a\"},{\"key2b\":\"value2b\"}],\"key3\":\"value3\"}"
    private val mCorrectJsonNestedWithArray = "{\"key1\":{\"key1a\":\"value1a\",\"key1b\":\"value1b\"},\"key2\":[{\"key2a\":\"value2a\"},{\"key2b\":\"value2b\"}],\"key3\":\"value3\"}"

    private lateinit var mCorrectJsonObjectBasic: JSONObject
    private lateinit var mCorrectJsonObjectNested: JSONObject
    private lateinit var mCorrectJsonObjectWithArray: JSONObject
    private lateinit var mCorrectJsonObjectNestedWithArray: JSONObject
    lateinit var booleanMap: MutableMap<String, Boolean>

    @Before
    @Test
    fun setUp() {
        mCorrectJsonObjectBasic = JSONObject(mCorrectJsonBasic)
        mCorrectJsonObjectNested = JSONObject(mCorrectJsonNested)
        mCorrectJsonObjectWithArray = JSONObject(mCorrectJsonWithArray)
        mCorrectJsonObjectNestedWithArray = JSONObject(mCorrectJsonNestedWithArray)
        booleanMap = HashMap()
        for (i in 0..9) {
            booleanMap["key$i"] = i % 2 == 0
        }
    }

    @Test
    fun copyJsonTest() {
        Assert.assertEquals(mCorrectJsonObjectBasic.toString(), mCorrectJsonObjectBasic.deepClone().toString())
        Assert.assertEquals(mCorrectJsonObjectNested.toString(), mCorrectJsonObjectNested.deepClone().toString())
        Assert.assertEquals(mCorrectJsonObjectWithArray.toString(), mCorrectJsonObjectWithArray.deepClone().toString())
    }

    private class JSONObjectSubType : JSONObject() {
        /**
         * Sample overridden function
         */
        override fun toString(): String {
            return removeQuotes(super.toString())
        }
    }

    @Test
    fun deepCloneTest() {
        val jsonObjectSubType = JSONObjectSubType()

        // Clone base JSONObject Type into JSONObjectSubType
        mCorrectJsonObjectNestedWithArray.deepClonedInto(jsonObjectSubType)

        // Test by passing result of base JSONObject's toString() to removeQuotes()
        // This is already done in the JSONObjectSubType object
        Assert.assertEquals(removeQuotes(mCorrectJsonObjectNestedWithArray.toString()), jsonObjectSubType.toString())
    }

    /**
     * Tests that the a new copy is returned instead of a reference to the original.
     */
    @Test
    fun deepCloneReferenceTest() {
        val clone = mCorrectJsonObjectBasic.deepClone()
        // Both objects should point to different memory address
        Assert.assertNotEquals(clone, mCorrectJsonObjectBasic)
    }

    @Test
    fun fromMapTest() {
        val fromMapJsonObject = fromMap(booleanMap)
        for (i in 0..9) {
            Assert.assertEquals(fromMapJsonObject.getBoolean("key$i"), i % 2 == 0)
        }
    }

    companion object {
        /**
         * Wraps all the alphanumeric words in a string in quotes
         */
        private fun removeQuotes(string: String): String {
            return string.replace("\"([a-zA-Z0-9]+)\"".toRegex(), "$1")
        }
    }
}
