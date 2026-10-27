// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.settings

import androidx.core.content.edit
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.EmptyApplicationCategory
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.common.preferences.sharedPrefs
import com.ichi2.testutils.EmptyApplication
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
@Config(application = EmptyApplication::class)
@Category(EmptyApplicationCategory::class)
class NotificationPreferencesTest : RobolectricTest() {
    private val preferences = targetContext.sharedPrefs()
    private val notifications = PrefsRepository(targetContext)

    @Test
    fun `existing reminder preference is read and updated without changing its key`() {
        preferences.edit { putBoolean("newReviewReminders", true) }
        assertTrue(notifications.newReviewRemindersEnabled)
        notifications.newReviewRemindersEnabled = false
        assertFalse(preferences.getBoolean("newReviewReminders", true))
    }

    @Test
    fun `both notification systems default to disabled`() {
        preferences.edit { clear() }
        assertFalse(notifications.newReviewRemindersEnabled)
        assertFalse(notifications.legacyNotificationsEnabled)
    }

    @Test
    fun `legacy notification threshold retains its disabled boundary`() {
        preferences.edit { putString("minimumCardsDueForNotification", "999999") }
        assertTrue(notifications.legacyNotificationsEnabled)
        preferences.edit { putString("minimumCardsDueForNotification", "1000000") }
        assertFalse(notifications.legacyNotificationsEnabled)
    }
}
