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

import androidx.annotation.CheckResult
import androidx.annotation.VisibleForTesting
import com.ichi2.libanki.Consts.DEFAULT_DECK_ID
import com.ichi2.libanki.Consts.MODEL_STD
import com.ichi2.libanki.utils.set
import com.ichi2.utils.KotlinCleanup
import com.ichi2.utils.deepClone
import com.ichi2.utils.emptyStringArray
import com.ichi2.utils.emptyStringMutableList
import org.json.JSONObject
import java.util.*
import java.util.regex.Pattern

@KotlinCleanup("lots to do")
class Note : Cloneable {

    /**
     * Should only be mutated by addNote()
     */
    var id: Long

    @get:VisibleForTesting
    var guId: String? = null
        private set
    lateinit var notetype: NotetypeJson

    var mid: Long = 0
        private set
    lateinit var tags: MutableList<String>
        private set
    lateinit var fields: MutableList<String>
        private set
    private var fMap: Map<String, Pair<Int, JSONObject>>? = null
    var usn = 0
        private set
    var mod: Int = 0
        private set

    constructor(col: Collection, id: Long) {
        this.id = id
        load(col)
    }

    constructor(notetype: NotetypeJson) {
        this.id = 0
        guId = Utils.guid64()
        this.notetype = notetype
        mid = notetype.getLong("id")
        tags = mutableListOf()
        fields = emptyStringMutableList(notetype.getJSONArray("flds").length())
        fMap = Notetypes.fieldMap(this.notetype)
    }

    fun load(col: Collection) {
        val note = col.backend.getNote(this.id)
        loadFromBackendNote(col, note)
    }

    private fun loadFromBackendNote(col: Collection, note: anki.notes.Note) {
        this.id = note.id
        this.guId = note.guid
        this.mid = note.notetypeId
        this.notetype = col.notetypes.get(mid)!! // not in libAnki
        this.mod = note.mtimeSecs
        this.usn = note.usn
        // the lists in the protobuf are NOT mutable, even though they cast to MutableList
        this.tags = note.tagsList.toMutableList()
        this.fields = note.fieldsList.toMutableList()
        this.fMap = Notetypes.fieldMap(notetype)
    }

    fun reloadModel(col: Collection) {
        notetype = col.notetypes.get(mid)!!
    }

    fun numberOfCards(col: Collection): Int {
        return col.db.queryLongScalar("SELECT count() FROM cards WHERE nid = ?", this.id).toInt()
    }

    fun cids(col: Collection): List<Long> {
        return col.db.queryLongList("SELECT id FROM cards WHERE nid = ? ORDER BY ord", this.id)
    }

    fun ephemeralCard(
        col: Collection,
        ord: Int = 0,
        customNoteType: NotetypeJson? = null,
        customTemplate: Template? = null,
        fillEmpty: Boolean = false
    ): Card {
        val card = Card(col, id = null)
        card.ord = ord
        card.did = DEFAULT_DECK_ID

        val model = customNoteType ?: notetype
        val template = if (customTemplate != null) {
            customTemplate.deepClone()
        } else {
            val index = if (model.type == MODEL_STD) ord else 0
            model.tmpls.getJSONObject(index)
        }
        // may differ in cloze case
        template["ord"] = card.ord

        val output = TemplateManager.TemplateRenderContext.fromCardLayout(
            col = col,
            note = this,
            card = card,
            notetype = model,
            template = template,
            fillEmpty = fillEmpty
        ).render()
        card.renderOutput = output
        card.setNote(this)
        return card
    }

    fun cards(col: Collection): ArrayList<Card> {
        val cards = ArrayList<Card>(cids(col).size)
        for (cid in cids(col)) {
            // each getCard access database. This is inefficient.
            // Seems impossible to solve without creating a constructor of a list of card.
            // Not a big trouble since most note have a small number of cards.
            cards.add(col.getCard(cid))
        }
        return cards
    }

    /** The first card, assuming it exists. */
    @CheckResult
    fun firstCard(col: Collection): Card {
        return col.getCard(
            col.db.queryLongScalar(
                "SELECT id FROM cards WHERE nid = ? ORDER BY ord LIMIT 1",
                this.id
            )
        )
    }

    /**
     * Dict interface
     * ***********************************************************
     */
    fun keys(): Array<String> {
        return fMap!!.keys.toTypedArray()
    }

    @KotlinCleanup("see if we can make this immutable")
    fun values(): MutableList<String> {
        return fields
    }

    fun items(): Array<Array<String>> {
        // TODO: Revisit this method. The field order returned differs from Anki.
        // The items here are only used in the note editor, so it's a low priority.
        val result = Array(
            fMap!!.size
        ) { emptyStringArray(2) }
        for (fname in fMap!!.keys) {
            val i = fMap!![fname]!!.first
            result[i][0] = fname
            result[i][1] = fields[i]
        }
        return result
    }

    private fun fieldIndex(key: String): Int {
        val fieldPair = fMap!![key]
            ?: throw IllegalArgumentException(
                String.format(
                    "No field named '%s' found",
                    key
                )
            )
        return fieldPair.first
    }

    fun getItem(key: String): String {
        return fields[fieldIndex(key)]
    }

    fun setItem(key: String, value: String) {
        fields[fieldIndex(key)] = value
    }

    operator fun contains(key: String): Boolean {
        return fMap!!.containsKey(key)
    }

    /**
     * Tags
     * ***********************************************************
     */
    fun hasTag(col: Collection, tag: String?): Boolean {
        return col.tags.inList(tag!!, tags)
    }

    fun stringTags(col: Collection): String {
        return col.tags.join(col.tags.canonify(tags))
    }

    fun setTagsFromStr(col: Collection, str: String?) {
        tags = col.tags.split(str!!)
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
    fun dupeOrEmpty(col: Collection): DupeOrEmpty {
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

    fun sFld(col: Collection): String = col.db.queryString("SELECT sfld FROM notes WHERE id = ?", this.id)

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

/** @see Note.hasTag */
context (Collection)
fun Note.hasTag(tag: String) = this.hasTag(this@Collection, tag)

/** @see Note.setTagsFromStr */
context (Collection)
fun Note.setTagsFromStr(str: String?) = this.setTagsFromStr(this@Collection, str)

/** @see Note.load */
context (Collection)
fun Note.load() = this.load(this@Collection)
