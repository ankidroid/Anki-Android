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
 * This class represents grid lines parallel to the y-axis
 *
 */
public class YGrid implements Drawable {
	public boolean hasVariableLimits = true;
	
	private boolean isAutoTic = false;
	
	private int pixelDistance = 25;
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
	private double ticStart = 0;
	
	/**
	 * the space between two grid lines
	 */
	private double tic = 1;
	
	/**
	 * maximal distance from x axis the grid will be drawn
	 */
	private double xLength = 10;
	
	/**
	 * maximal distance from y axis the grid will be drawn
	 */
	private double yLength = 2;
	
	/**
	 * true if the grid should be drawn into the direction left to the axis
	 */
	private boolean gridkOnLeft = true;

	/**
	 * true if the grid should be drawn into the direction right to the axis
	 */
	private boolean gridOnRight = true;
	
	/**
	 * true if the grid should be drawn under the x-axis
	 */
	private boolean gridOnDownside = true;
	
	/**
	 * true if the grid should be drawn above the x-axis
	 */
	private boolean gridOnUpside = true;
    private double[] mTickPositions;

    /**
	 * Constructor for an Y-Grid object
	 * @param plotSheet the sheet the grid will be drawn onto
	 * @param ticStart start point for relative positioning of grid
	 * @param tic the space between two grid lines
	 */
	public YGrid(PlotSheet plotSheet, double ticStart, double tic) {
		super();
		this.plotSheet = plotSheet;
		this.ticStart = ticStart;
		this.tic = tic;
	}

	/**
	 * Constructor for an Y-Grid object
	 * @param color set color of the grid
	 * @param plotSheet the sheet the grid will be drawn onto
	 * @param ticStart start point for relative positioning of grid
	 * @param tic the space between two grid lines
	 */
	public YGrid(ColorWrap color, PlotSheet plotSheet, double ticStart, double tic) {
		super();
		this.color = color;
		this.plotSheet = plotSheet;
		this.ticStart = ticStart;
		this.tic = tic;
	}
	
	/**
	 * Constructor for an Y-Grid object
	 * @param plotSheet the sheet the grid will be drawn onto
	 * @param ticStart start point for relative positioning of grid
	 */
	public YGrid(PlotSheet plotSheet, double ticStart, int pixelDistance) {
		super();
		this.plotSheet = plotSheet;
		this.ticStart = ticStart;
		this.pixelDistance = pixelDistance;
        isAutoTic = true;
	}

	/**
	 * Constructor for an Y-Grid object
	 * @param color set color of the grid
	 * @param plotSheet the sheet the grid will be drawn onto
	 * @param ticStart start point for relative positioning of grid
	 */
	public YGrid(ColorWrap color, PlotSheet plotSheet, double ticStart, int pixelDistance) {
		super();
		this.color = color;
		this.plotSheet = plotSheet;
		this.ticStart = ticStart;
		this.pixelDistance = pixelDistance;
        isAutoTic = true;
	}

	/* (non-Javadoc)
	 * @see rendering.Drawable#paint(java.awt.Graphics)
	 */
	@Override
	public void paint(GraphicsWrap g) {
		ColorWrap oldColor = g.getColor();
		RectangleWrap field = g.getClipBounds();
		g.setColor(color);
		
		if(this.hasVariableLimits) {
			this.xLength = Math.max(Math.abs(plotSheet.getxRange()[0]), Math.abs(plotSheet.getxRange()[1]));
			this.yLength = Math.max(Math.abs(plotSheet.getyRange()[0]), Math.abs(plotSheet.getyRange()[1]));
		}
		if(this.isAutoTic)
			this.tic = plotSheet.ticsCalcX(pixelDistance, field);


        int tics = (int)((this.ticStart - (0-this.xLength))/tic);
		double leftStart = this.ticStart - this.tic*tics; 
		
		if(leftStart < 0 ) {
			if(!this.gridkOnLeft) {
				while(leftStart<0) {
					leftStart+=this.tic;
				}
			}
			
		}


        if(mTickPositions == null)
            drawImplicitLines(g, leftStart);
        else
            drawExplicitLines(g);


		//System.err.println("out of loop");
		g.setColor(oldColor);

	}
    private void drawImplicitLines(GraphicsWrap g, double leftStart){
        RectangleWrap field = g.getClipBounds();
        double currentX = leftStart;

        while(currentX <= this.xLength && !(currentX > 0 && !this.gridOnRight)) {
            drawGridLine(currentX, g, field);
            currentX+=this.tic;
            //System.err.println("another loop");
        }
    }

    private void drawExplicitLines(GraphicsWrap g){
        RectangleWrap field = g.getClipBounds();

		for (double currentX : mTickPositions) {
			drawGridLine(currentX, g, field);
		}
    }
	
	/**
	 * Draw a grid line in specified graphics object
	 * @param x x-position the vertical line shall be drawn
	 * @param g graphic the line shall be drawn onto
	 * @param field definition of the graphic boundaries
	 */
	private void drawGridLine(double x, GraphicsWrap g, RectangleWrap field) {
		if(this.gridOnUpside) {
			g.drawLine(plotSheet.xToGraphic(x, field), plotSheet.yToGraphic(0, field), plotSheet.xToGraphic(x, field), plotSheet.yToGraphic(yLength, field));
		}
		
		if(this.gridOnDownside) {
			g.drawLine(plotSheet.xToGraphic(x, field), plotSheet.yToGraphic(0, field), plotSheet.xToGraphic(x, field), plotSheet.yToGraphic(-yLength, field));
		}
	}
	
	/**
	 * true if the grid should be drawn into the direction left to the axis
	 */
	public void setGridkOnLeft(boolean gridkOnLeft) {
		this.gridkOnLeft = gridkOnLeft;
	}
	
	/**
	 * true if the grid should be drawn into the direction right to the axis
	 */
	public void setGridOnRight(boolean gridOnRight) {
		this.gridOnRight = gridOnRight;
	}
	
	/**
	 * true if the grid should be drawn under the x-axis
	 */
	public void setGridOnDownside(boolean gridOnDownside) {
		this.gridOnDownside = gridOnDownside;
	}

	/**
	 * true if the grid should be drawn above the x-axis
	 */
	public void setGridOnUpside(boolean gridOnUpside) {
		this.gridOnUpside = gridOnUpside;
	}
	
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
        return true;
    }

    public void setColor(ColorWrap color) {
        this.color = color;
    }

    public void setExplicitTicks(double[] tickPositions){
        mTickPositions = tickPositions;
    }
    public void unsetExplicitTics(){
        mTickPositions = null;
    }
}
