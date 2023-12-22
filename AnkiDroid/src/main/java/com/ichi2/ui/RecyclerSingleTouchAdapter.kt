/*
 Copyright (c) 2020 David Allison <davidallisongithub@gmail.com>

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
package com.ichi2.ui

import android.content.Context
import android.view.GestureDetector
import android.view.GestureDetector.SimpleOnGestureListener
import android.view.MotionEvent
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.OnItemTouchListener

/** Adapts a RecyclerView.OnItemTouchListener to provide a click listener  */

class RecyclerSingleTouchAdapter(val context: Context, val listener: OnItemClickListener) : OnItemTouchListener {
    private val mGestureDetector =
        GestureDetector(
            context,
            object : SimpleOnGestureListener() {
                override fun onSingleTapUp(e: MotionEvent): Boolean {
                    // onDown was too fast
                    return true
                }
            },
        )

    fun interface OnItemClickListener {
        fun onItemClick(
            view: View,
            position: Int,
        )
    }

    override fun onInterceptTouchEvent(
        view: RecyclerView,
        e: MotionEvent,
    ): Boolean {
        val childView = view.findChildViewUnder(e.x, e.y)
        if (childView != null && mGestureDetector.onTouchEvent(e)) {
            listener.onItemClick(childView, view.getChildAdapterPosition(childView))
            return true
        }
        return false
    }

    override fun onTouchEvent(
        view: RecyclerView,
        motionEvent: MotionEvent,
    ) {
        // intentionally empty
    }

    override fun onRequestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {
        // intentionally empty
    }
}
