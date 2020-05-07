package com.ichi2.utils;

import java.lang.reflect.Array;
import java.util.Collection;
import java.util.Iterator;

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
        Assert.that(ar == null || ar instanceof JSONArray, "Object %s should have been an instance of our JSONArray.", ar);
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

    public JSONArray(Collection copyFrom) {
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

    public String toString(int indentSpaces) {
        try {
            return super.toString(indentSpaces);
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
}
