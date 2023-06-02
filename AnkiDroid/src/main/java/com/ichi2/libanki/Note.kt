/****************************************************************************************
 * Copyright (c) 2011 Norbert Nagold <norbert.nagold@gmail.com>                         *
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

import androidx.annotation.VisibleForTesting
import com.ichi2.libanki.utils.TimeManager.time
import com.ichi2.utils.BlocksSchemaUpgrade
import com.ichi2.utils.KotlinCleanup
import net.ankiweb.rsdroid.BackendFactory
import net.ankiweb.rsdroid.BackendFactory.defaultLegacySchema
import org.json.JSONObject
import timber.log.Timber
import java.util.AbstractSet
import java.util.regex.Pattern

@KotlinCleanup("lots to do")
class Note : Cloneable {
    val col: Collection

    /**
     * Should only be mutated by addNote()
     */
    var id: Long

    @get:VisibleForTesting
    var guId: String? = null
        private set
    private lateinit var mModel: Model

    var mid: Long = 0
        private set
    lateinit var tags: ArrayList<String>
        private set
    lateinit var fields: Array<String>
        private set
    private var mFlags = 0
    private var mData: String? = null
    private var mFMap: Map<String, Pair<Int, JSONObject>>? = null
    private var mScm: Long = 0
    var usn = 0
        private set
    var mod: Long = 0
        private set
    private var mNewlyAdded = false

    constructor(col: Collection, id: Long) {
        this.col = col
        this.id = id
        load()
    }

    constructor(col: Collection, model: Model) {
        this.col = col
        this.id = if (BackendFactory.defaultLegacySchema) {
            time.timestampID(col.db, "notes")
        } else {
            0
        }
        guId = Utils.guid64()
        mModel = model
        mid = model.getLong("id")
        tags = ArrayList()
        fields = Array(model.getJSONArray("flds").length()) { "" }
        mFlags = 0
        mData = ""
        mFMap = Models.fieldMap(mModel)
        mScm = col.scm
    }

    fun load() {
        Timber.d("load()")
        col.db
            .query(
                "SELECT guid, mid, mod, usn, tags, flds, flags, data FROM notes WHERE id = ?",
                this.id
            ).use { cursor ->
                if (!cursor.moveToFirst()) {
                    throw WrongId(this.id, "note")
                }
                guId = cursor.getString(0)
                mid = cursor.getLong(1)
                mod = cursor.getLong(2)
                usn = cursor.getInt(3)
                tags = ArrayList(col.tags.split(cursor.getString(4)))
                fields = Utils.splitFields(cursor.getString(5))
                mFlags = cursor.getInt(6)
                mData = cursor.getString(7)
                mModel = col.models.get(mid)!!
                mFMap = Models.fieldMap(mModel)
                mScm = col.scm
            }
    }

    fun reloadModel() {
        mModel = col.models.get(mid)!!
    }

    /*
     * If fields or tags have changed, write changes to disk.
     */
    @BlocksSchemaUpgrade("new path must update to native note adding/updating routine")
    fun flush(mod: Long? = null, changeUsn: Boolean = true) {
        assert(mScm == col.scm)
        preFlush()
        if (changeUsn) {
            usn = col.usn()
        }
        val csumAndStrippedFieldField = Utils.sfieldAndCsum(
            fields,
            col.models.sortIdx(mModel)
        )
        val sfld = csumAndStrippedFieldField.first
        val tags = stringTags()
        val fields = joinedFields()
        if (mod == null && col.db.queryScalar(
                "select 1 from notes where id = ? and tags = ? and flds = ?",
                this.id.toString(),
                tags,
                fields
            ) > 0
        ) {
            return
        }
        val csum = csumAndStrippedFieldField.second
        this.mod = mod ?: time.intTime()
        col.db.execute(
            "insert or replace into notes values (?,?,?,?,?,?,?,?,?,?,?)",
            this.id, guId!!, mid, this.mod, usn, tags, fields, sfld, csum, mFlags, mData!!
        )
        if (defaultLegacySchema) {
            col.tags.register(this.tags)
        } else {
            // TODO: tags are not registered; calling code must switch to using backend add/update notes
        }
        postFlush()
    }

    private fun joinedFields(): String {
        return Utils.joinFields(fields)
    }

    fun numberOfCards(): Int {
        return col.db.queryLongScalar("SELECT count() FROM cards WHERE nid = ?", this.id).toInt()
    }

    fun cids(): List<Long> {
        return col.db.queryLongList("SELECT id FROM cards WHERE nid = ? ORDER BY ord", this.id)
    }

    fun cards(): ArrayList<Card> {
        val cards = ArrayList<Card>(cids().size)
        for (cid in cids()) {
            // each getCard access database. This is inefficient.
            // Seems impossible to solve without creating a constructor of a list of card.
            // Not a big trouble since most note have a small number of cards.
            cards.add(col.getCard(cid))
        }
        return cards
    }

    /** The first card, assuming it exists. */
    fun firstCard(): Card {
        return col.getCard(
            col.db.queryLongScalar(
                "SELECT id FROM cards WHERE nid = ? ORDER BY ord LIMIT 1",
                this.id
            )
        )
    }

    @KotlinCleanup("replace with variable")
    fun model(): Model {
        return mModel
    }

    /**
     * Dict interface
     * ***********************************************************
     */
    fun keys(): Array<String> {
        return mFMap!!.keys.toTypedArray()
    }

    fun values(): Array<String> {
        return fields
    }

    @KotlinCleanup("make non-null")
    fun items(): Array<Array<String?>> {
        // TODO: Revisit this method. The field order returned differs from Anki.
        // The items here are only used in the note editor, so it's a low priority.
        val result = Array(
            mFMap!!.size
        ) { arrayOfNulls<String>(2) }
        for (fname in mFMap!!.keys) {
            val i = mFMap!![fname]!!.first
            result[i][0] = fname
            result[i][1] = fields[i]
        }
        return result
    }

    private fun fieldOrd(key: String): Int {
        val fieldPair = mFMap!![key]
            ?: throw IllegalArgumentException(
                String.format(
                    "No field named '%s' found",
                    key
                )
            )
        return fieldPair.first
    }

    fun getItem(key: String): String {
        return fields[fieldOrd(key)]
    }

    fun setItem(key: String, value: String) {
        fields[fieldOrd(key)] = value
    }

    operator fun contains(key: String): Boolean {
        return mFMap!!.containsKey(key)
    }

    /**
     * Tags
     * ***********************************************************
     */
    fun hasTag(tag: String?): Boolean {
        return col.tags.inList(tag!!, tags)
    }

    fun stringTags(): String {
        return col.tags.join(col.tags.canonify(tags))
    }

    fun setTagsFromStr(str: String?) {
        tags = ArrayList(col.tags.split(str!!))
    }

    fun delTag(tag: String?) {
        val rem: MutableList<String> = ArrayList(
            tags.size
        )
        for (t in tags) {
            if (t.equals(tag, ignoreCase = true)) {
                rem.add(t)
            }
        }
        for (r in rem) {
            tags.remove(r)
        }
    }

    /*
     *  duplicates will be stripped on save
     */
    fun addTag(tag: String) {
        tags.add(tag)
    }

    fun addTags(tags: AbstractSet<String>?) {
        tags!!.addAll(tags)
    }

    /**
     * Unique/duplicate check
     * ***********************************************************
     */
    enum class DupeOrEmpty {
        CORRECT, EMPTY, DUPE
    }

    /**
     *
     * @return whether it has no content, dupe first field, or nothing remarkable.
     */
    fun dupeOrEmpty(): DupeOrEmpty {
        if (fields[0].trim { it <= ' ' }.isEmpty()) {
            return DupeOrEmpty.EMPTY
        }
        val csumAndStrippedFieldField = Utils.sfieldAndCsum(
            fields,
            0
        )
        val csum = csumAndStrippedFieldField.second
        // find any matching csums and compare
        val strippedFirstField = csumAndStrippedFieldField.first
        val fields = col.db.queryStringList(
            "SELECT flds FROM notes WHERE csum = ? AND id != ? AND mid = ?",
            csum,
            this.id,
            mid
        )
        for (flds in fields) {
            if (Utils.stripHTMLMedia(
                    Utils.splitFields(flds)[0]
                ) == strippedFirstField
            ) {
                return DupeOrEmpty.DUPE
            }
        }
        return DupeOrEmpty.CORRECT
    }

    /**
     * Flushing cloze notes
     * ***********************************************************
     */
    /*
     * have we been added yet?
     */
    private fun preFlush() {
        mNewlyAdded = col.db.queryScalar("SELECT 1 FROM cards WHERE nid = ?", this.id) == 0
    }

    /*
     * generate missing cards
     */
    private fun postFlush() {
        if (!mNewlyAdded) {
            col.genCards(this.id, mModel)
        }
    }

    fun sFld() = col.db.queryString("SELECT sfld FROM notes WHERE id = ?", this.id)

    fun setField(index: Int, value: String) {
        fields[index] = value
    }

    public override fun clone(): Note {
        return try {
            super.clone() as Note
        } catch (e: CloneNotSupportedException) {
            throw RuntimeException(e)
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || javaClass != other.javaClass) return false
        val note = other as Note
        return this.id == note.id
    }

    override fun hashCode(): Int {
        return (this.id xor (this.id ushr 32)).toInt()
    }

    object ClozeUtils {
        private val mClozeRegexPattern = Pattern.compile("\\{\\{c(\\d+)::")

        /**
         * Calculate the next number that should be used if inserting a new cloze deletion.
         * Per the manual the next number should be greater than any existing cloze deletion
         * even if there are gaps in the sequence, and regardless of existing cloze ordering
         *
         * @param fieldValues Iterable of field values that may contain existing cloze deletions
         * @return the next index that a cloze should be inserted at
         */
        @KotlinCleanup("general regex fixes for '.group' being nullable")
        fun getNextClozeIndex(fieldValues: Iterable<String>): Int {
            var highestClozeId = 0
            // Begin looping through the fields
            for (fieldLiteral in fieldValues) {
                // Begin searching in the current field for cloze references
                val matcher = mClozeRegexPattern.matcher(fieldLiteral)
                while (matcher.find()) {
                    val detectedClozeId = matcher.group(1)!!.toInt()
                    if (detectedClozeId > highestClozeId) {
                        highestClozeId = detectedClozeId
                    }
                }
            }
            return highestClozeId + 1
        }
    }
}
