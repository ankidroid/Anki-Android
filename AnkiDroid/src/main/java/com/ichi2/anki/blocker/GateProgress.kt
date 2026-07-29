// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2026 Tim Farrelly <timf34@gmail.com>

package com.ichi2.anki.blocker

import anki.scheduler.CardAnswer.Rating
import com.ichi2.anki.libanki.CardId

/**
 * Tracks progress through a blocker gate session: [requiredCorrect] unique cards
 * must be rated [Rating.GOOD] or [Rating.EASY] for the gate to open.
 *
 * [Rating.AGAIN] and [Rating.HARD] intentionally do not count. A card answered
 * correctly more than once (possible when it lapses and returns via learning steps)
 * counts a single time.
 */
class GateProgress(
    private val requiredCorrect: Int,
) {
    init {
        require(requiredCorrect >= 1) { "a gate must require at least one card" }
    }

    private val correctCardIds = mutableSetOf<CardId>()

    enum class State {
        IN_PROGRESS,
        COMPLETE,
    }

    val correctCount: Int get() = correctCardIds.size

    val isComplete: Boolean get() = correctCardIds.size >= requiredCorrect

    fun onAnswer(
        cardId: CardId,
        rating: Rating,
    ): State {
        if (rating == Rating.GOOD || rating == Rating.EASY) {
            correctCardIds += cardId
        }
        return if (isComplete) State.COMPLETE else State.IN_PROGRESS
    }
}
