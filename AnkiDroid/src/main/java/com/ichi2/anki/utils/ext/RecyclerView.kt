// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.utils.ext

import android.view.View
import androidx.annotation.IdRes
import androidx.recyclerview.widget.RecyclerView

/** @see View.findViewById */
fun <T : View> RecyclerView.ViewHolder.findViewById(
    @IdRes id: Int,
) = itemView.findViewById<T>(id)

/**
 * Adds a listener invoked whenever the [RecyclerView] has completed scrolling.
 *
 * - `dx` - The amount of horizontal scroll.
 * - `dy` - The amount of vertical scroll.
 *
 * @see RecyclerView.OnScrollListener.onScrolled
 */
inline fun RecyclerView.doOnScrolled(crossinline action: (dx: Int, dy: Int) -> Unit) {
    addOnScrollListener(
        object : RecyclerView.OnScrollListener() {
            override fun onScrolled(
                recyclerView: RecyclerView,
                dx: Int,
                dy: Int,
            ) = action(dx, dy)
        },
    )
}
