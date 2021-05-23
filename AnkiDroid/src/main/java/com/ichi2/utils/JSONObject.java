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
public class JSONObject extends JSONContainer<String, ObjectNode, JSONObject> implements Iterable<String> {


    /**
     * Creates instance from {@link ObjectNode}
     *
     * JSONObject is essentially is the same as the {@link ObjectNode} with a
     * different API, so any changes to this class would result in changes
     * in the passed node and vice versa.
     */
    @JsonCreator
    public JSONObject(@NonNull ObjectNode node) {
        super(node);
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
        super(source);
    }

    /**
     * Creates a deep copy from another {@link JSONObject}
     * @param copyFrom instance to copy
     */
    public JSONObject(@NonNull JSONObject copyFrom) {
        this(copyFrom.getRootJsonNode().deepCopy());
    }

    @Override
    protected JSONObject thisAsInheritedType() {
        return this;
    }

    /**
     * @return iterator over all filed names (keys) in the object
     */
    @NonNull
    public Iterator<String> iterator() {
        return keys();
    }


    /**
     * Put a key/value pair in the InheritedType. If the value is <code>null</code>, then the
     * key will be removed from the InheritedType if it is present.
     *
     * @throws JSONException if passed type is not supported
     * @see JSONUtils#objectToJsonNode(Object) for supported types
     */
    @NonNull
    @Override
    public JSONObject put(@NonNull String name, @Nullable Object value) {
        if (value == null) {
            mNode.remove(name);
        }
        return super.put(name, value);
    }

    @NonNull
    @Override
    public JSONObject put(@NonNull String name, @NonNull JsonNode node) {
        mNode.set(name, node);
        return this;
    }

    @NonNull
    @Override
    protected JsonNode getJsonNode(@NonNull String name) {
        JsonNode jsonNode = mNode.get(name);
        if (jsonNode == null) {
            throw new JSONException("Key:" + name + " does not exist");
        }
        return jsonNode;
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

    @Override
    protected @Nullable JsonNode optNode(@Nullable String name) {
        return mNode.get(name);
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

    public boolean has(String name) {
        return mNode.has(name);
    }


    public void remove(@NonNull String name) {
        mNode.remove(name);
    }
}
