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
import android.os.Bundle
import androidx.annotation.StringRes
import androidx.annotation.XmlRes
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.ichi2.anki.CollectionHelper
import com.ichi2.anki.Preferences
import com.ichi2.anki.analytics.UsageAnalytics
import com.ichi2.libanki.Collection
import com.ichi2.preferences.IncrementerNumberRangePreferenceCompat
import com.ichi2.preferences.NumberRangePreferenceCompat
import com.ichi2.preferences.SeekBarPreferenceCompat

abstract class SettingsFragment : PreferenceFragmentCompat() {
    /** @return The XML file which defines the preferences displayed by this PreferenceFragment
     */
    @get:XmlRes
    abstract val preferenceResource: Int

    /**
     * Refreshes all values on the screen
     * Call if a large number of values are changed from one preference.
     */
    protected fun refreshScreen() {
        preferenceScreen.removeAll()
        addPreferencesFromResource(preferenceResource)
        initSubscreen()
    }

    protected val col: Collection?
        get() = CollectionHelper.getInstance().getCol(requireContext())

    abstract fun initSubscreen()

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        UsageAnalytics.sendAnalyticsScreenView(analyticsScreenNameConstant)
        addPreferencesFromResource(preferenceResource)
        initSubscreen()
    }

    /** Obtains a non-null reference to the preference defined by the key, or throws  */
    @Suppress("UNCHECKED_CAST")
    protected fun <T : Preference?> requirePreference(key: String): T {
        val preference = findPreference<Preference>(key)
            ?: throw IllegalStateException("missing preference: '$key'")
        return preference as T
    }

    @Suppress("UNCHECKED_CAST")
    /**
     * Obtains a non-null reference to the preference whose
     * key is defined with given [resId] or throws
     * e.g. `requirePreference(R.string.day_theme_key)` returns
     * the preference whose key is `@string/day_theme_key`
     * The resource IDs with preferences keys can be found on `res/values/preferences.xml`
     */
    protected fun <T : Preference?> requirePreference(@StringRes resId: Int): T {
        return requirePreference(getString(resId)) as T
    }

    protected abstract val analyticsScreenNameConstant: String

    @Suppress("deprecation") // setTargetFragment
    override fun onDisplayPreferenceDialog(preference: Preference) {
        val dialogFragment = when (preference) {
            is IncrementerNumberRangePreferenceCompat -> IncrementerNumberRangePreferenceCompat.IncrementerNumberRangeDialogFragmentCompat.newInstance(preference.getKey())
            is NumberRangePreferenceCompat -> NumberRangePreferenceCompat.NumberRangeDialogFragmentCompat.newInstance(preference.getKey())
            is SeekBarPreferenceCompat -> SeekBarPreferenceCompat.SeekBarDialogFragmentCompat.newInstance(preference.getKey())
            else -> null
        }

        if (dialogFragment != null) {
            dialogFragment.setTargetFragment(this, 0)
            dialogFragment.show(parentFragmentManager, "androidx.preference.PreferenceFragment.DIALOG")
        } else {
            super.onDisplayPreferenceDialog(preference)
        }
    }

    companion object {
        @JvmStatic
        protected fun getSubscreenIntent(context: Context?, javaClassName: String): Intent {
            return Intent(context, Preferences::class.java)
                .putExtra(Preferences.INITIAL_FRAGMENT_EXTRA, javaClassName)
        }
    }
}
