/*
 *  Copyright (c) 2020 Arthur Milchior <arthur@milchior.fr>
 *  Copyright (c) 2021 Tarek Mohamed Abdalla <tarekkma@gmail.com>
 *
 *  This file is free software: you may copy, redistribute and/or modify it  
 *  under the terms of the GNU General Public License as published by the  
 *  Free Software Foundation, either version 3 of the License, or (at your  
 *  option) any later version.  
 *  
 *  This file is distributed in the hope that it will be useful, but  
 *  WITHOUT ANY WARRANTY; without even the implied warranty of  
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU  
 *  General Public License for more details.  
 *  
 *  You should have received a copy of the GNU General Public License  
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *  
 *  This file incorporates work covered by the following copyright and  
 *  permission notice:  
 *  
 *    Copyright (c) 2002 JSON.org
 *    
 *    Permission is hereby granted, free of charge, to any person obtaining a copy
 *    of this software and associated documentation files (the "Software"), to deal
 *    in the Software without restriction, including without limitation the rights
 *    to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 *    copies of the Software, and to permit persons to whom the Software is
 *    furnished to do so, subject to the following conditions:
 *   
 *    The above copyright notice and this permission notice shall be included in all
 *    copies or substantial portions of the Software.
 *   
 *    The Software shall be used for Good, not Evil.
 *
 *    THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *    IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *    FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *    AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *    LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *    OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 *    SOFTWARE. 
 */

package com.ichi2.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ichi2.anki.AnkiSerialization;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import static java.lang.System.identityHashCode;

/**
 * An array of JSON elements that follows the api of {@link org.json.JSONArray}
 * but backed by {@link ArrayNode} from Jackson serialization library.
 *
 * Some differences from {@link org.json.JSONArray}:
 *   - {@link #getJSONArray(int)} returns this class
 *   - {@link #getJSONObject(int)} returns instance of {@link JSONObject} not {@link org.json.JSONObject}
 *   - Null is instance of {@link NullNode}/{@link JSONObject#NULL}
 *   - Exceptions are of type {@link JSONException} which is unchecked exception
 *
 * Internal implementation using {@link ArrayNode} should be invisible to the user as long
 * as they don't access the underlying node via {@link #getRootJsonNode()}.
 */
public class JSONArray {

    @NonNull
    private final ArrayNode mNode;

    /**
     * Creates instance from {@link ArrayNode}
     *
     * JSONArray is essentially is the same as the {@link ArrayNode} with a
     * different API, so any changes to this class would result in changes
     * in the passed node and vice versa.
     */
    public JSONArray(@NonNull ArrayNode node) {
        mNode = node;
    }

    /**
     * @return the backing {@link ArrayNode}
     *
     * Changing the content of the returned node will result
     * in a change in this instance of JSONArray.
     *
     *
     * This method should not be used directly to change the
     * content of the json array, but can be used for jackson
     * deserialization and for changing the wrapper class around
     * the {@link ArrayNode}
     */
    public ArrayNode getRootJsonNode() {
        return mNode;
    }

    /**
     * Creates an empty json array using the default {@link ObjectMapper}
     */
    public JSONArray() {
        // ObjectNode/ArrayNode require JsonNodeFactory to be created. So instead of hardcoding the JsonNodeFactory
        // config about how to handle decimals, I use the globally created ObjectMapper and it will create
        // object/array with the correct configured factory.
        this(AnkiSerialization.getObjectMapper().createArrayNode());
    }

    /**
     * Creates a deep copy from another {@link JSONArray}
     * @param copyFrom instance to copy
     */
    public JSONArray(@NonNull JSONArray copyFrom) {
        mNode = copyFrom.mNode.deepCopy();
    }

    /**
     * Deserializes a string to to a {@link JSONArray} using the default {@link ObjectMapper}
     * @param source the json string
     *
     * @throws JSONException if the string couldn't be parsed.
     * the encapsulated exception is either {@link JsonProcessingException} or {@link JsonMappingException}
     */
    public JSONArray(@NonNull String source) {
        try {
            mNode = (ArrayNode) AnkiSerialization.getObjectMapper().readTree(source);
        } catch (Exception e) {
            throw new JSONException(e);
        }
    }


    /**
     * Construct {@link JSONArray} filed with a given array
     * @param array object that is an array (ex. int[], double[])
     */
    public JSONArray(@NonNull Object array) {
        this();
        if (array.getClass().isArray()) {
            int length = Array.getLength(array);
            for (int i = 0; i < length; i += 1) {
                this.put(Array.get(array, i));
            }
        } else {
            throw new JSONException(
                    "JSONArray initial value should be a string or collection or array.");
        }
    }


    /**
     * Construct {@link JSONArray} filed with items in the given iterator
     */
    public JSONArray(@Nullable Iterator<?> iterator) {
        this();
        if (iterator == null) {
            return;
        }
        while (iterator.hasNext()) {
            this.put(iterator.next());
        }
    }


    /**
     * Construct {@link JSONArray} filed with a given iterable
     */
    public JSONArray(@Nullable Iterable<?> iterable) {
        this(iterable == null ? null : iterable.iterator());
    }

    /**
     * Appends {@code value} to the end of this array.
     */
    @NonNull
    public JSONArray put(int value) {
        return put(mNode.numberNode(value));
    }

    /**
     * Appends {@code value} to the end of this array.
     */
    @NonNull
    public JSONArray put(double value) {
        return put(mNode.numberNode(value));
    }

    /**
     * Appends {@code value} to the end of this array.
     */
    @NonNull
    public JSONArray put(String value) {
        return put(mNode.textNode(value));
    }

    /**
     * Appends {@code value} to the end of this array.
     */
    @NonNull
    public JSONArray put(@Nullable JSONArray value) {
        return put(value == null ?
                JSONObject.NULL :
                value.getRootJsonNode());
    }


    /**
     * Appends {@code value} to the end of this array.
     */
    @NonNull
    public JSONArray put(@Nullable JSONObject value) {
        return put(value == null ?
                JSONObject.NULL :
                value.getRootJsonNode());
    }

    /**
     * Appends {@code value} to the end of this array.
     *
     * @see JSONUtils#objectToJsonNode(Object) for supported types
     */
    @NonNull
    public JSONArray put(@Nullable Object value) {
        return put(JSONUtils.objectToJsonNode(value));
    }

    /**
     * Appends {@code node} to the end of this array.
     */
    @NonNull
    public JSONArray put(JsonNode node) {
        mNode.add(node);
        return this;
    }

    /**
     * Sets the value at {@code index} to {@code value}
     *
     * @see #put(int, JsonNode) for details about null padding
     */
    @NonNull
    public JSONArray put(int index, boolean value) {
        return put(index, mNode.booleanNode(value));
    }

    /**
     * Sets the value at {@code index} to {@code value}
     *
     * @see #put(int, JsonNode) for details about null padding
     */
    @NonNull
    public JSONArray put(int index, double value) {
        return put(index, mNode.numberNode(value));
    }

    /**
     * Sets the value at {@code index} to {@code value}
     *
     * @see #put(int, JsonNode) for details about null padding
     */
    @NonNull
    public JSONArray put(int index, int value) {
        return put(index, mNode.numberNode(value));
    }

    /**
     * Sets the value at {@code index} to {@code value}
     */
    @NonNull
    public JSONArray put(int index, long value) {
        return put(index, mNode.numberNode(value));
    }

    /**
     * Sets the value at {@code index} to {@code value}
     *
     * @see JSONUtils#objectToJsonNode(Object) for supported types
     * @see #put(int, JsonNode) for details about null padding
     */
    @NonNull
    public JSONArray put(int index, Object value) {
        JsonNode node = JSONUtils.objectToJsonNode(value);
        return put(index, node);
    }

    /**
     * Sets the value at {@code index} to {@code node}, null padding this array
     * to the required length if necessary. If a value already exists at {@code
     * index}, it will be replaced.
     *
     * Null padding is done primarily to be compatible with the previous implementation
     * which relied on {@link org.json.JSONArray}, there is no actual need for this feature
     * in AnkiDroid.
     */
    @NonNull
    public JSONArray put(int index, JsonNode node) {
        while (length() <= index) {
            put(JSONObject.NULL);
        }
        mNode.set(index, node);
        return this;
    }


    /**
     * This is used primarily to be compatible with the previous implementation
     * which did wrap every exception inside {@link JSONException}
     *
     * @implNote note that jackson don't throw on out of bound exception
     * when retrieving a value rather it returns null, so we need to
     * manually check and throw
     *
     * @throws JSONException if index is out out bounds
     */
    protected void throwIfInvalidIndex(int index) {
        if (isInvalidIndex(index)) {
            throw new JSONException(new IndexOutOfBoundsException("Index: " + index + ", Size: " + length()));
        }
    }


    /**
     * @return true if index is out of bounds, false otherwise
     */
    protected boolean isInvalidIndex(int index) {
        return (index < 0) || (index >= length());
    }


    /**
     * @return value at index
     * @throws JSONException if the index is out of bounds
     *                       or if the value at index cannot be converted to boolean
     */
    public boolean getBoolean(int index) {
        return JSONTypeConverters.convert(index, get(index), Boolean.class);
    }

    /**
     * @return value at index
     * @throws JSONException if the index is out of bounds
     *                       or if the value at index cannot be converted to double
     */
    public double getDouble(int index) {
        return JSONTypeConverters.convert(index, get(index), Double.class);
    }

    /**
     * @return value at index
     * @throws JSONException if the index is out of bounds
     *                       or if the value at index cannot be converted to int
     */
    public int getInt(int index) {
        return JSONTypeConverters.convert(index, get(index), Integer.class);
    }

    /**
     * @return value at index
     * @throws JSONException if the index is out of bounds
     *                       or if the value at index cannot be converted to long
     */
    public long getLong(int index) {
        return JSONTypeConverters.convert(index, get(index), Long.class);
    }

    /**
     * @return value at index
     * @throws JSONException if the index is out of bounds
     *                       or if the value at index cannot be converted to String
     */
    public String getString(int index) {
        return JSONTypeConverters.convert(index, get(index), String.class);
    }


    /**
     * @return JSONArray at index
     * @throws JSONException if the index is out of bounds
     *                       or if the value at index isn't an array
     */
    public JSONArray getJSONArray(int index) {
        return JSONTypeConverters.convert(index, get(index), JSONArray.class);
    }

    /**
     * @return JSONObject at index
     * @throws JSONException if the index is out of bounds
     *                       or if the value at index isn't an object
     */
    public JSONObject getJSONObject(int index) {
        return JSONTypeConverters.convert(index, get(index), JSONObject.class);
    }


    /**
     * Returns the value at {@code index}.
     *
     *
     * @implNote The value can be directly used, That is Long, Boolean, JSONObject, etc...
     * and not the actual value of type {@link JsonNode} stored in the underlying {@link #mNode}
     *
     * @return value at index
     * @throws JSONException if the index is out of bounds
     *                       or the value type isn't supported
     *
     * @see JSONUtils#jsonNodeToObject(JsonNode) for list of supported types
     */
    public Object get(int index) {
        JsonNode node = getJsonNode(index);
        return JSONUtils.jsonNodeToObject(node);
    }

    /**
     * @return node at index
     * @throws JSONException if the index is out of bounds
     */
    protected JsonNode getJsonNode(int index) {
        throwIfInvalidIndex(index);
        return mNode.get(index);
    }

    /**
     * Returns the value at {@code index} if it exists, coercing it if
     * necessary. Returns the empty string if no such value exists.
     */
    public String optString(int index) {
        if (isInvalidIndex(index)) {
            return "";
        }
        return mNode.get(index).asText("");
    }

    @NonNull
    public JSONArray deepClone() {
        ArrayNode node = mNode.deepCopy();
        return new JSONArray(node);
    }

    @NonNull
    public Iterable<JSONArray> jsonArrayIterable() {
        return this::jsonArrayIterator;
    }
    @NonNull
    public Iterator<JSONArray> jsonArrayIterator() {
        return new Iterator<JSONArray>() {
            private int mIndex = 0;
            @Override
            public boolean hasNext() {
                return mIndex < length();
            }


            @Override
            public JSONArray next() {
                JSONArray array = getJSONArray(mIndex);
                mIndex++;
                return array;
            }
        };
    }

    public int length() {
        return mNode.size();
    }

    public Iterable<JSONObject> jsonObjectIterable() {
        return this::jsonObjectIterator;
    }
    public Iterator<JSONObject> jsonObjectIterator() {
        return new Iterator<JSONObject>() {
            private int mIndex = 0;
            @Override
            public boolean hasNext() {
                return mIndex < length();
            }


            @Override
            public JSONObject next() {
                JSONObject object = getJSONObject(mIndex);
                mIndex++;
                return object;
            }
        };
    }

    public Iterable<String> stringIterable() {
        return this::stringIterator;
    }
    public Iterator<String> stringIterator() {
        return new Iterator<String>() {
            private int mIndex = 0;
            @Override
            public boolean hasNext() {
                return mIndex < length();
            }


            @Override
            public String next() {
                String string = getString(mIndex);
                mIndex++;
                return string;
            }
        };
    }

    public Iterable<Long> longIterable() {
        return this::longIterator;
    }
    public Iterator<Long> longIterator() {
        return new Iterator<Long>() {
            private int mIndex = 0;
            @Override
            public boolean hasNext() {
                return mIndex < length();
            }


            @Override
            public Long next() {
                Long long_ = getLong(mIndex);
                mIndex++;
                return long_;
            }
        };
    }

    public List<JSONObject> toJSONObjectList() {
        List<JSONObject> l = new ArrayList<>(length());
        for (JSONObject object : jsonObjectIterable()) {
            l.add(object);
        }
        return l;
    }

    public List<Long> toLongList() {
        List<Long> l = new ArrayList<>(length());
        for (Long object : longIterable()) {
            l.add(object);
        }
        return l;
    }

    public List<String> toStringList() {
        List<String> l = new ArrayList<>(length());
        for (String object : stringIterable()) {
            l.add(object);
        }
        return l;
    }


    /**
     * @return Given an array of objects, return the array of the value with `key`, assuming that they are String.
     * E.g. templates, fields are a JSONArray whose objects have name
     */
    public List<String> toStringList(String key) {
        List<String> l = new ArrayList<>(length());
        for (JSONObject object : jsonObjectIterable()) {
            l.add(object.getString(key));
        }
        return l;
    }


    public boolean isNull(int i) {
        JsonNode node = mNode.get(i);
        return node == null || node.isNull();
    }

    @Override
    public String toString() {
        return mNode.toString();
    }

    public String toPrettyString() {
        return mNode.toPrettyString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        JSONArray other = (JSONArray) o;
        // intentional reference comparison
        return mNode == other.getRootJsonNode();
    }


    @Override
    public int hashCode() {
        return identityHashCode(mNode);
    }
}
