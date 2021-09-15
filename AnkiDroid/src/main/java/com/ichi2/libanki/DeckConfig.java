/*
 Copyright (c) 2020 David Allison <davidallisongithub@gmail.com>
 Copyright (c) 2020 Arthur Milchior <Arthur@Milchior.fr>

 This program is free software; you can redistribute it and/or modify it under
 the terms of the GNU General Public License as published by the Free Software
 Foundation; either version 3 of the License, or (at your option) any later
 version.

 This program is distributed in the hope that it will be useful, but WITHOUT ANY
 WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 PARTICULAR PURPOSE. See the GNU General Public License for more details.

 You should have received a copy of the GNU General Public License along with
 this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.ichi2.libanki;

import com.ichi2.utils.JSONObject;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import timber.log.Timber;

public class DeckConfig extends JSONObject {
    @NonNull
    private final Source mSource;

    /**
     * Creates a new empty deck config object
     */
    private DeckConfig(@NonNull Source source) {
        super();
        this.mSource = source;
    }

    /**
     * Creates a copy from {@link JSONObject} and use it as a string
     *
     * This function will perform deepCopy on the passed object
     *
     */
    public DeckConfig(JSONObject json, @NonNull Source source) {
        super(json);
        mSource = source;
    }

    /**
     * Creates a deck config object form a json string
     */
    public DeckConfig(String json, @NonNull Source source) {
        super(json);
        mSource = source;
    }

    public static @Nullable Boolean parseTimer(JSONObject config) {
        //Note: Card.py used != 0, DeckOptions used == 1
        try {
            //#6089 - Anki 2.1.24 changed this to a bool, reverted in 2.1.25.
            return config.getInt("timer") != 0;
        } catch (Exception e) {
            Timber.w(e);
            try {
                return config.getBoolean("timer");
            } catch (Exception ex) {
                Timber.w(ex);
                return null;
            }
        }
    }

    public static boolean parseTimerOpt(JSONObject config, boolean defaultValue) {
        Boolean ret = parseTimer(config);
        if (ret == null) {
            ret = defaultValue;
        }
        return ret;
    }

    public boolean isDyn() {
        return getInt("dyn") == Consts.DECK_DYN;
    }

    public boolean isStd() {
        return getInt("dyn") == Consts.DECK_STD;
    }


    @Override
    public DeckConfig deepClone() {
        DeckConfig dc = new DeckConfig(this.getSource());
        return deepClonedInto(dc);
    }

    @NonNull
    public Source getSource() {
        return mSource;
    }

    /** Specifies how to save the config */
    public enum Source {
        /** From an entry in dconf */
        DECK_CONFIG,
        /** filtered decks have their config embedded in the deck */
        DECK_EMBEDDED
    }
}
