// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2025 Ashish Yadav <mailtoashish693@gmail.com>

package com.ichi2.anki.mediacheck

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import anki.media.CheckMediaResponse
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.common.annotations.NeedsTest
import com.ichi2.anki.observability.undoableOp
import com.ichi2.anki.progress.HasProgress
import com.ichi2.anki.progress.ProgressManager
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** The operation [MediaCheckViewModel] is running, shown as a progress message by the UI. */
enum class MediaCheckProgress {
    CHECKING_MEDIA,
    ADDING_TAGS,
    DELETING_MEDIA,
}

// TODO: handle errors in the ViewModel instead of returning a Deferred for the UI to await
@NeedsTest("Test the media check process i.e. the buttons and views")
class MediaCheckViewModel :
    ViewModel(),
    HasProgress<MediaCheckProgress> {
    override val progressManager = ProgressManager<MediaCheckProgress>()

    val mediaCheckResult: StateFlow<CheckMediaResponse?>
        field = MutableStateFlow<CheckMediaResponse?>(null)

    private val deletedFilesCount: MutableStateFlow<Int> = MutableStateFlow(0)
    private val taggedFilesCount: MutableStateFlow<Int> = MutableStateFlow(0)

    val deletedFiles: Int
        get() = deletedFilesCount.value

    val taggedFiles: Int
        get() = taggedFilesCount.value

    fun tagMissing(tag: String): Deferred<Unit> =
        viewModelScope.async {
            progressManager.withProgress(message = MediaCheckProgress.ADDING_TAGS) {
                val taggedNotes =
                    undoableOp {
                        tags.bulkAdd(mediaCheckResult.value?.missingMediaNotesList ?: listOf(), tag)
                    }
                taggedFilesCount.value = taggedNotes.count
            }
        }

    fun checkMedia(): Deferred<Unit> =
        viewModelScope.async {
            progressManager.withProgress(message = MediaCheckProgress.CHECKING_MEDIA) {
                mediaCheckResult.value = withCol { media.check() }
            }
        }

    fun deleteTrash(): Deferred<Unit> =
        viewModelScope.async {
            progressManager.withProgress { withCol { media.emptyTrash() } }
        }

    fun restoreTrash(): Deferred<Unit> =
        viewModelScope.async {
            progressManager.withProgress { withCol { media.restoreTrash() } }
        }

    // TODO: investigate: the underlying implementation exposes progress, which we do not yet handle.
    fun deleteUnusedMedia(): Deferred<Unit> =
        viewModelScope.async {
            progressManager.withProgress(message = MediaCheckProgress.DELETING_MEDIA) {
                val unused = mediaCheckResult.value?.unusedList ?: listOf()
                withCol { media.trashFiles(unused) }
                deletedFilesCount.value = unused.size
            }
        }
}
