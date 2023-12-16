/*
 *  Copyright (c) 2020 David Allison <davidallisongithub@gmail.com>
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
package com.ichi2.utils

import android.app.Activity
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import androidx.annotation.CheckResult
import androidx.annotation.StringRes
import com.ichi2.anki.UIUtils.showThemedToast
import com.ichi2.anki.snackbar.canProperlyShowSnackbars
import com.ichi2.anki.snackbar.showSnackbar
import timber.log.Timber

object ClipboardUtil {
    // JPEG is sent via pasted content
    val IMAGE_MIME_TYPES = arrayOf("image/gif", "image/png", "image/jpg", "image/jpeg")

    fun hasImage(clipboard: ClipboardManager?): Boolean {
        return clipboard
            ?.takeIf { it.hasPrimaryClip() }
            ?.primaryClip
            ?.let { hasImage(it.description) }
            ?: false
    }

    fun hasImage(description: ClipDescription?): Boolean {
        return description
            ?.run { IMAGE_MIME_TYPES.any { hasMimeType(it) } }
            ?: false
    }

    private fun getFirstItem(clipboard: ClipboardManager?) = clipboard
        ?.takeIf { it.hasPrimaryClip() }
        ?.primaryClip
        ?.takeIf { it.itemCount > 0 }
        ?.getItemAt(0)

    fun getImageUri(clipboard: ClipboardManager?): Uri? {
        return getFirstItem(clipboard)?.uri
    }

    @CheckResult
    fun getText(clipboard: ClipboardManager?): CharSequence? {
        return getFirstItem(clipboard)?.text
    }

    @CheckResult
    fun getPlainText(clipboard: ClipboardManager?, context: Context): CharSequence? {
        return getFirstItem(clipboard)?.coerceToText(context)
    }
}

/**
 * Copies the provided text to the clipboard (truncated if necessary)
 * @return `true` if the clipboard is modified. `false` otherwise
 */
fun Context.copyToClipboard(text: String): Boolean {
    val clipboardManager = this.getSystemService(Activity.CLIPBOARD_SERVICE) as? ClipboardManager
    if (clipboardManager == null) {
        Timber.w("Failed to obtain ClipboardManager")
        return false
    }

    clipboardManager.setPrimaryClip(
        ClipData.newPlainText(
            "${VersionUtils.appName} v${VersionUtils.pkgVersionName}",
            text
        )
    )
    return true
}

/**
 * Copy given [text] to the clipboard,
 * and show either a snackbar, if possible, or a toast, with the success or failure message.
 *
 * @see copyToClipboard
 */
fun Context.copyToClipboardAndShowConfirmation(
    text: String,
    @StringRes successMessageId: Int,
    @StringRes failureMessageId: Int,
) {
    val successfullyCopied = copyToClipboard(text)

    val confirmationMessage = if (successfullyCopied) successMessageId else failureMessageId

    when (this is Activity && this.canProperlyShowSnackbars()) {
        true -> showSnackbar(confirmationMessage)
        false -> showThemedToast(this, confirmationMessage, true)
    }
}
