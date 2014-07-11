/**
 * 
 */
package com.wildplot.android.rendering;


import com.wildplot.android.rendering.graphics.wrapper.Color;

import java.util.Vector;

/**
 * @author Michael Goldbach
 *
 */
public class RelativeColorGradient {
	public static Color[] makeGradient(Vector<Color> colorVector, int numberOfColorsInGradient) {
		if(colorVector == null) {
			colorVector = new Vector<Color>();
		}
		
		if(colorVector.size() < 2) {
			colorVector.add(new Color(255,255,255));
			if(colorVector.size() < 2) {
				colorVector.add(0, new Color(0,0,0));
			}
		}
		numberOfColorsInGradient -= colorVector.size();
		
		while(numberOfColorsInGradient >= 0) {
			Color firstColor = colorVector.get(0);
			Color secondColor = colorVector.get(1);
			int index = 1;
			double highestDelta = 0;
			
			//determine the color with the biggest difference to the color before in the vector
			for(int i = 1; i<colorVector.size(); i++) {
				secondColor = colorVector.get(i);
				double delta  = delta(firstColor.getRed(), secondColor.getRed());
				delta  += delta(firstColor.getGreen(), secondColor.getGreen());
				delta  += delta(firstColor.getBlue(), secondColor.getBlue());
				delta = Math.pow(delta, 1.0/3.0);
				if(delta > highestDelta) {
					highestDelta = delta;
					index = i;
				}
				firstColor = secondColor;
			}
			
			//add new Color between those two colors, new color is middled between those two
			firstColor	= colorVector.get(index-1);
			secondColor	= colorVector.get(index);
			int newRed		= firstColor.getRed() - (firstColor.getRed() - secondColor.getRed())/2;
			int newGreen 	= firstColor.getGreen() - (firstColor.getGreen() - secondColor.getGreen())/2;
			int newBlue 	= firstColor.getBlue() - (firstColor.getBlue() - secondColor.getBlue())/2;
			
			colorVector.add(index, new Color(newRed, newGreen, newBlue));
			numberOfColorsInGradient--;
		}
		
		
		return colorVector.toArray(new Color[colorVector.size()]);
	}
	private static double delta(int first, int second) {
		return Math.pow(Math.abs(first -second), 3);
	}
	
	
	
}
