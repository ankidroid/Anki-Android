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

@file:Suppress(
    "RedundantIf",
    "LiftReturnOrAssignment",
    "MemberVisibilityCanBePrivate",
    "FunctionName",
    "ConvertToStringTemplate",
    "LocalVariableName"
)

package com.ichi2.libanki

import anki.collection.OpChangesWithCount
import anki.collection.OpChangesWithId
import anki.decks.FilteredDeckForUpdate
import com.google.protobuf.ByteString
import com.ichi2.libanki.Decks.Companion.ACTIVE_DECKS
import com.ichi2.libanki.Utils.ids2str
import com.ichi2.libanki.backend.BackendUtils
import com.ichi2.libanki.backend.exception.DeckRenameException
import com.ichi2.libanki.utils.*
import com.ichi2.libanki.utils.TimeManager.time
import com.ichi2.utils.deepClone
import com.ichi2.utils.jsonObjectIterable
import com.ichi2.utils.longIterable
import java8.util.Optional
import net.ankiweb.rsdroid.RustCleanup
import net.ankiweb.rsdroid.exceptions.BackendDeckIsFilteredException
import net.ankiweb.rsdroid.exceptions.BackendNotFoundException
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.util.*

data class DeckNameId(val name: String, val id: DeckId)

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

// legacy code may pass this in as the type argument to .id()
const val defaultDeck = 0
const val defaultDynamicDeck = 1

/** Any kind of deck */
// typealias Deck = Union<NonFilteredDeck, FilteredDeck>
// typealias NonFilteredDeck = Dict<string, Any>
// typealias FilteredDeck = Dict<string, Any>
open class DeckV16 private constructor(private val deck: JSONObject) {
    class NonFilteredDeck(val deck: JSONObject) : DeckV16(deck)
    class FilteredDeck(val deck: JSONObject) : DeckV16(deck)
    internal class Generic(val deck: JSONObject) : DeckV16(deck)

    // to be usd rarely
    fun getJsonObject(): JSONObject {
        return deck
    }

    fun hasKey(s: String): Boolean = deck.has(s)

    var name: str
        get() = deck.getString("name")
        set(value) {
            deck.put("name", value)
        }

    var collapsed: bool
        get() = deck.getBoolean("collapsed")
        set(value) {
            deck.put("collapsed", value)
        }

    var id: DeckId
        get() = deck.getLong("id")
        set(value) {
            deck.put("id", value)
        }

    var browserCollapsed: bool
        get() = deck.optBoolean("browserCollapsed", false)
        set(value) {
            deck.put("browserCollapsed", value)
        }

    var conf: Long
        get() = deck.getLong("conf")
        set(value) {
            deck.put("conf", value)
        }

    val dyn: Int
        get() = deck.getInt("dyn")
}

// TODO: do we want optional<str>, or string here
var Optional<DeckV16>.name: str
    get() = this.get()!!.name
    set(value) {
        this.get()!!.name = value
    }

// /** Configuration of standard deck, as seen from the deck picker's gear. */
// typealias Config = Dict<str, Any>
// typealias DeckConfig = Union<FilteredDeck, Config>

/** Configuration of some deck, filtered deck for filtered deck, config for standard deck */
abstract class DeckConfigV16 private constructor(val config: JSONObject) {
    class Config(val configData: JSONObject) : DeckConfigV16(configData) {
        override fun deepClone(): DeckConfigV16 = Config(configData.deepClone())
        override val source = DeckConfig.Source.DECK_CONFIG
    }

    class FilteredDeck(val deckData: JSONObject) : DeckConfigV16(deckData) {
        override fun deepClone(): DeckConfigV16 = FilteredDeck(deckData.deepClone())
        override val source = DeckConfig.Source.DECK_EMBEDDED
    }

    abstract val source: DeckConfig.Source

    var conf: Long
        get() = config.getLong("conf")
        set(value) {
            config.put("conf", value)
        }

    var id: dcid
        get() = config.getLong("id")
        set(value) {
            config.put("id", value)
        }

    var name: str
        get() = config.getString("name")
        set(value) {
            config.put("name", value)
        }

    var dyn: bool
        get() = config.getInt("dyn") == Consts.DECK_DYN
        set(value) {
            config.put("dyn", if (value) Consts.DECK_DYN else Consts.DECK_STD)
        }

    fun getJSONObject(key: String): JSONObject = config.getJSONObject(key)
    abstract fun deepClone(): DeckConfigV16

    companion object {
        fun from(g: DeckConfig): DeckConfigV16 {
            return when (g.source) {
                DeckConfig.Source.DECK_EMBEDDED -> FilteredDeck(g)
                DeckConfig.Source.DECK_CONFIG -> Config(g)
            }
        }
    }
}

/** New/lrn/rev conf, from deck config */
private typealias QueueConfig = Dict<str, Any>

private typealias childMapNode = Dict<DeckId, Any>
// Change to Dict[int, "DeckManager.childMapNode"] when MyPy allow recursive type

// TODO: col was a weakref

/**
 * Untested WIP implementation of Decks for Schema V16.
 *
 * It's planned to consolidate interfaces between this and decks.py
 *
 * Afterwards, we can finish up the implementations, run our tests, and use this with a V16
 * collection, using decks as a separate table
 */
class DecksV16(private val col: CollectionV16) :
    DeckManager() {

    /* Registry save/load */

    @Throws(DeckRenameException::class)
    private fun save(grp: DeckConfigV16) {
        when (grp) {
            is DeckConfigV16.Config -> save(grp)
            is DeckConfigV16.FilteredDeck -> save(DeckV16.FilteredDeck(grp.deckData))
        }
    }

    @RustCleanup("not in V16")
    override fun save() {
        Timber.w(Exception("Decks.save() called - probably a bug"))
    }

    @Throws(DeckRenameException::class)
    override fun save(g: Deck) {
        save(DeckV16.Generic(g))
    }

    override fun save(g: DeckConfig) {
        save(DeckConfigV16.from(g))
    }

    fun save(g: DeckConfigV16.Config) {
        // deck conf?
        this.update_config(g)
    }

    @Throws(DeckRenameException::class)
    fun save(g: DeckV16) {
        // legacy code expects preserve_usn=false behaviour, but that
        // causes a backup entry to be created, which invalidates the
        // v2 review history. So we manually update the usn/mtime here
        g.getJsonObject().run {
            put("mod", time.intTime())
            put("usn", col.usn())
        }
        this.update(g, preserve_usn = true)
    }

    @RustCleanup("unused in V16")
    override fun load(
        @Suppress("UNUSED_PARAMETER") decks: String,
        @Suppress("UNUSED_PARAMETER") dconf: String
    ) {
    }

    // legacy
    override fun flush() {
        // no-op
    }

    /* Deck save/load */
    @RustCleanup("only for java interface: newDyn was used for filtered decks")
    override fun id(name: str): DeckId {
        // use newDyn for now
        return id(name, true, 0).get()
    }

    @RustCleanup("only for java interface - should be removed")
    @RustCleanup("This needs major testing - the behavior had changed")
    override fun id_safe(name: String, @Suppress("UNUSED_PARAMETER") type: String): Long {
        return id(name, create = true, type = 0).get()
    }

    /** "Add a deck with NAME. Reuse deck if already exists. Return id as int." */
    @Throws(DeckRenameException::class)
    fun id(name: str, create: bool = true, type: Int = 0): Optional<DeckId> {
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

    fun addDeckLegacy(deck: DeckV16): OpChangesWithId {
        val changes = col.backend.addDeckLegacy(
            json = BackendUtils.to_json_bytes(deck.getJsonObject())
        )
        deck.id = changes.id
        return changes
    }

    /** Remove the deck. If cardsToo, delete any cards inside. */
    override fun rem(did: DeckId, cardsToo: bool, childrenToo: bool) {
        assert(cardsToo && childrenToo)
        col.backend.removeDecks(listOf(did))
    }

    fun removeDecks(deckIds: Iterable<Long>): OpChangesWithCount {
        return col.backend.removeDecks(dids = deckIds)
    }

    @Suppress("deprecation")
    override fun allNames(dyn: Boolean): List<String> {
        return allNames(dyn = dyn, force_default = true)
    }

    /** A sorted sequence of deck names and IDs. */
    fun all_names_and_ids(
        skip_empty_default: bool = false,
        include_filtered: bool = true
    ): ImmutableList<DeckNameId> {
        return col.backend.getDeckNames(skip_empty_default, include_filtered).map {
                entry ->
            DeckNameId(entry.name, entry.id)
        }
    }

    override fun id_for_name(name: str): DeckId? {
        try {
            return col.backend.getDeckIdByName(name)
        } catch (ex: BackendNotFoundException) {
            return null
        }
    }

    fun get_legacy(did: DeckId): Deck? {
        return get_deck_legacy(did)?.let { x -> Deck(x.getJsonObject()) }
    }

    private fun get_deck_legacy(did: DeckId): DeckV16? {
        try {
            val jsonObject = BackendUtils.from_json_bytes(col.backend.getDeckLegacy(did))
            val ret = if (Decks.isDynamic(Deck(jsonObject))) {
                DeckV16.FilteredDeck(jsonObject)
            } else {
                DeckV16.NonFilteredDeck(jsonObject)
            }
            return ret
        } catch (ex: BackendNotFoundException) {
            return null
        }
    }

    fun get_all_legacy(): ImmutableList<DeckV16> {
        return BackendUtils.from_json_bytes(col.backend.getAllDecksLegacy())
            .objectIterable { obj -> DeckV16.Generic(obj) }
            .toList()
    }

    private fun <T> JSONObject.objectIterable(f: (JSONObject) -> T) = sequence {
        keys().forEach { k -> yield(f(getJSONObject(k))) }
    }

    fun new_deck_legacy(filtered: bool): DeckV16 {
        val deck = BackendUtils.from_json_bytes(col.backend.newDeckLegacy(filtered))
        return if (filtered) {
            // until migrating to the dedicated method for creating filtered decks,
            // we need to ensure the default config matches legacy expectations
            val terms = deck.getJSONArray("terms").getJSONArray(0)
            terms.put(0, "")
            terms.put(2, 0)
            deck.put("terms", JSONArray(listOf(terms)))
            deck.put("browserCollapsed", false)
            deck.put("collapsed", false)
            DeckV16.FilteredDeck(deck)
        } else {
            DeckV16.NonFilteredDeck(deck)
        }
    }

    /** All decks. Expensive; prefer all_names_and_ids() */
    override fun all(): ImmutableList<Deck> {
        return this.get_all_legacy().map { x -> Deck(x.getJsonObject()) }
    }

    @Deprecated("decks.allIds() is deprecated, use .all_names_and_ids()")
    override fun allIds(): Set<DeckId> {
        return this.all_names_and_ids().map { x -> x.id }.toSet()
    }

    @Deprecated("decks.allNames() is deprecated, use .all_names_and_ids()")
    fun allNames(dyn: bool = true, force_default: bool = true): MutableList<str> {
        return this.all_names_and_ids(
            skip_empty_default = !force_default,
            include_filtered = dyn
        ).map { x ->
            x.name
        }.toMutableList()
    }

    override fun collapse(did: DeckId) {
        val deck = this.get(did).toV16()
        deck.collapsed = !deck.collapsed
        this.save(deck)
    }

    fun collapseBrowser(did: DeckId) {
        val deck = this.get(did).toV16()
        val collapsed = deck.browserCollapsed
        deck.browserCollapsed = !collapsed
        this.save(deck)
    }

    override fun count(): Int {
        return len(this.all_names_and_ids())
    }

    override fun get(did: DeckId, _default: bool): Deck? {
        val deck = this.get_legacy(did)
        return when {
            deck != null -> deck
            _default -> this.get_legacy(1)
            else -> null
        }
    }

    /** Get deck with NAME, ignoring case. */
    override fun byName(name: str): Deck? {
        val id = this.id_for_name(name)
        if (id != null) {
            return this.get_legacy(id)
        }
        return null
    }

    @Throws(DeckRenameException::class)
    /** This skips the backend undo queue, so is required instead of save()
     * to avoid clobbering the v2 review queue.
     */
    override fun update(g: Deck) {
        // we preserve USN here as this method is used for syncing and merging
        update(DeckV16.Generic(g), preserve_usn = true)
    }

    @Throws(DeckRenameException::class)
    override fun rename(g: Deck, newName: String) {
        rename(DeckV16.Generic(g), newName)
    }

    /** Add or update an existing deck. Used for syncing and merging. */
    fun update(g: DeckV16, preserve_usn: bool = true) {
        g.id = try {
            col.backend.addOrUpdateDeckLegacy(g.to_json_bytes(), preserve_usn)
        } catch (ex: BackendDeckIsFilteredException) {
            throw DeckRenameException.filteredAncestor(g.name, "")
        }
    }

    private fun DeckV16.to_json_bytes(): ByteString {
        return BackendUtils.toByteString(this.getJsonObject())
    }

    /** Rename deck prefix to NAME if not exists. Updates children. */
    fun rename(g: DeckV16, newName: str) {
        g.name = newName
        this.update(g, preserve_usn = false)
    }

    /* Deck configurations */

    /** A list of all deck config. */
    fun all_config(): ImmutableList<DeckConfigV16.Config> {
        return BackendUtils.jsonToArray(col.backend.allDeckConfigLegacy())
            .jsonObjectIterable()
            .map { obj -> DeckConfigV16.Config(obj) }
            .toList()
    }

    @RustCleanup("Return v16 config - we return a typed object here")
    override fun confForDid(did: DeckId): DeckConfig {
        val deck = this.get(did, _default = false).toV16Optional()
        assert(deck.isPresent)
        val deckValue = deck.get()
        if (deckValue.hasKey("conf")) {
            val dcid = deckValue.conf // TODO: may be a string
            val conf = get_config(dcid)
            conf.dyn = false
            return DeckConfig(conf.config, conf.source)
        }
        // dynamic decks have embedded conf
        return DeckConfig(deck.get().getJsonObject(), DeckConfig.Source.DECK_EMBEDDED)
    }

    /* Backend will return default config if provided id doesn't exist. */
    fun get_config(conf_id: dcid): DeckConfigV16 {
        val jsonObject = BackendUtils.from_json_bytes(col.backend.getDeckConfigLegacy(conf_id))
        val config = DeckConfigV16.Config(jsonObject)
        return config
    }

    fun update_config(conf: DeckConfigV16, preserve_usn: bool = false) {
        if (preserve_usn) {
            TODO("no longer supported; need to switch to new sync code")
        }
        conf.id = col.backend.addOrUpdateDeckConfigLegacy(conf.to_json_bytes())
    }

    private fun DeckConfigV16.to_json_bytes(): ByteString {
        return BackendUtils.toByteString(this.config)
    }

    fun add_config(
        name: str,
        clone_from: Optional<DeckConfigV16> = Optional.empty()
    ): DeckConfigV16 {
        val conf: DeckConfigV16
        if (clone_from.isPresent) {
            conf = clone_from.get().deepClone()
            conf.id = 0L
        } else {
            conf = newDeckConfigLegacy()
        }
        conf.name = name
        this.update_config(conf)
        return conf
    }

    private fun newDeckConfigLegacy(): DeckConfigV16 {
        val jsonObject = BackendUtils.from_json_bytes(col.backend.newDeckConfigLegacy())
        return DeckConfigV16.Config(jsonObject)
    }

    fun add_config_returning_id(
        name: str,
        clone_from: Optional<DeckConfigV16> = Optional.empty()
    ): dcid = this.add_config(name, clone_from).id

    /** Remove a configuration and update all decks using it. */
    fun remove_config(id: dcid) {
        this.col.modSchema() // TODO: True was passed in as an arg
        for (g in this.all()) {
            // ignore cram decks
            if (!g.has("conf")) {
                continue
            }
            if (g.conf.toString() == id.toString()) {
                g.put("conf", 1L) // we need this as setConf() already is defined
                this.save(g)
            }
        }
        col.backend.removeDeckConfig(dcid = id)
    }

    override fun setConf(grp: Deck, id: Long) {
        setConf(DeckV16.Generic(grp), id)
    }

    override fun didsForConf(conf: DeckConfig): List<Long> =
        didsForConf(DeckConfigV16.from(conf))

    override fun restoreToDefault(conf: DeckConfig) {
        restoreToDefault(DeckConfigV16.from(conf))
    }

    @RustCleanup("maybe an issue here - grp was deckConfig in V16")
    fun setConf(grp: DeckV16, id: dcid) {
        grp.conf = id
        this.save(grp)
    }

    fun didsForConf(conf: DeckConfigV16): MutableList<DeckId> {
        val dids = mutableListOf<DeckId>()
        for (deck in this.all()) {
            if (deck.has("conf") && deck.conf == conf.id) {
                dids.append(deck.id)
            }
        }
        return dids
    }

    fun restoreToDefault(conf: DeckConfigV16) {
        val oldOrder = conf.getJSONObject("new").getInt("order")
        val new = newDeckConfigLegacy()
        new.id = conf.id
        new.name = conf.name
        this.update_config(new)
        // if it was previously randomized, re-sort
        if (oldOrder == 0) {
            this.col.sched.resortConf(DeckConfig(new.config, DeckConfig.Source.DECK_CONFIG))
        }
    }

    // legacy
    override fun allConf() =
        all_config().map { x -> DeckConfig(x.config, x.source) }.toMutableList()

    /* Reverts to default if provided id missing */
    override fun getConf(confId: dcid): DeckConfig =
        get_config(confId).let { x -> DeckConfig(x.config, x.source) }

    override fun confId(name: String, cloneFrom: String): Long {
        val config: Optional<DeckConfigV16> =
            Optional.of(DeckConfigV16.Config(JSONObject(cloneFrom)))
        return add_config_returning_id(name, config)
    }

    override fun updateConf(g: DeckConfig) = updateConf(DeckConfigV16.from(g), preserve_usn = false)
    fun updateConf(conf: DeckConfigV16, preserve_usn: bool = false) =
        update_config(conf, preserve_usn)

    override fun remConf(id: dcid) = remove_config(id)
    fun confId(name: str, clone_from: Optional<DeckConfigV16> = Optional.empty()) =
        add_config_returning_id(name, clone_from)

    /* Deck utils */

    override fun name(did: DeckId, _default: bool): str {
        val deck = this.get(did, _default = _default).toV16Optional()
        if (deck.isPresent) {
            return deck.name
        }
        // TODO: Needs i18n, but the Java did the same, appears to be dead code
        return "[no deck]"
    }

    fun nameOrNone(did: DeckId): Optional<str> {
        val deck = this.get(did, _default = false).toV16Optional()
        if (deck.isPresent) {
            return Optional.of(deck.name)
        }
        return Optional.empty()
    }

    override fun cids(did: DeckId, children: bool): MutableList<Long> {
        if (!children) {
            return this.col.db.queryLongList("select id from cards where did=?", did)
        }
        val dids = mutableListOf(did)
        for ((_, id) in this.children(did)) {
            dids.append(id)
        }
        return this.col.db.queryLongList("select id from cards where did in " + ids2str(dids))
    }

    @RustCleanup("needs testing")
    override fun checkIntegrity() {
        // I believe this is now handled in libAnki
    }

    fun for_card_ids(cids: List<Long>): List<DeckId> {
        return this.col.db.queryLongList("select did from cards where id in ${ids2str(cids)}")
    }

    /* Deck selection */

    /** The currently active dids. */
    @RustCleanup("Probably better as a queue")
    override fun active(): LinkedList<DeckId> {
        val activeDecks: JSONArray = col.get_config_array(ACTIVE_DECKS)
        val result = LinkedList<Long>()
        result.addAll(activeDecks.longIterable())
        return result
    }

    /** The currently selected did. */
    override fun selected(): DeckId {
        return this.col.backend.getCurrentDeck().id
    }

    override fun current(): Deck {
        return this.get(this.selected())
    }

    /** Select a new branch. */
    override fun select(did: DeckId) {
        // make sure arg is an int
        // did = int(did) - code removed, logically impossible
        col.backend.setCurrentDeck(did)
        val active = this.deck_and_child_ids(did)
        if (active != this.active()) {
            this.col.set_config(ACTIVE_DECKS, active.toJsonArray())
        }
    }

    /** don't use this, it will likely go away */
    override fun update_active() {
        this.select(this.current().id)
    }

    /* Parents/children */

    companion object {

        fun find_deck_in_tree(
            node: anki.decks.DeckTreeNode,
            deck_id: DeckId
        ): Optional<anki.decks.DeckTreeNode> {
            if (node.deckId == deck_id) {
                return Optional.of(node)
            }
            for (child in node.childrenList) {
                val match = find_deck_in_tree(child, deck_id)
                if (match.isPresent) {
                    return match
                }
            }
            return Optional.empty()
        }

        fun path(name: str): ImmutableList<str> {
            return name.split("::")
        }

        fun _path(name: str) = path(name)

        fun basename(name: str): str {
            return path(name).last()
        }

        fun _basename(str: str) = basename(str)

        fun immediate_parent_path(name: str): MutableList<str> {
            return _path(name).dropLast(1).toMutableList()
        }

        fun immediate_parent(name: str): Optional<str> {
            val pp = immediate_parent_path(name)
            if (pp.isNotNullOrEmpty()) {
                return Optional.of("::".join(pp))
            }
            return Optional.empty()
        }

        fun key(deck: DeckV16): ImmutableList<str> {
            return path(deck.name)
        }
    }

    /** All children of did, as (name, id). */
    override fun children(did: DeckId): TreeMap<str, DeckId> {
        val name: str = this.get(did).toV16().name
        val actv = TreeMap<str, DeckId>()
        for (g in this.all_names_and_ids()) {
            if (g.name.startsWith(name + "::")) {
                actv.put(g.name, g.id)
            }
        }
        return actv
    }

    override fun childDids(did: DeckId, childMap: Decks.Node): List<Long> {
        return childDids(did, childMapNode(childMap))
    }

    fun child_ids(parent_name: str): Iterable<DeckId> {
        val prefix = parent_name + "::"
        return all_names_and_ids().filter { x ->
            x.name.startsWith(prefix)
        }.map { d ->
            d.id
        }.toMutableList()
    }

    fun deck_and_child_ids(deck_id: DeckId): MutableList<DeckId> {
        val parent_name = this.get_legacy(deck_id)!!.toV16().name
        val out = mutableListOf(deck_id)
        out.extend(this.child_ids(parent_name))
        return out
    }

    @Suppress("UNCHECKED_CAST")
    fun childDids(did: DeckId, childMap: childMapNode): MutableList<DeckId> {
        fun gather(node: childMapNode, arr: MutableList<DeckId>) {
            for ((itemDid, child) in node.items()) {
                arr.append(itemDid)
                gather(child as childMapNode, arr)
            }
        }

        val arr = mutableListOf<DeckId>()
        gather(childMap[did] as childMapNode, arr)
        return arr
    }

    @Suppress("UNCHECKED_CAST")
    @RustCleanup("used to return childMapNode")
    override fun childMap(): Decks.Node {
        val nameMap = this.nameMap()
        val childMap = childMapNode()

        // go through all decks, sorted by name
        for (deck in sorted(this.all())) {
            val node = Dict<DeckId, Any>()
            childMap[deck.id] = node

            // add note to immediate parent
            val immediateParent = immediate_parent(deck.name)
            if (immediateParent.isPresent) {
                val pid = nameMap[immediateParent.get()]?.id
                val value = childMap[pid] as childMapNode?
                if (value != null) {
                    value[deck.id] = node
                }
            }
        }

        return childMap.toNode()
    }

    @Suppress("UNCHECKED_CAST")
    @RustCleanup("needs testing")
    fun childMapNode.toNode(): Decks.Node {
        val ret = Decks.Node()
        for (x in this) {
            ret[x.key] = (x.value as childMapNode).toNode()
        }
        return ret
    }

    override fun parents(did: DeckId): List<Deck> {
        return parents(did, Optional.empty())
    }

    @RustCleanup("not needed")
    override fun beforeUpload() {
        // intentionally blank
    }

    private fun sorted(all: ImmutableList<Deck>): ImmutableList<Deck> {
        return all.sortedBy { d -> d.getString("name") }
    }

    /** All parents of did. */
    fun parents(
        did: DeckId,
        nameMap: Optional<Dict<str, Deck>> = Optional.empty()
    ): List<Deck> {
        // get parent and grandparent names
        val parents_names: MutableList<str> = mutableListOf()
        for (part in immediate_parent_path(this.get(did).toV16Optional().name)) {
            if (parents_names.isNullOrEmpty()) {
                parents_names.append(part)
            } else {
                parents_names.append(parents_names.last() + "::" + part)
            }
        }
        val parents: MutableList<Deck> = mutableListOf()
        // convert to objects
        for (parent_name in parents_names) {
            var deck: Deck
            if (nameMap.isPresent) {
                deck = nameMap.get()[parent_name]!!
            } else {
                deck = this.get(this.id(parent_name))
            }
            parents.append(deck)
        }
        return parents
    }

    /** All existing parents of name */
    fun parentsByName(name: str): MutableList<Deck> {
        if (!name.contains("::")) {
            return mutableListOf()
        }
        val names: MutableList<str> = immediate_parent_path(name)
        val head: MutableList<str> = mutableListOf()
        val parents: MutableList<Deck> = mutableListOf()

        while (names.isNotNullOrEmpty()) {
            head.append(names.pop(0))
            val deck = this.byName("::".join(head))
            if (deck != null) {
                parents.append(deck)
            }
        }

        return parents
    }

    fun nameMap(): Map<str, Deck> {
        return all().map { d -> Pair(d.name, d) }.toMap()
    }

    /*
     Dynamic decks
     */

    /** Return a new dynamic deck and set it as the current deck. */
    override fun newDyn(name: str): DeckId {
        val did = this.id(name, type = 1).get()
        this.select(did)
        return did
    }

    // 1 for dyn, 0 for standard
    override fun isDyn(did: DeckId): Boolean {
        return this.get(did).toV16().dyn != 0
    }

    fun Deck?.toV16Optional(): Optional<DeckV16> {
        if (this == null) {
            return Optional.empty()
        }
        return Optional.of(this.toV16())
    }

    fun Deck.toV16(): DeckV16 {
        return DeckV16.Generic(this)
    }

    val Deck.name: str get() = this.getString("name")
    val Deck.conf: Long get() = this.getLong("conf")
}

// These take and return bytes that the frontend TypeScript code will encode/decode.
fun CollectionV16.getDeckNamesRaw(input: ByteArray): ByteArray {
    return backend.getDeckNamesRaw(input)
}

/**
 * Gets the filtered deck with given [did]
 * or creates a new one if [did] = 0
 */
fun CollectionV16.getOrCreateFilteredDeck(did: DeckId): FilteredDeckForUpdate {
    return backend.getOrCreateFilteredDeck(did = did)
}
