//noinspection MissingCopyrightHeader #8659
package com.ichi2.libanki.importer

import android.text.TextUtils
import android.util.Pair
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import com.ichi2.anki.AnkiDroidApp
import com.ichi2.anki.R
import com.ichi2.libanki.*
import com.ichi2.libanki.template.ParsedNode
import com.ichi2.libanki.utils.StringUtils
import com.ichi2.utils.Assert
import com.ichi2.utils.HashUtil
import com.ichi2.utils.HtmlUtils
import com.ichi2.utils.JSONObject

// Ported from https://github.com/ankitects/anki/blob/50fdf9b03dec33c99a501f332306f378db5eb4ea/pylib/anki/importing/noteimp.py
// Aside from 9f676dbe0b2ad9b87a3bf89d7735b4253abd440e, which allows empty notes.
open class NoteImporter(col: com.ichi2.libanki.Collection, file: String?) : Importer(col, file) {
    private val mNeedMapper = true
    private val mNeedDelimiter = false
    private var mAllowHTML = false
    private var mImportMode = ImportMode.UPDATE_MODE

    /** Note: elements can be null  */
    private var mMapping: List<String?>?
    private val mTagModified: String?
    private val mModel: Model? = col.models.current()

    /** _tagsMapped in python  */
    private var mTagsMapped: Boolean

    /** _fmap in Python  */
    private var mFMap: Map<String?, Pair<Int, JSONObject>>? = null

    /** _nextID in python  */
    private var mNextId: Long = 0
    private var mIds: ArrayList<Long>? = null
    private var mEmptyNotes = false
    private var mUpdateCount = 0
    private val mTemplateParsed: List<ParsedNode> = mModel!!.parsedNodes()
    override fun run() {
        Assert.that(mMapping != null)
        Assert.that(mMapping!!.isNotEmpty())
        val c = foreignNotes()
        importNotes(c)
    }

    /** The number of fields. */
    open fun fields(): Int {
        return 0
    }

    fun initMapping() {
        var flds = mModel!!.fieldsNames
        // truncate to provided count
        flds = flds.subList(0, Math.min(flds.size, fields()))
        // if there's room left, add tags
        if (fields() > flds.size) {
            flds.add(TAGS_IDENTIFIER)
        }
        // and if there's still room left, pad
        val iterations = fields() - flds.size
        for (i in 0 until iterations) {
            flds.add(null)
        }
        mMapping = flds
    }

    fun mappingOk(): Boolean {
        return mMapping!!.contains(mModel!!.getJSONArray("flds").getJSONObject(0).getString("name"))
    }

    protected open fun foreignNotes(): List<ForeignNote> {
        return ArrayList()
    }

    /** Open file and ensure it's in the right format.  */
    protected open fun open() {
        // intentionally empty
    }

    /** Closes the open file.  */
    protected fun close() {
        // intentionally empty
    }

    /** Convert each card into a note, apply attributes and add to col.  */
    fun importNotes(notes: List<ForeignNote>) {
        Assert.that(mappingOk())
        // note whether tags are mapped
        mTagsMapped = false
        for (f in mMapping!!) {
            if (TAGS_IDENTIFIER == f) {
                mTagsMapped = true
                break
            }
        }
        // gather checks for duplicate comparison
        val csums = HashMap<Long, MutableList<Long>>()
        mCol.db.query("select csum, id from notes where mid = ?", mModel!!.getLong("id")).use { c ->
            while (c.moveToNext()) {
                val csum = c.getLong(0)
                val id = c.getLong(1)
                if (csums.containsKey(csum)) {
                    csums[csum]!!.add(id)
                } else {
                    csums[csum] = ArrayList(listOf(id))
                }
            }
        }
        val firsts = HashUtil.HashSetInit<String>(notes.size)
        val fld0index = mMapping!!.indexOf(mModel.getJSONArray("flds").getJSONObject(0).getString("name"))
        mFMap = Models.fieldMap(mModel)
        mNextId = mCol.time.timestampID(mCol.db, "notes")
        // loop through the notes
        val updates: MutableList<Array<Any>> = ArrayList(notes.size)
        val updateLog: MutableList<String> = ArrayList(notes.size)
        // PORT: Translations moved closer to their sources
        val _new: MutableList<Array<Any>> = ArrayList()
        mIds = ArrayList()
        mEmptyNotes = false
        var dupeCount = 0
        val dupes: MutableList<String> = ArrayList(notes.size)
        for (n in notes) {
            for (c in n.mFields.indices) {
                if (!mAllowHTML) {
                    n.mFields[c] = HtmlUtils.escape(n.mFields[c]!!)
                }
                n.mFields[c] = n.mFields[c]!!.trim { it <= ' ' }
                if (!mAllowHTML) {
                    n.mFields[c] = n.mFields[c]!!.replace("\n", "<br>")
                }
            }
            val fld0 = n.mFields[fld0index]
            val csum = Utils.fieldChecksum(fld0)
            // first field must exist
            if (fld0 == null || fld0.isEmpty()) {
                log.add(getString(R.string.note_importer_error_empty_first_field, TextUtils.join(" ", n.mFields)))
                continue
            }
            // earlier in import?
            if (firsts.contains(fld0) && mImportMode != ImportMode.ADD_MODE) {
                // duplicates in source file; log and ignore
                log.add(getString(R.string.note_importer_error_appeared_twice, fld0))
                continue
            }
            firsts.add(fld0)
            // already exists?
            var found = false
            if (csums.containsKey(csum)) {
                // csum is not a guarantee; have to check
                for (id in csums[csum]!!) {
                    val flds = mCol.db.queryString("select flds from notes where id = ?", id)
                    val sflds = Utils.splitFields(flds)
                    if (fld0 == sflds[0]) {
                        // duplicate
                        found = true
                        if (mImportMode == ImportMode.UPDATE_MODE) {
                            val data = updateData(n, id, sflds)
                            if (data != null && data.isNotEmpty()) {
                                updates.add(data)
                                updateLog.add(getString(R.string.note_importer_error_first_field_matched, fld0))
                                dupeCount += 1
                                found = true
                            }
                        } else if (mImportMode == ImportMode.IGNORE_MODE) {
                            dupeCount += 1
                        } else if (mImportMode == ImportMode.ADD_MODE) {
                            // allow duplicates in this case
                            if (!dupes.contains(fld0)) {
                                // only show message once, no matter how many
                                // duplicates are in the collection already
                                updateLog.add(getString(R.string.note_importer_error_added_duplicate_first_field, fld0))
                                dupes.add(fld0)
                            }
                            found = false
                        }
                    }
                }
            }
            // newly add
            if (!found) {
                val data = newData(n)
                if (data != null && data.isNotEmpty()) {
                    _new.add(data)
                    // note that we've seen this note once already
                    firsts.add(fld0)
                }
            }
        }
        addNew(_new)
        addUpdates(updates)
        // make sure to update sflds, etc
        mCol.updateFieldCache(mIds)
        // generate cards
        if (mCol.genCards(mIds, mModel).isNotEmpty()) {
            this.log.add(0, getString(R.string.note_importer_empty_cards_found))
        }

        // we randomize or order here, to ensure that siblings
        // have the same due#
        val did = mCol.decks.selected()
        val conf = mCol.decks.confForDid(did)
        // in order due?
        if (conf.getJSONObject("new").getInt("order") == Consts.NEW_CARDS_RANDOM) {
            mCol.sched.randomizeCards(did)
        }
        val part1 = getQuantityString(R.plurals.note_importer_notes_added, _new.size)
        val part2 = getQuantityString(R.plurals.note_importer_notes_updated, mUpdateCount)
        val unchanged: Int = when (mImportMode) {
            ImportMode.UPDATE_MODE -> dupeCount - mUpdateCount
            ImportMode.IGNORE_MODE -> dupeCount
            else -> 0
        }
        val part3 = getQuantityString(R.plurals.note_importer_notes_unchanged, unchanged)
        mLog.add(String.format("%s, %s, %s.", part1, part2, part3))
        mLog.addAll(updateLog)
        if (mEmptyNotes) {
            mLog.add(getString(R.string.note_importer_error_empty_notes))
        }
        mTotal = mIds!!.size
    }

    private fun newData(n: ForeignNote): Array<Any>? {
        val id = mNextId
        mNextId++
        mIds!!.add(id)
        return if (!processFields(n)) {
            null
        } else arrayOf(
            id,
            Utils.guid64(),
            mModel!!.getLong("id"),
            mCol.time.intTime(),
            mCol.usn(),
            mCol.tags.join(n.mTags),
            n.fieldsStr,
            "",
            "",
            0,
            ""
        )
    }

    private fun addNew(rows: List<Array<Any>>) {
        mCol.db.executeMany("insert or replace into notes values (?,?,?,?,?,?,?,?,?,?,?)", rows)
    }

    private fun updateData(n: ForeignNote, id: Long, sflds: Array<String>): Array<Any>? {
        mIds!!.add(id)
        if (!processFields(n, sflds)) {
            return null
        }
        var tags: String?
        return when {
            mTagsMapped -> {
                tags = mCol.tags.join(n.mTags)
                arrayOf(mCol.time.intTime(), mCol.usn(), n.fieldsStr, tags, id, n.fieldsStr, tags)
            }
            mTagModified != null -> {
                tags = mCol.db.queryString("select tags from notes where id = ?", id)
                val tagList = mCol.tags.split(tags)
                tagList.addAll(StringUtils.splitOnWhitespace(mTagModified))
                tags = mCol.tags.join(tagList)
                arrayOf(mCol.time.intTime(), mCol.usn(), n.fieldsStr, tags, id, n.fieldsStr)
            }
            else -> {
                // This looks inconsistent but is fine, see: addUpdates
                arrayOf(mCol.time.intTime(), mCol.usn(), n.fieldsStr, id, n.fieldsStr)
            }
        }
    }

    private fun addUpdates(rows: List<Array<Any>>) {
        val changes = mCol.db.queryScalar("select total_changes()")
        if (mTagsMapped) {
            mCol.db.executeMany(
                "update notes set mod = ?, usn = ?, flds = ?, tags = ? " +
                    "where id = ? and (flds != ? or tags != ?)",
                rows
            )
        } else if (mTagModified != null) {
            mCol.db.executeMany(
                "update notes set mod = ?, usn = ?, flds = ?, tags = ? " +
                    "where id = ? and flds != ?",
                rows
            )
        } else {
            mCol.db.executeMany(
                "update notes set mod = ?, usn = ?, flds = ? " +
                    "where id = ? and flds != ?",
                rows
            )
        }
        val changes2 = mCol.db.queryScalar("select total_changes()")
        mUpdateCount = changes2 - changes
    }

    private fun processFields(note: ForeignNote, fields: Array<String>? = null): Boolean {
        val fieldList = fields ?: Array(mModel!!.getJSONArray("flds").length()) { "" }
        for ((c, value) in mMapping!!.withIndex()) {
            if (value == null) {
                continue
            }
            if (value == TAGS_IDENTIFIER) {
                note.mTags.addAll(mCol.tags.split(note.mFields[c]!!))
            } else {
                val sidx = mFMap!![value]!!.first
                fieldList[sidx] = note.mFields[c]!!
            }
        }
        note.fieldsStr = Utils.joinFields(fieldList)
        val ords = Models.availOrds(mModel, fieldList, mTemplateParsed, Models.AllowEmpty.TRUE)
        if (ords.isEmpty()) {
            mEmptyNotes = true
            return false
        }
        return true
    }

    val total: Int
        get() = mTotal

    fun setImportMode(mode: ImportMode) {
        mImportMode = mode
    }

    private fun getQuantityString(@PluralsRes res: Int, quantity: Int): String {
        return AnkiDroidApp.getAppResources().getQuantityString(res, quantity, quantity)
    }

    protected fun getString(@StringRes res: Int): String {
        return AnkiDroidApp.getAppResources().getString(res)
    }

    protected fun getString(res: Int, vararg formatArgs: Any): String {
        return AnkiDroidApp.getAppResources().getString(res, *formatArgs)
    }

    fun setAllowHtml(allowHtml: Boolean) {
        mAllowHTML = allowHtml
    }

    enum class ImportMode {
        /** update if first field matches existing note  */
        UPDATE_MODE, // 0

        /** ignore if first field matches existing note  */
        IGNORE_MODE, // 1

        /** ADD_MODE: import even if first field matches existing note  */
        ADD_MODE
        // 2
    }

    /** A temporary object storing fields and attributes.  */
    class ForeignNote {
        @JvmField
        val mFields: MutableList<String?> = ArrayList()
        @JvmField
        val mTags: MutableList<String> = ArrayList()
        var deck = Any()
        var fieldsStr = ""
    }

    class ForeignCard {
        val mDue: Long = 0
        val mIvl = 1
        val mFactor = Consts.STARTING_FACTOR
        val mReps = 0
        val mLapses = 0
    }

    private class Triple(val nid: Long, val ord: Int, val card: ForeignCard)
    companion object {
        /** A magic string used in [this.mMapping] when a csv field should be mapped to the tags of a note  */
        const val TAGS_IDENTIFIER = "_tags"
    }

    init {
        mMapping = null
        mTagModified = null
        mTagsMapped = false
    }
}
