/****************************************************************************************
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

package com.ichi2.utils

import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.pm.PackageInfoCompat
import com.ichi2.anki.AnkiDroidApp
import com.ichi2.anki.CrashReportService
import timber.log.Timber
import java.lang.NullPointerException

/**
 * Created by Tim on 11/04/2015.
 */
object VersionUtils {
    /**
     * Get package name as defined in the manifest.
     */
    @Suppress("deprecation") // getPackageInfo
    val appName: String
        get() {
            var pkgName = AnkiDroidApp.TAG
            val context: Context = applicationInstance ?: return AnkiDroidApp.TAG
            try {
                val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
                pkgName = context.getString(pInfo.applicationInfo.labelRes)
            } catch (e: PackageManager.NameNotFoundException) {
                Timber.e(e, "Couldn't find package named %s", context.packageName)
            }
            return pkgName
        }

    /**
     * Get the package versionName as defined in the manifest.
     */
    @Suppress("deprecation") // getPackageInfo
    val pkgVersionName: String
        get() {
            var pkgVersion = "?"
            val context: Context = applicationInstance ?: return pkgVersion
            try {
                val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
                pkgVersion = pInfo.versionName
            } catch (e: PackageManager.NameNotFoundException) {
                Timber.e(e, "Couldn't find package named %s", context.packageName)
            }
            return pkgVersion
        }

    /**
     * Get the package versionCode as defined in the manifest.
     */
    @Suppress("deprecation") // getPackageInfo
    val pkgVersionCode: Long
        get() {
            val context: Context = applicationInstance ?: return 0
            try {
                val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
                val versionCode = PackageInfoCompat.getLongVersionCode(pInfo)
                Timber.d("getPkgVersionCode() is %s", versionCode)
                return versionCode
            } catch (e: PackageManager.NameNotFoundException) {
                Timber.e(e, "Couldn't find package named %s", context.packageName)
            } catch (npe: NullPointerException) {
                if (context.packageManager == null) {
                    Timber.e("getPkgVersionCode() null package manager?")
                } else if (context.packageName == null) {
                    Timber.e("getPkgVersionCode() null package name?")
                }
                CrashReportService.sendExceptionReport(npe, "Unexpected exception getting version code?")
                Timber.e(npe, "Unexpected exception getting version code?")
            }
            return 0
        }

    private val applicationInstance: Context?
        get() = if (AnkiDroidApp.isInitialized) {
            AnkiDroidApp.instance
        } else {
            Timber.w("AnkiDroid instance not set")
            null
        }

    /**
     * Return whether the package version code is set to that for release version
     * @return whether build number in manifest version code is '3'
     */
    val isReleaseVersion: Boolean
        get() {
            val versionCode = java.lang.Long.toString(pkgVersionCode)
            Timber.d("isReleaseVersion() versionCode: %s", versionCode)
            return versionCode[versionCode.length - 3] == '3'
        }
}
