/**
 * 
 */
package com.wildplot.android.rendering;

import com.wildplot.android.rendering.interfaces.Drawable;

import java.util.Vector;

/**
 * This class is used to store informations for a certain plot in a multi-plot sheet.
 * The informations are the drawables for this plotsheet and the x and y limitations
 *
 */
public class MultiScreenPart {
	private double[] xRange = {-15,15};
	private double[] yRange = {-10,10};
	private Vector<Drawable> drawables = new Vector<Drawable>();
	
	
	
	/**
	 *  Constructor for a screen part,
	 * a screen part is build with its information about x- and y-range.
	 * Drawable objects that will be drawn on the screen part can be added after construction.
	 * 
	 * @param xRange
	 * @param yRange
	 */
	public MultiScreenPart(double[] xRange, double[] yRange) {
		super();
		this.xRange = xRange;
		this.yRange = yRange;
	}
	
	/**
	 * Constructor for a screen part,
	 * a screen part is build with its information about x- and y-range, aswell as a list of Drawable objects that will be drawn
	 * onto the screen part
	 * 
	 * @param xRange
	 * @param yRange
	 * @param drawables
	 */
	public MultiScreenPart(double[] xRange, double[] yRange, Vector<Drawable> drawables) {
		super();
		this.xRange = xRange;
		this.yRange = yRange;
		this.drawables = drawables;
	}
	
	/**
	 * get the x-range of this screen part
	 * @return the xRange
	 */
	public double[] getxRange() {
		return xRange;
	}
	
	/**
	 * set the x-range of this screen-part
	 * @param xRange the xRange to set
	 */
	public void setxRange(double[] xRange) {
		this.xRange = xRange;
	}
	
	/**
	 * Get the y-range of this screen part
	 * @return the yRange
	 */
	public double[] getyRange() {
		return yRange;
	}
	
	/**
	 * Set the y-range for this screen part
	 * @param yRange the yRange to set
	 */
	public void setyRange(double[] yRange) {
		this.yRange = yRange;
	}
	/**
	 * get the Drawable objects associated with this screen part
	 * @return the drawables
	 */
	public Vector<Drawable> getDrawables() {
		return drawables;
	}
	
	/**
	 * add another Drawable object that shall be drawn onto the sheet
	 * 
	 * @param draw Drawable object which will be added to plot sheet
	 */
	public void addDrawable(Drawable draw) {
		this.drawables.add(draw);
	}
	
}
