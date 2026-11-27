// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import androidx.appcompat.app.AlertDialog
import androidx.core.content.edit
import androidx.test.ext.junit.runners.AndroidJUnit4
import anki.backend.backendError
import anki.collection.OpChanges
import com.ichi2.anki.backend.DatabaseCorruption
import com.ichi2.anki.common.preferences.sharedPrefs
import com.ichi2.anki.dialogs.DatabaseErrorDialog
import com.ichi2.testutils.BackupManagerTestUtilities
import com.ichi2.testutils.rules.BackendOverrideRule
import net.ankiweb.rsdroid.Backend
import net.ankiweb.rsdroid.BackendException.BackendDbException.BackendDbCorruptException
import net.ankiweb.rsdroid.exceptions.BackendIoException
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.shadows.ShadowDialog
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class IntentHandlerSelectionFailureRegressionTest : RobolectricTest() {
    @get:Rule
    val backendOverride = BackendOverrideRule()

    private var selectionFailure: Exception? = null

    @Test
    fun `startup shortcut corruption opens database recovery instead of review`() {
        BackupManagerTestUtilities.setupSpaceForBackup(targetContext)
        targetContext.sharedPrefs().edit { putBoolean("backupPromptDisabled", true) }
        val previouslyDetected = DatabaseCorruption.isDetected
        DatabaseCorruption.isDetected = false
        try {
            val shortcut = openReviewShortcut(BackendDbCorruptException(backendError { message = "database disk image is malformed" }))
            val redirect = assertNotNull(shadowOf(shortcut.get()).nextStartedActivity)
            assertEquals(DeckPicker::class.java.name, redirect.component?.className)
            selectionFailure = null
            val picker = startActivityNormallyOpenCollectionWithIntent(DeckPicker::class.java, redirect)
            advanceRobolectricLooperUntil {
                picker.supportFragmentManager.fragments.any { it is DatabaseErrorDialog && it.dialog?.isShowing == true }
            }
            assertTrue(DatabaseCorruption.isDetected)
        } finally {
            DatabaseCorruption.isDetected = previouslyDetected
        }
    }

    @Test
    fun `startup shortcut selection failure is shown without launching review`() {
        val failure = BackendIoException(backendError { message = "Cannot select the shortcut deck" })
        val shortcut = openReviewShortcut(failure)
        advanceRobolectricLooperUntil { ShadowDialog.getLatestDialog()?.isShowing == true }
        assertEquals(failure.localizedMessage, getAlertDialogText(checkDismissed = true))
        assertNull(shadowOf(shortcut.get()).nextStartedActivity, "Failed selection must not start review for the previous deck")
    }

    @Test
    fun `dismissing a selection failure closes the shortcut screen`() {
        val shortcut = openReviewShortcut(BackendIoException(backendError { message = "Cannot select the shortcut deck" }))
        advanceRobolectricLooperUntil { ShadowDialog.getLatestDialog()?.isShowing == true }
        assertFalse(shortcut.get().isFinishing)

        val dialog = assertNotNull(ShadowDialog.getLatestDialog() as? AlertDialog)
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        advanceRobolectricLooper()

        assertTrue(shortcut.get().isFinishing)
    }

    private fun openReviewShortcut(failure: Exception): ActivityController<IntentHandler> {
        backendOverride.replaceWith {
            object : Backend() {
                override fun setCurrentDeck(did: Long): OpChanges = selectionFailure?.let { throw it } ?: super.setCurrentDeck(did)
            }
        }
        val target = addDeck("Shortcut deck")
        addNoteToDeck(target)
        ensureCollectionLoadIsSynchronous()
        setIntroductionSlidesShown(true)
        throwOnShowError = false
        val destination = IntentHandler.getReviewDeckIntent(targetContext, target)
        selectionFailure = failure
        return Robolectric.buildActivity(IntentHandler::class.java, destination).also { controller ->
            saveControllerForCleanup(controller)
            controller
                .create()
                .start()
                .resume()
                .visible()
        }
    }
}
