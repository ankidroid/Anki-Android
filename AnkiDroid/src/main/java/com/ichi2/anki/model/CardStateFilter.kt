/*
 *  Copyright (c) 2023 David Allison <davidallisongithub@gmail.com>
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

package com.ichi2.anki.model

import anki.scheduler.CustomStudyRequest.Cram.CramKind
import com.ichi2.anki.CollectionManager.TR

/**
 * Allows filtering a search by the state of cards
 *
 * @see [anki.search.SearchNode.CardState]
 *
 * https://github.com/ankitects/anki/blob/f6a3e98ac3dcb19d54e7fdbba96bf2fa15fc2b3f/rslib/src/scheduler/filtered/custom_study.rs#L246-L277
 */
enum class CardStateFilter(val getDescription: () -> String, val order: Int, val cramKind: CramKind) {
    /** New cards, in order added, reschedule */
    NEW({ TR.customStudyNewCardsOnly() }, 0, CramKind.CRAM_KIND_NEW),

    /** Due cards (is:due), order by due, reschedule */
    DUE({ TR.customStudyDueCardsOnly() }, 1, CramKind.CRAM_KIND_DUE),

    /** Anything but new cards, random order, reschedule */
    REVIEW({ TR.customStudyAllReviewCardsInRandomOrder() }, 2, CramKind.CRAM_KIND_REVIEW),

    /** All cards, random order, no rescheduling */
    ALL_CARDS({ TR.customStudyAllCardsInRandomOrderDont() }, 3, CramKind.CRAM_KIND_ALL);

    companion object {
        fun fromOrder(order: Int) = CardStateFilter.entries.firstOrNull { it.order == order }
    }
}
