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
import android.provider.MediaStore
import androidx.activity.result.contract.ActivityResultContracts
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.SwitchPreference
import com.ichi2.anki.*
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.snackbar.showSnackbar
import com.ichi2.libanki.Utils
import com.ichi2.themes.Theme
import com.ichi2.themes.Themes
import com.ichi2.themes.Themes.systemIsInNightMode
import com.ichi2.themes.Themes.updateCurrentTheme
import timber.log.Timber
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

class AppearanceSettingsFragment : SettingsFragment() {
    private var mBackgroundImage: SwitchPreference? = null
    override val preferenceResource: Int
        get() = R.xml.preferences_appearance
    override val analyticsScreenNameConstant: String
        get() = "prefs.appearance"

    override fun initSubscreen() {
        // Configure background
        mBackgroundImage = requirePreference<SwitchPreference>("deckPickerBackground")
        mBackgroundImage!!.onPreferenceClickListener = Preference.OnPreferenceClickListener {
            if (mBackgroundImage!!.isChecked) {
                try {
                    mBackgroundImageResultLauncher.launch("image/*")
                    mBackgroundImage!!.isChecked = true
                } catch (ex: Exception) {
                    Timber.e("%s", ex.localizedMessage)
                }
            } else {
                mBackgroundImage!!.isChecked = false
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
                    requireActivity().recreate()
                }
            }
        }

        dayThemePref.setOnPreferenceChangeListener { newValue ->
            if (newValue != dayThemePref.value && !systemIsInNightMode(requireContext()) && newValue != Themes.currentTheme.id) {
                requireActivity().recreate()
            }
        }

        nightThemePref.setOnPreferenceChangeListener { newValue ->
            if (newValue != nightThemePref.value && systemIsInNightMode(requireContext()) && newValue != Themes.currentTheme.id) {
                requireActivity().recreate()
            }
        }

        // Default font
        requirePreference<ListPreference>(R.string.pref_default_font_key).apply {
            entries = getCustomFonts("System default")
            entryValues = getCustomFonts("")
        }
        // Browser and Note editor font
        requirePreference<ListPreference>(R.string.pref_browser_and_editor_font_key).apply {
            entries = getCustomFonts("System default")
            entryValues = getCustomFonts("", useFullPath = true)
        }

        // Show estimate time
        // Represents the collection pref "estTime": i.e.
        // whether the buttons should indicate the duration of the interval if we click on them.
        requirePreference<SwitchPreference>(R.string.show_estimates_preference).apply {
            launchCatchingTask { isChecked = withCol { get_config_boolean("estTimes") } }
            setOnPreferenceChangeListener { newETA ->
                launchWithCol { set_config("estTimes", newETA) }
            }
        }
        // Show progress
        // Represents the collection pref "dueCounts": i.e.
        // whether the remaining number of cards should be shown.
        requirePreference<SwitchPreference>(R.string.show_progress_preference).apply {
            launchCatchingTask { isChecked = withCol { get_config_boolean("dueCounts") } }
            setOnPreferenceChangeListener { newDueCountsValue ->
                launchWithCol { set_config("dueCounts", newDueCountsValue) }
            }
        }
    }

    /** Returns a list of the names of the installed custom fonts */
    private fun getCustomFonts(defaultValue: String, useFullPath: Boolean = false): Array<String?> {
        val fonts = Utils.getCustomFonts(requireContext())
        val count = fonts.size
        Timber.d("There are %d custom fonts", count)
        val names = arrayOfNulls<String>(count + 1)
        names[0] = defaultValue
        if (useFullPath) {
            for (index in 1 until count + 1) {
                names[index] = fonts[index - 1].path
                Timber.d("Adding custom font: %s", names[index])
            }
        } else {
            for (index in 1 until count + 1) {
                names[index] = fonts[index - 1].name
                Timber.d("Adding custom font: %s", names[index])
            }
        }
        return names
    }

    private val mBackgroundImageResultLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { selectedImage ->
        if (selectedImage != null) {
            // handling file may result in exception
            try {
                val filePathColumn = arrayOf(MediaStore.MediaColumns.SIZE)
                requireContext().contentResolver.query(selectedImage, filePathColumn, null, null, null).use { cursor ->
                    cursor!!.moveToFirst()
                    // file size in MB
                    val fileLength = cursor.getLong(0) / (1024 * 1024)
                    val currentAnkiDroidDirectory = CollectionHelper.getCurrentAnkiDroidDirectory(requireContext())
                    val imageName = "DeckPickerBackground.png"
                    val destFile = File(currentAnkiDroidDirectory, imageName)
                    // Image size less than 10 MB copied to AnkiDroid directory
                    if (fileLength < 10) {
                        (requireContext().contentResolver.openInputStream(selectedImage) as FileInputStream).channel.use { sourceChannel ->
                            FileOutputStream(destFile).channel.use { destChannel ->
                                destChannel.transferFrom(sourceChannel, 0, sourceChannel.size())
                                showSnackbar(R.string.background_image_applied)
                            }
                        }
                    } else {
                        mBackgroundImage!!.isChecked = false
                        UIUtils.showThemedToast(requireContext(), getString(R.string.image_max_size_allowed, 10), false)
                    }
                }
            } catch (e: OutOfMemoryError) {
                Timber.w(e)
                showSnackbar(getString(R.string.error_selecting_image, e.localizedMessage))
            } catch (e: Exception) {
                Timber.w(e)
                showSnackbar(getString(R.string.error_selecting_image, e.localizedMessage))
            }
        } else {
            mBackgroundImage!!.isChecked = false
            showSnackbar(R.string.no_image_selected)
        }
    }
}
