// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2026 Ashish Yadav <mailtoashish693@gmail.com>
package com.ichi2.anki.mediacheck

import android.widget.Button
import androidx.test.ext.junit.runners.AndroidJUnit4
import anki.backend.BackendError
import anki.backend.backendError
import anki.media.CheckMediaResponse
import com.ichi2.anki.CollectionManager
import com.ichi2.anki.CommonPlurals
import com.ichi2.anki.R
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.SingleFragmentActivity
import com.ichi2.testutils.rules.BackendOverrideRule
import com.ichi2.utils.positiveButton
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import net.ankiweb.rsdroid.Backend
import net.ankiweb.rsdroid.exceptions.BackendIoException
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.android.controller.ActivityController
import org.robolectric.shadows.ShadowDialog
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class MediaCheckFragmentTest : RobolectricTest() {
    private val failure =
        BackendIoException(
            backendError {
                kind = BackendError.Kind.IO_ERROR
                message = "Operation not permitted (os error 1)"
            },
        )

    @get:Rule
    val backend = BackendOverrideRule()

    override fun getCollectionStorageMode() = CollectionStorageMode.ON_DISK

    /** Issue 22075 */
    @Test
    fun `failed media check shows an error`() =
        runTest {
            backend.replaceWith {
                object : Backend() {
                    override fun checkMedia(): CheckMediaResponse = throw failure
                }
            }

            startMediaCheck()

            assertEquals(failure.localizedMessage, getAlertDialogText(checkDismissed = true))
        }

    @Test
    fun `deleting unused media shows the result`() =
        runTest {
            addUnusedMedia()
            val mediaCheck = startMediaCheck()

            mediaCheck.confirmDeletion()

            assertEquals(oneFileDeleted, getAlertDialogText(checkDismissed = true))
            clickAlertDialogButton { positiveButton }
            assertTrue(mediaCheck.get().isFinishing)
        }

    @Test
    fun `result is shown again after recreation`() =
        runTest {
            addUnusedMedia()
            val mediaCheck = startMediaCheck()
            mediaCheck.confirmDeletion()
            val shownBeforeRecreation = ShadowDialog.getLatestDialog()

            mediaCheck.recreate()
            advanceRobolectricLooper()

            assertNotSame(shownBeforeRecreation, ShadowDialog.getLatestDialog())
            assertEquals(oneFileDeleted, getAlertDialogText(checkDismissed = true))
        }

    @Test
    fun `result dialog is not duplicated on return`() =
        runTest {
            addUnusedMedia()
            val mediaCheck = startMediaCheck()
            mediaCheck.confirmDeletion()
            val result = ShadowDialog.getLatestDialog()

            mediaCheck.pause().stop()
            mediaCheck.start().resume()
            advanceRobolectricLooper()

            assertSame(result, ShadowDialog.getLatestDialog())
        }

    @Test
    fun `failed deletion shows an error instead of the result`() =
        runTest {
            failDeletion()

            startMediaCheck().confirmDeletion()

            assertEquals(failure.localizedMessage, getAlertDialogText(checkDismissed = true))
        }

    @Test
    fun `error is shown again after recreation`() =
        runTest {
            failDeletion()
            val mediaCheck = startMediaCheck()
            mediaCheck.confirmDeletion()
            val shownBeforeRecreation = ShadowDialog.getLatestDialog()

            mediaCheck.recreate()
            advanceRobolectricLooper()

            assertNotSame(shownBeforeRecreation, ShadowDialog.getLatestDialog())
            assertEquals(failure.localizedMessage, getAlertDialogText(checkDismissed = true))
        }

    @Test
    fun `dismissed error is not shown again`() =
        runTest {
            failDeletion()
            val mediaCheck = startMediaCheck()
            mediaCheck.confirmDeletion()
            clickAlertDialogButton { positiveButton }
            val dismissed = ShadowDialog.getLatestDialog()

            mediaCheck.recreate()
            advanceRobolectricLooper()

            assertSame(dismissed, ShadowDialog.getLatestDialog())
        }

    @Test
    fun `deletion failing during recreation shows an error`() =
        runTest {
            failDeletion()
            val mediaCheck = startMediaCheck()

            withQueuedCollectionAccess {
                mediaCheck.confirmDeletion()
                mediaCheck.recreate()
                advanceUntilIdle()
            }
            advanceRobolectricLooper()

            assertEquals(failure.localizedMessage, getAlertDialogText(checkDismissed = true))
        }

    @Test
    fun `failure in the background is shown on return`() =
        runTest {
            failDeletion()
            val mediaCheck = startMediaCheck()

            withQueuedCollectionAccess {
                mediaCheck.confirmDeletion()
                mediaCheck.pause().stop()
                advanceUntilIdle()
            }
            advanceRobolectricLooper()
            assertNull(getAlertDialogText(checkDismissed = true))

            mediaCheck.start().resume()
            advanceRobolectricLooper()

            assertEquals(failure.localizedMessage, getAlertDialogText(checkDismissed = true))
        }

    private suspend fun failDeletion() {
        backend.replaceWith {
            object : Backend() {
                override fun trashMediaFiles(fnames: Iterable<String>): Unit = throw failure
            }
        }
        addUnusedMedia()
    }

    private fun addUnusedMedia() = File(col.media.dir, "unused.png").writeText("not an image")

    private val oneFileDeleted: String
        get() = targetContext.resources.getQuantityString(CommonPlurals.delete_media_result_message, 1, 1)

    private fun ActivityController<SingleFragmentActivity>.confirmDeletion() {
        get().findViewById<Button>(R.id.delete_used_media_button).performClick()
        advanceRobolectricLooper()
        clickAlertDialogButton { positiveButton }
    }

    private suspend fun <T> TestScope.withQueuedCollectionAccess(block: suspend () -> T): T {
        val previousQueue = CollectionManager.setTestDispatcher(StandardTestDispatcher(testScheduler), useReentrantLock = false)
        try {
            return block()
        } finally {
            CollectionManager.setTestDispatcher(previousQueue)
        }
    }

    private fun startMediaCheck(): ActivityController<SingleFragmentActivity> =
        startActivityControllerNormallyOpenCollectionWithIntent(
            SingleFragmentActivity::class.java,
            MediaCheckFragment.getIntent(targetContext),
        )
}
