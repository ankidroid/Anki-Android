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

import android.net.Uri
import android.os.Bundle
import android.os.Message
import com.afollestad.materialdialogs.MaterialDialog
import com.ichi2.anki.AnkiActivity
import com.ichi2.anki.DeckPicker
import com.ichi2.anki.R
import com.ichi2.async.Connection.ConflictResolution
import com.ichi2.libanki.CollectionGetter
import com.ichi2.utils.contentNullable
import com.ichi2.utils.iconAttr

class SyncErrorDialog : AsyncDialogFragment() {
    interface SyncErrorDialogListener : CollectionGetter {
        fun showSyncErrorDialog(dialogType: Int)
        fun showSyncErrorDialog(dialogType: Int, message: String?)
        fun loginToSyncServer()
        fun sync(conflict: ConflictResolution? = null)
        fun mediaCheck()
        fun dismissAllDialogFragments()
        fun integrityCheck()
    }

    @Suppress("Deprecation") // Material dialog neutral button deprecation
    override fun onCreateDialog(savedInstanceState: Bundle?): MaterialDialog {
        super.onCreate(savedInstanceState)
        val dialog = MaterialDialog(requireActivity())
            .title(text = title)
            .contentNullable(message)
            .cancelable(true)
        return when (requireArguments().getInt("dialogType")) {
            DIALOG_USER_NOT_LOGGED_IN_SYNC -> {

                // User not logged in; take them to login screen
                dialog.show {
                    iconAttr(R.attr.dialogSyncErrorIcon)
                    positiveButton(R.string.log_in) {
                        (activity as SyncErrorDialogListener).loginToSyncServer()
                    }
                    negativeButton(R.string.dialog_cancel)
                }
            }
            DIALOG_CONNECTION_ERROR -> {

                // Connection error; allow user to retry or cancel
                dialog.show {
                    iconAttr(R.attr.dialogSyncErrorIcon)
                    positiveButton(R.string.retry) {
                        (activity as SyncErrorDialogListener).sync()
                        dismissAllDialogFragments()
                    }
                    negativeButton(R.string.dialog_cancel) {
                        dismissAllDialogFragments()
                    }
                }
            }
            DIALOG_SYNC_CONFLICT_RESOLUTION -> {

                // Sync conflict; allow user to cancel, or choose between local and remote versions
                dialog.show {
                    iconAttr(R.attr.dialogSyncErrorIcon)
                    positiveButton(R.string.sync_conflict_keep_local_new) {
                        (activity as SyncErrorDialogListener?)
                            ?.showSyncErrorDialog(DIALOG_SYNC_CONFLICT_CONFIRM_KEEP_LOCAL)
                    }
                    negativeButton(R.string.sync_conflict_keep_remote_new) {
                        (activity as SyncErrorDialogListener?)
                            ?.showSyncErrorDialog(DIALOG_SYNC_CONFLICT_CONFIRM_KEEP_REMOTE)
                    }
                    neutralButton(R.string.dialog_cancel) {
                        dismissAllDialogFragments()
                    }
                }
            }
            DIALOG_SYNC_CONFLICT_CONFIRM_KEEP_LOCAL -> {

                // Confirmation before pushing local collection to server after sync conflict
                dialog.show {
                    iconAttr(R.attr.dialogSyncErrorIcon)
                    positiveButton(R.string.dialog_positive_replace) {
                        val activity = activity as SyncErrorDialogListener?
                        activity!!.sync(ConflictResolution.FULL_UPLOAD)
                        dismissAllDialogFragments()
                    }
                    negativeButton(R.string.dialog_cancel)
                }
            }
            DIALOG_SYNC_CONFLICT_CONFIRM_KEEP_REMOTE -> {

                // Confirmation before overwriting local collection with server collection after sync conflict
                dialog.show {
                    iconAttr(R.attr.dialogSyncErrorIcon)
                    positiveButton(R.string.dialog_positive_replace) {
                        val activity = activity as SyncErrorDialogListener?
                        activity!!.sync(ConflictResolution.FULL_DOWNLOAD)
                        dismissAllDialogFragments()
                    }
                    negativeButton(R.string.dialog_cancel)
                }
            }
            DIALOG_SYNC_SANITY_ERROR -> {

                // Sync sanity check error; allow user to cancel, or choose between local and remote versions
                dialog.show {
                    positiveButton(R.string.sync_sanity_local) {
                        (activity as SyncErrorDialogListener?)
                            ?.showSyncErrorDialog(DIALOG_SYNC_SANITY_ERROR_CONFIRM_KEEP_LOCAL)
                    }
                    neutralButton(R.string.sync_sanity_remote) {
                        (activity as SyncErrorDialogListener?)
                            ?.showSyncErrorDialog(DIALOG_SYNC_SANITY_ERROR_CONFIRM_KEEP_REMOTE)
                    }
                    negativeButton(R.string.dialog_cancel)
                }
            }
            DIALOG_SYNC_SANITY_ERROR_CONFIRM_KEEP_LOCAL -> {

                // Confirmation before pushing local collection to server after sanity check error
                dialog.show {
                    positiveButton(R.string.dialog_positive_replace) {
                        (activity as SyncErrorDialogListener).sync(ConflictResolution.FULL_UPLOAD)
                        dismissAllDialogFragments()
                    }
                    negativeButton(R.string.dialog_cancel)
                }
            }
            DIALOG_SYNC_SANITY_ERROR_CONFIRM_KEEP_REMOTE -> {

                // Confirmation before overwriting local collection with server collection after sanity check error
                dialog.show {
                    positiveButton(R.string.dialog_positive_replace) {
                        (activity as SyncErrorDialogListener).sync(ConflictResolution.FULL_DOWNLOAD)
                        dismissAllDialogFragments()
                    }
                    negativeButton(R.string.dialog_cancel)
                }
            }
            DIALOG_MEDIA_SYNC_ERROR -> {
                dialog.show {
                    positiveButton(R.string.check_media) {
                        (activity as SyncErrorDialogListener).mediaCheck()
                        dismissAllDialogFragments()
                    }
                    negativeButton(R.string.dialog_cancel)
                }
            }
            DIALOG_SYNC_CORRUPT_COLLECTION -> {
                dialog.show {
                    positiveButton(R.string.dialog_ok)
                    negativeButton(R.string.help) {
                        (requireActivity() as AnkiActivity).openUrl(Uri.parse(getString(R.string.repair_deck)))
                    }
                    cancelable(false)
                }
            }
            DIALOG_SYNC_BASIC_CHECK_ERROR -> {
                dialog.show {
                    positiveButton(R.string.check_db) {
                        (activity as SyncErrorDialogListener).integrityCheck()
                        dismissAllDialogFragments()
                    }
                    negativeButton(R.string.dialog_cancel)
                }
            }
            else -> null!!
        }
    }

    private val title: String
        get() = when (requireArguments().getInt("dialogType")) {
            DIALOG_USER_NOT_LOGGED_IN_SYNC -> resources.getString(R.string.not_logged_in_title)
            DIALOG_SYNC_CONFLICT_CONFIRM_KEEP_LOCAL, DIALOG_SYNC_CONFLICT_CONFIRM_KEEP_REMOTE -> resources.getString(R.string.sync_conflict_replace_title)
            DIALOG_SYNC_CONFLICT_RESOLUTION -> resources.getString(R.string.sync_conflict_title_new)
            else -> resources.getString(R.string.sync_error)
        }

    /**
     * Get the title which is shown in notification bar when dialog fragment can't be shown
     *
     * @return tile to be shown in notification in bar
     */
    override val notificationTitle: String
        get() {
            return if (requireArguments().getInt("dialogType") == DIALOG_USER_NOT_LOGGED_IN_SYNC) {
                resources.getString(R.string.sync_error)
            } else title
        }

    private val message: String?
        get() = when (requireArguments().getInt("dialogType")) {
            DIALOG_USER_NOT_LOGGED_IN_SYNC -> resources.getString(R.string.login_create_account_message)
            DIALOG_CONNECTION_ERROR -> resources.getString(R.string.connection_error_message)
            DIALOG_SYNC_CONFLICT_RESOLUTION -> resources.getString(R.string.sync_conflict_message_new)
            DIALOG_SYNC_CONFLICT_CONFIRM_KEEP_LOCAL, DIALOG_SYNC_SANITY_ERROR_CONFIRM_KEEP_LOCAL -> resources.getString(R.string.sync_conflict_local_confirm_new)
            DIALOG_SYNC_CONFLICT_CONFIRM_KEEP_REMOTE, DIALOG_SYNC_SANITY_ERROR_CONFIRM_KEEP_REMOTE -> resources.getString(R.string.sync_conflict_remote_confirm_new)
            DIALOG_SYNC_CORRUPT_COLLECTION -> {
                val syncMessage = requireArguments().getString("dialogMessage")
                val repairUrl = getString(R.string.repair_deck)
                val dialogMessage = getString(R.string.sync_corrupt_database, repairUrl)
                DeckPicker.joinSyncMessages(dialogMessage, syncMessage)
            }
            else -> requireArguments().getString("dialogMessage")
        }

    /**
     * Get the message which is shown in notification bar when dialog fragment can't be shown
     *
     * @return message to be shown in notification in bar
     */
    override val notificationMessage: String?
        get() {
            return if (requireArguments().getInt("dialogType") == DIALOG_USER_NOT_LOGGED_IN_SYNC) {
                resources.getString(R.string.not_logged_in_title)
            } else message
        }

    override val dialogHandlerMessage: Message
        get() {
            val msg = Message.obtain()
            msg.what = DialogHandler.MSG_SHOW_SYNC_ERROR_DIALOG
            val b = Bundle()
            b.putInt("dialogType", requireArguments().getInt("dialogType"))
            b.putString("dialogMessage", requireArguments().getString("dialogMessage"))
            msg.data = b
            return msg
        }

    fun dismissAllDialogFragments() {
        (activity as SyncErrorDialogListener).dismissAllDialogFragments()
    }

    companion object {
        const val DIALOG_USER_NOT_LOGGED_IN_SYNC = 0
        const val DIALOG_CONNECTION_ERROR = 1
        const val DIALOG_SYNC_CONFLICT_RESOLUTION = 2
        const val DIALOG_SYNC_CONFLICT_CONFIRM_KEEP_LOCAL = 3
        const val DIALOG_SYNC_CONFLICT_CONFIRM_KEEP_REMOTE = 4
        const val DIALOG_SYNC_SANITY_ERROR = 6
        const val DIALOG_SYNC_SANITY_ERROR_CONFIRM_KEEP_LOCAL = 7
        const val DIALOG_SYNC_SANITY_ERROR_CONFIRM_KEEP_REMOTE = 8
        const val DIALOG_MEDIA_SYNC_ERROR = 9
        const val DIALOG_SYNC_CORRUPT_COLLECTION = 10
        const val DIALOG_SYNC_BASIC_CHECK_ERROR = 11

        /**
         * A set of dialogs belonging to AnkiActivity which deal with sync problems
         *
         * @param dialogType An integer which specifies which of the sub-dialogs to show
         * @param dialogMessage A string which can be optionally used to set the dialog message
         */
        fun newInstance(dialogType: Int, dialogMessage: String?): SyncErrorDialog {
            val f = SyncErrorDialog()
            val args = Bundle()
            args.putInt("dialogType", dialogType)
            args.putString("dialogMessage", dialogMessage)
            f.arguments = args
            return f
        }
    }
}
