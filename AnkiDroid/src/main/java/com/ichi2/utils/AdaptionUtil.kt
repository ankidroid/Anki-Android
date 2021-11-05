/****************************************************************************************
 * Copyright (c) 2020 gaoyingjun@xiaomi.com                                             *
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

import android.app.ActivityManager
import android.content.ComponentName
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.ichi2.anki.AnkiDroidApp
import timber.log.Timber
import java.lang.Exception
import java.util.*

object AdaptionUtil {
    private var sHasRunRestrictedLearningDeviceCheck = false
    private var sIsRestrictedLearningDevice = false
    private var sHasRunWebBrowserCheck = false
    private var sHasWebBrowser = true
    private var sIsRunningMiUI: Boolean? = null
    @JvmStatic
    fun hasWebBrowser(context: Context): Boolean {
        if (sHasRunWebBrowserCheck) {
            return sHasWebBrowser
        }
        sHasWebBrowser = checkHasWebBrowser(context)
        sHasRunWebBrowserCheck = true
        return sHasWebBrowser
    }

    @JvmStatic
    val isUserATestClient: Boolean
        get() = try {
            ActivityManager.isUserAMonkey() ||
                isRunningUnderFirebaseTestLab
        } catch (e: Exception) {
            Timber.w(e)
            false
        }
    val isRunningUnderFirebaseTestLab: Boolean
        get() = try {
            isRunningUnderFirebaseTestLab(AnkiDroidApp.getInstance().contentResolver)
        } catch (e: Exception) {
            Timber.w(e)
            false
        }

    private fun isRunningUnderFirebaseTestLab(contentResolver: ContentResolver): Boolean {
        // https://firebase.google.com/docs/test-lab/android/android-studio#modify_instrumented_test_behavior_for
        val testLabSetting = Settings.System.getString(contentResolver, "firebase.test.lab")
        return "true" == testLabSetting
    }

    private fun checkHasWebBrowser(context: Context): Boolean {
        // The test monkey often gets stuck on the Shared Decks WebView, ignore it as it shouldn't crash.
        if (isUserATestClient) {
            return false
        }
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("http://www.google.com"))
        val pm = context.packageManager
        val list = pm.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
        for (ri in list) {
            if (!isValidBrowser(ri)) {
                continue
            }

            // If we aren't a restricted device, any browser will do
            if (!isRestrictedLearningDevice) {
                return true
            }
            // If we are a restricted device, only a system browser will do
            if (isSystemApp(ri.activityInfo.packageName, pm)) {
                return true
            }
        }
        // Either there are no web browsers, or we're a restricted learning device and there's no system browsers.
        return false
    }

    private fun isValidBrowser(ri: ResolveInfo?): Boolean {
        // https://stackoverflow.com/a/57223246/
        return ri != null && ri.activityInfo != null && ri.activityInfo.exported
    }

    private fun isSystemApp(packageName: String?, pm: PackageManager): Boolean {
        return if (packageName != null) {
            try {
                val info = pm.getPackageInfo(packageName, 0)
                info != null && info.applicationInfo != null &&
                    info.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM != 0
            } catch (e: PackageManager.NameNotFoundException) {
                Timber.w(e)
                false
            }
        } else {
            false
        }
    }

    @JvmStatic
    val isRestrictedLearningDevice: Boolean
        get() {
            if (!sHasRunRestrictedLearningDeviceCheck) {
                sIsRestrictedLearningDevice = "Xiaomi".equals(Build.MANUFACTURER, ignoreCase = true) &&
                    ("Archytas".equals(Build.PRODUCT, ignoreCase = true) || "Archimedes".equals(Build.PRODUCT, ignoreCase = true))
                sHasRunRestrictedLearningDeviceCheck = true
            }
            return sIsRestrictedLearningDevice
        }

    fun canUseContextMenu(): Boolean {
        return !isRunningMiui
    }

    private val isRunningMiui: Boolean
        get() {
            if (sIsRunningMiUI == null) {
                sIsRunningMiUI = queryIsMiui()
            }
            return sIsRunningMiUI!!
        }

    // https://stackoverflow.com/questions/47610456/how-to-detect-miui-rom-programmatically-in-android
    private fun isIntentResolved(ctx: Context, intent: Intent?): Boolean {
        return intent != null && ctx.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY) != null
    }

    private fun queryIsMiui(): Boolean {
        val ctx: Context = AnkiDroidApp.getInstance()
        return (
            isIntentResolved(ctx, Intent("miui.intent.action.OP_AUTO_START").addCategory(Intent.CATEGORY_DEFAULT)) ||
                isIntentResolved(ctx, Intent().setComponent(ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"))) ||
                isIntentResolved(ctx, Intent("miui.intent.action.POWER_HIDE_MODE_APP_LIST").addCategory(Intent.CATEGORY_DEFAULT)) ||
                isIntentResolved(ctx, Intent().setComponent(ComponentName("com.miui.securitycenter", "com.miui.powercenter.PowerSettings")))
            )
    }

    /** See: https://en.wikipedia.org/wiki/Vivo_(technology_company)  */
    @JvmStatic
    val isVivo: Boolean
        get() {
            val manufacturer = Build.MANUFACTURER ?: return false
            return manufacturer.lowercase(Locale.ROOT) == "vivo"
        }
}
