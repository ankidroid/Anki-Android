// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import android.content.Intent
import android.os.Bundle
import android.os.Looper
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.dialogs.ConfirmationDialog
import com.ichi2.anki.utils.ext.DIALOG_FRAGMENT_TAG
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class CardTemplateEditorConfirmationReplayTest : RobolectricTest() {
    private lateinit var controller: ActivityController<CardTemplateEditor>

    private val editor: CardTemplateEditor
        get() = controller.get()

    @Test
    fun `pending delete confirmation opens full sync warning when returning from background`() {
        col.setSchemaModified()
        col.db.execute("update col set ls = scm")
        assertFalse(col.schemaChanged())

        val intent = CardTemplateEditor.getIntent(targetContext, col.notetypes.basicAndReversed.id)
        controller = startActivityControllerNormallyOpenCollectionWithIntent(CardTemplateEditor::class.java, intent)
        advanceRobolectricLooper()
        assertTrue(shadowOf(editor).clickMenuItem(R.id.action_delete))
        advanceRobolectricLooper()

        val saved = Bundle()
        controller
            .pause()
            .stop()
            .saveInstanceState(saved)
            .destroy()

        val realCollection = col
        val loadStarted = CountDownLatch(1)
        val releaseLoad = CountDownLatch(1)
        mockkObject(CollectionManager)
        every { CollectionManager.isOpenUnsafe() } returns false
        every { CollectionManager.getColUnsafe() } answers {
            if (Looper.myLooper() != Looper.getMainLooper()) {
                loadStarted.countDown()
                check(releaseLoad.await(15, TimeUnit.SECONDS)) { "Background load was not released" }
            }
            realCollection
        }

        try {
            controller = Robolectric.buildActivity(CardTemplateEditor::class.java, Intent(intent))
            saveControllerForCleanup(controller)
            controller.setup(saved)
            check(loadStarted.await(5, TimeUnit.SECONDS)) { "Collection load did not begin" }

            // Confirm the real restored dialog while the collection is still loading.
            confirm()
            assertNull(shownDialog())
            assertFalse(realCollection.schemaChanged())
            controller.pause().stop()

            // Loading finishes while the receiving fragment is stopped, leaving a pending result.
            releaseLoad.countDown()
            advanceRobolectricLooperUntil { editor.mainBinding.cardTemplateEditorPager.adapter != null }

            // ON_START replays the result. Opening its full-sync warning must not run nested transactions.
            controller.start().resume().visible()
            advanceRobolectricLooper()

            val warning = assertIs<ConfirmationDialog>(shownDialog()).requireDialog() as AlertDialog
            assertEquals(
                editor.getString(CommonString.full_sync_confirmation),
                requireNotNull(warning.findViewById<TextView>(android.R.id.message)).text.toString(),
            )
            assertEquals(listOf("Card 1", "Card 2"), editor.tempNoteType!!.notetype.templatesNames)
            confirm()
            assertEquals(listOf("Card 2"), editor.tempNoteType!!.notetype.templatesNames)
        } finally {
            releaseLoad.countDown()
            unmockkObject(CollectionManager)
            advanceRobolectricLooper()
        }
    }

    private fun confirm() {
        val dialog = assertIs<ConfirmationDialog>(shownDialog()).requireDialog() as AlertDialog
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        advanceRobolectricLooper()
    }

    private fun shownDialog() = editor.supportFragmentManager.findFragmentByTag(DIALOG_FRAGMENT_TAG)
}
