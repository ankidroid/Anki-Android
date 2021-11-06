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

import android.os.Bundle
import com.afollestad.materialdialogs.DialogAction
import com.afollestad.materialdialogs.MaterialDialog
import com.ichi2.anki.AnkiDroidApp
import com.ichi2.anki.R
import com.ichi2.utils.contentNullable
import com.ichi2.utils.titleNullable

class SimpleMessageDialog : AsyncDialogFragment() {
    interface SimpleMessageDialogListener {
        fun dismissSimpleMessageDialog(reload: Boolean)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): MaterialDialog {
        // FIXME this should be super.onCreateDialog(Bundle), no?
        super.onCreate(savedInstanceState)
        return MaterialDialog.Builder(requireActivity())
            .titleNullable(notificationTitle)
            .contentNullable(notificationMessage)
            .positiveText(res().getString(R.string.dialog_ok))
            .onPositive { _: MaterialDialog?, _: DialogAction? ->
                (activity as SimpleMessageDialogListener?)
                    ?.dismissSimpleMessageDialog(
                        requireArguments().getBoolean(
                            "reload"
                        )
                    )
            }
            .show()
    }

    override fun getNotificationTitle(): String? {
        val title = requireArguments().getString("title")
        return if ("" != title) {
            title
        } else {
            AnkiDroidApp.getAppResources().getString(R.string.app_name)
        }
    }

    override fun getNotificationMessage(): String? {
        return requireArguments().getString("message")
    }

    companion object {
        @JvmStatic
        fun newInstance(message: String?, reload: Boolean): SimpleMessageDialog {
            return newInstance("", message, reload)
        }

        @JvmStatic
        fun newInstance(title: String?, message: String?, reload: Boolean): SimpleMessageDialog {
            val f = SimpleMessageDialog()
            val args = Bundle()
            args.putString("title", title)
            args.putString("message", message)
            args.putBoolean("reload", reload)
            f.arguments = args
            return f
        }
    }
}
