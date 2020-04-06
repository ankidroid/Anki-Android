package com.ichi2.utils;

import java.util.Comparator;

public class DeckNameComparator implements Comparator<String> {
    public static final DeckNameComparator instance = new DeckNameComparator();

    @Override
    public int compare(String lhs, String rhs) {
        String[] o1;
        String[] o2;
        o1 = lhs.split("::");
        o2 = rhs.split("::");
        for (int i = 0; i < Math.min(o1.length, o2.length); i++) {
            int result = o1[i].compareToIgnoreCase(o2[i]);
            if (result != 0) {
                return result;
            }
        }
        if (o1.length < o2.length) {
            return -1;
        } else if (o1.length > o2.length) {
            return 1;
        } else {
            return 0;
        }
    }
}
