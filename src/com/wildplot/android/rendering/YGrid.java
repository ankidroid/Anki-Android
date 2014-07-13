/**
 * 
 */
package com.wildplot.android.rendering;

import com.wildplot.android.rendering.graphics.wrapper.Color;
import com.wildplot.android.rendering.graphics.wrapper.Graphics;
import com.wildplot.android.rendering.graphics.wrapper.Rectangle;
import com.wildplot.android.rendering.interfaces.Drawable;


/**
 * This class represents grid lines parallel to the y-axis
 * 
 * 
 */
public class YGrid implements Drawable {
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
	public YGrid(Color color, PlotSheet plotSheet, double ticStart, double tic) {
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
	}

	/**
	 * Constructor for an Y-Grid object
	 * @param color set color of the grid
	 * @param plotSheet the sheet the grid will be drawn onto
	 * @param ticStart start point for relative positioning of grid
	 */
	public YGrid(Color color, PlotSheet plotSheet, double ticStart, int pixelDistance) {
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
		Color oldColor = g.getColor();
		Rectangle field = g.getClipBounds();
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
    private void drawImplicitLines(Graphics g, double leftStart){
        Rectangle field = g.getClipBounds();
        double currentX = leftStart;

        while(currentX <= this.xLength && !(currentX > 0 && !this.gridOnRight)) {
            drawGridLine(currentX, g, field);
            currentX+=this.tic;
            //System.err.println("another loop");
        }
    }

    private void drawExplicitLines(Graphics g){
        Rectangle field = g.getClipBounds();

        for(int i = 0; i< mTickPositions.length; i++) {
            double currentX = mTickPositions[i];
            drawGridLine(currentX, g, field);
        }
    }
	
	/**
	 * Draw a grid line in specified graphics object
	 * @param x x-position the vertical line shall be drawn
	 * @param g graphic the line shall be drawn onto
	 * @param field definition of the graphic boundaries
	 */
	private void drawGridLine(double x, Graphics g, Rectangle field) {
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

    public void setColor(Color color) {
        this.color = color;
    }

    public void setExplicitTicks(double[] tickPositions){
        mTickPositions = tickPositions;
    }
    public void unsetExplicitTics(){
        mTickPositions = null;
    }
}
