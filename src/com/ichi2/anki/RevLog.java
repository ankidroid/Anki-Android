/****************************************************************************************
 * Copyright (c) 2009 Daniel Svärd <daniel.svard@gmail.com>                             *
 *                                                                                      *
 * This program is free software; you can redistribute it and/or modify it under        *
 * the terms of the GNU General Public License as published by the Free Software        *
 * Foundation; either version 3 of the License, or (at your option) any later           *
 * version.                                                                             *
 *                                                                                      *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY      *
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A      *
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.             *
 *                                                                                      *
 * You should have received a copy of the GNU General Public License along with         *
 * this program.  If not, see <http://www.gnu.org/licenses/>.                           *
 ****************************************************************************************/

package com.ichi2.anki;

import android.content.ContentValues;
import android.database.Cursor;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.lang.reflect.Field;
import java.sql.Date;

/**
 * Flags: 0=standard review, 1=reschedule due to cram, drill, etc
 * Rep: Repetition number. The same number may appear twice if a card has been
 * manually rescheduled or answered on multiple sites before a sync.
 * 
 * We store the times in integer milliseconds to avoid an extra index on the
 * primary key.
 */
public class RevLog {
 
    // BEGIN: SQL table columns
	private long mTime;
	private long mCardId;
	private int mEase;
	private int mRep;
	private double mLastInterval;
	private double mInterval;
	private double mFactor;
	private long mUserTime;
	private int mFlags = 0;


    public static void logReview(Deck deck, Card card, int ease) {
    	logReview(deck, card, ease, 0);
    }
    public static void logReview(Deck deck, Card card, int ease, int flags) {
        ContentValues values = new ContentValues();
        values.put("time", Utils.now() * 1000);
        values.put("cardId", card.getId());
        values.put("ease", ease);
        values.put("rep", card.getReps());
        values.put("lastInterval", card.getLastInterval());
        values.put("Interval", card.getInterval());
        values.put("factor", card.getFactor());
        values.put("userTime", card.userTime() * 1000);
        values.put("flags", flags);

        AnkiDatabaseManager.getDatabase(deck.getDeckPath()).insert(deck, "revlog", null, values);
    }
}
