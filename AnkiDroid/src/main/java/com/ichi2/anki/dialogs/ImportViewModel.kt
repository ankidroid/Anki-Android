// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Georgios Michelakis <michelakisgio@gmail.com>

package com.ichi2.anki.dialogs

import androidx.lifecycle.ViewModel
import com.ichi2.anki.ImportSession
import com.ichi2.anki.ImportSessionId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.getAndUpdate

class ImportViewModel : ViewModel() {
    val importAddFlow: StateFlow<ImportRequest?>
        field = MutableStateFlow<ImportRequest?>(null)

    val importReplaceFlow: StateFlow<ImportRequest?>
        field = MutableStateFlow<ImportRequest?>(null)

    fun triggerImportAdd(request: ImportRequest) {
        importAddFlow.value = request
    }

    fun triggerImportReplace(request: ImportRequest) {
        importReplaceFlow.value = request
    }

    fun takeImportAdd(): ImportRequest? = importAddFlow.getAndUpdate { null }

    fun takeImportReplace(): ImportRequest? = importReplaceFlow.getAndUpdate { null }

    override fun onCleared() {
        ImportSession.finish(importAddFlow.value?.sessionId)
        ImportSession.finish(importReplaceFlow.value?.sessionId)
    }
}

data class ImportRequest(
    val path: String,
    val sessionId: ImportSessionId,
)
