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

import android.content.Intent
import android.os.Build
import android.os.Parcelable
import android.view.KeyCharacterMap.deviceHasKey
import android.view.KeyEvent.*
import com.ichi2.compat.CompatHelper.Companion.compat
import java.io.Serializable

/**
 * Selects a [Compat] class based on the device's [Build.VERSION.SDK_INT]
 *
 * Use [compat] to obtain this instance:
 *
 * ```kotlin
 *     CompatHelper.compat.copyFile(stream, path)
 * ```
 */
class CompatHelper private constructor() {

    // Note: Needs ": Compat" or the type system assumes `Compat21`
    private val compatValue: Compat = when {
        sdkVersion >= Build.VERSION_CODES.TIRAMISU -> CompatV33()
        sdkVersion >= Build.VERSION_CODES.S -> CompatV31()
        sdkVersion >= Build.VERSION_CODES.Q -> CompatV29()
        sdkVersion >= Build.VERSION_CODES.O -> CompatV26()
        sdkVersion >= Build.VERSION_CODES.M -> CompatV23()
        else -> CompatV21()
    }

    companion object {
        /** Singleton instance of [CompatHelper] */
        private val instance by lazy { CompatHelper() }

        /** Get the current Android API level.  */
        val sdkVersion: Int
            get() = Build.VERSION.SDK_INT

        /** Determine if the device is running API level 23 or higher.  */
        val isMarshmallow: Boolean
            get() = sdkVersion >= Build.VERSION_CODES.M

        /**
         * Main public method to get the compatibility class
         */
        val compat get() = instance.compatValue

        val isChromebook: Boolean
            get() = (
                "chromium".equals(Build.BRAND, ignoreCase = true) || "chromium".equals(Build.MANUFACTURER, ignoreCase = true) ||
                    "novato_cheets".equals(Build.DEVICE, ignoreCase = true)
                )
        val isKindle: Boolean
            get() = "amazon".equals(Build.BRAND, ignoreCase = true) || "amazon".equals(Build.MANUFACTURER, ignoreCase = true)

        fun hasKanaAndEmojiKeys(): Boolean {
            return deviceHasKey(KEYCODE_SWITCH_CHARSET) && deviceHasKey(KEYCODE_PICTSYMBOLS)
        }

        fun hasScrollKeys(): Boolean {
            return deviceHasKey(KEYCODE_PAGE_UP) || deviceHasKey(KEYCODE_PAGE_DOWN)
        }
        inline fun <reified T : Serializable?> Intent.getSerializableExtraCompat(name: String): T? {
            return compat.getSerializableExtra(this, name, T::class.java)
        }

        inline fun <reified T : Parcelable> Intent.getParcelableExtraCompat(name: String): T? {
            return compat.getParcelableExtra(this, name, T::class.java)
        }
    }
}
