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


import com.wildplot.android.parsing.TopLevelParser;
import com.wildplot.android.rendering.graphics.wrapper.*;
import com.wildplot.android.rendering.interfaces.Drawable;
import com.wildplot.android.rendering.interfaces.Function3D;

import java.text.DecimalFormat;
import java.util.Arrays;
import java.util.Vector;


/**
 * Draws a relief of a three dimensional function on a two dimensional plot sheet. The relief is drawn either with borders
 * or with a color gradient.
 *
 */
public class ReliefDrawer implements Drawable {
	
	
	private float pixelSkip = 6;
	private boolean abortPaint = false;
	private boolean depthSearchAborted = false;
	
	private int threadCnt = 1;
	
	/**
	 * x-bounds of relief
	 */
	private double[] xrange = {0,0};
	
	/**
	 * y-bounds of relief
	 */
	private double[] yrange = {0,0};
	
	/**
	 * this variable will be used to store the lowest function value in the ploting range in the starting resolution
	 */
	private double f_xLowest 	= 0;
	
	/**
	 * this variable will be used to store the highest function value in the ploting range in the starting resolution
	 */
	private double f_xHighest 	= 0;
	
	/**
	 * to make the gradient colors non linear to the function value, this exponential factor can be used
	 */
	private double gradientCurveFactor = 1;
	
	/**
	 * the count of different height regions, can be dependent on the count of gradient colors
	 */
	private int heightRegionCount = 10;
	
	/**
	 * The gradient colors used for colored relief, can be dynamically expanded
	 */
//	private Color[] gradientColors = {new Color(0, 0, 143), new Color(0, 15, 200), new Color(0, 32, 255), new Color(0, 115, 255),
//			new Color(0, 170, 255), new Color(0, 231, 255), new Color(73, 242, 190),new Color(147, 255, 108), new Color(100, 230, 87),new Color(48, 213, 75),
//			new Color(98, 217, 62), new Color(148, 223, 50),new Color(188, 233, 35), new Color(235, 246, 20),new Color(245, 180, 10),new Color(255, 127, 0),
//			new Color(255, 88, 0),new Color(255, 39, 0), new Color(192, 19, 0), new Color(128, 0, 0) };
	
	private ColorWrap[] gradientColors = {ColorWrap.white, ColorWrap.GREEN.darker(), ColorWrap.GREEN.darker().darker(), ColorWrap.BLACK};
    //private Color[] gradientColors = {Color.white, Color.BLACK};
	
	/**
	 * The border function value between where borders are drawn. On colored plot each region between two borders gets
	 * a unique color.
	 */
	private double[] borders = null;
	private boolean depthScanningIsFinished = false;
	
	/**
	 * determines if this ReliefDrawer draws only one colored borders ore uses color gradient for the relief
	 */
	private boolean colored = true;
	
	/**
	 * the function for which the relief plot is drawn
	 */
	private Function3D function;
	
	/**
	 * the PlotSheet object on which the relief is drawn onto
	 */
	private PlotSheet plotSheet;
	
	/**
	 * border color for non-colored plots
	 */
	private ColorWrap color = new ColorWrap(255,0,0);
	
	/**
	 * Creates a new ReliefDrawer object
	 * @param gradientCurveFactor factor for non linear gradients (1=linear, <1 finer resolution for higher values, >1 finer resolution for lower values)
	 * @param heightRegionCount number of gradient regions, if colored this is set to the number of gradient colors, if the count is less than the colors, 
	 * if it is higher than the number of colors the color array will be expanded
	 * @param function the three dimensional function plotted with this relief drawer
	 * @param plotSheet this is where the relief is drawn upon
	 * @param colored true if a color gradient should be used, false if borders shall be used
	 */
	public ReliefDrawer(double gradientCurveFactor, int heightRegionCount, Function3D function, PlotSheet plotSheet, boolean colored) {
		super();
		this.gradientCurveFactor = gradientCurveFactor;
		this.heightRegionCount = heightRegionCount;
		this.function = function;
		this.plotSheet = plotSheet;
		this.colored = colored;
		if(colored){
			if(this.gradientColors.length >= heightRegionCount){
				this.heightRegionCount = this.gradientColors.length;
			} else {
				Vector<ColorWrap> colorVector = new Vector<>(Arrays.asList(this.gradientColors));
				this.gradientColors = RelativeColorGradient.makeGradient(colorVector, this.heightRegionCount);
				this.heightRegionCount = this.gradientColors.length;
			}
		}
	}
	
	/**
	 * Creates a new ReliefDrawer object for colored gradients
	 * @param gradientCurveFactor factor for non linear gradients 
	 * (1=linear, <1 finer resolution for higher values, >1 finer resolution for lower values)
	 * @param function the three dimensional function plotted with this relief drawer
	 * @param plotSheet this is where the relief is drawn upon
	 */
	public ReliefDrawer(double gradientCurveFactor, Function3D function, PlotSheet plotSheet) {
		super();
		this.gradientCurveFactor = gradientCurveFactor;
		this.function = function;
		this.plotSheet = plotSheet;		
		this.heightRegionCount = this.gradientColors.length;

	}
	
	/**
	 * 
	 * @param gradientCurveFactor factor for non linear gradients 
	 * (1=linear, <1 finer resolution for higher values, >1 finer resolution for lower values)
	 * @param heightRegionCount number of gradient regions, if colored this is set to the number of gradient colors, if the count is less than the colors, 
	 * if it is higher than the number of colors the color array will be expanded
	 * @param function the three dimensional function plotted with this relief drawer
	 * @param plotSheet this is where the relief is drawn upon
	 * @param colored true if a color gradient should be used, false if borders shall be used
	 * @param color color of borders if non colored plot is used
	 */
	public ReliefDrawer(double gradientCurveFactor, int heightRegionCount, Function3D function, PlotSheet plotSheet,boolean colored, ColorWrap color) {
		super();
		this.gradientCurveFactor = gradientCurveFactor;
		this.heightRegionCount = heightRegionCount;
		this.function = function;
		this.plotSheet = plotSheet;
		this.color = color;
		this.colored = colored;
		
		if(colored){
			if(this.gradientColors.length >= heightRegionCount){
				this.heightRegionCount = this.gradientColors.length;
			} else {
				Vector<ColorWrap> colorVector = new Vector<>(Arrays.asList(this.gradientColors));
				this.gradientColors = RelativeColorGradient.makeGradient(colorVector, this.heightRegionCount);
				this.heightRegionCount = this.gradientColors.length;
			}
		}
	}

	/* (non-Javadoc)
	 * @see rendering.Drawable#paint(java.awt.Graphics)
	 */
	@Override
	public void paint(GraphicsWrap g) {
		abortPaint = false;
		ColorWrap oldColor = g.getColor();
		RectangleWrap field = g.getClipBounds();
		
		
		if(rangeHasChanged()){
			try {
				scanDepth(field);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
		if(abortPaint)
			return;
		
		this.depthScanningIsFinished = true;
		if(this.colored){
			try {
				drawColoredRelief(g);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		} else{
			g.setColor(color);
			drawBorders(g);
		}
		
		
		
		g.setColor(oldColor);
	}
	
	/**
	 * draws relief with color gradient
	 * @param g graphic object used to draw relief
	 */
	private void drawColoredRelief(GraphicsWrap g) throws InterruptedException {
		RectangleWrap field = g.getClipBounds();
		
		BufferedImageWrap[] bimages = new BufferedImageWrap[threadCnt];
		for(int i = 0; i< bimages.length; i++){
			bimages[i] = new BufferedImageWrap(field.width, field.height, BufferedImageWrap.TYPE_INT_ARGB);
		}
		
//		double[] thisCoordinate = plotSheet.toCoordinatePoint(0, 0, field);
//		
//		double thisF_xy;
//		for(int i = field.x+plotSheet.getFrameThickness() ; i < field.x + field.width -plotSheet.getFrameThickness(); i++) {
//			for(int j = field.y + +plotSheet.getFrameThickness() ; j < field.y +field.height -plotSheet.getFrameThickness(); j++) {
//				thisCoordinate = plotSheet.toCoordinatePoint(i, j, field);
//				thisF_xy = function.f(thisCoordinate[0], thisCoordinate[1]);
//				g.setColor(getColor(thisF_xy));
//				g.drawLine(i, j, i, j);
//				
//			}
//		}

        float length = (field.x + field.width-plotSheet.getFrameThickness()[PlotSheet.RIGHT_FRAME_THICKNESS_INDEX]) -
                (field.x+plotSheet.getFrameThickness()[PlotSheet.LEFT_FRAME_THICKNESS_INDEX]);
		Thread[] threads = new Thread[threadCnt];
		
		PartRenderer[] partRenderer = new PartRenderer[threadCnt];
		
		GraphicsWrap gnew = bimages[0].getGraphics();
		gnew.setClip(field);
		partRenderer[0] = new PartRenderer(gnew,
                field.x + plotSheet.getFrameThickness()[PlotSheet.LEFT_FRAME_THICKNESS_INDEX],
                field.x + plotSheet.getFrameThickness()[PlotSheet.LEFT_FRAME_THICKNESS_INDEX]+ length/threadCnt,
                function);
		threads[0] = new Thread(partRenderer[0]);
		for(int i = 1; i< threads.length-1; i++){
			gnew = bimages[i].getGraphics();
			gnew.setClip(field);
			partRenderer[i] = new PartRenderer(gnew,
                    field.x + plotSheet.getFrameThickness()[PlotSheet.LEFT_FRAME_THICKNESS_INDEX] +
                            length*i/threadCnt +1,
                    field.x + plotSheet.getFrameThickness()[PlotSheet.LEFT_FRAME_THICKNESS_INDEX] +
                            length*(i+1)/threadCnt,function);
			threads[i] = new Thread(partRenderer[i]);
		}
		if(threadCnt > 1){
		gnew = bimages[threadCnt-1].getGraphics();
		gnew.setClip(field);
		partRenderer[threadCnt-1] = new PartRenderer(gnew,
                field.x + plotSheet.getFrameThickness()[PlotSheet.LEFT_FRAME_THICKNESS_INDEX] +
                        length*(threadCnt-1)/threadCnt +1,
                field.x+ plotSheet.getFrameThickness()[PlotSheet.LEFT_FRAME_THICKNESS_INDEX] + length,function);
		threads[threadCnt-1] = new Thread(partRenderer[threadCnt-1]);
		}
		for(Thread thread : threads) {
			thread.start();
		}
		
		for(Thread thread : threads) {
			thread.join();
		}
		
		for(BufferedImageWrap bimage: bimages){
			g.drawImage(bimage, null, 0, 0);
		}
		
		//
	}
	
	/**
	 * draws bordered relief plot
	 * @param g graphic object used to draw relief
	 */
	private void drawBorders(GraphicsWrap g) {
		RectangleWrap field = g.getClipBounds();
		double[] thisCoordinate = plotSheet.toCoordinatePoint(0, 0, field);
		double[] upToThisCoordinate = plotSheet.toCoordinatePoint(0, 0, field);
		double[] leftToThisCoordinate = plotSheet.toCoordinatePoint(0, 0, field);
		
		double thisF_xy;
		double upToThisF_xy;
		double leftToThisF_xy;
		
		for(int i = Math.round(field.x+plotSheet.getFrameThickness()[PlotSheet.LEFT_FRAME_THICKNESS_INDEX] + 1);
            i < field.x + field.width-plotSheet.getFrameThickness()[PlotSheet.RIGHT_FRAME_THICKNESS_INDEX]; i++) {
			for(int j = Math.round(field.y+plotSheet.getFrameThickness()[PlotSheet.UPPER_FRAME_THICKNESS_INDEX] + 1);
                j < field.y +field.height-plotSheet.getFrameThickness()[PlotSheet.BOTTOM_FRAME_THICKNESS_INDEX]; j++) {
				thisCoordinate = plotSheet.toCoordinatePoint(i, j, field);
				upToThisCoordinate = plotSheet.toCoordinatePoint(i, j-1, field);
				leftToThisCoordinate = plotSheet.toCoordinatePoint(i-1, j, field);
				thisF_xy = function.f(thisCoordinate[0], thisCoordinate[1]);
				upToThisF_xy = function.f(upToThisCoordinate[0], upToThisCoordinate[1]);
				leftToThisF_xy = function.f(leftToThisCoordinate[0], leftToThisCoordinate[1]);
				
				if(onBorder(thisF_xy, upToThisF_xy) || onBorder(thisF_xy, leftToThisF_xy)) {
					g.drawLine(i, j, i, j);
				}
				
			}
		}
	}
	
	/**
	 * if the bounds have changed the min and max height of relief has to be determined anew
	 * @return
	 */
	private boolean rangeHasChanged() {
		boolean tester = true;
		
		tester &= plotSheet.getxRange()[0] == this.xrange[0];
		tester &= plotSheet.getxRange()[1] == this.xrange[1];
		tester &= plotSheet.getyRange()[0] == this.yrange[0];
		tester &= plotSheet.getyRange()[1] == this.yrange[1];
		
		if(!tester) {
			this.xrange = plotSheet.getxRange().clone();
			this.yrange = plotSheet.getyRange().clone();
		}
		
		return !tester || this.depthSearchAborted;
	}
	
	/**
	 * scan depth of relief to determine distance between borders
	 * @param field bounds of plot
	 */
	private void scanDepth(RectangleWrap field) throws InterruptedException {
		depthSearchAborted = true;
		double[] coordinate = plotSheet.toCoordinatePoint(0, 0, field);
		double f_xy = function.f(coordinate[0], coordinate[1]);
		this.f_xHighest = f_xy;
		this.f_xLowest 	= f_xy;

        float length = (field.x + field.width-plotSheet.getFrameThickness()[PlotSheet.RIGHT_FRAME_THICKNESS_INDEX]) -
                (field.x+plotSheet.getFrameThickness()[PlotSheet.LEFT_FRAME_THICKNESS_INDEX]);
		Thread[] threads = new Thread[threadCnt];

        float stepSize = length/threadCnt;
		
		DepthSearcher[] dSearcher = new DepthSearcher[threadCnt];

        float leftLim = field.x+plotSheet.getFrameThickness()[PlotSheet.LEFT_FRAME_THICKNESS_INDEX];
        float rightLim = (field.x + plotSheet.getFrameThickness()[PlotSheet.LEFT_FRAME_THICKNESS_INDEX]+ (stepSize));
		dSearcher[0] = new DepthSearcher(field,leftLim ,rightLim );
		threads[0] = new Thread(dSearcher[0]);
		for(int i = 1; i< threads.length-1; i++){
			dSearcher[i] = new DepthSearcher(field,
                    field.x + plotSheet.getFrameThickness()[PlotSheet.LEFT_FRAME_THICKNESS_INDEX] + stepSize*i +1,
                    field.x + plotSheet.getFrameThickness()[PlotSheet.LEFT_FRAME_THICKNESS_INDEX] + stepSize*(i+1));
			threads[i] = new Thread(dSearcher[i]);
		}
		if(threadCnt>1){
			dSearcher[threadCnt-1] = new DepthSearcher(field,
                    field.x + plotSheet.getFrameThickness()[PlotSheet.LEFT_FRAME_THICKNESS_INDEX] +
                            stepSize*(threadCnt-1) +1,
                    field.x + plotSheet.getFrameThickness()[PlotSheet.LEFT_FRAME_THICKNESS_INDEX] + length);
			threads[threadCnt-1] = new Thread(dSearcher[threadCnt-1]);
		}
		for(Thread thread : threads) {
			thread.start();
		}
		
		for(Thread thread : threads) {
			thread.join();
		}
		
		for(DepthSearcher searcher : dSearcher ){
			if(searcher.getF_xHighest() > this.f_xHighest)
				this.f_xHighest = searcher.getF_xHighest();
			if(searcher.getF_xLowest() < this.f_xLowest)
				this.f_xLowest = searcher.getF_xLowest();
		}
		
		//System.err.println(this.f_xHighest + " : " + this.f_xLowest);
		//create borders based on heigth gradient
		borders = new double[this.heightRegionCount];
		double steps = (this.f_xHighest - this.f_xLowest)/this.heightRegionCount;
		
		for(int i = 0; i < borders.length ; i++) {
			borders[i] =  this.f_xLowest +  (this.f_xHighest - this.f_xLowest)*Math.pow((1.0/this.heightRegionCount)*(i+1.0), gradientCurveFactor);
			//System.err.println(borders[i]+" " + (this.f_xHighest - this.f_xLowest)*Math.pow((1.0/this.heightRegionCount)*(i+1.0), gradientCurveFactor));
		}
		if(!this.abortPaint){
			depthSearchAborted = false;
			depthScanningIsFinished = true;
		}
	}
	
	/**
	 * returns true if a pixel on plot is directly on a border which has to be drawn
	 * @param f_xy the current function value
	 * @param f_xyNext the function value of a neighbor
	 * @return true if between those two function values a border has to be drawn
	 */
	private boolean onBorder(double f_xy, double f_xyNext) {
		double lowerBorder = this.f_xLowest;
		double higherBorder = this.f_xHighest;

        for (double border : borders) {
            higherBorder = border;
            if ((f_xy >= lowerBorder && f_xy < higherBorder) || (f_xyNext >= lowerBorder && f_xyNext < higherBorder)) {

                return !((f_xy >= lowerBorder && f_xy < higherBorder) && (f_xyNext >= lowerBorder && f_xyNext < higherBorder));
            }
            lowerBorder = higherBorder;
        }
		
		return true;
		
	}
	
	/**
	 * get the gradient color for the corresponding function value
	 * @param f_xy function value
	 * @return color that corresponds to the function value
	 */
	private ColorWrap getColor(double f_xy) {
		double lowerBorder = this.f_xLowest;
		double higherBorder = this.f_xHighest;
		try{

			for(int i = 0 ; i< borders.length; i++) {
				higherBorder = borders[i];
				if((f_xy >= lowerBorder && f_xy < higherBorder)) {
					return this.gradientColors[i];
					
				}
				lowerBorder = higherBorder;
			}
		} catch(NullPointerException e){
			e.printStackTrace();
			System.exit(-1);
		}
		
		
		return (f_xy < borders[0])? this.gradientColors[0] : this.gradientColors[this.gradientColors.length-1];
		
	}
	
	/*
	 * (non-Javadoc)
	 * @see rendering.Drawable#isOnFrame()
	 */
	public boolean isOnFrame() {
		return false;
	}
	
	public Drawable getLegend() {
		return new ReliefLegend();
	}
	
	
	
	
	public void setThreadCnt(int threadCnt) {
		this.threadCnt = threadCnt;
	}




	/**
	 * Legend for ReliefDrawer as Drawable implementing inner class
	 * @author Michael Goldbach
	 *
	 */
	private class ReliefLegend implements Drawable{
		
		/**
		 * Format that is used to print numbers under markers
		 */
		private DecimalFormat df =   new DecimalFormat( "##0.00#" );	
		private DecimalFormat dfScience =   new DecimalFormat( "0.0###E0" );
		private boolean isAborted = false;
		private boolean isScientific = false;
		
		/*
		 * (non-Javadoc)
		 * @see rendering.Drawable#paint(java.awt.Graphics)
		 */
		public void paint(GraphicsWrap g) {
			isAborted = false;
			while(!ReliefDrawer.this.depthScanningIsFinished || rangeHasChanged()){
				if(this.isAborted){
					System.err.println("no relief legend will be drawn!");
					return;
				}
				try {
					
					Thread.sleep(100);
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
			ReliefDrawer.this.depthScanningIsFinished = false;
			ColorWrap oldColor = g.getColor();
			RectangleWrap field = g.getClipBounds();
			double deltaZ = (ReliefDrawer.this.yrange[1] - ReliefDrawer.this.yrange[0])/ReliefDrawer.this.borders.length;
			
			
			@SuppressWarnings("deprecation")
            float leftStart = plotSheet.xToGraphic(ReliefDrawer.this.xrange[1], field) + 10;
			
			double lowerStart = ReliefDrawer.this.f_xLowest;
			double upperEnd = 0;
			double currentHeight = ReliefDrawer.this.yrange[0];
			double yToZQuotient = Math.abs(ReliefDrawer.this.yrange[1] - ReliefDrawer.this.yrange[0])/(ReliefDrawer.this.f_xHighest - ReliefDrawer.this.f_xLowest);
			
			//draw colors for legend
            for (double border : ReliefDrawer.this.borders) {

                upperEnd = border;
                deltaZ = yToZQuotient * (upperEnd - lowerStart);
                ColorWrap regionColor = ReliefDrawer.this.getColor((lowerStart + upperEnd) / 2);

                g.setColor(regionColor);
                g.fillRect(leftStart, plotSheet.yToGraphic(currentHeight + deltaZ, field), 10, plotSheet.yToGraphic(currentHeight, field) - plotSheet.yToGraphic(currentHeight + deltaZ, field));

                currentHeight += deltaZ;
                lowerStart = upperEnd;
            }
			
			g.setColor(ColorWrap.black);
			double ztics = ticsCalc(ReliefDrawer.this.f_xHighest - ReliefDrawer.this.f_xLowest, 12);
			
			
			double startY = ReliefDrawer.this.yrange[0];
			double startZ = ReliefDrawer.this.f_xLowest;
			
			double currentY = startY;
			double currentZ = startZ;
			
			if(ztics < 1e-2 || ztics > 1e3)
				this.isScientific = true;
			
			//draw numbering left to the color bar
			while(currentY <= ReliefDrawer.this.yrange[1]) {
				if(this.isScientific)
					g.drawString(df.format(currentZ), leftStart + 22, plotSheet.yToGraphic(currentY, field));
				else
					g.drawString(dfScience.format(currentZ), leftStart + 22, plotSheet.yToGraphic(currentY, field));
				currentZ+=ztics;
				currentY += yToZQuotient*ztics;
			}

			g.setColor(oldColor);
		}
		
		/**
		 * calculate nice logical tics
		 * @param deltaRange range
		 * @param ticlimit number of maximal tics in given range
		 * @return tics for the specified parameters
		 */
		private double ticsCalc(double deltaRange, float ticlimit){
			double tics = Math.pow(10, (int)Math.log10(deltaRange/ticlimit));
			while(2.0*(deltaRange/(tics)) <= ticlimit) {
				tics /= 2.0;
			}
			while((deltaRange/(tics))/2 >= ticlimit) {
				tics *= 2.0;
			}
			return tics;
		}

		/*
		 * (non-Javadoc)
		 * @see rendering.Drawable#isOnFrame()
		 */
		public boolean isOnFrame() {
			return true;
		}

		@Override
		public void abortAndReset() {
			isAborted = true;
			
		}
        @Override
        public boolean isClusterable() {
            return false;
        }

        @Override
        public boolean isCritical() {
            return false;
        }

    }
	private class DepthSearcher implements Runnable{

		double f_xHighest = 0;
		double f_xLowest = 0;
		
		RectangleWrap field = null;
        float leftLim = 0;
        float rightLim = 0;
        Function3D function;

		public DepthSearcher(RectangleWrap field, float leftLim, float rightLim) {
			super();
			this.field = field;
			this.leftLim = leftLim;
			this.rightLim = rightLim;
            if(ReliefDrawer.this.function instanceof TopLevelParser)
                function = ((TopLevelParser)ReliefDrawer.this.function).createCopy();
            else function = ReliefDrawer.this.function;
		}


		@Override
		public void run() {
			double[] coordinate = plotSheet.toCoordinatePoint(0, 0, field);
			double f_xy = function.f(coordinate[0], coordinate[1]);
			this.f_xHighest = f_xy;
			this.f_xLowest 	= f_xy;
			
			//scan for minimum and maximum f(x,y) in the given range
			for(int i = Math.round(leftLim); i <= rightLim; i+=pixelSkip) {
				for(int j = Math.round(field.y+plotSheet.getFrameThickness()[PlotSheet.UPPER_FRAME_THICKNESS_INDEX]);
                    j < field.y +field.height-plotSheet.getFrameThickness()[PlotSheet.BOTTOM_FRAME_THICKNESS_INDEX];
                    j+=pixelSkip) {
					if(abortPaint){
						return;
					}
					coordinate = plotSheet.toCoordinatePoint(i, j, field);
					f_xy = function.f(coordinate[0], coordinate[1]);
					if(f_xy < this.f_xLowest && f_xy != Double.NaN && f_xy != Double.NEGATIVE_INFINITY && f_xy != Double.POSITIVE_INFINITY ) {
						this.f_xLowest 	= f_xy;
					} 
					if(f_xy > this.f_xHighest && f_xy != Double.NaN && f_xy != Double.NEGATIVE_INFINITY && f_xy != Double.POSITIVE_INFINITY ) {
						this.f_xHighest 	= f_xy;
					}
					
					
				}
			}
			
		}

		public double getF_xHighest() {
			return f_xHighest;
		}


		public double getF_xLowest() {
			return f_xLowest;
		}

	}
	
	private class PartRenderer implements Runnable{

		GraphicsWrap g = null;
		RectangleWrap field = null;
        float leftLim = 0;
        float rightLim = 0;
        Function3D function;

		public PartRenderer(GraphicsWrap g, float leftLim, float rightLim, Function3D function) {
			super();
			this.field = g.getClipBounds();
			this.leftLim = leftLim;
			this.rightLim = rightLim;
			this.g = g;
            if(function instanceof TopLevelParser)
                function = ((TopLevelParser)function).createCopy();
            this.function = function;
		}


		@Override
		public void run() {
			double[] thisCoordinate = plotSheet.toCoordinatePoint(0, 0, field);
			
			double thisF_xy;
			for(int i = Math.round(leftLim) ; i <= rightLim; i+=pixelSkip) {
				for(int j = Math.round(field.y + +plotSheet.getFrameThickness()[PlotSheet.UPPER_FRAME_THICKNESS_INDEX]);
                    j < field.y +field.height -plotSheet.getFrameThickness()[PlotSheet.BOTTOM_FRAME_THICKNESS_INDEX];
                    j+=pixelSkip) {
					if(abortPaint)
						return;
					thisCoordinate = plotSheet.toCoordinatePoint(i, j, field);
					thisF_xy = function.f(thisCoordinate[0], thisCoordinate[1]);
					g.setColor(getColor(thisF_xy));
					g.fillRect(i, j, pixelSkip, pixelSkip);
//					g.drawLine(i, j, i, j);
					
				}
			}
			
		}

	}

	@Override
	public void abortAndReset() {
		abortPaint = true;
		
	}

	public float getPixelSkip() {
		return pixelSkip;
	}

	public void setPixelSkip(float pixelSkip) {
		this.pixelSkip = pixelSkip;
	}

    @Override
    public boolean isClusterable() {
        return false;
    }

    @Override
    public boolean isCritical() {
        return false;
    }
}
