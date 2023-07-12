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
import androidx.core.content.edit
import com.google.android.material.snackbar.Snackbar
import com.ichi2.anki.dialogs.AsyncDialogFragment
import com.ichi2.anki.dialogs.ImportDialog
import com.ichi2.anki.dialogs.ImportFileSelectionFragment
import com.ichi2.anki.dialogs.ImportFileSelectionFragment.ImportOptions
import com.ichi2.anki.pages.CsvImporter
import com.ichi2.anki.preferences.sharedPrefs
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

fun DeckPicker.showImportDialog(id: Int, messageList: ArrayList<String>) {
    Timber.d("showImportDialog() delegating to ImportDialog")
    if (messageList.isEmpty()) {
        messageList.add("")
    }
    val newFragment: AsyncDialogFragment = ImportDialog.newInstance(id, messageList)
    showAsyncDialogFragment(newFragment)
}
fun DeckPicker.showImportDialog() {
    showImportDialog(
        ImportOptions(
            importApkg = true,
            importColpkg = true,
            importTextFile = true
        )
    )
}

fun DeckPicker.showImportDialog(options: ImportOptions) {
    if (ScopedStorageService.mediaMigrationIsInProgress(this)) {
        showSnackbar(
            R.string.functionality_disabled_during_storage_migration,
            Snackbar.LENGTH_SHORT
        )
        return
    }
    showDialogFragment(ImportFileSelectionFragment.createInstance(this, options))
}

class DatabaseRestorationListener(val deckPicker: DeckPicker, val newAnkiDroidDirectory: String) : ImportColpkgListener {
    override fun onImportColpkg(colpkgPath: String?) {
        Timber.i("Database restoration correct")
        deckPicker.sharedPrefs().edit {
            putString("deckPath", newAnkiDroidDirectory)
        }
        deckPicker.dismissAllDialogFragments()
        deckPicker.importColpkgListener = null
        CollectionHelper.ankiDroidDirectoryOverride = null
    }
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

/**
 * @param impList: List of packages to import
 * @param errList: a string describing the errors. Null if no error.
 */
data class ImporterData(val impList: List<AnkiPackageImporter>?, val errList: String?)
