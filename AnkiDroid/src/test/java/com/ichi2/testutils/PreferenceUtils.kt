/*
 Copyright (c) 2020 David Allison <davidallisongithub@gmail.com>

 This program is free software; you can redistribute it and/or modify it under
 the terms of the GNU General Public License as published by the Free Software
 Foundation; either version 3 of the License, or (at your option) any later
 version.

 This program is distributed in the hope that it will be useful, but WITHOUT ANY
 WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 PARTICULAR PURPOSE. See the GNU General Public License for more details.

 You should have received a copy of the GNU General Public License along with
 this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.ichi2.testutils

import android.content.Context
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import com.ichi2.anki.Preferences
import java.util.concurrent.atomic.AtomicReference

object PreferenceUtils {
    @JvmStatic
    fun getAllCustomButtonKeys(context: Context?): Set<String> {
        val ret = AtomicReference<Set<String>>()
        val i = Preferences.CustomButtonsSettingsFragment.getSubscreenIntent(context)
        ActivityScenario.launch<Preferences>(i).use { scenario ->
            scenario.moveToState(Lifecycle.State.STARTED)
            scenario.onActivity { a: Preferences -> ret.set(a.loadedPreferenceKeys) }
        }
        val preferenceKeys = ret.get()?.toMutableSet() ?: throw IllegalStateException("no keys were set")
        preferenceKeys.remove("reset_custom_buttons")
        return preferenceKeys
    }
}
