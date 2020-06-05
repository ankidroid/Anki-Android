package com.ichi2.utils;

import com.ichi2.utils.JSONObject;

import java.util.Comparator;

public class NamedJSONComparator implements Comparator<JSONObject> {
    public static final NamedJSONComparator instance = new NamedJSONComparator();

    @Override
    public int compare(JSONObject lhs, JSONObject rhs) {
        String o1;
        String o2;
        o1 = lhs.getString("name");
        o2 = rhs.getString("name");
        return o1.compareToIgnoreCase(o2);
    }
}
