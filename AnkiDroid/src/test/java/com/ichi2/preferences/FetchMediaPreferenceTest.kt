// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.preferences

import android.content.DialogInterface
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.CommonString
import com.ichi2.anki.R
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.preferences.PreferencesActivity
import com.ichi2.anki.preferences.PreferencesFragment
import com.ichi2.anki.preferences.SyncSettingsFragment
import com.ichi2.anki.preferences.requirePreference
import com.ichi2.anki.settings.Prefs
import com.ichi2.anki.settings.enums.ShouldFetchMedia
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FetchMediaPreferenceTest : RobolectricTest() {
    @Test
    fun `the warning switch only shows under Never`() =
        withFetchMediaDialog {
            assertThat("hidden while media is fetched", binding.mediaSyncOffPanel.isVisible, equalTo(false))

            binding.fetchMediaNever.performClick()
            assertThat("shown once Never is picked", binding.mediaSyncOffPanel.isVisible, equalTo(true))
            assertThat("picking an option keeps the dialog open", requireDialog().isShowing, equalTo(true))

            binding.fetchMediaAlways.performClick()
            assertThat("hidden again once media is fetched", binding.mediaSyncOffPanel.isVisible, equalTo(false))
        }

    @Test
    fun `the warning switch shows when the dialog opens with media sync off`() {
        Prefs.putEnum(R.string.sync_fetch_media_key, ShouldFetchMedia.NEVER)
        withFetchMediaDialog {
            assertThat(binding.fetchMediaNever.isChecked, equalTo(true))
            assertThat(binding.mediaSyncOffPanel.isVisible, equalTo(true))
        }
    }

    @Test
    fun `OK saves the choice and the warning switch`() =
        withFetchMediaDialog {
            binding.fetchMediaNever.performClick()
            binding.missingMediaWarningSwitch.isChecked = false
            clickButton(DialogInterface.BUTTON_POSITIVE)

            assertThat(Prefs.shouldFetchMedia, equalTo(ShouldFetchMedia.NEVER))
            assertThat(Prefs.showMissingMediaWarnings, equalTo(false))
            assertThat(preference.summary, equalTo(targetContext.getString(CommonString.sync_media_never)))
        }

    @Test
    fun `Cancel keeps the previous settings`() =
        withFetchMediaDialog {
            binding.fetchMediaNever.performClick()
            binding.missingMediaWarningSwitch.isChecked = false
            clickButton(DialogInterface.BUTTON_NEGATIVE)

            assertThat(Prefs.shouldFetchMedia, equalTo(ShouldFetchMedia.ALWAYS))
            assertThat(Prefs.warnAboutMissingMedia, equalTo(true))
        }

    @Test
    fun `unsaved choices survive recreation`() {
        launchSyncSettings().use { scenario ->
            scenario.openFetchMediaDialog()
            scenario.onFetchMediaDialog {
                binding.fetchMediaNever.performClick()
                binding.missingMediaWarningSwitch.isChecked = false
            }

            scenario.recreate()
            advanceRobolectricLooper()

            scenario.onFetchMediaDialog {
                assertThat(binding.fetchMediaNever.isChecked, equalTo(true))
                assertThat(binding.mediaSyncOffPanel.isVisible, equalTo(true))
                assertThat(binding.missingMediaWarningSwitch.isChecked, equalTo(false))
            }
        }
    }

    private fun FetchMediaPreferenceDialogFragment.clickButton(which: Int) {
        (requireDialog() as AlertDialog).getButton(which).performClick()
        advanceRobolectricLooper()
    }

    private fun withFetchMediaDialog(block: FetchMediaPreferenceDialogFragment.() -> Unit) {
        launchSyncSettings().use { scenario ->
            scenario.openFetchMediaDialog()
            scenario.onFetchMediaDialog(block)
        }
    }

    private fun launchSyncSettings(): ActivityScenario<PreferencesActivity> =
        ActivityScenario.launch(PreferencesActivity.getIntent(targetContext, SyncSettingsFragment::class))

    private fun ActivityScenario<PreferencesActivity>.openFetchMediaDialog() {
        onActivity { it.syncSettings.requirePreference<FetchMediaPreference>(R.string.sync_fetch_media_key).performClick() }
        advanceRobolectricLooper()
    }

    private fun ActivityScenario<PreferencesActivity>.onFetchMediaDialog(block: FetchMediaPreferenceDialogFragment.() -> Unit) {
        onActivity { activity ->
            val dialogFragment =
                activity.syncSettings.parentFragmentManager.fragments
                    .filterIsInstance<FetchMediaPreferenceDialogFragment>()
                    .single()
            dialogFragment.block()
        }
    }

    private val PreferencesActivity.syncSettings: SyncSettingsFragment
        get() {
            val preferencesFragment = fragment as PreferencesFragment
            return preferencesFragment.childFragmentManager.findFragmentById(R.id.settings_container) as SyncSettingsFragment
        }
}
