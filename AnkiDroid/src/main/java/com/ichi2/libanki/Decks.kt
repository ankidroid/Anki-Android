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
import com.google.protobuf.ByteString
import com.ichi2.libanki.backend.BackendUtils
import com.ichi2.libanki.backend.exception.DeckRenameException
import com.ichi2.libanki.utils.*
import com.ichi2.libanki.utils.TimeManager.time
import com.ichi2.utils.KotlinCleanup
import com.ichi2.utils.deepClone
import com.ichi2.utils.jsonObjectIterable
import java8.util.Optional
import net.ankiweb.rsdroid.RustCleanup
import net.ankiweb.rsdroid.exceptions.BackendDeckIsFilteredException
import net.ankiweb.rsdroid.exceptions.BackendNotFoundException
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.util.*

data class DeckNameId(val name: String, val id: DeckId)

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

    var name: String
        get() = deck.getString("name")
        set(value) {
            deck.put("name", value)
        }

    var collapsed: Boolean
        get() = deck.getBoolean("collapsed")
        set(value) {
            deck.put("collapsed", value)
        }

    var id: DeckId
        get() = deck.getLong("id")
        set(value) {
            deck.put("id", value)
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
var Optional<DeckV16>.name: String
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

    var id: DeckConfigId
        get() = config.getLong("id")
        set(value) {
            config.put("id", value)
        }

    var name: String
        get() = config.getString("name")
        set(value) {
            config.put("name", value)
        }

    var dyn: Boolean
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

    /* Registry save/load */

    @Throws(DeckRenameException::class)
    private fun save(grp: DeckConfigV16) {
        when (grp) {
            is DeckConfigV16.Config -> save(grp)
            is DeckConfigV16.FilteredDeck -> save(DeckV16.FilteredDeck(grp.deckData))
        }
    }

    @RustCleanup("not in V16")
    fun save() {
        Timber.w(Exception("Decks.save() called - probably a bug"))
    }

    @Throws(DeckRenameException::class)
    fun save(g: Deck) {
        save(DeckV16.Generic(g))
    }

    fun save(g: DeckConfig) {
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

    fun addDeckLegacy(deck: DeckV16): OpChangesWithId {
        val changes = col.backend.addDeckLegacy(
            json = BackendUtils.to_json_bytes(deck.getJsonObject())
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
        return allNames(dyn = dyn, force_default = true)
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

    fun get_all_legacy(): List<DeckV16> {
        return BackendUtils.from_json_bytes(col.backend.getAllDecksLegacy())
            .objectIterable { obj -> DeckV16.Generic(obj) }
            .toList()
    }

    private fun <T> JSONObject.objectIterable(f: (JSONObject) -> T) = sequence {
        keys().forEach { k -> yield(f(getJSONObject(k))) }
    }

    fun new_deck_legacy(filtered: Boolean): DeckV16 {
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
    fun all(): List<Deck> {
        return this.get_all_legacy().map { x -> Deck(x.getJsonObject()) }
    }

    @Deprecated("decks.allIds() is deprecated, use .all_names_and_ids()")
    fun allIds(): Set<DeckId> {
        return this.allNamesAndIds().map { x -> x.id }.toSet()
    }

    @Deprecated("decks.allNames() is deprecated, use .all_names_and_ids()")
    fun allNames(dyn: Boolean = true, force_default: Boolean = true): MutableList<String> {
        return this.allNamesAndIds(
            skipEmptyDefault = !force_default,
            includeFiltered = dyn
        ).map { x ->
            x.name
        }.toMutableList()
    }

    fun collapse(did: DeckId) {
        val deck = this.get(did).toV16()
        deck.collapsed = !deck.collapsed
        this.save(deck)
    }

    fun count(): Int {
        return len(this.allNamesAndIds())
    }

    fun get(did: DeckId, _default: Boolean): Deck? {
        val deck = this.get_legacy(did)
        return when {
            deck != null -> deck
            _default -> this.get_legacy(1)
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

    /** This skips the backend undo queue, so is required instead of save()
     * to avoid clobbering the v2 review queue.
     */
    @Throws(DeckRenameException::class)
    fun update(g: Deck) {
        // we preserve USN here as this method is used for syncing and merging
        update(DeckV16.Generic(g), preserve_usn = true)
    }

    @Throws(DeckRenameException::class)
    fun rename(g: Deck, newName: String) {
        rename(DeckV16.Generic(g), newName)
    }

    /** Add or update an existing deck. Used for syncing and merging. */
    fun update(g: DeckV16, preserve_usn: Boolean = true) {
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
    fun rename(g: DeckV16, newName: String) {
        g.name = newName
        this.update(g, preserve_usn = false)
    }

    /* Deck configurations */

    /** A list of all deck config. */
    fun all_config(): List<DeckConfigV16.Config> {
        return BackendUtils.jsonToArray(col.backend.allDeckConfigLegacy())
            .jsonObjectIterable()
            .map { obj -> DeckConfigV16.Config(obj) }
            .toList()
    }

    @RustCleanup("Return v16 config - we return a typed object here")
    fun confForDid(did: DeckId): DeckConfig {
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
    fun get_config(conf_id: DeckConfigId): DeckConfigV16 {
        val jsonObject = BackendUtils.from_json_bytes(col.backend.getDeckConfigLegacy(conf_id))
        val config = DeckConfigV16.Config(jsonObject)
        return config
    }

    fun update_config(conf: DeckConfigV16, preserve_usn: Boolean = false) {
        if (preserve_usn) {
            TODO("no longer supported; need to switch to new sync code")
        }
        conf.id = col.backend.addOrUpdateDeckConfigLegacy(conf.to_json_bytes())
    }

    private fun DeckConfigV16.to_json_bytes(): ByteString {
        return BackendUtils.toByteString(this.config)
    }

    fun add_config(
        name: String
    ): DeckConfigV16 {
        val conf = newDeckConfigLegacy()
        conf.name = name
        this.update_config(conf)
        return conf
    }

    private fun newDeckConfigLegacy(): DeckConfigV16 {
        val jsonObject = BackendUtils.from_json_bytes(col.backend.newDeckConfigLegacy())
        return DeckConfigV16.Config(jsonObject)
    }

    fun add_config_returning_id(
        name: String
    ): DeckConfigId = this.add_config(name).id

    fun setConf(grp: Deck, id: Long) {
        setConf(DeckV16.Generic(grp), id)
    }

    @RustCleanup("maybe an issue here - grp was deckConfig in V16")
    fun setConf(grp: DeckV16, id: DeckConfigId) {
        grp.conf = id
        this.save(grp)
    }

    // legacy
    fun allConf() =
        all_config().map { x -> DeckConfig(x.config, x.source) }.toMutableList()

    /* Reverts to default if provided id missing */
    fun getConf(confId: DeckConfigId): DeckConfig =
        get_config(confId).let { x -> DeckConfig(x.config, x.source) }

    fun confId(name: String): Long {
        return add_config_returning_id(name)
    }

    fun updateConf(g: DeckConfig) = updateConf(DeckConfigV16.from(g), preserve_usn = false)
    fun updateConf(conf: DeckConfigV16, preserve_usn: Boolean = false) =
        update_config(conf, preserve_usn)

    /* Deck utils */

    fun name(did: DeckId, _default: Boolean): String {
        val deck = this.get(did, _default = _default).toV16Optional()
        if (deck.isPresent) {
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

    /** All children of did, as (name, id). */
    fun children(did: DeckId): TreeMap<String, DeckId> {
        val name: String = this.get(did).toV16().name
        val actv = TreeMap<String, DeckId>()
        for (g in this.allNamesAndIds()) {
            if (g.name.startsWith(name + "::")) {
                actv.put(g.name, g.id)
            }
        }
        return actv
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

    // 1 for dyn, 0 for standard
    fun isDyn(did: DeckId): Boolean {
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

    val Deck.name: String get() = this.getString("name")
    val Deck.conf: Long get() = this.getLong("conf")

    /** Remove the deck. delete any cards inside and child decks. */
    fun rem(did: DeckId) = rem(did, true)

    /** Remove the deck. Delete child decks. If cardsToo, delete any cards inside. */
    fun rem(did: DeckId, cardsToo: Boolean = true) = rem(did, cardsToo, true)

    /** An unsorted list of all deck names. */
    fun allNames() = allNames(true)

    /** Obtains the deck from the DeckID, or default if the deck was not found */
    @CheckResult
    fun get(did: DeckId): Deck = get(did, true)!!

    fun name(did: DeckId): String = name(did, _default = false)

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
            return deck.isDyn
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
