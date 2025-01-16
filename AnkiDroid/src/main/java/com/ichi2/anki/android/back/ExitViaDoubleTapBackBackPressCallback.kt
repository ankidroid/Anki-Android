/*
 *  Copyright (c) 2025 David Allison <davidallisongithub@gmail.com>
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

package com.ichi2.anki.android.back

import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import androidx.activity.OnBackPressedCallback
import androidx.preference.PreferenceManager
import com.google.android.material.snackbar.Snackbar
import com.ichi2.anki.AnkiActivity
import com.ichi2.anki.AnkiDroidApp
import com.ichi2.anki.R
import com.ichi2.anki.settings.Prefs
import com.ichi2.anki.snackbar.showSnackbar
import com.ichi2.libanki.Consts
import com.ichi2.utils.HandlerUtils
import timber.log.Timber

/**
 * Note: this uses sharedPreferences via AnkiDroidApp, so must be called after
 * [AnkiActivity.showedActivityFailedScreen]
 *
 * @see Prefs.exitViaDoubleTapBack
 */
// TODO: Convert this to a class when context parameters are usable
fun AnkiActivity.exitViaDoubleTapBackCallback(): OnBackPressedCallback =
    object : OnBackPressedCallback(enabled = Prefs.exitViaDoubleTapBack) {
        lateinit var strongListenerReference: OnSharedPreferenceChangeListener

        override fun handleOnBackPressed() {
            showSnackbar(R.string.back_pressed_once, Snackbar.LENGTH_SHORT)
            this.isEnabled = false
            HandlerUtils.executeFunctionWithDelay(Consts.SHORT_TOAST_DURATION) {
                this.isEnabled = true
            }
        }
    }.also { callback ->
        // PreferenceManager uses weak references, so we need our own strong reference which
        // will go out of scope
        callback.strongListenerReference =
            OnSharedPreferenceChangeListener { prefs, key ->
                if (key == getString(R.string.exit_via_double_tap_back_key)) {
                    callback.isEnabled =
                        Prefs.exitViaDoubleTapBack.also {
                            Timber.i("exit via double tap callback -> %b", it)
                        }
                }
            }

        PreferenceManager
            .getDefaultSharedPreferences(AnkiDroidApp.instance)
            .registerOnSharedPreferenceChangeListener(callback.strongListenerReference)
    }
