/*
 * Copyright (c) 2025 Brayan Oliveira <69634269+brayandso@users.noreply.github.com>
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program. If not, see <http://www.gnu.org/licenses/>.
 */
package com.ichi2.anki.utils.ext

import anki.scheduler.CardAnswer
import anki.scheduler.CardAnswer.Rating
import anki.scheduler.SchedulingStates
import com.ichi2.anki.libanki.Card
import com.ichi2.anki.libanki.sched.Counts
import com.ichi2.anki.libanki.sched.Scheduler

fun Scheduler.answerCard(
    card: Card,
    states: SchedulingStates,
    rating: Rating,
): CardAnswer =
    buildAnswer(card, states, rating).also {
        numberOfAnswersRecorded += 1
    }

/**
 * @return Number of new, rev and lrn card to review in all decks.
 */
fun Scheduler.allDecksCounts(): Counts {
    val total = Counts()
    // Only count the top-level decks in the total
    val nodes = deckDueTree().children
    for (node in nodes) {
        total.addNew(node.newCount)
        total.addLrn(node.lrnCount)
        total.addRev(node.revCount)
    }
    return total
}
