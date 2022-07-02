/***************************************************************************************
 * Copyright (c) 2012 Norbert Nagold <norbert.nagold@gmail.com>                         *
 * Copyright (c) 2016 Houssam Salem <houssam.salem.au@gmail.com>                        *
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

package com.ichi2.libanki.importer

import android.text.TextUtils
import android.util.Pair
import com.ichi2.anki.R
import com.ichi2.anki.exception.ConfirmModSchemaException
import com.ichi2.anki.exception.ImportExportException
import com.ichi2.libanki.Collection
import com.ichi2.libanki.Consts.CARD_QUEUE
import com.ichi2.libanki.Consts.CARD_TYPE
import com.ichi2.libanki.Consts.CARD_TYPE_LRN
import com.ichi2.libanki.Consts.CARD_TYPE_NEW
import com.ichi2.libanki.Consts.CARD_TYPE_REV
import com.ichi2.libanki.Consts.QUEUE_TYPE_DAY_LEARN_RELEARN
import com.ichi2.libanki.Consts.QUEUE_TYPE_NEW
import com.ichi2.libanki.Consts.QUEUE_TYPE_REV
import com.ichi2.libanki.DB
import com.ichi2.libanki.Decks
import com.ichi2.libanki.Media
import com.ichi2.libanki.Storage.collection
import com.ichi2.libanki.Utils
import com.ichi2.libanki.utils.TimeManager
import com.ichi2.utils.HashUtil
import com.ichi2.utils.KotlinCleanup
import timber.log.Timber
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.util.Arrays
import java.util.Locale
import java.util.regex.Matcher

@KotlinCleanup("IDE-lint")
@KotlinCleanup("remove !!")
@KotlinCleanup("lateinit")
@KotlinCleanup("make col non-null")
open class Anki2Importer(col: Collection?, file: String) : Importer(col, file) {
    private val mDeckPrefix: String?
    private val mAllowUpdate: Boolean
    private var mDupeOnSchemaChange: Boolean

    private class NoteTriple(val nid: Long, val mod: Long, val mid: Long)

    private var mNotes: MutableMap<String, NoteTriple>? = null
    private var mDecks: MutableMap<Long, Long>? = null
    private var mModelMap: MutableMap<Long, Long>? = null
    private var mIgnoredGuids: MutableSet<String>? = null
    var dupes = 0
        private set
    var added = 0
        private set
    var updated = 0
        private set

    /** If importing SchedV1 into SchedV2 we need to reset the learning cards  */
    private var mMustResetLearning = false
    @Throws(ImportExportException::class)
    override fun run() {
        publishProgress(0, 0, 0)
        try {
            _prepareFiles()
            try {
                _import()
            } finally {
                mSrc.close(false)
            }
        } catch (e: Exception) {
            Timber.e(e, "Exception while importing")
            throw ImportExportException(e.message)
        }
    }

    private fun _prepareFiles() {
        val importingV2 = mFile.endsWith(".anki21")
        mMustResetLearning = false
        mDst = mCol
        mSrc = collection(mContext, mFile)
        if (!importingV2 && mCol.schedVer() != 1) {
            // any scheduling included?
            if (mSrc.db.queryScalar("select 1 from cards where queue != $QUEUE_TYPE_NEW limit 1") > 0) {
                mMustResetLearning = true
            }
        }
    }

    private fun _import() {
        mDecks = HashUtil.HashMapInit(mSrc.decks.count())
        try {
            // Use transactions for performance and rollbacks in case of error
            mDst.db.database.beginTransaction()
            mDst.media.db.database.beginTransaction()
            if (!TextUtils.isEmpty(mDeckPrefix)) {
                val id = mDst.decks.id_safe(mDeckPrefix!!)
                mDst.decks.select(id)
            }
            Timber.i("Preparing Import")
            _prepareTS()
            _prepareModels()
            Timber.i("Importing notes")
            _importNotes()
            Timber.i("Importing Cards")
            _importCards()
            Timber.i("Importing Media")
            _importStaticMedia()
            publishProgress(100, 100, 25)
            Timber.i("Performing post-import")
            _postImport()
            publishProgress(100, 100, 50)
            mDst.db.database.setTransactionSuccessful()
            mDst.media.db.database.setTransactionSuccessful()
        } catch (err: Exception) {
            Timber.e(err, "_import() exception")
            throw err
        } finally {
            // endTransaction throws about invalid transaction even when you check first!
            DB.safeEndInTransaction(mDst.db)
            DB.safeEndInTransaction(mDst.media.db)
        }
        Timber.i("Performing vacuum/analyze")
        try {
            mDst.db.execute("vacuum")
        } catch (e: Exception) {
            Timber.w(e)
            // This is actually not fatal but can fail since vacuum takes so much space
            // Allow the import to succeed but recommend the user run check database
            mLog.add(
                res.getString(
                    R.string.import_succeeded_but_check_database,
                    e.localizedMessage
                )
            )
        }
        publishProgress(100, 100, 65)
        try {
            mDst.db.execute("analyze")
        } catch (e: Exception) {
            Timber.w(e)
            // This is actually not fatal but can fail
            // Allow the import to succeed but recommend the user run check database
            mLog.add(
                res.getString(
                    R.string.import_succeeded_but_check_database,
                    e.localizedMessage
                )
            )
        }
        publishProgress(100, 100, 75)
    }

    /**
     * Notes
     * ***********************************************************
     */
    private fun _importNotes() {
        val noteCount = mDst.noteCount()
        // build guid -> (id,mod,mid) hash & map of existing note ids
        mNotes = HashUtil.HashMapInit(noteCount)
        val existing: MutableSet<Long> = HashUtil.HashSetInit(noteCount)
        mDst.db.query("select id, guid, mod, mid from notes").use { cur ->
            while (cur.moveToNext()) {
                val id = cur.getLong(0)
                val guid = cur.getString(1)
                val mod = cur.getLong(2)
                val mid = cur.getLong(3)
                mNotes!![guid] = NoteTriple(id, mod, mid)
                existing.add(id)
            }
        }
        // we ignore updates to changed schemas. we need to note the ignored
        // guids, so we avoid importing invalid cards
        mIgnoredGuids = HashSet()
        // iterate over source collection
        val nbNoteToImport = mSrc.noteCount()
        val add = ArrayList<Array<Any>>(nbNoteToImport)
        var totalAddCount = 0
        val thresExecAdd = 1000
        val update = ArrayList<Array<Any>>(nbNoteToImport)
        var totalUpdateCount = 0
        val thresExecUpdate = 1000
        val dirty = ArrayList<Long>(nbNoteToImport)
        var totalDirtyCount = 0
        val thresExecDirty = 1000
        val usn = mDst.usn()
        var dupes = 0
        val dupesIgnored = ArrayList<String>(nbNoteToImport)
        mDst.db.database.beginTransaction()
        try {
            mSrc.db.database.query(
                "select id, guid, mid, mod, tags, flds, sfld, csum, flags, data  from notes",
                null
            ).use { cur ->
                // Counters for progress updates
                val total = cur.count
                val largeCollection = total > 200
                val onePercent = total / 100
                var i = 0
                while (cur.moveToNext()) {
                    // turn the db result into a mutable list
                    var nid = cur.getLong(0)
                    val guid = cur.getString(1)
                    var mid = cur.getLong(2)
                    val mod = cur.getLong(3)
                    val tags = cur.getString(4)
                    var flds = cur.getString(5)
                    val sfld = cur.getString(6)
                    val csum = cur.getLong(7)
                    val flag = cur.getInt(8)
                    val data = cur.getString(9)
                    val shouldAddAndNewMid = _uniquifyNote(guid, mid)
                    val shouldAdd = shouldAddAndNewMid.first
                    mid = shouldAddAndNewMid.second
                    if (shouldAdd) {
                        // ensure nid is unique
                        while (existing.contains(nid)) {
                            nid += 999
                        }
                        existing.add(nid)
                        // bump usn
                        // update media references in case of dupes
                        flds = _mungeMedia(mid, flds)
                        add.add(arrayOf(nid, guid, mid, mod, usn, tags, flds, sfld, csum, flag, data))
                        dirty.add(nid)
                        // note we have the added guid
                        mNotes!![guid] = NoteTriple(nid, mod, mid)
                    } else {
                        // a duplicate or changed schema - safe to update?
                        dupes += 1
                        if (mAllowUpdate) {
                            val n = mNotes!!.get(guid)
                            val oldNid = n!!.nid
                            val oldMod = n.mod
                            val oldMid = n.mid
                            // will update if incoming note more recent
                            if (oldMod < mod) {
                                // safe if note types identical
                                if (oldMid == mid) {
                                    // incoming note should use existing id
                                    nid = oldNid
                                    flds = _mungeMedia(mid, flds)
                                    update.add(arrayOf(nid, guid, mid, mod, usn, tags, flds, sfld, csum, flag, data))
                                    dirty.add(nid)
                                } else {
                                    dupesIgnored.add(
                                        String.format(
                                            "%s: %s",
                                            mCol.models.get(oldMid)!!.getString("name"),
                                            flds.replace('\u001f', ',')
                                        )
                                    )
                                    mIgnoredGuids!!.add(guid)
                                }
                            }
                        }
                    }
                    i++

                    // add to col partially, so as to avoid OOM
                    if (add.size >= thresExecAdd) {
                        totalAddCount += add.size
                        addNotes(add)
                        add.clear()
                        Timber.d("add notes: %d", totalAddCount)
                    }
                    // add to col partially, so as to avoid OOM
                    if (update.size >= thresExecUpdate) {
                        totalUpdateCount += update.size
                        updateNotes(update)
                        update.clear()
                        Timber.d("update notes: %d", totalUpdateCount)
                    }
                    // add to col partially, so as to avoid OOM
                    if (dirty.size >= thresExecDirty) {
                        totalDirtyCount += dirty.size
                        mDst.updateFieldCache(dirty)
                        mDst.tags.registerNotes(dirty)
                        dirty.clear()
                        Timber.d("dirty notes: %d", totalDirtyCount)
                    }
                    if (total != 0 && (!largeCollection || i % onePercent == 0)) {
                        // Calls to publishProgress are reasonably expensive due to res.getString()
                        publishProgress(i * 100 / total, 0, 0)
                    }
                }
                publishProgress(100, 0, 0)

                // summarize partial add/update/dirty results for total values
                totalAddCount += add.size
                totalUpdateCount += update.size
                totalDirtyCount += dirty.size
                if (dupes > 0) {
                    mLog.add(res.getString(R.string.import_update_details, totalUpdateCount, dupes))
                    if (!dupesIgnored.isEmpty()) {
                        mLog.add(res.getString(R.string.import_update_ignored))
                    }
                }
                // export info for calling code
                this.dupes = dupes
                added = totalAddCount
                updated = totalUpdateCount
                Timber.d("add notes total:    %d", totalAddCount)
                Timber.d("update notes total: %d", totalUpdateCount)
                Timber.d("dirty notes total:  %d", totalDirtyCount)
                // add to col (for last chunk)
                addNotes(add)
                add.clear()
                updateNotes(update)
                update.clear()
                mDst.db.database.setTransactionSuccessful()
            }
        } finally {
            DB.safeEndInTransaction(mDst.db)
        }
        mDst.updateFieldCache(dirty)
        mDst.tags.registerNotes(dirty)
    }

    private fun addNotes(add: List<Array<Any>>) {
        mDst.db.executeManyNoTransaction(
            "insert or replace into notes values (?,?,?,?,?,?,?,?,?,?,?)",
            add
        )
    }

    private fun updateNotes(update: List<Array<Any>>) {
        mDst.db.executeManyNoTransaction("insert or replace into notes values (?,?,?,?,?,?,?,?,?,?,?)", update)
    }

    // determine if note is a duplicate, and adjust mid and/or guid as required
    // returns true if note should be added and its mid
    private fun _uniquifyNote(origGuid: String, srcMid: Long): Pair<Boolean, Long> {
        val dstMid = _mid(srcMid)
        // duplicate Schemas?
        if (srcMid == dstMid) {
            return Pair(!mNotes!!.containsKey(origGuid), srcMid)
        }
        // differing schemas and note doesn't exist?
        if (!mNotes!!.containsKey(origGuid)) {
            return Pair(true, dstMid)
        }
        // schema changed; don't import
        mIgnoredGuids!!.add(origGuid)
        return Pair(false, dstMid)
    }
    /*
      Models
      ***********************************************************
      Models in the two decks may share an ID but not a schema, so we need to
      compare the field & template signature rather than just rely on ID. If
      the schemas don't match, we increment the mid and try again, creating a
      new model if necessary.
     */
    /** Prepare index of schema hashes.  */
    private fun _prepareModels() {
        mModelMap = HashUtil.HashMapInit(mSrc.models.count())
    }

    /** Return local id for remote MID.  */
    @KotlinCleanup("use scope function apply")
    private fun _mid(srcMid: Long): Long {
        // already processed this mid?
        if (mModelMap!!.containsKey(srcMid)) {
            return mModelMap!![srcMid]!!
        }
        var mid = srcMid
        val srcModel = mSrc.models.get(srcMid)
        val srcScm = mSrc.models.scmhash(srcModel!!)
        while (true) {
            // missing from target col?
            if (!mDst.models.have(mid)) {
                // copy it over
                val model = srcModel.deepClone()
                model.put("id", mid)
                model.put("mod", TimeManager.time.intTime())
                model.put("usn", mCol.usn())
                mDst.models.update(model)
                break
            }
            // there's an existing model; do the schemas match?
            val dstModel = mDst.models.get(mid)
            val dstScm = mDst.models.scmhash(dstModel!!)
            if (srcScm == dstScm) {
                // they do; we can reuse this mid
                val model = srcModel.deepClone()
                model.put("id", mid)
                model.put("mod", TimeManager.time.intTime())
                model.put("usn", mCol.usn())
                mDst.models.update(model)
                break
            }
            // as they don't match, try next id
            mid += 1
        }
        // save map and return new mid
        mModelMap!![srcMid] = mid
        return mid
    }
    /*
     * Decks
     * ***********************************************************
     */
    /** Given did in src col, return local id.  */
    @KotlinCleanup("use scope function")
    private fun _did(did: Long): Long {
        // already converted?
        if (mDecks!!.containsKey(did)) {
            return mDecks!![did]!!
        }
        // get the name in src
        val g = mSrc.decks.get(did)
        var name = g.getString("name")
        // if there's a prefix, replace the top level deck
        if (!TextUtils.isEmpty(mDeckPrefix)) {
            val parts = Arrays.asList(*Decks.path(name))
            val tmpname = TextUtils.join("::", parts.subList(1, parts.size))
            name = mDeckPrefix!!
            if (!TextUtils.isEmpty(tmpname)) {
                name += "::$tmpname"
            }
        }
        // Manually create any parents so we can pull in descriptions
        var head: String? = ""
        val parents = Arrays.asList(*Decks.path(name))
        for (parent in parents.subList(0, parents.size - 1)) {
            if (!TextUtils.isEmpty(head)) {
                head += "::"
            }
            head += parent
            val idInSrc = mSrc.decks.id_safe(head!!)
            _did(idInSrc)
        }
        // create in local
        val newid = mDst.decks.id_safe(name)
        // pull conf over
        if (g.has("conf") && g.getLong("conf") != 1L) {
            val conf = mSrc.decks.getConf(g.getLong("conf"))
            mDst.decks.save(conf!!)
            mDst.decks.updateConf(conf)
            val g2 = mDst.decks.get(newid)
            g2.put("conf", g.getLong("conf"))
            mDst.decks.save(g2)
        }
        // save desc
        val deck = mDst.decks.get(newid)
        deck.put("desc", g.getString("desc"))
        mDst.decks.save(deck)
        // add to deck map and return
        mDecks!![did] = newid
        return newid
    }

    /**
     * Cards
     * ***********************************************************
     */
    private fun _importCards() {
        if (mMustResetLearning) {
            try {
                mSrc.changeSchedulerVer(2)
            } catch (e: ConfirmModSchemaException) {
                throw RuntimeException(
                    "Changing the scheduler of an import should not cause schema modification",
                    e
                )
            }
        }
        // build map of guid -> (ord -> cid) and used id cache
        /*
         * Since we can't use a tuple as a key in Java, we resort to indexing twice with nested maps.
         * Python: (guid, ord) -> cid
         * Java: guid -> ord -> cid
         */
        val nbCard = mDst.cardCount()
        val cardsByGuid: MutableMap<String, MutableMap<Int, Long>> = HashUtil.HashMapInit(nbCard)
        val existing: MutableSet<Long> = HashUtil.HashSetInit(nbCard)
        mDst.db.query(
            "select f.guid, c.ord, c.id from cards c, notes f " +
                "where c.nid = f.id"
        ).use { cur ->
            while (cur.moveToNext()) {
                val guid = cur.getString(0)
                val ord = cur.getInt(1)
                val cid = cur.getLong(2)
                existing.add(cid)
                if (cardsByGuid.containsKey(guid)) {
                    cardsByGuid[guid]!![ord] = cid
                } else {
                    val map: MutableMap<Int, Long> =
                        HashMap() // The size is at most the number of card type in the note type.
                    map[ord] = cid
                    cardsByGuid[guid] = map
                }
            }
        }
        // loop through src
        val nbCardsToImport = mSrc.cardCount()
        val cards: MutableList<Array<Any>> = ArrayList(nbCardsToImport)
        var totalCardCount = 0
        val thresExecCards = 1000
        val revlog: MutableList<Array<Any>> = ArrayList(mSrc.sched.logCount())
        var totalRevlogCount = 0
        val thresExecRevlog = 1000
        val usn = mDst.usn()
        val aheadBy = (mSrc.sched.today - mDst.sched.today).toLong()
        mDst.db.database.beginTransaction()
        try {
            mSrc.db.query(
                "select f.guid, c.id, c.did, c.ord, c.type, c.queue, c.due, c.ivl, c.factor, c.reps, c.lapses, c.left, c.odue, c.odid, c.flags, c.data from cards c, notes f " +
                    "where c.nid = f.id"
            ).use { cur ->

                // Counters for progress updates
                val total = cur.count
                val largeCollection = total > 200
                val onePercent = total / 100
                var i = 0
                while (cur.moveToNext()) {
                    val guid = cur.getString(0)
                    var cid = cur.getLong(1)
                    val scid = cid // To keep track of card id in source
                    var did = cur.getLong(2)
                    val ord = cur.getInt(3)
                    @CARD_TYPE var type = cur.getInt(4)
                    @CARD_QUEUE var queue = cur.getInt(5)
                    var due = cur.getLong(6)
                    val ivl = cur.getLong(7)
                    val factor = cur.getLong(8)
                    val reps = cur.getInt(9)
                    val lapses = cur.getInt(10)
                    val left = cur.getInt(11)
                    var odue = cur.getLong(12)
                    var odid = cur.getLong(13)
                    val flags = cur.getInt(14)
                    val data = cur.getString(15)
                    if (mIgnoredGuids!!.contains(guid)) {
                        continue
                    }
                    // does the card's note exist in dst col?
                    if (!mNotes!!.containsKey(guid)) {
                        continue
                    }
                    // does the card already exist in the dst col?
                    if (cardsByGuid.containsKey(guid) && cardsByGuid[guid]!!.containsKey(ord)) {
                        // fixme: in future, could update if newer mod time
                        continue
                    }
                    // ensure the card id is unique
                    while (existing.contains(cid)) {
                        cid += 999
                    }
                    existing.add(cid)
                    // update cid, nid, etc
                    val nid = mNotes!![guid]!!.nid
                    did = _did(did)
                    val mod = TimeManager.time.intTime()
                    // review cards have a due date relative to collection
                    if (queue == QUEUE_TYPE_REV || queue == QUEUE_TYPE_DAY_LEARN_RELEARN || type == CARD_TYPE_REV) {
                        due -= aheadBy
                    }
                    // odue needs updating too
                    if (odue != 0L) {
                        odue -= aheadBy
                    }
                    // if odid true, convert card from filtered to normal
                    if (odid != 0L) {
                        // odid
                        odid = 0
                        // odue
                        due = odue
                        odue = 0
                        // queue
                        if (type == CARD_TYPE_LRN) { // type
                            queue = QUEUE_TYPE_NEW
                        } else {
                            queue = type
                        }
                        // type
                        if (type == CARD_TYPE_LRN) {
                            type = CARD_TYPE_NEW
                        }
                    }
                    cards.add(arrayOf(cid, nid, did, ord, mod, usn, type, queue, due, ivl, factor, reps, lapses, left, odue, odid, flags, data))
                    mSrc.db.query("select * from revlog where cid = $scid").use { cur2 ->
                        while (cur2.moveToNext()) {
                            val rev = arrayOf<Any>(
                                cur2.getLong(0),
                                cur2.getLong(1),
                                cur2.getInt(2),
                                cur2.getInt(3),
                                cur2.getLong(4),
                                cur2.getLong(5),
                                cur2.getLong(6),
                                cur2.getLong(7),
                                cur2.getInt(8)
                            )
                            rev[1] = cid
                            rev[2] = mDst.usn()
                            revlog.add(rev)
                        }
                    }
                    i++
                    // apply card changes partially
                    if (cards.size >= thresExecCards) {
                        totalCardCount += cards.size
                        insertCards(cards)
                        cards.clear()
                        Timber.d("add cards: %d", totalCardCount)
                    }
                    // apply revlog changes partially
                    if (revlog.size >= thresExecRevlog) {
                        totalRevlogCount += revlog.size
                        insertRevlog(revlog)
                        revlog.clear()
                        Timber.d("add revlog: %d", totalRevlogCount)
                    }
                    if (total != 0 && (!largeCollection || i % onePercent == 0)) {
                        publishProgress(100, i * 100 / total, 0)
                    }
                }
                publishProgress(100, 100, 0)

                // count total values
                totalCardCount += cards.size
                totalRevlogCount += revlog.size
                Timber.d("add cards total:  %d", totalCardCount)
                Timber.d("add revlog total: %d", totalRevlogCount)
                // apply (for last chunk)
                insertCards(cards)
                cards.clear()
                insertRevlog(revlog)
                revlog.clear()
                mCardCount = totalCardCount
                mDst.db.database.setTransactionSuccessful()
            }
        } finally {
            DB.safeEndInTransaction(mDst.db)
        }
    }

    private fun insertCards(cards: List<Array<Any>>) {
        mDst.db.executeManyNoTransaction(
            "insert or ignore into cards values (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
            cards
        )
    }

    private fun insertRevlog(revlog: List<Array<Any>>) {
        mDst.db.executeManyNoTransaction(
            "insert or ignore into revlog values (?,?,?,?,?,?,?,?,?)",
            revlog
        )
    }

    /**
     * Media
     * ***********************************************************
     */
    // note: this func only applies to imports of .anki2. For .apkg files, the
    // apkg importer does the copying
    private fun _importStaticMedia() {
        // Import any '_foo' prefixed media files regardless of whether
        // they're used on notes or not
        val dir = mSrc.media.dir()
        if (!File(dir).exists()) {
            return
        }
        for (f in File(dir).listFiles()!!) {
            val fname = f.name
            if (fname.startsWith("_") && !mDst.media.have(fname)) {
                try {
                    _srcMediaData(fname).use { data -> _writeDstMedia(fname, data!!) }
                } catch (e: IOException) {
                    Timber.w(e, "Failed to close stream")
                }
            }
        }
    }

    private fun _mediaData(fname: String, directory: String): BufferedInputStream? {
        var dir: String? = directory
        if (dir == null) {
            dir = mSrc.media.dir()
        }
        val path = File(dir, fname).absolutePath
        return try {
            BufferedInputStream(FileInputStream(path), MEDIAPICKLIMIT * 2)
        } catch (e: IOException) {
            Timber.w(e)
            null
        }
    }

    /**
     * Data for FNAME in src collection.
     */
    protected open fun _srcMediaData(fname: String): BufferedInputStream? {
        return _mediaData(fname, mSrc.media.dir())
    }

    /**
     * Data for FNAME in dst collection.
     */
    private fun _dstMediaData(fname: String): BufferedInputStream? {
        return _mediaData(fname, mDst.media.dir())
    }

    private fun _writeDstMedia(fname: String?, data: BufferedInputStream) {
        try {
            val path = File(mDst.media.dir(), Utils.nfcNormalized(fname)).absolutePath
            Utils.writeToFile(data, path)
            // Mark file addition to media db (see note in Media.java)
            mDst.media.markFileAdd(fname)
        } catch (e: IOException) {

            // the user likely used subdirectories
            Timber.e(e, "Error copying file %s.", fname)

            // If we are out of space, we should re-throw
            if (e.cause != null && e.cause!!.message!!.contains("No space left on device")) {
                // we need to let the user know why we are failing
                Timber.e("We are out of space, bubbling up the file copy exception")
                throw RuntimeException(e)
            }
        }
    }

    // running splitFields() on every note is fairly expensive and actually not necessary
    private fun _mungeMedia(mid: Long, fields: String): String {
        var _fields = fields
        for (p in Media.REGEXPS) {
            val m = p.matcher(_fields)
            val sb = StringBuffer()
            val fnameIdx = Media.indexOfFname(p)
            while (m.find()) {
                val fname = m.group(fnameIdx)!!
                try {
                    val srcData = _srcMediaData(fname)
                    val dstData = _dstMediaData(fname)
                    if (srcData == null) {
                        // file was not in source, ignore
                        m.appendReplacement(sb, Matcher.quoteReplacement(m.group(0)!!))
                    }
                    // if model-local file exists from a previous import, use that
                    val split = Utils.splitFilename(fname)
                    val name = split[0]
                    val ext = split[1]
                    val lname = String.format(Locale.US, "%s_%s%s", name, mid, ext)
                    if (mDst.media.have(lname)) {
                        m.appendReplacement(
                            sb,
                            Matcher.quoteReplacement(m.group(0)!!.replace(fname, lname))
                        )
                        continue
                    } else if (dstData == null || compareMedia(
                            srcData!!,
                            dstData
                        )
                    ) { // if missing or the same, pass unmodified
                        // need to copy?
                        if (dstData == null) {
                            _writeDstMedia(fname, srcData!!)
                        }
                        m.appendReplacement(sb, Matcher.quoteReplacement(m.group(0)!!))
                        continue
                    }
                    // exists but does not match, so we need to dedupe
                    _writeDstMedia(lname, srcData)
                    m.appendReplacement(
                        sb,
                        Matcher.quoteReplacement(m.group(0)!!.replace(fname, lname))
                    )
                } catch (e: IOException) {
                    Timber.w(e, "Failed to close stream")
                }
            }
            m.appendTail(sb)
            _fields = sb.toString()
        }
        return _fields
    }

    /**
     * Post-import cleanup
     * ***********************************************************
     */
    private fun _postImport() {
        for (did in mDecks!!.values) {
            mCol.sched.maybeRandomizeDeck(did)
        }
        // make sure new position is correct
        mDst.set_config("nextPos", mDst.db.queryLongScalar("select max(due)+1 from cards where type = $CARD_TYPE_NEW"))
        mDst.save()
    }

    /**
     * The methods below are not in LibAnki.
     * ***********************************************************
     */
    private fun compareMedia(lhis: BufferedInputStream, rhis: BufferedInputStream): Boolean {
        val lhbytes = _mediaPick(lhis)
        val rhbytes = _mediaPick(rhis)
        return Arrays.equals(lhbytes, rhbytes)
    }

    /**
     * Return the contents of the given input stream, limited to Anki2Importer.MEDIAPICKLIMIT bytes This is only used
     * for comparison of media files with the limited resources of mobile devices
     */
    @KotlinCleanup("rename `is` ")
    private fun _mediaPick(`is`: BufferedInputStream): ByteArray? {
        return try {
            val baos = ByteArrayOutputStream(MEDIAPICKLIMIT * 2)
            val buf = ByteArray(MEDIAPICKLIMIT)
            var readLen: Int
            var readSoFar = 0
            `is`.mark(MEDIAPICKLIMIT * 2)
            while (true) {
                readLen = `is`.read(buf)
                baos.write(buf)
                if (readLen == -1) {
                    break
                }
                readSoFar += readLen
                if (readSoFar > MEDIAPICKLIMIT) {
                    break
                }
            }
            `is`.reset()
            val result = ByteArray(MEDIAPICKLIMIT)
            System.arraycopy(
                baos.toByteArray(),
                0,
                result,
                0,
                Math.min(baos.size(), MEDIAPICKLIMIT)
            )
            result
        } catch (e: IOException) {
            Timber.w(e)
            null
        }
    }

    /**
     * @param notesDone Percentage of notes complete.
     * @param cardsDone Percentage of cards complete.
     * @param postProcess Percentage of remaining tasks complete.
     */
    @KotlinCleanup("simplify with ?.")
    protected fun publishProgress(notesDone: Int, cardsDone: Int, postProcess: Int) {
        if (mProgress != null) {
            mProgress.publishProgress(
                res.getString(R.string.import_progress, notesDone, cardsDone, postProcess)
            )
        }
    }

    /* The methods below are only used for testing. */
    fun setDupeOnSchemaChange(b: Boolean) {
        mDupeOnSchemaChange = b
    }

    companion object {
        private const val MEDIAPICKLIMIT = 1024
    }

    init {
        @KotlinCleanup("combined declaration and initialization")
        mNeedMapper = false
        mDeckPrefix = null
        mAllowUpdate = true
        mDupeOnSchemaChange = false
    }
}
