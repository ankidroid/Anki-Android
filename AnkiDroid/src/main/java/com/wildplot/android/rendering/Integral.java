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
import com.wildplot.android.rendering.interfaces.Function2D;


/**
 * Integral marks the region between x axis and a function or another function for a given interval 
 *
 */
public class Integral implements Drawable {
	
	private Function2D function = null;
	
	private Function2D function2 = null;
	
	
	private PlotSheet plotSheet;
	
	private double start = 0;
	
	private double end = Math.PI;
	
	private ColorWrap color = new ColorWrap(0.7f, 1f, 0f, 0.4f);
	
	/**
	 * set the color for integral area
	 * @param color integral area color
	 */
	public void setColor(ColorWrap color) {
		this.color = color;
	}

	/**
	 * Constructor for Integral object for integral between a function and x-axis
	 * @param function given function for the integral
	 * @param plotSheet the sheet the integral will be drawn onto
	 * @param start starting position
	 * @param end ending position
	 */
	public Integral(Function2D function, PlotSheet plotSheet, double start, double end) {
		super();
		this.function = function;
		this.plotSheet = plotSheet;
		this.start = start;
		this.end = end;
	}
	
	/**
	 * Constructor for Integral object between two functions
	 * @param function given function for the integral
	 * @param function2 second given function for the integral
	 * @param plotSheet the sheet the integral will be drawn onto
	 * @param start starting position
	 * @param end ending position
	 */
	public Integral(Function2D function, Function2D function2, PlotSheet plotSheet, double start, double end) {
		super();
		this.function = function;
		this.function2 = function2;
		this.plotSheet = plotSheet;
		this.start = start;
		this.end = end;
	}


	/* (non-Javadoc)
	 * @see rendering.Drawable#paint(java.awt.Graphics)
	 */
	@Override
	public void paint(GraphicsWrap g) {
		
		ColorWrap oldColor = g.getColor();
		RectangleWrap field = g.getClipBounds();
		g.setColor(color);

        float[] startPoint 	= plotSheet.toGraphicPoint(this.start, 0, field);
        float[] endPoint 		= plotSheet.toGraphicPoint(this.end, 0, field);
		
		for(int i = Math.round(startPoint[0]); i<=endPoint[0];i++) {
			double currentX = plotSheet.xToCoordinate(i, field);
			double currentY = function.f(currentX);
			
			if(this.function2 != null){
				double currentY2 = function2.f(currentX);
				g.drawLine(plotSheet.xToGraphic(currentX, field), plotSheet.yToGraphic(currentY, field), plotSheet.xToGraphic(currentX, field), plotSheet.yToGraphic(currentY2, field));
			}else {
				g.drawLine(plotSheet.xToGraphic(currentX, field), plotSheet.yToGraphic(currentY, field), plotSheet.xToGraphic(currentX, field), plotSheet.yToGraphic(0, field));
			}
			
			
			
		}
		

		g.setColor(oldColor);

	}
	
	/*
	 * (non-Javadoc)
	 * @see rendering.Drawable#isOnFrame()
	 */
	public boolean isOnFrame() {
		return false;
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
}
