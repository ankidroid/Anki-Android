// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import androidx.fragment.app.Fragment
import com.ichi2.anki.FragmentLifecycleFixture.Transition.DESTROY_VIEW
import com.ichi2.anki.FragmentLifecycleFixture.Transition.RECREATE_VIEW
import com.ichi2.anki.FragmentLifecycleFixture.Transition.REMOVE
import com.ichi2.anki.browser.BrowserColumnSelectionFragment
import com.ichi2.anki.cardviewer.SingleCardSide
import com.ichi2.anki.dialogs.ChangeNoteTypeDialog
import com.ichi2.anki.dialogs.InsertFieldDialog
import com.ichi2.anki.dialogs.InsertFieldMetadata
import com.ichi2.anki.dialogs.customstudy.CustomStudyDialog
import com.ichi2.anki.filtered.FilteredDeckOptionsFragment
import com.ichi2.anki.mediacheck.MediaCheckFragment
import com.ichi2.anki.model.CardsOrNotes
import com.ichi2.anki.preferences.AboutFragment
import com.ichi2.anki.preferences.AdvancedSettingsFragment
import com.ichi2.anki.preferences.AppearanceSettingsFragment
import com.ichi2.anki.preferences.DeveloperOptionsFragment
import com.ichi2.anki.preferences.GeneralSettingsFragment
import com.ichi2.anki.preferences.ReviewerOptionsFragment
import com.ichi2.anki.preferences.ReviewingSettingsFragment
import com.ichi2.anki.preferences.SyncSettingsFragment
import com.ichi2.anki.reviewreminders.AddEditReminderDialog
import com.ichi2.anki.reviewreminders.ReviewReminderScope
import com.ichi2.anki.reviewreminders.ScheduleRemindersFragment
import com.ichi2.anki.ui.windows.managespace.ManageSpaceFragment

/** A screen and its required setup for [FragmentCoroutineLifecycleTest]. */
class FragmentLifecycleFixture(
    private val name: String,
    val host: Class<out AnkiActivity> = AnkiActivity::class.java,
    val hasFragmentView: Boolean = true,
    // Confirmed failures in the startup stress test. Remove each entry when its failure is fixed.
    val knownFailures: Set<Transition> = emptySet(),
    val create: RobolectricTest.() -> Fragment,
) {
    enum class Transition { REMOVE, DESTROY_VIEW, RECREATE_VIEW }

    override fun toString(): String = name
}

fun fragmentLifecycleFixtures(): List<FragmentLifecycleFixture> =
    listOf(
        FragmentLifecycleFixture("StudyOptions", knownFailures = setOf(REMOVE)) { StudyOptionsFragment() },
        FragmentLifecycleFixture("AppearanceSettings", knownFailures = setOf(REMOVE)) { AppearanceSettingsFragment() },
        FragmentLifecycleFixture("GeneralSettings", knownFailures = setOf(REMOVE)) { GeneralSettingsFragment() },
        FragmentLifecycleFixture("ReviewerOptions", knownFailures = setOf(REMOVE)) { ReviewerOptionsFragment() },
        FragmentLifecycleFixture("ReviewingSettings", knownFailures = setOf(REMOVE)) { ReviewingSettingsFragment() },
        FragmentLifecycleFixture("AdvancedSettings") { AdvancedSettingsFragment() },
        FragmentLifecycleFixture("DeveloperOptions") { DeveloperOptionsFragment() },
        FragmentLifecycleFixture("SyncSettings") { SyncSettingsFragment() },
        FragmentLifecycleFixture("About") { AboutFragment() },
        FragmentLifecycleFixture("ScheduleReminders", knownFailures = setOf(REMOVE, DESTROY_VIEW)) { ScheduleRemindersFragment() },
        FragmentLifecycleFixture("FilteredDeckOptions", knownFailures = setOf(REMOVE, DESTROY_VIEW, RECREATE_VIEW)) {
            FilteredDeckOptionsFragment()
        },
        FragmentLifecycleFixture("MediaCheck", knownFailures = setOf(REMOVE, DESTROY_VIEW)) { MediaCheckFragment() },
        FragmentLifecycleFixture("ManageSpace", knownFailures = setOf(REMOVE)) { ManageSpaceFragment() },
        FragmentLifecycleFixture("ChangeNoteType", knownFailures = setOf(REMOVE)) {
            ChangeNoteTypeDialog.newInstance(listOf(addBasicNote().id))
        },
        FragmentLifecycleFixture("CustomStudy", hasFragmentView = false) { CustomStudyDialog.createInstance(col.decks.selected()) },
        FragmentLifecycleFixture("AddReminder", hasFragmentView = false, knownFailures = setOf(REMOVE)) {
            AddEditReminderDialog.getInstance(AddEditReminderDialog.DialogMode.Add(ReviewReminderScope.Global))
        },
        FragmentLifecycleFixture("InsertField", knownFailures = setOf(REMOVE)) {
            InsertFieldDialog.newInstance(
                fieldItems = listOf("Front", "Back"),
                metadata = InsertFieldMetadata(SingleCardSide.FRONT, "Card 1", "Basic", null, null, null, "Default"),
                requestKey = "insert-field-test",
            )
        },
        FragmentLifecycleFixture("BrowserColumns", host = CardBrowser::class.java, knownFailures = setOf(REMOVE, DESTROY_VIEW)) {
            BrowserColumnSelectionFragment.createInstance(CardsOrNotes.CARDS)
        },
    )
