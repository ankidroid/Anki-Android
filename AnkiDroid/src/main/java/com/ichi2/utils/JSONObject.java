/*  
 *  Copyright (c) 2020 Arthur Milchior <arthur@milchior.fr>
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

import java.util.Iterator;
import java.util.Map;

import androidx.annotation.CheckResult;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import static com.ichi2.utils.JSON.checkDouble;

public class JSONObject extends org.json.JSONObject implements Iterable<String> {

    public static final Object NULL = org.json.JSONObject.NULL;

    public JSONObject() {
        super();
    }

    public JSONObject(Map copyFrom) {
        super(copyFrom);
    }

    /**
     * Creates a new {@code JSONObject} by copying mappings for the listed names
     * from the given object. Names that aren't present in {@code copyFrom} will
     * be skipped.
     *
     * Code copied from upstream.
     */
    public JSONObject(@NonNull org.json.JSONObject copyFrom, @NonNull String /*@NonNull*/ [] names) {
        this();
        try {
            for (String name : names) {
                Object value = copyFrom.opt(name);
                if (value != null) {
                    super.put(name, value);
                }
            }
        } catch (org.json.JSONException e) {
            throw new JSONException(e);
        }
    }

    // original code from https://github.com/stleary/JSON-java/blob/master/JSONObject.java
    // super() must be first instruction, thus it can't be in a try, and the exception can't be catched
    public JSONObject(JSONTokener x) {
        this();
        try {
            char c;
            String key;

            if (x.nextClean() != '{') {
                throw x.syntaxError("A JSONObject text must begin with '{'");
            }
            for (; ; ) {
                c = x.nextClean();
                switch (c) {
                    case 0:
                        throw x.syntaxError("A JSONObject text must end with '}'");
                    case '}':
                        return;
                    default:
                        x.back();
                        key = x.nextValue().toString();
                }

                // The key is followed by ':'.

                c = x.nextClean();
                if (c != ':') {
                    throw x.syntaxError("Expected a ':' after a key");
                }

                // Use syntaxError(..) to include error location

                if (key != null) {
                    // Check if key exists
                    if (this.opt(key) != null) {
                        // key already exists
                        throw x.syntaxError("Duplicate key \"" + key + "\"");
                    }
                    // Only add value if non-null
                    Object value = x.nextValue();
                    if (value != null) {
                        this.put(key, value);
                    }
                }

                // Pairs are separated by ','.

                switch (x.nextClean()) {
                    case ';':
                    case ',':
                        if (x.nextClean() == '}') {
                            return;
                        }
                        x.back();
                        break;
                    case '}':
                        return;
                    default:
                        throw x.syntaxError("Expected a ',' or '}'");
                }
            }
        } catch (org.json.JSONException e) {
            throw new JSONException(e);
        }
    }

    public JSONObject(String source) {
        this(new JSONTokener(source));
    }

    public JSONObject(JSONObject copyFrom) {
        this();
        for (String key: copyFrom) {
            put(key, copyFrom.get(key));
        }
    }

    /**
        Iters on the keys. (Similar to iteration in Python's
        dictionnary.
    */
    @NonNull
    public Iterator<String> iterator() {
        return keys();
    }

    /**
     * Change type from JSONObject to JSONObject.
     *
     * Assuming the whole code use only JSONObject, JSONArray and JSONTokener,
     * there should be no instance of JSONObject or JSONArray which is not a JSONObject or JSONArray.
     *
     * In theory, it would be easy to create a JSONObject similar to a JSONObject. It would suffices to iterate over key and add them here. But this would create two distinct objects, and update here would not be reflected in the initial object. So we must avoid this method.
     * Since the actual map in JSONObject is private, the child class can't edit it directly and can't access it. Which means that there is no easy way to create a JSONObject with the same underlying map. Unless the JSONObject was saved in a variable here. Which would entirely defeat the purpose of inheritence.
     *
     * @param obj A json object
     * @return Exactly the same object, with a different type.
     */
    public static JSONObject objectToObject(org.json.JSONObject obj){
        return (JSONObject) obj;
    }

    public JSONObject put(String name, boolean value) {
        try {
            super.put(name, value);
            return this;
        } catch (org.json.JSONException e) {
            throw new JSONException(e);
        }
    }

    public JSONObject put(String name, double value) {
        try {
            super.put(name, value);
            return this;
        } catch (org.json.JSONException e) {
            throw new JSONException(e);
        }
    }

    public JSONObject put(String name, int value) {
        try {
            super.put(name, value);
            return this;
        } catch (org.json.JSONException e) {
            throw new JSONException(e);
        }
    }

    public JSONObject put(String name, long value) {
        try {
            super.put(name, value);
            return this;
        } catch (org.json.JSONException e) {
            throw new JSONException(e);
        }
    }

    public JSONObject put(String name, Object value) {
        try {
            super.put(name, value);
            return this;
        } catch (org.json.JSONException e) {
            throw new JSONException(e);
        }
    }

    public JSONObject putOpt(String name, Object value) {
        try {
            super.putOpt(name, value);
            return this;
        } catch (org.json.JSONException e) {
            throw new JSONException(e);
        }
    }

    // Copied from upstream
    private static String checkName(String name) {
        if (name == null) {
            throw new JSONException("Names must be non-null");
        }
        return name;
    }

    /**
     * Appends {@code value} to the array already mapped to {@code name}. If
     * this object has no mapping for {@code name}, this inserts a new mapping.
     * If the mapping exists but its value is not an array, the existing
     * and new values are inserted in order into a new array which is itself
     * mapped to {@code name}. In aggregate, this allows values to be added to a
     * mapping one at a time.
     *
     * <p> Note that {@code append(String, Object)} provides better semantics.
     * In particular, the mapping for {@code name} will <b>always</b> be a
     * {@link JSONArray}. Using {@code accumulate} will result in either a
     * {@link JSONArray} or a mapping whose type is the type of {@code value}
     * depending on the number of calls to it.
     *
     * @param value a {@link JSONObject}, {@link JSONArray}, String, Boolean,
     *     Integer, Long, Double, {@link #NULL} or null. May not be {@link
     *     Double#isNaN() NaNs} or {@link Double#isInfinite() infinities}.
     */
    // TODO: Change {@code append) to {@link #append} when append is
    // unhidden.
    // Copied from upstream.
    // It must be copied, otherwise it adds a org.json.JSONArray instead of a JSONArray
    // in the object
    @NonNull public JSONObject accumulate(@NonNull String name, @Nullable Object value) {
        Object current = opt(checkName(name));
        if (current == null) {
            return put(name, value);
        }

        // check in accumulate, since array.put(Object) doesn't do any checking
        if (value instanceof Number) {
            checkDouble(((Number) value).doubleValue());
        }

        if (current instanceof JSONArray) {
            JSONArray array = (JSONArray) current;
            array.put(value);
        } else {
            JSONArray array = new JSONArray();
            array.put(current);
            array.put(value);
            put(name, array);
        }
        return this;
    }

    @CheckResult
    public Object get(String name) {
        try {
            return super.get(name);
        } catch (org.json.JSONException e) {
            throw new JSONException(e);
        }
    }

    @CheckResult
    public boolean getBoolean(String name) {
        try {
            return super.getBoolean(name);
        } catch (org.json.JSONException e) {
            throw new JSONException(e);
        }
    }

    @CheckResult
    public double getDouble(String name) {
        try {
            return super.getDouble(name);
        } catch (org.json.JSONException e) {
            throw new JSONException(e);
        }
    }

    @CheckResult
    public int getInt(String name) {
        try {
            return super.getInt(name);
        } catch (org.json.JSONException e) {
            throw new JSONException(e);
        }
    }

    @CheckResult
    public long getLong(String name) {
        try {
            return super.getLong(name);
        } catch (org.json.JSONException e) {
            throw new JSONException(e);
        }
    }

    @CheckResult
    public String getString(String name) {
        try {
            return super.getString(name);
        } catch (org.json.JSONException e) {
            throw new JSONException(e);
        }
    }

    @CheckResult
    public JSONArray getJSONArray(String name) {
        try {
            return JSONArray.arrayToArray(super.getJSONArray(name));
        } catch (org.json.JSONException e) {
            throw new JSONException (e);
        }
    }

    @CheckResult
    public JSONObject getJSONObject(String name) {
        try {
            return objectToObject(super.getJSONObject(name));
        } catch (org.json.JSONException e) {
            throw new JSONException (e);
        }
    }


    // Code copied from upstream
    @CheckResult
    public JSONArray toJSONArray(JSONArray names) {
        JSONArray result = new JSONArray();
        if (names == null) {
            return null;
        }
        int length = names.length();
        if (length == 0) {
            return null;
        }
        for (int i = 0; i < length; i++) {
            String name = JSON.toString(names.opt(i));
            result.put(opt(name));
        }
        return result;
    }

    @CheckResult
    public static String numberToString(Number number) {
        try {
            return org.json.JSONObject.numberToString(number);
        } catch (org.json.JSONException e) {
            throw new JSONException (e);
        }
    }

    @CheckResult
    public JSONArray names() {
        org.json.JSONArray ar = super.names();
        if (ar == null) {
            return null;
        } else {
            return new JSONArray(ar);
        }
    }

    @CheckResult
    public JSONArray optJSONArray(String name) {
        return JSONArray.arrayToArray(super.optJSONArray(name));
    }

    @CheckResult
    public JSONObject optJSONObject(String name) {
        return JSONObject.objectToObject(super.optJSONObject(name));
    }

    @CheckResult
    public JSONObject deepClone() {
        JSONObject clone = new JSONObject();
        return deepClonedInto(clone);
    }

    /** deep clone this into clone.

        Given a subtype `T` of JSONObject, and a JSONObject `j`, we could do
        ```
        T t = new T();
        j.deepClonedInto(t);
        ```
        in order to obtain a deep clone of `j` of type ```T```. */
    protected <T extends JSONObject> T deepClonedInto(T clone) {
        for (String key: this) {
            if (get(key) instanceof JSONObject) {
                clone.put(key, getJSONObject(key).deepClone());
            }
            else if (get(key) instanceof JSONArray) {
                clone.put(key, getJSONArray(key).deepClone());
            } else {
                clone.put(key, get(key));
            }
        }
        return clone;
    }

    public static JSONObject fromMap(Map<String, Boolean> map) {
        JSONObject ret = new JSONObject();
        for (Map.Entry<String, Boolean> i : map.entrySet()) {
            ret.put(i.getKey(), i.getValue());
        }
        return ret;
    }

    @Override
    public String toString(int indentSpace) {
        try {
            return super.toString(indentSpace);
        } catch (org.json.JSONException e) {
            throw new JSONException(e);
        }
    }
}
