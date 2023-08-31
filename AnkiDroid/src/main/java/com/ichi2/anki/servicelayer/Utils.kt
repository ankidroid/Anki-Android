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

import com.ichi2.anki.CrashReportService
import com.ichi2.libanki.Card
import com.ichi2.utils.Computation
import timber.log.Timber

/**
 * @return whether the task succeeded, and the array of cards affected.
 */
// This was converted from CollectionTask, we want a better name, but keep it until DismissNotes is removed
fun <TTaskResult : Any, TProgress, TResult> AnkiTask<TProgress, TResult>.dismissNotes(cardIds: List<Long>, task: (Array<Card>) -> Computation<TTaskResult>): Computation<Pair<TTaskResult, Array<Card>>> {
    // query cards
    val cards = cardIds.map { cid -> col.getCard(cid) }.toTypedArray()
    try {
        col.db.database.beginTransaction()
        try {
            val result = task(cards)
            if (!result.succeeded()) {
                return Computation.err()
            }
            col.db.database.setTransactionSuccessful()
            // pass cards back so more actions can be performed by the caller
            // (querying the cards again is unnecessarily expensive)
            return Computation.ok(Pair(result.value, cards))
        } finally {
            col.db.safeEndInTransaction()
        }
    } catch (e: RuntimeException) {
        Timber.e(e, "doInBackgroundSuspendCard - RuntimeException on suspending card")
        CrashReportService.sendExceptionReport(e, "doInBackgroundSuspendCard")
        return Computation.err()
    }
}
