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
package com.ichi2.anki.cardviewer

import android.webkit.URLUtil
import android.webkit.WebResourceRequest
import timber.log.Timber
import java.io.File
import java.util.function.Consumer

/** Handles logic for displaying help for missing media files  */
class MissingImageHandler {

    companion object {
        /** Specify a maximum number of times to display, as it's somewhat annoying  */
        const val MAX_DISPLAY_TIMES = 2
    }

    private var mMissingMediaCount = 0
    private var mHasShownInefficientImage = false
    private var mHasExecuted = false
    fun processFailure(request: WebResourceRequest?, onFailure: Consumer<String?>) {
        // We do not want this to trigger more than once on the same side of the card as the UI will flicker.
        if (request == null || mHasExecuted) return

        // The UX of the snackbar is annoying, as it obscures the content. Assume that if a user ignores it twice, they don't care.
        if (mMissingMediaCount >= MAX_DISPLAY_TIMES) return

        val url = request.url.toString()
        // We could do better here (external images failing due to no HTTPS), but failures can occur due to no network.
        // As we don't yet check the error data, we don't know.
        // Therefore limit this feature to the common case of local files, which should always work.
        if (!url.contains("collection.media")) return

        try {
            val filename = URLUtil.guessFileName(url, null, null)
            onFailure.accept(filename)
            mMissingMediaCount++
        } catch (e: Exception) {
            Timber.w(e, "Failed to notify UI of media failure")
        } finally {
            mHasExecuted = true
        }
    }

    fun processMissingSound(file: File?, onFailure: Consumer<String?>) {
        // We want this to trigger more than once on the same side - as the user is in control of pressing "play"
        // and we want to provide feedback
        if (file == null) return

        // The UX of the snackbar is annoying, as it obscures the content. Assume that if a user ignores it twice, they don't care.
        if (mMissingMediaCount >= MAX_DISPLAY_TIMES) return

        try {
            val fileName = file.name
            onFailure.accept(fileName)
            if (!mHasExecuted) {
                mMissingMediaCount++
            }
        } catch (e: Exception) {
            Timber.w(e, "Failed to notify UI of media failure")
        } finally {
            mHasExecuted = true
        }
    }

    fun onCardSideChange() {
        mHasExecuted = false
    }

    fun processInefficientImage(onFailure: Runnable) {
        if (mHasShownInefficientImage) return

        mHasShownInefficientImage = true
        onFailure.run()
    }
}
