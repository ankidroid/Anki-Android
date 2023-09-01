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

import android.os.Bundle
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.ichi2.anki.R
import com.ichi2.utils.AdaptionUtil

class HeaderFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preference_headers, rootKey)

        requirePreference<Preference>(R.string.pref_advanced_screen_key).apply {
            if (AdaptionUtil.isXiaomiRestrictedLearningDevice) {
                isVisible = false
            }
        }

        if (DevOptionsFragment.isEnabled(requireContext())) {
            setDevOptionsVisibility(true)
        }
    }

    fun setDevOptionsVisibility(isVisible: Boolean) {
        requirePreference<Preference>(R.string.pref_dev_options_screen_key).isVisible = isVisible
    }
}
