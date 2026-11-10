// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import androidx.appcompat.app.AlertDialog
import androidx.test.ext.junit.runners.AndroidJUnit4
import anki.backend.backendError
import anki.collection.OpChanges
import com.ichi2.anki.widgets.DeckAdapter
import com.ichi2.testutils.rules.BackendOverrideRule
import net.ankiweb.rsdroid.Backend
import net.ankiweb.rsdroid.exceptions.BackendIoException
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog
import kotlin.test.assertEquals
import kotlin.test.assertNull

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "xlarge")
class DeckPickerPartialDeletionRegressionTest : RobolectricTest() {
    @get:Rule
    val backendOverride = BackendOverrideRule()

    @Test
    fun `committed deletion is reconciled when replacement selection fails`() {
        var failSelection = false
        val failure = BackendIoException(backendError { message = "Cannot select the replacement deck" })
        backendOverride.replaceWith {
            object : Backend() {
                override fun setCurrentDeck(did: Long): OpChanges {
                    if (failSelection) {
                        failSelection = false
                        throw failure
                    }
                    return super.setCurrentDeck(did)
                }
            }
        }
        addNoteToDeck(1L)
        val deleted = addDeck("Deleted", setAsSelected = true)
        val survivor = addDeck("Survivor")
        addNoteToDeck(deleted)
        withDeckPicker(deckCount = 0) { picker ->
            picker.awaitVisibleDecks(listOf(1L, deleted, survivor))
            advanceRobolectricLooperUntil {
                picker.viewModel.loadDeckCounts?.isCompleted == true && picker.fragment?.shownDeckId == deleted
            }
            throwOnShowError = false
            failSelection = true
            val deletion = picker.deleteDeck(deleted)
            advanceRobolectricLooperUntil {
                deletion.isCompleted && (ShadowDialog.getLatestDialog() as? AlertDialog)?.isShowing == true
            }
            assertEquals(failure.localizedMessage, getAlertDialogText(checkDismissed = true))
            assertNull(col.decks.getLegacy(deleted), "The removal committed before selecting its replacement failed")
            ShadowDialog.getLatestDialog().dismiss()
            val adapter = picker.deckPickerBinding.decks.adapter as DeckAdapter
            advanceRobolectricLooperUntil(
                lazyMessage = { "The committed deletion must be reflected in the deck list, displayed pane, and Undo menu" },
            ) {
                adapter.currentList.none { it.did == deleted } &&
                    picker.fragment?.shownDeckId == col.decks.selected() &&
                    picker.viewModel.optionsMenuState?.undoAvailable == true
            }
        }
    }
}
