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
                positiveButton(R.string.reset) {
                    // Reset the settings to default
                    requireContext().sharedPrefs().edit {
                        allKeys().forEach {
                            remove(it)
                        }
                    }
                    // #9263: refresh the screen to display the changes
                    refreshScreen()
                }
                negativeButton(R.string.dialog_cancel, null)
            }
            true
        }
    }

    @VisibleForTesting(otherwise = VisibleForTesting.NONE)
    fun allKeys(): HashSet<String> {
        return allPreferences().mapTo(hashSetOf()) { it.key }
    }

    companion object {
        fun getSubscreenIntent(context: Context): Intent {
            return getSubscreenIntent(context, CustomButtonsSettingsFragment::class)
        }
    }
}
