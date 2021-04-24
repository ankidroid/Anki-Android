/*
 Copyright (c) 2021 Tarek Mohamed Abdalla <tarekkma@gmail.com>

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
package com.ichi2.utils;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

/**
 * Collection of useful methods to be used with {@link android.os.Bundle}
 */
public class BundleUtils {


    /**
     * Retrieves a {@link Long} value from a {@link Bundle} using a key, returns null if not found
     *
     * @param bundle the bundle to look into
     *               can be null to support nullable bundles like {@link Fragment#getArguments()}
     * @param key the key to use
     * @return the long value, or null if not found
     */
    @Nullable
    public static Long getNullableLong(@Nullable Bundle bundle, @NonNull String key) {
        if (bundle == null || !bundle.containsKey(key)) {
            return null;
        }
        return bundle.getLong(key);
    }

}
