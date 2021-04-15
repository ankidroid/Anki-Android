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

import android.annotation.SuppressLint;

@SuppressLint({"NonPublicNonStaticFieldName", "ConstantFieldName"})
public class ColorWrap {
    //android.graphics.Color
    private final int colorValue;
    public static final ColorWrap red = new ColorWrap(android.graphics.Color.RED);
    public static final ColorWrap RED = new ColorWrap(android.graphics.Color.RED);

    public static final ColorWrap BLACK = new ColorWrap(android.graphics.Color.BLACK);
    public static final ColorWrap black = new ColorWrap(android.graphics.Color.BLACK);

    public static final ColorWrap GREEN = new ColorWrap(android.graphics.Color.GREEN);
    public static final ColorWrap green = new ColorWrap(android.graphics.Color.GREEN);

    public static final ColorWrap LIGHT_GRAY = new ColorWrap(android.graphics.Color.LTGRAY);

    public static final ColorWrap WHITE = new ColorWrap(android.graphics.Color.WHITE);
    public static final ColorWrap white = new ColorWrap(android.graphics.Color.WHITE);


    public ColorWrap(int colorValue) {
        super();
        this.colorValue = colorValue;
    }


    public ColorWrap(int colorValue, float af) {
        super();
        int a = Math.round(af * 255);
        int r = android.graphics.Color.red(colorValue);
        int g = android.graphics.Color.green(colorValue);
        int b = android.graphics.Color.blue(colorValue);
        this.colorValue = android.graphics.Color.argb(a, r, g, b);
    }


    public ColorWrap(int r, int g, int b) {
        this.colorValue = android.graphics.Color.rgb(r, g, b);
    }


    public ColorWrap(int r, int g, int b, int a) {
        this.colorValue = android.graphics.Color.argb(a, r, g, b);
    }


    public ColorWrap(float r, float g, float b, float a) {
        this.colorValue = android.graphics.Color.argb((int) (a * 255), (int) (r * 255), (int) (g * 255), (int) (b * 255));
    }


    public ColorWrap(float r, float g, float b) {
        this.colorValue = android.graphics.Color.rgb((int) (r * 255), (int) (g * 255), (int) (b * 255));
    }


    public int getColorValue() {
        return colorValue;
    }


    public int getRed() {
        return android.graphics.Color.red(colorValue);
    }


    public int getGreen() {
        return android.graphics.Color.green(colorValue);
    }


    public int getBlue() {
        return android.graphics.Color.blue(colorValue);
    }
}
