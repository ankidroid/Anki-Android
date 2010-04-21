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

package com.ichi2.utils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

import android.util.Log;

public class FileUtils {

	private static final String TAG = "AnkiDroid";
	
	private static final int CHUNK_SIZE = 32768;
	
	/**
	 * Utility method to write to a file.
	 */
	public static boolean writeToFile(InputStream source, String destination)
	{
		//Log.i(TAG, "writeToFile = " + destination);
		try
		{
			//Log.i(TAG, "createNewFile");
			new File(destination).createNewFile();
			//Log.i(TAG, "New file created");
	
			OutputStream output = new FileOutputStream(destination);
			
			// Transfer bytes, from source to destination.
			byte[] buf = new byte[CHUNK_SIZE];
			int len;
			if(source == null) Log.i(TAG, "source is null!");
			while ((len = source.read(buf)) > 0)
			{
				//Log.i(TAG, "Writing to file...");
				output.write(buf, 0, len);
			}
			
			output.close();
			//Log.i(TAG, "Write finished!");

		} catch (Exception e) {
			//Log.i(TAG, "IOException e = " + e.getMessage());
			return false;
		}
		return true;
	}
}
