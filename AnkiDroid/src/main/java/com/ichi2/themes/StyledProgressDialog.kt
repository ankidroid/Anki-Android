/****************************************************************************************
 * Copyright (c) 2011 Norbert Nagold <norbert.nagold@gmail.com>                         *
 *                                                                                      *
 * based on custom Dialog windows by antoine vianey                                     *
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
package com.ichi2.themes

import android.app.Dialog
import android.content.Context
import android.content.DialogInterface
import android.view.WindowManager.BadTokenException
import com.ichi2.anki.AnkiActivity
import timber.log.Timber

class StyledProgressDialog(context: Context?) : Dialog(context!!) {
    override fun show() {
        try {
            setCanceledOnTouchOutside(false)
            super.show()
        } catch (e: BadTokenException) {
            Timber.e(e, "Could not show dialog")
        }
    }

    @Suppress("unused_parameter")
    fun setMax(max: Int) {
        // TODO
    }

    @Suppress("unused_parameter")
    fun setProgress(progress: Int) {
        // TODO
    }

    @Suppress("unused_parameter")
    fun setProgressStyle(style: Int) {
        // TODO
    }

    @Suppress("Deprecation") // ProgressDialog deprecation
    companion object {
        fun show(
            context: Context,
            title: CharSequence?,
            message: CharSequence?,
            cancelable: Boolean = false,
            cancelListener: DialogInterface.OnCancelListener? = null
        ): android.app.ProgressDialog {
            var t = title
            if ("" == t) {
                t = null
                Timber.d("Invalid title was provided. Using null")
            }
            return android.app.ProgressDialog(context).apply {
                setTitle(t)
                setMessage(message)
                progress = 0
                setCancelable(cancelable)
                setOnCancelListener(cancelListener)
                show()
            }
        }

        @Suppress("unused")
        private fun animationEnabled(context: Context): Boolean {
            return if (context is AnkiActivity) {
                context.animationEnabled()
            } else {
                true
            }
        }
    }
}
