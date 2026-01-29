package com.ichi2.anki

import android.os.Looper
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebViewClient
import com.ichi2.anki.databinding.ActivitySharedDecksBinding
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows

@RunWith(RobolectricTestRunner::class)
class SharedDecksActivityTest {
    private lateinit var activity: SharedDecksActivity
    private lateinit var binding: ActivitySharedDecksBinding
    private lateinit var webViewClient: WebViewClient
    private lateinit var cookieManager: CookieManager

    @Before
    fun setUp() {
        activity =
            Robolectric
                .buildActivity(SharedDecksActivity::class.java)
                .create()
                .start()
                .visible()
                .get()

        binding = ActivitySharedDecksBinding.bind(Shadows.shadowOf(activity).contentView)
        webViewClient = activity.webViewClient
        cookieManager = CookieManager.getInstance()
    }

    @Test
    fun `when user is logged in and receives HTTP 429, should not redirect to login page`() {
        setAnkiWebLoggedIn()

        // Verify cookie was actually set
        val cookies = cookieManager.getCookie("https://ankiweb.net")
        assertTrue("Login cookie not set", cookies?.contains("has_auth=1") == true)

        val initialUrl = activity.getString(R.string.shared_decks_url)

        triggerHttpError(429)

        // Ensure authenticated users stay on the current page even when rate-limited
        val currentUrl = Shadows.shadowOf(binding.webView).lastLoadedUrl

        assertEquals("User should stay on the content page", initialUrl, currentUrl)
    }

    @Test
    fun `when user is not logged in and receives HTTP 429, should redirect to login page`() {
        clearAnkiWebCookies()

        // Verify cookies were actually cleared
        val cookies = cookieManager.getCookie("https://ankiweb.net")
        assertTrue("Cookies not cleared", cookies == null || !cookies.contains("has_auth=1"))

        triggerHttpError(429)

        // Redirect unauthenticated users to the login page on rate-limiting
        val expectedUrl = activity.getString(R.string.shared_decks_login_url)
        val actualUrl = Shadows.shadowOf(binding.webView).lastLoadedUrl

        assertEquals(expectedUrl, actualUrl)
    }

    @Test
    fun `should stop redirecting after 3 attempts`() {
        clearAnkiWebCookies()

        val loginUrl = activity.getString(R.string.shared_decks_login_url)
        val contentUrl = "https://ankiweb.net/shared/decks/test"

        repeat(3) {
            binding.webView.loadUrl(contentUrl)
            triggerHttpError(429)
            assertEquals("Redirect should happen within limit", loginUrl, Shadows.shadowOf(binding.webView).lastLoadedUrl)
        }

        binding.webView.loadUrl(contentUrl)
        triggerHttpError(429)

        assertEquals("Redirect should STOP after limit", contentUrl, Shadows.shadowOf(binding.webView).lastLoadedUrl)
    }

    @After
    fun tearDown() {
        clearAnkiWebCookies()
    }

    private fun clearAnkiWebCookies() {
        cookieManager.removeAllCookies(null)
        cookieManager.flush()
        // Ensure async operations complete in Robolectric
        Shadows.shadowOf(Looper.getMainLooper()).idle()
    }

    private fun setAnkiWebLoggedIn() {
        cookieManager.setCookie("https://ankiweb.net", "has_auth=1")
        cookieManager.flush()
        // Ensure async operations complete in Robolectric
        Shadows.shadowOf(Looper.getMainLooper()).idle()
    }

    private fun triggerHttpError(statusCode: Int) {
        val mockRequest =
            mock<WebResourceRequest> {
                on { isForMainFrame } doReturn true
            }
        val mockErrorResponse =
            mock<WebResourceResponse> {
                on { this.statusCode } doReturn statusCode
            }
        webViewClient.onReceivedHttpError(binding.webView, mockRequest, mockErrorResponse)
    }
}
