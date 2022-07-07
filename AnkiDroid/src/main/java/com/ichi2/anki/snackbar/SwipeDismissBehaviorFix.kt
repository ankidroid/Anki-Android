/*
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program. If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.anki.snackbar

import android.view.MotionEvent
import android.view.View
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.customview.widget.ViewDragHelper
import com.google.android.material.behavior.SwipeDismissBehavior

/**
 * This exists to help Snackbars actually move on the screen when you try to swipe them away.
 * With the default SwipeDismissBehavior, while you can can dismiss them by flinging,
 * they stay in place while you do so, and only ever disappear to the right,
 * not into the direction of the fling. The library provides the functionality, it's just broken.
 *
 * The issue is, when we get a move event, ViewDragHelper will capture the view for dragging,
 * and ask parent to stop intercepting further touches. This propagates to CoordinatorLayout,
 * which then resets touch behavior for its children--including us.
 *
 * The sequence of unfortunate events:
 *   * [onInterceptTouchEvent]
 *   * [ViewDragHelper.shouldInterceptTouchEvent]
 *   * [ViewDragHelper.tryCaptureViewForDrag]
 *   * [ViewDragHelper.captureChildView]
 *   * [ViewDragHelper.Callback.onViewCaptured]
 *   * [CoordinatorLayout.requestDisallowInterceptTouchEvent]
 *   * [CoordinatorLayout.resetTouchBehaviors]
 *   * [onTouchEvent]
 *
 * This fix solves the issue in a very simple way, by ignoring calls to [onTouchEvent]
 * during the call to [onInterceptTouchEvent].
 *
 * Another problem with SwipeDismissBehavior is that the way it uses ViewDragHelper,
 * event interception may happen well past the initial start of the gesture.
 * ViewDragHelper will capture the view under the finger,
 * which at this point might be some unrelated view, and *that* view will be moved instead.
 *
 * This solves the issue by never intercepting touch events unless the finger is over the snackbar.
 * This does prevent the user from moving snackbar if they put a finger on it,
 * move it directly upwards, and then to the side.
 * However, this inconvenience is rather minor and fixing it properly might require too much effort.
 */
class SwipeDismissBehaviorFix<V : View> : SwipeDismissBehavior<V>() {
    private var ignoreCallsToOnTouchEvent = false

    override fun onInterceptTouchEvent(parent: CoordinatorLayout, child: V, event: MotionEvent): Boolean {
        if (!parent.isPointInChildBounds(child, event.x.toInt(), event.y.toInt())) return false

        ignoreCallsToOnTouchEvent = true
        return super.onInterceptTouchEvent(parent, child, event).also {
            ignoreCallsToOnTouchEvent = false
        }
    }

    override fun onTouchEvent(parent: CoordinatorLayout, child: V, event: MotionEvent): Boolean {
        if (ignoreCallsToOnTouchEvent) return false
        return super.onTouchEvent(parent, child, event)
    }
}
