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

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.PackageManager.NameNotFoundException
import android.content.pm.ResolveInfo
import android.os.Build
import android.os.Bundle
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

        @Suppress("unused")
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

        inline fun <reified T : Serializable?> Bundle.getSerializableCompat(name: String): T? {
            return compat.getSerializable(this, name, T::class.java)
        }

        @Suppress("unused")
        inline fun <reified T : Serializable?> Intent.getSerializableExtraCompat(name: String): T? {
            return compat.getSerializableExtra(this, name, T::class.java)
        }

        /**
         * Retrieve overall information about an application package that is
         * installed on the system.
         *
         * @see PackageManager.getPackageInfo
         * @throws NameNotFoundException if no such package is available to the caller.
         */
        @Throws(NameNotFoundException::class)
        fun Context.getPackageInfoCompat(packageName: String, flags: PackageInfoFlagsCompat): PackageInfo? =
            this.packageManager.getPackageInfoCompat(packageName, flags)

        /**
         * Retrieve overall information about an application package that is
         * installed on the system.
         *
         * @see PackageManager.getPackageInfo
         * @throws NameNotFoundException if no such package is available to the caller.
         */
        @Throws(NameNotFoundException::class)
        fun PackageManager.getPackageInfoCompat(packageName: String, flags: PackageInfoFlagsCompat): PackageInfo? =
            compat.getPackageInfo(this, packageName, flags)

        /**
         * Determine the best service to handle for a given Intent.
         *
         * @param intent An intent containing all of the desired specification
         *            (action, data, type, category, and/or component).
         * @param flags Additional option flags to modify the data returned.
         * @return Returns a ResolveInfo object containing the final service intent
         *         that was determined to be the best action. Returns null if no
         *         matching service was found.
         */
        fun PackageManager.resolveServiceCompat(intent: Intent, flags: ResolveInfoFlagsCompat): ResolveInfo? {
            return compat.resolveService(this, intent, flags)
        }

        /**
         * Retrieve all activities that can be performed for the given intent.
         *
         * @param intent The desired intent as per resolveActivity().
         * @param flags Additional option flags to modify the data returned. The
         *            most important is [MATCH_DEFAULT_ONLY], to limit the
         *            resolution to only those activities that support the
         *            [CATEGORY_DEFAULT]. Or, set
         *            [MATCH_ALL] to prevent any filtering of the results.
         * @return Returns a List of ResolveInfo objects containing one entry for
         *         each matching activity, ordered from best to worst. In other
         *         words, the first item is what would be returned by
         *         {@link #resolveActivity}. If there are no matching activities, an
         *         empty list is returned.
         */
        fun PackageManager.queryIntentActivitiesCompat(intent: Intent, flags: ResolveInfoFlagsCompat): List<ResolveInfo> {
            return compat.queryIntentActivities(this, intent, flags)
        }

        /**
         * Determine the best action to perform for a given Intent. This is how
         * resolveActivity finds an activity if a class has not been
         * explicitly specified.
         *
         * Note: if using an implicit Intent (without an explicit
         * ComponentName specified), be sure to consider whether to set the
         * MATCH_DEFAULT_ONLY only flag. You need to do so to resolve the
         * activity in the same way that
         * android.content.Context#startActivity(Intent) and
         * android.content.Intent#resolveActivity(PackageManager)
         * Intent.resolveActivity(PackageManager) do.
         *
         * @param intent An intent containing all of the desired specification
         *            (action, data, type, category, and/or component).
         * @param flags Additional option flags to modify the data returned. The
         *            most important is MATCH_DEFAULT_ONLY, to limit the
         *            resolution to only those activities that support the
         *            android.content.Intent#CATEGORY_DEFAULT.
         * @return Returns a ResolveInfo object containing the final activity intent
         *         that was determined to be the best action. Returns null if no
         *         matching activity was found. If multiple matching activities are
         *         found and there is no default set, returns a ResolveInfo object
         *         containing something else, such as the activity resolver.
         */
        fun PackageManager.resolveActivityCompat(intent: Intent, flags: ResolveInfoFlagsCompat): ResolveInfo? {
            return compat.resolveActivity(this, intent, flags)
        }
    }
}
