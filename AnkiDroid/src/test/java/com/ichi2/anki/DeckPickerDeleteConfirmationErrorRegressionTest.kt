// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import android.view.KeyEvent
import androidx.appcompat.app.AlertDialog
import androidx.core.content.edit
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import anki.backend.backendError
import com.google.protobuf.ByteString
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.common.preferences.sharedPrefs
import com.ichi2.testutils.BackupManagerTestUtilities
import com.ichi2.testutils.rules.BackendOverrideRule
import kotlinx.coroutines.runBlocking
import net.ankiweb.rsdroid.Backend
import net.ankiweb.rsdroid.exceptions.BackendIoException
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "xlarge")
class DeckPickerDeleteConfirmationErrorRegressionTest : RobolectricTest() {
    @get:Rule
    val backendOverride = BackendOverrideRule()

    @Test
    fun `error preparing guarded tablet deletion survives recreation`() {
        val failDetails = AtomicBoolean(false)
        val failure = BackendIoException(backendError { message = "Cannot read deletion details" })
        backendOverride.replaceWith {
            object : Backend() {
                override fun getDeckLegacy(did: Long): ByteString {
                    if (failDetails.getAndSet(false)) throw failure
                    return super.getDeckLegacy(did)
                }
            }
        }
        val displayed = addDeck("Displayed", setAsSelected = true)
        val selected = addDeck("Selected")
        addNoteToDeck(displayed)
        addNoteToDeck(selected)
        setIntroductionSlidesShown(true)
        BackupManagerTestUtilities.setupSpaceForBackup(targetContext)
        targetContext.sharedPrefs().edit { putBoolean("backupPromptDisabled", true) }
        throwOnShowError = false
        ActivityScenario.launch(DeckPicker::class.java).use { scenario ->
            scenario.onActivity { picker ->
                picker.viewModel.awaitStartup()
                advanceRobolectricLooperUntil {
                    picker.viewModel.loadDeckCounts?.isCompleted == true && picker.fragment?.shownDeckId == displayed
                }
                runBlocking { withCol { decks.select(selected) } }
                assertEquals(displayed, picker.fragment?.shownDeckId)
                assertEquals(selected, col.decks.selected())
                failDetails.set(true)
                picker.onKeyUp(
                    KeyEvent.KEYCODE_DEL,
                    KeyEvent(0, 0, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DEL, 0, KeyEvent.META_SHIFT_ON),
                )
                advanceRobolectricLooperUntil { (ShadowDialog.getLatestDialog() as? AlertDialog)?.isShowing == true }
                assertEquals(failure.localizedMessage, getAlertDialogText(checkDismissed = false))
                assertNotNull(col.decks.getLegacy(selected), "The failed confirmation must leave the deck intact")
            }
            scenario.recreate()
            scenario.onActivity { picker ->
                picker.viewModel.awaitStartup()
                advanceRobolectricLooper()
                val restored = ShadowDialog.getLatestDialog() as? AlertDialog
                assertTrue(
                    restored?.isShowing == true && restored.context.findAnkiActivity() === picker,
                    "The confirmation error disappeared",
                )
                assertEquals(failure.localizedMessage, getAlertDialogText(checkDismissed = false))
            }
        }
    }
}
