// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.preferences

import android.Manifest
import androidx.preference.SwitchPreferenceCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.R
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.common.destinations.DeferredNavigation
import com.ichi2.anki.common.destinations.PreferencesDestination
import com.ichi2.anki.common.destinations.launchActivity
import com.ichi2.anki.settings.PrefsRepository
import com.ichi2.testutils.denyPermissions
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.nullValue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

@RunWith(AndroidJUnit4::class)
class AdvancedSettingsFragmentTest : RobolectricTest() {
    // not `Prefs` - the singleton is not cleared
    private val prefs get() = PrefsRepository(targetContext)

    @Test
    fun `'allow templates to record audio' switch updates the pref read by the card viewers`() {
        grantRecordAudioPermission()
        prefs.allowTemplatesToRecordAudio = false

        withAdvancedSettings {
            allowTemplatesToRecordAudioSwitch.performClick()
            assertThat("enabled after checking the switch", prefs.allowTemplatesToRecordAudio, equalTo(true))

            allowTemplatesToRecordAudioSwitch.performClick()
            assertThat("disabled after unchecking the switch", prefs.allowTemplatesToRecordAudio, equalTo(false))
        }
    }

    @Test
    fun `'allow templates to record audio' switch does not request the permission when toggled off`() {
        grantRecordAudioPermission()
        prefs.allowTemplatesToRecordAudio = true

        withAdvancedSettings {
            // the switch is forced off when the screen opens without the permission,
            // so an opt-out without it requires a revocation while the screen is open
            denyPermissions(Manifest.permission.RECORD_AUDIO)

            allowTemplatesToRecordAudioSwitch.performClick()

            assertThat("opt-out is persisted", prefs.allowTemplatesToRecordAudio, equalTo(false))
            assertThat(
                "no permission request is launched",
                shadowOf(requireActivity()).lastRequestedPermission,
                nullValue(),
            )
        }
    }

    @Test
    fun `'allow templates to record audio' switch is unchecked if the microphone permission is missing`() {
        prefs.allowTemplatesToRecordAudio = true

        withAdvancedSettings {
            assertThat("switch is unchecked", allowTemplatesToRecordAudioSwitch.isChecked, equalTo(false))
        }
    }
}

private val AdvancedSettingsFragment.allowTemplatesToRecordAudioSwitch
    get() = requirePreference<SwitchPreferenceCompat>(R.string.pref_allow_template_audio_recording)

/** Runs [block] on the 'Advanced' settings screen */
context(_: DeferredNavigation)
private fun withAdvancedSettings(block: AdvancedSettingsFragment.() -> Unit) = withPreferences(PreferencesDestination.Advanced, block)

/**
 * Opens the settings screen at [destination] and runs [block] on the fragment which displays it.
 */
context(_: DeferredNavigation)
private inline fun <reified F : SettingsFragment> withPreferences(
    destination: PreferencesDestination,
    crossinline block: F.() -> Unit,
) {
    launchActivity<PreferencesActivity>(destination).use { scenario ->
        scenario.onActivity { activity ->
            val preferencesFragment = activity.fragment as PreferencesFragment
            val settingsFragment = preferencesFragment.childFragmentManager.findFragmentByTag(F::class.java.name) as F
            settingsFragment.block()
        }
    }
}
