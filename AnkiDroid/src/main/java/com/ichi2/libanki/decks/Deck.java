package com.ichi2.libanki.decks;

import com.ichi2.libanki.Collection;
import com.ichi2.utils.JSONArray;
import com.ichi2.utils.JSONObject;

public class Deck extends ReadOnlyJSONObject {
    public Deck(JSONObject json) {
        super(json);
    }

    public Deck(String json) {
        super(json);
    }

    public void setDesc(Object o) {
        put("desc", o);
    }

    public void setConf(long conf) {
        put("conf", conf);
    }

    public JSONArray getTerms() {
        return getJSONArray("terms");
    }

    public void setDelays(JSONArray delays) {
        put("delays", delays);
    }

    public void setDelaysNull() {
        put("delays", JSONObject.NULL);
    }

    public JSONArray getDelays() {
        return getJSONArray("delays");
    }

    public void setResched(boolean resched) {
        put("resched", resched);
    }

    public void setMid(long mid) {
        put("mid", mid);
    }

    public void setExtend(String key, int n) {
        put("extend" + key, n);
    }

    public JSONArray getToday(String key) {
        return getJSONArray(key + "Today");
    }

    public void removeEmpty() {
        remove("empty");
    }

    /* Methods updating schema versions */
    public void version3to4(Collection col) {
        put("dyn", 0);
        put("collapsed", false);
        col.getDecks().save();
    }
}
