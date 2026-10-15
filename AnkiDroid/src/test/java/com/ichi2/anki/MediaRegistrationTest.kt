// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import android.content.ClipDescription
import android.content.ContentProvider
import android.database.MatrixCursor
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.net.toUri
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowContentResolver
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class MediaRegistrationTest : RobolectricTest() {
    override fun getCollectionStorageMode() = CollectionStorageMode.ON_DISK

    @Test
    fun `pasted image references the normalized media filename`() {
        assertPastedMedia("image.png", "image/png", """<img src="%s">""")
    }

    @Test
    fun `pasted audio references the normalized media filename`() {
        assertPastedMedia("audio.mp3", "audio/mpeg", "[sound:%s]")
    }

    private fun assertPastedMedia(
        filename: String,
        mimeType: String,
        expectedTag: String,
    ) {
        val data = "pasted media".toByteArray()
        // Exceed the backend's filename length limit using characters valid on every OS.
        val originalFilename = "a".repeat(120) + filename
        val uri = stubClipboardMedia(originalFilename, data)

        val html =
            MediaRegistration.loadMediaIntoCollection(
                targetContext,
                uri,
                ClipDescription("media", arrayOf(mimeType)),
                pasteAsPng = false,
                showError = { error("Unexpected paste error: $it") },
            )

        val importedFile =
            col.media.dir
                .listFiles()!!
                .single()
        assertTrue(importedFile.name.length < originalFilename.length, "Expected the backend to shorten the filename")
        assertContentEquals(data, importedFile.readBytes())
        assertEquals(expectedTag.format(importedFile.name), html)
    }

    private fun stubClipboardMedia(
        filename: String,
        data: ByteArray,
    ): Uri {
        val uri = "content://clipboard/media".toUri()
        val cursor = MatrixCursor(arrayOf(OpenableColumns.DISPLAY_NAME)).apply { addRow(arrayOf(filename)) }
        val provider = mock<ContentProvider>()
        whenever(provider.query(uri, null, null, null, null)).thenReturn(cursor)
        ShadowContentResolver.registerProviderInternal("clipboard", provider)
        shadowOf(targetContext.contentResolver).registerInputStreamSupplier(uri) { data.inputStream() }
        return uri
    }
}
