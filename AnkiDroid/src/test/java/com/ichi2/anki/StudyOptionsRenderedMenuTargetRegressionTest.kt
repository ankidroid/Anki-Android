// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import android.widget.TextView
import androidx.appcompat.view.menu.MenuBuilder
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.dialogs.customstudy.CustomStudyDialog
import com.ichi2.anki.reviewreminders.ReviewReminderScope
import com.ichi2.anki.reviewreminders.ScheduleRemindersFragment
import com.ichi2.anki.settings.Prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "xlarge")
class StudyOptionsRenderedMenuTargetRegressionTest : RobolectricTest() {
    @Test
    fun `Custom Study uses the displayed deck before a refreshed model is rendered`() {
        withUnrenderedSelection { picker, pane, shown ->
            pane.onMenuItemSelected(MenuBuilder(picker).add(0, R.id.action_custom_study, 0, "Custom Study"))
            picker.supportFragmentManager.executePendingTransactions()
            val fragments = picker.supportFragmentManager.fragments
            val dialog = fragments.filterIsInstance<CustomStudyDialog>().single()
            assertEquals(shown, dialog.viewModel.deckId, "Custom Study must not change limits or cards in another deck")
        }
    }

    @Test
    fun `Deck Options uses the displayed deck before a refreshed model is rendered`() {
        withUnrenderedSelection { picker, pane, shown ->
            pane.onMenuItemSelected(MenuBuilder(picker).add(0, R.id.action_deck_or_study_options, 0, "Deck Options"))
            val destination = assertNotNull(shadowOf(picker).nextStartedActivity)
            val arguments = assertNotNull(destination.getBundleExtra(SingleFragmentActivity.EXTRA_FRAGMENT_ARGS))
            assertEquals(shown, arguments.getLong("deckId"), "The pane must open options for the deck it displays")
        }
    }

    @Test
    fun `Deck Options keeps the displayed deck type before a filtered deck refresh is rendered`() {
        withUnrenderedSelection(otherFiltered = true) { picker, pane, shown ->
            pane.onMenuItemSelected(MenuBuilder(picker).add(0, R.id.action_deck_or_study_options, 0, "Deck Options"))
            val destination = assertNotNull(shadowOf(picker).nextStartedActivity)
            val optionsClass = com.ichi2.anki.pages.DeckOptions::class.java.name
            assertEquals(optionsClass, destination.getStringExtra(SingleFragmentActivity.EXTRA_FRAGMENT_NAME))
            val arguments = assertNotNull(destination.getBundleExtra(SingleFragmentActivity.EXTRA_FRAGMENT_ARGS))
            assertEquals(shown, arguments.getLong("deckId"))
        }
    }

    @Test
    @Suppress("DEPRECATION")
    fun `Review Reminders uses the displayed deck before a refreshed model is rendered`() {
        Prefs.newReviewRemindersEnabled = true
        withUnrenderedSelection { picker, pane, shown ->
            pane.onMenuItemSelected(MenuBuilder(picker).add(0, R.id.action_schedule_reminders, 0, "Review Reminders"))
            picker.supportFragmentManager.executePendingTransactions()
            val reminders = assertIs<ScheduleRemindersFragment>(picker.supportFragmentManager.findFragmentById(R.id.studyoptions_fragment))
            assertEquals(
                ReviewReminderScope.DeckSpecific(shown),
                reminders.requireArguments().getParcelable<ReviewReminderScope>("arg_scope"),
                "The pane must edit reminders for the deck it displays",
            )
        }
    }

    private fun withUnrenderedSelection(
        otherFiltered: Boolean = false,
        block: (DeckPicker, StudyOptionsFragment, Long) -> Unit,
    ) {
        val shown = addDeck("Shown", setAsSelected = true)
        val other = if (otherFiltered) addDynamicDeck("Other") else addDeck("Other")
        col.decks.select(shown)
        addNoteToDeck(shown)
        if (!otherFiltered) addNoteToDeck(other)
        withDeckPicker(deckCount = 0) { picker ->
            advanceRobolectricLooperUntil {
                picker.viewModel.loadDeckCounts?.isCompleted == true && picker.fragment?.viewModel?.selectedDeckId == shown
            }
            advanceRobolectricLooper()
            val pane = assertNotNull(picker.fragment)
            val label = pane.requireView().findViewById<TextView>(R.id.studyoptions_deck_name)
            assertEquals("Shown", label.text.toString())
            col.decks.select(other)
            // Finish the worker refresh without running the main-thread render queued by its StateFlow.
            runBlocking { withContext(Dispatchers.Default) { pane.viewModel.refreshData() } }
            assertEquals(other, pane.viewModel.selectedDeckId)
            assertEquals("Shown", label.text.toString())
            block(picker, pane, shown)
        }
    }
}
