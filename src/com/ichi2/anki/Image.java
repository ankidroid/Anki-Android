/***************************************************************************************
* Copyright (c) 2009 Edu Zamora <edu.zasu@gmail.com>                                   *
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

package com.ichi2.anki;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;

/**
 * Class used to display and handle correctly images
 */
public class Image {
	
	/**
	 * Tag for logging messages
	 */
	private static final String TAG = "AnkiDroid";
	
	/**
	 * Pattern used to identify img tags
	 */
	private static Pattern mImagePattern = Pattern.compile("<img src=\"([^\"<>]*)\"");

	/**
	 * 
	 * @param deckFilename Deck's filename whose images are going to be scaled
	 * @param content HTML content of a card's side (question or answer)
	 * @param displayHeight The height of the display where the image has to be shown
	 * @param displayWidth The width of the display where the image has to be shown
	 * @param scaleInPercent Scale factor that will be applied to the images later on
	 * @return content Modified content in order to display correctly the images
	 */
	public static String scaleImages(String deckFilename, String content, int displayHeight, int displayWidth, float scaleInPercent)
	{
		Log.i(TAG, "Image - loadImages, filename = " + deckFilename);
		
		Log.i(TAG, "Display height = " + displayHeight);
		Log.i(TAG, "Display width = " + displayWidth);
		
		// Leave some margin
		displayWidth -= 15;
		
		String imagePath = deckFilename.replace(".anki", ".media/");
		Log.i(TAG, "Image path = " + imagePath);
		
		// Find img tags on content
		Matcher matcher = mImagePattern.matcher(content);
		// While there is matches of the pattern for img tags
		while(matcher.find())
		{
			Log.i(TAG, "img match = " + matcher.group());
			Log.i(TAG, "img file match = " + matcher.group(1));
			// Get the image filename
			String image = matcher.group(1);
			if(image.startsWith("http://"))
			{
				// TODO: If it is a remote image...
				
			}
			else
			{
				// If it is a local image
				
				Bitmap imageBitmap = BitmapFactory.decodeFile(imagePath + image);
				if(imageBitmap != null)
				{
					// Get the real height and width of the image
					int imageHeight = imageBitmap.getHeight();
					int imageWidth = imageBitmap.getWidth();
					Log.i(TAG, "img height = " + imageHeight + ", width = " + imageWidth);

					// If image's width is greater than display's width
					if(imageWidth > displayWidth)
					{
						// Calculate the proper height and width for the current image in order to fit in the available display width
						float scaleFactor = (float) imageWidth / (float) displayWidth;
						Log.i(TAG, "Scale factor = " + scaleFactor);
						
						imageHeight = (int) (imageHeight / scaleFactor);
						Log.i(TAG, "Scaled img height = " + imageHeight);
						
						imageWidth = (int) (imageWidth / scaleFactor);
						Log.i(TAG, "Scaled img width = " + imageWidth);
					}
					
					// Counter the scale factor that will be applied later
					imageHeight = (int) (imageHeight / scaleInPercent);
					imageWidth = (int) (imageWidth / scaleInPercent);
					
					// Apply scaled height and scaled width
					content = content.replaceFirst(matcher.group(), matcher.group() + " height=\"" + (imageHeight) + "\" width=\"" + (imageWidth) + "\"");
				}
			}
		}
		
		return content;
	}
}
