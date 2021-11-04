/***************************************************************************************
 * Copyright (c) 2015 Timothy Rae <perceptualchaos2@gmail.com>                          *
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

package com.ichi2.compat;


import android.os.Build;
import android.view.KeyCharacterMap;

public class CompatHelper {
    private static CompatHelper sInstance;
    private final Compat mCompat;


    private CompatHelper() {
        if (getSdkVersion() >= Build.VERSION_CODES.S) {
            mCompat = new CompatV31();
        } else if (getSdkVersion() >= Build.VERSION_CODES.Q) {
            mCompat = new CompatV29();
        } else if (getSdkVersion() >= Build.VERSION_CODES.O) {
            mCompat = new CompatV26();
        } else if (getSdkVersion() >= Build.VERSION_CODES.M) {
            mCompat = new CompatV23();
        } else {
            mCompat = new CompatV21();
        }
    }

    /** Get the current Android API level. */
    public static int getSdkVersion() {
        return Build.VERSION.SDK_INT;
    }

    /** Determine if the device is running API level 23 or higher. */
    public static boolean isMarshmallow() {
        return getSdkVersion() >= Build.VERSION_CODES.M;
    }

    /**
     * Main public method to get the compatibility class
     */
    public static Compat getCompat() {
        return getInstance().mCompat;
    }

    public static synchronized CompatHelper getInstance() {
        if (sInstance == null) {
            sInstance = new CompatHelper();
        }
        return sInstance;
    }

    public static boolean isChromebook() {
        return "chromium".equalsIgnoreCase(Build.BRAND) || "chromium".equalsIgnoreCase(Build.MANUFACTURER)
                || "novato_cheets".equalsIgnoreCase(Build.DEVICE);
    }

    public static boolean isKindle() {
        return "amazon".equalsIgnoreCase(Build.BRAND) || "amazon".equalsIgnoreCase(Build.MANUFACTURER);
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public static boolean hasKanaAndEmojiKeys() {
        return KeyCharacterMap.deviceHasKey(94) && KeyCharacterMap.deviceHasKey(95);
    }

    public static boolean hasScrollKeys() {
        return KeyCharacterMap.deviceHasKey(92) || KeyCharacterMap.deviceHasKey(93);
    }
}
