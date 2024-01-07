/****************************************************************************************
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

import anki.import_export.ExportLimit
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.export.ExportDialogsFactoryProvider
import com.ichi2.libanki.exportAnkiPackage
import com.ichi2.libanki.exportCardsCsv
import com.ichi2.libanki.exportCollectionPackage
import com.ichi2.libanki.exportNotesCsv

fun AnkiActivity.exportApkgPackage(
    exportPath: String,
    withScheduling: Boolean,
    withDeckConfigs: Boolean,
    withMedia: Boolean,
    limit: ExportLimit
) {
    launchCatchingTask {
        val onProgress: ProgressContext.() -> Unit = {
            if (progress.hasExporting()) {
                text = getString(R.string.export_preparation_in_progress)
            }
        }
        withProgress(extractProgress = onProgress) {
            withCol { exportAnkiPackage(exportPath, withScheduling, withDeckConfigs, withMedia, limit) }
        }
        val factory =
            (this@exportApkgPackage as ExportDialogsFactoryProvider).exportDialogsFactory()
        val dialog = factory.newExportReadyDialog().withArguments(exportPath)
        showAsyncDialogFragment(dialog)
    }
}

suspend fun AnkiActivity.exportColpkg(colpkgPath: String, withMedia: Boolean) {
    val onProgress: ProgressContext.() -> Unit = {
        if (progress.hasExporting()) {
            text = getString(R.string.export_preparation_in_progress)
        }
    }
    withProgress(extractProgress = onProgress) {
        withCol { exportCollectionPackage(colpkgPath, withMedia, true) }
    }
}

fun AnkiActivity.exportCollectionPackage(exportPath: String, withMedia: Boolean) {
    launchCatchingTask {
        exportColpkg(exportPath, withMedia)
        val factory =
            (this@exportCollectionPackage as ExportDialogsFactoryProvider).exportDialogsFactory()
        val dialog = factory.newExportReadyDialog().withArguments(exportPath)
        showAsyncDialogFragment(dialog)
    }
}

fun AnkiActivity.exportSelectedNotes(
    exportPath: String,
    withHtml: Boolean,
    withTags: Boolean,
    withDeck: Boolean,
    withNotetype: Boolean,
    withGuid: Boolean,
    limit: ExportLimit
) {
    launchCatchingTask {
        val onProgress: ProgressContext.() -> Unit = {
            if (progress.hasExporting()) {
                text = getString(R.string.export_preparation_in_progress)
            }
        }
        withProgress(extractProgress = onProgress) {
            withCol {
                exportNotesCsv(
                    exportPath,
                    withHtml,
                    withTags,
                    withDeck,
                    withNotetype,
                    withGuid,
                    limit
                )
            }
        }
        val factory =
            (this@exportSelectedNotes as ExportDialogsFactoryProvider).exportDialogsFactory()
        val dialog = factory.newExportReadyDialog().withArguments(exportPath)
        showAsyncDialogFragment(dialog)
    }
}

fun AnkiActivity.exportSelectedCards(
    exportPath: String,
    withHtml: Boolean,
    limit: ExportLimit
) {
    launchCatchingTask {
        val onProgress: ProgressContext.() -> Unit = {
            if (progress.hasExporting()) {
                text = getString(R.string.export_preparation_in_progress)
            }
        }
        withProgress(extractProgress = onProgress) {
            withCol {
                exportCardsCsv(exportPath, withHtml, limit)
            }
        }
        val factory =
            (this@exportSelectedCards as ExportDialogsFactoryProvider).exportDialogsFactory()
        val dialog = factory.newExportReadyDialog().withArguments(exportPath)
        showAsyncDialogFragment(dialog)
    }
}
