/****************************************************************************************
 * Copyright (c) 2011 Norbert Nagold <norbert.nagold@gmail.com>                         *
 * Copyright (c) 2012 Kostas Spyropoulos <inigo.aldana@gmail.com>                       *
 * Copyright (c) 2014 Houssam Salem <houssam.salem.au@gmail.com>                        *
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
package com.ichi2.libanki

import androidx.annotation.WorkerThread
import anki.collection.OpChangesWithCount
import com.ichi2.libanki.utils.join
import com.ichi2.libanki.utils.list
import com.ichi2.libanki.utils.set
import net.ankiweb.rsdroid.RustCleanup
import java.util.*

/**
 * Anki maintains a cache of used tags so it can quickly present a list of tags
 * for autocomplete and in the browser. For efficiency, deletions are not
 * tracked, so unused tags can only be removed from the list with a DB check.
 *
 * This module manages the tag cache and tags for notes.
 *
 */
@WorkerThread
class Tags(private val col: Collection) {
    /** all tags */
    fun all(): List<String> = col.backend.allTags()

    @RustCleanup("remove after migrating to backend custom study code")
    fun byDeck(did: DeckId, children: Boolean): List<String> {
        val basequery = "select n.tags from cards c, notes n WHERE c.nid = n.id"
        val query: String
        val res: List<String>
        if (!children) {
            query = basequery + " AND c.did=?"
            res = col.db.queryStringList(query, did)
            return list(set(split(" ".join(res))))
        }
        val dids = mutableListOf(did)
        for ((_, id) in col.decks.children(did)) {
            dids.add(id)
        }
        query = basequery + " AND c.did IN " + Utils.ids2str(dids)
        res = col.db.queryStringList(query)
        return list(set(split(" ".join(res))))
    }

    /* Legacy signature, used by unit tests. */
    fun bulkAdd(ids: List<Long>, tags: String, add: Boolean) {
        if (add) {
            bulkAdd(ids, tags)
        } else {
            bulkRemove(ids, tags)
        }
    }

    /** Add space-separate tags to provided notes. */
    fun bulkAdd(noteIds: List<Long>, tags: String): OpChangesWithCount {
        return col.backend.addNoteTags(noteIds = noteIds, tags = tags)
    }

    /* Remove space-separated tags from provided notes. */
    fun bulkRemove(
        noteIds: List<Long>,
        tags: String
    ): OpChangesWithCount {
        return col.backend.removeNoteTags(
            noteIds = noteIds,
            tags = tags
        )
    }

    /*
     * String-based utilities
     * ***********************************************************
     */

    /** Parse a string and return a list of tags. */
    fun split(tags: String): MutableList<String> {
        return tags.replace('\u3000', ' ')
            .split("\\s".toRegex())
            .filter { it.isNotEmpty() }
            .toMutableList()
    }

    /** Join tags into a single string, with leading and trailing spaces. */
    fun join(tags: kotlin.collections.Collection<String>): String {
        if (tags.isEmpty()) {
            return ""
        }
        return " ${" ".join(tags)} "
    }

    /*
     * List-based utilities
     * ***********************************************************
     */
    /** {@inheritDoc}  */

    // this is now a no-op - the tags are canonified when the note is saved
    fun canonify(tagList: List<String>): java.util.AbstractSet<String> {
        // libAnki difference: tagList was returned directly
        return HashSet(tagList)
    }

    /** True if TAG is in TAGS. Ignore case.*/
    fun inList(tag: String, tags: Iterable<String>): Boolean {
        return tags.map { it.lowercase() }.contains(tag.lowercase())
    }
}
