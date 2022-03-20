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
package com.ichi2.compat

import android.os.Build
import android.view.KeyCharacterMap
import com.ichi2.utils.KotlinCleanup

class CompatHelper private constructor() {

    @KotlinCleanup("inline & convert to when")
    private val compatValue: Compat

    companion object {
        private var sInstance: CompatHelper? = null

        /** Get the current Android API level.  */
        @JvmStatic
        val sdkVersion: Int
            get() = Build.VERSION.SDK_INT

        /** Determine if the device is running API level 23 or higher.  */
        @JvmStatic
        val isMarshmallow: Boolean
            get() = sdkVersion >= Build.VERSION_CODES.M

        /**
         * Main public method to get the compatibility class
         */
        @JvmStatic
        val compat get() = instance.compatValue

        @get:Synchronized
        @KotlinCleanup("lazy")
        val instance: CompatHelper
            get() {
                if (sInstance == null) {
                    sInstance = CompatHelper()
                }
                return sInstance!!
            }

        val isChromebook: Boolean
            get() = (
                "chromium".equals(Build.BRAND, ignoreCase = true) || "chromium".equals(Build.MANUFACTURER, ignoreCase = true) ||
                    "novato_cheets".equals(Build.DEVICE, ignoreCase = true)
                )
        @JvmStatic
        val isKindle: Boolean
            get() = "amazon".equals(Build.BRAND, ignoreCase = true) || "amazon".equals(Build.MANUFACTURER, ignoreCase = true)

        fun hasKanaAndEmojiKeys(): Boolean {
            return KeyCharacterMap.deviceHasKey(94) && KeyCharacterMap.deviceHasKey(95)
        }

        fun hasScrollKeys(): Boolean {
            return KeyCharacterMap.deviceHasKey(92) || KeyCharacterMap.deviceHasKey(93)
        }
    }

    init {
        if (sdkVersion >= Build.VERSION_CODES.S) {
            compatValue = CompatV31()
        } else if (sdkVersion >= Build.VERSION_CODES.Q) {
            compatValue = CompatV29()
        } else if (sdkVersion >= Build.VERSION_CODES.O) {
            compatValue = CompatV26()
        } else if (sdkVersion >= Build.VERSION_CODES.M) {
            compatValue = CompatV23()
        } else {
            compatValue = CompatV21()
        }
    }
}
