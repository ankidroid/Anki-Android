/*
 *  Copyright (c) 2023 Brayan Oliveira <brayandso.dev@gmail.com>
 *  Copyright (c) 2024 David Allison <davidallisongithub@gmail.com>
 *  Copyright (c) 2024 voczi <dev@voczi.com>
 *
 *  This program is free software; you can redistribute it and/or modify it under
 *  the terms of the GNU General Public License as published by the Free Software
 *  Foundation; either version 3 of the License, or (at your option) any later
 *  version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY
 *  WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 *  PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along with
 *  this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.ichi2.anki.pages

import android.app.Activity
import androidx.annotation.VisibleForTesting
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import anki.collection.OpChanges
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.NoteEditor
import com.ichi2.anki.importAnkiPackageUndoable
import com.ichi2.anki.importCsvRaw
import com.ichi2.anki.launchCatchingTask
import com.ichi2.anki.searchInBrowser
import com.ichi2.libanki.Collection
import com.ichi2.libanki.completeTagRaw
import com.ichi2.libanki.getCsvMetadataRaw
import com.ichi2.libanki.getDeckConfigsForUpdateRaw
import com.ichi2.libanki.getDeckNamesRaw
import com.ichi2.libanki.getFieldNamesRaw
import com.ichi2.libanki.getImportAnkiPackagePresetsRaw
import com.ichi2.libanki.getNotetypeNamesRaw
import com.ichi2.libanki.sched.computeFsrsParamsRaw
import com.ichi2.libanki.sched.computeOptimalRetentionRaw
import com.ichi2.libanki.sched.evaluateParamsRaw
import com.ichi2.libanki.sched.simulateFsrsReviewRaw
import com.ichi2.libanki.stats.cardStatsRaw
import com.ichi2.libanki.stats.getGraphPreferencesRaw
import com.ichi2.libanki.stats.graphsRaw
import com.ichi2.libanki.stats.setGraphPreferencesRaw
import com.ichi2.libanki.undoableOp
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import timber.log.Timber

interface PostRequestHandler {
    suspend fun handlePostRequest(uri: String, bytes: ByteArray): ByteArray
}

fun <ByteArray> backendIdentity(bytes: ByteArray): ByteArray = bytes

typealias CollectionBackendInterface = Collection.(bytes: ByteArray) -> ByteArray

@VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
val collectionMethods = hashMapOf<String, CollectionBackendInterface>(
    "i18nResources" to { bytes -> i18nResourcesRaw(bytes) },
    "getGraphPreferences" to { _ -> getGraphPreferencesRaw() },
    "setGraphPreferences" to { bytes -> setGraphPreferencesRaw(bytes) },
    "graphs" to { bytes -> graphsRaw(bytes) },
    "getNotetypeNames" to { bytes -> getNotetypeNamesRaw(bytes) },
    "getDeckNames" to { bytes -> getDeckNamesRaw(bytes) },
    "getCsvMetadata" to { bytes -> getCsvMetadataRaw(bytes) },
    "importDone" to { bytes -> backendIdentity(bytes) },
    "getImportAnkiPackagePresets" to { bytes -> getImportAnkiPackagePresetsRaw(bytes) },
    "completeTag" to { bytes -> completeTagRaw(bytes) },
    "getFieldNames" to { bytes -> getFieldNamesRaw(bytes) },
    "cardStats" to { bytes -> cardStatsRaw(bytes) },
    "getDeckConfigsForUpdate" to { bytes -> getDeckConfigsForUpdateRaw(bytes) },
    "computeFsrsParams" to { bytes -> computeFsrsParamsRaw(bytes) },
    "computeOptimalRetention" to { bytes -> computeOptimalRetentionRaw(bytes) },
    "evaluateParams" to { bytes -> evaluateParamsRaw(bytes) },
    "simulateFsrsReview" to { bytes -> simulateFsrsReviewRaw(bytes) },
    "getImageForOcclusion" to { bytes -> getImageForOcclusionRaw(bytes) },
    "getImageOcclusionNote" to { bytes -> getImageOcclusionNoteRaw(bytes) },
    "setWantsAbort" to { bytes -> setWantsAbortRaw(bytes) },
    "latestProgress" to { bytes -> latestProgressRaw(bytes) },
    "getSchedulingStatesWithContext" to { bytes -> getSchedulingStatesWithContextRaw(bytes) },
    "setSchedulingStates" to { bytes -> setSchedulingStatesRaw(bytes) },
    "getChangeNotetypeInfo" to { bytes -> getChangeNotetypeInfoRaw(bytes) },
    "changeNotetype" to { bytes -> changeNotetypeRaw(bytes) },
    "importJsonString" to { bytes -> importJsonStringRaw(bytes) },
    "importJsonFile" to { bytes -> importJsonFileRaw(bytes) },
    "congratsInfo" to { bytes -> congratsInfoRaw(bytes) },
    "getImageOcclusionFields" to { bytes -> getImageOcclusionFieldsRaw(bytes) }
)

suspend fun handleCollectionPostRequest(methodName: String, bytes: ByteArray): ByteArray? {
    return collectionMethods[methodName]?.let { method -> withCol { method.invoke(this, bytes) } } ?: run {
        Timber.w("Unknown TS method called.")
        Timber.d("handleCollectionPostRequest could not resolve TS method %s", methodName)
        null
    }
}

typealias UIBackendInterface = FragmentActivity.(bytes: ByteArray) -> Deferred<ByteArray>

@VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
val uiMethods = hashMapOf<String, UIBackendInterface>(
    "searchInBrowser" to { bytes -> lifecycleScope.async { searchInBrowser(bytes) } },
    "updateDeckConfigs" to { bytes -> lifecycleScope.async { updateDeckConfigsRaw(bytes) } },
    "importCsv" to { bytes -> lifecycleScope.async { importCsvRaw(bytes) } },
    "importAnkiPackage" to { bytes -> lifecycleScope.async { importAnkiPackageUndoable(bytes) } },
    "addImageOcclusionNote" to { bytes ->
        lifecycleScope.async {
            withCol { addImageOcclusionNoteRaw(bytes) }
        }
    },
    "updateImageOcclusionNote" to { bytes ->
        lifecycleScope.async {
            withCol { updateImageOcclusionNoteRaw(bytes) }
        }
    }
)

suspend fun FragmentActivity?.handleUiPostRequest(
    methodName: String,
    bytes: ByteArray
): ByteArray? {
    if (this == null) {
        Timber.w("ignored UI request '%s' due to screen/app being backgrounded", methodName)
        return null
    }

    val data = uiMethods[methodName]?.invoke(this, bytes)?.await() ?: run {
        Timber.w("Unknown TS method called.")
        Timber.d("handleUiPostRequest could not resolve TS method %s", methodName)
        return null
    }
    when (methodName) {
        "addImageOcclusionNote" -> {
            undoableOp { OpChanges.parseFrom(data) }
            launchCatchingTask {
                // Allow time for toast message to appear before closing editor
                delay(1000)
                setResult(Activity.RESULT_OK)
                finish()
            }
        }

        "updateImageOcclusionNote" -> {
            undoableOp { OpChanges.parseFrom(data) }
            launchCatchingTask {
                // Allow time for toast message to appear before closing editor
                delay(1000)
                setResult(NoteEditor.RESULT_UPDATED_IO_NOTE)
                finish()
            }
        }
    }
    return data
}
