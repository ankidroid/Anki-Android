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

import android.view.View
import androidx.recyclerview.widget.RecyclerView
import com.ichi2.anki.RobolectricTest.Companion.advanceRobolectricLooper

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
