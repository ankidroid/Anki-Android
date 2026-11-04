// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.pages

import android.graphics.Bitmap
import androidx.core.graphics.createBitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import anki.image_occlusion.updateImageOcclusionNoteRequest
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.SingleFragmentActivity
import com.ichi2.anki.pages.viewmodel.ImageOcclusionArgs
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class ImageOcclusionUpdateRequestTest : RobolectricTest() {
    override fun getCollectionStorageMode() = CollectionStorageMode.ON_DISK

    @Test
    fun `a save received while the editor stops does not overwrite a later edit`() =
        runTest {
            val image = tempFolder.newFile("occlusion.png")
            image.outputStream().use {
                assertTrue(createBitmap(16, 16).compress(Bitmap.CompressFormat.PNG, 100, it))
            }
            val mask = "{{c1::image-occlusion:rect:left=.1:top=.1:width=.3:height=.3}}"
            val notetype = col.notetypes.imageOcclusion.id
            withCol { addImageOcclusionNote(notetype, image.path, mask, "Initial header", "", emptyList()) }
            val note = col.findNotes("").single()
            val controller =
                startActivityControllerNormallyOpenCollectionWithIntent(
                    SingleFragmentActivity::class.java,
                    ImageOcclusion.getIntent(targetContext, ImageOcclusionArgs.Edit(note)),
                )
            val editor = assertIs<ImageOcclusion>(controller.get().fragment)
            val input =
                updateImageOcclusionNoteRequest {
                    noteId = note
                    occlusions = mask
                    header = "Older edit"
                }.toByteArray()

            controller.pause().stop()
            val firstSave = async { editor.handlePostRequest(PostRequestUri("/_anki/updateImageOcclusionNote"), input) }
            runCurrent()
            // A separate editor can save the same note while this activity is stopped.
            withCol { updateImageOcclusionNote(note, mask, "Newer edit", "", emptyList()) }

            controller.restart().start().resume()
            firstSave.await()
            assertEquals("Newer edit", col.getImageOcclusionNote(note).note.header)
        }
}
