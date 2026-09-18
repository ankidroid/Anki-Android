// noinspection MissingCopyrightHeader #17351

package com.ichi2.anki.shareddeck

import android.app.DownloadManager
import android.content.Intent
import android.database.MatrixCursor
import android.net.Uri
import android.os.Bundle
import android.os.Looper
import androidx.core.content.FileProvider
import androidx.core.view.isVisible
import androidx.fragment.app.commitNow
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.IntentHandler
import com.ichi2.anki.R
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.shareddeck.SharedDecksDownloadFragment.Companion.getDeckPageUri
import com.ichi2.utils.openInputStreamSafe
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mockStatic
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowDialog
import java.io.File
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Tests for [SharedDecksDownloadFragment] */
@RunWith(AndroidJUnit4::class)
class SharedDecksDownloadFragmentTest : RobolectricTest() {
    @Test
    fun `completed download opens the existing file for import`() {
        val download = completedDownload()

        val intent = assertNotNull(shadowOf(download.activity).nextStartedActivity)
        assertEquals(IntentHandler::class.java.name, intent.component?.className)
        assertEquals(download.uri, intent.data)
        assertTrue(intent.getBooleanExtra(SharedDecksDownloadFragment.EXTRA_IS_SHARED_DOWNLOAD, false))
        val input = assertNotNull(download.activity.contentResolver.openInputStreamSafe(intent.data!!))
        assertEquals("deck contents", input.bufferedReader().use { it.readText() })
    }

    @Test
    fun `filename collision imports the completed download instead of the older file`() {
        val download = startDownload()
        download.file.writeText("older deck")
        val completedFile = File(download.file.parentFile, "blank-1.apkg").apply { writeText("new deck") }

        download.complete(completedFile = completedFile)

        val intent = assertNotNull(shadowOf(download.activity).nextStartedActivity)
        val input = assertNotNull(download.activity.contentResolver.openInputStreamSafe(intent.data!!))
        assertEquals("new deck", input.bufferedReader().use { it.readText() })
        assertEquals("older deck", download.file.readText())

        // Manually importing again must also use the completed download's file.
        download.withFileProvider {
            download.fragment.binding.importSharedDeckButton
                .performClick()
        }
        val retryIntent = assertNotNull(shadowOf(download.activity).nextStartedActivity)
        assertEquals(intent.data, retryIntent.data)

        assertTrue(completedFile.delete())
        download.fragment.binding.importSharedDeckButton
            .performClick()
        shadowOf(Looper.getMainLooper()).idle()

        assertNull(shadowOf(download.activity).nextStartedActivity)
        assertTrue(download.fragment.binding.tryDownloadAgainButton.isVisible)
    }

    @Test
    fun `missing completed file offers retry even if the requested filename exists`() {
        val download = startDownload()
        download.file.writeText("older deck")
        val completedFile = File(download.file.parentFile, "blank-1.apkg")
        assertFalse(completedFile.exists())

        download.complete(completedFile = completedFile)

        assertNull(shadowOf(download.activity).nextStartedActivity)
        assertTrue(download.fragment.binding.tryDownloadAgainButton.isVisible)
    }

    @Test
    fun `missing download location offers retry instead of importing the older file`() {
        val download = startDownload()
        download.file.writeText("older deck")

        download.complete(localUri = null)

        assertNull(shadowOf(download.activity).nextStartedActivity)
        assertTrue(download.fragment.binding.tryDownloadAgainButton.isVisible)
    }

    @Test
    fun `duplicate completion does not replace the completed file or start another import`() {
        val download = completedDownload()
        val intent = assertNotNull(shadowOf(download.activity).nextStartedActivity)
        val otherFile = File(download.file.parentFile, "other.apkg").apply { writeText("other deck") }

        download.complete(completedFile = otherFile)

        assertNull(shadowOf(download.activity).nextStartedActivity)

        download.withFileProvider {
            download.fragment.binding.importSharedDeckButton
                .performClick()
        }
        val manualIntent = assertNotNull(shadowOf(download.activity).nextStartedActivity)
        assertEquals(intent.data, manualIntent.data)
    }

    @Test
    fun `retry ignores the previous download and imports the new one`() {
        val download = startDownload()
        download.complete(status = DownloadManager.STATUS_FAILED)
        assertNull(shadowOf(download.activity).nextStartedActivity)

        whenever(download.activity.downloadManager.enqueue(any())).thenReturn(2L)
        val retryButton = download.fragment.binding.tryDownloadAgainButton
        assertTrue(retryButton.isVisible)
        retryButton.performClick()
        download.file.writeText("downloaded again")

        download.complete(downloadId = 1L)
        assertNull(shadowOf(download.activity).nextStartedActivity)

        download.complete(downloadId = 2L)
        assertNotNull(shadowOf(download.activity).nextStartedActivity)
    }

    @Test
    fun `retry binds the import button to the new completed file`() {
        val download = completedDownload()
        assertNotNull(shadowOf(download.activity).nextStartedActivity)
        assertTrue(download.file.delete())
        download.fragment.binding.importSharedDeckButton
            .performClick()
        shadowOf(Looper.getMainLooper()).idle()

        whenever(download.activity.downloadManager.enqueue(any())).thenReturn(2L)
        assertTrue(download.fragment.binding.tryDownloadAgainButton.isVisible)
        download.fragment.binding.tryDownloadAgainButton
            .performClick()
        val completedFile = File(download.file.parentFile, "blank-1.apkg").apply { writeText("new deck") }
        download.complete(downloadId = 2L, completedFile = completedFile)
        val intent = assertNotNull(shadowOf(download.activity).nextStartedActivity)

        download.withFileProvider {
            download.fragment.binding.importSharedDeckButton
                .performClick()
        }

        val manualIntent = assertNotNull(shadowOf(download.activity).nextStartedActivity)
        assertEquals(intent.data, manualIntent.data)
        val input = assertNotNull(download.activity.contentResolver.openInputStreamSafe(manualIntent.data!!))
        assertEquals("new deck", input.bufferedReader().use { it.readText() })
    }

    @Test
    fun `completed download with a missing file offers retry instead of importing`() {
        val download = startDownload()
        assertFalse(download.file.exists())

        download.complete()

        assertNull(shadowOf(download.activity).nextStartedActivity)
        val retryButton = download.fragment.binding.tryDownloadAgainButton
        assertTrue(retryButton.isVisible)

        retryButton.performClick()
        verify(download.activity.downloadManager).remove(1L)
        download.file.writeText("downloaded again")
        download.complete()

        assertNotNull(shadowOf(download.activity).nextStartedActivity)
    }

    @Test
    fun `import button offers retry if the downloaded file has been deleted`() {
        val download = completedDownload()
        assertNotNull(shadowOf(download.activity).nextStartedActivity)
        assertTrue(download.file.delete())

        download.fragment.binding.importSharedDeckButton
            .performClick()
        shadowOf(Looper.getMainLooper()).idle()

        assertNull(shadowOf(download.activity).nextStartedActivity)
        val retryButton = download.fragment.binding.tryDownloadAgainButton
        assertTrue(retryButton.isVisible)
    }

    @Test
    fun `removing the download fragment prevents late completion from starting an import`() {
        val download = startDownload()
        download.file.writeText("deck contents")
        download.activity.supportFragmentManager.commitNow { remove(download.fragment) }

        download.complete()

        assertNull(shadowOf(download.activity).nextStartedActivity)
    }

    @Test
    fun `destroying only the view prevents late completion from starting an import`() {
        val download = startDownload()
        download.file.writeText("deck contents")
        download.activity.supportFragmentManager.commitNow { detach(download.fragment) }
        assertEquals(Lifecycle.State.CREATED, download.fragment.lifecycle.currentState)
        assertNull(download.fragment.view)

        download.complete()

        assertNull(shadowOf(download.activity).nextStartedActivity)
    }

    @Test
    fun `destroying the view dismisses the download cancellation dialog`() {
        val download = startDownload()
        download.fragment.binding.cancelDownloadButton
            .performClick()
        val dialog = assertNotNull(ShadowDialog.getLatestDialog())
        assertTrue(dialog.isShowing)

        download.activity.supportFragmentManager.commitNow { detach(download.fragment) }

        assertFalse(dialog.isShowing)
    }

    @Test
    fun `test getDeckIdFromDownloadURL with valid URL`() {
        val url = "https://ankiweb.net/svc/shared/download-deck/1104981491?t=some-token"
        assertEquals("1104981491", SharedDecksDownloadFragment.getDeckIdFromDownloadURL(url))
    }

    @Test
    fun `test getDeckIdFromDownloadURL without Query Parameter`() {
        val url = "https://ankiweb.net/svc/shared/download-deck/1104981491"
        assertEquals("1104981491", SharedDecksDownloadFragment.getDeckIdFromDownloadURL(url))
    }

    @Test
    fun `test getDeckIdFromDownloadURL with invalid URL`() {
        val url = "https://ankiweb.net/svc/shared/download-deck/"
        assertNull(SharedDecksDownloadFragment.getDeckIdFromDownloadURL(url), "Expected deckId to be null")
    }

    @Test
    fun `test getDeckPageUri with valid deck URL`() {
        val url = "https://ankiweb.net/svc/shared/download-deck/1104981491?t=some-token"
        assertEquals("https://ankiweb.net/shared/info/1104981491", targetContext.getDeckPageUri(url))
    }

    @Test
    fun `test getDeckPageUri with invalid deck URL`() {
        val url = "https://ankiweb.net/svc/shared/download-deck/"
        assertEquals("https://ankiweb.net/shared/decks/", targetContext.getDeckPageUri(url))
    }

    private fun completedDownload(): Download =
        startDownload().apply {
            file.writeText("deck contents")
            complete()
        }

    private fun startDownload(): Download {
        val activity = startActivityNormallyOpenCollectionWithIntent(SharedDecksActivity::class.java, Intent())
        activity.downloadManager =
            mock {
                on { enqueue(any()) } doReturn 1L
                on { query(any()) } doAnswer { downloadCursor(DownloadManager.STATUS_RUNNING) }
            }
        val fragment =
            SharedDecksDownloadFragment().apply {
                arguments =
                    Bundle().apply {
                        putSerializable(
                            SharedDecksActivity.DOWNLOAD_FILE,
                            DownloadFile(
                                url = "https://ankiweb.net/svc/shared/download-deck/1",
                                userAgent = "AnkiDroid",
                                contentDisposition = "attachment; filename=\"blank.apkg\"",
                                mimeType = "application/octet-stream",
                            ),
                        )
                    }
            }
        activity.supportFragmentManager.commitNow {
            add(R.id.shared_decks_fragment_container, fragment)
        }
        shadowOf(Looper.getMainLooper()).idle()
        val request = argumentCaptor<DownloadManager.Request>()
        verify(activity.downloadManager).enqueue(request.capture())
        return Download(activity, fragment, File(shadowOf(request.firstValue).destination.path!!))
    }

    private inner class Download(
        val activity: SharedDecksActivity,
        val fragment: SharedDecksDownloadFragment,
        val file: File,
    ) {
        val uri: Uri =
            Uri
                .Builder()
                .scheme("content")
                .authority("${activity.packageName}.apkgfileprovider")
                .appendPath("shared_decks")
                .appendPath(file.name)
                .build()

        fun complete(
            downloadId: Long = 1L,
            status: Int = DownloadManager.STATUS_SUCCESSFUL,
            completedFile: File = file,
            localUri: Uri? = Uri.fromFile(completedFile),
        ) {
            whenever(activity.downloadManager.query(any())).thenAnswer { downloadCursor(status, localUri) }
            withFileProvider {
                activity.sendBroadcast(
                    Intent(DownloadManager.ACTION_DOWNLOAD_COMPLETE).putExtra(DownloadManager.EXTRA_DOWNLOAD_ID, downloadId),
                )
            }
        }

        fun withFileProvider(action: () -> Unit) {
            // FileProvider's root check hardcodes '/', which rejects Windows paths under Robolectric.
            // Stub only URI generation; keep the real file and the fragment's existence checks.
            mockStatic(FileProvider::class.java).use { provider ->
                provider
                    .`when`<Uri> { FileProvider.getUriForFile(any(), eq(uri.authority!!), any()) }
                    .thenAnswer { invocation ->
                        val importedFile = invocation.getArgument<File>(2)
                        val importedUri =
                            uri
                                .buildUpon()
                                .path(null)
                                .appendPath("shared_decks")
                                .appendPath(importedFile.name)
                                .build()
                        shadowOf(activity.contentResolver).registerInputStreamSupplier(importedUri) { importedFile.inputStream() }
                        importedUri
                    }
                action()
                shadowOf(Looper.getMainLooper()).idle()
            }
        }
    }

    private fun downloadCursor(
        status: Int,
        localUri: Uri? = null,
    ): MatrixCursor =
        MatrixCursor(
            arrayOf(
                DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR,
                DownloadManager.COLUMN_TOTAL_SIZE_BYTES,
                DownloadManager.COLUMN_STATUS,
                DownloadManager.COLUMN_REASON,
                DownloadManager.COLUMN_LOCAL_URI,
            ),
        ).apply { addRow(arrayOf<Any?>(100L, 100L, status, 0, localUri?.toString())) }
}
