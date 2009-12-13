/****************************************************************************************
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

import android.util.Log;

/**
 * Class used to display and handle correctly images
 *
 */
public class Image {
	
	/**
	 * Tag for logging messages
	 */
	private static final String TAG = "Ankidroid";
	
	/**
	 * 
	 * @param deckFilename Deck's filename whose images are going to be load
	 * @param content HTML content of a card's side (question or answer)
	 * @return content Modified content in order to display correctly the images
	 */
	public static String loadImages(String deckFilename, String content)
	{
		Log.i(TAG, "Image - loadImages, filename = " + deckFilename);
		String imagePath = deckFilename.replaceAll(".anki", ".media/");
		return content.replaceAll("<img src=\"", "<img src=\"" + "content://com.ichi2.anki" + imagePath);
	}
}
