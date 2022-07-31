/****************************************************************************************
 * Copyright (c) 2022 Divyansh Kushwaha <kushwaha.divyansh.dxn@gmail.com>               *
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

package com.ichi2.async.coroutines

import android.content.Context
import com.ichi2.anki.CrashReportService
import com.ichi2.libanki.Collection
import com.ichi2.libanki.Models
import com.ichi2.libanki.Note
import timber.log.Timber

object CollectionJob {

    abstract class AddNote<CTX : Context>(
        private val col: Collection,
        private val note: Note,
        context: CTX?
    ) : CoroutineJobWithContext<CTX, Void, Int, Boolean>(AddNote::class.java.simpleName, context) {
        override suspend fun doInBackground(vararg params: Void): Boolean {
            Timber.d("doInBackgroundAddNote")
            try {
                val db = col.db
                var noOfSavedNotes: Int? = null
                db.executeInTransaction {
                    noOfSavedNotes = col.addNote(note, Models.AllowEmpty.ONLY_CLOZE)
                }
                // if note is saved without any exception, then noOfSavedNotes must be non-null
                onProgressUpdate(noOfSavedNotes!!)
            } catch (e: RuntimeException) {
                Timber.e(e, "doInBackgroundAddNote - RuntimeException on adding note")
                CrashReportService.sendExceptionReport(e, "doInBackgroundAddNote")
                return false
            }
            return true
        }
    }
}
