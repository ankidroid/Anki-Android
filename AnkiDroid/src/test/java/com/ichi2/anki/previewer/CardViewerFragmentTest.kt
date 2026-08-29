// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.previewer

import android.content.DialogInterface
import android.os.Build
import android.view.ViewGroup
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebView
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import com.ichi2.anki.R
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.browser.IdsFile
import com.ichi2.anki.settings.Prefs
import com.ichi2.testutils.createTransientDirectory
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
            clickAlertDialogButton(DialogInterface.BUTTON_POSITIVE, true)
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
            clickAlertDialogButton(DialogInterface.BUTTON_NEGATIVE, true)
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
    private fun withCardViewerChromeClient(block: (WebChromeClient) -> Unit) {
        val note = addBasicAndReversedNote()
        val intent =
            PreviewerFragment.getIntent(
                targetContext,
                idsFile = IdsFile(createTransientDirectory(), note.cardIds(col)),
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
                block(requireNotNull(webView.webChromeClient) { "no WebChromeClient attached" })
            }
        }
    }

    /** [Prefs.allowTemplatesToRecordAudio] is process-wide state: reset it between tests */
    @After
    fun resetTemplateAudioOptIn() {
        Prefs.allowTemplatesToRecordAudio = false
    }
}
