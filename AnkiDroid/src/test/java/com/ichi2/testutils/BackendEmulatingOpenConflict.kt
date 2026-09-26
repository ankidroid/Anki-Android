// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.testutils

import anki.backend.BackendError
import net.ankiweb.rsdroid.Backend
import net.ankiweb.rsdroid.BackendException.BackendDbException.BackendDbLockedException
import net.ankiweb.rsdroid.BackendFactory
import org.mockito.Mockito

/** Test helper:
 * causes getCol to emulate an exception caused by having another AnkiDroid instance open on the same collection
 */
class BackendEmulatingOpenConflict : Backend() {
    override fun openCollection(
        collectionPath: String,
        mediaFolderPath: String,
        mediaDbPath: String,
    ) {
        val error = Mockito.mock(BackendError::class.java)
        throw BackendDbLockedException(error)
    }

    companion object {
        fun enable() {
            BackendFactory.setOverride { BackendEmulatingOpenConflict() }
        }

        fun disable() {
            BackendFactory.setOverride(null)
        }
    }
}
