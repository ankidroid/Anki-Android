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

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;

public class StringUtils {

	/**
	 * Converts an InputStream to a String
	 * 
	 * @param is
	 *            InputStream to convert
	 * @return String version of the InputStream
	 */
	public static String convertStreamToString(InputStream is)
	{
		String contentOfMyInputStream = "";
		try
		{
			BufferedReader rd = new BufferedReader(new InputStreamReader(is), 4096);
			String line;
			StringBuilder sb = new StringBuilder();
			while ((line = rd.readLine()) != null)
			{
				sb.append(line);
			}
			rd.close();
			contentOfMyInputStream = sb.toString();
		} catch (Exception e)
		{
			e.printStackTrace();
		}

		return contentOfMyInputStream;
	}
}
