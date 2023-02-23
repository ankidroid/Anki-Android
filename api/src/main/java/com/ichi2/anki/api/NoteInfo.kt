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
package com.ichi2.anki.api

import android.database.Cursor
import com.ichi2.anki.FlashCardsContract
import java.util.*

/**
 * Representation of the contents of a note in AnkiDroid.
 */
public class NoteInfo {
    private val id: Long
    private val fields: Array<String>
    private val tags: Set<String>

    internal companion object {
        /**
         * Static initializer method to build a NoteInfo object from a Cursor
         * @param cursor from a query to FlashCardsContract.Note.CONTENT_URI
         * @return a NoteInfo object or null if the cursor was not valid
         */
        @JvmStatic // API Project
        internal fun buildFromCursor(cursor: Cursor): NoteInfo? {
            return try {
                val idIndex = cursor.getColumnIndexOrThrow(FlashCardsContract.Note._ID)
                val fldsIndex = cursor.getColumnIndexOrThrow(FlashCardsContract.Note.FLDS)
                val tagsIndex = cursor.getColumnIndexOrThrow(FlashCardsContract.Note.TAGS)
                val fields = Utils.splitFields(cursor.getString(fldsIndex))
                val id = cursor.getLong(idIndex)
                val tags: Set<String> = HashSet(listOf(*Utils.splitTags(cursor.getString(tagsIndex))))
                NoteInfo(id, fields, tags)
            } catch (e: Exception) {
                null
            }
        }
    }

    private constructor(id: Long, fields: Array<String>, tags: Set<String>) {
        this.id = id
        this.fields = fields
        this.tags = tags
    }

    /**
     * Clone a NoteInfo object
     * @param parent the object to clone
     */
    public constructor(parent: NoteInfo) {
        id = parent.id
        fields = parent.fields.clone()
        tags = HashSet(parent.tags)
    }

    /** Note ID  */
    public fun getId(): Long {
        return id
    }

    /** The array of fields  */
    public fun getFields(): Array<String> {
        return fields
    }

    /** The set of tags  */
    public fun getTags(): Set<String> {
        return tags
    }

    /** The first field  */
    public fun getKey(): String {
        return getFields()[0]
    }
}
