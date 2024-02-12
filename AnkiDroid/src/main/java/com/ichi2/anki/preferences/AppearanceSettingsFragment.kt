/*
 *  Copyright (c) 2022 Brayan Oliveira <brayandso.dev@gmail.com>
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
package com.ichi2.anki.preferences

import android.os.Build
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.SwitchPreferenceCompat
import anki.config.ConfigKey
import com.ichi2.anki.CollectionManager
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.R
import com.ichi2.anki.UIUtils
import com.ichi2.anki.deckpicker.BackgroundImage
import com.ichi2.anki.deckpicker.BackgroundImage.FileSizeResult
import com.ichi2.anki.launchCatchingTask
import com.ichi2.anki.snackbar.showSnackbar
import com.ichi2.themes.Theme
import com.ichi2.themes.Themes
import com.ichi2.themes.Themes.systemIsInNightMode
import com.ichi2.themes.Themes.updateCurrentTheme
import com.ichi2.utils.negativeButton
import com.ichi2.utils.positiveButton
import com.ichi2.utils.show
import com.ichi2.utils.title
import timber.log.Timber

class AppearanceSettingsFragment : SettingsFragment() {
    private var backgroundImage: Preference? = null
    override val preferenceResource: Int
        get() = R.xml.preferences_appearance
    override val analyticsScreenNameConstant: String
        get() = "prefs.appearance"

    override fun initSubscreen() {
        // Configure background
        backgroundImage = requirePreference<Preference>("deckPickerBackground")
        backgroundImage!!.onPreferenceClickListener = Preference.OnPreferenceClickListener {
            try {
                backgroundImageResultLauncher.launch("image/*")
            } catch (ex: Exception) {
                Timber.w(ex)
            }
            true
        }

        val appThemePref = requirePreference<ListPreference>(R.string.app_theme_key)
        val dayThemePref = requirePreference<ListPreference>(R.string.day_theme_key)
        val nightThemePref = requirePreference<ListPreference>(R.string.night_theme_key)
        val themeIsFollowSystem = appThemePref.value == Themes.FOLLOW_SYSTEM_MODE

        // Remove follow system options in android versions which do not have system dark mode
        // When minSdk reaches 29, the only necessary change is to remove this if-block
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            dayThemePref.isVisible = false
            nightThemePref.isVisible = false

            // Drop "Follow system" option (the first one)
            appThemePref.entries = resources.getStringArray(R.array.app_theme_labels).drop(1).toTypedArray()
            appThemePref.entryValues = resources.getStringArray(R.array.app_theme_values).drop(1).toTypedArray()
            if (themeIsFollowSystem) {
                appThemePref.value = Theme.fallback.id
            }
        }
        dayThemePref.isEnabled = themeIsFollowSystem
        nightThemePref.isEnabled = themeIsFollowSystem

        appThemePref.setOnPreferenceChangeListener { newValue ->
            val selectedThemeIsFollowSystem = newValue == Themes.FOLLOW_SYSTEM_MODE
            dayThemePref.isEnabled = selectedThemeIsFollowSystem
            nightThemePref.isEnabled = selectedThemeIsFollowSystem

            // Only restart if theme has changed
            if (newValue != appThemePref.value) {
                val previousThemeId = Themes.currentTheme.id
                appThemePref.value = newValue.toString()
                updateCurrentTheme(requireContext())

                if (previousThemeId != Themes.currentTheme.id) {
                    ActivityCompat.recreate(requireActivity())
                }
            }
        }

        dayThemePref.setOnPreferenceChangeListener { newValue ->
            if (newValue != dayThemePref.value && !systemIsInNightMode(requireContext()) && newValue != Themes.currentTheme.id) {
                ActivityCompat.recreate(requireActivity())
            }
        }

        nightThemePref.setOnPreferenceChangeListener { newValue ->
            if (newValue != nightThemePref.value && systemIsInNightMode(requireContext()) && newValue != Themes.currentTheme.id) {
                ActivityCompat.recreate(requireActivity())
            }
        }

        // Show estimate time
        // Represents the collection pref "estTime": i.e.
        // whether the buttons should indicate the duration of the interval if we click on them.
        requirePreference<SwitchPreferenceCompat>(R.string.show_estimates_preference).apply {
            launchCatchingTask { isChecked = withCol { config.get("estTimes") ?: true } }
            setOnPreferenceChangeListener { _, newETA ->
                val newETABool = newETA as? Boolean ?: return@setOnPreferenceChangeListener false
                launchCatchingTask { withCol { config.set("estTimes", newETABool) } }
                true
            }
        }
        // Show progress
        // Represents the collection pref "dueCounts": i.e.
        // whether the remaining number of cards should be shown.
        requirePreference<SwitchPreferenceCompat>(R.string.show_progress_preference).apply {
            launchCatchingTask { isChecked = withCol { config.get("dueCounts") ?: true } }
            setOnPreferenceChangeListener { _, newDueCountsValue ->
                val newDueCountsValueBool = newDueCountsValue as? Boolean ?: return@setOnPreferenceChangeListener false
                launchCatchingTask { withCol { config.set("dueCounts", newDueCountsValueBool) } }
                true
            }
        }

        // Show play buttons on cards with audio
        // Note: Stored inverted in the collection as HIDE_AUDIO_PLAY_BUTTONS
        requirePreference<SwitchPreferenceCompat>(R.string.show_audio_play_buttons_key).apply {
            title = CollectionManager.TR.preferencesShowPlayButtonsOnCardsWith()
            launchCatchingTask { isChecked = withCol { !config.getBool(ConfigKey.Bool.HIDE_AUDIO_PLAY_BUTTONS) } }
            setOnPreferenceChangeListener { newValue ->
                launchCatchingTask { withCol { config.setBool(ConfigKey.Bool.HIDE_AUDIO_PLAY_BUTTONS, !(newValue as Boolean)) } }
            }
        }
    }

    private fun showRemoveBackgroundImageDialog() {
        AlertDialog.Builder(requireContext()).show {
            title(R.string.remove_background_image)
            positiveButton(R.string.dialog_remove) {
                if (BackgroundImage.remove(requireContext())) {
                    showSnackbar(R.string.background_image_removed)
                } else {
                    showSnackbar(R.string.error_deleting_image)
                }
            }
            negativeButton(R.string.dialog_keep)
        }
    }

    private val backgroundImageResultLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { selectedImage ->
        if (selectedImage == null) {
            if (BackgroundImage.shouldBeShown(requireContext())) {
                showRemoveBackgroundImageDialog()
            } else {
                showSnackbar(R.string.no_image_selected)
            }
            return@registerForActivityResult
        }
        // handling file may result in exception
        try {
            when (val sizeResult = BackgroundImage.validateBackgroundImageFileSize(selectedImage)) {
                is FileSizeResult.FileTooLarge -> {
                    UIUtils.showThemedToast(requireContext(), getString(R.string.image_max_size_allowed, sizeResult.maxMB), false)
                }
                is FileSizeResult.UncompressedBitmapTooLarge -> {
                    UIUtils.showThemedToast(requireContext(), getString(R.string.image_dimensions_too_large, sizeResult.width, sizeResult.height), false)
                }
                is FileSizeResult.OK -> {
                    BackgroundImage.import(selectedImage)
                }
            }
        } catch (e: OutOfMemoryError) {
            Timber.w(e)
            showSnackbar(getString(R.string.error_selecting_image, e.localizedMessage))
        } catch (e: Exception) {
            Timber.w(e)
            showSnackbar(getString(R.string.error_selecting_image, e.localizedMessage))
        }
    }
}
