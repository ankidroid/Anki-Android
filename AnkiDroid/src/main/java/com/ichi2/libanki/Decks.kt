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
 *  This file incorporates work covered by the following copyright and
 *  permission notice:
 *
 *  https://github.com/ankitects/anki/blob/c4db4bd2913234d077aa289543da6405a62f53dc/pylib/anki/decks.py
 *
 *  # Copyright: Ankitects Pty Ltd and contributors
 *  # License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
 *
 */

package com.ichi2.libanki

import androidx.annotation.CheckResult
import anki.collection.OpChangesWithCount
import anki.collection.OpChangesWithId
import anki.decks.FilteredDeckForUpdate
import com.google.protobuf.kotlin.toByteStringUtf8
import com.ichi2.libanki.backend.BackendUtils
import com.ichi2.libanki.backend.exception.DeckRenameException
import com.ichi2.libanki.utils.*
import com.ichi2.utils.KotlinCleanup
import com.ichi2.utils.jsonObjectIterable
import java8.util.Optional
import net.ankiweb.rsdroid.RustCleanup
import net.ankiweb.rsdroid.exceptions.BackendDeckIsFilteredException
import net.ankiweb.rsdroid.exceptions.BackendNotFoundException
import org.json.JSONArray
import org.json.JSONObject
import java.util.*

data class DeckNameId(val name: String, val id: DeckId)

// TODO: col was a weakref

/**
 * Untested WIP implementation of Decks for Schema V16.
 *
 * It's planned to consolidate interfaces between this and decks.py
 *
 * Afterwards, we can finish up the implementations, run our tests, and use this with a V16
 * collection, using decks as a separate table
 */
class Decks(private val col: Collection) {
    fun save(g: DeckConfig) {
        // deck conf?
        this.update_config(g)
    }

    fun save(g: Deck) {
        this.update(g)
    }

    /* Deck save/load */
    @RustCleanup("only for java interface: newDyn was used for filtered decks")
    fun id(name: String): DeckId {
        // use newDyn for now
        return id(name, true, 0).get()
    }

    /** "Add a deck with NAME. Reuse deck if already exists. Return id as int." */
    @Throws(DeckRenameException::class)
    fun id(name: String, create: Boolean = true, type: Int = 0): Optional<DeckId> {
        val id = this.id_for_name(name)
        if (id != null) {
            return Optional.of(id)
        } else if (!create) {
            return Optional.empty()
        }

        val deck = this.new_deck_legacy(type != 0)
        deck.name = name
        addDeckLegacy(deck)
        return Optional.of(deck.id)
    }

    fun addDeckLegacy(deck: Deck): OpChangesWithId {
        val changes = col.backend.addDeckLegacy(
            json = BackendUtils.to_json_bytes(deck)
        )
        deck.id = changes.id
        return changes
    }

    /** Remove the deck. If cardsToo, delete any cards inside. */
    fun rem(did: DeckId, cardsToo: Boolean, childrenToo: Boolean) {
        assert(cardsToo && childrenToo)
        col.backend.removeDecks(listOf(did))
    }

    fun removeDecks(deckIds: Iterable<Long>): OpChangesWithCount {
        return col.backend.removeDecks(dids = deckIds)
    }

    @Suppress("deprecation")
    fun allNames(dyn: Boolean): List<String> {
        return allNames(dyn = dyn, forcedefault = true)
    }

    /** A sorted sequence of deck names and IDs. */
    fun allNamesAndIds(
        skipEmptyDefault: Boolean = false,
        includeFiltered: Boolean = true
    ): List<DeckNameId> {
        return col.backend.getDeckNames(skipEmptyDefault = skipEmptyDefault, includeFiltered = includeFiltered).map { entry ->
            DeckNameId(entry.name, entry.id)
        }
    }

    fun id_for_name(name: String): DeckId? {
        try {
            return col.backend.getDeckIdByName(name)
        } catch (ex: BackendNotFoundException) {
            return null
        }
    }

    fun get_legacy(did: DeckId): Deck? {
        return get_deck_legacy(did)
    }

    private fun get_deck_legacy(did: DeckId): Deck? {
        return try {
            Deck(BackendUtils.from_json_bytes(col.backend.getDeckLegacy(did)))
        } catch (ex: BackendNotFoundException) {
            null
        }
    }

    private fun <T> JSONObject.objectIterable(f: (JSONObject) -> T) = sequence {
        keys().forEach { k -> yield(f(getJSONObject(k))) }
    }

    fun new_deck_legacy(filtered: Boolean): Deck {
        val deck = BackendUtils.from_json_bytes(col.backend.newDeckLegacy(filtered))
        return Deck(
            if (filtered) {
                // until migrating to the dedicated method for creating filtered decks,
                // we need to ensure the default config matches legacy expectations
                val terms = deck.getJSONArray("terms").getJSONArray(0)
                terms.put(0, "")
                terms.put(2, 0)
                deck.put("terms", JSONArray(listOf(terms)))
                deck.put("browserCollapsed", false)
                deck.put("collapsed", false)
                deck
            } else {
                deck
            }
        )
    }

    @Deprecated("decks.allIds() is deprecated, use .all_names_and_ids()")
    fun allIds(): Set<DeckId> {
        return this.allNamesAndIds().map { x -> x.id }.toSet()
    }

    @Deprecated("decks.allNames() is deprecated, use .all_names_and_ids()")
    fun allNames(dyn: Boolean = true, forcedefault: Boolean = true): MutableList<String> {
        return this.allNamesAndIds(
            skipEmptyDefault = !forcedefault,
            includeFiltered = dyn
        ).map { x ->
            x.name
        }.toMutableList()
    }

    fun collapse(did: DeckId) {
        val deck = this.get(did)
        deck.collapsed = !deck.collapsed
        this.save(deck)
    }

    fun count(): Int {
        return len(this.allNamesAndIds())
    }

    fun get(did: DeckId, default: Boolean): Deck? {
        val deck = this.get_legacy(did)
        return when {
            deck != null -> deck
            default -> this.get_legacy(1)
            else -> null
        }
    }

    /** Get deck with NAME, ignoring case. */
    fun byName(name: String): Deck? {
        val id = this.id_for_name(name)
        if (id != null) {
            return this.get_legacy(id)
        }
        return null
    }

    /** Add or update an existing deck. Used for syncing and merging. */
    fun update(g: Deck) {
        g.set(
            "id",
            try {
                col.backend.addOrUpdateDeckLegacy(
                    BackendUtils.toByteString(g),
                    preserveUsnAndMtime = false
                ).toString()
            } catch (ex: BackendDeckIsFilteredException) {
                throw DeckRenameException.filteredAncestor(g.name, "")
            }
        )
    }

    /** Rename deck prefix to NAME if not exists. Updates children. */
    fun rename(g: Deck, newName: String) {
        g.name = newName
        this.update(g)
    }

    /* Deck configurations */

    /** A list of all deck config. */
    fun allConfig(): List<DeckConfig> {
        return BackendUtils.jsonToArray(col.backend.allDeckConfigLegacy())
            .jsonObjectIterable()
            .map { obj -> DeckConfig(obj) }
            .toList()
    }

    fun confForDid(did: DeckId): DeckConfig {
        val deck = get(did)
        return DeckConfig(BackendUtils.from_json_bytes(col.backend.getDeckConfigLegacy(deck.conf)))
    }

    fun update_config(conf: DeckConfig) {
        conf.id = col.backend.addOrUpdateDeckConfigLegacy(conf.toString().toByteStringUtf8())
    }

    fun add_config(
        name: String
    ): DeckConfig {
        val conf = DeckConfig(newDeckConfigLegacy())
        conf.name = name
        this.update_config(conf)
        return conf
    }

    private fun newDeckConfigLegacy(): DeckConfig {
        return DeckConfig(BackendUtils.from_json_bytes(col.backend.newDeckConfigLegacy()))
    }

    fun add_config_returning_id(
        name: String
    ): DeckConfigId = this.add_config(name).id

    fun setConf(grp: Deck, id: DeckConfigId) {
        grp.conf = id
        this.save(grp)
    }

    /* Reverts to default if provided id missing */
    fun getConf(confId: DeckConfigId): DeckConfig =
        DeckConfig(BackendUtils.from_json_bytes(col.backend.getDeckConfigLegacy(confId)))

    fun confId(name: String): Long {
        return add_config_returning_id(name)
    }
    /* Deck utils */

    fun name(did: DeckId, default: Boolean): String {
        val deck = this.get(did, default = default)
        if (deck !== null) {
            return deck.name
        }
        // TODO: Needs i18n, but the Java did the same, appears to be dead code
        return "[no deck]"
    }

    @RustCleanup("needs testing")
    fun checkIntegrity() {
        // I believe this is now handled in libAnki
    }

    /* Deck selection */

    /** The currently active dids. */
    @RustCleanup("Probably better as a queue")
    fun active(): LinkedList<DeckId> {
        val activeDecks = col.config.get<List<DeckId>>(ACTIVE_DECKS) ?: listOf()
        val result = LinkedList<Long>()
        result.addAll(activeDecks.asIterable())
        return result
    }

    /** The currently selected did. */
    fun selected(): DeckId {
        return this.col.backend.getCurrentDeck().id
    }

    fun current(): Deck {
        return this.get(this.selected())
    }

    /** Select a new branch. */
    fun select(did: DeckId) {
        col.backend.setCurrentDeck(did)
    }

    /*
     Dynamic decks
     */

    /** Return a new dynamic deck and set it as the current deck. */
    fun newDyn(name: String): DeckId {
        val did = this.id(name, type = 1).get()
        this.select(did)
        return did
    }

    fun isDyn(did: DeckId): Boolean {
        return this.get(did).isFiltered
    }

    /** Remove the deck. delete any cards inside and child decks. */
    fun rem(did: DeckId) = rem(did, true)

    /** Remove the deck. Delete child decks. If cardsToo, delete any cards inside. */
    fun rem(did: DeckId, cardsToo: Boolean = true) = rem(did, cardsToo, true)

    /** An unsorted list of all deck names. */
    fun allNames() = allNames(true)

    /** Obtains the deck from the DeckID, or default if the deck was not found */
    @CheckResult
    fun get(did: DeckId): Deck = get(did, true)!!

    fun name(did: DeckId): String = name(did, default = false)

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

    /*
     * Not in libAnki
     */

    companion object {
        /* Parents/children */

        fun path(name: String): List<String> {
            return name.split("::")
        }

        fun basename(name: String): String {
            return path(name).last()
        }

        /** Invalid id, represents an id on an unfound deck  */
        const val NOT_FOUND_DECK_ID = -1L

        /** Configuration saving the current deck  */
        const val CURRENT_DECK = "curDeck"

        /** Configuration saving the set of active decks (i.e. current decks and its descendants)  */
        const val ACTIVE_DECKS = "activeDecks"

        // not in libAnki
        const val DECK_SEPARATOR = "::"

    /*
    * ***********************************************************
    * The methods below are not in LibAnki.
    * ***********************************************************
    */
        @KotlinCleanup("nullability")
        fun isValidDeckName(deckName: String?): Boolean {
            return deckName != null && !deckName.trim { it <= ' ' }.isEmpty()
        }

        fun isDynamic(col: Collection, deckId: Long): Boolean {
            return isDynamic(col.decks.get(deckId))
        }

        fun isDynamic(deck: Deck): Boolean {
            return deck.isFiltered
        }
    }
}

// These take and return bytes that the frontend TypeScript code will encode/decode.
fun Collection.getDeckNamesRaw(input: ByteArray): ByteArray {
    return backend.getDeckNamesRaw(input)
}

/**
 * Gets the filtered deck with given [did]
 * or creates a new one if [did] = 0
 */
fun Collection.getOrCreateFilteredDeck(did: DeckId): FilteredDeckForUpdate {
    return backend.getOrCreateFilteredDeck(did = did)
}
