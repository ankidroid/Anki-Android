/*
 *  Copyright (c) 2023 David Allison <davidallisongithub@gmail.com>
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

package com.ichi2.anki

import android.content.Intent
import com.google.android.material.snackbar.Snackbar
import com.ichi2.anki.dialogs.ImportFileSelectionFragment
import com.ichi2.anki.pages.CsvImporter
import com.ichi2.anki.servicelayer.ScopedStorageService
import com.ichi2.anki.snackbar.showSnackbar
import com.ichi2.async.TaskListenerWithContext
import com.ichi2.libanki.importer.AnkiPackageImporter
import com.ichi2.themes.StyledProgressDialog
import com.ichi2.utils.Computation
import com.ichi2.utils.ImportUtils
import timber.log.Timber

// see also:
// ImportFileSelectionFragment - selects 'APKG/COLPKG/CSV' and opens a file picker
// onSelectedPackageToImport/onSelectedCsvForImport
// importUtils - copying selected file into local cache
// ImportDialog - confirmation screen after file copied to cache
//    * ImportDialogListener - DeckPicker implementation of handler for the confirmation screen
//    * DeckPicker.importAdd/importReplace - called from confirmation screen
// BackendBackups/BackendImporting - new backend for importing
// importReplaceListener - old backend listener for importing

fun interface ImportColpkgListener {
    fun onImportColpkg(colpkgPath: String?)
}

fun DeckPicker.onSelectedPackageToImport(data: Intent) {
    val importResult = ImportUtils.handleFileImport(this, data)
    if (!importResult.isSuccess) {
        ImportUtils.showImportUnsuccessfulDialog(this, importResult.humanReadableMessage, false)
    }
}

fun DeckPicker.onSelectedCsvForImport(data: Intent) {
    val path = ImportUtils.getFileCachedCopy(this, data) ?: return
    startActivity(CsvImporter.getIntent(this, path))
}

fun DeckPicker.showImportDialog() {
    if (ScopedStorageService.userMigrationIsInProgress(this)) {
        showSnackbar(
            R.string.functionality_disabled_during_storage_migration,
            Snackbar.LENGTH_SHORT
        )
        return
    }
    showDialogFragment(ImportFileSelectionFragment.createInstance(this))
}

/* Legacy Backend */

fun DeckPicker.importReplaceListener(): TaskListenerWithContext<DeckPicker, String, Computation<*>?> {
    return ImportReplaceListener(this)
}

private class ImportReplaceListener(deckPicker: DeckPicker) : TaskListenerWithContext<DeckPicker, String, Computation<*>?>(deckPicker) {
    override fun actualOnPostExecute(context: DeckPicker, result: Computation<*>?) {
        Timber.i("Import: Replace Task Completed")
        if (context.mProgressDialog != null && context.mProgressDialog!!.isShowing) {
            context.mProgressDialog!!.dismiss()
        }
        val res = context.resources
        if (result!!.succeeded()) {
            context.onImportColpkg(colpkgPath = null)
        } else {
            context.showSimpleMessageDialog(res.getString(R.string.import_log_no_apkg), reload = true)
        }
    }

    override fun actualOnPreExecute(context: DeckPicker) {
        if (context.mProgressDialog == null || !context.mProgressDialog!!.isShowing) {
            context.mProgressDialog = StyledProgressDialog.show(
                context,
                context.resources.getString(R.string.import_title),
                context.resources.getString(R.string.import_replacing),
                false
            )
        }
    }

    /**
     * @param value A message
     */
    override fun actualOnProgressUpdate(context: DeckPicker, value: String) {
        @Suppress("Deprecation")
        context.mProgressDialog!!.setMessage(value)
    }
}

fun DeckPicker.importAddListener(): TaskListenerWithContext<DeckPicker, String, ImporterData?> =
    ImportAddListener(this)

private class ImportAddListener(deckPicker: DeckPicker) : TaskListenerWithContext<DeckPicker, String, ImporterData?>(deckPicker) {
    override fun actualOnPostExecute(context: DeckPicker, result: ImporterData?) {
        if (context.mProgressDialog != null && context.mProgressDialog!!.isShowing) {
            context.mProgressDialog!!.dismiss()
        }
        // If result.errFlag and result are both set, we are signalling
        // some files were imported successfully & some errors occurred.
        // If result.impList is null & result.errList is set
        // we are signalling all the files which were selected threw error
        if (result!!.impList == null && result.errList != null) {
            Timber.w("Import: Add Failed: %s", result.errList)
            context.showSimpleMessageDialog(result.errList)
        } else {
            Timber.i("Import: Add succeeded")

            var fileCount = 0
            var totalCardCount = 0

            var errorMsg = ""

            for (data in result.impList!!) {
                // Check if mLog is not null or empty
                // If mLog is not null or empty that indicates an error has occurred.
                if (data.log.isEmpty()) {
                    fileCount += 1
                    totalCardCount += data.cardCount
                } else { errorMsg += data.fileName + "\n" + data.log[0] + "\n" }
            }

            var dialogMsg = context.resources.getQuantityString(R.plurals.import_complete_message, fileCount, fileCount, totalCardCount)
            if (result.errList != null) {
                errorMsg += result.errList
            }
            if (errorMsg.isNotEmpty()) {
                dialogMsg += "\n\n" + context.resources.getString(R.string.import_stats_error, errorMsg)
            }

            context.showSimpleMessageDialog(dialogMsg)
            context.updateDeckList()
        }
    }

    override fun actualOnPreExecute(context: DeckPicker) {
        if (context.mProgressDialog == null || !context.mProgressDialog!!.isShowing) {
            context.mProgressDialog = StyledProgressDialog.show(
                context,
                context.resources.getString(R.string.import_title),
                null,
                false
            )
        }
    }

    override fun actualOnProgressUpdate(context: DeckPicker, value: String) {
        @Suppress("Deprecation")
        context.mProgressDialog!!.setMessage(value)
    }
}

/**
 * @param impList: List of packages to import
 * @param errList: a string describing the errors. Null if no error.
 */
data class ImporterData(val impList: List<AnkiPackageImporter>?, val errList: String?)
