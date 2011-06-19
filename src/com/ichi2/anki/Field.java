package com.ichi2.anki;

import android.database.Cursor;

public class Field {
    /**
     * FIXME: nothing is done in case of db error or no returned row
     * 
     * @param factId
     * @param fieldModelId
     * @return the value of a field corresponding to the 2 search parameters - or an empty string if not found
     */
    protected final static String fieldValuefromDb(Deck deck, long factId, long fieldModelId) {
        Cursor cursor = null;
        String value = "";
        try {
            StringBuffer query = new StringBuffer();
            query.append("SELECT value");
            query.append(" FROM fields");
            query.append(" WHERE factId = ").append(factId).append(" AND fieldModelId = ").append(fieldModelId);
            cursor = AnkiDatabaseManager.getDatabase(deck.getDeckPath()).getDatabase().rawQuery(query.toString(), null);

            cursor.moveToFirst();
            value = cursor.getString(0); // Primary key
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return value;
    }
}
