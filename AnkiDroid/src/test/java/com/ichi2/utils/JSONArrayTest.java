/*
 Copyright (c) 2021 Tarek Mohamed Abdalla <tarekkma@gmail.com>

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
package com.ichi2.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
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

import static com.ichi2.utils.ListUtil.assertListEquals;
import static org.junit.Assert.*;

public class JSONArrayTest {


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

        jsonArray.put(1, content);

        assertEquals(JSONObject.NULL, jsonArray.get(0));
        assertEquals(content, jsonArray.get(1));
        assertEquals(content, jsonArray.getBoolean(1));
    }


    @Test
    public void testPutIndexGetDouble() {
        JSONArray jsonArray = new JSONArray();

        double content = 2323.2;

        jsonArray.put(1, content);

        assertEquals(JSONObject.NULL, jsonArray.get(0));
        assertEquals(content, jsonArray.get(1));
        assertEquals(content, jsonArray.getDouble(1), 0);
    }


    @Test
    public void testPutIndexGetInt() {
        JSONArray jsonArray = new JSONArray();

        int content = 12;

        jsonArray.put(1, content);

        assertEquals(JSONObject.NULL, jsonArray.get(0));
        assertEquals(content, jsonArray.get(1));
        assertEquals(content, jsonArray.getInt(1));
    }


    @Test
    public void testPutIndexGetLong() {
        JSONArray jsonArray = new JSONArray();

        long content = 12L;

        jsonArray.put(1, content);

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
}