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

// BackendFactory.defaultLegacySchema must be false to use this code.

package com.ichi2.anki

import anki.import_export.ImportResponse
import com.ichi2.libanki.exportAnkiPackage
import com.ichi2.libanki.importAnkiPackage
import com.ichi2.libanki.undoableOp
import net.ankiweb.rsdroid.Translations

fun DeckPicker.importApkgs(apkgPaths: List<String>) {
    launchCatchingCollectionTask { col ->
        for (apkgPath in apkgPaths) {
            val report = runInBackgroundWithProgress(
                col.backend,
                extractProgress = {
                    if (progress.hasImporting()) {
                        text = progress.importing
                    }
                },
            ) {
                undoableOp {
                    col.importAnkiPackage(apkgPath)
                }
            }
            showSimpleMessageDialog(summarizeReport(col.tr, report))
        }
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

fun DeckPicker.exportApkg(
    apkgPath: String,
    withScheduling: Boolean,
    withMedia: Boolean,
    deckId: Long?
) {
    launchCatchingCollectionTask { col ->
        runInBackgroundWithProgress(
            col.backend,
            extractProgress = {
                if (progress.hasExporting()) {
                    text = progress.exporting
                }
            },
        ) {
            col.exportAnkiPackage(apkgPath, withScheduling, withMedia, deckId)
        }
    }
}
