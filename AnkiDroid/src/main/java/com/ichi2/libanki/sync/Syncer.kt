/***************************************************************************************
 * Copyright (c) 2011 Norbert Nagold <norbert.nagold@gmail.com>                         *
 * Copyright (c) 2014 Timothy Rae <perceptualchaos2@gmail.com>                          *
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

package com.ichi2.libanki.sync

import android.database.Cursor
import android.database.SQLException
import com.ichi2.anki.CrashReportService
import com.ichi2.anki.R
import com.ichi2.anki.analytics.UsageAnalytics
import com.ichi2.anki.analytics.UsageAnalytics.sendAnalyticsEvent
import com.ichi2.anki.exception.UnknownHttpResponseException
import com.ichi2.async.Connection
import com.ichi2.async.Connection.Companion.isCancelled
import com.ichi2.libanki.*
import com.ichi2.libanki.Collection
import com.ichi2.libanki.sched.AbstractDeckTreeNode
import com.ichi2.libanki.sync.Syncer.ConnectionResultType.*
import com.ichi2.libanki.utils.TimeManager.time
import com.ichi2.utils.*
import com.ichi2.utils.HashUtil.HashMapInit
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import timber.log.Timber
import java.io.IOException
import java.net.UnknownHostException
import java.util.*

@KotlinCleanup("IDE-lint")
class Syncer(
    private val col: Collection,
    private val remoteServer: RemoteServer,
    private val hostNum: HostNum
) {
    // private long mRScm;
    private var mMaxUsn = 0

    // private long mLScm;
    private var mMinUsn = 0
    private var mLNewer = false
    var syncMsg: String? = null
        private set
    private var mTablesLeft: LinkedList<String>? = null
    private var mCursor: Cursor? = null

    enum class ConnectionResultType(private val message: String) {
        BAD_AUTH("badAuth"),
        NO_CHANGES("noChanges"),
        CLOCK_OFF("clockOff"),
        FULL_SYNC("fullSync"),
        DB_ERROR("dbError"),
        BASIC_CHECK_FAILED("basicCheckFailed"),
        OVERWRITE_ERROR("overwriteError"),
        REMOTE_DB_ERROR("remoteDbError"),
        SD_ACCESS_ERROR("sdAccessError"),
        FINISH_ERROR("finishError"),
        IO_EXCEPTION("IOException"),
        GENERIC_ERROR("genericError"),
        OUT_OF_MEMORY_ERROR("outOfMemoryError"),
        SANITY_CHECK_ERROR("sanityCheckError"),
        SERVER_ABORT("serverAbort"),
        MEDIA_SYNC_SERVER_ERROR("mediaSyncServerError"),
        CUSTOM_SYNC_SERVER_URL("customSyncServerUrl"),
        USER_ABORTED_SYNC("userAbortedSync"),
        SUCCESS("success"),
        ARBITRARY_STRING("arbitraryString"), // arbitrary error message received from sync

        MEDIA_SANITY_FAILED("sanityFailed"),
        CORRUPT("corrupt"),
        OK("OK"),

        // The next three ones are the only that can be returned during login
        UPGRADE_REQUIRED("upgradeRequired"),
        CONNECTION_ERROR("connectionError"),
        ERROR("error"),
        NETWORK_ERROR("noNetwork");

        override fun toString(): String {
            return message
        }
    }

    @Throws(UnknownHttpResponseException::class)
    fun sync(con: Connection): Pair<ConnectionResultType, Any?>? {
        syncMsg = ""
        // if the deck has any pending changes, flush them first and bump mod time
        col.save()
        // step 1: login & metadata
        val ret = try {
            remoteServer.meta()
        } catch (e: Exception) {
            Timber.e(e.toString())
            if (e is UnknownHostException) {
                return Pair(NETWORK_ERROR, null)
            } else {
                throw e
            }
        }
        val returntype = ret.code
        if (returntype == 403) {
            return Pair(BAD_AUTH, null)
        }
        try {
            col.db.database.beginTransaction()
            try {
                Timber.i("Sync: getting meta data from server")
                val rMeta = JSONObject(ret.body!!.string())
                col.log("rmeta", rMeta)
                syncMsg = rMeta.getString("msg")
                if (!rMeta.getBoolean("cont")) {
                    // Don't add syncMsg; it can be fetched by UI code using the accessor
                    return Pair(SERVER_ABORT, null)
                } else {
                    // don't abort, but ui should show messages after sync finishes
                    // and require confirmation if it's non-empty
                }
                throwExceptionIfCancelled(con)
                val rscm = rMeta.getLong("scm")
                val rts = rMeta.getInt("ts")
                val rMod = rMeta.getLong("mod")
                mMaxUsn = rMeta.getInt("usn")
                // skip uname, AnkiDroid already stores and shows it
                trySetHostNum(rMeta)
                Timber.i("Sync: building local meta data")
                val lMeta = meta()
                col.log("lmeta", lMeta)
                val lMod = lMeta.getLong("mod")
                mMinUsn = lMeta.getInt("usn")
                val lscm = lMeta.getLong("scm")
                val lts = lMeta.getInt("ts")
                val diff = Math.abs(rts - lts).toLong()
                if (diff > 300) {
                    col.log("clock off")
                    return Pair(CLOCK_OFF, diff)
                }
                if (lMod == rMod) {
                    Timber.i("Sync: no changes - returning")
                    col.log("no changes")
                    return Pair(NO_CHANGES, null)
                } else if (lscm != rscm) {
                    Timber.i("Sync: full sync necessary - returning")
                    col.log("schema diff")
                    return Pair(FULL_SYNC, null)
                }
                mLNewer = lMod > rMod
                // step 1.5: check collection is valid
                if (!col.basicCheck()) {
                    col.log("basic check")
                    return Pair(BASIC_CHECK_FAILED, null)
                }
                throwExceptionIfCancelled(con)
                // step 2: deletions
                publishProgress(con, R.string.sync_deletions_message)
                Timber.i("Sync: collection removed data")
                val lrem = removed()
                val o = JSONObject()
                o.put("minUsn", mMinUsn)
                o.put("lnewer", mLNewer)
                o.put("graves", lrem)
                Timber.i("Sync: sending and receiving removed data")
                val rrem = remoteServer.start(o)
                Timber.i("Sync: applying removed data")
                throwExceptionIfCancelled(con)
                remove(rrem)
                // ... and small objects
                publishProgress(con, R.string.sync_small_objects_message)
                Timber.i("Sync: collection small changes")
                val lchg = changes()
                val sch = JSONObject()
                sch.put("changes", lchg)
                Timber.i("Sync: sending and receiving small changes")
                val rchg = remoteServer.applyChanges(sch)
                throwExceptionIfCancelled(con)
                Timber.i("Sync: merging small changes")
                try {
                    mergeChanges(lchg, rchg)
                } catch (e: UnexpectedSchemaChange) {
                    Timber.w(e)
                    remoteServer.abort()
                    _forceFullSync()
                }
                // step 3: stream large tables from server
                publishProgress(con, R.string.sync_download_chunk)
                while (true) {
                    throwExceptionIfCancelled(con)
                    Timber.i("Sync: downloading chunked data")
                    val chunk = remoteServer.chunk()
                    col.log("server chunk", chunk)
                    Timber.i("Sync: applying chunked data")
                    applyChunk(chunk)
                    if (chunk.getBoolean("done")) {
                        break
                    }
                }
                // step 4: stream to server
                publishProgress(con, R.string.sync_upload_chunk)
                while (true) {
                    throwExceptionIfCancelled(con)
                    Timber.i("Sync: collecting chunked data")
                    val chunk = chunk()
                    col.log("client chunk", chunk)
                    val sech = JSONObject()
                    sech.put("chunk", chunk)
                    Timber.i("Sync: sending chunked data")
                    remoteServer.applyChunk(sech)
                    if (chunk.getBoolean("done")) {
                        break
                    }
                }
                // step 5: sanity check
                val c = sanityCheck()
                val sanity = remoteServer.sanityCheck2(c)
                if ("ok" != sanity.optString("status", "bad")) {
                    return sanityCheckError(c, sanity)
                }
                // finalize
                publishProgress(con, R.string.sync_finish_message)
                Timber.i("Sync: sending finish command")
                val mod = remoteServer.finish()
                if (mod == 0L) {
                    return Pair(FINISH_ERROR, null)
                }
                Timber.i("Sync: finishing")
                finish(mod)
                publishProgress(con, R.string.sync_writing_db)
                col.db.database.setTransactionSuccessful()
            } finally {
                col.db.safeEndInTransaction()
            }
        } catch (e: IllegalStateException) {
            throw RuntimeException(e)
        } catch (e: OutOfMemoryError) {
            CrashReportService.sendExceptionReport(e, "Syncer-sync")
            Timber.w(e)
            return Pair(OUT_OF_MEMORY_ERROR, null)
        } catch (e: IOException) {
            CrashReportService.sendExceptionReport(e, "Syncer-sync")
            Timber.w(e)
            return Pair(IO_EXCEPTION, null)
        }
        return Pair(SUCCESS, null)
    }

    private fun trySetHostNum(rMeta: JSONObject) {
        // We perform this as old version of the sync server may not provide the hostNum
        // And it's fine to continue without one.
        try {
            if (rMeta.has("hostNum")) {
                hostNum.hostNum = rMeta.getInt("hostNum")
            }
        } catch (e: Exception) {
            Timber.w(e, "Failed to set hostNum")
        }
    }

    protected fun sanityCheckError(c: JSONObject?, sanity: JSONObject?): Pair<ConnectionResultType, Any?> {
        col.log("sanity check failed", c, sanity)
        sendAnalyticsEvent(UsageAnalytics.Category.SYNC, "sanityCheckError")
        _forceFullSync()
        return Pair(SANITY_CHECK_ERROR, null)
    }

    private fun _forceFullSync() {
        // roll back and force full sync
        col.modSchemaNoCheck()
        col.save()
    }

    private fun publishProgress(con: Connection?, id: Int) {
        con?.publishProgress(id)
    }

    @KotlinCleanup("use apply{}")
    @Throws(JSONException::class)
    fun meta(): JSONObject {
        val j = JSONObject()
        j.put("mod", col.mod)
        j.put("scm", col.scm)
        j.put("usn", col.usnForSync)
        j.put("ts", time.intTime())
        j.put("musn", 0)
        j.put("msg", "")
        j.put("cont", true)
        return j
    }

    /** Bundle up small objects.  */
    @KotlinCleanup("use apply{}")
    fun changes(): JSONObject {
        val o = JSONObject()
        o.put("models", models)
        o.put("decks", decks)
        o.put("tags", tags)
        if (mLNewer) {
            o.put("conf", conf)
            o.put("crt", col.crt)
        }
        return o
    }

    @Throws(UnexpectedSchemaChange::class)
    fun applyChanges(changes: JSONObject): JSONObject {
        val lchg = changes()
        // merge our side before returning
        mergeChanges(lchg, changes)
        return lchg
    }

    @Throws(UnexpectedSchemaChange::class)
    fun mergeChanges(@Suppress("UNUSED_PARAMETER") lchg: JSONObject?, rchg: JSONObject) {
        // then the other objects
        mergeModels(rchg.getJSONArray("models"))
        mergeDecks(rchg.getJSONArray("decks"))
        mergeTags(rchg.getJSONArray("tags"))
        if (rchg.has("conf")) {
            mergeConf(rchg.getJSONObject("conf"))
        }
        // this was left out of earlier betas
        if (rchg.has("crt")) {
            col.crt = rchg.getLong("crt")
        }
        prepareToChunk()
    }

    fun sanityCheck(): JSONObject {
        val result = JSONObject()
        return try {
            if (col.db.queryScalar("SELECT count() FROM cards WHERE nid NOT IN (SELECT id FROM notes)") != 0) {
                Timber.e("Sync - SanityCheck: there are cards without mother notes")
                result.put("client", "missing notes")
                return result
            }
            if (col.db.queryScalar("SELECT count() FROM notes WHERE id NOT IN (SELECT DISTINCT nid FROM cards)") != 0) {
                Timber.e("Sync - SanityCheck: there are notes without cards")
                result.put("client", "missing cards")
                return result
            }
            if (col.db.queryScalar("SELECT count() FROM cards WHERE usn = -1") != 0) {
                Timber.e("Sync - SanityCheck: there are unsynced cards")
                result.put("client", "cards had usn = -1")
                return result
            }
            if (col.db.queryScalar("SELECT count() FROM notes WHERE usn = -1") != 0) {
                Timber.e("Sync - SanityCheck: there are unsynced notes")
                result.put("client", "notes had usn = -1")
                return result
            }
            if (col.db.queryScalar("SELECT count() FROM revlog WHERE usn = -1") != 0) {
                Timber.e("Sync - SanityCheck: there are unsynced revlogs")
                result.put("client", "revlog had usn = -1")
                return result
            }
            if (col.db.queryScalar("SELECT count() FROM graves WHERE usn = -1") != 0) {
                Timber.e("Sync - SanityCheck: there are unsynced graves")
                result.put("client", "graves had usn = -1")
                return result
            }
            for (g in col.decks.all(col)) {
                if (g.getInt("usn") == -1) {
                    Timber.e("Sync - SanityCheck: unsynced deck: %s", g.getString("name"))
                    result.put("client", "deck had usn = -1")
                    return result
                }
            }
            if (col.tags.minusOneValue()) {
                Timber.e("Sync - SanityCheck: there are unsynced tags")
                result.put("client", "tag had usn = -1")
                return result
            }
            var found = false
            for (m in col.models.all(col)) {
                if (col.server) {
                    // the web upgrade was mistakenly setting usn
                    if (m.getInt("usn") < 0) {
                        m.put("usn", 0)
                        found = true
                    }
                } else {
                    if (m.getInt("usn") == -1) {
                        Timber.e("Sync - SanityCheck: unsynced model: %s", m.getString("name"))
                        result.put("client", "model had usn = -1")
                        return result
                    }
                }
            }
            if (found) {
                col.models.save(col)
            }
            // check for missing parent decks
            col.sched.quickDeckDueTree<AbstractDeckTreeNode>(col)
            // return summary of deck
            val check = JSONArray()
            val counts = JSONArray()

            col.sched.resetCounts(col)
            val counts_ = col.sched.counts(col)
            @KotlinCleanup("apply{}")
            counts.put(counts_.new)
            counts.put(counts_.lrn)
            counts.put(counts_.rev)
            check.put(counts)
            check.put(col.db.queryScalar("SELECT count() FROM cards"))
            check.put(col.db.queryScalar("SELECT count() FROM notes"))
            check.put(col.db.queryScalar("SELECT count() FROM revlog"))
            check.put(col.db.queryScalar("SELECT count() FROM graves"))
            check.put(col.models.all(col).size)
            check.put(col.decks.all(col).size)
            check.put(col.decks.allConf(col).size)
            result.put("client", check)
            result
        } catch (e: JSONException) {
            Timber.e(e, "Syncer.sanityCheck()")
            throw RuntimeException(e)
        }
    }

    // private Map<String, Object> sanityCheck2(JSONArray client) {
    // Object server = sanityCheck();
    // Map<String, Object> result = new HashMap<String, Object>();
    // if (client.equals(server)) {
    // result.put("status", "ok");
    // } else {
    // result.put("status", "bad");
    // result.put("c", client);
    // result.put("s", server);
    // }
    // return result;
    // }
    private fun usnLim(): Pair<String, Array<Any>> {
        return if (col.server) {
            Pair(
                "usn >= ?",
                arrayOf(mMinUsn)
            )
        } else {
            Pair("usn = -1", arrayOf())
        }
    }

    fun finish(): Long {
        return finish(0)
    }

    private fun finish(mod: Long): Long {
        var _mod = mod
        if (_mod == 0L) {
            // server side; we decide new mod time
            _mod = time.intTimeMS()
        }
        col.ls = _mod
        col.setUsnAfterSync(mMaxUsn + 1)
        // ensure we save the mod time even if no changes made
        col.db.mod = true
        col.save(null, _mod)
        return _mod
    }

    /**
     * Chunked syncing ********************************************************************
     */
    private fun prepareToChunk() {
        mTablesLeft = LinkedList()
        mTablesLeft!!.add("revlog")
        mTablesLeft!!.add("cards")
        mTablesLeft!!.add("notes")
        mCursor = null
    }

    private fun cursorForTable(table: String): Cursor {
        val limAndArg = usnLim()
        return if ("revlog" == table) {
            col.db.query("SELECT id, cid, " + mMaxUsn + ", ease, ivl, lastIvl, factor, time, type FROM revlog WHERE " + limAndArg.first, *limAndArg.second)
        } else if ("cards" == table) {
            col.db.query("SELECT id, nid, did, ord, mod, " + mMaxUsn + ", type, queue, due, ivl, factor, reps, lapses, left, odue, odid, flags, data FROM cards WHERE " + limAndArg.first, *limAndArg.second)
        } else {
            col.db.query("SELECT id, guid, mid, mod, " + mMaxUsn + ", tags, flds, '', '', flags, data FROM notes WHERE " + limAndArg.first, *limAndArg.second)
        }
    }

    private fun columnTypesForQuery(table: String): List<Int> {
        return if ("revlog" == table) {
            listOf(
                TYPE_INTEGER, TYPE_INTEGER, TYPE_INTEGER, TYPE_INTEGER, TYPE_INTEGER, TYPE_INTEGER,
                TYPE_INTEGER, TYPE_INTEGER, TYPE_INTEGER
            )
        } else if ("cards" == table) {
            listOf(
                TYPE_INTEGER, TYPE_INTEGER, TYPE_INTEGER, TYPE_INTEGER, TYPE_INTEGER, TYPE_INTEGER,
                TYPE_INTEGER, TYPE_INTEGER, TYPE_INTEGER, TYPE_INTEGER, TYPE_INTEGER, TYPE_INTEGER, TYPE_INTEGER,
                TYPE_INTEGER, TYPE_INTEGER, TYPE_INTEGER, TYPE_INTEGER, TYPE_STRING
            )
        } else {
            listOf(
                TYPE_INTEGER, TYPE_STRING, TYPE_INTEGER, TYPE_INTEGER, TYPE_INTEGER, TYPE_STRING,
                TYPE_STRING, TYPE_STRING, TYPE_STRING, TYPE_INTEGER, TYPE_STRING
            )
        }
    }

    fun chunk(): JSONObject {
        val buf = JSONObject()
        buf.put("done", false)
        var lim = 250
        var colTypes: List<Int>?
        while (!mTablesLeft!!.isEmpty() && lim > 0) {
            val curTable = mTablesLeft!!.first
            if (mCursor == null) {
                mCursor = cursorForTable(curTable)
            }
            colTypes = columnTypesForQuery(curTable)
            val rows = JSONArray()
            val count = mCursor!!.columnCount
            var fetched = 0
            while (mCursor!!.moveToNext()) {
                val r = JSONArray()
                for (i in 0 until count) {
                    when (colTypes[i]) {
                        TYPE_STRING -> r.put(mCursor!!.getString(i))
                        TYPE_FLOAT -> r.put(mCursor!!.getDouble(i))
                        TYPE_INTEGER -> r.put(mCursor!!.getLong(i))
                    }
                }
                rows.put(r)
                if (++fetched == lim) {
                    break
                }
            }
            if (fetched != lim) {
                // table is empty
                mTablesLeft!!.removeFirst()
                mCursor!!.close()
                mCursor = null
                // if we're the client, mark the objects as having been sent
                if (!col.server) {
                    col.db.execute("UPDATE $curTable SET usn=? WHERE usn=-1", mMaxUsn)
                }
            }
            buf.put(curTable, rows)
            lim -= fetched
        }
        if (mTablesLeft!!.isEmpty()) {
            buf.put("done", true)
        }
        return buf
    }

    fun applyChunk(chunk: JSONObject) {
        if (chunk.has("revlog")) {
            mergeRevlog(chunk.getJSONArray("revlog"))
        }
        if (chunk.has("cards")) {
            mergeCards(chunk.getJSONArray("cards"))
        }
        if (chunk.has("notes")) {
            mergeNotes(chunk.getJSONArray("notes"))
        }
    }

    /**
     * Deletions ********************************************************************
     */
    @KotlinCleanup("use apply{}")
    private fun removed(): JSONObject {
        val cards = JSONArray()
        val notes = JSONArray()
        val decks = JSONArray()
        val limAndArgs = usnLim()
        col.db
            .query("SELECT oid, type FROM graves WHERE " + limAndArgs.first, *limAndArgs.second)
            .use { cur ->
                while (cur.moveToNext()) {
                    @Consts.REM_TYPE val type = cur.getInt(1)
                    when (type) {
                        Consts.REM_CARD -> cards.put(cur.getLong(0))
                        Consts.REM_NOTE -> notes.put(cur.getLong(0))
                        Consts.REM_DECK -> decks.put(cur.getLong(0))
                    }
                }
            }
        if (!col.server) {
            col.db.execute("UPDATE graves SET usn=$mMaxUsn WHERE usn=-1")
        }
        @KotlinCleanup("apply{}")
        val o = JSONObject()
        o.put("cards", cards)
        o.put("notes", notes)
        o.put("decks", decks)
        return o
    }

    fun start(minUsn: Int, lnewer: Boolean, graves: JSONObject): JSONObject {
        mMaxUsn = col.usnForSync
        mMinUsn = minUsn
        mLNewer = !lnewer
        val lgraves = removed()
        remove(graves)
        return lgraves
    }

    private fun remove(graves: JSONObject) {
        // pretend to be the server so we don't set usn = -1
        val wasServer = col.server
        col.server = true
        // notes first, so we don't end up with duplicate graves
        col._remNotes(graves.getJSONArray("notes").toLongList())
        // then cards
        col.remCards(graves.getJSONArray("cards").toLongList(), false)
        // and decks
        val decks = graves.getJSONArray("decks")
        for (did in decks.longIterable()) {
            col.decks.rem(col, did, false, false)
        }
        col.server = wasServer
    }

    /**
     * Models ********************************************************************
     */
    private val models: JSONArray
        get() {
            val result = JSONArray()
            if (col.server) {
                for (m in col.models.all(col)) {
                    if (m.getInt("usn") >= mMinUsn) {
                        result.put(m)
                    }
                }
            } else {
                for (m in col.models.all(col)) {
                    if (m.getInt("usn") == -1) {
                        m.put("usn", mMaxUsn)
                        result.put(m)
                    }
                }
                col.models.save(col)
            }
            return result
        }

    @Throws(UnexpectedSchemaChange::class)
    private fun mergeModels(rchg: JSONArray) {
        for (model in rchg.jsonObjectIterable()) {
            val r = Model(model)
            val l = col.models.get(col, r.getLong("id"))
            // if missing locally or server is newer, update
            if (l == null || r.getLong("mod") > l.getLong("mod")) {
                // This is a hack to detect when the note type has been altered
                // in an import without a full sync being forced. A future
                // syncing algorithm should handle this in a better way.
                if (l != null) {
                    if (l.getJSONArray("flds").length() != r.getJSONArray("flds").length()) {
                        throw UnexpectedSchemaChange()
                    }
                    if (l.getJSONArray("tmpls").length() != r.getJSONArray("tmpls").length()) {
                        throw UnexpectedSchemaChange()
                    }
                }
                col.models.update(col, r)
            }
        }
    }

    /**
     * Decks ********************************************************************
     */
    private val decks: JSONArray
        get() {
            val result = JSONArray()
            if (col.server) {
                val decks = JSONArray()
                for (g in col.decks.all(col)) {
                    if (g.getInt("usn") >= mMinUsn) {
                        decks.put(g)
                    }
                }
                val dconfs = JSONArray()
                for (g in col.decks.allConf(col)) {
                    if (g.getInt("usn") >= mMinUsn) {
                        dconfs.put(g)
                    }
                }
                result.put(decks)
                result.put(dconfs)
            } else {
                val decks = JSONArray()
                for (g in col.decks.all(col)) {
                    if (g.getInt("usn") == -1) {
                        g.put("usn", mMaxUsn)
                        decks.put(g)
                    }
                }
                val dconfs = JSONArray()
                for (g in col.decks.allConf(col)) {
                    if (g.getInt("usn") == -1) {
                        g.put("usn", mMaxUsn)
                        dconfs.put(g)
                    }
                }
                col.decks.save(col)
                result.put(decks)
                result.put(dconfs)
            }
            return result
        }

    private fun mergeDecks(rchg: JSONArray) {
        val decks = rchg.getJSONArray(0)
        for (deck in decks.jsonObjectIterable()) {
            val r = Deck(deck)
            val l = col.decks.get(col, r.getLong("id"), false)
            // if missing locally or server is newer, update
            if (l == null || r.getLong("mod") > l.getLong("mod")) {
                col.decks.update(col, r)
            }
        }
        val confs = rchg.getJSONArray(1)
        for (deckConfig in confs.jsonObjectIterable()) {
            val r = DeckConfig(deckConfig, DeckConfig.Source.DECK_CONFIG)
            val l = col.decks.getConf(col, r.getLong("id"))
            // if missing locally or server is newer, update
            if (l == null || r.getLong("mod") > l.getLong("mod")) {
                col.decks.updateConf(col, r)
            }
        }
    }

    /**
     * Tags ********************************************************************
     */
    private val tags: JSONArray
        get() {
            val result = JSONArray()
            if (col.server) {
                for ((tag, usn) in col.tags.allItems()) {
                    if (usn >= mMinUsn) {
                        result.put(tag)
                    }
                }
            } else {
                for ((tag, usn) in col.tags.allItems()) {
                    if (usn == -1) {
                        col.tags.add(tag, mMaxUsn)
                        result.put(tag)
                    }
                }
                col.tags.save()
            }
            return result
        }

    private fun mergeTags(tags: JSONArray) {
        col.tags.register(tags.toStringList(), mMaxUsn)
    }

    /**
     * Cards/notes/revlog ********************************************************************
     */
    private fun mergeRevlog(logs: JSONArray) {
        for (log in logs.jsonArrayIterable()) {
            try {
                col.db.execute(
                    "INSERT OR IGNORE INTO revlog VALUES (?,?,?,?,?,?,?,?,?)",
                    *Utils.jsonArray2Objects(log)
                )
            } catch (e: SQLException) {
                throw RuntimeException(e)
            }
        }
    }

    private fun newerRows(data: JSONArray, table: String, modIdx: Int): ArrayList<Array<Any>> {
        val ids = LongArray(data.length())
        for (i in 0 until data.length()) {
            ids[i] = data.getJSONArray(i).getLong(0)
        }
        val limAndArg = usnLim()
        val lmods: MutableMap<Long, Long> = HashMapInit(
            col.db.queryScalar(
                "SELECT count() FROM " + table + " WHERE id IN " + Utils.ids2str(ids) + " AND " + limAndArg.first,
                *limAndArg.second
            )
        )
        col.db.query(
            "SELECT id, mod FROM " + table + " WHERE id IN " + Utils.ids2str(ids) + " AND " + limAndArg.first,
            *limAndArg.second
        ).use { cur ->
            while (cur.moveToNext()) {
                lmods[cur.getLong(0)] = cur.getLong(1)
            }
        }
        val update = ArrayList<Array<Any>>(data.length())
        for (r in data.jsonArrayIterable()) {
            if (!lmods.containsKey(r.getLong(0)) || lmods[r.getLong(0)]!! < r.getLong(modIdx)) {
                update.add(Utils.jsonArray2Objects(r))
            }
        }
        col.log(table, data)
        return update
    }

    private fun mergeCards(cards: JSONArray) {
        for (r in newerRows(cards, "cards", 4)) {
            col.db.execute("INSERT OR REPLACE INTO cards VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)", *r)
        }
    }

    private fun mergeNotes(notes: JSONArray) {
        for (n in newerRows(notes, "notes", 4)) {
            col.db.execute("INSERT OR REPLACE INTO notes VALUES (?,?,?,?,?,?,?,?,?,?,?)", *n)
            col.updateFieldCache(longArrayOf((n[0] as Number).toLong()))
        }
    }

    /**
     * Col config ********************************************************************
     */
    private val conf: JSONObject
        get() = col.conf

    private fun mergeConf(conf: JSONObject) {
        col.conf = conf
    }

    /**
     * If the user asked to cancel the sync then we just throw a Runtime exception which should be gracefully handled
     * @param con
     */
    private fun throwExceptionIfCancelled(con: Connection) {
        if (isCancelled) {
            Timber.i("Sync was cancelled")
            publishProgress(con, R.string.sync_cancelled)
            try {
                remoteServer.abort()
            } catch (ignored: UnknownHttpResponseException) {
                Timber.w(ignored)
            }
            throw RuntimeException(ConnectionResultType.USER_ABORTED_SYNC.toString())
        }
    }

    private class UnexpectedSchemaChange : Exception()
    companion object {
        // Mapping of column type names to Cursor types for API < 11
        const val TYPE_NULL = 0
        const val TYPE_INTEGER = 1
        const val TYPE_FLOAT = 2
        const val TYPE_STRING = 3
        const val TYPE_BLOB = 4
    }
}
