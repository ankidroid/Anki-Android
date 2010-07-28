/****************************************************************************************
* Copyright (c) 2009 Daniel Svärd <daniel.svard@gmail.com>                             *
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

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.sql.Date;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.TreeSet;
import java.util.zip.Deflater;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.util.Log;

import com.mindprod.common11.BigDate;

/**
 * TODO comments
 */
public class Utils {
	
	private static final String TAG = "AnkiDroid";
	
	private static final int CHUNK_SIZE = 32768;
	
	private static final long MILLIS_IN_A_DAY = 86400000;
	private static final int DAYS_BEFORE_1970 = 719163;
	
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
				Log.i(TAG, "JSONException = " + e.getMessage());
			}
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
	
	public static List<String> jsonArrayToListString(JSONArray jsonArray) throws JSONException
	{
		ArrayList<String> list = new ArrayList<String>();
		
		int len = jsonArray.length();
		for(int i = 0; i < len; i++)
		{
			list.add(jsonArray.getString(i));
		}
		
		return list;
	}
	
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
	
	/**
	 * Compress data.
	 * @param bytesToCompress is the byte array to compress.
	 * @return a compressed byte array.
	 * @throws java.io.IOException
	 */
	public static byte[] compress(byte[] bytesToCompress) throws IOException
	{
		// Compressor with highest level of compression.
		Deflater compressor = new Deflater(Deflater.BEST_COMPRESSION);
		// Give the compressor the data to compress.
		compressor.setInput(bytesToCompress); 
		compressor.finish();

		// Create an expandable byte array to hold the compressed data.
		// It is not necessary that the compressed data will be smaller than
		// the uncompressed data.
		ByteArrayOutputStream bos = new ByteArrayOutputStream(bytesToCompress.length);

		// Compress the data
		byte[] buf = new byte[bytesToCompress.length + 100];
		while (!compressor.finished())
		{
			bos.write(buf, 0, compressor.deflate(buf));
		}

		bos.close();

		// Get the compressed data
		return bos.toByteArray();
	}
	

	/**
	 * Utility method to write to a file.
	 */
	public static boolean writeToFile(InputStream source, String destination)
	{
		try
		{
			Log.i(TAG, "Creating new file... = " + destination);
			new File(destination).createNewFile();
	
			OutputStream output = new FileOutputStream(destination);
			
			// Transfer bytes, from source to destination.
			byte[] buf = new byte[CHUNK_SIZE];
			int len;
			if(source == null) Log.i(TAG, "source is null!");
			while ((len = source.read(buf)) > 0)
			{
				output.write(buf, 0, len);
				Log.i(TAG, "Write...");
			}
			
			Log.i(TAG, "Finished writing!");
			output.close();

		} catch (Exception e) {
			return false;
		}
		return true;
	}
	
	// Print methods
	public static void printJSONObject(JSONObject jsonObject)
	{
		printJSONObject(jsonObject, "-", false);
	}
	
	public static void printJSONObject(JSONObject jsonObject, boolean writeToFile)
	{
		if(writeToFile)
		{
			new File("/sdcard/payloadAndroid.txt").delete();
		}
		printJSONObject(jsonObject, "-", writeToFile);
	}
	
	public static void printJSONObject(JSONObject jsonObject, String indentation, boolean writeToFile)
	{
		try {
			
			Iterator<String> keys = jsonObject.keys();
			TreeSet<String> orderedKeysSet = new TreeSet<String>();
			while(keys.hasNext())
			{
				orderedKeysSet.add(keys.next());
			}
			
			Iterator<String> orderedKeys = orderedKeysSet.iterator();
			while(orderedKeys.hasNext())
			{
				String key = orderedKeys.next();

				try {
					Object value = jsonObject.get(key);
					if(value instanceof JSONObject)
					{
						if(writeToFile)
						{
							BufferedWriter buff = new BufferedWriter(new FileWriter("/sdcard/payloadAndroid.txt", true));
							buff.write(indentation + " " + key + " : ");
							buff.newLine();
							buff.close();
						}
						Log.i(TAG, "	" + indentation + key + " : ");
						printJSONObject((JSONObject)value, indentation + "-", writeToFile);
					}
					else
					{
						if(writeToFile)
						{
							BufferedWriter buff = new BufferedWriter(new FileWriter("/sdcard/payloadAndroid.txt", true));
							buff.write(indentation + " " + key + " = " + jsonObject.get(key).toString());
							buff.newLine();
							buff.close();
						}
						Log.i(TAG, "	" + indentation + key + " = " + jsonObject.get(key).toString());
					}
				} catch (JSONException e) {
					Log.i(TAG, "JSONException = " + e.getMessage());
				}
			}
			
		} catch (IOException e1) {
			Log.i(TAG, "IOException = " + e1.getMessage());
		}
		
	}

	public static void saveJSONObject(JSONObject jsonObject) throws IOException
	{
		Log.i(TAG, "saveJSONObject");
		BufferedWriter buff = new BufferedWriter(new FileWriter("/sdcard/jsonObjectAndroid.txt", true));
		buff.write(jsonObject.toString());
		buff.close();
	}
	
	public static int booleanToInt(boolean b)
	{
		return (b)?1:0;
	}
	
	/**
	 * Returns the proleptic Gregorian ordinal of the date, where January 1 of year 1 has ordinal 1
	 * @param date Date to convert to ordinal, since 01/01/01
	 * @return The ordinal representing the date
	 */
	public static int dateToOrdinal(Date date)
	{
		//BigDate.toOrdinal returns the ordinal since 1970, so we add up the days from 01/01/01 to 1970
		return BigDate.toOrdinal(date.getYear() + 1900, date.getMonth() + 1, date.getDate()) + DAYS_BEFORE_1970;
	}
	
	/**
	 * Return the date corresponding to the proleptic Gregorian ordinal, where January 1 of year 1 has ordinal 1
	 * @param ordinal representing the days since 01/01/01
	 * @return Date converted from the ordinal
	 */
	public static Date ordinalToDate(int ordinal)
	{
		return new Date((long)(ordinal - DAYS_BEFORE_1970) * MILLIS_IN_A_DAY);
	}
	
	//Test for dateToOrdinal and fromOrdintalToDate
	/*
	 				boolean error = false;
				
				int year = 0;
				int month = 0;
				int day = 1;
				Calendar cal = Calendar.getInstance();
				for(year = 0; year < 111; year++)
				{
					if(error) break;
					Log.i(TAG, "/--------------- YEAR: " + (year + 1900) + " -----------------/");
					cal.set(Calendar.YEAR, year + 1900);
					
					for(month = 0; month < 12; month++)
					{
						if(error) break;
						cal.set(Calendar.MONTH, month);
						cal.set(Calendar.DAY_OF_MONTH, 1);
						Log.i(TAG, "/--------------- MONTH: " + (month + 1) + " -----------------/");
						
						int maxDaysOnMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH);
						//Log.i(TAG, "Maxim days on this month = " + maxDaysOnMonth);
						
						for(day = 1; day <= maxDaysOnMonth; day++)
						{
							Log.i(TAG, "/--------------- DAY: " + day + " -----------------/");
							cal.set(Calendar.DAY_OF_MONTH, day);
							Log.i(TAG, "Calendar = " + cal.get(Calendar.YEAR) + "-" + (cal.get(Calendar.MONTH) + 1) + "-" + cal.get(Calendar.DAY_OF_MONTH));
							Date testDate = new Date(year,month,day);
							//Date testDate = new Date(System.currentTimeMillis());
							//Log.i(TAG, "Current millis = " + System.currentTimeMillis());
							Log.i(TAG, "Day = " + testDate);
							//Log.i(TAG, "Day of the month = " + testDate.getDate());
							//Log.i(TAG, "Month = " + (testDate.getMonth() + 1));
							//Log.i(TAG, "Year = " + (testDate.getYear() + 1900));
							int ordinal = Utils.dateToOrdinal(testDate);
							Log.i(TAG, "Ordinal day = " + ordinal);
							Date date2 = Utils.fromOrdinalToDate(ordinal);
							Log.i(TAG, "Back to date = " + date2);
							//Log.i(TAG, "Day of the month = " + date2.getDate());
							//Log.i(TAG, "Month = " + (date2.getMonth() + 1));
							//Log.i(TAG, "Year = " + (date2.getYear() + 1900));
							
							if(testDate.getDate() == date2.getDate() && testDate.getMonth() == date2.getMonth() && testDate.getYear() == date2.getYear())
							{
								Log.i(TAG, "OK!");
							}
							else
							{
								Log.e(TAG, "ERROOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOR!!!");
								error = true;
								break;
							}
								
						}
					}
				}
				
				Log.i(TAG, "PERFECT!!! ^^");
	 */
}
