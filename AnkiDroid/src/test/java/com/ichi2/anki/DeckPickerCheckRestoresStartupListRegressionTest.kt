// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import android.content.Intent
import androidx.appcompat.app.AlertDialog
import androidx.core.content.edit
import androidx.core.view.isVisible
import androidx.test.ext.junit.runners.AndroidJUnit4
import anki.backend.backendError
import anki.decks.DeckTreeNode
import com.ichi2.anki.backend.DatabaseCorruption
import com.ichi2.anki.common.preferences.sharedPrefs
import com.ichi2.anki.dialogs.DatabaseErrorDialog.DatabaseErrorDialogType
import com.ichi2.anki.dialogs.utils.performPositiveClick
import com.ichi2.testutils.BackupManagerTestUtilities
import com.ichi2.testutils.rules.BackendOverrideRule
import kotlinx.coroutines.Dispatchers
import net.ankiweb.rsdroid.Backend
import net.ankiweb.rsdroid.BackendException.BackendDbException.BackendDbCorruptException
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.shadows.ShadowDialog
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class DeckPickerCheckRestoresStartupListRegressionTest : RobolectricTest() {
    @get:Rule
    val backendOverride = BackendOverrideRule()

    @Test
    fun `successful database check restores a deck list whose first refresh failed`() {
        val firstRead = CountDownLatch(1)
        val releaseRead = CountDownLatch(1)
        val repaired = AtomicBoolean(false)
        backendOverride.replaceWith {
            object : Backend() {
                override fun deckTree(now: Long): DeckTreeNode {
                    if (!repaired.get()) {
                        firstRead.countDown()
                        check(releaseRead.await(30, TimeUnit.SECONDS))
                        throw BackendDbCorruptException(backendError { message = "database disk image is malformed" })
                    }
                    return super.deckTree(now)
                }

                override fun checkDatabase(): List<String> {
                    repaired.set(true)
                    return listOf("Database check complete")
                }
            }
        }
        val deck = addDeck("Target")
        addNoteToDeck(deck)
        setIntroductionSlidesShown(true)
        BackupManagerTestUtilities.setupSpaceForBackup(targetContext)
        targetContext.sharedPrefs().edit { putBoolean("backupPromptDisabled", true) }
        throwOnShowError = false
        val previouslyDetected = DatabaseCorruption.isDetected
        DatabaseCorruption.isDetected = false
        val dispatcher = CollectionManager.setTestDispatcher(Dispatchers.IO.limitedParallelism(1), useReentrantLock = false)
        try {
            val picker = startActivityNormallyOpenCollectionWithIntent(DeckPicker::class.java, Intent())
            advanceRobolectricLooperUntil { firstRead.count == 0L }
            picker.showDatabaseErrorDialog(DatabaseErrorDialogType.DIALOG_CONFIRM_DATABASE_CHECK)
            advanceRobolectricLooper()
            val confirmation = ShadowDialog.getLatestDialog() as AlertDialog
            releaseRead.countDown()
            advanceRobolectricLooperUntil { picker.viewModel.loadDeckCounts?.isCompleted == true }
            assertTrue(
                picker.viewModel.pendingErrors.value
                    .isNotEmpty(),
            )
            assertFalse(picker.deckPickerBinding.deckPickerContent.isVisible)

            confirmation.performPositiveClick()
            advanceRobolectricLooperUntil {
                val dialog = ShadowDialog.getLatestDialog()
                repaired.get() && dialog !== confirmation && dialog?.isShowing == true
            }
            assertEquals("Database check complete", getAlertDialogText(checkDismissed = false))
            ShadowDialog.getLatestDialog().dismiss()

            advanceRobolectricLooperUntil(lazyMessage = { "The deck list remains hidden after the successful database check" }) {
                picker.deckPickerBinding.deckPickerContent.isVisible
            }
            picker.awaitVisibleDecks(listOf(deck))
        } finally {
            releaseRead.countDown()
            CollectionManager.setTestDispatcher(dispatcher)
            DatabaseCorruption.isDetected = previouslyDetected
        }
    }
}
