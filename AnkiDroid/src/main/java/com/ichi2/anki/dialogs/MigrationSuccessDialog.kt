/*
 *  Copyright (c) 2023 Ashish Yadav <mailtoashish693@gmail.com>
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

package com.ichi2.anki.dialogs

import android.app.AlertDialog
import android.app.Dialog
import android.os.Bundle
import com.ichi2.anki.DeckPicker.Companion.migrationSuccessMessage
import com.ichi2.anki.DeckPicker.Companion.migrationSuccessTitle
import com.ichi2.anki.R

class MigrationSuccessDialog : AsyncDialogFragment() {
    override val notificationMessage: String?
        get() = migrationSuccessMessage
    override val notificationTitle: String
        get() = migrationSuccessTitle

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return AlertDialog.Builder(requireContext())
            .setTitle(resources.getString(R.string.migration_successful_message))
            .setMessage(resources.getString(R.string.migration_completed))
            .setPositiveButton(R.string.dialog_ok) { _, _ ->
                dismiss()
            }.create()
    }
}