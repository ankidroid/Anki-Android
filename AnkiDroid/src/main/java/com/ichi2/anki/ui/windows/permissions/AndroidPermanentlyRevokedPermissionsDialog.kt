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

package com.ichi2.anki.ui.windows.permissions

import android.annotation.SuppressLint
import android.content.Context
import android.content.DialogInterface
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import com.ichi2.anki.AnkiActivity
import com.ichi2.anki.CollectionHelper
import com.ichi2.anki.R
import com.ichi2.anki.dialogs.DatabaseErrorDialog.DatabaseErrorDialogType
import com.ichi2.anki.dialogs.DatabaseErrorDialog.UninstallListItem
import com.ichi2.utils.cancelable
import com.ichi2.utils.listItemsAndMessage
import com.ichi2.utils.show
import timber.log.Timber

/**
 * Inform the user that Android 13+ has permanently revoked access to `WRITE_EXTERNAL_STORAGE`.
 * Typically due to app permissions being revoked from unused apps
 *
 * Their collection is safe, but inaccessible
 *
 * Provide recovery options
 *
 * Issue 14423
 *
 * @see DatabaseErrorDialogType.DIALOG_STORAGE_UNAVAILABLE_AFTER_UNINSTALL
 */
object AndroidPermanentlyRevokedPermissionsDialog {
    @SuppressLint("CheckResult")
    fun show(context: AnkiActivity) {
        val listItemData = StoragePermanentlyRevokedOptions.createList()

        val message = context.getString(
            R.string.directory_revoked_after_inactivity,
            "WRITE_EXTERNAL_STORAGE",
            getCurrentAnkiDroidDirectory(context)
        )
        AlertDialog.Builder(context).show {
            listItemsAndMessage(message = message, listItemData.map { context.getString(it.stringRes) }) { dialog: DialogInterface, index: Int ->
                val listItem = listItemData[index]
                listItem.onClick(context)
                if (listItem.dismissesDialog) {
                    dialog.dismiss()
                }
            }
            cancelable(false)
        }
    }

    private fun getCurrentAnkiDroidDirectory(context: Context): String = try {
        CollectionHelper.getCurrentAnkiDroidDirectory(context)
    } catch (e: Exception) {
        Timber.w(e)
        context.getString(R.string.card_browser_unknown_deck_name)
    }

    /**
     * List items, copied from [UninstallListItem]
     * Aside from 'Restore from AnkiWeb': we're unable to access the Deck Picker to sync
     * and would also have no folder to restore to
     */
    private class StoragePermanentlyRevokedOptions(@StringRes val stringRes: Int, val dismissesDialog: Boolean, val onClick: (AnkiActivity) -> Unit) {
        companion object {
            fun createList(): List<StoragePermanentlyRevokedOptions> {
                return UninstallListItem.createList()
                    .filter { it != UninstallListItem.RESTORE_FROM_ANKIWEB }
                    .map { listItem ->
                        StoragePermanentlyRevokedOptions(
                            stringRes = listItem.stringRes,
                            dismissesDialog = listItem.dismissesDialog,
                            onClick = listItem.onClick
                        )
                    }
            }
        }
    }
}
