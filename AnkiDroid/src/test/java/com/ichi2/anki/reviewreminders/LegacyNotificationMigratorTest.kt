/*
 *  Copyright (c) 2026 AnkiDroid Contributors
 *
 *  This program is free software; you can redistribute it and/or modify it under
 *  the terms of the GNU General Public License as published by the Free Software
 *  Foundation; either version 3 of the License, or (at your option) any later
 *  version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY
 *  WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 *  PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along with
 *  this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.anki.reviewreminders

import android.content.Context
import androidx.core.content.edit
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.R
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.common.preferences.sharedPrefs
import com.ichi2.anki.preferences.PENDING_NOTIFICATIONS_ONLY
import com.ichi2.anki.settings.Prefs
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.hasSize
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LegacyNotificationMigratorTest : RobolectricTest() {
    private lateinit var context: Context

    @Before
    override fun setUp() {
        super.setUp()
        context = targetContext
        Prefs.legacyNotificationsMigrated = false
        Prefs.newReviewRemindersEnabled = false
        ReviewRemindersDatabase.remindersSharedPrefs.edit { clear() }
        context.sharedPrefs().edit {
            putString(
                context.getString(R.string.pref_notifications_minimum_cards_due_key),
                PENDING_NOTIFICATIONS_ONLY.toString(),
            )
        }
    }

    @After
    override fun tearDown() {
        ReviewRemindersDatabase.remindersSharedPrefs.edit { clear() }
        Prefs.legacyNotificationsMigrated = false
        super.tearDown()
    }

    @Test
    fun `migration is idempotent`() =
        runTest {
            LegacyNotificationMigrator.migrateIfNeeded(context)
            assertThat(Prefs.legacyNotificationsMigrated, equalTo(true))
            assertThat(Prefs.newReviewRemindersEnabled, equalTo(true))

            Prefs.newReviewRemindersEnabled = false
            LegacyNotificationMigrator.migrateIfNeeded(context)
            // Second call must not re-enable or re-run side effects beyond the completed flag.
            assertThat(Prefs.newReviewRemindersEnabled, equalTo(false))
            assertThat(Prefs.legacyNotificationsMigrated, equalTo(true))
        }

    @Test
    fun `disabled legacy notifications enable review reminders without creating a reminder`() =
        runTest {
            LegacyNotificationMigrator.migrateIfNeeded(context)

            assertThat(Prefs.newReviewRemindersEnabled, equalTo(true))
            assertThat(Prefs.legacyNotificationsMigrated, equalTo(true))
            assertThat(
                ReviewRemindersDatabase.getRemindersForScope(ReviewReminderScope.Global).getRemindersList(),
                hasSize(0),
            )
        }

    @Test
    fun `enabled legacy notifications create an equivalent global reminder`() =
        runTest {
            val legacyThreshold = 10
            context.sharedPrefs().edit {
                putString(
                    context.getString(R.string.pref_notifications_minimum_cards_due_key),
                    legacyThreshold.toString(),
                )
            }

            LegacyNotificationMigrator.migrateIfNeeded(context)

            val reminders =
                ReviewRemindersDatabase.getRemindersForScope(ReviewReminderScope.Global).getRemindersList()
            assertThat(reminders, hasSize(1))
            val reminder = reminders.single()
            assertThat(reminder.enabled, equalTo(true))
            assertThat(reminder.cardTriggerThreshold.threshold, equalTo(legacyThreshold))
            assertThat(reminder.scope, equalTo(ReviewReminderScope.Global))
            assertThat(reminder.time.minute, equalTo(0))
            assertThat(Prefs.newReviewRemindersEnabled, equalTo(true))

            // Legacy pref should be disabled after migration.
            val migratedThreshold =
                context
                    .sharedPrefs()
                    .getString(
                        context.getString(R.string.pref_notifications_minimum_cards_due_key),
                        null,
                    )!!
                    .toInt()
            assertThat(migratedThreshold, equalTo(PENDING_NOTIFICATIONS_ONLY))
        }

    @Test
    fun `existing global reminders are not duplicated`() =
        runTest {
            val existing =
                ReviewReminder.createReviewReminder(
                    time = ReviewReminderTime(9, 30),
                    cardTriggerThreshold = ReviewReminderCardTriggerThreshold(1),
                )
            ReviewRemindersDatabase.insertReminder(existing)
            context.sharedPrefs().edit {
                putString(
                    context.getString(R.string.pref_notifications_minimum_cards_due_key),
                    "5",
                )
            }

            LegacyNotificationMigrator.migrateIfNeeded(context)

            val reminders =
                ReviewRemindersDatabase.getRemindersForScope(ReviewReminderScope.Global).getRemindersList()
            assertThat(reminders, hasSize(1))
            assertThat(reminders.single().id, equalTo(existing.id))
        }
}
