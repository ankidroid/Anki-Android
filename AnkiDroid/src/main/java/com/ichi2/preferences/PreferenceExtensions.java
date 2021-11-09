/*
 *  Copyright (c) 2020 David Allison <davidallisongithub@gmail.com>
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

package com.ichi2.preferences;

import android.content.SharedPreferences;

import java.util.function.Supplier;

import androidx.annotation.CheckResult;
import androidx.annotation.NonNull;

/** Extension methods over the SharedPreferences class */
public class PreferenceExtensions {

    /**
     * Returns the string value specified by the key, or sets key to the result of the lambda and returns it.<br/>
     * This is not designed to be used when bulk editing preferences.<br/>
     * Defect #5828 - This is potentially not thread safe and could cause another preference commit to fail.
     */
    @CheckResult //Not truly an error as this has a side effect, but you should use a "set" API for perf.
    public static String getOrSetString(@NonNull SharedPreferences target, @NonNull String key, @NonNull Supplier<String> supplier) {
        if (target.contains(key)) {
            //the default Is never returned. The value might be able be optimised, but the Android API should be better.
            return target.getString(key, "");
        }
        String supplied = supplier.get();
        target.edit().putString(key, supplied).apply();
        return supplied;
    }
}
