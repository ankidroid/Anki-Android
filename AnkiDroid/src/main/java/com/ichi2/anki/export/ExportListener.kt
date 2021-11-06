/*
 Copyright (c) 2021 Tarek Mohamed Abdalla <tarekkma@gmail.com>

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
package com.ichi2.anki.export

import android.util.Pair
import com.afollestad.materialdialogs.MaterialDialog
import com.ichi2.anki.AnkiActivity
import com.ichi2.anki.R
import com.ichi2.anki.UIUtils.showThemedToast
import com.ichi2.async.TaskListenerWithContext
import com.ichi2.themes.StyledProgressDialog
import timber.log.Timber

internal class ExportListener(activity: AnkiActivity?, private val dialogsFactory: ExportDialogsFactory) : TaskListenerWithContext<AnkiActivity?, Void?, Pair<Boolean?, String?>?>(activity) {
    private var mProgressDialog: MaterialDialog? = null
    override fun actualOnPreExecute(activity: AnkiActivity) {
        mProgressDialog = StyledProgressDialog.show(
            activity, "",
            activity.resources.getString(R.string.export_in_progress), false
        )
    }

    override fun actualOnPostExecute(activity: AnkiActivity, result: Pair<Boolean?, String?>?) {
        if (mProgressDialog != null && mProgressDialog!!.isShowing) {
            mProgressDialog!!.dismiss()
        }

        if (result == null) {
            return
        }
        // If boolean and string are both set, we are signalling an error message
        // instead of a successful result.
        if (result.first == true && result.second != null) {
            Timber.w("Export Failed: %s", result.second)
            activity.showSimpleMessageDialog(result.second)
        } else {
            Timber.i("Export successful")
            val exportPath = result.second
            if (exportPath != null) {
                val dialog = dialogsFactory.newExportCompleteDialog().withArguments(exportPath)
                activity.showAsyncDialogFragment(dialog)
            } else {
                showThemedToast(activity, activity.resources.getString(R.string.export_unsuccessful), true)
            }
        }
    }
}
