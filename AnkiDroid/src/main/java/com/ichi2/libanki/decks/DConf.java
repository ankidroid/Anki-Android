package com.ichi2.libanki.decks;

import com.ichi2.libanki.Collection;
import com.ichi2.utils.JSONArray;
import com.ichi2.utils.JSONObject;

import androidx.annotation.Nullable;

public class DConf extends ReadOnlyJSONObject{
    public DConf(JSONObject json) {
        super(json);
    }

    public DConf(String json) {
        super(json);
    }

    /**
     * Filtered decks contains their own config.
     * We keep the same values but consider it as a dconf.*/
    public DConf(Deck deck) {
        this(deck.getJSON());
    }

    @Nullable
    public Boolean parseTimer() {
        //Note: Card.py used != 0, DeckOptions used == 1
        try {
            //#6089 - Anki 2.1.24 changed this to a bool, reverted in 2.1.25.
            return getInt("timer") != 0;
        } catch (Exception e) {
            try {
                return getBoolean("timer");
            } catch (Exception ex) {
                return null;
            }
        }
    }

    public boolean parseTimerOpt(boolean defaultValue) {
        Boolean ret = parseTimer();
        if (ret == null) {
            ret = defaultValue;
        }
        return ret;
    }

    public NewConf getNew() {
        return new NewConf(getJSONObject("new"));
    }

    public ReviewConf getRev() {
        return new ReviewConf(getJSONObject("rev"));
    }

    public LapseConf getLapse() {
        return new LapseConf(getJSONObject("lapse"));
    }

    public void setMaxTaken(Object o) {
        put("maxTaken", o);
    }

    public JSONObject getReminder(){
        return getJSON().getJSONObject("reminder");
    }

    public void setReminder(Object o) {
        put("reminder", o);
    }

    public void setTimer(int timer) {
        put("timer", timer);
    }

    public void setAutoplay(Object o) {
        put("autoplay", o);
    }

    public void setReplayq(Object o) {
        put("replayq", o);
    }

    public void setName(Object o) {
        put("name", o);
    }

    public void version10to11(Collection col) {
        ReviewingConf r = getRev();
        r.put("ivlFct", r.optDouble("ivlFct", 1));
        if (r.has("ivlfct")) {
            r.remove("ivlfct");
        }
        r.put("maxIvl", 36500);
        col.getDecks().save();
    }
}
