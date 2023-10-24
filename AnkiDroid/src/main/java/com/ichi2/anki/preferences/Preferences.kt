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
import android.content.SharedPreferences
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.annotation.VisibleForTesting
import androidx.annotation.XmlRes
import androidx.appcompat.app.ActionBar
import androidx.core.content.edit
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.commit
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.bytehamster.lib.preferencesearch.SearchConfiguration
import com.bytehamster.lib.preferencesearch.SearchPreferenceFragment
import com.bytehamster.lib.preferencesearch.SearchPreferenceResult
import com.bytehamster.lib.preferencesearch.SearchPreferenceResultListener
import com.ichi2.anki.*
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.services.BootService.Companion.scheduleNotification
import com.ichi2.compat.CompatHelper
import com.ichi2.libanki.Collection
import com.ichi2.libanki.utils.TimeManager
import com.ichi2.utils.AdaptionUtil
import com.ichi2.utils.getInstanceFromClassName
import timber.log.Timber
import java.util.*

/**
 * Preferences dialog.
 */
class Preferences :
    AnkiActivity(),
    PreferenceFragmentCompat.OnPreferenceStartFragmentCallback,
    SearchPreferenceResultListener {
    val searchConfiguration: SearchConfiguration by lazy { configureSearchBar() }
    lateinit var searchView: PreferencesSearchView

    // ----------------------------------------------------------------------------
    // Overridden methods
    // ----------------------------------------------------------------------------
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.preferences)

        val actionBar = enableToolbar().apply {
            setHomeButtonEnabled(true)
            setDisplayHomeAsUpEnabled(true)
        }

        // Load initial fragment if activity is being first created.
        // If activity is being recreated (i.e. savedInstanceState != null),
        // which could happen on configuration changes as screen rotation and theme changes,
        // don't replace the previous opened fragments
        if (savedInstanceState == null) {
            loadInitialFragment()
        }
        updateActionBarTitle(supportFragmentManager, actionBar)
        supportFragmentManager.addOnBackStackChangedListener {
            updateActionBarTitle(supportFragmentManager, supportActionBar)
        }
    }

    /**
     * Starts the first fragment for the [Preferences] activity,
     * which by default is [HeaderFragment].
     * The initial fragment may be overridden by putting the java class name
     * of the fragment on an intent extra with the key [INITIAL_FRAGMENT_EXTRA]
     */
    private fun loadInitialFragment() {
        val fragmentClassName = intent?.getStringExtra(INITIAL_FRAGMENT_EXTRA)
        val initialFragment = if (fragmentClassName == null) {
            HeaderFragment()
        } else {
            try {
                getInstanceFromClassName<Fragment>(fragmentClassName)
            } catch (e: Exception) {
                throw RuntimeException("Failed to load $fragmentClassName", e)
            }
        }
        supportFragmentManager.commit {
            replace(R.id.settings_container, initialFragment, initialFragment::class.java.name)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.preferences, menu)

        val searchIcon = menu.findItem(R.id.preferences_search)
        searchView = searchIcon.actionView as PreferencesSearchView
        searchView.setActivity(this)
        searchView.searchConfiguration = searchConfiguration

        return super.onCreateOptionsMenu(menu)
    }

    override fun onPreferenceStartFragment(
        caller: PreferenceFragmentCompat,
        pref: Preference
    ): Boolean {
        val fragment = supportFragmentManager.fragmentFactory.instantiate(
            classLoader,
            pref.fragment!!
        )
        fragment.arguments = pref.extras
        supportFragmentManager.commit {
            replace(R.id.settings_container, fragment)
            addToBackStack(null)
        }
        return true
    }

    /**
     * @return the default [SearchConfiguration] for AnkiDroid settings
     */
    private fun configureSearchBar(): SearchConfiguration {
        val searchConfig = SearchConfiguration(this).apply {
            setFragmentContainerViewId(R.id.settings_container)
            setBreadcrumbsEnabled(true)
            setFuzzySearchEnabled(false)
            setHistoryEnabled(true)
            textNoResults = getString(R.string.pref_search_no_results)

            index(R.xml.preferences_general)
            index(R.xml.preferences_reviewing)
            index(R.xml.preferences_sync)
            index(R.xml.preferences_custom_sync_server)
                .addBreadcrumb(R.string.pref_cat_sync)
            index(R.xml.preferences_notifications)
            index(R.xml.preferences_appearance)
            index(R.xml.preferences_custom_buttons)
                .addBreadcrumb(R.string.pref_cat_appearance)
            index(R.xml.preferences_controls)
            index(R.xml.preferences_accessibility)
        }

        // Some preferences and categories are only shown conditionally,
        // so they should be searchable based on the same conditions

        /** From [HeaderFragment.onCreatePreferences] */
        if (DevOptionsFragment.isEnabled(this)) {
            searchConfig.index(R.xml.preferences_dev_options)
            /** From [DevOptionsFragment.initSubscreen] */
            if (BuildConfig.DEBUG) {
                searchConfig.ignorePreference(getString(R.string.dev_options_enabled_by_user_key))
            }
        }

        /** From [HeaderFragment.onCreatePreferences] */
        if (!AdaptionUtil.isXiaomiRestrictedLearningDevice) {
            searchConfig.index(R.xml.preferences_advanced)
        }

        /** From [NotificationsSettingsFragment.initSubscreen] */
        if (AdaptionUtil.isXiaomiRestrictedLearningDevice) {
            searchConfig.ignorePreference(getString(R.string.pref_notifications_vibrate_key))
            searchConfig.ignorePreference(getString(R.string.pref_notifications_blink_key))
        }

        /** From [AdvancedSettingsFragment.removeUnnecessaryAdvancedPrefs] */
        if (!CompatHelper.hasScrollKeys()) {
            searchConfig.ignorePreference(getString(R.string.double_scrolling_gap_key))
        }
        return searchConfig
    }

    private fun updateActionBarTitle(fragmentManager: FragmentManager, actionBar: ActionBar?) {
        val fragment = fragmentManager.findFragmentById(R.id.settings_container)

        if (fragment is SearchPreferenceFragment) {
            return
        }

        actionBar?.title = when (fragment) {
            is SettingsFragment -> fragment.preferenceScreen.title
            is AboutFragment -> getString(R.string.pref_cat_about_title)
            else -> getString(R.string.settings)
        }
    }

    @Suppress("deprecation") // onBackPressed
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            onBackPressed()
            return true
        }
        return false
    }

    fun restartWithNewDeckPicker() {
        launchCatchingTask {
            CollectionManager.discardBackend()
            val deckPicker = Intent(this@Preferences, DeckPicker::class.java)
            deckPicker.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(deckPicker)
        }
    }

    // ----------------------------------------------------------------------------
    // Class methods
    // ----------------------------------------------------------------------------

    /**
     * Enables and sets the visibility of the "Developer options" header on [HeaderFragment]
     */
    fun setDevOptionsEnabled(isEnabled: Boolean) {
        // Update the "devOptionsEnabledByUser" pref value
        this.sharedPrefs().edit {
            putBoolean(getString(R.string.dev_options_enabled_by_user_key), isEnabled)
        }
        // Show/hide the header
        val headerFragment =
            supportFragmentManager.findFragmentByTag(HeaderFragment::class.java.name)
        if (headerFragment is HeaderFragment) {
            headerFragment.setDevOptionsVisibility(isEnabled)
        }
    }

    override fun onSearchResultClicked(result: SearchPreferenceResult) {
        val resultFragment = getFragmentFromXmlRes(result.resourceFile)
            ?: return

        val fragments = supportFragmentManager.fragments
        // The last opened fragment is going to be
        // the search fragment, so get the one before it
        val currentFragment = fragments[fragments.lastIndex - 1]
        // then clear the search fragment from the backstack
        supportFragmentManager.popBackStack()

        // If the clicked result is on the currently opened fragment,
        // it isn't necessary to create it again
        val fragmentToHighlight = if (currentFragment::class != resultFragment::class) {
            supportFragmentManager.commit {
                replace(R.id.settings_container, resultFragment, resultFragment.javaClass.name)
                addToBackStack(resultFragment.javaClass.name)
            }
            resultFragment
        } else {
            currentFragment
        }

        Timber.i("Highlighting key '%s' on %s", result.key, fragmentToHighlight)
        result.highlight(fragmentToHighlight as PreferenceFragmentCompat)
    }

    companion object {

        /* Only enable AnkiDroid notifications unrelated to due reminders */
        const val PENDING_NOTIFICATIONS_ONLY = 1000000

        const val DEFAULT_ROLLOVER_VALUE: Int = 4

        /**
         * The number of cards that should be due today in a deck to justify adding a notification.
         */
        const val MINIMUM_CARDS_DUE_FOR_NOTIFICATION = "minimumCardsDueForNotification"

        const val INITIAL_FRAGMENT_EXTRA = "initial_fragment"

        /** Returns the hour that the collection rolls over to the next day  */

        private fun getSchedVer(col: Collection): Int {
            val ver = col.schedVer()
            if (ver < 1 || ver > 2) {
                Timber.w("Unknown scheduler version: %d", ver)
            }
            return ver
        }

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

        /** Whether the user is logged on to AnkiWeb  */
        fun hasAnkiWebAccount(preferences: SharedPreferences): Boolean =
            preferences.getString("username", "")!!.isNotEmpty()

        /** Sets the hour that the collection rolls over to the next day  */
        @VisibleForTesting
        suspend fun setDayOffset(context: Context, hours: Int) {
            withCol {
                when (getSchedVer(this)) {
                    2 -> {
                        config.set("rollover", hours)
                    }
                    else -> { // typically "1"
                        val date: Calendar = crtGregorianCalendar()
                        date[Calendar.HOUR_OF_DAY] = hours
                        crt = date.timeInMillis / 1000
                    }
                }
            }
            scheduleNotification(TimeManager.time, context)
        }

        suspend fun getDayOffset(): Int {
            return withCol {
                when (schedVer()) {
                    2 -> config.get("rollover") ?: DEFAULT_ROLLOVER_VALUE
                    // 1, or otherwise:
                    else -> crtGregorianCalendar()[Calendar.HOUR_OF_DAY]
                }
            }
        }
    }
}
