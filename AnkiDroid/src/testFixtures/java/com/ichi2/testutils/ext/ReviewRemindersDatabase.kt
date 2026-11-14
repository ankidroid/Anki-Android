// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.testutils.ext

import com.ichi2.anki.reviewreminders.ReviewReminder
import com.ichi2.anki.reviewreminders.ReviewRemindersDatabase

/**
 * Test setup shorthand for [ReviewRemindersDatabase.upsertOrDeleteReminder].
 * Tests of that method itself should call it directly.
 */
suspend fun ReviewRemindersDatabase.insertReminder(reminder: ReviewReminder) =
    upsertOrDeleteReminder(oldReminder = null, newReminder = reminder)

/** @see insertReminder */
suspend fun ReviewRemindersDatabase.deleteReminder(reminder: ReviewReminder) =
    upsertOrDeleteReminder(oldReminder = reminder, newReminder = null)
