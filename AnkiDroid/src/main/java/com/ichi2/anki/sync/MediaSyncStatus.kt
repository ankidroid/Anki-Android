// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.sync

import androidx.work.NetworkType
import androidx.work.WorkInfo
import com.ichi2.anki.common.time.TimeManager
import com.ichi2.anki.isLoggedIn
import com.ichi2.anki.libanki.EpochMilliseconds
import com.ichi2.anki.settings.Prefs
import com.ichi2.anki.settings.enums.ShouldFetchMedia
import com.ichi2.utils.NetworkUtils

sealed interface MediaSyncStatus {
    data object NotLoggedIn : MediaSyncStatus

    data object Off : MediaSyncStatus

    data object Syncing : MediaSyncStatus

    data object WaitingForNetwork : MediaSyncStatus

    data object WaitingForUnmeteredNetwork : MediaSyncStatus

    data object NotRecorded : MediaSyncStatus

    data class Failed(
        val time: EpochMilliseconds,
    ) : MediaSyncStatus

    data class Completed(
        val time: EpochMilliseconds,
    ) : MediaSyncStatus

    val canSync: Boolean
        get() = this is Failed || this is Completed || this == NotRecorded

    companion object {
        fun from(
            isLoggedIn: Boolean,
            fetchMedia: ShouldFetchMedia,
            workState: WorkInfo.State?,
            workNeedsUnmeteredNetwork: Boolean,
            isOnline: Boolean,
            isNetworkMetered: Boolean,
            lastAttempt: MediaSyncAttempt?,
        ): MediaSyncStatus =
            when {
                !isLoggedIn -> NotLoggedIn
                workState == WorkInfo.State.RUNNING -> Syncing
                workState == WorkInfo.State.ENQUEUED && !isOnline -> WaitingForNetwork
                workState == WorkInfo.State.ENQUEUED && workNeedsUnmeteredNetwork && isNetworkMetered -> WaitingForUnmeteredNetwork
                workState == WorkInfo.State.ENQUEUED -> Syncing
                fetchMedia == ShouldFetchMedia.NEVER -> Off
                lastAttempt == null -> NotRecorded
                lastAttempt.failed -> Failed(lastAttempt.time)
                else -> Completed(lastAttempt.time)
            }
    }
}

data class MediaSyncAttempt(
    val time: EpochMilliseconds,
    val failed: Boolean,
)

val lastMediaSyncAttempt: MediaSyncAttempt?
    get() = Prefs.lastMediaSyncTime.takeIf { it > 0 }?.let { MediaSyncAttempt(it, Prefs.lastMediaSyncFailed) }

fun recordMediaSyncAttempt(failed: Boolean) = Prefs.recordMediaSync(TimeManager.time.intTimeMS(), failed)

fun mediaSyncStatus(work: WorkInfo?): MediaSyncStatus =
    MediaSyncStatus.from(
        isLoggedIn = isLoggedIn(),
        fetchMedia = Prefs.shouldFetchMedia,
        workState = work?.state,
        workNeedsUnmeteredNetwork = work?.constraints?.requiredNetworkType == NetworkType.UNMETERED,
        isOnline = NetworkUtils.isOnline,
        isNetworkMetered = NetworkUtils.isActiveNetworkMetered(),
        lastAttempt = lastMediaSyncAttempt,
    )
