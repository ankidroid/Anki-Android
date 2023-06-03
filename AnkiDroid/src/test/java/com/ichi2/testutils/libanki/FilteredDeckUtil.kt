/*
 *  Copyright (c) 2020 David Allison <davidallisongithub@gmail.com>
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
package com.ichi2.testutils.libanki

import com.ichi2.libanki.Collection
import com.ichi2.libanki.backend.exception.DeckRenameException

object FilteredDeckUtil {
    fun createFilteredDeck(col: Collection, name: String?, search: String): Long {
        val filteredDid: Long = try {
            col.decks.newDyn(col, name!!)
        } catch (filteredAncestor: DeckRenameException) {
            throw RuntimeException(filteredAncestor)
        }
        val conf = col.decks.confForDid(col, filteredDid)
        conf.getJSONArray("terms").getJSONArray(0).put(0, search)
        col.decks.save(col, conf)
        return filteredDid
    }
}
