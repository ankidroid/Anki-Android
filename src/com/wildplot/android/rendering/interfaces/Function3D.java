/**
 * 
 */
package com.wildplot.android.rendering.interfaces;

/**
 * Interface for functions with two dependent variables
 *
 */
public interface Function3D {
	
	/**
	 * calculate function value with given x and y values
	 * @param x given x value
	 * @param y given y value
	 * @return function value calculated with given x and y values
	 */
	public double f(double x, double y);
}
