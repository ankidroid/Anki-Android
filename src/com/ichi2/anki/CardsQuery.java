/****************************************************************************************
 * Copyright (c) 2011 Flavio Lerda <flerda@gmail.com>                                   *
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

import android.database.Cursor;
import android.text.TextUtils;

import java.util.HashMap;

import com.ichi2.libanki.Utils;

/**
 * Class to handle queries for a set of cards.
 */
public final class CardsQuery {

    /* package for testing */public static final String[] PROJECTION = { "cards.id", // 0
            "cards.question", // 1
            "cards.answer", // 2
            "facts.tags", // 3
            "models.tags", // 4
            "cardModels.name", // 5
            "cards.priority", // 6
            "cards.due", // 7
            "cards.interval", // 8
            "cards.factor", // 9
            "cards.created", // 10
    };

    /* package for testing */static final int CARDS_ID = 0;
    /* package for testing */static final int CARDS_QUESTION = 1;
    /* package for testing */static final int CARDS_ANSWER = 2;
    /* package for testing */static final int FACTS_TAGS = 3;
    /* package for testing */static final int MODELS_TAGS = 4;
    /* package for testing */static final int CARDMODELS_NAME = 5;
    /* package for testing */static final int CARDS_PRIORITY = 6;
    /* package for testing */static final int CARDS_DUE = 7;
    /* package for testing */static final int CARDS_INTERVAL = 8;
    /* package for testing */static final int CARDS_FACTOR = 9;
    /* package for testing */static final int CARDS_CREATED = 10;


    /**
     * Returns the query for getting card details. It supports pagination.
     *
     * @param chunkSize the maximum number of values to return
     * @param startId the id for the last card returned by a previous query
     */
    public static String getRawQuery(int chunkSize, String startId) {
        return String.format("SELECT %s FROM cards, facts, models, cardModels "
                + "WHERE cards.factId == facts.id AND facts.modelId == models.id "
                + "AND cards.cardModelId = cardModels.id %s ORDER BY cards.id LIMIT %d", Utils.join(", ", PROJECTION),
                TextUtils.isEmpty(startId) ? "" : ("AND cards.id > " + startId), chunkSize);
    }


    /**
     * Creates a new card from the content of the cursor. It does not modify the position of the cursor, which should
     * already be pointing to the data to use.
     */
    public static HashMap<String, String> newCardFromCursor(Cursor cursor) {
        HashMap<String, String> data = new HashMap<String, String>();
        data.put("id", Long.toString(cursor.getLong(CardsQuery.CARDS_ID)));
        data.put("question", Utils.replaceLineBreak(Utils.stripHTML(cursor.getString(CardsQuery.CARDS_QUESTION))));
        data.put("answer", Utils.replaceLineBreak(Utils.stripHTML(cursor.getString(CardsQuery.CARDS_ANSWER))));
        String factTags = cursor.getString(CardsQuery.FACTS_TAGS);
        String cardsPriority = cursor.getString(CardsQuery.CARDS_PRIORITY);

        String flags = null;
        if (factTags.contains("Marked")) {
            flags = "1";
        } else {
            flags = "0";
        }

        // Is it suspended?
        if (cardsPriority.equals("-3")) {
            flags = flags + "1";
        } else {
            flags = flags + "0";
        }

        data.put("flags", flags);

        data.put(
                "tags",
                factTags + " " + cursor.getString(CardsQuery.MODELS_TAGS) + " "
                        + cursor.getString(CardsQuery.CARDMODELS_NAME));
        data.put("due", Double.toString(cursor.getDouble(CardsQuery.CARDS_DUE)));
        data.put("interval", Double.toString(cursor.getDouble(CardsQuery.CARDS_INTERVAL)));
        data.put("factor", Double.toString(cursor.getDouble(CardsQuery.CARDS_FACTOR)));
        data.put("created", Double.toString(cursor.getDouble(CardsQuery.CARDS_CREATED)));

        return data;
    }

}
