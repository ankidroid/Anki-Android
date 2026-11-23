// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.settings

import android.content.res.Resources
import com.github.ivanshafran.sharedpreferencesmock.SPMockBuilder
import com.ichi2.anki.AnkiDroidApp
import com.ichi2.anki.R
import com.ichi2.anki.common.preferences.SharedPreferencesTestHook
import com.ichi2.testutils.JvmPreferencesRule
import com.ichi2.testutils.withPref
import io.mockk.every
import io.mockk.mockk
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PrefsSingletonTest {
    @get:Rule
    val preferences = JvmPreferencesRule()

    @Test
    fun `resource lookups follow the current application resources`() {
        val keyResource = R.string.pref_deck_picker_background_key
        assertEquals(keyResource.toString(), Prefs.key(keyResource))
        val replacement = mockk<Resources>()
        every { replacement.getString(any()) } returns "replacement key"
        AnkiDroidApp.resourcesTestingOverride = replacement

        assertEquals("replacement key", Prefs.key(keyResource))
    }

    @Test
    fun `reads and writes follow the current application preferences`() =
        withPref(Prefs::isBackgroundEnabled, true) {
            assertTrue(Prefs.isBackgroundEnabled)
            val original = Prefs.sharedPrefs
            val originalOverride = SharedPreferencesTestHook.override
            val replacement = SPMockBuilder().createSharedPreferences()
            val key = Prefs.key(R.string.pref_deck_picker_background_key)

            // Replace SharedPreferences to simulate starting another Robolectric test.
            // Verify that Prefs uses the provider instead of keeping the previous instance.
            SharedPreferencesTestHook.override = replacement
            try {
                assertFalse(Prefs.isBackgroundEnabled)
                Prefs.isBackgroundEnabled = true
                assertTrue(replacement.getBoolean(key, false))
                Prefs.isBackgroundEnabled = false
                assertFalse(replacement.getBoolean(key, true))
                assertTrue(original.getBoolean(key, false))
            } finally {
                SharedPreferencesTestHook.override = originalOverride
            }

            assertTrue(Prefs.isBackgroundEnabled)
        }
}
