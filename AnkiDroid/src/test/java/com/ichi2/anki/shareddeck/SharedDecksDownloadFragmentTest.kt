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
    fun `duplicate completion does not start another import`() {
        val download = completedDownload()
        assertNotNull(shadowOf(download.activity).nextStartedActivity)

        download.complete()

        assertNull(shadowOf(download.activity).nextStartedActivity)
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

        init {
            shadowOf(activity.contentResolver).registerInputStreamSupplier(uri) { file.inputStream() }
        }

        fun complete(
            downloadId: Long = 1L,
            status: Int = DownloadManager.STATUS_SUCCESSFUL,
        ) {
            whenever(activity.downloadManager.query(any())).thenAnswer { downloadCursor(status) }
            // FileProvider's root check hardcodes '/', which rejects Windows paths under Robolectric.
            // Stub only URI generation; keep the real file and the fragment's existence checks.
            mockStatic(FileProvider::class.java).use { provider ->
                provider.`when`<Uri> { FileProvider.getUriForFile(any(), eq(uri.authority!!), eq(file)) }.thenReturn(uri)
                activity.sendBroadcast(
                    Intent(DownloadManager.ACTION_DOWNLOAD_COMPLETE).putExtra(DownloadManager.EXTRA_DOWNLOAD_ID, downloadId),
                )
                shadowOf(Looper.getMainLooper()).idle()
            }
        }
    }

    private fun downloadCursor(status: Int): MatrixCursor =
        MatrixCursor(
            arrayOf(
                DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR,
                DownloadManager.COLUMN_TOTAL_SIZE_BYTES,
                DownloadManager.COLUMN_STATUS,
                DownloadManager.COLUMN_REASON,
            ),
        ).apply { addRow(arrayOf<Any>(100L, 100L, status, 0)) }
}
