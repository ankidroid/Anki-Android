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
package com.wildplot.android.rendering.graphics.wrapper;

import android.graphics.Rect;

public class RectangleWrap {
    public final int x;
    public final int y;
    public int width;
    public int height;


    public RectangleWrap(int width, int heigth) {
        this(new Rect(0, 0, width, heigth));
    }


    public RectangleWrap(Rect rect) {
        super();
        this.x = rect.left;
        this.y = rect.top;
        this.height = rect.height();
        this.width = rect.width();
    }


    public int width() {
        return width;
    }


    public int height() {
        return height;
    }
}
