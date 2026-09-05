// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2026 Ashish Yadav <mailtoashish693@gmail.com>
package com.ichi2.anki.mediacheck

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.ichi2.anki.R
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.progress.ViewModelProgress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.setMain
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class MediaCheckViewModelTest : RobolectricTest() {
    private val viewModel = MediaCheckViewModel()

    // backend media operations need a real collection folder
    override fun getCollectionStorageMode() = CollectionStorageMode.ON_DISK

    @Test
    fun `checkMedia reports progress and publishes the result`() =
        runViewModelTest {
            addUnusedMediaFile("unused.png")

            viewModel.assertProgressAround(R.string.check_media_message) { viewModel.checkMedia() }

            val result = assertNotNull(viewModel.mediaCheckResult.value)
            assertEquals(listOf("unused.png"), result.unusedList)
        }

    @Test
    fun `deleteUnusedMedia reports progress and trashes the unused files`() =
        runViewModelTest {
            val file = addUnusedMediaFile("unused.png")
            viewModel.checkMedia().join()

            viewModel.assertProgressAround(R.string.delete_media_message) { viewModel.deleteUnusedMedia() }

            assertEquals(1, viewModel.deletedFiles)
            assertTrue(!file.exists(), "unused file should have been moved to the trash")
        }

    @Test
    fun `tagMissing reports progress and tags the notes with missing media`() =
        runViewModelTest {
            val note = addBasicNote("""<img src="missing.png">""", "back")
            viewModel.checkMedia().join()

            viewModel.assertProgressAround(R.string.check_media_adding_missing_tag) { viewModel.tagMissing("missing") }

            assertEquals(1, viewModel.taggedFiles)
            assertEquals(listOf("missing"), col.getNote(note.id).tags)
        }

    private suspend fun MediaCheckViewModel.assertProgressAround(
        messageRes: Int,
        op: () -> Job,
    ) {
        progressManager.progress.test {
            assertIs<ViewModelProgress.Idle>(awaitItem())
            op().join()
            val active = assertIs<ViewModelProgress.Active>(awaitItem())
            assertEquals(messageRes, active.messageRes)
            assertIs<ViewModelProgress.Idle>(awaitItem())
            expectNoEvents()
        }
    }

    private fun addUnusedMediaFile(name: String): File = File(col.media.dir, name).apply { writeText("not an image") }

    /**
     * [runTest] with a [StandardTestDispatcher] as Main: the default unconfined one runs a
     * ViewModel op to completion before any collector resumes, hiding the Active state.
     */
    private fun runViewModelTest(testBody: suspend TestScope.() -> Unit) =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            testBody()
        }
}
