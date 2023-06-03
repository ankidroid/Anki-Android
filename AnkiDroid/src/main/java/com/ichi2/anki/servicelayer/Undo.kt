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

import androidx.annotation.VisibleForTesting
import com.ichi2.anki.CrashReportService
import com.ichi2.anki.servicelayer.SchedulerService.NextCard
import com.ichi2.libanki.Card
import com.ichi2.libanki.Collection
import com.ichi2.utils.Computation
import timber.log.Timber

class Undo : ActionAndNextCard() {
    override fun execute(): ComputeResult {
        return try {
            val card = col.db.executeInTransaction { nonTaskUndo(col) }
            Computation.ok(NextCard.withNoResult(card))
        } catch (e: RuntimeException) {
            Timber.e(e, "doInBackgroundUndo - RuntimeException on undoing")
            CrashReportService.sendExceptionReport(e, "doInBackgroundUndo")
            Computation.err()
        }
    }

    companion object {
        @VisibleForTesting
        fun nonTaskUndo(col: Collection): Card? {
            val sched = col.sched
            val card = col.undo()
            if (card == null) {
                /* multi-card action undone, no action to take here */
                Timber.d("Multi-select undo succeeded")
            } else {
                // cid is actually a card id.
                // a review was undone,
                /* card review undone, set up to review that card again */
                Timber.d("Single card review undo succeeded")
                card.startTimer()
                col.reset()
                sched.deferReset(col, card)
            }
            return card
        }
    }
}
