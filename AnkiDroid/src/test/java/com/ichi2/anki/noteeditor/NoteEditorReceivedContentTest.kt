// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.noteeditor

import android.content.ClipData
import android.content.ClipDescription
import android.content.ContentProvider
import android.database.MatrixCursor
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.net.toUri
import androidx.core.view.ContentInfoCompat
import androidx.core.view.ViewCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.FieldEditText
import com.ichi2.anki.NoteEditorActivity
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.common.destinations.NoteEditorDestination
import com.ichi2.anki.common.utils.ext.pasteImagesAsPng
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowContentResolver
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class NoteEditorReceivedContentTest : RobolectricTest() {
    override fun getCollectionStorageMode() = CollectionStorageMode.ON_DISK

    @Test
    fun `mixed drop keeps text and images in clip order`() =
        runTest {
            val field = openBackField()
            val image = stubImage()
            val clip =
                ClipData(ClipDescription("drop", arrayOf("text/plain", "image/jpeg")), ClipData.Item("before ")).apply {
                    addItem(ClipData.Item(image))
                    addItem(ClipData.Item(" after"))
                }

            drop(field, clip)
            advanceUntilIdle()

            val filename =
                col.media.dir
                    .listFiles()!!
                    .single()
                    .name
            assertEquals("""Backbefore <img src="$filename"> after""", field.text.toString())
        }

    /**
     * A drop can arrive while an open editor can no longer access the collection.
     * Reject the attachment without throwing out of the receive callback or losing the draft.
     */
    @Test
    fun `dropping an image with a locked collection preserves the draft`() =
        runTest {
            val field = openBackField().apply { append(" draft") }
            val clip = ClipData("photo", arrayOf("image/jpeg"), ClipData.Item(stubImage()))

            // Lock the collection only after the field has loaded and contains an unsaved edit.
            // Without exception handling, the PNG preference lookup throws from this callback.
            withNullCollection {
                drop(field, clip)
                advanceUntilIdle()
            }

            assertEquals("Back draft", field.text.toString())
        }

    /**
     * Pasting a URI-only document as plain text must leave selected note text intact.
     * Android can turn an unsupported content URI into an empty string and paste it over the selection.
     */
    @Test
    fun `pasting an unsupported document as plain text preserves selected text`() =
        runTest {
            val field = openBackField()
            val document = "content://documents/report.pdf".toUri()
            val provider = mock<ContentProvider>()
            whenever(provider.getType(document)).thenReturn("application/pdf")
            ShadowContentResolver.registerProviderInternal("documents", provider)

            // Use the clipboard command, where unsupported MIME types can reach the listener.
            field.clipboard!!.setPrimaryClip(ClipData("document", arrayOf("application/pdf"), ClipData.Item(document)))
            field.setSelection(0, field.length())
            field.onTextContextMenuItem(android.R.id.pasteAsPlainText)
            advanceUntilIdle()

            // The buggy listener returns the URI to the default receiver, which replaces "Back".
            assertEquals("Back", field.text.toString())
        }

    /**
     * Replacing selected text with a caption and a photo should leave a caret after the insertion.
     * If the replacement remains selected, the next keystroke deletes both the caption and photo.
     */
    @Test
    fun `mixed drop collapses the selection after the inserted content`() =
        runTest {
            val field = openBackField()
            // A nonempty selection exercises replacement, unlike the append-only ordering test.
            field.setSelection(0, field.length())
            val clip =
                ClipData("caption and photo", arrayOf("text/plain", "image/jpeg"), ClipData.Item("caption ")).apply {
                    addItem(ClipData.Item(stubImage()))
                }
            drop(field, clip)
            advanceUntilIdle()

            // Compare to the actual replacement length to isolate caret behavior from clip ordering.
            // On the buggy revision the first assertion fails because the left edge stays selected.
            assertEquals(field.length(), field.selectionStart)
            assertEquals(field.length(), field.selectionEnd)
        }

    private fun openBackField(): FieldEditText {
        col.config.pasteImagesAsPng = false
        val note = addBasicNote("Front", "Back")
        val editor =
            startRegularActivity<NoteEditorActivity>(
                NoteEditorDestination.EditNoteFromPreviewer(note.firstCard().id).toIntent(targetContext),
            ).getNoteEditorFragment()
        return editor.editFieldAt(1)!!.apply {
            assertTrue(requestFocus())
            setSelection(length())
        }
    }

    private fun stubImage(): Uri {
        val image = "content://drop/photo.jpg".toUri()
        val provider = mock<ContentProvider>()
        whenever(provider.query(image, null, null, null, null)).thenAnswer {
            MatrixCursor(arrayOf(OpenableColumns.DISPLAY_NAME)).apply { addRow(arrayOf("photo.jpg")) }
        }
        whenever(provider.getType(image)).thenReturn("image/jpeg")
        ShadowContentResolver.registerProviderInternal("drop", provider)
        shadowOf(targetContext.contentResolver).registerInputStreamSupplier(image) { "photo".byteInputStream() }
        return image
    }

    private fun drop(
        field: FieldEditText,
        clip: ClipData,
    ) {
        val content = ContentInfoCompat.Builder(clip, ContentInfoCompat.SOURCE_DRAG_AND_DROP).build()
        ViewCompat.performReceiveContent(field, content)
    }
}
