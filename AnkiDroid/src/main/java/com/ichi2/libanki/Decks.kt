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

import anki.collection.OpChangesWithCount
import anki.collection.OpChangesWithId
import anki.decks.FilteredDeckForUpdate
import com.google.protobuf.kotlin.toByteStringUtf8
import com.ichi2.libanki.backend.BackendUtils
import com.ichi2.libanki.utils.*
import com.ichi2.utils.KotlinCleanup
import com.ichi2.utils.jsonObjectIterable
import net.ankiweb.rsdroid.RustCleanup
import net.ankiweb.rsdroid.exceptions.BackendDeckIsFilteredException
import net.ankiweb.rsdroid.exceptions.BackendNotFoundException
import org.json.JSONArray
import java.util.*

data class DeckNameId(val name: String, val id: DeckId)

// TODO: col was a weakref

class Decks(private val col: Collection) {
    /** "Add a deck with NAME. Reuse deck if already exists. Return id as int." */
    fun id(name: String): DeckId {
        val id = this.idForName(name)
        if (id != null) {
            return id
        }
        val deck = this.newDeckLegacy(false)
        deck.name = name
        addDeckLegacy(deck)
        return deck.id
    }

    private fun addDeckLegacy(deck: Deck): OpChangesWithId {
        val changes = col.backend.addDeckLegacy(
            json = BackendUtils.to_json_bytes(deck)
        )
        deck.id = changes.id
        return changes
    }

    fun removeDecks(deckIds: Iterable<Long>): OpChangesWithCount {
        return col.backend.removeDecks(dids = deckIds)
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

    fun idForName(name: String): DeckId? {
        return try {
            col.backend.getDeckIdByName(name)
        } catch (ex: BackendNotFoundException) {
            null
        }
    }

    fun get(did: DeckId): Deck? {
        return try {
            Deck(BackendUtils.from_json_bytes(col.backend.getDeckLegacy(did)))
        } catch (ex: BackendNotFoundException) {
            null
        }
    }

    private fun newDeckLegacy(filtered: Boolean): Deck {
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

    fun collapse(did: DeckId) {
        val deck = this.get(did) ?: return
        deck.collapsed = !deck.collapsed
        this.save(deck)
    }

    fun count(): Int {
        return len(this.allNamesAndIds())
    }

    /** Get deck with NAME, ignoring case. */
    fun byName(name: String): Deck? {
        val id = this.idForName(name)
        if (id != null) {
            return get(id)
        }
        return null
    }

    /**
     * Add or update an existing deck. Used for syncing and merging.
     * @throws BackendDeckIsFilteredException
     */
    fun save(g: Deck) {
        g.id = col.backend.addOrUpdateDeckLegacy(
            BackendUtils.toByteString(g),
            preserveUsnAndMtime = false
        )
    }

    /** Rename deck prefix to NAME if not exists. Updates children. */
    fun rename(g: Deck, newName: String) {
        g.name = newName
        this.save(g)
    }

    /* Deck configurations */

    /** A list of all deck config. */
    fun allConfig(): List<DeckConfig> {
        return BackendUtils.jsonToArray(col.backend.allDeckConfigLegacy())
            .jsonObjectIterable()
            .map { obj -> DeckConfig(obj) }
            .toList()
    }

    /** Falls back on default config if deck or config missing */
    fun confForDid(did: DeckId): DeckConfig {
        val conf = get(did)?.conf ?: 1
        return DeckConfig(BackendUtils.from_json_bytes(col.backend.getDeckConfigLegacy(conf)))
    }

    fun save(g: DeckConfig) {
        g.id = col.backend.addOrUpdateDeckConfigLegacy(g.toString().toByteStringUtf8())
    }

    private fun addConfig(
        name: String
    ): DeckConfig {
        val conf = DeckConfig(newDeckConfigLegacy())
        conf.name = name
        this.save(conf)
        return conf
    }

    private fun newDeckConfigLegacy(): DeckConfig {
        return DeckConfig(BackendUtils.from_json_bytes(col.backend.newDeckConfigLegacy()))
    }

    fun setConf(grp: Deck, id: DeckConfigId) {
        grp.conf = id
        this.save(grp)
    }

    /* Reverts to default if provided id missing */
    fun getConf(confId: DeckConfigId): DeckConfig =
        DeckConfig(BackendUtils.from_json_bytes(col.backend.getDeckConfigLegacy(confId)))

    fun confId(name: String): Long {
        return addConfig(name).id
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
        return this.get(this.selected()) ?: this.get(1)!!
    }

    /** Select a new branch. */
    fun select(did: DeckId) {
        col.backend.setCurrentDeck(did)
        val selectedDeckName = name(did)
        val childrenDids =
            allNamesAndIds(skipEmptyDefault = true, includeFiltered = false)
                .filter { it.name.startsWith("$selectedDeckName::") }
                .map { it.id }
        col.config.set(ACTIVE_DECKS, listOf(did) + childrenDids)
    }

    /*
     Dynamic decks
     */

    /** Return a new dynamic deck and set it as the current deck. */
    fun newDyn(name: String): DeckId {
        val deck = this.newDeckLegacy(true)
        deck.name = name
        addDeckLegacy(deck)
        this.select(deck.id)
        return deck.id
    }

    fun isDyn(did: DeckId): Boolean {
        return this.get(did)?.isFiltered == true
    }

    fun name(did: DeckId): String = get(did)?.name ?: "[no deck]"

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
        val deck = get(did) ?: return null
        return deck.getString("name") + DECK_SEPARATOR + subdeckName
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
        fun isValidDeckName(deckName: String): Boolean = deckName.trim { it <= ' ' }.isNotEmpty()
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
