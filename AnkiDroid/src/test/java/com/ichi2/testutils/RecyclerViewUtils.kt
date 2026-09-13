/*
 Copyright (c) 2021 Tarek Mohamed Abdalla <tarekkma@gmail.com>

 This program is free software; you can redistribute it and/or modify it under
 the terms of the GNU General Public License as published by the Free Software
 Foundation; either version 3 of the License, or (at your option) any later
 version.

 This program is distributed in the hope that it will be useful, but WITHOUT ANY
 WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 PARTICULAR PURPOSE. See the GNU General Public License for more details.

 You should have received a copy of the GNU General Public License along with
 this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.ichi2.testutils

import android.os.Looper
import android.view.MotionEvent
import android.view.View
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.ichi2.anki.RobolectricTest.Companion.advanceRobolectricLooper
import org.robolectric.Shadows.shadowOf
import java.time.Duration

object RecyclerViewUtils {
    inline fun <reified VH : RecyclerView.ViewHolder?> viewHolderAt(
        recyclerView: RecyclerView,
        position: Int,
    ): VH = recyclerView.findViewHolderForAdapterPosition(position) as VH
}

/** The adapter position of the last item */
val RecyclerView.lastPosition: Int
    get() = adapter!!.itemCount - 1

/** The laid-out view of the last item, which must be on screen */
val RecyclerView.lastItemView: View
    get() = layoutManager!!.findViewByPosition(lastPosition)!!

/**
 * Requests a scroll to the last item. Layout is left to the caller.
 *
 * @see scrollToEnd
 */
fun RecyclerView.scrollToLastPosition() = scrollToPosition(lastPosition)

/** Scrolls until the last item is fully visible and the list can scroll no further, then settles layout */
fun RecyclerView.scrollToEnd() {
    scrollToLastPosition()
    while (canScrollVertically(1)) scrollBy(0, 50)
    advanceRobolectricLooper()
}

/** Releases an [ItemTouchHelper] drag: the item animates back into place before it settles */
fun RecyclerView.dropDraggedItem() = dispatchTouch(MotionEvent.ACTION_UP)

/** Completes the animation which returns a [dropped][dropDraggedItem] item into place */
fun RecyclerView.completeDroppedItem() {
    // ItemTouchHelper animates a drop for the item animator's 'move' duration
    val duration = itemAnimator?.moveDuration ?: ItemTouchHelper.Callback.DEFAULT_DRAG_ANIMATION_DURATION.toLong()
    // doubled: the animation starts a frame after the drop, and ends on the frame after its duration
    shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(duration * 2))
}

/** The positions of the items which the adapter reports as changed, from now on */
fun RecyclerView.Adapter<*>.changedPositions(): Set<Int> {
    val positions = mutableSetOf<Int>()
    registerAdapterDataObserver(
        object : RecyclerView.AdapterDataObserver() {
            override fun onItemRangeChanged(
                positionStart: Int,
                itemCount: Int,
            ) {
                positions += positionStart until positionStart + itemCount
            }
        },
    )
    return positions
}
