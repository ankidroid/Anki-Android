// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2025 Brayan Oliveira <69634269+brayandso@users.noreply.github.com>

package com.ichi2.anki.utils.ext

import anki.scheduler.CardAnswer
import anki.scheduler.CardAnswer.Rating
import anki.scheduler.SchedulingStates
import com.ichi2.anki.libanki.Card
import com.ichi2.anki.libanki.sched.Scheduler

fun Scheduler.answerCard(
    card: Card,
    states: SchedulingStates,
    rating: Rating,
): CardAnswer =
    buildAnswer(card, states, rating).also {
        numberOfAnswersRecorded += 1
    }
