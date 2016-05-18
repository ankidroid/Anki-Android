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


import android.graphics.Typeface;

import com.wildplot.android.rendering.graphics.wrapper.*;
import com.wildplot.android.rendering.interfaces.Drawable;
import com.wildplot.android.rendering.interfaces.Legendable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Vector;

import timber.log.Timber;


/**
 * This is a sheet that is used to plot mathematical functions including coordinate systems and optional extras like 
 * legends and descriptors. Additionally all conversions from image to plot coordinates are done here
 */
public class PlotSheet implements Drawable {
    protected boolean isLogX = false;
    protected Typeface typeface = Typeface.DEFAULT;

    protected boolean isLogY = false;
    protected boolean hasTitle = false;

    protected float fontSize = 10f;
    protected boolean fontSizeSet = false;

    protected ColorWrap backgroundColor = ColorWrap.white;
    protected ColorWrap textColor = ColorWrap.black;

    /**
     * title of plotSheet
     */
    protected String title = "PlotSheet";
    
    /**
     * not yet implemented
     */
    protected boolean isMultiMode = false;

    protected boolean isBackwards = false;

    /**
     * thickness of frame in pixel
     */
    protected float leftFrameThickness = 0;
    protected float upperFrameThickness = 0;
    protected float rightFrameThickness = 0;
    protected float bottomFrameThickness = 0;

    public static final int LEFT_FRAME_THICKNESS_INDEX = 0;
    public static final int RIGHT_FRAME_THICKNESS_INDEX = 1;
    public static final int UPPER_FRAME_THICKNESS_INDEX = 2;
    public static final int BOTTOM_FRAME_THICKNESS_INDEX = 3;
    
    /**
     * states if there is a border between frame and plot
     */
    protected boolean isBordered = true;
    
    /**
     * thickness of border in pixel, until now more than 1 may bring problems for axis drawing
     */
    protected float borderThickness = 1;
    
    //if class shold be made threadable for mulitplot mode, than
    //this must be done otherwise
    /**
     * screen that is currently rendered
     */
    protected int currentScreen = 0;
    
    /**
     * the ploting screens, screen 0 is the only one in single mode
     */
    Vector<MultiScreenPart> screenParts = new Vector<>();

    //Use LinkedHashMap so that the legend items will be displayed in the order
    //in which they were added
    private Map<String, ColorWrap> mLegendMap = new LinkedHashMap<>();
    private boolean mDrawablesPrepared = false;

    /**
     * Create a virtual sheet used for the plot
     * @param xStart the start of the x-range
     * @param xEnd the end of the x-range
     * @param yStart the start of the y-range
     * @param yEnd the end of the y-range
     * @param drawables list of Drawables that shall be drawn onto the sheet
     */
    public PlotSheet(double xStart, double xEnd, double yStart, double yEnd, Vector<Drawable> drawables) {
        double[] xRange = {xStart, xEnd};
        double[] yRange = {yStart, yEnd};
        screenParts.add(0, new MultiScreenPart(xRange, yRange, drawables));
    }
    
    /**
     * 
     * Create a virtual sheet used for the plot
     * @param xStart the start of the x-range
     * @param xEnd the end of the x-range
     * @param yStart the start of the y-range
     * @param yEnd the end of the y-range
     */
    public PlotSheet(double xStart, double xEnd, double yStart, double yEnd) {
        double[] xRange = {xStart, xEnd};
        double[] yRange = {yStart, yEnd};
        screenParts.add(0, new MultiScreenPart(xRange, yRange));
        
    }
    
    /**
     * update the x-Range of this PlotSheet
     * @param xStart left beginning of plot
     * @param xEnd right end of plot
     */
    public void updateX(double xStart, double xEnd) {
        double[] xRange = {xStart, xEnd};
        this.screenParts.get(0).setxRange(xRange);
    }
    
    /**
     * update the y-Range of this PlotSheet
     * @param yStart bottom beginning of plot
     * @param yEnd upper end of plot
     */
    public void updateY(double yStart, double yEnd) {
        double[] yRange = {yStart, yEnd};
        this.screenParts.get(0).setyRange(yRange);
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
     * converts a given x coordinate from ploting field coordinate to a graphic field coordinate
     * @param x given graphic x coordinate
     * @param field the graphic field
     * @return the converted x value
     */
    @Deprecated
    public float xToGraphic(double x, RectangleWrap field) {

        return (this.isLogX)?xToGraphicLog(x,field):xToGraphicLinear(x,field);
    }
    private float xToGraphicLinear(double x, RectangleWrap field) {
        double xQuotient = (field.width - leftFrameThickness -rightFrameThickness) /
                (Math.abs(this.screenParts.get(currentScreen).getxRange()[1] -
                        this.screenParts.get(currentScreen).getxRange()[0]));
        double xDistanceFromLeft = x - this.screenParts.get(currentScreen).getxRange()[0];
        
        return field.x + leftFrameThickness + (float)(xDistanceFromLeft * xQuotient);
    }
    private float xToGraphicLog(double x, RectangleWrap field) {
        double range = Math.log10(this.screenParts.get(currentScreen).getxRange()[1]) -
                Math.log10(this.screenParts.get(currentScreen).getxRange()[0]);

        return (float) (field.x + this.leftFrameThickness + (Math.log10(x) -
                Math.log10(this.screenParts.get(currentScreen).getxRange()[0]))/(range) *
                (field.width - leftFrameThickness - rightFrameThickness));
    }
    
    /**
     * 
     * converts a given y coordinate from ploting field coordinate to a graphic field coordinate
     * @param y given graphic y coordinate
     * @param field the graphic field
     * @return the converted y value
     */
    @Deprecated
    public float yToGraphic(double y, RectangleWrap field) {
        return (this.isLogY)?yToGraphicLog(y,field):yToGraphicLinear(y,field);
    }
    
    
    private float yToGraphicLinear(double y, RectangleWrap field) {
        double yQuotient = (field.height - upperFrameThickness - bottomFrameThickness) /
                (Math.abs(this.screenParts.get(currentScreen).getyRange()[1] -
                        this.screenParts.get(currentScreen).getyRange()[0]));

        double yDistanceFromTop = this.screenParts.get(currentScreen).getyRange()[1] - y;
        
        return (float)(field.y + upperFrameThickness + yDistanceFromTop * yQuotient);
    }
    private float yToGraphicLog(double y, RectangleWrap field) {
        
        
        return (float) (((Math.log10(y)-Math.log10(this.screenParts.get(currentScreen).getyRange()[0]))/
                (Math.log10(this.screenParts.get(currentScreen).getyRange()[1]) -
                        Math.log10(this.screenParts.get(currentScreen).getyRange()[0])))
                *(field.height - upperFrameThickness - bottomFrameThickness) -
                (field.height-upperFrameThickness - bottomFrameThickness))*(-1) + upperFrameThickness;
    }
    
    /**
     * Convert a coordinate system point to a point used for graphical processing (with hole pixels) 
     * @param x given x-coordinate
     * @param y given y-coordinate
     * @param field clipping bounds for drawing
     * @return the point in graphical coordinates
     */
    public float[] toGraphicPoint(double x, double y, RectangleWrap field) {
        float[] graphicPoint = {xToGraphic(x, field), yToGraphic(y, field)};
        return graphicPoint;
    }
    
    /**
     * Transforms a graphical x-value to a x-value from the plotting coordinate system.
     * This method should not be used for future compatibility as transformations in more complex coordinate systems 
     * cannot be done by only giving one coordinate
     * @param x graphical x-coordinate
     * @param field clipping bounds
     * @return x-coordinate in plotting coordinate system
     */
    @Deprecated
    public double xToCoordinate(float x, RectangleWrap field) {
        
        
        return (this.isLogX)?xToCoordinateLog(x,field):xToCoordinateLinear(x,field);
    }
    
    private double xToCoordinateLinear(float x, RectangleWrap field) {
        double xQuotient = (Math.abs(this.screenParts.get(currentScreen).getxRange()[1] -
                this.screenParts.get(currentScreen).getxRange()[0])) /
                (field.width- leftFrameThickness - rightFrameThickness);
        double xDistanceFromLeft = field.x - leftFrameThickness + x;
        
        return this.screenParts.get(currentScreen).getxRange()[0] + xDistanceFromLeft*xQuotient;
    }
    
    private double xToCoordinateLog(float x, RectangleWrap field) {
        double range = Math.log10(this.screenParts.get(currentScreen).getxRange()[1]) -
                Math.log10(this.screenParts.get(currentScreen).getxRange()[0]);
        
        return Math.pow(10, ((x- (field.x + leftFrameThickness))*1.0*(range) )/
                (field.width - leftFrameThickness -rightFrameThickness) +
                Math.log10(this.screenParts.get(currentScreen).getxRange()[0]) ) ;
    }
    
    
    /**
     * Transforms a graphical y-value to a y-value from the plotting coordinate system.
     * This method should not be used for future compatibility as transformations in more complex coordinate systems 
     * cannot be done by only giving one coordinate
     * @param y graphical y-coordinate
     * @param field clipping bounds
     * @return y-coordinate in plotting coordinate system
     */
    @Deprecated
    public double yToCoordinate(float y, RectangleWrap field) {
        
        
        return (this.isLogY)?yToCoordinateLog(y, field):yToCoordinateLinear(y, field);
    }
    
    public double yToCoordinateLinear(float y, RectangleWrap field) {
        double yQuotient = (Math.abs(this.screenParts.get(currentScreen).getyRange()[1] -
                this.screenParts.get(currentScreen).getyRange()[0])) /
                (field.height -upperFrameThickness -bottomFrameThickness);
        double yDistanceFromBottom = field.y + field.height - 1 - y -bottomFrameThickness;
        
        return this.screenParts.get(currentScreen).getyRange()[0] + yDistanceFromBottom*yQuotient;
    }
    
    public double yToCoordinateLog(float y, RectangleWrap field) {

        return Math.pow(10,
                ((y - upperFrameThickness + (field.height-upperFrameThickness-bottomFrameThickness))*(-1))/
                        ((field.height-upperFrameThickness-bottomFrameThickness))*
                        ((Math.log10(this.screenParts.get(currentScreen).getyRange()[1]) -
                                Math.log10(this.screenParts.get(currentScreen).getyRange()[0]))) +
                        Math.log10(this.screenParts.get(currentScreen).getyRange()[0]));
    }
    
    /**
     * Convert a graphical coordinate-system point to a point used for plotting processing 
     * @param x given graphical x
     * @param y given graphical y
     * @param field clipping bounds for drawing
     * @return the point in plotting coordinates
     */
    public double[] toCoordinatePoint(float x, float y, RectangleWrap field) {
        double[] coordinatePoint = {xToCoordinate(x, field), yToCoordinate(y, field)};
        
        return coordinatePoint;
    }

    /*
     * (non-Javadoc)
     * @see rendering.Drawable#paint(java.awt.Graphics)
     */
    public void paint(GraphicsWrap g) {
        //TODO insets
        if(this.isMultiMode) {
            drawMultiMode(g);           
        } else {
            drawSingleMode(g, 0);
        }
    }
    
    private void drawMultiMode(GraphicsWrap g) {
        //TODO
    }
    
    private void drawSingleMode(GraphicsWrap g, int screenNr) {
        RectangleWrap field = g.getClipBounds();
        this.currentScreen = screenNr;
        prepareDrawables();
        Vector<Drawable> offFrameDrawables = new Vector<>();
        Vector<Drawable> onFrameDrawables = new Vector<>();


        g.setTypeface(typeface);
        g.setColor(backgroundColor);
        g.fillRect(0, 0, field.width, field.height);
        g.setColor(ColorWrap.BLACK);


        if(fontSizeSet) {
            g.setFontSize(fontSize);
        }
        int i = 0;
        
        if(this.screenParts.get(screenNr).getDrawables() != null &&
                this.screenParts.get(screenNr).getDrawables().size() != 0) {
            for(Drawable draw : this.screenParts.get(screenNr).getDrawables()) {
                if(!draw.isOnFrame()) {
                    offFrameDrawables.add(draw);
                } else {
                    onFrameDrawables.add(draw);
                }
            }
        }

        for(Drawable offFrameDrawing : offFrameDrawables){
            offFrameDrawing.paint(g);

        }

        //paint white frame to over paint everything that was drawn over the border 
        ColorWrap oldColor = g.getColor();
        if(leftFrameThickness>0 || rightFrameThickness > 0 || upperFrameThickness > 0 || bottomFrameThickness > 0){
            g.setColor(backgroundColor);
            //upper frame
            g.fillRect(0, 0, field.width, upperFrameThickness);

            //left frame
            g.fillRect(0, upperFrameThickness, leftFrameThickness, field.height);
            
            //right frame
            g.fillRect(field.width+1-rightFrameThickness, upperFrameThickness,rightFrameThickness +
                    leftFrameThickness, field.height-bottomFrameThickness);
            
            //bottom frame
            //gFrame.setColor(Color.RED); //DEBUG
            g.fillRect(leftFrameThickness, field.height-bottomFrameThickness,
                    field.width-rightFrameThickness,bottomFrameThickness+1);
            
            //make small black border frame
            if(isBordered){
                g.setColor(ColorWrap.black);
                //upper border
                g.fillRect(leftFrameThickness-borderThickness+1, upperFrameThickness-borderThickness+1,
                        field.width-leftFrameThickness - rightFrameThickness +2*borderThickness-2, borderThickness);
                
                //lower border
                g.fillRect(leftFrameThickness-borderThickness+1, field.height-bottomFrameThickness,
                        field.width-leftFrameThickness -rightFrameThickness+2*borderThickness-2, borderThickness);
                
                //left border
                g.fillRect(leftFrameThickness-borderThickness+1, upperFrameThickness-borderThickness+1,
                        borderThickness, field.height-upperFrameThickness - bottomFrameThickness+2*borderThickness-2);
                
                //right border
                g.fillRect(field.width-rightFrameThickness, upperFrameThickness-borderThickness+1,
                        borderThickness, field.height-upperFrameThickness - bottomFrameThickness +2*borderThickness-2);
                
            }

            g.setColor(oldColor);
            
//          Font oldFont = gFrame.getFont();
//          gFrame.setFont(oldFont.deriveFont(20.0f));
            if(hasTitle) {
                float oldFontSize = g.getFontSize();
                float newFontSize = oldFontSize * 2;
                g.setFontSize(newFontSize);
                FontMetricsWrap fm = g.getFontMetrics();
                float height = fm.getHeight();

                float width = fm.stringWidth(this.title);
                g.drawString(this.title, field.width / 2 - width / 2, upperFrameThickness - 10 - height);
                g.setFontSize(oldFontSize);
            }

            List<String> keyList = new Vector<>(mLegendMap.keySet());

            if(isBackwards) {
                Collections.reverse(keyList);
            }

            float oldFontSize = g.getFontSize();
            g.setFontSize(oldFontSize* 0.9f);
            FontMetricsWrap fm = g.getFontMetrics();
            float height = fm.getHeight();
            float spacerValue = height * 0.5f;
            float xPointer = spacerValue;
            float ySpacer = spacerValue;
            float rectangleSize = height;

            float currentPixelWidth = xPointer;

            int legendCnt = 0;
            Timber.d("should draw legend now, number of legend entries: %d", mLegendMap.size());

            for(String legendName : keyList){

                float stringWidth = fm.stringWidth(" : "+legendName);

                float delta = rectangleSize - height;
                ColorWrap color = mLegendMap.get(legendName);
                g.setColor(color);

                if(legendCnt++ != 0 && xPointer + rectangleSize*2.0f + stringWidth >= field.width){
                    xPointer = spacerValue;
                    ySpacer += rectangleSize + spacerValue;
                }
                g.fillRect(xPointer, ySpacer, rectangleSize, rectangleSize);
                g.setColor(textColor);

                g.drawString(" : "+legendName, xPointer + rectangleSize , ySpacer+rectangleSize);
                xPointer += rectangleSize*1.3f + stringWidth;
                Timber.d("drawing a legend Item: (%s) %d, x: %,.2f , y: %,.2f", legendName, legendCnt, xPointer + rectangleSize, ySpacer+rectangleSize);

            }
            g.setFontSize(oldFontSize);
            //g.setColor(ColorWrap.BLACK);
            g.setColor(textColor);
//          gFrame.setFont(oldFont);
        }

        for(Drawable onFrameDrawing : onFrameDrawables){
            onFrameDrawing.paint(g);
        }
        
    }


    /**
     *sort runnables and group them together to use lesser threads
     */
    private void prepareDrawables(){

        if(!mDrawablesPrepared) {
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
                        if (onFrameContainer.isCritical() == drawable.isCritical()) {
                            onFrameContainer.addDrawable(drawable);
                        } else {
                            if (onFrameContainer.getSize() > 0) {
                                onFrameDrawables.add(onFrameContainer);
                            }
                            onFrameContainer = new DrawableContainer(true, drawable.isCritical());
                            onFrameContainer.addDrawable(drawable);
                        }
                    } else {
                        if (onFrameContainer.getSize() > 0) {
                            onFrameDrawables.add(onFrameContainer);
                        }
                        onFrameDrawables.add(drawable);
                        onFrameContainer = new DrawableContainer(true, false);

                    }
                } else {
                    if (drawable.isClusterable()) {
                        if (offFrameContainer.isCritical() == drawable.isCritical()) {
                            offFrameContainer.addDrawable(drawable);
                        } else {
                            if (offFrameContainer.getSize() > 0) {
                                offFrameDrawables.add(offFrameContainer);
                            }
                            offFrameContainer = new DrawableContainer(false, drawable.isCritical());
                            offFrameContainer.addDrawable(drawable);
                        }
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
     * @return double array in the lenght of two with the first element beeingt left and the second element beeing the right border
     */
    public double[] getxRange() {
        return this.screenParts.get(0).getxRange();
    }
    
    /**
     * sets new bounds for x coordinates on the plot
     * @param xRange double array in the length of two with the first element beeingt left and the second element beeing the right border
     */
    public void setxRange(double[] xRange) {
        this.screenParts.get(0).setxRange(xRange);
    }
    
    /**
     * the <-range for the plot
     * @return double array in the lenght of two with the first element being lower and the second element being the upper border
     */
    public double[] getyRange() {
        return this.screenParts.get(0).getyRange();
    }
    
    /**
     * sets new bounds for y coordinates on the plot
     * @param yRange double array in the length of two with the first element beeingt left and the second element beeing the right border
     */
    public void setyRange(double[] yRange) {
        this.screenParts.get(0).setyRange(yRange);
    }
    
    /**
     * returns the size in pixel of the outer frame
     * @return the size of the outer frame for left, right, upper and bottom frame
     */
    public float[] getFrameThickness() {
        return new float[]{leftFrameThickness, rightFrameThickness, upperFrameThickness, bottomFrameThickness};
    }
    
    /**
     * set the size of the outer frame in pixel
     */
    public void setFrameThickness(float leftFrameThickness, float rightFrameThickness, float upperFrameThickness, float bottomFrameThickness) {
        if(leftFrameThickness < 0 ||rightFrameThickness < 0 || upperFrameThickness < 0 || bottomFrameThickness < 0){
            System.err.println("PlotSheet:Error::Wrong Frame size (smaller than 0)");
            System.exit(-1);
        }
        this.leftFrameThickness = leftFrameThickness;
        this.rightFrameThickness = rightFrameThickness;
        this.upperFrameThickness = upperFrameThickness;
        this.bottomFrameThickness = bottomFrameThickness;
    }
    
    /**
     * sets the size of the border between plot and outer frame in pixel
     * @param borderThickness size of border in pixel
     */
    public void setBorderThickness(float borderThickness) {
        this.borderThickness = borderThickness;
        this.isBordered = true;
    }
    
    /**
     * activates the border between outer frame and plot
     */
    public void setBorder() {
        this.isBordered = true;
    }
    
    /**
     * deactivates the border between outer frame and plot
     */
    public void unsetBorder() {
        this.isBordered = false;
    }
    
    /*
     * (non-Javadoc)
     * @see rendering.Drawable#isOnFrame()
     */
    public boolean isOnFrame() {
        return false;
    }
    
    /**
     * this function calculates the best approximation for a 10based tic distance based on a given pixeldistance for x-axis tics
     * @param pixelDistance
     * @param field
     * @return
     */
    public double ticsCalcX(float pixelDistance, RectangleWrap field){
        double deltaRange = this.screenParts.get(currentScreen).getxRange()[1] - this.screenParts.get(currentScreen).getxRange()[0];
        float ticlimit = field.width/pixelDistance;
        double tics = Math.pow(10, (int)Math.log10(deltaRange/ticlimit));
        while(2.0*(deltaRange/(tics)) <= ticlimit) {
            tics /= 2.0;
        }
        while((deltaRange/(tics))/2 >= ticlimit) {
            tics *= 2.0;
        }
        return tics;
    }
    /**
     * this function calculates the best approximation for a 10based tic distance based on a given pixeldistance for y-axis tics
     * @param pixelDistance
     * @param field
     * @return
     */
    public double ticsCalcY(float pixelDistance, RectangleWrap field){
        double deltaRange = this.screenParts.get(currentScreen).getyRange()[1] - this.screenParts.get(currentScreen).getyRange()[0];
        float ticlimit = field.height/pixelDistance;
        double tics = Math.pow(10, (int)Math.log10(deltaRange/ticlimit));
        while(2.0*(deltaRange/(tics)) <= ticlimit) {
            tics /= 2.0;
        }
        while((deltaRange/(tics))/2 >= ticlimit) {
            tics *= 2.0;
        }
        Timber.d("PlotSheet ticksCalcY: pixelDistance: %d, ticks: %d" + pixelDistance, tics);
        return tics;
    }
    
    /**
     * set the title of the plot
     * @param title title string shown above plot
     */
    public void setTitle(String title){
        this.title = title;
        this.hasTitle = true;
    }

    /**
     * @return the isMultiMode
     */
    public boolean isMultiMode() {
        return isMultiMode;
    }
    
    public void setLogX() {
        this.isLogX = true;
    }

    public void setLogY() {
        this.isLogY = true;
    }
    
    public void unsetLogX() {
        this.isLogX = false;
    }

    public void unsetLogY() {
        this.isLogY = false;
    }



    @Override
    public void abortAndReset() {
        // TODO Auto-generated method stub
        
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

    public void unsetFontSize() {
        fontSizeSet = false;

    }

    /**
     * Show the legend items in reverse order of the order in which they were added.
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
