/*
 *  Copyright (c) 2024 Brayan Oliveira <brayandso.dev@gmail.com>
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
import com.ichi2.anki.SingleFragmentActivity
import com.ichi2.anki.preferences.reviewer.ReviewerMenuSettingsFragment

/**
 * Developer options to test some of the new reviewer settings and features
 *
 * Not a `SettingsFragment` to avoid boilerplate and sending analytics reports,
 * since this is just a temporary screen while the new reviewer is being developed.
 */
class ReviewerOptionsFragment :
    PreferenceFragmentCompat(),
    PreferenceXmlSource {
    override val preferenceResource: Int = R.xml.preferences_reviewer

    override fun onCreatePreferences(
        savedInstanceState: Bundle?,
        rootKey: String?,
    ) {
        addPreferencesFromResource(preferenceResource)

        // TODO launch the fragment inside PreferencesFragment instead of using a new activity.
        // An activity is being currently used because the preferences screens are shown below the
        // collapsible toolbar, and the menu screen has a non collapsible one. Putting it in
        // `settings_container` would lead to two toolbars, which isn't desirable. Putting its menu
        // into the collapsible toolbar would ruin the preview, which also isn't desirable.
        // An activity partially solves that, because the screen looks alright in phones, but in
        // tablets/big screens, the preferences navigation lateral bar isn't shown.
        requirePreference<Preference>(R.string.reviewer_menu_settings_key).setOnPreferenceClickListener {
            val intent = SingleFragmentActivity.getIntent(requireContext(), ReviewerMenuSettingsFragment::class)
            startActivity(intent)
            true
        }
    }
}
