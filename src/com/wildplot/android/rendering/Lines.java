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
import com.wildplot.android.rendering.interfaces.Legendable;


/**
 * The LinesPoints objects draw points from a data array and connect them with lines on. 
 * These LinesPoints are drawn onto a PlotSheet object
 */
public class Lines implements Drawable, Legendable {

    private boolean mHasShadow = false;
    private float mShadowRadius = 0.0f;
    private float mShadowDx= 0.0f;
    private float mShadowDy = 0.0f;
    private ColorWrap mShadowColor = ColorWrap.BLACK;

    private String mName = "";
    private boolean mNameIsSet = false;

	private PlotSheet plotSheet;
	
	private double[][] pointList;
	
	private ColorWrap color;

    public void setSize(float size) {
        this.size = size;
    }

    private float size;

    /**
	 * Constructor for points connected with lines without drawn points
	 * @param plotSheet the sheet the lines and points will be drawn onto
	 * @param pointList x- , y-positions of given points
	 * @param color point and line color
	 */
	public Lines(PlotSheet plotSheet, double[][] pointList, ColorWrap color) {
		this.plotSheet = plotSheet;
		this.pointList = pointList;
		this.color = color;
	}
	
	/* (non-Javadoc)
	 * @see rendering.Drawable#paint(java.awt.Graphics)
	 */
	@Override
	public void paint(GraphicsWrap g) {
		ColorWrap oldColor = g.getColor();
		RectangleWrap field = g.getClipBounds();
		g.setColor(color);
        StrokeWrap oldStroke = g.getStroke();
        g.setStroke(new BasicStrokeWrap(this.size));  // set stroke width of 10
        if(mHasShadow){
            //g.setShadow(mShadowRadius, mShadowDx, mShadowDy, mShadowColor);
        }

        float[] coordStart = plotSheet.toGraphicPoint(pointList[0][0],pointList[1][0],field);
        float[] coordEnd = coordStart;
		
		for(int i = 0; i< pointList[0].length; i++) {
			coordEnd = coordStart;
			coordStart = plotSheet.toGraphicPoint(pointList[0][i],pointList[1][i],field);
            if(mHasShadow){
                StrokeWrap oldShadowLessStroke = g.getStroke();
                g.setStroke(new BasicStrokeWrap(this.size*1.5f));  // set stroke width of 10
                ColorWrap shadowColor = new ColorWrap(mShadowColor.getRed(), mShadowColor.getGreen(), mShadowColor.getBlue(), 80);
                g.setColor(shadowColor);
                g.drawLine(coordStart[0] + mShadowDx, coordStart[1] + mShadowDy, coordEnd[0] + mShadowDx, coordEnd[1] + mShadowDy);
                g.setColor(color);
                g.setStroke(oldShadowLessStroke);
            }
			g.drawLine(coordStart[0], coordStart[1], coordEnd[0], coordEnd[1]);



			//drawPoint(pointList[0][i], pointList[1][i], canvas, paint, field);
		}
        if(mHasShadow){
            //g.unsetShadow();
        }
        g.setStroke(oldStroke);
		g.setColor(oldColor);
	}
	
	/**
	 * Draw points as karo
	 * @param x x-value of a point
	 * @param y y-value of a point
	 * @param g graphic object where to draw
	 * @param field given Rect field
	 */
	public void drawPoint(double x, double y, GraphicsWrap g, RectangleWrap field) {
        float[] coordStart 	= plotSheet.toGraphicPoint(x, y,field);
		g.drawRect(coordStart[0]-3, coordStart[1]-3, coordStart[0]-3+6, coordStart[1]-3+6);
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

    @Override
    public ColorWrap getColor() {
        return color;
    }

    @Override
    public String getName() {
        return mName;
    }

    @Override
    public boolean nameIsSet() {
        return mNameIsSet;
    }

    public void setName(String name){
        mName = name;
        mNameIsSet = true;
    }

    public void setShadow(float radius, float dx, float dy, ColorWrap color){
        mHasShadow =true;
        mShadowRadius = radius;
        mShadowDx = dx;
        mShadowDy = dy;
        mShadowColor = color;
    }
    public void unsetShadow(){
        mHasShadow = false;
    }
}
