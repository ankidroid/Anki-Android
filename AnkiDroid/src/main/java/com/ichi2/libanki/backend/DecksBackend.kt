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

@file:Suppress("NonPublicNonStaticFieldName", "FunctionName")

package com.ichi2.libanki.backend

import com.google.protobuf.ByteString
import com.ichi2.libanki.Deck
import com.ichi2.libanki.DeckConfigV16
import com.ichi2.libanki.DeckV16
import com.ichi2.libanki.Decks
import com.ichi2.libanki.backend.BackendUtils.from_json_bytes
import com.ichi2.libanki.backend.BackendUtils.jsonToArray
import com.ichi2.libanki.backend.BackendUtils.toByteString
import com.ichi2.libanki.backend.exception.DeckRenameException
import com.ichi2.utils.JSONObject
import java8.util.Optional
import net.ankiweb.rsdroid.BackendV1
import net.ankiweb.rsdroid.database.NotImplementedException
import net.ankiweb.rsdroid.exceptions.BackendDeckIsFilteredException
import net.ankiweb.rsdroid.exceptions.BackendNotFoundException

private typealias did = Long
private typealias dcid = Long

data class DeckNameId(val name: String, val id: did)

data class DeckTreeNode(
    val deck_id: Long,
    val name: String,
    val children: List<DeckTreeNode>,
    val level: UInt,
    val collapsed: Boolean,
    val review_count: UInt,
    val learn_count: UInt,
    val new_count: UInt,
    val filtered: Boolean
)

/** Anti-corruption layer, removing the dependency on protobuf types from libAnki code */
interface DecksBackend {
    fun get_config(conf_id: dcid): Optional<DeckConfigV16>
    fun update_config(conf: DeckConfigV16, preserve_usn: Boolean): dcid
    fun new_deck_config_legacy(): DeckConfigV16
    fun all_config(): List<DeckConfigV16.Config>
    fun add_or_update_deck_legacy(deck: DeckV16, preserve_usn: Boolean): did
    fun id_for_name(name: String): Optional<did>
    fun get_deck_legacy(did: did): Optional<DeckV16>
    fun all_decks_legacy(): List<DeckV16>
    fun new_deck_legacy(filtered: Boolean): DeckV16
    /** A sorted sequence of deck names and IDs. */
    fun all_names_and_ids(skip_empty_default: Boolean, include_filtered: Boolean): List<DeckNameId>
    fun deck_tree(now: Long, top_deck_id: Long): DeckTreeNode
    fun remove_deck_config(id: dcid)
    fun remove_deck(did: did)
}

/** WIP: Backend implementation for usage in Decks.kt */
class RustDroidDeckBackend(private val backend: BackendV1) : DecksBackend {

    override fun get_config(conf_id: dcid): Optional<DeckConfigV16> {
        return try {
            val jsonObject = from_json_bytes(backend.getDeckConfigLegacy(conf_id))
            val config = DeckConfigV16.Config(jsonObject)
            Optional.of(config)
        } catch (ex: BackendNotFoundException) {
            Optional.empty()
        }
    }

    override fun update_config(conf: DeckConfigV16, preserve_usn: Boolean): dcid {
        return backend.addOrUpdateDeckConfigLegacy(conf.to_json_bytes(), preserve_usn).dcid
    }

    override fun new_deck_config_legacy(): DeckConfigV16 {
        val jsonObject = from_json_bytes(backend.newDeckConfigLegacy())
        return DeckConfigV16.Config(jsonObject)
    }

    override fun all_config(): MutableList<DeckConfigV16.Config> {
        return jsonToArray(backend.allDeckConfigLegacy())
            .jsonObjectIterable()
            .map { obj -> DeckConfigV16.Config(obj) }
            .toMutableList()
    }

    @Throws(DeckRenameException::class)
    override fun add_or_update_deck_legacy(deck: DeckV16, preserve_usn: Boolean): did {
        try {
            val addOrUpdateResult = backend.addOrUpdateDeckLegacy(deck.to_json_bytes(), preserve_usn)
            return addOrUpdateResult.did
        } catch (ex: BackendDeckIsFilteredException) {
            throw DeckRenameException.filteredAncestor(deck.name, "")
        }
    }

    override fun id_for_name(name: String): Optional<did> {
        try {
            return Optional.of(backend.getDeckIDByName(name).did)
        } catch (ex: BackendNotFoundException) {
            return Optional.empty()
        }
    }

    override fun get_deck_legacy(did: did): Optional<DeckV16> {
        try {
            val jsonObject = from_json_bytes(backend.getDeckLegacy(did))
            val ret = if (Decks.isDynamic(Deck(jsonObject))) {
                DeckV16.FilteredDeck(jsonObject)
            } else {
                DeckV16.NonFilteredDeck(jsonObject)
            }
            return Optional.of(ret)
        } catch (ex: BackendNotFoundException) {
            return Optional.empty()
        }
    }

    override fun new_deck_legacy(filtered: Boolean): DeckV16 {
        val deck = from_json_bytes(backend.newDeckLegacy(filtered))
        return if (filtered) {
            DeckV16.FilteredDeck(deck)
        } else {
            DeckV16.NonFilteredDeck(deck)
        }
    }

    override fun all_decks_legacy(): MutableList<DeckV16> {
        return from_json_bytes(backend.allDecksLegacy)
            .objectIterable { obj -> DeckV16.Generic(obj) }
            .toMutableList()
    }

    override fun all_names_and_ids(skip_empty_default: Boolean, include_filtered: Boolean): List<DeckNameId> {
        return backend.getDeckNames(skip_empty_default, include_filtered).entriesList.map {
            entry ->
            DeckNameId(entry.name, entry.id)
        }
    }

    override fun deck_tree(now: Long, top_deck_id: Long): DeckTreeNode {
        backend.deckTree(now, top_deck_id)
        throw NotImplementedException()
    }

    override fun remove_deck_config(id: dcid) {
        backend.removeDeckConfig(id)
    }

    override fun remove_deck(did: did) {
        backend.removeDeck(did)
    }

    private fun DeckV16.to_json_bytes(): ByteString {
        return toByteString(this.getJsonObject())
    }

    private fun DeckConfigV16.to_json_bytes(): ByteString {
        return toByteString(this.config)
    }

    private fun <T> JSONObject.objectIterable(f: (JSONObject) -> T) = sequence {
        keys().forEach { k -> yield(f(getJSONObject(k))) }
    }
}
