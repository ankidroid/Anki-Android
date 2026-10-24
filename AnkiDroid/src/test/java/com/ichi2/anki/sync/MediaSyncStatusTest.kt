// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.sync

import androidx.work.WorkInfo
import com.ichi2.anki.settings.enums.ShouldFetchMedia
import com.ichi2.anki.sync.MediaSyncStatus.Completed
import com.ichi2.anki.sync.MediaSyncStatus.Failed
import com.ichi2.anki.sync.MediaSyncStatus.NotLoggedIn
import com.ichi2.anki.sync.MediaSyncStatus.NotRecorded
import com.ichi2.anki.sync.MediaSyncStatus.Off
import com.ichi2.anki.sync.MediaSyncStatus.Syncing
import com.ichi2.anki.sync.MediaSyncStatus.WaitingForNetwork
import com.ichi2.anki.sync.MediaSyncStatus.WaitingForUnmeteredNetwork
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.Test

class MediaSyncStatusTest {
    @Test
    fun `being logged out hides every other status`() {
        assertThat(status(isLoggedIn = false, workState = WorkInfo.State.RUNNING, lastAttempt = completed), equalTo(NotLoggedIn))
    }

    @Test
    fun `running or queued work is syncing`() {
        assertThat(status(workState = WorkInfo.State.RUNNING, lastAttempt = failed), equalTo(Syncing))
        assertThat(status(workState = WorkInfo.State.ENQUEUED), equalTo(Syncing))
        assertThat(status(workState = WorkInfo.State.ENQUEUED, workNeedsUnmeteredNetwork = true), equalTo(Syncing))
    }

    @Test
    fun `work needing an unmetered network waits on a metered one`() {
        assertThat(
            status(workState = WorkInfo.State.ENQUEUED, workNeedsUnmeteredNetwork = true, isNetworkMetered = true),
            equalTo(WaitingForUnmeteredNetwork),
        )
    }

    @Test
    fun `queued work waits for a connection while offline`() {
        assertThat(status(workState = WorkInfo.State.ENQUEUED, isOnline = false), equalTo(WaitingForNetwork))
        assertThat(
            status(workState = WorkInfo.State.ENQUEUED, workNeedsUnmeteredNetwork = true, isOnline = false),
            equalTo(WaitingForNetwork),
        )
    }

    @Test
    fun `media sync turned off shows once nothing is pending`() {
        assertThat(status(fetchMedia = ShouldFetchMedia.NEVER, lastAttempt = completed), equalTo(Off))
        assertThat(status(fetchMedia = ShouldFetchMedia.NEVER, workState = WorkInfo.State.RUNNING), equalTo(Syncing))
    }

    @Test
    fun `the last attempt decides once the work has finished`() {
        for (finished in listOf(null, WorkInfo.State.SUCCEEDED, WorkInfo.State.FAILED, WorkInfo.State.CANCELLED)) {
            assertThat(status(workState = finished), equalTo(NotRecorded))
            assertThat(status(workState = finished, lastAttempt = failed), equalTo(Failed(TIME)))
            assertThat(status(workState = finished, lastAttempt = completed), equalTo(Completed(TIME)))
        }
    }

    @Test
    fun `only settled statuses offer a sync`() {
        val offered = listOf(Completed(TIME), Failed(TIME), NotRecorded)
        val notOffered = listOf(NotLoggedIn, Off, Syncing, WaitingForNetwork, WaitingForUnmeteredNetwork)
        assertThat(offered.map { it.canSync }, equalTo(listOf(true, true, true)))
        assertThat(notOffered.map { it.canSync }, equalTo(listOf(false, false, false, false, false)))
    }

    private fun status(
        isLoggedIn: Boolean = true,
        fetchMedia: ShouldFetchMedia = ShouldFetchMedia.ALWAYS,
        workState: WorkInfo.State? = null,
        workNeedsUnmeteredNetwork: Boolean = false,
        isOnline: Boolean = true,
        isNetworkMetered: Boolean = false,
        lastAttempt: MediaSyncAttempt? = null,
    ) = MediaSyncStatus.from(isLoggedIn, fetchMedia, workState, workNeedsUnmeteredNetwork, isOnline, isNetworkMetered, lastAttempt)

    companion object {
        private const val TIME = 1_700_000_000_000L
        private val completed = MediaSyncAttempt(TIME, failed = false)
        private val failed = MediaSyncAttempt(TIME, failed = true)
    }
}
