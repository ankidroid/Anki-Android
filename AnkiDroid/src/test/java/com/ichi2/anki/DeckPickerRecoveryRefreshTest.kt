// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import android.content.DialogInterface
import androidx.appcompat.app.AlertDialog
import androidx.core.os.BundleCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import anki.backend.backendError
import anki.decks.DeckTreeNode
import com.ichi2.anki.backend.DatabaseCorruption
import com.ichi2.anki.dialogs.DatabaseErrorDialog
import com.ichi2.anki.dialogs.DatabaseErrorDialog.DatabaseErrorDialogType
import com.ichi2.anki.utils.ext.DIALOG_FRAGMENT_TAG
import com.ichi2.testutils.rules.BackendOverrideRule
import net.ankiweb.rsdroid.Backend
import net.ankiweb.rsdroid.BackendException.BackendDbException.BackendDbCorruptException
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.shadows.ShadowDialog
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
class DeckPickerRecoveryRefreshTest : RobolectricTest() {
    @get:Rule
    val backendOverride = BackendOverrideRule()

    @After
    fun resetCorruptionFlag() {
        DatabaseCorruption.isDetected = false
    }

    @Test
    fun `returning to the deck picker keeps the recovery options open`() {
        var failDeckTree = false
        backendOverride.replaceWith {
            object : Backend() {
                override fun deckTree(now: Long): DeckTreeNode =
                    if (failDeckTree) throw BackendDbCorruptException(backendError {}) else super.deckTree(now)
            }
        }

        withDeckPicker(deckCount = 0) { deckPicker ->
            throwOnShowError = false
            failDeckTree = true
            deckPicker.viewModel.updateDeckList()
            advanceRobolectricLooperUntil { deckPicker.shownDatabaseErrorDialog == DatabaseErrorDialogType.DIALOG_LOAD_FAILED }

            // the user opens the recovery options
            (ShadowDialog.getLatestDialog() as AlertDialog).getButton(DialogInterface.BUTTON_POSITIVE).performClick()
            advanceRobolectricLooperUntil { deckPicker.shownDatabaseErrorDialog == DatabaseErrorDialogType.DIALOG_ERROR_HANDLING }

            // the user switches apps and returns: onResume refreshes the deck list
            val previousRefresh = deckPicker.viewModel.loadDeckCounts
            deckPicker.refreshState()
            advanceRobolectricLooperUntil {
                deckPicker.viewModel.loadDeckCounts.let { it !== previousRefresh && it?.isCompleted == true }
            }
            advanceRobolectricLooper()

            assertEquals(
                DatabaseErrorDialogType.DIALOG_ERROR_HANDLING,
                deckPicker.shownDatabaseErrorDialog,
                "returning to the app should keep the recovery options open",
            )
        }
    }

    private val DeckPicker.shownDatabaseErrorDialog: DatabaseErrorDialogType?
        get() {
            val dialog = supportFragmentManager.findFragmentByTag(DIALOG_FRAGMENT_TAG) as? DatabaseErrorDialog ?: return null
            return BundleCompat.getParcelable(dialog.requireArguments(), "dialog", DatabaseErrorDialogType::class.java)
        }
}
