/*
 * Copyright (c) 2026 Brayan Oliveira <brayandso.dev@gmail.com>
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program. If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.anki.utils.ext

import java.util.Collections

/**
 * Moves an item within a MutableList by swapping adjacent elements. This is particularly
 * optimized for [androidx.recyclerview.widget.ItemTouchHelper] drag-and-drop operations.
 */
fun <T> MutableList<T>.swapPositions(
    fromPosition: Int,
    toPosition: Int,
) {
    if (fromPosition < toPosition) {
        for (i in fromPosition until toPosition) {
            Collections.swap(this, i, i + 1)
        }
    } else {
        for (i in fromPosition downTo toPosition + 1) {
            Collections.swap(this, i, i - 1)
        }
    }
}
