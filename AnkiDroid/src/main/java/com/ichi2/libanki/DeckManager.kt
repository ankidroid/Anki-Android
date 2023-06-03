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
import com.ichi2.libanki.backend.exception.DeckRenameException
import com.ichi2.utils.DeckComparator
import com.ichi2.utils.DeckNameComparator
import com.ichi2.utils.KotlinCleanup
import net.ankiweb.rsdroid.RustCleanup
import org.intellij.lang.annotations.Language
import java.util.*

abstract class DeckManager {

    /*
     * Registry save/load
     * ***********************************************************
     */

    abstract fun load(@Language("JSON") decks: String, dconf: String)

    @RustCleanup("Unused in V16")
    /** @throws DeckRenameException */
    abstract fun save(col: Collection)

    /** Can be called with either a deck or a deck configuration.
     * @throws DeckRenameException */
    abstract fun save(col: Collection, g: Deck)
    abstract fun save(col: Collection, g: DeckConfig)
    abstract fun flush(col: Collection)

    /*
     * Deck save/load
     * ***********************************************************
     */

    abstract fun id_for_name(col: Collection, name: String): Long?

    @Throws(DeckRenameException::class)
    abstract fun id(col: Collection, name: String): Long

    /** Same as id, but rename ancestors if filtered to avoid failure */
    fun id_safe(col: Collection, name: String) = id_safe(col, name, Decks.DEFAULT_DECK)

    /** Same as id, but rename ancestors if filtered to avoid failure */
    abstract fun id_safe(col: Collection, name: String, type: String): Long

    /** Remove the deck. delete any cards inside and child decks. */
    fun rem(col: Collection, did: DeckId) = rem(col, did, true)

    /** Remove the deck. Delete child decks. If cardsToo, delete any cards inside. */
    fun rem(col: Collection, did: DeckId, cardsToo: Boolean = true) = rem(col, did, cardsToo, true)

    /** Remove the deck. If cardsToo, delete any cards inside. */
    abstract fun rem(col: Collection, did: DeckId, cardsToo: Boolean = true, childrenToo: Boolean = true)

    /** An unsorted list of all deck names. */
    fun allNames(col: Collection) = allNames(col, true)

    /** An unsorted list of all deck names. */
    abstract fun allNames(col: Collection, dyn: Boolean = true): List<String>

    /** A list of all decks. */
    abstract fun all(col: Collection): List<Deck>
    abstract fun allIds(col: Collection): Set<Long>

    abstract fun collapse(col: Collection, did: DeckId)

    /** Return the number of decks. */
    @RustCleanup("This is a long in V16 - shouldn't make a difference, but needs investigation")
    abstract fun count(col: Collection): Int

    /** Obtains the deck from the DeckID, or default if the deck was not found */
    @CheckResult
    fun get(col: Collection, did: DeckId): Deck = get(col, did, true)!!

    /**
     * Obtains the deck from the DeckID
     * @param did The deck to obtain
     * @param _default Whether to return the default deck, or null if did is not found
     */
    @CheckResult
    abstract fun get(col: Collection, did: DeckId, _default: Boolean): Deck?

    /** Get deck with NAME, ignoring case */
    @CheckResult
    abstract fun byName(col: Collection, name: String): Deck?

    /**
     * Add or update an existing deck. Used for syncing and merging.
     * @throws DeckRenameException
     */
    abstract fun update(col: Collection, g: Deck)

    /** Rename deck prefix to NAME if not exists. Updates children. */
    @Throws(DeckRenameException::class)
    abstract fun rename(col: Collection, g: Deck, newName: String)

    /*
     * Deck configurations
     ************************************************************
     */

    /** * A list of all deck config. */
    abstract fun allConf(col: Collection): List<DeckConfig>
    abstract fun confForDid(col: Collection, did: DeckId): DeckConfig
    abstract fun getConf(col: Collection, confId: Long): DeckConfig?
    abstract fun updateConf(col: Collection, g: DeckConfig)

    fun confId(col: Collection, name: String): Long = confId(col, name, Decks.DEFAULT_CONF)

    /** Create a new configuration and return id */
    abstract fun confId(col: Collection, name: String, cloneFrom: String): Long

    /**
     * Remove a configuration and update all decks using it.
     * @throws ConfirmModSchemaException
     */
    @Throws(ConfirmModSchemaException::class)
    abstract fun remConf(col: Collection, id: Long)
    abstract fun setConf(col: Collection, grp: Deck, id: Long)

    abstract fun didsForConf(col: Collection, conf: DeckConfig): List<Long>
    abstract fun restoreToDefault(col: Collection, conf: DeckConfig)

    /*
     * Deck utils
     * ***********************************************************
     */

    fun name(col: Collection, did: DeckId): String = name(col, did, _default = false)
    abstract fun name(col: Collection, did: DeckId, _default: Boolean = false): String
    fun cids(col: Collection, did: DeckId): MutableList<Long> = cids(col, did, false)
    abstract fun cids(col: Collection, did: DeckId, children: Boolean): MutableList<Long>
    abstract fun checkIntegrity(col: Collection)

    /*
     * Deck selection
     * ***********************************************************
     */
    /** The currently active dids. Make sure to copy before modifying. */
    abstract fun active(col: Collection): LinkedList<Long>

    /** The currently selected did. */
    abstract fun selected(col: Collection): Long
    abstract fun current(col: Collection): Deck

    /** Select a new branch. */
    abstract fun select(col: Collection, did: DeckId)

    /**
     * All children of did as nodes of (key:name, value:id)
     *
     * TODO: There is likely no need for this collection to be a TreeMap. This method should not
     * need to sort on behalf of select().
     */
    abstract fun children(col: Collection, did: DeckId): TreeMap<String, Long>
    abstract fun childDids(did: DeckId, childMap: Decks.Node): List<Long>
    abstract fun childMap(col: Collection): Decks.Node

    /** All parents of did. */
    abstract fun parents(col: Collection, did: DeckId): List<Deck>

    /*
     * Sync handling
     * ***********************************************************
     */

    abstract fun beforeUpload(col: Collection)

    /*
     * Dynamic decks
     * ***********************************************************
     */

    /** Return a new dynamic deck and set it as the current deck. */
    @Throws(DeckRenameException::class)
    abstract fun newDyn(col: Collection, name: String): Long
    abstract fun isDyn(col: Collection, did: DeckId): Boolean

    /*
     * ***********************************************************
     * The methods below are not in LibAnki.
     * ***********************************************************
     */
    @KotlinCleanup("convert to extension method (possibly in servicelayer)")
    fun getActualDescription(col: Collection): String = current(col).optString("desc", "")

    /** @return the fully qualified name of the subdeck, or null if unavailable */
    fun getSubdeckName(col: Collection, did: DeckId, subdeckName: String?): String? {
        if (subdeckName.isNullOrEmpty()) {
            return null
        }
        val newName = subdeckName.replace("\"".toRegex(), "")
        if (newName.isEmpty()) {
            return null
        }
        val deck = get(col, did, false) ?: return null
        return deck.getString("name") + Decks.DECK_SEPARATOR + subdeckName
    }

    @RustCleanup("to be removed")
    abstract fun update_active(col: Collection)

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
    /** {@inheritDoc}  */
    fun allSorted(col: Collection): List<Deck> {
        val decks: List<Deck> = all(col)
        Collections.sort(decks, DeckComparator.INSTANCE)
        return decks
    }

    @VisibleForTesting
    @KotlinCleanup("potentially an extension function")
    fun allSortedNames(col: Collection): List<String> {
        val names = allNames(col)
        Collections.sort(names, DeckNameComparator.INSTANCE)
        return names
    }

    @KotlinCleanup("potentially an extension function")
    fun allDynamicDeckIds(col: Collection): Array<Long> {
        val ids = allIds(col)
        val validValues = ArrayList<Long>(ids.size)
        for (did in ids) {
            if (isDyn(col, did)) {
                validValues.add(did)
            }
        }
        return validValues.toTypedArray()
    }
}
