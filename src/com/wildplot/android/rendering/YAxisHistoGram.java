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
 * Histogram plot, detailed informations regarding histograms are available in the internets
 * 
 *
 */
public class YAxisHistoGram implements Drawable {
	
	private double extraScaleFactor = 1;
	
	private boolean autoscale = false;
	
	private double scaleFactor = 10;
	
	private boolean isOnFrame = false;
	
	private double xOffset = 0;
	
	private PlotSheet plotSheet;
	
	private double[][] points;
	
	private double start = 0;
	private double binSize = 1;
	private double size = 1;
	
	private ColorWrap color;
	
	private ColorWrap fillColor;
	
	private boolean filling = false;

    private boolean isOnReset = false;

	/**
	 * @param plotSheet
	 * @param points the points used for calculating histogram data
	 * @param start relative start position of histogram bars the other bars will be aligned to
	 * @param size size of bars from left to right
	 * @param color border color of bars, for filling color use setFilling() and setFillingColor()
	 */
	public YAxisHistoGram(PlotSheet plotSheet, double[][] points, double start, double size, ColorWrap color) {
		super();
		this.plotSheet = plotSheet;
		this.points = points;
		this.start = start;
		this.size = size;
		this.color = color;
	}
	
	/**
	 * determine if bars are filled with color or not
	 * @param filling true if bars should be filled 
	 */
	public void setFilling(boolean filling) {
		this.filling = filling;
		if(this.fillColor == null && filling) {
			this.fillColor = this.color.brighter();
		}
	}
	
	/**
	 * set filling color for bars
	 * @param fillColor
	 */
	public void setFillColor(ColorWrap fillColor) {
		this.fillColor = fillColor;
	}

	/* (non-Javadoc)
	 * @see rendering.Drawable#paint(java.awt.Graphics)
	 */
	@Override
	public void paint(GraphicsWrap g) {
        isOnReset = false;
		
		ColorWrap oldColor = g.getColor();
		RectangleWrap field = g.getClipBounds();
		g.setColor(color);
		
		if(autoscale){
			double[] start = this.plotSheet.toCoordinatePoint(0, 0, field);
			double[] end = this.plotSheet.toCoordinatePoint(
                    0+this.plotSheet.getFrameThickness()[PlotSheet.LEFT_FRAME_THICKNESS_INDEX], 0, field);
			
			this.scaleFactor = Math.abs(end[0] - start[0]);
//			this.scaleFactor *= binSize;
		} else {
			this.scaleFactor = 1.0;
		}
		
		
		if(this.isOnFrame)
			xOffset = plotSheet.getxRange()[0];
		
		
		double steps = this.size;
		
		double tmp =  (int)((0-plotSheet.getyRange()[0])/steps);
		tmp = (start - tmp*steps); 
		
		while(tmp <= plotSheet.getyRange()[1]) {
            if(isOnReset)
                return;

			double sizeInRange = getSizeInRange(tmp, tmp+size);
			if(sizeInRange != 0)
				drawBar(tmp, sizeInRange*scaleFactor*extraScaleFactor, g, field);
			tmp += steps;
			//System.err.println("xaxisHisto"+tmp + ": " + sizeInRange);
		}
		g.setColor(oldColor);
	}
	public double getMaxValue(){
		double max = Double.NEGATIVE_INFINITY;
		double steps = this.size;
		
		double tmp =  (int)((0-plotSheet.getyRange()[0])/steps);
		tmp = (start - tmp*steps); 
		
		while(tmp <= plotSheet.getyRange()[1]) {
			double sizeInRange = getSizeInRange(tmp, tmp+size);
			if(max < sizeInRange)
				max = sizeInRange;
			tmp += steps;
			//System.err.println("xaxisHisto"+tmp + ": " + sizeInRange);
		}
		return max;
	}
	
	/**
	 * draw a single bar at given coordinate and with the given height
	 * @param x coordinate on plot
	 * @param y height
	 * @param g graphic object used to draw this bar
	 * @param field bounds of plot
	 */
	private void drawBar(double x, double y, GraphicsWrap g, RectangleWrap field) {
		drawBar(x,y,g,field,this.size);
	}
	
	/**
	 * draw a single bar at given coordinate and with the given height with given specific size
	 * @param y coordinate on plot
	 * @param heigth height
	 * @param g graphic object used to draw this bar
	 * @param field bounds of plot
	 * @param size specific size (width) of this bar
	 */
	private void drawBar(double y, double heigth, GraphicsWrap g, RectangleWrap field, double size) {


        float[] pointUpLeft 		= plotSheet.toGraphicPoint(0,y+size , field);
        float[] pointUpRight 		= plotSheet.toGraphicPoint(0+heigth,y+size , field);
        float[] pointBottomLeft 	= plotSheet.toGraphicPoint(0,y , field);
		
		if(heigth < 0) {
			pointUpLeft 		= plotSheet.toGraphicPoint(0+heigth,y+size , field);
			pointUpRight 		= plotSheet.toGraphicPoint(0,y+size , field);
			pointBottomLeft 	= plotSheet.toGraphicPoint(0+heigth,y , field);
		}
		
		if(this.isOnFrame) {
			
			pointUpLeft 		= plotSheet.toGraphicPoint(this.xOffset-heigth,y+size , field);
			pointUpRight 		= plotSheet.toGraphicPoint(this.xOffset,y+size , field);
			pointBottomLeft 	= plotSheet.toGraphicPoint(this.xOffset-heigth,y , field);
			
		}
		
		
		if(filling){
			ColorWrap oldColor = g.getColor();
			if(this.fillColor != null)
				g.setColor(fillColor);
			

			g.fillRect(pointUpLeft[0], pointUpLeft[1], pointUpRight[0]-pointUpLeft[0], pointBottomLeft[1]-pointUpLeft[1]);

			//g.fillRect(pointUpLeft[0], pointUpLeft[1], pointUpRight[0]-pointUpLeft[0], pointBottomLeft[1]-pointUpLeft[1]);
			
			g.setColor(oldColor);
		}
		

		g.drawRect(pointUpLeft[0], pointUpLeft[1], pointUpRight[0]-pointUpLeft[0], pointBottomLeft[1]-pointUpLeft[1]);

		
//		g.drawLine(pointUpLeft[0], pointUpLeft[1], pointUpRight[0], pointUpRight[1]);
//		g.drawLine(pointUpLeft[0], pointUpLeft[1], pointBottomLeft[0], pointBottomLeft[1]);
//		g.drawLine(pointBottomRight[0], pointBottomRight[1], pointBottomLeft[0], pointBottomLeft[1]);
	}
	
	
	//TODO: schauen, ob es Regeln folgt, die Ronny spezifiziert hat
	/** 
	 * get height of bar for given classification
	 * @param leftBorder
	 * @param rightBorder
	 * @return
	 */
	private double getSizeInRange(double leftBorder, double rightBorder){
		//System.err.println("XHisto: " + leftBorder + " : "+ rightBorder);
		int cnt = 0;
		for(int i = 0; i<this.points[1].length; i++) {
			
			if(points[1][i] >= leftBorder && points[1][i] < rightBorder) {
				cnt++;
				//System.err.println("XHist cnt++");
			}
		}
		return (double)cnt/((double)points[1].length*this.size);
	}
	
	/**
	 * unset the axis to draw on the border between outer frame and plot
	 */
	public void unsetOnFrame() {
		this.isOnFrame = false;
		xOffset = 0;
	}
	
	public void setOnFrame(double extraSpace) {
		this.isOnFrame = true;
		xOffset = plotSheet.getxRange()[0]+ extraSpace;
	}
	
	public void setOnFrame() {
		setOnFrame(0);
	}
	
	/**
	 * returns if this histogram is can draw on the outer frame of plot
	 */
	public boolean isOnFrame() {
		return true;
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
