/**
 * 
 */
package com.wildplot.android.rendering;

import com.wildplot.android.rendering.graphics.wrapper.Color;
import com.wildplot.android.rendering.graphics.wrapper.Graphics;
import com.wildplot.android.rendering.graphics.wrapper.Rectangle;
import com.wildplot.android.rendering.interfaces.Drawable;


/**
 * This class represents grid lines parallel to the x-axis
 * 
 */
public class XGrid implements Drawable {
	
	public boolean hasVariableLimits = true;
	
	private boolean isAutoTic = true;
	
	private int pixelDistance = 25;
	
	/**
	 * the color of the grid lines
	 */
	private Color color = Color.LIGHT_GRAY;
	
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

	/**
	 * Constructor for an X-Grid object
	 * @param plotSheet the sheet the grid will be drawn onto
	 * @param ticStart start point for relative positioning of grid
	 * @param tic the space between two grid lines
	 */
	public XGrid(PlotSheet plotSheet, double ticStart, double tic) {
		super();
		this.plotSheet = plotSheet;
		this.ticStart = ticStart;
		this.tic = tic;
	}
	
	/**
	 * Constructor for an X-Grid object
	 * @param plotSheet the sheet the grid will be drawn onto
	 * @param ticStart start point for relative positioning of grid
	 * @param tic the space between two grid lines
	 */
	public XGrid(Color color, PlotSheet plotSheet, double ticStart, double tic) {
		super();
		this.color = color;
		this.plotSheet = plotSheet;
		this.ticStart = ticStart;
		this.tic = tic;
	}
	/**
	 * Constructor for an X-Grid object
	 * @param plotSheet the sheet the grid will be drawn onto
	 * @param ticStart start point for relative positioning of grid
	 */
	public XGrid(PlotSheet plotSheet, double ticStart, int pixelDistance) {
		super();
		this.plotSheet = plotSheet;
		this.ticStart = ticStart;
		this.pixelDistance = pixelDistance;
	}
	
	/**
	 * Constructor for an X-Grid object
	 * @param plotSheet the sheet the grid will be drawn onto
	 * @param ticStart start point for relative positioning of grid
	 */
	public XGrid(Color color, PlotSheet plotSheet, double ticStart, int pixelDistance) {
		super();
		this.color = color;
		this.plotSheet = plotSheet;
		this.ticStart = ticStart;
		this.pixelDistance = pixelDistance;
	}





	/* (non-Javadoc)
	 * @see rendering.Drawable#paint(java.awt.Graphics)
	 */
	@Override
	public void paint(Graphics g) {
		
		if(this.hasVariableLimits) {
			this.xLength = Math.max(Math.abs(plotSheet.getxRange()[0]), Math.abs(plotSheet.getxRange()[1]));
			this.yLength = Math.max(Math.abs(plotSheet.getyRange()[0]), Math.abs(plotSheet.getyRange()[1]));
		}
		
		
		Color oldColor = g.getColor();
		Rectangle field = g.getClipBounds();
		g.setColor(color);
		if(this.isAutoTic)
			this.tic = plotSheet.ticsCalcY(pixelDistance, field);

        int tics = (int)((this.ticStart - (0-this.yLength))/tic);
		double downStart = this.ticStart - this.tic*tics; 
		
		if(downStart < 0 ) {
			if(!this.gridOnDownside) {
				while(downStart<0) {
					downStart+=this.tic;
				}
			}
			
		}
		double currentY = downStart;
		
		while(currentY <= this.yLength && !(currentY > 0 && !this.gridOnUpside)) {
			drawGridLine(currentY, g, field);
			currentY+=this.tic;
			//System.err.println("another loop");
		}
		//System.err.println("out of loop");
		g.setColor(oldColor);

	}
	
	/**
	 * Draw a grid line in specified graphics object
	 * @param y x-position the vertical line shall be drawn
	 * @param g graphic the line shall be drawn onto
	 * @param field definition of the graphic boundaries
	 */
	private void drawGridLine(double y, Graphics g, Rectangle field) {
		if(this.gridkOnLeft) {
			g.drawLine(plotSheet.xToGraphic(0, field), plotSheet.yToGraphic(y, field), plotSheet.xToGraphic(-this.xLength, field), plotSheet.yToGraphic(y, field));
		}
		
		if(this.gridOnRight) {
			g.drawLine(plotSheet.xToGraphic(0, field), plotSheet.yToGraphic(y, field), plotSheet.xToGraphic(this.xLength, field), plotSheet.yToGraphic(y, field));
		}
	}
	
	/**
	 * set if the grid should be drawn onto the left side
	 * @param gridkOnLeft 
	 */
	public void setGridkOnLeft(boolean gridkOnLeft) {
		this.gridkOnLeft = gridkOnLeft;
	}

	/**
	 * set if the grid should be drawn onto the right side
	 */
	public void setGridOnRight(boolean gridOnRight) {
		this.gridOnRight = gridOnRight;
	}

	/**
	 * set if the grid should be drawn onto the downside side
	 */
	public void setGridOnDownside(boolean gridOnDownside) {
		this.gridOnDownside = gridOnDownside;
	}
	
	/**
	 * set if the grid should be drawn onto the upside
	 */
	public void setGridOnUpside(boolean gridOnUpside) {
		this.gridOnUpside = gridOnUpside;
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
        return true;
    }

    public void setColor(Color color) {
        this.color = color;
    }
}
