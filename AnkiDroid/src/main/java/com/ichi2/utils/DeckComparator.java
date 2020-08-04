package com.ichi2.utils;

import com.ichi2.libanki.Deck;
import com.ichi2.utils.DeckNameComparator;
import com.ichi2.utils.JSONObject;

import java.util.Comparator;

public class DeckComparator implements Comparator<JSONObject> {
    public static final DeckComparator instance = new DeckComparator();

    @Override
    public int compare(JSONObject lhs, JSONObject rhs) {
        return DeckNameComparator.instance.compare(lhs.getString("name"), rhs.getString("name"));
    }
}
