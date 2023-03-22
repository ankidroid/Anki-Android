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

import android.content.Context
import android.content.Intent
import androidx.annotation.VisibleForTesting
import androidx.appcompat.app.AlertDialog
import androidx.core.content.edit
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import com.ichi2.anki.AnkiDroidApp
import com.ichi2.anki.R
import com.ichi2.utils.*

class CustomButtonsSettingsFragment : SettingsFragment() {
    override val preferenceResource: Int
        get() = R.xml.preferences_custom_buttons
    override val analyticsScreenNameConstant: String
        get() = "prefs.custom_buttons"

    override fun initSubscreen() {
        // Reset toolbar button customizations
        val resetCustomButtons = requirePreference<Preference>("reset_custom_buttons")
        resetCustomButtons.onPreferenceClickListener = Preference.OnPreferenceClickListener {
            AlertDialog.Builder(requireContext()).show {
                title(R.string.reset_settings_to_default)
                    .message(R.string.reset_confirmation_text)
                    .positiveButton(R.string.reset) {
                        // Reset the settings to default
                        AnkiDroidApp.getSharedPrefs(requireContext()).edit {
                            remove("customButtonUndo")
                            remove("customButtonScheduleCard")
                            remove("customButtonEditCard")
                            remove("customButtonTags")
                            remove("customButtonAddCard")
                            remove("customButtonReplay")
                            remove("customButtonCardInfo")
                            remove("customButtonSelectTts")
                            remove("customButtonDeckOptions")
                            remove("customButtonMarkCard")
                            remove("customButtonToggleMicToolBar")
                            remove("customButtonBury")
                            remove("customButtonSuspend")
                            remove("customButtonFlag")
                            remove("customButtonDelete")
                            remove("customButtonEnableWhiteboard")
                            remove("customButtonSaveWhiteboard")
                            remove("customButtonWhiteboardPenColor")
                            remove("customButtonClearWhiteboard")
                            remove("customButtonShowHideWhiteboard")
                        }
                        // #9263: refresh the screen to display the changes
                        refreshScreen()
                    }
                    .negativeButton(R.string.cancel, null)
            }
            true
        }
    }

    @VisibleForTesting(otherwise = VisibleForTesting.NONE)
    fun allKeys(): HashSet<String> {
        val allKeys = HashSet<String>()
        for (i in 0 until preferenceScreen.preferenceCount) {
            val pref = preferenceScreen.getPreference(i)
            if (pref is PreferenceCategory) {
                for (j in 0 until pref.preferenceCount) {
                    allKeys.add(pref.getPreference(j).key)
                }
            } else {
                allKeys.add(pref.key)
            }
        }
        return allKeys
    }

    companion object {
        fun getSubscreenIntent(context: Context): Intent {
            return getSubscreenIntent(context, CustomButtonsSettingsFragment::class)
        }
    }
}
