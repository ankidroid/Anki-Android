// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.pages

import android.graphics.Bitmap
import androidx.core.graphics.createBitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import anki.image_occlusion.addImageOcclusionNoteRequest
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.SingleFragmentActivity
import com.ichi2.anki.libanki.Consts
import com.ichi2.anki.libanki.DeckId
import com.ichi2.anki.pages.viewmodel.ImageOcclusionArgs
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.android.controller.ActivityController
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class ImageOcclusionRequestTest : RobolectricTest() {
    override fun getCollectionStorageMode() = CollectionStorageMode.ON_DISK

    @Test
    fun `add saves to the editor deck after the active deck changes while stopped`() =
        runTest {
            val editorDeck = Consts.DEFAULT_DECK_ID
            val otherDeck = addDeck("Other deck")
            val (controller, input) = openEditor(editorDeck)
            val editor = assertIs<ImageOcclusion>(controller.get().fragment)
            runCurrent()
            assertEquals(editorDeck, col.decks.selected())

            controller.pause().stop()
            val save = async { editor.handlePostRequest(ADD_NOTE, input) }
            runCurrent()
            // A separate activity or a Deck.CONTENT_SELECTED_URI update can select a different deck.
            withCol { decks.select(otherDeck) }
            controller.restart().start().resume()
            save.await()

            assertEquals(1, col.cardCount())
            assertEquals(otherDeck, col.decks.selected())
            assertEquals(1, col.decks.cardCount(editorDeck), "The card must go to the deck shown in the editor")
        }

    @Test
    fun `queued add does not fall back to Default after the editor deck is deleted`() =
        runTest {
            val editorDeck = addDeck("Editor deck")
            val (controller, input) = openEditor(editorDeck)
            val editor = assertIs<ImageOcclusion>(controller.get().fragment)
            runCurrent()
            assertEquals(editorDeck, col.decks.selected())

            controller.pause().stop()
            val save = async { runCatching { editor.handlePostRequest(ADD_NOTE, input) } }
            runCurrent()
            withCol { decks.remove(listOf(editorDeck)) }
            controller.restart().start().resume()
            if (save.await().isFailure) {
                assertFalse(controller.get().isFinishing, "The editor should remain open after a failed save")
            }

            assertEquals(0, col.cardCount(), "Deleting the chosen deck must not redirect its queued save to another deck")
        }

    private fun openEditor(deckId: DeckId): Pair<ActivityController<SingleFragmentActivity>, ByteArray> {
        val image = tempFolder.newFile("occlusion.png")
        image.outputStream().use {
            assertTrue(createBitmap(16, 16).compress(Bitmap.CompressFormat.PNG, 100, it))
        }
        val notetype = col.notetypes.imageOcclusion.id
        val input =
            addImageOcclusionNoteRequest {
                imagePath = image.path
                notetypeId = notetype
                occlusions = "{{c1::image-occlusion:rect:left=.1:top=.1:width=.3:height=.3}}"
            }.toByteArray()
        val controller =
            startActivityControllerNormallyOpenCollectionWithIntent(
                SingleFragmentActivity::class.java,
                ImageOcclusion.getIntent(targetContext, ImageOcclusionArgs.Add(image.path, notetype, deckId)),
            )
        return controller to input
    }

    companion object {
        private val ADD_NOTE = PostRequestUri("/_anki/addImageOcclusionNote")
    }
}
