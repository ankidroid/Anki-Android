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

import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * If this test has failed, please ensure the review reminder schema version and old schemas in the review reminder
 * migration chain are set correctly.
 */
@RunWith(JUnit4::class)
class ReviewReminderMigrationSettingsTest {
    @Test
    fun `current schema version points to ReviewReminder`() {
        assertThat(ReviewReminderMigrationSettings.SCHEMA_VERSION.value, equalTo(1))
        assertThat(
            ReviewReminderMigrationSettings.oldReviewReminderSchemasForMigration.keys
                .last()
                .value,
            equalTo(1),
        )
        assertThat(ReviewReminderMigrationSettings.oldReviewReminderSchemasForMigration.values.last(), equalTo(ReviewReminder::class))
    }
}
