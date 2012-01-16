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

import android.database.MatrixCursor;
import android.test.AndroidTestCase;

import java.lang.reflect.Field;
import java.util.HashMap;

/**
 * Unit tests for {@link CardsQuery}.
 */
public class CardsQueryTest extends AndroidTestCase {
    
    public void testProjection() throws Exception {
        // Check that each field corresponds to an entry in the projection.
        for (Field field : CardsQuery.class.getDeclaredFields()) {
            if (!field.getName().equals("PROJECTION")) {
                assertEquals(field.getName().toLowerCase().replace('_', '.'),
                        CardsQuery.PROJECTION[field.getInt(null)].toLowerCase());
            }
        }
    }
    
    
    public void testGetRawQuery() {
        assertTrue(CardsQuery.getRawQuery(10, null).contains(Utils.join(", ", CardsQuery.PROJECTION)));
        assertTrue(CardsQuery.getRawQuery(10, null).contains(" LIMIT 10"));
        assertTrue(CardsQuery.getRawQuery(20, null).contains(" LIMIT 20"));
        assertFalse(CardsQuery.getRawQuery(10, null).contains(" AND cards.id > "));
        assertTrue(CardsQuery.getRawQuery(10, "1234").contains(" AND cards.id > 1234 "));
    }
    
    
    public void testCardFromCursor() {
        MatrixCursor cursor = new MatrixCursor(CardsQuery.PROJECTION);
        cursor.moveToFirst();
        Object[] values = new Object[]{
                123456789L,
                "question",
                "answer",
                "ftag1 ftag2",
                "mtag",
                "model",
                2,
                13000.0,
                25.0,
                1.25,
                12950.0,
        };
        cursor.addRow(values);
        cursor.moveToFirst();
        
        HashMap<String, String> card = CardsQuery.newCardFromCursor(cursor);
        
        assertEquals("123456789", card.get("id"));
        assertEquals("question", card.get("question"));
        assertEquals("answer", card.get("answer"));
        assertEquals("00", card.get("flags"));
        assertEquals("ftag1 ftag2 mtag model", card.get("tags"));
        assertEquals("13000.0", card.get("due"));
        assertEquals("25.0", card.get("interval"));
        assertEquals("1.25", card.get("factor"));
        assertEquals("12950.0", card.get("created"));
    }
    
}
