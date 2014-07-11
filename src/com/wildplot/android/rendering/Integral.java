/**
 * 
 */
package com.wildplot.android.rendering;

import com.wildplot.android.rendering.graphics.wrapper.Color;
import com.wildplot.android.rendering.graphics.wrapper.Graphics;
import com.wildplot.android.rendering.graphics.wrapper.Graphics2D;
import com.wildplot.android.rendering.graphics.wrapper.Rectangle;
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
	
	private Color color = new Color(0.7f, 1f, 0f, 0.4f);
	
	/**
	 * set the color for integral area
	 * @param color integral area color
	 */
	public void setColor(Color color) {
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
	public void paint(Graphics g) {
		
		Color oldColor = g.getColor();
		Rectangle field = g.getClipBounds();
		Graphics2D g2d = (Graphics2D)g;
		g2d.setColor(color);

        float[] startPoint 	= plotSheet.toGraphicPoint(this.start, 0, field);
        float[] endPoint 		= plotSheet.toGraphicPoint(this.end, 0, field);
		
		for(int i = Math.round(startPoint[0]); i<=endPoint[0];i++) {
			double currentX = plotSheet.xToCoordinate(i, field);
			double currentY = function.f(currentX);
			
			if(this.function2 != null){
				double currentY2 = function2.f(currentX);
				g2d.drawLine(plotSheet.xToGraphic(currentX, field), plotSheet.yToGraphic(currentY, field), plotSheet.xToGraphic(currentX, field), plotSheet.yToGraphic(currentY2, field));
			}else {
				g2d.drawLine(plotSheet.xToGraphic(currentX, field), plotSheet.yToGraphic(currentY, field), plotSheet.xToGraphic(currentX, field), plotSheet.yToGraphic(0, field));
			}
			
			
			
		}
		
		g2d = (Graphics2D)g;
		
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
