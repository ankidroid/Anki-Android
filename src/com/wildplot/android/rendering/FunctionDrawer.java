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

import com.wildplot.android.rendering.graphics.wrapper.*;
import com.wildplot.android.rendering.interfaces.Drawable;
import com.wildplot.android.rendering.interfaces.Function2D;
import com.wildplot.android.rendering.interfaces.StepFunction2D;


/**
 * FunctionDrawer is used to draw a mathematical function on a given PlotSheet
 * 
 * 
 */
public class FunctionDrawer implements Drawable {
	
	private boolean isStepFunction = false;
	
	private double extraScaleFactor = 1;
	
	private boolean autoscale = false;
	
	private double scaleFactor = 10;
	private double binSize = 1;
	
	private boolean isOnFrame = false;
	
	private double yOffset = 0;
	
	private float size = 1;
	
	private double leftLimit = 0;
	private double rightLimit = 0;
	
	private boolean hasLimit = false;
	
	/**
	 * true when warning for pole positions is allready given
	 */
	private boolean warned = false;
	
	/**
	 * the function which will be plotted with this FunctionDrawer
	 */
	private Function2D function;
	
	/**
	 * the PlotSheet on which this FunctionDrawer is drawing upon
	 */
	private PlotSheet plotSheet;
	
	/**
	 * the color of the function graph
	 */
	private ColorWrap color = new ColorWrap(255,0,0);

    private boolean isOnReset = false;

    /**
	 * Constructor for a FunctionDrawer object
	 * @param function given function which is drawn
	 * @param plotSheet the sheet the function will be drawn onto
	 * @param color color of the function
	 */
	public FunctionDrawer(Function2D function, PlotSheet plotSheet, ColorWrap color) {
		this.function = function;
		this.plotSheet = plotSheet;
		this.color = color;
	}
	
	/**
	 * Constructor for a FunctionDrawer object
	 * @param function given function which is drawn
	 * @param plotSheet the sheet the function will be drawn onto
	 * @param color color of the function
	 */
	public FunctionDrawer(Function2D function, PlotSheet plotSheet, ColorWrap color, double leftLimit, double rightLimit) {
		this.function = function;
		this.plotSheet = plotSheet;
		this.color = color;
		this.hasLimit = true;
		this.leftLimit = leftLimit;
		this.rightLimit = rightLimit;
	}
	
	/* (non-Javadoc)
	 * @see rendering.Drawable#paint(java.awt.Graphics)
	 */
	@Override
	public void paint(GraphicsWrap g) {

        isOnReset = false;
		if(function instanceof StepFunction2D) {
			this.isStepFunction = true;
		}
		

		 StrokeWrap oldStroke = g.getStroke();
		    g.setStroke(new BasicStrokeWrap(this.size));  // set stroke width of 10
		 
		ColorWrap oldColor = g.getColor();
		RectangleWrap field = g.getClipBounds();
		g.setColor(color);
		
		
		if(autoscale){
			double[] start = this.plotSheet.toCoordinatePoint(0, 0, field);
			double[] end = this.plotSheet.toCoordinatePoint(0,
                    0+this.plotSheet.getFrameThickness()[PlotSheet.UPPER_FRAME_THICKNESS_INDEX], field);
			
			this.scaleFactor = Math.abs(end[1] - start[1]);
//			this.scaleFactor *= binSize;
		} else {
			this.scaleFactor = 1.0;
		}
		
		if(this.isOnFrame)
			yOffset = plotSheet.getyRange()[0];
		
		double[] drawingPoint = plotSheet.toCoordinatePoint(field.x,0,field);
		if(this.isOnFrame)
			drawingPoint = plotSheet.toCoordinatePoint(field.x+this.plotSheet.getFrameThickness()[PlotSheet.LEFT_FRAME_THICKNESS_INDEX],0,field);
		
		double f_x = function.f(drawingPoint[0])*scaleFactor*extraScaleFactor;
		double f_x_old = f_x;

        float[] coordStart = plotSheet.toGraphicPoint(drawingPoint[0],f_x,field);
		if(this.isOnFrame)
			coordStart = plotSheet.toGraphicPoint(drawingPoint[0],this.yOffset-f_x,field);

        float[] coordEnd = coordStart;

        float leftStart = field.x+1;
        float rightEnd = field.width + field.x;
		if(this.isOnFrame){
			leftStart = field.x+this.plotSheet.getFrameThickness()[PlotSheet.LEFT_FRAME_THICKNESS_INDEX]+1;
			rightEnd = field.width + field.x-this.plotSheet.getFrameThickness()[PlotSheet.RIGHT_FRAME_THICKNESS_INDEX];
		}
		
		if(this.hasLimit){
			leftStart = plotSheet.xToGraphic(leftLimit, field);
			rightEnd = plotSheet.xToGraphic(rightLimit, field);
		}
		
		for(int i = Math.round(leftStart); i< rightEnd; i++) {
            if(isOnReset)
                return;
			drawingPoint = plotSheet.toCoordinatePoint(i,0,field);
			
			coordEnd = coordStart;
			
			f_x_old = f_x;
			f_x = function.f(drawingPoint[0])*scaleFactor*extraScaleFactor;
			coordStart = plotSheet.toGraphicPoint(drawingPoint[0],f_x,field);
			if(this.isOnFrame)
				coordStart = plotSheet.toGraphicPoint(drawingPoint[0],this.yOffset-f_x,field);
			
			double overlap = 0.2 * (plotSheet.getyRange()[1] - plotSheet.getyRange()[0]);
			
			if(f_x_old != Double.NaN && f_x!= Double.NaN &&
					f_x_old != Double.NEGATIVE_INFINITY && f_x!= Double.NEGATIVE_INFINITY &&
					f_x_old != Double.POSITIVE_INFINITY && f_x!= Double.POSITIVE_INFINITY &&
					f_x_old <= plotSheet.getyRange()[1] + overlap && f_x_old >= plotSheet.getyRange()[0] - overlap &&
					f_x <= plotSheet.getyRange()[1] + overlap && f_x >= plotSheet.getyRange()[0] - overlap) {
				
				g.drawLine(coordStart[0], coordStart[1], coordEnd[0], coordEnd[1]);

				
			} else if(!warned) {
				System.err.println("Could not draw part of function, possible pole or out of reach");
				warned = true;
			}
			
		}
		g.setStroke(oldStroke);
		g.setColor(oldColor);

	}
	
	public double getMaxValue(int pixelResolution){
		RectangleWrap field = new RectangleWrap(pixelResolution, pixelResolution);
		double[] drawingPoint = plotSheet.toCoordinatePoint(field.x,0,field);
		double max = Double.NEGATIVE_INFINITY;
		
		for(int i = 0; i< pixelResolution; i++) {
			drawingPoint = plotSheet.toCoordinatePoint(i,0,field);
			double f_x = function.f(drawingPoint[0]);
			if(f_x > max)
				max=f_x;
		}
		
		return max;
	}
	
	
	/**
	 * @param size the size to set
	 */
	public void setSize(float size) {
		this.size = size;
	}

	public boolean isOnFrame() {
		return this.isOnFrame;
	}
	
	/**
	 * unset the axis to draw on the border between outer frame and plot
	 */
	public void unsetOnFrame() {
		this.isOnFrame = false;
		yOffset = 0;
	}
	
	/**
	 * set the axis to draw on the border between outer frame and plot
	 */
	public void setOnFrame(double extraSpace) {
		this.isOnFrame = true;
		yOffset = plotSheet.getyRange()[0]-extraSpace;
	}
	
	public void setOnFrame() {
		setOnFrame(0);
	}
	
	public void setAutoscale(double binWidth) {
		this.binSize = binWidth;
		this.autoscale = true;
	}
	public void unsetAutoscale() {
		this.autoscale = false;
	}

	public double getExtraScaleFactor() {
		return extraScaleFactor;
	}

	public void setExtraScaleFactor(double extraScaleFactor) {
		this.extraScaleFactor = extraScaleFactor;
	}

	@Override
	public void abortAndReset() {
        isOnReset = true;
		
	}

    @Override
    public boolean isClusterable() {
        return true;
    }

    @Override
    public boolean isCritical() {
        return false;
    }


}
