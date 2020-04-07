package com.ichi2.libanki.decks;

import java.util.Comparator;

public class ROJSONComparator implements Comparator<ReadOnlyJSONObject> {
    public static ROJSONComparator instance = new ROJSONComparator();
    @Override
    public int compare(ReadOnlyJSONObject lhs, ReadOnlyJSONObject rhs) {
        String o1;
        String o2;
        o1 = lhs.getString("name");
        o2 = rhs.getString("name");
        return o1.compareToIgnoreCase(o2);
    }
}
