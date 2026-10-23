// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import android.view.ContextThemeWrapper
import androidx.appcompat.app.AlertDialog
import androidx.core.content.edit
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import anki.backend.backendError
import anki.collection.OpChangesWithCount
import com.google.protobuf.ByteString
import com.ichi2.anki.backend.DatabaseCorruption
import com.ichi2.anki.common.preferences.sharedPrefs
import com.ichi2.anki.dialogs.DatabaseErrorDialog
import com.ichi2.anki.utils.awaitDismissal
import com.ichi2.testutils.BackupManagerTestUtilities
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import net.ankiweb.rsdroid.Backend
import net.ankiweb.rsdroid.BackendException.BackendDbException.BackendDbCorruptException
import net.ankiweb.rsdroid.BackendFactory
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.shadows.ShadowDialog
import kotlin.test.assertFalse
import com.ichi2.anki.common.android.R as CommonR

@RunWith(AndroidJUnit4::class)
class DeckPickerAdditionalReviewTest : RobolectricTest() {
    @After
    fun resetBackendFactory() {
        BackendFactory.setOverride(null)
    }

    @Test
    fun `corruption while applying a deck filter opens recovery`() {
        var failRead = false
        runBlocking { CollectionManager.discardBackend() }
        BackendFactory.setOverride {
            object : Backend() {
                override fun getDeckLegacy(did: Long): ByteString =
                    if (failRead) {
                        throw BackendDbCorruptException(backendError { message = "database disk image is malformed" })
                    } else {
                        super.getDeckLegacy(did)
                    }
            }
        }
        val deckId = addDeck("Existing")
        val previouslyDetected = DatabaseCorruption.isDetected
        DatabaseCorruption.isDetected = false
        try {
            withDeckPicker(deckCount = 0) { picker ->
                throwOnShowError = false
                advanceRobolectricLooperUntil {
                    picker.viewModel.loadDeckCounts?.isCompleted == true &&
                        picker.viewModel.flowOfDeckList.value.data
                            .any { it.did == deckId }
                }
                failRead = true
                picker.viewModel.updateDeckFilter("no matching deck")
                advanceRobolectricLooperUntil(
                    lazyMessage = { "Corruption while filtering must open database recovery" },
                ) {
                    DatabaseCorruption.isDetected &&
                        picker.supportFragmentManager.fragments.any { it is DatabaseErrorDialog }
                }
            }
        } finally {
            DatabaseCorruption.isDetected = previouslyDetected
        }
    }

    @Test
    fun `cancelling the deck picker dialog wait releases the window`() =
        runTest {
            val dialog = AlertDialog.Builder(ContextThemeWrapper(targetContext, CommonR.style.Theme_Light)).show()
            val waiting = launch(start = CoroutineStart.UNDISPATCHED) { dialog.awaitDismissal() }
            try {
                waiting.cancel()
                waiting.join()
                assertFalse(dialog.isShowing, "Destroying the owner must dismiss its queued error dialog")
            } finally {
                dialog.dismiss()
            }
        }

    @Test
    fun `recreating deck picker dismisses the previous error dialog`() {
        runBlocking { CollectionManager.discardBackend() }
        BackendFactory.setOverride {
            object : Backend() {
                override fun removeDecks(dids: Iterable<Long>): OpChangesWithCount =
                    throw IllegalStateException("Review probe: deletion failed")
            }
        }
        val deckId = addDeck("Deck")
        setIntroductionSlidesShown(true)
        BackupManagerTestUtilities.setupSpaceForBackup(targetContext)
        targetContext.sharedPrefs().edit { putBoolean("backupPromptDisabled", true) }
        throwOnShowError = false

        ActivityScenario.launch(DeckPicker::class.java).use { scenario ->
            lateinit var originalDialog: AlertDialog
            scenario.onActivity { picker ->
                picker.viewModel.awaitStartup()
                val job = picker.deleteDeck(deckId)
                advanceRobolectricLooperUntil {
                    job.isCompleted && (ShadowDialog.getLatestDialog() as? AlertDialog)?.isShowing == true
                }
                originalDialog = ShadowDialog.getLatestDialog() as AlertDialog
            }
            scenario.recreate()
            scenario.onActivity {
                try {
                    assertFalse(originalDialog.isShowing, "The destroyed activity's error window must be released")
                } finally {
                    originalDialog.dismiss()
                }
            }
        }
    }
}
