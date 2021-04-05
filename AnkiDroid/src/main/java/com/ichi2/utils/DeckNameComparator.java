package com.ichi2.utils;

import com.ichi2.libanki.Decks;

import java.util.Comparator;

public class DeckNameComparator implements Comparator<String> {
    public static final DeckNameComparator INSTANCE = new DeckNameComparator();

    @Override
    public int compare(String lhs, String rhs) {
        String[] o1 = Decks.path(lhs);
        String[] o2 = Decks.path(rhs);
        for (int i = 0; i < Math.min(o1.length, o2.length); i++) {
            int result = o1[i].compareToIgnoreCase(o2[i]);
            if (result != 0) {
                return result;
            }
        }
        return Integer.compare(o1.length, o2.length);
    }
}
