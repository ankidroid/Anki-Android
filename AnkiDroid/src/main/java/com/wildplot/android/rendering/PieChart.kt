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
import com.wildplot.android.rendering.graphics.wrapper.FontMetricsWrap;
import com.wildplot.android.rendering.graphics.wrapper.GraphicsWrap;
import com.wildplot.android.rendering.graphics.wrapper.RectangleWrap;
import com.wildplot.android.rendering.interfaces.Drawable;
import com.wildplot.android.rendering.interfaces.Legendable;

import androidx.annotation.NonNull;

@SuppressLint("NonPublicNonStaticFieldName")
public class PieChart implements Drawable, Legendable {
    // First sector starts at 12 o'clock.
    private static final float FIRST_SECTOR_OFFSET = -90;

    private final PlotSheet mPlotSheet;
    private final double[] mValues;
    private final ColorWrap[] mColors;

    private String mName = "";
    private boolean mNameIsSet = false;
    private final double[] mPercent;
    private double mSum;


    public PieChart(@NonNull PlotSheet plotSheet, double[] values, ColorWrap[] colors) {
        checkArguments(values, colors);
        mPlotSheet = plotSheet;
        mValues = values;
        mColors = colors;
        mPercent = new double[mValues.length];
        for (double v : mValues) {
            mSum += v;
        }

        double denominator = (mSum == 0) ? 1 : mSum;

        mPercent[0] = mValues[0] / denominator;
        for (int i = 1; i < mValues.length; i++) {
            mPercent[i] = mPercent[i - 1] + mValues[i] / denominator;
        }
    }


    private void checkArguments(double[] values, ColorWrap[] colors) {
        if (values.length != colors.length) {
            throw new IllegalArgumentException(
                    "The number of colors must match the number of values");
        }
    }


    @Override
    public boolean isOnFrame() {
        return false;
    }


    @Override
    public void paint(GraphicsWrap g) {
        //Do not show chart if segments are all zero
        if (mSum == 0) {
            return;
        }

        float maxSideBorders = Math.max(mPlotSheet.getFrameThickness()[PlotSheet.LEFT_FRAME_THICKNESS_INDEX],
                mPlotSheet.getFrameThickness()[PlotSheet.RIGHT_FRAME_THICKNESS_INDEX]);
        float maxUpperBottomBorders = Math.max(mPlotSheet.getFrameThickness()[PlotSheet.UPPER_FRAME_THICKNESS_INDEX],
                mPlotSheet.getFrameThickness()[PlotSheet.BOTTOM_FRAME_THICKNESS_INDEX]);

        float realBorder = Math.max(maxSideBorders, maxUpperBottomBorders) + 3;

        RectangleWrap field = g.getClipBounds();
        float diameter = Math.min(field.width, field.height) - 2 * realBorder;
        float xCenter = field.width / 2.0F;
        float yCenter = field.height / 2.0F;

        ColorWrap oldColor = g.getColor();

        drawSectors(g, diameter, xCenter, yCenter);
        drawSectorLabels(g, diameter, xCenter, yCenter);

        g.setColor(oldColor);
    }


    private void drawSectors(GraphicsWrap g, float diameter, float xCenter, float yCenter) {
        float left = xCenter - diameter / 2F;
        float top = yCenter - diameter / 2F;

        float currentAngle = FIRST_SECTOR_OFFSET;
        for (int i = 0; i < mPercent.length - 1; i++) {
            g.setColor(mColors[i]);
            float arcLength = (float) ((360 * mValues[i]) / mSum);
            g.fillArc(left, top, diameter, diameter, currentAngle, arcLength);
            currentAngle += arcLength;
        }

        //last one does need some corrections to fill a full circle:
        g.setColor(getLastSectorColor());
        g.fillArc(left, top, diameter, diameter, currentAngle,
                (360F + FIRST_SECTOR_OFFSET - currentAngle));

        g.setColor(ColorWrap.black);
        g.drawArc(left, top, diameter, diameter, 0, 360);
    }


    private ColorWrap getLastSectorColor() {
        return mColors[mColors.length - 1];
    }


    private void drawSectorLabels(GraphicsWrap g, float diameter, float xCenter, float yCenter) {
        ColorWrap labelBackground = new ColorWrap(0, 0, 0, 0.5f);
        for (int j = 0; j < mPercent.length; j++) {
            if (mValues[j] == 0) {
                continue;
            }
            double oldPercent = 0;
            if (j != 0) {
                oldPercent = mPercent[j - 1];
            }
            String text = "" + Math.round((((mPercent[j] - oldPercent)) * 100) * 100) / 100.0 + "%";
            float x = (float) (xCenter + Math.cos(-1 * ((oldPercent + (mPercent[j] - oldPercent) * 0.5) * 360 + FIRST_SECTOR_OFFSET) * Math.PI / 180.0) * 0.375 * diameter) - 20;
            float y = (float) (yCenter - Math.sin(-1 * ((oldPercent + (mPercent[j] - oldPercent) * 0.5) * 360 + FIRST_SECTOR_OFFSET) * Math.PI / 180.0) * 0.375 * diameter);
            FontMetricsWrap fm = g.getFontMetrics();
            float width = fm.stringWidth(text);
            float height = fm.getHeight();
            g.setColor(labelBackground);
            g.fillRect(x - 1, y - height + 3, width + 2, height);
            g.setColor(ColorWrap.white);
            g.drawString(text, x, y);
        }
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
        return mColors.length > 0 ? mColors[0] : ColorWrap.WHITE;
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
