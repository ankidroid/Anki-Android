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

import com.ichi2.libanki.CollectionV16
import com.ichi2.libanki.awaitBackupCompletion
import com.ichi2.libanki.createBackup
import kotlinx.coroutines.*

fun DeckPicker.performBackupInBackground() {
    launchCatchingCollectionTask { col ->
        // Wait a second to allow the deck list to finish loading first, or it
        // will hang until the first stage of the backup completes.
        delay(1000)
        createBackup(col, false)
    }
}

fun DeckPicker.importColpkg(colpkgPath: String) {
    launchCatchingTask {
        val helper = CollectionHelper.getInstance()
        val backend = helper.getOrCreateBackend(baseContext)
        runInBackgroundWithProgress(
            backend,
            extractProgress = {
                if (progress.hasImporting()) {
                    text = progress.importing
                }
            },
        ) {
            helper.importColpkg(baseContext, colpkgPath)
        }
        invalidateOptionsMenu()
        updateDeckList()
    }
}

private suspend fun createBackup(col: CollectionV16, force: Boolean) {
    runInBackground {
        // this two-step approach releases the backend lock after the initial copy
        col.createBackup(
            BackupManager.getBackupDirectoryFromCollection(col.path),
            force,
            waitForCompletion = false
        )
        col.awaitBackupCompletion()
    }
}
