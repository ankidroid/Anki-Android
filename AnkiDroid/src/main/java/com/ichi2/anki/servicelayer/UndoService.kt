/*
 *  Copyright (c) 2021 David Allison <davidallisongithub@gmail.com>
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

package com.ichi2.anki.servicelayer

import com.ichi2.anki.AnkiDroidApp
import com.ichi2.anki.servicelayer.SchedulerService.NextCard
import com.ichi2.async.CollectionTask
import com.ichi2.utils.Computation
import timber.log.Timber

class UndoService {
    class Undo : ActionAndNextCard() {
        override fun execute(): ComputeResult {
            try {
                val card = col.db.executeInTransactionReturn {
                    return@executeInTransactionReturn CollectionTask.nonTaskUndo(col)
                }
                return Computation.ok(NextCard.withNoResult(card))
            } catch (e: RuntimeException) {
                Timber.e(e, "doInBackgroundUndo - RuntimeException on undoing")
                AnkiDroidApp.sendExceptionReport(e, "doInBackgroundUndo")
                return Computation.err()
            }
        }
    }
}
