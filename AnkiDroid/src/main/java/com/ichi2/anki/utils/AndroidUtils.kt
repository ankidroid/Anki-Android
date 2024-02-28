/*
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program. If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.anki.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import com.ichi2.anki.AnkiDroidApp
import com.ichi2.anki.R
import com.ichi2.anki.showThemedToast
import com.ichi2.anki.snackbar.showSnackbar
import com.ichi2.utils.AdaptionUtil
import com.ichi2.utils.copyToClipboard

/**
 * Acquire a wake lock and release it after running [block].
 *
 * @param levelAndFlags Combination of wake lock level and flag values defining
 *   the requested behavior of the WakeLock
 * @param tag Your class name (or other tag) for debugging purposes
 * @return The return value of `block`
 *
 * @see android.os.PowerManager.newWakeLock
 */
inline fun <T> withWakeLock(levelAndFlags: Int, tag: String, block: () -> T): T {
    val context = AnkiDroidApp.instance
    val wakeLock = ContextCompat
        .getSystemService(context, PowerManager::class.java)!!
        .newWakeLock(levelAndFlags, context.packageName + ":" + tag)

    wakeLock.acquire()

    return try {
        block()
    } finally {
        wakeLock.release()
    }
}

fun Context.openUrl(uri: Uri) {
    if (!AdaptionUtil.hasWebBrowser(this)) {
        val noBrowserMessage = getString(R.string.no_browser_msg, uri.toString())
        if (this is FragmentActivity) {
            showSnackbar(noBrowserMessage) {
                setAction(android.R.string.copyUrl) {
                    copyToClipboard(uri.toString())
                }
            }
        } else {
            showThemedToast(this, noBrowserMessage, shortLength = false)
        }
        return
    }
    startActivity(Intent(Intent.ACTION_VIEW, uri))
}

// necessary for Fragments that are BaseSnackbarBuilderProvider to work correctly
fun Fragment.openUrl(uri: Uri) {
    if (!AdaptionUtil.hasWebBrowser(requireContext())) {
        showSnackbar(getString(R.string.no_browser_msg, uri.toString()))
        return
    }
    startActivity(Intent(Intent.ACTION_VIEW, uri))
}

fun Fragment.openUrl(@StringRes stringRes: Int) =
    openUrl(Uri.parse(requireContext().getString(stringRes)))
