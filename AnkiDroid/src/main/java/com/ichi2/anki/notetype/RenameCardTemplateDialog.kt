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

package com.ichi2.anki.notetype

import android.content.Context
import androidx.appcompat.app.AlertDialog
import com.ichi2.anki.CollectionManager
import com.ichi2.anki.R
import com.ichi2.utils.input
import com.ichi2.utils.negativeButton
import com.ichi2.utils.positiveButton
import com.ichi2.utils.show

class RenameCardTemplateDialog {

    companion object {
        fun showInstance(context: Context, prefill: String, block: (result: String) -> Unit) {
            AlertDialog.Builder(context).show {
                positiveButton(R.string.dialog_ok) { }
                negativeButton(R.string.dialog_cancel)
                setView(R.layout.dialog_generic_text_input)
            }
                .input(
                    hint = CollectionManager.TR.actionsNewName(),
                    allowEmpty = false,
                    prefill = prefill,
                    waitForPositiveButton = true,
                    callback = { dialog, result ->
                        block(result.toString())
                        dialog.dismiss()
                    }
                )
        }
    }
}
