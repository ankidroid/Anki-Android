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

import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.libanki.awaitBackupCompletion
import com.ichi2.libanki.createBackup
import kotlinx.coroutines.*

fun DeckPicker.performBackupInBackground() {
    launchCatchingTask {
        // Wait a second to allow the deck list to finish loading first, or it
        // will hang until the first stage of the backup completes.
        delay(1000)
        createBackup(force = false)
    }
}

fun <Activity> Activity.importColpkg(colpkgPath: String) where Activity : AnkiActivity, Activity : ImportColpkgListener {
    launchCatchingTask {
        withProgress(
            extractProgress = {
                if (progress.hasImporting()) {
                    text = progress.importing
                }
            }
        ) {
            CollectionManager.importColpkg(colpkgPath)
        }

        onImportColpkg(colpkgPath)
    }
}

private suspend fun createBackup(force: Boolean) {
    withCol {
        // this two-step approach releases the backend lock after the initial copy
        createBackup(
            BackupManager.getBackupDirectoryFromCollection(this.path),
            force,
            waitForCompletion = false
        )
        awaitBackupCompletion()
    }
}
