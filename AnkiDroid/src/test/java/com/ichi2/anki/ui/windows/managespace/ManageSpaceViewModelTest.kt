// SPDX-FileCopyrightText: 2026 Ashish Yadav <mailtoashish693@gmail.com>
// SPDX-License-Identifier: GPL-3.0-or-later
package com.ichi2.anki.ui.windows.managespace

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.progress.ViewModelProgress
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertIs

@RunWith(AndroidJUnit4::class)
class ManageSpaceViewModelTest : RobolectricTest() {
    override fun getCollectionStorageMode() = CollectionStorageMode.ON_DISK

    @Test
    fun `deleteMediaFiles reports progress`() =
        runProgressTest {
            assertProgressAround(ManageSpaceProgress.DELETING_MEDIA) { deleteMediaFiles(emptyList()) }
        }

    @Test
    fun `deleteBackups reports progress`() =
        runProgressTest {
            assertProgressAround(ManageSpaceProgress.DELETING_BACKUPS) { deleteBackups(emptyList()) }
        }

    @Test
    fun `deleteCollection reports progress`() =
        runProgressTest {
            assertProgressAround(ManageSpaceProgress.DELETING_COLLECTION) { deleteCollection() }
        }

    private fun runProgressTest(testBody: suspend ManageSpaceViewModel.() -> Unit) =
        runTest {
            col
            ManageSpaceViewModel(ApplicationProvider.getApplicationContext<Application>()).testBody()
        }

    private suspend fun ManageSpaceViewModel.assertProgressAround(
        message: ManageSpaceProgress,
        op: suspend () -> Unit,
    ) {
        progressManager.progress.test {
            assertIs<ViewModelProgress.Idle>(awaitItem())
            op()
            val active = assertIs<ViewModelProgress.Active<ManageSpaceProgress>>(awaitItem())
            assertEquals(message, active.message)
            assertIs<ViewModelProgress.Idle>(awaitItem())
            expectNoEvents()
        }
    }
}
