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
import androidx.annotation.XmlRes
import androidx.core.os.bundleOf
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.ichi2.anki.analytics.UsageAnalytics
import com.ichi2.preferences.DialogFragmentProvider
import kotlin.reflect.KClass
import kotlin.reflect.jvm.jvmName

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

    abstract fun initSubscreen()

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        UsageAnalytics.sendAnalyticsScreenView(analyticsScreenNameConstant)
        addPreferencesFromResource(preferenceResource)
        initSubscreen()
    }

    protected abstract val analyticsScreenNameConstant: String

    @Suppress("deprecation") // setTargetFragment #9452
    // androidx.preference.PreferenceDialogFragmentCompat uses the deprecated method
    // `getTargetFragment()`, which throws if `setTargetFragment()` isn't used before.
    // While this isn't fixed on upstream, suppress the deprecation warning
    override fun onDisplayPreferenceDialog(preference: Preference) {
        if (preference is DialogFragmentProvider) {
            val dialogFragment = preference.makeDialogFragment()
            dialogFragment.arguments = bundleOf("key" to preference.key)
            dialogFragment.setTargetFragment(this, 0)
            dialogFragment.show(parentFragmentManager, "androidx.preference.PreferenceFragment.DIALOG")
        } else {
            super.onDisplayPreferenceDialog(preference)
        }
    }

    companion object {
        @JvmStatic // Using protected members which are not @JvmStatic in the superclass companion is unsupported yet
        protected fun getSubscreenIntent(context: Context, fragmentClass: KClass<out SettingsFragment>): Intent {
            return Intent(context, Preferences::class.java)
                .putExtra(Preferences.INITIAL_FRAGMENT_EXTRA, fragmentClass.jvmName)
        }
    }
}
