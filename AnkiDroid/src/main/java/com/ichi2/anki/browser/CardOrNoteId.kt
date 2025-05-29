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

import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.libanki.CardId
import com.ichi2.anki.model.CardsOrNotes

@JvmInline
value class CardOrNoteId(
    val cardOrNoteId: Long,
) {
    override fun toString(): String = cardOrNoteId.toString()

    // TODO: We use this for 'Edit Note' or 'Card Info'. We should reconsider whether we ever want
    //  to move from NoteId to CardId. Our move to 'Notes' mode wasn't well thought-through
    suspend fun toCardId(type: CardsOrNotes): CardId =
        when (type) {
            CardsOrNotes.CARDS -> cardOrNoteId as CardId
            CardsOrNotes.NOTES -> withCol { cardIdsOfNote(cardOrNoteId).first() }
        }
}
