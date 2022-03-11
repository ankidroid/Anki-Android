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


import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class JSONArray extends org.json.JSONArray {
    public JSONArray() {
        super();
    }

    public JSONArray(org.json.JSONArray copyFrom) {
        try {
            for (int i = 0; i < copyFrom.length(); i++) {
                put(i, copyFrom.get(i));
            }
        } catch (org.json.JSONException e) {
            throw new JSONException(e);
        }
    }


    /**
     * This method simply change the type.
     *
     * See the comment of objectToObject to read about the problems met here.
     *
     * @param ar Actually a JSONArray
     * @return the same element as input. But considered as a JSONArray.
     */
    public static JSONArray arrayToArray(org.json.JSONArray ar){
        return (JSONArray) ar;
    }

    public JSONArray(JSONTokener x) {
        this();
        try {
            if (x.nextClean() != '[') {
                throw x.syntaxError("A JSONArray text must start with '['");
            }

            char nextChar = x.nextClean();
            if (nextChar == 0) {
                // array is unclosed. No ']' found, instead EOF
                throw x.syntaxError("Expected a ',' or ']'");
            }
            if (nextChar != ']') {
                x.back();
                for (; ; ) {
                    if (x.nextClean() == ',') {
                        x.back();
                        put(JSONObject.NULL);
                    } else {
                        x.back();
                        put(x.nextValue());
                    }
                    switch (x.nextClean()) {
                        case 0:
                            // array is unclosed. No ']' found, instead EOF
                            throw x.syntaxError("Expected a ',' or ']'");
                        case ',':
                            nextChar = x.nextClean();
                            if (nextChar == 0) {
                                // array is unclosed. No ']' found, instead EOF
                                throw x.syntaxError("Expected a ',' or ']'");
                            }
                            if (nextChar == ']') {
                                return;
                            }
                            x.back();
                            break;
                        case ']':
                            return;
                        default:
                            throw x.syntaxError("Expected a ',' or ']'");
                    }
                }
            }
        } catch (org.json.JSONException e) {
            throw new JSONException(e);
        }
    }

    public JSONArray(String source) {
        this(new JSONTokener(source));
    }

    public JSONArray(Object array) {
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

    public JSONArray(Collection<?> copyFrom) {
        this();
        if (copyFrom != null) {
            for (Object o : copyFrom) {
                put(o);
            }
        }
    }

    public JSONArray put(double value) {
        try {
            super.put(value);
            return this;
        } catch (org.json.JSONException e) {
            throw new JSONException(e);
        }
    }

    public JSONArray put(int index, boolean value) {
        try {
            super.put(index, value);
            return this;
        } catch (org.json.JSONException e) {
            throw new JSONException(e);
        }
    }

    public JSONArray put(int index, double value) {
        try {
            super.put(index, value);
            return this;
        } catch (org.json.JSONException e) {
            throw new JSONException(e);
        }
    }

    public JSONArray put(int index, int value) {
        try {
            super.put(index, value);
            return this;
        } catch (org.json.JSONException e) {
            throw new JSONException(e);
        }
    }

    public JSONArray put(int index, long value) {
        try {
            super.put(index, value);
            return this;
        } catch (org.json.JSONException e) {
            throw new JSONException(e);
        }
    }

    public JSONArray put(int index, Object value) {
        try {
            super.put(index, value);
            return this;
        } catch (org.json.JSONException e) {
            throw new JSONException(e);
        }
    }

    public Object get(int index) {
        try {
            return super.get(index);
        } catch (org.json.JSONException e) {
            throw new JSONException(e);
        }
    }

    public boolean getBoolean(int index) {
        try {
            return super.getBoolean(index);
        } catch (org.json.JSONException e) {
            throw new JSONException(e);
        }
    }

    public double getDouble(int index) {
        try {
            return super.getDouble(index);
        } catch (org.json.JSONException e) {
            throw new JSONException(e);
        }
    }

    public int getInt(int index) {
        try {
            return super.getInt(index);
        } catch (org.json.JSONException e) {
            throw new JSONException(e);
        }
    }

    public long getLong(int index) {
        try {
            return super.getLong(index);
        } catch (org.json.JSONException e) {
            throw new JSONException(e);
        }
    }

    public String getString(int index) {
        try {
            return super.getString(index);
        } catch (org.json.JSONException e) {
            throw new JSONException(e);
        }
    }

    public JSONArray getJSONArray(int pos) {
        try {
            return arrayToArray(super.getJSONArray(pos));
        } catch (org.json.JSONException e) {
            throw new RuntimeException (e);
        }
    }

    public JSONObject getJSONObject(int pos) {
        try {
            return JSONObject.objectToObject(super.getJSONObject(pos));
        } catch (org.json.JSONException e) {
            throw new RuntimeException (e);
        }
    }

    public String join(String separator) {
        try {
            return super.join(separator);
        } catch (org.json.JSONException e) {
            throw new JSONException(e);
        }
    }

    @Override
    public String toString(int indentSpace) {
        try {
            return super.toString(indentSpace);
        } catch (org.json.JSONException e) {
            throw new JSONException(e);
        }
    }


    public JSONArray deepClone() {
        JSONArray clone = new JSONArray();
        for (int i = 0; i < length(); i++) {
            if (get(i) instanceof JSONObject) {
                clone.put(getJSONObject(i).deepClone());
            }
            else if (get(i) instanceof JSONArray) {
                clone.put(getJSONArray(i).deepClone());
            } else {
                clone.put(get(i));
            }
        }
        return clone;
    }

    public Iterable<JSONArray> jsonArrayIterable() {
        return this::jsonArrayIterator;
    }
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
            result.put(name, opt(i));
        }
        return result;
    }
}