/*
 *  Copyright (c) 2021 David Allison <davidallisongithub@gmail.com>
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
 *
 * * This file incorporates code under the following license
 * https://github.com/ankitects/anki/blob/ef5c38dbc619be4e45315b8ff49f0f7aa2433efa/pylib/anki/tags.py
 *
 *    # Copyright: Ankitects Pty Ltd and contributors
 *    # License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
 *
 *
 */

package com.ichi2.libanki

import com.ichi2.libanki.Utils.ids2str
import com.ichi2.libanki.backend.TagsBackend
import com.ichi2.libanki.backend.model.TagUsnTuple
import com.ichi2.libanki.utils.join
import com.ichi2.libanki.utils.list
import com.ichi2.libanki.utils.set
import java.util.regex.Pattern

/**
 * Anki maintains a cache of used tags so it can quickly present a list of tags
 * for autocomplete and in the browser. For efficiency, deletions are not
 * tracked, so unused tags can only be removed from the list with a DB check.
 * This module manages the tag cache and tags for notes.
 */
class TagsV16(val col: Collection, private val backend: TagsBackend) : TagManager() {

    /** all tags */
    override fun all(): List<String> = backend.all_tags().map { it.tag }

    /** List of (tag, usn) */
    override fun allItems(): List<TagUsnTuple> = backend.all_tags()

    /*
    # Registering and fetching tags
    #############################################################
    */

    override fun register(
        tags: Iterable<String>,
        usn: Int?,
        clear_first: Boolean
    ) {

        val preserve_usn: Boolean
        val usn_: Int
        if (usn == null) {
            preserve_usn = false
            usn_ = 0
        } else {
            usn_ = usn
            preserve_usn = true
        }

        backend.register_tags(
            tags = " ".join(tags), preserve_usn = preserve_usn, usn = usn_, clear_first = clear_first
        )
    }

    /** Add any missing tags from notes to the tags list. */
    override fun registerNotes(nids: kotlin.collections.Collection<Long>?) {

        // when called without an argument, the old list is cleared first.
        val clear: Boolean
        val lim: String
        if (nids != null) {
            lim = " where id in " + ids2str(nids)
            clear = false
        } else {
            lim = ""
            clear = true
        }
        register(
            set(
                split(
                    " ".join(col.db.queryStringList("select distinct tags from notes" + lim))
                )
            ),
            clear_first = clear,
        )
    }

    override fun byDeck(did: Long, children: Boolean): List<String> {
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
        query = basequery + " AND c.did IN " + ids2str(dids)
        res = col.db.queryStringList(query)
        return list(set(split(" ".join(res))))
    }

    /*
    # Bulk addition/removal from notes
    #############################################################
     */

    /** Add space-separate tags to provided notes,
     * @return changed count. */
    fun bulk_add(nids: List<Long>, tags: String): Int {
        return backend.add_note_tags(nids = nids, tags = tags)
    }

    /** Replace space-separated tags, returning changed count.
     * Tags replaced with an empty string will be removed.
     * @return changed count.
     */
    fun bulk_update(
        nids: List<Long>,
        tags: String,
        replacement: String,
        regex: Boolean
    ): Int {
        return backend.update_note_tags(
            nids = nids, tags = tags, replacement = replacement, regex = regex
        )
    }

    // legacy routines

    /**  Add tags in bulk. TAGS is space-separated. */
    override fun bulkAdd(ids: List<Long>, tags: String, add: Boolean) {
        if (add) {
            bulk_add(ids, tags)
        } else {
            bulk_update(ids, tags, "", false)
        }
    }

    /*
    # String-based utilities
    ##########################################################################
     */

    /** Parse a string and return a list of tags. */
    override fun split(tags: String): MutableList<String> {
        return tags.replace('\u3000', ' ')
            .split("\\s".toRegex())
            .filter { it.isNotEmpty() }
            .toMutableList()
    }

    /** Join tags into a single string, with leading and trailing spaces. */
    override fun join(tags: kotlin.collections.Collection<String>): String {
        if (tags.isEmpty()) {
            return ""
        }
        return " ${" ".join(tags)} "
    }

    /** Add tags if they don't exist, and canonify. */
    fun addToStr(addtags: String, tags: String): String {

        val currentTags = split(tags)
        for (tag in split(addtags)) {
            if (!inList(tag, currentTags)) {
                currentTags.add(tag)
            }
        }
        return join(canonify(currentTags))
    }

    /** Delete tags if they exist. */
    override fun remFromStr(deltags: String, tags: String): String {
        fun wildcard(search: String, str: String): Boolean {
            // TODO: needs testing
            val escaped = Pattern.quote(search).replace("\\*", ".*")
            val pattern = Pattern.compile("^" + escaped + "$", Pattern.CASE_INSENSITIVE)
            return pattern.matcher(str).find()
        }

        val currentTags = split(tags)
        for (tag in split(deltags)) {
            // find tags, ignoring case
            val remove = mutableListOf<String>()
            for (tx in currentTags) {
                if ((tag.lowercase() == tx.lowercase()) or wildcard(tag, tx)) {
                    remove.add(tx)
                }
            }
            // remove them
            for (r in remove) {
                currentTags.remove(r)
            }
        }
        return join(currentTags)
    }

    /*
    # List-based utilities
    ##########################################################################
     */

    // this is now a no-op - the tags are canonified when the note is saved
    override fun canonify(tagList: List<String>): java.util.AbstractSet<String> {
        // libAnki difference: tagList was returned directly
        return HashSet(tagList)
    }

    /** True if TAG is in TAGS. Ignore case.*/
    override fun inList(tag: String, tags: Iterable<String>): Boolean {
        return tags.map { it.lowercase() }.contains(tag.lowercase())
    }

    /*
     * Not in libAnki
     */

    override fun add(tag: String, usn: Int?) {
        register(listOf(tag), usn)
    }

    /*
     * Interface compatibility - to be removed
     */

    override fun load(json: String) {
        // intentionally left blank
    }

    override fun flush() {
        // intentionally left blank
    }

    override fun save() {
        // intentionally left blank//
    }

    override fun beforeUpload() {
        // intentionally left blank//
    }
}
