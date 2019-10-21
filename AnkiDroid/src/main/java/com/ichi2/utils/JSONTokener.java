package com.ichi2.utils;


import com.ichi2.utils.JSONException;

public class JSONTokener extends org.json.JSONTokener {
    public JSONTokener(String s) {
        super(s);
    }


    public Object nextValue() throws org.json.JSONException {
        char c = this.nextClean();
        this.back();

        switch (c) {
            case '{':
                return new JSONObject(this);
            case '[':
                return new JSONArray(this);
            default:
                return super.nextValue();
        }
    }
}
