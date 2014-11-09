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


import com.wildplot.android.rendering.graphics.wrapper.FontMetricsWrap;
import com.wildplot.android.rendering.graphics.wrapper.GraphicsWrap;
import com.wildplot.android.rendering.graphics.wrapper.RectangleWrap;
import com.wildplot.android.rendering.interfaces.Drawable;

import java.text.DecimalFormat;

/**
 * This Class represents a Drawable x-axis 
 * 
 */
public class XAxis implements Drawable {

    private boolean isIntegerNumbering = false;
	public boolean hasVariableLimits = true;
	
	/**
	 * this determines if this axis uses automatic tics calculation based on pixel distance between tics
	 */
	private boolean isAutoTic = false;
	
	/**
	 * believe it or not, this axis can be placed higher or lower than y=0
	 */
	private double yOffset = 0;
	
	/**
	 * the offset of the y-axis so that we know where the intersection is
	 */
	private double xOffset = 0;
	
	/**
	 * Name of axis
	 */
	private String name = "X";

    private boolean mHasName = false;
	
	/**
	 * Format that is used to print numbers under markers
	 */
	private DecimalFormat df =   new DecimalFormat( "##0.0#" );
	
	/**
	 * format for very big or small values
	 */
	private DecimalFormat dfScience =   new DecimalFormat( "0.0##E0" );
    private DecimalFormat dfInteger =   new DecimalFormat( "#.#" );
	/**
	 * is set to true if scientifiv format (e.g. 1E-3) should be used
	 */
	private boolean isScientific = false;
	
	/**
	 * the PlotSheet object the x-axis is drawn onto
	 */
	private PlotSheet plotSheet;
	
	/**
	 * the start of x-axis marker, used for relative alignment of further marks
	 */
	private double ticStart = 0;
	
	/**
	 * the space between two marks
	 */
	private double tic = 1;
	
	/**
	 * the space between two minor marks
	 */
	private double minorTic= 0.5;
	
	/**
	 * the estimated size between two major tics in auto tic mode
	 */
	private float pixelDistance = 25;
	
	/**
	 * the estimated size between two minor tics in auto tic mode
	 */
	private float minorPixelDistance = 25;
	
	/**
	 * start of drawn x-axis
	 */
	private double start = 0;
	
	/**
	 * end of drawn x-axis
	 */
	private double end = 100;
	
	/**
	 * true if the marker should be drawn into the direction above the axis
	 */
	private boolean markOnUpside = true;
	
	/**
	 * true if the marker should be drawn into the direction under the axis
	 */
	private boolean markOnDownside = true;
	
	/**
	 * length of a marker in pixel, length is only for one side
	 */
	private float markerLength = 5;
	
	/**
	 * true if this  axis is drawn onto the frame
	 */
	private boolean isOnFrame = false;
	//FIXME if Axis is on frame and zoom is used, stay on frame!

    private String[] mTickNameList = null;
    private double[] mTickPositions = null;
	
	
	/**
	 * Constructor for an X-axis object
	 * @param plotSheet the sheet the axis will be drawn onto
	 * @param ticStart the start of the axis markers used for relative alignment of other markers
	 * @param tic the space between two markers
	 */
	public XAxis(PlotSheet plotSheet, double ticStart, double tic, double minorTic) {
		this.plotSheet = plotSheet;
		this.ticStart = ticStart;
		this.tic = tic;
		this.minorTic = minorTic;
	}
	
	/**
	 * Constructor for an X-axis object this instance uses autocalculation of tics with a given pixeldistance
	 * @param plotSheet the sheet the axis will be drawn onto
	 * @param ticStart the start of the axis markers used for relative alignment of other markers
	 */
	public XAxis(PlotSheet plotSheet, double ticStart, float pixelDistance, float minorPixelDistance ) {
		this.plotSheet = plotSheet;
		this.ticStart = ticStart;
		this.pixelDistance = pixelDistance;
		this.minorPixelDistance = minorPixelDistance;
		this.isAutoTic = true;
	}
	
	/*
	 * (non-Javadoc)
	 * @see rendering.Drawable#paint(java.awt.Graphics)
	 */
	public void paint(GraphicsWrap g) {
		RectangleWrap field = g.getClipBounds();
		
		if(this.hasVariableLimits){
			start = plotSheet.getxRange()[0];
			end = plotSheet.getxRange()[1];
		}
		
		if(this.isOnFrame)
			yOffset = plotSheet.getyRange()[0];
			
		if(this.isAutoTic){
			this.tic = plotSheet.ticsCalcX(pixelDistance, field);			
			double quotient = (pixelDistance*1.0)/(minorPixelDistance*1.0);
			this.minorTic = (this.tic / Math.round(quotient));
			//System.err.println("XAXIS DEBUG");
		}
		else {
			this.pixelDistance = Math.abs(plotSheet.xToGraphic(0, field) - plotSheet.xToGraphic(tic, field));
			this.minorPixelDistance = Math.abs(plotSheet.xToGraphic(0, field) - plotSheet.xToGraphic(minorTic, field));
		}
		if(this.tic < 1e-2 || this.tic > 1e2)
			this.isScientific = true;
		
		//horizontale Linie
        float[] coordStart 	= plotSheet.toGraphicPoint(start, yOffset, field);
        float[] coordEnd 		= plotSheet.toGraphicPoint(end, yOffset, field);
		
		if(!this.isOnFrame)
			g.drawLine(coordStart[0], coordStart[1], coordEnd[0], coordEnd[1]);
		
		drawMarkers(g);
        if(mTickPositions == null)
		    drawMinorMarkers(g);
	}



	/**
	 * draw markers on the axis
	 * @param g graphic object used for drawing
	 */
	private void drawMarkers(GraphicsWrap g) {
		RectangleWrap field = g.getClipBounds();


        if(mTickPositions == null)
            drawImplicitMarker(g);
        else
            drawExplicitMarkers(g);


		//arrow
        float[] arowheadPos = {(plotSheet.getxRange()[1] >= this.end)? plotSheet.xToGraphic( this.end, field): plotSheet.xToGraphic(plotSheet.getxRange()[1], field), plotSheet.yToGraphic(yOffset, field) };

		FontMetricsWrap fm = g.getFontMetrics( g.getFont() );
        float fontHeigth = fm.getHeight(true);
        float width = fm.stringWidth(this.name);
		if(!this.isOnFrame) {
			g.drawLine(arowheadPos[0]-1, arowheadPos[1]-1, arowheadPos[0]-6, arowheadPos[1]-3);
			g.drawLine(arowheadPos[0]-1, arowheadPos[1]+1, arowheadPos[0]-6, arowheadPos[1]+3);
            if(mHasName)
			    g.drawString(this.name, arowheadPos[0]-14-width, arowheadPos[1] + 12);
		} else {
            float[] middlePosition = {plotSheet.xToGraphic(0, field), plotSheet.yToGraphic( yOffset, field) };
            if(mHasName)
			    g.drawString(this.name, field.width/2-width/2, Math.round(middlePosition[1]+fontHeigth* 2.5f));
		}
	}

    private void drawImplicitMarker(GraphicsWrap g){
        RectangleWrap field = g.getClipBounds();
        int tics = (int)((this.ticStart - this.start)/tic);
        double leftStart = this.ticStart - this.tic*tics;

        double currentX = leftStart;

        while(currentX <= this.end) {
            if((!this.isOnFrame && plotSheet.xToGraphic(currentX, field) <= plotSheet.xToGraphic(this.end, field) -45
                    && plotSheet.xToGraphic(currentX, field) <= field.x + field.width - 45) ||
                    (this.isOnFrame && currentX <= this.plotSheet.getxRange()[1] &&
                            currentX >= this.plotSheet.getxRange()[0])){

                if(currentX < this.plotSheet.getxRange()[0]){
                    //System.out.println("WTF");
                }
                if(this.markOnDownside) {
                    drawDownwardsMarker(g, field, currentX);
                }
                if(this.markOnUpside) {
                    drawUpwardsMarker(g, field, currentX);
                }
                drawNumbering(g, field, currentX, -1);
            }
            currentX += this.tic;
        }
    }

    private void drawExplicitMarkers(GraphicsWrap g){
        RectangleWrap field = g.getClipBounds();
        for(int i = 0; i < mTickPositions.length; i ++){
            double currentX = mTickPositions[i];
            if((!this.isOnFrame && plotSheet.xToGraphic(currentX, field) <= plotSheet.xToGraphic(this.end, field) -45
                    && plotSheet.xToGraphic(currentX, field) <= field.x + field.width - 45) ||
                    (this.isOnFrame && currentX <= this.plotSheet.getxRange()[1] &&
                            currentX >= this.plotSheet.getxRange()[0])){

                if(currentX < this.plotSheet.getxRange()[0]){
                    //System.out.println("WTF");
                }
                if(this.markOnDownside) {
                    drawDownwardsMarker(g, field, currentX);
                }
                if(this.markOnUpside) {
                    drawUpwardsMarker(g, field, currentX);
                }
                drawNumbering(g, field, currentX, i);
            }
        }
    }
	
	/**
	 * draw number under a marker
	 * @param g graphic object used for drawing
	 * @param field bounds of plot
	 * @param x position of number
	 */
	private void drawNumbering(GraphicsWrap g, RectangleWrap field, double x, int index) {
		
		if(this.tic < 1 && Math.abs(ticStart-x) <  this.tic*this.tic)
			x = ticStart;
		
		FontMetricsWrap fm = g.getFontMetrics( g.getFont() );
        float fontHeigth = fm.getHeight();
        float[] coordStart = plotSheet.toGraphicPoint(x, yOffset, field);
		if(Math.abs(x) - Math.abs(xOffset) <0.001 && !this.isOnFrame){
			coordStart[0]+=10;
			coordStart[1]-=10;
		}

		String text = (mTickNameList == null)?df.format(x) : mTickNameList[index];
        float width = fm.stringWidth(text);
		if(this.isScientific || width > this.pixelDistance){
			text = (mTickNameList == null)?dfScience.format(x) : mTickNameList[index];
			width = fm.stringWidth(text);
			g.drawString(text, coordStart[0]-width/2, Math.round(coordStart[1]+((this.isOnFrame)? Math.round(fontHeigth*1.5) : 20)));
		}else if(isIntegerNumbering){
            text = (mTickNameList == null)?dfInteger.format(x) : mTickNameList[index];
            width = fm.stringWidth(text);
            g.drawString(text, coordStart[0]-width/2, Math.round(coordStart[1]+((this.isOnFrame)? Math.round(fontHeigth*1.5) : 20)));
        } else {
			width = fm.stringWidth(text);
			g.drawString(text, coordStart[0]-width/2, Math.round(coordStart[1]+((this.isOnFrame)? Math.round(fontHeigth*1.5) : 20)));
		}
	}
	
	/**
	 * draws an upwards marker
	 * @param g graphic object used for drawing
	 * @param field bounds of plot
	 * @param x position of marker
	 */
	private void drawUpwardsMarker(GraphicsWrap g, RectangleWrap field, double x){

        float[] coordStart = plotSheet.toGraphicPoint(x, yOffset, field);
        float[] coordEnd = {coordStart[0], coordStart[1] - this.markerLength};
		g.drawLine(coordStart[0], coordStart[1], coordEnd[0], coordEnd[1]);
		
	}
	
	/**
	 * draws an downwards marker
	 *@param g graphic object used for drawing
	 * @param field bounds of plot
	 * @param x position of marker
	 */
	private void drawDownwardsMarker(GraphicsWrap g, RectangleWrap field, double x){
        float[] coordStart = plotSheet.toGraphicPoint(x, yOffset, field);
        float[] coordEnd = {coordStart[0], coordStart[1] + this.markerLength};
		g.drawLine(coordStart[0], coordStart[1], coordEnd[0], coordEnd[1]);
		
	}

	/**
	 * get the offset of this axis
	 * @return the offset of this axis
	 */
	public double getyOffset() {
		return yOffset;
	}
	
	/**
	 * set the offset of this axis
	 * @param yOffset new offset
	 */
	public void setyOffset(double yOffset) {
		this.yOffset = yOffset;
	}
	
	/**
	 * set offset back to zero for normal axis behavior
	 */
	public void unsetyOffset() {
		this.yOffset = 0;
	}
	
	/**
	 * set the axis to draw on the border between outer frame and plot
	 */
	public void setOnFrame() {
		this.isOnFrame = true;
		yOffset = plotSheet.getyRange()[0];
		markOnDownside = false;
	}
	
	/**
	 * unset the axis to draw on the border between outer frame and plot
	 */
	public void unsetOnFrame() {
		this.isOnFrame = false;
		yOffset = 0;
		markOnDownside = true;
	}
	
	/*
	 * (non-Javadoc)
	 * @see rendering.Drawable#isOnFrame()
	 */
	public boolean isOnFrame() {
		return isOnFrame;
	}
	
	/**
	 * set name description of axis
	 * @param name of axis
	 */
	public void setName(String name) {
		this.name = name;
        mHasName = true;
        if(name.equals(""))
            mHasName = false;
	}
	/**
	 * draw minor markers on the axis
	 * @param g graphic object used for drawing
	 */
	private void drawMinorMarkers(GraphicsWrap g) {
		RectangleWrap field = g.getClipBounds();
		
		int tics = (int)((this.ticStart - this.start)/tic);
		double leftStart = this.ticStart - this.tic*tics;

		double currentX = leftStart;
		
		while(currentX <= this.end) {
			if((!this.isOnFrame && plotSheet.xToGraphic(currentX, field) <= plotSheet.xToGraphic(this.end, field) -45 
					&& plotSheet.xToGraphic(currentX, field) <= field.x + field.width - 45) || 
					(this.isOnFrame && currentX <= this.plotSheet.getxRange()[1] &&
					currentX >= this.plotSheet.getxRange()[0])){
				
				if(currentX < this.plotSheet.getxRange()[0]){
					//System.out.println("WTF");
				}
				if(this.markOnDownside) {
					drawDownwardsMinorMarker(g, field, currentX);
				}
				if(this.markOnUpside) {
					drawUpwardsMinorMarker(g, field, currentX);
				}

				//drawNumbering(g, field, currentX);
			}
			currentX += minorTic;
//			currentX += calcMinorTics(g)*this.tic;
		}

	}
	
//	/**
//	 * caculates minor tics
//	 * @param g graphic object used for drawing
//	 * @return returns the new minor tics in percent
//	 */
//	private double calcMinorTics(Graphics g)
//	{
//		double percentMinorTic=this.minorTic/this.tic;
//		return percentMinorTic;
//	}
	
	/**
	 * draws an upwards minor marker
	 * @param g graphic object used for drawing
	 * @param field bounds of plot
	 * @param x position of marker
	 */
	private void drawUpwardsMinorMarker(GraphicsWrap g, RectangleWrap field, double x){

        float[] coordStart = plotSheet.toGraphicPoint(x, yOffset, field);
        float[] coordEnd = {coordStart[0], (int) (coordStart[1] - 0.5*this.markerLength)};
		g.drawLine(coordStart[0], coordStart[1], coordEnd[0], coordEnd[1]);
		
	}
	
	/**
	 * draws an downwards minor marker
	 *@param g graphic object used for drawing
	 * @param field bounds of plot
	 * @param x position of marker
	 */
	private void drawDownwardsMinorMarker(GraphicsWrap g, RectangleWrap field, double x){
        float[] coordStart = plotSheet.toGraphicPoint(x, yOffset, field);
        float[] coordEnd = {coordStart[0], (int) (coordStart[1] + 0.5*this.markerLength)};
		g.drawLine(coordStart[0], coordStart[1], coordEnd[0], coordEnd[1]);
		
	}
    public void setIntegerNumbering(boolean isIntegerNumbering){
        this.isIntegerNumbering = isIntegerNumbering;
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

    public void setExplicitTicks(double[] tickPositions, String[] tickNameList){
        mTickPositions = tickPositions;
        mTickNameList = tickNameList;
    }
    public void unsetExplicitTics(){
        mTickNameList = null;
        mTickPositions = null;
    }

}
