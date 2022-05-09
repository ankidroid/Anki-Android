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


/**
 * Representation of the contents of a note in AnkiDroid.
 */
public final class NoteInfo {
    private final long mId;
    private final String[] mFields;
    private final Set<String> mTags;

    /**
     * Static initializer method to build a NoteInfo object from a Cursor
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

    private NoteInfo(long id, String[] fields, Set<String> tags) {
        mId = id;
        mFields = fields;
        mTags = tags;
    }

    /**
     * Clone a NoteInfo object
     * @param parent the object to clone
     */
    public NoteInfo(NoteInfo parent) {
        mId = parent.getId();
        mFields = parent.getFields().clone();
        mTags = new HashSet<>(parent.getTags());
    }

    /** Note ID */
    public long getId() {
        return mId;
    }

    /** The array of fields */
    public String[] getFields() {
        return mFields;
    }

    /** The set of tags */
    public Set<String> getTags() {
        return mTags;
    }

    /** The first field **/
    public String getKey() {
        return getFields()[0];
    }
}
