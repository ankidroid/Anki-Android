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

import com.ichi2.anki.AnkiActivity
import com.ichi2.anki.R
import com.ichi2.anki.UIUtils.showThemedToast
import com.ichi2.async.TaskListenerWithContext
import com.ichi2.themes.StyledProgressDialog
import com.ichi2.utils.KotlinCleanup
import timber.log.Timber

sealed class ExportResult
class ExportPath(val path: String) : ExportResult()
class ExportError(val message: String?) : ExportResult()
object ExportException : ExportResult()

internal class ExportListener(activity: AnkiActivity, private val dialogsFactory: ExportDialogsFactory) : TaskListenerWithContext<AnkiActivity, Void, ExportResult>(activity) {
    @Suppress("Deprecation")
    private var mProgressDialog: android.app.ProgressDialog? = null
    override fun actualOnPreExecute(context: AnkiActivity) {
        mProgressDialog = StyledProgressDialog.show(
            context, "",
            context.resources.getString(R.string.export_in_progress), false
        )
    }

    @KotlinCleanup("Decide what to do with this code. Clearly, Timbers are wrong")
    override fun actualOnPostExecute(context: AnkiActivity, result: ExportResult) {
        if (mProgressDialog != null && mProgressDialog!!.isShowing) {
            mProgressDialog!!.dismiss()
        }

        // If boolean and string are both set, we are signalling an error message
        // instead of a successful result.
        when (result) {
            is ExportError -> {
                if (result.message != null) {
                    Timber.w("Export Failed: %s", result.message)
                    context.showSimpleMessageDialog(result.message)
                } else {
                    Timber.i("Export successful")
                    showThemedToast(
                        context,
                        context.resources.getString(R.string.export_unsuccessful),
                        true
                    )
                }
            }
            is ExportException -> {
                Timber.i("Export successful")
                showThemedToast(
                    context,
                    context.resources.getString(R.string.export_unsuccessful),
                    true
                )
            }
            is ExportPath -> {
                Timber.i("Export successful")
                val dialog = dialogsFactory.newExportCompleteDialog().withArguments(result.path)
                context.showAsyncDialogFragment(dialog)
            }
        }
    }
}
