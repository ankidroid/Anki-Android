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
import android.content.SharedPreferences
import android.os.Bundle
import androidx.annotation.VisibleForTesting
import androidx.annotation.XmlRes
import androidx.core.os.bundleOf
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import androidx.preference.PreferenceManager.OnPreferenceTreeClickListener
import com.ichi2.anki.analytics.UsageAnalytics
import com.ichi2.preferences.DialogFragmentProvider
import java.lang.NumberFormatException
import kotlin.reflect.KClass
import kotlin.reflect.jvm.jvmName

abstract class SettingsFragment :
    PreferenceFragmentCompat(),
    OnPreferenceTreeClickListener,
    SharedPreferences.OnSharedPreferenceChangeListener {
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

    override fun onPreferenceTreeClick(preference: Preference): Boolean {
        UsageAnalytics.sendAnalyticsEvent(
            category = UsageAnalytics.Category.SETTING,
            action = UsageAnalytics.Actions.TAPPED_SETTING,
            label = preference.key
        )
        return super.onPreferenceTreeClick(preference)
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String?) {
        if (key !in UsageAnalytics.preferencesWhoseChangesShouldBeReported) {
            return
        }
        if (key != null) {
            val valueToReport = getPreferenceReportableValue(sharedPreferences.get(key))
            UsageAnalytics.sendAnalyticsEvent(
                category = UsageAnalytics.Category.SETTING,
                action = UsageAnalytics.Actions.CHANGED_SETTING,
                value = valueToReport,
                label = key
            )
        }
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        UsageAnalytics.sendAnalyticsScreenView(analyticsScreenNameConstant)
        addPreferencesFromResource(preferenceResource)
        allPreferences().forEach { it.isSingleLineTitle = false }
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

    override fun onStart() {
        super.onStart()
        PreferenceManager.getDefaultSharedPreferences(requireContext())
            .registerOnSharedPreferenceChangeListener(this)
    }

    override fun onStop() {
        super.onStop()
        PreferenceManager.getDefaultSharedPreferences(requireContext())
            .unregisterOnSharedPreferenceChangeListener(this)
    }

    protected fun allPreferences(): List<Preference> {
        val allPreferences = mutableListOf<Preference>()
        for (i in 0 until preferenceScreen.preferenceCount) {
            val pref = preferenceScreen.getPreference(i)
            if (pref is PreferenceCategory) {
                for (j in 0 until pref.preferenceCount) {
                    allPreferences.add(pref.getPreference(j))
                }
            } else {
                allPreferences.add(pref)
            }
        }
        return allPreferences
    }

    companion object {
        @JvmStatic // Using protected members which are not @JvmStatic in the superclass companion is unsupported yet
        protected fun getSubscreenIntent(context: Context, fragmentClass: KClass<out SettingsFragment>): Intent {
            return Intent(context, Preferences::class.java)
                .putExtra(Preferences.INITIAL_FRAGMENT_EXTRA, fragmentClass.jvmName)
        }

        /**
         * Converts a preference value to a numeric number that
         * can be reported to analytics, since analytics events only accept
         * [Int] as value ([UsageAnalytics.sendAnalyticsEvent]),
         * or null if it can't be converted.
         *
         * Boolean preferences will return 1 if true and 0 if false
         *
         * String preferences whose values are stored in a numeric format,
         * e.g. fullscreen mode whose values are "0", "1" and "2",
         * can have their values reported as well.
         * */
        @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
        fun getPreferenceReportableValue(value: Any?): Int? {
            return when (value) {
                is Int -> value
                is String -> try { value.toInt() } catch (e: NumberFormatException) { null }
                is Boolean -> if (value) 1 else 0
                is Float -> value.toInt()
                is Long -> value.toInt()
                else -> null
            }
        }
    }
}
