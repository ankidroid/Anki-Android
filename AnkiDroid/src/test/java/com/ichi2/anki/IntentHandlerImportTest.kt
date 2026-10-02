// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import android.content.Intent
import android.os.Looper
import androidx.appcompat.app.AlertDialog
import androidx.core.net.toUri
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.dialogs.utils.message
import com.ichi2.utils.ImportResolution
import com.ichi2.utils.ImportResult
import com.ichi2.utils.ImportUtils
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowDialog
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Import handling tests for [IntentHandler]. */
@RunWith(AndroidJUnit4::class)
class IntentHandlerImportTest : RobolectricTest() {
    @Before
    fun prepareImport() {
        setIntroductionSlidesShown(true)
        mockkObject(ImportUtils)
    }

    @After
    fun resetImport() {
        unmockkObject(ImportUtils)
    }

    @Test
    fun `import failure stays visible until dismissed`() {
        val failure = ImportResult.Failure("The selected file is no longer available")
        every { ImportUtils.prepareImport(any(), any()) } returns failure

        val activity = launchImport()

        val dialog = assertIs<AlertDialog>(ShadowDialog.getLatestDialog())
        assertEquals(failure.humanReadableMessage, dialog.message)
        assertTrue(dialog.isShowing)
        assertFalse(activity.isFinishing)

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(activity.isFinishing)
    }

    @Test
    fun `successful import finishes the intent handler`() {
        every { ImportUtils.prepareImport(any(), any()) } returns
            ImportResult.Success(
                ImportResolution.DeckPackage(ImportResolution.Source("content://import/deck.apkg".toUri(), "deck.apkg")),
                "/cache/deck.apkg",
            )

        assertTrue(launchImport().isFinishing)
    }

    @Test
    fun `package import failure from a text intent stays visible until dismissed`() {
        val failure = ImportResult.Failure("The selected file is no longer available")
        every { ImportUtils.prepareImport(any(), any()) } returns failure

        val activity = launchImport("text/plain")

        val dialog = assertIs<AlertDialog>(ShadowDialog.getLatestDialog())
        assertEquals(failure.humanReadableMessage, dialog.message)
        assertTrue(dialog.isShowing)
        assertFalse(activity.isFinishing)

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(activity.isFinishing)
    }

    @Test
    fun `import before introduction finishes the intent handler`() {
        setIntroductionSlidesShown(false)

        assertTrue(launchImport().isFinishing)
    }

    private fun launchImport(mimeType: String? = null): IntentHandler {
        val intent = Intent(Intent.ACTION_VIEW).setDataAndType("content://import/deck.apkg".toUri(), mimeType)
        val controller = Robolectric.buildActivity(IntentHandler::class.java, intent)
        saveControllerForCleanup(controller)
        return controller.create().get()
    }
}
