// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2026 Tim Farrelly <timf34@gmail.com>

package com.ichi2.anki.blocker

import anki.scheduler.CardAnswer.Rating
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.Test

class GateProgressTest {
    @Test
    fun `good and easy count toward completion`() {
        val progress = GateProgress(requiredCorrect = 2)
        assertThat(progress.onAnswer(cardId = 1, rating = Rating.GOOD), equalTo(GateProgress.State.IN_PROGRESS))
        assertThat(progress.correctCount, equalTo(1))
        assertThat(progress.onAnswer(cardId = 2, rating = Rating.EASY), equalTo(GateProgress.State.COMPLETE))
        assertThat(progress.correctCount, equalTo(2))
        assertThat(progress.isComplete, equalTo(true))
    }

    @Test
    fun `again and hard do not count`() {
        val progress = GateProgress(requiredCorrect = 1)
        assertThat(progress.onAnswer(cardId = 1, rating = Rating.AGAIN), equalTo(GateProgress.State.IN_PROGRESS))
        assertThat(progress.onAnswer(cardId = 1, rating = Rating.HARD), equalTo(GateProgress.State.IN_PROGRESS))
        assertThat(progress.correctCount, equalTo(0))
        assertThat(progress.isComplete, equalTo(false))
    }

    @Test
    fun `the same card passed twice counts once`() {
        val progress = GateProgress(requiredCorrect = 2)
        assertThat(progress.onAnswer(cardId = 1, rating = Rating.GOOD), equalTo(GateProgress.State.IN_PROGRESS))
        assertThat(progress.onAnswer(cardId = 1, rating = Rating.GOOD), equalTo(GateProgress.State.IN_PROGRESS))
        assertThat(progress.correctCount, equalTo(1))
        assertThat(progress.isComplete, equalTo(false))
    }

    @Test
    fun `a card failed then passed counts once`() {
        val progress = GateProgress(requiredCorrect = 1)
        assertThat(progress.onAnswer(cardId = 1, rating = Rating.AGAIN), equalTo(GateProgress.State.IN_PROGRESS))
        assertThat(progress.onAnswer(cardId = 1, rating = Rating.GOOD), equalTo(GateProgress.State.COMPLETE))
        assertThat(progress.correctCount, equalTo(1))
    }

    @Test
    fun `completes exactly at the required count for each allowed n`() {
        for (n in 1..3) {
            val progress = GateProgress(requiredCorrect = n)
            for (card in 1 until n) {
                assertThat(progress.onAnswer(cardId = card.toLong(), rating = Rating.GOOD), equalTo(GateProgress.State.IN_PROGRESS))
            }
            assertThat(progress.onAnswer(cardId = n.toLong(), rating = Rating.GOOD), equalTo(GateProgress.State.COMPLETE))
        }
    }

    @Test
    fun `progress stays complete after extra answers`() {
        val progress = GateProgress(requiredCorrect = 1)
        progress.onAnswer(cardId = 1, rating = Rating.GOOD)
        assertThat(progress.onAnswer(cardId = 2, rating = Rating.AGAIN), equalTo(GateProgress.State.COMPLETE))
        assertThat(progress.isComplete, equalTo(true))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `zero required cards is rejected`() {
        GateProgress(requiredCorrect = 0)
    }
}
