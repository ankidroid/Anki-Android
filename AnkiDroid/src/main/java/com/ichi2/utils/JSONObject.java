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

/*
  Each method similar to the methods in JSONObjects. Name changed to add a ,
  and it throws JSONException instead of JSONException.
  Furthermore, it returns JSONObject and JSONArray

 */

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ichi2.anki.AnkiSerialization;

import java.util.Iterator;
import java.util.Map;

import androidx.annotation.CheckResult;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import static java.lang.System.identityHashCode;

/**
 * A JSON object that follows the api of {@link org.json.JSONObject}
 * but backed by {@link ObjectNode} from Jackson serialization library.
 *
 * Some differences from {@link org.json.JSONObject}:
 *   - {@link #getJSONObject(String)} returns this class
 *   - {@link #getJSONArray(String)} returns instance of {@link JSONArray} not {@link org.json.JSONArray}
 *   - Null is instance of {@link NullNode}/{@link JSONObject#NULL}
 *   - Exceptions are of type {@link JSONException} which is unchecked exception
 *
 * Internal implementation using {@link ObjectNode} should be invisible to the user as long
 * as they don't access the underlying node via {@link #getRootJsonNode()}.
 *
 *
 * Databinding API:
 *
 * JSONObject support jackson databinding API, this is achieved {@link JsonCreator} and
 * {@link JsonValue} annotations which tell jackson how to de/serialize it from json source.
 *
 * If a sub class wishes to enable databinding, it should create a constructor that delegates to
 * {@link #JSONObject(ObjectNode)} and annotate it with {@link JsonCreator}.
 */
public class JSONObject implements Iterable<String> {

    public static final JsonNode NULL = NullNode.getInstance();

    @NonNull
    private final ObjectNode mNode;

    /**
     * Creates instance from {@link ObjectNode}
     *
     * JSONObject is essentially is the same as the {@link ObjectNode} with a
     * different API, so any changes to this class would result in changes
     * in the passed node and vice versa.
     */
    @JsonCreator
    public JSONObject(@NonNull ObjectNode node) {
        mNode = node;
    }

    /**
     * @return the backing {@link ObjectNode}
     *
     * Changing the content of the returned node will result
     * in a change in this instance of JSONObject.
     *
     * This method should not be used directly to change the
     * content of the json object, but can be used for jackson
     * deserialization and for changing the wrapper class around
     * the {@link ObjectNode} like {@link com.ichi2.libanki.Deck#from(JSONObject)}
     */
    @NonNull
    @JsonValue
    public ObjectNode getRootJsonNode() {
        return mNode;
    }


    /**
     * Creates an empty json object using the default {@link ObjectMapper}
     */
    public JSONObject() {
        // ObjectNode/ArrayNode require JsonNodeFactory to be created. So instead of hardcoding the JsonNodeFactory
        // config about how to handle decimals, I use the globally created ObjectMapper and it will create
        // object/array with the correct configured factory.
        this(AnkiSerialization.getObjectMapper().createObjectNode());
    }


    /**
     * Deserializes a string to to a {@link JSONObject} using the default {@link ObjectMapper}
     * @param source the json string
     *
     * @throws JSONException if the string couldn't be parsed.
     * the encapsulated exception is either {@link JsonProcessingException} or {@link JsonMappingException}
     */
    public JSONObject(@NonNull String source) {
        try {
            mNode = (ObjectNode) AnkiSerialization.getObjectMapper().readTree(source);
        } catch (Exception e) {
            throw new JSONException(e);
        }
    }


    /**
     * Creates a deep copy from another {@link JSONObject}
     * @param copyFrom instance to copy
     */
    public JSONObject(@NonNull JSONObject copyFrom) {
        mNode = copyFrom.mNode.deepCopy();
    }


    /**
     * @return iterator over all filed names (keys) in the object
     */
    @NonNull
    public Iterator<String> iterator() {
        return keys();
    }

    @NonNull
    public static JSONObject objectToObject(JSONObject obj) {
        return obj.deepClone();
    }

    @NonNull
    public JSONObject put(@NonNull String name, boolean value) {
        return this.put(name, mNode.booleanNode(value));
    }

    @NonNull
    public JSONObject put(@NonNull String name, double value) {
        return this.put(name, mNode.numberNode(value));
    }

    @NonNull
    public JSONObject put(@NonNull String name, int value) {
        return this.put(name, mNode.numberNode(value));
    }

    @NonNull
    public JSONObject put(@NonNull String name, long value) {
        return this.put(name, mNode.numberNode(value));
    }


    /**
     * Put a key/value pair in the JSONObject. If the value is <code>null</code>, then the
     * key will be removed from the JSONObject if it is present.
     *
     * @throws JSONException if passed type is not supported
     * @see JSONUtils#objectToJsonNode(Object) for supported types
     */
    @NonNull
    public JSONObject put(@NonNull String name, @Nullable Object value) {
        if (value == null) {
            mNode.remove(name);
        } else {
            put(name, JSONUtils.objectToJsonNode(value));
        }
        return this;
    }


    /**
     * Put a key/value pair in the JSONObject, but only if the key and the value
     * are both non-null.
     */
    @NonNull
    public JSONObject putOpt(@Nullable String name, @Nullable Object value) {
        if (name == null || value == null) {
            return this;
        }
        return put(name, value);
    }

    @NonNull
    public JSONObject put(@NonNull String name, @NonNull CharSequence value) {
        return this.put(name, mNode.textNode(value.toString()));
    }


    @NonNull
    public JSONObject put(@NonNull String name, @NonNull JSONArray value) {
        return this.put(name, value.getRootJsonNode());
    }


    @NonNull
    public JSONObject put(@NonNull String name, @NonNull JSONObject value) {
        return this.put(name, value.getRootJsonNode());
    }


    @NonNull
    public JSONObject put(@NonNull String name, @NonNull JsonNode node) {
        mNode.set(name, node);
        return this;
    }

    @NonNull
    protected JsonNode getJsonNode(@NonNull String name) {
        JsonNode jsonNode = mNode.get(name);
        if (jsonNode == null) {
            throw new JSONException("Key:" + name + " does not exist");
        }
        return jsonNode;
    }

    /**
     * Get the value object associated with a name.
     */
    @CheckResult
    @NonNull
    public Object get(@NonNull String name) {
        JsonNode node = getJsonNode(name);
        return JSONUtils.jsonNodeToObject(node);
    }


    /**
     * @return the value at "name" converted to boolean.
     * @throws JSONException if value didn't exist, or it couldn't be
     *                       converted to the correct type
     */
    @CheckResult
    public boolean getBoolean(String name) {
        return JSONTypeConverters.convert(name, get(name), Boolean.class);
    }


    /**
     * @return the value at "name" converted to double.
     * @throws JSONException if value didn't exist, or it couldn't be
     *                       converted to the correct type
     */
    @CheckResult
    public double getDouble(String name) {
        return JSONTypeConverters.convert(name, get(name), Double.class);
    }


    /**
     * @return the value at "name" converted to int.
     * @throws JSONException if value didn't exist, or it couldn't be
     *                       converted to the correct type
     */
    @CheckResult
    public int getInt(String name) {
        return JSONTypeConverters.convert(name, get(name), Integer.class);
    }


    /**
     * @return the value at "name" converted to long.
     * @throws JSONException if value didn't exist, or it couldn't be
     *                       converted to the correct type
     */
    @CheckResult
    public long getLong(String name) {
        return JSONTypeConverters.convert(name, get(name), Long.class);
    }


    /**
     * @return the value at "name" converted to string.
     * @throws JSONException if value didn't exist, or it couldn't be
     *                       converted to the correct type
     */
    @CheckResult
    @NonNull
    public String getString(String name) {
        return JSONTypeConverters.convert(name, get(name), String.class);
    }


    /**
     * @return array at "name"
     * @throws JSONException if value didn't exist, or it couldn't be
     *                       converted to the correct type
     */
    @CheckResult
    @NonNull
    public JSONArray getJSONArray(String name) {
        return JSONTypeConverters.convert(name, get(name), JSONArray.class);
    }


    /**
     * @return object at "name"
     * @throws JSONException if value didn't exist, or it couldn't be
     *                       converted to the correct type
     */
    @CheckResult
    @NonNull
    public JSONObject getJSONObject(String name) {
        return JSONTypeConverters.convert(name, get(name), JSONObject.class);
    }

    /**
     * @return child count
     */
    @CheckResult
    public int length() {
        return mNode.size();
    }


    /**
     * @return return all keys of this object as {@link JSONArray} instance
     *         returns null if this object contains no mappings.
     *
     * @see #keys()
     */
    @CheckResult
    @Nullable
    public JSONArray names() {
        if (length() == 0) {
            return null;
        }
        return new JSONArray(keys());
    }

    /**
     * @return iterator over all filed names (keys) in the object
     */
    @CheckResult
    @NonNull
    public Iterator<String> keys() {
        return mNode.fieldNames();
    }

    @Nullable
    public Object opt(@Nullable String name) {
        if (name == null) {
            return null;
        }
        JsonNode node = mNode.get(name);
        if (node == null) {
            return null;
        }
        return JSONUtils.jsonNodeToObject(node);
    }

    @Nullable
    public JsonNode optNode(@Nullable String name) {
        return mNode.get(name);
    }


    /**
     * @return JSONArray at name if it exists otherwise null
     */
    @Nullable
    @CheckResult
    public JSONArray optJSONArray(@Nullable String name) {
        Object value = opt(name);
        if (value == null) {
            return null;
        }
        return JSONTypeConverters.convertOr(value, JSONArray.class, null);
    }


    /**
     * @return JSONObject at name if it exists otherwise null
     */
    @Nullable
    @CheckResult
    public JSONObject optJSONObject(@Nullable String name) {
        Object value = opt(name);
        if (value == null) {
            return null;
        }
        return JSONTypeConverters.convertOr(value, JSONObject.class, null);
    }


    @NonNull
    @CheckResult
    public JSONObject deepClone() {
        ObjectNode clone = mNode.deepCopy();
        return new JSONObject(clone);
    }


    @NonNull
    public static JSONObject fromMap(@NonNull Map<String, Boolean> map) {
        JSONObject ret = new JSONObject();
        for (Map.Entry<String, Boolean> i : map.entrySet()) {
            ret.put(i.getKey(), i.getValue());
        }
        return ret;
    }


    public boolean optBoolean(@Nullable String name, boolean defaultValue) {
        Object value = opt(name);
        if (value == null) {
            return defaultValue;
        }
        return JSONTypeConverters.convertOr(value, Boolean.class, defaultValue);
    }


    public int optInt(@Nullable String name, int defaultValue) {
        Object value = opt(name);
        if (value == null) {
            return defaultValue;
        }
        return JSONTypeConverters.convertOr(value, Integer.class, defaultValue);
    }


    public long optLong(@Nullable String name, long defaultValue) {
        Object value = opt(name);
        if (value == null) {
            return defaultValue;
        }
        return JSONTypeConverters.convertOr(value, Long.class, defaultValue);
    }

    public long optLong(@Nullable String name) {
        return optLong(name, 0L);
    }

    public double optDouble(@Nullable String name) {
        return optDouble(name, 0D);
    }


    public boolean has(String name) {
        return mNode.has(name);
    }


    public void remove(@NonNull String name) {
        mNode.remove(name);
    }


    public String optString(String name, String defaultValue) {
        Object value = opt(name);
        if (value == null) {
            return defaultValue;
        }
        return JSONTypeConverters.convertOr(value, String.class, defaultValue);
    }


    public String optString(String name) {
        return optString(name, "");
    }


    public boolean isNull(String name) {
        return mNode.get(name).isNull();
    }


    public boolean optBoolean(String name) {
        return optBoolean(name, false);
    }


    public double optDouble(String name, double defaultValue) {
        Object value = opt(name);
        if (value == null) {
            return defaultValue;
        }
        return JSONTypeConverters.convertOr(value, Double.class, defaultValue);
    }


    @Override
    public String toString() {
        return mNode.toString();
    }

    public String toPrettyString() {
        return mNode.toPrettyString();
    }

    public String toString(int indentation/*not used*/) {
        return toPrettyString();
    }


    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        JSONObject other = (JSONObject) o;
        // intentional reference comparison
        return mNode == other.getRootJsonNode();
    }


    @Override
    public int hashCode() {
        return identityHashCode(mNode);
    }
}
