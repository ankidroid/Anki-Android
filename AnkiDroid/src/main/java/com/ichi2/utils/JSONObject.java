package com.ichi2.utils;

/**
 * Each method similar to the methods in JSONObjects. Name changed to add a ,
 * and it throws JSONException instead of JSONException.
 * Furthermore, it returns JSONObject and JSONArray
 *
 */

import java.util.Iterator;
import java.util.Map;

public class JSONObject extends org.json.JSONObject implements Iterable<String> {

    public static final Object NULL = JSONObject.NULL;

    public JSONObject() {
        super();
    }

    public JSONObject(Map copyFrom) {
        super(copyFrom);
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
        for (String key: this) {
            put(key, copyFrom.get(key));
        }
    }

    /**
        Iters on the keys. (Similar to iteration in Python's
        dictionnary.
    */
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
        Assert.that(obj == null || (obj instanceof JSONObject), "Object %s should have been an instance of our JSONObject.", obj);
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

    public JSONObject accumulate(String name, Object value) {
        try {
            super.accumulate(name, value);
            return this;
        } catch (org.json.JSONException e) {
            throw new JSONException(e);
        }
    }

    public Object get(String name) {
        try {
            return super.get(name);
        } catch (org.json.JSONException e) {
            throw new JSONException(e);
        }
    }

    public boolean getBoolean(String name) {
        try {
            return super.getBoolean(name);
        } catch (org.json.JSONException e) {
            throw new JSONException(e);
        }
    }

    public double getDouble(String name) {
        try {
            return super.getDouble(name);
        } catch (org.json.JSONException e) {
            throw new JSONException(e);
        }
    }

    public int getInt(String name) {
        try {
            return super.getInt(name);
        } catch (org.json.JSONException e) {
            throw new JSONException(e);
        }
    }

    public long getLong(String name) {
        try {
            return super.getLong(name);
        } catch (org.json.JSONException e) {
            throw new JSONException(e);
        }
    }

    public String getString(String name) {
        try {
            return super.getString(name);
        } catch (org.json.JSONException e) {
            throw new JSONException(e);
        }
    }

    public JSONArray getJSONArray(String name) {
        try {
            return JSONArray.arrayToArray(super.getJSONArray(name));
        } catch (org.json.JSONException e) {
            throw new RuntimeException (e);
        }
    }

    public JSONObject getJSONObject(String name) {
        try {
            return objectToObject(super.getJSONObject(name));
        } catch (org.json.JSONException e) {
            throw new RuntimeException (e);
        }
    }

    public JSONArray toJSONArray(JSONArray names) {
        try {
            return JSONArray.arrayToArray(super.toJSONArray(names));
        } catch (org.json.JSONException e) {
            throw new JSONException(e);
        }
    }

    public static String numberToString(Number number) {
        try {
            return org.json.JSONObject.numberToString(number);
        } catch (org.json.JSONException e) {
            throw new RuntimeException (e);
        }
    }

    public JSONArray names() {
        org.json.JSONArray ar = super.names();
        if (ar == null) {
            return null;
        } else {
            return new JSONArray(ar);
        }
    }

    public JSONArray optJSONArray(String name) {
        return JSONArray.arrayToArray(super.optJSONArray(name));
    }

    public JSONObject optJSONObject(String name) {
        return JSONObject.objectToObject(super.optJSONObject(name));
    }

    public JSONObject deepClone() {
        JSONObject clone = new JSONObject();
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
}
