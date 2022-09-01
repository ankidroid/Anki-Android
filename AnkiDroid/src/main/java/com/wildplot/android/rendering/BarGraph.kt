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

import com.wildplot.android.rendering.graphics.wrapper.ColorWrap;
import com.wildplot.android.rendering.graphics.wrapper.GraphicsWrap;
import com.wildplot.android.rendering.graphics.wrapper.RectangleWrap;
import com.wildplot.android.rendering.interfaces.Drawable;
import com.wildplot.android.rendering.interfaces.Legendable;


/**
 * BarGraph uses a point matrix or a function to render bar graphs on PlotSheet object
 */
@SuppressLint("NonPublicNonStaticFieldName")
public class BarGraph implements Drawable, Legendable {

    private String mName = "";
    private boolean mNameIsSet = false;

    private final PlotSheet plotSheet;

    private final double[][] points;

    private final double size;

    private final ColorWrap color;

    private ColorWrap fillColor;

    private boolean filling = false;


    /**
     * Constructor for BarGraph object
     *
     * @param plotSheet the sheet the bar will be drawn onto
     * @param size      absolute x-width of the bar
     * @param points    start points (x,y) from each bar
     * @param color     color of the bar
     */
    public BarGraph(PlotSheet plotSheet, double size, double[][] points, ColorWrap color) {
        this.plotSheet = plotSheet;
        this.size = size;
        this.points = points;
        this.color = color;
    }


    /**
     * Set filling for a bar graph true or false
     */
    public void setFilling(boolean filling) {
        this.filling = filling;
        if (this.fillColor == null) {
            this.fillColor = this.color;
        }
    }


    /**
     * Set filling color for bar graph
     *
     * @param fillColor of the bar graph
     */
    public void setFillColor(ColorWrap fillColor) {
        this.fillColor = fillColor;
    }


    @Override
    public void paint(GraphicsWrap g) {
        ColorWrap oldColor = g.getColor();
        RectangleWrap field = g.getClipBounds();
        g.setColor(color);

        for (int i = 0; i < this.points[0].length; i++) {
            if (points.length == 3) {
                drawBar(points[0][i], points[1][i], g, field, points[2][i]);
            } else {
                drawBar(points[0][i], points[1][i], g, field);
            }
        }

        g.setColor(oldColor);

    }


    /**
     * draw a single bar at given coordinates with given graphics object and bounds
     *
     * @param x     x-coordinate of bar
     * @param y     height of bar
     * @param g     graphics object for drawing
     * @param field bounds of plot
     */
    private void drawBar(double x, double y, GraphicsWrap g, RectangleWrap field) {
        drawBar(x, y, g, field, this.size);
    }


    /**
     * draw a single bar at given coordinates with given graphics object and bounds and specific size
     *
     * @param x     x-coordinate of bar
     * @param y     height of bar
     * @param g     graphics object for drawing
     * @param field bounds of plot
     * @param size  specific size for this bar
     */
    private void drawBar(double x, double y, GraphicsWrap g, RectangleWrap field, double size) {


        float[] pointUpLeft = plotSheet.toGraphicPoint(x - size / 2, y, field);
        float[] pointUpRight = plotSheet.toGraphicPoint(x + size / 2, y, field);
        float[] pointBottomLeft = plotSheet.toGraphicPoint(x - size / 2, 0, field);

        if (filling) {
            ColorWrap oldColor = g.getColor();
            if (this.fillColor != null) {
                g.setColor(fillColor);
            }

            if (y < 0) {
                g.fillRect(pointUpLeft[0], plotSheet.yToGraphic(0, field), pointUpRight[0] - pointUpLeft[0], pointUpLeft[1] - pointBottomLeft[1]);
            } else {
                g.fillRect(pointUpLeft[0], pointUpLeft[1], pointUpRight[0] - pointUpLeft[0], pointBottomLeft[1] - pointUpLeft[1]);
            }

            g.setColor(oldColor);
        } else {

            if (y < 0) {
                g.drawRect(pointUpLeft[0], plotSheet.yToGraphic(0, field), pointUpRight[0] - pointUpLeft[0], pointUpLeft[1] - pointBottomLeft[1]);
            } else {
                g.drawRect(pointUpLeft[0], pointUpLeft[1], pointUpRight[0] - pointUpLeft[0], pointBottomLeft[1] - pointUpLeft[1]);
            }
        }
    }


    /**
     * returns true if this BarGraph can draw on the outer frame of plot (normally not)
     */
    public boolean isOnFrame() {
        return false;
    }


    @Override
    public boolean isClusterable() {
        return true;
    }


    @Override
    public boolean isCritical() {
        return false;
    }


    @Override
    public ColorWrap getColor() {
        return fillColor;
    }


    @Override
    public String getName() {
        return mName;
    }


    @Override
    public boolean nameIsSet() {
        return mNameIsSet;
    }


    public void setName(String name) {
        mName = name;
        mNameIsSet = true;
    }
}
