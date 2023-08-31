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
 */

package com.ichi2.libanki

import com.ichi2.libanki.backend.model.TagUsnTuple
import net.ankiweb.rsdroid.RustCleanup

/**
 * Manages the tag cache and tags for notes.
 *
 * This is the public API surface for tags, to unify [Tags] and [TagsV16]
 */
@RustCleanup("remove docs: this exists to unify Tags.java and TagsV16")
abstract class TagManager {

    /*
     * Registry save/load
     * ***********************************************************
     */
    @RustCleanup("Tags.java only")
    abstract fun load(json: String)

    @RustCleanup("Tags.java only")
    abstract fun flush()

    /*
     * Registering and fetching tags
     * ***********************************************************
     */

    /** Given a list of tags, add any missing ones to tag registry. */
    fun register(tags: Iterable<String>) = register(tags, null)

    /** Given a list of tags, add any missing ones to tag registry. */
    fun register(tags: Iterable<String>, usn: Int? = null) = register(tags, usn, false)

    /** Given a list of tags, add any missing ones to tag registry.
     * @param clear_first Whether to clear the tags in the database before registering the provided tags
     * */
    abstract fun register(tags: Iterable<String>, usn: Int? = null, clear_first: Boolean = false)
    abstract fun all(): List<String>

    /** Add any missing tags from notes to the tags list. The old list is cleared first */
    fun registerNotes() = registerNotes(null)

    /**
     * Add any missing tags from notes to the tags list.
     * @param nids The old list is cleared first if this is null
     */
    abstract fun registerNotes(nids: kotlin.collections.Collection<Long>? = null)

    abstract fun allItems(): Iterable<TagUsnTuple>

    @RustCleanup("Tags.java only")
    abstract fun save()

    /**
     * byDeck returns the tags of the cards in the deck
     * @param did the deck id
     * @param children whether to include the deck's children
     * @return a list of the tags
     */
    abstract fun byDeck(did: DeckId, children: Boolean = false): List<String>

    /*
    * Bulk addition/removal from notes
    * ***********************************************************
    */

    /* Legacy signature, currently only used by unit tests. New code in TagsV16
      takes two args. */
    abstract fun bulkAdd(ids: List<Long>, tags: String, add: Boolean = true)

    /*
     * String-based utilities
     * ***********************************************************
     */

    /** Parse a string and return a list of tags. */
    abstract fun split(tags: String): MutableList<String>

    /** Join tags into a single string, with leading and trailing spaces. */
    abstract fun join(tags: kotlin.collections.Collection<String>): String

    /** Delete tags if they exist. */
    abstract fun remFromStr(deltags: String, tags: String): String

    /*
     * List-based utilities
     * ***********************************************************
     */

    /** Strip duplicates, adjust case to match existing tags, and sort. */
    @RustCleanup("List, not Collection")
    abstract fun canonify(tagList: List<String>): java.util.AbstractSet<String>

    /** @return True if TAG is in TAGS. Ignore case. */
    abstract fun inList(tag: String, tags: Iterable<String>): Boolean

    /*
     * Sync handling
     * ***********************************************************
     */

    @RustCleanup("Tags.java only")
    abstract fun beforeUpload()

    /*
     * ***********************************************************
     * The methods below are not in LibAnki.
     * ***********************************************************
     */

    /** Add a tag to the collection. We use this method instead of exposing mTags publicly.*/
    abstract fun add(tag: String, usn: Int?)

    /** Whether any tags have a usn of -1 */
    @RustCleanup("not optimised")
    open fun minusOneValue(): Boolean {
        TODO("obsolete when moving to backend for sync")
//        allItems().any { it.usn == -1 }
    }
}
