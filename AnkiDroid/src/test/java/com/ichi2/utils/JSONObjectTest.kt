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

import androidx.test.ext.junit.runners.AndroidJUnit4
import junit.framework.TestCase.*
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.core.IsNull.notNullValue
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal
import java.math.BigInteger
import java.util.*

/**
 * This black box test was written without inspecting the non-free org.json sourcecode.
 */
@RunWith(AndroidJUnit4::class)
@KotlinCleanup("fix `object`")
class JSONObjectTest {
    @Test
    fun testEmptyObject() {
        val `object` = JSONObject()
        assertEquals(0, `object`.length())
        // bogus (but documented) behaviour: returns null rather than the empty object!
        assertNull(`object`.names())
        // returns null rather than an empty array!
        assertNull(`object`.toJSONArray(JSONArray()))
        assertEquals("{}", `object`.toString())
        assertEquals("{}", `object`.toString(5))
        try {
            `object`["foo"]
            fail()
        } catch (e: JSONException) {
        }
        try {
            `object`.getBoolean("foo")
            fail()
        } catch (e: JSONException) {
        }
        try {
            `object`.getDouble("foo")
            fail()
        } catch (e: JSONException) {
        }
        try {
            `object`.getInt("foo")
            fail()
        } catch (e: JSONException) {
        }
        try {
            `object`.getJSONArray("foo")
            fail()
        } catch (e: JSONException) {
        }
        try {
            `object`.getJSONObject("foo")
            fail()
        } catch (e: JSONException) {
        }
        try {
            `object`.getLong("foo")
            fail()
        } catch (e: JSONException) {
        }
        try {
            `object`.getString("foo")
            fail()
        } catch (e: JSONException) {
        }
        assertFalse(`object`.has("foo"))
        assertTrue(`object`.isNull("foo")) // isNull also means "is not present"
        assertNull(`object`.opt("foo"))
        assertEquals(false, `object`.optBoolean("foo"))
        assertEquals(true, `object`.optBoolean("foo", true))
        assertEquals(Double.NaN, `object`.optDouble("foo"), 0.0)
        assertEquals(5.0, `object`.optDouble("foo", 5.0), 0.0)
        assertEquals(0, `object`.optInt("foo"))
        assertEquals(5, `object`.optInt("foo", 5))
        assertEquals(null, `object`.optJSONArray("foo"))
        assertEquals(null, `object`.optJSONObject("foo"))
        assertEquals(0, `object`.optLong("foo"))
        assertEquals(Long.MAX_VALUE - 1, `object`.optLong("foo", Long.MAX_VALUE - 1))
        assertEquals("", `object`.optString("foo")) // empty string is default!
        assertEquals("bar", `object`.optString("foo", "bar"))
        assertNull(`object`.remove("foo"))
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
        val `object` = JSONObject()
        val value = Any()
        `object`.put("foo", value)
        `object`.put("bar", Any())
        `object`.put("baz", Any())
        assertSame(value, `object`["foo"])
        try {
            `object`["FOO"]
            fail()
        } catch (e: JSONException) {
        }
    }

    @Test
    fun testPut() {
        val `object` = JSONObject()
        assertSame(`object`, `object`.put("foo", true))
        `object`.put("foo", false)
        assertEquals(false, `object`["foo"])
        `object`.put("foo", 5.0)
        assertEquals(5.0, `object`["foo"])
        `object`.put("foo", 0)
        assertEquals(0, `object`["foo"])
        `object`.put("bar", Long.MAX_VALUE - 1)
        assertEquals(Long.MAX_VALUE - 1, `object`["bar"])
        `object`.put("baz", "x")
        assertEquals("x", `object`["baz"])
        `object`.put("bar", JSONObject.NULL)
        assertSame(JSONObject.NULL, `object`["bar"])
    }

    @Test
    fun testPutNullRemoves() {
        val `object` = JSONObject()
        `object`.put("foo", "bar")
        `object`.put("foo", null)
        assertEquals(0, `object`.length())
        assertFalse(`object`.has("foo"))
        try {
            `object`["foo"]
            fail()
        } catch (e: JSONException) {
        }
    }

    @Test
    fun testPutOpt() {
        val `object` = JSONObject()
        `object`.put("foo", "bar")
        `object`.putOpt("foo", null)
        assertEquals("bar", `object`["foo"])
        `object`.putOpt(null, null)
        assertEquals(1, `object`.length())
        `object`.putOpt(null, "bar")
        assertEquals(1, `object`.length())
    }

    @Test
    fun testPutOptUnsupportedNumbers() {
        val `object` = JSONObject()
        try {
            `object`.putOpt("foo", Double.NaN)
            fail()
        } catch (e: JSONException) {
        }
        try {
            `object`.putOpt("foo", Double.NEGATIVE_INFINITY)
            fail()
        } catch (e: JSONException) {
        }
        try {
            `object`.putOpt("foo", Double.POSITIVE_INFINITY)
            fail()
        } catch (e: JSONException) {
        }
    }

    @Test
    fun testRemove() {
        val `object` = JSONObject()
        `object`.put("foo", "bar")
        assertEquals(null, `object`.remove(null))
        assertEquals(null, `object`.remove(""))
        assertEquals(null, `object`.remove("bar"))
        assertEquals("bar", `object`.remove("foo"))
        assertEquals(null, `object`.remove("foo"))
    }

    @Test
    fun testBooleans() {
        val `object` = JSONObject()
        `object`.put("foo", true)
        `object`.put("bar", false)
        `object`.put("baz", "true")
        `object`.put("quux", "false")
        assertEquals(4, `object`.length())
        assertEquals(true, `object`.getBoolean("foo"))
        assertEquals(false, `object`.getBoolean("bar"))
        assertEquals(true, `object`.getBoolean("baz"))
        assertEquals(false, `object`.getBoolean("quux"))
        assertFalse(`object`.isNull("foo"))
        assertFalse(`object`.isNull("quux"))
        assertTrue(`object`.has("foo"))
        assertTrue(`object`.has("quux"))
        assertFalse(`object`.has("missing"))
        assertEquals(true, `object`.optBoolean("foo"))
        assertEquals(false, `object`.optBoolean("bar"))
        assertEquals(true, `object`.optBoolean("baz"))
        assertEquals(false, `object`.optBoolean("quux"))
        assertEquals(false, `object`.optBoolean("missing"))
        assertEquals(true, `object`.optBoolean("foo", true))
        assertEquals(false, `object`.optBoolean("bar", true))
        assertEquals(true, `object`.optBoolean("baz", true))
        assertEquals(false, `object`.optBoolean("quux", true))
        assertEquals(true, `object`.optBoolean("missing", true))
        `object`.put("foo", "truE")
        `object`.put("bar", "FALSE")
        assertEquals(true, `object`.getBoolean("foo"))
        assertEquals(false, `object`.getBoolean("bar"))
        assertEquals(true, `object`.optBoolean("foo"))
        assertEquals(false, `object`.optBoolean("bar"))
        assertEquals(true, `object`.optBoolean("foo", false))
        assertEquals(false, `object`.optBoolean("bar", false))
    }

    @Test
    fun testNumbers() {
        val `object` = JSONObject()
        `object`.put("foo", Double.MIN_VALUE)
        `object`.put("bar", 9223372036854775806L)
        `object`.put("baz", Double.MAX_VALUE)
        `object`.put("quux", -0.0)
        assertEquals(4, `object`.length())
        val toString = `object`.toString()
        assertTrue(toString, toString.contains("\"foo\":4.9E-324"))
        assertTrue(toString, toString.contains("\"bar\":9223372036854775806"))
        assertTrue(toString, toString.contains("\"baz\":1.7976931348623157E308"))
        // toString() and getString() return different values for -0d!
        assertTrue(
            toString,
            toString.contains("\"quux\":-0}") || // no trailing decimal point
                toString.contains("\"quux\":-0,")
        )
        assertEquals(Double.MIN_VALUE, `object`["foo"])
        assertEquals(9223372036854775806L, `object`["bar"])
        assertEquals(Double.MAX_VALUE, `object`["baz"])
        assertEquals(-0.0, `object`["quux"])
        assertEquals(Double.MIN_VALUE, `object`.getDouble("foo"), 0.0)
        assertEquals(9.223372036854776E18, `object`.getDouble("bar"), 0.0)
        assertEquals(Double.MAX_VALUE, `object`.getDouble("baz"), 0.0)
        assertEquals(-0.0, `object`.getDouble("quux"), 0.0)
        assertEquals(0, `object`.getLong("foo"))
        assertEquals(9223372036854775806L, `object`.getLong("bar"))
        assertEquals(Long.MAX_VALUE, `object`.getLong("baz"))
        assertEquals(0, `object`.getLong("quux"))
        assertEquals(0, `object`.getInt("foo"))
        assertEquals(-2, `object`.getInt("bar"))
        assertEquals(Int.MAX_VALUE, `object`.getInt("baz"))
        assertEquals(0, `object`.getInt("quux"))
        assertEquals(Double.MIN_VALUE, `object`.opt("foo"))
        assertEquals(9223372036854775806L, `object`.optLong("bar"))
        assertEquals(Double.MAX_VALUE, `object`.optDouble("baz"), 0.0)
        assertEquals(0, `object`.optInt("quux"))
        assertEquals(Double.MIN_VALUE, `object`.opt("foo"))
        assertEquals(9223372036854775806L, `object`.optLong("bar"))
        assertEquals(Double.MAX_VALUE, `object`.optDouble("baz"), 0.0)
        assertEquals(0, `object`.optInt("quux"))
        assertEquals(Double.MIN_VALUE, `object`.optDouble("foo", 5.0), 0.0)
        assertEquals(9223372036854775806L, `object`.optLong("bar", 1L))
        assertEquals(Long.MAX_VALUE, `object`.optLong("baz", 1L))
        assertEquals(0, `object`.optInt("quux", -1))
        assertEquals("4.9E-324", `object`.getString("foo"))
        assertEquals("9223372036854775806", `object`.getString("bar"))
        assertEquals("1.7976931348623157E308", `object`.getString("baz"))
        assertEquals("-0.0", `object`.getString("quux"))
    }

    @Test
    fun testFloats() {
        val `object` = JSONObject()
        try {
            `object`.put("foo", Float.NaN)
            fail()
        } catch (e: JSONException) {
        }
        try {
            `object`.put("foo", Float.NEGATIVE_INFINITY)
            fail()
        } catch (e: JSONException) {
        }
        try {
            `object`.put("foo", Float.POSITIVE_INFINITY)
            fail()
        } catch (e: JSONException) {
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
        val `object` = JSONObject()
        try {
            `object`.put("foo", nan)
            fail("Object.put() accepted a NaN (via a custom Number class)")
        } catch (e: JSONException) {
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
        val `object` = JSONObject()
        `object`.put("foo", foreign)
        assertEquals("{\"foo\":\"x\"}", `object`.toString())
    }

    @Test
    fun testStrings() {
        val `object` = JSONObject()
        `object`.put("foo", "true")
        `object`.put("bar", "5.5")
        `object`.put("baz", "9223372036854775806")
        `object`.put("quux", "null")
        `object`.put("height", "5\"8' tall")
        assertTrue(`object`.toString().contains("\"foo\":\"true\""))
        assertTrue(`object`.toString().contains("\"bar\":\"5.5\""))
        assertTrue(`object`.toString().contains("\"baz\":\"9223372036854775806\""))
        assertTrue(`object`.toString().contains("\"quux\":\"null\""))
        assertTrue(`object`.toString().contains("\"height\":\"5\\\"8' tall\""))
        assertEquals("true", `object`["foo"])
        assertEquals("null", `object`.getString("quux"))
        assertEquals("5\"8' tall", `object`.getString("height"))
        assertEquals("true", `object`.opt("foo"))
        assertEquals("5.5", `object`.optString("bar"))
        assertEquals("true", `object`.optString("foo", "x"))
        assertFalse(`object`.isNull("foo"))
        assertEquals(true, `object`.getBoolean("foo"))
        assertEquals(true, `object`.optBoolean("foo"))
        assertEquals(true, `object`.optBoolean("foo", false))
        assertEquals(0, `object`.optInt("foo"))
        assertEquals(-2, `object`.optInt("foo", -2))
        assertEquals(5.5, `object`.getDouble("bar"), 0.0)
        assertEquals(5L, `object`.getLong("bar"))
        assertEquals(5, `object`.getInt("bar"))
        assertEquals(5, `object`.optInt("bar", 3))
        // The last digit of the string is a 6 but getLong returns a 7. It's probably parsing as a
        // double and then converting that to a long. This is consistent with JavaScript.
        assertEquals(9223372036854775807L, `object`.getLong("baz"))
        assertEquals(9.223372036854776E18, `object`.getDouble("baz"), 0.0)
        assertEquals(Int.MAX_VALUE, `object`.getInt("baz"))
        assertFalse(`object`.isNull("quux"))
        try {
            `object`.getDouble("quux")
            fail()
        } catch (e: JSONException) {
        }
        assertEquals(Double.NaN, `object`.optDouble("quux"), 0.0)
        assertEquals(-1.0, `object`.optDouble("quux", -1.0), 0.0)
        `object`.put("foo", "TRUE")
        assertEquals(true, `object`.getBoolean("foo"))
    }

    @Test
    fun testJSONObjects() {
        val `object` = JSONObject()
        val a = JSONArray()
        val b = JSONObject()
        `object`.put("foo", a)
        `object`.put("bar", b)
        assertSame(a, `object`.getJSONArray("foo"))
        assertSame(b, `object`.getJSONObject("bar"))
        try {
            `object`.getJSONObject("foo")
            fail()
        } catch (e: JSONException) {
        }
        try {
            `object`.getJSONArray("bar")
            fail()
        } catch (e: JSONException) {
        }
        assertEquals(a, `object`.optJSONArray("foo"))
        assertEquals(b, `object`.optJSONObject("bar"))
        assertEquals(null, `object`.optJSONArray("bar"))
        assertEquals(null, `object`.optJSONObject("foo"))
    }

    @Test
    fun testNullCoercionToString() {
        val `object` = JSONObject()
        `object`.put("foo", JSONObject.NULL)
        assertEquals("null", `object`.getString("foo"))
    }

    @Test
    fun testArrayCoercion() {
        val `object` = JSONObject()
        `object`.put("foo", "[true]")
        try {
            `object`.getJSONArray("foo")
            fail()
        } catch (e: JSONException) {
        }
    }

    @Test
    fun testObjectCoercion() {
        val `object` = JSONObject()
        `object`.put("foo", "{}")
        try {
            `object`.getJSONObject("foo")
            fail()
        } catch (e: JSONException) {
        }
    }

    @Test
    fun testAccumulateValueChecking() {
        val `object` = JSONObject()
        try {
            `object`.accumulate("foo", Double.NaN)
            fail()
        } catch (e: JSONException) {
        }
        `object`.accumulate("foo", 1)
        try {
            `object`.accumulate("foo", Double.NaN)
            fail()
        } catch (e: JSONException) {
        }
        `object`.accumulate("foo", 2)
        try {
            `object`.accumulate("foo", Double.NaN)
            fail()
        } catch (e: JSONException) {
        }
    }

    @Test
    fun testToJSONArray() {
        val `object` = JSONObject()
        val value = Any()
        `object`.put("foo", true)
        `object`.put("bar", 5.0)
        `object`.put("baz", -0.0)
        `object`.put("quux", value)
        val names = JSONArray()
        names.put("baz")
        names.put("quux")
        names.put("foo")
        val array = `object`.toJSONArray(names)
        assertEquals(-0.0, array[0])
        assertEquals(value, array[1])
        assertEquals(true, array[2])
        `object`.put("foo", false)
        assertEquals(true, array[2])
    }

    @Test
    fun testToJSONArrayMissingNames() {
        val `object` = JSONObject()
        `object`.put("foo", true)
        `object`.put("bar", 5.0)
        `object`.put("baz", JSONObject.NULL)
        val names = JSONArray()
        names.put("bar")
        names.put("foo")
        names.put("quux")
        names.put("baz")
        val array = `object`.toJSONArray(names)
        assertEquals(4, array.length())
        assertEquals(5.0, array[0])
        assertEquals(true, array[1])
        try {
            array[2]
            fail()
        } catch (e: JSONException) {
        }
        assertEquals(JSONObject.NULL, array[3])
    }

    @Test
    fun testToJSONArrayNull() {
        val `object` = JSONObject()
        assertEquals(null, `object`.toJSONArray(null))
        `object`.put("foo", 5)
        try {
            `object`.toJSONArray(null)
        } catch (e: JSONException) {
        }
    }

    @Test
    fun testToJSONArrayEndsUpEmpty() {
        val `object` = JSONObject()
        `object`.put("foo", 5)
        val array = JSONArray()
        array.put("bar")
        assertEquals(1, `object`.toJSONArray(array).length())
    }

    @Test
    fun testToJSONArrayNonString() {
        val `object` = JSONObject()
        `object`.put("foo", 5)
        `object`.put("null", 10)
        `object`.put("false", 15)
        val names = JSONArray()
        names.put(JSONObject.NULL)
        names.put(false)
        names.put("foo")
        // array elements are converted to strings to do name lookups on the map!
        val array = `object`.toJSONArray(names)
        assertEquals(3, array.length())
        assertEquals(10, array[0])
        assertEquals(15, array[1])
        assertEquals(5, array[2])
    }

    @Test
    fun testPutUnsupportedNumbers() {
        val `object` = JSONObject()
        try {
            `object`.put("foo", Double.NaN)
            fail()
        } catch (e: JSONException) {
        }
        try {
            `object`.put("foo", Double.NEGATIVE_INFINITY)
            fail()
        } catch (e: JSONException) {
        }
        try {
            `object`.put("foo", Double.POSITIVE_INFINITY)
            fail()
        } catch (e: JSONException) {
        }
    }

    @Test
    fun testPutUnsupportedNumbersAsObjects() {
        val `object` = JSONObject()
        try {
            `object`.put("foo", Double.NaN)
            fail()
        } catch (e: JSONException) {
        }
        try {
            `object`.put("foo", Double.NEGATIVE_INFINITY)
            fail()
        } catch (e: JSONException) {
        }
        try {
            `object`.put("foo", Double.POSITIVE_INFINITY)
            fail()
        } catch (e: JSONException) {
        }
    }

    /**
     * Although JSONObject is usually defensive about which numbers it accepts,
     * it doesn't check inputs in its constructor.
     */
    @Test
    fun testCreateWithUnsupportedNumbers() {
        val contents: MutableMap<String?, Any?> = HashMap()
        contents["foo"] = Double.NaN
        contents["bar"] = Double.NEGATIVE_INFINITY
        contents["baz"] = Double.POSITIVE_INFINITY
        val `object` = JSONObject(contents)
        assertEquals(Double.NaN, `object`["foo"])
        assertEquals(Double.NEGATIVE_INFINITY, `object`["bar"])
        assertEquals(Double.POSITIVE_INFINITY, `object`["baz"])
    }

    @Test
    fun testToStringWithUnsupportedNumbers() {
        // when the object contains an unsupported number, toString returns null!
        val `object` = JSONObject(Collections.singletonMap("foo", Double.NaN))
        assertEquals(null, `object`.toString())
    }

    @Test
    fun testMapConstructorCopiesContents() {
        val contents: MutableMap<String?, Any?> = HashMap()
        contents["foo"] = 5
        val `object` = JSONObject(contents)
        contents["foo"] = 10
        assertEquals(5, `object`["foo"])
    }

    @Test
    fun testMapConstructorWithBogusEntries() {
        val contents: MutableMap<Any?, Any?> = HashMap()
        contents[5] = 5
        try {
            JSONObject(contents)
            fail("JSONObject constructor doesn't validate its input!")
        } catch (e: Exception) {
        }
    }

    @Test
    fun testTokenerConstructor() {
        val `object` = JSONObject(JSONTokener("{\"foo\": false}"))
        assertEquals(1, `object`.length())
        assertEquals(false, `object`["foo"])
    }

    @Test
    fun testTokenerConstructorWrongType() {
        try {
            JSONObject(JSONTokener("[\"foo\", false]"))
            fail()
        } catch (e: JSONException) {
        }
    }

    @Test
    fun testTokenerConstructorNull() {
        try {
            JSONObject(null as JSONTokener?)
            fail()
        } catch (e: NullPointerException) {
        }
    }

    @Test
    fun testTokenerConstructorParseFail() {
        try {
            JSONObject(JSONTokener("{"))
            fail()
        } catch (e: JSONException) {
        }
    }

    @Test
    fun testStringConstructor() {
        val `object` = JSONObject("{\"foo\": false}")
        assertEquals(1, `object`.length())
        assertEquals(false, `object`["foo"])
    }

    @Test
    fun testStringConstructorWrongType() {
        try {
            JSONObject("[\"foo\", false]")
            fail()
        } catch (e: JSONException) {
        }
    }

    @Test
    fun testStringConstructorNull() {
        try {
            JSONObject(null as String?)
            fail()
        } catch (e: NullPointerException) {
        }
    }

    @Test
    fun testStringonstructorParseFail() {
        try {
            JSONObject("{")
            fail()
        } catch (e: JSONException) {
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
        val `object` = JSONObject()
        `object`.put("foo", 5)
        `object`.accumulate("foo", 6)
        val array = `object`.getJSONArray("foo")
        assertEquals("[5,6]", array.toString())
        `object`.accumulate("foo", 7)
        assertEquals("[5,6,7]", array.toString())
    }

    @Test
    fun testAccumulateExistingArray() {
        val array = JSONArray()
        val `object` = JSONObject()
        `object`.put("foo", array)
        `object`.accumulate("foo", 5)
        assertEquals("[5]", array.toString())
    }

    @Test
    fun testAccumulatePutArray() {
        val `object` = JSONObject()
        `object`.accumulate("foo", 5)
        assertEquals("{\"foo\":5}", `object`.toString())
        `object`.accumulate("foo", JSONArray())
        assertEquals("{\"foo\":[5,[]]}", `object`.toString())
    }

    @Test
    fun testEmptyStringKey() {
        val `object` = JSONObject()
        `object`.put("", 5)
        assertEquals(5, `object`[""])
        assertEquals("{\"\":5}", `object`.toString())
    }

    @Test
    fun testNullValue() {
        val `object` = JSONObject()
        `object`.put("foo", JSONObject.NULL)
        `object`.put("bar", null)
        // there are two ways to represent null; each behaves differently!
        assertTrue(`object`.has("foo"))
        assertFalse(`object`.has("bar"))
        assertTrue(`object`.isNull("foo"))
        assertTrue(`object`.isNull("bar"))
    }

    @Test
    fun testHas() {
        val `object` = JSONObject()
        `object`.put("foo", 5)
        assertTrue(`object`.has("foo"))
        assertFalse(`object`.has("bar"))
        assertFalse(`object`.has(null))
    }

    @Test
    fun testOptNull() {
        val `object` = JSONObject()
        `object`.put("foo", "bar")
        assertEquals(null, `object`.opt(null))
        assertEquals(false, `object`.optBoolean(null))
        assertEquals(Double.NaN, `object`.optDouble(null), 0.0)
        assertEquals(0, `object`.optInt(null))
        assertEquals(0L, `object`.optLong(null))
        assertEquals(null, `object`.optJSONArray(null))
        assertEquals(null, `object`.optJSONObject(null))
        assertEquals("", `object`.optString(null))
        assertEquals(true, `object`.optBoolean(null, true))
        assertEquals(0.0, `object`.optDouble(null, 0.0), 0.0)
        assertEquals(1, `object`.optInt(null, 1))
        assertEquals(1L, `object`.optLong(null, 1L))
        assertEquals("baz", `object`.optString(null, "baz"))
    }

    @Test
    fun testToStringWithIndentFactor() {
        val `object` = JSONObject()
        `object`.put("foo", JSONArray(Arrays.asList(5, 6) as Collection<*>?))
        `object`.put("bar", JSONObject())
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
        val string = `object`.toString(5)
        assertTrue(string, foobar == string || barfoo == string)
    }

    @Test
    fun testNames() {
        val `object` = JSONObject()
        `object`.put("foo", 5)
        `object`.put("bar", 6)
        `object`.put("baz", 7)
        val array = `object`.names()!!
        assertTrue(array.toString().contains("foo"))
        assertTrue(array.toString().contains("bar"))
        assertTrue(array.toString().contains("baz"))
    }

    @Test
    fun testKeysEmptyObject() {
        val `object` = JSONObject()
        assertFalse(`object`.keys().hasNext())
        try {
            `object`.keys().next()
            fail()
        } catch (e: NoSuchElementException) {
        }
    }

    @Test
    fun testKeys() {
        val `object` = JSONObject()
        `object`.put("foo", 5)
        `object`.put("bar", 6)
        `object`.put("foo", 7)
        val keys = `object`.keys() as Iterator<String>
        val result: MutableSet<String> = HashSet()
        assertTrue(keys.hasNext())
        result.add(keys.next())
        assertTrue(keys.hasNext())
        result.add(keys.next())
        assertFalse(keys.hasNext())
        assertEquals(HashSet(Arrays.asList("foo", "bar")), result)
        try {
            keys.next()
            fail()
        } catch (e: NoSuchElementException) {
        }
    }

    @Test
    fun testMutatingKeysMutatesObject() {
        val `object` = JSONObject()
        `object`.put("foo", 5)
        val keys: MutableIterator<*> = `object`.keys()
        keys.next()
        keys.remove()
        assertEquals(0, `object`.length())
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
        try {
            JSONObject.numberToString(Double.NaN)
            fail()
        } catch (e: JSONException) {
        }
        try {
            JSONObject.numberToString(Double.NEGATIVE_INFINITY)
            fail()
        } catch (e: JSONException) {
        }
        try {
            JSONObject.numberToString(Double.POSITIVE_INFINITY)
            fail()
        } catch (e: JSONException) {
        }
        assertEquals("0.001", JSONObject.numberToString(BigDecimal("0.001")))
        assertEquals(
            "9223372036854775806",
            JSONObject.numberToString(BigInteger("9223372036854775806"))
        )
        try {
            JSONObject.numberToString(null)
            fail()
        } catch (e: JSONException) {
        }
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
    private var mCorrectJsonObjectBasic: JSONObject? = null
    private var mCorrectJsonObjectNested: JSONObject? = null
    private var mCorrectJsonObjectWithArray: JSONObject? = null
    private var mCorrectJsonObjectNestedWithArray: JSONObject? = null
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
        Assert.assertThrows(JSONException::class.java) { JSONObject(mDuplicateKey) }
    }

    @Test
    fun copyJsonTest() {
        Assert.assertEquals(mCorrectJsonObjectBasic.toString(), JSONObject(mCorrectJsonObjectBasic).toString())
        Assert.assertEquals(mCorrectJsonObjectNested.toString(), JSONObject(mCorrectJsonObjectNested).toString())
        Assert.assertEquals(mCorrectJsonObjectWithArray.toString(), JSONObject(mCorrectJsonObjectWithArray).toString())
    }

    @Test
    fun objectToObjectTest() {
        Assert.assertEquals(mCorrectJsonObjectBasic.toString(), JSONObject.objectToObject(mCorrectJsonObjectBasic).toString())
        Assert.assertEquals(mCorrectJsonObjectNested.toString(), JSONObject.objectToObject(mCorrectJsonObjectNested).toString())
        Assert.assertNotEquals(mCorrectJsonObjectNested.toString(), JSONObject.objectToObject(mCorrectJsonObjectWithArray).toString())
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
        mCorrectJsonObjectNestedWithArray!!.deepClonedInto(jsonObjectSubType)

        // Test by passing result of base JSONObject's toString() to removeQuotes()
        // This is already done in the JSONObjectSubType object
        Assert.assertEquals(removeQuotes(mCorrectJsonObjectNestedWithArray.toString()), jsonObjectSubType.toString())
    }

    /**
     * Tests that the a new copy is returned instead of a reference to the original.
     */
    @Test
    fun deepCloneReferenceTest() {
        val clone = mCorrectJsonObjectBasic!!.deepClone()
        // Both objects should point to different memory address
        Assert.assertNotEquals(clone, mCorrectJsonObjectBasic)
    }

    @Test
    fun fromMapTest() {
        val fromMapJsonObject = JSONObject.fromMap(booleanMap)
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
