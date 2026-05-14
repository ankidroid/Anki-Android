// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.ui.windows.permissions

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ichi2.anki.CollectionManager
import com.ichi2.anki.StoragePermissionSet
import com.ichi2.anki.common.preferences.sharedPrefs
import com.ichi2.anki.common.storage.AnkiDroidFolder
import com.ichi2.anki.common.storage.CollectionHelper
import com.ichi2.anki.common.storage.StorageDecision
import com.ichi2.anki.startup.StorageChoiceCoordinator
import com.ichi2.anki.startup.StoragePolicy
import com.ichi2.anki.startup.commitAppFolderPath
import com.ichi2.anki.startup.getDefaultAnkiDroidDirectory
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async

/** Retains a storage change and its result until an activity can handle it, including across rotation. */
class PermissionsViewModel(
    private val app: Application,
) : ViewModel() {
    var storageChange: Deferred<Boolean>? = null
        private set

    val collectionPath: String?
        get() = app.sharedPrefs().getString(CollectionHelper.PREF_COLLECTION_PATH, null)

    /** Only undecided storage can be chosen here; Internet remains required. */
    fun canChooseAppPrivateStorage(permissionSet: StoragePermissionSet): Boolean =
        permissionSet.storagePolicy == StoragePolicy.UserChoosesOnPermissionScreen &&
            StoragePermissionSet.APP_PRIVATE.hasRequiredPermissions(app) &&
            CollectionHelper.storageDecision(app.sharedPrefs()) == StorageDecision.Undecided

    fun useAppPrivateStorage(permissionSet: StoragePermissionSet): Deferred<Boolean> {
        storageChange?.let { return it }
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
            }.also { storageChange = it }
    }

    fun storageChangeHandled(change: Deferred<Boolean>) {
        if (storageChange === change) storageChange = null
    }

    companion object {
        fun factory(app: Application): ViewModelProvider.Factory =
            viewModelFactory {
                initializer { PermissionsViewModel(app) }
            }
    }
}
