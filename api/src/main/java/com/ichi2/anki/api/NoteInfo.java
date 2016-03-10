/***************************************************************************************
 *                                                                                      *
 * Copyright (c) 2016 Timothy Rae <perceptualchaos2@gmail.com>                          *
 *                                                                                      *
 * This program is free software; you can redistribute it and/or modify it under        *
 * the terms of the GNU Lesser General Public License as published by the Free Software *
 * Foundation; either version 3 of the License, or (at your option) any later           *
 * version.                                                                             *
 *                                                                                      *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY      *
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A      *
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.             *
 *                                                                                      *
 * You should have received a copy of the GNU Lesser General Public License along with  *
 * this program.  If not, see <http://www.gnu.org/licenses/>.                           *
 ****************************************************************************************/

package com.ichi2.anki.api;

import android.database.Cursor;
import com.ichi2.anki.FlashCardsContract;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;


/**  Container class containing the following public fields to share data with client applications
 *   id:      note ID (if skipped true and valid note exists, then it's the note ID of the existing note)
 *   flds:    The array of fields corresponding to note with given id
 *   key:     The very first field (used as a key for duplicate checking)*
 *   tags:    The array of tags
 *
 *  It may also contain a boolean 'newlyAdded' which is true if the note was newly added to the database
 **/

public class NoteInfo {
    /** note ID */
    public Long id;
    /** The array of fields */
    public String[] fields;
    /** The very first field (used as a key for duplicate checking) */
    public String key;
    /** The array of tags */
    public Set<String> tags;
    /** Whether or not the note was newly added to the database */
    public boolean newlyAdded = false;


    public NoteInfo(long id, String[] fields, Set<String> tags) {
        this.id = id;
        this.fields = fields;
        this.key = fields[0];
        this.tags = tags;
    }

    /**
     * Static initializer method to build the object from a cursor
     * @param cursor from a query to FlashCardsContract.Note.CONTENT_URI
     * @return a NoteInfo object or null if the cursor was not valid
     */
    static NoteInfo buildFromCursor(Cursor cursor) {
        try {
            int idIndex = cursor.getColumnIndexOrThrow(FlashCardsContract.Note._ID);
            int fldsIndex = cursor.getColumnIndexOrThrow(FlashCardsContract.Note.FLDS);
            int tagsIndex = cursor.getColumnIndexOrThrow(FlashCardsContract.Note.TAGS);
            String[] fields = Utils.splitFields(cursor.getString(fldsIndex));
            long id = cursor.getLong(idIndex);
            Set<String> tags = new HashSet<>(Arrays.asList(Utils.splitTags(cursor.getString(tagsIndex))));
            return new NoteInfo(id, fields, tags);
        } catch (Exception e) {
            return null;
        }
    }
}
