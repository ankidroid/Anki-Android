/*
 *  Copyright (c) 2022 Akshit Sinha <akshitsinha3@gmail.com>
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

package com.ichi2.anki.servicelayer

import com.ichi2.anki.CardUtils

object CardService {
    /**
     * get unique note ids from a list of card ids
     * @param selectedCardIds list of card ids
     * can do better with performance here
     * TODO: blocks the UI, should be fixed
     */
    fun selectedNoteIds(
        selectedCardIds: List<Long>,
        col: com.ichi2.libanki.Collection,
    ) = CardUtils.getNotes(
        selectedCardIds.map { col.getCard(it) },
    ).map { it.id }
}
