/*
 *  Copyright (c) 2023 Brayan Oliveira <brayandso.dev@gmail.com>
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
import androidx.fragment.app.FragmentActivity
import anki.collection.OpChanges
import com.ichi2.anki.CollectionManager.getBackend
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.NoteEditor
import com.ichi2.anki.importAnkiPackageUndoable
import com.ichi2.anki.importCsvRaw
import com.ichi2.anki.launchCatchingTask
import com.ichi2.anki.searchInBrowser
import com.ichi2.libanki.completeTagRaw
import com.ichi2.libanki.getCsvMetadataRaw
import com.ichi2.libanki.getDeckConfigRaw
import com.ichi2.libanki.getDeckConfigsForUpdateRaw
import com.ichi2.libanki.getDeckNamesRaw
import com.ichi2.libanki.getFieldNamesRaw
import com.ichi2.libanki.getImportAnkiPackagePresetsRaw
import com.ichi2.libanki.getNotetypeNamesRaw
import com.ichi2.libanki.sched.computeFsrsWeightsRaw
import com.ichi2.libanki.sched.computeOptimalRetentionRaw
import com.ichi2.libanki.sched.evaluateWeightsRaw
import com.ichi2.libanki.stats.cardStatsRaw
import com.ichi2.libanki.stats.getGraphPreferencesRaw
import com.ichi2.libanki.stats.graphsRaw
import com.ichi2.libanki.stats.setGraphPreferencesRaw
import com.ichi2.libanki.undoableOp
import kotlinx.coroutines.delay

interface PostRequestHandler {
    suspend fun handlePostRequest(uri: String, bytes: ByteArray): ByteArray
}

suspend fun handleCollectionPostRequest(methodName: String, bytes: ByteArray): ByteArray? {
    return when (methodName) {
        "i18nResources" -> withCol { i18nResourcesRaw(bytes) }
        "getGraphPreferences" -> withCol { getGraphPreferencesRaw() }
        "setGraphPreferences" -> withCol { setGraphPreferencesRaw(bytes) }
        "graphs" -> withCol { graphsRaw(bytes) }
        "getNotetypeNames" -> withCol { getNotetypeNamesRaw(bytes) }
        "getDeckNames" -> withCol { getDeckNamesRaw(bytes) }
        "getCsvMetadata" -> withCol { getCsvMetadataRaw(bytes) }
        "importCsv" -> importCsvRaw(bytes)
        "importAnkiPackage" -> importAnkiPackageUndoable(bytes)
        "importDone" -> bytes
        "getImportAnkiPackagePresets" -> withCol { getImportAnkiPackagePresetsRaw(bytes) }
        "completeTag" -> withCol { completeTagRaw(bytes) }
        "getFieldNames" -> withCol { getFieldNamesRaw(bytes) }
        "cardStats" -> withCol { cardStatsRaw(bytes) }
        "getDeckConfig" -> withCol { getDeckConfigRaw(bytes) }
        "getDeckConfigsForUpdate" -> withCol { getDeckConfigsForUpdateRaw(bytes) }
        "computeFsrsWeights" -> withCol { computeFsrsWeightsRaw(bytes) }
        "computeOptimalRetention" -> withCol { computeOptimalRetentionRaw(bytes) }
        "evaluateWeights" -> withCol { evaluateWeightsRaw(bytes) }
        "getImageForOcclusion" -> withCol { getImageForOcclusionRaw(bytes) }
        "getImageOcclusionNote" -> withCol { getImageOcclusionNoteRaw(bytes) }
        "getImageForOcclusionFields" -> withCol { getImageOcclusionFieldsRaw(bytes) }
        "setWantsAbort" -> getBackend().setWantsAbortRaw(bytes)
        "latestProgress" -> getBackend().latestProgressRaw(bytes)
        "congratsInfo" -> withCol { congratsInfoRaw(bytes) }
        else -> null
    }
}

suspend fun FragmentActivity.handleUiPostRequest(methodName: String, bytes: ByteArray): ByteArray? {
    return when (methodName) {
        "searchInBrowser" -> searchInBrowser(bytes)
        "updateDeckConfigs" -> updateDeckConfigsRaw(bytes)
        "addImageOcclusionNote" -> {
            val data = withCol { addImageOcclusionNoteRaw(bytes) }
            undoableOp { OpChanges.parseFrom(data) }
            launchCatchingTask {
                // Allow time for toast message to appear before closing editor
                delay(1000)
                setResult(Activity.RESULT_OK)
                finish()
            }
            data
        }
        "updateImageOcclusionNote" -> {
            val data = withCol {
                updateImageOcclusionNoteRaw(bytes)
            }
            undoableOp { OpChanges.parseFrom(data) }
            launchCatchingTask {
                // Allow time for toast message to appear before closing editor
                delay(1000)
                setResult(NoteEditor.RESULT_UPDATED_IO_NOTE)
                finish()
            }
            data
        }
        else -> null
    }
}
