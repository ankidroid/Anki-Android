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
package com.wildplot.android.rendering;

import android.annotation.SuppressLint;

import com.wildplot.android.rendering.interfaces.Drawable;

import java.util.Vector;

/**
 * This class is used to store informations for a certain plot in a multi-plot sheet.
 * The informations are the drawables for this plotsheet and the x and y limitations
 */
@SuppressLint("NonPublicNonStaticFieldName")
public class MultiScreenPart {
    private final double[] xRange;
    private final double[] yRange;
    private Vector<Drawable> drawables = new Vector<>();


    /**
     * Constructor for a screen part,
     * a screen part is build with its information about x- and y-range.
     * Drawable objects that will be drawn on the screen part can be added after construction.
     */
    public MultiScreenPart(double[] xRange, double[] yRange) {
        super();
        this.xRange = xRange;
        this.yRange = yRange;
    }


    /**
     * Constructor for a screen part,
     * a screen part is build with its information about x- and y-range, aswell as a list of Drawable objects that will be drawn
     * onto the screen part
     */
    public MultiScreenPart(double[] xRange, double[] yRange, Vector<Drawable> drawables) {
        super();
        this.xRange = xRange;
        this.yRange = yRange;
        this.drawables = drawables;
    }


    /**
     * get the x-range of this screen part
     *
     * @return the xRange
     */
    public double[] getxRange() {
        return xRange;
    }


    /**
     * Get the y-range of this screen part
     *
     * @return the yRange
     */
    public double[] getyRange() {
        return yRange;
    }


    /**
     * get the Drawable objects associated with this screen part
     *
     * @return the drawables
     */
    public Vector<Drawable> getDrawables() {
        return drawables;
    }


    /**
     * add another Drawable object that shall be drawn onto the sheet
     *
     * @param draw Drawable object which will be added to plot sheet
     */
    public void addDrawable(Drawable draw) {
        this.drawables.add(draw);
    }

}
