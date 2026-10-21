// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2026 YongWoo Shin <onlym6659@gmail.com>

package com.ichi2.anki.shareddeck

import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebResourceResponse
import android.webkit.WebView
import androidx.appcompat.app.AlertDialog
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.CommonString
import com.ichi2.anki.R
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.dialogs.utils.message
import com.ichi2.anki.dialogs.utils.title
import com.ichi2.anki.shareddeck.SharedDecksActivity.Companion.HTTP_STATUS_TOO_MANY_REQUESTS
import com.ichi2.utils.neutralButton
import com.ichi2.utils.positiveButton
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.robolectric.Robolectric
import org.robolectric.Shadows
import org.robolectric.fakes.RoboMenuItem
import org.robolectric.shadows.ShadowDialog
import timber.log.Timber
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Tests for [SharedDecksActivity] */
@RunWith(AndroidJUnit4::class)
class SharedDecksActivityTest : RobolectricTest() {
    @Test
    fun `rate limit while logged out asks to log in or sign up`() {
        val activity = startSharedDecks()

        activity.receiveTooManyRequests()

        val dialog = ShadowDialog.getLatestDialog() as AlertDialog
        assertEquals(getResourceString(CommonString.not_logged_in_title), dialog.title)
        assertEquals(getResourceString(CommonString.shared_decks_ankiweb_login_limit), dialog.message)
        assertEquals(getResourceString(R.string.shared_decks_url), activity.lastLoadedUrl)
    }

    @Test
    fun `log in from the rate limit dialog opens the AnkiWeb login page`() {
        val activity = startSharedDecks()
        activity.receiveTooManyRequests()

        clickAlertDialogButton { positiveButton }

        assertEquals(getResourceString(R.string.shared_decks_login_url), activity.lastLoadedUrl)
    }

    @Test
    fun `sign up from the rate limit dialog opens the AnkiWeb sign up page`() {
        val activity = startSharedDecks()
        activity.receiveTooManyRequests()

        clickAlertDialogButton { requireNotNull(neutralButton) }

        assertEquals(getResourceString(R.string.shared_decks_sign_up_url), activity.lastLoadedUrl)
    }

    @Test
    fun `repeated rate limits show a single dialog`() {
        val activity = startSharedDecks()

        activity.receiveTooManyRequests()
        activity.receiveTooManyRequests()

        assertEquals(1, ShadowDialog.getShownDialogs().size)
    }

    @Test
    fun `rate limit while logged in to AnkiWeb leaves the page alone`() {
        CookieManager.getInstance().setCookie("https://ankiweb.net", "has_auth=1")
        val activity = startSharedDecks()

        activity.receiveTooManyRequests()

        assertNull(ShadowDialog.getLatestDialog())
        assertEquals(getResourceString(R.string.shared_decks_url), activity.lastLoadedUrl)
    }

    @Test
    fun `logging in to AnkiWeb returns to the shared deck`() {
        val activity = startOnSharedDeck()

        activity.logInToAnkiWeb()

        assertEquals(SHARED_DECK, activity.webView.url)
    }

    @Test
    fun `back after logging in to AnkiWeb returns to the search results`() {
        val searchResults = "https://ankiweb.net/shared/decks/Japanese"
        val activity = startSharedDecks()
        activity.visit(searchResults)
        activity.visit(SHARED_DECK)

        activity.logInToAnkiWeb()

        activity.visit(SHARED_DECK)
        assertEquals(SHARED_DECK, activity.webView.url)

        activity.onBackPressedDispatcher.onBackPressed()

        assertFalse(activity.isFinishing, "Back should return to the search results instead of closing the screen")
        assertEquals(searchResults, activity.webView.url)
    }

    @Test
    fun `back after logging in to AnkiWeb returns to each earlier shared page once`() {
        val sharedDecks = getResourceString(R.string.shared_decks_url)
        val searchResults = "https://ankiweb.net/shared/decks/Japanese"
        val activity = startSharedDecks()
        activity.visit(sharedDecks)
        activity.visit(searchResults)
        activity.visit(SHARED_DECK)
        activity.logInToAnkiWeb()
        activity.visit(AFTER_LOGIN)
        activity.visit(SHARED_DECK)

        activity.pressBack()
        assertEquals(searchResults, activity.webView.url)
        activity.pressBack()
        assertEquals(sharedDecks, activity.webView.url)
        activity.pressBack()
        assertTrue(activity.isFinishing)
    }

    @Test
    fun `back from home after logging in to AnkiWeb closes the screen`() {
        val activity = startOnSharedDeck()
        activity.logInToAnkiWeb()
        activity.visit(SHARED_DECK)

        activity.onOptionsItemSelected(RoboMenuItem(R.id.home))
        activity.visit(getResourceString(R.string.shared_decks_url))
        activity.pressBack()

        assertTrue(activity.isFinishing)
    }

    @Test
    fun `logging in after AnkiWeb refuses a download returns to the deck page`() {
        val activity = startOnSharedDeck()
        activity.visit(DECK_DOWNLOAD)

        activity.logInToAnkiWeb()

        assertEquals(SHARED_DECK, activity.webView.url)
    }

    @Test
    fun `AnkiWeb login without a session stays on AnkiWeb`() {
        val activity = startOnSharedDeck()
        activity.openAnkiWebLogin()

        activity.visit(AFTER_LOGIN)

        assertEquals(AFTER_LOGIN, activity.webView.url)
    }

    @Test
    fun `back while returning after login must not reopen login`() {
        val activity = startOnSharedDeck()
        activity.logInToAnkiWeb()
        // loadUrl was requested, but the return navigation has not committed yet.
        activity.onBackPressedDispatcher.onBackPressed()
        assertFalse(
            activity.webView.url?.startsWith(getResourceString(R.string.shared_decks_login_url)) == true,
            "Back reopened the login page while the return to the deck was pending",
        )
    }

    @Test
    fun `returning after a login does not log the deck URL at info`() {
        val activity = startOnSharedDeck()

        val logs = captureInfoLogs { activity.logInToAnkiWeb() }

        assertEquals(SHARED_DECK, activity.webView.url)
        assertFalse(logs.any { it.contains(SHARED_DECK) }, "Deck URL was logged at info or above: $logs")
    }

    private fun startOnSharedDeck(): SharedDecksActivity =
        startSharedDecks().apply {
            visit(getResourceString(R.string.shared_decks_url))
            visit(SHARED_DECK)
        }

    private fun SharedDecksActivity.openAnkiWebLogin() {
        receiveTooManyRequests()
        clickAlertDialogButton { positiveButton }
        visit(getResourceString(R.string.shared_decks_login_url))
    }

    private fun SharedDecksActivity.logInToAnkiWeb() {
        openAnkiWebLogin()
        CookieManager.getInstance().setCookie("https://ankiweb.net", "has_auth=1")
        visit(AFTER_LOGIN)
    }

    private fun SharedDecksActivity.visit(url: String) {
        if (webView.copyBackForwardList().currentItem?.url != url) {
            Shadows.shadowOf(webView).pushEntryToHistory(url)
        }
        webViewClient.doUpdateVisitedHistory(webView, url, false)
    }

    private fun SharedDecksActivity.pressBack() {
        onBackPressedDispatcher.onBackPressed()
        if (!isFinishing) visit(requireNotNull(webView.url))
    }

    private fun startSharedDecks(): SharedDecksActivity {
        val controller =
            Robolectric
                .buildActivity(SharedDecksActivity::class.java)
                .create()
                .start()
                .resume()
                .visible()
        saveControllerForCleanup(controller)
        return controller.get()
    }

    private fun captureInfoLogs(block: () -> Unit): List<String> {
        val messages = mutableListOf<String>()
        val tree =
            object : Timber.Tree() {
                override fun log(
                    priority: Int,
                    tag: String?,
                    message: String,
                    t: Throwable?,
                ) {
                    if (priority >= Log.INFO) messages += message
                }
            }
        Timber.plant(tree)
        try {
            block()
        } finally {
            Timber.uproot(tree)
        }
        return messages
    }

    private fun SharedDecksActivity.receiveTooManyRequests() {
        val response = mock<WebResourceResponse> { on { statusCode } doReturn HTTP_STATUS_TOO_MANY_REQUESTS }
        webViewClient.onReceivedHttpError(webView, mock(), response)
    }

    private val SharedDecksActivity.webView: WebView
        get() = findViewById(R.id.web_view)

    private val SharedDecksActivity.lastLoadedUrl: String?
        get() = Shadows.shadowOf(webView).lastLoadedUrl

    companion object {
        private const val SHARED_DECK = "https://ankiweb.net/shared/info/1234"
        private const val DECK_DOWNLOAD = "https://ankiweb.net/svc/shared/download-deck/1234?t=key"
        private const val AFTER_LOGIN = "https://ankiweb.net/account/login?afterAuth=1"
    }
}
