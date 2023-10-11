/*
 *  Copyright (c) 2021 Mike Hardy <mike@mikehardy.net>
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

import android.annotation.TargetApi
import android.content.Context
import android.media.MediaRecorder
import android.os.VibrationEffect
import android.os.VibratorManager

/** Implementation of [Compat] for SDK level 31  */
@TargetApi(31)
open class CompatV31 : CompatV29(), Compat {
    override fun vibrate(context: Context, durationMillis: Long) {
        val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
        val effect = VibrationEffect.createOneShot(durationMillis, VibrationEffect.DEFAULT_AMPLITUDE)
        val vibrator = vibratorManager.defaultVibrator
        vibrator.vibrate(effect)
    }

    override fun getMediaRecorder(context: Context): MediaRecorder {
        return MediaRecorder(context)
    }
}
