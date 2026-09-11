// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (c) 2026 Eric Li <ericli3690@gmail.com>

package com.ichi2.anki.reviewreminders

import androidx.fragment.app.FragmentActivity
import com.ichi2.anki.ScreenshotTest
import com.ichi2.anki.reviewreminders.AddEditReminderDialog.DialogMode
import org.junit.Test
import org.robolectric.Robolectric.buildActivity

class AddEditReminderDialogScreenshotTest : ScreenshotTest() {
    @Test
    fun `add mode`() {
        withReminderDialog(DialogMode.Add(ReviewReminderScope.Global)) {
            captureScreen("add_mode")

            binding.addEditReminderAdvancedDropdown.performClick()
            advanceRobolectricLooper()
            captureScreen("add_mode_advanced_open")
        }
    }

    @Test
    fun `edit mode`() {
        val deckId = addDeck("Test Deck")
        val reminder =
            ReviewReminder.createReviewReminder(
                time = ReviewReminderTime(9, 0),
                scope = ReviewReminderScope.DeckSpecific(deckId),
                onlyNotifyIfNoReviews = true,
            )
        withReminderDialog(DialogMode.Edit(reminder)) {
            captureScreen("edit_mode")

            binding.addEditReminderAdvancedDropdown.performClick()
            advanceRobolectricLooper()
            captureScreen("edit_mode_advanced_open")
        }
    }

    private fun withReminderDialog(
        mode: DialogMode,
        block: AddEditReminderDialog.() -> Unit,
    ) {
        val activity = buildActivity(FragmentActivity::class.java).setup().get()
        val dialog = AddEditReminderDialog.getInstance(mode)
        dialog.show(activity.supportFragmentManager, "dialog")
        advanceRobolectricLooper()
        dialog.block()
    }
}
