// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.settings

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.ivanshafran.sharedpreferencesmock.SPMockBuilder
import com.ichi2.anki.AnkiDroidApp
import com.ichi2.anki.R
import com.ichi2.anki.RobolectricTest
import com.ichi2.testutils.withBooleanPreference
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class PrefsSingletonTest : RobolectricTest() {
    @Test
    fun `reads and writes follow the current application preferences`() =
        withBooleanPreference(R.string.pref_deck_picker_background_key, true) {
            assertTrue(Prefs.isBackgroundEnabled)
            val original = Prefs.sharedPrefs
            val originalOverride = AnkiDroidApp.sharedPreferencesTestingOverride
            val replacement = SPMockBuilder().createSharedPreferences()
            val key = targetContext.getString(R.string.pref_deck_picker_background_key)

            // Replace SharedPreferences to simulate starting another Robolectric test.
            // Verify that Prefs uses the provider instead of keeping the previous instance.
            AnkiDroidApp.sharedPreferencesTestingOverride = replacement
            try {
                assertFalse(Prefs.isBackgroundEnabled)
                Prefs.isBackgroundEnabled = true
                assertTrue(replacement.getBoolean(key, false))
                Prefs.isBackgroundEnabled = false
                assertFalse(replacement.getBoolean(key, true))
                assertTrue(original.getBoolean(key, false))
            } finally {
                AnkiDroidApp.sharedPreferencesTestingOverride = originalOverride
            }

            assertTrue(Prefs.isBackgroundEnabled)
        }
}
