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

package com.ichi2.anki.cardviewer;

import android.content.res.Resources;
import android.os.Build;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.WebView;

import com.ichi2.anki.AbstractFlashcardViewer;
import com.ichi2.libanki.Card;

import org.junit.Test;

import java.util.concurrent.locks.Lock;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.lifecycle.Lifecycle;

import static com.ichi2.utils.StrictMock.strictMock;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RequiresApi(api = Build.VERSION_CODES.O) //onRenderProcessGone & RenderProcessGoneDetail
public class OnRenderProcessGoneDelegateTest {

    @Test
    public void singleCallCausesRefresh() {
        AbstractFlashcardViewer mock = getViewer();
        OnRenderProcessGoneDelegateImpl delegate = getInstance(mock);

        callOnRenderProcessGone(delegate);

        verify(mock, times(1)).displayCardQuestion();
        assertThat(delegate.mDisplayedToast, is(true));
    }


    @Test
    public void secondCallCausesDialog() {
        AbstractFlashcardViewer mock = getViewer();
        OnRenderProcessGoneDelegateImpl delegate = getInstance(mock);

        callOnRenderProcessGone(delegate);

        verify(mock, times(1)).displayCardQuestion();

        callOnRenderProcessGone(delegate);

        verify(mock, times(1)
                .description("displayCardQuestion should not be called again as the screen should close"))
                .displayCardQuestion();
        assertThat(delegate.mDisplayedDialog, is(true));
        verify(mock, times(1).description("After the dialog, the screen should be closed")).finishWithoutAnimation();
    }

    @Test
    public void secondCallDoesNothingIfMinimised() {
        AbstractFlashcardViewer mock = getMinimisedViewer();
        OnRenderProcessGoneDelegateImpl delegate = getInstance(mock);

        callOnRenderProcessGone(delegate);

        verify(mock, times(1)).displayCardQuestion();

        callOnRenderProcessGone(delegate);

        verify(mock, times(2)
                .description("displayCardQuestion should be called again as the app was minimised"))
                .displayCardQuestion();
        assertThat(delegate.mDisplayedDialog, is(false));
    }


    @Test
    public void nothingHappensIfWebViewIsNotTheSame() {
        AbstractFlashcardViewer mock = getViewer();
        OnRenderProcessGoneDelegateImpl delegate = getInstance(mock);

        callOnRenderProcessGone(delegate, mock(WebView.class));

        verify(mock, never().description("No mutating methods should be called if the WebView is not relevant")).destroyWebViewFrame();
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    @Test
    public void unrecoverableCrashDoesNotRecreateWebView() {
        AbstractFlashcardViewer mock = getViewer();
        OnRenderProcessGoneDelegateImpl delegate = getInstance(mock);

        doReturn(null).when(mock).getCurrentCard();
        callOnRenderProcessGone(delegate);

        verify(mock, times(1)).destroyWebViewFrame();
        verify(mock, never()).recreateWebViewFrame();

        assertThat("A toast should be displayed", delegate.mDisplayedToast, is(true));
        verify(mock, times(1).description("screen should be closed")).finishWithoutAnimation();
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    @Test
    public void unrecoverableCrashCloses() {
        AbstractFlashcardViewer mock = getMinimisedViewer();
        OnRenderProcessGoneDelegateImpl delegate = getInstance(mock);

        doReturn(null).when(mock).getCurrentCard();
        callOnRenderProcessGone(delegate);

        verify(mock, times(1)).destroyWebViewFrame();
        verify(mock, never()).recreateWebViewFrame();

        assertThat("A toast should not be displayed as the screen is minimised", delegate.mDisplayedToast, is(false));
        verify(mock, times(1).description("screen should be closed")).finishWithoutAnimation();
    }


    protected void callOnRenderProcessGone(OnRenderProcessGoneDelegateImpl delegate) {
        callOnRenderProcessGone(delegate, delegate.getTarget().getWebView());
    }


    private void callOnRenderProcessGone(OnRenderProcessGoneDelegateImpl delegate, WebView webView) {
        boolean result = delegate.onRenderProcessGone(webView, getCrashDetail());
        assertThat("onRenderProcessGone should only return false if we want the app killed", result, is(true));
    }


    protected AbstractFlashcardViewer getMinimisedViewer() {
        return getViewer(Lifecycle.State.CREATED);
    }

    @NonNull
    protected AbstractFlashcardViewer getViewer() {
        return getViewer(Lifecycle.State.STARTED);
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    private AbstractFlashcardViewer getViewer(Lifecycle.State state) {
        WebView mockWebView = mock(WebView.class);
        AbstractFlashcardViewer mock = strictMock(AbstractFlashcardViewer.class);
        doReturn(mock(Lock.class)).when(mock).getWriteLock();
        doReturn(mock(Resources.class)).when(mock).getResources();
        doReturn(mockWebView).when(mock).getWebView();
        doReturn(mock(Card.class)).when(mock).getCurrentCard();
        doReturn(lifecycleOf(state)).when(mock).getLifecycle();
        doNothing().when(mock).destroyWebViewFrame();
        doNothing().when(mock).recreateWebViewFrame();
        doNothing().when(mock).displayCardQuestion();
        doNothing().when(mock).finishWithoutAnimation();
        return mock;
    }


    private Lifecycle lifecycleOf(Lifecycle.State state) {
        Lifecycle ret = mock(Lifecycle.class);
        when(ret.getCurrentState()).thenReturn(state);
        return ret;
    }


    @NonNull
    protected OnRenderProcessGoneDelegateImpl getInstance(AbstractFlashcardViewer mock) {
        return spy(new OnRenderProcessGoneDelegateImpl(mock));
    }

    protected RenderProcessGoneDetail getCrashDetail() {
        RenderProcessGoneDetail mock = mock(RenderProcessGoneDetail.class);
        when(mock.didCrash()).thenReturn(true); // this value doesn't matter for now as it only defines a string
        return mock;
    }

    public static class OnRenderProcessGoneDelegateImpl extends OnRenderProcessGoneDelegate {

        private boolean mDisplayedToast;
        private boolean mDisplayedDialog;


        public OnRenderProcessGoneDelegateImpl(AbstractFlashcardViewer target) {
            super(target);
        }


        @Override
        protected void displayFatalError(RenderProcessGoneDetail detail) {
            this.mDisplayedToast = true;
        }


        @Override
        protected void displayNonFatalError(RenderProcessGoneDetail detail) {
            this.mDisplayedToast = true;
        }


        @Override
        protected void displayRenderLoopDialog(long currentCardId, RenderProcessGoneDetail detail) {
            this.mDisplayedDialog = true;
            this.onCloseRenderLoopDialog();
        }
    }
}
