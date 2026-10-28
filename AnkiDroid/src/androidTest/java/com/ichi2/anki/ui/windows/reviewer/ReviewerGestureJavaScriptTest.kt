// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.ui.windows.reviewer

import android.view.ViewGroup
import android.webkit.WebView
import androidx.fragment.app.Fragment
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.SingleFragmentActivity
import com.ichi2.anki.pages.AnkiServer
import com.ichi2.anki.pages.PostRequestHandler
import com.ichi2.anki.pages.PostRequestUri
import com.ichi2.anki.tests.InstrumentedTest
import com.ichi2.anki.testutil.GrantStoragePermission.storagePermission
import com.ichi2.anki.testutil.awaitJavascript
import com.ichi2.anki.testutil.ensureWebViewIsSupported
import com.ichi2.anki.testutil.evaluate
import com.ichi2.anki.testutil.grantPermissions
import com.ichi2.anki.testutil.notificationPermission
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals

/** Executes the production touch listeners and requests in a real WebView. */
@RunWith(AndroidJUnit4::class)
class ReviewerGestureJavaScriptTest : InstrumentedTest() {
    @get:Rule
    val runtimePermissionRule = grantPermissions(storagePermission, notificationPermission)

    @Test
    fun cardBaseDoesNotRedirectGestureRequests() = checkCardBaseDoesNotRedirectGestureRequests(base = "/media/")

    @Test
    fun crossOriginCardBaseDoesNotRedirectGestureRequests() = checkCardBaseDoesNotRedirectGestureRequests(base = "https://example.invalid/")

    private fun checkCardBaseDoesNotRedirectGestureRequests(base: String) {
        val requests = LinkedBlockingQueue<String>()
        // Accept every path so a wrong destination fails with its actual URL, rather than a timeout.
        val handler = recordingHandler { uri -> requests.add(uri) }
        withGesturePage(handler) {
            // Prove the production scripts and localhost transport work before changing the base.
            sendTap()
            assertEquals("/ankidroid/tapOrSwipe", requests.poll(5, TimeUnit.SECONDS))

            evaluate("document.getElementById('qa').insertAdjacentHTML('afterbegin', '<base href=\"$base\">');")
            sendTap()
            assertEquals(
                "/ankidroid/tapOrSwipe",
                requests.poll(5, TimeUnit.SECONDS),
                "Card content must not change the destination of native gesture requests",
            )
        }
    }

    private fun withGesturePage(
        handler: PostRequestHandler,
        block: WebView.() -> Unit,
    ) {
        ensureWebViewIsSupported()
        val server = AnkiServer(handler).also { it.start() }
        val intent = SingleFragmentActivity.getIntent(testContext, Fragment::class)
        try {
            ActivityScenario.launch<SingleFragmentActivity>(intent).use { scenario ->
                lateinit var webView: WebView
                scenario.onActivity { webView = WebView(it) }
                try {
                    scenario.onActivity { activity ->
                        activity.setContentView(webView)
                        webView.settings.javaScriptEnabled = true
                        webView.settings.allowFileAccess = true
                        webView.loadDataWithBaseURL(
                            server.baseUrl(),
                            """
                            <!DOCTYPE html>
                            <html><body>
                            <div id="qa"><div id="card">Gesture target</div></div>
                            <script src="file:///android_asset/scripts/ankidroid-cardviewer.js"></script>
                            <script src="file:///android_asset/scripts/ankidroid-reviewer.js"></script>
                            </body></html>
                            """.trimIndent(),
                            "text/html",
                            null,
                            null,
                        )
                    }
                    // The initial about:blank page is also 'complete': wait for the gesture page itself
                    webView.awaitJavascript("document.readyState === 'complete' && document.getElementById('card') !== null")
                    webView.block()
                } finally {
                    scenario.onActivity {
                        (webView.parent as? ViewGroup)?.removeView(webView)
                        webView.destroy()
                    }
                }
            }
        } finally {
            server.stop()
        }
    }

    private fun recordingHandler(onRequest: (uri: String) -> Unit) =
        object : PostRequestHandler {
            override suspend fun handlePostRequest(
                uri: PostRequestUri,
                bytes: ByteArray,
            ): ByteArray {
                onRequest(uri.uri)
                return byteArrayOf()
            }
        }

    private fun WebView.sendTap() {
        evaluate(
            """
            (() => {
                const target = document.getElementById('card');
                const touch = new Touch({identifier: 1, target, clientX: 50, clientY: 50});
                target.dispatchEvent(new TouchEvent('touchstart', {
                    bubbles: true, touches: [touch], changedTouches: [touch]
                }));
                target.dispatchEvent(new TouchEvent('touchend', {
                    bubbles: true, touches: [], changedTouches: [touch]
                }));
            })();
            """.trimIndent(),
        )
    }
}
