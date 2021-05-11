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


/**
 * This class represents grid lines parallel to the y-axis
 */
@SuppressLint("NonPublicNonStaticFieldName")
public class YGrid implements Drawable {

    /**
     * the color of the grid lines
     */
    private ColorWrap color = ColorWrap.LIGHT_GRAY;

    /**
     * the Sheet the grid lines will be drawn onto
     */
    private final PlotSheet plotSheet;

    /**
     * start point for relative positioning of grid
     */
    private final double ticStart;

    /**
     * the space between two grid lines
     */
    private final double tic;

    /**
     * maximal distance from x axis the grid will be drawn
     */
    private double xLength = 10;

    /**
     * maximal distance from y axis the grid will be drawn
     */
    private double yLength = 2;

    private double[] mTickPositions;


    /**
     * Constructor for an Y-Grid object
     *
     * @param plotSheet the sheet the grid will be drawn onto
     * @param ticStart  start point for relative positioning of grid
     * @param tic       the space between two grid lines
     */
    public YGrid(PlotSheet plotSheet, double ticStart, double tic) {
        super();
        this.plotSheet = plotSheet;
        this.ticStart = ticStart;
        this.tic = tic;
    }


    @Override
    public void paint(GraphicsWrap g) {
        ColorWrap oldColor = g.getColor();
        g.setColor(color);

        this.xLength = Math.max(Math.abs(plotSheet.getxRange()[0]), Math.abs(plotSheet.getxRange()[1]));
        this.yLength = Math.max(Math.abs(plotSheet.getyRange()[0]), Math.abs(plotSheet.getyRange()[1]));

        int tics = (int) ((this.ticStart - (0 - this.xLength)) / tic);
        double leftStart = this.ticStart - this.tic * tics;

        if (mTickPositions == null) {
            drawImplicitLines(g, leftStart);
        } else {
            drawExplicitLines(g);
        }

        g.setColor(oldColor);

    }


    private void drawImplicitLines(GraphicsWrap g, double leftStart) {
        RectangleWrap field = g.getClipBounds();
        double currentX = leftStart;

        while (currentX <= this.xLength) {
            drawGridLine(currentX, g, field);
            currentX += this.tic;
        }
    }


    private void drawExplicitLines(GraphicsWrap g) {
        RectangleWrap field = g.getClipBounds();

        for (double currentX : mTickPositions) {
            drawGridLine(currentX, g, field);
        }
    }


    /**
     * Draw a grid line in specified graphics object
     *
     * @param x     x-position the vertical line shall be drawn
     * @param g     graphic the line shall be drawn onto
     * @param field definition of the graphic boundaries
     */
    private void drawGridLine(double x, GraphicsWrap g, RectangleWrap field) {
        g.drawLine(plotSheet.xToGraphic(x, field), plotSheet.yToGraphic(0, field),
                plotSheet.xToGraphic(x, field), plotSheet.yToGraphic(yLength, field));
        g.drawLine(plotSheet.xToGraphic(x, field), plotSheet.yToGraphic(0, field),
                plotSheet.xToGraphic(x, field), plotSheet.yToGraphic(-yLength, field));
    }


    public boolean isOnFrame() {
        return false;
    }


    @Override
    public boolean isClusterable() {
        return true;
    }


    @Override
    public boolean isCritical() {
        return true;
    }


    public void setColor(ColorWrap color) {
        this.color = color;
    }


    public void setExplicitTicks(double[] tickPositions) {
        mTickPositions = tickPositions;
    }
}
