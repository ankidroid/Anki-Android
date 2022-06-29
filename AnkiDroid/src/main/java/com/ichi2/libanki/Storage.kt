/***************************************************************************************
 * Copyright (c) 2011 Norbert Nagold <norbert.nagold@gmail.com>                         *
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
import android.content.Context
import android.database.sqlite.SQLiteDatabaseLockedException
import com.ichi2.anki.UIUtils
import com.ichi2.anki.exception.ConfirmModSchemaException
import com.ichi2.libanki.Consts.DECK_STD
import com.ichi2.libanki.backend.DroidBackend
import com.ichi2.libanki.backend.DroidBackendFactory
import com.ichi2.libanki.exception.UnknownDatabaseVersionException
import com.ichi2.libanki.utils.Time
import com.ichi2.libanki.utils.TimeManager
import com.ichi2.utils.JSONArray
import com.ichi2.utils.JSONException
import com.ichi2.utils.JSONObject
import com.ichi2.utils.KotlinCleanup
import timber.log.Timber
import java.io.File
import java.io.FileNotFoundException
import java.util.*

@KotlinCleanup("IDE warnings")
object Storage {
    private var sUseBackend = true
    var isInMemory = false
        private set
    /**
     * Whether the collection can be opened. If true, [collection]
     * throws a [SQLiteDatabaseLockedException]
     */
    /**
     * The collection is locked from being opened via the [Storage] class. All collection accesses in the app
     * should use this class.
     *
     * Opening a collection will then throw [SQLiteDatabaseLockedException]
     *
     * A collection which was opened before sIsLocked was set will be usable until it is closed.
     */
    var isLocked = false
        private set

    /** Helper method for when the collection can't be opened  */
    @JvmStatic
    @Throws(UnknownDatabaseVersionException::class)
    @KotlinCleanup("make path non-null")
    fun getDatabaseVersion(path: String?): Int {
        return try {
            if (!File(path!!).exists()) {
                throw UnknownDatabaseVersionException(FileNotFoundException(path))
            }
            val db = DB(path)
            val result = db.queryScalar("SELECT ver FROM col")
            db.close()
            result
        } catch (e: Exception) {
            Timber.w(e, "Can't open database")
            throw UnknownDatabaseVersionException(e)
        }
    }

    /* Open a new or existing collection. Path must be unicode */
    @JvmOverloads
    @JvmStatic
    @KotlinCleanup("context non-null")
    fun collection(
        context: Context?,
        path: String,
        server: Boolean = false,
        log: Boolean = false,
        time: Time = TimeManager.time
    ): Collection {
        assert(path.endsWith(".anki2") || path.endsWith(".anki21"))
        if (isLocked) {
            throw SQLiteDatabaseLockedException("AnkiDroid has locked the database")
        }
        val dbFile = File(path)
        val create = !dbFile.exists()
        val backend = DroidBackendFactory.getInstance(useBackend())
        val db = backend.openCollectionDatabase(if (isInMemory) ":memory:" else path)
        return try {
            // initialize
            val ver: Int
            ver = if (create) {
                _createDB(db, time, backend)
            } else {
                _upgradeSchema(db, time)
            }
            // add db to col and do any remaining upgrades
            val col = backend.createCollection(context!!, db, path, server, log)
            if (ver < Consts.SCHEMA_VERSION) {
                _upgrade(col, ver)
            } else if (ver > Consts.SCHEMA_VERSION) {
                throw RuntimeException("This file requires a newer version of Anki.")
            } else if (create) {
                addNoteTypes(col, backend)
                col.onCreate()
                col.save()
            }
            col
        } catch (e: Exception) {
            Timber.e(e, "Error opening collection; closing database")
            db.close()
            throw e
        }
    }

    /** Add note types when creating database  */
    @KotlinCleanup("col non-null")
    private fun addNoteTypes(col: Collection?, backend: DroidBackend) {
        if (backend.databaseCreationInitializesData()) {
            Timber.i("skipping adding note types - already exist")
            return
        }
        // add in reverse order so basic is default
        for (i in StdModels.STD_MODELS.indices.reversed()) {
            StdModels.STD_MODELS[i].add(col)
        }
    }

    /**
     * Whether the collection should try to be opened with a Rust-based DB Backend
     * Falls back to Java if init fails.
     */
    internal fun useBackend(): Boolean {
        return sUseBackend
    }

    private fun _upgradeSchema(db: DB, time: Time): Int {
        val ver = db.queryScalar("SELECT ver FROM col")
        if (ver == Consts.SCHEMA_VERSION) {
            return ver
        }
        // add odid to cards, edue->odue
        if (db.queryScalar("SELECT ver FROM col") == 1) {
            db.execute("ALTER TABLE cards RENAME TO cards2")
            _addSchema(db, false, time)
            db.execute("insert into cards select id, nid, did, ord, mod, usn, type, queue, due, ivl, factor, reps, lapses, left, edue, 0, flags, data from cards2")
            db.execute("DROP TABLE cards2")
            db.execute("UPDATE col SET ver = 2")
            _updateIndices(db)
        }
        // remove did from notes
        if (db.queryScalar("SELECT ver FROM col") == 2) {
            db.execute("ALTER TABLE notes RENAME TO notes2")
            _addSchema(db, true, time)
            db.execute("insert into notes select id, guid, mid, mod, usn, tags, flds, sfld, csum, flags, data from notes2")
            db.execute("DROP TABLE notes2")
            db.execute("UPDATE col SET ver = 3")
            _updateIndices(db)
        }
        return ver
    }

    @KotlinCleanup("col non-null")
    private fun _upgrade(col: Collection?, ver: Int) {
        try {
            if (ver < 3) {
                // new deck properties
                for (d in col!!.decks.all()) {
                    d.put("dyn", DECK_STD)
                    d.put("collapsed", false)
                    col.decks.save(d)
                }
            }
            if (ver < 4) {
                col!!.modSchemaNoCheck()
                val models = col.models.all()
                val clozes = ArrayList<Model>(models.size)
                for (m in models) {
                    if (!m.getJSONArray("tmpls").getJSONObject(0).getString("qfmt")
                        .contains("{{cloze:")
                    ) {
                        m.put("type", Consts.MODEL_STD)
                    } else {
                        clozes.add(m)
                    }
                }
                for (m in clozes) {
                    try {
                        _upgradeClozeModel(col, m)
                    } catch (e: ConfirmModSchemaException) {
                        // Will never be reached as we already set modSchemaNoCheck()
                        throw RuntimeException(e)
                    }
                }
                col.db.execute("UPDATE col SET ver = 4")
            }
            if (ver < 5) {
                col!!.db.execute("UPDATE cards SET odue = 0 WHERE queue = 2")
                col.db.execute("UPDATE col SET ver = 5")
            }
            if (ver < 6) {
                col!!.modSchemaNoCheck()
                for (m in col.models.all()) {
                    m.put("css", JSONObject(Models.DEFAULT_MODEL).getString("css"))
                    val ar = m.getJSONArray("tmpls")
                    for (t in ar.jsonObjectIterable()) {
                        if (!t.has("css")) {
                            continue
                        }
                        m.put(
                            "css",
                            """
                                ${m.getString("css")}
                                ${
                            t.getString("css").replace(".card ", ".card" + t.getInt("ord") + 1)
                            }
                            """.trimIndent()
                        )
                        t.remove("css")
                    }
                    col.models.save(m)
                }
                col.db.execute("UPDATE col SET ver = 6")
            }
            if (ver < 7) {
                col!!.modSchemaNoCheck()
                col.db.execute("UPDATE cards SET odue = 0 WHERE (type = " + Consts.CARD_TYPE_LRN + " OR queue = 2) AND NOT odid")
                col.db.execute("UPDATE col SET ver = 7")
            }
            if (ver < 8) {
                col!!.modSchemaNoCheck()
                col.db.execute("UPDATE cards SET due = due / 1000 WHERE due > 4294967296")
                col.db.execute("UPDATE col SET ver = 8")
            }
            if (ver < 9) {
                col!!.db.execute("UPDATE col SET ver = 9")
            }
            if (ver < 10) {
                col!!.db.execute("UPDATE cards SET left = left + left * 1000 WHERE queue = " + Consts.QUEUE_TYPE_LRN)
                col.db.execute("UPDATE col SET ver = 10")
            }
            if (ver < 11) {
                col!!.modSchemaNoCheck()
                for (d in col.decks.all()) {
                    if (d.isDyn) {
                        var order = d.getInt("order")
                        // failed order was removed
                        if (order >= 5) {
                            order -= 1
                        }
                        val terms = JSONArray(
                            Arrays.asList(
                                d.getString("search"),
                                d.getInt("limit"), order
                            )
                        )
                        d.put("terms", JSONArray())
                        d.getJSONArray("terms").put(0, terms)
                        d.remove("search")
                        d.remove("limit")
                        d.remove("order")
                        d.put("resched", true)
                        d.put("return", true)
                    } else {
                        if (!d.has("extendNew")) {
                            d.put("extendNew", 10)
                            d.put("extendRev", 50)
                        }
                    }
                    col.decks.save(d)
                }
                for (c in col.decks.allConf()) {
                    val r = c.getJSONObject("rev")
                    r.put("ivlFct", r.optDouble("ivlFct", 1.0))
                    if (r.has("ivlfct")) {
                        r.remove("ivlfct")
                    }
                    r.put("maxIvl", 36500)
                    col.decks.save(c)
                }
                for (m in col.models.all()) {
                    val tmpls = m.getJSONArray("tmpls")
                    for (t in tmpls.jsonObjectIterable()) {
                        t.put("bqfmt", "")
                        t.put("bafmt", "")
                    }
                    col.models.save(m)
                }
                col.db.execute("update col set ver = 11")
            }
        } catch (e: JSONException) {
            throw RuntimeException(e)
        }
    }

    @Throws(ConfirmModSchemaException::class)
    @KotlinCleanup("col non-null")
    private fun _upgradeClozeModel(col: Collection?, m: Model) {
        m.put("type", Consts.MODEL_CLOZE)
        // convert first template
        val t = m.getJSONArray("tmpls").getJSONObject(0)
        for (type in arrayOf("qfmt", "afmt")) {
            t.put(
                type,
                t.getString(type).replace("\\{\\{cloze:1:(.+?)\\}\\}".toRegex(), "{{cloze:$1}}")
            )
        }
        t.put("name", "Cloze")
        // delete non-cloze cards for the model
        val tmpls = m.getJSONArray("tmpls")
        val rem = ArrayList<JSONObject>()
        for (ta in tmpls.jsonObjectIterable()) {
            if (!ta.getString("afmt").contains("{{cloze:")) {
                rem.add(ta)
            }
        }
        for (r in rem) {
            col!!.models.remTemplate(m, r)
        }
        val newTmpls = JSONArray()
        newTmpls.put(tmpls.getJSONObject(0))
        m.put("tmpls", newTmpls)
        Models._updateTemplOrds(m)
        col!!.models.save(m)
    }

    private fun _createDB(db: DB, time: Time, backend: DroidBackend): Int {
        if (backend.databaseCreationCreatesSchema()) {
            if (!backend.databaseCreationInitializesData()) {
                _setColVars(db, time)
            }
            // This line is required for testing - otherwise Rust will override a mocked time.
            db.execute("update col set crt = ?", UIUtils.getDayStart(time) / 1000)
        } else {
            db.execute("PRAGMA page_size = 4096")
            db.execute("PRAGMA legacy_file_format = 0")
            db.execute("VACUUM")
            _addSchema(db, true, time)
            _updateIndices(db)
        }
        db.execute("ANALYZE")
        return Consts.SCHEMA_VERSION
    }

    private fun _addSchema(db: DB, setColConf: Boolean, time: Time) {
        db.execute(
            "create table if not exists col ( " + "id              integer primary key, " +
                "crt             integer not null," + "mod             integer not null," +
                "scm             integer not null," + "ver             integer not null," +
                "dty             integer not null," + "usn             integer not null," +
                "ls              integer not null," + "conf            text not null," +
                "models          text not null," + "decks           text not null," +
                "dconf           text not null," + "tags            text not null" + ");"
        )
        db.execute(
            "create table if not exists notes (" + "   id              integer primary key,   /* 0 */" +
                "  guid            text not null,   /* 1 */" + " mid             integer not null,   /* 2 */" +
                " mod             integer not null,   /* 3 */" + " usn             integer not null,   /* 4 */" +
                " tags            text not null,   /* 5 */" + " flds            text not null,   /* 6 */" +
                " sfld            integer not null,   /* 7 */" + " csum            integer not null,   /* 8 */" +
                " flags           integer not null,   /* 9 */" + " data            text not null   /* 10 */" + ");"
        )
        db.execute(
            "create table if not exists cards (" + "   id              integer primary key,   /* 0 */" +
                "  nid             integer not null,   /* 1 */" + "  did             integer not null,   /* 2 */" +
                "  ord             integer not null,   /* 3 */" + "  mod             integer not null,   /* 4 */" +
                " usn             integer not null,   /* 5 */" + " type            integer not null,   /* 6 */" +
                " queue           integer not null,   /* 7 */" + "    due             integer not null,   /* 8 */" +
                "   ivl             integer not null,   /* 9 */" + "  factor          integer not null,   /* 10 */" +
                " reps            integer not null,   /* 11 */" + "   lapses          integer not null,   /* 12 */" +
                "   left            integer not null,   /* 13 */" + "   odue            integer not null,   /* 14 */" +
                "   odid            integer not null,   /* 15 */" + "   flags           integer not null,   /* 16 */" +
                "   data            text not null   /* 17 */" + ");"
        )
        db.execute(
            "create table if not exists revlog (" + "   id              integer primary key," +
                "   cid             integer not null," + "   usn             integer not null," +
                "   ease            integer not null," + "   ivl             integer not null," +
                "   lastIvl         integer not null," + "   factor          integer not null," +
                "   time            integer not null," + "   type            integer not null" + ");"
        )
        db.execute(
            "create table if not exists graves (" + "    usn             integer not null," +
                "    oid             integer not null," + "    type            integer not null" + ")"
        )
        db.execute(
            "INSERT OR IGNORE INTO col VALUES(1,0,0," +
                time.intTimeMS() + "," + Consts.SCHEMA_VERSION +
                ",0,0,0,'','{}','','','{}')"
        )
        if (setColConf) {
            _setColVars(db, time)
        }
    }

    private fun _setColVars(db: DB, time: Time) {
        val g = JSONObject(Decks.DEFAULT_DECK)
        g.put("id", 1)
        g.put("name", "Default")
        g.put("conf", 1)
        g.put("mod", time.intTime())
        val gc = JSONObject(Decks.DEFAULT_CONF)
        gc.put("id", 1)
        val ag = JSONObject()
        ag.put("1", g)
        val agc = JSONObject()
        agc.put("1", gc)
        val values = ContentValues()
        values.put("conf", Collection.DEFAULT_CONF)
        values.put("decks", Utils.jsonToString(ag))
        values.put("dconf", Utils.jsonToString(agc))
        db.update("col", values)
    }

    private fun _updateIndices(db: DB) {
        db.execute("create index if not exists ix_notes_usn on notes (usn);")
        db.execute("create index if not exists ix_cards_usn on cards (usn);")
        db.execute("create index if not exists ix_revlog_usn on revlog (usn);")
        db.execute("create index if not exists ix_cards_nid on cards (nid);")
        db.execute("create index if not exists ix_cards_sched on cards (did, queue, due);")
        db.execute("create index if not exists ix_revlog_cid on revlog (cid);")
        db.execute("create index if not exists ix_notes_csum on notes (csum);)")
    }

    fun addIndices(db: DB) {
        _updateIndices(db)
    }

    @KotlinCleanup("use kotlin property syntax instead of setters")
    fun setUseBackend(useBackend: Boolean) {
        sUseBackend = useBackend
    }

    @JvmStatic
    @KotlinCleanup("use kotlin property syntax instead of setters")
    fun setUseInMemory(useInMemoryDatabase: Boolean) {
        isInMemory = useInMemoryDatabase
    }

    /** Allows the collection to be opened  */
    fun unlockCollection() {
        isLocked = false
        Timber.i("unlocked collection")
    }

    /**
     * Stops the collection from being opened via throwing [SQLiteDatabaseLockedException].
     * does not affect a currently open collection
     *
     * To ensure that the collection is locked and unopenable:
     *
     * * Lock the collection
     * * Get an instance of the collection, if it succeeds, close it
     * * Ensure the collection is locked by trying to open it, it should fail.
     * * Perform your operation
     * * Unlock the collection
     */
    fun lockCollection() {
        isLocked = true
        Timber.i("locked collection. Opening will throw SQLiteDatabaseLockedException")
    }
}
