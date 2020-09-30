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

package com.ichi2.anki.cardviewer;

import android.os.Build;
import android.webkit.URLUtil;
import android.webkit.WebResourceRequest;

import com.ichi2.utils.FunctionalInterfaces.Consumer;

import androidx.annotation.NonNull;
import timber.log.Timber;

/** Handles logic for displaying help for missing media files */
public class MissingImageHandler {

    /** Specify a maximum number of times to display, as it's somewhat annoying */
    public static final int MAX_DISPLAY_TIMES = 2;

    private int mNumberOfMissingImages = 0;
    private boolean mHasExecuted = false;


    public MissingImageHandler() {

    }

    public void processFailure(WebResourceRequest request, @NonNull Consumer<String> onFailure) {
        // We do not want this to trigger more than once on the same side of the card as the UI will flicker.
        if (request == null || mHasExecuted) {
            return;
        }

        // The UX of the snackbar is annoying, as it obscures the content. Assume that if a user ignores it twice, they don't care.
        if (mNumberOfMissingImages >= MAX_DISPLAY_TIMES) {
            return;
        }

        // This code doesn't seem to be called anyway, but we can't use request.getUrl() on < API 21.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            return;
        }

        String url = request.getUrl().toString();
        // We could do better here (external images failing due to no HTTPS), but failures can occur due to no network.
        // As we don't yet check the error data, we don't know.
        // Therefore limit this feature to the common case of local files, which should always work.
        if (!url.contains("collection.media")) {
            return;
        }

        try {
            String filename = URLUtil.guessFileName(url, null, null);
            onFailure.consume(filename);
            mNumberOfMissingImages++;
        } catch (Exception e) {
            Timber.w(e, "Failed to notify UI of media failure");
        } finally {
            mHasExecuted = true;
        }
    }

    public void onCardSideChange() {
        mHasExecuted = false;
    }
}
