/**
 * 
 */
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
    private Color mShadowColor = Color.BLACK;

    private String mName = "";
    private boolean mNameIsSet = false;

	private PlotSheet plotSheet;
	
	private double[][] pointList;
	
	private Color color;

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
	public Lines(PlotSheet plotSheet, double[][] pointList, Color color) {
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
        Stroke oldStroke = g.getStroke();
        g.setStroke(new BasicStroke(this.size));  // set stroke width of 10
        if(mHasShadow){
            //g.setShadow(mShadowRadius, mShadowDx, mShadowDy, mShadowColor);
        }

        float[] coordStart = plotSheet.toGraphicPoint(pointList[0][0],pointList[1][0],field);
        float[] coordEnd = coordStart;
		
		for(int i = 0; i< pointList[0].length; i++) {
			coordEnd = coordStart;
			coordStart = plotSheet.toGraphicPoint(pointList[0][i],pointList[1][i],field);
            if(mHasShadow){
                Stroke oldShadowLessStroke = g.getStroke();
                g.setStroke(new BasicStroke(this.size*1.5f));  // set stroke width of 10
                Color shadowColor = new Color(mShadowColor.getRed(), mShadowColor.getGreen(), mShadowColor.getBlue(), 80);
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
	public void drawPoint(double x, double y, Graphics g, Rectangle field) {
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
    public Color getColor() {
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

    public void setShadow(float radius, float dx, float dy, Color color){
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
