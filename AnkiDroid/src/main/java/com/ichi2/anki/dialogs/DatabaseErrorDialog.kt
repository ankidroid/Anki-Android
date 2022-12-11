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

import android.annotation.SuppressLint
import android.content.DialogInterface
import android.os.Bundle
import android.os.Message
import android.view.KeyEvent
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.WhichButton
import com.afollestad.materialdialogs.actions.setActionButtonEnabled
import com.afollestad.materialdialogs.list.listItems
import com.afollestad.materialdialogs.list.listItemsSingleChoice
import com.ichi2.anki.*
import com.ichi2.async.Connection
import com.ichi2.libanki.Consts
import com.ichi2.libanki.utils.TimeManager
import com.ichi2.utils.UiUtil.makeBold
import com.ichi2.utils.contentNullable
import com.ichi2.utils.iconAttr
import net.ankiweb.rsdroid.BackendFactory
import timber.log.Timber
import java.io.File
import java.io.IOException
import java.util.*

class DatabaseErrorDialog : AsyncDialogFragment() {
    private lateinit var mRepairValues: IntArray
    private lateinit var mBackups: Array<File>

    @Suppress("Deprecation") // Material dialog neutral button deprecation
    @SuppressLint("CheckResult")
    override fun onCreateDialog(savedInstanceState: Bundle?): MaterialDialog {
        super.onCreate(savedInstanceState)
        val type = requireArguments().getInt("dialogType")
        val res = resources
        val dialog = MaterialDialog(requireActivity())
        val isLoggedIn = isLoggedIn()
        dialog.cancelable(true)
            .title(text = title)
            .cancelOnTouchOutside(false)
        var sqliteInstalled = false
        try {
            sqliteInstalled = Runtime.getRuntime().exec("sqlite3 --version").waitFor() == 0
        } catch (e: IOException) {
            Timber.w(e)
        } catch (e: InterruptedException) {
            Timber.w(e)
        }
        return when (type) {
            DIALOG_LOAD_FAILED -> {

                // Collection failed to load; give user the option of either choosing from repair options, or closing
                // the activity
                dialog.show {
                    cancelable(false)
                    contentNullable(message)
                    iconAttr(R.attr.dialogErrorIcon)
                    positiveButton(R.string.error_handling_options) {
                        (activity as DeckPicker?)
                            ?.showDatabaseErrorDialog(DIALOG_ERROR_HANDLING)
                    }
                    negativeButton(R.string.close) {
                        exit()
                    }
                }
            }
            DIALOG_DB_ERROR -> {

                // Database Check failed to execute successfully; give user the option of either choosing from repair
                // options, submitting an error report, or closing the activity
                dialog.show {
                    cancelable(false)
                    contentNullable(message)
                    iconAttr(R.attr.dialogErrorIcon)
                    positiveButton(R.string.error_handling_options) {
                        (activity as DeckPicker?)
                            ?.showDatabaseErrorDialog(DIALOG_ERROR_HANDLING)
                    }
                    negativeButton(R.string.answering_error_report) {
                        (activity as DeckPicker).sendErrorReport()
                        dismissAllDialogFragments()
                    }
                    neutralButton(R.string.close) {
                        exit()
                    }
                    setActionButtonEnabled(WhichButton.NEGATIVE, (activity as DeckPicker).hasErrorFiles())
                }
            }
            DIALOG_ERROR_HANDLING -> {

                // The user has asked to see repair options; allow them to choose one of the repair options or go back
                // to the previous dialog
                val options = ArrayList<String>(6)
                val values = ArrayList<Int>(6)
                if (!(activity as AnkiActivity).colIsOpen()) {
                    // retry
                    options.add(res.getString(R.string.backup_retry_opening))
                    values.add(0)
                } else {
                    // fix integrity
                    options.add(res.getString(R.string.check_db))
                    values.add(1)
                }
                // repair db with sqlite
                if (sqliteInstalled) {
                    options.add(res.getString(R.string.backup_error_menu_repair))
                    values.add(2)
                }
                // // restore from backup
                options.add(res.getString(R.string.backup_restore))
                values.add(3)
                // full sync from server
                if (isLoggedIn) {
                    options.add(res.getString(R.string.backup_full_sync_from_server))
                    values.add(4)
                }
                // delete old collection and build new one
                options.add(res.getString(R.string.backup_del_collection))
                values.add(5)
                val titles = arrayOfNulls<String>(options.size).toMutableList()
                mRepairValues = IntArray(options.size)
                var i = 0
                while (i < options.size) {
                    titles[i] = options[i]
                    mRepairValues[i] = values[i]
                    i++
                }
                dialog.show {
                    iconAttr(R.attr.dialogErrorIcon)
                    negativeButton(R.string.dialog_cancel)
                    listItems(items = titles.toList().map { it as CharSequence }) { _: MaterialDialog, index: Int, _: CharSequence ->
                        when (mRepairValues[index]) {
                            0 -> {
                                (activity as DeckPicker).recreate()
                                return@listItems
                            }
                            1 -> {
                                (activity as DeckPicker).showDatabaseErrorDialog(DIALOG_CONFIRM_DATABASE_CHECK)
                                return@listItems
                            }
                            2 -> {
                                (activity as DeckPicker).showDatabaseErrorDialog(DIALOG_REPAIR_COLLECTION)
                                return@listItems
                            }
                            3 -> {
                                (activity as DeckPicker).showDatabaseErrorDialog(DIALOG_RESTORE_BACKUP)
                                return@listItems
                            }
                            4 -> {
                                (activity as DeckPicker).showDatabaseErrorDialog(DIALOG_FULL_SYNC_FROM_SERVER)
                                return@listItems
                            }
                            5 -> {
                                (activity as DeckPicker).showDatabaseErrorDialog(DIALOG_NEW_COLLECTION)
                                return@listItems
                            }
                            else -> throw RuntimeException("Unknown dialog selection: " + mRepairValues[index])
                        }
                    }
                }
            }
            DIALOG_REPAIR_COLLECTION -> {

                // Allow user to run BackupManager.repairCollection()
                dialog.show {
                    contentNullable(message)
                    iconAttr(R.attr.dialogErrorIcon)
                    positiveButton(R.string.dialog_positive_repair) {
                        (activity as DeckPicker).repairCollection()
                        dismissAllDialogFragments()
                    }
                    negativeButton(R.string.dialog_cancel)
                }
            }
            DIALOG_RESTORE_BACKUP -> {

                // Allow user to restore one of the backups
                val path = CollectionHelper.getCollectionPath(requireContext())
                mBackups = BackupManager.getBackups(File(path))
                if (mBackups.isEmpty()) {
                    dialog.title(R.string.backup_restore)
                        .contentNullable(message)
                        .positiveButton(R.string.dialog_ok) {
                            (activity as DeckPicker?)
                                ?.showDatabaseErrorDialog(DIALOG_ERROR_HANDLING)
                        }
                } else {
                    // Show backups sorted with latest on top
                    mBackups.reverse()
                    val formatter = LocalizedUnambiguousBackupTimeFormatter()
                    val dates = mBackups.map { formatter.getTimeOfBackupAsText(it) }

                    dialog.title(R.string.backup_restore_select_title)
                        .positiveButton(R.string.restore_backup_choose_another) {
                            ImportFileSelectionFragment.openImportFilePicker(activity as AnkiActivity, DeckPicker.PICK_APKG_FILE)
                        }
                        .negativeButton(R.string.dialog_cancel)
                        .listItemsSingleChoice(items = dates.toTypedArray().toList(), waitForPositiveButton = false) { _: MaterialDialog, index: Int, _: CharSequence ->
                            if (mBackups[index].length() > 0) {
                                // restore the backup if it's valid
                                (activity as DeckPicker?)
                                    ?.restoreFromBackup(
                                        mBackups[index].path
                                    )
                                dismissAllDialogFragments()
                            } else {
                                // otherwise show an error dialog
                                MaterialDialog(requireActivity()).show {
                                    title(R.string.vague_error)
                                    message(R.string.backup_invalid_file_error)
                                    positiveButton(R.string.dialog_ok)
                                }
                            }
                        }
                        // needed because listItemsSingleChoice disables the positive button and we
                        // want to allow in the dialog direct item selection and different action for
                        // positive button
                        .setActionButtonEnabled(WhichButton.POSITIVE, true)
                }
                dialog.setOnKeyListener { _: DialogInterface?, keyCode: Int, _: KeyEvent? ->
                    if (keyCode == KeyEvent.KEYCODE_BACK) {
                        Timber.i("DIALOG_RESTORE_BACKUP caught hardware back button")
                        dismissAllDialogFragments()
                        return@setOnKeyListener true
                    }
                    false
                }
                dialog
            }
            DIALOG_NEW_COLLECTION -> {

                // Allow user to create a new empty collection
                dialog.show {
                    contentNullable(message)
                    positiveButton(R.string.dialog_positive_create) {
                        val ch = CollectionHelper.instance
                        val time = TimeManager.time
                        ch.closeCollection(false, "DatabaseErrorDialog: Before Create New Collection")
                        val path1 = CollectionHelper.getCollectionPath(requireActivity())
                        if (BackupManager.moveDatabaseToBrokenDirectory(path1, false, time)) {
                            (activity as DeckPicker).recreate()
                        } else {
                            (activity as DeckPicker).showDatabaseErrorDialog(DIALOG_LOAD_FAILED)
                        }
                    }
                    negativeButton(R.string.dialog_cancel)
                }
            }
            DIALOG_CONFIRM_DATABASE_CHECK -> {

                // Confirmation dialog for database check
                dialog.show {
                    contentNullable(message)
                    positiveButton(R.string.dialog_ok) {
                        (activity as DeckPicker).integrityCheck()
                        dismissAllDialogFragments()
                    }
                    negativeButton(R.string.dialog_cancel)
                }
            }
            DIALOG_CONFIRM_RESTORE_BACKUP -> {

                // Confirmation dialog for backup restore
                dialog.show {
                    contentNullable(message)
                    positiveButton(R.string.dialog_continue) {
                        (activity as DeckPicker?)
                            ?.showDatabaseErrorDialog(DIALOG_RESTORE_BACKUP)
                    }
                    negativeButton(R.string.dialog_cancel)
                }
            }
            DIALOG_FULL_SYNC_FROM_SERVER -> {

                // Allow user to do a full-sync from the server
                dialog.show {
                    contentNullable(message)
                    positiveButton(R.string.dialog_positive_overwrite) {
                        (activity as DeckPicker).sync(Connection.ConflictResolution.FULL_DOWNLOAD)
                        dismissAllDialogFragments()
                    }
                    negativeButton(R.string.dialog_cancel)
                }
            }
            DIALOG_DB_LOCKED -> {

                // If the database is locked, all we can do is ask the user to exit.
                dialog.show {
                    contentNullable(message)
                    positiveButton(R.string.close) {
                        exit()
                    }
                    cancelable(false)
                }
            }
            INCOMPATIBLE_DB_VERSION -> {
                val values: MutableList<Int> = ArrayList(2)
                val options = mutableListOf<CharSequence>()
                options.add(makeBold(res.getString(R.string.backup_restore)))
                values.add(0)
                if (isLoggedIn) {
                    options.add(makeBold(res.getString(R.string.backup_full_sync_from_server)))
                    values.add(1)
                }
                dialog.show {
                    cancelable(false)
                    contentNullable(message)
                    iconAttr(R.attr.dialogErrorIcon)
                    positiveButton(R.string.close) {
                        exit()
                    }
                    listItems(items = options, waitForPositiveButton = false) { _: MaterialDialog, index: Int, _: CharSequence ->
                        when (values[index]) {
                            0 -> (activity as DeckPicker).showDatabaseErrorDialog(
                                DIALOG_RESTORE_BACKUP
                            )
                            1 -> (activity as DeckPicker).showDatabaseErrorDialog(
                                DIALOG_FULL_SYNC_FROM_SERVER
                            )
                        }
                    }
                }
            }
            else -> null!!
        }
    }

    private fun exit() {
        (activity as DeckPicker).exit()
    } // Generic message shown when a libanki task failed

    // The sqlite database has been corrupted (DatabaseErrorHandler.onCorrupt() was called)
    // Show a specific message appropriate for the situation
    private val message: String?
        get() = when (requireArguments().getInt("dialogType")) {
            DIALOG_LOAD_FAILED -> if (databaseCorruptFlag) {
                // The sqlite database has been corrupted (DatabaseErrorHandler.onCorrupt() was called)
                // Show a specific message appropriate for the situation
                resources.getString(R.string.corrupt_db_message, resources.getString(R.string.repair_deck))
            } else {
                // Generic message shown when a libanki task failed
                resources.getString(R.string.access_collection_failed_message, resources.getString(R.string.link_help))
            }
            DIALOG_DB_ERROR -> resources.getString(R.string.answering_error_message)
            DIALOG_REPAIR_COLLECTION -> resources.getString(R.string.repair_deck_dialog, BackupManager.BROKEN_COLLECTIONS_SUFFIX)
            DIALOG_RESTORE_BACKUP -> resources.getString(R.string.backup_restore_no_backups)
            DIALOG_NEW_COLLECTION -> resources.getString(R.string.backup_del_collection_question)
            DIALOG_CONFIRM_DATABASE_CHECK -> resources.getString(R.string.check_db_warning)
            DIALOG_CONFIRM_RESTORE_BACKUP -> resources.getString(R.string.restore_backup)
            DIALOG_FULL_SYNC_FROM_SERVER -> resources.getString(R.string.backup_full_sync_from_server_question)
            DIALOG_DB_LOCKED -> resources.getString(R.string.database_locked_summary)
            INCOMPATIBLE_DB_VERSION -> {
                var databaseVersion = -1
                try {
                    databaseVersion = CollectionHelper.getDatabaseVersion(requireContext())
                } catch (e: Exception) {
                    Timber.w(e, "Failed to get database version, using -1")
                }
                val schemaVersion = if (BackendFactory.defaultLegacySchema) {
                    Consts.LEGACY_SCHEMA_VERSION
                } else {
                    Consts.BACKEND_SCHEMA_VERSION
                }
                resources.getString(
                    R.string.incompatible_database_version_summary,
                    schemaVersion,
                    databaseVersion
                )
            }
            else -> requireArguments().getString("dialogMessage")
        }
    private val title: String
        get() = when (requireArguments().getInt("dialogType")) {
            DIALOG_LOAD_FAILED -> resources.getString(R.string.open_collection_failed_title)
            DIALOG_ERROR_HANDLING -> resources.getString(R.string.error_handling_title)
            DIALOG_REPAIR_COLLECTION -> resources.getString(R.string.dialog_positive_repair)
            DIALOG_RESTORE_BACKUP -> resources.getString(R.string.backup_restore)
            DIALOG_NEW_COLLECTION -> resources.getString(R.string.backup_new_collection)
            DIALOG_CONFIRM_DATABASE_CHECK -> resources.getString(R.string.check_db_title)
            DIALOG_CONFIRM_RESTORE_BACKUP -> resources.getString(R.string.restore_backup_title)
            DIALOG_FULL_SYNC_FROM_SERVER -> resources.getString(R.string.backup_full_sync_from_server)
            DIALOG_DB_LOCKED -> resources.getString(R.string.database_locked_title)
            INCOMPATIBLE_DB_VERSION -> resources.getString(R.string.incompatible_database_version_title)
            DIALOG_DB_ERROR -> resources.getString(R.string.answering_error_title)
            else -> resources.getString(R.string.answering_error_title)
        }

    override val notificationMessage: String? get() = message
    override val notificationTitle: String get() = resources.getString(R.string.answering_error_title)

    override val dialogHandlerMessage: Message
        get() {
            val msg = Message.obtain()
            msg.what = DialogHandler.MSG_SHOW_DATABASE_ERROR_DIALOG
            val b = Bundle()
            b.putInt("dialogType", requireArguments().getInt("dialogType"))
            msg.data = b
            return msg
        }

    fun dismissAllDialogFragments() {
        (activity as DeckPicker).dismissAllDialogFragments()
    }

    companion object {
        const val DIALOG_LOAD_FAILED = 0
        const val DIALOG_DB_ERROR = 1
        const val DIALOG_ERROR_HANDLING = 2
        const val DIALOG_REPAIR_COLLECTION = 3
        const val DIALOG_RESTORE_BACKUP = 4
        const val DIALOG_NEW_COLLECTION = 5
        const val DIALOG_CONFIRM_DATABASE_CHECK = 6
        const val DIALOG_CONFIRM_RESTORE_BACKUP = 7
        const val DIALOG_FULL_SYNC_FROM_SERVER = 8

        /** If the database is locked, all we can do is reset the app  */
        const val DIALOG_DB_LOCKED = 9

        /** If the database is at a version higher than what we can currently handle  */
        const val INCOMPATIBLE_DB_VERSION = 10

        // public flag which lets us distinguish between inaccessible and corrupt database
        var databaseCorruptFlag = false

        /**
         * A set of dialogs which deal with problems with the database when it can't load
         *
         * @param dialogType An integer which specifies which of the sub-dialogs to show
         */
        fun newInstance(dialogType: Int): DatabaseErrorDialog {
            val f = DatabaseErrorDialog()
            val args = Bundle()
            args.putInt("dialogType", dialogType)
            f.arguments = args
            return f
        }
    }
}
