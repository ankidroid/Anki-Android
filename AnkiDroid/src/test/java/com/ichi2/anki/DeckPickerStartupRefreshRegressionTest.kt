// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import android.content.Intent
import androidx.core.content.edit
import androidx.core.view.isVisible
import androidx.test.ext.junit.runners.AndroidJUnit4
import anki.backend.backendError
import anki.decks.DeckTreeNode
import com.ichi2.anki.common.preferences.sharedPrefs
import com.ichi2.testutils.BackupManagerTestUtilities
import com.ichi2.testutils.rules.BackendOverrideRule
import net.ankiweb.rsdroid.Backend
import net.ankiweb.rsdroid.exceptions.BackendIoException
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.shadows.ShadowDialog
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
class DeckPickerStartupRefreshRegressionTest : RobolectricTest() {
    @get:Rule
    val backendOverride = BackendOverrideRule()

    @Test
    fun `the deck list becomes usable after acknowledging a transient startup refresh failure`() {
        val failFirstTree = AtomicBoolean(true)
        val failure = BackendIoException(backendError { message = "Cannot load the deck list" })
        backendOverride.replaceWith {
            object : Backend() {
                override fun deckTree(now: Long): DeckTreeNode = if (failFirstTree.getAndSet(false)) throw failure else super.deckTree(now)
            }
        }
        val target = addDeck("Target")
        addNoteToDeck(target)
        setIntroductionSlidesShown(true)
        BackupManagerTestUtilities.setupSpaceForBackup(targetContext)
        targetContext.sharedPrefs().edit { putBoolean("backupPromptDisabled", true) }
        throwOnShowError = false

        val picker = startActivityNormallyOpenCollectionWithIntent(DeckPicker::class.java, Intent())
        picker.viewModel.awaitStartup()
        advanceRobolectricLooperUntil { ShadowDialog.getLatestDialog()?.isShowing == true }
        assertEquals(failure.localizedMessage, getAlertDialogText(checkDismissed = true))
        ShadowDialog.getLatestDialog().dismiss()
        advanceRobolectricLooperUntil(lazyMessage = { "The deck list is still hidden behind its startup spinner" }) {
            picker.deckPickerBinding.deckPickerContent.isVisible
        }
        picker.awaitVisibleDecks(listOf(target))
    }
}
