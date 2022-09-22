/****************************************************************************************
 * Copyright (c) 2014 Michael Goldbach <michael@wildplot.com>                           *
 *                                                                                      *
 * This program is free software; you can redistribute it and/or modify it under        *
 * the terms of the GNU General Public License as published by the Free Software        *
 * Foundation; either version 3 of the License, or (at your option) any later           *
 * version.                                                                             *
 *                                                                                      *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY      *
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A      *
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.             *
 *                                                                                      *
 * You should have received a copy of the GNU General Public License along with         *
 * this program.  If not, see <http://www.gnu.org/licenses/>.                           *
 ****************************************************************************************/
package com.wildplot.android.rendering

import com.wildplot.android.rendering.graphics.wrapper.GraphicsWrap
import com.wildplot.android.rendering.interfaces.Drawable
import java.util.*

class DrawableContainer(private val isOnFrame: Boolean, private val isCritical: Boolean) : Drawable {
    private val drawableVector = Vector<Drawable>()
    fun addDrawable(drawable: Drawable) {
        drawableVector.add(drawable)
    }

    override fun paint(g: GraphicsWrap) {
        for (drawable in drawableVector) {
            drawable.paint(g)
        }
    }

    override fun isOnFrame(): Boolean {
        return isOnFrame
    }

    override fun isClusterable(): Boolean {
        return false
    }

    override fun isCritical(): Boolean {
        return isCritical
    }

    val size: Int
        get() = drawableVector.size
}
