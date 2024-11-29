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

import android.app.Activity
import android.content.Intent
import androidx.core.app.TaskStackBuilder
import androidx.core.content.edit
import androidx.lifecycle.Lifecycle
import com.ichi2.anki.dialogs.AsyncDialogFragment
import com.ichi2.anki.dialogs.ImportDialog
import com.ichi2.anki.dialogs.ImportFileSelectionFragment
import com.ichi2.anki.dialogs.ImportFileSelectionFragment.ImportOptions
import com.ichi2.anki.pages.CsvImporter
import com.ichi2.anki.preferences.sharedPrefs
import com.ichi2.annotations.NeedsTest
import com.ichi2.utils.ImportUtils
import timber.log.Timber

// see also:
// ImportFileSelectionFragment - selects 'APKG/COLPKG/CSV' and opens a file picker
// onSelectedPackageToImport/onSelectedCsvForImport
// importUtils - copying selected file into local cache
// ImportDialog - confirmation screen after file copied to cache
//    * ImportDialogListener - AnkiActivity implementation of handler for the confirmation screen
//    * AnkiActivity.importAdd/importReplace - called from confirmation screen
// BackendBackups/BackendImporting - new backend for importing
// importReplaceListener - old backend listener for importing

fun interface ImportColpkgListener {
    fun onImportColpkg(colpkgPath: String?)
}

@NeedsTest("successful import from the app menu")
fun AnkiActivity.onSelectedPackageToImport(data: Intent) {
    val importResult = ImportUtils.handleFileImport(this, data)
    if (!importResult.isSuccess) {
        runOnUiThread {
            ImportUtils.showImportUnsuccessfulDialog(this, importResult.humanReadableMessage, false)
        }
    } else {
        // a Message was posted, don't wait for onResume to process it
        if (this.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            dialogHandler.popMessage()?.let { dialogHandler.sendStoredMessage(it) }
        }
    }
}

fun Activity.onSelectedCsvForImport(data: Intent) {
    val path = ImportUtils.getFileCachedCopy(this, data) ?: return
    val csvImporterIntent = CsvImporter.getIntent(this, path)

    val stackBuilder = TaskStackBuilder.create(this)
    stackBuilder.addNextIntentWithParentStack(Intent(this, DeckPicker::class.java))
    stackBuilder.addNextIntent(csvImporterIntent)

    stackBuilder.startActivities()
}

fun AnkiActivity.showImportDialog(id: Int, importPath: String) {
    Timber.d("showImportDialog() delegating to ImportDialog")
    val newFragment: AsyncDialogFragment = ImportDialog.newInstance(id, importPath)
    showAsyncDialogFragment(newFragment)
}
fun AnkiActivity.showImportDialog() {
    showImportDialog(
        ImportOptions(
            importApkg = true,
            importColpkg = true,
            importTextFile = true
        )
    )
}

fun AnkiActivity.showImportDialog(options: ImportOptions) {
    showDialogFragment(ImportFileSelectionFragment.newInstance(options))
}

class DatabaseRestorationListener(val activity: AnkiActivity, val newAnkiDroidDirectory: String) : ImportColpkgListener {
    override fun onImportColpkg(colpkgPath: String?) {
        Timber.i("Database restoration correct")
        activity.sharedPrefs().edit {
            putString("deckPath", newAnkiDroidDirectory)
        }
        activity.dismissAllDialogFragments()
        activity.importColpkgListener = null
        CollectionHelper.ankiDroidDirectoryOverride = null
    }
}
