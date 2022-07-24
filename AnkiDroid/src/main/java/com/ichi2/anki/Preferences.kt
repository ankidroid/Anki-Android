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
package com.ichi2.anki

import android.app.AlertDialog
import android.content.*
import android.os.Bundle
import android.view.MenuItem
import android.webkit.URLUtil
import androidx.annotation.VisibleForTesting
import androidx.appcompat.app.ActionBar
import androidx.core.content.edit
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.preference.*
import com.afollestad.materialdialogs.MaterialDialog
import com.ichi2.anim.ActivityTransitionAnimation
import com.ichi2.anki.UIUtils.showThemedToast
import com.ichi2.anki.analytics.UsageAnalytics
import com.ichi2.anki.preferences.AboutFragment
import com.ichi2.anki.preferences.HeaderFragment
import com.ichi2.anki.preferences.SettingsFragment
import com.ichi2.anki.preferences.setOnPreferenceChangeListener
import com.ichi2.anki.services.BootService.Companion.scheduleNotification
import com.ichi2.anki.web.CustomSyncServer.handleSyncServerPreferenceChange
import com.ichi2.libanki.Collection
import com.ichi2.libanki.Utils
import com.ichi2.libanki.utils.TimeManager
import com.ichi2.preferences.*
import com.ichi2.preferences.ControlPreference.Companion.addAllControlPreferencesToCategory
import com.ichi2.themes.Themes.setThemeLegacy
import net.ankiweb.rsdroid.BackendFactory
import timber.log.Timber
import java.util.*

/**
 * Preferences dialog.
 */
class Preferences : AnkiActivity() {
    /** The collection path when Preferences was opened   */
    private var mOldCollectionPath: String? = null

    private val mOnBackStackChangedListener: FragmentManager.OnBackStackChangedListener = FragmentManager.OnBackStackChangedListener {
        updateActionBarTitle(supportFragmentManager, supportActionBar)
    }
    // ----------------------------------------------------------------------------
    // Overridden methods
    // ----------------------------------------------------------------------------
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.preferences)
        setThemeLegacy(this)

        val actionBar = enableToolbar().apply {
            setHomeButtonEnabled(true)
            setDisplayHomeAsUpEnabled(true)
        }

        // onRestoreInstanceState takes priority, this is only set on init.
        mOldCollectionPath = CollectionHelper.getCollectionPath(this)

        // Load initial fragment if activity is being first created.
        // If activity is being recreated (i.e. savedInstanceState != null),
        // which could happen on configuration changes as screen rotation and theme changes,
        // don't replace the previous opened fragments
        if (savedInstanceState == null) {
            loadInitialFragment()
        }
        updateActionBarTitle(supportFragmentManager, actionBar)
        supportFragmentManager.addOnBackStackChangedListener(mOnBackStackChangedListener)
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
                Class.forName(fragmentClassName).newInstance() as Fragment
            } catch (e: Exception) {
                throw RuntimeException("Failed to load $fragmentClassName", e)
            }
        }
        supportFragmentManager
            .beginTransaction()
            .replace(R.id.settings_container, initialFragment, initialFragment::class.java.name)
            .commit()
    }

    override fun onDestroy() {
        super.onDestroy()
        supportFragmentManager.removeOnBackStackChangedListener(mOnBackStackChangedListener)
    }

    private fun updateActionBarTitle(fragmentManager: FragmentManager, actionBar: ActionBar?) {
        val fragment = fragmentManager.findFragmentById(R.id.settings_container)

        actionBar?.title = when (fragment) {
            is SettingsFragment -> fragment.preferenceScreen.title
            is AboutFragment -> getString(R.string.pref_cat_about_title)
            else -> getString(R.string.settings)
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            onBackPressed()
            return true
        }
        return false
    }

    override fun onBackPressed() {
        // If the collection path has changed, we want to move back to the deck picker immediately
        // This performs the move when back is pressed on the "Advanced" screen
        if (!Utils.equals(CollectionHelper.getCollectionPath(this), mOldCollectionPath)) {
            restartWithNewDeckPicker()
        } else {
            super.onBackPressed()
        }
    }

    fun restartWithNewDeckPicker() {
        // PERF: DB access on foreground thread
        val helper = CollectionHelper.getInstance()
        helper.closeCollection(true, "Preference Modification: collection path changed")
        helper.discardBackend()
        val deckPicker = Intent(this, DeckPicker::class.java)
        deckPicker.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivityWithAnimation(deckPicker, ActivityTransitionAnimation.Direction.DEFAULT)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("mOldCollectionPath", mOldCollectionPath)
    }

    override fun onRestoreInstanceState(state: Bundle) {
        super.onRestoreInstanceState(state)
        mOldCollectionPath = state.getString("mOldCollectionPath")
    }

    // ----------------------------------------------------------------------------
    // Class methods
    // ----------------------------------------------------------------------------

    /** Sets the hour that the collection rolls over to the next day  */
    @VisibleForTesting
    fun setDayOffset(hours: Int) {
        when (getSchedVer(col)) {
            2 -> {
                col.set_config("rollover", hours)
                col.flush()
            }
            else -> { // typically "1"
                val date: Calendar = col.crtGregorianCalendar()
                date[Calendar.HOUR_OF_DAY] = hours
                col.crt = date.timeInMillis / 1000
                col.setMod()
            }
        }
        scheduleNotification(TimeManager.time, this)
    }

    fun closePreferences() {
        finishWithAnimation(ActivityTransitionAnimation.Direction.FADE)
        if (col != null && !col.dbClosed) {
            col.save()
        }
    }

    // ----------------------------------------------------------------------------
    // Inner classes
    // ----------------------------------------------------------------------------

    class AdvancedStatisticsSettingsFragment : SettingsFragment() {
        override val preferenceResource: Int
            get() = R.xml.preferences_advanced_statistics
        override val analyticsScreenNameConstant: String
            get() = "prefs.advanced_statistics"

        override fun initSubscreen() {
            // Precision of computation
            requirePreference<SeekBarPreferenceCompat>(R.string.pref_computation_precision_key)
                .setFormattedSummary(R.string.pref_summary_percentage)
        }
    }

    class CustomSyncServerSettingsFragment : SettingsFragment() {
        override val preferenceResource: Int
            get() = R.xml.preferences_custom_sync_server
        override val analyticsScreenNameConstant: String
            get() = "prefs.custom_sync_server"

        override fun initSubscreen() {
            // Use custom sync server
            requirePreference<SwitchPreference>(R.string.custom_sync_server_enable_key).setOnPreferenceChangeListener { _ ->
                handleSyncServerPreferenceChange(requireContext())
            }
            // Sync url
            requirePreference<Preference>(R.string.custom_sync_server_base_url_key).setOnPreferenceChangeListener { _, newValue: Any ->
                val newUrl = newValue.toString()
                if (!URLUtil.isValidUrl(newUrl)) {
                    AlertDialog.Builder(requireContext())
                        .setTitle(R.string.custom_sync_server_base_url_invalid)
                        .setPositiveButton(R.string.dialog_ok, null)
                        .show()

                    return@setOnPreferenceChangeListener false
                }
                handleSyncServerPreferenceChange(requireContext())
                true
            }
            // Media url
            requirePreference<Preference>(R.string.custom_sync_server_media_url_key).setOnPreferenceChangeListener { _, newValue: Any ->
                val newUrl = newValue.toString()
                if (!URLUtil.isValidUrl(newUrl)) {
                    AlertDialog.Builder(requireContext())
                        .setTitle(R.string.custom_sync_server_media_url_invalid)
                        .setPositiveButton(R.string.dialog_ok, null)
                        .show()
                    return@setOnPreferenceChangeListener false
                }
                handleSyncServerPreferenceChange(requireContext())
                true
            }
        }
    }

    class ControlsSettingsFragment : SettingsFragment() {
        override val preferenceResource: Int
            get() = R.xml.preferences_controls
        override val analyticsScreenNameConstant: String
            get() = "prefs.controls"

        override fun initSubscreen() {
            addAllControlPreferencesToCategory(requirePreference(R.string.controls_command_mapping_cat_key))
        }
    }

    /**
     * Fragment exclusive to DEBUG builds which can be used
     * to add options useful for developers or WIP features.
     */
    class DevOptionsFragment : SettingsFragment() {
        override val preferenceResource: Int
            get() = R.xml.preferences_dev_options
        override val analyticsScreenNameConstant: String
            get() = "prefs.dev_options"

        override fun initSubscreen() {
            val enableDevOptionsPref = requirePreference<SwitchPreference>(R.string.dev_options_enabled_by_user_key)
            // If it is a DEBUG build, hide the preference to disable developer options
            // If it is a RELEASE build, configure the preference to disable dev options
            if (BuildConfig.DEBUG) {
                enableDevOptionsPref.isVisible = false
            } else {
                enableDevOptionsPref.setOnPreferenceChangeListener { _, _ ->
                    showDisableDevOptionsDialog()
                    false
                }
            }
            // Make it possible to test crash reporting
            requirePreference<Preference>(getString(R.string.pref_trigger_crash_key)).setOnPreferenceClickListener {
                Timber.w("Crash triggered on purpose from advanced preferences in debug mode")
                throw RuntimeException("This is a test crash")
            }
            // Make it possible to test analytics
            requirePreference<Preference>(getString(R.string.pref_analytics_debug_key)).setOnPreferenceClickListener {
                if (UsageAnalytics.isEnabled) {
                    showThemedToast(requireContext(), "Analytics set to dev mode", true)
                } else {
                    showThemedToast(requireContext(), "Done! Enable Analytics in 'General' settings to use.", true)
                }
                UsageAnalytics.setDevMode()
                true
            }
            // Lock database
            requirePreference<Preference>(getString(R.string.pref_lock_database_key)).setOnPreferenceClickListener {
                val c = CollectionHelper.getInstance().getCol(requireContext())!!
                Timber.w("Toggling database lock")
                c.db.database.beginTransaction()
                true
            }
            // Reset onboarding
            requirePreference<Preference>(getString(R.string.pref_reset_onboarding_key)).setOnPreferenceClickListener {
                OnboardingUtils.reset(requireContext())
                true
            }
            // Use V16 Backend
            requirePreference<Preference>(getString(R.string.pref_rust_backend_key)).apply {
                setDefaultValue(!BackendFactory.defaultLegacySchema)
                setOnPreferenceClickListener {
                    BackendFactory.defaultLegacySchema = false
                    (requireActivity() as Preferences).restartWithNewDeckPicker()
                    true
                }
            }
            // Use scoped storage
            requirePreference<Preference>(getString(R.string.pref_scoped_storage_key)).apply {
                setDefaultValue(AnkiDroidApp.TESTING_SCOPED_STORAGE)
                setOnPreferenceClickListener {
                    AnkiDroidApp.TESTING_SCOPED_STORAGE = true
                    (requireActivity() as Preferences).restartWithNewDeckPicker()
                    true
                }
            }
        }

        /**
         * Shows dialog to confirm if developer options should be disabled
         */
        private fun showDisableDevOptionsDialog() {
            MaterialDialog(requireContext()).show {
                title(R.string.disable_dev_options)
                positiveButton(R.string.dialog_ok) {
                    disableDevOptions()
                }
                negativeButton(R.string.dialog_cancel)
            }
        }

        /**
         * Destroys the fragment and hides developer options on [HeaderFragment]
         */
        private fun disableDevOptions() {
            val fragment = parentFragmentManager.findFragmentByTag(HeaderFragment::class.java.name)
            if (fragment is HeaderFragment) {
                fragment.setDevOptionsVisibility(false)
            }
            parentFragmentManager.popBackStack()
            setDevOptionsEnabledByUser(requireContext(), false)
        }

        companion object {
            /**
             * @return whether developer options should be shown to the user.
             * True in case [BuildConfig.DEBUG] is true
             * or if the user has enabled it with the secret on [com.ichi2.anki.preferences.AboutFragment]
             */
            fun isEnabled(context: Context): Boolean {
                return BuildConfig.DEBUG || AnkiDroidApp.getSharedPrefs(context)
                    .getBoolean(context.getString(R.string.dev_options_enabled_by_user_key), false)
            }

            fun setDevOptionsEnabledByUser(context: Context, isEnabled: Boolean) {
                AnkiDroidApp.getSharedPrefs(context).edit {
                    putBoolean(context.getString(R.string.dev_options_enabled_by_user_key), isEnabled)
                }
            }
        }
    }

    @VisibleForTesting(otherwise = VisibleForTesting.NONE)
    public override fun attachBaseContext(context: Context) {
        super.attachBaseContext(context)
    }

    companion object {
        /** Key of the language preference  */
        const val LANGUAGE = "language"

        /* Only enable AnkiDroid notifications unrelated to due reminders */
        const val PENDING_NOTIFICATIONS_ONLY = 1000000

        /**
         * The number of cards that should be due today in a deck to justify adding a notification.
         */
        const val MINIMUM_CARDS_DUE_FOR_NOTIFICATION = "minimumCardsDueForNotification"

        const val INITIAL_FRAGMENT_EXTRA = "initial_fragment"

        /** Returns the hour that the collection rolls over to the next day  */
        @JvmStatic
        fun getDayOffset(col: Collection): Int {
            return when (col.schedVer()) {
                2 -> col.get_config("rollover", 4.toInt())!!
                // 1, or otherwise:
                else -> col.crtGregorianCalendar()[Calendar.HOUR_OF_DAY]
            }
        }

        fun getSchedVer(col: Collection): Int {
            val ver = col.schedVer()
            if (ver < 1 || ver > 2) {
                Timber.w("Unknown scheduler version: %d", ver)
            }
            return ver
        }

        /**
         * Join [strings] with ` • ` as separator
         * to build a summary string for some preferences categories
         * e.g. `foo`, `bar`, `hi` ->  `foo • bar • hi`
         */
        fun buildCategorySummary(vararg strings: String): String {
            return if (!LanguageUtils.appLanguageIsRTL()) {
                strings.joinToString(separator = " • ")
            } else {
                strings.reversed().joinToString(separator = " • ")
            }
        }

        /** Whether the user is logged on to AnkiWeb  */
        fun hasAnkiWebAccount(preferences: SharedPreferences): Boolean =
            preferences.getString("username", "")!!.isNotEmpty()
    }
}
