// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import android.app.DownloadManager
import android.content.Intent
import android.database.Cursor
import android.database.MatrixCursor
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsCompat.Type.displayCutout
import androidx.core.view.WindowInsetsCompat.Type.navigationBars
import androidx.core.view.WindowInsetsCompat.Type.statusBars
import androidx.fragment.app.commit
import com.ichi2.testutils.insetsOf
import com.ichi2.utils.dp
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.robolectric.RuntimeEnvironment

/**
 * Screenshot tests for [SharedDecksActivity]
 *
 * `./gradlew :AnkiDroid:verifyRoborazziPlayDebug -Pscreenshot --tests "com.ichi2.anki.SharedDecksScreenshotTest"`
 */
class SharedDecksScreenshotTest : ScreenshotTest() {
    @Test
    fun sharedDecksPortrait() =
        withSharedDecks { activity ->
            activity.simulateNavigationBar()
            captureScreen("portrait")
        }

    /**
     * Landscape with 3-button navigation: the navigation bar is a side inset and the camera
     * cutout is on the opposite side. The toolbar and content clear both sides.
     */
    @Test
    fun sharedDecksLandscape() {
        RuntimeEnvironment.setQualifiers("+land")
        withSharedDecks { activity ->
            activity.simulateSideNavigationBar()
            captureScreen("landscape")
        }
    }

    /** [SharedDecksDownloadFragment]: a full-screen overlay above the WebView */
    @Test
    fun downloadFragment() =
        withSharedDecks { activity ->
            // show the fragment first: insets are only received by attached views
            activity.showDownloadFragment()
            activity.simulateNavigationBar()
            captureScreen("download")
        }

    private fun withSharedDecks(block: (SharedDecksActivity) -> Unit) {
        val activity =
            startActivityNormallyOpenCollectionWithIntent(
                SharedDecksActivity::class.java,
                Intent(),
            )
        advanceRobolectricLooper()
        block(activity)
    }

    /**
     * Shows [SharedDecksDownloadFragment] as the activity's download listener would,
     * with a mocked [DownloadManager] reporting a stable in-progress download
     */
    private fun SharedDecksActivity.showDownloadFragment() {
        downloadManager =
            mock {
                on { enqueue(any()) } doReturn 1L
                on { query(any()) } doAnswer { inProgressDownloadCursor() }
            }
        val fragment =
            SharedDecksDownloadFragment().apply {
                arguments =
                    Bundle().apply {
                        putSerializable(
                            SharedDecksActivity.DOWNLOAD_FILE,
                            DownloadFile(
                                url = "https://ankiweb.net/svc/shared/download-deck/1104981491?t=token",
                                userAgent = "Mozilla/5.0",
                                contentDisposition = "attachment; filename=\"MyTestDeck.apkg\"",
                                mimeType = "application/octet-stream",
                            ),
                        )
                    }
            }
        supportFragmentManager.commit {
            add(
                R.id.shared_decks_fragment_container,
                fragment,
                SharedDecksActivity.SHARED_DECKS_DOWNLOAD_FRAGMENT,
            )
        }
        advanceRobolectricLooper()
    }

    /** A download which is 30% complete */
    private fun inProgressDownloadCursor(): Cursor =
        MatrixCursor(
            arrayOf(
                DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR,
                DownloadManager.COLUMN_TOTAL_SIZE_BYTES,
                DownloadManager.COLUMN_STATUS,
                DownloadManager.COLUMN_REASON,
            ),
        ).apply {
            addRow(arrayOf<Any>(3_000_000L, 10_000_000L, DownloadManager.STATUS_RUNNING, 0))
        }

    /**
     * Robolectric reports zero system-bar insets by default. Inject realistic ones so the app's
     * edge-to-edge layout responds as it would on a real device, and overlay a translucent band
     * where the nav bar would sit to see if content is drawn underneath it.
     */
    private fun SharedDecksActivity.simulateNavigationBar() {
        val navBarHeight = 48.dp
        val insets =
            with(targetContext) {
                WindowInsetsCompat
                    .Builder()
                    .setInsets(statusBars(), insetsOf(top = 24.dp))
                    .setInsets(navigationBars(), insetsOf(bottom = navBarHeight))
                    .build()
            }
        ViewCompat.dispatchApplyWindowInsets(window.decorView, insets)

        val decor = window.decorView as ViewGroup
        val navBarOverlay =
            View(this).apply {
                setBackgroundColor(0x80000000.toInt())
            }
        decor.addView(
            navBarOverlay,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                navBarHeight.toPx(targetContext),
                Gravity.BOTTOM,
            ),
        )
    }

    /**
     * As [simulateNavigationBar], but for landscape with 3-button navigation: the navigation bar
     * is a side inset, with the camera cutout on the opposite side.
     */
    private fun SharedDecksActivity.simulateSideNavigationBar() {
        val navBarWidth = 48.dp
        val insets =
            with(targetContext) {
                WindowInsetsCompat
                    .Builder()
                    .setInsets(statusBars(), insetsOf(top = 24.dp))
                    .setInsets(navigationBars(), insetsOf(right = navBarWidth))
                    .setInsets(displayCutout(), insetsOf(left = 32.dp))
                    .build()
            }
        ViewCompat.dispatchApplyWindowInsets(window.decorView, insets)

        val decor = window.decorView as ViewGroup
        val navBarOverlay =
            View(this).apply {
                setBackgroundColor(0x80000000.toInt())
            }
        decor.addView(
            navBarOverlay,
            FrameLayout.LayoutParams(
                navBarWidth.toPx(targetContext),
                FrameLayout.LayoutParams.MATCH_PARENT,
                Gravity.END,
            ),
        )
    }
}
