// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import android.app.Activity
import android.content.Intent
import androidx.core.app.TaskStackBuilder
import androidx.core.content.edit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import com.ichi2.anki.common.destinations.CsvImporterDestination
import com.ichi2.anki.common.destinations.addNextIntent
import com.ichi2.anki.common.preferences.sharedPrefs
import com.ichi2.anki.common.storage.CollectionHelper
import com.ichi2.anki.dialogs.AsyncDialogFragment
import com.ichi2.anki.dialogs.DialogHandler
import com.ichi2.anki.dialogs.ImportDialog
import com.ichi2.anki.dialogs.ImportFileSelectionFragment
import com.ichi2.anki.dialogs.ImportFileSelectionFragment.ImportOptions
import com.ichi2.anki.utils.ext.dismissAllDialogFragments
import com.ichi2.anki.utils.ext.showDialogFragment
import com.ichi2.utils.ImportResolution
import com.ichi2.utils.ImportResult
import com.ichi2.utils.ImportUtils
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File

// see also:
// ImportFileSelectionFragment - selects 'APKG/COLPKG/CSV' and opens a file picker
// onSelectedFileToImport - prepares a resolved source, then opens its import UI
// importUtils - copying selected file into local cache
// ImportDialog - confirmation screen after file copied to cache
//    * ImportDialogListener - AnkiActivity implementation of handler for the confirmation screen
//    * AnkiActivity.importAdd/importReplace - called from confirmation screen
// BackendBackups/BackendImporting - new backend for importing
// importReplaceListener - old backend listener for importing

fun interface ImportColpkgListener {
    fun onImportColpkg(colpkgPath: String?)
}

/** Shared by the file pickers and drag-and-drop. File I/O completes before displaying any UI. */
fun AnkiActivity.onSelectedFileToImport(data: Intent): Job =
    lifecycleScope.launch {
        prepareAndOpenImport(exitActivityOnFailure = false) {
            withProgress(message = getString(CommonString.import_preparing_file)) {
                withContext(ioDispatcher) { ImportUtils.prepareImport(this@onSelectedFileToImport, data) }
            }
        }
    }

/** Hands the session to the import UI on success and releases it on every other exit. */
internal suspend fun Activity.prepareAndOpenImport(
    exitActivityOnFailure: Boolean,
    existingSession: ImportSession? = null,
    onPrepared: () -> Unit = {},
    prepare: suspend () -> ImportResult,
): ImportResult.Success? {
    val session = existingSession ?: ImportSession.acquire(this)
    if (session == null) {
        if (exitActivityOnFailure) finish()
        return null
    }
    var opened = false
    try {
        val result = openPreparedImport(prepare(), exitActivityOnFailure, onPrepared, session.id)
        opened = result != null
        return result
    } finally {
        if (!opened) session.finish()
    }
}

private fun Activity.openPreparedImport(
    result: ImportResult,
    exitActivityOnFailure: Boolean,
    onPrepared: () -> Unit,
    sessionId: ImportSessionId,
): ImportResult.Success? {
    // finish() may precede onDestroy(), which cancels preparation.
    if (isFinishing) return null
    onPrepared()
    return when (result) {
        is ImportResult.Failure -> {
            Timber.w("File import failed")
            ImportUtils.showImportUnsuccessfulDialog(this, result, exitActivity = exitActivityOnFailure)
            null
        }
        is ImportResult.Success -> {
            openImport(result, sessionId)
            result
        }
    }
}

fun Activity.openCsvImporter(
    path: String,
    sessionId: ImportSessionId? = null,
) {
    val stackBuilder = TaskStackBuilder.create(this)
    stackBuilder.addNextIntentWithParentStack(Intent(this, DeckPicker::class.java))
    stackBuilder.addNextIntent(CsvImporterDestination(path))
    stackBuilder.editIntentAt(stackBuilder.intentCount - 1)!!.putExtra(ImportSession.EXTRA_SESSION_ID, sessionId?.value)

    stackBuilder.startActivities()
}

/** Open a prepared import on the UI thread. */
fun Activity.openImport(
    result: ImportResult.Success,
    sessionId: ImportSessionId? = null,
) {
    runOnUiThread {
        val message =
            when (result.file) {
                is ImportResolution.DeckPackage -> ImportUtils.CollectionImportAdd(result.path, sessionId)
                is ImportResolution.CollectionPackage -> ImportUtils.CollectionImportReplace(result.path, sessionId)
                is ImportResolution.Text -> {
                    openCsvImporter(result.path, sessionId)
                    return@runOnUiThread
                }
            }
        DialogHandler.storeMessage(message.toMessage())
        // IntentHandler leaves the message for DeckPicker; a resumed picker can display it now.
        if (this is AnkiActivity && lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            dialogHandler.popMessage()?.let { dialogHandler.sendStoredMessage(it) }
        }
    }
}

fun AnkiActivity.showImportDialog(
    id: ImportDialog.Type,
    importPath: String,
    sessionId: ImportSessionId? = null,
) {
    Timber.d("showImportDialog() delegating to ImportDialog")
    val newFragment: AsyncDialogFragment = ImportDialog.newInstance(id, importPath, sessionId)
    showAsyncDialogFragment(newFragment)
}

fun AnkiActivity.showImportDialog() {
    showImportDialog(
        ImportOptions(
            importApkg = true,
            importColpkg = true,
            importTextFile = true,
        ),
    )
}

fun AnkiActivity.showImportDialog(options: ImportOptions) {
    showDialogFragment(ImportFileSelectionFragment.newInstance(options))
}

class DatabaseRestorationListener(
    val activity: AnkiActivity,
    val newAnkiDroidDirectory: File,
) : ImportColpkgListener {
    override fun onImportColpkg(colpkgPath: String?) {
        Timber.i("Database restoration correct")
        activity.sharedPrefs().edit {
            putString("deckPath", newAnkiDroidDirectory.absolutePath)
        }
        activity.dismissAllDialogFragments()
        activity.importColpkgListener = null
        CollectionHelper.ankiDroidDirectoryOverride = null
    }
}
