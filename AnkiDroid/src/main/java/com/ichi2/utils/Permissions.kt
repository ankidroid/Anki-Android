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

package com.ichi2.utils;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

public class Permissions {
    private Permissions() { }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public static boolean canUseCamera(@NonNull Context context) {
        return hasPermission(context, Manifest.permission.CAMERA);
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public static boolean canRecordAudio(@NonNull Context context) {
        return hasPermission(context, Manifest.permission.RECORD_AUDIO);
    }

    private static boolean hasPermission(@NonNull Context context, @NonNull String permission) {
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED;
    }


    /**
     * Check if we have permission to access the external storage
     * @param context
     * @return
     */
    public static boolean hasStorageAccessPermission(Context context) {
        return hasPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE);
    }


    public static boolean canUseWakeLock(Context context) {
        return hasPermission(context, Manifest.permission.WAKE_LOCK);
    }
}
