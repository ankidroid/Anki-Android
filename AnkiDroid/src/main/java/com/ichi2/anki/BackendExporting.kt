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
import com.ichi2.libanki.exportAnkiPackage
import com.ichi2.libanki.exportCollectionPackage

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

suspend fun AnkiActivity.exportColpkg(colpkgPath: String, withMedia: Boolean) {
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
