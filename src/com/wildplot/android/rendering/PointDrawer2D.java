/**
 * 
 */
package com.wildplot.android.rendering;

import com.wildplot.android.rendering.graphics.wrapper.Color;
import com.wildplot.android.rendering.graphics.wrapper.Graphics;
import com.wildplot.android.rendering.graphics.wrapper.Rectangle;
import com.wildplot.android.rendering.interfaces.Drawable;


/**
 * This class simply draws points from a given data array or a function in given interval on a PlotSheet object
 * 
 */
public class PointDrawer2D implements Drawable {
	
	private PlotSheet plotSheet;
	
	private double[][] pointList;
	
	private Color color;
	
	public PointDrawer2D(PlotSheet plotSheet, double[][] pointList, Color color) {
		this.plotSheet = plotSheet;
		this.pointList = pointList;
		this.color = color;
	}
	
	/* (non-Javadoc)
	 * @see rendering.Drawable#paint(java.awt.Graphics)
	 */
	@Override
	public void paint(Graphics g) {
		Color oldColor = g.getColor();
		Rectangle field = g.getClipBounds();
		g.setColor(color);
		
		for(int i = 0; i< pointList[0].length; i++) {
			drawPoint(pointList[0][i], pointList[1][i], g, field);
		}
		g.setColor(oldColor);
	}
	
	/**
	 * Draw points as cross
	 * @param x x-value of a point
	 * @param y y-value of a point
	 * @param g graphic object where to draw
	 * @param field given rectangle field
	 */
	public void drawPoint(double x, double y, Graphics g, Rectangle field) {
        float[] coordStart 	= plotSheet.toGraphicPoint(x, y,field);
		g.drawArc(coordStart[0]-3, coordStart[1]-3, 6, 6, 0, 360);
		
//		g.drawLine(coordStart[0]-3, coordStart[1]-3, coordStart[0]+3, coordStart[1]+3);
//		g.drawLine(coordStart[0]-3, coordStart[1]+3, coordStart[0]+3, coordStart[1]-3);		
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
}
