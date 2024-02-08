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
import androidx.core.app.ActivityCompat
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.SwitchPreferenceCompat
import anki.config.ConfigKey
import com.ichi2.anki.*
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.deckpicker.BackgroundImage
import com.ichi2.anki.deckpicker.BackgroundImage.FileSizeResult
import com.ichi2.anki.snackbar.showSnackbar
import com.ichi2.themes.Theme
import com.ichi2.themes.Themes
import com.ichi2.themes.Themes.systemIsInNightMode
import com.ichi2.themes.Themes.updateCurrentTheme
import timber.log.Timber
import java.io.File

class AppearanceSettingsFragment : SettingsFragment() {
    private var backgroundImage: SwitchPreferenceCompat? = null
    override val preferenceResource: Int
        get() = R.xml.preferences_appearance
    override val analyticsScreenNameConstant: String
        get() = "prefs.appearance"

    override fun initSubscreen() {
        // Configure background
        backgroundImage = requirePreference<SwitchPreferenceCompat>("deckPickerBackground")
        backgroundImage!!.onPreferenceClickListener = Preference.OnPreferenceClickListener {
            if (backgroundImage!!.isChecked) {
                try {
                    backgroundImageResultLauncher.launch("image/*")
                    backgroundImage!!.isChecked = true
                } catch (ex: Exception) {
                    Timber.e("%s", ex.localizedMessage)
                }
            } else {
                backgroundImage!!.isChecked = false
                val currentAnkiDroidDirectory = CollectionHelper.getCurrentAnkiDroidDirectory(requireContext())
                val imgFile = File(currentAnkiDroidDirectory, "DeckPickerBackground.png")
                if (imgFile.exists()) {
                    if (imgFile.delete()) {
                        showSnackbar(R.string.background_image_removed)
                    } else {
                        showSnackbar(R.string.error_deleting_image)
                    }
                } else {
                    showSnackbar(R.string.background_image_removed)
                }
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

    private val backgroundImageResultLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { selectedImage ->
        if (selectedImage == null) {
            backgroundImage!!.isChecked = false
            showSnackbar(R.string.no_image_selected)
            return@registerForActivityResult
        }
        // handling file may result in exception
        try {
            when (val sizeResult = BackgroundImage.validateBackgroundImageFileSize(selectedImage)) {
                is FileSizeResult.FileTooLarge -> {
                    backgroundImage!!.isChecked = false
                    UIUtils.showThemedToast(requireContext(), getString(R.string.image_max_size_allowed, sizeResult.maxMB), false)
                }
                is FileSizeResult.UncompressedBitmapTooLarge -> {
                    backgroundImage!!.isChecked = false
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
