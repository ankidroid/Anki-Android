/****************************************************************************************
 *                                                                                      *
 * Copyright (c) 2022 Brian Da Silva <brianjose2010@gmail.com>                          *
 *                                                                                      *
 * This program is free software; you can redistribute it and/or modify it under        *
 * the terms of the GNU General Public License as published by the Free Software        *
 * Foundation; either version 3 of the License, or (at your option) any later           *
 * version.                                                                             *
 *                                                                                      *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY      *
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A      *
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.             *
 *                                                                                      *
 * You should have received a copy of the GNU General Public License along with         *
 * this program.  If not, see <http://www.gnu.org/licenses/>.                           *
 ****************************************************************************************/
package com.ichi2.anki

import androidx.preference.Preference
import com.ichi2.anki.preferences.SettingsFragment
import com.ichi2.anki.preferences.requirePreference

class ManageSpaceFragment : SettingsFragment() {
    override val preferenceResource: Int
        get() = R.xml.manage_space
    override val analyticsScreenNameConstant: String
        get() = "manageSpace"

    override fun initSubscreen() {
        requirePreference<Preference>(R.string.delete_collection_key).setOnPreferenceClickListener {
            UIUtils.showThemedToast(context, "Not implemented yet", false)
            true
        }
    }
}
