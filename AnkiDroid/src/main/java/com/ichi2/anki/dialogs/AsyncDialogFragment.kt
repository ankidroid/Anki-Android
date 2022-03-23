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
package com.ichi2.anki.dialogs

import android.content.res.Resources
import android.os.Message
import com.ichi2.anki.AnkiDroidApp
import com.ichi2.anki.analytics.AnalyticsDialogFragment
import com.ichi2.utils.KotlinCleanup
import timber.log.Timber

abstract class AsyncDialogFragment : AnalyticsDialogFragment() {
    /* provide methods for text to show in notification bar when the DialogFragment
       can't be shown due to the host activity being in stopped state.
       This can happen when the DialogFragment is shown from
       the onPostExecute() method of an AsyncTask */
    @KotlinCleanup("convert these back to properties")
    abstract fun getNotificationMessage(): String?
    abstract fun getNotificationTitle(): String?
    open fun getDialogHandlerMessage(): Message? {
        return null
    }

    protected fun res(): Resources {
        return try {
            AnkiDroidApp.getAppResources()
        } catch (e: Exception) {
            Timber.w(e, "AnkiDroidApp.getAppResources failure. Returning Fragment resources as fallback.")
            resources
        }
    }
}
