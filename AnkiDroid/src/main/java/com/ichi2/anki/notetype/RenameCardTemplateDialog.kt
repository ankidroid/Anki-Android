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
import com.ichi2.utils.getInputTextLayout
import com.ichi2.utils.input
import com.ichi2.utils.negativeButton
import com.ichi2.utils.positiveButton
import com.ichi2.utils.show
import com.ichi2.utils.title

class RenameCardTemplateDialog {
    companion object {
        fun showInstance(
            context: Context,
            prefill: String,
            existingNames: List<String>,
            block: (result: String) -> Unit,
        ) {
            AlertDialog
                .Builder(context)
                .show {
                    title(R.string.rename_card_type)
                    positiveButton(R.string.rename) { }
                    negativeButton(R.string.dialog_cancel)
                    setView(R.layout.dialog_generic_text_input)
                }.input(
                    hint = CollectionManager.TR.actionsNewName(),
                    displayKeyboard = true,
                    allowEmpty = false,
                    prefill = prefill,
                    waitForPositiveButton = false,
                    callback = { dialog, result ->
                        val text = result.toString()
                        val isNotADuplicate = text == prefill || !existingNames.contains(text)
                        if (isNotADuplicate) {
                            dialog.getInputTextLayout().error = null
                        } else {
                            dialog.getInputTextLayout().error =
                                context.getString(R.string.card_browser_list_my_searches_new_search_error_dup)
                        }
                        dialog.positiveButton.isEnabled = text.isNotEmpty() && isNotADuplicate
                        dialog.positiveButton.setOnClickListener {
                            if (text.isNotEmpty() && isNotADuplicate) {
                                block(text)
                                dialog.dismiss()
                            }
                        }
                    },
                )
        }
    }
}
