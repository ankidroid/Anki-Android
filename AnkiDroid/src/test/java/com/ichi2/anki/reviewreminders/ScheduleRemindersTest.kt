/*
 *  Copyright (c) 2025 Eric Li <ericli3690@gmail.com>
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

import androidx.core.content.edit
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.SingleFragmentActivity
import com.ichi2.anki.libanki.DeckId
import com.ichi2.anki.throwOnShowError
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.Description
import org.hamcrest.Matcher
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.containsInAnyOrder
import org.hamcrest.TypeSafeMatcher
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import kotlin.reflect.full.memberProperties

/**
 * If tests in this file have failed, it may be because you have updated [ReviewReminder]!
 * Please read the documentation of [ReviewReminder] carefully and ensure you have implemented
 * a proper migration method to the new schema.
 */
@RunWith(AndroidJUnit4::class)
class ScheduleRemindersTest : RobolectricTest() {
    /**
     * A sample old review reminder schema. Perhaps this was how the [ReviewReminder] data class was originally implemented.
     * We would like to test the code that checks if review reminders stored on the device adhere to an old, outdated schema.
     * In particular, does the code correctly migrate the serialized data class strings to the updated, current version of [ReviewReminder]?
     */
    @Serializable
    private data class OldReviewReminderSchemaTestOne(
        val id: Int,
        val time: ReviewReminderTime,
        val cardTriggerThreshold: Int,
        val did: DeckId,
        val enabled: Boolean,
    ) : OldReviewReminderSchema {
        override fun migrate(): ReviewReminder =
            ReviewReminder.createReviewReminder(
                time = this.time,
                cardTriggerThreshold = ReviewReminderCardTriggerThreshold(this.cardTriggerThreshold),
                scope = if (this.did == -1L) ReviewReminderScope.Global else ReviewReminderScope.DeckSpecific(this.did),
                enabled = this.enabled,
            )
    }

    /**
     * Another example of an old review reminder schema. See [OldReviewReminderSchemaTestOne] for more details.
     */
    @Serializable
    private data class OldReviewReminderSchemaTestTwo(
        val id: Int,
        val time: Int,
        val snoozeAmount: Int,
        val cardTriggerThreshold: Int,
        val did: DeckId,
    ) : OldReviewReminderSchema {
        override fun migrate(): ReviewReminder =
            ReviewReminder.createReviewReminder(
                time = ReviewReminderTime(this.time / 60, this.time % 60),
                cardTriggerThreshold = ReviewReminderCardTriggerThreshold(this.cardTriggerThreshold),
                scope = if (this.did == -1L) ReviewReminderScope.Global else ReviewReminderScope.DeckSpecific(this.did),
                enabled = true,
            )
    }

    /**
     * When review reminders are migrated to the new schema, the reminders' IDs will be recreated from scratch.
     * Thus, validation that our tests succeeded should ignore [ReviewReminder.id].
     * This custom Hamcrest matcher performs this validation using reflection.
     */
    private fun containsEqualReviewRemindersInAnyOrderIgnoringId(expected: Collection<ReviewReminder>): Matcher<Iterable<ReviewReminder>> =
        object : TypeSafeMatcher<Iterable<ReviewReminder>>() {
            override fun describeTo(description: Description) {
                description.appendValue(expected)
            }

            override fun matchesSafely(actual: Iterable<ReviewReminder>): Boolean {
                val expectedSet =
                    expected
                        .map { e ->
                            ReviewReminder::class
                                .memberProperties
                                .filterNot { it.name == "id" }
                                .associateWith { it.get(e) }
                        }.toSet()

                val actualSet =
                    actual
                        .map { a ->
                            ReviewReminder::class
                                .memberProperties
                                .filterNot { it.name == "id" }
                                .associateWith { it.get(a) }
                        }.toSet()

                return expectedSet == actualSet
            }
        }

    private lateinit var database: ReviewRemindersDatabase

    @Before
    override fun setUp() {
        super.setUp()
        database = ReviewRemindersDatabase()
        database.deleteAllReviewReminderSharedPrefs()
        // We must set throwOnShowError to false or else we get unhandled exceptions
        throwOnShowError = false
    }

    @After
    override fun tearDown() {
        super.tearDown()
        database.deleteAllReviewReminderSharedPrefs()
    }

    @Test
    fun `opening fragment with unrecoverable corrupted reminders fails gracefully`() {
        ScheduleReminders.oldReviewReminderSchemasForMigration =
            listOf(
                OldReviewReminderSchemaTestOne::class,
            )

        ReviewRemindersDatabase.remindersSharedPrefs.edit(commit = true) {
            putString(ReviewRemindersDatabase.APP_WIDE_KEY, "corrupted string")
        }

        val sharedPrefsBefore = database.getAllReviewReminderSharedPrefsAsMap()

        // Trigger a database read by loading the reminders onto the screen
        val intent = ScheduleReminders.getIntent(targetContext, ReviewReminderScope.Global)
        Robolectric
            .buildActivity(SingleFragmentActivity::class.java, intent)
            .create()
            .start()
            .resume()
            .get()

        // Should not alter shared prefs, leave any migration side-effects, etc.
        val sharedPrefsAfter = database.getAllReviewReminderSharedPrefsAsMap()
        assertThat(sharedPrefsAfter, equalTo(sharedPrefsBefore))
    }

    @Test
    fun `opening fragment with non-corrupted reminders throws no errors`() {
        ScheduleReminders.oldReviewReminderSchemasForMigration = listOf(OldReviewReminderSchemaTestOne::class)

        val did = 12345L
        val reminderOne =
            ReviewReminder.createReviewReminder(
                ReviewReminderTime(10, 0),
                ReviewReminderCardTriggerThreshold(5),
                ReviewReminderScope.DeckSpecific(did),
                true,
            )
        val reminderTwo =
            ReviewReminder.createReviewReminder(
                ReviewReminderTime(11, 0),
                ReviewReminderCardTriggerThreshold(10),
                ReviewReminderScope.DeckSpecific(did),
                false,
            )

        val reminders = mapOf(ReviewReminderId(0) to reminderOne, ReviewReminderId(1) to reminderTwo)
        val remindersJson = Json.encodeToString(reminders)

        ReviewRemindersDatabase.remindersSharedPrefs.edit(commit = true) {
            putString(ReviewRemindersDatabase.DECK_SPECIFIC_KEY + did, remindersJson)
        }

        // Trigger a database read by loading the reminders onto the screen
        val intent = ScheduleReminders.getIntent(targetContext, ReviewReminderScope.Global)
        Robolectric
            .buildActivity(SingleFragmentActivity::class.java, intent)
            .create()
            .start()
            .resume()
            .get()

        val retrievedRemindersJson =
            ReviewRemindersDatabase.remindersSharedPrefs.getString(ReviewRemindersDatabase.DECK_SPECIFIC_KEY + did, null)
                ?: error("Reminders not found")
        val retrievedReminders = Json.decodeFromString<Map<ReviewReminderId, ReviewReminder>>(retrievedRemindersJson)
        val retrievedRemindersAsList = retrievedReminders.values.toList()

        assertThat(retrievedRemindersAsList, containsInAnyOrder(reminderOne, reminderTwo))
    }

    private fun triggerAndValidateMigrationUsingOldSchemaOne() {
        val did1 = 12345L
        val did2 = 67890L

        val deckSpecificReminderOne =
            OldReviewReminderSchemaTestOne(
                0,
                ReviewReminderTime(10, 0),
                5,
                did1,
                true,
            )
        val deckSpecificReminderTwo =
            OldReviewReminderSchemaTestOne(
                1,
                ReviewReminderTime(11, 0),
                10,
                did1,
                false,
            )
        val deckSpecificReminderThree =
            OldReviewReminderSchemaTestOne(
                2,
                ReviewReminderTime(12, 0),
                15,
                did2,
                true,
            )
        val globalReminder =
            OldReviewReminderSchemaTestOne(
                3,
                ReviewReminderTime(13, 0),
                20,
                -1L,
                false,
            )

        val correctlyMigratedDeckSpecificReminderOne =
            ReviewReminder.createReviewReminder(
                ReviewReminderTime(10, 0),
                ReviewReminderCardTriggerThreshold(5),
                ReviewReminderScope.DeckSpecific(did1),
                true,
            )
        val correctlyMigratedDeckSpecificReminderTwo =
            ReviewReminder.createReviewReminder(
                ReviewReminderTime(11, 0),
                ReviewReminderCardTriggerThreshold(10),
                ReviewReminderScope.DeckSpecific(did1),
                false,
            )
        val correctlyMigratedDeckSpecificReminderThree =
            ReviewReminder.createReviewReminder(
                ReviewReminderTime(12, 0),
                ReviewReminderCardTriggerThreshold(15),
                ReviewReminderScope.DeckSpecific(did2),
                true,
            )
        val correctlyMigratedGlobalReminder =
            ReviewReminder.createReviewReminder(
                ReviewReminderTime(13, 0),
                ReviewReminderCardTriggerThreshold(20),
                ReviewReminderScope.Global,
                false,
            )

        val correctlyMigratedDeckOneRemindersAsList =
            listOf(correctlyMigratedDeckSpecificReminderOne, correctlyMigratedDeckSpecificReminderTwo)
        val correctlyMigratedDeckTwoRemindersAsList = listOf(correctlyMigratedDeckSpecificReminderThree)
        val correctlyMigratedGlobalRemindersAsList = listOf(correctlyMigratedGlobalReminder)

        val deckOneReminders = mapOf(ReviewReminderId(0) to deckSpecificReminderOne, ReviewReminderId(1) to deckSpecificReminderTwo)
        val deckTwoReminders = mapOf(ReviewReminderId(2) to deckSpecificReminderThree)
        val globalReminders = mapOf(ReviewReminderId(3) to globalReminder)

        val deckOneRemindersJson = Json.encodeToString(deckOneReminders)
        val deckTwoRemindersJson = Json.encodeToString(deckTwoReminders)
        val globalRemindersJson = Json.encodeToString(globalReminders)

        ReviewRemindersDatabase.remindersSharedPrefs.edit(commit = true) {
            putString(ReviewRemindersDatabase.DECK_SPECIFIC_KEY + did1, deckOneRemindersJson)
            putString(ReviewRemindersDatabase.DECK_SPECIFIC_KEY + did2, deckTwoRemindersJson)
            putString(ReviewRemindersDatabase.APP_WIDE_KEY, globalRemindersJson)
        }

        // Trigger a database read by loading the reminders onto the screen, which should trigger a migration
        val intent = ScheduleReminders.getIntent(targetContext, ReviewReminderScope.Global)
        Robolectric
            .buildActivity(SingleFragmentActivity::class.java, intent)
            .create()
            .start()
            .resume()
            .get()

        val retrievedDeckOneRemindersJson =
            ReviewRemindersDatabase.remindersSharedPrefs.getString(ReviewRemindersDatabase.DECK_SPECIFIC_KEY + did1, null)
                ?: error("Deck one reminders not found")
        val retrievedDeckTwoRemindersJson =
            ReviewRemindersDatabase.remindersSharedPrefs.getString(ReviewRemindersDatabase.DECK_SPECIFIC_KEY + did2, null)
                ?: error("Deck two reminders not found")
        val retrievedGlobalRemindersJson =
            ReviewRemindersDatabase.remindersSharedPrefs.getString(ReviewRemindersDatabase.APP_WIDE_KEY, null)
                ?: error("Global reminders not found")

        val retrievedDeckOneReminders = Json.decodeFromString<Map<ReviewReminderId, ReviewReminder>>(retrievedDeckOneRemindersJson)
        val retrievedDeckTwoReminders = Json.decodeFromString<Map<ReviewReminderId, ReviewReminder>>(retrievedDeckTwoRemindersJson)
        val retrievedGlobalReminders = Json.decodeFromString<Map<ReviewReminderId, ReviewReminder>>(retrievedGlobalRemindersJson)

        val retrievedDeckOneRemindersAsList = retrievedDeckOneReminders.values.toList()
        val retrievedDeckTwoRemindersAsList = retrievedDeckTwoReminders.values.toList()
        val retrievedGlobalRemindersAsList = retrievedGlobalReminders.values.toList()

        retrievedDeckOneReminders.forEach { (id, reminder) -> assertThat(id, equalTo(reminder.id)) }
        retrievedDeckTwoReminders.forEach { (id, reminder) -> assertThat(id, equalTo(reminder.id)) }
        retrievedGlobalReminders.forEach { (id, reminder) -> assertThat(id, equalTo(reminder.id)) }

        // We ignore ID because the migration process will generate new review reminders from scratch during the migration; ID is a private, inaccessible property
        assertThat(
            retrievedDeckOneRemindersAsList,
            containsEqualReviewRemindersInAnyOrderIgnoringId(correctlyMigratedDeckOneRemindersAsList),
        )
        assertThat(
            retrievedDeckTwoRemindersAsList,
            containsEqualReviewRemindersInAnyOrderIgnoringId(correctlyMigratedDeckTwoRemindersAsList),
        )
        assertThat(
            retrievedGlobalRemindersAsList,
            containsEqualReviewRemindersInAnyOrderIgnoringId(correctlyMigratedGlobalRemindersAsList),
        )

        // Shared Preferences should not contain any random corrupted keys after or due to the migration process
        // There should be three: two for the specific decks, one for app-wide
        val sharedPrefsSize = database.getAllReviewReminderSharedPrefsAsMap().size
        assertThat(sharedPrefsSize, equalTo(3))
    }

    @Test
    fun `single review reminder schema migration works`() {
        ScheduleReminders.oldReviewReminderSchemasForMigration = listOf(OldReviewReminderSchemaTestOne::class)
        triggerAndValidateMigrationUsingOldSchemaOne()
    }

    @Test
    fun `review reminder schema migration with multiple candidates works`() {
        ScheduleReminders.oldReviewReminderSchemasForMigration =
            listOf(
                OldReviewReminderSchemaTestTwo::class,
                OldReviewReminderSchemaTestOne::class,
                OldReviewReminderSchemaTestTwo::class,
            )
        triggerAndValidateMigrationUsingOldSchemaOne()
    }
}
