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

import java.util.Vector;


public class RelativeColorGradient {
	public static ColorWrap[] makeGradient(Vector<ColorWrap> colorVector, int numberOfColorsInGradient) {
		if(colorVector == null) {
			colorVector = new Vector<>();
		}
		
		if(colorVector.size() < 2) {
			colorVector.add(new ColorWrap(255,255,255));
			if(colorVector.size() < 2) {
				colorVector.add(0, new ColorWrap(0,0,0));
			}
		}
		numberOfColorsInGradient -= colorVector.size();
		
		while(numberOfColorsInGradient >= 0) {
			ColorWrap firstColor = colorVector.get(0);
			ColorWrap secondColor = colorVector.get(1);
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
			
			colorVector.add(index, new ColorWrap(newRed, newGreen, newBlue));
			numberOfColorsInGradient--;
		}
		
		
		return colorVector.toArray(new ColorWrap[colorVector.size()]);
	}
	private static double delta(int first, int second) {
		return Math.pow(Math.abs(first -second), 3);
	}
	
	
	
}
