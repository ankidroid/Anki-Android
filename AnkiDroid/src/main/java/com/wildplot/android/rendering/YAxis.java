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

import com.wildplot.android.rendering.graphics.wrapper.FontMetricsWrap;
import com.wildplot.android.rendering.graphics.wrapper.GraphicsWrap;
import com.wildplot.android.rendering.graphics.wrapper.RectangleWrap;
import com.wildplot.android.rendering.interfaces.Drawable;

import java.text.DecimalFormat;

/**
 * This Class represents a Drawable x-axis
 */
@SuppressLint("NonPublicNonStaticFieldName")
public class YAxis implements Drawable {

    private boolean mHasNumbersRotated = false;

    private float maxTextWidth = 0;

    private boolean isIntegerNumbering = false;

    private boolean isOnRightSide = false;

    private final boolean isLog = false;

    /**
     * offset to move axis left or right
     */
    private double xOffset = 0;

    private String name = "Y";

    private boolean mHasName = false;

    /**
     * Format that is used to print numbers under markers
     */
    private final DecimalFormat df = new DecimalFormat("##0.0#");
    private final DecimalFormat dfScience = new DecimalFormat("0.0###E0");
    private final DecimalFormat dfInteger = new DecimalFormat("#.#");

    private boolean isScientific = false;
    /**
     * the PlotSheet object the x-axis is drawn onto
     */
    private final PlotSheet plotSheet;

    /**
     * the start of x-axis marker, used for relative alignment of further marks
     */
    private final double ticStart;

    /**
     * the space between two marks
     */
    private final double tic;

    /**
     * the space between two minor marks
     */
    private final double minorTic;

    /**
     * start of drawn x-axis
     */
    private double start = 0;

    /**
     * end of drawn x-axis
     */
    private double end = 100;

    /**
     * true if the marker should be drawn into the direction above the axis
     */
    private boolean markOnLeft = true;

    /**
     * true if the marker should be drawn into the direction under the axis
     */
    private boolean markOnRight = true;

    /**
     * length of a marker in pixel, length is only for one side
     */
    private final float markerLength = 5;

    private boolean isOnFrame = false;


    /**
     * Constructor for an Y-axis object
     *
     * @param plotSheet the sheet the axis will be drawn onto
     * @param ticStart  the start of the axis markers used for relative alignment of other markers
     * @param tic       the space between two markers
     */
    public YAxis(PlotSheet plotSheet, double ticStart, double tic, double minorTic) {
        this.plotSheet = plotSheet;
        this.ticStart = ticStart;
        this.tic = tic;
        this.minorTic = minorTic;
    }


    /*
     * (non-Javadoc)
     * @see rendering.Drawable#paint(java.awt.Graphics)
     */
    public void paint(GraphicsWrap g) {
        RectangleWrap field = g.getClipBounds();

        start = plotSheet.getyRange()[0];
        end = plotSheet.getyRange()[1];

        if (this.tic < 1e-2 || this.tic > 1e2) {
            this.isScientific = true;
        }

        float[] coordStart = plotSheet.toGraphicPoint(xOffset, start, field);
        float[] coordEnd = plotSheet.toGraphicPoint(xOffset, end, field);

        if (!this.isOnFrame) {
            g.drawLine(coordStart[0], coordStart[1], coordEnd[0], coordEnd[1]);
        }

        drawMarkers(g);
        drawMinorMarkers(g);
    }


    /**
     * draw markers on the axis
     *
     * @param g graphic object used for drawing
     */
    private void drawMarkers(GraphicsWrap g) {
        RectangleWrap field = g.getClipBounds();
        double yOffset = 0;

        float cleanSpace = 17; // space in pixel that will be unmarked on the end of the axis for arrow and description

        float tics = (int) ((this.ticStart - this.start) / tic);
        double leftStart = this.ticStart - this.tic * tics;

        double logStart = 0;
        if (this.isLog) {
            logStart = Math.ceil(Math.log10(this.start));
            leftStart = Math.pow(10, logStart++);
        }
        double currentY = leftStart;

        while (currentY <= this.end) {
            if ((!this.isOnFrame && plotSheet.yToGraphic(currentY, field) >= plotSheet.yToGraphic(this.end, field) + cleanSpace
                    && plotSheet.yToGraphic(currentY, field) <= plotSheet.yToGraphic(this.start, field) - cleanSpace
                    && plotSheet.yToGraphic(currentY, field) <= field.y + field.height - cleanSpace
                    && plotSheet.yToGraphic(currentY, field) >= field.y + cleanSpace) ||
                    (this.isOnFrame && currentY <= this.plotSheet.getyRange()[1] &&
                            currentY >= this.plotSheet.getyRange()[0])) {

                if (this.markOnRight) {
                    drawRightMarker(g, field, currentY);
                }
                if (this.markOnLeft) {
                    drawLeftMarker(g, field, currentY);
                }
                if (!(Math.abs(currentY) < yOffset + 0.001 && !this.isOnFrame)) {
                    if (isOnRightSide) {
                        drawNumberingOnRightSide(g, field, currentY);
                    } else {
                        drawNumbering(g, field, currentY);
                    }
                }
            }
            if (this.isLog) {
                currentY = Math.pow(10, logStart++);
            } else {
                currentY += this.tic;
            }
        }
        FontMetricsWrap fm = g.getFontMetrics();
        float width = fm.stringWidth(this.name);
        //arrow

        float[] arowheadPos = {plotSheet.xToGraphic(xOffset, field), plotSheet.yToGraphic(Math.min(plotSheet.getyRange()[1], this.end), field)};
        if (!this.isOnFrame) {
            g.drawLine(arowheadPos[0] - 1, arowheadPos[1] + 1, arowheadPos[0] - 3, arowheadPos[1] + 6);
            g.drawLine(arowheadPos[0] + 1, arowheadPos[1] + 1, arowheadPos[0] + 3, arowheadPos[1] + 6);
            if (mHasName) {
                g.drawString(this.name, arowheadPos[0] - 13 - width, arowheadPos[1] + 10);
            }
        } else {

            float spacerValue = maxTextWidth;
            if (mHasNumbersRotated) {
                spacerValue = g.getFontMetrics().getHeight();
            }

            g.save();
            float[] middlePosition = {plotSheet.xToGraphic(xOffset, field), plotSheet.yToGraphic(0, field)};
            if (isOnRightSide) {
                g.rotate(90, middlePosition[0] + spacerValue * 1.4f, field.height / 2 - width / 2);
                if (mHasName) {
                    g.drawString(this.name, middlePosition[0] + spacerValue * 1.4f, field.height / 2 - width / 2);
                }
            } else {
                g.rotate(-90, middlePosition[0] - spacerValue * 1.4f, field.height / 2 + width / 2);
                if (mHasName) {
                    g.drawString(this.name, middlePosition[0] - spacerValue * 1.4f, field.height / 2 + width / 2);
                }
            }
            g.restore();
        }
    }


    /**
     * draw number left to a marker
     *
     * @param g     graphic object used for drawing
     * @param field bounds of plot
     * @param position     position of number
     */
    private void drawNumbering(GraphicsWrap g, RectangleWrap field, double position) {

        double y = position;
        if (this.tic < 1 && Math.abs(ticStart - y) < this.tic * this.tic) {
            y = ticStart;
        }

        float[] coordStart = plotSheet.toGraphicPoint(xOffset, y, field);


        FontMetricsWrap fm = g.getFontMetrics();
        float fontHeight = fm.getHeight();
        String font = df.format(y);
        float width = fm.stringWidth(font);
        if (this.isScientific && !isIntegerNumbering) {

            font = dfScience.format(y);
            width = fm.stringWidth(font);
        } else if (isIntegerNumbering) {
            font = dfInteger.format(y);
            width = fm.stringWidth(font);
        }
        g.save();
        if (mHasNumbersRotated) {
            float[] middlePosition = {plotSheet.xToGraphic(xOffset, field), plotSheet.yToGraphic(y, field)};
            g.rotate(-90, middlePosition[0] - width * 0.1f, middlePosition[1] + width / 2.0f);
            g.drawString(font, middlePosition[0] - width * 0.1f, middlePosition[1] + width / 2.0f);
        } else {
            g.drawString(font, Math.round(coordStart[0] - width * 1.1f), Math.round(coordStart[1] + fontHeight * 0.4f));
        }

        g.restore();
        if (width > maxTextWidth) {
            maxTextWidth = width;
        }
    }


    /**
     * draw number left to a marker
     *
     * @param g     graphic object used for drawing
     * @param field bounds of plot
     * @param position     position of number
     */
    private void drawNumberingOnRightSide(GraphicsWrap g, RectangleWrap field, double position) {
        double y = position;
        if (this.tic < 1 && Math.abs(ticStart - y) < this.tic * this.tic) {
            y = ticStart;
        }

        float[] coordStart = plotSheet.toGraphicPoint(xOffset, y, field);
        FontMetricsWrap fm = g.getFontMetrics();
        float fontHeight = fm.getHeight();
        String font = df.format(y);
        float width = fm.stringWidth(font);
        g.save();
        if (this.isScientific && !isIntegerNumbering) {
            font = dfScience.format(y);
            width = fm.stringWidth(font);
        } else if (isIntegerNumbering) {
            font = dfInteger.format(y);
            width = fm.stringWidth(font);
        }
        if (mHasNumbersRotated) {
            float[] middlePosition = {plotSheet.xToGraphic(xOffset, field), plotSheet.yToGraphic(y, field)};
            g.rotate(90, middlePosition[0] + width * 0.1f, middlePosition[1] - width / 2.0f);
            g.drawString(font, middlePosition[0] + width * 0.1f, middlePosition[1] - width / 2.0f);
        } else {
            g.drawString(font, Math.round(coordStart[0] + width * 0.1f), Math.round(coordStart[1] + fontHeight * 0.4f));
        }
        g.restore();
        if (width > maxTextWidth) {
            maxTextWidth = width;
        }
    }


    /**
     * draws an left marker
     *
     * @param g     graphic object used for drawing
     * @param field bounds of plot
     * @param y     position of marker
     */
    private void drawLeftMarker(GraphicsWrap g, RectangleWrap field, double y) {

        float[] coordStart = plotSheet.toGraphicPoint(xOffset, y, field);
        float[] coordEnd = {coordStart[0] - this.markerLength, coordStart[1]};
        g.drawLine(coordStart[0], coordStart[1], coordEnd[0], coordEnd[1]);

    }


    /**
     * draws an right marker
     *
     * @param g     graphic object used for drawing
     * @param field bounds of plot
     * @param y     position of marker
     */
    private void drawRightMarker(GraphicsWrap g, RectangleWrap field, double y) {
        float[] coordStart = plotSheet.toGraphicPoint(xOffset, y, field);
        float[] coordEnd = {coordStart[0] + this.markerLength + 1, coordStart[1]};
        g.drawLine(coordStart[0], coordStart[1], coordEnd[0], coordEnd[1]);

    }


    /**
     * set the axis to draw on the border between outer frame and plot
     */
    public void setOnFrame() {
        this.isOnFrame = true;
        xOffset = plotSheet.getxRange()[0];
        markOnLeft = false;
    }


    public void setOnRightSideFrame() {
        this.isOnFrame = true;
        xOffset = plotSheet.getxRange()[1];
        markOnRight = false;
        isOnRightSide = true;
    }


    /*
     * (non-Javadoc)
     * @see rendering.Drawable#isOnFrame()
     */
    public boolean isOnFrame() {
        return isOnFrame;
    }


    /**
     * set name of axis
     *
     * @param name name of axis
     */
    public void setName(String name) {
        this.name = name;
        mHasName = !"".equals(name);
    }


    private void drawMinorMarkers(GraphicsWrap g) {
        RectangleWrap field = g.getClipBounds();

        int cleanSpace = 17; // space in pixel that will be unmarked on the end of the axis for arrow and description

        int tics = (int) ((this.ticStart - this.start) / tic);
        double leftStart = this.ticStart - this.tic * tics;

        int factor = 1;
        double logStart = 0;
        if (this.isLog) {
            logStart = Math.floor(Math.log10(this.start));
            leftStart = Math.pow(10, logStart) * factor++;
        }

        double currentY = leftStart;

        while (currentY <= this.end) {

            if ((!this.isOnFrame && plotSheet.yToGraphic(currentY, field) >= plotSheet.yToGraphic(this.end, field) + cleanSpace
                    && plotSheet.yToGraphic(currentY, field) <= plotSheet.yToGraphic(this.start, field) - cleanSpace
                    && plotSheet.yToGraphic(currentY, field) <= field.y + field.height - cleanSpace
                    && plotSheet.yToGraphic(currentY, field) >= field.y + cleanSpace) ||
                    (this.isOnFrame && currentY <= this.plotSheet.getyRange()[1] &&
                            currentY >= this.plotSheet.getyRange()[0])) {

                if (this.markOnRight) {
                    drawRightMinorMarker(g, field, currentY);
                }
                if (this.markOnLeft) {
                    drawLeftMinorMarker(g, field, currentY);
                }
            }
            if (this.isLog) {
                if (factor == 10) {
                    factor = 1;
                    logStart++;
                }
                currentY = Math.pow(10, logStart) * factor++;
            } else {
                currentY += minorTic;
            }
        }

    }


    /**
     * draws an left minor marker
     *
     * @param g     graphic object used for drawing
     * @param field bounds of plot
     * @param y     position of marker
     */
    private void drawLeftMinorMarker(GraphicsWrap g, RectangleWrap field, double y) {

        float[] coordStart = plotSheet.toGraphicPoint(xOffset, y, field);
        float[] coordEnd = {(float) (coordStart[0] - 0.5 * this.markerLength), coordStart[1]};
        g.drawLine(coordStart[0], coordStart[1], coordEnd[0], coordEnd[1]);

    }


    /**
     * draws an right minor marker
     *
     * @param g     graphic object used for drawing
     * @param field bounds of plot
     * @param y     position of marker
     */
    private void drawRightMinorMarker(GraphicsWrap g, RectangleWrap field, double y) {
        float[] coordStart = plotSheet.toGraphicPoint(xOffset, y, field);
        float[] coordEnd = {(float) (coordStart[0] + 0.5 * this.markerLength + 1), coordStart[1]};
        g.drawLine(coordStart[0], coordStart[1], coordEnd[0], coordEnd[1]);

    }


    public void setIntegerNumbering(boolean isIntegerNumbering) {
        this.isIntegerNumbering = isIntegerNumbering;
    }


    @Override
    public boolean isClusterable() {
        return true;
    }


    @Override
    public boolean isCritical() {
        return true;
    }


    public void setHasNumbersRotated() {
        mHasNumbersRotated = true;
    }
}
