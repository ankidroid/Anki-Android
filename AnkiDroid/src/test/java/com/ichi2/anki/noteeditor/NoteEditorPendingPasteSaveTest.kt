// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.noteeditor

import android.content.ClipData
import android.content.ContentProvider
import android.database.MatrixCursor
import android.graphics.Bitmap
import android.provider.OpenableColumns
import androidx.core.graphics.createBitmap
import androidx.core.net.toUri
import androidx.core.view.ContentInfoCompat
import androidx.core.view.ViewCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.NoteEditorActivity
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.common.destinations.NoteEditorDestination
import com.ichi2.anki.common.utils.ext.pasteImagesAsPng
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.setMain
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowContentResolver
import java.io.ByteArrayOutputStream
import kotlin.test.assertContains
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NoteEditorPendingPasteSaveTest : RobolectricTest() {
    override fun getCollectionStorageMode() = CollectionStorageMode.ON_DISK

    @Test
    fun `save includes a pending dropped image`() {
        assertSaveIncludesPendingImage(ContentInfoCompat.SOURCE_DRAG_AND_DROP)
    }

    @Test
    fun `save includes a pending clipboard image`() {
        assertSaveIncludesPendingImage(ContentInfoCompat.SOURCE_CLIPBOARD)
    }

    @Test
    fun `save includes a dropped image converted to PNG`() {
        assertSaveIncludesPendingImage(ContentInfoCompat.SOURCE_DRAG_AND_DROP, pasteAsPng = true)
    }

    @Test
    fun `save includes a clipboard image converted to PNG`() {
        assertSaveIncludesPendingImage(ContentInfoCompat.SOURCE_CLIPBOARD, pasteAsPng = true)
    }

    private fun assertSaveIncludesPendingImage(
        source: Int,
        pasteAsPng: Boolean = false,
    ) = runTest {
        col.config.pasteImagesAsPng = pasteAsPng
        val note = addBasicNote("Front", "Back")
        val editor =
            startRegularActivity<NoteEditorActivity>(
                NoteEditorDestination.EditNoteFromPreviewer(note.firstCard().id).toIntent(targetContext),
            ).getNoteEditorFragment()
        val image = "content://clipboard/image.jpg".toUri()
        val imageData =
            ByteArrayOutputStream().use {
                createBitmap(1, 1).compress(Bitmap.CompressFormat.JPEG, 100, it)
                it.toByteArray()
            }
        val cursor = MatrixCursor(arrayOf(OpenableColumns.DISPLAY_NAME)).apply { addRow(arrayOf("image.jpg")) }
        val provider = mock<ContentProvider>()
        whenever(provider.query(image, null, null, null, null)).thenReturn(cursor)
        ShadowContentResolver.registerProviderInternal("clipboard", provider)
        shadowOf(targetContext.contentResolver).registerInputStreamSupplier(image) { imageData.inputStream() }
        val payload =
            ContentInfoCompat
                .Builder(
                    ClipData("image", arrayOf("image/jpeg"), ClipData.Item(image)),
                    source,
                ).build()

        // A paste must finish before returning, even if coroutine work is queued.
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val field = editor.editFieldAt(1)!!
        ViewCompat.performReceiveContent(field, payload)
        assertContains(field.text.toString(), "Back<img src=\"", message = "Paste must insert the image synchronously")
        editor.saveNote()

        assertContains(col.getNote(note.id).fields[1], "Back<img src=\"")
        val importedImage =
            col.media.dir
                .listFiles()!!
                .single()
        assertEquals(if (pasteAsPng) "png" else "jpg", importedImage.extension)
    }
}
