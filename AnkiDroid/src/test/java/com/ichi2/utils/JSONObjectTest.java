/*
 Copyright (c) 2020 David Allison <davidallisongithub@gmail.com>

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

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.HashMap;
import java.util.Map;

import androidx.annotation.NonNull;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsNull.notNullValue;

@RunWith(AndroidJUnit4.class)
public class JSONObjectTest {

    private final String emptyJson = "{}";
    private final String correctJsonBasic = "{key1:value1}";
    private final String correctJsonNested = "{key1:{key1a:value1a,key1b:value1b},key2:value2}";
    private final String correctJsonWithArray = "{key1:value1,key2:[{key2a:value2a},{key2b:value2b}],key3:value3}";
    private final String correctJsonNestedWithArray = "{key1:{key1a:value1a,key1b:value1b},key2:[{key2a:value2a},{key2b:value2b}],key3:value3}";
    private final String noOpeningBracket = "key1:value1}";
    private final String extraOpeningBracket = "{{key1: value1}";
    private final String noClosingBracket = "{key1:value1";
    private final String wrongKeyValueSeparator = "{key1:value1,key2 value2}";
    private final String duplicateKey = "{key1:value1,key1:value2}";

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
        Assert.assertThrows(JSONException.class, () -> new JSONObject(duplicateKey));
    }

    @Test
    public void copyJsonTest() {
        Assert.assertEquals(correctJsonObjectBasic.toString(), new JSONObject(correctJsonObjectBasic).toString());
        Assert.assertEquals(correctJsonObjectNested.toString(), new JSONObject(correctJsonObjectNested).toString());
        Assert.assertEquals(correctJsonObjectWithArray.toString(), new JSONObject(correctJsonObjectWithArray).toString());
    }

    @Test
    public void objectToObjectTest() {
        Assert.assertEquals(correctJsonObjectBasic.toString(), JSONObject.objectToObject(correctJsonObjectBasic).toString());
        Assert.assertEquals(correctJsonObjectNested.toString(), JSONObject.objectToObject(correctJsonObjectNested).toString());
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

        Assert.assertEquals(6, correctJsonObjectBasicCopy.getInt("int_key"));
        Assert.assertEquals(2L, correctJsonObjectBasicCopy.getLong("long_key"));
        Assert.assertEquals(2d, correctJsonObjectBasicCopy.getDouble("double_key"), 1e-10);
        Assert.assertTrue(correctJsonObjectBasicCopy.getBoolean("boolean_key"));
        Assert.assertEquals(correctJsonBasic, correctJsonObjectBasicCopy.get("object_key"));

        // Check that putOpt doesn't add pair when one is null
        correctJsonObjectBasicCopy.putOpt("boolean_key_2", null);
        Assert.assertFalse(correctJsonObjectBasicCopy.has("boolean_key_2"));
        Assert.assertThrows(JSONException.class, () -> correctJsonObjectBasicCopy.get("boolean_key_2"));
    }

    /**
     * Wraps all the alphanumeric words in a string in quotes
     */
    private static String removeQuotes(String string) {
        return string.replaceAll("\"([a-zA-Z0-9]+)\"", "$1");
    }

    private static class JSONObjectSubType extends JSONObject {
        /**
         * Sample overridden function
         */
        @NonNull
        @Override
        public String toString() {
            return removeQuotes(super.toString());
        }
    }

    @Test
    public void deepCloneTest() {
        JSONObjectSubType jsonObjectSubType = new JSONObjectSubType();

        // Clone base JSONObject Type into JSONObjectSubType
        correctJsonObjectNestedWithArray.deepClonedInto(jsonObjectSubType);

        // Test by passing result of base JSONObject's toString() to removeQuotes()
        // This is already done in the JSONObjectSubType object
        Assert.assertEquals(removeQuotes(correctJsonObjectNestedWithArray.toString()), jsonObjectSubType.toString());
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
            Assert.assertEquals(fromMapJsonObject.getBoolean("key" + i), i%2 == 0);
        }
    }
}
