/****************************************************************************************
* Copyright (c) 2009 Daniel Svärd <daniel.svard@gmail.com>                             *
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

import java.util.List;
import java.util.Random;
import java.util.TreeSet;

import org.json.JSONArray;
import org.json.JSONException;

/**
 * TODO comments
 */
public class Utils {
	
	private static TreeSet<Integer> idTree;
	private static long idTime;

	public static long genID() {
		long time = System.currentTimeMillis();
		long id;
		int rand;
		Random random = new Random();

		if (idTree == null) {
			idTree = new TreeSet<Integer>();
			idTime = time;
		}
		else if (idTime != time) {
			idTime = time;
			idTree.clear();
		}

		while (true) {
			rand = random.nextInt(2^23);
			if (!idTree.contains(new Integer(rand))) {
				idTree.add(new Integer(rand));
				break;
			}
		}
		id = rand << 41 | time;
		return id;
	}
	
	/**
	 * Returns a SQL string from an array of integers.
	 *
	 * @param ids
	 *            The array of integers to include in the list.
	 * @return An SQL compatible string in the format (ids[0],ids[1],..).
	 */
	public static String ids2str(long[] ids)
	{
		String str = "(";
		int len = ids.length;
		for (int i = 0; i < len; i++)
		{
			if (i == (len - 1))
				str += ids[i];
			else
				str += ids[i] + ",";
		}
		str += ")";
		return str;
	}

	/**
	 * Returns a SQL string from an array of integers.
	 *
	 * @param ids
	 *            The array of integers to include in the list.
	 * @return An SQL compatible string in the format (ids[0],ids[1],..).
	 */
	public static String ids2str(JSONArray ids)
	{
		String str = "(";
		int len = ids.length();
		for (int i = 0; i < len; i++)
		{
			try {
				if (i == (len - 1))
				{
					str += ids.get(i);
				}	
				else
				{
					str += ids.get(i) + ",";
				}
			} catch (JSONException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		str += ")";
		return str;
	}
	
	public static JSONArray listToJSONArray(List list)
	{
		JSONArray jsonArray = new JSONArray();
		
		int len = list.size();
		for(int i = 0; i < len; i++)
		{
			jsonArray.put(list.get(i));
		}
		
		return jsonArray;
	}
	
	/**
	 * Returns a SQL string from an array of integers.
	 *
	 * @param ids
	 *            The array of integers to include in the list.
	 * @return An SQL compatible string in the format (ids[0],ids[1],..).
	 */
	public static String ids2str(List<String> ids)
	{
		String str = "(";
		int len = ids.size();
		for (int i = 0; i < len; i++)
		{
			if (i == (len - 1))
			{
				str += ids.get(i);
			}	
			else
			{
				str += ids.get(i) + ",";
			}
		}
		str += ")";
		return str;
	}
}
