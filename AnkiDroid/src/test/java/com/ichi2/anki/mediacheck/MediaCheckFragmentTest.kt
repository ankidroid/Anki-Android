// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2026 Ashish Yadav <mailtoashish693@gmail.com>
package com.ichi2.anki.mediacheck

import android.widget.Button
import androidx.test.ext.junit.runners.AndroidJUnit4
import anki.backend.BackendError
import anki.backend.backendError
import anki.media.CheckMediaResponse
import com.ichi2.anki.CollectionManager
import com.ichi2.anki.R
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.SingleFragmentActivity
import com.ichi2.anki.throwOnShowError
import com.ichi2.utils.positiveButton
import net.ankiweb.rsdroid.Backend
import net.ankiweb.rsdroid.BackendFactory
import net.ankiweb.rsdroid.exceptions.BackendIoException
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
class MediaCheckFragmentTest : RobolectricTest() {
    private val failure =
        BackendIoException(
            backendError {
                kind = BackendError.Kind.IO_ERROR
                message = "Operation not permitted (os error 1)"
            },
        )

    override fun getCollectionStorageMode() = CollectionStorageMode.ON_DISK

    @After
    fun resetBackendFactory() {
        BackendFactory.setOverride(null)
    }

    /** Issue 22075 */
    @Test
    fun `failed media check shows an error`() =
        runTest {
            useBackend {
                object : Backend() {
                    override fun checkMedia(): CheckMediaResponse = throw failure
                }
            }
            throwOnShowError = false

            startMediaCheck()

            assertEquals(failure.localizedMessage, getAlertDialogText(checkDismissed = true))
        }

    @Test
    fun `failed deletion shows an error instead of the result`() =
        runTest {
            useBackend {
                object : Backend() {
                    override fun trashMediaFiles(fnames: Iterable<String>): Unit = throw failure
                }
            }
            File(col.media.dir, "unused.png").writeText("not an image")
            throwOnShowError = false

            val activity = startMediaCheck()
            activity.findViewById<Button>(R.id.delete_used_media_button).performClick()
            advanceRobolectricLooper()
            clickAlertDialogButton { positiveButton }

            assertEquals(failure.localizedMessage, getAlertDialogText(checkDismissed = true))
        }

    private suspend fun useBackend(backend: () -> Backend) {
        CollectionManager.discardBackend()
        BackendFactory.setOverride { backend() }
    }

    private fun startMediaCheck(): SingleFragmentActivity =
        startActivityNormallyOpenCollectionWithIntent(
            SingleFragmentActivity::class.java,
            MediaCheckFragment.getIntent(targetContext),
        )
}
