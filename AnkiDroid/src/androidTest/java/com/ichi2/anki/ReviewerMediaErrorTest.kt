// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import android.content.Intent
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import com.ichi2.anki.pages.AnkiServer.Companion.LOCALHOST
import com.ichi2.anki.tests.InstrumentedTest
import com.ichi2.anki.tests.checkWithTimeout
import com.ichi2.anki.testutil.AvoidDayRolloverRule
import com.ichi2.anki.testutil.GrantStoragePermission.storagePermission
import com.ichi2.anki.testutil.ResetMediaErrorHandlerRule
import com.ichi2.anki.testutil.ensureWebViewIsSupported
import com.ichi2.anki.testutil.grantPermissions
import com.ichi2.anki.testutil.notificationPermission
import com.ichi2.anki.testutil.waitUntil
import fi.iki.elonen.NanoHTTPD
import org.hamcrest.Matchers.allOf
import org.junit.Rule
import org.junit.Test
import java.net.InetSocketAddress
import java.net.Socket
import java.util.UUID
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.assertEquals
import com.google.android.material.R as MaterialR

class ReviewerMediaErrorTest : InstrumentedTest() {
    @get:Rule(order = 0)
    val avoidDayRollover = AvoidDayRolloverRule()

    @get:Rule(order = 1)
    val resetMediaErrorHandler = ResetMediaErrorHandlerRule()

    @get:Rule
    val runtimePermissionRule = grantPermissions(storagePermission, notificationPermission)

    @Test
    fun apiHttpFailureDoesNotHideMissingMediaSnackbar() {
        assertApiFailureDoesNotHideMissingMediaSnackbar()
    }

    @Test
    fun apiTransportFailureDoesNotHideMissingMediaSnackbar() {
        // Reserve a port without listening, so connections are refused and no server can claim it.
        Socket().use { socket ->
            socket.bind(InetSocketAddress(LOCALHOST, 0))
            assertApiFailureDoesNotHideMissingMediaSnackbar(
                apiBaseUrl = "http://$LOCALHOST:${socket.localPort}",
                expectedApiErrorCode = WebViewClient.ERROR_CONNECT,
            )
        }
    }

    private fun assertApiFailureDoesNotHideMissingMediaSnackbar(
        apiBaseUrl: String? = null,
        expectedApiErrorCode: Int = 500,
    ) {
        withErrorServer { mediaBaseUrl ->
            withReviewer { scenario ->
                val errors = scenario.recordWebViewErrors()
                scenario.evaluateJavascript(
                    "fetch('${apiBaseUrl ?: mediaBaseUrl}/jsapi/cardDue', {method: 'POST'}).catch(() => {});",
                )
                assertEquals("/jsapi/cardDue" to expectedApiErrorCode, errors.poll(10, TimeUnit.SECONDS))
                onView(withId(MaterialR.id.snackbar_text)).check(doesNotExist())

                scenario.assertMissingImageIsReported(mediaBaseUrl, errors)
            }
        }
    }

    private fun withReviewer(block: (ActivityScenario<Reviewer>) -> Unit) {
        ensureWebViewIsSupported()
        val originalDeckId = col.decks.selected()
        val deckId = col.decks.addNormalDeckWithName("ReviewerMediaErrorTest-${UUID.randomUUID()}").id
        try {
            col.decks.select(deckId)
            addNoteUsingBasicNoteType("Media error integration test").firstCard(col).update { did = deckId }
            // Launch the legacy reviewer explicitly: the new study screen does not implement this API.
            ActivityScenario.launch<Reviewer>(Intent(testContext, Reviewer::class.java)).use { scenario ->
                scenario.awaitCardLoaded()
                block(scenario)
            }
        } finally {
            col.decks.remove(listOf(deckId))
            col.decks.select(originalDeckId)
        }
    }

    private fun withErrorServer(block: (baseUrl: String) -> Unit) {
        val server =
            object : NanoHTTPD(LOCALHOST, 0) {
                override fun serve(session: IHTTPSession): Response {
                    val status =
                        when {
                            session.method == Method.OPTIONS -> Response.Status.OK
                            session.uri == "/jsapi/cardDue" -> Response.Status.INTERNAL_ERROR
                            else -> Response.Status.NOT_FOUND
                        }
                    return newFixedLengthResponse(status, "text/plain", "Deliberate test failure").apply {
                        addHeader("Access-Control-Allow-Origin", "*")
                    }
                }
            }
        try {
            server.start()
            block("http://$LOCALHOST:${server.listeningPort}")
        } finally {
            server.stop()
        }
    }

    private fun ActivityScenario<Reviewer>.awaitCardLoaded() {
        val ready = AtomicBoolean(false)
        waitUntil(message = { "Reviewer card did not finish loading" }) {
            onActivity { reviewer ->
                reviewer.webView?.evaluateJavascript(
                    "document.readyState === 'complete' && document.body.innerText.includes('Media error integration test')",
                ) { ready.set(it == "true") }
            }
            ready.get()
        }
    }

    private fun ActivityScenario<Reviewer>.recordWebViewErrors(): LinkedBlockingQueue<Pair<String?, Int>> {
        val errors = LinkedBlockingQueue<Pair<String?, Int>>()
        onActivity { reviewer ->
            val client = checkNotNull(reviewer.webViewClient)
            checkNotNull(reviewer.webView).webViewClient =
                object : WebViewClient() {
                    override fun shouldInterceptRequest(
                        view: WebView,
                        request: WebResourceRequest,
                    ): WebResourceResponse? = client.shouldInterceptRequest(view, request)

                    override fun onReceivedError(
                        view: WebView,
                        request: WebResourceRequest,
                        error: WebResourceError,
                    ) {
                        client.onReceivedError(view, request, error)
                        errors.add(request.url.path to error.errorCode)
                    }

                    override fun onReceivedHttpError(
                        view: WebView,
                        request: WebResourceRequest,
                        errorResponse: WebResourceResponse,
                    ) {
                        // Exercise the production callback before signaling the instrumentation thread.
                        client.onReceivedHttpError(view, request, errorResponse)
                        errors.add(request.url.path to errorResponse.statusCode)
                    }
                }
        }
        return errors
    }

    private fun ActivityScenario<Reviewer>.evaluateJavascript(script: String) {
        onActivity { reviewer ->
            checkNotNull(reviewer.webView).evaluateJavascript(script, null)
        }
    }

    private fun ActivityScenario<Reviewer>.assertMissingImageIsReported(
        baseUrl: String,
        errors: LinkedBlockingQueue<Pair<String?, Int>>,
    ) {
        val missingImage = "missing-${UUID.randomUUID()}.png"
        evaluateJavascript("const image = new Image(); image.src = '$baseUrl/$missingImage'; document.body.appendChild(image);")
        assertEquals("/$missingImage" to 404, errors.poll(10, TimeUnit.SECONDS))
        onView(withId(MaterialR.id.snackbar_text)).checkWithTimeout(
            matches(
                allOf(
                    isDisplayed(),
                    withText(testContext.getString(CommonString.card_viewer_could_not_find_image, missingImage)),
                ),
            ),
        )
    }
}
