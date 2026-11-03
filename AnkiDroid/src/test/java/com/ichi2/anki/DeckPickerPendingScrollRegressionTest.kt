// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import android.os.Looper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.widgets.DeckAdapter
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class DeckPickerPendingScrollRegressionTest : RobolectricTest() {
    @Test
    fun `a pending focus scroll cannot hide a newer visible selection`() = assertNewerSelectionStaysVisible(waitForOldScrollRequest = false)

    @Test
    fun `a requested focus scroll cannot hide a newer visible selection`() =
        assertNewerSelectionStaysVisible(waitForOldScrollRequest = true)

    private fun assertNewerSelectionStaysVisible(waitForOldScrollRequest: Boolean) {
        withDeckPicker(deckCount = 40, withCards = true) { picker ->
            advanceRobolectricLooperUntil { picker.viewModel.loadDeckCounts?.isCompleted == true }
            val decks = picker.deckPickerBinding.decks
            val adapter = decks.adapter as DeckAdapter
            val layout = decks.layoutManager as LinearLayoutManager
            advanceRobolectricLooperUntil { adapter.itemCount >= 40 }
            layout.scrollToPositionWithOffset(0, 0)
            advanceRobolectricLooper()
            val latestPosition = layout.findFirstCompletelyVisibleItemPosition()
            assertTrue(latestPosition >= 0)
            val latest = adapter.currentList[latestPosition].did
            val oldPosition = adapter.itemCount - 1
            val old = adapter.currentList[oldPosition].did
            assertTrue(oldPosition > layout.findLastVisibleItemPosition())

            try {
                // Hold open the interval between requesting a scroll and its layout frame.
                decks.suppressLayout(true)
                val olderSelection = picker.viewModel.selectDeck(old)
                shadowOf(Looper.getMainLooper()).idle()
                assertTrue(olderSelection.isCompleted)
                assertEquals(old, picker.viewModel.focusedDeck)

                if (waitForOldScrollRequest) {
                    shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(10))
                    assertTrue(latestPosition in layout.findFirstVisibleItemPosition()..layout.findLastVisibleItemPosition())
                }

                val newerSelection = picker.viewModel.selectDeck(latest)
                shadowOf(Looper.getMainLooper()).idle()
                assertTrue(newerSelection.isCompleted)
                assertEquals(latest, col.decks.selected())
                assertEquals(latest, picker.viewModel.focusedDeck)
            } finally {
                decks.suppressLayout(false)
            }

            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(50))

            assertTrue(
                latestPosition in layout.findFirstVisibleItemPosition()..layout.findLastVisibleItemPosition(),
                "The delayed scroll for an older selection must not scroll the current selection off screen",
            )
        }
    }
}
