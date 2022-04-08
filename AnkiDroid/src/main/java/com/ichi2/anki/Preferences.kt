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

import android.app.Activity
import android.app.AlarmManager
import android.app.AlertDialog
import android.content.*
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.text.TextUtils
import android.view.MenuItem
import android.view.WindowManager.BadTokenException
import android.webkit.URLUtil
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.annotation.VisibleForTesting
import androidx.annotation.XmlRes
import androidx.appcompat.app.ActionBar
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.preference.*
import com.afollestad.materialdialogs.MaterialDialog
import com.ichi2.anim.ActivityTransitionAnimation
import com.ichi2.anim.ActivityTransitionAnimation.slide
import com.ichi2.anki.OnboardingUtils.Companion.reset
import com.ichi2.anki.UIUtils.showSimpleSnackbar
import com.ichi2.anki.UIUtils.showThemedToast
import com.ichi2.anki.analytics.Acra.Companion.onPreferenceChanged
import com.ichi2.anki.analytics.UsageAnalytics
import com.ichi2.anki.cardviewer.GestureProcessor
import com.ichi2.anki.contextmenu.AnkiCardContextMenu
import com.ichi2.anki.contextmenu.CardBrowserContextMenu
import com.ichi2.anki.debug.DatabaseLock.engage
import com.ichi2.anki.exception.ConfirmModSchemaException
import com.ichi2.anki.exception.StorageAccessException
import com.ichi2.anki.reviewer.AutomaticAnswerAction
import com.ichi2.anki.reviewer.FullScreenMode
import com.ichi2.anki.services.BootService.Companion.scheduleNotification
import com.ichi2.anki.services.NotificationService
import com.ichi2.anki.web.CustomSyncServer
import com.ichi2.anki.web.CustomSyncServer.getSyncBaseUrlOrDefault
import com.ichi2.anki.web.CustomSyncServer.handleSyncServerPreferenceChange
import com.ichi2.anki.web.CustomSyncServer.isEnabled
import com.ichi2.compat.CompatHelper
import com.ichi2.libanki.Collection
import com.ichi2.libanki.Consts
import com.ichi2.libanki.Utils
import com.ichi2.libanki.backend.exception.BackendNotSupportedException
import com.ichi2.preferences.*
import com.ichi2.preferences.ControlPreference.Companion.setup
import com.ichi2.themes.Themes
import com.ichi2.themes.Themes.setThemeLegacy
import com.ichi2.themes.Themes.systemIsInNightMode
import com.ichi2.utils.AdaptionUtil.isRestrictedLearningDevice
import com.ichi2.utils.AdaptionUtil.isUserATestClient
import com.ichi2.utils.KotlinCleanup
import com.ichi2.utils.LanguageUtil
import com.ichi2.utils.VersionUtils.pkgVersionName
import timber.log.Timber
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.*

/**
 * Preferences dialog.
 */
class Preferences : AnkiActivity() {
    // Other variables
    @KotlinCleanup("we use string? as some keys were null")
    private val mOriginalSummaries = HashMap<String?, String>()

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
        enableToolbar()

        // Add a home button to the actionbar
        supportActionBar!!.setHomeButtonEnabled(true)
        supportActionBar!!.setDisplayHomeAsUpEnabled(true)
        title = resources.getText(R.string.preferences_title)

        val fragment = getInitialFragment(intent)

        // onRestoreInstanceState takes priority, this is only set on init.
        mOldCollectionPath = CollectionHelper.getCollectionPath(this)

        supportFragmentManager
            .beginTransaction()
            .replace(R.id.settings_container, fragment)
            .commit()

        addFragmentsToBackStack(supportFragmentManager, intent)

        supportFragmentManager.addOnBackStackChangedListener(mOnBackStackChangedListener)
    }

    override fun onDestroy() {
        super.onDestroy()
        supportFragmentManager.removeOnBackStackChangedListener(mOnBackStackChangedListener)
    }

    private fun updateActionBarTitle(fragmentManager: FragmentManager, actionBar: ActionBar?) {
        val fragment = fragmentManager.findFragmentById(R.id.settings_container)

        if (actionBar == null)
            return

        actionBar.title = when (fragment) {
            is AdvancedStatisticsSettingsFragment -> resources.getString(R.string.advanced_statistics_title)
            is CustomSyncServerSettingsFragment -> resources.getString(R.string.custom_sync_server_title)
            is CustomButtonsSettingsFragment -> resources.getString(R.string.custom_buttons)
            else -> resources.getString(R.string.preferences_title)
        }
    }

    private fun getInitialFragment(intent: Intent?): Fragment {
        if (intent == null) {
            return HeaderFragment()
        }
        val fragmentClass = intent.getStringExtra(EXTRA_SHOW_FRAGMENT)
            ?: return HeaderFragment()
        return try {
            Class.forName(fragmentClass).newInstance() as Fragment
        } catch (e: Exception) {
            throw RuntimeException("Failed to load $fragmentClass", e)
        }
    }

    /**
     * Adds fragments specified in [intent] extra to [fragmentManager] backstack,
     * following the fragments array order
     * @param intent with extra key [EXTRA_BACKSTACK_FRAGMENTS]
     * and value of a array of fragments java class names
     */
    private fun addFragmentsToBackStack(fragmentManager: FragmentManager, intent: Intent?) {
        if (intent == null) {
            return
        }
        val fragmentClasses = intent.getStringArrayExtra(EXTRA_BACKSTACK_FRAGMENTS)
            ?: return

        for (fragmentClass in fragmentClasses) {
            try {
                val fragment = Class.forName(fragmentClass).newInstance() as Fragment
                fragmentManager
                    .beginTransaction()
                    .replace(R.id.settings_container, fragment)
                    .addToBackStack(null)
                    .commit()
            } catch (e: Exception) {
                throw RuntimeException("Failed to load $fragmentClass", e)
            }
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

    @Suppress("deprecation") // startActivity
    fun restartWithNewDeckPicker() {
        // PERF: DB access on foreground thread
        CollectionHelper.getInstance().closeCollection(true, "Preference Modification: collection path changed")
        val deckPicker = Intent(this, DeckPicker::class.java)
        deckPicker.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(deckPicker)
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

    /**
     * Loop over every preference in the list and set the summary text
     */
    private fun initAllPreferences(screen: PreferenceScreen) {
        for (i in 0 until screen.preferenceCount) {
            val preference = screen.getPreference(i)
            if (preference is PreferenceGroup) {
                for (j in 0 until preference.preferenceCount) {
                    val nestedPreference = preference.getPreference(j)
                    if (nestedPreference is PreferenceGroup) {
                        for (k in 0 until nestedPreference.preferenceCount) {
                            initPreference(nestedPreference.getPreference(k))
                        }
                    } else {
                        initPreference(preference.getPreference(j))
                    }
                }
            } else {
                initPreference(preference)
            }
        }
    }

    private fun initPreference(pref: Preference) {
        // Load stored values from Preferences which are stored in the Collection
        if (sCollectionPreferences.contains(pref.key)) {
            val col = col
            if (col != null) {
                try {
                    when (pref.key) {
                        SHOW_ESTIMATE -> (pref as CheckBoxPreference).isChecked = col.get_config_boolean("estTimes")
                        SHOW_PROGRESS -> (pref as CheckBoxPreference).isChecked = col.get_config_boolean("dueCounts")
                        LEARN_CUTOFF -> (pref as NumberRangePreferenceCompat).setValue(col.get_config_int("collapseTime") / 60)
                        TIME_LIMIT -> (pref as NumberRangePreferenceCompat).setValue(col.get_config_int("timeLim") / 60)
                        USE_CURRENT -> (pref as ListPreference).setValueIndex(if (col.get_config("addToCur", true)!!) 0 else 1)
                        AUTOMATIC_ANSWER_ACTION -> (pref as ListPreference).setValueIndex(col.get_config(AutomaticAnswerAction.CONFIG_KEY, 0.toInt())!!)
                        NEW_SPREAD -> (pref as ListPreference).setValueIndex(col.get_config_int("newSpread"))
                        DAY_OFFSET -> (pref as SeekBarPreferenceCompat).value = getDayOffset(col)
                        PASTE_PNG -> (pref as CheckBoxPreference).isChecked = col.get_config("pastePNG", false)!!
                        NEW_TIMEZONE_HANDLING -> {
                            val checkBox = pref as CheckBoxPreference
                            checkBox.isChecked = col.sched._new_timezone_enabled()
                            if (col.schedVer() <= 1 || !col.isUsingRustBackend) {
                                Timber.d("Disabled 'newTimezoneHandling' box")
                                checkBox.isEnabled = false
                            }
                        }
                    }
                } catch (e: NumberFormatException) {
                    throw RuntimeException(e)
                }
            } else {
                // Disable Col preferences if Collection closed
                pref.isEnabled = false
            }
        } else if (MINIMUM_CARDS_DUE_FOR_NOTIFICATION == pref.key) {
            updateNotificationPreference(pref as ListPreference)
        }
        // Set the value from the summary cache
        val s = pref.summary
        mOriginalSummaries[pref.key] = s?.toString() ?: ""
        // Update summary
        updateSummary(pref)
    }

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
        scheduleNotification(col.time, this)
    }

    fun updateNotificationPreference(listpref: ListPreference) {
        val entries = listpref.entries
        val values = listpref.entryValues
        for (i in entries.indices) {
            val value = values[i].toString().toInt()
            if (entries[i].toString().contains("%d")) {
                entries[i] = String.format(entries[i].toString(), value)
            }
        }
        listpref.entries = entries
        listpref.summary = listpref.entry.toString()
    }

    private fun updateSummary(pref: Preference?) {
        if (pref == null || pref.key == null) {
            return
        }
        // Handle special cases
        when (pref.key) {
            "about_dialog_preference" -> pref.summary = resources.getString(R.string.about_version) + " " + pkgVersionName
            "custom_sync_server_link" -> {
                val preferences = AnkiDroidApp.getSharedPrefs(this)
                if (!isEnabled(preferences)) {
                    pref.setSummary(R.string.disabled)
                } else {
                    pref.summary = getSyncBaseUrlOrDefault(preferences, "")
                }
            }
            "advanced_statistics_link" -> if (!AnkiDroidApp.getSharedPrefs(this).getBoolean("advanced_statistics_enabled", false)) {
                pref.setSummary(R.string.disabled)
            } else {
                pref.setSummary(R.string.enabled)
            }
        }
        // Get value text
        val value: String = when (pref) {
            is NumberRangePreferenceCompat -> pref.getValue().toString()
            is SeekBarPreferenceCompat -> pref.value.toString()
            is ListPreference -> pref.entry?.toString() ?: ""
            is EditTextPreference -> pref.text ?: ""
            is ControlPreference -> return
            else -> return
        }

        // Get summary text
        val oldSummary = mOriginalSummaries[pref.key] ?: ""
        // Replace summary text with value according to some rules
        pref.summary = when {
            oldSummary == "" -> value
            value == "" -> oldSummary
            MINIMUM_CARDS_DUE_FOR_NOTIFICATION == pref.key -> replaceStringIfNumeric(oldSummary, value)
            else -> replaceString(oldSummary, value)
        }
    }

    /**
     * Replace "XXX" in [str] with [value]
     *
     * This exists to enable formatting the summary of a preference with data
     * As summary is set via XML, this cannot have format strings, so we use "XXX" later on.
     */
    private fun replaceString(str: String, value: String): String {
        return if (str.contains("XXX")) {
            str.replace("XXX", value)
        } else {
            str
        }
    }

    /**
     * If [value] is convertible to a double, replace "XXX" in [str] with the value
     * @param str A string which may have "XXX", if so, this may be replaced
     * @param value If this is a double, the "XXX" string in [str] is replaced with [value]
     */
    private fun replaceStringIfNumeric(str: String, value: String): String? {
        return try {
            value.toDouble()
            replaceString(str, value)
        } catch (e: NumberFormatException) {
            Timber.w(e)
            value
        }
    }

    @Suppress("deprecation") // finish
    private fun closePreferences() {
        finish()
        slide(this, ActivityTransitionAnimation.Direction.FADE)
        if (col != null && col.db != null) {
            col.save()
        }
    }

    /** This is not fit for purpose (other than testing a single screen)  */
    @get:VisibleForTesting(otherwise = VisibleForTesting.NONE)
    val loadedPreferenceKeys: Set<String>
        get() = mOriginalSummaries.keys.filterNotNull().toSet()

    // ----------------------------------------------------------------------------
    // Inner classes
    // ----------------------------------------------------------------------------

    class HeaderFragment : PreferenceFragmentCompat() {
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.preference_headers, rootKey)

            if (isRestrictedLearningDevice) {
                findPreference<Preference>("pref_screen_advanced")!!.isVisible = false
            }
        }
    }

    abstract class SettingsFragment : PreferenceFragmentCompat(), OnSharedPreferenceChangeListener {
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            val screenName = analyticsScreenNameConstant
            UsageAnalytics.sendAnalyticsScreenView(screenName)
            initSubscreen()
            (activity as Preferences?)!!.initAllPreferences(preferenceScreen)
        }

        /** Obtains a non-null reference to the preference defined by the key, or throws  */
        @Suppress("UNCHECKED_CAST")
        protected fun <T : Preference?> requirePreference(key: String): T {
            val preference = findPreference<Preference>(key)
                ?: throw IllegalStateException("missing preference: '$key'")
            return preference as T
        }

        protected abstract val analyticsScreenNameConstant: String

        /**
         * Loads preferences (via addPreferencesFromResource) and sets up appropriate listeners for the preferences
         * Called by base class, do not call directly.
         */
        protected abstract fun initSubscreen()

        override fun onResume() {
            super.onResume()
            val prefs = preferenceManager.sharedPreferences
            prefs!!.registerOnSharedPreferenceChangeListener(this)
            // syncAccount's summary can change while preferences are still open (user logs
            // in from preferences screen), so we need to update it here.
            updatePreference(activity as Preferences?, prefs, "syncAccount")
            updatePreference(activity as Preferences?, prefs, "custom_sync_server_link")
            updatePreference(activity as Preferences?, prefs, "advanced_statistics_link")
        }

        override fun onPause() {
            preferenceManager.sharedPreferences!!.unregisterOnSharedPreferenceChangeListener(this)
            super.onPause()
        }

        override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String) {
            updatePreference(activity as Preferences?, sharedPreferences, key)
        }

        @Suppress("deprecation") // setTargetFragment
        override fun onDisplayPreferenceDialog(preference: Preference) {
            val dialogFragment = when (preference) {
                is IncrementerNumberRangePreferenceCompat -> IncrementerNumberRangePreferenceCompat.IncrementerNumberRangeDialogFragmentCompat.newInstance(preference.getKey())
                is NumberRangePreferenceCompat -> NumberRangePreferenceCompat.NumberRangeDialogFragmentCompat.newInstance(preference.getKey())
                is ResetLanguageDialogPreference -> ResetLanguageDialogPreference.ResetLanguageDialogFragmentCompat.newInstance(preference.getKey())
                is ConfirmationPreferenceCompat -> ConfirmationPreferenceCompat.ConfirmationDialogFragmentCompat.newInstance(preference.getKey())
                is SeekBarPreferenceCompat -> SeekBarPreferenceCompat.SeekBarDialogFragmentCompat.newInstance(preference.getKey())
                is ControlPreference -> ControlPreference.View.newInstance(preference.getKey())
                else -> null
            }

            if (dialogFragment != null) {
                dialogFragment.setTargetFragment(this, 0)
                dialogFragment.show(parentFragmentManager, "androidx.preference.PreferenceFragment.DIALOG")
            } else {
                super.onDisplayPreferenceDialog(preference)
            }
        }

        /**
         * Code which is run when a SharedPreference change has been detected
         * @param preferencesActivity A handle to the calling activity
         * @param prefs instance of SharedPreferences
         * @param key key in prefs which is being updated
         */
        private fun updatePreference(preferencesActivity: Preferences?, prefs: SharedPreferences?, key: String) {
            try {
                val screen = preferenceScreen
                val pref = screen.findPreference<Preference>(key)
                if (pref == null) {
                    Timber.e("Preferences: no preference found for the key: %s", key)
                    return
                }
                // Handle special cases
                when (key) {
                    CustomSyncServer.PREFERENCE_CUSTOM_MEDIA_SYNC_URL, CustomSyncServer.PREFERENCE_CUSTOM_SYNC_BASE, CustomSyncServer.PREFERENCE_ENABLE_CUSTOM_SYNC_SERVER -> // This may be a tad hasty - performed before "back" is pressed.
                        handleSyncServerPreferenceChange(preferencesActivity!!.baseContext)
                    "timeoutAnswer" -> {
                        val keepScreenOn = screen.findPreference<CheckBoxPreference>("keepScreenOn")
                        keepScreenOn!!.isChecked = (pref as CheckBoxPreference).isChecked
                    }
                    LANGUAGE -> preferencesActivity!!.closePreferences()
                    SHOW_PROGRESS -> {
                        preferencesActivity!!.col.set_config("dueCounts", (pref as CheckBoxPreference).isChecked)
                        preferencesActivity.col.setMod()
                    }
                    SHOW_ESTIMATE -> {
                        preferencesActivity!!.col.set_config("estTimes", (pref as CheckBoxPreference).isChecked)
                        preferencesActivity.col.setMod()
                    }
                    NEW_SPREAD -> {
                        preferencesActivity!!.col.set_config("newSpread", (pref as ListPreference).value.toInt())
                        preferencesActivity.col.setMod()
                    }
                    TIME_LIMIT -> {
                        preferencesActivity!!.col.set_config("timeLim", (pref as NumberRangePreferenceCompat).getValue() * 60)
                        preferencesActivity.col.setMod()
                    }
                    LEARN_CUTOFF -> {
                        preferencesActivity!!.col.set_config("collapseTime", (pref as NumberRangePreferenceCompat).getValue() * 60)
                        preferencesActivity.col.setMod()
                    }
                    USE_CURRENT -> {
                        preferencesActivity!!.col.set_config("addToCur", "0" == (pref as ListPreference).value)
                        preferencesActivity.col.setMod()
                    }
                    AUTOMATIC_ANSWER_ACTION -> {
                        preferencesActivity!!.col.set_config(AutomaticAnswerAction.CONFIG_KEY, (pref as ListPreference).value.toInt())
                        preferencesActivity.col.setMod()
                    }
                    DAY_OFFSET -> {
                        preferencesActivity!!.setDayOffset((pref as SeekBarPreferenceCompat).value)
                    }
                    PASTE_PNG -> {
                        preferencesActivity!!.col.set_config("pastePNG", (pref as CheckBoxPreference).isChecked)
                        preferencesActivity.col.setMod()
                    }
                    MINIMUM_CARDS_DUE_FOR_NOTIFICATION -> {
                        val listpref = screen.findPreference<ListPreference>(MINIMUM_CARDS_DUE_FOR_NOTIFICATION)
                        if (listpref != null) {
                            preferencesActivity!!.updateNotificationPreference(listpref)
                            if (listpref.value.toInt() < PENDING_NOTIFICATIONS_ONLY) {
                                scheduleNotification(preferencesActivity.col.time, preferencesActivity)
                            } else {
                                val intent = CompatHelper.compat.getImmutableBroadcastIntent(
                                    preferencesActivity, 0,
                                    Intent(preferencesActivity, NotificationService::class.java), 0
                                )
                                val alarmManager = preferencesActivity.getSystemService(ALARM_SERVICE) as AlarmManager
                                alarmManager.cancel(intent)
                            }
                        }
                    }
                    AnkiDroidApp.FEEDBACK_REPORT_KEY -> {
                        val value = prefs!!.getString(AnkiDroidApp.FEEDBACK_REPORT_KEY, "")
                        onPreferenceChanged(preferencesActivity!!, value!!)
                    }
                    "syncAccount" -> {
                        val preferences = AnkiDroidApp.getSharedPrefs(preferencesActivity!!.baseContext)
                        val username = preferences.getString("username", "")
                        val syncAccount = screen.findPreference<Preference>("syncAccount")
                        if (syncAccount != null) {
                            if (TextUtils.isEmpty(username)) {
                                syncAccount.setSummary(R.string.sync_account_summ_logged_out)
                            } else {
                                syncAccount.summary = preferencesActivity.getString(R.string.sync_account_summ_logged_in, username)
                            }
                        }
                    }
                    "providerEnabled" -> {
                        val providerName = ComponentName(preferencesActivity!!, "com.ichi2.anki.provider.CardContentProvider")
                        val pm = preferencesActivity.packageManager
                        val state: Int
                        if ((pref as CheckBoxPreference).isChecked) {
                            state = PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                            Timber.i("AnkiDroid ContentProvider enabled by user")
                        } else {
                            state = PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                            Timber.i("AnkiDroid ContentProvider disabled by user")
                        }
                        pm.setComponentEnabledSetting(providerName, state, PackageManager.DONT_KILL_APP)
                    }
                    NEW_TIMEZONE_HANDLING -> {
                        if (preferencesActivity!!.col.schedVer() != 1 && preferencesActivity.col.isUsingRustBackend) {
                            val sched = preferencesActivity.col.sched
                            val wasEnabled = sched._new_timezone_enabled()
                            val isEnabled = (pref as CheckBoxPreference).isChecked
                            if (wasEnabled != isEnabled) {
                                if (isEnabled) {
                                    try {
                                        sched.set_creation_offset()
                                    } catch (e: BackendNotSupportedException) {
                                        throw e.alreadyUsingRustBackend()
                                    }
                                } else {
                                    sched.clear_creation_offset()
                                }
                            }
                        }
                    }
                    CardBrowserContextMenu.CARD_BROWSER_CONTEXT_MENU_PREF_KEY -> CardBrowserContextMenu.ensureConsistentStateWithSharedPreferences(preferencesActivity!!)
                    AnkiCardContextMenu.ANKI_CARD_CONTEXT_MENU_PREF_KEY -> AnkiCardContextMenu.ensureConsistentStateWithSharedPreferences(preferencesActivity!!)
                    "gestureCornerTouch" -> {
                        GesturesSettingsFragment.updateGestureCornerTouch(preferencesActivity, screen)
                    }
                }
                // Update the summary text to reflect new value
                preferencesActivity!!.updateSummary(pref)
            } catch (e: BadTokenException) {
                Timber.e(e, "Preferences: BadTokenException on showDialog")
            } catch (e: NumberFormatException) {
                throw RuntimeException(e)
            }
        }

        companion object {
            /** Obtains a non-null reference to the preference defined by the key, or throws  */
            @JvmStatic
            @Suppress("UNCHECKED_CAST")
            protected fun <T : Preference?> requirePreference(screen: PreferenceScreen, key: String): T {
                val preference = screen.findPreference<Preference>(key)
                    ?: throw IllegalStateException("missing preference: '$key'")
                return preference as T
            }
        }
    }

    /**
     * Temporary abstraction
     * Due to deprecation, we need to move from all Preference code in the Preference activity
     * into separate fragments.
     *
     * Fragments will inherit from this class
     *
     * This class adds methods which were previously in Preferences, and are now shared between Settings Fragments
     * To be merged with SettingsFragment once it can be made abstract
     */
    abstract class SpecificSettingsFragment : SettingsFragment() {
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
            initSubscreen()
        }

        protected val col: Collection?
            get() = CollectionHelper.getInstance().getCol(requireContext())

        /** Sets the title of the window to the provided string  */
        protected fun setTitle(@StringRes stringRes: Int) {
            val activity: Activity? = activity

            val supportActionBar: ActionBar?
            if (activity is AppCompatActivity) {
                supportActionBar = activity.supportActionBar
            } else {
                Timber.w("Activity was of the wrong type")
                return
            }

            if (supportActionBar == null) {
                Timber.w("No action bar detected")
                return
            }

            supportActionBar.setTitle(stringRes)
        }

        /**
         * Loads preferences (via addPreferencesFromResource) and sets up appropriate listeners for the preferences
         * Called by base class, do not call directly.
         */
        abstract override fun initSubscreen()

        companion object {
            @JvmStatic
            protected fun getSubscreenIntent(context: Context?, className: String): Intent {
                val i = Intent(context, Preferences::class.java)
                i.putExtra(EXTRA_SHOW_FRAGMENT, "com.ichi2.anki.Preferences$$className")
                return i
            }
        }
    }

    class GeneralSettingsFragment : SpecificSettingsFragment() {
        override val preferenceResource: Int
            get() = R.xml.preferences_general
        override val analyticsScreenNameConstant: String
            get() = "prefs.general"

        override fun initSubscreen() {
            addPreferencesFromResource(R.xml.preferences_general)
            val screen = preferenceScreen
            if (isRestrictedLearningDevice) {
                val checkBoxPrefVibrate = requirePreference<CheckBoxPreference>("widgetVibrate")
                val checkBoxPrefBlink = requirePreference<CheckBoxPreference>("widgetBlink")
                val category = requirePreference<PreferenceCategory>("category_general_notification_pref")
                category.removePreference(checkBoxPrefVibrate)
                category.removePreference(checkBoxPrefBlink)
            }
            // Build languages
            initializeLanguageDialog(screen)
        }

        @KotlinCleanup("iteration with i")
        private fun initializeLanguageDialog(screen: PreferenceScreen) {
            val languageSelection = screen.findPreference<ListPreference>(LANGUAGE)
            if (languageSelection != null) {
                val items: MutableMap<String, String> = TreeMap(java.lang.String.CASE_INSENSITIVE_ORDER)
                for (localeCode in LanguageUtil.APP_LANGUAGES) {
                    val loc = LanguageUtil.getLocale(localeCode)
                    items[loc.getDisplayName(loc)] = loc.toString()
                }
                val languageDialogLabels = arrayOfNulls<CharSequence>(items.size + 1)
                val languageDialogValues = arrayOfNulls<CharSequence>(items.size + 1)
                languageDialogLabels[0] = resources.getString(R.string.language_system)
                languageDialogValues[0] = ""
                var i = 1
                for ((key, value) in items) {
                    languageDialogLabels[i] = key
                    languageDialogValues[i] = value
                    i++
                }

                languageSelection.entries = languageDialogLabels
                languageSelection.entryValues = languageDialogValues
            }
        }
    }

    class ReviewingSettingsFragment : SpecificSettingsFragment() {
        override val preferenceResource: Int
            get() = R.xml.preferences_reviewing
        override val analyticsScreenNameConstant: String
            get() = "prefs.reviewing"

        override fun initSubscreen() {
            addPreferencesFromResource(R.xml.preferences_reviewing)
            // Show error toast if the user tries to disable answer button without gestures on
            val buttonsPreference = requirePreference<Preference>(getString(R.string.answer_buttons_position_preference))
            buttonsPreference.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _: Preference?, newValue: Any ->
                val prefs = AnkiDroidApp.getSharedPrefs(requireContext())
                if (prefs.getBoolean(GestureProcessor.PREF_KEY, false) || newValue != "none") {
                    return@OnPreferenceChangeListener true
                } else {
                    showThemedToast(
                        requireContext(),
                        R.string.full_screen_error_gestures, false
                    )
                    return@OnPreferenceChangeListener false
                }
            }
            val fullscreenPreference = requirePreference<ListPreference>(FullScreenMode.PREF_KEY)
            fullscreenPreference.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, newValue: Any ->
                val prefs = AnkiDroidApp.getSharedPrefs(requireContext())
                if (prefs.getBoolean(GestureProcessor.PREF_KEY, false) || FullScreenMode.FULLSCREEN_ALL_GONE.getPreferenceValue() != newValue) {
                    return@OnPreferenceChangeListener true
                } else {
                    showThemedToast(
                        requireContext(),
                        R.string.full_screen_error_gestures, false
                    )
                    return@OnPreferenceChangeListener false
                }
            }
        }
    }

    class AppearanceSettingsFragment : SpecificSettingsFragment() {
        private var mBackgroundImage: CheckBoxPreference? = null
        override val preferenceResource: Int
            get() = R.xml.preferences_appearance
        override val analyticsScreenNameConstant: String
            get() = "prefs.appearance"

        @Suppress("deprecation") // startActivityForResult
        override fun initSubscreen() {
            addPreferencesFromResource(R.xml.preferences_appearance)
            mBackgroundImage = requirePreference<CheckBoxPreference>("deckPickerBackground")
            mBackgroundImage!!.onPreferenceClickListener = Preference.OnPreferenceClickListener {
                if (mBackgroundImage!!.isChecked) {
                    try {
                        mBackgroundImageResultLauncher.launch("image/*")
                        mBackgroundImage!!.isChecked = true
                    } catch (ex: Exception) {
                        Timber.e("%s", ex.localizedMessage)
                    }
                } else {
                    mBackgroundImage!!.isChecked = false
                    val currentAnkiDroidDirectory = CollectionHelper.getCurrentAnkiDroidDirectory(requireContext())
                    val imgFile = File(currentAnkiDroidDirectory, "DeckPickerBackground.png")
                    if (imgFile.exists()) {
                        if (imgFile.delete()) {
                            showThemedToast(requireContext(), getString(R.string.background_image_removed), false)
                        } else {
                            showThemedToast(requireContext(), getString(R.string.error_deleting_image), false)
                        }
                    } else {
                        showThemedToast(requireContext(), getString(R.string.background_image_removed), false)
                    }
                }
                true
            }

            val appThemePref = requirePreference<ListPreference>(getString(R.string.app_theme_key))
            val dayThemePref = requirePreference<ListPreference>(getString(R.string.day_theme_key))
            val nightThemePref = requirePreference<ListPreference>(getString(R.string.night_theme_key))

            // Remove follow system options in android versions which do not have system dark mode
            // When minSdk reaches 29, only this if block needs to be removed
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                dayThemePref.isVisible = false
                nightThemePref.isVisible = false

                // Drop "Follow system" option (the first one)
                val appThemesLabels = resources.getStringArray(R.array.app_theme_labels)
                val appThemesValues = resources.getStringArray(R.array.app_theme_values)

                appThemePref.entries = appThemesLabels.sliceArray(1..appThemesLabels.lastIndex)
                appThemePref.entryValues = appThemesValues.sliceArray(1..appThemesValues.lastIndex)
            }

            val followSystem = Themes.themeFollowsSystem(requireContext())
            dayThemePref.isEnabled = followSystem
            nightThemePref.isEnabled = followSystem

            appThemePref.setOnPreferenceChangeListener { _, newValue ->
                dayThemePref.isEnabled = newValue == Themes.FOLLOW_SYSTEM_MODE
                nightThemePref.isEnabled = newValue == Themes.FOLLOW_SYSTEM_MODE

                // Only restart if value was changed
                if (newValue != appThemePref.value) {
                    restartActivityOnBackStackTop()
                }
                true
            }

            dayThemePref.setOnPreferenceChangeListener { _, newValue ->
                if (newValue != dayThemePref.value && !systemIsInNightMode(requireContext())) {
                    restartActivityOnBackStackTop()
                }
                true
            }

            nightThemePref.setOnPreferenceChangeListener { _, newValue ->
                if (newValue != nightThemePref.value && systemIsInNightMode(requireContext())) {
                    restartActivityOnBackStackTop()
                }
                true
            }
            initializeCustomFontsDialog()
        }

        /**
         * Restart [Preferences] activity with [AppearanceSettingsFragment]
         * in the top of the backstack
         */
        private fun restartActivityOnBackStackTop() {
            Timber.i("PreferenceActivity -- restartActivity()")
            val intent = Intent(context, requireActivity().javaClass)
            val fragmentClassNames = arrayOf(AppearanceSettingsFragment::class.java.name)
            intent.putExtra(EXTRA_BACKSTACK_FRAGMENTS, fragmentClassNames)
            requireContext().startActivity(intent)
            this.requireActivity().finish()
        }

        /** Initializes the list of custom fonts shown in the preferences.  */
        private fun initializeCustomFontsDialog() {
            val defaultFontPreference = requirePreference<ListPreference>("defaultFont")
            defaultFontPreference.entries = getCustomFonts("System default")
            defaultFontPreference.entryValues = getCustomFonts("")
            val browserEditorCustomFontsPreference = requirePreference<ListPreference>("browserEditorFont")
            browserEditorCustomFontsPreference.entries = getCustomFonts("System default")
            browserEditorCustomFontsPreference.entryValues = getCustomFonts("", true)
        }

        /** Returns a list of the names of the installed custom fonts.  */
        private fun getCustomFonts(defaultValue: String): Array<String?> {
            return getCustomFonts(defaultValue, false)
        }

        private fun getCustomFonts(defaultValue: String, useFullPath: Boolean): Array<String?> {
            val fonts = Utils.getCustomFonts(requireContext())
            val count = fonts.size
            Timber.d("There are %d custom fonts", count)
            val names = arrayOfNulls<String>(count + 1)
            names[0] = defaultValue
            if (useFullPath) {
                for (index in 1 until count + 1) {
                    names[index] = fonts[index - 1].path
                    Timber.d("Adding custom font: %s", names[index])
                }
            } else {
                for (index in 1 until count + 1) {
                    names[index] = fonts[index - 1].name
                    Timber.d("Adding custom font: %s", names[index])
                }
            }
            return names
        }

        private val mBackgroundImageResultLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { selectedImage ->
            if (selectedImage != null) {
                // handling file may result in exception
                try {
                    val filePathColumn = arrayOf(MediaStore.MediaColumns.SIZE)
                    requireContext().contentResolver.query(selectedImage, filePathColumn, null, null, null).use { cursor ->
                        cursor!!.moveToFirst()
                        // file size in MB
                        val fileLength = cursor.getLong(0) / (1024 * 1024)
                        val currentAnkiDroidDirectory = CollectionHelper.getCurrentAnkiDroidDirectory(requireContext())
                        val imageName = "DeckPickerBackground.png"
                        val destFile = File(currentAnkiDroidDirectory, imageName)
                        // Image size less than 10 MB copied to AnkiDroid directory
                        if (fileLength < 10) {
                            (requireContext().contentResolver.openInputStream(selectedImage) as FileInputStream?)!!.channel.use { sourceChannel ->
                                FileOutputStream(destFile).channel.use { destChannel ->
                                    destChannel.transferFrom(sourceChannel, 0, sourceChannel.size())
                                    showThemedToast(requireContext(), getString(R.string.background_image_applied), false)
                                }
                            }
                        } else {
                            mBackgroundImage!!.isChecked = false
                            showThemedToast(requireContext(), getString(R.string.image_max_size_allowed, 10), false)
                        }
                    }
                } catch (e: OutOfMemoryError) {
                    Timber.w(e)
                    showThemedToast(requireContext(), getString(R.string.error_selecting_image, e.localizedMessage), false)
                } catch (e: Exception) {
                    Timber.w(e)
                    showThemedToast(requireContext(), getString(R.string.error_selecting_image, e.localizedMessage), false)
                }
            } else {
                mBackgroundImage!!.isChecked = false
                showThemedToast(requireContext(), getString(R.string.no_image_selected), false)
            }
        }
    }

    class GesturesSettingsFragment : SpecificSettingsFragment() {
        override val preferenceResource: Int
            get() = R.xml.preferences_gestures
        override val analyticsScreenNameConstant: String
            get() = "prefs.gestures"

        override fun initSubscreen() {
            addPreferencesFromResource(R.xml.preferences_gestures)
            val screen = preferenceScreen
            updateGestureCornerTouch(screen)
        }

        private fun updateGestureCornerTouch(screen: PreferenceScreen) {
            updateGestureCornerTouch(requireContext(), screen)
        }

        companion object {
            fun updateGestureCornerTouch(context: Context?, screen: PreferenceScreen) {
                val gestureCornerTouch = AnkiDroidApp.getSharedPrefs(context).getBoolean("gestureCornerTouch", false)
                if (gestureCornerTouch) {
                    requirePreference<Preference>(screen, "gestureTapTop").setTitle(R.string.gestures_corner_tap_top_center)
                    requirePreference<Preference>(screen, "gestureTapLeft").setTitle(R.string.gestures_corner_tap_middle_left)
                    requirePreference<Preference>(screen, "gestureTapRight").setTitle(R.string.gestures_corner_tap_middle_right)
                    requirePreference<Preference>(screen, "gestureTapBottom").setTitle(R.string.gestures_corner_tap_bottom_center)
                } else {
                    requirePreference<Preference>(screen, "gestureTapTop").setTitle(R.string.gestures_tap_top)
                    requirePreference<Preference>(screen, "gestureTapLeft").setTitle(R.string.gestures_tap_left)
                    requirePreference<Preference>(screen, "gestureTapRight").setTitle(R.string.gestures_tap_right)
                    requirePreference<Preference>(screen, "gestureTapBottom").setTitle(R.string.gestures_tap_bottom)
                }
            }
        }
    }

    class AdvancedSettingsFragment : SpecificSettingsFragment() {
        override val preferenceResource: Int
            get() = R.xml.preferences_advanced
        override val analyticsScreenNameConstant: String
            get() = "prefs.advanced"

        override fun initSubscreen() {
            addPreferencesFromResource(R.xml.preferences_advanced)
            val screen = preferenceScreen
            // Check that input is valid before committing change in the collection path
            val collectionPathPreference = requirePreference<EditTextPreference>(CollectionHelper.PREF_DECK_PATH)
            collectionPathPreference.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, newValue: Any? ->
                val newPath = newValue as String?
                try {
                    CollectionHelper.initializeAnkiDroidDirectory(newPath)
                    return@OnPreferenceChangeListener true
                } catch (e: StorageAccessException) {
                    Timber.e(e, "Could not initialize directory: %s", newPath)
                    MaterialDialog.Builder(requireContext())
                        .title(R.string.dialog_collection_path_not_dir)
                        .positiveText(R.string.dialog_ok)
                        .negativeText(R.string.reset_custom_buttons)
                        .onPositive { dialog: MaterialDialog, _ -> dialog.dismiss() }
                        .onNegative { _, _ -> collectionPathPreference.text = CollectionHelper.getDefaultAnkiDroidDirectory(requireContext()) }
                        .show()
                    return@OnPreferenceChangeListener false
                }
            }
            setupContextMenuPreference(CardBrowserContextMenu.CARD_BROWSER_CONTEXT_MENU_PREF_KEY, R.string.card_browser_context_menu)
            setupContextMenuPreference(AnkiCardContextMenu.ANKI_CARD_CONTEXT_MENU_PREF_KEY, R.string.context_menu_anki_card_label)
            if (col!!.schedVer() == 1) {
                Timber.i("Displaying V1-to-V2 scheduler preference")
                val schedVerPreference = CheckBoxPreference(requireContext())
                schedVerPreference.setTitle(R.string.sched_v2)
                schedVerPreference.setSummary(R.string.sched_v2_summ)
                schedVerPreference.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, _ ->
                    val builder = MaterialDialog.Builder(requireContext())
                    // Going to V2
                    builder.title(R.string.sched_ver_toggle_title)
                    builder.content(R.string.sched_ver_1to2)
                    builder.onPositive { _, _ ->
                        col!!.modSchemaNoCheck()
                        try {
                            col!!.changeSchedulerVer(2)
                            screen.removePreference(schedVerPreference)
                        } catch (e2: ConfirmModSchemaException) {
                            // This should never be reached as we explicitly called modSchemaNoCheck()
                            throw RuntimeException(e2)
                        }
                    }
                    builder.onNegative { _, _ -> schedVerPreference.isChecked = false }
                    builder.onNeutral { _, _ ->
                        // call v2 scheduler documentation website
                        val uri = Uri.parse(getString(R.string.link_anki_2_scheduler))
                        val intent = Intent(Intent.ACTION_VIEW, uri)
                        startActivity(intent)
                    }
                    builder.positiveText(R.string.dialog_ok)
                    builder.neutralText(R.string.help)
                    builder.negativeText(R.string.dialog_cancel)
                    builder.show()
                    false
                }
                // meaning of order here is the position of Preference in xml layout.
                schedVerPreference.order = 5
                screen.addPreference(schedVerPreference)
            }

            // Make it possible to test crash reporting, but only for DEBUG builds
            if (BuildConfig.DEBUG && !isUserATestClient) {
                Timber.i("Debug mode, allowing for test crashes")
                val triggerTestCrashPreference = Preference(requireContext())
                triggerTestCrashPreference.key = "trigger_crash_preference"
                triggerTestCrashPreference.title = "Trigger test crash"
                triggerTestCrashPreference.summary = "Touch here for an immediate test crash"
                triggerTestCrashPreference.onPreferenceClickListener = Preference.OnPreferenceClickListener {
                    Timber.w("Crash triggered on purpose from advanced preferences in debug mode")
                    throw RuntimeException("This is a test crash")
                }
                screen.addPreference(triggerTestCrashPreference)
            }
            // Make it possible to test analytics, but only for DEBUG builds
            if (BuildConfig.DEBUG) {
                Timber.i("Debug mode, allowing for dynamic analytics config")
                val analyticsDebugMode = Preference(requireContext())
                analyticsDebugMode.key = "analytics_debug_preference"
                analyticsDebugMode.title = "Switch Analytics to dev mode"
                analyticsDebugMode.summary = "Touch here to use Analytics dev tag and 100% sample rate"
                analyticsDebugMode.onPreferenceClickListener = Preference.OnPreferenceClickListener {
                    if (UsageAnalytics.isEnabled()) {
                        showThemedToast(requireContext(), "Analytics set to dev mode", true)
                    } else {
                        showThemedToast(requireContext(), "Done! Enable Analytics in 'General' settings to use.", true)
                    }
                    UsageAnalytics.setDevMode()
                    true
                }
                screen.addPreference(analyticsDebugMode)
            }
            if (BuildConfig.DEBUG) {
                Timber.i("Debug mode, allowing database lock preference")
                val lockDbPreference = Preference(requireContext())
                lockDbPreference.key = "debug_lock_database"
                lockDbPreference.title = "Lock Database"
                lockDbPreference.summary = "Touch here to lock the database (all threads block in-process, exception if using second process)"
                lockDbPreference.onPreferenceClickListener = Preference.OnPreferenceClickListener {
                    engage(requireContext())
                    true
                }
                screen.addPreference(lockDbPreference)
            }
            if (BuildConfig.DEBUG) {
                Timber.i("Debug mode, option for showing onboarding walkthrough")
                val onboardingPreference = CheckBoxPreference(requireContext())
                onboardingPreference.key = "showOnboarding"
                onboardingPreference.setTitle(R.string.show_onboarding)
                onboardingPreference.setSummary(R.string.show_onboarding_desc)
                screen.addPreference(onboardingPreference)
            }
            if (BuildConfig.DEBUG) {
                Timber.i("Debug mode, option for resetting onboarding walkthrough")
                val onboardingPreference = Preference(requireContext())
                onboardingPreference.key = "resetOnboarding"
                onboardingPreference.setTitle(R.string.reset_onboarding)
                onboardingPreference.setSummary(R.string.reset_onboarding_desc)
                onboardingPreference.onPreferenceClickListener = Preference.OnPreferenceClickListener {
                    reset(requireContext())
                    true
                }
                screen.addPreference(onboardingPreference)
            }
            if (BuildConfig.DEBUG) {
                Timber.i("Debug mode, add option for using V16 backend")
                val onboardingPreference = Preference(requireContext())
                onboardingPreference.key = "useRustBackend"
                onboardingPreference.setDefaultValue(AnkiDroidApp.TESTING_USE_V16_BACKEND)
                onboardingPreference.title = "Use V16 Backend"
                onboardingPreference.summary = "UNSTABLE. DO NOT USE ON A COLLECTION YOU CARE ABOUT. REVERTED ON APP CLOSE"
                onboardingPreference.onPreferenceClickListener = Preference.OnPreferenceClickListener {
                    AnkiDroidApp.TESTING_USE_V16_BACKEND = true
                    Consts.SCHEMA_VERSION = 16
                    (requireActivity() as Preferences).restartWithNewDeckPicker()
                    true
                }
                screen.addPreference(onboardingPreference)
            }
            if (BuildConfig.DEBUG) {
                Timber.i("Debug mode, add option for scoped storage")
                val onboardingPreference = Preference(requireContext())
                onboardingPreference.key = "useScopedStorage"
                onboardingPreference.setDefaultValue(AnkiDroidApp.TESTING_SCOPED_STORAGE)
                onboardingPreference.title = "Enable Scoped Storage"
                onboardingPreference.summary = "UNSTABLE. DO NOT USE ON A COLLECTION YOU CARE ABOUT. REVERTED ON APP CLOSE"
                onboardingPreference.onPreferenceClickListener = Preference.OnPreferenceClickListener {
                    AnkiDroidApp.TESTING_SCOPED_STORAGE = true
                    (requireActivity() as Preferences).restartWithNewDeckPicker()
                    true
                }
                screen.addPreference(onboardingPreference)
            }
            // Adding change logs in both debug and release builds
            Timber.i("Adding open changelog")
            val changelogPreference = Preference(requireContext())
            changelogPreference.setTitle(R.string.open_changelog)
            val infoIntent = Intent(requireContext(), Info::class.java)
            infoIntent.putExtra(Info.TYPE_EXTRA, Info.TYPE_NEW_VERSION)
            changelogPreference.intent = infoIntent
            screen.addPreference(changelogPreference)
            // Force full sync option
            val fullSyncPreference = requirePreference<ConfirmationPreferenceCompat>("force_full_sync")
            fullSyncPreference.setDialogMessage(R.string.force_full_sync_summary)
            fullSyncPreference.setDialogTitle(R.string.force_full_sync_title)
            fullSyncPreference.setOkHandler {
                if (col == null) {
                    showThemedToast(requireContext(), R.string.directory_inaccessible, false)
                    return@setOkHandler
                }
                col!!.modSchemaNoCheck()
                col!!.setMod()
                showThemedToast(requireContext(), android.R.string.ok, true)
            }
            // Workaround preferences
            removeUnnecessaryAdvancedPrefs()
            addThirdPartyAppsListener()
        }

        private fun setupContextMenuPreference(key: String, @StringRes contextMenuName: Int) {
            // FIXME: The menu is named in the system language (as it's defined in the manifest which may be
            //  different than the app language
            val cardBrowserContextMenuPreference = requirePreference<CheckBoxPreference>(key)
            val menuName = getString(contextMenuName)
            // Note: The below format strings are generic, not card browser specific despite the name
            cardBrowserContextMenuPreference.title = getString(R.string.card_browser_enable_external_context_menu, menuName)
            cardBrowserContextMenuPreference.summary = getString(R.string.card_browser_enable_external_context_menu_summary, menuName)
        }

        private fun removeUnnecessaryAdvancedPrefs() {
            val plugins = findPreference<PreferenceCategory>("category_plugins")
            // Disable the emoji/kana buttons to scroll preference if those keys don't exist
            if (!CompatHelper.hasKanaAndEmojiKeys()) {
                val emojiScrolling = findPreference<CheckBoxPreference>("scrolling_buttons")
                if (emojiScrolling != null && plugins != null) {
                    plugins.removePreference(emojiScrolling)
                }
            }
            // Disable the double scroll preference if no scrolling keys
            if (!CompatHelper.hasScrollKeys() && !CompatHelper.hasKanaAndEmojiKeys()) {
                val doubleScrolling = findPreference<CheckBoxPreference>("double_scrolling")
                if (doubleScrolling != null && plugins != null) {
                    plugins.removePreference(doubleScrolling)
                }
            }
        }

        private fun addThirdPartyAppsListener() {
            // #5864 - some people don't have a browser so we can't use <intent>
            // and need to handle the keypress ourself.
            val showThirdParty = requirePreference<Preference>("thirdpartyapps_link")
            val githubThirdPartyAppsUrl = "https://github.com/ankidroid/Anki-Android/wiki/Third-Party-Apps"
            showThirdParty.onPreferenceClickListener = Preference.OnPreferenceClickListener {
                try {
                    val openThirdPartyAppsIntent = Intent(Intent.ACTION_VIEW, Uri.parse(githubThirdPartyAppsUrl))
                    super.startActivity(openThirdPartyAppsIntent)
                } catch (e: ActivityNotFoundException) {
                    Timber.w(e)
                    // We use a different message here. We have limited space in the snackbar
                    val error = getString(R.string.activity_start_failed_load_url, githubThirdPartyAppsUrl)
                    showSimpleSnackbar(requireActivity(), error, false)
                }
                true
            }
        }

        companion object {
            @JvmStatic
            fun getSubscreenIntent(context: Context?): Intent {
                return getSubscreenIntent(context, AdvancedSettingsFragment::class.java.simpleName)
            }
        }
    }

    class CustomButtonsSettingsFragment : SpecificSettingsFragment() {
        override val preferenceResource: Int
            get() = R.xml.preferences_custom_buttons
        override val analyticsScreenNameConstant: String
            get() = "prefs.custom_buttons"

        override fun initSubscreen() {
            setTitle(R.string.custom_buttons)
            addPreferencesFromResource(R.xml.preferences_custom_buttons)
            // Reset toolbar button customizations
            val resetCustomButtons = requirePreference<Preference>("reset_custom_buttons")
            resetCustomButtons.onPreferenceClickListener = Preference.OnPreferenceClickListener {
                AnkiDroidApp.getSharedPrefs(requireContext()).edit {
                    remove("customButtonUndo")
                    remove("customButtonScheduleCard")
                    remove("customButtonEditCard")
                    remove("customButtonTags")
                    remove("customButtonAddCard")
                    remove("customButtonReplay")
                    remove("customButtonCardInfo")
                    remove("customButtonSelectTts")
                    remove("customButtonDeckOptions")
                    remove("customButtonMarkCard")
                    remove("customButtonToggleMicToolBar")
                    remove("customButtonBury")
                    remove("customButtonSuspend")
                    remove("customButtonFlag")
                    remove("customButtonDelete")
                    remove("customButtonEnableWhiteboard")
                    remove("customButtonSaveWhiteboard")
                    remove("customButtonWhiteboardPenColor")
                    remove("customButtonClearWhiteboard")
                    remove("customButtonShowHideWhiteboard")
                }
                // #9263: refresh the screen to display the changes
                refreshScreen()
                true
            }
        }

        companion object {
            @JvmStatic
            fun getSubscreenIntent(context: Context?): Intent {
                return getSubscreenIntent(context, CustomButtonsSettingsFragment::class.java.simpleName)
            }
        }
    }

    class AdvancedStatisticsSettingsFragment : SpecificSettingsFragment() {
        override val preferenceResource: Int
            get() = R.xml.preferences_advanced_statistics
        override val analyticsScreenNameConstant: String
            get() = "prefs.advanced_statistics"

        override fun initSubscreen() {
            setTitle(R.string.advanced_statistics_title)
            addPreferencesFromResource(R.xml.preferences_advanced_statistics)
        }
    }

    class CustomSyncServerSettingsFragment : SpecificSettingsFragment() {
        override val preferenceResource: Int
            get() = R.xml.preferences_custom_sync_server
        override val analyticsScreenNameConstant: String
            get() = "prefs.custom_sync_server"

        override fun initSubscreen() {
            setTitle(R.string.custom_sync_server_title)
            addPreferencesFromResource(R.xml.preferences_custom_sync_server)
            val syncUrlPreference = requirePreference<Preference>("syncBaseUrl")
            val syncMediaUrlPreference = requirePreference<Preference>("syncMediaUrl")
            syncUrlPreference.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, newValue: Any ->
                val newUrl = newValue.toString()
                if (!URLUtil.isValidUrl(newUrl)) {
                    AlertDialog.Builder(requireContext())
                        .setTitle(R.string.custom_sync_server_base_url_invalid)
                        .setPositiveButton(R.string.dialog_ok, null)
                        .show()

                    return@OnPreferenceChangeListener false
                }
                true
            }
            syncMediaUrlPreference.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, newValue: Any ->
                val newUrl = newValue.toString()
                if (!URLUtil.isValidUrl(newUrl)) {
                    AlertDialog.Builder(requireContext())
                        .setTitle(R.string.custom_sync_server_media_url_invalid)
                        .setPositiveButton(R.string.dialog_ok, null)
                        .show()
                    return@OnPreferenceChangeListener false
                }
                true
            }
        }

        companion object {
            fun getSubscreenIntent(context: Context?): Intent {
                return getSubscreenIntent(context, CustomSyncServerSettingsFragment::class.java.simpleName)
            }
        }
    }

    class ControlsSettingsFragment : SpecificSettingsFragment() {
        override val preferenceResource: Int
            get() = R.xml.preferences_controls
        override val analyticsScreenNameConstant: String
            get() = "prefs.controls"

        override fun initSubscreen() {
            addPreferencesFromResource(R.xml.preferences_controls)
            val cat = requirePreference<PreferenceCategory>("key_map_category")
            setup(cat)
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
         * Represents in Android preferences the collections configuration "estTime": i.e. whether the buttons should indicate the duration of the interval if we click on them.
         */
        private const val SHOW_ESTIMATE = "showEstimates"

        /**
         * Represents in Android preferences the collections configuration "dueCounts": i.e.
         * whether the remaining number of cards should be shown.
         */
        private const val SHOW_PROGRESS = "showProgress"

        /**
         * Represents in Android preferences the collections configuration "collapseTime": i.e.
         * if there are no card to review now, but there are learning cards remaining for today, we show those learning cards if they are due before LEARN_CUTOFF minutes
         * Note that "collapseTime" is in second while LEARN_CUTOFF is in minute.
         */
        private const val LEARN_CUTOFF = "learnCutoff"

        /**
         * Represents in Android preferences the collections configuration "timeLim": i.e.
         * the duration of a review timebox in minute. Each TIME_LIMIT minutes, a message appear suggesting to halt and giving the number of card reviewed
         * Note that "timeLim" is in seconds while TIME_LIMIT is in minutes.
         */
        private const val TIME_LIMIT = "timeLimit"

        /**
         * Represents in Android preferences the collections configuration "addToCur": i.e.
         * if true, then add note to current decks, otherwise let the note type's configuration decide
         * Note that "addToCur" is a boolean while USE_CURRENT is "0" or "1"
         */
        private const val USE_CURRENT = "useCurrent"

        /**
         * Represents in Android preferences the collections configuration "newSpread": i.e.
         * whether the new cards are added at the end of the queue or randomly in it.
         */
        private const val NEW_SPREAD = "newSpread"

        /**
         * Represents in Android preference the collection's configuration "rollover"
         * in sched v2, and crt in sched v1. I.e. at which time of the day does the scheduler reset
         */
        private const val DAY_OFFSET = "dayOffset"

        /**
         * Represents in Android preference the collection's configuration "pastePNG" , i.e.
         * whether to convert clipboard uri to png format or not.
         * TODO: convert to png if a image file has transparency, or at least if it supports it.
         */
        private const val PASTE_PNG = "pastePNG"

        /**
         * Represents in Android preferences the collection's "Automatic Answer" action.
         *
         * An integer representing the action when "Automatic Answer" flips a card from answer to question
         *
         * 0 represents "bury", 1-4 represents the named buttons
         *
         * @see com.ichi2.anki.reviewer.AutomaticAnswerAction
         *
         * Although AnkiMobile and AnkiDroid have the feature, this config key is currently AnkiDroid only
         *
         * We use the same key in the collection config
         *
         *
         * @see com.ichi2.anki.reviewer.AutomaticAnswerAction.CONFIG_KEY
         */
        private const val AUTOMATIC_ANSWER_ACTION = "automaticAnswerAction"

        /**
         * The number of cards that should be due today in a deck to justify adding a notification.
         */
        const val MINIMUM_CARDS_DUE_FOR_NOTIFICATION = "minimumCardsDueForNotification"
        private const val NEW_TIMEZONE_HANDLING = "newTimezoneHandling"
        private val sCollectionPreferences = arrayOf(
            SHOW_ESTIMATE, SHOW_PROGRESS,
            LEARN_CUTOFF, TIME_LIMIT, USE_CURRENT, NEW_SPREAD, DAY_OFFSET, NEW_TIMEZONE_HANDLING, AUTOMATIC_ANSWER_ACTION
        )
        const val EXTRA_SHOW_FRAGMENT = ":android:show_fragment"

        /**
         * Key of intent extra used in [addFragmentsToBackStack]
         */
        const val EXTRA_BACKSTACK_FRAGMENTS = ":android:backstack_fragments"

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

        /** Whether the user is logged on to AnkiWeb  */
        fun hasAnkiWebAccount(preferences: SharedPreferences): Boolean =
            preferences.getString("username", "")!!.isNotEmpty()
    }
}
