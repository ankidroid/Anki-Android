/**
 * 
 */
package com.wildplot.android.rendering;

import com.wildplot.android.rendering.graphics.wrapper.Color;
import com.wildplot.android.rendering.graphics.wrapper.Graphics;
import com.wildplot.android.rendering.graphics.wrapper.Rectangle;
import com.wildplot.android.rendering.interfaces.Drawable;


/**
 * The LinesPoints objects draw points from a data array and connect them with lines on. 
 * These LinesPoints are drawn onto a PlotSheet object
 */
public class LinesPoints implements Drawable {
	
	private PlotSheet plotSheet;
	
	private double[][] pointList;
	
	private Color color;
	
	/**
	 * Constructor for points connected with lines
	 * @param plotSheet the sheet the lines and points will be drawn onto
	 * @param pointList x- , y-positions of given points
	 * @param color point and line color
	 */
	public LinesPoints(PlotSheet plotSheet, double[][] pointList, Color color) {
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

        float[] coordStart = plotSheet.toGraphicPoint(pointList[0][0],pointList[1][0],field);
        float[] coordEnd = coordStart;
		
		for(int i = 0; i< pointList[0].length; i++) {
			coordEnd = coordStart;
			coordStart = plotSheet.toGraphicPoint(pointList[0][i],pointList[1][i],field);
			g.drawLine(coordStart[0], coordStart[1], coordEnd[0], coordEnd[1]);
			drawPoint(pointList[0][i], pointList[1][i], g, field);
		}
		g.setColor(oldColor);
	}
	
	/**
	 * Draw points as karo
	 * @param x x-value of a point
	 * @param y y-value of a point
	 * @param g graphic object where to draw
	 * @param field given rectangle field
	 */
	public void drawPoint(double x, double y, Graphics g, Rectangle field) {
        float[] coordStart 	= plotSheet.toGraphicPoint(x, y,field);
		g.drawRect(coordStart[0]-3, coordStart[1]-3, 6, 6);
//		g.drawLine(coordStart[0]-3, coordStart[1]-3, coordStart[0]+3, coordStart[1]-3);
//		g.drawLine(coordStart[0]+3, coordStart[1]-3, coordStart[0]+3, coordStart[1]+3);
//		g.drawLine(coordStart[0]+3, coordStart[1]+3, coordStart[0]-3, coordStart[1]+3);
//		g.drawLine(coordStart[0]-3, coordStart[1]+3, coordStart[0]-3, coordStart[1]-3);
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
