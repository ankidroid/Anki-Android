// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import android.view.KeyEvent
import androidx.test.ext.junit.runners.AndroidJUnit4
import anki.ankidroid.DbResponse
import anki.backend.backendError
import com.ichi2.anki.dialogs.DeckPickerContextMenu.DeckPickerContextMenuOption.SCHEDULE_REMINDERS
import com.ichi2.anki.reviewreminders.ScheduleRemindersFragment
import com.ichi2.anki.settings.Prefs
import com.ichi2.testutils.rules.BackendOverrideRule
import net.ankiweb.rsdroid.Backend
import net.ankiweb.rsdroid.exceptions.BackendIoException
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "xlarge")
class DeckPickerShortcutConfirmationErrorTest : RobolectricTest() {
    @get:Rule
    val backendOverride = BackendOverrideRule()

    @Test
    fun `a failed confirmation read shows an error`() {
        val failingQuery = AtomicReference<String?>()
        val failure = BackendIoException(backendError { message = "Cannot count cards for deletion confirmation" })
        backendOverride.replaceWith {
            object : Backend() {
                override fun fullQueryProto(
                    query: String,
                    bindArgs: Array<out Any?>,
                ): DbResponse {
                    val queryToFail = failingQuery.get()
                    if (query == queryToFail && failingQuery.compareAndSet(queryToFail, null)) throw failure
                    return super.fullQueryProto(query, bindArgs)
                }
            }
        }
        val target = addDeck("Selected deck", setAsSelected = true)
        val other = addDeck("Reminders deck")
        addNoteToDeck(target)
        addNoteToDeck(other)
        Prefs.newReviewRemindersEnabled = true

        withDeckPicker(deckCount = 0) { picker ->
            picker.selectContextMenuOptionAndAwait(SCHEDULE_REMINDERS, other) {
                picker.supportFragmentManager.findFragmentById(R.id.studyoptions_fragment) is ScheduleRemindersFragment
            }
            assertEquals(target, col.decks.selected())
            throwOnShowError = false
            failingQuery.set("select count() from cards where did in ($target) or odid in ($target)")
            assertTrue(
                picker.onKeyUp(
                    KeyEvent.KEYCODE_DEL,
                    KeyEvent(0, 0, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DEL, 0, KeyEvent.META_SHIFT_ON),
                ),
            )

            advanceRobolectricLooperUntil(lazyMessage = { "The confirmation error was not shown" }) {
                ShadowDialog.getLatestDialog()?.isShowing == true
            }
            assertEquals(failure.localizedMessage, getAlertDialogText(checkDismissed = true))
            assertNotNull(col.decks.getLegacy(target))
        }
    }
}
