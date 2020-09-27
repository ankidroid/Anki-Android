package com.ichi2.libanki;


import android.util.Pair;

import com.ichi2.utils.JSONArray;
import com.ichi2.utils.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import androidx.annotation.NonNull;

public class Model extends JSONObject {
    @SuppressWarnings("RegExpRedundantEscape") // In Android, } should be escaped
    private static final Pattern fClozePattern1 = Pattern.compile("\\{\\{[^}]*?cloze:(?:[^}]?:)*(.+?)\\}\\}");
    private static final Pattern fClozePattern2 = Pattern.compile("<%cloze:(.+?)%>");

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

    /** Name of the fields which may contain cloze number */
    public java.util.Collection<String> getNamesOfFieldsContainingCloze() {
        java.util.Collection<String> matches = new ArrayList<>();
        final String question_template = m.getJSONArray("tmpls").getJSONObject(0).getString("qfmt");
        Matcher mm = fClozePattern1.matcher(question_template);
        while (mm.find()) {
            matches.add(mm.group(1));
        }
        mm = fClozePattern2.matcher(question_template);
        while (mm.find()) {
            matches.add(mm.group(1));
        }
        return matches;
    }

    @Override
    public Model deepClone() {
        Model clone = new Model();
        deepClonedInto(clone);
        return clone;
    }
}
