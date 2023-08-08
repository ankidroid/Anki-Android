/***************************************************************************************
 * Copyright (c) 2022 Ankitects Pty Ltd <http://apps.ankiweb.net>                       *
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

package com.ichi2.anki

import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.FragmentActivity
import anki.import_export.ExportLimit
import anki.import_export.ImportResponse
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.libanki.exportAnkiPackage
import com.ichi2.libanki.exportCollectionPackage
import com.ichi2.libanki.importAnkiPackage
import com.ichi2.libanki.importCsvRaw
import com.ichi2.libanki.undoableOp
import com.ichi2.utils.message
import com.ichi2.utils.positiveButton
import com.ichi2.utils.show
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.ankiweb.rsdroid.Translations

fun AnkiActivity.importApkgs(apkgPaths: List<String>) {
    launchCatchingTask {
        for (apkgPath in apkgPaths) {
            val report = withProgress(
                extractProgress = {
                    if (progress.hasImporting()) {
                        text = progress.importing
                    }
                }
            ) {
                undoableOp {
                    importAnkiPackage(apkgPath)
                }
            }
            showSimpleMessageDialog(summarizeReport(col.tr, report))
        }
    }
}

@Suppress("BlockingMethodInNonBlockingContext") // ImportResponse.parseFrom
suspend fun FragmentActivity.importCsvRaw(input: ByteArray): ByteArray {
    return withContext(Dispatchers.Main) {
        val output = withProgress(
            extractProgress = {
                if (progress.hasImporting()) {
                    text = progress.importing
                }
            },
            op = { withCol { col.importCsvRaw(input) } }
        )
        val importResponse = ImportResponse.parseFrom(output)
        undoableOp { importResponse }
        AlertDialog.Builder(this@importCsvRaw).show {
            message(text = summarizeReport(TR, importResponse))
            positiveButton(R.string.dialog_ok) {
                this@importCsvRaw.finish()
            }
        }
        output
    }
}

private fun summarizeReport(tr: Translations, output: ImportResponse): String {
    val log = output.log
    val total = log.conflictingCount + log.updatedCount + log.newCount + log.duplicateCount
    val msgs = mutableListOf(tr.importingNotesFoundInFile(total))
    if (log.conflictingCount > 0) {
        msgs.add(tr.importingNotesThatCouldNotBeImported(log.conflictingCount))
    }
    if (log.updatedCount > 0) {
        msgs.add(tr.importingNotesUpdatedAsFileHadNewer(log.updatedCount))
    }
    if (log.newCount > 0) {
        msgs.add(tr.importingNotesAddedFromFile(log.newCount))
    }
    if (log.duplicateCount > 0) {
        msgs.add(tr.importingNotesSkippedAsTheyreAlreadyIn(log.duplicateCount))
    }
    return msgs.joinToString("\n")
}

suspend fun AnkiActivity.exportApkg(
    apkgPath: String,
    withScheduling: Boolean,
    withMedia: Boolean,
    limit: ExportLimit
) {
    withProgress(
        extractProgress = {
            if (progress.hasExporting()) {
                text = getString(R.string.export_preparation_in_progress)
            }
        }
    ) {
        withCol {
            exportAnkiPackage(apkgPath, withScheduling, withMedia, limit)
        }
    }
}

suspend fun AnkiActivity.exportColpkg(
    colpkgPath: String,
    withMedia: Boolean
) {
    withProgress(
        extractProgress = {
            if (progress.hasExporting()) {
                text = getString(R.string.export_preparation_in_progress)
            }
        }
    ) {
        withCol {
            exportCollectionPackage(colpkgPath, withMedia, true)
        }
    }
}
