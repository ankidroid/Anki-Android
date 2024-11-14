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
import anki.collection.OpChanges
import anki.collection.OpChangesWithCount
import anki.collection.OpChangesWithId
import anki.collection.opChangesWithId
import anki.deck_config.DeckConfigsForUpdate
import anki.deck_config.UpdateDeckConfigsRequest
import anki.decks.DeckTreeNode
import anki.decks.FilteredDeckForUpdate
import anki.decks.SetDeckCollapsedRequest
import anki.decks.copy
import com.google.protobuf.kotlin.toByteStringUtf8
import com.ichi2.annotations.NeedsTest
import com.ichi2.libanki.backend.BackendUtils
import com.ichi2.libanki.utils.LibAnkiAlias
import com.ichi2.libanki.utils.NotInLibAnki
import com.ichi2.libanki.utils.append
import com.ichi2.libanki.utils.len
import com.ichi2.utils.jsonObjectIterable
import net.ankiweb.rsdroid.RustCleanup
import net.ankiweb.rsdroid.exceptions.BackendDeckIsFilteredException
import net.ankiweb.rsdroid.exceptions.BackendNotFoundException
import org.json.JSONArray
import java.util.LinkedList

typealias UpdateDeckConfigs = UpdateDeckConfigsRequest
data class DeckNameId(val name: String, val id: DeckId)

const val DEFAULT_DECK_CONF_ID: DeckConfigId = 1L

// TODO: col was a weakref

class Decks(private val col: Collection) {

    /*
     * Registry save/load
     *************************************************************
     */

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

    fun save(g: DeckConfig) {
        g.id = col.backend.addOrUpdateDeckConfigLegacy(g.toString().toByteStringUtf8())
    }

    /*
     * Deck save/load
     *************************************************************
     */

    /** If deck exists, return existing id */
    @LibAnkiAlias("add_normal_deck_with_name")
    @Suppress("unused")
    fun addNormalDeckWithName(name: String): OpChangesWithId {
        val id = col.decks.idForName(name)
        return if (id != null) {
            opChangesWithId { this.id = id }
        } else {
            val deck = col.decks.newDeck().copy {
                this.name = name
            }
            addDeck(deck)
        }
    }

    @LibAnkiAlias("add_deck_legacy")
    private fun addDeckLegacy(deck: Deck): OpChangesWithId {
        val changes = col.backend.addDeckLegacy(
            json = BackendUtils.toJsonBytes(deck)
        )
        deck.id = changes.id
        return changes
    }

    /** Add a deck with NAME. Reuse deck if already exists. Return id as [DeckId] */
    fun id(name: String, create: Boolean, type: DeckConfigId = 0L): DeckId? {
        val id = idForName(name)
        if (id != null) {
            return id
        } else if (!create) {
            return null
        }

        val deck = this.newDeckLegacy(type != 0L)
        deck.name = name
        val out = addDeckLegacy(deck)
        return out.id
    }

    @NotInLibAnki
    fun id(name: String, type: DeckConfigId = 0L): DeckId {
        return id(name = name, create = true, type = type)!!
    }

    fun remove(deckIds: Iterable<Long>): OpChangesWithCount {
        return col.backend.removeDecks(dids = deckIds)
    }

    /** A sorted sequence of deck names and IDs. */
    @LibAnkiAlias("all_names_and_ids")
    fun allNamesAndIds(
        skipEmptyDefault: Boolean = false,
        includeFiltered: Boolean = true
    ): List<DeckNameId> {
        return col.backend.getDeckNames(skipEmptyDefault = skipEmptyDefault, includeFiltered = includeFiltered).map { entry ->
            DeckNameId(entry.name, entry.id)
        }
    }

    @LibAnkiAlias("id_for_name")
    fun idForName(name: String): DeckId? {
        return try {
            col.backend.getDeckIdByName(name)
        } catch (ex: BackendNotFoundException) {
            null
        }
    }

    @LibAnkiAlias("get_legacy")
    @RustCleanup("rename once we've removed this")
    fun get(did: DeckId): Deck? {
        return try {
            Deck(BackendUtils.fromJsonBytes(col.backend.getDeckLegacy(did)))
        } catch (ex: BackendNotFoundException) {
            null
        }
    }

    @Suppress("unused")
    fun have(id: DeckId): Boolean {
        return get(id) != null
    }

    @RustCleanup("implement and make public")
    @LibAnkiAlias("get_all_legacy")
    @Suppress("unused")
    private fun getAllLegacy(): List<Deck> {
        TODO()
    }

    /** Return a new normal deck. It must be added with [addDeck] after a name assigned. */
    @LibAnkiAlias("new_deck")
    @Suppress("unused")
    fun newDeck(): anki.decks.Deck {
        return col.backend.newDeck()
    }

    @LibAnkiAlias("add_deck")
    @Suppress("unused")
    fun addDeck(deck: anki.decks.Deck): OpChangesWithId {
        return col.backend.addDeck(input = deck)
    }

    @LibAnkiAlias("new_deck_legacy")
    private fun newDeckLegacy(filtered: Boolean): Deck {
        val deck = BackendUtils.fromJsonBytes(col.backend.newDeckLegacy(filtered))
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

    @RustCleanup("implement and make public")
    @LibAnkiAlias("deck_tree")
    @Suppress("unused")
    private fun deckTree(): DeckTreeNode {
        TODO()
    }

    @LibAnkiAlias("find_deck_in_tree")
    fun findDeckInTree(node: DeckTreeNode, deckId: DeckId): DeckTreeNode? {
        if (node.deckId == deckId) return node
        for (child in node.childrenList) {
            val foundNode = findDeckInTree(child, deckId)
            if (foundNode != null) return foundNode
        }
        return null
    }

    @RustCleanup("implement and make public")
    @Suppress("unused")
    /** "All decks. Expensive; prefer [allNamesAndIds] */
    private fun all(): List<Deck> {
        TODO()
    }

    @RustCleanup("implement and make public")
    @LibAnkiAlias("set_collapsed")
    @Suppress("unused", "unused_parameter")
    private fun setCollapsed(deckId: DeckId, collapsed: Boolean, scope: SetDeckCollapsedRequest.Scope): OpChanges {
        TODO()
    }

    fun collapse(did: DeckId) {
        val deck = this.get(did) ?: return
        deck.collapsed = !deck.collapsed
        this.save(deck)
    }

    @RustCleanup("implement and make public")
    @LibAnkiAlias("collapse_browser")
    @Suppress("unused", "unused_parameter")
    private fun collapseBrowser(deckId: DeckId) {
        TODO()
    }

    fun count(): Int {
        return len(this.allNamesAndIds())
    }

    @LibAnkiAlias("card_count")
    fun cardCount(vararg decks: DeckId, includeSubdecks: Boolean): Int {
        val dids = decks.toMutableSet()
        if (includeSubdecks) {
            // dids.update([child[1] for did in dids for child in self.children(did)])
            dids.addAll(dids.flatMap { did -> children(did) }.map { nameAndId -> nameAndId.second })
        }
        val strIds = Utils.ids2str(dids)
        return col.db.queryScalar("select count() from cards where did in $strIds or odid in $strIds")
    }

    @RustCleanup("implement and make public")
    @LibAnkiAlias("get")
    @Suppress("unused", "unused_parameter")
    private fun get(did: DeckId, default: Boolean = true): Deck? {
        return try {
            Deck(BackendUtils.fromJsonBytes(col.backend.getDeckLegacy(did)))
        } catch (ex: BackendNotFoundException) {
            null
        }
    }

    /** Get deck with NAME, ignoring case. */
    @LibAnkiAlias("by_name")
    fun byName(name: String): Deck? {
        val id = this.idForName(name)
        if (id != null) {
            return get(id)
        }
        return null
    }

    /** Add or update an existing deck. Used for syncing and merging. */
    @Suppress("unused")
    fun update(deck: Deck, preserveUsn: Boolean) {
        deck.id = col.backend.addOrUpdateDeckLegacy(
            deck = BackendUtils.toJsonBytes(deck),
            preserveUsnAndMtime = preserveUsn
        )
    }

    @LibAnkiAlias("update_dict")
    @Suppress("unused")
    fun updateDict(deck: Deck): OpChanges {
        return col.backend.updateDeckLegacy(BackendUtils.toJsonBytes(deck))
    }

    /** Rename deck prefix to NAME if not exists. Updates children. */
    @RustCleanup("return OpChanges")
    fun rename(deck: Deck, newName: String) {
        deck.name = newName
        this.save(deck)
    }

    /*
     * Drag/drop
     *************************************************************
     */

    /**
     * Rename one or more source decks that were dropped on [newParent].
     *
     * If [newParent] is `0`, decks will be placed at the top level.
     */
    @Suppress("unused")
    fun reparent(deckIds: List<DeckId>, newParent: DeckId): OpChangesWithCount {
        return col.backend.reparentDecks(
            deckIds = deckIds,
            newParent = newParent
        )
    }

    /*
     * Deck configurations
     *************************************************************
     */

    @LibAnkiAlias("get_deck_configs_for_update")
    @Suppress("unused")
    fun getDeckConfigsForUpdate(deckId: DeckId): DeckConfigsForUpdate {
        return col.backend.getDeckConfigsForUpdate(deckId)
    }

    @RustCleanup("implement and make public")
    @LibAnkiAlias("update_deck_configs")
    @Suppress("unused", "unused_parameter")
    private fun updateDeckConfigs(input: UpdateDeckConfigs): DeckConfigsForUpdate {
        TODO()
    }

    /** A list of all deck config. */
    @LibAnkiAlias("all_config")
    fun allConfig(): List<DeckConfig> {
        return BackendUtils.jsonToArray(col.backend.allDeckConfigLegacy())
            .jsonObjectIterable()
            .map { obj -> DeckConfig(obj) }
            .toList()
    }

    /** Falls back on default config if deck or config missing */
    @LibAnkiAlias("config_dict_for_deck_id")
    fun configDictForDeckId(did: DeckId): DeckConfig {
        val conf = get(did)?.conf ?: 1
        return DeckConfig(BackendUtils.fromJsonBytes(col.backend.getDeckConfigLegacy(conf)))
    }

    /* Reverts to default if provided id missing */
    @LibAnkiAlias("get_config")
    fun getConfig(confId: DeckConfigId): DeckConfig =
        DeckConfig(BackendUtils.fromJsonBytes(col.backend.getDeckConfigLegacy(confId)))

    @RustCleanup("implement and make public")
    @LibAnkiAlias("update_config")
    @Suppress("unused", "unused_parameter")
    private fun updateConfig(config: DeckConfig, preserveUsn: Boolean = false) {
        TODO()
    }

    @LibAnkiAlias("add_config")
    private fun addConfig(
        name: String
    ): DeckConfig {
        val conf = DeckConfig(newDeckConfigLegacy())
        conf.name = name
        this.save(conf)
        return conf
    }

    @LibAnkiAlias("add_config_returning_id")
    fun addConfigReturningId(name: String): Long {
        return addConfig(name).id
    }

    @RustCleanup("implement and make public")
    @LibAnkiAlias("remove_config")
    @Suppress("unused", "unused_parameter")
    private fun removeConfig(id: DeckConfigId) {
        TODO()
    }

    @LibAnkiAlias("set_config_id_for_deck_dict")
    fun setConfigIdForDeckDict(grp: Deck, id: DeckConfigId) {
        grp.conf = id
        this.save(grp)
    }

    @NotInLibAnki
    @RustCleanup("inline")
    private fun newDeckConfigLegacy(): DeckConfig {
        return DeckConfig(BackendUtils.fromJsonBytes(col.backend.newDeckConfigLegacy()))
    }

    @RustCleanup("implement and make public")
    @LibAnkiAlias("decks_using_config")
    @Suppress("unused", "unused_parameter")
    private fun decksUsingConfig(config: DeckConfig): List<DeckId> {
        TODO()
    }

    @RustCleanup("implement and make public")
    @LibAnkiAlias("restore_to_default")
    @Suppress("unused", "unused_parameter")
    private fun restoreToDefault(config: DeckConfig) {
        TODO()
    }

    /*
     * Deck utils
     *************************************************************
     */

    @RustCleanup("use TR")
    fun name(did: DeckId): String = get(did)?.name ?: "[no deck]"

    @RustCleanup("implement and make public")
    @LibAnkiAlias("name_if_exists")
    @Suppress("unused", "unused_parameter")
    private fun nameIfExists(did: DeckId): String? {
        TODO()
    }

    @RustCleanup("implement and make public")
    @Suppress("unused", "unused_parameter")
    private fun cids(did: DeckId, children: Boolean): List<CardId> {
        TODO()
    }

    @RustCleanup("implement and make public")
    @LibAnkiAlias("for_card_ids")
    @Suppress("unused", "unused_parameter")
    private fun forCardIds(cids: List<CardId>): List<DeckId> {
        TODO()
    }

    /*
     * Deck selection
     *************************************************************
     */

    @CheckResult
    @LibAnkiAlias("set_current")
    fun setCurrent(deck: DeckId): OpChanges {
        return col.backend.setCurrentDeck(deck)
    }

    /** @return The currently selected deck ID. */
    @LibAnkiAlias("get_current_id")
    fun getCurrentId(): DeckId = col.backend.getCurrentDeck().id

    fun current(): Deck {
        return this.get(this.selected()) ?: this.get(1)!!
    }

    /** The currently active dids. */
    @RustCleanup("Probably better as a queue")
    @RustCleanup("should not return an empty list")
    fun active(): LinkedList<DeckId> {
        val activeDecks = col.config.get<List<DeckId>>(ACTIVE_DECKS) ?: listOf()
        val result = LinkedList<Long>()
        result.addAll(activeDecks.asIterable())
        return result
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

    /** @return The currently selected deck ID. */
    fun selected(): DeckId = getCurrentId()

    /*
     * Parents/children
     *************************************************************
     */

    /** The deck of did and all its children, as (name, id). */
    @LibAnkiAlias("deck_and_child_name_ids")
    fun deckAndChildNameIds(deckId: DeckId): List<Pair<String, DeckId>> {
        return col.backend.getDeckAndChildNames(deckId).map { entry ->
            entry.name to entry.id
        }
    }

    /** All children of did, as (name, id). */
    @LibAnkiAlias("children")
    fun children(did: DeckId): List<Pair<String, Long>> {
        return deckAndChildNameIds(did).filter {
            it.second != did
        }
    }

    @LibAnkiAlias("child_ids")
    fun childIds(parentName: String): List<DeckId> {
        val parentId = idForName(parentName) ?: return emptyList()
        return children(parentId).map { it.second }
    }

    @LibAnkiAlias("deck_and_child_ids")
    fun deckAndChildIds(deckId: DeckId): List<DeckId> {
        return col.backend.getDeckAndChildNames(deckId).map { entry ->
            entry.id
        }
    }

    /** All parents of did. */
    @LibAnkiAlias("parents")
    fun parents(did: DeckId, nameMap: Map<String, Deck>? = null): List<Deck> {
        // get parent and grandparent names
        val parentsNames = mutableListOf<String>()
        for (part in immediateParentPath(get(did)!!.name)) {
            if (parentsNames.isEmpty()) {
                parentsNames.add(part)
            } else {
                parentsNames.append("${parentsNames.last()}::$part")
            }
        }
        // convert to objects
        val parents = mutableListOf<Deck>()
        for (parentName in parentsNames) {
            val deck = if (nameMap != null) {
                nameMap[parentName]
            } else {
                get(id(parentName))
            }!!
            parents.add(deck)
        }
        return parents.toList()
    }

    /** All existing parents of [name] */
    @NeedsTest("implementation matches Anki's")
    @LibAnkiAlias("parents_by_name")
    fun parentsByName(name: String): List<Deck> {
        if (!name.contains("::")) {
            return listOf()
        }
        val names = immediateParentPath(name)
        val head = mutableListOf<String>()
        val parents = mutableListOf<Deck>()

        for (deckName in names) {
            head.add(deckName)
            val deck = byName(head.joinToString("::"))
            if (deck != null) {
                parents.append(deck)
            }
        }

        return parents.toList()
    }

    /*
     * Filtered decks
     *************************************************************
     */

    /** For new code, prefer [getOrCreateFilteredDeck] */
    @LibAnkiAlias("new_filtered")
    fun newFiltered(name: String): DeckId {
        val did = id(name, type = DEFAULT_DECK_CONF_ID)
        select(did!!)
        return did
    }

    @LibAnkiAlias("is_filtered")
    fun isFiltered(did: DeckId): Boolean {
        return this.get(did)?.isFiltered == true
    }

    /*
     * Not in libAnki
     *************************************************************
     */

    /** @return the fully qualified name of the subdeck, or null if unavailable */
    @NotInLibAnki
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

    @NotInLibAnki
    fun cardCount(did: DeckId): Int =
        col.db.queryScalar("SELECT count() FROM cards WHERE did = ? ", did)

    @NotInLibAnki
    fun isEmpty(did: DeckId): Boolean = cardCount(did) == 0

    companion object {
        /* Parents/children */

        fun path(name: String): List<String> {
            return name.split("::")
        }

        fun basename(name: String): String {
            return path(name).last()
        }

        @LibAnkiAlias("immediate_parent_path")
        fun immediateParentPath(name: String): List<String> {
            return path(name).dropLast(1)
        }

        @LibAnkiAlias("immediate_parent")
        fun immediateParent(name: String): String? {
            val parentPath = immediateParentPath(name)
            if (parentPath.isNotEmpty()) {
                return parentPath.joinToString("::")
            }
            return null
        }

        /** Invalid id, represents an id on an unfound deck  */
        const val NOT_FOUND_DECK_ID = -1L

        /** Configuration saving the current deck  */
        const val CURRENT_DECK = "curDeck"

        /** Configuration saving the set of active decks (i.e. current decks and its descendants)  */
        const val ACTIVE_DECKS = "activeDecks"

        @NotInLibAnki
        const val DECK_SEPARATOR = "::"

        @NotInLibAnki
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
