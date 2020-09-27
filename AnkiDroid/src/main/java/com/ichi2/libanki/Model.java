package com.ichi2.libanki;


import android.util.Pair;

import com.ichi2.utils.JSONArray;
import com.ichi2.utils.JSONObject;

import java.util.HashMap;
import java.util.Map;

import androidx.annotation.NonNull;

public class Model extends JSONObject {
    private Map<String, Pair<Integer, JSONObject>> mFieldMap;

    public Model() {
        super();
    }

    public Model(JSONObject json) {
        super(json);
    }

    public Model(String json) {
        super(json);
    }

    /** Empty cache. Should be called when the model is saved in the collection. */
    protected void reset() {
        mFieldMap = null;
    }

    /**
     * @return Mapping of field name -> (ord, field)*/
    @NonNull
    public synchronized Map<String, Pair<Integer, JSONObject>> fieldMap() {
        if (mFieldMap == null) {
            JSONArray flds = getJSONArray("flds");
            // TreeMap<Integer, String> map = new TreeMap<Integer, String>();
            mFieldMap = new HashMap<>();
            for (JSONObject f: flds.jsonObjectIterable()) {
                mFieldMap.put(f.getString("name"), new Pair<>(f.getInt("ord"), f));
            }
        }
        return mFieldMap;
    }


    @Override
    public Model deepClone() {
        Model clone = new Model();
        deepClonedInto(clone);
        return clone;
    }
}
