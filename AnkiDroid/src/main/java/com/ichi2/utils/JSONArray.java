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
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.ichi2.anki.AnkiSerialization;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import static com.ichi2.utils.JSONTypeConverters.checkDouble;
import static java.lang.System.identityHashCode;

/**
 * An array of JSON elements that follows the api of {@link org.json.JSONArray}
 * but backed by {@link ArrayNode} from Jackson serialization library.
 *
 * Some differences from {@link org.json.JSONArray}:
 *
 * Internal implementation using {@link ArrayNode} should be invisible to the user as long
 * as they don't access the underlying node via {@link #getRootJsonNode()}.
 *
 *
 * Databinding API:
 *
 * JSONArray support jackson databinding API, this is achieved {@link JsonCreator} and
 * {@link JsonValue} annotations which tell jackson how to de/serialize it from json source.
 *
 * If a sub class wishes to enable databinding, it should create a constructor that delegates to
 * {@link #JSONArray(ArrayNode)} and annotate it with {@link JsonCreator}.
 */
public class JSONArray extends JSONContainer<Integer, ArrayNode, JSONArray> {

    /**
     * Creates instance from {@link ArrayNode}
     *
     * JSONArray is essentially is the same as the {@link ArrayNode} with a
     * different API, so any changes to this class would result in changes
     * in the passed node and vice versa.
     */
    @JsonCreator
    public JSONArray(@NonNull ArrayNode node) {
        super(node);
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
        this(copyFrom.mNode.deepCopy());
    }

    /**
     * Deserializes a string to to a {@link JSONArray} using the default {@link ObjectMapper}
     * @param source the json string
     *
     * @throws JSONException if the string couldn't be parsed.
     * the encapsulated exception is either {@link JsonProcessingException} or {@link JsonMappingException}
     * @throws IllegalArgumentException if the source is null. (Differes from org.json which throws nullPointerException)
     */
    public JSONArray(@NonNull String source) {
        super(source, ArrayNode.class);
    }

    /**
     * Construct {@link JSONArray} filed with a given array
     * @param array object that is an array (ex. int[], double[])
     */
    public JSONArray(@NonNull Object array) {
        // array's type is object, as it's the only way to have a type encapsulating arrays of arbitrary native type
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

    protected JSONArray thisAsInheritedType() {
        return this;
    };

    /**
     * Appends {@code value} to the end of this array.
     */
    @NonNull
    public JSONArray put(long value) {
        return put(mNode.numberNode(value));
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
    public JSONArray put(double value) throws JSONException {
        return put(mNode.numberNode(checkDouble(value)));
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
    public JSONArray put(@Nullable JSONContainer value) {
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
     * Sets the value at {@code index} to {@code node}, null padding this array
     * to the required length if necessary. If a value already exists at {@code
     * index}, it will be replaced.
     *
     * Null padding is done primarily to be somewhat compatible with the previous implementation
     * which relied on {@link org.json.JSONArray}, there is no actual need for this feature
     * in AnkiDroid.
     * 
     * org.json padds with actual null value, which cause the array to be invalid, and a get on the padded value fails.
     */
    @NonNull
    public JSONArray put(Integer index, JsonNode node) {
        int index_ = index;
        while (length() <= index_) {
            put(JSONObject.NULL);
        }
        mNode.set(index_, node);
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
     * @return node at index
     * @throws JSONException if the index is out of bounds
     */
    protected JsonNode getJsonNode(Integer index) {
        int index_ = index;
        throwIfInvalidIndex(index_);
        return mNode.get(index_);
    }
    /**
     * @return node at index. null if out of bounds
     */
    protected JsonNode optNode(Integer index) {
        int index_ = index;
        if(isInvalidIndex(index_)) {
            return null;
        }
        return mNode.get(index_);
    }


    @Override
    protected JsonNode removeNode(Integer index) {
        return mNode.remove(index);
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

    /**
     * Returns a new object whose values are the values in this array, and whose
     * names are the values in {@code names}. Names and values are paired up by
     * index from 0 through to the shorter array's length. Names that are not
     * strings will be coerced to strings. This method returns null if either
     * array is empty.
     */
    public @Nullable JSONObject toJSONObject(@NonNull JSONArray names) {
        // copied from upstream
        JSONObject result = new JSONObject();
        int length = Math.min(names.length(), length());
        if (length == 0) {
            return null;
        }
        for (int i = 0; i < length; i++) {
            String name = JSON.toString(names.opt(i));
            result.putSafe(name, opt(i));
        }
        return result;
    }
}
