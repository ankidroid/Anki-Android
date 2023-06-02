/****************************************************************************************
 * Copyright (c) 2009 Daniel Svärd <daniel.svard@gmail.com>                             *
 * Copyright (c) 2009 Casey Link <unnamedrambler@gmail.com>                             *
 * Copyright (c) 2009 Edu Zamora <edu.zasu@gmail.com>                                   *
 * Copyright (c) 2010 Norbert Nagold <norbert.nagold@gmail.com>                         *
 * Copyright (c) 2015 Houssam Salem <houssam.salem.au@gmail.com>                        *
 * Copyright (c) 2018 Chris Williams <chris@chrispwill.com>                             *
 *                                                                                      *
 * This program is free software; you can redistribute it and/or modify it under        *
 * the terms of the GNU General Public License as published by the Free Software        *
 * Foundation; either version 3 of the License, or (at your option) any later           *
 * version.                                                                             *
 *                                                                                      *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY      *
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A      *
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.             *
 *                                                                                      *
 * You should have received a copy of the GNU General Public License along with         *
 * this program.  If not, see <http://www.gnu.org/licenses/>.                           *
 ****************************************************************************************/

package com.ichi2.libanki

import android.content.ContentValues
import androidx.annotation.CheckResult
import androidx.annotation.VisibleForTesting
import com.ichi2.anki.CrashReportService
import com.ichi2.anki.exception.ConfirmModSchemaException
import com.ichi2.libanki.Consts.DECK_STD
import com.ichi2.libanki.backend.exception.DeckRenameException
import com.ichi2.libanki.utils.TimeManager.time
import com.ichi2.utils.*
import com.ichi2.utils.HashUtil.HashMapInit
import net.ankiweb.rsdroid.RustCleanup
import org.intellij.lang.annotations.Language
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.text.Normalizer
import java.util.*
import java.util.regex.Pattern

// fixmes:
// - make sure users can't set grad interval < 1
@KotlinCleanup("IDE Lint")
@KotlinCleanup("lots to do")
@KotlinCleanup("remove unused functions")
@KotlinCleanup("where ever possible replace ArrayList() with mutableListOf()")
@KotlinCleanup("nullability")
class Decks(private val col: Collection) : DeckManager() {
    @get:RustCleanup("This exists in Rust as DecksDictProxy, but its usage is warned against")
    @KotlinCleanup("lateinit")
    @get:VisibleForTesting
    var decks: HashMap<Long, Deck>? = null
        private set
    private var mDconf: HashMap<Long, DeckConfig>? = null

    // Never access mNameMap directly. Uses byName
    @KotlinCleanup("lateinit (it's set in load)")
    private var mNameMap: NameMap? = null
    private var mChanged = false

    /**
     * A tool to quickly access decks from name. Ensure that names get properly normalized so that difference in
     * name unicode normalization or upper/lower case, is ignored during deck search.
     */
    @KotlinCleanup("nullability")
    private class NameMap private constructor(size: Int) {
        private val mNameMap: HashMap<String?, Deck>

        /**
         * @param name A name of deck to get
         * @return The deck with this name if it exists, null otherwise.
         */
        @Synchronized
        operator fun get(name: String?): Deck? {
            val normalized = normalizeName(name)
            val deck = mNameMap[normalized] ?: return null
            val foundName = deck.getString("name")
            if (!equalName(name, foundName)) {
                @KotlinCleanup("use triple quoted string")
                CrashReportService.sendExceptionReport(
                    "We looked for deck \"$name\" and instead got deck \"$foundName\".",
                    "Decks - byName"
                )
            }
            return deck
        }

        /**
         * @param g Add a deck. Allow from its name to get quick access to the deck.
         */
        @Synchronized
        fun add(g: Deck) {
            val name = g.getString("name")
            mNameMap[name] = g
            // Normalized name is also added because it's required to use it in by name.
            // Non normalized is kept for Parent
            mNameMap[normalizeName(name)] = g
        }

        /**
         * Remove name from nameMap if it is equal to expectedDeck.
         *
         * It is possible that another deck has been given name `name`,
         * in which case we don't want to remove it from nameMap.
         *
         * E.g. if A is renamed to A::B and A::B already existed and get
         * renamed to A::B::B, then we don't want to remove A::B from
         * nameMap when A::B is renamed to A::B::B, since A::B is
         * potentially the correct value.
         */
        @Synchronized
        fun remove(name: String?, expectedDeck: JSONObject) {
            val names = arrayOf(name, normalizeName(name))
            for (name_ in names) {
                val currentDeck: JSONObject? = mNameMap[name_]
                if (currentDeck != null && currentDeck.getLong("id") == expectedDeck.getLong("id")) {
                    /* Remove name from mapping only if it still maps to
                     * expectedDeck. I.e. no other deck had been given this
                     * name yet. */
                    mNameMap.remove(name_)
                }
            }
        }

        companion object {
            /**
             * @param decks The collection of decks we want to get access quickly
             * @return A name map, allowing to get decks from name
             */
            fun constructor(decks: kotlin.collections.Collection<Deck>): NameMap {
                val map = NameMap(2 * decks.size)
                for (deck in decks) {
                    map.add(deck)
                }
                return map
            }
        }

        /**
         * @param size The expected number of deck to keep
         */
        init {
            @KotlinCleanup("combine property declaration and initialization, remove init")
            mNameMap = HashMapInit(size)
        }
    }

    override fun load(@Language("JSON") decks: String, dconf: String) {
        val decksarray = JSONObject(decks)
        var ids = decksarray.names()
        this.decks = HashMapInit(decksarray.length())
        if (ids != null) {
            for (id in ids.stringIterable()) {
                val o = Deck(decksarray.getJSONObject(id))
                val longId = id.toLong()
                this.decks!!.put(longId, o)
            }
        }
        mNameMap = NameMap.constructor(this.decks!!.values)
        val confarray = JSONObject(dconf)
        ids = confarray.names()
        mDconf = HashMapInit(confarray.length())
        if (ids != null) {
            for (id in ids.stringIterable()) {
                mDconf!![id.toLong()] =
                    DeckConfig(confarray.getJSONObject(id), DeckConfig.Source.DECK_CONFIG)
            }
        }
        mChanged = false
    }

    /** {@inheritDoc}  */
    override fun save() {
        save(null as JSONObject?)
    }

    /** {@inheritDoc}  */
    override fun save(g: Deck) {
        save(g as JSONObject)
    }

    /** {@inheritDoc}  */
    override fun save(g: DeckConfig) {
        save(g as JSONObject)
    }

    private fun save(g: JSONObject?) {
        if (g != null) {
            g.put("mod", time.intTime())
            g.put("usn", col.usn())
        }
        mChanged = true
    }

    override fun flush() {
        val values = ContentValues()
        if (mChanged) {
            val decksarray = JSONObject()
            for ((key, value) in decks!!) {
                decksarray.put(java.lang.Long.toString(key), value)
            }
            values.put("decks", Utils.jsonToString(decksarray))
            val confarray = JSONObject()
            for ((key, value) in mDconf!!) {
                confarray.put(java.lang.Long.toString(key), value)
            }
            values.put("dconf", Utils.jsonToString(confarray))
            col.db.update("col", values)
            mChanged = false
        }
    }

    /**
     * Deck save/load
     * ***********************************************************
     */
    @Suppress("NAME_SHADOWING")
    @KotlinCleanup("Simplify function body")
    override fun id_for_name(name: String): Long? {
        var name = name
        name = usable_name(name)
        val deck = byName(name)
        return deck?.getLong("id")
    }

    @Throws(DeckRenameException::class)
    override fun id(name: String): Long {
        return id(name, DEFAULT_DECK)
    }

    @Suppress("NAME_SHADOWING")
    @KotlinCleanup("Simplify function body")
    private fun usable_name(name: String): String {
        var name = name
        name = strip(name)
        name = name.replace("\"", "")
        name = Normalizer.normalize(name, Normalizer.Form.NFC)
        return name
    }

    /**
     * Add a deck with NAME. Reuse deck if already exists. Return id as int.
     */
    @Throws(DeckRenameException::class)
    @Suppress("NAME_SHADOWING")
    @KotlinCleanup("Simplify function body")
    fun id(name: String, type: String): Long {
        var name = name
        name = usable_name(name)
        val id = id_for_name(name)
        if (id != null) {
            return id
        }
        if (name.contains("::")) {
            // not top level; ensure all parents exist
            name = _ensureParents(name)
        }
        return id_create_name_valid(name, type)
    }

    /**
     * @param name A name, assuming it's not a deck name, all ancestors exists and are not filtered
     * @param type The json encoding of the deck, except for name and id
     * @return the deck's id
     */
    private fun id_create_name_valid(name: String, type: String): Long {
        var id: Long
        val g = Deck(type)
        g.put("name", name)
        do {
            id = time.intTimeMS()
        } while (decks!!.containsKey(id))
        g.put("id", id)
        decks!![id] = g
        save(g)
        maybeAddToActive()
        mNameMap!!.add(g)
        // runHook("newDeck"); // TODO
        return id
    }

    /** {@inheritDoc}  */
    @Suppress("NAME_SHADOWING")
    @KotlinCleanup("Simplify function body")
    override fun id_safe(name: String, type: String): Long {
        var name = name
        name = usable_name(name)
        val id = id_for_name(name)
        if (id != null) {
            return id
        }
        if (name.contains("::")) {
            // not top level; ensure all parents exist
            name = _ensureParentsNotFiltered(name)
        }
        return id_create_name_valid(name, type)
    }

    /** {@inheritDoc}  */
    override fun rem(did: Long, cardsToo: Boolean, childrenToo: Boolean) {
        val deck = get(did, false)
        if (did == 1L) {
            // we won't allow the default deck to be deleted, but if it's a
            // child of an existing deck then it needs to be renamed
            if (deck != null && deck.getString("name").contains("::")) {
                deck.put("name", "Default")
                save(deck)
            }
            return
        }
        // log the removal regardless of whether we have the deck or not
        col._logRem(listOf(did), Consts.REM_DECK)
        // do nothing else if doesn't exist
        if (deck == null) {
            return
        }
        if (deck.isDyn) {
            // deleting a cramming deck returns cards to their previous deck
            // rather than deleting the cards
            col.sched.emptyDyn(did)
            if (childrenToo) {
                for (id in children(did).values) {
                    rem(id, cardsToo, false)
                }
            }
        } else {
            // delete children first
            if (childrenToo) {
                // we don't want to delete children when syncing
                for (id in children(did).values) {
                    rem(id, cardsToo, false)
                }
            }
            // delete cards too?
            if (cardsToo) {
                // don't use cids(col), as we want cards in cram decks too
                val cids = col.db.queryLongList(
                    "SELECT id FROM cards WHERE did = ? OR odid = ?",
                    did,
                    did
                )
                col.remCards(cids)
            }
        }
        // delete the deck and add a grave
        decks!!.remove(did)
        mNameMap!!.remove(deck.getString("name"), deck)
        // ensure we have an active deck
        if (active().contains(did)) {
            select(decks!!.keys.iterator().next())
        }
        save()
    }

    /** {@inheritDoc}  */
    @KotlinCleanup("Simplify if-for code block with forEach on decks.values")
    override fun allNames(dyn: Boolean): List<String> {
        val list: MutableList<String> = ArrayList(
            decks!!.size
        )
        if (dyn) {
            for (x in decks!!.values) {
                list.add(x.getString("name"))
            }
        } else {
            for (x in decks!!.values) {
                if (x.isStd) {
                    list.add(x.getString("name"))
                }
            }
        }
        return list
    }

    /** {@inheritDoc}  */
    override fun all(): List<Deck> {
        return ArrayList(decks!!.values)
    }

    override fun allIds(): Set<Long> {
        return decks!!.keys
    }

    override fun collapse(did: Long) {
        val deck = get(did)
        deck.put("collapsed", !deck.getBoolean("collapsed"))
        save(deck)
    }

    fun collapseBrowser(did: Long) {
        val deck = get(did)
        val collapsed = deck.optBoolean("browserCollapsed", false)
        deck.put("browserCollapsed", !collapsed)
        save(deck)
    }

    /** {@inheritDoc}  */
    override fun count(): Int {
        return decks!!.size
    }

    @CheckResult
    override operator fun get(did: Long, _default: Boolean): Deck? {
        return if (decks!!.containsKey(did)) {
            decks!![did]
        } else if (_default) {
            decks!![1L]
        } else {
            null
        }
    }

    /** {@inheritDoc}  */
    @CheckResult
    override fun byName(name: String): Deck? {
        return mNameMap!![name]
    }

    /** {@inheritDoc}  */
    override fun update(g: Deck) {
        val id = g.getLong("id")
        val oldDeck: JSONObject? = get(id, false)
        if (oldDeck != null) {
            // In case where another update got the name
            // `oldName`, it would be a mistake to remove it from nameMap
            mNameMap!!.remove(oldDeck.getString("name"), oldDeck)
        }
        mNameMap!!.add(g)
        decks!![g.getLong("id")] = g
        maybeAddToActive()
        // mark registry changed, but don't bump mod time
        save()
    }

    /** {@inheritDoc}  */
    @Suppress("NAME_SHADOWING")
    @Throws(DeckRenameException::class)
    override fun rename(g: Deck, newName: String) {
        var newName = newName
        newName = strip(newName)
        // make sure target node doesn't already exist
        val deckWithThisName = byName(newName)
        if (deckWithThisName != null) {
            if (deckWithThisName.getLong("id") != g.getLong("id")) {
                throw DeckRenameException(DeckRenameException.ALREADY_EXISTS)
            }
            /* else: We are renaming the deck to the "same"
             * name. I.e. case may varie, normalization may be
             * different, but anki essentially consider that the name
             * did not change. We still need to run the remaining of
             * the code in order do this change. */
        }
        // ensure we have parents and none is a filtered deck
        newName = _ensureParents(newName)

        // rename children
        val oldName = g.getString("name")
        for (grp in all()) {
            val grpOldName = grp.getString("name")
            if (grpOldName.startsWith("$oldName::")) {
                val grpNewName =
                    grpOldName.replaceFirst(Pattern.quote("$oldName::").toRegex(), "$newName::")
                // In Java, String.replaceFirst consumes a regex so we need to quote the pattern to be safe
                mNameMap!!.remove(grpOldName, grp)
                grp.put("name", grpNewName)
                mNameMap!!.add(grp)
                save(grp)
            }
        }
        mNameMap!!.remove(oldName, g)
        // adjust name
        g.put("name", newName)
        // ensure we have parents again, as we may have renamed parent->child
        // No ancestor can be filtered after renaming
        @Suppress("UNUSED_VALUE") // TODO: Maybe a bug
        newName = _ensureParentsNotFiltered(newName)
        mNameMap!!.add(g)
        save(g)
        // renaming may have altered active did order
        maybeAddToActive()
    }

    /* Buggy implementation. Keep as first draft if we want to use it again
    public void renameForDragAndDrop(Long draggedDeckDid, Long ontoDeckDid) throws DeckRenameException {
        Deck draggedDeck = get(draggedDeckDid);
        String draggedDeckName = draggedDeck.getString("name");
        String ontoDeckName = get(ontoDeckDid).getString("name");

        String draggedBasename = basename(draggedDeckName);
        if (ontoDeckDid == null) {
            if (!draggedBasename.equals(draggedDeckName)) {
                rename(draggedDeck, draggedBasename);
            }
        } else if (_canDragAndDrop(draggedDeckName, ontoDeckName)) {
            rename(draggedDeck, ontoDeckName + "::" + draggedBasename);
        }
    }


    private boolean _canDragAndDrop(String draggedDeckName, String ontoDeckName) {
        if (draggedDeckName.equals(ontoDeckName)
                || _isParent(ontoDeckName, draggedDeckName)
                || _isAncestor(draggedDeckName, ontoDeckName)) {
            return false;
        } else {
            return true;
        }
    }
    */
    @KotlinCleanup("make all methods that work on deck names only static. Maybe group them in a separate place since they are not related to the actual collections.")
    private fun _isParent(parentDeckName: String, childDeckName: String): Boolean {
        val parentDeckPath = path(parentDeckName)
        val childDeckPath = path(childDeckName)
        if (parentDeckPath.size + 1 != childDeckPath.size) {
            return false
        }
        @KotlinCleanup("improve (maybe indices.all & use `==`)")
        for (i in parentDeckPath.indices) {
            if (parentDeckPath[i] != childDeckPath[i]) {
                return false
            }
        }
        return true
    }

    private fun _isAncestor(ancestorDeckName: String, descendantDeckName: String): Boolean {
        val ancestorDeckPath = path(ancestorDeckName)
        val descendantDeckPath = path(descendantDeckName)
        if (ancestorDeckPath.size > descendantDeckPath.size) {
            return false
        }
        @KotlinCleanup(".all and ==")
        for (i in ancestorDeckPath.indices) {
            if (!Utils.equals(ancestorDeckPath[i], descendantDeckPath[i])) {
                return false
            }
        }
        return true
    }

    /**
     *
     * @param name The name whose parents should exists
     * @return The name, with potentially change in capitalization and unicode normalization, so that the parent's name corresponds to an existing deck.
     * @throws DeckRenameException if a parent is filtered
     */
    @VisibleForTesting
    @Throws(DeckRenameException::class)
    @Suppress("NAME_SHADOWING")
    internal fun _ensureParents(name: String): String {
        var name = name
        var s = ""
        val path = path(name)
        if (path.size < 2) {
            return name
        }
        for (i in 0 until path.size - 1) {
            var p = path[i]
            // Fix bugs in issue #11026
            // Extra check if the parent name was blank when deck is created
            if ("" == p) {
                p = "blank"
            }
            s += if (s.isEmpty()) {
                p
            } else {
                "::$p"
            }
            // fetch or create
            val did = id(s)
            // get original case
            s = name(did)
            val deck = get(did)
            if (deck.isDyn) {
                throw DeckRenameException.filteredAncestor(name, s)
            }
        }
        val lastDeck = path[path.size - 1]
        name = s + "::" + if (lastDeck.isEmpty()) "blank" else lastDeck
        return name
    }

    /** {@inheritDoc}  */
    @VisibleForTesting
    @Suppress("NAME_SHADOWING")
    internal fun _ensureParentsNotFiltered(name: String): String {
        var name = name
        var s = ""
        val path = path(name)
        if (path.size < 2) {
            return name
        }
        for (i in 0 until path.size - 1) {
            val p = path[i]
            s += if (s.isEmpty()) {
                p
            } else {
                "::$p"
            }
            var did = id_safe(s)
            var deck = get(did)
            s = name(did)
            while (deck.isDyn) {
                s = "$s'"
                // fetch or create
                did = id_safe(s)
                // get original case
                s = name(did)
                deck = get(did)
            }
        }
        name = s + "::" + path[path.size - 1]
        return name
    }
    /*
      Deck configurations
      ***********************************************************
     */
    /** {@inheritDoc}  */
    override fun allConf(): List<DeckConfig> {
        return ArrayList(mDconf!!.values)
    }

    override fun confForDid(did: Long): DeckConfig {
        val deck = get(did, false)!!
        if (deck.has("conf")) {
            // fall back on default
            @KotlinCleanup("Clarify comment. It doesn't make sense when using :?")
            val conf = getConf(deck.getLong("conf")) ?: getConf(1L)!!
            return conf.apply {
                put("dyn", DECK_STD)
            }
        }
        // dynamic decks have embedded conf
        return DeckConfig(deck, DeckConfig.Source.DECK_EMBEDDED)
    }

    override fun getConf(confId: Long): DeckConfig? {
        return mDconf!![confId]
    }

    override fun updateConf(g: DeckConfig) {
        mDconf!![g.getLong("id")] = g
        save()
    }

    /** {@inheritDoc}  */
    override fun confId(name: String, cloneFrom: String): Long {
        var id: Long
        val c = DeckConfig(cloneFrom, DeckConfig.Source.DECK_CONFIG)
        do {
            id = time.intTimeMS()
        } while (mDconf!!.containsKey(id))
        c.put("id", id)
        c.put("name", name)
        mDconf!![id] = c
        save(c)
        return id
    }

    /** {@inheritDoc}  */
    @Throws(ConfirmModSchemaException::class)
    override fun remConf(id: Long) {
        assert(id != 1L)
        col.modSchema()
        mDconf!!.remove(id)
        @KotlinCleanup("filter and map")
        for (g in all()) {
            // ignore cram decks
            if (!g.has("conf")) {
                continue
            }
            if (g.getString("conf") == java.lang.Long.toString(id)) {
                g.put("conf", 1)
                save(g)
            }
        }
    }

    override fun setConf(grp: Deck, id: Long) {
        grp.put("conf", id)
        save(grp)
    }

    @KotlinCleanup("filter + map")
    override fun didsForConf(conf: DeckConfig): List<Long> {
        val dids: MutableList<Long> = ArrayList()
        for (deck in decks!!.values) {
            if (deck.has("conf") && deck.getLong("conf") == conf.getLong("id")) {
                dids.add(deck.getLong("id"))
            }
        }
        return dids
    }

    @RustCleanup("use backend method")
    override fun restoreToDefault(conf: DeckConfig) {
        val oldOrder = conf.getJSONObject("new").getInt("order")
        val _new = DeckConfig(DEFAULT_CONF, DeckConfig.Source.DECK_CONFIG)
        _new.put("id", conf.getLong("id"))
        _new.put("name", conf.getString("name"))
        updateConf(_new)
        // if it was previously randomized, resort
        KotlinCleanup("replace 0 by constant to mean random/standard")
        if (oldOrder == 0) {
            col.sched.resortConf(_new)
        }
    }

    /**
     * Deck utils
     * ***********************************************************
     */
    override fun name(did: Long, _default: Boolean): String {
        val deck = get(did, _default)
        return deck?.getString("name") ?: "[no deck]"
    }

    fun nameOrNone(did: Long): String? {
        val deck = get(did, false)
        return deck?.getString("name")
    }

    private fun maybeAddToActive() {
        // reselect current deck, or default if current has disappeared
        val c = current()
        select(c.getLong("id"))
    }

    override fun cids(did: Long, children: Boolean): MutableList<Long> {
        if (!children) {
            return col.db.queryLongList("select id from cards where did=?", did)
        }
        @KotlinCleanup("simplify with listOf(did) + values")
        val values: kotlin.collections.Collection<Long> = children(did).values
        val dids: MutableList<Long> = ArrayList(values.size + 1)
        dids.add(did)
        dids.addAll(values)
        return col.db.queryLongList("select id from cards where did in " + Utils.ids2str(dids))
    }

    private fun _recoverOrphans() {
        val mod = col.db.mod
        SyncStatus.ignoreDatabaseModification {
            @KotlinCleanup("maybe change concat to interpolation")
            col.db.execute(
                "update cards set did = 1 where did not in " + Utils.ids2str(
                    allIds()
                )
            )
        }
        col.db.mod = mod
    }

    private fun _checkDeckTree() {
        val sortedDecks = allSorted()
        val names: MutableMap<String?, Deck?> = HashMapInit(sortedDecks.size)
        for (deck in sortedDecks) {
            var deckName = deck.getString("name")

            /* With 2.1.28, anki started strips whitespace of deck name.  This method paragraph is here for
              compatibility while we wait for rust.  It should be executed before other changes, because both "FOO "
              and "FOO" will be renamed to the same name, and so this will need to be renamed again in case of
              duplicate.*/
            val strippedName = strip(deckName)
            if (deckName != strippedName) {
                mNameMap!!.remove(deckName, deck)
                deckName = strippedName
                deck.put("name", deckName)
                mNameMap!!.add(deck)
                save(deck)
            }

            // ensure no sections are blank
            if ("" == deckName) {
                Timber.i("Fix deck with empty name")
                mNameMap!!.remove(deckName, deck)
                deckName = "blank"
                deck.put("name", "blank")
                mNameMap!!.add(deck)
                save(deck)
            }
            if (deckName.contains("::::")) {
                Timber.i("fix deck with missing sections %s", deck.getString("name"))
                mNameMap!!.remove(deckName, deck)
                do {
                    deckName = deck.getString("name").replace("::::", "::blank::")
                    // We may need to iterate, in order to replace "::::::" and adding to "blank" in it.
                } while (deckName.contains("::::"))
                deck.put("name", deckName)
                mNameMap!!.add(deck)
                save(deck)
            }

            // two decks with the same name?
            val homonym = names[normalizeName(deckName)]
            if (homonym != null) {
                Timber.i("fix duplicate deck name %s", deckName)
                do {
                    deckName += "+"
                    deck.put("name", deckName)
                } while (names.containsKey(normalizeName(deckName)))
                mNameMap!!.add(deck)
                mNameMap!!.add(homonym) // Ensuring both names are correctly in mNameMap
                save(deck)
            }

            // immediate parent must exist
            val immediateParent = parent(deckName)
            if (immediateParent != null && !names.containsKey(normalizeName(immediateParent))) {
                Timber.i("fix deck with missing parent %s", deckName)
                val parent = byName(immediateParent)
                _ensureParentsNotFiltered(deckName)
                names[normalizeName(immediateParent)] = parent
            }
            names[normalizeName(deckName)] = deck
        }
    }

    override fun checkIntegrity() {
        _recoverOrphans()
        _checkDeckTree()
    }
    /*
      Deck selection
      ***********************************************************
     */
    /** {@inheritDoc}  */
    override fun active(): LinkedList<Long> {
        val activeDecks = col.get_config_array(ACTIVE_DECKS)
        val result = LinkedList<Long>()
        result.addAll(activeDecks.longIterable())
        return result
    }

    /** {@inheritDoc}  */
    override fun selected(): Long {
        return col.get_config_long(CURRENT_DECK)
    }

    @Suppress("SENSELESS_COMPARISON") // get(selected)
    override fun current(): Deck {
        if (get(selected()) == null || !decks!!.containsKey(selected())) {
            select(Consts.DEFAULT_DECK_ID) // Select default deck if the selected deck is null
        }
        return get(selected())
    }

    /** {@inheritDoc}  */
    override fun select(did: Long) {
        val name = decks!![did]!!.getString("name")

        // current deck
        col.set_config(CURRENT_DECK, did)
        // and active decks (current + all children)
        val actv = children(did) // Note: TreeMap is already sorted
        actv[name] = did
        val activeDecks = JSONArray()
        for (n in actv.values) {
            activeDecks.put(n)
        }
        col.set_config(ACTIVE_DECKS, activeDecks)
    }

    /** {@inheritDoc}  */
    override fun children(did: Long): TreeMap<String, Long> {
        val name = get(did).getString("name")
        val actv = TreeMap<String, Long>()
        @KotlinCleanup("filter and map")
        for (g in all()) {
            if (g.getString("name").startsWith("$name::")) {
                actv[g.getString("name")] = g.getLong("id")
            }
        }
        return actv
    }

    class Node : HashMap<Long?, Node?>()

    @KotlinCleanup("nullability")
    private fun gather(node: Node?, arr: MutableList<Long?>) {
        for ((key, child) in node!!) {
            arr.add(key)
            gather(child, arr)
        }
    }

    @KotlinCleanup("fix nullability")
    override fun childDids(did: Long, childMap: Node): List<Long> {
        val arr: MutableList<Long?> = ArrayList()
        gather(childMap[did], arr)
        return arr.requireNoNulls()
    }

    override fun childMap(): Node {
        val childMap = Node()

        // Go through all decks, sorted by name
        val decks = all()
        Collections.sort(decks, DeckComparator.INSTANCE)
        for (deck in decks) {
            val node = Node()
            childMap[deck.getLong("id")] = node
            val parts = listOf(*path(deck.getString("name")))
            if (parts.size > 1) {
                val immediateParent = parts.subList(0, parts.size - 1).joinToString("::")
                val pid = byName(immediateParent)!!.getLong("id")
                childMap[pid]!![deck.getLong("id")] = node
            }
        }
        return childMap
    }

    /**
     * @return Names of ancestors of parents of name.
     */
    @KotlinCleanup("see if function can return Array")
    private fun parentsNames(name: String): Array<String?> {
        val parts = path(name)
        val parentsNames = arrayOfNulls<String>(parts.size - 1)
        // Top level names have no parent, so it returns an empty list.
        // So the array size is 1 less than the number of parts.
        var prefix = ""
        for (i in 0 until parts.size - 1) {
            prefix += parts[i]
            parentsNames[i] = prefix
            prefix += "::"
        }
        return parentsNames
    }

    /** {@inheritDoc}  */
    override fun parents(did: Long): List<Deck> {
        // get parent and grandparent names
        val parents = parentsNames(get(did).getString("name"))
        // convert to objects
        val oParents: MutableList<Deck> = ArrayList(parents.size)
        for (i in parents.indices) {
            val parentName = parents[i]
            val deck = mNameMap!![parentName]!!
            oParents.add(i, deck)
        }
        return oParents
    }

    /**
     * Sync handling
     * ***********************************************************
     */
    override fun beforeUpload() {
        val changed_decks = Utils.markAsUploaded(all())
        val changed_conf = Utils.markAsUploaded(allConf())
        if (changed_decks || changed_conf) {
            // shouldSave should always be called on both lists, for
            // its side effect. Thus the disjunction should not be
            // directly applied to the methods.
            save()
        }
    }

    /*
      Dynamic decks
     */
    /** {@inheritDoc}  */
    @Throws(DeckRenameException::class)
    @KotlinCleanup("maybe use 'apply'")
    override fun newDyn(name: String): Long {
        val did = id(name, defaultDynamicDeck)
        select(did)
        return did
    }

    @KotlinCleanup("convert to expression body")
    override fun isDyn(did: Long): Boolean {
        return get(did).isDyn
    }

    override fun update_active() {
        // intentionally blank
    }

    companion object {
        /** Invalid id, represents an id on an unfound deck  */
        const val NOT_FOUND_DECK_ID = -1L

        /** Configuration saving the current deck  */
        const val CURRENT_DECK = "curDeck"

        /** Configuration saving the set of active decks (i.e. current decks and its descendants)  */
        const val ACTIVE_DECKS = "activeDecks"

        // not in libAnki
        const val DECK_SEPARATOR = "::"

        @KotlinCleanup("Maybe use triple quotes and @language? for these properties")
        const val DEFAULT_DECK = (
            "" +
                "{" +
                "\"newToday\": [0, 0]," + // currentDay, count
                "\"revToday\": [0, 0]," +
                "\"lrnToday\": [0, 0]," +
                "\"timeToday\": [0, 0]," + // time in ms
                "\"conf\": 1," +
                "\"usn\": 0," +
                "\"desc\": \"\"," +
                "\"dyn\": 0," + // anki uses int/bool interchangeably here
                "\"collapsed\": false," +
                "\"browserCollapsed\": false," + // added in beta11
                "\"extendNew\": 0," +
                "\"extendRev\": 0" +
                "}"
            )
        private const val defaultDynamicDeck = (
            "" +
                "{" +
                "\"newToday\": [0, 0]," +
                "\"revToday\": [0, 0]," +
                "\"lrnToday\": [0, 0]," +
                "\"timeToday\": [0, 0]," +
                "\"collapsed\": false," +
                "\"dyn\": 1," +
                "\"desc\": \"\"," +
                "\"usn\": 0," +
                "\"delays\": null," +
                "\"separate\": true," + // list of (search, limit, order); we only use first element for now
                "\"terms\": [[\"\", 100, 0]]," +
                "\"resched\": true," +
                "\"previewDelay\": 10," +
                "\"browserCollapsed\": false" +
                "}"
            )
        const val DEFAULT_CONF = (
            "" +
                "{" +
                "\"name\": \"Default\"," +
                "\"dyn\": false," + // previously optional. Default was false
                "\"new\": {" +
                "\"delays\": [1, 10]," +
                "\"ints\": [1, 4, 7]," + // 7 is not currently used
                "\"initialFactor\": " + Consts.STARTING_FACTOR + "," +
                "\"order\": " + Consts.NEW_CARDS_DUE + "," +
                "\"perDay\": 20," + // may not be set on old decks
                "\"bury\": false" +
                "}," +
                "\"lapse\": {" +
                "\"delays\": [10]," +
                "\"mult\": 0," +
                "\"minInt\": 1," +
                "\"leechFails\": 8," + // type 0=suspend, 1=tagonly
                "\"leechAction\": " + Consts.LEECH_TAGONLY +
                "}," +
                "\"rev\": {" +
                "\"perDay\": 200," +
                "\"ease4\": 1.3," +
                "\"hardFactor\": 1.2," +
                "\"ivlFct\": 1," +
                "\"maxIvl\": 36500," + // may not be set on old decks
                "\"bury\": false" +
                "}," +
                "\"maxTaken\": 60," +
                "\"timer\": 0," +
                "\"autoplay\": true," +
                "\"replayq\": true," +
                "\"mod\": 0," +
                "\"usn\": 0" +
                "}"
            )
        private val pathCache = HashMap<String, Array<String>>()
        fun path(name: String): Array<String> {
            if (!pathCache.containsKey(name)) {
                pathCache[name] = name.split("::".toRegex()).toTypedArray()
            }
            return pathCache[name]!!
        }

        fun basename(name: String): String {
            val path = path(name)
            return path[path.size - 1]
        }

        private val spaceAroundSeparator = Pattern.compile("\\s*::\\s*")

        @Suppress("NAME_SHADOWING")
        @VisibleForTesting
        fun strip(deckName: String): String {
            // Ends of components are either the ends of the deck name, or near the ::.
            // Deal with all spaces around ::
            var deckName = deckName
            deckName = spaceAroundSeparator.matcher(deckName).replaceAll("::")
            // Deal with spaces at start/end of the deck name.
            deckName = deckName.trim { it <= ' ' }
            return deckName
        }

        /*
     * ******************************
     * utils methods
     * **************************************
     */
        private val normalized = HashMap<String?, String>()

        @KotlinCleanup("nullability")
        fun normalizeName(name: String?): String? {
            if (!normalized.containsKey(name)) {
                normalized[name] = Normalizer.normalize(name, Normalizer.Form.NFC).lowercase()
            }
            return normalized[name]
        }

        @KotlinCleanup("nullability")
        fun equalName(name1: String?, name2: String?): Boolean {
            return normalizeName(name1) == normalizeName(name2)
        }

        /*
    * ***********************************************************
    * The methods below are not in LibAnki.
    * ***********************************************************
    */
        @KotlinCleanup("nullability")
        fun isValidDeckName(deckName: String?): Boolean {
            return deckName != null && !deckName.trim { it <= ' ' }.isEmpty()
        }

        private val sParentCache = HashMap<String, String?>()
        fun parent(deckName: String): String? {
            // method parent, from sched's method deckDueList in python
            if (!sParentCache.containsKey(deckName)) {
                var parts = listOf(*path(deckName))
                if (parts.size < 2) {
                    sParentCache[deckName] = null
                } else {
                    parts = parts.subList(0, parts.size - 1)
                    val parentName = parts.joinToString("::")
                    sParentCache[deckName] = parentName
                }
            }
            return sParentCache[deckName]
        }

        fun isDynamic(col: Collection, deckId: Long): Boolean {
            return isDynamic(col.decks.get(deckId))
        }

        fun isDynamic(deck: Deck): Boolean {
            return deck.isDyn
        }
    }
}
