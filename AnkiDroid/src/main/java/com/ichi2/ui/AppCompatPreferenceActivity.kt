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
import android.view.KeyEvent
import android.view.MenuInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.LayoutRes
import androidx.appcompat.app.ActionBar
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.Toolbar
import com.ichi2.anki.AnkiDroidApp
import com.ichi2.anki.CollectionHelper
import com.ichi2.anki.receiver.SdCardReceiver
import com.ichi2.libanki.Collection
import timber.log.Timber

/**
 * A [android.preference.PreferenceActivity] which implements and proxies the necessary calls
 * to be used with AppCompat.
 *
 * This technique can be used with an [android.app.Activity] class, not just
 * [android.preference.PreferenceActivity].
 */
abstract class AppCompatPreferenceActivity : PreferenceActivity(), SharedPreferences.OnSharedPreferenceChangeListener {
    private var mDelegate: AppCompatDelegate? = null
    protected lateinit var col: Collection
        private set

    fun isColInitialized() = ::col.isInitialized

    protected var prefChanged = false
    lateinit var unmountReceiver: BroadcastReceiver

    @Deprecated("Deprecated in Java")
    override fun onCreate(savedInstanceState: Bundle?) {
        delegate.installViewFactory()
        delegate.onCreate(savedInstanceState)
        super.onCreate(savedInstanceState)
        val col = CollectionHelper.getInstance().getCol(this)
        if (col != null) {
            this.col = col
        } else {
            finish()
        }
    }

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(AnkiDroidApp.updateContextWithLanguage(base))
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

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK && event.repeatCount == 0) {
            Timber.i("DeckOptions - onBackPressed()")
            closeWithResult()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }
}
