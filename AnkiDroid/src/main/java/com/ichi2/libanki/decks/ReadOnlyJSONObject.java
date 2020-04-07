package com.ichi2.libanki.decks;

import com.ichi2.utils.JSONArray;
import com.ichi2.utils.JSONObject;

import java.util.ArrayList;
import java.util.Iterator;

import androidx.annotation.NonNull;

/** Encapsulate some JSON. At the end of the pull request, restricting
 * edition to the current decks package. */
public class ReadOnlyJSONObject implements Iterable<String> {
    private JSONObject json;

    /** All changes made to this are applied to json */
    public ReadOnlyJSONObject(JSONObject json) {
        this.json = json;
    }

    public ReadOnlyJSONObject() {
        this.json = new JSONObject();
    }

    public ReadOnlyJSONObject(String json) {
        this.json = new JSONObject(json);
    }

    public JSONObject getJSON() {
        return json;
    }

    public Object get(String name) {
        return json.get(name);
    }

    public int getInt(String key) {
        return json.getInt(key);
    }

    public long getLong(String key) {
        return json.getLong(key);
    }

    public double getDouble(String key) {
        return json.getDouble(key);
    }

    public boolean getBoolean(String key) {
        return json.getBoolean(key);
    }

    public String getString(String key) {
        return json.getString(key);
    }

    public JSONObject getJSONObject(String key) {
        return json.getJSONObject(key);
    }

    public JSONArray getJSONArray(String key) {
        return json.getJSONArray(key);
    }

    public Object opt(String name) {
        return json.opt(name);
    }

    public int optInt(String key) {
        return json.optInt(key);
    }

    public long optLong(String key) {
        return json.optLong(key);
    }

    public double optDouble(String key) {
        return json.optDouble(key);
    }

    public boolean optBoolean(String key) {
        return json.optBoolean(key);
    }

    public String optString(String key) {
        return json.optString(key);
    }

    public JSONObject optJSONObject(String key) {
        return json.optJSONObject(key);
    }

    public JSONArray optJSONArray(String key) {
        return json.optJSONArray(key);
    }

    public int optInt(String key, int fallback) {
        return json.optInt(key, fallback);
    }

    public long optLong(String key, long fallback) {
        return json.optLong(key, fallback);
    }

    public double optDouble(String key, double fallback) {
        return json.optDouble(key, fallback);
    }

    public boolean optBoolean(String key, boolean fallback) {
        return json.optBoolean(key, fallback);
    }

    public String optString(String key, String fallback) {
        return json.optString(key, fallback);
    }

    public JSONObject put(String name, Object value) {
        return json.put(name, value);
    }

    public JSONObject put(String key, int value) {
        return json.put(key, value);
    }

    public JSONObject put(String key, long value) {
        return json.put(key, value);
    }

    public JSONObject put(String key, double value) {
        return json.put(key, value);
    }

    public JSONObject put(String key, boolean value) {
        return json.put(key, value);
    }

    public boolean has(String key) {
        // TODO: remove it. It's used only to differentiate filtered and standard deck. There is more elegant way to do it.
        return json.has(key);
    }

    public void remove(String key) {
        json.remove(key);
    }

    /** A deep copy of the underlying JSON.

     This copy can be modified without impact on this object itself.*/
    public JSONObject deepClone() {
        return json.deepClone();
    }

    /**
     Set usn to 0 in every object.

     This method is called during full sync, before uploading, so
     during an instant, the value will be zero while the object is
     not actually online. This is not a problem because if the sync
     fails, a full sync will occur again next time.

     @return whether there was a non-zero usn; in this case the list
     should be saved before the upload.
     */
    public static boolean markAsUploaded(ArrayList<? extends ReadOnlyJSONObject> ar) {
        boolean changed = false;
        for (ReadOnlyJSONObject obj: ar) {
            if (obj.optInt("usn", 1) != 0) {
                obj.put("usn", 0);
                changed = true;
            }
        }
        return changed;
    }


    @NonNull
    @Override
    public Iterator<String> iterator() {
        return json.iterator();
    }

    @NonNull
    @Override
    public String toString() {
        return getJSON().toString();
    }
}
