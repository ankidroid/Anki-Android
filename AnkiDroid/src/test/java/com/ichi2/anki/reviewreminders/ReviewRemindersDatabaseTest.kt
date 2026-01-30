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
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import org.hamcrest.Description
import org.hamcrest.Matcher
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.anEmptyMap
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.hasItem
import org.hamcrest.Matchers.not
import org.hamcrest.TypeSafeMatcher
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.reflect.full.memberProperties

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

    @Test
    fun `editRemindersForDeck should delete SharedPreferences key if no reminders are returned`() {
        ReviewRemindersDatabase.editRemindersForDeck(did1) { dummyDeckSpecificRemindersForDeckOne }
        ReviewRemindersDatabase.editRemindersForDeck(did1) { emptyMap() }
        val attemptedRetrieval = ReviewRemindersDatabase.getRemindersForDeck(did1)
        assertThat(attemptedRetrieval, anEmptyMap())
        assertThat(
            ReviewRemindersDatabase.remindersSharedPrefs.all.keys,
            not(hasItem(ReviewRemindersDatabase.DECK_SPECIFIC_KEY + did1)),
        )
    }

    @Test
    fun `editAllAppWideReminders should delete SharedPreferences key if no reminders are returned`() {
        ReviewRemindersDatabase.editAllAppWideReminders { dummyAppWideReminders }
        ReviewRemindersDatabase.editAllAppWideReminders { emptyMap() }
        val attemptedRetrieval = ReviewRemindersDatabase.getAllAppWideReminders()
        assertThat(attemptedRetrieval, anEmptyMap())
        assertThat(
            ReviewRemindersDatabase.remindersSharedPrefs.all.keys,
            not(hasItem(ReviewRemindersDatabase.APP_WIDE_KEY)),
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

    /**
     * If this test has failed, please ensure the review reminder schema version and old schemas in the review reminder
     * migration chain are set correctly. If you've written a new migration, please also write a new test in this file
     * to prove your migration works!
     *
     * This test is designed to fail and be updated every time the schema is changed
     * to ensure developers know what they are doing and to remind them to write migration tests.
     */
    @Test
    fun `current schema version points to ReviewReminder`() {
        assertThat(ReviewRemindersDatabase.schemaVersion.value, equalTo(2))
        assertThat(
            ReviewRemindersDatabase
                .oldReviewReminderSchemasForMigration
                .keys
                .last()
                .value,
            equalTo(2),
        )
        assertThat(
            ReviewRemindersDatabase
                .oldReviewReminderSchemasForMigration
                .values
                .last(),
            equalTo(ReviewReminder::class),
        )
    }

    /**
     * If this test has failed, you have likely updated [ReviewReminder] without writing a migration!
     * Please write a migration (see [ReviewReminder] and the tests in this file for more information),
     * update the latest schema version (see [ReviewRemindersDatabase]), add a new test in this file
     * to prove your migration works, and update this test to the new latest schema version.
     *
     * To get a raw string, add a log to [ReviewRemindersDatabase.decodeJson] to print out its input string.
     *
     * This test is designed to fail and be updated every time the schema is changed
     * to ensure developers know what they are doing and to remind them to write migration tests.
     */
    @Test
    fun `raw ReviewReminder string can be deserialized without throwing`() {
        val rawString =
            """
            {
              "version":2,
              "remindersMapJson":"{\"0\":{\"id\":0,\"time\":{\"hour\":9,\"minute\":0},\"cardTriggerThreshold\":5,\"scope\":{\"type\":\"com.ichi2.anki.reviewreminders.ReviewReminderScope.DeckSpecific\",\"did\":12345},\"enabled\":false,\"profileID\":\"\",\"onlyNotifyIfNoReviews\":false},\"1\":{\"id\":1,\"time\":{\"hour\":10,\"minute\":30},\"cardTriggerThreshold\":10,\"scope\":{\"type\":\"com.ichi2.anki.reviewreminders.ReviewReminderScope.DeckSpecific\",\"did\":12345},\"enabled\":true,\"profileID\":\"\",\"onlyNotifyIfNoReviews\":false}}"
            }
            """.trimIndent()

        val storedReviewRemindersMap = Json.decodeFromString<ReviewRemindersDatabase.StoredReviewRemindersMap>(rawString)
        val mapSerializer = MapSerializer(ReviewReminderId.serializer(), ReviewReminder.serializer())
        Json.decodeFromString(mapSerializer, storedReviewRemindersMap.remindersMapJson)
    }

    /**
     * If this test has failed, you have likely updated [ReviewReminder]'s schema! Please ensure you've written
     * a migration for this schema change (see [ReviewReminder]) and write a test in this file to prove your migration works.
     *
     * This test is designed to fail and be updated every time the schema is changed
     * to ensure developers know what they are doing and to remind them to write migration tests.
     */
    @Test
    fun `ReviewReminder properties and types are the expected values`() {
        val expectedPropertiesWithTypes =
            mapOf(
                "id" to ReviewReminderId::class,
                "time" to ReviewReminderTime::class,
                "cardTriggerThreshold" to ReviewReminderCardTriggerThreshold::class,
                "scope" to ReviewReminderScope::class,
                "enabled" to Boolean::class,
                "profileID" to String::class,
                "onlyNotifyIfNoReviews" to Boolean::class,
            )

        val actualPropertiesWithTypes =
            ReviewReminder::class
                .memberProperties
                .associate { it.name to it.returnType.classifier }

        assertThat(actualPropertiesWithTypes, equalTo(expectedPropertiesWithTypes))
    }

    /**
     * A single test case for migration testing.
     * @see assertMigrationsWork
     */
    private data class MigrationTestCase(
        val inputVersion: ReviewReminderSchemaVersion,
        val input: ReviewReminderSchema,
        val expectedOutput: ReviewReminder,
    )

    /**
     * Helper function for performing migrations and asserting they work as expected.
     *
     * In order to create a unified helper function for doing this which can accept arbitrary subclasses
     * of [ReviewReminderSchema] and get their serializers at runtime, we need to opt into
     * the internal serialization API. Since this is a test-only function, this should be acceptable.
     */
    @OptIn(InternalSerializationApi::class)
    private fun assertMigrationsWork(vararg testCases: MigrationTestCase) {
        // Group
        val groupedByScope =
            testCases.groupBy {
                when (val scope = it.expectedOutput.scope) {
                    is ReviewReminderScope.DeckSpecific -> scope.did
                    is ReviewReminderScope.Global -> null
                }
            }

        // Write
        groupedByScope.forEach { (did, casesInScope) ->
            // Reading and writing is done per scope, so all test cases in a scope will have the same input version
            if (casesInScope.map { it.inputVersion }.toSet().size != 1) {
                throw IllegalArgumentException("All test cases in a scope must have the same input version and type")
            }
            val version = casesInScope.first().inputVersion
            val inputType = ReviewRemindersDatabase.oldReviewReminderSchemasForMigration[version]!!

            // We need an unchecked runtime cast to allow this helper to operate on arbitrary subclasses of ReviewReminderSchema
            @Suppress("UNCHECKED_CAST")
            val inputSerializer = inputType.serializer() as KSerializer<Any>
            val mapSerializer = MapSerializer(ReviewReminderId.serializer(), inputSerializer)

            val inputMap = casesInScope.associate { it.input.id to it.input }
            val packagedInput =
                ReviewRemindersDatabase.StoredReviewRemindersMap(
                    version,
                    Json.encodeToString(mapSerializer, inputMap),
                )

            val key =
                if (did != null) {
                    ReviewRemindersDatabase.DECK_SPECIFIC_KEY + did
                } else {
                    ReviewRemindersDatabase.APP_WIDE_KEY
                }
            ReviewRemindersDatabase.remindersSharedPrefs.edit(commit = true) {
                putString(key, Json.encodeToString(packagedInput))
            }
        }

        // Read and assert
        groupedByScope.forEach { (did, casesInScope) ->
            val retrievedReminders =
                if (did != null) {
                    ReviewRemindersDatabase.getRemindersForDeck(did)
                } else {
                    ReviewRemindersDatabase.getAllAppWideReminders()
                }

            // We ignore ID because the migration process will generate new review reminders from scratch during the migration
            // ID is a private, inaccessible property
            // Instead, we only check that the ID matches the key in the map; all other properties can be compared normally
            retrievedReminders.forEach { (id, reminder) ->
                assertThat(id, equalTo(reminder.id))
            }
            assertThat(
                retrievedReminders.values,
                containsEqualReviewRemindersInAnyOrderIgnoringId(
                    casesInScope.map { it.expectedOutput },
                ),
            )
        }

        // Shared Preferences should not contain any random corrupted keys after or due to the migration process
        assertThat(
            ReviewRemindersDatabase.remindersSharedPrefs.all.size,
            equalTo(groupedByScope.size),
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
                ReviewReminderSchemaVersion(1) to TestingReviewReminderMigrationSettings.ReviewReminderTestSchemaVersionOne::class,
                ReviewReminderSchemaVersion(2) to TestingReviewReminderMigrationSettings.ReviewReminderTestSchemaVersionTwo::class,
                ReviewReminderSchemaVersion(3) to ReviewReminder::class,
            )

        assertMigrationsWork(
            // To spice things up, some will be version one...
            MigrationTestCase(
                inputVersion = ReviewReminderSchemaVersion(1),
                input =
                    TestingReviewReminderMigrationSettings.ReviewReminderTestSchemaVersionOne(
                        id = ReviewReminderId(0),
                        hour = 9,
                        minute = 0,
                        cardTriggerThreshold = 5,
                        did = did1,
                        enabled = false,
                    ),
                expectedOutput = dummyDeckSpecificRemindersForDeckOne[ReviewReminderId(0)]!!,
            ),
            MigrationTestCase(
                inputVersion = ReviewReminderSchemaVersion(1),
                input =
                    TestingReviewReminderMigrationSettings.ReviewReminderTestSchemaVersionOne(
                        id = ReviewReminderId(1),
                        hour = 10,
                        minute = 30,
                        cardTriggerThreshold = 10,
                        did = did1,
                    ),
                expectedOutput = dummyDeckSpecificRemindersForDeckOne[ReviewReminderId(1)]!!,
            ),
            // ...and some will be version two...
            MigrationTestCase(
                inputVersion = ReviewReminderSchemaVersion(2),
                input =
                    TestingReviewReminderMigrationSettings.ReviewReminderTestSchemaVersionTwo(
                        id = ReviewReminderId(2),
                        time = TestingReviewReminderMigrationSettings.VersionTwoDataClasses.ReviewReminderTime(10, 30),
                        snoozeAmount = 1,
                        cardTriggerThreshold = 10,
                        did = did2,
                        enabled = true,
                    ),
                expectedOutput = dummyDeckSpecificRemindersForDeckTwo[ReviewReminderId(2)]!!,
            ),
            MigrationTestCase(
                inputVersion = ReviewReminderSchemaVersion(2),
                input =
                    TestingReviewReminderMigrationSettings.ReviewReminderTestSchemaVersionTwo(
                        id = ReviewReminderId(3),
                        time = TestingReviewReminderMigrationSettings.VersionTwoDataClasses.ReviewReminderTime(12, 30),
                        snoozeAmount = 1,
                        cardTriggerThreshold = 20,
                        did = did2,
                    ),
                expectedOutput = dummyDeckSpecificRemindersForDeckTwo[ReviewReminderId(3)]!!,
            ),
            // ...and some will be app-wide for good measure
            MigrationTestCase(
                inputVersion = ReviewReminderSchemaVersion(1),
                input =
                    TestingReviewReminderMigrationSettings.ReviewReminderTestSchemaVersionOne(
                        id = ReviewReminderId(4),
                        hour = 9,
                        minute = 0,
                        cardTriggerThreshold = 5,
                        did = -1L,
                    ),
                expectedOutput = dummyAppWideReminders[ReviewReminderId(4)]!!,
            ),
            MigrationTestCase(
                inputVersion = ReviewReminderSchemaVersion(1),
                input =
                    TestingReviewReminderMigrationSettings.ReviewReminderTestSchemaVersionOne(
                        id = ReviewReminderId(5),
                        hour = 10,
                        minute = 30,
                        cardTriggerThreshold = 10,
                        did = -1L,
                    ),
                expectedOutput = dummyAppWideReminders[ReviewReminderId(5)]!!,
            ),
        )

        // Reset mocks
        ReviewRemindersDatabase.schemaVersion = savedSchemaVersion
        ReviewRemindersDatabase.oldReviewReminderSchemasForMigration = savedOldReviewReminderSchemasForMigration
    }

    @Test
    fun `review reminder v1 to v2 migration works`() {
        assertMigrationsWork(
            MigrationTestCase(
                inputVersion = ReviewReminderSchemaVersion(1),
                input =
                    ReviewReminderSchemaV1(
                        id = ReviewReminderId(0),
                        time = ReviewReminderTime(9, 0),
                        cardTriggerThreshold = ReviewReminderCardTriggerThreshold(5),
                        scope = ReviewReminderScope.DeckSpecific(did1),
                        enabled = true,
                        profileID = "",
                    ),
                expectedOutput =
                    ReviewReminder.createReviewReminder(
                        time = ReviewReminderTime(9, 0),
                        cardTriggerThreshold = ReviewReminderCardTriggerThreshold(5),
                        scope = ReviewReminderScope.DeckSpecific(did1),
                        enabled = true,
                        profileID = "",
                        onlyNotifyIfNoReviews = false,
                    ),
            ),
            MigrationTestCase(
                inputVersion = ReviewReminderSchemaVersion(1),
                input =
                    ReviewReminderSchemaV1(
                        id = ReviewReminderId(1),
                        time = ReviewReminderTime(10, 30),
                        cardTriggerThreshold = ReviewReminderCardTriggerThreshold(10),
                        scope = ReviewReminderScope.Global,
                        enabled = false,
                        profileID = "",
                        onlyNotifyIfNoReviews = true,
                    ),
                expectedOutput =
                    ReviewReminder.createReviewReminder(
                        time = ReviewReminderTime(10, 30),
                        cardTriggerThreshold = ReviewReminderCardTriggerThreshold(10),
                        scope = ReviewReminderScope.Global,
                        enabled = false,
                        profileID = "",
                        onlyNotifyIfNoReviews = true,
                    ),
            ),
        )
    }
}
