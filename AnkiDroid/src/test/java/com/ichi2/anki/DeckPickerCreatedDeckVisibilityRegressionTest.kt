// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import androidx.appcompat.app.AlertDialog
import androidx.test.ext.junit.runners.AndroidJUnit4
import anki.backend.backendError
import anki.collection.OpChanges
import com.ichi2.anki.dialogs.utils.input
import com.ichi2.anki.dialogs.utils.performPositiveClick
import com.ichi2.testutils.rules.BackendOverrideRule
import net.ankiweb.rsdroid.Backend
import net.ankiweb.rsdroid.exceptions.BackendIoException
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.shadows.ShadowDialog
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@RunWith(AndroidJUnit4::class)
class DeckPickerCreatedDeckVisibilityRegressionTest : RobolectricTest() {
    @get:Rule
    val backendOverride = BackendOverrideRule()

    @Test
    fun `created deck remains visible when its selection fails`() {
        var failSelection = false
        val failure = BackendIoException(backendError { message = "Cannot select the new deck" })
        backendOverride.replaceWith {
            object : Backend() {
                override fun setCurrentDeck(did: Long): OpChanges = if (failSelection) throw failure else super.setCurrentDeck(did)
            }
        }
        val existing = addDeck("Existing")
        withDeckPicker(deckCount = 0) { picker ->
            picker.awaitVisibleDecks(listOf(existing))
            advanceRobolectricLooperUntil { picker.viewModel.loadDeckCounts?.isCompleted == true }
            throwOnShowError = false
            picker.showCreateDeckDialog()
            val createDialog = ShadowDialog.getLatestDialog() as AlertDialog
            createDialog.input = "New deck"
            failSelection = true
            createDialog.performPositiveClick()
            advanceRobolectricLooperUntil {
                ShadowDialog.getLatestDialog().let { it !== createDialog && it?.isShowing == true }
            }
            assertEquals(failure.localizedMessage, getAlertDialogText(checkDismissed = true))
            val created = assertNotNull(col.decks.byName("New deck")).id
            ShadowDialog.getLatestDialog().dismiss()
            picker.awaitVisibleDecks(listOf(existing, created))
        }
    }
}
