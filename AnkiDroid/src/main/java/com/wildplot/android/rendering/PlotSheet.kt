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
import android.graphics.Typeface;

import com.wildplot.android.rendering.graphics.wrapper.ColorWrap;
import com.wildplot.android.rendering.graphics.wrapper.FontMetricsWrap;
import com.wildplot.android.rendering.graphics.wrapper.GraphicsWrap;
import com.wildplot.android.rendering.graphics.wrapper.RectangleWrap;
import com.wildplot.android.rendering.interfaces.Drawable;
import com.wildplot.android.rendering.interfaces.Legendable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Vector;

import timber.log.Timber;


/**
 * This is a sheet that is used to plot mathematical functions including coordinate systems and optional extras like
 * legends and descriptors. Additionally all conversions from image to plot coordinates are done here
 */
@SuppressLint("NonPublicNonStaticFieldName")
public class PlotSheet implements Drawable {

    protected Typeface typeface = Typeface.DEFAULT;

    private boolean hasTitle = false;

    private float fontSize = 10f;
    private boolean fontSizeSet = false;

    private ColorWrap backgroundColor = ColorWrap.white;
    protected ColorWrap textColor = ColorWrap.black;

    /**
     * title of plotSheet
     */
    protected String title = "PlotSheet";

    private boolean isBackwards = false;

    /**
     * thickness of frame in pixel
     */
    private float leftFrameThickness = 0;
    private float upperFrameThickness = 0;
    private float rightFrameThickness = 0;
    private float bottomFrameThickness = 0;

    public static final int LEFT_FRAME_THICKNESS_INDEX = 0;
    public static final int RIGHT_FRAME_THICKNESS_INDEX = 1;
    public static final int UPPER_FRAME_THICKNESS_INDEX = 2;
    public static final int BOTTOM_FRAME_THICKNESS_INDEX = 3;

    /**
     * states if there is a border between frame and plot
     */
    private boolean isBordered = true;

    //if class shold be made threadable for mulitplot mode, than
    //this must be done otherwise
    /**
     * screen that is currently rendered
     */
    private final int currentScreen = 0;

    /**
     * the ploting screens, screen 0 is the only one in single mode
     */
    private final Vector<MultiScreenPart> screenParts = new Vector<>();

    //Use LinkedHashMap so that the legend items will be displayed in the order
    //in which they were added
    private final Map<String, ColorWrap> mLegendMap = new LinkedHashMap<>();
    private boolean mDrawablesPrepared = false;


    /**
     * Create a virtual sheet used for the plot
     *
     * @param xStart    the start of the x-range
     * @param xEnd      the end of the x-range
     * @param yStart    the start of the y-range
     * @param yEnd      the end of the y-range
     * @param drawables list of Drawables that shall be drawn onto the sheet
     */
    public PlotSheet(double xStart, double xEnd, double yStart, double yEnd, Vector<Drawable> drawables) {
        double[] xRange = {xStart, xEnd};
        double[] yRange = {yStart, yEnd};
        screenParts.add(0, new MultiScreenPart(xRange, yRange, drawables));
    }


    /**
     * Create a virtual sheet used for the plot
     *
     * @param xStart the start of the x-range
     * @param xEnd   the end of the x-range
     * @param yStart the start of the y-range
     * @param yEnd   the end of the y-range
     */
    public PlotSheet(double xStart, double xEnd, double yStart, double yEnd) {
        double[] xRange = {xStart, xEnd};
        double[] yRange = {yStart, yEnd};
        screenParts.add(0, new MultiScreenPart(xRange, yRange));

    }


    /**
     * add another Drawable object that shall be drawn onto the sheet
     * this adds only drawables for the first screen in multimode plots for
     *
     * @param draw Drawable object which will be addet to plot sheet
     */
    public void addDrawable(Drawable draw) {
        this.screenParts.get(0).addDrawable(draw);
        mDrawablesPrepared = false;
    }


    /**
     * Converts a given x coordinate from plotting field coordinate to a graphic field coordinate
     *
     * @param x     given graphic x coordinate
     * @param field the graphic field
     * @return the converted x value
     */
    public float xToGraphic(double x, RectangleWrap field) {

        double xQuotient = (field.width - leftFrameThickness - rightFrameThickness) /
                (Math.abs(this.screenParts.get(currentScreen).getxRange()[1] -
                        this.screenParts.get(currentScreen).getxRange()[0]));
        double xDistanceFromLeft = x - this.screenParts.get(currentScreen).getxRange()[0];

        return field.x + leftFrameThickness + (float) (xDistanceFromLeft * xQuotient);
    }


    /**
     * Converts a given y coordinate from plotting field coordinate to a graphic field coordinate.
     *
     * @param y     given graphic y coordinate
     * @param field the graphic field
     * @return the converted y value
     */
    public float yToGraphic(double y, RectangleWrap field) {

        double yQuotient = (field.height - upperFrameThickness - bottomFrameThickness) /
                (Math.abs(this.screenParts.get(currentScreen).getyRange()[1] -
                        this.screenParts.get(currentScreen).getyRange()[0]));

        double yDistanceFromTop = this.screenParts.get(currentScreen).getyRange()[1] - y;

        return (float) (field.y + upperFrameThickness + yDistanceFromTop * yQuotient);
    }


    /**
     * Convert a coordinate system point to a point used for graphical processing (with hole pixels)
     *
     * @param x     given x-coordinate
     * @param y     given y-coordinate
     * @param field clipping bounds for drawing
     * @return the point in graphical coordinates
     */
    public float[] toGraphicPoint(double x, double y, RectangleWrap field) {
        return new float[] {xToGraphic(x, field), yToGraphic(y, field)};
    }


    public void paint(GraphicsWrap g) {

        RectangleWrap field = g.getClipBounds();
        prepareDrawables();
        Vector<Drawable> offFrameDrawables = new Vector<>();
        Vector<Drawable> onFrameDrawables = new Vector<>();


        g.setTypeface(typeface);
        g.setColor(backgroundColor);
        g.fillRect(0, 0, field.width, field.height);
        g.setColor(ColorWrap.BLACK);


        if (fontSizeSet) {
            g.setFontSize(fontSize);
        }

        if ((this.screenParts.get(0).getDrawables() != null) && (this.screenParts.get(0).getDrawables().size() != 0)) {
            for (Drawable draw : this.screenParts.get(0).getDrawables()) {
                if (!draw.isOnFrame()) {
                    offFrameDrawables.add(draw);
                } else {
                    onFrameDrawables.add(draw);
                }
            }
        }

        for (Drawable offFrameDrawing : offFrameDrawables) {
            offFrameDrawing.paint(g);
        }

        //paint white frame to over paint everything that was drawn over the border 
        ColorWrap oldColor = g.getColor();
        if (leftFrameThickness > 0 || rightFrameThickness > 0 || upperFrameThickness > 0 || bottomFrameThickness > 0) {
            g.setColor(backgroundColor);
            //upper frame
            g.fillRect(0, 0, field.width, upperFrameThickness);

            //left frame
            g.fillRect(0, upperFrameThickness, leftFrameThickness, field.height);

            //right frame
            g.fillRect(field.width + 1 - rightFrameThickness, upperFrameThickness, rightFrameThickness +
                    leftFrameThickness, field.height - bottomFrameThickness);

            //bottom frame
            g.fillRect(leftFrameThickness, field.height - bottomFrameThickness,
                    field.width - rightFrameThickness, bottomFrameThickness + 1);

            //make small black border frame
            if (isBordered) {
                drawBorder(g, field);
            }

            g.setColor(oldColor);

            if (hasTitle) {
                drawTitle(g, field);
            }

            List<String> keyList = new Vector<>(mLegendMap.keySet());

            if (isBackwards) {
                Collections.reverse(keyList);
            }

            float oldFontSize = g.getFontSize();
            g.setFontSize(oldFontSize * 0.9f);
            FontMetricsWrap fm = g.getFontMetrics();
            float height = fm.getHeight();
            float spacerValue = height * 0.5f;
            float xPointer = spacerValue;
            float ySpacer = spacerValue;


            int legendCnt = 0;
            Timber.d("should draw legend now, number of legend entries: %d", mLegendMap.size());

            for (String legendName : keyList) {

                float stringWidth = fm.stringWidth(" : " + legendName);

                ColorWrap color = mLegendMap.get(legendName);
                g.setColor(color);

                if (legendCnt++ != 0 && xPointer + height * 2.0f + stringWidth >= field.width) {
                    xPointer = spacerValue;
                    ySpacer += height + spacerValue;
                }
                g.fillRect(xPointer, ySpacer, height, height);
                g.setColor(textColor);

                g.drawString(" : " + legendName, xPointer + height, ySpacer + height);
                xPointer += height * 1.3f + stringWidth;
                Timber.d("drawing a legend Item: (%s) %d, x: %,.2f , y: %,.2f", legendName, legendCnt, xPointer + height, ySpacer + height);

            }
            g.setFontSize(oldFontSize);
            g.setColor(textColor);
        }

        for (Drawable onFrameDrawing : onFrameDrawables) {
            onFrameDrawing.paint(g);
        }

    }


    private void drawBorder(GraphicsWrap g, RectangleWrap field) {
        g.setColor(ColorWrap.black);
        //upper border
        int borderThickness = 1;
        g.fillRect(leftFrameThickness - borderThickness + 1, upperFrameThickness - borderThickness + 1,
                field.width - leftFrameThickness - rightFrameThickness + 2 * borderThickness - 2, borderThickness);

        //lower border
        g.fillRect(leftFrameThickness - borderThickness + 1, field.height - bottomFrameThickness,
                field.width - leftFrameThickness - rightFrameThickness + 2 * borderThickness - 2, borderThickness);

        //left border
        g.fillRect(leftFrameThickness - borderThickness + 1, upperFrameThickness - borderThickness + 1,
                borderThickness, field.height - upperFrameThickness - bottomFrameThickness + 2 * borderThickness - 2);

        //right border
        g.fillRect(field.width - rightFrameThickness, upperFrameThickness - borderThickness + 1,
                borderThickness, field.height - upperFrameThickness - bottomFrameThickness + 2 * borderThickness - 2);
    }


    private void drawTitle(GraphicsWrap g, RectangleWrap field) {
        float oldFontSize = g.getFontSize();
        float newFontSize = oldFontSize * 2;
        g.setFontSize(newFontSize);
        FontMetricsWrap fm = g.getFontMetrics();
        float height = fm.getHeight();

        float width = fm.stringWidth(this.title);
        g.drawString(this.title, field.width / 2 - width / 2, upperFrameThickness - 10 - height);
        g.setFontSize(oldFontSize);
    }


    /**
     * sort runnables and group them together to use lesser threads
     */
    private void prepareDrawables() {

        if (!mDrawablesPrepared) {
            mDrawablesPrepared = true;
            Vector<Drawable> drawables = this.screenParts.get(0).getDrawables();
            Vector<Drawable> onFrameDrawables = new Vector<>();
            Vector<Drawable> offFrameDrawables = new Vector<>();

            DrawableContainer onFrameContainer = new DrawableContainer(true, false);

            DrawableContainer offFrameContainer = new DrawableContainer(false, false);
            for (Drawable drawable : drawables) {
                if (drawable instanceof Legendable && ((Legendable) drawable).nameIsSet()) {
                    ColorWrap color = ((Legendable) drawable).getColor();
                    String name = ((Legendable) drawable).getName();
                    mLegendMap.put(name, color);
                }
                if (drawable.isOnFrame()) {
                    if (drawable.isClusterable()) {
                        if (onFrameContainer.isCritical() != drawable.isCritical()) {
                            if (onFrameContainer.getSize() > 0) {
                                onFrameDrawables.add(onFrameContainer);
                            }
                            onFrameContainer = new DrawableContainer(true, drawable.isCritical());
                        }
                        onFrameContainer.addDrawable(drawable);
                    } else {
                        if (onFrameContainer.getSize() > 0) {
                            onFrameDrawables.add(onFrameContainer);
                        }
                        onFrameDrawables.add(drawable);
                        onFrameContainer = new DrawableContainer(true, false);

                    }
                } else {
                    if (drawable.isClusterable()) {
                        if (offFrameContainer.isCritical() != drawable.isCritical()) {
                            if (offFrameContainer.getSize() > 0) {
                                offFrameDrawables.add(offFrameContainer);
                            }
                            offFrameContainer = new DrawableContainer(false, drawable.isCritical());
                        }
                        offFrameContainer.addDrawable(drawable);
                    } else {
                        if (offFrameContainer.getSize() > 0) {
                            offFrameDrawables.add(offFrameContainer);
                        }
                        offFrameDrawables.add(drawable);
                        offFrameContainer = new DrawableContainer(false, false);
                    }
                }
            }
            if (onFrameContainer.getSize() > 0) {
                onFrameDrawables.add(onFrameContainer);
            }

            if (offFrameContainer.getSize() > 0) {
                offFrameDrawables.add(offFrameContainer);
            }

            this.screenParts.get(0).getDrawables().removeAllElements();
            this.screenParts.get(0).getDrawables().addAll(offFrameDrawables);
            this.screenParts.get(0).getDrawables().addAll(onFrameDrawables);

        }
    }


    /**
     * the x-range for the plot
     *
     * @return double array in the lenght of two with the first element beeingt left and the second element beeing the right border
     */
    public double[] getxRange() {
        return this.screenParts.get(0).getxRange();
    }


    /**
     * the <-range for the plot
     *
     * @return double array in the lenght of two with the first element being lower and the second element being the upper border
     */
    public double[] getyRange() {
        return this.screenParts.get(0).getyRange();
    }


    /**
     * returns the size in pixel of the outer frame
     *
     * @return the size of the outer frame for left, right, upper and bottom frame
     */
    public float[] getFrameThickness() {
        return new float[] {leftFrameThickness, rightFrameThickness, upperFrameThickness, bottomFrameThickness};
    }


    /**
     * set the size of the outer frame in pixel
     */
    public void setFrameThickness(float leftFrameThickness, float rightFrameThickness, float upperFrameThickness, float bottomFrameThickness) {
        if (leftFrameThickness < 0 || rightFrameThickness < 0 || upperFrameThickness < 0 || bottomFrameThickness < 0) {
            Timber.e("PlotSheet:Error::Wrong Frame size (smaller than 0)");
            this.leftFrameThickness = this.rightFrameThickness = this.upperFrameThickness = this.bottomFrameThickness = 0;
        }
        this.leftFrameThickness = leftFrameThickness;
        this.rightFrameThickness = rightFrameThickness;
        this.upperFrameThickness = upperFrameThickness;
        this.bottomFrameThickness = bottomFrameThickness;
    }


    /**
     * deactivates the border between outer frame and plot
     */
    public void unsetBorder() {
        this.isBordered = false;
    }


    public boolean isOnFrame() {
        return false;
    }


    /**
     * set the title of the plot
     *
     * @param title title string shown above plot
     */
    public void setTitle(String title) {
        this.title = title;
        this.hasTitle = true;
    }


    @Override
    public boolean isClusterable() {
        return true;
    }


    @Override
    public boolean isCritical() {
        return false;
    }


    public void setTypeface(Typeface typeface) {
        this.typeface = typeface;
    }


    /**
     * Show the legend items in reverse order of the order in which they were added.
     *
     * @param isBackwards If true, the legend items are shown in reverse order.
     */
    public void setIsBackwards(boolean isBackwards) {
        this.isBackwards = isBackwards;
    }


    public void setFontSize(float fontSize) {
        fontSizeSet = true;
        this.fontSize = fontSize;
    }


    public void setBackgroundColor(ColorWrap backgroundColor) {
        this.backgroundColor = backgroundColor;
    }


    public void setTextColor(ColorWrap textColor) {
        this.textColor = textColor;
    }
}
