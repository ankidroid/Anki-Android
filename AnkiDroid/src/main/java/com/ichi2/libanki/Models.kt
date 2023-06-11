/****************************************************************************************
 * Copyright (c) 2009 Daniel Svärd <daniel.svard@gmail.com>                             *
 * Copyright (c) 2010 Rick Gruber-Riemer <rick@vanosten.net>                            *
 * Copyright (c) 2011 Norbert Nagold <norbert.nagold@gmail.com>                         *
 * Copyright (c) 2011 Kostas Spyropoulos <inigo.aldana@gmail.com>                       *
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
import androidx.annotation.VisibleForTesting
import com.ichi2.anki.exception.ConfirmModSchemaException
import com.ichi2.libanki.template.ParsedNode
import com.ichi2.libanki.template.TemplateError
import com.ichi2.libanki.utils.TimeManager.time
import com.ichi2.utils.*
import com.ichi2.utils.HashUtil.HashMapInit
import com.ichi2.utils.KotlinCleanup
import com.ichi2.utils.jsonObjectIterable
import com.ichi2.utils.stringIterable
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.util.*
import java.util.regex.Pattern

@KotlinCleanup("IDE Lint")
@KotlinCleanup("Lots to do")
class Models(col: Collection) : ModelManager(col) {
    /**
     * Saving/loading registry
     * ***********************************************************************************************
     */

    private var mChanged = false

    @KotlinCleanup("lateinit")
    private var mModels: HashMap<Long, Model>? = null

    /**
     * @return the ID
     */
    // BEGIN SQL table entries
    val id = 0

    /** {@inheritDoc}  */
    override fun load(json: String) {
        mChanged = false
        mModels = HashMap()
        val modelarray = JSONObject(json)

        @KotlinCleanup("simplify with ?.forEach{}")
        val ids = modelarray.names()
        if (ids != null) {
            for (id in ids.stringIterable()) {
                val o = Model(modelarray.getJSONObject(id))
                mModels!![o.getLong("id")] = o
            }
        }
    }

    /** {@inheritDoc}  */
    override fun save(m: Model?, templates: Boolean) {
        if (m != null && m.has("id")) {
            m.put("mod", time.intTime())
            m.put("usn", col.usn())
            // TODO: fix empty id problem on _updaterequired (needed for model adding)
            if (!isModelNew(m)) {
                // this fills in the `req` chunk of the model. Not used on AnkiDroid 2.15+ or AnkiDesktop 2.1.x
                // Included only for backwards compatibility (to AnkiDroid <2.14 etc)
                // https://forums.ankiweb.net/t/is-req-still-used-or-present/9977
                // https://github.com/ankidroid/Anki-Android/issues/8945
                _updateRequired(m)
            }
            if (templates) {
                _syncTemplates(m)
            }
        }
        mChanged = true
        // The following hook rebuilds the tree in the Anki Desktop browser -- we don't need it
        // runHook("newModel")
    }

    /** {@inheritDoc}  */
    @KotlinCleanup("fix 'val'")
    override fun flush() {
        if (mChanged) {
            ensureNotEmpty()
            val array = JSONObject()
            for ((key, value) in mModels!!) {
                array.put(java.lang.Long.toString(key), value)
            }
            val `val` = ContentValues()
            `val`.put("models", Utils.jsonToString(array))
            col.db.update("col", `val`)
            mChanged = false
        }
    }

    /** {@inheritDoc}  */
    override fun ensureNotEmpty(): Boolean {
        return if (mModels!!.isEmpty()) {
            // TODO: Maybe we want to restore all models if we don't have any
            StdModels.BASIC_MODEL.add(col)
            true
        } else {
            false
        }
    }

    /*
      Retrieving and creating models
      ***********************************************************************************************
     */
    /** {@inheritDoc}  */
    @KotlinCleanup("remove var and simplify fun with direct return")
    override fun current(forDeck: Boolean): Model? {
        var m: Model? = null
        if (forDeck) {
            m = get(col.decks.current().optLong("mid", -1))
        }
        if (m == null) {
            m = get(col.get_config("curModel", -1L)!!)
        }
        if (m == null) {
            if (!mModels!!.isEmpty()) {
                m = mModels!!.values.iterator().next()
            }
        }
        return m
    }

    override fun setCurrent(m: Model) {
        col.set_config("curModel", m.getLong("id"))
    }

    /** {@inheritDoc}  */
    @KotlinCleanup("replace with mModels!![id] if this matches 'libanki'")
    override operator fun get(id: Long): Model? {
        return if (mModels!!.containsKey(id)) {
            mModels!![id]
        } else {
            null
        }
    }

    /** {@inheritDoc}  */
    override fun all(): ArrayList<Model> {
        return ArrayList(mModels!!.values)
    }

    /** {@inheritDoc}  */
    @KotlinCleanup("replace with map")
    override fun allNames(): ArrayList<String> {
        val nameList = ArrayList<String>()
        for (m in mModels!!.values) {
            nameList.add(m.getString("name"))
        }
        return nameList
    }

    /** {@inheritDoc}  */
    @KotlinCleanup("replace with .find { }")
    override fun byName(name: String): Model? {
        for (m in mModels!!.values) {
            if (m.getString("name") == name) {
                return m
            }
        }
        return null
    }

    /** {@inheritDoc}  */
    override fun newModel(name: String): Model {
        // caller should call save() after modifying
        return Model(DEFAULT_MODEL).apply {
            put("name", name)
            put("mod", time.intTime())
            put("flds", JSONArray())
            put("tmpls", JSONArray())
            put("id", 0)
        }
    }

    /** {@inheritDoc}  */
    @Throws(ConfirmModSchemaException::class)
    override operator fun rem(m: Model) {
        col.modSchema()
        val id = m.getLong("id")
        val current = current()!!.getLong("id") == id
        // delete notes/cards
        col.removeCards(
            col.db.queryLongList(
                "SELECT id FROM cards WHERE nid IN (SELECT id FROM notes WHERE mid = ?)",
                id
            )
        )
        // then the model
        mModels!!.remove(id)
        save()
        // GUI should ensure last model is not deleted
        if (current) {
            setCurrent(mModels!!.values.iterator().next())
        }
    }

    override fun add(m: Model) {
        _setID(m)
        update(m)
        setCurrent(m)
        save(m)
    }

    override fun update(m: Model, preserve_usn_and_mtime: Boolean) {
        if (!preserve_usn_and_mtime) {
            Timber.w("preserve_usn_and_mtime is not supported in legacy java class")
        }
        mModels!![m.getLong("id")] = m
        // mark registry changed, but don't bump mod time
        save()
    }

    private fun _setID(m: Model) {
        var id = time.intTimeMS()
        while (mModels!!.containsKey(id)) {
            id = time.intTimeMS()
        }
        m.put("id", id)
    }

    override fun have(id: Long): Boolean {
        return mModels!!.containsKey(id)
    }

    override fun ids(): Set<Long> {
        return mModels!!.keys
    }
    /*
      Tools ***********************************************************************************************
     */
    /** {@inheritDoc}  */
    override fun nids(m: Model): List<Long> {
        return col.db.queryLongList("SELECT id FROM notes WHERE mid = ?", m.getLong("id"))
    }

    /** {@inheritDoc}  */
    override fun useCount(m: Model): Int {
        return col.db.queryScalar("select count() from notes where mid = ?", m.getLong("id"))
    }

    /** {@inheritDoc}  */
    override fun tmplUseCount(m: Model, ord: Int): Int {
        return col.db.queryScalar(
            "select count() from cards, notes where cards.nid = notes.id and notes.mid = ? and cards.ord = ?",
            m.getLong("id"),
            ord
        )
    }
    /*
      Copying ***********************************************************************************************
     */
    /** {@inheritDoc}  */
    override fun copy(m: Model): Model {
        val m2 = m.deepClone()
        m2.put("name", m2.getString("name") + " copy")
        add(m2)
        return m2
    }

    /*
     * Fields ***********************************************************************************************
     */
    override fun newField(name: String): JSONObject {
        return JSONObject(defaultField).apply {
            put("name", name)
        }
    }

    override fun sortIdx(m: Model): Int {
        return m.getInt("sortf")
    }

    @Throws(ConfirmModSchemaException::class)
    override fun setSortIdx(m: Model, idx: Int) {
        col.modSchema()
        m.put("sortf", idx)
        col.updateFieldCache(nids(m))
        save(m)
    }

    override fun _addField(m: Model, field: JSONObject) {
        // do the actual work of addField. Do not check whether model
        // is not new.
        val flds = m.getJSONArray("flds")
        flds.put(field)
        m.put("flds", flds)
        _updateFieldOrds(m)
        save(m)
        _transformFields(m, TransformFieldAdd())
    }

    @Throws(ConfirmModSchemaException::class)
    override fun addField(m: Model, field: JSONObject) {
        // only mod schema if model isn't new
        // this is Anki's addField.
        if (!isModelNew(m)) {
            col.modSchema()
        }
        _addField(m, field)
    }

    internal class TransformFieldAdd : TransformFieldVisitor {
        @KotlinCleanup("remove arrayOfNulls")
        override fun transform(fields: Array<String>): Array<String> {
            val f = arrayOfNulls<String>(fields.size + 1)
            System.arraycopy(fields, 0, f, 0, fields.size)
            f[fields.size] = ""
            return f.requireNoNulls()
        }
    }

    @Throws(ConfirmModSchemaException::class)
    override fun remField(m: Model, field: JSONObject) {
        col.modSchema()
        val flds = m.getJSONArray("flds")
        val flds2 = JSONArray()
        var idx = -1
        for (i in 0 until flds.length()) {
            if (field == flds.getJSONObject(i)) {
                idx = i
                continue
            }
            flds2.put(flds.getJSONObject(i))
        }
        m.put("flds", flds2)
        val sortf = m.getInt("sortf")
        if (sortf >= m.getJSONArray("flds").length()) {
            m.put("sortf", sortf - 1)
        }
        _updateFieldOrds(m)
        _transformFields(m, TransformFieldDelete(idx))
        if (idx == sortIdx(m)) {
            // need to rebuild
            col.updateFieldCache(nids(m))
        }
        renameFieldInternal(m, field, null)
    }

    internal class TransformFieldDelete(private val idx: Int) : TransformFieldVisitor {
        @KotlinCleanup("simplify fun with array.toMutableList().filterIndexed")
        override fun transform(fields: Array<String>): Array<String> {
            val fl = ArrayList(listOf(*fields))
            fl.removeAt(idx)
            return fl.toTypedArray()
        }
    }

    @Throws(ConfirmModSchemaException::class)
    override fun moveField(m: Model, field: JSONObject, idx: Int) {
        col.modSchema()
        var flds = m.getJSONArray("flds")
        val l = ArrayList<JSONObject?>(flds.length())
        var oldidx = -1
        for (i in 0 until flds.length()) {
            l.add(flds.getJSONObject(i))
            if (field == flds.getJSONObject(i)) {
                oldidx = i
                if (idx == oldidx) {
                    return
                }
            }
        }
        // remember old sort field
        val sortf = Utils.jsonToString(m.getJSONArray("flds").getJSONObject(m.getInt("sortf")))
        // move
        l.removeAt(oldidx)
        l.add(idx, field)
        m.put("flds", JSONArray(l))
        // restore sort field
        flds = m.getJSONArray("flds")
        for (i in 0 until flds.length()) {
            if (Utils.jsonToString(flds.getJSONObject(i)) == sortf) {
                m.put("sortf", i)
                break
            }
        }
        _updateFieldOrds(m)
        save(m)
        _transformFields(m, TransformFieldMove(idx, oldidx))
    }

    internal class TransformFieldMove(private val idx: Int, private val oldidx: Int) :
        TransformFieldVisitor {
        @KotlinCleanup("simplify with array.toMutableList and maybe scope function")
        override fun transform(fields: Array<String>): Array<String> {
            val `val` = fields[oldidx]
            val fl = ArrayList(listOf(*fields))
            fl.removeAt(oldidx)
            fl.add(idx, `val`)
            return fl.toTypedArray()
        }
    }

    @KotlinCleanup("remove this, make newName non-null and use empty string instead of null")
    @KotlinCleanup("turn 'pat' into pat.toRegex()")
    private fun renameFieldInternal(m: Model, field: JSONObject, newName: String?) {
        @Suppress("NAME_SHADOWING")
        var newName: String? = newName
        col.modSchema()
        val pat = String.format(
            "\\{\\{([^{}]*)([:#^/]|[^:#/^}][^:}]*?:|)%s\\}\\}",
            Pattern.quote(field.getString("name"))
        )
        if (newName == null) {
            newName = ""
        }
        val repl = "{{$1$2$newName}}"
        val tmpls = m.getJSONArray("tmpls")
        for (t in tmpls.jsonObjectIterable()) {
            for (fmt in arrayOf("qfmt", "afmt")) {
                if ("" != newName) {
                    t.put(fmt, t.getString(fmt).replace(pat.toRegex(), repl))
                } else {
                    t.put(fmt, t.getString(fmt).replace(pat.toRegex(), ""))
                }
            }
        }
        field.put("name", newName)
        save(m)
    }

    @Throws(ConfirmModSchemaException::class)
    override fun renameField(m: Model, field: JSONObject, newName: String) =
        renameFieldInternal(m, field, newName)

    fun _updateFieldOrds(m: JSONObject) {
        val flds = m.getJSONArray("flds")
        for (i in 0 until flds.length()) {
            val f = flds.getJSONObject(i)
            f.put("ord", i)
        }
    }

    interface TransformFieldVisitor {
        fun transform(fields: Array<String>): Array<String>
    }

    fun _transformFields(m: Model, fn: TransformFieldVisitor) {
        // model hasn't been added yet?
        if (isModelNew(m)) {
            return
        }
        val r = ArrayList<Array<Any>>()
        col.db
            .query("select id, flds from notes where mid = ?", m.getLong("id")).use { cur ->
                while (cur.moveToNext()) {
                    r.add(
                        arrayOf(
                            Utils.joinFields(fn.transform(Utils.splitFields(cur.getString(1)))),
                            time.intTime(),
                            col.usn(),
                            cur.getLong(0)
                        )
                    )
                }
            }
        col.db.executeMany("update notes set flds=?,mod=?,usn=? where id = ?", r)
    }

    /** Note: should col.genCards() afterwards.  */
    override fun _addTemplate(m: Model, template: JSONObject) {
        // do the actual work of addTemplate. Do not consider whether
        // model is new or not.
        val tmpls = m.getJSONArray("tmpls")
        tmpls.put(template)
        m.put("tmpls", tmpls)
        _updateTemplOrds(m)
        save(m)
    }

    @Throws(ConfirmModSchemaException::class)
    override fun addTemplate(m: Model, template: JSONObject) {
        // That is Anki's addTemplate method
        if (!isModelNew(m)) {
            col.modSchema()
        }
        _addTemplate(m, template)
    }

    /** {@inheritDoc}  */
    @Throws(ConfirmModSchemaException::class)
    override fun remTemplate(m: Model, template: JSONObject) {
        if (m.getJSONArray("tmpls").length() <= 1) {
            return
        }
        // find cards using this template
        var tmpls = m.getJSONArray("tmpls")
        var ord = -1
        for (i in 0 until tmpls.length()) {
            if (tmpls.getJSONObject(i) == template) {
                ord = i
                break
            }
        }
        require(ord != -1) { "Invalid template proposed for delete" }
        // the code in "isRemTemplateSafe" was in place here in libanki. It is extracted to a method for reuse
        val cids = getCardIdsForModel(m.getLong("id"), intArrayOf(ord))
        if (cids == null) {
            Timber.d("remTemplate getCardIdsForModel determined it was unsafe to delete the template")
            return
        }

        // ok to proceed; remove cards
        Timber.d("remTemplate proceeding to delete the template and %d cards", cids.size)
        col.modSchema()
        col.removeCards(cids)
        // shift ordinals
        col.db
            .execute(
                "update cards set ord = ord - 1, usn = ?, mod = ? where nid in (select id from notes where mid = ?) and ord > ?",
                col.usn(),
                time.intTime(),
                m.getLong("id"),
                ord
            )
        tmpls = m.getJSONArray("tmpls")
        val tmpls2 = JSONArray()
        for (i in 0 until tmpls.length()) {
            if (template == tmpls.getJSONObject(i)) {
                continue
            }
            tmpls2.put(tmpls.getJSONObject(i))
        }
        m.put("tmpls", tmpls2)
        _updateTemplOrds(m)
        save(m)
        Timber.d("remTemplate done working")
    }

    override fun moveTemplate(m: Model, template: JSONObject, idx: Int) {
        var tmpls = m.getJSONArray("tmpls")
        var oldidx = -1
        val l = ArrayList<JSONObject?>()
        val oldidxs = HashMap<Int, Int>()
        for (i in 0 until tmpls.length()) {
            if (tmpls.getJSONObject(i) == template) {
                oldidx = i
                if (idx == oldidx) {
                    return
                }
            }
            val t = tmpls.getJSONObject(i)
            oldidxs[t.hashCode()] = t.getInt("ord")
            l.add(t)
        }
        l.removeAt(oldidx)
        l.add(idx, template)
        m.put("tmpls", JSONArray(l))
        _updateTemplOrds(m)
        // generate change map - We use StringBuilder
        val sb = StringBuilder()
        tmpls = m.getJSONArray("tmpls")
        for (i in 0 until tmpls.length()) {
            val t = tmpls.getJSONObject(i)
            sb.append("when ord = ").append(oldidxs[t.hashCode()]).append(" then ")
                .append(t.getInt("ord"))
            if (i != tmpls.length() - 1) {
                sb.append(" ")
            }
        }
        // apply
        save(m)
        col.db.execute(
            "update cards set ord = (case " + sb +
                " end),usn=?,mod=? where nid in (select id from notes where mid = ?)",
            col.usn(),
            time.intTime(),
            m.getLong("id")
        )
    }

    private fun _syncTemplates(m: Model) {
        @Suppress("UNUSED_VARIABLE") // unused upstream as well
        val rem = col.genCards(nids(m), m)!!
    }

    /*
      Model changing ***********************************************************************************************
     */
    /** {@inheritDoc}  */
    @Throws(ConfirmModSchemaException::class)
    @KotlinCleanup("rename null param from genCards")
    override fun change(
        m: Model,
        nid: NoteId,
        newModel: Model,
        fmap: Map<Int, Int?>,
        cmap: Map<Int, Int?>
    ) {
        col.modSchema()
        _changeNote(nid, newModel, fmap)
        _changeCards(nid, m, newModel, cmap)
        col.genCards(nid, newModel, null)
    }

    private fun _changeNote(nid: Long, newModel: Model, map: Map<Int, Int?>) {
        val nfields = newModel.getJSONArray("flds").length()
        val mid = newModel.getLong("id")
        val sflds = col.db.queryString("select flds from notes where id = ?", nid)
        val flds = Utils.splitFields(sflds)
        val newflds: MutableMap<Int?, String> = HashMapInit(map.size)
        for ((key, value) in map) {
            newflds[value] = flds[key]
        }
        val flds2: MutableList<String> = ArrayList(nfields)
        for (c in 0 until nfields) {
            @KotlinCleanup("getKeyOrDefault")
            if (newflds.containsKey(c)) {
                flds2.add(newflds[c]!!)
            } else {
                flds2.add("")
            }
        }
        val joinedFlds = Utils.joinFields(flds2.toTypedArray())
        col.db.execute(
            "update notes set flds=?,mid=?,mod=?,usn=? where id = ?",
            joinedFlds,
            mid,
            time.intTime(),
            col.usn(),
            nid
        )
        col.updateFieldCache(longArrayOf(nid))
    }

    private fun _changeCards(nid: Long, oldModel: Model, newModel: Model, map: Map<Int, Int?>) {
        val d: MutableList<Array<Any>> = ArrayList()
        val deleted: MutableList<Long> = ArrayList()
        val omType = oldModel.getInt("type")
        val nmType = newModel.getInt("type")
        val nflds = newModel.getJSONArray("tmpls").length()
        col.db.query(
            "select id, ord from cards where nid = ?",
            nid
        ).use { cur ->
            while (cur.moveToNext()) {
                // if the src model is a cloze, we ignore the map, as the gui doesn't currently
                // support mapping them
                var newOrd: Int?
                val cid = cur.getLong(0)
                val ord = cur.getInt(1)
                if (omType == Consts.MODEL_CLOZE) {
                    newOrd = cur.getInt(1)
                    if (nmType != Consts.MODEL_CLOZE) {
                        // if we're mapping to a regular note, we need to check if
                        // the destination ord is valid
                        if (nflds <= ord) {
                            newOrd = null
                        }
                    }
                } else {
                    // mapping from a regular note, so the map should be valid
                    newOrd = map[ord]
                }
                if (newOrd != null) {
                    d.add(arrayOf(newOrd, col.usn(), time.intTime(), cid))
                } else {
                    deleted.add(cid)
                }
            }
        }
        col.db.executeMany("update cards set ord=?,usn=?,mod=? where id=?", d)
        col.removeCards(deleted)
    }

    /*
      Schema hash ***********************************************************************************************
     */
    override fun scmhash(m: Model): String {
        val s = StringBuilder()
        val flds = m.getJSONArray("flds")
        for (fld in flds.jsonObjectIterable()) {
            s.append(fld.getString("name"))
        }
        val tmpls = m.getJSONArray("tmpls")
        for (t in tmpls.jsonObjectIterable()) {
            s.append(t.getString("name"))
        }
        return Utils.checksum(s.toString())
    }

    /**
     * Required field/text cache
     * ***********************************************************************************************
     */
    private fun _updateRequired(m: Model) {
        if (m.isCloze) {
            // nothing to do
            return
        }
        val req = JSONArray()
        val flds = m.fieldsNames
        val templates = m.getJSONArray("tmpls")
        for (t in templates.jsonObjectIterable()) {
            val ret = _reqForTemplate(m, flds, t)
            val r = JSONArray()
            r.put(t.getInt("ord"))
            r.put(ret[0])
            r.put(ret[1])
            req.put(r)
        }
        m.put("req", req)
    }

    private fun _reqForTemplate(m: Model, flds: List<String>, t: JSONObject): Array<Any> {
        val nbFields = flds.size
        val a = Array(nbFields) { "ankiflag" }
        val b = Array(nbFields) { "" }
        val ord = t.getInt("ord")
        val full = col._renderQA(1L, m, 1L, ord, "", a, 0)["q"]
        val empty = col._renderQA(1L, m, 1L, ord, "", b, 0)["q"]
        // if full and empty are the same, the template is invalid and there is no way to satisfy it
        if (full == empty) {
            return arrayOf(REQ_NONE, JSONArray(), JSONArray())
        }
        var type = REQ_ALL
        var req = JSONArray()
        for (i in flds.indices) {
            a[i] = ""
            // if no field content appeared, field is required
            if (!col._renderQA(1L, m, 1L, ord, "", a, 0)["q"]!!.contains("ankiflag")) {
                req.put(i)
            }
            a[i] = "ankiflag"
        }
        if (req.length() > 0) {
            return arrayOf(type, req)
        }
        // if there are no required fields, switch to any mode
        type = REQ_ANY
        req = JSONArray()
        for (i in flds.indices) {
            b[i] = "1"
            // if not the same as empty, this field can make the card non-blank
            if (col._renderQA(1L, m, 1L, ord, "", b, 0)["q"] != empty) {
                req.put(i)
            }
            b[i] = ""
        }
        return arrayOf(type, req)
    }

    /**
     * Whether to allow empty note to generate a card. When importing a deck, this is useful to be able to correct it. When doing "check card" it avoids to delete empty note.
     * By default, it is allowed for cloze type but not for standard type.
     */
    enum class AllowEmpty {
        TRUE, FALSE, ONLY_CLOZE;

        companion object {
            /**
             * @param allowEmpty a Boolean representing whether empty note should be allowed. Null is understood as default
             * @return AllowEmpty similar to the boolean
             */
            fun fromBoolean(allowEmpty: Boolean?): AllowEmpty {
                return if (allowEmpty == null) {
                    ONLY_CLOZE
                } else if (allowEmpty == true) {
                    TRUE
                } else {
                    FALSE
                }
            }
        }
    }

    /*
     * Sync handling ***********************************************************************************************
     */
    override fun beforeUpload() {
        val changed = Utils.markAsUploaded(all())
        if (changed) {
            save()
        }
    }

    /*
     * Other stuff NOT IN LIBANKI
     * ***********************************************************************************************
     */
    override fun setChanged() {
        mChanged = true
    }

    val templateNames: HashMap<Long, HashMap<Int, String>>
        get() {
            val result = HashMapInit<Long, HashMap<Int, String>>(
                mModels!!.size
            )
            for (m in mModels!!.values) {
                val templates = m.getJSONArray("tmpls")
                val names = HashMapInit<Int, String>(templates.length())
                for (t in templates.jsonObjectIterable()) {
                    names[t.getInt("ord")] = t.getString("name")
                }
                result[m.getLong("id")] = names
            }
            return result
        }

    override fun getModels(): HashMap<Long, Model> {
        return mModels!!
    }

    /** {@inheritDoc}  */
    override fun count(): Int {
        return mModels!!.size
    }

    companion object {
        const val NOT_FOUND_NOTE_TYPE = -1L

        @VisibleForTesting
        val REQ_NONE = "none"

        @VisibleForTesting
        val REQ_ANY = "any"

        @VisibleForTesting
        val REQ_ALL = "all"

        // In Android, } should be escaped
        private val fClozePattern1 = Pattern.compile("\\{\\{[^}]*?cloze:(?:[^}]?:)*(.+?)\\}\\}")
        private val fClozePattern2 = Pattern.compile("<%cloze:(.+?)%>")
        private val fClozeOrdPattern = Pattern.compile("(?si)\\{\\{c(\\d+)::.*?\\}\\}")

        @KotlinCleanup("Use triple quotes for this properties and maybe `@language('json'')`")
        const val DEFAULT_MODEL = (
            "{\"sortf\": 0, " +
                "\"did\": 1, " +
                "\"latexPre\": \"" +
                "\\\\documentclass[12pt]{article}\\n" +
                "\\\\special{papersize=3in,5in}\\n" +
                "\\\\usepackage[utf8]{inputenc}\\n" +
                "\\\\usepackage{amssymb,amsmath}\\n" +
                "\\\\pagestyle{empty}\\n" +
                "\\\\setlength{\\\\parindent}{0in}\\n" +
                "\\\\begin{document}\\n" +
                "\", " +
                "\"latexPost\": \"\\\\end{document}\", " +
                "\"latexsvg\": false," +
                "\"mod\": 0, " +
                "\"usn\": 0, " +
                "\"type\": " +
                Consts.MODEL_STD +
                ", " +
                "\"css\": \".card {\\n" +
                "  font-family: arial;\\n" +
                "  font-size: 20px;\\n" +
                "  text-align: center;\\n" +
                "  color: black;\\n" +
                "  background-color: white;\\n" +
                "}\n\"" +
                "}"
            )
        private const val defaultField =
            "{\"name\": \"\", " + "\"ord\": null, " + "\"sticky\": false, " + // the following alter editing, and are used as defaults for the template wizard
                "\"rtl\": false, " + "\"font\": \"Arial\", " + "\"size\": 20 }"
        private const val defaultTemplate =
            (
                "{\"name\": \"\", " + "\"ord\": null, " + "\"qfmt\": \"\", " +
                    "\"afmt\": \"\", " + "\"did\": null, " + "\"bqfmt\": \"\"," + "\"bafmt\": \"\"," + "\"bfont\": \"\"," +
                    "\"bsize\": 0 }"
                )

        // /** Regex pattern used in removing tags from text before diff */
        // private static final Pattern sFactPattern = Pattern.compile("%\\([tT]ags\\)s");
        // private static final Pattern sModelPattern = Pattern.compile("%\\(modelTags\\)s");
        // private static final Pattern sTemplPattern = Pattern.compile("%\\(cardModel\\)s");

        // not in anki
        fun isModelNew(m: Model): Boolean {
            return m.getLong("id") == 0L
        }

        /** "Mapping of field name -> (ord, field).  */
        fun fieldMap(m: Model): Map<String, Pair<Int, JSONObject>> {
            val flds = m.getJSONArray("flds")
            // TreeMap<Integer, String> map = new TreeMap<Integer, String>();
            val result: MutableMap<String, Pair<Int, JSONObject>> = HashMapInit(flds.length())
            for (f in flds.jsonObjectIterable()) {
                result[f.getString("name")] = Pair(f.getInt("ord"), f)
            }
            return result
        }

        /*
     * Templates ***********************************************************************************************
     */
        @KotlinCleanup("direct return and use scope function")
        fun newTemplate(name: String?): JSONObject {
            val t = JSONObject(defaultTemplate)
            t.put("name", name)
            return t
        }

        fun _updateTemplOrds(m: Model) {
            val tmpls = m.getJSONArray("tmpls")
            for (i in 0 until tmpls.length()) {
                val f = tmpls.getJSONObject(i)
                f.put("ord", i)
            }
        }

        /**
         * @param m A note type
         * @param ord a card type number of this model
         * @param sfld Fields of a note of this model. (Not trimmed)
         * @return Whether this card is empty
         */
        @Throws(TemplateError::class)
        fun emptyCard(m: Model, ord: Int, sfld: Array<String>): Boolean {
            return if (m.isCloze) {
                // For cloze, getting the list of cloze numbes is linear in the size of the template
                // So computing the full list is almost as efficient as checking for a particular number
                !_availClozeOrds(m, sfld, false).contains(ord)
            } else {
                emptyStandardCard(
                    m.getJSONArray("tmpls").getJSONObject(ord),
                    m.nonEmptyFields(sfld)
                )
            }
        }

        /**
         * @return Whether the standard card is empty
         */
        @Throws(TemplateError::class)
        fun emptyStandardCard(tmpl: JSONObject, nonEmptyFields: Set<String>): Boolean {
            return ParsedNode.parse_inner(tmpl.getString("qfmt")).template_is_empty(nonEmptyFields)
        }

        /**
         * @param m A model
         * @param sfld Fields of a note
         * @param nodes Nodes used for parsing the variaous templates. Null for cloze
         * @param allowEmpty whether to always return an ord, even if all cards are actually empty
         * @return The index of the cards that are generated. For cloze cards, if no card is generated, then {0}
         */
        @KotlinCleanup("nodes is only non-null on one path")
        fun availOrds(
            m: Model,
            sfld: Array<String>,
            nodes: List<ParsedNode?>?,
            allowEmpty: AllowEmpty
        ): ArrayList<Int> {
            return if (m.getInt("type") == Consts.MODEL_CLOZE) {
                _availClozeOrds(
                    m,
                    sfld,
                    allowEmpty == AllowEmpty.TRUE || allowEmpty == AllowEmpty.ONLY_CLOZE
                )
            } else {
                _availStandardOrds(
                    m,
                    sfld,
                    nodes!!,
                    allowEmpty == AllowEmpty.TRUE
                )
            }
        }

        fun availOrds(
            m: Model,
            sfld: Array<String>,
            allowEmpty: AllowEmpty = AllowEmpty.ONLY_CLOZE
        ): ArrayList<Int> {
            return if (m.isCloze) {
                _availClozeOrds(
                    m,
                    sfld,
                    allowEmpty == AllowEmpty.TRUE || allowEmpty == AllowEmpty.ONLY_CLOZE
                )
            } else {
                _availStandardOrds(m, sfld, allowEmpty == AllowEmpty.TRUE)
            }
        }

        fun _availStandardOrds(
            m: Model,
            sfld: Array<String>,
            allowEmpty: Boolean = false
        ): ArrayList<Int> {
            return _availStandardOrds(m, sfld, m.parsedNodes(), allowEmpty)
        }

        /** Given a joined field string and a standard note type, return available template ordinals  */
        fun _availStandardOrds(
            m: Model,
            sfld: Array<String>,
            nodes: List<ParsedNode?>,
            allowEmpty: Boolean
        ): ArrayList<Int> {
            val nonEmptyFields = m.nonEmptyFields(sfld)
            val avail = ArrayList<Int>(nodes.size)
            for (i in nodes.indices) {
                val node = nodes[i]
                if (node != null && !node.template_is_empty(nonEmptyFields)) {
                    avail.add(i)
                }
            }
            if (allowEmpty && avail.isEmpty()) {
                /* According to anki documentation:
            When adding/importing, if a normal note doesn’t generate any cards, Anki will now add a blank card 1 instead of refusing to add the note. */
                avail.add(0)
            }
            return avail
        }

        /**
         * Cache of getNamesOfFieldsContainingCloze
         * Computing hash of string is costly. However, hash is cashed in the string object, so this virtually ensure that
         * given a card type, we don't need to recompute the hash.
         */
        private val namesOfFieldsContainingClozeCache = WeakHashMap<String, List<String>>()

        /** The name of all fields that are used as cloze in the question.
         * It is not guaranteed that the field found are actually the name of any field of the note type. */
        @VisibleForTesting
        internal fun getNamesOfFieldsContainingCloze(question: String): List<String> {
            if (!namesOfFieldsContainingClozeCache.containsKey(question)) {
                val matches: MutableList<String> = ArrayList()
                for (pattern in arrayOf(fClozePattern1, fClozePattern2)) {
                    val mm = pattern.matcher(question)
                    while (mm.find()) {
                        matches.add(mm.group(1)!!)
                    }
                }
                namesOfFieldsContainingClozeCache[question] = matches
            }
            return namesOfFieldsContainingClozeCache[question]!!
        }

        /**
         * @param m A note type with cloze
         * @param sflds The fields of a note of type m. (Assume the size of the array is the number of fields)
         * @param allowEmpty Whether we allow to generate at least one card even if they are all empty
         * @return The indexes (in increasing order) of cards that should be generated according to req rules.
         * If empty is not allowed, it will contains ord 1.
         */
        @KotlinCleanup("sflds: String? to string")
        @KotlinCleanup("return arrayListOf(0)")
        fun _availClozeOrds(
            m: Model,
            sflds: Array<String>,
            allowEmpty: Boolean = true
        ): ArrayList<Int> {
            val map = fieldMap(m)
            val question = m.getJSONArray("tmpls").getJSONObject(0).getString("qfmt")
            val ords: MutableSet<Int> = HashSet()
            val matches = getNamesOfFieldsContainingCloze(question)
            for (fname in matches) {
                if (!map.containsKey(fname)) {
                    continue
                }
                val ord = map[fname]!!.first
                val mm = fClozeOrdPattern.matcher(sflds[ord])
                while (mm.find()) {
                    ords.add(mm.group(1)!!.toInt() - 1)
                }
            }
            ords.remove(-1)
            return if (ords.isEmpty() && allowEmpty) {
                // empty clozes use first ord
                ArrayList(listOf(0))
            } else {
                ArrayList(ords)
            }
        }
    }
    // private long mCrt = mCol.getTime().intTime();
    // private long mMod = mCol.getTime().intTime();
    // private JSONObject mConf;
    // private String mCss = "";
    // private JSONArray mFields;
    // private JSONArray mTemplates;
    // BEGIN SQL table entries
    // private Decks mDeck;
    // private DB mDb;
    //
    // ** Map for compiled Mustache Templates */
    // private Map<String, Template> mCmpldTemplateMap = new HashMap<>();
    //
    // /** Map for convenience and speed which contains FieldNames from current model */
    // private TreeMap<String, Integer> mFieldMap = new TreeMap<String, Integer>();
    //
    // /** Map for convenience and speed which contains Templates from current model */
    // private TreeMap<Integer, JSONObject> mTemplateMap = new TreeMap<Integer, JSONObject>();
    //
    // /** Map for convenience and speed which contains the CSS code related to a Template */
    // private HashMap<Integer, String> mCssTemplateMap = new HashMap<Integer, String>();
    //
    // /**
    // * The percentage chosen in preferences for font sizing at the time when the css for the CardModels related to
    // this
    // * Model was calculated in prepareCSSForCardModels.
    // */
    // private transient int mDisplayPercentage = 0;
    // private boolean mNightMode = false;
}
