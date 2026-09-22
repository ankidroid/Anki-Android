// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.deckpicker

import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.Test

class DeckPickerBackgroundApplyTest {
    @Test
    fun stopClearDoesNotChangeActivityHasBackground() {
        assertThat(
            nextActivityHasBackground(current = true, drawableApplied = null),
            equalTo(true),
        )
        assertThat(
            nextActivityHasBackground(current = false, drawableApplied = null),
            equalTo(false),
        )
    }

    @Test
    fun resolveAttemptUpdatesActivityHasBackground() {
        assertThat(
            nextActivityHasBackground(current = false, drawableApplied = true),
            equalTo(true),
        )
        assertThat(
            nextActivityHasBackground(current = true, drawableApplied = false),
            equalTo(false),
        )
    }

    @Test
    fun failureToastIsConsumedOncePerActivityInstance() {
        val state = BackgroundFailureToastState()
        assertThat(state.shouldToast(), equalTo(true))
        assertThat(state.shouldToast(), equalTo(false))
        assertThat(state.shouldToast(), equalTo(false))
    }
}
