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
import com.ichi2.libanki.utils.Time
import net.ankiweb.rsdroid.RustCleanup
import kotlin.Throws

/**
 * A class which implements the Rust backend functionality in Java - this is to allow moving our current Java code to
 * the rust-based interface so we are able to perform regression testing against the converted interface
 *
 * This also allows an easy switch of functionality once we are happy that there are no regressions
 */
@RustCleanup("After the rust conversion is complete - this will be removed")
class JavaDroidBackend : DroidBackend {
    override fun createCollection(context: Context, db: DB, path: String, server: Boolean, log: Boolean, time: Time): Collection {
        return Collection(context, db, path, server, log, time, this)
    }

    override fun openCollectionDatabase(path: String): DB {
        return DB(path)
    }

    override fun closeCollection(db: DB, downgradeToSchema11: Boolean) {
        db.close()
    }

    override fun databaseCreationCreatesSchema(): Boolean {
        return false
    }

    override fun databaseCreationInitializesData(): Boolean {
        return false
    }

    override fun isUsingRustBackend(): Boolean {
        return false
    }

    override fun debugEnsureNoOpenPointers() {
        // no-op
    }

    @Throws(BackendNotSupportedException::class)
    override fun sched_timing_today(createdSecs: Long, createdMinsWest: Int, nowSecs: Long, nowMinsWest: Int, rolloverHour: Int): SchedTimingToday {
        throw BackendNotSupportedException()
    }

    @Throws(BackendNotSupportedException::class)
    override fun local_minutes_west(timestampSeconds: Long): Int {
        throw BackendNotSupportedException()
    }

    override fun useNewTimezoneCode(col: Collection) {
        // intentionally blank - unavailable on Java backend
    }

    @Throws(BackendNotSupportedException::class)
    override fun extract_av_tags(text: String, question_side: Boolean): ExtractAVTagsOut {
        throw BackendNotSupportedException()
    }

    @Throws(BackendNotSupportedException::class)
    override fun renderCardForTemplateManager(templateRenderContext: TemplateRenderContext): RenderCardOut {
        throw BackendNotSupportedException()
    }
}
