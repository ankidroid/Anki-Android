package com.ichi2.libanki;

import com.ichi2.utils.JSONArray;
import com.ichi2.utils.JSONObject;

public class Deck extends JSONObject {
    public Deck(JSONObject json) {
        super(json);
    }

    public Deck(String json) {
        super(json);
    }

    public Deck() {
        super();
    }

    @Override
    public Deck deepClone() {
        Deck clone = new Deck();
        deepClonedInto(clone);
        return clone;
    }
}
