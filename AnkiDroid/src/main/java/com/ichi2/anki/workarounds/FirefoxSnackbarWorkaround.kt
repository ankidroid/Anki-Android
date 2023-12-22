/*
 Copyright (c) 2020 David Allison <davidallisongithub@gmail.com>

 This program is free software; you can redistribute it and/or modify it under
 the terms of the GNU General Public License as published by the Free Software
 Foundation; either version 3 of the License, or (at your option) any later
 version.

 This program is distributed in the hope that it will be useful, but WITHOUT ANY
 WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 PARTICULAR PURPOSE. See the GNU General Public License for more details.

 You should have received a copy of the GNU General Public License along with
 this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.anki.workarounds

import android.content.Context
import android.content.Intent
import android.provider.Browser
import com.ichi2.anki.R
import com.ichi2.anki.UIUtils.showThemedToast
import timber.log.Timber

/** #5374
 *
 * If a user attempts to open an apkg from the Firefox Snackbar, we just get a ACTION_VIEW on Reviewer...
 * It works if they click the download notification, but if they click "Open", then that disappears.
 *
 * So... tell them to go to about:downloads, or select the file from their file manager.
 * It sucks, but not much we can do.
 *
 * Reported as fixed in Firefox Preview
 */
object FirefoxSnackbarWorkaround {
    fun handledLaunchFromWebBrowser(
        intent: Intent?,
        context: Context,
    ): Boolean {
        if (intent == null) {
            Timber.w("FirefoxSnackbarWorkaround: No intent provided")
            return false
        }
        if (wasLaunchFromWebBrowser(intent)) {
            showThemedToast(context, context.getString(R.string.firefox_workaround_launched_reviewer_opening_deck), false)
            return true
        }
        return false
    }

    private fun wasLaunchFromWebBrowser(intent: Intent): Boolean {
        return intent.getStringExtra(Browser.EXTRA_APPLICATION_ID) != null
    }
}
