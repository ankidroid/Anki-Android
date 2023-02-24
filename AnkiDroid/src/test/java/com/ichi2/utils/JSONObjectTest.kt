/*
 * Copyright (C) 2010 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * The only difference with the original is the package, and hence the class actually tested.
 * 2022-03-11: Converted to Kotlin
 *
 * With the exception of clearly indicated code at the bottom which is:
 *
 Copyright (c) 2020 David Allison <davidallisongithub@gmail.com>

Most of the code is:
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

import android.annotation.SuppressLint
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.testutils.EmptyApplication
import junit.framework.TestCase.*
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.core.IsNull.notNullValue
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.json.JSONTokener
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.math.BigDecimal
import java.math.BigInteger
import kotlin.test.assertFailsWith

/**
 * This black box test was written without inspecting the non-free org.json sourcecode.
 */
@RunWith(AndroidJUnit4::class)
@Config(application = EmptyApplication::class)
@SuppressLint("CheckResult") // many usages: checking exceptions
class JSONObjectTest {
    @Test
    fun testEmptyObject() {
        val testObject = JSONObject()
        assertEquals(0, testObject.length())
        // bogus (but documented) behaviour: returns null rather than the empty object!
        assertNull(testObject.names())
        // returns null rather than an empty array!
        assertNull(testObject.toJSONArray(JSONArray()))
        assertEquals("{}", testObject.toString())
        assertEquals("{}", testObject.toString(5))
        assertFailsWith<JSONException> {
            testObject["foo"]
        }
        assertFailsWith<JSONException> {
            testObject.getBoolean("foo")
        }
        assertFailsWith<JSONException> {
            testObject.getDouble("foo")
        }
        assertFailsWith<JSONException> {
            testObject.getInt("foo")
        }
        assertFailsWith<JSONException> {
            testObject.getJSONArray("foo")
        }
        assertFailsWith<JSONException> {
            testObject.getJSONObject("foo")
        }
        assertFailsWith<JSONException> {
            testObject.getLong("foo")
        }
        assertFailsWith<JSONException> {
            testObject.getString("foo")
        }
        assertFalse(testObject.has("foo"))
        assertTrue(testObject.isNull("foo")) // isNull also means "is not present"
        assertNull(testObject.opt("foo"))
        assertEquals(false, testObject.optBoolean("foo"))
        assertEquals(true, testObject.optBoolean("foo", true))
        assertEquals(Double.NaN, testObject.optDouble("foo"), 0.0)
        assertEquals(5.0, testObject.optDouble("foo", 5.0), 0.0)
        assertEquals(0, testObject.optInt("foo"))
        assertEquals(5, testObject.optInt("foo", 5))
        assertEquals(null, testObject.optJSONArray("foo"))
        assertEquals(null, testObject.optJSONObject("foo"))
        assertEquals(0, testObject.optLong("foo"))
        assertEquals(Long.MAX_VALUE - 1, testObject.optLong("foo", Long.MAX_VALUE - 1))
        assertEquals("", testObject.optString("foo")) // empty string is default!
        assertEquals("bar", testObject.optString("foo", "bar"))
        assertNull(testObject.remove("foo"))
    }

    @Test
    fun testEqualsAndHashCode() {
        val a = JSONObject()
        val b = JSONObject()
        // JSON object doesn't override either equals or hashCode (!)
        assertFalse(a == b)
        assertEquals(a.hashCode(), System.identityHashCode(a))
    }

    @Test
    fun testGet() {
        val testObject = JSONObject()
        val value = Any()
        testObject.put("foo", value)
        testObject.put("bar", Any())
        testObject.put("baz", Any())
        assertSame(value, testObject["foo"])
        assertFailsWith<JSONException> {
            testObject["FOO"]
        }
    }

    @Test
    fun testPut() {
        val testObject = JSONObject()
        assertSame(testObject, testObject.put("foo", true))
        testObject.put("foo", false)
        assertEquals(false, testObject["foo"])
        testObject.put("foo", 5.0)
        assertEquals(5.0, testObject["foo"])
        testObject.put("foo", 0)
        assertEquals(0, testObject["foo"])
        testObject.put("bar", Long.MAX_VALUE - 1)
        assertEquals(Long.MAX_VALUE - 1, testObject["bar"])
        testObject.put("baz", "x")
        assertEquals("x", testObject["baz"])
        testObject.put("bar", JSONObject.NULL)
        assertSame(JSONObject.NULL, testObject["bar"])
    }

    @Test
    fun testPutNullRemoves() {
        val testObject = JSONObject()
        testObject.put("foo", "bar")
        testObject.put("foo", null)
        assertEquals(0, testObject.length())
        assertFalse(testObject.has("foo"))
        assertFailsWith<JSONException> {
            testObject["foo"]
        }
    }

    @Test
    fun testPutOpt() {
        val testObject = JSONObject()
        testObject.put("foo", "bar")
        testObject.putOpt("foo", null)
        assertEquals("bar", testObject["foo"])
        testObject.putOpt(null, null)
        assertEquals(1, testObject.length())
        testObject.putOpt(null, "bar")
        assertEquals(1, testObject.length())
    }

    @Test
    fun testPutOptUnsupportedNumbers() {
        val testObject = JSONObject()
        assertFailsWith<JSONException> {
            testObject.putOpt("foo", Double.NaN)
        }
        assertFailsWith<JSONException> {
            testObject.putOpt("foo", Double.NEGATIVE_INFINITY)
        }
        assertFailsWith<JSONException> {
            testObject.putOpt("foo", Double.POSITIVE_INFINITY)
        }
    }

    @Test
    fun testRemove() {
        val testObject = JSONObject()
        testObject.put("foo", "bar")
        assertEquals(null, testObject.remove(null))
        assertEquals(null, testObject.remove(""))
        assertEquals(null, testObject.remove("bar"))
        assertEquals("bar", testObject.remove("foo"))
        assertEquals(null, testObject.remove("foo"))
    }

    @Test
    fun testBooleans() {
        val testObject = JSONObject()
        testObject.put("foo", true)
        testObject.put("bar", false)
        testObject.put("baz", "true")
        testObject.put("quux", "false")
        assertEquals(4, testObject.length())
        assertEquals(true, testObject.getBoolean("foo"))
        assertEquals(false, testObject.getBoolean("bar"))
        assertEquals(true, testObject.getBoolean("baz"))
        assertEquals(false, testObject.getBoolean("quux"))
        assertFalse(testObject.isNull("foo"))
        assertFalse(testObject.isNull("quux"))
        assertTrue(testObject.has("foo"))
        assertTrue(testObject.has("quux"))
        assertFalse(testObject.has("missing"))
        assertEquals(true, testObject.optBoolean("foo"))
        assertEquals(false, testObject.optBoolean("bar"))
        assertEquals(true, testObject.optBoolean("baz"))
        assertEquals(false, testObject.optBoolean("quux"))
        assertEquals(false, testObject.optBoolean("missing"))
        assertEquals(true, testObject.optBoolean("foo", true))
        assertEquals(false, testObject.optBoolean("bar", true))
        assertEquals(true, testObject.optBoolean("baz", true))
        assertEquals(false, testObject.optBoolean("quux", true))
        assertEquals(true, testObject.optBoolean("missing", true))
        testObject.put("foo", "truE")
        testObject.put("bar", "FALSE")
        assertEquals(true, testObject.getBoolean("foo"))
        assertEquals(false, testObject.getBoolean("bar"))
        assertEquals(true, testObject.optBoolean("foo"))
        assertEquals(false, testObject.optBoolean("bar"))
        assertEquals(true, testObject.optBoolean("foo", false))
        assertEquals(false, testObject.optBoolean("bar", false))
    }

    @Test
    fun testNumbers() {
        val testObject = JSONObject()
        testObject.put("foo", Double.MIN_VALUE)
        testObject.put("bar", 9223372036854775806L)
        testObject.put("baz", Double.MAX_VALUE)
        testObject.put("quux", -0.0)
        assertEquals(4, testObject.length())
        val toString = testObject.toString()
        assertTrue(toString, toString.contains("\"foo\":4.9E-324"))
        assertTrue(toString, toString.contains("\"bar\":9223372036854775806"))
        assertTrue(toString, toString.contains("\"baz\":1.7976931348623157E308"))
        // toString() and getString() return different values for -0d!
        assertTrue(
            toString,
            toString.contains("\"quux\":-0}") || // no trailing decimal point
                toString.contains("\"quux\":-0,")
        )
        assertEquals(Double.MIN_VALUE, testObject["foo"])
        assertEquals(9223372036854775806L, testObject["bar"])
        assertEquals(Double.MAX_VALUE, testObject["baz"])
        assertEquals(-0.0, testObject["quux"])
        assertEquals(Double.MIN_VALUE, testObject.getDouble("foo"), 0.0)
        assertEquals(9.223372036854776E18, testObject.getDouble("bar"), 0.0)
        assertEquals(Double.MAX_VALUE, testObject.getDouble("baz"), 0.0)
        assertEquals(-0.0, testObject.getDouble("quux"), 0.0)
        assertEquals(0, testObject.getLong("foo"))
        assertEquals(9223372036854775806L, testObject.getLong("bar"))
        assertEquals(Long.MAX_VALUE, testObject.getLong("baz"))
        assertEquals(0, testObject.getLong("quux"))
        assertEquals(0, testObject.getInt("foo"))
        assertEquals(-2, testObject.getInt("bar"))
        assertEquals(Int.MAX_VALUE, testObject.getInt("baz"))
        assertEquals(0, testObject.getInt("quux"))
        assertEquals(Double.MIN_VALUE, testObject.opt("foo"))
        assertEquals(9223372036854775806L, testObject.optLong("bar"))
        assertEquals(Double.MAX_VALUE, testObject.optDouble("baz"), 0.0)
        assertEquals(0, testObject.optInt("quux"))
        assertEquals(Double.MIN_VALUE, testObject.opt("foo"))
        assertEquals(9223372036854775806L, testObject.optLong("bar"))
        assertEquals(Double.MAX_VALUE, testObject.optDouble("baz"), 0.0)
        assertEquals(0, testObject.optInt("quux"))
        assertEquals(Double.MIN_VALUE, testObject.optDouble("foo", 5.0), 0.0)
        assertEquals(9223372036854775806L, testObject.optLong("bar", 1L))
        assertEquals(Long.MAX_VALUE, testObject.optLong("baz", 1L))
        assertEquals(0, testObject.optInt("quux", -1))
        assertEquals("4.9E-324", testObject.getString("foo"))
        assertEquals("9223372036854775806", testObject.getString("bar"))
        assertEquals("1.7976931348623157E308", testObject.getString("baz"))
        assertEquals("-0.0", testObject.getString("quux"))
    }

    @Test
    fun testFloats() {
        val testObject = JSONObject()
        assertFailsWith<JSONException> {
            testObject.put("foo", Float.NaN)
        }
        assertFailsWith<JSONException> {
            testObject.put("foo", Float.NEGATIVE_INFINITY)
        }
        assertFailsWith<JSONException> {
            testObject.put("foo", Float.POSITIVE_INFINITY)
        }
    }

    @Test
    @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
    fun testOtherNumbers() {
        val nan: java.lang.Number = object : java.lang.Number() {
            override fun intValue(): Int {
                throw UnsupportedOperationException()
            }

            override fun longValue(): Long {
                throw UnsupportedOperationException()
            }

            override fun floatValue(): Float {
                throw UnsupportedOperationException()
            }

            override fun doubleValue(): Double {
                return Double.NaN
            }

            override fun toString(): String {
                return "x"
            }
        }
        val testObject = JSONObject()
        assertFailsWith<JSONException> {
            testObject.put("foo", nan)
            fail("Object.put() accepted a NaN (via a custom Number class)")
        }
    }

    @Test
    fun testForeignObjects() {
        val foreign: Any = object : Any() {
            override fun toString(): String {
                return "x"
            }
        }
        // foreign object types are accepted and treated as Strings!
        val testObject = JSONObject()
        testObject.put("foo", foreign)
        assertEquals("{\"foo\":\"x\"}", testObject.toString())
    }

    @Test
    fun testStrings() {
        val testObject = JSONObject()
        testObject.put("foo", "true")
        testObject.put("bar", "5.5")
        testObject.put("baz", "9223372036854775806")
        testObject.put("quux", "null")
        testObject.put("height", "5\"8' tall")
        assertTrue(testObject.toString().contains("\"foo\":\"true\""))
        assertTrue(testObject.toString().contains("\"bar\":\"5.5\""))
        assertTrue(testObject.toString().contains("\"baz\":\"9223372036854775806\""))
        assertTrue(testObject.toString().contains("\"quux\":\"null\""))
        assertTrue(testObject.toString().contains("\"height\":\"5\\\"8' tall\""))
        assertEquals("true", testObject["foo"])
        assertEquals("null", testObject.getString("quux"))
        assertEquals("5\"8' tall", testObject.getString("height"))
        assertEquals("true", testObject.opt("foo"))
        assertEquals("5.5", testObject.optString("bar"))
        assertEquals("true", testObject.optString("foo", "x"))
        assertFalse(testObject.isNull("foo"))
        assertEquals(true, testObject.getBoolean("foo"))
        assertEquals(true, testObject.optBoolean("foo"))
        assertEquals(true, testObject.optBoolean("foo", false))
        assertEquals(0, testObject.optInt("foo"))
        assertEquals(-2, testObject.optInt("foo", -2))
        assertEquals(5.5, testObject.getDouble("bar"), 0.0)
        assertEquals(5L, testObject.getLong("bar"))
        assertEquals(5, testObject.getInt("bar"))
        assertEquals(5, testObject.optInt("bar", 3))
        // The last digit of the string is a 6 but getLong returns a 7. It's probably parsing as a
        // double and then converting that to a long. This is consistent with JavaScript.
        assertEquals(9223372036854775807L, testObject.getLong("baz"))
        assertEquals(9.223372036854776E18, testObject.getDouble("baz"), 0.0)
        assertEquals(Int.MAX_VALUE, testObject.getInt("baz"))
        assertFalse(testObject.isNull("quux"))
        assertFailsWith<JSONException> {
            testObject.getDouble("quux")
        }
        assertEquals(Double.NaN, testObject.optDouble("quux"), 0.0)
        assertEquals(-1.0, testObject.optDouble("quux", -1.0), 0.0)
        testObject.put("foo", "TRUE")
        assertEquals(true, testObject.getBoolean("foo"))
    }

    @Test
    fun testJSONObjects() {
        val testObject = JSONObject()
        val a = JSONArray()
        val b = JSONObject()
        testObject.put("foo", a)
        testObject.put("bar", b)
        assertSame(a, testObject.getJSONArray("foo"))
        assertSame(b, testObject.getJSONObject("bar"))
        assertFailsWith<JSONException> {
            testObject.getJSONObject("foo")
        }
        assertFailsWith<JSONException> {
            testObject.getJSONArray("bar")
        }
        assertEquals(a, testObject.optJSONArray("foo"))
        assertEquals(b, testObject.optJSONObject("bar"))
        assertEquals(null, testObject.optJSONArray("bar"))
        assertEquals(null, testObject.optJSONObject("foo"))
    }

    @Test
    fun testNullCoercionToString() {
        val testObject = JSONObject()
        testObject.put("foo", JSONObject.NULL)
        assertEquals("null", testObject.getString("foo"))
    }

    @Test
    fun testArrayCoercion() {
        val testObject = JSONObject()
        testObject.put("foo", "[true]")
        assertFailsWith<JSONException> {
            testObject.getJSONArray("foo")
        }
    }

    @Test
    fun testObjectCoercion() {
        val testObject = JSONObject()
        testObject.put("foo", "{}")
        assertFailsWith<JSONException> {
            testObject.getJSONObject("foo")
        }
    }

    @Test
    fun testAccumulateValueChecking() {
        val testObject = JSONObject()
        assertFailsWith<JSONException> {
            testObject.accumulate("foo", Double.NaN)
        }
        testObject.accumulate("foo", 1)
        assertFailsWith<JSONException> {
            testObject.accumulate("foo", Double.NaN)
        }
        testObject.accumulate("foo", 2)
        assertFailsWith<JSONException> {
            testObject.accumulate("foo", Double.NaN)
        }
    }

    @Test
    fun testToJSONArray() {
        val testObject = JSONObject()
        val value = Any()
        testObject.put("foo", true)
        testObject.put("bar", 5.0)
        testObject.put("baz", -0.0)
        testObject.put("quux", value)
        val names = JSONArray()
        names.put("baz")
        names.put("quux")
        names.put("foo")
        val array = testObject.toJSONArray(names)!!
        assertEquals(-0.0, array[0])
        assertEquals(value, array[1])
        assertEquals(true, array[2])
        testObject.put("foo", false)
        assertEquals(true, array[2])
    }

    @Test
    fun testToJSONArrayMissingNames() {
        val testObject = JSONObject()
        testObject.put("foo", true)
        testObject.put("bar", 5.0)
        testObject.put("baz", JSONObject.NULL)
        val names = JSONArray()
        names.put("bar")
        names.put("foo")
        names.put("quux")
        names.put("baz")
        val array = testObject.toJSONArray(names)!!
        assertEquals(4, array.length())
        assertEquals(5.0, array[0])
        assertEquals(true, array[1])
        assertFailsWith<JSONException> {
            array[2]
        }
        assertEquals(JSONObject.NULL, array[3])
    }

    @Test
    fun testToJSONArrayNull() {
        val testObject = JSONObject()
        assertEquals(null, testObject.toJSONArray(null))
        testObject.put("foo", 5)
        try {
            testObject.toJSONArray(null)
        } catch (_: JSONException) {
        }
    }

    @Test
    fun testToJSONArrayEndsUpEmpty() {
        val testObject = JSONObject()
        testObject.put("foo", 5)
        val array = JSONArray()
        array.put("bar")
        assertEquals(1, testObject.toJSONArray(array)!!.length())
    }

    @Test
    fun testToJSONArrayNonString() {
        val testObject = JSONObject()
        testObject.put("foo", 5)
        testObject.put("null", 10)
        testObject.put("false", 15)
        val names = JSONArray()
        names.put(JSONObject.NULL)
        names.put(false)
        names.put("foo")
        // array elements are converted to strings to do name lookups on the map!
        val array = testObject.toJSONArray(names)!!
        assertEquals(3, array.length())
        assertEquals(10, array[0])
        assertEquals(15, array[1])
        assertEquals(5, array[2])
    }

    @Test
    fun testPutUnsupportedNumbers() {
        val testObject = JSONObject()
        assertFailsWith<JSONException> {
            testObject.put("foo", Double.NaN)
        }
        assertFailsWith<JSONException> {
            testObject.put("foo", Double.NEGATIVE_INFINITY)
        }
        assertFailsWith<JSONException> {
            testObject.put("foo", Double.POSITIVE_INFINITY)
        }
    }

    @Test
    fun testPutUnsupportedNumbersAsObjects() {
        val testObject = JSONObject()
        assertFailsWith<JSONException> {
            testObject.put("foo", Double.NaN)
        }
        assertFailsWith<JSONException> {
            testObject.put("foo", Double.NEGATIVE_INFINITY)
        }
        assertFailsWith<JSONException> {
            testObject.put("foo", Double.POSITIVE_INFINITY)
        }
    }

    /**
     * Although JSONObject is usually defensive about which numbers it accepts,
     * it doesn't check inputs in its constructor.
     */
    @Test
    fun testCreateWithUnsupportedNumbers() {
        val contents: MutableMap<String, Any?> = HashMap()
        contents["foo"] = Double.NaN
        contents["bar"] = Double.NEGATIVE_INFINITY
        contents["baz"] = Double.POSITIVE_INFINITY
        val testObject = JSONObject(contents.toMap())
        assertEquals(Double.NaN, testObject["foo"])
        assertEquals(Double.NEGATIVE_INFINITY, testObject["bar"])
        assertEquals(Double.POSITIVE_INFINITY, testObject["baz"])
    }

    @Test
    fun testMapConstructorCopiesContents() {
        val contents: MutableMap<String, Any?> = HashMap()
        contents["foo"] = 5
        val testObject = JSONObject(contents.toMap())
        contents["foo"] = 10
        assertEquals(5, testObject["foo"])
    }

    @Test
    fun testMapConstructorWithBogusEntries() {
        val contents: MutableMap<Any?, Any?> = HashMap()
        contents[5] = 5
        assertFailsWith<Exception> {
            // Cast is invalid. Used only for testing with invalid map input
            @Suppress("UNCHECKED_CAST")
            JSONObject(contents as Map<String, Any?>)
            fail("JSONObject constructor doesn't validate its input!")
        }
    }

    @Test
    fun testTokenerConstructor() {
        val testObject = JSONObject(JSONTokener("{\"foo\": false}"))
        assertEquals(1, testObject.length())
        assertEquals(false, testObject["foo"])
    }

    @Test
    fun testTokenerConstructorWrongType() {
        assertFailsWith<JSONException> {
            JSONObject(JSONTokener("[\"foo\", false]"))
        }
    }

    @Test
    fun testTokenerConstructorParseFail() {
        assertFailsWith<JSONException> {
            JSONObject(JSONTokener("{"))
        }
    }

    @Test
    fun testStringConstructor() {
        val testObject = JSONObject("{\"foo\": false}")
        assertEquals(1, testObject.length())
        assertEquals(false, testObject["foo"])
    }

    @Test
    fun testStringConstructorWrongType() {
        assertFailsWith<JSONException> {
            JSONObject("[\"foo\", false]")
        }
    }

    @Test
    fun testStringonstructorParseFail() {
        assertFailsWith<JSONException> {
            JSONObject("{")
        }
    }

    @Test
    fun testCopyConstructor() {
        val source = JSONObject()
        source.put("a", JSONObject.NULL)
        source.put("b", false)
        source.put("c", 5)
        val copy = JSONObject(source, arrayOf("a", "c"))
        assertEquals(2, copy.length())
        assertEquals(JSONObject.NULL, copy["a"])
        assertEquals(5, copy["c"])
        assertEquals(null, copy.opt("b"))
    }

    @Test
    fun testCopyConstructorMissingName() {
        val source = JSONObject()
        source.put("a", JSONObject.NULL)
        source.put("b", false)
        source.put("c", 5)
        val copy = JSONObject(source, arrayOf("a", "c", "d"))
        assertEquals(2, copy.length())
        assertEquals(JSONObject.NULL, copy["a"])
        assertEquals(5, copy["c"])
        assertEquals(0, copy.optInt("b"))
    }

    @Test
    fun testAccumulateMutatesInPlace() {
        val testObject = JSONObject()
        testObject.put("foo", 5)
        testObject.accumulate("foo", 6)
        val array = testObject.getJSONArray("foo")
        assertEquals("[5,6]", array.toString())
        testObject.accumulate("foo", 7)
        assertEquals("[5,6,7]", array.toString())
    }

    @Test
    fun testAccumulateExistingArray() {
        val array = JSONArray()
        val testObject = JSONObject()
        testObject.put("foo", array)
        testObject.accumulate("foo", 5)
        assertEquals("[5]", array.toString())
    }

    @Test
    fun testAccumulatePutArray() {
        val testObject = JSONObject()
        testObject.accumulate("foo", 5)
        assertEquals("{\"foo\":5}", testObject.toString())
        testObject.accumulate("foo", JSONArray())
        assertEquals("{\"foo\":[5,[]]}", testObject.toString())
    }

    @Test
    fun testEmptyStringKey() {
        val testObject = JSONObject()
        testObject.put("", 5)
        assertEquals(5, testObject[""])
        assertEquals("{\"\":5}", testObject.toString())
    }

    @Test
    fun testNullValue() {
        val testObject = JSONObject()
        testObject.put("foo", JSONObject.NULL)
        testObject.put("bar", null)
        // there are two ways to represent null; each behaves differently!
        assertTrue(testObject.has("foo"))
        assertFalse(testObject.has("bar"))
        assertTrue(testObject.isNull("foo"))
        assertTrue(testObject.isNull("bar"))
    }

    @Test
    fun testHas() {
        val testObject = JSONObject()
        testObject.put("foo", 5)
        assertTrue(testObject.has("foo"))
        assertFalse(testObject.has("bar"))
        assertFalse(testObject.has(null))
    }

    @Test
    fun testOptNull() {
        val testObject = JSONObject()
        testObject.put("foo", "bar")
        assertEquals(null, testObject.opt(null))
        assertEquals(false, testObject.optBoolean(null))
        assertEquals(Double.NaN, testObject.optDouble(null), 0.0)
        assertEquals(0, testObject.optInt(null))
        assertEquals(0L, testObject.optLong(null))
        assertEquals(null, testObject.optJSONArray(null))
        assertEquals(null, testObject.optJSONObject(null))
        assertEquals("", testObject.optString(null))
        assertEquals(true, testObject.optBoolean(null, true))
        assertEquals(0.0, testObject.optDouble(null, 0.0), 0.0)
        assertEquals(1, testObject.optInt(null, 1))
        assertEquals(1L, testObject.optLong(null, 1L))
        assertEquals("baz", testObject.optString(null, "baz"))
    }

    @Test
    fun testToStringWithIndentFactor() {
        val testObject = JSONObject()
        testObject.put("foo", JSONArray(listOf(5, 6)))
        testObject.put("bar", JSONObject())
        val foobar = """{
     "foo": [
          5,
          6
     ],
     "bar": {}
}"""
        val barfoo = """{
     "bar": {},
     "foo": [
          5,
          6
     ]
}"""
        val string = testObject.toString(5)
        assertTrue(string, foobar == string || barfoo == string)
    }

    @Test
    fun testNames() {
        val testObject = JSONObject()
        testObject.put("foo", 5)
        testObject.put("bar", 6)
        testObject.put("baz", 7)
        val array = testObject.names()!!
        assertTrue(array.toString().contains("foo"))
        assertTrue(array.toString().contains("bar"))
        assertTrue(array.toString().contains("baz"))
    }

    @Test
    fun testKeysEmptyObject() {
        val testObject = JSONObject()
        assertFalse(testObject.keys().hasNext())
        assertFailsWith<NoSuchElementException> { testObject.keys().next() }
    }

    @Test
    fun testKeys() {
        val testObject = JSONObject()
        testObject.put("foo", 5)
        testObject.put("bar", 6)
        testObject.put("foo", 7)
        val keys = testObject.keys() as Iterator<String>
        val result: MutableSet<String> = HashSet()
        assertTrue(keys.hasNext())
        result.add(keys.next())
        assertTrue(keys.hasNext())
        result.add(keys.next())
        assertFalse(keys.hasNext())
        assertEquals(HashSet(listOf("foo", "bar")), result)
        assertFailsWith<NoSuchElementException> { keys.next() }
    }

    @Test
    fun testMutatingKeysMutatesObject() {
        val testObject = JSONObject()
        testObject.put("foo", 5)
        val keys: MutableIterator<*> = testObject.keys()
        keys.next()
        keys.remove()
        assertEquals(0, testObject.length())
    }

    @Test
    fun testQuote() {
        // covered by JSONStringerTest.testEscaping
    }

    @Test
    fun testQuoteNull() {
        assertEquals("\"\"", JSONObject.quote(null))
    }

    @Test
    fun testNumberToString() {
        assertEquals("5", JSONObject.numberToString(5))
        assertEquals("-0", JSONObject.numberToString(-0.0))
        assertEquals("9223372036854775806", JSONObject.numberToString(9223372036854775806L))
        assertEquals("4.9E-324", JSONObject.numberToString(Double.MIN_VALUE))
        assertEquals("1.7976931348623157E308", JSONObject.numberToString(Double.MAX_VALUE))
        assertFailsWith<JSONException> {
            JSONObject.numberToString(Double.NaN)
        }
        assertFailsWith<JSONException> {
            JSONObject.numberToString(Double.NEGATIVE_INFINITY)
        }
        assertFailsWith<JSONException> {
            JSONObject.numberToString(Double.POSITIVE_INFINITY)
        }
        assertEquals("0.001", JSONObject.numberToString(BigDecimal("0.001")))
        assertEquals(
            "9223372036854775806",
            JSONObject.numberToString(BigInteger("9223372036854775806"))
        )
    }

    /* *************************************************************************

    Ankidroid specific test
    *************************************************************************** */
    private val mEmptyJson = "{}"
    private val mCorrectJsonBasic = "{\"key1\":\"value1\"}"
    private val mCorrectJsonNested = "{\"key1\":{\"key1a\":\"value1a\",\"key1b\":\"value1b\"},\"key2\":\"value2\"}"
    private val mCorrectJsonWithArray = "{\"key1\":\"value1\",\"key2\":[{\"key2a\":\"value2a\"},{\"key2b\":\"value2b\"}],\"key3\":\"value3\"}"
    private val mCorrectJsonNestedWithArray = "{\"key1\":{\"key1a\":\"value1a\",\"key1b\":\"value1b\"},\"key2\":[{\"key2a\":\"value2a\"},{\"key2b\":\"value2b\"}],\"key3\":\"value3\"}"
    private val mNoOpeningBracket = "\"key1\":\"value1\"}"
    private val mExtraOpeningBracket = "{{\"key1\": \"value1\"}"
    private val mNoClosingBracket = "{\"key1\":value1"
    private val mWrongKeyValueSeparator = "{\"key1\":\"value1\",\"key2\" \"value2\"}"
    private val mDuplicateKey = "{\"key1\":\"value1\",\"key1\":\"value2\"}"
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
    fun objectNullIsNotNull() {
        // #6289
        assertThat(JSONObject.NULL, notNullValue())
    }

    @Test
    fun formatTest() {
        // Correct formats
        JSONObject(mCorrectJsonBasic)
        JSONObject(mCorrectJsonNested)
        JSONObject(mCorrectJsonWithArray)
        JSONObject(mEmptyJson)

        // Incorrect formats
        Assert.assertThrows(JSONException::class.java) { JSONObject(mNoOpeningBracket) }
        Assert.assertThrows(JSONException::class.java) { JSONObject(mExtraOpeningBracket) }
        Assert.assertThrows(JSONException::class.java) { JSONObject(mNoClosingBracket) }
        Assert.assertThrows(JSONException::class.java) { JSONObject(mWrongKeyValueSeparator) }
        // Assert.assertThrows(JSONException::class.java) { JSONObject(mDuplicateKey) }
    }

    @Test
    fun copyJsonTest() {
        Assert.assertEquals(mCorrectJsonObjectBasic.toString(), mCorrectJsonObjectBasic.deepClone().toString())
        Assert.assertEquals(mCorrectJsonObjectNested.toString(), mCorrectJsonObjectNested.deepClone().toString())
        Assert.assertEquals(mCorrectJsonObjectWithArray.toString(), mCorrectJsonObjectWithArray.deepClone().toString())
    }

    fun getTest() {
        val correctJsonObjectBasicCopy = JSONObject(mCorrectJsonBasic)
        correctJsonObjectBasicCopy.put("int-key", 2)
        correctJsonObjectBasicCopy.put("int_key", 6)
        correctJsonObjectBasicCopy.put("long_key", 2L)
        correctJsonObjectBasicCopy.put("double_key", 2.0)
        correctJsonObjectBasicCopy.putOpt("boolean_key", true)
        correctJsonObjectBasicCopy.putOpt("object_key", mCorrectJsonBasic)

        Assert.assertEquals(6, correctJsonObjectBasicCopy.getInt("int_key").toLong())
        Assert.assertEquals(2L, correctJsonObjectBasicCopy.getLong("long_key"))
        Assert.assertEquals(2.0, correctJsonObjectBasicCopy.getDouble("double_key"), 1e-10)
        Assert.assertTrue(correctJsonObjectBasicCopy.getBoolean("boolean_key"))
        Assert.assertEquals(mCorrectJsonBasic, correctJsonObjectBasicCopy["object_key"])

        // Check that putOpt doesn't add pair when one is null
        correctJsonObjectBasicCopy.putOpt("boolean_key_2", null)
        Assert.assertFalse(correctJsonObjectBasicCopy.has("boolean_key_2"))
        Assert.assertThrows(JSONException::class.java) { correctJsonObjectBasicCopy["boolean_key_2"] }
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

    /**
     * Tests that exception is caught in the catch statement
     */
    @Test
    fun testGetThrows() {
        val testObject = JSONObject()
        assertFailsWith<JSONException> { testObject.getBoolean("key") }
        assertFailsWith<JSONException> {
            testObject.getInt("key")
        }
        assertFailsWith<JSONException> {
            testObject.getLong("key")
        }
        assertFailsWith<JSONException> {
            testObject.getString("key")
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
