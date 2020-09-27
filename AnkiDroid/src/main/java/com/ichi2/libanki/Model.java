package com.ichi2.libanki;


import android.util.Pair;

import com.ichi2.utils.JSONArray;
import com.ichi2.utils.JSONObject;

import java.util.HashMap;
import java.util.Map;

import androidx.annotation.NonNull;

public class Model extends JSONObject {
    public Model() {
        super();
    }

    public Model(JSONObject json) {
        super(json);
    }

    public Model(String json) {
        super(json);
    }


    /** 
     * @return Mapping of field name -> (ord, field)*/
    @NonNull
    public Map<String, Pair<Integer, JSONObject>> fieldMap() {
        JSONArray flds = getJSONArray("flds");
        // TreeMap<Integer, String> map = new TreeMap<Integer, String>();
        Map<String, Pair<Integer, JSONObject>> result = new HashMap<>();
        for (JSONObject f: flds.jsonObjectIterable()) {
            result.put(f.getString("name"), new Pair<>(f.getInt("ord"), f));
        }
        return result;
    }


    @Override
    public Model deepClone() {
        Model clone = new Model();
        deepClonedInto(clone);
        return clone;
    }
}
