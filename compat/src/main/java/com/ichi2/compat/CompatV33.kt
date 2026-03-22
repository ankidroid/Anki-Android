/*
 *  Copyright (c) 2022 Ashish Yadav <mailtoashish693@gmail.com>
 *  Copyright (c) 2022 David Allison <davidallisongithub@gmail.com>
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
package com.ichi2.compat

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Bundle
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.VibratorManager
import androidx.annotation.RequiresApi
import java.io.Serializable
import kotlin.time.Duration

@RequiresApi(33)
open class CompatV33 :
    CompatV31(),
    Compat {
    /**
     *  @throws SecurityException on some Xiaomi phones if performing a cross-profile query #19711
     */
    override fun resolveActivity(
        packageManager: PackageManager,
        intent: Intent,
        flags: ResolveInfoFlagsCompat,
    ): ResolveInfo? = packageManager.resolveActivity(intent, PackageManager.ResolveInfoFlags.of(flags.value))

    override fun <T : Serializable?> getSerializableExtra(
        intent: Intent,
        name: String,
        className: Class<T>,
    ): T? = intent.getSerializableExtra(name, className)

    override fun <T : Serializable?> getSerializable(
        bundle: Bundle,
        key: String,
        clazz: Class<T>,
    ): T? = bundle.getSerializable(key, clazz)

    override fun getPackageInfo(
        packageManager: PackageManager,
        packageName: String,
        flags: PackageInfoFlagsCompat,
    ): PackageInfo? = packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(flags.value))

    override fun resolveService(
        packageManager: PackageManager,
        intent: Intent,
        flags: ResolveInfoFlagsCompat,
    ): ResolveInfo? = packageManager.resolveService(intent, PackageManager.ResolveInfoFlags.of(flags.value))

    override fun queryIntentActivities(
        packageManager: PackageManager,
        intent: Intent,
        flags: ResolveInfoFlagsCompat,
    ): List<ResolveInfo> = packageManager.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(flags.value))

    override fun vibrate(
        context: Context,
        duration: Duration,
        @VibrationUsage usage: Int,
    ) {
        val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
        val effect = VibrationEffect.createOneShot(duration.inWholeMilliseconds, VibrationEffect.DEFAULT_AMPLITUDE)
        val vibrator = vibratorManager.defaultVibrator
        val attributes = VibrationAttributes.Builder().setUsage(usage).build()
        vibrator.vibrate(effect, attributes)
    }
}
