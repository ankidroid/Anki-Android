/*
 Copyright (c) 2020 David Allison <davidallisongithub@gmail.com>
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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import androidx.annotation.NonNull;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsNull.notNullValue;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;

public class JSONObjectTest {

    private final String emptyJson = "{}";
    private final String correctJsonBasic = "{\"key1\":\"value1\"}";
    private final String correctJsonNested = "{\"key1\":{\"key1a\":\"value1a\",\"key1b\":\"value1b\"},\"key2\":\"value2\"}";
    private final String correctJsonWithArray = "{\"key1\":\"value1\",\"key2\":[{\"key2a\":\"value2a\"},{\"key2b\":\"value2b\"}],\"key3\":\"value3\"}";
    private final String correctJsonNestedWithArray = "{\"key1\":{\"key1a\":\"value1a\",\"key1b\":\"value1b\"},\"key2\":[{\"key2a\":\"value2a\"},{\"key2b\":\"value2b\"}],\"key3\":\"value3\"}";
    private final String noOpeningBracket = "\"key1\":\"value1\"}";
    private final String extraOpeningBracket = "{{\"key1\": \"value1\"}";
    private final String noClosingBracket = "{\"key1\":value1";
    private final String wrongKeyValueSeparator = "{\"key1\":\"value1\",\"key2\" \"value2\"}";

    private JSONObject correctJsonObjectBasic;
    private JSONObject correctJsonObjectNested;
    private JSONObject correctJsonObjectWithArray;
    private JSONObject correctJsonObjectNestedWithArray;
    Map<String, Boolean> booleanMap;

    @Before
    public void setUp() {
        correctJsonObjectBasic = new JSONObject(correctJsonBasic);
        correctJsonObjectNested = new JSONObject(correctJsonNested);
        correctJsonObjectWithArray = new JSONObject(correctJsonWithArray);
        correctJsonObjectNestedWithArray = new JSONObject(correctJsonNestedWithArray);

        booleanMap = new HashMap<>();
        for (int i = 0 ; i < 10 ; ++i) {
            booleanMap.put("key" + i, i%2 == 0);
        }
    }

    @Test
    public void objectNullIsNotNull() {
        //#6289
        assertThat(JSONObject.NULL, notNullValue());
    }


    @Test
    public void formatTest() {
        // Correct formats
        new JSONObject(correctJsonBasic);
        new JSONObject(correctJsonNested);
        new JSONObject(correctJsonWithArray);
        new JSONObject(emptyJson);

        // Incorrect formats
        Assert.assertThrows(JSONException.class, () -> new JSONObject(noOpeningBracket));
        Assert.assertThrows(JSONException.class, () -> new JSONObject(extraOpeningBracket));
        Assert.assertThrows(JSONException.class, () -> new JSONObject(noClosingBracket));
        Assert.assertThrows(JSONException.class, () -> new JSONObject(wrongKeyValueSeparator));
    }

    @Test
    public void copyJsonTest() {
        assertEquals(correctJsonObjectBasic.toString(), new JSONObject(correctJsonObjectBasic).toString());
        assertEquals(correctJsonObjectNested.toString(), new JSONObject(correctJsonObjectNested).toString());
        assertEquals(correctJsonObjectWithArray.toString(), new JSONObject(correctJsonObjectWithArray).toString());
    }

    @Test
    public void objectToObjectTest() {
        assertEquals(correctJsonObjectBasic.toString(), JSONObject.objectToObject(correctJsonObjectBasic).toString());
        assertEquals(correctJsonObjectNested.toString(), JSONObject.objectToObject(correctJsonObjectNested).toString());
        Assert.assertNotEquals(correctJsonObjectNested.toString(), JSONObject.objectToObject(correctJsonObjectWithArray).toString());
    }

    @Test
    public void getTest() {
        JSONObject correctJsonObjectBasicCopy = new JSONObject(correctJsonBasic);
        correctJsonObjectBasicCopy.put("int-key", 2);
        correctJsonObjectBasicCopy.put("int_key", 6);
        correctJsonObjectBasicCopy.put("long_key", 2L);
        correctJsonObjectBasicCopy.put("double_key", 2d);
        correctJsonObjectBasicCopy.putOpt("boolean_key", (boolean) true);
        correctJsonObjectBasicCopy.putOpt("object_key", correctJsonBasic);

        assertEquals(6, correctJsonObjectBasicCopy.getInt("int_key"));
        assertEquals(2L, correctJsonObjectBasicCopy.getLong("long_key"));
        assertEquals(2d, correctJsonObjectBasicCopy.getDouble("double_key"), 1e-10);
        Assert.assertTrue(correctJsonObjectBasicCopy.getBoolean("boolean_key"));
        assertEquals(correctJsonBasic, correctJsonObjectBasicCopy.get("object_key"));

        // Check that putOpt doesn't add pair when one is null
        correctJsonObjectBasicCopy.putOpt("boolean_key_2", null);
        Assert.assertFalse(correctJsonObjectBasicCopy.has("boolean_key_2"));
        Assert.assertThrows(JSONException.class, () -> correctJsonObjectBasicCopy.get("boolean_key_2"));
    }

    /**
     * Tests that the a new copy is returned instead of a reference to the original.
     */
    @Test
    public void deepCloneReferenceTest() {
        JSONObject clone = correctJsonObjectBasic.deepClone();
        // Both objects should point to different memory address
        Assert.assertNotEquals(clone, correctJsonObjectBasic);
    }

    @Test
    public void fromMapTest() {
        JSONObject fromMapJsonObject = JSONObject.fromMap(booleanMap);
        for (int i = 0 ; i < 10 ; ++i) {
            assertEquals(fromMapJsonObject.getBoolean("key" + i), i%2 == 0);
        }
    }


    /*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*
     *  Jackson tree model implementation tests  *
     *-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*/

    @Test
    public void testEmptyJson() {
        JSONObject jsonObject = new JSONObject();
        assertEquals("{}", jsonObject.toString());
    }


    @Test
    public void testCreateJSONObjectFromObjectNode() {
        ObjectNode objectNode = new ObjectMapper().createObjectNode();
        objectNode.put("a", 1);
        objectNode.put("z", 4.02);

        JSONObject jsonObject = new JSONObject(objectNode);
        jsonObject.put("1", 3);

        assertEquals(objectNode, jsonObject.getRootJsonNode());
        assertEquals("{\"a\":1,\"z\":4.02,\"1\":3}", jsonObject.toString());
    }


    @Test
    public void testCreateJSONObject_from_JSONObject() {
        JSONObject jsonObject1 = new JSONObject();

        jsonObject1.put("a", 2);

        JSONObject jsonObject2 = new JSONObject(jsonObject1);

        // not same reference
        assertNotSame(jsonObject1, jsonObject2);
        // same content
        assertEquals(jsonObject1.getRootJsonNode(), jsonObject2.getRootJsonNode());

        jsonObject1.put("1", 55);

        // content diverged
        assertNotEquals(jsonObject1.getRootJsonNode(), jsonObject2.getRootJsonNode());
    }


    @Test
    public void testObjectBeingIterable() {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("a", "b")
                .put("c", "d")
                .put("e", "f")
                .put("g", "h");

        String[] s = new String[jsonObject.length()];
        int c = 0;
        for (String key : jsonObject) {
            s[c++] = key;
        }

        assertArrayEquals(
                new String[] {"a", "c", "e", "g"},
                s);
    }


    @Test
    public void testKeys() {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("a", "b")
                .put("c", "d")
                .put("e", "f")
                .put("g", "h");

        String[] s = new String[jsonObject.length()];
        int c = 0;
        Iterator<String> keys = jsonObject.keys();
        while (keys.hasNext()) {
            s[c++] = keys.next();
        }

        assertArrayEquals(
                new String[] {"a", "c", "e", "g"},
                s);
    }


    @Test
    public void testNames() {
        JSONObject jsonObject = new JSONObject();

        assertNull(jsonObject.names());

        jsonObject.put("a", "b")
                .put("c", "d")
                .put("e", "f")
                .put("g", "h");

        String[] s = new String[jsonObject.length()];
        int c = 0;
        Iterator<String> keys = jsonObject.names().stringIterator();
        while (keys.hasNext()) {
            s[c++] = keys.next();
        }

        assertArrayEquals(
                new String[] {"a", "c", "e", "g"},
                s);
    }


    @SuppressWarnings( {"ConstantConditions", "SimplifiableAssertion"})
    @Test
    public void testPutGetBoolean() {
        JSONObject jsonObject = new JSONObject();
        String key = "key";
        boolean content = false;

        jsonObject.put(key, content);

        assertEquals(content, jsonObject.get(key));
        assertEquals(content, jsonObject.getBoolean(key));

        assertEquals(content, jsonObject.optBoolean(key));
        assertEquals(content, jsonObject.optBoolean(key, false));

        assertEquals(false, jsonObject.optBoolean("notkey"));
        assertEquals(false, jsonObject.optBoolean("notkey", false));
        assertEquals(true, jsonObject.optBoolean("notkey", true));
    }


    @Test
    public void testPutGetDouble() {
        JSONObject jsonObject = new JSONObject();
        String key = "key";
        double content = 434.23;

        jsonObject.put(key, content);

        assertEquals(content, jsonObject.get(key));
        assertEquals(content, jsonObject.getDouble(key), 0);

        assertEquals(content, jsonObject.optDouble(key), 0);
        assertEquals(content, jsonObject.optDouble(key, 232D), 0);

        assertEquals(0, jsonObject.optDouble("notkey"), 0);
        assertEquals(232D, jsonObject.optDouble("notkey", 232D), 0);
    }


    @Test
    public void testPutGetInt() {
        JSONObject jsonObject = new JSONObject();
        String key = "key";
        int content = 2323;

        jsonObject.put(key, content);

        assertEquals(content, jsonObject.get(key));
        assertEquals(content, jsonObject.getInt(key));

        assertEquals(content, jsonObject.optInt(key, 12));
        assertEquals(23, jsonObject.optInt("notket", 23));
    }


    @Test
    public void testPutGetLong() {
        JSONObject jsonObject = new JSONObject();
        String key = "key";
        long content = 2323L;

        jsonObject.put(key, content);

        assertEquals(content, jsonObject.get(key));
        assertEquals(content, jsonObject.getLong(key));

        assertEquals(content, jsonObject.optLong(key));
        assertEquals(0, jsonObject.optLong("notkey"));

        assertEquals(content, jsonObject.optLong(key, 12));
        assertEquals(23, jsonObject.optLong("notkey", 23));
    }

    @Test
    public void testPutGetJSONObject() {
        JSONObject jsonObject = new JSONObject();
        String key = "key";
        JSONObject content = new JSONObject("{\"a\": \"b\",\"c\": [\"a\"],\"d\": {\"name\": \"test\"}}");

        jsonObject.put(key, content);
        assertEquals(content, jsonObject.get(key));
        assertEquals(content, jsonObject.getJSONObject(key));

        assertEquals(content, jsonObject.optJSONObject(key));
        assertEquals(null, jsonObject.optJSONObject("notkey"));

        assertThrows(JSONException.class, () -> {
            jsonObject.getJSONArray("notkey");
        });
    }

    @Test
    public void testPutGetJSONArray() {
        JSONObject jsonObject = new JSONObject();
        String key = "key";
        JSONArray content = new JSONArray("[1,2,4,66,32,2.3]");

        jsonObject.put(key, content);
        assertEquals(content, jsonObject.get(key));
        assertEquals(content, jsonObject.getJSONArray(key));

        assertEquals(content, jsonObject.optJSONArray(key));
        assertEquals(null, jsonObject.optJSONArray("notkey"));

        assertThrows(JSONException.class, () -> {
            jsonObject.getJSONObject("notkey");
        });
    }

    @Test
    public void testPutGetCharSequence() {
        JSONObject jsonObject = new JSONObject();
        String key = "key";
        CharSequence content = "w234234";

        jsonObject.put(key, content);
        assertEquals(content, jsonObject.get(key));

        assertEquals(content, jsonObject.optString(key));
        assertEquals("", jsonObject.optString("notkey"));

        assertEquals(content, jsonObject.optString(key, "asdf"));
        assertEquals("asdfw", jsonObject.optString("notkey", "asdfw"));
    }


    @Test
    public void testPutGetObject() {
        JSONObject jsonObject = new JSONObject();
        String key = "key";
        Object content = JSONObject.NULL;

        jsonObject.put(key, content);
        assertEquals(content, jsonObject.get(key));
    }


    @Test
    public void testPutOpt() {
        JSONObject jsonObject = new JSONObject();
        jsonObject.putOpt(null, null);
        assertEquals("{}", jsonObject.toString());
        jsonObject.putOpt("null", null);
        assertEquals("{}", jsonObject.toString());
        jsonObject.putOpt(null, "null");
        assertEquals("{}", jsonObject.toString());
        jsonObject.putOpt("null", "null");
        assertEquals("{\"null\":\"null\"}", jsonObject.toString());
    }
}