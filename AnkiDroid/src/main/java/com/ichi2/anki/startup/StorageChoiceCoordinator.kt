// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.startup

import android.content.Context
import com.ichi2.anki.common.preferences.sharedPrefs
import com.ichi2.anki.common.storage.CollectionHelper
import com.ichi2.anki.selectStoragePermissions
import java.io.File

/**
 * Avoids a race condition when creating an AnkiDroid directory.
 *
 * This ensures that there is no race condition between checking the collection path is unset
 * and writing the app-folder path.
 *
 * Due to directory creation potentially taking a long time, we can have the situation where
 * * app-folder creation is started
 * * A public storage folder is selected
 * * app-folder creation completes
 *
 * This lock prevents the stale choice from updating the collection path.
 *
 * Directory creation and collection queue waits must happen outside the lock.
 */
internal object StorageChoiceCoordinator {
    class Choice internal constructor()

    private val lock = Any()
    private var pendingChoice: Choice? = null

    fun beginChoice(): Choice =
        synchronized(lock) {
            Choice().also { pendingChoice = it }
        }

    /** Runs the final consent check and path write only if no newer choice has superseded it. */
    fun commitChoice(
        choice: Choice,
        block: () -> Boolean,
    ): Boolean =
        synchronized(lock) {
            if (pendingChoice !== choice) {
                false
            } else {
                pendingChoice = null
                block()
            }
        }

    /**
     * Accepts the current path (or its default) and prevents pending app-folder setup
     * requests from saving their collection path.
     * [block] returns true only when the current storage permissions allow startup to proceed.
     */
    fun acceptCurrentStorage(block: () -> Boolean) {
        synchronized(lock) {
            if (block()) pendingChoice = null
        }
    }
}

/**
 * Selects a default path when unset and prevents pending app-folder requests from saving
 * their path once the required storage permissions are available.
 */
internal fun StorageChoiceCoordinator.acceptCurrentStorage(context: Context) {
    acceptCurrentStorage {
        maybeSetDefaultCollectionPath(context)
        selectStoragePermissions(context).hasRequiredPermissions(context)
    }
}

/**
 * Saves [directory] if this request is still current and no collection path has been set.
 * Returns whether the path was saved.
 *
 * Call on the collection queue with the collection closed and [directory] already initialized.
 */
internal fun StorageChoiceCoordinator.Choice.commitAppFolderPath(
    context: Context,
    directory: File,
): Boolean =
    StorageChoiceCoordinator.commitChoice(this) {
        // Recheck the path after filesystem work. Internet is enforced again by the screen.
        if (context.sharedPrefs().getString(CollectionHelper.PREF_COLLECTION_PATH, null) != null) {
            false
        } else {
            resetAnkiDroidDirectory(context, directory)
            true
        }
    }
