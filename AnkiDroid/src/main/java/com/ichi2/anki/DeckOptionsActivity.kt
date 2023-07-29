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
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.preference.CheckBoxPreference
import android.preference.ListPreference
import android.preference.Preference
import android.preference.PreferenceScreen
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.app.PendingIntentCompat
import com.ichi2.anim.ActivityTransitionAnimation
import com.ichi2.anim.ActivityTransitionAnimation.Direction.FADE
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.exception.ConfirmModSchemaException
import com.ichi2.anki.services.ReminderService
import com.ichi2.annotations.NeedsTest
import com.ichi2.async.changeDeckConfiguration
import com.ichi2.libanki.Collection
import com.ichi2.libanki.Consts
import com.ichi2.libanki.DeckConfig
import com.ichi2.libanki.utils.Time
import com.ichi2.libanki.utils.TimeManager
import com.ichi2.preferences.NumberRangePreference
import com.ichi2.preferences.StepsPreference
import com.ichi2.preferences.TimePreference
import com.ichi2.themes.StyledProgressDialog
import com.ichi2.themes.Themes
import com.ichi2.ui.AppCompatPreferenceActivity
import com.ichi2.utils.*
import com.ichi2.utils.KotlinCleanup
import com.ichi2.utils.NamedJSONComparator
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import timber.log.Timber
import java.util.*

@NeedsTest("onCreate - to be done after preference migration (5019)")
@KotlinCleanup("lateinit wherever possible")
class DeckOptionsActivity :
    AppCompatPreferenceActivity<DeckOptionsActivity.DeckPreferenceHack>() {
    private lateinit var mOptions: DeckConfig

    inner class DeckPreferenceHack : AppCompatPreferenceActivity<DeckOptionsActivity.DeckPreferenceHack>.AbstractPreferenceHack() {
        @Suppress("Deprecation")
        lateinit var progressDialog: android.app.ProgressDialog
        private val mListeners: MutableList<SharedPreferences.OnSharedPreferenceChangeListener> = LinkedList()

        private val deckOptionsActivity: DeckOptionsActivity
            get() = this@DeckOptionsActivity

        override fun cacheValues() {
            Timber.i("DeckOptions - CacheValues")
            try {
                mOptions = col.decks.confForDid(deck.getLong("id"))

                mValues.apply {
                    set("name", deck.getString("name"))
                    set("desc", deck.getString("desc"))
                    set("deckConf", deck.getString("conf"))
                    // general
                    set("maxAnswerTime", mOptions.getString("maxTaken"))
                    set("showAnswerTimer", parseTimerValue(mOptions).toString())
                    set("autoPlayAudio", mOptions.getBoolean("autoplay").toString())
                    set("replayQuestion", mOptions.optBoolean("replayq", true).toString())
                }

                // new
                val newOptions = mOptions.getJSONObject("new")
                mValues.apply {
                    set("newSteps", StepsPreference.convertFromJSON(newOptions.getJSONArray("delays")))
                    set("newGradIvl", newOptions.getJSONArray("ints").getString(0))
                    set("newEasy", newOptions.getJSONArray("ints").getString(1))
                    set("newFactor", (newOptions.getInt("initialFactor") / 10).toString())
                    set("newOrder", newOptions.getString("order"))
                    set("newPerDay", newOptions.getString("perDay"))
                    set("newBury", newOptions.optBoolean("bury", true).toString())
                }
                // rev
                val revOptions = mOptions.getJSONObject("rev")
                mValues.apply {
                    set("revPerDay", revOptions.getString("perDay"))
                    set("easyBonus", String.format(Locale.ROOT, "%.0f", revOptions.getDouble("ease4") * 100))
                    set("hardFactor", String.format(Locale.ROOT, "%.0f", revOptions.optDouble("hardFactor", 1.2) * 100))
                    set("revIvlFct", String.format(Locale.ROOT, "%.0f", revOptions.getDouble("ivlFct") * 100))
                    set("revMaxIvl", revOptions.getString("maxIvl"))
                    set("revBury", revOptions.optBoolean("bury", true).toString())

                    set("revUseGeneralTimeoutSettings", revOptions.optBoolean("useGeneralTimeoutSettings", true).toString())
                    set("revTimeoutAnswer", revOptions.optBoolean("timeoutAnswer", false).toString())
                    set("revTimeoutAnswerSeconds", revOptions.optInt("timeoutAnswerSeconds", 6).toString())
                    set("revTimeoutQuestionSeconds", revOptions.optInt("timeoutQuestionSeconds", 60).toString())
                }
                // lapse
                val lapOptions = mOptions.getJSONObject("lapse")
                mValues.apply {
                    set("lapSteps", StepsPreference.convertFromJSON(lapOptions.getJSONArray("delays")))
                    set("lapNewIvl", String.format(Locale.ROOT, "%.0f", lapOptions.getDouble("mult") * 100))
                    set("lapMinIvl", lapOptions.getString("minInt"))
                    set("lapLeechThres", lapOptions.getString("leechFails"))
                    set("lapLeechAct", lapOptions.getString("leechAction"))
                    // options group management
                    set("currentConf", col.decks.getConf(deck.getLong("conf")).getString("name"))
                }
                // reminders
                if (mOptions.has("reminder")) {
                    val reminder = mOptions.getJSONObject("reminder")
                    val reminderTime = reminder.getJSONArray("time")

                    mValues["reminderEnabled"] = reminder.getBoolean("enabled").toString()
                    mValues["reminderTime"] = String.format(
                        "%1$02d:%2$02d",
                        reminderTime.getLong(0),
                        reminderTime.getLong(1)
                    )
                } else {
                    mValues["reminderEnabled"] = "false"
                    mValues["reminderTime"] = TimePreference.DEFAULT_VALUE
                }
            } catch (e: JSONException) {
                Timber.e(e, "DeckOptions - cacheValues")
                CrashReportService.sendExceptionReport(e, "DeckOptions: cacheValues")
                UIUtils.showThemedToast(this@DeckOptionsActivity, this@DeckOptionsActivity.resources.getString(R.string.deck_options_corrupt, e.localizedMessage), false)
                finish()
            }
        }

        private fun parseTimerValue(options: DeckConfig): Boolean {
            return DeckConfig.parseTimerOpt(options, true)
        }

        fun confChangeHandler(timbering: String, block: Collection.() -> Unit) {
            launch(getCoroutineExceptionHandler(this@DeckOptionsActivity)) {
                preConfChange()
                Timber.d(timbering)
                try {
                    withCol(block)
                } finally {
                    // need to call postConfChange in finally because if withCol{} throws an exception,
                    // postConfChange would never get called and progress-bar will never get dismissed
                    postConfChange()
                }
            }
        }

        fun preConfChange() {
            val res = deckOptionsActivity.resources
            progressDialog = StyledProgressDialog.show(
                deckOptionsActivity as Context,
                null,
                res?.getString(R.string.reordering_cards),
                false
            )
        }

        fun postConfChange() {
            cacheValues()
            deckOptionsActivity.buildLists()
            deckOptionsActivity.updateSummaries()
            progressDialog.dismiss()
            // Restart to reflect the new preference values
            deckOptionsActivity.restartActivity()
        }

        inner class Editor : AppCompatPreferenceActivity<DeckOptionsActivity.DeckPreferenceHack>.AbstractPreferenceHack.Editor() {
            override fun commit(): Boolean {
                Timber.d("DeckOptions - commit() changes back to database")

                try {
                    for ((key, value) in update.valueSet()) {
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
                                    confChangeHandler("doInBackground - reorder") {
                                        sched.resortConf(mOptions)
                                    }
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
                                deck.put("desc", value)
                                col.decks.save(deck)
                            }
                            "newSteps" -> mOptions.getJSONObject("new").put("delays", StepsPreference.convertToJSON((value as String)))
                            "lapSteps" -> mOptions.getJSONObject("lapse").put("delays", StepsPreference.convertToJSON((value as String)))
                            // TODO: Extract out deckConf, confReset, remConf and confSetSubdecks to a function. They are overall similar.
                            "deckConf" -> {
                                val newConfId: Long = (value as String).toLong()
                                confChangeHandler("change Deck configuration") {
                                    mOptions = decks.getConf(newConfId)
                                    changeDeckConfiguration(deck, mOptions, this)
                                }
                            }
                            "confRename" -> {
                                val newName = value as String
                                if (newName.isNotEmpty()) {
                                    mOptions.put("name", newName)
                                }
                            }
                            "confReset" -> if (value as Boolean) {
                                // reset configuration
                                confChangeHandler("doInBackgroundConfReset") {
                                    decks.restoreToDefault(mOptions)
                                    save()
                                }
                            }
                            "confAdd" -> {
                                val newName = value as String
                                if (newName.isNotEmpty()) {
                                    // New config clones current config
                                    val id = col.decks.confId(newName, mOptions.toString())
                                    deck.put("conf", id)
                                    col.decks.save(deck)
                                }
                            }
                            "confRemove" -> if (mOptions.getLong("id") == 1L) {
                                // Don't remove the options group if it's the default group
                                UIUtils.showThemedToast(
                                    this@DeckOptionsActivity,
                                    resources.getString(R.string.default_conf_delete_error),
                                    false
                                )
                            } else {
                                // Remove options group, handling the case where the user needs to confirm full sync
                                try {
                                    remConf()
                                } catch (e: ConfirmModSchemaException) {
                                    e.log()
                                    // Libanki determined that a full sync will be required, so confirm with the user before proceeding
                                    // TODO : Use ConfirmationDialog DialogFragment -- not compatible with PreferenceActivity
                                    AlertDialog.Builder(this@DeckOptionsActivity).show {
                                        message(R.string.full_sync_confirmation)
                                        positiveButton(R.string.dialog_ok) {
                                            col.modSchemaNoCheck()
                                            try {
                                                remConf()
                                            } catch (cmse: ConfirmModSchemaException) {
                                                // This should never be reached as we just forced modSchema
                                                throw RuntimeException(cmse)
                                            }
                                        }
                                        negativeButton(R.string.dialog_cancel)
                                    }
                                }
                            }
                            "confSetSubdecks" -> if (value as Boolean) {
                                launch(getCoroutineExceptionHandler(this@DeckOptionsActivity)) {
                                    preConfChange()
                                    try {
                                        withCol {
                                            Timber.d("confSetSubdecks")
                                            val children = col.decks.children(deck.getLong("id"))
                                            for (childDid in children.values) {
                                                val child = col.decks.get(childDid)
                                                if (child.isDyn) continue
                                                changeDeckConfiguration(child, mOptions, col)
                                            }
                                        }
                                    } finally {
                                        postConfChange()
                                    }
                                }
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
                                val reminderIntent = PendingIntentCompat.getBroadcast(
                                    applicationContext,
                                    mOptions.getLong("id").toInt(),
                                    Intent(applicationContext, ReminderService::class.java).putExtra(
                                        ReminderService.EXTRA_DECK_OPTION_ID,
                                        mOptions.getLong("id")
                                    ),
                                    0,
                                    false
                                )

                                alarmManager.cancel(reminderIntent)
                                if (value as Boolean) {
                                    val calendar = reminderToCalendar(TimeManager.time, reminder)

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
                                val reminderIntent = PendingIntentCompat.getBroadcast(
                                    applicationContext,
                                    mOptions.getLong("id").toInt(),
                                    Intent(
                                        applicationContext,
                                        ReminderService::class.java
                                    ).putExtra(
                                        ReminderService.EXTRA_DECK_OPTION_ID,
                                        mOptions.getLong("id")
                                    ),
                                    0,
                                    false
                                )
                                alarmManager.cancel(reminderIntent)

                                val calendar = reminderToCalendar(TimeManager.time, reminder)

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

            /**
             * Remove the currently selected options group
             */
            @Throws(ConfirmModSchemaException::class)
            private fun remConf() {
                // Remove options group, asking user to confirm full sync if necessary
                col.decks.remConf(mOptions.getLong("id"))
                // Run the CPU intensive re-sort operation in a background thread
                val conf = mOptions
                confChangeHandler("Remove configuration") {
                    // Note: We do the actual removing of the options group in the main thread so that we
                    // can ask the user to confirm if they're happy to do a full sync, and just do the resorting here

                    // When a conf is deleted, all decks using it revert to the default conf.
                    // Cards must be reordered according to the default conf.
                    val order = conf.getJSONObject("new").getInt("order")
                    val defaultOrder =
                        col.decks.getConf(1).getJSONObject("new").getInt("order")
                    if (order != defaultOrder) {
                        conf.getJSONObject("new").put("order", defaultOrder)
                        col.sched.resortConf(conf)
                    }
                    col.save()
                }
                deck.put("conf", 1)
            }
        }

        override fun edit(): Editor {
            return Editor()
        }
    }

    // conversion to fragments tracked as #5019 in github
    @Deprecated("Deprecated in Java")
    override fun onCreate(savedInstanceState: Bundle?) {
        Themes.setTheme(this)
        Themes.setLegacyActionBar(this)
        super.onCreate(savedInstanceState)

        if (!isColInitialized()) {
            return
        }
        val extras = intent.extras
        deck = if (extras != null && extras.containsKey("did")) {
            col.decks.get(extras.getLong("did"))
        } else {
            col.decks.current()
        }
        registerExternalStorageListener()

        pref = DeckPreferenceHack()
        // #6068 - constructor can call finish()
        if (this.isFinishing) {
            return
        }
        pref.registerOnSharedPreferenceChangeListener(this)

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
                title.replace("XXX", deck.getString("name"))
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

    override fun closeWithResult() {
        if (prefChanged) {
            setResult(RESULT_OK)
        } else {
            setResult(RESULT_CANCELED)
        }
        finish()
        ActivityTransitionAnimation.slide(this, FADE)
    }

    // TODO Tracked in https://github.com/ankidroid/Anki-Android/issues/5019
    override fun updateSummaries() {
        // for all text preferences, set summary as current database value
        for (key in pref.mValues.keys) {
            val pref = findPreference(key)
            if ("deckConf" == key) {
                var groupName = optionsGroupName
                val count = optionsGroupCount
                // Escape "%" in groupName as it's treated as a token
                groupName = groupName.replace("%".toRegex(), "%%")
                pref!!.summary = resources.getQuantityString(R.plurals.deck_conf_group_summ, count, groupName, count)
                continue
            }

            val value: String? = if (pref == null) {
                continue
            } else if (pref is CheckBoxPreference) {
                continue
            } else if (pref is ListPreference) {
                if (pref.entry != null) pref.entry.toString() else ""
            } else {
                this.pref.getString(key, "")
            }
            // update summary
            if (!this.pref.mSummaries.containsKey(key)) {
                this.pref.mSummaries[key] = pref.summary?.toString()
            }
            val summ = this.pref.mSummaries[key]
            pref.summary = if (summ != null && summ.contains("XXX")) {
                summ.replace("XXX", value!!)
            } else {
                value
            }
        }
        // Update summaries of preference items that don't have values (aren't in mValues)
        val subDeckCount = subdeckCount
        findPreference("confSetSubdecks").summary = resources.getQuantityString(R.plurals.deck_conf_set_subdecks_summ, subDeckCount, subDeckCount)
    }

    // TODO Tracked in https://github.com/ankidroid/Anki-Android/issues/5019
    private fun buildLists() {
        val deckConfPref = findPreference("deckConf") as ListPreference
        val confs = col.decks.allConf()
        Collections.sort(confs, NamedJSONComparator.INSTANCE)
        val confValues = arrayOfNulls<String>(confs.size)
        val confLabels = arrayOfNulls<String>(confs.size)
        confs.forEachIndexed { index, deckConfig ->
            confValues[index] = deckConfig.getString("id")
            confLabels[index] = deckConfig.getString("name")
        }

        deckConfPref.apply {
            entries = confLabels
            entryValues = confValues
            value = pref.getString("deckConf", "0")
        }

        val newOrderPref = findPreference("newOrder") as ListPreference
        newOrderPref.apply {
            setEntries(R.array.new_order_labels)
            setEntryValues(R.array.new_order_values)
            value = pref.getString("newOrder", "0")
        }

        val leechActPref = findPreference("lapLeechAct") as ListPreference
        leechActPref.apply {
            setEntries(R.array.leech_action_labels)
            setEntryValues(R.array.leech_action_values)
            value = pref.getString(
                "lapLeechAct",
                Consts.LEECH_SUSPEND.toString()
            )
        }
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
            val conf = deck.getLong("conf")
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
            val confId = pref.getLong("deckConf", 0)
            return col.decks.getConf(confId).getString("name")
        }

    /**
     * Get the number of (non-dynamic) subdecks for the current deck
     */
    @KotlinCleanup("Use .count{}")
    private val subdeckCount: Int
        get() {
            var count = 0
            val did = deck.getLong("id")
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

    private fun restartActivity() {
        ActivityCompat.recreate(this)
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
