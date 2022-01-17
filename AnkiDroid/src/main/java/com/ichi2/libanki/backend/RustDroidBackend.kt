/*
 *  Copyright (c) 2020 David Allison <davidallisongithub@gmail.com>
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
 */

package com.ichi2.libanki.backend

import BackendProto.Backend.ExtractAVTagsOut
import BackendProto.Backend.RenderCardOut
import android.content.Context
import com.ichi2.libanki.Collection
import com.ichi2.libanki.DB
import com.ichi2.libanki.TemplateManager.TemplateRenderContext
import com.ichi2.libanki.backend.exception.BackendNotSupportedException
import com.ichi2.libanki.backend.model.SchedTimingToday
import com.ichi2.libanki.backend.model.SchedTimingTodayProto
import com.ichi2.libanki.utils.Time
import net.ankiweb.rsdroid.BackendFactory
import net.ankiweb.rsdroid.database.RustV11SQLiteOpenHelperFactory

/** The V11 Backend in Rust  */
open class RustDroidBackend(
    // I think we can change this to BackendV1 once new DB() accepts it.
    private val backend: BackendFactory
) : DroidBackend {
    override fun createCollection(context: Context, db: DB, path: String, server: Boolean, log: Boolean, time: Time): Collection {
        return Collection(context, db, path, server, log, time, this)
    }

    override fun openCollectionDatabase(path: String): DB {
        return DB(path) { RustV11SQLiteOpenHelperFactory(backend) }
    }

    override fun closeCollection(db: DB?, downgradeToSchema11: Boolean) {
        db?.close()
    }

    override fun databaseCreationCreatesSchema(): Boolean {
        return true
    }

    /** Whether the 'Decks' , 'Deck Config', 'Note Types' etc.. are set by database creation  */
    override fun databaseCreationInitializesData(): Boolean {
        return false // only true in V16, not V11
    }

    override fun isUsingRustBackend(): Boolean {
        return true
    }

    override fun debugEnsureNoOpenPointers() {
        val result = backend.backend.debugActiveDatabaseSequenceNumbers(UNUSED_VALUE.toLong())
        if (result.sequenceNumbersCount > 0) {
            val numbers = result.sequenceNumbersList.toString()
            throw IllegalStateException("Contained unclosed sequence numbers: $numbers")
        }
    }

    override fun sched_timing_today(createdSecs: Long, createdMinsWest: Int, nowSecs: Long, nowMinsWest: Int, rolloverHour: Int): SchedTimingToday {
        val res = backend.backend.schedTimingTodayLegacy(createdSecs, createdMinsWest, nowSecs, nowMinsWest, rolloverHour)
        return SchedTimingTodayProto(res)
    }

    override fun local_minutes_west(timestampSeconds: Long): Int {
        return backend.backend.localMinutesWest(timestampSeconds).getVal()
    }

    override fun useNewTimezoneCode(col: Collection) {
        // enable the new timezone code on a new collection
        try {
            col.sched.set_creation_offset()
        } catch (e: BackendNotSupportedException) {
            throw e.alreadyUsingRustBackend()
        }
    }

    @Throws(BackendNotSupportedException::class)
    override fun extract_av_tags(text: String, question_side: Boolean): ExtractAVTagsOut {
        throw BackendNotSupportedException()
    }

    @Throws(BackendNotSupportedException::class)
    override fun renderCardForTemplateManager(templateRenderContext: TemplateRenderContext): RenderCardOut {
        throw BackendNotSupportedException()
    }

    companion object {
        const val UNUSED_VALUE = 0
    }
}
