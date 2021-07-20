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

package com.ichi2.testutils;

import android.content.Context;
import android.content.Intent;

import com.ichi2.anki.Preferences;

import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import androidx.lifecycle.Lifecycle;
import androidx.test.core.app.ActivityScenario;

public class PreferenceUtils {

    public static Set<String> getAllCustomButtonKeys(Context context) {
        AtomicReference<Set<String>> ret = new AtomicReference<>();
        Intent i = Preferences.CustomButtonsSettingsFragment.getSubscreenIntent(context);
        try (ActivityScenario<Preferences> scenario = ActivityScenario.launch(i)) {
            scenario.moveToState(Lifecycle.State.STARTED);
            scenario.onActivity(a -> ret.set(a.getLoadedPreferenceKeys()));
        }
        Set<String> preferenceKeys = ret.get();
        if (preferenceKeys == null) {
            throw new IllegalStateException("no keys were set");
        }
        preferenceKeys.remove("reset_custom_buttons");
        return preferenceKeys;
    }
}
