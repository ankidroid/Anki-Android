/***
 * Copyright (c) 2021 Tarek Mohamed Abdalla <tarekkma@gmail.com>
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.

 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.

 * You should have received a copy of the GNU General Public License along with
 * this program.  If not, see <http://www.gnu.org/licenses/>.

 * This work include code under:

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
package com.ichi2.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.io.JsonEOFException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.BigIntegerNode;
import com.ichi2.anki.AnkiSerialization;

import org.intellij.lang.annotations.Language;
import org.junit.Test;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;

import static com.ichi2.utils.JSONUtilsTest.assertThrowsJSONExceptionEncapsulating;
import static com.ichi2.utils.ListUtil.assertListEquals;
import static org.junit.Assert.*;


import org.junit.runner.RunWith;

import androidx.test.ext.junit.runners.AndroidJUnit4;

/**
 * This black box test was written without inspecting the non-free org.json sourcecode.
 */
@RunWith(AndroidJUnit4.class)
public class JSONArrayTest {

    @Test
    public void testEmptyArray() {
        JSONArray array = new JSONArray();
        assertEquals(0, array.length());
        // assertEquals("", array.join(" AND "));
        assertThrowsJSONExceptionEncapsulating(IndexOutOfBoundsException.class,
                () -> array.get(0));
        assertThrowsJSONExceptionEncapsulating(IndexOutOfBoundsException.class,
                () -> array.getBoolean(0));
        assertEquals("[]", array.toString());
        // assertEquals("[]", array.toString(4)); // not implemented
        // out of bounds is co-opted with defaulting
        assertTrue(array.isNull(0));
        assertNull(array.opt(0));
        assertFalse(array.optBoolean(0));
        assertTrue(array.optBoolean(0, true));
        // bogus (but documented) behaviour: returns null rather than an empty object!
        assertNull(array.toJSONObject(new JSONArray()));
    }

    @Test
    public void testEqualsAndHashCode() {
        JSONArray a = new JSONArray();
        JSONArray b = new JSONArray();
        assertTrue(a.equals(b));
        assertEquals("equals() not consistent with hashCode()", a.hashCode(), b.hashCode());
        a.put(true);
        a.put(false);
        b.put(true);
        b.put(false);
        assertTrue(a.equals(b));
        assertEquals(a.hashCode(), b.hashCode());
        b.put(true);
        assertFalse(a.equals(b));
        assertTrue(a.hashCode() != b.hashCode());
    }
    @Test
    public void testBooleans() {
        JSONArray array = new JSONArray();
        array.put(true);
        array.put(false);
        array.put(new Integer(2), false);
        array.put(new Integer(3), false);
        array.put(new Integer(2), true);
        assertEquals("[true,false,true,false]", array.toString());
        assertEquals(4, array.length());
        assertEquals(Boolean.TRUE, array.get(0));
        assertEquals(Boolean.FALSE, array.get(1));
        assertEquals(Boolean.TRUE, array.get(2));
        assertEquals(Boolean.FALSE, array.get(3));
        assertFalse(array.isNull(0));
        assertFalse(array.isNull(1));
        assertFalse(array.isNull(2));
        assertFalse(array.isNull(3));
        assertEquals(true, array.optBoolean(0));
        assertEquals(false, array.optBoolean(1, true));
        assertEquals(true, array.optBoolean(2, false));
        assertEquals(false, array.optBoolean(3));
        assertEquals("true", array.getString(0));
        assertEquals("false", array.getString(1));
        assertEquals("true", array.optString(2));
        assertEquals("false", array.optString(3, "x"));
        // assertEquals("[\n     true,\n     false,\n     true,\n     false\n]", array.toString(5)); not implemented
        JSONArray other = new JSONArray();
        other.put(true);
        other.put(false);
        other.put(true);
        other.put(false);
        assertTrue(array.equals(other));
        other.put(true);
        assertFalse(array.equals(other));
        other = new JSONArray();
        other.put("true");
        other.put("false");
        other.put("truE");
        other.put("FALSE");
        assertFalse(array.equals(other));
        assertFalse(other.equals(array));
        assertEquals(true, other.getBoolean(0));
        assertEquals(false, other.optBoolean(1, true));
        assertEquals(true, other.optBoolean(2));
        assertEquals(false, other.getBoolean(3));
    }

    /**
     * Our behaviour is questioned by this bug:
     * http://code.google.com/p/android/issues/detail?id=7257
     */
    @Test
    public void testParseNullYieldsJSONObjectNull() {
        JSONArray array = new JSONArray("[\"null\",null]");
        // array.put((Object) null);
        assertEquals("null", array.get(0));
        assertEquals(JSONObject.NULL, array.get(1));
        //assertThrowsJSONExceptionEncapsulating(
        //        () -> array.get(2));
        assertEquals("null", array.getString(0));
        assertEquals("null", array.getString(1));
        // assertThrowsJSONExceptionEncapsulating(
        //        () -> array.getString(2));
    }
    @Test
    public void testNumbers() {
        JSONArray array = new JSONArray();
        array.put(Double.MIN_VALUE);
        array.put(9223372036854775806L);
        array.put(Double.MAX_VALUE);
        array.put(-0d);
        assertEquals(4, array.length());
        // toString() and getString(int) return different values for -0d
        // jackson add .0 to indicate double. org.json do not. So difference in output here
        //assertEquals("[4.9E-324,9223372036854775806,1.7976931348623157E308,-0]", array.toString());
        assertEquals("[4.9E-324,9223372036854775806,1.7976931348623157E308,-0.0]", array.toString());
        // Can't be done in jackson without rewritting
        assertEquals(Double.MIN_VALUE, array.get(0));
        assertEquals(9223372036854775806L, array.get(1));
        assertEquals(Double.MAX_VALUE, array.get(2));
        assertEquals(-0d, array.get(3));
        assertEquals(Double.MIN_VALUE, array.getDouble(0), 0);
        assertEquals(9.223372036854776E18, array.getDouble(1), 0);
        assertEquals(Double.MAX_VALUE, array.getDouble(2), 0);
        assertEquals(-0d, array.getDouble(3), 0);
        assertEquals(0, array.getLong(0));
        assertEquals(9223372036854775806L, array.getLong(1));
        assertEquals(Long.MAX_VALUE, array.getLong(2));
        assertEquals(0, array.getLong(3));
        assertEquals(0, array.getInt(0));
        assertEquals(-2, array.getInt(1));
        assertEquals(Integer.MAX_VALUE, array.getInt(2));
        assertEquals(0, array.getInt(3));
        assertEquals(Double.MIN_VALUE, array.opt(0));
        assertEquals(Double.MIN_VALUE, array.optDouble(0), 0);
        assertEquals(0, array.optLong(0, 1L));
        assertEquals(0, array.optInt(0, 1));
        assertEquals("4.9E-324", array.getString(0));
        assertEquals("9223372036854775806", array.getString(1));
        assertEquals("1.7976931348623157E308", array.getString(2));
        assertEquals("-0.0", array.getString(3));
        JSONArray other = new JSONArray();
        other.put(Double.MIN_VALUE);
        other.put(9223372036854775806L);
        other.put(Double.MAX_VALUE);
        other.put(-0d);
        assertTrue(array.equals(other));
        other.put(new Integer(0), 0L);
        assertFalse(array.equals(other));
    }
    @Test
    public void testStrings() {
        JSONArray array = new JSONArray();
        array.put("true");
        array.put("5.5");
        array.put("9223372036854775806");
        array.put("null");
        array.put("5\"8' tall");
        assertEquals(5, array.length());
        assertEquals("[\"true\",\"5.5\",\"9223372036854775806\",\"null\",\"5\\\"8' tall\"]",
                array.toString());
        // although the documentation doesn't mention it, join() escapes text and wraps
        // strings in quotes
        // assertEquals("\"true\" \"5.5\" \"9223372036854775806\" \"null\" \"5\\\"8' tall\"",
        //        array.join(" "));
        assertEquals("true", array.get(0));
        assertEquals("null", array.getString(3));
        assertEquals("5\"8' tall", array.getString(4));
        assertEquals("true", array.opt(0));
        assertEquals("5.5", array.optString(1));
        assertEquals("9223372036854775806", array.optString(2, null));
        assertEquals("null", array.optString(3, "-1"));
        assertFalse(array.isNull(0));
        assertFalse(array.isNull(3));
        assertEquals(true, array.getBoolean(0));
        assertEquals(true, array.optBoolean(0));
        assertEquals(true, array.optBoolean(0, false));
        assertEquals(0, array.optInt(0));
        assertEquals(-2, array.optInt(0, -2));
        assertEquals(5.5d, array.getDouble(1), 0);
        assertEquals(5L, array.getLong(1));
        assertEquals(5, array.getInt(1));
        assertEquals(5, array.optInt(1, 3));
        // The last digit of the string is a 6 but getLong returns a 7. It's probably parsing as a
        // double and then converting that to a long. This is consistent with JavaScript.
        assertEquals(9223372036854775807L, array.getLong(2));
        assertEquals(9.223372036854776E18, array.getDouble(2), 0);
        assertEquals(Integer.MAX_VALUE, array.getInt(2));
        assertFalse(array.isNull(3));
        assertThrowsJSONExceptionEncapsulating(
                () -> array.getDouble(3));
        assertEquals(Double.NaN, array.optDouble(3), 0);
        assertEquals(-1.0d, array.optDouble(3, -1.0d), 0);
    }
    @Test
    public void testToJSONObject() {
        JSONArray keys = new JSONArray();
        keys.put("a");
        keys.put("b");
        JSONArray values = new JSONArray();
        values.put(5.5d);
        values.put(false);
        JSONObject object = values.toJSONObject(keys);
        assertEquals(5.5d, object.get("a"));
        assertEquals(false, object.get("b"));
        keys.put(0, "a");
        values.put(new Integer(0), 11.0d);
        assertEquals(5.5d, object.get("a"));
    }
    @Test
    public void testToJSONObjectMoreNamesThanValues() {
        JSONArray keys = new JSONArray();
        keys.put("a");
        keys.put("b");
        JSONArray values = new JSONArray();
        values.put(5.5d);
        JSONObject object = values.toJSONObject(keys);
        assertEquals(1, object.length());
        assertEquals(5.5d, object.get("a"));
    }
    @Test
    public void testToJSONObjectMoreValuesThanNames() {
        JSONArray keys = new JSONArray();
        keys.put("a");
        JSONArray values = new JSONArray();
        values.put(5.5d);
        values.put(11.0d);
        JSONObject object = values.toJSONObject(keys);
        assertEquals(1, object.length());
        assertEquals(5.5d, object.get("a"));
    }
    @Test
    public void testToJSONObjectNullKey() {
        JSONArray keys = new JSONArray();
        keys.put(JSONObject.NULL);
        JSONArray values = new JSONArray();
        values.put(5.5d);
        JSONObject object = values.toJSONObject(keys);
        assertEquals(1, object.length());
        assertEquals(5.5d, object.get("null"));
    }
    @Test
    public void testPutUnsupportedNumbers() {
        JSONArray array = new JSONArray();
        assertThrowsJSONExceptionEncapsulating(
                () -> array.put(Double.NaN));
        assertThrowsJSONExceptionEncapsulating(
                () -> array.put(new Integer(0), Double.NEGATIVE_INFINITY));
        assertThrowsJSONExceptionEncapsulating(
                () -> array.put(new Integer(0), Double.POSITIVE_INFINITY));
    }
    /**
     * Although JSONArray is usually defensive about which numbers it accepts,
     * it doesn't check inputs in its constructor.
     */
    @Test
    public void testCreateWithUnsupportedNumbers() {
        JSONArray array = new JSONArray(Arrays.asList(5.5, Double.NaN));
        assertEquals(2, array.length());
        assertEquals(5.5, array.getDouble(0), 0);
        assertEquals(Double.NaN, array.getDouble(1), 0);
    }

    @Test
    public void testListConstructorCopiesContents() {
        List<Object> contents = Arrays.<Object>asList(5);
        JSONArray array = new JSONArray(contents);
        contents.set(0, 10);
        assertEquals(5, array.get(0));
    }
    @Test
    public void testStringConstructor() {
        JSONArray object = new JSONArray("[false]");
        assertEquals(1, object.length());
        assertEquals(false, object.get(0));
    }
    @Test
    public void testStringConstructorWrongType() {
        assertThrowsJSONExceptionEncapsulating(JSONException.class,
                () -> new JSONArray("{\"foo\": false}"));
    }
    @Test
    public void testStringConstructorNull() {
        try {
            new JSONArray((String) null);
            fail();
        } catch (JSONException e) {
            assertEquals(e.getCause().getClass(), IllegalArgumentException.class);
        }
    }
    @Test
    public void testStringConstructorParseFail() {
        try {
            assertThrowsJSONExceptionEncapsulating(JsonEOFException.class,
                    () -> new JSONArray("["));
        } catch (StackOverflowError e) {
            fail("Stack overflowed on input: \"[\"");
        }
    }
    @Test
    public void testCreate() {
        JSONArray array = new JSONArray(Arrays.asList(5.5, true));
        assertEquals(2, array.length());
        assertEquals(5.5, array.getDouble(0), 0);
        assertEquals(true, array.get(1));
        assertEquals("[5.5,true]", array.toString());
    }
    @Test
    public void testAccessOutOfBounds() {
        JSONArray array = new JSONArray();
        array.put("foo");
        assertEquals(null, array.opt(3));
        assertEquals(null, array.opt(-3));
        assertEquals("", array.optString(3));
        assertEquals("", array.optString(-3));
        assertThrowsJSONExceptionEncapsulating(IndexOutOfBoundsException.class,
                () -> array.get(3));
        assertThrowsJSONExceptionEncapsulating(IndexOutOfBoundsException.class,
                () -> array.get(-3));
        assertThrowsJSONExceptionEncapsulating(IndexOutOfBoundsException.class,
                () -> array.getString(3));
        assertThrowsJSONExceptionEncapsulating(IndexOutOfBoundsException.class,
                () -> array.getString(-3));
    }

    @Test
    public void testDatabindingSerializeDeserialize() throws JsonProcessingException {
        @Language("JSON") String jsonArrayString = "[\"a\",\"b\",{\"c\":\"d\"},[\"e\",\"f\",[\"g\"]]]";
        JSONArray arr = new ObjectMapper().readValue(jsonArrayString, JSONArray.class);

        assertEquals(arr.get(0), "a");
        assertEquals(arr.get(1), "b");
        assertEquals(arr.getJSONObject(2).get("c"), "d");
        assertEquals(arr.getJSONArray(3).get(0), "e");
        assertEquals(arr.getJSONArray(3).get(1), "f");
        assertEquals(arr.getJSONArray(3).getJSONArray(2).get(0), "g");

        String serializedJsonString = AnkiSerialization.getObjectMapper().writeValueAsString(arr);
        assertEquals(jsonArrayString, serializedJsonString);
    }


    @Test
    public void emptyJSONArray() {
        JSONArray jsonArray = new JSONArray();
        assertEquals("[]", jsonArray.toString());
    }


    @Test
    public void createJSONArray() {
        ArrayNode arrayNode = new ObjectMapper().createArrayNode();
        arrayNode.add(1);
        arrayNode.add(1.2);

        JSONArray jsonArray = new JSONArray(arrayNode);
        jsonArray.put(55);


        assertEquals(arrayNode, jsonArray.getRootJsonNode());
        assertEquals("[1,1.2,55]", jsonArray.toString());
    }


    @Test
    public void testJSONArray_from_JSONArray() {
        JSONArray jsonArray1 = new JSONArray();

        jsonArray1.put(false);

        JSONArray jsonArray2 = new JSONArray(jsonArray1);

        // not same reference
        assertNotSame(jsonArray1, jsonArray2);
        // same content
        assertEquals(jsonArray1.getRootJsonNode(), jsonArray2.getRootJsonNode());

        jsonArray1.put(55);

        // content diverged
        assertNotEquals(jsonArray1.getRootJsonNode(), jsonArray2.getRootJsonNode());
    }


    @Test
    public void testWillParseFromString() {
        String jsonString = "[\"a\",\"b\",5.5,4,false]";
        JSONArray array = new JSONArray(jsonString);

        assertEquals("a", array.get(0));
        assertEquals("b", array.get(1));
        assertEquals(5.5, array.get(2));
        assertEquals(4, array.get(3));
        assertEquals(false, array.get(4));
    }


    @Test
    public void testCreateFromArray() {
        int[] arr1 = new int[] {1, 23, 45, 66, 77, 1};
        JSONArray jarr1 = new JSONArray(arr1);
        for (int i = 0; i < arr1.length; i++) {
            assertEquals(arr1[i], jarr1.getInt(i));
        }


        Long[] arr2 = new Long[] {1L, 23L, 45L, 66L, 77L, 1L};
        JSONArray jarr2 = new JSONArray(arr2);
        for (int i = 0; i < arr2.length; i++) {
            assertEquals(arr2[i], (Long) jarr2.getLong(i));
        }
    }


    @Test
    public void testCreateFromCollection() {
        List<Double> doubleList = Arrays.asList(1.0, 44.4, 22.4, 897.5);
        JSONArray doubleListJSON = new JSONArray(doubleList);
        for (int i = 0; i < doubleList.size(); i++) {
            assertEquals(doubleList.get(i), (Double) doubleListJSON.getDouble(i));
        }
    }


    @Test
    public void testCreateFromIterator() {
        List<String> list = Arrays.asList("a", "b", "c", "d", "e", "f");
        JSONArray json = new JSONArray(list.iterator());
        for (int i = 0; i < list.size(); i++) {
            assertEquals(list.get(i), json.getString(i));
        }
    }


    @Test
    public void testPutGetInt() {
        JSONArray jsonArray = new JSONArray();
        jsonArray.put(22);
        assertEquals(22, jsonArray.get(0));
        assertEquals(22, jsonArray.getInt(0));
    }


    @Test
    public void testPutGetDouble() {
        JSONArray jsonArray = new JSONArray();
        jsonArray.put(22.5);
        assertEquals(22.5, jsonArray.get(0));
        assertEquals(22.5, jsonArray.getDouble(0), 0);
    }


    @Test
    public void testPutGetString() {
        JSONArray jsonArray = new JSONArray();
        jsonArray.put("tarekkma");
        assertEquals("tarekkma", jsonArray.get(0));
        assertEquals("tarekkma", jsonArray.getString(0));
    }


    @Test
    public void testPutGetJSONArray() {
        JSONArray jsonArray = new JSONArray();

        JSONArray content = new JSONArray("[1,2,4,66,32,2.3]");

        jsonArray.put(content);
        assertEquals(content, jsonArray.get(0));
        assertEquals(content, jsonArray.getJSONArray(0));

        assertThrows(JSONException.class, () -> {
            jsonArray.getJSONObject(0);
        });
    }


    @Test
    public void testPutGetJSONObject() {
        JSONArray jsonArray = new JSONArray();

        JSONObject content = new JSONObject("{\"a\": \"b\",\"c\": [\"a\"],\"d\": {\"name\": \"test\"}}");

        jsonArray.put(content);
        assertEquals(content, jsonArray.get(0));
        assertEquals(content, jsonArray.getJSONObject(0));

        assertThrows(JSONException.class, () -> {
            jsonArray.getJSONArray(0);
        });
    }


    @Test
    public void testPutGetObject() {
        JSONArray jsonArray = new JSONArray();

        Object content = JSONObject.NULL;

        jsonArray.put(content);
        assertEquals(content, jsonArray.get(0));
    }


    @SuppressWarnings("ConstantConditions")
    @Test
    public void testPutIndexGetBoolean() {
        JSONArray jsonArray = new JSONArray();

        boolean content = false;

        jsonArray.put((Integer) 1, content);

        assertEquals(JSONObject.NULL, jsonArray.get(0));
        assertEquals(content, jsonArray.get(1));
        assertEquals(content, jsonArray.getBoolean(1));
    }


    @Test
    public void testPutIndexGetDouble() {
        JSONArray jsonArray = new JSONArray();

        double content = 2323.2;

        jsonArray.put((Integer) 1, content);

        assertEquals(JSONObject.NULL, jsonArray.get(0));
        assertEquals(content, jsonArray.get(1));
        assertEquals(content, jsonArray.getDouble(1), 0);
    }


    @Test
    public void testPutIndexGetInt() {
        JSONArray jsonArray = new JSONArray();

        int content = 12;

        jsonArray.put((Integer) 1, content);

        assertEquals(JSONObject.NULL, jsonArray.get(0));
        assertEquals(content, jsonArray.get(1));
        assertEquals(content, jsonArray.getInt(1));
    }


    @Test
    public void testPutIndexGetLong() {
        JSONArray jsonArray = new JSONArray();

        long content = 12L;

        jsonArray.put((Integer) 1, content);

        assertEquals(JSONObject.NULL, jsonArray.get(0));
        assertEquals(content, jsonArray.get(1));
        assertEquals(content, jsonArray.getLong(1));
    }


    @Test
    public void testPutIndexGetObject() {
        JSONArray jsonArray = new JSONArray();

        Object content = JSONObject.NULL;

        jsonArray.put(1, content);

        assertEquals(JSONObject.NULL, jsonArray.get(0));
        assertEquals(content, jsonArray.get(1));
    }


    @Test
    public void testPutIndexGetJsonNode() {
        JSONArray jsonArray = new JSONArray();

        JsonNode content = BigIntegerNode.valueOf(BigInteger.TEN);

        jsonArray.put(1, content);

        assertEquals(JSONObject.NULL, jsonArray.get(0));
        assertEquals(content, jsonArray.getJsonNode(1));
    }


    @Test
    public void testOptString() {
        JSONArray jsonArray = new JSONArray("[1,\"a\", false]");
        assertEquals("a", jsonArray.optString(1));
        assertEquals("1", jsonArray.optString(0));
        assertEquals("false", jsonArray.optString(2));
        assertEquals("", jsonArray.optString(10));
        assertEquals("", jsonArray.optString(-1));
    }


    @Test
    public void testDeepClone() {
        JSONArray ja1 = new JSONArray("[1,\"a\", false]");
        JSONArray ja2 = ja1.deepClone();

        assertNotSame(ja1, ja2);
        assertEquals(ja1.getRootJsonNode(), ja2.getRootJsonNode());

        ja1.put("ahlan");

        assertNotSame(ja1, ja2);
        assertNotEquals(ja1.getRootJsonNode(), ja2.getRootJsonNode());
    }


    @Test
    public void test_jsonArrayIterable() {
        JSONArray ja1 = new JSONArray("[1]");
        JSONArray ja2 = new JSONArray("[2]");
        JSONArray ja3 = new JSONArray("[3]");
        List<JSONArray> jsonArrays = Arrays.asList(ja1, ja2, ja3);

        JSONArray cont = new JSONArray(jsonArrays);

        int c = 0;
        for (JSONArray a : cont.jsonArrayIterable()) {
            JSONArray expected = jsonArrays.get(c);
            assertEquals(expected, a);
            c++;
        }
    }


    @Test
    public void test_jsonObjectIterable() {
        JSONObject ja1 = new JSONObject("{\"a\": \"a\"}");
        JSONObject ja2 = new JSONObject("{\"a\": \"b\"}");
        JSONObject ja3 = new JSONObject("{\"a\": \"c\"}");
        List<JSONObject> jsonObjects = Arrays.asList(ja1, ja2, ja3);

        JSONArray cont = new JSONArray(jsonObjects);

        int c = 0;
        for (JSONObject a : cont.jsonObjectIterable()) {
            JSONObject expected = jsonObjects.get(c);
            assertEquals(expected, a);
            c++;
        }

        assertListEquals(jsonObjects, cont.toJSONObjectList());
        assertListEquals(Arrays.asList("a", "b", "c"), cont.toStringList("a"));
    }


    @Test
    public void test_stringIterable() {
        List<String> strings = Arrays.asList("a", "b", "sds");

        JSONArray cont = new JSONArray(strings);

        int c = 0;
        for (String a : cont.stringIterable()) {
            String expected = strings.get(c);
            assertEquals(expected, a);
            c++;
        }

        assertListEquals(strings, cont.toStringList());
    }


    @Test
    public void test_longIterable() {
        List<Long> longs = Arrays.asList(1L, 23L, 578787L);

        JSONArray cont = new JSONArray(longs);

        int c = 0;
        for (Long a : cont.longIterable()) {
            Long expected = longs.get(c);
            assertEquals(expected, a);
            c++;
        }

        assertListEquals(longs, cont.toLongList());
    }


    @Test
    public void testLength() {
        JSONArray ja = new JSONArray("[1,3,5,6]");
        ja.put(234);
        ja.put(new JSONObject());

        assertEquals(6, ja.length());
    }


    @Test
    public void testIsNull() {
        JSONArray ja = new JSONArray("[1,3,null,6]");
        assertTrue(ja.isNull(2));
        assertTrue(ja.isNull(-1));
        assertTrue(ja.isNull(100));
    }

    @Test
    public void containersAreMutable() {
        JSONObject topLevel = new JSONObject();
        topLevel.put("array", new JSONArray());
        topLevel.getJSONArray("array").put(2);
        assertEquals(2, topLevel.getJSONArray("array").get(0));
    }
}