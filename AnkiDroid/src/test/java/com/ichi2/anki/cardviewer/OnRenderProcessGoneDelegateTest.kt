/*
 *  Copyright (c) 2021 David Allison <davidallisongithub@gmail.com>
 *
 *  This program is free software; you can redistribute it and/or modify it under
 *  the terms of the GNU General Public License as published by the Free Software
 *  Foundation; either version 3 of the License, or (at your option) any later
 *  version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY
 *  WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 *  PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along with
 *  this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.ichi2.anki.cardviewer

import android.content.res.Resources
import android.os.Build
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebView
import androidx.annotation.RequiresApi
import androidx.lifecycle.Lifecycle
import com.ichi2.anki.AbstractFlashcardViewer
import com.ichi2.libanki.Card
import com.ichi2.utils.KotlinCleanup
import com.ichi2.utils.StrictMock.Companion.strictMock
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.`is`
import org.junit.Test
import org.mockito.Mockito.*
import java.util.concurrent.locks.Lock

@RequiresApi(api = Build.VERSION_CODES.O) // onRenderProcessGone & RenderProcessGoneDetail
@KotlinCleanup("IDE-lint")
@KotlinCleanup("`when` -> whenever")
@KotlinCleanup("`is` -> equalTo")
class OnRenderProcessGoneDelegateTest {
    @Test
    fun singleCallCausesRefresh() {
        val mock = viewer
        val delegate = getInstance(mock)

        callOnRenderProcessGone(delegate)

        verify(mock, times(1)).displayCardQuestion()
        assertThat(delegate.displayedToast, `is`(true))
    }

    @Test
    fun secondCallCausesDialog() {
        val mock = viewer
        val delegate = getInstance(mock)

        callOnRenderProcessGone(delegate)

        verify(mock, times(1)).displayCardQuestion()

        callOnRenderProcessGone(delegate)

        verify(
            mock,
            times(1)
                .description("displayCardQuestion should not be called again as the screen should close")
        )
            .displayCardQuestion()
        assertThat(delegate.displayedDialog, `is`(true))
        verify(mock, times(1).description("After the dialog, the screen should be closed")).finishWithoutAnimation()
    }

    @Test
    fun secondCallDoesNothingIfMinimised() {
        val mock = minimisedViewer
        val delegate = getInstance(mock)

        callOnRenderProcessGone(delegate)

        verify(mock, times(1)).displayCardQuestion()

        callOnRenderProcessGone(delegate)

        verify(
            mock,
            times(2)
                .description("displayCardQuestion should be called again as the app was minimised")
        )
            .displayCardQuestion()
        assertThat(delegate.displayedDialog, `is`(false))
    }

    @Test
    fun nothingHappensIfWebViewIsNotTheSame() {
        val mock = viewer
        val delegate = getInstance(mock)

        callOnRenderProcessGone(delegate, mock(WebView::class.java))

        verify(mock, never().description("No mutating methods should be called if the WebView is not relevant")).destroyWebViewFrame()
    }

    @Test
    fun unrecoverableCrashDoesNotRecreateWebView() {
        val mock = viewer
        val delegate = getInstance(mock)

        doReturn(null).`when`(mock).currentCard
        callOnRenderProcessGone(delegate)

        verify(mock, times(1)).destroyWebViewFrame()
        verify(mock, never()).recreateWebViewFrame()

        assertThat("A toast should be displayed", delegate.displayedToast, `is`(true))
        verify(mock, times(1).description("screen should be closed")).finishWithoutAnimation()
    }

    @Test
    fun unrecoverableCrashCloses() {
        val mock = minimisedViewer
        val delegate = getInstance(mock)

        doReturn(null).`when`(mock).currentCard
        callOnRenderProcessGone(delegate)

        verify(mock, times(1)).destroyWebViewFrame()
        verify(mock, never()).recreateWebViewFrame()

        assertThat("A toast should not be displayed as the screen is minimised", delegate.displayedToast, `is`(false))
        verify(mock, times(1).description("screen should be closed")).finishWithoutAnimation()
    }

    protected fun callOnRenderProcessGone(delegate: OnRenderProcessGoneDelegateImpl) {
        callOnRenderProcessGone(delegate, delegate.target.webView)
    }

    @KotlinCleanup("webview nullability")
    private fun callOnRenderProcessGone(delegate: OnRenderProcessGoneDelegateImpl, webView: WebView?) {
        val result = delegate.onRenderProcessGone(webView!!, crashDetail)
        assertThat("onRenderProcessGone should only return false if we want the app killed", result, `is`(true))
    }

    protected val minimisedViewer: AbstractFlashcardViewer
        get() = getViewer(Lifecycle.State.CREATED)
    protected val viewer: AbstractFlashcardViewer
        get() = getViewer(Lifecycle.State.STARTED)

    private fun getViewer(state: Lifecycle.State): AbstractFlashcardViewer {
        val mockWebView = mock(WebView::class.java)
        val mock = strictMock(AbstractFlashcardViewer::class.java)
        doReturn(mock(Lock::class.java)).`when`(mock).writeLock
        doReturn(mock(Resources::class.java)).`when`(mock).resources
        doReturn(mockWebView).`when`(mock).webView
        doReturn(mock(Card::class.java)).`when`(mock).currentCard
        doReturn(lifecycleOf(state)).`when`(mock).lifecycle
        doNothing().`when`(mock).destroyWebViewFrame()
        doNothing().`when`(mock).recreateWebViewFrame()
        doNothing().`when`(mock).displayCardQuestion()
        doNothing().`when`(mock).finishWithoutAnimation()
        return mock
    }

    private fun lifecycleOf(state: Lifecycle.State): Lifecycle {
        val ret = mock(Lifecycle::class.java)
        `when`(ret.currentState).thenReturn(state)
        return ret
    }

    protected fun getInstance(mock: AbstractFlashcardViewer?): OnRenderProcessGoneDelegateImpl {
        return spy(OnRenderProcessGoneDelegateImpl(mock))
    }

    // this value doesn't matter for now as it only defines a string
    protected val crashDetail: RenderProcessGoneDetail
        get() {
            val mock = mock(RenderProcessGoneDetail::class.java)
            `when`(mock.didCrash()).thenReturn(true) // this value doesn't matter for now as it only defines a string
            return mock
        }

    class OnRenderProcessGoneDelegateImpl(target: AbstractFlashcardViewer?) : OnRenderProcessGoneDelegate(target!!) {
        var displayedToast = false
        var displayedDialog = false
        override fun displayFatalError(detail: RenderProcessGoneDetail) {
            displayedToast = true
        }

        override fun displayNonFatalError(detail: RenderProcessGoneDetail) {
            displayedToast = true
        }

        override fun displayRenderLoopDialog(currentCardId: Long, detail: RenderProcessGoneDetail) {
            displayedDialog = true
            onCloseRenderLoopDialog()
        }
    }
}
