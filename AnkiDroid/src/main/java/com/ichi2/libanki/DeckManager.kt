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
import androidx.annotation.WorkerThread
import com.ichi2.anki.exception.ConfirmModSchemaException
import com.ichi2.libanki.backend.exception.DeckRenameException
import com.ichi2.utils.DeckComparator
import com.ichi2.utils.DeckNameComparator
import com.ichi2.utils.KotlinCleanup
import net.ankiweb.rsdroid.RustCleanup
import org.intellij.lang.annotations.Language
import java.util.*

@WorkerThread
abstract class DeckManager {

    /*
     * Registry save/load
     * ***********************************************************
     */

    abstract fun load(@Language("JSON") decks: String, dconf: String)

    @RustCleanup("Unused in V16")
    /** @throws DeckRenameException */
    abstract fun save()

    /** Can be called with either a deck or a deck configuration.
     * @throws DeckRenameException */
    abstract fun save(g: Deck)
    abstract fun save(g: DeckConfig)
    abstract fun flush()

    /*
     * Deck save/load
     * ***********************************************************
     */

    abstract fun id_for_name(name: String): Long?

    @Throws(DeckRenameException::class)
    abstract fun id(name: String): Long

    /** Same as id, but rename ancestors if filtered to avoid failure */
    fun id_safe(name: String) = id_safe(name, Decks.DEFAULT_DECK)

    /** Same as id, but rename ancestors if filtered to avoid failure */
    abstract fun id_safe(name: String, type: String): Long

    /** Remove the deck. delete any cards inside and child decks. */
    fun rem(did: DeckId) = rem(did, true)

    /** Remove the deck. Delete child decks. If cardsToo, delete any cards inside. */
    fun rem(did: DeckId, cardsToo: Boolean = true) = rem(did, cardsToo, true)

    /** Remove the deck. If cardsToo, delete any cards inside. */
    abstract fun rem(did: DeckId, cardsToo: Boolean = true, childrenToo: Boolean = true)

    /** An unsorted list of all deck names. */
    fun allNames() = allNames(true)

    /** An unsorted list of all deck names. */
    abstract fun allNames(dyn: Boolean = true): List<String>

    /** A list of all decks. */
    abstract fun all(): List<Deck>
    abstract fun allIds(): Set<Long>

    abstract fun collapse(did: DeckId)

    /** Return the number of decks. */
    @RustCleanup("This is a long in V16 - shouldn't make a difference, but needs investigation")
    abstract fun count(): Int

    /** Obtains the deck from the DeckID, or default if the deck was not found */
    @CheckResult
    fun get(did: DeckId): Deck = get(did, true)!!

    /**
     * Obtains the deck from the DeckID
     * @param did The deck to obtain
     * @param _default Whether to return the default deck, or null if did is not found
     */
    @CheckResult
    abstract fun get(did: DeckId, _default: Boolean): Deck?

    /** Get deck with NAME, ignoring case */
    @CheckResult
    abstract fun byName(name: String): Deck?

    /**
     * Add or update an existing deck. Used for syncing and merging.
     * @throws DeckRenameException
     */
    abstract fun update(g: Deck)

    /** Rename deck prefix to NAME if not exists. Updates children. */
    @Throws(DeckRenameException::class)
    abstract fun rename(g: Deck, newName: String)

    /*
     * Deck configurations
     ************************************************************
     */

    /** * A list of all deck config. */
    abstract fun allConf(): List<DeckConfig>
    abstract fun confForDid(did: DeckId): DeckConfig
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

    fun name(did: DeckId): String = name(did, _default = false)
    abstract fun name(did: DeckId, _default: Boolean = false): String
    fun cids(did: DeckId): MutableList<Long> = cids(did, false)
    abstract fun cids(did: DeckId, children: Boolean): MutableList<Long>
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
    abstract fun select(did: DeckId)

    /**
     * All children of did as nodes of (key:name, value:id)
     *
     * TODO: There is likely no need for this collection to be a TreeMap. This method should not
     * need to sort on behalf of select().
     */
    abstract fun children(did: DeckId): TreeMap<String, Long>
    abstract fun childDids(did: DeckId, childMap: Decks.Node): List<Long>
    abstract fun childMap(): Decks.Node

    /** All parents of did. */
    abstract fun parents(did: DeckId): List<Deck>

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
    @Throws(DeckRenameException::class)
    abstract fun newDyn(name: String): Long
    abstract fun isDyn(did: DeckId): Boolean

    /*
     * ***********************************************************
     * The methods below are not in LibAnki.
     * ***********************************************************
     */
    @KotlinCleanup("convert to extension method (possibly in servicelayer)")
    fun getActualDescription(): String = current().optString("desc", "")

    /** @return the fully qualified name of the subdeck, or null if unavailable */
    fun getSubdeckName(did: DeckId, subdeckName: String?): String? {
        if (subdeckName.isNullOrEmpty()) {
            return null
        }
        val newName = subdeckName.replace("\"".toRegex(), "")
        if (newName.isEmpty()) {
            return null
        }
        val deck = get(did, false) ?: return null
        return deck.getString("name") + Decks.DECK_SEPARATOR + subdeckName
    }

    @RustCleanup("to be removed")
    abstract fun update_active()

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
    fun allSorted(): List<Deck> {
        val decks: List<Deck> = all()
        Collections.sort(decks, DeckComparator.INSTANCE)
        return decks
    }

    @VisibleForTesting
    @KotlinCleanup("potentially an extension function")
    fun allSortedNames(): List<String> {
        val names = allNames()
        Collections.sort(names, DeckNameComparator.INSTANCE)
        return names
    }

    @KotlinCleanup("potentially an extension function")
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
