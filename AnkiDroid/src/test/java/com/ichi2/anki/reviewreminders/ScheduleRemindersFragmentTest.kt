// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2026 Eric Li <ericli3690@gmail.com>

package com.ichi2.anki.reviewreminders

import androidx.core.content.edit
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.utils.ConfigAwareSingleFragmentActivity
import kotlinx.coroutines.runBlocking
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ScheduleRemindersFragmentTest : RobolectricTest() {
    @Before
    fun setUpReminders() {
        ReviewRemindersDatabase.remindersSharedPrefs.edit { clear() }
    }

    @After
    fun tearDownReminders() {
        ReviewRemindersDatabase.remindersSharedPrefs.edit { clear() }
    }

    @Test
    fun `reminders added on another screen are shown when the screen starts`() {
        val added = ReviewReminder.createReviewReminder(ReviewReminderTime(10, 0))

        val intent = ScheduleRemindersFragment.getIntent(targetContext, ReviewReminderScope.Global)
        ActivityScenario.launch<ConfigAwareSingleFragmentActivity>(intent).use { scenario ->
            advanceRobolectricLooper()
            var placeholderShownOnStart: Boolean? = null
            scenario.onActivity { activity ->
                val fragment = activity.fragment as ScheduleRemindersFragment
                fragment.lifecycle.addObserver(
                    LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_START) placeholderShownOnStart = fragment.binding.noRemindersPlaceholder.isVisible
                    },
                )
            }

            // Another screen, e.g. Settings, changes the reminders while this one is stopped
            scenario.moveToState(Lifecycle.State.CREATED)
            runBlocking { ReviewRemindersDatabase.insertReminder(added) }
            scenario.moveToState(Lifecycle.State.RESUMED)
            advanceRobolectricLooper()

            assertThat(placeholderShownOnStart, equalTo(false))
            scenario.onActivity { activity ->
                val fragment = activity.fragment as ScheduleRemindersFragment
                val displayedIds = (fragment.binding.recyclerView.adapter as ScheduleRemindersAdapter).currentList.map { it.id }
                assertThat(displayedIds, equalTo(listOf(added.id)))
            }
        }
    }
}
