/*
 *  Copyright (c) 2024 David Allison <davidallisongithub@gmail.com>
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

import android.app.Activity
import android.os.Build
import android.view.View
import androidx.core.view.OnReceiveContentListener
import androidx.draganddrop.DropHelper
import com.ichi2.utils.ClipboardUtil.MEDIA_MIME_TYPES

typealias DropHelperOptionsCompat = DropHelper.Options
typealias DropHelperOptionsBuilder = DropHelper.Options.Builder

/**
 * We have applied `tools:overrideLibrary="androidx.draganddrop"` so we manually need to handle
 * compat of [DropHelper]
 */
object DropHelperCompat {

    /**
     * Configures a [View] for drag and drop operations, including the highlighting that
     * indicates the view is a drop target. Sets a listener that enables the view to handle dropped
     * data.
     *
     * @see DropHelper.configureView
     */
    fun configureView(
        activity: Activity,
        view: View,
        options: DropHelperOptionsCompat,
        onReceiveContentListener: OnReceiveContentListener
    ) {
        // library will fail < API 24
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return

        DropHelper.configureView(
            activity,
            view,
            MEDIA_MIME_TYPES,
            options,
            onReceiveContentListener
        )
    }
}
