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

import android.content.DialogInterface
import android.os.Bundle
import android.os.Message
import android.view.KeyEvent
import android.view.View
import com.afollestad.materialdialogs.DialogAction
import com.afollestad.materialdialogs.MaterialDialog
import com.ichi2.anki.*
import com.ichi2.async.Connection
import com.ichi2.libanki.Consts
import com.ichi2.utils.UiUtil.makeBold
import com.ichi2.utils.contentNullable
import timber.log.Timber
import java.io.File
import java.io.IOException
import java.util.*

class DatabaseErrorDialog : AsyncDialogFragment() {
    private lateinit var mRepairValues: IntArray
    private lateinit var mBackups: Array<File?>
    override fun onCreateDialog(savedInstanceState: Bundle?): MaterialDialog {
        super.onCreate(savedInstanceState)
        val type = requireArguments().getInt("dialogType")
        val res = resources
        val builder = MaterialDialog.Builder(requireActivity())
        builder.cancelable(true)
            .title(title)
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
                builder.cancelable(false)
                    .contentNullable(message)
                    .iconAttr(R.attr.dialogErrorIcon)
                    .positiveText(R.string.error_handling_options)
                    .negativeText(R.string.close)
                    .onPositive { _: MaterialDialog?, _: DialogAction? ->
                        (activity as DeckPicker?)
                            ?.showDatabaseErrorDialog(DIALOG_ERROR_HANDLING)
                    }
                    .onNegative { _: MaterialDialog?, _: DialogAction? -> exit() }
                    .show()
            }
            DIALOG_DB_ERROR -> {

                // Database Check failed to execute successfully; give user the option of either choosing from repair
                // options, submitting an error report, or closing the activity
                val dialog = builder
                    .cancelable(false)
                    .contentNullable(message)
                    .iconAttr(R.attr.dialogErrorIcon)
                    .positiveText(R.string.error_handling_options)
                    .negativeText(R.string.answering_error_report)
                    .neutralText(res.getString(R.string.close))
                    .onPositive { _: MaterialDialog?, _: DialogAction? ->
                        (activity as DeckPicker?)
                            ?.showDatabaseErrorDialog(DIALOG_ERROR_HANDLING)
                    }
                    .onNegative { _: MaterialDialog?, _: DialogAction? ->
                        (activity as DeckPicker?)!!.sendErrorReport()
                        dismissAllDialogFragments()
                    }
                    .onNeutral { _: MaterialDialog?, _: DialogAction? -> exit() }
                    .show()
                dialog.customView!!.findViewById<View>(R.id.md_buttonDefaultNegative).isEnabled = (activity as DeckPicker?)!!.hasErrorFiles()
                dialog
            }
            DIALOG_ERROR_HANDLING -> {

                // The user has asked to see repair options; allow them to choose one of the repair options or go back
                // to the previous dialog
                val options = ArrayList<String>(6)
                val values = ArrayList<Int>(6)
                if (!(activity as AnkiActivity?)!!.colIsOpen()) {
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
                // delete old collection and build new one
                options.add(res.getString(R.string.backup_full_sync_from_server))
                values.add(4)
                // delete old collection and build new one
                options.add(res.getString(R.string.backup_del_collection))
                values.add(5)
                val titles = arrayOfNulls<String>(options.size)
                mRepairValues = IntArray(options.size)
                var i = 0
                while (i < options.size) {
                    titles[i] = options[i]
                    mRepairValues[i] = values[i]
                    i++
                }
                builder.iconAttr(R.attr.dialogErrorIcon)
                    .negativeText(R.string.dialog_cancel)
                    .items(*titles)
                    .itemsCallback { _: MaterialDialog?, _: View?, which: Int, _: CharSequence? ->
                        when (mRepairValues[which]) {
                            0 -> {
                                (activity as DeckPicker?)!!.restartActivity()
                                return@itemsCallback
                            }
                            1 -> {
                                (activity as DeckPicker?)!!.showDatabaseErrorDialog(DIALOG_CONFIRM_DATABASE_CHECK)
                                return@itemsCallback
                            }
                            2 -> {
                                (activity as DeckPicker?)!!.showDatabaseErrorDialog(DIALOG_REPAIR_COLLECTION)
                                return@itemsCallback
                            }
                            3 -> {
                                (activity as DeckPicker?)!!.showDatabaseErrorDialog(DIALOG_RESTORE_BACKUP)
                                return@itemsCallback
                            }
                            4 -> {
                                (activity as DeckPicker?)!!.showDatabaseErrorDialog(DIALOG_FULL_SYNC_FROM_SERVER)
                                return@itemsCallback
                            }
                            5 -> {
                                (activity as DeckPicker?)!!.showDatabaseErrorDialog(DIALOG_NEW_COLLECTION)
                                return@itemsCallback
                            }
                            else -> throw RuntimeException("Unknown dialog selection: " + mRepairValues[which])
                        }
                    }
                    .show()
            }
            DIALOG_REPAIR_COLLECTION -> {

                // Allow user to run BackupManager.repairCollection()
                builder.contentNullable(message)
                    .iconAttr(R.attr.dialogErrorIcon)
                    .positiveText(R.string.dialog_positive_repair)
                    .negativeText(R.string.dialog_cancel)
                    .onPositive { _: MaterialDialog?, _: DialogAction? ->
                        (activity as DeckPicker?)!!.repairCollection()
                        dismissAllDialogFragments()
                    }
                    .show()
            }
            DIALOG_RESTORE_BACKUP -> {

                // Allow user to restore one of the backups
                val path = CollectionHelper.getCollectionPath(activity)
                val files = BackupManager.getBackups(File(path))
                mBackups = arrayOfNulls(files.size)
                var i = 0
                while (i < files.size) {
                    mBackups[i] = files[files.size - 1 - i]
                    i++
                }
                if (mBackups.isEmpty()) {
                    builder.title(res.getString(R.string.backup_restore))
                        .contentNullable(message)
                        .positiveText(R.string.dialog_ok)
                        .onPositive { _: MaterialDialog?, _: DialogAction? ->
                            (activity as DeckPicker?)
                                ?.showDatabaseErrorDialog(DIALOG_ERROR_HANDLING)
                        }
                } else {
                    val dates = arrayOfNulls<String>(mBackups.size)
                    var j = 0
                    while (j < mBackups.size) {
                        dates[j] = mBackups[j]!!.name.replace(
                            ".*-(\\d{4}-\\d{2}-\\d{2})-(\\d{2})-(\\d{2}).apkg".toRegex(), "$1 ($2:$3 h)"
                        )
                        j++
                    }
                    builder.title(res.getString(R.string.backup_restore_select_title))
                        .negativeText(R.string.dialog_cancel)
                        .items(*dates)
                        .itemsCallbackSingleChoice(
                            dates.size
                        ) { _: MaterialDialog?, _: View?, which: Int, _: CharSequence? ->
                            if (mBackups[which]!!.length() > 0) {
                                // restore the backup if it's valid
                                (activity as DeckPicker?)
                                    ?.restoreFromBackup(
                                        mBackups[which]

                                            ?.path
                                    )
                                dismissAllDialogFragments()
                            } else {
                                // otherwise show an error dialog
                                MaterialDialog.Builder(requireActivity())
                                    .title(R.string.vague_error)
                                    .content(R.string.backup_invalid_file_error)
                                    .positiveText(R.string.dialog_ok)
                                    .build().show()
                            }
                            true
                        }
                }
                val materialDialog = builder.build()
                materialDialog.setOnKeyListener { _: DialogInterface?, keyCode: Int, _: KeyEvent? ->
                    if (keyCode == KeyEvent.KEYCODE_BACK) {
                        Timber.i("DIALOG_RESTORE_BACKUP caught hardware back button")
                        dismissAllDialogFragments()
                        return@setOnKeyListener true
                    }
                    false
                }
                materialDialog
            }
            DIALOG_NEW_COLLECTION -> {

                // Allow user to create a new empty collection
                builder.contentNullable(message)
                    .positiveText(R.string.dialog_positive_create)
                    .negativeText(R.string.dialog_cancel)
                    .onPositive { _: MaterialDialog?, _: DialogAction? ->
                        val ch = CollectionHelper.getInstance()
                        val time = ch.getTimeSafe(context)
                        ch.closeCollection(false, "DatabaseErrorDialog: Before Create New Collection")
                        val path1 = CollectionHelper.getCollectionPath(activity)
                        if (BackupManager.moveDatabaseToBrokenFolder(path1, false, time)) {
                            (activity as DeckPicker?)!!.restartActivity()
                        } else {
                            (activity as DeckPicker?)!!.showDatabaseErrorDialog(DIALOG_LOAD_FAILED)
                        }
                    }
                    .show()
            }
            DIALOG_CONFIRM_DATABASE_CHECK -> {

                // Confirmation dialog for database check
                builder.contentNullable(message)
                    .positiveText(R.string.dialog_ok)
                    .negativeText(R.string.dialog_cancel)
                    .onPositive { _: MaterialDialog?, _: DialogAction? ->
                        (activity as DeckPicker?)!!.integrityCheck()
                        dismissAllDialogFragments()
                    }
                    .show()
            }
            DIALOG_CONFIRM_RESTORE_BACKUP -> {

                // Confirmation dialog for backup restore
                builder.contentNullable(message)
                    .positiveText(R.string.dialog_continue)
                    .negativeText(R.string.dialog_cancel)
                    .onPositive { _: MaterialDialog?, _: DialogAction? ->
                        (activity as DeckPicker?)
                            ?.showDatabaseErrorDialog(DIALOG_RESTORE_BACKUP)
                    }
                    .show()
            }
            DIALOG_FULL_SYNC_FROM_SERVER -> {

                // Allow user to do a full-sync from the server
                builder.contentNullable(message)
                    .positiveText(R.string.dialog_positive_overwrite)
                    .negativeText(R.string.dialog_cancel)
                    .onPositive { _: MaterialDialog?, _: DialogAction? ->
                        (activity as DeckPicker?)!!.sync(Connection.ConflictResolution.FULL_DOWNLOAD)
                        dismissAllDialogFragments()
                    }
                    .show()
            }
            DIALOG_DB_LOCKED -> {

                // If the database is locked, all we can do is ask the user to exit.
                builder.contentNullable(message)
                    .positiveText(R.string.close)
                    .cancelable(false)
                    .onPositive { _: MaterialDialog?, _: DialogAction? -> exit() }
                    .show()
            }
            INCOMPATIBLE_DB_VERSION -> {
                val values: MutableList<Int> = ArrayList(2)
                val options = arrayOf<CharSequence>(makeBold(res.getString(R.string.backup_restore)), makeBold(res.getString(R.string.backup_full_sync_from_server)))
                values.add(0)
                values.add(1)
                builder
                    .cancelable(false)
                    .contentNullable(message)
                    .iconAttr(R.attr.dialogErrorIcon)
                    .positiveText(R.string.close)
                    .onPositive { _: MaterialDialog?,
                        _: DialogAction? ->
                        exit()
                    }
                    .items(*options) // .itemsColor(ContextCompat.getColor(requireContext(), R.color.material_grey_500))
                    .itemsCallback { _: MaterialDialog?, _: View?, position: Int, _: CharSequence? ->
                        when (values[position]) {
                            0 -> (activity as DeckPicker?)!!.showDatabaseErrorDialog(DIALOG_RESTORE_BACKUP)
                            1 -> (activity as DeckPicker?)!!.showDatabaseErrorDialog(DIALOG_FULL_SYNC_FROM_SERVER)
                        }
                    }
                    .show()
            }
            else -> null!!
        }
    }

    private fun exit() {
        (activity as DeckPicker?)!!.exit()
    } // Generic message shown when a libanki task failed

    // The sqlite database has been corrupted (DatabaseErrorHandler.onCorrupt() was called)
    // Show a specific message appropriate for the situation
    private val message: String?
        get() = when (requireArguments().getInt("dialogType")) {
            DIALOG_LOAD_FAILED -> if (databaseCorruptFlag) {
                // The sqlite database has been corrupted (DatabaseErrorHandler.onCorrupt() was called)
                // Show a specific message appropriate for the situation
                res().getString(R.string.corrupt_db_message, res().getString(R.string.repair_deck))
            } else {
                // Generic message shown when a libanki task failed
                res().getString(R.string.access_collection_failed_message, res().getString(R.string.link_help))
            }
            DIALOG_DB_ERROR -> res().getString(R.string.answering_error_message)
            DIALOG_REPAIR_COLLECTION -> res().getString(R.string.repair_deck_dialog, BackupManager.BROKEN_DECKS_SUFFIX)
            DIALOG_RESTORE_BACKUP -> res().getString(R.string.backup_restore_no_backups)
            DIALOG_NEW_COLLECTION -> res().getString(R.string.backup_del_collection_question)
            DIALOG_CONFIRM_DATABASE_CHECK -> res().getString(R.string.check_db_warning)
            DIALOG_CONFIRM_RESTORE_BACKUP -> res().getString(R.string.restore_backup)
            DIALOG_FULL_SYNC_FROM_SERVER -> res().getString(R.string.backup_full_sync_from_server_question)
            DIALOG_DB_LOCKED -> res().getString(R.string.database_locked_summary)
            INCOMPATIBLE_DB_VERSION -> {
                var databaseVersion = -1
                try {
                    databaseVersion = CollectionHelper.getDatabaseVersion(requireContext())
                } catch (e: Exception) {
                    Timber.w(e, "Failed to get database version, using -1")
                }
                res().getString(R.string.incompatible_database_version_summary, Consts.SCHEMA_VERSION, databaseVersion)
            }
            else -> requireArguments().getString("dialogMessage")
        }
    private val title: String
        get() = when (requireArguments().getInt("dialogType")) {
            DIALOG_LOAD_FAILED -> res().getString(R.string.open_collection_failed_title)
            DIALOG_ERROR_HANDLING -> res().getString(R.string.error_handling_title)
            DIALOG_REPAIR_COLLECTION -> res().getString(R.string.dialog_positive_repair)
            DIALOG_RESTORE_BACKUP -> res().getString(R.string.backup_restore)
            DIALOG_NEW_COLLECTION -> res().getString(R.string.backup_new_collection)
            DIALOG_CONFIRM_DATABASE_CHECK -> res().getString(R.string.check_db_title)
            DIALOG_CONFIRM_RESTORE_BACKUP -> res().getString(R.string.restore_backup_title)
            DIALOG_FULL_SYNC_FROM_SERVER -> res().getString(R.string.backup_full_sync_from_server)
            DIALOG_DB_LOCKED -> res().getString(R.string.database_locked_title)
            INCOMPATIBLE_DB_VERSION -> res().getString(R.string.incompatible_database_version_title)
            DIALOG_DB_ERROR -> res().getString(R.string.answering_error_title)
            else -> res().getString(R.string.answering_error_title)
        }

    override fun getNotificationMessage(): String? {
        return message
    }

    override fun getNotificationTitle(): String {
        return res().getString(R.string.answering_error_title)
    }

    override fun getDialogHandlerMessage(): Message {
        val msg = Message.obtain()
        msg.what = DialogHandler.MSG_SHOW_DATABASE_ERROR_DIALOG
        val b = Bundle()
        b.putInt("dialogType", requireArguments().getInt("dialogType"))
        msg.data = b
        return msg
    }

    fun dismissAllDialogFragments() {
        (activity as DeckPicker?)!!.dismissAllDialogFragments()
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
        @JvmField
        var databaseCorruptFlag = false

        /**
         * A set of dialogs which deal with problems with the database when it can't load
         *
         * @param dialogType An integer which specifies which of the sub-dialogs to show
         */
        @JvmStatic
        fun newInstance(dialogType: Int): DatabaseErrorDialog {
            val f = DatabaseErrorDialog()
            val args = Bundle()
            args.putInt("dialogType", dialogType)
            f.arguments = args
            return f
        }
    }
}
