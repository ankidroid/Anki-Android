//noinspection MissingCopyrightHeader
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
 *
 * The only difference with the original are:
 * * the package, and hence the class actually tested, are com.ichi2.utils.
 * * the class don't inherit TestCase
 * * the equality of double uses explicitly a delta of 0
 */
package com.ichi2.utils

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.testutils.EmptyApplication
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.json.JSONTokener
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.lang.Boolean.FALSE
import java.lang.Boolean.TRUE
import java.lang.Double.*
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/**
 * This black box test was written without inspecting the non-free org.json sourcecode.
 */
@RunWith(AndroidJUnit4::class)
@Config(application = EmptyApplication::class)
@KotlinCleanup("have toJSONObject return non-null")
class JSONArrayTest {
    @Test
    fun testEmptyArray() {
        val array = JSONArray()
        assertEquals(0, array.length())
        assertEquals("", array.join(" AND "))
        assertFailsWith<JSONException> {
            array[0]
        }
        assertFailsWith<JSONException> {
            array.getBoolean(0)
        }
        assertEquals("[]", array.toString())
        assertEquals("[]", array.toString(4))
        // out of bounds is co-opted with defaulting
        assertTrue(array.isNull(0))
        assertNull(array.opt(0))
        assertFalse(array.optBoolean(0))
        assertTrue(array.optBoolean(0, true))
        // bogus (but documented) behaviour: returns null rather than an empty object!
        assertNull(array.toJSONObject(JSONArray()))
    }

    @Test
    fun testEqualsAndHashCode() {
        val a = JSONArray()
        val b = JSONArray()
        assertTrue(a == b)
        assertEquals("equals() not consistent with hashCode()", a.hashCode(), b.hashCode())
        a.put(true)
        a.put(false)
        b.put(true)
        b.put(false)
        assertTrue(a == b)
        assertEquals(a.hashCode(), b.hashCode())
        b.put(true)
        assertFalse(a == b)
        assertTrue(a.hashCode() != b.hashCode())
    }

    @Test
    fun testBooleans() {
        val array = JSONArray()
        array.put(true)
        array.put(false)
        array.put(2, false)
        array.put(3, false)
        array.put(2, true)
        assertEquals("[true,false,true,false]", array.toString())
        assertEquals(4, array.length())
        assertEquals(TRUE, array[0])
        assertEquals(FALSE, array[1])
        assertEquals(TRUE, array[2])
        assertEquals(FALSE, array[3])
        assertFalse(array.isNull(0))
        assertFalse(array.isNull(1))
        assertFalse(array.isNull(2))
        assertFalse(array.isNull(3))
        assertEquals(true, array.optBoolean(0))
        assertEquals(false, array.optBoolean(1, true))
        assertEquals(true, array.optBoolean(2, false))
        assertEquals(false, array.optBoolean(3))
        assertEquals("true", array.getString(0))
        assertEquals("false", array.getString(1))
        assertEquals("true", array.optString(2))
        assertEquals("false", array.optString(3, "x"))
        assertEquals("[\n     true,\n     false,\n     true,\n     false\n]", array.toString(5))
        var other = JSONArray()
        other.put(true)
        other.put(false)
        other.put(true)
        other.put(false)
        assertTrue(array == other)
        other.put(true)
        assertFalse(array == other)
        other = JSONArray()
        other.put("true")
        other.put("false")
        other.put("truE")
        other.put("FALSE")
        assertFalse(array == other)
        assertFalse(other == array)
        assertEquals(true, other.getBoolean(0))
        assertEquals(false, other.optBoolean(1, true))
        assertEquals(true, other.optBoolean(2))
        assertEquals(false, other.getBoolean(3))
    }

    /**
     * Our behaviour is questioned by this bug:
     * http://code.google.com/p/android/issues/detail?id=7257
     */
    @Test
    fun testParseNullYieldsJSONObjectNull() {
        val array = JSONArray("[\"null\",null]")
        array.put(null)
        assertEquals("null", array[0])
        assertEquals(JSONObject.NULL, array[1])
        assertFailsWith<JSONException> {
            array[2]
        }
        assertEquals("null", array.getString(0))
        assertEquals("null", array.getString(1))
        assertFailsWith<JSONException> {
            array.getString(2)
        }
    }

    @Test
    fun testNumbers() {
        val array = JSONArray()
        array.put(Double.MIN_VALUE)
        array.put(9223372036854775806L)
        array.put(Double.MAX_VALUE)
        array.put(-0.0)
        assertEquals(4, array.length())
        // toString() and getString(int) return different values for -0d
        assertEquals("[4.9E-324,9223372036854775806,1.7976931348623157E308,-0]", array.toString())
        assertEquals(Double.MIN_VALUE, array[0])
        assertEquals(9223372036854775806L, array[1])
        assertEquals(Double.MAX_VALUE, array[2])
        assertEquals(-0.0, array[3])
        assertEquals(Double.MIN_VALUE, array.getDouble(0), 0.0)
        assertEquals(9.223372036854776E18, array.getDouble(1), 0.0)
        assertEquals(Double.MAX_VALUE, array.getDouble(2), 0.0)
        assertEquals(-0.0, array.getDouble(3), 0.0)
        assertEquals(0, array.getLong(0))
        assertEquals(9223372036854775806L, array.getLong(1))
        assertEquals(Long.MAX_VALUE, array.getLong(2))
        assertEquals(0, array.getLong(3))
        assertEquals(0, array.getInt(0))
        assertEquals(-2, array.getInt(1))
        assertEquals(Int.MAX_VALUE, array.getInt(2))
        assertEquals(0, array.getInt(3))
        assertEquals(Double.MIN_VALUE, array.opt(0))
        assertEquals(Double.MIN_VALUE, array.optDouble(0), 0.0)
        assertEquals(0, array.optLong(0, 1L))
        assertEquals(0, array.optInt(0, 1))
        assertEquals("4.9E-324", array.getString(0))
        assertEquals("9223372036854775806", array.getString(1))
        assertEquals("1.7976931348623157E308", array.getString(2))
        assertEquals("-0.0", array.getString(3))
        val other = JSONArray()
        other.put(Double.MIN_VALUE)
        other.put(9223372036854775806L)
        other.put(Double.MAX_VALUE)
        other.put(-0.0)
        assertTrue(array == other)
        other.put(0, 0L)
        assertFalse(array == other)
    }

    @Test
    fun testStrings() {
        val array = JSONArray()
        array.put("true")
        array.put("5.5")
        array.put("9223372036854775806")
        array.put("null")
        array.put("5\"8' tall")
        assertEquals(5, array.length())
        assertEquals(
            "[\"true\",\"5.5\",\"9223372036854775806\",\"null\",\"5\\\"8' tall\"]",
            array.toString()
        )
        // although the documentation doesn't mention it, join() escapes text and wraps
        // strings in quotes
        assertEquals(
            "\"true\" \"5.5\" \"9223372036854775806\" \"null\" \"5\\\"8' tall\"",
            array.join(" ")
        )
        assertEquals("true", array[0])
        assertEquals("null", array.getString(3))
        assertEquals("5\"8' tall", array.getString(4))
        assertEquals("true", array.opt(0))
        assertEquals("5.5", array.optString(1))
        assertEquals("9223372036854775806", array.optString(2, null))
        assertEquals("null", array.optString(3, "-1"))
        assertFalse(array.isNull(0))
        assertFalse(array.isNull(3))
        assertEquals(true, array.getBoolean(0))
        assertEquals(true, array.optBoolean(0))
        assertEquals(true, array.optBoolean(0, false))
        assertEquals(0, array.optInt(0))
        assertEquals(-2, array.optInt(0, -2))
        assertEquals(5.5, array.getDouble(1), 0.0)
        assertEquals(5L, array.getLong(1))
        assertEquals(5, array.getInt(1))
        assertEquals(5, array.optInt(1, 3))
        // The last digit of the string is a 6 but getLong returns a 7. It's probably parsing as a
        // double and then converting that to a long. This is consistent with JavaScript.
        assertEquals(9223372036854775807L, array.getLong(2))
        assertEquals(9.223372036854776E18, array.getDouble(2), 0.0)
        assertEquals(Int.MAX_VALUE, array.getInt(2))
        assertFalse(array.isNull(3))
        assertFailsWith<JSONException> {
            array.getDouble(3)
        }
        assertEquals(NaN, array.optDouble(3), 0.0)
        assertEquals(-1.0, array.optDouble(3, -1.0), 0.0)
    }

    @Test
    fun testJoin() {
        val array = JSONArray()
        array.put(null)
        assertEquals("null", array.join(" & "))
        array.put("\"")
        assertEquals("null & \"\\\"\"", array.join(" & "))
        array.put(5)
        assertEquals("null & \"\\\"\" & 5", array.join(" & "))
        array.put(true)
        assertEquals("null & \"\\\"\" & 5 & true", array.join(" & "))
        array.put(JSONArray(mutableListOf(true, false)))
        assertEquals("null & \"\\\"\" & 5 & true & [true,false]", array.join(" & "))
        array.put(JSONObject().apply { put("x", 6) })
        assertEquals("null & \"\\\"\" & 5 & true & [true,false] & {\"x\":6}", array.join(" & "))
    }

    @Test
    fun testJoinWithSpecialCharacters() {
        val array = JSONArray(mutableListOf(5, 6))
        assertEquals("5\"6", array.join("\""))
    }

    @Test
    fun testToJSONObject() {
        val keys = JSONArray()
        keys.put("a")
        keys.put("b")
        val values = JSONArray()
        values.put(5.5)
        values.put(false)
        val value = values.toJSONObject(keys)
        assertEquals(5.5, value!!["a"])
        assertEquals(false, value["b"])
        keys.put(0, "a")
        values.put(0, 11.0)
        assertEquals(5.5, value["a"])
    }

    @Test
    fun testToJSONObjectWithNulls() {
        val keys = JSONArray()
        keys.put("a")
        keys.put("b")
        val values = JSONArray()
        values.put(5.5)
        values.put(null)
        // null values are stripped!
        val value = values.toJSONObject(keys)
        assertEquals(1, value!!.length())
        assertFalse(value.has("b"))
        assertEquals("{\"a\":5.5}", value.toString())
    }

    @Test
    fun testToJSONObjectMoreNamesThanValues() {
        val keys = JSONArray()
        keys.put("a")
        keys.put("b")
        val values = JSONArray()
        values.put(5.5)
        val value = values.toJSONObject(keys)
        assertEquals(1, value!!.length())
        assertEquals(5.5, value["a"])
    }

    @Test
    fun testToJSONObjectMoreValuesThanNames() {
        val keys = JSONArray()
        keys.put("a")
        val values = JSONArray()
        values.put(5.5)
        values.put(11.0)
        val value = values.toJSONObject(keys)
        assertEquals(1, value!!.length())
        assertEquals(5.5, value["a"])
    }

    @Test
    fun testToJSONObjectNullKey() {
        val keys = JSONArray()
        keys.put(JSONObject.NULL)
        val values = JSONArray()
        values.put(5.5)
        val value = values.toJSONObject(keys)
        assertEquals(1, value!!.length())
        assertEquals(5.5, value["null"])
    }

    @Test
    fun testPutUnsupportedNumbers() {
        val array = JSONArray()
        assertFailsWith<JSONException> {
            array.put(Double.NaN)
        }
        assertFailsWith<JSONException> {
            array.put(0, Double.NEGATIVE_INFINITY)
        }
        assertFailsWith<JSONException> {
            array.put(0, Double.POSITIVE_INFINITY)
        }
    }

    @Test
    fun testPutUnsupportedNumbersAsObject() {
        val array = JSONArray()

        assertFailsWith<JSONException> {
            array.put(NaN)
            array.put(NEGATIVE_INFINITY)
            array.put(POSITIVE_INFINITY)
            assertEquals(null, array.toString())
        }
    }

    /**
     * Although JSONArray is usually defensive about which numbers it accepts,
     * it doesn't check inputs in its constructor.
     */
    @Test
    fun testCreateWithUnsupportedNumbers() {
        val array = JSONArray(mutableListOf(5.5, Double.NaN))
        assertEquals(2, array.length())
        assertEquals(5.5, array.getDouble(0), 0.0)
        assertEquals(Double.NaN, array.getDouble(1), 0.0)
    }

    @Test
    fun testToStringWithUnsupportedNumbers() {
        // when the array contains an unsupported number, toString returns null!
        val array = JSONArray(mutableListOf(5.5, Double.NaN))
        assertNull(array.toString())
    }

    @Test
    fun testListConstructorCopiesContents() {
        val contents = mutableListOf<Any?>(5)
        val array = JSONArray(contents)
        contents[0] = 10
        assertEquals(5, array[0])
    }

    @Test
    fun testTokenerConstructor() {
        val value = JSONArray(JSONTokener("[false]"))
        assertEquals(1, value.length())
        assertEquals(false, value[0])
    }

    @Test
    fun testTokenerConstructorWrongType() {
        assertFailsWith<JSONException> {
            JSONArray(JSONTokener("{\"foo\": false}"))
        }
    }

    @Test
    fun testTokenerConstructorParseFail() {
        try {
            assertFailsWith<JSONException> {
                JSONArray(JSONTokener("["))
            }
        } catch (e: StackOverflowError) {
            fail("Stack overflowed on input: \"[\"")
        }
    }

    @Test
    fun testStringConstructor() {
        val value = JSONArray("[false]")
        assertEquals(1, value.length())
        assertEquals(false, value[0])
    }

    @Test
    fun testStringConstructorWrongType() {
        assertFailsWith<JSONException> {
            JSONArray("{\"foo\": false}")
        }
    }

    @Test
    fun testStringConstructorParseFail() {
        try {
            assertFailsWith<JSONException> {
                JSONArray("[")
            }
        } catch (e: StackOverflowError) {
            fail("Stack overflowed on input: \"[\"")
        }
    }

    @Test
    fun testCreate() {
        val array = JSONArray(mutableListOf(5.5, true))
        assertEquals(2, array.length())
        assertEquals(5.5, array.getDouble(0), 0.0)
        assertEquals(true, array[1])
        assertEquals("[5.5,true]", array.toString())
    }

    @Test
    fun testAccessOutOfBounds() {
        val array = JSONArray()
        array.put("foo")
        assertEquals(null, array.opt(3))
        assertEquals(null, array.opt(-3))
        assertEquals("", array.optString(3))
        assertEquals("", array.optString(-3))
        assertFailsWith<JSONException> {
            array[3]
        }
        assertFailsWith<JSONException> {
            array[-3]
        }
        assertFailsWith<JSONException> {
            array.getString(3)
        }
        assertFailsWith<JSONException> {
            array.getString(-3)
        }
    }
}
