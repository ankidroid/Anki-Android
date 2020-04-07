package com.ichi2.libanki.decks;

import com.ichi2.utils.JSONArray;
import com.ichi2.utils.JSONObject;

public class Deck extends ReadOnlyJSONObject{
    public Deck() {
        super();
    }

    public Deck(Deck deck) {
        super(deck.getJSON());
    }

    public Deck(JSONObject json) {
        super(json);
    }

    public Deck(String json) {
        super(json);
    }
}
