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
import android.net.Uri
import android.os.Bundle
import android.os.Message
import android.os.Parcelable
import android.view.KeyEvent
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.core.os.BundleCompat
import androidx.core.os.bundleOf
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.WhichButton
import com.afollestad.materialdialogs.actions.setActionButtonEnabled
import com.afollestad.materialdialogs.list.listItems
import com.afollestad.materialdialogs.list.listItemsSingleChoice
import com.ichi2.anki.*
import com.ichi2.anki.dialogs.DatabaseErrorDialog.DatabaseErrorDialogType.*
import com.ichi2.anki.dialogs.ImportFileSelectionFragment.ImportOptions
import com.ichi2.anki.servicelayer.ScopedStorageService
import com.ichi2.async.Connection
import com.ichi2.libanki.Consts
import com.ichi2.libanki.utils.TimeManager
import com.ichi2.utils.*
import com.ichi2.utils.UiUtil.makeBold
import kotlinx.parcelize.Parcelize
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
        val res = res()
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
        return when (requireDialogType()) {
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
                } else if (!ScopedStorageService.mediaMigrationIsInProgress(requireContext())) {
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
                                AlertDialog.Builder(requireActivity()).show {
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
            DIALOG_DISK_FULL -> {
                dialog.show {
                    contentNullable(message)
                    positiveButton(R.string.close) {
                        exit()
                    }
                }
            }
            DIALOG_STORAGE_UNAVAILABLE_AFTER_UNINSTALL -> {
                val listItems = UninstallListItem.createList()
                dialog.show {
                    contentNullable(message)
                    listItems(items = listItems.map { getString(it.stringRes) }, waitForPositiveButton = false) { _: MaterialDialog, index: Int, _: CharSequence ->
                        val listItem = listItems[index]
                        listItem.onClick(activity as DeckPicker)
                        if (listItem.dismissesDialog) {
                            this.dismiss()
                        }
                    }
                    noAutoDismiss()
                    cancelable(false)
                }
            }
        }
    }

    /** List items for [DIALOG_STORAGE_UNAVAILABLE_AFTER_UNINSTALL] */
    private enum class UninstallListItem(@StringRes val stringRes: Int, val dismissesDialog: Boolean, val onClick: (DeckPicker) -> Unit) {

        RESTORE_FROM_ANKIWEB(
            R.string.restore_data_from_ankiweb,
            dismissesDialog = true,
            {
                this.displayResetToNewDirectoryDialog(it)
            }
        ),
        INSTALL_NON_PLAY_APP_RECOMMENDED(
            R.string.install_non_play_store_ankidroid_recommended,
            dismissesDialog = false,
            {
                val restoreUi = Uri.parse(it.getString(R.string.link_install_non_play_store_install))
                it.openUrl(restoreUi)
            }
        ),
        INSTALL_NON_PLAY_APP_NORMAL(
            R.string.install_non_play_store_ankidroid,
            dismissesDialog = false,
            {
                val restoreUi = Uri.parse(it.getString(R.string.link_install_non_play_store_install))
                it.openUrl(restoreUi)
            }
        ),
        RESTORE_FROM_BACKUP(
            R.string.restore_data_from_backup,
            dismissesDialog = false,
            { deckPicker ->
                Timber.i("Restoring from colpkg")
                val newAnkiDroidDirectory = CollectionHelper.getDefaultAnkiDroidDirectory(deckPicker)
                deckPicker.importColpkgListener = DatabaseRestorationListener(deckPicker, newAnkiDroidDirectory)

                deckPicker.launchCatchingTask {
                    CollectionHelper.ankiDroidDirectoryOverride = newAnkiDroidDirectory

                    CollectionManager.withCol {
                        deckPicker.showImportDialog(
                            ImportOptions(
                                importTextFile = false,
                                importColpkg = true,
                                importApkg = false
                            )
                        )
                    }
                }
            }
        ),
        GET_HELP(
            R.string.help_title_get_help,
            dismissesDialog = false,
            {
                it.openUrl(Uri.parse(it.getString(R.string.link_forum)))
            }
        ),
        RECREATE_COLLECTION(
            R.string.create_new_collection,
            dismissesDialog = false,
            {
                this.displayResetToNewDirectoryDialog(it)
            }
        );

        companion object {
            /** A dialog which creates a new collection in an unsafe location */
            fun displayResetToNewDirectoryDialog(context: DeckPicker) {
                AlertDialog.Builder(context).show {
                    title(R.string.backup_new_collection)
                    iconAttr(R.attr.dialogErrorIcon)
                    message(R.string.new_unsafe_collection)
                    positiveButton(R.string.dialog_positive_create) {
                        Timber.w("Creating new collection")
                        val ch = CollectionHelper.instance
                        ch.closeCollection(false, "DatabaseErrorDialog: Before Create New Collection")
                        CollectionHelper.resetAnkiDroidDirectory(context)
                        context.exit()
                    }
                    negativeButton(R.string.dialog_cancel)
                    cancelable(false)
                }
            }
            fun createList(): List<UninstallListItem> {
                return if (isLoggedIn()) {
                    listOf(RESTORE_FROM_ANKIWEB, INSTALL_NON_PLAY_APP_NORMAL, RESTORE_FROM_BACKUP, GET_HELP, RECREATE_COLLECTION)
                } else {
                    listOf(INSTALL_NON_PLAY_APP_RECOMMENDED, RESTORE_FROM_BACKUP, GET_HELP, RECREATE_COLLECTION)
                }
            }
        }
    }

    private fun exit() {
        (activity as DeckPicker).exit()
    } // Generic message shown when a libanki task failed

    // The sqlite database has been corrupted (DatabaseErrorHandler.onCorrupt() was called)
    // Show a specific message appropriate for the situation
    private val message: String?
        get() = when (requireDialogType()) {
            DIALOG_LOAD_FAILED -> if (databaseCorruptFlag) {
                // The sqlite database has been corrupted (DatabaseErrorHandler.onCorrupt() was called)
                // Show a specific message appropriate for the situation
                res().getString(R.string.corrupt_db_message, res().getString(R.string.repair_deck))
            } else {
                // Generic message shown when a libanki task failed
                res().getString(R.string.access_collection_failed_message, res().getString(R.string.link_help))
            }
            DIALOG_DB_ERROR -> res().getString(R.string.answering_error_message)
            DIALOG_DISK_FULL -> res().getString(R.string.storage_full_message)
            DIALOG_REPAIR_COLLECTION -> res().getString(R.string.repair_deck_dialog, BackupManager.BROKEN_COLLECTIONS_SUFFIX)
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
                val schemaVersion = if (BackendFactory.defaultLegacySchema) {
                    Consts.LEGACY_SCHEMA_VERSION
                } else {
                    Consts.BACKEND_SCHEMA_VERSION
                }
                res().getString(
                    R.string.incompatible_database_version_summary,
                    schemaVersion,
                    databaseVersion
                )
            }
            DIALOG_STORAGE_UNAVAILABLE_AFTER_UNINSTALL -> res().getString(R.string.directory_inaccessible_after_uninstall_summary, CollectionHelper.getCurrentAnkiDroidDirectory(requireContext()))
            else -> requireArguments().getString("dialogMessage")
        }
    private val title: String
        get() = when (requireDialogType()) {
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
            DIALOG_DISK_FULL -> res().getString(R.string.storage_full_title)
            DIALOG_STORAGE_UNAVAILABLE_AFTER_UNINSTALL -> res().getString(R.string.directory_inaccessible_after_uninstall)
        }

    override val notificationMessage: String? get() = message
    override val notificationTitle: String get() = res().getString(R.string.answering_error_title)

    override val dialogHandlerMessage
        get() = ShowDatabaseErrorDialog(requireDialogType())

    private fun requireDialogType() = BundleCompat.getParcelable(requireArguments(), "dialog", DatabaseErrorDialogType::class.java)!!

    fun dismissAllDialogFragments() {
        (activity as DeckPicker).dismissAllDialogFragments()
    }

    @Parcelize
    enum class DatabaseErrorDialogType : Parcelable {
        DIALOG_LOAD_FAILED,
        DIALOG_DB_ERROR,
        DIALOG_ERROR_HANDLING,
        DIALOG_REPAIR_COLLECTION,
        DIALOG_RESTORE_BACKUP,
        DIALOG_NEW_COLLECTION,
        DIALOG_CONFIRM_DATABASE_CHECK,
        DIALOG_CONFIRM_RESTORE_BACKUP,
        DIALOG_FULL_SYNC_FROM_SERVER,

        /** If the database is locked, all we can do is reset the app  */
        DIALOG_DB_LOCKED,

        /** If the database is at a version higher than what we can currently handle  */
        INCOMPATIBLE_DB_VERSION,

        /** If the disk space is full **/
        DIALOG_DISK_FULL,

        /** If [android.R.attr.preserveLegacyExternalStorage] is no longer active */
        DIALOG_STORAGE_UNAVAILABLE_AFTER_UNINSTALL;
    }

    companion object {

        // public flag which lets us distinguish between inaccessible and corrupt database
        var databaseCorruptFlag = false

        /**
         * A set of dialogs which deal with problems with the database when it can't load
         *
         * @param dialogType the sub-dialog to show
         */
        fun newInstance(dialogType: DatabaseErrorDialogType): DatabaseErrorDialog {
            val f = DatabaseErrorDialog()
            val args = Bundle()
            args.putParcelable("dialog", dialogType)
            f.arguments = args
            return f
        }
    }

    /** Database error dialog */
    class ShowDatabaseErrorDialog(val dialogType: DatabaseErrorDialogType) : DialogHandlerMessage(
        which = WhichDialogHandler.MSG_SHOW_DATABASE_ERROR_DIALOG,
        analyticName = "DatabaseErrorDialog"
    ) {
        override fun handleAsyncMessage(deckPicker: DeckPicker) {
            deckPicker.showDatabaseErrorDialog(dialogType)
        }

        override fun toMessage(): Message = Message.obtain().apply {
            what = this@ShowDatabaseErrorDialog.what
            data = bundleOf(
                "dialog" to dialogType
            )
        }

        companion object {
            fun fromMessage(message: Message): ShowDatabaseErrorDialog {
                val dialogType = BundleCompat.getParcelable(message.data, "dialog", DatabaseErrorDialogType::class.java)!!
                return ShowDatabaseErrorDialog(dialogType)
            }
        }
    }
}
