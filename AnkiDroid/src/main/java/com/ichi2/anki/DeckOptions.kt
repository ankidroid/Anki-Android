/****************************************************************************************
 * Copyright (c) 2009 Casey Link <unnamedrambler@gmail.com>                             *
 * Copyright (c) 2012 Norbert Nagold <norbert.nagold@gmail.com>                         *
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
@file:Suppress("DEPRECATION") // Migrate to AndroidX preferences #5019

package com.ichi2.anki

import android.app.AlarmManager
import android.content.*
import android.os.Bundle
import android.preference.CheckBoxPreference
import android.preference.ListPreference
import android.preference.Preference
import android.preference.PreferenceScreen
import android.text.TextUtils
import android.view.KeyEvent
import android.view.MenuItem
import com.afollestad.materialdialogs.DialogAction
import com.afollestad.materialdialogs.MaterialDialog
import com.ichi2.anim.ActivityTransitionAnimation
import com.ichi2.anim.ActivityTransitionAnimation.Direction.FADE
import com.ichi2.anki.exception.ConfirmModSchemaException
import com.ichi2.anki.receiver.SdCardReceiver
import com.ichi2.anki.services.ReminderService
import com.ichi2.annotations.NeedsTest
import com.ichi2.async.CollectionTask
import com.ichi2.async.TaskListenerWithContext
import com.ichi2.async.TaskManager
import com.ichi2.compat.CompatHelper
import com.ichi2.libanki.Consts
import com.ichi2.libanki.Deck
import com.ichi2.libanki.DeckConfig
import com.ichi2.libanki.utils.Time
import com.ichi2.preferences.NumberRangePreference
import com.ichi2.preferences.StepsPreference
import com.ichi2.preferences.TimePreference
import com.ichi2.themes.StyledProgressDialog
import com.ichi2.themes.Themes
import com.ichi2.ui.AppCompatPreferenceActivity
import com.ichi2.utils.*
import timber.log.Timber
import java.util.*

@NeedsTest("onCreate - to be done after preference migration (5019)")
@KotlinCleanup("lateinit wherever possible")
@KotlinCleanup("IDE lint")
@KotlinCleanup("All java.lang. methods")
class DeckOptions :
    AppCompatPreferenceActivity(),
    SharedPreferences.OnSharedPreferenceChangeListener {
    private lateinit var mOptions: DeckConfig
    private lateinit var mDeck: Deck
    private var mPreferenceChanged = false
    private var mUnmountReceiver: BroadcastReceiver? = null
    private lateinit var mPref: DeckPreferenceHack

    inner class DeckPreferenceHack : SharedPreferences {
        val mValues: MutableMap<String, String> = HashUtil.HashMapInit(30) // At most as many as in cacheValues
        val mSummaries: MutableMap<String, String?> = HashMap()
        lateinit var progressDialog: MaterialDialog
        private val mListeners: MutableList<SharedPreferences.OnSharedPreferenceChangeListener> = LinkedList()

        val deckOptions: DeckOptions
            get() = this@DeckOptions

        @KotlinCleanup("Use kotlin's methods instead of java's")
        @KotlinCleanup("scope function")
        fun cacheValues() {
            Timber.i("DeckOptions - CacheValues")
            try {
                mOptions = col.decks.confForDid(mDeck.getLong("id"))

                mValues["name"] = mDeck.getString("name")
                mValues["desc"] = mDeck.getString("desc")
                mValues["deckConf"] = mDeck.getString("conf")
                // general
                mValues["maxAnswerTime"] = mOptions.getString("maxTaken")
                mValues["showAnswerTimer"] = java.lang.Boolean.toString(parseTimerValue(mOptions))
                mValues["autoPlayAudio"] = java.lang.Boolean.toString(mOptions.getBoolean("autoplay"))
                mValues["replayQuestion"] = java.lang.Boolean.toString(mOptions.optBoolean("replayq", true))
                // new
                val newOptions = mOptions.getJSONObject("new")
                mValues["newSteps"] = StepsPreference.convertFromJSON(newOptions.getJSONArray("delays"))
                mValues["newGradIvl"] = newOptions.getJSONArray("ints").getString(0)
                mValues["newEasy"] = newOptions.getJSONArray("ints").getString(1)
                mValues["newFactor"] = Integer.toString(newOptions.getInt("initialFactor") / 10)
                mValues["newOrder"] = newOptions.getString("order")
                mValues["newPerDay"] = newOptions.getString("perDay")
                mValues["newBury"] = java.lang.Boolean.toString(newOptions.optBoolean("bury", true))
                // rev
                val revOptions = mOptions.getJSONObject("rev")
                mValues["revPerDay"] = revOptions.getString("perDay")
                mValues["easyBonus"] = String.format(Locale.ROOT, "%.0f", revOptions.getDouble("ease4") * 100)
                mValues["hardFactor"] = String.format(Locale.ROOT, "%.0f", revOptions.optDouble("hardFactor", 1.2) * 100)
                mValues["revIvlFct"] = String.format(Locale.ROOT, "%.0f", revOptions.getDouble("ivlFct") * 100)
                mValues["revMaxIvl"] = revOptions.getString("maxIvl")
                mValues["revBury"] = java.lang.Boolean.toString(revOptions.optBoolean("bury", true))

                mValues["revUseGeneralTimeoutSettings"] = java.lang.Boolean.toString(revOptions.optBoolean("useGeneralTimeoutSettings", true))
                mValues["revTimeoutAnswer"] = java.lang.Boolean.toString(revOptions.optBoolean("timeoutAnswer", false))
                mValues["revTimeoutAnswerSeconds"] = Integer.toString(revOptions.optInt("timeoutAnswerSeconds", 6))
                mValues["revTimeoutQuestionSeconds"] = Integer.toString(revOptions.optInt("timeoutQuestionSeconds", 60))

                // lapse
                val lapOptions = mOptions.getJSONObject("lapse")
                mValues["lapSteps"] = StepsPreference.convertFromJSON(lapOptions.getJSONArray("delays"))
                mValues["lapNewIvl"] = String.format(Locale.ROOT, "%.0f", lapOptions.getDouble("mult") * 100)
                mValues["lapMinIvl"] = lapOptions.getString("minInt")
                mValues["lapLeechThres"] = lapOptions.getString("leechFails")
                mValues["lapLeechAct"] = lapOptions.getString("leechAction")
                // options group management
                mValues["currentConf"] = col.decks.getConf(mDeck.getLong("conf"))!!.getString("name")
                // reminders
                if (mOptions.has("reminder")) {
                    val reminder = mOptions.getJSONObject("reminder")
                    val reminderTime = reminder.getJSONArray("time")

                    mValues["reminderEnabled"] = java.lang.Boolean.toString(reminder.getBoolean("enabled"))
                    mValues["reminderTime"] = String.format(
                        "%1$02d:%2$02d", reminderTime.getLong(0), reminderTime.getLong(1)
                    )
                } else {
                    mValues["reminderEnabled"] = "false"
                    mValues["reminderTime"] = TimePreference.DEFAULT_VALUE
                }
            } catch (e: JSONException) {
                Timber.e(e, "DeckOptions - cacheValues")
                CrashReportService.sendExceptionReport(e, "DeckOptions: cacheValues")
                @KotlinCleanup("Remove `val r` and access resources directly")
                val r = this@DeckOptions.resources
                UIUtils.showThemedToast(this@DeckOptions, r.getString(R.string.deck_options_corrupt, e.localizedMessage), false)
                finish()
            }
        }

        private fun parseTimerValue(options: DeckConfig): Boolean {
            return DeckConfig.parseTimerOpt(options, true)
        }

        inner class Editor : SharedPreferences.Editor {
            private var mUpdate = ContentValues()

            override fun clear(): SharedPreferences.Editor {
                Timber.d("clear()")
                mUpdate = ContentValues()
                return this
            }

            override fun commit(): Boolean {
                Timber.d("DeckOptions - commit() changes back to database")

                try {
                    for ((key, value) in mUpdate.valueSet()) {
                        Timber.i("Change value for key '%s': %s", key, value)

                        when (key) {
                            "maxAnswerTime" -> mOptions.put("maxTaken", value)
                            "newFactor" -> mOptions.getJSONObject("new").put("initialFactor", value as Int * 10)
                            "newOrder" -> {
                                val newOrder: Int = (value as String).toInt()
                                // Sorting is slow, so only do it if we change order
                                val oldOrder = mOptions.getJSONObject("new").getInt("order")
                                if (oldOrder != newOrder) {
                                    mOptions.getJSONObject("new").put("order", newOrder)
                                    TaskManager.launchCollectionTask(CollectionTask.Reorder(mOptions), confChangeHandler())
                                }
                                mOptions.getJSONObject("new").put("order", value.toInt())
                            }
                            "newPerDay" -> mOptions.getJSONObject("new").put("perDay", value)
                            "newGradIvl" -> {
                                val newInts = JSONArray() // [graduating, easy]

                                newInts.put(value)
                                newInts.put(mOptions.getJSONObject("new").getJSONArray("ints").getInt(1))
                                newInts.put(mOptions.getJSONObject("new").getJSONArray("ints").optInt(2, 7))
                                mOptions.getJSONObject("new").put("ints", newInts)
                            }
                            "newEasy" -> {
                                val newInts = JSONArray() // [graduating, easy]
                                newInts.put(mOptions.getJSONObject("new").getJSONArray("ints").getInt(0))
                                newInts.put(value)
                                newInts.put(mOptions.getJSONObject("new").getJSONArray("ints").optInt(2, 7))
                                mOptions.getJSONObject("new").put("ints", newInts)
                            }
                            "newBury" -> mOptions.getJSONObject("new").put("bury", value)
                            "revPerDay" -> mOptions.getJSONObject("rev").put("perDay", value)
                            "easyBonus" -> mOptions.getJSONObject("rev").put("ease4", (value as Int / 100.0f).toDouble())
                            "hardFactor" -> mOptions.getJSONObject("rev").put("hardFactor", (value as Int / 100.0f).toDouble())
                            "revIvlFct" -> mOptions.getJSONObject("rev").put("ivlFct", (value as Int / 100.0f).toDouble())
                            "revMaxIvl" -> mOptions.getJSONObject("rev").put("maxIvl", value)
                            "revBury" -> mOptions.getJSONObject("rev").put("bury", value)
                            "revUseGeneralTimeoutSettings" -> mOptions.getJSONObject("rev").put("useGeneralTimeoutSettings", value)
                            "revTimeoutAnswer" -> mOptions.getJSONObject("rev").put("timeoutAnswer", value)
                            "revTimeoutAnswerSeconds" -> mOptions.getJSONObject("rev").put("timeoutAnswerSeconds", value)
                            "revTimeoutQuestionSeconds" -> mOptions.getJSONObject("rev").put("timeoutQuestionSeconds", value)
                            "lapMinIvl" -> mOptions.getJSONObject("lapse").put("minInt", value)
                            "lapLeechThres" -> mOptions.getJSONObject("lapse").put("leechFails", value)
                            "lapLeechAct" -> mOptions.getJSONObject("lapse").put("leechAction", (value as String).toInt())
                            "lapNewIvl" -> mOptions.getJSONObject("lapse").put("mult", (value as Int / 100.0f).toDouble())
                            "showAnswerTimer" -> mOptions.put("timer", if (value as Boolean) 1 else 0)
                            "autoPlayAudio" -> mOptions.put("autoplay", value)
                            "replayQuestion" -> mOptions.put("replayq", value)
                            "desc" -> {
                                mDeck.put("desc", value)
                                col.decks.save(mDeck)
                            }
                            "newSteps" -> mOptions.getJSONObject("new").put("delays", StepsPreference.convertToJSON((value as String)))
                            "lapSteps" -> mOptions.getJSONObject("lapse").put("delays", StepsPreference.convertToJSON((value as String)))
                            "deckConf" -> {
                                val newConfId: Long = (value as String).toLong()
                                mOptions = col.decks.getConf(newConfId)!!
                                TaskManager.launchCollectionTask(CollectionTask.ConfChange(mDeck, mOptions), confChangeHandler())
                            }
                            "confRename" -> {
                                val newName = value as String
                                if (!TextUtils.isEmpty(newName)) {
                                    mOptions.put("name", newName)
                                }
                            }
                            "confReset" -> if (value as Boolean) {
                                TaskManager.launchCollectionTask(
                                    CollectionTask.ConfReset(
                                        mOptions
                                    ),
                                    confChangeHandler()
                                )
                            }
                            "confAdd" -> {
                                val newName = value as String
                                if (!TextUtils.isEmpty(newName)) {
                                    // New config clones current config
                                    val id = col.decks.confId(newName, mOptions.toString())
                                    mDeck.put("conf", id)
                                    col.decks.save(mDeck)
                                }
                            }
                            "confRemove" -> if (mOptions.getLong("id") == 1L) {
                                // Don't remove the options group if it's the default group
                                UIUtils.showThemedToast(
                                    this@DeckOptions,
                                    resources.getString(R.string.default_conf_delete_error), false
                                )
                            } else {
                                // Remove options group, handling the case where the user needs to confirm full sync
                                try {
                                    remConf()
                                } catch (e: ConfirmModSchemaException) {
                                    e.log()
                                    // Libanki determined that a full sync will be required, so confirm with the user before proceeding
                                    // TODO : Use ConfirmationDialog DialogFragment -- not compatible with PreferenceActivity
                                    MaterialDialog.Builder(this@DeckOptions)
                                        .content(R.string.full_sync_confirmation)
                                        .positiveText(R.string.dialog_ok)
                                        .negativeText(R.string.dialog_cancel)
                                        .onPositive { _: MaterialDialog?, _: DialogAction? ->
                                            col.modSchemaNoCheck()
                                            try {
                                                remConf()
                                            } catch (cmse: ConfirmModSchemaException) {
                                                // This should never be reached as we just forced modSchema
                                                throw RuntimeException(cmse)
                                            }
                                        }
                                        .build().show()
                                }
                            }
                            "confSetSubdecks" -> if (value as Boolean) {
                                TaskManager.launchCollectionTask(CollectionTask.ConfSetSubdecks(mDeck, mOptions), confChangeHandler())
                            }
                            "reminderEnabled" -> {
                                val reminder = JSONObject()

                                reminder.put("enabled", value)
                                if (mOptions.has("reminder")) {
                                    reminder.put("time", mOptions.getJSONObject("reminder").getJSONArray("time"))
                                } else {
                                    reminder.put(
                                        "time",
                                        JSONArray()
                                            .put(TimePreference.parseHours(TimePreference.DEFAULT_VALUE))
                                            .put(TimePreference.parseMinutes(TimePreference.DEFAULT_VALUE))
                                    )
                                }
                                mOptions.put("reminder", reminder)

                                val alarmManager = getSystemService(ALARM_SERVICE) as AlarmManager
                                val reminderIntent = CompatHelper.compat.getImmutableBroadcastIntent(
                                    applicationContext,
                                    mOptions.getLong("id").toInt(),
                                    Intent(applicationContext, ReminderService::class.java).putExtra(
                                        ReminderService.EXTRA_DECK_OPTION_ID, mOptions.getLong("id")
                                    ),
                                    0
                                )

                                alarmManager.cancel(reminderIntent)
                                if (value as Boolean) {
                                    val calendar = reminderToCalendar(col.time, reminder)

                                    alarmManager.setRepeating(
                                        AlarmManager.RTC_WAKEUP,
                                        calendar.timeInMillis,
                                        AlarmManager.INTERVAL_DAY,
                                        reminderIntent
                                    )
                                }
                            }
                            "reminderTime" -> {
                                val reminder = JSONObject()

                                reminder.put("enabled", true)
                                reminder.put(
                                    "time",
                                    JSONArray().put(TimePreference.parseHours((value as String)))
                                        .put(TimePreference.parseMinutes(value))
                                )

                                mOptions.put("reminder", reminder)
                                val alarmManager = getSystemService(ALARM_SERVICE) as AlarmManager
                                val reminderIntent = CompatHelper.compat.getImmutableBroadcastIntent(
                                    applicationContext,
                                    mOptions.getLong("id").toInt(),
                                    Intent(
                                        applicationContext,
                                        ReminderService::class.java
                                    ).putExtra(
                                        ReminderService.EXTRA_DECK_OPTION_ID,
                                        mOptions.getLong("id")
                                    ),
                                    0
                                )
                                alarmManager.cancel(reminderIntent)

                                val calendar = reminderToCalendar(col.time, reminder)

                                alarmManager.setRepeating(
                                    AlarmManager.RTC_WAKEUP,
                                    calendar.timeInMillis,
                                    AlarmManager.INTERVAL_DAY,
                                    reminderIntent
                                )
                            }
                            else -> Timber.w("Unknown key type: %s", key)
                        }
                    }
                } catch (e: JSONException) {
                    throw RuntimeException(e)
                }

                // save conf
                try {
                    col.decks.save(mOptions)
                } catch (e: RuntimeException) {
                    Timber.e(e, "DeckOptions - RuntimeException on saving conf")
                    CrashReportService.sendExceptionReport(e, "DeckOptionsSaveConf")
                    setResult(DeckPicker.RESULT_DB_ERROR)
                    finish()
                }

                // make sure we refresh the parent cached values
                cacheValues()
                buildLists()
                updateSummaries()

                // and update any listeners
                for (listener in mListeners) {
                    listener.onSharedPreferenceChanged(this@DeckPreferenceHack, null)
                }
                return true
            }

            override fun putBoolean(key: String, value: Boolean): SharedPreferences.Editor {
                mUpdate.put(key, value)
                return this
            }

            override fun putFloat(key: String, value: Float): SharedPreferences.Editor {
                mUpdate.put(key, value)
                return this
            }

            override fun putInt(key: String, value: Int): SharedPreferences.Editor {
                mUpdate.put(key, value)
                return this
            }

            override fun putLong(key: String, value: Long): SharedPreferences.Editor {
                mUpdate.put(key, value)
                return this
            }

            override fun putString(key: String, value: String?): SharedPreferences.Editor {
                mUpdate.put(key, value)
                return this
            }

            override fun remove(key: String): SharedPreferences.Editor {
                Timber.d("Editor.remove(key=%s)", key)
                mUpdate.remove(key)
                return this
            }

            override fun apply() {
                commit()
            }

            // @Override On Android 1.5 this is not Override
            override fun putStringSet(arg0: String, arg1: Set<String>?): SharedPreferences.Editor? {
                // TODO Auto-generated method stub
                return null
            }

            @Suppress("unused")
            @KotlinCleanup("maybe remove this")
            val deckPreferenceHack: DeckPreferenceHack
                get() = this@DeckPreferenceHack

            fun confChangeHandler(): ConfChangeHandler {
                return ConfChangeHandler(this@DeckPreferenceHack)
            }

            /**
             * Remove the currently selected options group
             */
            @Throws(ConfirmModSchemaException::class)
            private fun remConf() {
                // Remove options group, asking user to confirm full sync if necessary
                col.decks.remConf(mOptions.getLong("id"))
                // Run the CPU intensive re-sort operation in a background thread
                TaskManager.launchCollectionTask(CollectionTask.ConfRemove(mOptions), confChangeHandler())
                mDeck.put("conf", 1)
            }
        }

        override fun contains(key: String): Boolean {
            return mValues.containsKey(key)
        }

        override fun edit(): Editor {
            return Editor()
        }

        override fun getAll(): Map<String, *> {
            return mValues
        }

        override fun getBoolean(key: String, defValue: Boolean): Boolean {
            return java.lang.Boolean.parseBoolean(this.getString(key, java.lang.Boolean.toString(defValue)))
        }

        override fun getFloat(key: String, defValue: Float): Float {
            return this.getString(key, java.lang.Float.toString(defValue))!!.toFloat()
        }

        override fun getInt(key: String, defValue: Int): Int {
            return this.getString(key, Integer.toString(defValue))!!.toInt()
        }

        override fun getLong(key: String, defValue: Long): Long {
            return this.getString(key, java.lang.Long.toString(defValue))!!.toLong()
        }

        override fun getString(key: String, defValue: String?): String? {
            Timber.d("getString(key=%s, defValue=%s)", key, defValue)
            return if (!mValues.containsKey(key)) {
                defValue
            } else mValues[key]
        }

        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
            mListeners.add(listener)
        }

        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
            mListeners.remove(listener)
        }

        // @Override On Android 1.5 this is not Override
        override fun getStringSet(arg0: String, arg1: Set<String>?): Set<String>? {
            // TODO Auto-generated method stub
            return null
        }

        init {
            cacheValues()
        }
    }

    class ConfChangeHandler(deckPreferenceHack: DeckPreferenceHack) :
        TaskListenerWithContext<DeckPreferenceHack, Void?, Boolean?>(deckPreferenceHack) {

        override fun actualOnPreExecute(context: DeckPreferenceHack) {
            @Suppress("unused") val res = context.deckOptions.resources
            context.progressDialog = StyledProgressDialog.show(
                context.deckOptions as Context, null,
                res?.getString(R.string.reordering_cards), false
            )
        }

        @KotlinCleanup("Scope function")
        override fun actualOnPostExecute(context: DeckPreferenceHack, result: Boolean?) {
            context.cacheValues()
            context.deckOptions.buildLists()
            context.deckOptions.updateSummaries()
            context.progressDialog.dismiss()
            // Restart to reflect the new preference values
            context.deckOptions.restartActivity()
        }
    }

    override fun getSharedPreferences(name: String, mode: Int): SharedPreferences {
        Timber.d("getSharedPreferences(name=%s)", name)
        return mPref
    }

    // conversion to fragments tracked as #5019 in github
    @Deprecated("Deprecated in Java")
    override fun onCreate(savedInstanceState: Bundle?) {
        Themes.setThemeLegacy(this)
        super.onCreate(savedInstanceState)

        if (!isColInitialized()) {
            return
        }
        val extras = intent.extras
        mDeck = if (extras != null && extras.containsKey("did")) {
            col.decks.get(extras.getLong("did"))
        } else {
            col.decks.current()
        }
        registerExternalStorageListener()

        mPref = DeckPreferenceHack()
        // #6068 - constructor can call finish()
        if (this.isFinishing) {
            return
        }
        mPref.registerOnSharedPreferenceChangeListener(this)

        addPreferencesFromResource(R.xml.deck_options)
        if (isSchedV2) {
            enableSchedV2Preferences()
        }
        buildLists()
        updateSummaries()
        // Set the activity title to include the name of the deck
        var title = resources.getString(R.string.deckpreferences_title)
        if (title.contains("XXX")) {
            title = try {
                title.replace("XXX", mDeck.getString("name"))
            } catch (e: JSONException) {
                Timber.w(e)
                title.replace("XXX", "???")
            }
        }
        setTitle(title)

        // Add a home button to the actionbar
        supportActionBar!!.setHomeButtonEnabled(true)
        supportActionBar!!.setDisplayHomeAsUpEnabled(true)
    }

    @Deprecated("Deprecated in Java") // TODO Tracked in https://github.com/ankidroid/Anki-Android/issues/5019
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            closeWithResult()
            return true
        }
        return false
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String?) {
        // update values on changed preference
        mPreferenceChanged = true
        updateSummaries()
    }

    // Workaround for bug 4611: http://code.google.com/p/android/issues/detail?id=4611
    @Deprecated("Deprecated in Java") // TODO Tracked in https://github.com/ankidroid/Anki-Android/issues/5019
    override fun onPreferenceTreeClick(preferenceScreen: PreferenceScreen, preference: Preference): Boolean {
        super.onPreferenceTreeClick(preferenceScreen, preference)
        if (preference is PreferenceScreen && preference.dialog != null) {
            preference.dialog.window!!.decorView.setBackgroundDrawable(
                this.window.decorView.background.constantState!!.newDrawable()
            )
        }

        return false
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK && event.repeatCount == 0) {
            Timber.i("DeckOptions - onBackPressed()")
            closeWithResult()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun closeWithResult() {
        if (mPreferenceChanged) {
            setResult(RESULT_OK)
        } else {
            setResult(RESULT_CANCELED)
        }
        finish()
        ActivityTransitionAnimation.slide(this, FADE)
    }

    @Deprecated("Deprecated in Java")
    override fun onDestroy() {
        super.onDestroy()
        if (mUnmountReceiver != null) {
            unregisterReceiver(mUnmountReceiver)
        }
    }

    // TODO Tracked in https://github.com/ankidroid/Anki-Android/issues/5019
    @KotlinCleanup("remove reduntant val res = resources")
    protected fun updateSummaries() {
        val res = resources
        // for all text preferences, set summary as current database value
        for (key in mPref.mValues.keys) {
            val pref = findPreference(key)
            if ("deckConf" == key) {
                var groupName = optionsGroupName
                val count = optionsGroupCount
                // Escape "%" in groupName as it's treated as a token
                groupName = groupName.replace("%".toRegex(), "%%")
                pref!!.summary = res.getQuantityString(R.plurals.deck_conf_group_summ, count, groupName, count)
                continue
            }

            val value: String? = if (pref == null) {
                continue
            } else if (pref is CheckBoxPreference) {
                continue
            } else if (pref is ListPreference) {
                val lp = pref
                if (lp.entry != null) lp.entry.toString() else ""
            } else {
                mPref.getString(key, "")
            }
            // update summary
            @KotlinCleanup("Remove `val s` and use pref.summary?.toString()")
            if (!mPref.mSummaries.containsKey(key)) {
                val s = pref.summary
                mPref.mSummaries[key] = if (s != null) pref.summary.toString() else null
            }
            val summ = mPref.mSummaries[key]
            @KotlinCleanup("pref.summary = if ...")
            if (summ != null && summ.contains("XXX")) {
                pref.summary = summ.replace("XXX", value!!)
            } else {
                pref.summary = value
            }
        }
        // Update summaries of preference items that don't have values (aren't in mValues)
        val subDeckCount = subdeckCount
        findPreference("confSetSubdecks").summary = res.getQuantityString(R.plurals.deck_conf_set_subdecks_summ, subDeckCount, subDeckCount)
    }

    // TODO Tracked in https://github.com/ankidroid/Anki-Android/issues/5019
    @KotlinCleanup("scope functions")
    protected fun buildLists() {
        val deckConfPref = findPreference("deckConf") as ListPreference
        val confs = col.decks.allConf()
        Collections.sort(confs, NamedJSONComparator.INSTANCE)
        val confValues = arrayOfNulls<String>(confs.size)
        val confLabels = arrayOfNulls<String>(confs.size)
        @KotlinCleanup(".forEachIndexed")
        for (i in confs.indices) {
            val o = confs[i]
            confValues[i] = o.getString("id")
            confLabels[i] = o.getString("name")
        }
        deckConfPref.entries = confLabels
        deckConfPref.entryValues = confValues
        deckConfPref.value = mPref.getString("deckConf", "0")

        val newOrderPref = findPreference("newOrder") as ListPreference
        newOrderPref.setEntries(R.array.new_order_labels)
        newOrderPref.setEntryValues(R.array.new_order_values)
        newOrderPref.value = mPref.getString("newOrder", "0")

        val leechActPref = findPreference("lapLeechAct") as ListPreference
        leechActPref.setEntries(R.array.leech_action_labels)
        leechActPref.setEntryValues(R.array.leech_action_values)
        leechActPref.value = mPref.getString(
            "lapLeechAct",
            Integer.toString(Consts.LEECH_SUSPEND)
        )
    }

    private val isSchedV2: Boolean
        get() = col.schedVer() == 2

    /**
     * Enable deck preferences that are only available with Scheduler V2.
     */
    // TODO Tracked in https://github.com/ankidroid/Anki-Android/issues/5019
    private fun enableSchedV2Preferences() {
        val hardFactorPreference = findPreference("hardFactor") as NumberRangePreference
        hardFactorPreference.isEnabled = true
    }

    /**
     * Returns the number of decks using the options group of the current deck.
     */
    private val optionsGroupCount: Int
        get() {
            var count = 0
            val conf = mDeck.getLong("conf")
            @KotlinCleanup("Join both if blocks")
            for (deck in col.decks.all()) {
                if (deck.isDyn) {
                    continue
                }
                if (deck.getLong("conf") == conf) {
                    count++
                }
            }
            return count
        }

    /**
     * Get the name of the currently set options group
     */
    private val optionsGroupName: String
        get() {
            val confId = mPref.getLong("deckConf", 0)
            return col.decks.getConf(confId)!!.getString("name")
        }

    /**
     * Get the number of (non-dynamic) subdecks for the current deck
     */
    @KotlinCleanup("Use .count{}")
    private val subdeckCount: Int
        get() {
            var count = 0
            val did = mDeck.getLong("id")
            val children = col.decks.children(did)
            for (childDid in children.values) {
                val child = col.decks.get(childDid)
                if (child.isDyn) {
                    continue
                }
                count++
            }
            return count
        }

    /**
     * finish when sd card is ejected
     */
    private fun registerExternalStorageListener() {
        if (mUnmountReceiver == null) {
            mUnmountReceiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    if (SdCardReceiver.MEDIA_EJECT == intent.action) {
                        finish()
                    }
                }
            }
            val iFilter = IntentFilter()
            iFilter.addAction(SdCardReceiver.MEDIA_EJECT)
            registerReceiver(mUnmountReceiver, iFilter)
        }
    }

    private fun restartActivity() {
        recreate()
    }

    companion object {
        fun reminderToCalendar(time: Time, reminder: JSONObject): Calendar {

            val calendar = time.calendar()

            calendar[Calendar.HOUR_OF_DAY] = reminder.getJSONArray("time").getInt(0)
            calendar[Calendar.MINUTE] = reminder.getJSONArray("time").getInt(1)
            calendar[Calendar.SECOND] = 0
            return calendar
        }
    }
}
