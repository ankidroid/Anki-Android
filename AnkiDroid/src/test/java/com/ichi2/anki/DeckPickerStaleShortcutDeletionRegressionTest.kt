// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import android.view.KeyEvent
import androidx.test.ext.junit.runners.AndroidJUnit4
import anki.backend.backendError
import anki.collection.OpChanges
import anki.scheduler.CongratsInfoResponse
import com.ichi2.testutils.rules.BackendOverrideRule
import net.ankiweb.rsdroid.Backend
import net.ankiweb.rsdroid.exceptions.BackendIoException
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "xlarge")
class DeckPickerStaleShortcutDeletionRegressionTest : RobolectricTest() {
    @get:Rule
    val backendOverride = BackendOverrideRule()

    @Test
    fun `Shift+Del does not delete a deck the side panel is not showing`() {
        var deleting = false
        var failPanelRefresh = false
        val failure = BackendIoException(backendError { message = "Cannot refresh the side panel" })
        backendOverride.replaceWith {
            object : Backend() {
                override fun setCurrentDeck(did: Long): OpChanges =
                    super.setCurrentDeck(did).also {
                        if (deleting) failPanelRefresh = true
                    }

                override fun congratsInfo(): CongratsInfoResponse = if (failPanelRefresh) throw failure else super.congratsInfo()
            }
        }
        addNoteToDeck(1L)
        val deleted = addDeck("Deleted", setAsSelected = true)
        val survivor = addDeck("Survivor")
        addNoteToDeck(deleted)
        addNoteToDeck(survivor)
        withDeckPicker(deckCount = 0) { picker ->
            picker.awaitVisibleDecks(listOf(1L, deleted, survivor))
            advanceRobolectricLooperUntil {
                picker.viewModel.loadDeckCounts?.isCompleted == true && picker.fragment?.shownDeckId == deleted
            }
            throwOnShowError = false
            deleting = true
            val deletion = picker.deleteDeck(deleted)
            advanceRobolectricLooperUntil { deletion.isCompleted }
            advanceRobolectricLooper()
            assertEquals(deleted, picker.fragment?.shownDeckId, "The side panel still shows the deleted deck")
            assertEquals(survivor, col.decks.selected())

            picker.onKeyUp(
                KeyEvent.KEYCODE_DEL,
                KeyEvent(0, 0, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DEL, 0, KeyEvent.META_SHIFT_ON),
            )
            advanceRobolectricLooper()

            assertNotNull(col.decks.getLegacy(survivor), "Shift+Del deleted Survivor, which the side panel never showed")
        }
    }
}
