// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.ui.windows.permissions

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ichi2.anki.CollectionManager
import com.ichi2.anki.InitialActivity
import com.ichi2.anki.StoragePermissionSet
import com.ichi2.anki.common.preferences.sharedPrefs
import com.ichi2.anki.common.storage.AnkiDroidFolder
import com.ichi2.anki.common.storage.CollectionHelper
import com.ichi2.anki.common.storage.StorageDecision
import com.ichi2.anki.startup.StorageChoiceCoordinator
import com.ichi2.anki.startup.StoragePolicy
import com.ichi2.anki.startup.commitAppFolderPath
import com.ichi2.anki.startup.getDefaultAnkiDroidDirectory
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async

/** Completes either storage choice and retains its result until the activity handles it, including across rotation. */
class PermissionsViewModel(
    private val app: Application,
) : ViewModel() {
    var storageCompletion: StorageCompletion? = null
        private set

    enum class StorageChoice {
        CURRENT_OR_DEFAULT,
        APP_FOLDER,
    }

    class StorageCompletion internal constructor(
        val choice: StorageChoice,
        /** True if the choice was accepted; false if setup must be refreshed. */
        val result: Deferred<Boolean>,
    )

    val choosingAppFolder: Boolean
        get() = storageCompletion?.choice == StorageChoice.APP_FOLDER

    private val collectionPath: String?
        get() = app.sharedPrefs().getString(CollectionHelper.PREF_COLLECTION_PATH, null)

    /** Only undecided storage can be chosen here; Internet remains required. */
    fun canChooseAppPrivateStorage(permissionSet: StoragePermissionSet): Boolean =
        permissionSet.storagePolicy == StoragePolicy.UserChoosesOnPermissionScreen &&
            StoragePermissionSet.APP_PRIVATE.hasRequiredPermissions(app) &&
            CollectionHelper.storageDecision(app.sharedPrefs()) == StorageDecision.Undecided

    /**
     * Continues with the current/default path when permissions allow it, or chooses an app folder.
     * Accepting current storage must supersede pending app-folder setup without waiting for its worker.
     * Returns null when no new completion can start.
     */
    fun completeStorageChoice(permissionSet: StoragePermissionSet): StorageCompletion? {
        val completion =
            if (permissionSet.hasRequiredPermissions(app)) {
                InitialActivity.decideStorageIfUndecided(app)
                StorageCompletion(StorageChoice.CURRENT_OR_DEFAULT, CompletableDeferred(true))
            } else {
                if (storageCompletion != null || !canChooseAppPrivateStorage(permissionSet)) return null
                StorageCompletion(StorageChoice.APP_FOLDER, useAppPrivateStorage(permissionSet))
            }
        storageCompletion = completion
        return completion
    }

    private fun useAppPrivateStorage(permissionSet: StoragePermissionSet): Deferred<Boolean> {
        // Record the choice before queueing: a newer task can supersede it while we wait or create files.
        val choice = StorageChoiceCoordinator.beginChoice()
        return viewModelScope
            .async {
                CollectionManager.withClosedCollection {
                    // The queue may have been busy: recheck consent and permissions before writing.
                    if (collectionPath != null || !canChooseAppPrivateStorage(permissionSet)) {
                        return@withClosedCollection false
                    }
                    val directory = getDefaultAnkiDroidDirectory(app, folder = AnkiDroidFolder.APP_PRIVATE)
                    CollectionHelper.initializeAnkiDroidDirectory(directory)
                    choice.commitAppFolderPath(app, directory)
                }
            }
    }

    fun storageCompletionHandled(completion: StorageCompletion) {
        if (storageCompletion === completion) storageCompletion = null
    }

    companion object {
        fun factory(app: Application): ViewModelProvider.Factory =
            viewModelFactory {
                initializer { PermissionsViewModel(app) }
            }
    }
}
