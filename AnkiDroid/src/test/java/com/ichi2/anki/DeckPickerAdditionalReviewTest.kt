// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import android.view.ContextThemeWrapper
import androidx.appcompat.app.AlertDialog
import androidx.core.content.edit
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import anki.collection.OpChangesWithCount
import com.ichi2.anki.common.preferences.sharedPrefs
import com.ichi2.anki.utils.awaitDismissal
import com.ichi2.testutils.BackupManagerTestUtilities
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import net.ankiweb.rsdroid.Backend
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
