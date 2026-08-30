// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.utils

import android.app.Activity
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.os.Build
import androidx.annotation.CheckResult
import androidx.annotation.StringRes
import com.ichi2.anki.S
import com.ichi2.anki.common.utils.android.showThemedToast
import com.ichi2.anki.snackbar.canProperlyShowSnackbars
import com.ichi2.anki.snackbar.showSnackbar
import timber.log.Timber

object ClipboardUtil {
    val IMAGE_MIME_TYPES = arrayOf("image/*")
    val AUDIO_MIME_TYPES = arrayOf("audio/*")
    val VIDEO_MIME_TYPES = arrayOf("video/*")
    val IMPORT_MIME_TYPES = arrayOf("application/*", "text/*")
    val MEDIA_MIME_TYPES = arrayOf(*IMAGE_MIME_TYPES, *AUDIO_MIME_TYPES, *VIDEO_MIME_TYPES)

    fun hasImage(clipboard: ClipboardManager?): Boolean =
        clipboard
            ?.primaryClip
            ?.let { hasImage(it.description) }
            ?: false

    fun hasImage(description: ClipDescription?): Boolean =
        description
            ?.run { IMAGE_MIME_TYPES.any { hasMimeType(it) } }
            ?: false

    fun hasVideo(description: ClipDescription?): Boolean =
        description
            ?.run { VIDEO_MIME_TYPES.any { hasMimeType(it) } }
            ?: false

    private fun ClipboardManager.getFirstItem() = primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)

    fun getUri(clipboard: ClipboardManager?): Uri? = clipboard?.getFirstItem()?.uri

    fun hasMedia(clipboard: ClipboardManager?): Boolean =
        clipboard
            ?.primaryClip
            ?.let { hasMedia(it.description) }
            ?: false

    fun hasMedia(description: ClipDescription?): Boolean =
        description
            ?.run { MEDIA_MIME_TYPES.any { hasMimeType(it) } }
            ?: false

    fun ClipData.items() =
        sequence {
            for (j in 0 until itemCount) {
                yield(getItemAt(j))
            }
        }

    fun getDescription(clipboard: ClipboardManager?): ClipDescription? = clipboard?.primaryClip?.description

    @CheckResult
    fun getPlainText(
        clipboard: ClipboardManager?,
        context: Context,
    ): CharSequence? = clipboard?.getFirstItem()?.coerceToText(context)
}

/**
 * Copies the provided [text] to the clipboard (truncated if necessary, >5 lines),
 * and show either a snackbar, if possible, or a toast with a success/failure
 * message if the system does not already show a 'copied to clipboard' message
 *
 * @param text the text that needs to be copied
 * @param successMessageId message that needs to be shown after successfully copying the text
 * @param failureMessageId message that needs to be shown in case failed to copy the text
 * @return whether the text was copied to the clipboard
 */
fun Context.copyToClipboard(
    text: String,
    @StringRes successMessageId: Int = S.about_ankidroid_successfully_copied_debug_info,
    @StringRes failureMessageId: Int = S.failed_to_copy,
): Boolean {
    val copied = copyTextToClipboard(text)
    // in Android S_V2 and above, the system is guaranteed to show a message on a successful copy
    // so we don't need to do anything
    val doesNotNeedToShowMessage = copied && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S_V2

    if (doesNotNeedToShowMessage) {
        Timber.v("successfully copied to clipboard & system informed user of copy")
        return true
    }

    val confirmationMessage = if (copied) successMessageId else failureMessageId

    if (this is Activity && canProperlyShowSnackbars()) {
        showSnackbar(confirmationMessage)
    } else {
        showThemedToast(this, confirmationMessage, shortLength = true)
    }

    return copied
}

/**
 * The method attempts to obtain the system clipboard manager using the current context. If the clipboard
 * manager is not available, it logs a warning and returns `false`.
 *
 * If the clipboard manager is obtained, the method creates a new clip with the provided text along with
 * the application name and version information. It then sets this clip as the primary clip on the clipboard.
 *
 * @param text The text to be copied to the clipboard.
 * @return `true` if the text was successfully copied to the clipboard, `false` if clipboard access failed.
 */
private fun Context.copyTextToClipboard(text: String): Boolean {
    val clipboardManager = this.getSystemService(Activity.CLIPBOARD_SERVICE) as? ClipboardManager
    if (clipboardManager == null) {
        Timber.w("Failed to obtain ClipboardManager")
        return false
    }

    return try {
        clipboardManager.setPrimaryClip(
            ClipData.newPlainText(
                "${VersionUtils.appName} v${VersionUtils.pkgVersionName}",
                text,
            ),
        )
        true
    } catch (e: Exception) {
        Timber.w(e, "Failed to copy text to clipboard")
        false
    }
}
