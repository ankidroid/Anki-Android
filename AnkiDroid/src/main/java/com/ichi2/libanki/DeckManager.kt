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

import androidx.annotation.CheckResult
import androidx.annotation.VisibleForTesting
import com.ichi2.anki.exception.ConfirmModSchemaException
import com.ichi2.anki.exception.DeckRenameException
import com.ichi2.anki.exception.FilteredAncestor
import com.ichi2.utils.JSONObject
import net.ankiweb.rsdroid.RustCleanup
import java.util.*

abstract class DeckManager {

    /*
     * Registry save/load
     * ***********************************************************
     */

    abstract fun load(decks: String, dconf: String)
    fun save() = save(null)
    /** Can be called with either a deck or a deck configuration. */
    abstract fun save(g: JSONObject?)
    abstract fun flush()

    /*
     * Deck save/load
     * ***********************************************************
     */

    abstract fun id_for_name(name: String): Long?
    @Throws(FilteredAncestor::class)
    abstract fun id(name: String): Long
    /** Same as id, but rename ancestors if filtered to avoid failure */
    fun id_safe(name: String) = id_safe(name, Decks.DEFAULT_DECK)
    /** Same as id, but rename ancestors if filtered to avoid failure */
    abstract fun id_safe(name: String, type: String): Long

    /** Remove the deck. delete any cards inside and child decks. */
    fun rem(did: Long) = rem(did, true)
    /** Remove the deck. Delete child decks. If cardsToo, delete any cards inside. */
    fun rem(did: Long, cardsToo: Boolean = true) = rem(did, cardsToo, true)
    /** Remove the deck. If cardsToo, delete any cards inside. */
    abstract fun rem(did: Long, cardsToo: Boolean = true, childrenToo: Boolean = true)

    /** An unsorted list of all deck names. */
    fun allNames() = allNames(true)
    /** An unsorted list of all deck names. */
    abstract fun allNames(dyn: Boolean = true): List<String>
    /** A list of all decks. */
    abstract fun all(): List<Deck>
    abstract fun allIds(): Set<Long>

    abstract fun collapse(did: Long)

    /** Return the number of decks. */
    abstract fun count(): Int
    @CheckResult
    /** Obtains the deck from the DeckID, or default if the deck was not found */
    fun get(did: Long): Deck = get(did, true)!!
    /**
     * Obtains the deck from the DeckID
     * @param did The deck to obtain
     * @param _default Whether to return the default deck, or null if did is not found
     */
    @CheckResult
    abstract fun get(did: Long, _default: Boolean): Deck?

    /** Get deck with NAME, ignoring case */
    @CheckResult
    abstract fun byName(name: String): Deck?

    /** Add or update an existing deck. Used for syncing and merging. */
    abstract fun update(g: Deck)

    /** Rename deck prefix to NAME if not exists. Updates children. */
    @Throws(DeckRenameException::class)
    abstract fun rename(g: Deck, newName: String)

    /*
     * Deck configurations
     ************************************************************
     */

    /** * A list of all deck config. */
    abstract fun allConf(): ArrayList<DeckConfig>
    abstract fun confForDid(did: Long): DeckConfig
    abstract fun getConf(confId: Long): DeckConfig?
    abstract fun updateConf(g: DeckConfig)

    fun confId(name: String): Long = confId(name, Decks.DEFAULT_CONF)
    /** Create a new configuration and return id */
    abstract fun confId(name: String, cloneFrom: String): Long
    /**
     * Remove a configuration and update all decks using it.
     * @throws ConfirmModSchemaException
     */
    @Throws(ConfirmModSchemaException::class)
    abstract fun remConf(id: Long)
    abstract fun setConf(grp: Deck, id: Long)

    abstract fun didsForConf(conf: DeckConfig): List<Long>
    abstract fun restoreToDefault(conf: DeckConfig)

    /*
     * Deck utils
     * ***********************************************************
     */

    abstract fun name(did: Long): String
    fun cids(did: Long): Array<Long> = cids(did, false)
    abstract fun cids(did: Long, children: Boolean): Array<Long>
    abstract fun checkIntegrity()

    /*
     * Deck selection
     * ***********************************************************
     */
    /** The currently active dids. Make sure to copy before modifying. */
    abstract fun active(): LinkedList<Long>
    /** The currently selected did. */
    abstract fun selected(): Long
    abstract fun current(): Deck
    /** Select a new branch. */
    abstract fun select(did: Long)
    /**
     * All children of did as nodes of (key:name, value:id)
     *
     * TODO: There is likely no need for this collection to be a TreeMap. This method should not
     * need to sort on behalf of select().
     */
    abstract fun children(did: Long): TreeMap<String, Long>
    abstract fun childDids(did: Long, childMap: Decks.Node): List<Long>
    abstract fun childMap(): Decks.Node
    /** All parents of did. */
    abstract fun parents(did: Long): List<Deck>

    /*
     * Sync handling
     * ***********************************************************
     */

    abstract fun beforeUpload()

    /*
     * Dynamic decks
     * ***********************************************************
     */

    /** Return a new dynamic deck and set it as the current deck. */
    @Throws(FilteredAncestor::class)
    abstract fun newDyn(name: String): Long
    abstract fun isDyn(did: Long): Boolean

    /*
     * ***********************************************************
     * The methods below are not in LibAnki.
     * ***********************************************************
     */

    abstract fun getActualDescription(): String

    /** @return the fully qualified name of the subdeck, or null if unavailable */
    abstract fun getSubdeckName(did: Long, subdeckName: String?): String?

    /* Methods only visible for testing */

    @VisibleForTesting
    abstract fun allSortedNames(): List<String>
    /**
     *
     * @param name The name whose parents should exists
     * @return The name, with potentially change in capitalization and unicode normalization, so that the parent's name corresponds to an existing deck.
     * @throws FilteredAncestor if a parent is filtered
     */
    @VisibleForTesting
    @Throws(FilteredAncestor::class)
    protected abstract fun _ensureParents(name: String): String

    /**
     *
     * Similar as ensure parent, to use when the method can't fail and it's better to allow more change to ancestor's names.
     * @param name The name whose parents should exists
     * @return The name similar to input, changed as required, and as little as required, so that no ancestor is filtered and the parent's name is an existing deck.
     */
    @VisibleForTesting
    protected abstract fun _ensureParentsNotFiltered(name: String): String

    /*
     * Not in libAnki
     */

    /**
     * Return the same deck list from all() but sorted using a comparator that ensures the same
     * sorting order for decks as the desktop client.
     *
     * This method does not exist in the original python module but *must* be used for any user
     * interface components that display a deck list to ensure the ordering is consistent.
     */
    abstract fun allSorted(): List<Deck>

    @RustCleanup("potentially an extension function")
    fun allDynamicDeckIds(): Array<Long> {
        val ids = allIds()
        val validValues = ArrayList<Long>(ids.size)
        for (did in ids) {
            if (isDyn(did)) {
                validValues.add(did)
            }
        }
        return validValues.toTypedArray()
    }
}
