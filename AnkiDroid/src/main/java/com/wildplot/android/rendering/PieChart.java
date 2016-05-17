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
import com.wildplot.android.rendering.graphics.wrapper.FontMetricsWrap;
import com.wildplot.android.rendering.graphics.wrapper.GraphicsWrap;
import com.wildplot.android.rendering.graphics.wrapper.RectangleWrap;
import com.wildplot.android.rendering.interfaces.Drawable;
import com.wildplot.android.rendering.interfaces.Legendable;

//TODO reimplement this whole mess!
public class PieChart implements Drawable, Legendable {

    private String mName = "";
    private boolean mNameIsSet = false;
	private double[] mValues;
	private double[] mPercent;
	private double mSum;
	private int mColorHelper;
	private PlotSheet mPlotSheet;

	private ColorWrap[] mColors = {
		new ColorWrap(255,  0,  0,180),
		new ColorWrap(0  ,255,  0,180),
		new ColorWrap(0  ,0,  255,180),
		
		new ColorWrap(255,255,  0,180),
		new ColorWrap(  0,255,255,180),
		new ColorWrap(255,  0,255,180)
		};

	public PieChart(PlotSheet plotSheet, double[] vals){
		this.mPlotSheet = plotSheet;
		mValues = vals;
		//mValues = new double[] {5,5,5,5,5,5,5,5,5,5,5,5,5,5,5,5,5,5,5,5,5};
		mPercent = new double[mValues.length];
		for(double v: mValues) mSum +=v;

        double denominator = (mSum == 0) ? 1 : mSum;

		mPercent[0]= mValues[0]/ denominator;
		for(int i=1; i< mValues.length; i++){
			mPercent[i]= mPercent[i-1]+ mValues[i]/ denominator;
		}
		mColorHelper = mColors.length;
		if((mValues.length-1)%(mColors.length)==0) mColorHelper = mColors.length-1;
	}

	/*
	 * (non-Javadoc)
	 * @see rendering.Drawable#isOnFrame()
	 */
	@Override
	public boolean isOnFrame() {
		return false;
	}
	
	/*
	 * (non-Javadoc)
	 * @see rendering.Drawable#paint(java.awt.Graphics)
	 */
	public void paint(GraphicsWrap g){

        //Do not show chart if segments are all zero
        if(mSum == 0) return;

		RectangleWrap field = g.getClipBounds();
        float maxSideBorders = Math.max(mPlotSheet.getFrameThickness()[PlotSheet.LEFT_FRAME_THICKNESS_INDEX],
                mPlotSheet.getFrameThickness()[PlotSheet.RIGHT_FRAME_THICKNESS_INDEX]);
        float maxUpperBottomBorders = Math.max(mPlotSheet.getFrameThickness()[PlotSheet.UPPER_FRAME_THICKNESS_INDEX],
                mPlotSheet.getFrameThickness()[PlotSheet.BOTTOM_FRAME_THICKNESS_INDEX]);

        float realBorder= Math.max(maxSideBorders, maxUpperBottomBorders) + 3;
        float diameter=Math.min(field.width, field.height)-2*realBorder;

        float xCenter = (float)(field.width/2.0);
        float yCenter = (float)(field.height/2.0);
		ColorWrap oldColor = g.getColor();

        float xMiddle = xCenter - (float)(diameter/2.0);
        float yMiddle = yCenter - (float)(diameter/2.0);

        float angleOffSet = -90;
        float currentAngle = angleOffSet;
        float nextAngle = currentAngle + (float)(360.0* mPercent[0]);
        int tmp = 0;
		for(int i = 0; i< mPercent.length-1; i++) {
			g.setColor(mColors[i% mColorHelper]);
            g.fillArc(xMiddle, yMiddle, (int)diameter, (int)(diameter), -1*currentAngle, -1*(nextAngle - currentAngle));
			currentAngle = nextAngle;
			nextAngle = (int)(angleOffSet + 360.0* mPercent[i+1]);
			tmp = i;
		}
        tmp++;

		//last one does need some corrections to fill a full circle:
		g.setColor(mColors[tmp% mColorHelper]);
        g.fillArc(xMiddle, yMiddle, diameter, diameter, -1*currentAngle, -1*(360 + angleOffSet - currentAngle));
		g.setColor(ColorWrap.black);
		g.drawArc(xMiddle, yMiddle, diameter, diameter, 0, 360);
		
		//Beschriftung
		g.setColor(ColorWrap.white);
		//g.drawString("" + Math.round(((mPercent[0]) * 100) * 100) / 100.0 + "%", (float) (xCenter + Math.cos(mPercent[0] * Math.PI) * 0.375 * diameter) - 20, (float) (yCenter - Math.sin(mPercent[0] * Math.PI) * 0.375 * diameter));
		for(int j=0;j< mPercent.length;j++)
		{
            double oldPercent = 0;
            if(j  != 0)
                oldPercent = mPercent[j-1];
            String text = ""+Math.round((((mPercent[j]- oldPercent))*100)*100)/100.0+"%";
            float x = (float)(xCenter+Math.cos(-1*((oldPercent+(mPercent[j]- oldPercent)*0.5)*360+angleOffSet)*Math.PI/180.0)*0.375*diameter)-20;
            float y = (float)(yCenter-Math.sin(-1*((oldPercent+(mPercent[j]- oldPercent)*0.5)*360+angleOffSet)*Math.PI/180.0)*0.375*diameter);
            FontMetricsWrap fm = g.getFontMetrics();
            float width = fm.stringWidth(text);
            float height = fm.getHeight();
            ColorWrap color = g.getColor();
            g.setColor(new ColorWrap(0,0,0,0.5f));
            g.fillRect(x-1,y-height+3,width+2,height);
            g.setColor(color);
			g.drawString(text,x ,y );
		}
		
		g.setColor(oldColor);
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
        return mColors[0];
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

    public void setColors(ColorWrap[] colors){
        mColors = colors;
        mColorHelper = mColors.length;
        if((mValues.length-1)%(mColors.length)==0)
            mColorHelper = mColors.length-1;
    }
}
