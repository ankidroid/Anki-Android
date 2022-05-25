package com.ichi2.anki

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

import android.content.*
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import android.os.Bundle
import android.preference.*
import android.view.KeyEvent
import android.view.MenuItem
import com.ichi2.anim.ActivityTransitionAnimation
import com.ichi2.anim.ActivityTransitionAnimation.slide
import com.ichi2.anki.analytics.UsageAnalytics
import com.ichi2.anki.receiver.SdCardReceiver
import com.ichi2.annotations.NeedsTest
import com.ichi2.libanki.Collection
import com.ichi2.libanki.Deck
import com.ichi2.preferences.StepsPreference.Companion.convertFromJSON
import com.ichi2.preferences.StepsPreference.Companion.convertToJSON
import com.ichi2.themes.Themes.setThemeLegacy
import com.ichi2.ui.AppCompatPreferenceActivity
import com.ichi2.utils.JSONArray
import com.ichi2.utils.JSONException
import com.ichi2.utils.JSONObject
import com.ichi2.utils.KotlinCleanup
import timber.log.Timber
import java.util.*

@NeedsTest("construction + onCreate - do this after converting to fragment-based preferences.")
class FilteredDeckOptions : AppCompatPreferenceActivity(), OnSharedPreferenceChangeListener {
    @KotlinCleanup("try to make mDeck non-null / use lateinit")
    private var mDeck: Deck? = null
    private var mCol: Collection? = null
    private var mAllowCommit = true
    private var mPrefChanged = false
    private var mUnmountReceiver: BroadcastReceiver? = null

    // TODO: not anymore used in libanki?
    private val mDynExamples = arrayOf(
        null,
        "{'search'=\"is:new\", 'resched'=False, 'steps'=\"1\", 'order'=5}",
        "{'search'=\"added:1\", 'resched'=False, 'steps'=\"1\", 'order'=5}",
        "{'search'=\"rated:1:1\", 'order'=4}",
        "{'search'=\"prop:due<=2\", 'order'=6}",
        "{'search'=\"is:due tag:TAG\", 'order'=6}",
        "{'search'=\"is:due\", 'order'=3}",
        "{'search'=\"\", 'steps'=\"1 10 20\", 'order'=0}"
    )

    inner class DeckPreferenceHack : SharedPreferences {
        val mValues: MutableMap<String, String> = HashMap()
        val mSummaries: MutableMap<String, String?> = HashMap()
        var secondFilter = false
        protected fun cacheValues() {
            Timber.d("cacheValues()")
            val ar = mDeck!!.getJSONArray("terms").getJSONArray(0)
            secondFilter = if (mDeck!!.getJSONArray("terms").length() > 1) true else false
            var ar2: JSONArray?
            mValues["search"] = ar.getString(0)
            mValues["limit"] = ar.getString(1)
            mValues["order"] = ar.getString(2)
            if (secondFilter) {
                ar2 = mDeck!!.getJSONArray("terms").getJSONArray(1)
                mValues["search_2"] = ar2.getString(0)
                mValues["limit_2"] = ar2.getString(1)
                mValues["order_2"] = ar2.getString(2)
            }
            val delays = mDeck!!.optJSONArray("delays")
            if (delays != null) {
                mValues["steps"] = convertFromJSON(delays)
                mValues["stepsOn"] = java.lang.Boolean.toString(true)
            } else {
                mValues["steps"] = "1 10"
                mValues["stepsOn"] = java.lang.Boolean.toString(false)
            }
            mValues["resched"] = java.lang.Boolean.toString(mDeck!!.getBoolean("resched"))
        }

        inner class Editor : SharedPreferences.Editor {
            private var mUpdate = ContentValues()
            override fun clear(): SharedPreferences.Editor {
                Timber.d("clear()")
                mUpdate = ContentValues()
                return this
            }

            override fun commit(): Boolean {
                Timber.d("commit() changes back to database")
                for ((key, value) in mUpdate.valueSet()) {
                    Timber.i("Change value for key '%s': %s", key, value)
                    val ar = mDeck!!.getJSONArray("terms")
                    if (mPref!!.secondFilter) {
                        if ("search_2" == key) {
                            ar.getJSONArray(1).put(0, value)
                        } else if ("limit_2" == key) {
                            ar.getJSONArray(1).put(1, value)
                        } else if ("order_2" == key) {
                            ar.getJSONArray(1).put(2, (value as String).toInt())
                        }
                    }
                    if ("search" == key) {
                        ar.getJSONArray(0).put(0, value)
                    } else if ("limit" == key) {
                        ar.getJSONArray(0).put(1, value)
                    } else if ("order" == key) {
                        ar.getJSONArray(0).put(2, (value as String).toInt())
                    } else if ("resched" == key) {
                        mDeck!!.put("resched", value)
                    } else if ("stepsOn" == key) {
                        val on = value as Boolean
                        if (on) {
                            val steps = convertToJSON(mValues["steps"]!!)
                            if (steps!!.length() > 0) {
                                mDeck!!.put("delays", steps)
                            }
                        } else {
                            mDeck!!.put("delays", JSONObject.NULL)
                        }
                    } else if ("steps" == key) {
                        mDeck!!.put("delays", convertToJSON((value as String)))
                    } else if ("preset" == key) {
                        val i: Int = (value as String).toInt()
                        if (i > 0) {
                            val presetValues = JSONObject(mDynExamples[i])
                            val arr = presetValues.names() ?: continue
                            for (name in arr.stringIterable()) {
                                if ("steps" == name) {
                                    mUpdate.put("stepsOn", true)
                                }
                                if ("resched" == name) {
                                    mUpdate.put(name, presetValues.getBoolean(name))
                                    mValues[name] = java.lang.Boolean.toString(presetValues.getBoolean(name))
                                } else {
                                    mUpdate.put(name, presetValues.getString(name))
                                    mValues[name] = presetValues.getString(name)
                                }
                            }
                            mUpdate.put("preset", "0")
                            commit()
                        }
                    }
                }

                // save deck
                try {
                    mCol!!.decks.save(mDeck!!)
                } catch (e: RuntimeException) {
                    Timber.e(e, "RuntimeException on saving deck")
                    CrashReportService.sendExceptionReport(e, "FilteredDeckOptionsSaveDeck")
                    setResult(DeckPicker.RESULT_DB_ERROR)
                    finish()
                }

                // make sure we refresh the parent cached values
                cacheValues()
                updateSummaries()

                // and update any listeners
                for (listener in listeners) {
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
                if (mAllowCommit) {
                    commit()
                }
            }

            // @Override On Android 1.5 this is not Override
            override fun putStringSet(arg0: String, arg1: Set<String>?): SharedPreferences.Editor? {
                // TODO Auto-generated method stub
                return null
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

        val listeners: MutableList<OnSharedPreferenceChangeListener> = LinkedList()
        override fun registerOnSharedPreferenceChangeListener(listener: OnSharedPreferenceChangeListener) {
            listeners.add(listener)
        }

        override fun unregisterOnSharedPreferenceChangeListener(listener: OnSharedPreferenceChangeListener) {
            listeners.remove(listener)
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

    private var mPref: DeckPreferenceHack? = null
    override fun getSharedPreferences(name: String, mode: Int): SharedPreferences {
        Timber.d("getSharedPreferences(name=%s)", name)
        return mPref!!
    }

    @Deprecated("Deprecated in Java")
    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        setThemeLegacy(this)
        super.onCreate(savedInstanceState)
        UsageAnalytics.sendAnalyticsScreenView(this)
        mCol = CollectionHelper.getInstance().getCol(this)
        if (mCol == null) {
            finish()
            return
        }
        val extras = intent.extras
        mDeck = if (extras != null && extras.containsKey("did")) {
            mCol!!.decks.get(extras.getLong("did"))
        } else {
            mCol!!.decks.current()
        }
        registerExternalStorageListener()
        if (mCol == null || mDeck!!.isStd) {
            Timber.w("No Collection loaded or deck is not a dyn deck")
            finish()
            return
        } else {
            mPref = DeckPreferenceHack()
            mPref!!.registerOnSharedPreferenceChangeListener(this)
            addPreferences(mCol!!)
            buildLists()
            updateSummaries()
        }

        // Set the activity title to include the name of the deck
        var title = resources.getString(R.string.deckpreferences_title)
        if (title.contains("XXX")) {
            title = try {
                title.replace("XXX", mDeck!!.getString("name"))
            } catch (e: JSONException) {
                Timber.w(e)
                title.replace("XXX", "???")
            }
        }
        this.title = title

        // Add a home button to the actionbar
        supportActionBar?.setHomeButtonEnabled(true)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    @Suppress("deprecation") // Tracked as #5019 on github: addPreferencesFromResource
    private fun addPreferences(col: Collection) {
        addPreferencesFromResource(R.xml.cram_deck_options)
        if (col.schedVer() != 1) {
            Timber.d("sched v2: removing filtered deck custom study steps")
            // getPreferenceScreen.removePreference didn't return true, so remove from the category
            setupSecondFilterListener()
            val category = findPreference("studyOptions") as PreferenceCategory
            removePreference(category, "stepsOn")
            removePreference(category, "steps")
        }
    }

    @Suppress("deprecation") // Tracked as #5019 on github: findPreference
    private fun removePreference(category: PreferenceCategory?, key: String) {
        val preference = findPreference(key)
        if (category == null || preference == null) {
            Timber.w("Failed to remove preference '%s'", key)
            return
        }
        val result = category.removePreference(preference)
        if (!result) {
            Timber.w("Failed to remove preference '%s'", key)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            closeDeckOptions()
            return true
        }
        return false
    }

    @KotlinCleanup("Find a different method rather than providing a null key in a caller")
    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String?) {
        // update values on changed preference
        updateSummaries()
        mPrefChanged = true
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK && event.repeatCount == 0) {
            Timber.i("DeckOptions - onBackPressed()")
            closeDeckOptions()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun closeDeckOptions() {
        if (mPrefChanged) {
            // Rebuild the filtered deck if a setting has changed
            try {
                mCol!!.sched.rebuildDyn(mDeck!!.getLong("id"))
            } catch (e: JSONException) {
                Timber.e(e)
            }
        }
        finish()
        slide(this, ActivityTransitionAnimation.Direction.FADE)
    }

    @Deprecated("Deprecated in Java")
    override fun onDestroy() {
        @Suppress("DEPRECATION")
        super.onDestroy()
        if (mUnmountReceiver != null) {
            unregisterReceiver(mUnmountReceiver)
        }
    }

    @Suppress("deprecation") // conversion to fragments tracked in github as #5019
    protected fun updateSummaries() {
        mAllowCommit = false
        // for all text preferences, set summary as current database value
        val keys: Set<String> = mPref!!.mValues.keys
        for (key in keys) {
            val pref = findPreference(key)
            var value: String?
            value = if (pref == null) {
                continue
            } else if (pref is CheckBoxPreference) {
                continue
            } else if (pref is ListPreference) {
                val entry = pref.entry
                entry?.toString() ?: ""
            } else {
                mPref!!.getString(key, "")
            }
            // update value for EditTexts
            if (pref is EditTextPreference) {
                pref.text = value
            }
            // update summary
            if (!mPref!!.mSummaries.containsKey(key)) {
                val s = pref.summary
                mPref!!.mSummaries[key] = if (s != null) pref.summary.toString() else null
            }
            val summ = mPref!!.mSummaries[key]
            if (summ != null && summ.contains("XXX")) {
                pref.summary = summ.replace("XXX", value!!)
            } else {
                pref.summary = value
            }
        }
        mAllowCommit = true
    }

    @Suppress("deprecation") // Tracked as #5019 on github
    protected fun buildLists() {
        val newOrderPref = findPreference("order") as ListPreference
        val newOrderPrefSecond = findPreference("order_2") as ListPreference
        newOrderPref.setEntries(R.array.cram_deck_conf_order_labels)
        newOrderPref.setEntryValues(R.array.cram_deck_conf_order_values)
        newOrderPref.value = mPref!!.getString("order", "0")
        newOrderPrefSecond.setEntries(R.array.cram_deck_conf_order_labels)
        newOrderPrefSecond.setEntryValues(R.array.cram_deck_conf_order_values)
        newOrderPrefSecond.value = mPref!!.getString("order_2", "5")
    }

    /**
     * Call exactly once, during creation
     * to ensure that if the SD card is ejected
     * this activity finish.
     */
    private fun registerExternalStorageListener() {
        mUnmountReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == SdCardReceiver.MEDIA_EJECT) {
                    finish()
                }
            }
        }
        val iFilter = IntentFilter()
        iFilter.addAction(SdCardReceiver.MEDIA_EJECT)
        registerReceiver(mUnmountReceiver, iFilter)
    }

    @Suppress("deprecation")
    private fun setupSecondFilterListener() {
        val secondFilterSign = findPreference("filterSecond") as CheckBoxPreference
        val secondFilter = findPreference("secondFilter") as PreferenceCategory
        if (mPref!!.secondFilter) {
            secondFilter.isEnabled = true
            secondFilterSign.isChecked = true
        }
        secondFilterSign.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _: Preference?, newValue: Any? ->
            if (newValue !is Boolean) {
                return@OnPreferenceChangeListener true
            }
            if (!newValue) {
                mDeck!!.getJSONArray("terms").remove(1)
                secondFilter.isEnabled = false
            } else {
                secondFilter.isEnabled = true
                /**Link to the defaults used in AnkiDesktop
                 * <https://github.com/ankitects/anki/blob/1b15069b248a8f86f9bd4b3c66a9bfeab8dfb2b8/qt/aqt/filtered_deck.py#L148-L149>
                 */
                val narr = JSONArray(Arrays.asList("", 20, 5))
                mDeck!!.getJSONArray("terms").put(1, narr)
                val newOrderPrefSecond = findPreference("order_2") as ListPreference
                newOrderPrefSecond.value = "5"
            }
            true
        }
    }
}
