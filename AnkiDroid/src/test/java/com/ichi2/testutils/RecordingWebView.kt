// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.testutils

import android.view.MotionEvent
import android.webkit.WebView
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadows.ShadowWebView

/** Records generic motion events sent to a [WebView], which the default Robolectric shadow ignores. */
@Implements(WebView::class)
class RecordingWebView : ShadowWebView() {
    val genericMotionEvents = mutableListOf<MotionEvent>()

    /** Not handled, as with the default shadow. Records a copy, as the sender may recycle the event */
    @Implementation
    fun onGenericMotionEvent(event: MotionEvent): Boolean {
        genericMotionEvents.add(MotionEvent.obtain(event))
        return false
    }
}
