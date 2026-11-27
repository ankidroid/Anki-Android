// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2026 Ashish Yadav <mailtoashish693@gmail.com>
package com.ichi2.anki

import android.content.Intent
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.core.content.edit
import androidx.core.view.isVisible
import androidx.test.ext.junit.runners.AndroidJUnit4
import anki.backend.BackendError
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
import org.robolectric.Robolectric
import org.robolectric.shadows.ShadowDialog
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
class DeckPickerRefreshErrorTest : RobolectricTest() {
    @get:Rule
    val backendOverride = BackendOverrideRule()

    private val failure =
        BackendIoException(
            backendError {
                kind = BackendError.Kind.IO_ERROR
                message = "Operation not permitted (os error 1)"
            },
        )

    @Test
    fun `failed deck list update shows an error`() {
        var failDeckTree = false
        backendOverride.replaceWith {
            object : Backend() {
                override fun deckTree(now: Long): DeckTreeNode = if (failDeckTree) throw failure else super.deckTree(now)
            }
        }

        withDeckPicker(deckCount = 0) { deckPicker ->
            throwOnShowError = false
            failDeckTree = true
            deckPicker.viewModel.updateDeckList()

            assertErrorShown()
        }
    }

    @Test
    fun `failed first deck list load hides the loading spinner`() {
        backendOverride.replaceWith {
            object : Backend() {
                override fun deckTree(now: Long): DeckTreeNode = throw failure
            }
        }
        throwOnShowError = false
        ensureCollectionLoadIsSynchronous()
        setIntroductionSlidesShown(true)
        BackupManagerTestUtilities.setupSpaceForBackup(targetContext)
        targetContext.sharedPrefs().edit { putBoolean("backupPromptDisabled", true) }

        val deckPicker = startActivityNormallyOpenCollectionWithIntent(DeckPicker::class.java, Intent())
        assertErrorShown()
        ShadowDialog.getLatestDialog().dismiss()

        val spinner = deckPicker.findViewById<View>(R.id.progress_bar)
        advanceRobolectricLooperUntil(lazyMessage = { "the loading spinner should be hidden after a failed refresh" }) {
            !spinner.isVisible
        }
    }

    @Test
    fun `early startup failure hides the loading spinner`() {
        backendOverride.replaceWith {
            object : Backend() {
                override fun deckTree(now: Long): DeckTreeNode = throw failure
            }
        }
        throwOnShowError = false
        ensureCollectionLoadIsSynchronous()
        setIntroductionSlidesShown(true)
        InitialActivity.setUpgradedToLatestVersion(getPreferences())
        BackupManagerTestUtilities.setupSpaceForBackup(targetContext)
        targetContext.sharedPrefs().edit { putBoolean("backupPromptDisabled", true) }

        val controller = Robolectric.buildActivity(DeckPicker::class.java, Intent())
        saveControllerForCleanup(controller)
        controller.create()
        val deckPicker = controller.get()
        advanceRobolectricLooperUntil {
            deckPicker.viewModel.startupJob?.isCompleted == true &&
                deckPicker.viewModel.loadDeckCounts?.isCompleted == true
        }
        controller.start().resume().visible()
        assertErrorShown()
        ShadowDialog.getLatestDialog().dismiss()

        val spinner = deckPicker.findViewById<View>(R.id.progress_bar)
        advanceRobolectricLooperUntil(lazyMessage = { "the loading spinner should be hidden after a failed startup" }) {
            !spinner.isVisible
        }
    }

    private fun assertErrorShown() {
        advanceRobolectricLooperUntil { (ShadowDialog.getLatestDialog() as? AlertDialog)?.isShowing == true }
        assertEquals(failure.localizedMessage, getAlertDialogText(checkDismissed = true))
    }
}
