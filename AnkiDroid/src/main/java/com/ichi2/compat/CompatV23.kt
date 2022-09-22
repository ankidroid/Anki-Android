/***************************************************************************************
 * Copyright (c) 2017 Profpatsch <mail@profpatsch.de>                                   *
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

import android.annotation.TargetApi
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.widget.TimePicker

/** Implementation of {@link Compat} for SDK level 23 and higher. Check  {@link Compat}'s for more detail. */
@TargetApi(23)
open class CompatV23 : CompatV21(), Compat {
    override fun setTime(picker: TimePicker, hour: Int, minute: Int) {
        picker.hour = hour
        picker.minute = minute
    }

    override fun getHour(picker: TimePicker): Int {
        return picker.hour
    }

    override fun getMinute(picker: TimePicker): Int {
        return picker.minute
    }

    override fun getImmutableActivityIntent(context: Context, requestCode: Int, intent: Intent, flags: Int): PendingIntent {
        return PendingIntent.getActivity(context, requestCode, intent, flags or PendingIntent.FLAG_IMMUTABLE)
    }

    override fun getImmutableBroadcastIntent(context: Context, requestCode: Int, intent: Intent, flags: Int): PendingIntent {
        return PendingIntent.getBroadcast(context, requestCode, intent, flags or PendingIntent.FLAG_IMMUTABLE)
    }
}
