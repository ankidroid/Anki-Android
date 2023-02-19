/****************************************************************************************
 * Copyright (c) 2011 Norbert Nagold <norbert.nagold@gmail.com>                         *
 * Copyright (c) 2012 Kostas Spyropoulos <inigo.aldana@gmail.com>                       *
 * Copyright (c) 2014 Houssam Salem <houssam.salem.au@gmail.com>                        *
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
import com.ichi2.libanki.backend.model.TagUsnTuple
import com.ichi2.libanki.utils.TimeManager
import org.json.JSONObject
import java.util.*
import java.util.regex.Pattern

/**
 * Anki maintains a cache of used tags so it can quickly present a list of tags
 * for autocomplete and in the browser. For efficiency, deletions are not
 * tracked, so unused tags can only be removed from the list with a DB check.
 *
 * This module manages the tag cache and tags for notes.
 *
 * This class differs from the python version by keeping the in-memory tag cache as a TreeMap
 * instead of a JSONObject. It is much more convenient to work with a TreeMap in Java, but there
 * may be a performance penalty in doing so (on startup and shutdown).
 */
class Tags
/**
 * Registry save/load
 * ***********************************************************
 */(private val col: Collection) : TagManager() {
    private val mTags = TreeMap<String, Int?>()
    private var mChanged = false
    override fun load(json: String) {
        val tags = JSONObject(json)
        for (t in tags.keys()) {
            mTags[t] = tags.getInt(t)
        }
        mChanged = false
    }

    override fun flush() {
        if (mChanged) {
            val tags = JSONObject()
            for ((key, value) in mTags) {
                tags.put(key, value)
            }
            val contentValues = ContentValues()
            contentValues.put("tags", Utils.jsonToString(tags))
            // TODO: the database update call here sets mod = true. Verify if this is intended.
            col.db.update("col", contentValues)
            mChanged = false
        }
    }
    /*
     * Registering and fetching tags
     * ***********************************************************
     */
    /** {@inheritDoc}  */
    override fun register(tags: Iterable<String>, usn: Int?, clear_first: Boolean) {
        // boolean found = false;
        for (t in tags) {
            if (!mTags.containsKey(t)) {
                mTags[t] = usn ?: col.usn()
                mChanged = true
            }
        }
        // if (found) {
        //    runHook("newTag"); // TODO
        // }
    }

    override fun all(): List<String> {
        return ArrayList(mTags.keys)
    }

    /** Add any missing tags from notes to the tags list.  */
    override fun registerNotes(nids: kotlin.collections.Collection<Long>?) {
        // when called with a null argument, the old list is cleared first.
        val lim: String
        if (nids != null) {
            lim = " WHERE id IN " + Utils.ids2str(nids)
        } else {
            lim = ""
            mTags.clear()
            mChanged = true
        }
        val tags: MutableList<String?> = ArrayList(col.noteCount())
        col.db.query("SELECT DISTINCT tags FROM notes$lim").use { cursor ->
            while (cursor.moveToNext()) {
                tags.add(cursor.getString(0))
            }
        }
        val tagSet = HashSet(split(tags.joinToString(" ")))
        register(tagSet)
    }

    override fun allItems(): Set<TagUsnTuple> {
        return mTags.entries.map { (key, value): Map.Entry<String, Int?> ->
            TagUsnTuple(
                key,
                value!!
            )
        }.toSet()
    }

    override fun save() {
        mChanged = true
    }

    /** {@inheritDoc}  */
    override fun byDeck(did: DeckId, children: Boolean): ArrayList<String> {
        val tags: List<String?> = if (children) {
            val values: kotlin.collections.Collection<Long> = col.decks.children(did).values
            val dids = ArrayList<Long>(values.size)
            dids.add(did)
            dids.addAll(values)
            col.db.queryStringList(
                "SELECT DISTINCT n.tags FROM cards c, notes n WHERE c.nid = n.id AND c.did IN " + Utils.ids2str(
                    dids
                )
            )
        } else {
            col.db.queryStringList(
                "SELECT DISTINCT n.tags FROM cards c, notes n WHERE c.nid = n.id AND c.did = ?",
                did
            )
        }
        // Cast to set to remove duplicates
        // Use methods used to get all tags to parse tags here as well.
        return ArrayList(HashSet(split(tags.joinToString(" "))))
    }
    /*
     * Bulk addition/removal from notes
     * ***********************************************************
     */
    /** {@inheritDoc}  */
    override fun bulkAdd(ids: List<Long>, tags: String, add: Boolean) {
        val newTags: List<String> = split(tags)
        if (newTags.isEmpty()) {
            return
        }
        // cache tag names
        if (add) {
            register(newTags)
        }
        // find notes missing the tags
        val l: String = if (add) {
            "tags not "
        } else {
            "tags "
        }
        val lim = StringBuilder()
        for (t in newTags) {
            if (lim.isNotEmpty()) {
                lim.append(" or ")
            }
            val replaced = t.replace("*", "%")
            lim.append(l).append("like '% ").append(replaced).append(" %'")
        }
        val res = ArrayList<Array<Any>>(
            col.db.queryScalar(
                "select count() from notes where id in " + Utils.ids2str(ids) + " and (" + lim + ")"
            )
        )
        col
            .db
            .query(
                "select id, tags from notes where id in " + Utils.ids2str(ids) +
                    " and (" + lim + ")"
            ).use { cur ->
                if (add) {
                    while (cur.moveToNext()) {
                        res.add(
                            arrayOf(
                                addToStr(tags, cur.getString(1)),
                                TimeManager.time.intTime(),
                                col.usn(),
                                cur.getLong(0)
                            )
                        )
                    }
                } else {
                    while (cur.moveToNext()) {
                        res.add(
                            arrayOf(
                                remFromStr(tags, cur.getString(1)),
                                TimeManager.time.intTime(),
                                col.usn(),
                                cur.getLong(0)
                            )
                        )
                    }
                }
            }
        // update tags
        col.db.executeMany("update notes set tags=:t,mod=:n,usn=:u where id = :id", res)
    }

    /*
     * String-based utilities
     * ***********************************************************
     */
    /** {@inheritDoc}  */
    override fun split(tags: String): ArrayList<String> {
        val list = ArrayList<String>(tags.length)
        for (s in tags.replace('\u3000', ' ').split("\\s".toRegex()).toTypedArray()) {
            if (s.isNotEmpty()) {
                list.add(s)
            }
        }
        return list
    }

    /** {@inheritDoc}  */
    override fun join(tags: kotlin.collections.Collection<String>): String {
        return if (tags.isEmpty()) {
            ""
        } else {
            val joined = tags.joinToString(" ")
            String.format(Locale.US, " %s ", joined)
        }
    }

    /** Add tags if they don't exist, and canonify  */
    fun addToStr(addtags: String, tags: String): String {
        val currentTags: MutableList<String> = split(tags)
        for (tag in split(addtags)) {
            if (!inList(tag, currentTags)) {
                currentTags.add(tag)
            }
        }
        return join(canonify(currentTags))
    }

    // submethod of remFromStr in anki
    private fun wildcard(pat: String, str: String): Boolean {
        val patReplaced = Pattern.quote(pat).replace("\\*", ".*")
        return Pattern.compile(patReplaced, Pattern.CASE_INSENSITIVE or Pattern.UNICODE_CASE)
            .matcher(str).matches()
    }

    /** {@inheritDoc}   */
    override fun remFromStr(deltags: String, tags: String): String {
        val currentTags: MutableList<String> = split(tags)
        for (tag in split(deltags)) {
            val remove: MutableList<String> =
                ArrayList() // Usually not a lot of tags are removed simultaneously.
            // So don't put initial capacity
            for (tx in currentTags) {
                if (tag.equals(tx, ignoreCase = true) || wildcard(tag, tx)) {
                    remove.add(tx)
                }
            }
            // remove them
            for (r in remove) {
                currentTags.remove(r)
            }
        }
        return join(currentTags)
    }
    /*
     * List-based utilities
     * ***********************************************************
     */
    /** {@inheritDoc}  */
    override fun canonify(tagList: List<String>): TreeSet<String> {
        // NOTE: The python version creates a list of tags, puts them into a set, then sorts them. The TreeSet
        // used here already guarantees uniqueness and sort order, so we return it as-is without those steps.
        val strippedTags = TreeSet(java.lang.String.CASE_INSENSITIVE_ORDER)
        for (t in tagList) {
            var s = sCanonify.matcher(t).replaceAll("")
            for (existingTag in mTags.keys) {
                if (s.equals(existingTag, ignoreCase = true)) {
                    s = existingTag
                }
            }
            strippedTags.add(s)
        }
        return strippedTags
    }

    /** {@inheritDoc}  */
    override fun inList(tag: String, tags: Iterable<String>): Boolean {
        for (t in tags) {
            if (t.equals(tag, ignoreCase = true)) {
                return true
            }
        }
        return false
    }

    /**
     * Sync handling
     * ***********************************************************
     */
    override fun beforeUpload() {
        var changed = false
        for ((key, value) in mTags) {
            if (value != 0) {
                mTags[key] = 0
                changed = true
            }
        }
        if (changed) {
            save()
        }
    }
    /*
     * ***********************************************************
     * The methods below are not in LibAnki.
     * ***********************************************************
     */
    /** Add a tag to the collection. We use this method instead of exposing mTags publicly. */
    override fun add(tag: String, usn: Int?) {
        mTags[tag] = usn
    }

    /** Whether any tags have a usn of -1  */
    override fun minusOneValue(): Boolean {
        return mTags.containsValue(-1)
    }

    companion object {
        private val sCanonify = Pattern.compile("[\"']")
    }
}
