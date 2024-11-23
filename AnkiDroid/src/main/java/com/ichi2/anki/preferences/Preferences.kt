/***************************************************************************************
 * Copyright (c) 2009 Nicolas Raoul <nicolas.raoul@gmail.com>                           *
 * Copyright (c) 2009 Edu Zamora <edu.zasu@gmail.com>                                   *
 * Copyright (c) 2010 Norbert Nagold <norbert.nagold@gmail.com>                         *
 * Copyright (c) 2012 Kostas Spyropoulos <inigo.aldana@gmail.com>                       *
 * Copyright (c) 2015 Timothy Rae <perceptualchaos2@gmail.com>                          *
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
package com.ichi2.anki.preferences

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.annotation.XmlRes
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.bytehamster.lib.preferencesearch.SearchConfiguration
import com.bytehamster.lib.preferencesearch.SearchPreferenceResult
import com.bytehamster.lib.preferencesearch.SearchPreferenceResultListener
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.CollapsingToolbarLayout
import com.google.android.material.appbar.MaterialToolbar
import com.ichi2.anki.R
import com.ichi2.anki.SingleFragmentActivity
import com.ichi2.anki.utils.isWindowCompact
import com.ichi2.utils.getInstanceFromClassName
import timber.log.Timber
import kotlin.reflect.KClass
import kotlin.reflect.jvm.jvmName

class PreferencesFragment :
    Fragment(R.layout.preferences),
    PreferenceFragmentCompat.OnPreferenceStartFragmentCallback,
    SearchPreferenceResultListener {

    private val onBackPressedCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            if (resources.isWindowCompact() && childFragmentManager.backStackEntryCount > 0) {
                childFragmentManager.popBackStack()
            } else {
                requireActivity().finish()
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        view.findViewById<MaterialToolbar>(R.id.toolbar)
            .setNavigationOnClickListener { onBackPressedCallback.handleOnBackPressed() }

        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, onBackPressedCallback)

        // Load initial subscreen if activity is being first created
        if (savedInstanceState == null) {
            loadInitialSubscreen()
        } else {
            childFragmentManager.findFragmentById(R.id.settings_container)?.let {
                setFragmentTitleOnToolbar(it)
            }
        }

        childFragmentManager.addOnBackStackChangedListener {
            val fragment = childFragmentManager.findFragmentById(R.id.settings_container)
                ?: return@addOnBackStackChangedListener

            setFragmentTitleOnToolbar(fragment)

            // Expand bar in new fragments if scrolled to top
            (fragment as? PreferenceFragmentCompat)?.listView?.post {
                val viewHolder = fragment.listView?.findViewHolderForAdapterPosition(0)
                val isAtTop = viewHolder != null && viewHolder.itemView.top >= 0
                view.findViewById<AppBarLayout>(R.id.appbar).setExpanded(isAtTop, false)
            }
        }
    }

    override fun onPreferenceStartFragment(
        caller: PreferenceFragmentCompat,
        pref: Preference
    ): Boolean {
        // avoid reopening the same fragment if already active
        val currentFragment = childFragmentManager.findFragmentById(R.id.settings_container)
            ?: return true
        if (pref.fragment == currentFragment::class.jvmName) return true

        val fragment = childFragmentManager.fragmentFactory.instantiate(
            requireActivity().classLoader,
            pref.fragment ?: return true
        )
        fragment.arguments = pref.extras
        childFragmentManager.commit {
            replace(R.id.settings_container, fragment, fragment::class.jvmName)
            addToBackStack(null)
        }
        return true
    }

    override fun onSearchResultClicked(result: SearchPreferenceResult) {
        val fragment = getFragmentFromXmlRes(result.resourceFile) ?: return

        parentFragmentManager.popBackStack() // clear the search fragment from the backstack
        childFragmentManager.commit {
            replace(R.id.settings_container, fragment, fragment.javaClass.name)
            addToBackStack(fragment.javaClass.name)
        }

        Timber.i("Highlighting key '%s' on %s", result.key, fragment)
        result.highlight(fragment as PreferenceFragmentCompat)
    }

    private fun setFragmentTitleOnToolbar(fragment: Fragment) {
        val title = if (fragment is TitleProvider) fragment.title else getString(R.string.settings)

        view?.findViewById<CollapsingToolbarLayout>(R.id.collapsingToolbarLayout)?.title = title
        view?.findViewById<MaterialToolbar>(R.id.toolbar)?.title = title
    }

    /**
     * Starts the first settings fragment, which by default is [HeaderFragment].
     * The initial fragment may be overridden by putting the java class name
     * of the fragment on an intent extra with the key [INITIAL_FRAGMENT_EXTRA]
     */
    private fun loadInitialSubscreen() {
        val fragmentClassName = arguments?.getString(INITIAL_FRAGMENT_EXTRA)
        val initialFragment = if (fragmentClassName == null) {
            if (resources.isWindowCompact()) HeaderFragment() else GeneralSettingsFragment()
        } else {
            try {
                getInstanceFromClassName<Fragment>(fragmentClassName)
            } catch (e: Exception) {
                throw RuntimeException("Failed to load $fragmentClassName", e)
            }
        }
        childFragmentManager.commit {
            // In big screens, show the headers fragment at the lateral navigation container
            if (!resources.isWindowCompact()) {
                replace(R.id.lateral_nav_container, HeaderFragment())
            }
            replace(R.id.settings_container, initialFragment, initialFragment::class.java.name)
        }
    }
}

/**
 * Host activity for [PreferencesFragment].
 *
 * Only necessary because [SearchConfiguration] demands an activity that implements
 * [SearchPreferenceResultListener].
 */
class PreferencesActivity : SingleFragmentActivity(), SearchPreferenceResultListener {
    override fun onSearchResultClicked(result: SearchPreferenceResult) {
        val fragment = supportFragmentManager.findFragmentByTag(FRAGMENT_TAG)
        if (fragment is SearchPreferenceResultListener) {
            fragment.onSearchResultClicked(result)
        }
    }

    companion object {
        fun getIntent(context: Context, initialFragment: KClass<out SettingsFragment>? = null): Intent {
            val arguments = bundleOf(INITIAL_FRAGMENT_EXTRA to initialFragment?.jvmName)
            return Intent(context, PreferencesActivity::class.java).apply {
                putExtra(FRAGMENT_NAME_EXTRA, PreferencesFragment::class.jvmName)
                putExtra(FRAGMENT_ARGS_EXTRA, arguments)
            }
        }
    }
}

interface TitleProvider {
    val title: CharSequence
}

/* Only enable AnkiDroid notifications unrelated to due reminders */
const val PENDING_NOTIFICATIONS_ONLY = 1000000

const val INITIAL_FRAGMENT_EXTRA = "initial_fragment"

/**
 * @return the [SettingsFragment] which uses the given [screen] resource.
 * i.e. [SettingsFragment.preferenceResource] value is the same of [screen]
 */
fun getFragmentFromXmlRes(@XmlRes screen: Int): SettingsFragment? {
    return when (screen) {
        R.xml.preferences_general -> GeneralSettingsFragment()
        R.xml.preferences_reviewing -> ReviewingSettingsFragment()
        R.xml.preferences_sync -> SyncSettingsFragment()
        R.xml.preferences_backup_limits -> BackupLimitsSettingsFragment()
        R.xml.preferences_custom_sync_server -> CustomSyncServerSettingsFragment()
        R.xml.preferences_notifications -> NotificationsSettingsFragment()
        R.xml.preferences_appearance -> AppearanceSettingsFragment()
        R.xml.preferences_controls -> ControlsSettingsFragment()
        R.xml.preferences_advanced -> AdvancedSettingsFragment()
        R.xml.preferences_accessibility -> AccessibilitySettingsFragment()
        R.xml.preferences_dev_options -> DevOptionsFragment()
        R.xml.preferences_custom_buttons -> CustomButtonsSettingsFragment()
        else -> null
    }
}
