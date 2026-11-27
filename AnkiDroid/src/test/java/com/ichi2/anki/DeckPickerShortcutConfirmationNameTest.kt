// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import android.view.KeyEvent
import androidx.appcompat.app.AlertDialog
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.dialogs.DeckPickerConfirmDeleteDeckDialog
import com.ichi2.anki.dialogs.DeckPickerContextMenu.DeckPickerContextMenuOption.SCHEDULE_REMINDERS
import com.ichi2.anki.dialogs.utils.message
import com.ichi2.anki.reviewreminders.ScheduleRemindersFragment
import com.ichi2.anki.settings.Prefs
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "xlarge")
class DeckPickerShortcutConfirmationNameTest : RobolectricTest() {
    @Test
    fun `confirmation keeps HTML entities in the name`() = assertShortcutConfirmationName("Math &amp; Science", "Math & Science")

    @Test
    fun `confirmation keeps double spaces in the name`() = assertShortcutConfirmationName("Math  Science", "Math Science")

    private fun assertShortcutConfirmationName(
        deckName: String,
        otherDeckName: String,
    ) {
        val target = addDeck(deckName, setAsSelected = true)
        val other = addDeck(otherDeckName)
        addNoteToDeck(target)
        addNoteToDeck(other)
        Prefs.newReviewRemindersEnabled = true

        withDeckPicker(deckCount = 0) { picker ->
            assertEquals(deckName, col.decks.name(target))
            assertEquals(otherDeckName, col.decks.name(other))
            picker.selectContextMenuOptionAndAwait(SCHEDULE_REMINDERS, other) {
                picker.supportFragmentManager.findFragmentById(R.id.studyoptions_fragment) is ScheduleRemindersFragment
            }
            assertEquals(target, col.decks.selected())
            picker.onKeyUp(
                KeyEvent.KEYCODE_DEL,
                KeyEvent(0, 0, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DEL, 0, KeyEvent.META_SHIFT_ON),
            )

            fun confirmation() =
                picker.supportFragmentManager.fragments
                    .filterIsInstance<DeckPickerConfirmDeleteDeckDialog>()
                    .firstOrNull { it.dialog?.isShowing == true }

            advanceRobolectricLooperUntil { confirmation() != null }
            val dialog = assertNotNull(confirmation()).requireDialog() as AlertDialog
            assertContains(dialog.message, deckName)
        }
    }
}
