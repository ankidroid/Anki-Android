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

import android.annotation.TargetApi;
import android.content.res.Resources;
import android.os.Build;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.WebView;

import com.afollestad.materialdialogs.MaterialDialog;
import com.ichi2.anki.AbstractFlashcardViewer;
import com.ichi2.anki.R;
import com.ichi2.anki.UIUtils;
import com.ichi2.libanki.Card;

import java.util.concurrent.locks.Lock;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import timber.log.Timber;

public class OnRenderProcessGoneDelegate {

    private final AbstractFlashcardViewer mTarget;

    /**
     * Last card that the WebView Renderer crashed on.
     * If we get 2 crashes on the same card, then we likely have an infinite loop and want to exit gracefully.
     */
    @Nullable
    private Long mLastCrashingCardId = null;

    public OnRenderProcessGoneDelegate(AbstractFlashcardViewer target) {
        this.mTarget = target;
    }

    /** Fix: #5780 - WebView Renderer OOM crashes reviewer */
    @RequiresApi(api = Build.VERSION_CODES.O)
    public boolean onRenderProcessGone(WebView view, RenderProcessGoneDetail detail) {
        Timber.i("Obtaining write lock for card");
        Lock writeLock = mTarget.getWriteLock();
        WebView mCardWebView = mTarget.getWebView();
        Card mCurrentCard = mTarget.getCurrentCard();
        Timber.i("Obtained write lock for card");
        try {
            writeLock.lock();
            if (mCardWebView == null || !mCardWebView.equals(view)) {
                //A view crashed that wasn't ours.
                //We have nothing to handle. Returning false is a desire to crash, so return true.
                Timber.i("Unrelated WebView Renderer terminated. Crashed: %b",  detail.didCrash());
                return true;
            }

            Timber.e("WebView Renderer process terminated. Crashed: %b",  detail.didCrash());

            mTarget.destroyWebViewFrame();

            //We only want to show one message per branch.

            //It's not necessarily an OOM crash, false implies a general code which is for "system terminated".
            int errorCauseId = detail.didCrash() ? R.string.webview_crash_unknown : R.string.webview_crash_oom;
            String errorCauseString = mTarget.getResources().getString(errorCauseId);

            if (!canRecoverFromWebViewRendererCrash()) {
                Timber.e("Unrecoverable WebView Render crash");
                String errorMessage = mTarget.getResources().getString(R.string.webview_crash_fatal, errorCauseString);
                UIUtils.showThemedToast(mTarget, errorMessage, false);
                mTarget.finishWithoutAnimation();
                return true;
            }

            if (webViewRendererLastCrashedOnCard(mCurrentCard.getId())) {
                Timber.e("Web Renderer crash loop on card: %d", mCurrentCard.getId());
                displayRenderLoopDialog(mCurrentCard, detail);
                return true;
            }

            // If we get here, the error is non-fatal and we should re-render the WebView
            // This logic may need to be better defined. The card could have changed by the time we get here.
            mLastCrashingCardId = mCurrentCard.getId();


            String nonFatalError = mTarget.getResources().getString(R.string.webview_crash_nonfatal, errorCauseString);
            UIUtils.showThemedToast(mTarget, nonFatalError, false);

            mTarget.recreateWebViewFrame();
        } finally {
            writeLock.unlock();
            Timber.d("Relinquished writeLock");
        }
        mTarget.displayCardQuestion();

        //We handled the crash and can continue.
        return true;
    }

    @TargetApi(Build.VERSION_CODES.O)
    private void displayRenderLoopDialog(Card currentCard, RenderProcessGoneDetail detail) {
        String cardInformation = Long.toString(currentCard.getId());
        Resources res = mTarget.getResources();

        String errorDetails = detail.didCrash()
                ? res.getString(R.string.webview_crash_unknwon_detailed)
                : res.getString(R.string.webview_crash_oom_details);
        new MaterialDialog.Builder(mTarget)
                .title(res.getString(R.string.webview_crash_loop_dialog_title))
                .content(res.getString(R.string.webview_crash_loop_dialog_content, cardInformation, errorDetails))
                .positiveText(R.string.dialog_ok)
                .cancelable(false)
                .canceledOnTouchOutside(false)
                .onPositive((materialDialog, dialogAction) -> mTarget.finishWithoutAnimation())
                .show();
    }



    private boolean webViewRendererLastCrashedOnCard(long cardId) {
        return mLastCrashingCardId != null && mLastCrashingCardId == cardId;
    }


    private boolean canRecoverFromWebViewRendererCrash() {
        // DEFECT
        // If we don't have a card to render, we're in a bad state. The class doesn't currently track state
        // well enough to be able to know exactly where we are in the initialisation pipeline.
        // so it's best to mark the crash as non-recoverable.
        // We should fix this, but it's very unlikely that we'll ever get here. Logs will tell

        // Revisit webViewCrashedOnCard() if changing this. Logic currently assumes we have a card.
        return mTarget.getCurrentCard() != null;
    }
}
