/*
 *  Copyright (c) 2024 David Allison <davidallisongithub@gmail.com>
 *
 *  This program is free software; you can redistribute it and/or modify it under
 *  the terms of the GNU General Public License as published by the Free Software
 *  Foundation; either version 3 of the License, or (at your option) any later
 *  version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY
 *  WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 *  PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along with
 *  this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.anki.browser

import android.os.Parcelable
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.libanki.CardId
import com.ichi2.anki.libanki.NoteId
import com.ichi2.anki.model.CardsOrNotes
import kotlinx.parcelize.Parcelize

/**
 * Either a [CardId] or a [NoteId]. The ID of a row inside the 'Browser' can be either.
 *
 * A [CardOrNoteId] should always be associated with [CardsOrNotes].
 * It is not included in the class as this class is primarily provided in a [BrowserRowCollection]
 * and storing the same value for every item in the list is a waste
 */
@Parcelize
@JvmInline
value class CardOrNoteId(
    val cardOrNoteId: Long,
) : Parcelable {
    override fun toString(): String = cardOrNoteId.toString()

    // TODO: We use this for 'Edit Note' or 'Card Info'. We should reconsider whether we ever want
    //  to move from NoteId to CardId. Our move to 'Notes' mode wasn't well thought-through
    suspend fun toCardId(type: CardsOrNotes): CardId =
        when (type) {
            CardsOrNotes.CARDS -> cardOrNoteId
            CardsOrNotes.NOTES -> withCol { cardIdsOfNote(cardOrNoteId).first() }
        }
}
