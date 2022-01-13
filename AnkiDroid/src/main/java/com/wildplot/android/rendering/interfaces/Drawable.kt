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

package com.wildplot.android.rendering.interfaces

import com.wildplot.android.rendering.graphics.wrapper.GraphicsWrap

/**
 * Classes that implement the Drawable interface have the ability to draw with a provided Graphics object onto
 * a PlotSheet.
 */
interface Drawable {
    /**
     * Paint the drawable object
     */
    fun paint(g: GraphicsWrap)

    /**
     * Returns true if this Drawable can draw on the outer frame of the plot
     * this is necessary because normally everything drawn over the border will be whited out by the PlotSheet object.
     * If a legend or descriptions shall be drawn onto the outer frame this method of the corresponding Drawables has
     * to return true. For all other cases it is highly recommended to return false.
     */
    fun isOnFrame(): Boolean
    fun isClusterable(): Boolean
    fun isCritical(): Boolean
}
