/*
 *  Copyright (c) 2021 David Allison <davidallisongithub@gmail.com>
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
package com.ichi2.anki.analytics

import android.content.Context
import com.ichi2.anki.AnkiDroidApp

class Acra {
    companion object {

        fun enableWithAskDialog(ctx: Context) {
            val askKey = AnkiDroidApp.FEEDBACK_REPORT_ASK
            AnkiDroidApp.getSharedPrefs(ctx).edit().putString(AnkiDroidApp.FEEDBACK_REPORT_KEY, askKey).apply()
            onPreferenceChanged(ctx, askKey)
        }

        fun disableAcra(ctx: Context) {
            val askKey = AnkiDroidApp.FEEDBACK_REPORT_NEVER
            AnkiDroidApp.getSharedPrefs(ctx).edit().putString(AnkiDroidApp.FEEDBACK_REPORT_KEY, askKey).apply()
            onPreferenceChanged(ctx, askKey)
        }

        @JvmStatic
        fun onPreferenceChanged(ctx: Context, newValue: String) {
            AnkiDroidApp.getInstance().setAcraReportingMode(newValue)
            // If the user changed error reporting, make sure future reports have a chance to post
            AnkiDroidApp.deleteACRALimiterData(ctx)
            // We also need to re-chain our UncaughtExceptionHandlers
            UsageAnalytics.reInitialize()
        }
    }
}
