// noinspection MissingCopyrightHeader #8659
/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
@file:Suppress("DEPRECATION") // TODO Tracked in https://github.com/ankidroid/Anki-Android/issues/5019

package com.ichi2.ui

import android.content.*
import android.content.res.Configuration
import android.os.Bundle
import android.preference.PreferenceActivity
import android.view.*
import androidx.annotation.LayoutRes
import androidx.appcompat.app.ActionBar
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.Toolbar
import com.ichi2.anki.CollectionHelper
import com.ichi2.anki.R
import com.ichi2.anki.receiver.SdCardReceiver
import com.ichi2.libanki.Collection
import com.ichi2.libanki.Deck
import com.ichi2.utils.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import net.ankiweb.rsdroid.BackendException
import timber.log.Timber
import java.util.*

/**
 * A [android.preference.PreferenceActivity] which implements and proxies the necessary calls
 * to be used with AppCompat.
 *
 * This technique can be used with an [android.app.Activity] class, not just
 * [android.preference.PreferenceActivity].
 */
abstract class AppCompatPreferenceActivity<PreferenceHack : AppCompatPreferenceActivity<PreferenceHack>.AbstractPreferenceHack> :
    PreferenceActivity(),
    CoroutineScope by MainScope(),
    SharedPreferences.OnSharedPreferenceChangeListener {
    private var mDelegate: AppCompatDelegate? = null
    fun isColInitialized() = ::col.isInitialized
    protected var prefChanged = false
    lateinit var unmountReceiver: BroadcastReceiver
    protected lateinit var col: Collection
        private set
    protected lateinit var pref: PreferenceHack
    protected lateinit var deck: Deck

    abstract inner class AbstractPreferenceHack : SharedPreferences {
        val values: MutableMap<String, String> = HashUtil.hashMapInit(30) // At most as many as in cacheValues
        val summaries: MutableMap<String, String?> = HashMap()
        protected val listeners: MutableList<SharedPreferences.OnSharedPreferenceChangeListener> = LinkedList()

        @KotlinCleanup("scope function")
        abstract fun cacheValues()

        abstract inner class Editor : SharedPreferences.Editor {
            protected var update = ContentValues()

            override fun clear(): SharedPreferences.Editor {
                Timber.d("clear()")
                update = ContentValues()
                return this
            }

            override fun putBoolean(key: String, value: Boolean): SharedPreferences.Editor {
                update.put(key, value)
                return this
            }

            override fun putFloat(key: String, value: Float): SharedPreferences.Editor {
                update.put(key, value)
                return this
            }

            override fun putInt(key: String, value: Int): SharedPreferences.Editor {
                update.put(key, value)
                return this
            }

            override fun putLong(key: String, value: Long): SharedPreferences.Editor {
                update.put(key, value)
                return this
            }

            override fun putString(key: String, value: String?): SharedPreferences.Editor {
                update.put(key, value)
                return this
            }

            override fun remove(key: String): SharedPreferences.Editor {
                Timber.d("Editor.remove(key=%s)", key)
                update.remove(key)
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
            val deckPreferenceHack: AbstractPreferenceHack
                get() = this@AbstractPreferenceHack
        }

        override fun contains(key: String): Boolean {
            return values.containsKey(key)
        }

        override fun getAll(): Map<String, *> {
            return values
        }

        override fun getBoolean(key: String, defValue: Boolean): Boolean {
            return java.lang.Boolean.parseBoolean(this.getString(key, java.lang.Boolean.toString(defValue)))
        }

        override fun getFloat(key: String, defValue: Float): Float {
            return this.getString(key, defValue.toString())!!.toFloat()
        }

        override fun getInt(key: String, defValue: Int): Int {
            return this.getString(key, defValue.toString())!!.toInt()
        }

        override fun getLong(key: String, defValue: Long): Long {
            return this.getString(key, defValue.toString())!!.toLong()
        }

        override fun getString(key: String, defValue: String?): String? {
            Timber.d("getString(key=%s, defValue=%s)", key, defValue)
            return if (!values.containsKey(key)) {
                defValue
            } else {
                values[key]
            }
        }

        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
            listeners.add(listener)
        }

        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
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

    @Deprecated("Deprecated in Java")
    override fun onCreate(savedInstanceState: Bundle?) {
        delegate.installViewFactory()
        delegate.onCreate(savedInstanceState)
        super.onCreate(savedInstanceState)
        val col = CollectionHelper.instance.getColUnsafe(this)
        if (col != null) {
            this.col = col
        } else {
            finish()
        }
    }

    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)
        delegate.onPostCreate(savedInstanceState)
    }

    val supportActionBar: ActionBar?
        get() = delegate.supportActionBar

    fun setSupportActionBar(toolbar: Toolbar?) {
        delegate.setSupportActionBar(toolbar)
    }

    override fun getMenuInflater(): MenuInflater {
        return delegate.menuInflater
    }

    override fun setContentView(@LayoutRes layoutResID: Int) {
        delegate.setContentView(layoutResID)
    }

    override fun setContentView(view: View) {
        delegate.setContentView(view)
    }

    override fun setContentView(view: View, params: ViewGroup.LayoutParams) {
        delegate.setContentView(view, params)
    }

    override fun addContentView(view: View, params: ViewGroup.LayoutParams) {
        delegate.addContentView(view, params)
    }

    override fun onPostResume() {
        super.onPostResume()
        delegate.onPostResume()
    }

    override fun onTitleChanged(title: CharSequence, color: Int) {
        super.onTitleChanged(title, color)
        delegate.setTitle(title)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        delegate.onConfigurationChanged(newConfig)
    }

    @Deprecated("Deprecated in Java")
    override fun onStop() {
        super.onStop()
        delegate.onStop()
    }

    @Deprecated("Deprecated in Java")
    override fun onDestroy() {
        super.onDestroy()
        delegate.onDestroy()
        unregisterReceiver(unmountReceiver)
        cancel() // cancel all the Coroutines started from Activity's Scope
    }

    override fun invalidateOptionsMenu() {
        delegate.invalidateOptionsMenu()
    }

    protected abstract fun updateSummaries()
    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String?) {
        // update values on changed preference
        prefChanged = true
        updateSummaries()
    }

    private val delegate: AppCompatDelegate
        get() {
            if (mDelegate == null) {
                mDelegate = AppCompatDelegate.create(this, null)
            }
            return mDelegate!! // safe as mDelegate is only initialized here, before being returned
        }

    /**
     * Call exactly once, during creation
     * to ensure that if the SD card is ejected
     * this activity finish.
     */

    /**
     * finish when sd card is ejected
     */
    fun registerExternalStorageListener() {
        unmountReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == SdCardReceiver.MEDIA_EJECT) {
                    finish()
                }
            }
        }
        val iFilter = IntentFilter()
        iFilter.addAction(SdCardReceiver.MEDIA_EJECT)
        registerReceiver(unmountReceiver, iFilter)
    }

    protected abstract fun closeWithResult()

    @Deprecated("Deprecated in Java")
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            tryCloseWithResult()
            return true
        }
        return false
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK && event.repeatCount == 0) {
            Timber.i("DeckOptions - onBackPressed()")
            tryCloseWithResult()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun tryCloseWithResult() {
        try {
            closeWithResult()
        } catch (e: BackendException) {
            Timber.e(e, "Backend exception while trying to finish an AppCompatPreferenceActivity")
            AlertDialog.Builder(this).show {
                title(text = resources.getString(R.string.pref__widget_text__error))
                message(text = e.message)
                positiveButton(R.string.dialog_ok) { dialogInterface ->
                    dialogInterface.dismiss()
                }
            }
        }
    }

    override fun getSharedPreferences(name: String, mode: Int): SharedPreferences {
        Timber.d("getSharedPreferences(name=%s)", name)
        return pref
    }
}
