// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.previewer

import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebView
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import com.ichi2.anki.R
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.browser.IdsFile
import com.ichi2.anki.settings.Prefs
import com.ichi2.testutils.assertNoActivityStarted
import com.ichi2.testutils.assertOpenedUrl
import com.ichi2.testutils.createTransientDirectory
import com.ichi2.testutils.mockWebResourceRequest
import com.ichi2.testutils.registerWebBrowser
import com.ichi2.utils.negativeButton
import com.ichi2.utils.positiveButton
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.kotlin.any
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowDialog

@SdkSuppress(minSdkVersion = Build.VERSION_CODES.O) // WebChromeClient
@RunWith(AndroidJUnit4::class)
class CardViewerFragmentTest : RobolectricTest() {
    // Changing the selected card renders its media controls, which requires a media folder.
    override fun getCollectionStorageMode() = CollectionStorageMode.IN_MEMORY_WITH_MEDIA

    @Test
    fun `web links open with external app permission disabled`() {
        editPreferences { putBoolean(targetContext.getString(R.string.pref_allow_card_external_launch_key), false) }
        targetContext.registerWebBrowser()
        withCardViewerWebView { webView ->
            for (scheme in listOf("http", "https")) {
                val url = "$scheme://github.com/ankidroid/Anki-Android/releases"
                shadowOf(webView).webViewClient.shouldOverrideUrlLoading(webView, mockWebResourceRequest(url))
                targetContext.assertOpenedUrl(url.toUri())
            }
        }
    }

    @Test
    fun `custom schemes and parsed intents still require external app permission`() {
        editPreferences { putBoolean(targetContext.getString(R.string.pref_allow_card_external_launch_key), false) }
        targetContext.registerWebBrowser()
        withCardViewerWebView { webView ->
            val urls =
                listOf(
                    "market://details?id=com.example",
                    "intent://example.org/#Intent;scheme=https;end",
                    "android-app://com.example/https/example.org",
                )
            for (url in urls) {
                shadowOf(webView).webViewClient.shouldOverrideUrlLoading(webView, mockWebResourceRequest(url))
                targetContext.assertNoActivityStarted(url)
            }
        }
    }

    @Test
    fun `blocked launch warnings do not stack`() {
        editPreferences { putBoolean(targetContext.getString(R.string.pref_allow_card_external_launch_key), false) }
        targetContext.registerWebBrowser()
        withCardViewerWebView { webView ->
            repeat(3) {
                shadowOf(webView).webViewClient.shouldOverrideUrlLoading(
                    webView,
                    mockWebResourceRequest("intent://example.org/#Intent;scheme=https;end"),
                )
                advanceRobolectricLooper()
            }
            assertThat(
                "blocked navigation must not stack modal warnings over the card",
                ShadowDialog.getShownDialogs().size,
                equalTo(1),
            )
        }
    }

    @Test
    fun `dismissing the launch warning stops it reopening for repeated launches`() {
        editPreferences { putBoolean(targetContext.getString(R.string.pref_allow_card_external_launch_key), false) }
        targetContext.registerWebBrowser()
        withCardViewerWebView { webView ->
            val url = "intent://example.org/#Intent;scheme=https;end"
            shadowOf(webView).webViewClient.shouldOverrideUrlLoading(webView, mockWebResourceRequest(url))
            advanceRobolectricLooper()
            assertThat(ShadowDialog.getShownDialogs().size, equalTo(1))

            ShadowDialog.getLatestDialog().cancel()
            advanceRobolectricLooper()

            shadowOf(webView).webViewClient.shouldOverrideUrlLoading(webView, mockWebResourceRequest(url))
            advanceRobolectricLooper()
            assertThat(
                "a dismissed warning must not reopen while the same card is displayed",
                ShadowDialog.getShownDialogs().size,
                equalTo(1),
            )
        }
    }

    @Test
    fun `launch warning can be shown again after previewing a different card`() {
        editPreferences { putBoolean(targetContext.getString(R.string.pref_allow_card_external_launch_key), false) }
        targetContext.registerWebBrowser()

        // The previewer contains two cards from the reversed note. The warning must be scoped to
        // the card that triggered it, rather than suppressing all later cards in this activity.
        val note = addBasicAndReversedNote()
        val intent =
            PreviewerFragment.getIntent(
                targetContext,
                idsFile = IdsFile(createTransientDirectory(), note.cardIds(col), IdsFile.Purpose.PREVIEW),
                currentIndex = 0,
            )

        ActivityScenario.launch<CardViewerActivity>(intent).use { scenario ->
            scenario.moveToState(Lifecycle.State.RESUMED)
            scenario.onActivity { activity ->
                val webViewLayout = activity.findViewById<ViewGroup>(R.id.web_view_layout)
                val webView =
                    (0 until webViewLayout.childCount)
                        .map { webViewLayout.getChildAt(it) }
                        .filterIsInstance<WebView>()
                        .single()
                val nextButton = activity.findViewById<View>(R.id.next_button)

                // Trigger and dismiss the warning for the first card. The dismissal intentionally
                // enables suppression until the fragment reports a card change.
                shadowOf(webView).webViewClient.shouldOverrideUrlLoading(
                    webView,
                    mockWebResourceRequest("market://details?id=com.example"),
                )
                advanceRobolectricLooper()
                assertThat(ShadowDialog.getShownDialogs().count { it.isShowing }, equalTo(1))
                ShadowDialog.getLatestDialog().cancel()
                advanceRobolectricLooper()

                // Move from the first card's question to answer, then to the second card.
                // The navigation runs on a background dispatcher: wait for each step to land,
                // otherwise the second click only repeats the first one.
                val previewer = activity.fragment as PreviewerFragment
                nextButton.performClick()
                advanceRobolectricLooperUntil { previewer.viewModel.showingAnswer.value }
                nextButton.performClick()
                advanceRobolectricLooperUntil { previewer.viewModel.currentIndex.value == 1 }
                advanceRobolectricLooper()

                // This assertion fails on the reviewed commit because PreviewerFragment never
                // resets suppression when PreviewerViewModel changes currentIndex.
                shadowOf(webView).webViewClient.shouldOverrideUrlLoading(
                    webView,
                    mockWebResourceRequest("market://details?id=com.example"),
                )
                advanceRobolectricLooper()
                assertThat(
                    "a new preview card must be allowed to show its own launch warning",
                    ShadowDialog.getShownDialogs().count { it.isShowing },
                    equalTo(1),
                )
            }
        }
    }

    @Test
    fun `dismissing the opt-in dialog denies the audio capture request`() {
        grantRecordAudioPermission()
        Prefs.allowTemplatesToRecordAudio = false
        val request = audioCaptureRequest()

        withCardViewerChromeClient { chromeClient ->
            chromeClient.onPermissionRequest(request)
            ShadowDialog.getLatestDialog().cancel()
            advanceRobolectricLooper()

            verify(request).deny()
            verify(request, never()).grant(any())
        }
    }

    @Test
    fun `audio capture requests are denied while an opt-in dialog is open`() {
        grantRecordAudioPermission()
        Prefs.allowTemplatesToRecordAudio = false
        val first = audioCaptureRequest()
        val second = audioCaptureRequest()

        withCardViewerChromeClient { chromeClient ->
            chromeClient.onPermissionRequest(first)
            chromeClient.onPermissionRequest(second)

            verify(second).deny()
            clickAlertDialogButton { positiveButton }
            verify(first).grant(arrayOf(PermissionRequest.RESOURCE_AUDIO_CAPTURE))
            verify(second, never()).grant(any())
        }
    }

    @Test
    fun `declining the opt-in denies later requests without re-prompting`() {
        grantRecordAudioPermission()
        Prefs.allowTemplatesToRecordAudio = false
        val first = audioCaptureRequest()
        val second = audioCaptureRequest()

        withCardViewerChromeClient { chromeClient ->
            chromeClient.onPermissionRequest(first)
            clickAlertDialogButton { negativeButton }
            verify(first).deny()

            val dialogCount = ShadowDialog.getShownDialogs().size
            chromeClient.onPermissionRequest(second)

            verify(second).deny()
            assertThat("the user is not prompted again", ShadowDialog.getShownDialogs().size, equalTo(dialogCount))
        }
    }

    @Test
    fun `cancelled audio capture requests close the opt-in dialog`() {
        grantRecordAudioPermission()
        Prefs.allowTemplatesToRecordAudio = false
        val request = audioCaptureRequest()

        withCardViewerChromeClient { chromeClient ->
            chromeClient.onPermissionRequest(request)
            chromeClient.onPermissionRequestCanceled(request)
            advanceRobolectricLooper()

            assertThat(
                "the opt-in dialog is dismissed with its request",
                shadowOf(ShadowDialog.getLatestDialog()).hasBeenDismissed(),
                equalTo(true),
            )
            verify(request, never()).grant(any())
            assertThat("no opt-in is recorded", Prefs.allowTemplatesToRecordAudio, equalTo(false))
        }
    }

    private fun audioCaptureRequest(): PermissionRequest =
        mock(PermissionRequest::class.java).also {
            whenever(it.resources).thenReturn(arrayOf(PermissionRequest.RESOURCE_AUDIO_CAPTURE))
        }

    /** Runs [block] on the [WebChromeClient] attached to an open previewer's WebView */
    private fun withCardViewerChromeClient(block: (WebChromeClient) -> Unit) =
        withCardViewerWebView { webView ->
            block(requireNotNull(webView.webChromeClient) { "no WebChromeClient attached" })
        }

    private fun withCardViewerWebView(block: (WebView) -> Unit) {
        val note = addBasicAndReversedNote()
        val intent =
            PreviewerFragment.getIntent(
                targetContext,
                idsFile = IdsFile(createTransientDirectory(), note.cardIds(col), IdsFile.Purpose.PREVIEW),
                currentIndex = 0,
            )

        ActivityScenario.launch<CardViewerActivity>(intent).use { scenario ->
            scenario.moveToState(Lifecycle.State.RESUMED)
            scenario.onActivity { activity ->
                val webViewLayout = activity.findViewById<ViewGroup>(R.id.web_view_layout)
                val webView =
                    (0 until webViewLayout.childCount)
                        .map { webViewLayout.getChildAt(it) }
                        .filterIsInstance<WebView>()
                        .single()
                block(webView)
            }
        }
    }

    /** [Prefs.allowTemplatesToRecordAudio] is process-wide state: reset it between tests */
    @After
    fun resetTemplateAudioOptIn() {
        Prefs.allowTemplatesToRecordAudio = false
    }
}
