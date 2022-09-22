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

import com.wildplot.android.rendering.graphics.wrapper.ColorWrap
import com.wildplot.android.rendering.graphics.wrapper.GraphicsWrap
import com.wildplot.android.rendering.interfaces.Drawable
import com.wildplot.android.rendering.interfaces.Legendable

class LegendDrawable : Drawable, Legendable {
    override var name: String = ""
        set(value) {
            field = value
            mNameIsSet = true
        }
    private var mNameIsSet = false
    override var color = ColorWrap.BLACK
    override fun paint(g: GraphicsWrap) {}
    override fun isOnFrame(): Boolean {
        return false
    }

    override fun isClusterable(): Boolean {
        return false
    }

    override fun isCritical(): Boolean {
        return false
    }

    override fun nameIsSet(): Boolean {
        return mNameIsSet
    }
}
