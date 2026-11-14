// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.preferences

import android.view.WindowManager
import android.widget.EditText
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import com.ichi2.anki.R
import com.ichi2.anki.ScreenshotTest
import com.ichi2.anki.preferences.BackupLimitsSettingsFragment
import com.ichi2.anki.preferences.PreferencesActivity
import com.ichi2.anki.preferences.PreferencesFragment
import com.ichi2.anki.preferences.requirePreference
import com.ichi2.utils.negativeButton
import org.junit.Rule
import org.junit.Test
import org.robolectric.RuntimeEnvironment
import org.robolectric.shadows.ShadowDialog

/** The same tests run against the original preferences and the extracted picker. */
class BackupPickerScreenshotTest : ScreenshotTest() {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    @Test
    fun backupPickers() =
        withBackups { fragment ->
            fragment.withPicker(R.string.pref_minutes_between_automatic_backups_key, 30) { dialog ->
                capturePicker("interval", dialog)
                val input = dialog.findViewById<EditText>(android.R.id.edit)!!
                for ((name, text) in listOf("minimum" to "5", "maximum" to "99999", "below_minimum" to "4", "empty" to "")) {
                    input.setText(text)
                    advanceRobolectricLooper()
                    capturePicker("interval_$name", dialog)
                }
            }
            fragment.captureOtherPickers("")
        }

    @Test
    fun largeText() {
        RuntimeEnvironment.setFontScale(2f)
        captureAllPickers("_large_text")
    }

    @Test
    fun landscape() {
        RuntimeEnvironment.setQualifiers("+land")
        captureAllPickers("_landscape")
    }

    private fun capturePicker(
        name: String,
        dialog: AlertDialog,
    ) {
        composeRule.waitForIdle()
        val window = dialog.window!!
        // Robolectric can retain extra window height when an error caption is added.
        // Use the measured panel, as Android does for a dialog with wrap-content height.
        window.setLayout(window.attributes.width, WindowManager.LayoutParams.WRAP_CONTENT)
        advanceRobolectricLooper()
        val decor = window.decorView
        val panel = decor.findViewById<android.view.View>(androidx.appcompat.R.id.parentPanel)
        panel.measure(
            android.view.View.MeasureSpec
                .makeMeasureSpec(panel.width, android.view.View.MeasureSpec.EXACTLY),
            android.view.View.MeasureSpec
                .makeMeasureSpec(0, android.view.View.MeasureSpec.UNSPECIFIED),
        )
        val height = panel.measuredHeight + decor.paddingTop + decor.paddingBottom
        window.setLayout(window.attributes.width, height)
        advanceRobolectricLooper()
        captureScreen(name)
    }

    private fun captureAllPickers(suffix: String) =
        withBackups { fragment ->
            fragment.withPicker(
                R.string.pref_minutes_between_automatic_backups_key,
                30,
            ) { dialog -> capturePicker("interval$suffix", dialog) }
            fragment.captureOtherPickers(suffix)
        }

    private fun BackupLimitsSettingsFragment.captureOtherPickers(suffix: String) {
        for ((key, name) in listOf(
            R.string.pref_daily_backups_to_keep_key to "daily",
            R.string.pref_weekly_backups_to_keep_key to "weekly",
            R.string.pref_monthly_backups_to_keep_key to "monthly",
        )) {
            withPicker(key, 12) { dialog -> capturePicker("$name$suffix", dialog) }
        }
    }

    private fun withBackups(block: (BackupLimitsSettingsFragment) -> Unit) {
        val intent = PreferencesActivity.getIntent(targetContext, BackupLimitsSettingsFragment::class)
        ActivityScenario.launch<PreferencesActivity>(intent).use { scenario ->
            scenario.onActivity { activity ->
                val parent = activity.fragment as PreferencesFragment
                val fragment = parent.childFragmentManager.findFragmentById(R.id.settings_container) as BackupLimitsSettingsFragment
                block(fragment)
            }
        }
    }

    private fun BackupLimitsSettingsFragment.withPicker(
        @StringRes key: Int,
        value: Int,
        block: (AlertDialog) -> Unit,
    ) {
        val preference = requirePreference<IncrementerNumberRangePreferenceCompat>(key)
        preference.setValue(value)
        onDisplayPreferenceDialog(preference)
        advanceRobolectricLooper()
        val dialog = ShadowDialog.getLatestDialog() as AlertDialog
        composeRule.waitForIdle()
        block(dialog)
        dialog.negativeButton.performClick()
        advanceRobolectricLooper()
    }
}
