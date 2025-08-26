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
import com.ichi2.anki.libanki.DeckId
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.hamcrest.CoreMatchers
import org.hamcrest.Description
import org.hamcrest.Matcher
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.anEmptyMap
import org.hamcrest.Matchers.equalTo
import org.hamcrest.TypeSafeMatcher
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.reflect.full.memberProperties

/**
 * Schema migration settings for testing purposes.
 * Consult this as an example of how to save old schemas and define their [ReviewReminderSchema.migrate] methods.
 */
object TestingReviewReminderMigrationSettings {
    /**
     * A sample old review reminder schema. Perhaps this was how the [ReviewReminder] data class was originally implemented.
     * We would like to test the code that checks if review reminders stored on the device adhere to an old, outdated schema.
     * In particular, does the code correctly migrate the serialized data class strings to the updated, current version of [ReviewReminder]?
     */
    @Serializable
    data class ReviewReminderSchemaVersionOne(
        override val id: ReviewReminderId,
        val hour: Int,
        val minute: Int,
        val cardTriggerThreshold: Int,
        val did: DeckId,
        val enabled: Boolean = true,
    ) : ReviewReminderSchema {
        override fun migrate(): ReviewReminderSchema =
            ReviewReminderSchemaVersionTwo(
                id = this.id,
                time = VersionTwoDataClasses.ReviewReminderTime(hour, minute),
                snoozeAmount = 1,
                cardTriggerThreshold = this.cardTriggerThreshold,
                did = this.did,
                enabled = enabled,
            )
    }

    /**
     * Here's an example of how you can handle renamed fields in a data class stored as part of a [ReviewReminder].
     * Otherwise, there's a namespace collision with [ReviewReminderTime].
     *
     * This class will be serialized into "ReviewReminderTime(timeHour=#, timeMinute=#)", which otherwise might conflict
     * with the updated definition of [ReviewReminderTime], which is serialized as "ReviewReminderTime(hour=#, minute=#)".
     * When we read the outdated schema from the disk, we need to tell the deserializer that it is reading a
     * [VersionTwoDataClasses.ReviewReminderTime] rather than a [ReviewReminderTime], even though the names are the same.
     *
     * @see ReviewReminderSchemaVersionTwo
     */
    object VersionTwoDataClasses {
        @Serializable
        data class ReviewReminderTime(
            val timeHour: Int,
            val timeMinute: Int,
        )
    }

    /**
     * Another example of an old review reminder schema. See [ReviewReminderSchemaVersionOne] for more details.
     */
    @Serializable
    data class ReviewReminderSchemaVersionTwo(
        override val id: ReviewReminderId,
        val time: VersionTwoDataClasses.ReviewReminderTime,
        val snoozeAmount: Int,
        val cardTriggerThreshold: Int,
        val did: DeckId,
        val enabled: Boolean = true,
    ) : ReviewReminderSchema {
        override fun migrate(): ReviewReminder =
            ReviewReminder.createReviewReminder(
                time = ReviewReminderTime(this.time.timeHour, this.time.timeMinute),
                cardTriggerThreshold = ReviewReminderCardTriggerThreshold(this.cardTriggerThreshold),
                scope = if (this.did == -1L) ReviewReminderScope.Global else ReviewReminderScope.DeckSpecific(this.did),
                enabled = enabled,
            )
    }
}

/**
 * If tests in this file have failed, it may be because you have updated [ReviewReminder]!
 * Please read the documentation of [ReviewReminder] carefully and ensure you have implemented
 * a proper migration method to the new schema. See [TestingReviewReminderMigrationSettings] for examples.
 */
@RunWith(AndroidJUnit4::class)
class ReviewRemindersDatabaseTest : RobolectricTest() {
    private val did1 = 12345L
    private val did2 = 67890L

    private val dummyDeckSpecificRemindersForDeckOne =
        mapOf(
            ReviewReminderId(0) to
                ReviewReminder.createReviewReminder(
                    ReviewReminderTime(9, 0),
                    ReviewReminderCardTriggerThreshold(5),
                    ReviewReminderScope.DeckSpecific(did1),
                    false,
                ),
            ReviewReminderId(1) to
                ReviewReminder.createReviewReminder(
                    ReviewReminderTime(10, 30),
                    ReviewReminderCardTriggerThreshold(10),
                    ReviewReminderScope.DeckSpecific(did1),
                ),
        )
    private val dummyDeckSpecificRemindersForDeckTwo =
        mapOf(
            ReviewReminderId(2) to
                ReviewReminder.createReviewReminder(
                    ReviewReminderTime(10, 30),
                    ReviewReminderCardTriggerThreshold(10),
                    ReviewReminderScope.DeckSpecific(did2),
                    true,
                ),
            ReviewReminderId(3) to
                ReviewReminder.createReviewReminder(
                    ReviewReminderTime(12, 30),
                    ReviewReminderCardTriggerThreshold(20),
                    ReviewReminderScope.DeckSpecific(did2),
                ),
        )
    private val dummyAppWideReminders =
        mapOf(
            ReviewReminderId(4) to
                ReviewReminder.createReviewReminder(
                    ReviewReminderTime(9, 0),
                    ReviewReminderCardTriggerThreshold(5),
                ),
            ReviewReminderId(5) to
                ReviewReminder.createReviewReminder(
                    ReviewReminderTime(10, 30),
                    ReviewReminderCardTriggerThreshold(10),
                ),
        )

    @Before
    override fun setUp() {
        super.setUp()
        ReviewRemindersDatabase.remindersSharedPrefs.edit { clear() }
    }

    @After
    override fun tearDown() {
        super.tearDown()
        ReviewRemindersDatabase.remindersSharedPrefs.edit { clear() }
    }

    @Test
    fun `getRemindersForDeck should return empty map when no reminders exist`() {
        val reminders = ReviewRemindersDatabase.getRemindersForDeck(did1)
        assertThat(reminders, anEmptyMap())
    }

    @Test
    fun `editRemindersForDeck and getRemindersForDeck should read and write reminders correctly`() {
        ReviewRemindersDatabase.editRemindersForDeck(did1) { dummyDeckSpecificRemindersForDeckOne }
        val storedReminders = ReviewRemindersDatabase.getRemindersForDeck(did1)
        assertThat(storedReminders, equalTo(dummyDeckSpecificRemindersForDeckOne))
    }

    @Test
    fun `getAllDeckSpecificReminders should return empty map when no reminders exist`() {
        val reminders = ReviewRemindersDatabase.getAllDeckSpecificReminders()
        assertThat(reminders, anEmptyMap())
    }

    @Test
    fun `getAllDeckSpecificReminders should return all reminders across decks`() {
        ReviewRemindersDatabase.editRemindersForDeck(did1) { dummyDeckSpecificRemindersForDeckOne }
        ReviewRemindersDatabase.editRemindersForDeck(did2) { dummyDeckSpecificRemindersForDeckTwo }
        val allReminders = ReviewRemindersDatabase.getAllDeckSpecificReminders()
        assertThat(
            allReminders,
            equalTo(dummyDeckSpecificRemindersForDeckOne + dummyDeckSpecificRemindersForDeckTwo),
        )
    }

    @Test
    fun `getAllAppWideReminders should return empty map when no reminders exist`() {
        val reminders = ReviewRemindersDatabase.getAllAppWideReminders()
        assertThat(reminders, anEmptyMap())
    }

    @Test
    fun `editAllAppWideReminders and getAllAppWideReminders should read and write reminders correctly`() {
        ReviewRemindersDatabase.editAllAppWideReminders { dummyAppWideReminders }
        val storedReminders = ReviewRemindersDatabase.getAllAppWideReminders()
        assertThat(storedReminders, equalTo(dummyAppWideReminders))
    }

    @Test(expected = SerializationException::class)
    fun `getRemindersForDeck should throw SerializationException if JSON string for StoredReviewReminder is corrupted`() {
        ReviewRemindersDatabase.remindersSharedPrefs.edit {
            putString(ReviewRemindersDatabase.DECK_SPECIFIC_KEY + did1, "corrupted_and_invalid_json_string")
        }
        ReviewRemindersDatabase.getRemindersForDeck(did1)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `getRemindersForDeck should throw IllegalArgumentException if JSON string is not a StoredReviewReminder`() {
        val randomObject = Pair("not a map of", "review reminders")
        ReviewRemindersDatabase.remindersSharedPrefs.edit {
            putString(ReviewRemindersDatabase.DECK_SPECIFIC_KEY + did1, Json.encodeToString(randomObject))
        }
        ReviewRemindersDatabase.getRemindersForDeck(did1)
    }

    @Test(expected = SerializationException::class)
    fun `getRemindersForDeck should throw SerializationException if JSON string for review reminder is corrupted`() {
        val corruptedStoredReviewReminder =
            ReviewRemindersDatabase.StoredReviewRemindersMap(
                ReviewRemindersDatabase.schemaVersion,
                "corrupted_and_invalid_json_string",
            )
        ReviewRemindersDatabase.remindersSharedPrefs.edit {
            putString(ReviewRemindersDatabase.DECK_SPECIFIC_KEY + did1, Json.encodeToString(corruptedStoredReviewReminder))
        }
        ReviewRemindersDatabase.getRemindersForDeck(did1)
    }

    @Test(expected = SerializationException::class)
    fun `getAllAppWideReminders should throw SerializationException if JSON string for StoredReviewReminder is corrupted`() {
        ReviewRemindersDatabase.remindersSharedPrefs.edit {
            putString(ReviewRemindersDatabase.APP_WIDE_KEY, "corrupted_and_invalid_json_string")
        }
        ReviewRemindersDatabase.getAllAppWideReminders()
    }

    @Test(expected = IllegalArgumentException::class)
    fun `getAllAppWideReminders should throw IllegalArgumentException if JSON string is not a StoredReviewReminder`() {
        val randomObject = Pair("not a map of", "review reminders")
        ReviewRemindersDatabase.remindersSharedPrefs.edit {
            putString(ReviewRemindersDatabase.APP_WIDE_KEY, Json.encodeToString(randomObject))
        }
        ReviewRemindersDatabase.getAllAppWideReminders()
    }

    @Test(expected = SerializationException::class)
    fun `getAllAppWideReminders should throw SerializationException if JSON string for review reminder is corrupted`() {
        val corruptedStoredReviewReminder =
            ReviewRemindersDatabase.StoredReviewRemindersMap(
                ReviewRemindersDatabase.schemaVersion,
                "corrupted_and_invalid_json_string",
            )
        ReviewRemindersDatabase.remindersSharedPrefs.edit {
            putString(ReviewRemindersDatabase.APP_WIDE_KEY, Json.encodeToString(corruptedStoredReviewReminder))
        }
        ReviewRemindersDatabase.getAllAppWideReminders()
    }

    @Test(expected = SerializationException::class)
    fun `getAllDeckSpecificReminders should throw SerializationException if JSON string for StoredReviewReminder is corrupted`() {
        ReviewRemindersDatabase.remindersSharedPrefs.edit {
            putString(ReviewRemindersDatabase.DECK_SPECIFIC_KEY + did1, "corrupted_and_invalid_json_string")
        }
        ReviewRemindersDatabase.getAllDeckSpecificReminders()
    }

    @Test(expected = IllegalArgumentException::class)
    fun `getAllDeckSpecificReminders should throw IllegalArgumentException if JSON string is not a StoredReviewReminder`() {
        val randomObject = Pair("not a map of", "review reminders")
        ReviewRemindersDatabase.remindersSharedPrefs.edit {
            putString(ReviewRemindersDatabase.DECK_SPECIFIC_KEY + did1, Json.encodeToString(randomObject))
        }
        ReviewRemindersDatabase.getAllDeckSpecificReminders()
    }

    @Test(expected = SerializationException::class)
    fun `getAllDeckSpecificReminders should throw SerializationException if JSON string for review reminder is corrupted`() {
        val corruptedStoredReviewReminder =
            ReviewRemindersDatabase.StoredReviewRemindersMap(
                ReviewRemindersDatabase.schemaVersion,
                "corrupted_and_invalid_json_string",
            )
        ReviewRemindersDatabase.remindersSharedPrefs.edit {
            putString(ReviewRemindersDatabase.DECK_SPECIFIC_KEY + did1, Json.encodeToString(corruptedStoredReviewReminder))
        }
        ReviewRemindersDatabase.getAllDeckSpecificReminders()
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

    /**
     * If this test has failed, please ensure the review reminder schema version and old schemas in the review reminder
     * migration chain are set correctly.
     */
    @Test
    fun `current schema version points to ReviewReminder`() {
        assertThat(ReviewRemindersDatabase.schemaVersion.value, equalTo(1))
        assertThat(
            ReviewRemindersDatabase
                .oldReviewReminderSchemasForMigration
                .keys
                .last()
                .value,
            equalTo(1),
        )
        assertThat(
            ReviewRemindersDatabase
                .oldReviewReminderSchemasForMigration
                .values
                .last(),
            equalTo(ReviewReminder::class),
        )
    }

    @Test
    fun `review reminder schema migration works`() {
        // Save existing mocks
        val savedOldReviewReminderSchemasForMigration = ReviewRemindersDatabase.oldReviewReminderSchemasForMigration
        val savedSchemaVersion = ReviewRemindersDatabase.schemaVersion
        // Inject mocks
        ReviewRemindersDatabase.schemaVersion = ReviewReminderSchemaVersion(3)
        ReviewRemindersDatabase.oldReviewReminderSchemasForMigration =
            mapOf(
                ReviewReminderSchemaVersion(1) to TestingReviewReminderMigrationSettings.ReviewReminderSchemaVersionOne::class,
                ReviewReminderSchemaVersion(2) to TestingReviewReminderMigrationSettings.ReviewReminderSchemaVersionTwo::class,
                ReviewReminderSchemaVersion(3) to ReviewReminder::class,
            )
        // To spice things up, some will be version one...
        val versionOneDummyDeckSpecificRemindersForDeckOne =
            mapOf(
                ReviewReminderId(0) to
                    TestingReviewReminderMigrationSettings.ReviewReminderSchemaVersionOne(
                        ReviewReminderId(0),
                        9,
                        0,
                        5,
                        did1,
                        false,
                    ),
                ReviewReminderId(1) to
                    TestingReviewReminderMigrationSettings.ReviewReminderSchemaVersionOne(
                        ReviewReminderId(1),
                        10,
                        30,
                        10,
                        did1,
                    ),
            )
        // ...and some will be version two
        val versionTwoDummyDeckSpecificRemindersForDeckTwo =
            mapOf(
                ReviewReminderId(2) to
                    TestingReviewReminderMigrationSettings.ReviewReminderSchemaVersionTwo(
                        ReviewReminderId(2),
                        TestingReviewReminderMigrationSettings.VersionTwoDataClasses.ReviewReminderTime(10, 30),
                        1,
                        10,
                        did2,
                    ),
                ReviewReminderId(3) to
                    TestingReviewReminderMigrationSettings.ReviewReminderSchemaVersionTwo(
                        ReviewReminderId(3),
                        TestingReviewReminderMigrationSettings.VersionTwoDataClasses.ReviewReminderTime(12, 30),
                        1,
                        20,
                        did2,
                    ),
            )
        val versionOneDummyAppWideReminders =
            mapOf(
                ReviewReminderId(4) to
                    TestingReviewReminderMigrationSettings.ReviewReminderSchemaVersionOne(
                        ReviewReminderId(4),
                        9,
                        0,
                        5,
                        -1L,
                    ),
                ReviewReminderId(5) to
                    TestingReviewReminderMigrationSettings.ReviewReminderSchemaVersionOne(
                        ReviewReminderId(5),
                        10,
                        30,
                        10,
                        -1L,
                    ),
            )

        val packagedDeckOneReminders =
            ReviewRemindersDatabase.StoredReviewRemindersMap(
                ReviewReminderSchemaVersion(1),
                Json.encodeToString(versionOneDummyDeckSpecificRemindersForDeckOne),
            )
        val packagedDeckTwoReminders =
            ReviewRemindersDatabase.StoredReviewRemindersMap(
                ReviewReminderSchemaVersion(2),
                Json.encodeToString(versionTwoDummyDeckSpecificRemindersForDeckTwo),
            )
        val packagedGlobalReminders =
            ReviewRemindersDatabase.StoredReviewRemindersMap(
                ReviewReminderSchemaVersion(1),
                Json.encodeToString(versionOneDummyAppWideReminders),
            )

        ReviewRemindersDatabase.remindersSharedPrefs.edit(commit = true) {
            putString(ReviewRemindersDatabase.DECK_SPECIFIC_KEY + did1, Json.encodeToString(packagedDeckOneReminders))
            putString(ReviewRemindersDatabase.DECK_SPECIFIC_KEY + did2, Json.encodeToString(packagedDeckTwoReminders))
            putString(ReviewRemindersDatabase.APP_WIDE_KEY, Json.encodeToString(packagedGlobalReminders))
        }

        val retrievedDeckOneReminders = ReviewRemindersDatabase.getRemindersForDeck(did1)
        val retrievedDeckTwoReminders = ReviewRemindersDatabase.getRemindersForDeck(did2)
        val retrievedGlobalReminders = ReviewRemindersDatabase.getAllAppWideReminders()

        retrievedDeckOneReminders.forEach { (id, reminder) ->
            assertThat(id, equalTo(reminder.id))
        }
        retrievedDeckTwoReminders.forEach { (id, reminder) ->
            assertThat(id, equalTo(reminder.id))
        }
        retrievedGlobalReminders.forEach { (id, reminder) ->
            assertThat(id, equalTo(reminder.id))
        }

        // We ignore ID because the migration process will generate new review reminders from scratch during the migration; ID is a private, inaccessible property
        assertThat(
            retrievedDeckOneReminders.values,
            containsEqualReviewRemindersInAnyOrderIgnoringId(dummyDeckSpecificRemindersForDeckOne.values),
        )
        assertThat(
            retrievedDeckTwoReminders.values,
            containsEqualReviewRemindersInAnyOrderIgnoringId(dummyDeckSpecificRemindersForDeckTwo.values),
        )
        assertThat(
            retrievedGlobalReminders.values,
            containsEqualReviewRemindersInAnyOrderIgnoringId(dummyAppWideReminders.values),
        )

        // Shared Preferences should not contain any random corrupted keys after or due to the migration process
        // There should be three: two for the specific decks, one for app-wide
        val sharedPrefsSize = ReviewRemindersDatabase.remindersSharedPrefs.all.size
        assertThat(sharedPrefsSize, CoreMatchers.equalTo(3))

        // Reset mocks
        ReviewRemindersDatabase.schemaVersion = savedSchemaVersion
        ReviewRemindersDatabase.oldReviewReminderSchemasForMigration = savedOldReviewReminderSchemasForMigration
    }
}
