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

import com.wildplot.android.rendering.graphics.wrapper.ColorWrap;
import com.wildplot.android.rendering.graphics.wrapper.GraphicsWrap;
import com.wildplot.android.rendering.graphics.wrapper.RectangleWrap;
import com.wildplot.android.rendering.interfaces.Drawable;


/**
 * This class represents grid lines parallel to the x-axis
 */
public class XGrid implements Drawable {

    /**
     * the color of the grid lines
     */
    private ColorWrap color = ColorWrap.LIGHT_GRAY;

    /**
     * the Sheet the grid lines will be drawn onto
     */
    private PlotSheet plotSheet;

    /**
     * start point for relative positioning of grid
     */
    private double ticStart;

    /**
     * the space between two grid lines
     */
    private double tic;

    /**
     * maximal distance from x axis the grid will be drawn
     */
    private double xLength = 10;


    /**
     * Constructor for an X-Grid object
     *
     * @param plotSheet the sheet the grid will be drawn onto
     * @param ticStart  start point for relative positioning of grid
     * @param tic       the space between two grid lines
     */
    public XGrid(PlotSheet plotSheet, double ticStart, double tic) {
        super();
        this.plotSheet = plotSheet;
        this.ticStart = ticStart;
        this.tic = tic;
    }


    @Override
    public void paint(GraphicsWrap g) {

        this.xLength = Math.max(Math.abs(plotSheet.getxRange()[0]), Math.abs(plotSheet.getxRange()[1]));
        double yLength = Math.max(Math.abs(plotSheet.getyRange()[0]), Math.abs(plotSheet.getyRange()[1]));


        ColorWrap oldColor = g.getColor();
        RectangleWrap field = g.getClipBounds();
        g.setColor(color);
        int tics = (int) ((this.ticStart - (0 - yLength)) / tic);
        double downStart = this.ticStart - this.tic * tics;

        if (downStart < 0) {
            while (downStart < 0) {
                downStart += this.tic;
            }
        }
        double currentY = downStart;

        while (currentY <= yLength) {
            drawGridLine(currentY, g, field);
            currentY += this.tic;
        }
        g.setColor(oldColor);

    }


    /**
     * Draw a grid line in specified graphics object
     *
     * @param y     x-position the vertical line shall be drawn
     * @param g     graphic the line shall be drawn onto
     * @param field definition of the graphic boundaries
     */
    private void drawGridLine(double y, GraphicsWrap g, RectangleWrap field) {
        g.drawLine(plotSheet.xToGraphic(0, field), plotSheet.yToGraphic(y, field),
                plotSheet.xToGraphic(-this.xLength, field), plotSheet.yToGraphic(y, field));
        g.drawLine(plotSheet.xToGraphic(0, field), plotSheet.yToGraphic(y, field),
                plotSheet.xToGraphic(this.xLength, field), plotSheet.yToGraphic(y, field));
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
}
