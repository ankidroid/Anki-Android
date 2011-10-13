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

package com.ichi2.libanki;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.net.Uri;
import android.text.Html;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import com.ichi2.anki.AnkiDroidApp;
import com.ichi2.anki.R;
import com.ichi2.anki.R.array;
import com.ichi2.anki.R.color;
import com.mindprod.common11.BigDate;
import com.tomgibara.android.veecheck.util.PrefSettings;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Date;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.GregorianCalendar;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.TreeSet;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.Deflater;

/**
 * TODO comments
 */
public class Utils {

    // Used to format doubles with English's decimal separator system
    public static final Locale ENGLISH_LOCALE = new Locale("en_US");

    public static final int CHUNK_SIZE = 32768;

    private static final int DAYS_BEFORE_1970 = 719163;

    private static NumberFormat mCurrentNumberFormat;
    private static NumberFormat mCurrentPercentageFormat;

    private static TreeSet<Long> sIdTree;
    private static long sIdTime;

    private static final int TIME_SECONDS = 0;
    private static final int TIME_MINUTES = 1;
    private static final int TIME_HOURS = 2;
    private static final int TIME_DAYS = 3;
    private static final int TIME_MONTHS = 4;
    private static final int TIME_YEARS = 5;

    public static final int TIME_FORMAT_DEFAULT = 0;
    public static final int TIME_FORMAT_IN = 1;
    public static final int TIME_FORMAT_BEFORE = 2;

    /* Prevent class from being instantiated */
    private Utils() { }

    // Regex pattern used in removing tags from text before diff
    private static final Pattern imgPattern = Pattern.compile("<img src=[\"']?([^\"'>]+)[\"']? ?/?>");
    private static final Pattern stylePattern = Pattern.compile("(?s)<style.*?>.*?</style>");
    private static final Pattern scriptPattern = Pattern.compile("(?s)<script.*?>.*?</script>");
    private static final Pattern tagPattern = Pattern.compile("<.*?>");
    private static final Pattern htmlEntitiesPattern = Pattern.compile("&#?\\w+;");


    /**
     * Return a string representing a time span (eg '2 days').
     * @param inFormat: if true, return eg 'in 2 days'
     */
    public static String fmtTimeSpan(double time, int format) {
    	return fmtTimeSpan(time, format, false);
    }
    public static String fmtTimeSpan(double time, int format, boolean boldNumber) {
    	int type;
    	int unit = 99;
    	int point = 0;
    	if (Math.abs(time) < 60 || unit < 1) {
    		type = TIME_SECONDS;
    	} else if (Math.abs(time) < 3599 || unit < 2) {
    		type = TIME_MINUTES;
    	} else if (Math.abs(time) < 60 * 60 * 24 || unit < 3) {
    		type = TIME_HOURS;
    	} else if (Math.abs(time) < 60 * 60 * 24 * 29.5 || unit < 4) {
    		type = TIME_DAYS;
    	} else if (Math.abs(time) < 60 * 60 * 24 * 30 * 11.95 || unit < 5) {
    		type = TIME_MONTHS;
    		point = 1;
    	} else {
    		type = TIME_YEARS;
    		point = 1;
    	}
    	time = convertSecondsTo(time, type);

    	int formatId;
    	switch (format) {
    	case TIME_FORMAT_IN:
    		if (Math.round(time * 10) == 10) {
    			formatId = R.array.next_review_in_s;
    		} else {
    			formatId = R.array.next_review_in_p;    			
    		}
    		break;
    	case TIME_FORMAT_BEFORE:
    		if (Math.round(time * 10) == 10) {
    			formatId = R.array.next_review_before_s;
    		} else {
    			formatId = R.array.next_review_before_p;    			
    		}
    		break;
    	case TIME_FORMAT_DEFAULT:
    	default:
    		if (Math.round(time * 10) == 10) {
    			formatId = R.array.next_review_s;
    		} else {
    			formatId = R.array.next_review_p;    			
    		}
    		break;
    	}

    	String timeString = String.format(AnkiDroidApp.getAppResources().getStringArray(formatId)[type], boldNumber ? "<b>" + fmtDouble(time, point) + "</b>" : fmtDouble(time, point));
		if (boldNumber && time == 1) {
			timeString = timeString.replace("1", "<b>1</b>");
		}
		return timeString;
    }


    private static double convertSecondsTo(double seconds, int type) {
    	switch (type) {
    	case TIME_SECONDS:
    		return seconds;
    	case TIME_MINUTES:
    		return seconds / 60.0;
    	case TIME_HOURS:
    		return seconds / 3600.0;    		
    	case TIME_DAYS:
    		return seconds / 86400.0;    		
    	case TIME_MONTHS:
    		return seconds / 2592000.0;    		
    	case TIME_YEARS:
    		return seconds / 31536000.0;
		default:
    		return 0;
    	}
    }


    /**
     * Locale
     * ***********************************************************************************************
     */

    /**
     * Get the current time in seconds since January 1, 1970 UTC.
     * @return the local system time in seconds
     */
    public static double now() {
        return (System.currentTimeMillis() / 1000.0);
    }


    /**
     * Get the current time in integer seconds since January 1, 1970 UTC.
     * @return the local system time in integer seconds
     */
    public static int intNow() {
        return (int) now();
    }


    /**
     * Return a string representing a time span (eg '2 days').
     * @param inFormat: if true, return eg 'in 2 days'
     */
    public static String fmtTimeSpan(double time, boolean inFormat) {
    	int type;
    	int unit = 99;
    	int point = 0;
    	if (Math.abs(time) < 60 || unit < 1) {
    		type = TIME_SECONDS;
    	} else if (Math.abs(time) < 3599 || unit < 2) {
    		type = TIME_MINUTES;
    	} else if (Math.abs(time) < 60 * 60 * 24 || unit < 3) {
    		type = TIME_HOURS;
    	} else if (Math.abs(time) < 60 * 60 * 24 * 30 || unit < 4) {
    		type = TIME_DAYS;
    	} else if (Math.abs(time) < 60 * 60 * 24 * 365 || unit < 5) {
    		type = TIME_MONTHS;
    		point = 1;
    	} else {
    		type = TIME_YEARS;
    		point = 1;
    	}
    	time = convertSecondsTo(time, type);

    	Resources res = AnkiDroidApp.getAppResources();
   		if (!inFormat) {
   	    	if (time == 1){
   	       		return res.getStringArray(R.array.next_review_s)[type];
   	       	} else {
   	       		return String.format(res.getStringArray(R.array.next_review_p)[type], fmtDouble(time, point));
   	    	}   			
   		} else {
   	    	if (time == 1){
   	       		return res.getStringArray(R.array.next_review_in_s)[type]; 			       			
   	       	} else {
   	       		return String.format(res.getStringArray(R.array.next_review_in_p)[type], fmtDouble(time, point));
   	    	}   			
   		}
    }


    private static double convertSecondsTo(double seconds, int type) {
    	switch (type) {
    	case TIME_SECONDS:
    		return seconds;
    	case TIME_MINUTES:
    		return seconds / 60.0;
    	case TIME_HOURS:
    		return seconds / 3600.0;    		
    	case TIME_DAYS:
    		return seconds / 86400.0;    		
    	case TIME_MONTHS:
    		return seconds / 2592000.0;    		
    	case TIME_YEARS:
    		return seconds / 31536000.0;
		default:
    		return 0;
    	}
    }


    /**
     * Locale
     * ***********************************************************************************************
     */

    /**
     * @return double with percentage sign
     */
    public static String fmtPercentage(Double value) {
	return fmtPercentage(value, 0);
    }
    public static String fmtPercentage(Double value, int point) {
    	// only retrieve the percentage format the first time
    	if (mCurrentPercentageFormat == null) {
    		mCurrentPercentageFormat = NumberFormat.getPercentInstance(Locale.getDefault());
    	}
    	mCurrentNumberFormat.setMaximumFractionDigits(point);
    	return mCurrentPercentageFormat.format(value);
    }


    /**
     * @return a string with decimal separator according to current locale
     */
    public static String fmtDouble(Double value) {
    	return fmtDouble(value, 1);
    }
    public static String fmtDouble(Double value, int point) {
    	// only retrieve the number format the first time
    	if (mCurrentNumberFormat == null) {
    		mCurrentNumberFormat = NumberFormat.getInstance(Locale.getDefault());
    	}
    	mCurrentNumberFormat.setMaximumFractionDigits(point);
    	return mCurrentNumberFormat.format(value);
    }

    /**
     * HTML
     * ***********************************************************************************************
     */

    /**
     * Strip HTML but keep media filenames
     */
    public static String stripHTMLMedia(String s) {
        Matcher imgMatcher = imgPattern.matcher(s);
        return stripHTML(imgMatcher.replaceAll(" $1 "));
    }


    public static String stripHTML(String s) {
        Matcher styleMatcher = stylePattern.matcher(s);
        s = styleMatcher.replaceAll("");
        Matcher scriptMatcher = scriptPattern.matcher(s);
        s = scriptMatcher.replaceAll("");
        Matcher tagMatcher = tagPattern.matcher(s);
        s = tagMatcher.replaceAll("");
        return entsToTxt(s);
    }


    private static String entsToTxt(String s) {
        Matcher htmlEntities = htmlEntitiesPattern.matcher(s);
        StringBuilder s2 = new StringBuilder(s);
        while (htmlEntities.find()) {
            String text = htmlEntities.group();
            text = Html.fromHtml(text).toString();
            // TODO: inefficiency below, can get rid of multiple regex searches
            s2.replace(htmlEntities.start(), htmlEntities.end(), text);
            htmlEntities = htmlEntitiesPattern.matcher(s2);
        }
        return s2.toString();
    }

    /**
     * IDs
     * ***********************************************************************************************
     */

    public static String hexifyID(int id) {
        return Integer.toHexString(id);
    }


    public static int dehexifyID(String id) {
    	return Integer.valueOf(id, 16);
    }


    /**
     * Returns a SQL string from an array of integers.
     * @param ids The array of integers to include in the list.
     * @return An SQL compatible string in the format (ids[0],ids[1],..).
     */
    public static String ids2str(int[] ids) {
        String str = "()";
        if (ids != null) {
            str = Arrays.toString(ids);
            str = "(" + str.substring(1, str.length()-1) + ")";
        }
        return str;
    }


    /**
     * Returns a SQL string from an array of integers.
     * @param ids The array of integers to include in the list.
     * @return An SQL compatible string in the format (ids[0],ids[1],..).
     */
    public static String ids2str(JSONArray ids) {
        StringBuilder str = new StringBuilder(512);
        str.append("(");
        if (ids != null) {
            int len = ids.length();
            for (int i = 0; i < len; i++) {
                try {
                    if (i == (len - 1)) {
                        str.append(ids.get(i));
                    } else {
                        str.append(ids.get(i)).append(",");
                    }
                } catch (JSONException e) {
                    Log.e(AnkiDroidApp.TAG, "JSONException = " + e.getMessage());
                }
            }
        }
        str.append(")");
        return str.toString();
    }


    /**
     * Returns a SQL string from an array of integers.
     * @param ids The array of integers to include in the list.
     * @return An SQL compatible string in the format (ids[0],ids[1],..).
     */
    public static String ids2str(List<String> ids) {
        StringBuilder str = new StringBuilder(512);
        str.append("(");
        if (ids != null) {
            int len = ids.size();
            for (int i = 0; i < len; i++) {
                if (i == (len - 1)) {
                    str.append(ids.get(i));
                } else {
                    str.append(ids.get(i)).append(",");
                }
            }
        }
        str.append(")");
        return str.toString();
    }


    public static JSONArray listToJSONArray(List<Object> list) {
        JSONArray jsonArray = new JSONArray();

        for (Object o : list) {
            jsonArray.put(o);
        }

        return jsonArray;
    }


    public static List<String> jsonArrayToListString(JSONArray jsonArray) throws JSONException {
        ArrayList<String> list = new ArrayList<String>();

        int len = jsonArray.length();
        for (int i = 0; i < len; i++) {
            list.add(jsonArray.getString(i));
        }

        return list;
    }
        

    /**
     * Tags
     * ***********************************************************************************************
     */

    /**
     * Parse a string and return a list of tags.
     * 
     * @param tags A string containing tags separated by space or comma (optionally followed by space)
     * @return An array of Strings containing the individual tags 
     */
    public static String[] parseTags(String tags) {
        if (tags != null && tags.length() != 0) {
            return tags.split("\\s");
        } else {
            return new String[] {};
        }
    }


    /**
     * Join tags into a single string, with leading and trailing spaces.
     * 
     * @param tags The list of tags to join
     * @return The joined tags in a single string 
     */
    public static String joinTags(Collection<String> tags) {
        if (tags == null || tags.size() == 0) {
            return "";
        } else {
            StringBuilder result = new StringBuilder(128);
            result.append(" ");
            for (String tag : tags) {
                result.append(tag).append(" ");
            }
            return result.toString();
        }
    }


    /**
     * Strip leading/trailing/superfluous spaces and duplicates.
     * 
     * @param tags The list of tags
     * @return The canonified string, as described above
     */
    public static String canonifyTags(String[] tags) {
        for (int i = 0; i < tags.length; i++) {
            if (tags[i].startsWith(":")) {
                tags[i].replace("^:+", "");
            }
        }
        return joinTags(new TreeSet<String>(Arrays.asList(tags)));
    }


    /**
     * Find if tag exists in a set of tags. The search is not case-sensitive
     * 
     * @param tag The tag to look for
     * @param tags The set of tags
     * @return True is the tag is found in the set, false otherwise
     */
    public static boolean hasTag(String tag, List<String> tags) {
        String lowercase = tag.toLowerCase();
        for (String t : tags) {
            if (t.toLowerCase().compareTo(lowercase) == 0) {
                return true;
            }
        }
        return false;
    }


    /**
     * Add tags if they don't exist.
     * Both parameters are in string format, the tags being separated by space or comma, as in parseTags
     * 
     * @param tagStr The new tag(s) that are to be added
     * @param tags The set of tags where the new ones will be added
     * @return A string containing the union of tags of the input parameters
     */
    public static String addTags(String addtags, String tags) {
        ArrayList<String> currentTags = new ArrayList<String>(Arrays.asList(parseTags(tags)));
        for (String tag : parseTags(addtags)) {
            if (!hasTag(tag, currentTags)) {
                currentTags.add(tag);
            }
        }
        return canonifyTags((String[]) currentTags.toArray());
    }

    /**
     * Fields
     * ***********************************************************************************************
     */

    public static String joinFields(String[] list) {
        StringBuilder result = new StringBuilder(128);
        for (int i = 0; i < list.length - 1; i++) {
            result.append(list[i]).append("\u001f");
        }
        result.append(list[list.length - 1]);
        return result.toString();
    }


    public static String[] splitFields(String fields) {
    	return fields.split("\\x1f");
    }


    /**
     * Checksums
     * ***********************************************************************************************
     */

    /**
     * MD5 checksum.
     * Equivalent to python md5.hexdigest()
     *
     * @param data the string to generate hash from
     * @return A string of length 32 containing the hexadecimal representation of the MD5 checksum of data.
     */
    public static String checksum(String data) {
        String result = "";
        if (data != null) {
            MessageDigest md = null;
            byte[] digest = null;
            try {
                md = MessageDigest.getInstance("MD5");
                digest = md.digest(data.getBytes("UTF-8"));
            } catch (NoSuchAlgorithmException e) {
                Log.e(AnkiDroidApp.TAG, "Utils.checksum: No such algorithm. " + e.getMessage());
                throw new RuntimeException(e);
            } catch (UnsupportedEncodingException e) {
                Log.e(AnkiDroidApp.TAG, "Utils.checksum: " + e.getMessage());
                e.printStackTrace();
            }
            BigInteger biginteger = new BigInteger(1, digest);
            result = biginteger.toString(16);
            // pad with zeros to length of 32
            if (result.length() < 32) {
                result = "00000000000000000000000000000000".substring(0, 32 - result.length()) + result;
            }
        }
        return result;
    }


    /**
     * @param data the string to generate hash from
     * @return 32 bit unsigned number from first 8 digits of md5 hash
     */
    public static int fieldChecksum(String data) {
	return Integer.valueOf(checksum(data).substring(0, 8), 16);
    }


    /**
     * MD5 sum of file.
     * Equivalent to checksum(open(os.path.join(mdir, file), "rb").read()))
     *
     * @param path The full path to the file
     * @return A string of length 32 containing the hexadecimal representation of the MD5 checksum of the contents
     * of the file
     */
    public static String fileChecksum(String path) {
        byte[] bytes = null;
        try {
            File file = new File(path);
            if (file != null && file.isFile()) {
                bytes = new byte[(int)file.length()];
                FileInputStream fin = new FileInputStream(file);
                fin.read(bytes);
            }
        } catch (FileNotFoundException e) {
            Log.e(AnkiDroidApp.TAG, "Can't find file " + path + " to calculate its checksum");
        } catch (IOException e) {
            Log.e(AnkiDroidApp.TAG, "Can't read file " + path + " to calculate its checksum");
        }
        if (bytes == null) {
            Log.w(AnkiDroidApp.TAG, "File " + path + " appears to be empty");
            return "";
        }
        MessageDigest md = null;
        byte[] digest = null;
        try {
            md = MessageDigest.getInstance("MD5");
            digest = md.digest(bytes);
        } catch (NoSuchAlgorithmException e) {
            Log.e(AnkiDroidApp.TAG, "Utils.checksum: No such algorithm. " + e.getMessage());
            throw new RuntimeException(e);
        }
        BigInteger biginteger = new BigInteger(1, digest);
        String result = biginteger.toString(16);
        // pad with zeros to length of 32
        if (result.length() < 32) {
            result = "00000000000000000000000000000000".substring(0, 32 - result.length()) + result;
        }
        return result;
    }


    /**
     * 
     * ***********************************************************************************************
     */

    /**
     * Converts an InputStream to a String.
     * @param is InputStream to convert
     * @return String version of the InputStream
     */
    public static String convertStreamToString(InputStream is) {
        String contentOfMyInputStream = "";
        try {
            BufferedReader rd = new BufferedReader(new InputStreamReader(is), 4096);
            String line;
            StringBuilder sb = new StringBuilder();
            while ((line = rd.readLine()) != null) {
                sb.append(line);
            }
            rd.close();
            contentOfMyInputStream = sb.toString();
        } catch (Exception e) {
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
    public static byte[] compress(byte[] bytesToCompress) throws IOException {
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
        while (!compressor.finished()) {
            bos.write(buf, 0, compressor.deflate(buf));
        }

        bos.close();

        // Get the compressed data
        return bos.toByteArray();
    }


    /**
     * Utility method to write to a file.
     * Throws the exception, so we can report it in syncing log
     * @throws IOException 
     */
    public static void writeToFile(InputStream source, String destination) throws IOException {
        Log.i(AnkiDroidApp.TAG, "Creating new file... = " + destination);
        new File(destination).createNewFile();

        long startTimeMillis = System.currentTimeMillis();
        OutputStream output = new BufferedOutputStream(new FileOutputStream(destination));

        // Transfer bytes, from source to destination.
        byte[] buf = new byte[CHUNK_SIZE];
        long sizeBytes = 0;
        int len;
        if (source == null) {
            Log.i(AnkiDroidApp.TAG, "source is null!");
        }
        while ((len = source.read(buf)) > 0) {
            output.write(buf, 0, len);
            sizeBytes += len;
            // Log.i(AnkiDroidApp.TAG, "Write...");
        }
        long endTimeMillis = System.currentTimeMillis();

        Log.i(AnkiDroidApp.TAG, "Finished writing!");
        long durationSeconds = (endTimeMillis - startTimeMillis) / 1000;
        long sizeKb = sizeBytes / 1024;
        long speedKbSec = 0;
        if (endTimeMillis != startTimeMillis) {
            speedKbSec = sizeKb * 1000 / (endTimeMillis - startTimeMillis);
        }
        Log.d(AnkiDroidApp.TAG, "Utils.writeToFile: " + "Size: " + sizeKb + "Kb, " + "Duration: " + durationSeconds + "s, " + "Speed: " + speedKbSec + "Kb/s");
        output.close();
    }


    // Print methods
    public static void printJSONObject(JSONObject jsonObject) {
        printJSONObject(jsonObject, "-", false);
    }


    public static void printJSONObject(JSONObject jsonObject, boolean writeToFile) {
        if (writeToFile) {
            new File("/sdcard/payloadAndroid.txt").delete();
        }
        printJSONObject(jsonObject, "-", writeToFile);
    }


    public static void printJSONObject(JSONObject jsonObject, String indentation, boolean writeToFile) {
        try {

            Iterator<String> keys = jsonObject.keys();
            TreeSet<String> orderedKeysSet = new TreeSet<String>();
            while (keys.hasNext()) {
                orderedKeysSet.add(keys.next());
            }

            Iterator<String> orderedKeys = orderedKeysSet.iterator();
            while (orderedKeys.hasNext()) {
                String key = orderedKeys.next();

                try {
                    Object value = jsonObject.get(key);
                    if (value instanceof JSONObject) {
                        if (writeToFile) {
                            BufferedWriter buff = new BufferedWriter(new FileWriter("/sdcard/payloadAndroid.txt", true));
                            buff.write(indentation + " " + key + " : ");
                            buff.newLine();
                            buff.close();
                        }
                        Log.i(AnkiDroidApp.TAG, "	" + indentation + key + " : ");
                        printJSONObject((JSONObject) value, indentation + "-", writeToFile);
                    } else {
                        if (writeToFile) {
                            BufferedWriter buff = new BufferedWriter(new FileWriter("/sdcard/payloadAndroid.txt", true));
                            buff.write(indentation + " " + key + " = " + jsonObject.get(key).toString());
                            buff.newLine();
                            buff.close();
                        }
                        Log.i(AnkiDroidApp.TAG, "	" + indentation + key + " = " + jsonObject.get(key).toString());
                    }
                } catch (JSONException e) {
                    Log.e(AnkiDroidApp.TAG, "JSONException = " + e.getMessage());
                }
            }

        } catch (IOException e1) {
            Log.e(AnkiDroidApp.TAG, "IOException = " + e1.getMessage());
        }

    }


    public static void saveJSONObject(JSONObject jsonObject) throws IOException {
        Log.i(AnkiDroidApp.TAG, "saveJSONObject");
        BufferedWriter buff = new BufferedWriter(new FileWriter("/sdcard/jsonObjectAndroid.txt", true));
        buff.write(jsonObject.toString());
        buff.close();
    }


    /**
     * Returns 1 if true, 0 if false
     *
     * @param b The boolean to convert to integer
     * @return 1 if b is true, 0 otherwise
     */
    public static int booleanToInt(boolean b) {
        return (b) ? 1 : 0;
    }


    /**
     *  Returns the effective date of the present moment.
     *  If the time is prior the cut-off time (9:00am by default as of 11/02/10) return yesterday,
     *  otherwise today
     *  Note that the Date class is java.sql.Date whose constructor sets hours, minutes etc to zero
     *
     * @param utcOffset The UTC offset in seconds we are going to use to determine today or yesterday.
     * @return The date (with time set to 00:00:00) that corresponds to today in Anki terms
     */
    public static Date genToday(double utcOffset) {
        // The result is not adjusted for timezone anymore, following libanki model
        // Timezone adjustment happens explicitly in Deck.updateCutoff(), but not in Deck.checkDailyStats()
        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd");
        df.setTimeZone(TimeZone.getTimeZone("GMT"));
        Calendar cal = new GregorianCalendar(TimeZone.getTimeZone("GMT"));
        cal.setTimeInMillis(System.currentTimeMillis() - (long) utcOffset * 1000l);
        Date today = Date.valueOf(df.format(cal.getTime()));
        return today;
    }


    public static String doubleToTime(double value) {
    	int time = (int) Math.round(value);
    	int seconds = time % 60;
    	int minutes = (time - seconds) / 60;
    	String formattedTime;
    	if (seconds < 10) {
    		formattedTime = Integer.toString(minutes) + ":0" + Integer.toString(seconds);
    	} else {
    		formattedTime = Integer.toString(minutes) + ":" + Integer.toString(seconds);
    	}
    	return formattedTime;
    }


    /**
     * Returns the proleptic Gregorian ordinal of the date, where January 1 of year 1 has ordinal 1.
     * @param date Date to convert to ordinal, since 01/01/01
     * @return The ordinal representing the date
     */
    public static int dateToOrdinal(Date date) {
        // BigDate.toOrdinal returns the ordinal since 1970, so we add up the days from 01/01/01 to 1970
        return BigDate.toOrdinal(date.getYear() + 1900, date.getMonth() + 1, date.getDate()) + DAYS_BEFORE_1970;
    }


    /**
     * Return the date corresponding to the proleptic Gregorian ordinal, where January 1 of year 1 has ordinal 1.
     * @param ordinal representing the days since 01/01/01
     * @return Date converted from the ordinal
     */
    public static Date ordinalToDate(int ordinal) {
        return new Date((new BigDate(ordinal - DAYS_BEFORE_1970)).getLocalDate().getTime());
    }


    /**
     * Indicates whether the specified action can be used as an intent. This method queries the package manager for
     * installed packages that can respond to an intent with the specified action. If no suitable package is found, this
     * method returns false.
     * @param context The application's environment.
     * @param action The Intent action to check for availability.
     * @return True if an Intent with the specified action can be sent and responded to, false otherwise.
     */
    public static boolean isIntentAvailable(Context context, String action) {
        return isIntentAvailable(context, action, null);
    }


    public static boolean isIntentAvailable(Context context, String action, ComponentName componentName) {
        final PackageManager packageManager = context.getPackageManager();
        final Intent intent = new Intent(action);
        intent.setComponent(componentName);
        List<ResolveInfo> list = packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY);
        return list.size() > 0;
    }


    public static String getBaseUrl(String mediaDir, Model model, Deck deck) {
        String base;
		try {
			base = deck.getConf().getString("mediaURL");
		} catch (JSONException e) {
			throw new RuntimeException(e);
		}
        if (base.length() != 0 && !base.equalsIgnoreCase("null")) {
            return base;
        } else {
            // Anki desktop calls deck.mediaDir() here, but for efficiency reasons we only call it once in
            // Reviewer.onCreate() and use the value from there            
            if (mediaDir != null) {                              
                base = urlEncodeMediaDir(mediaDir);
            }
        }
        return base;
    }


    /**
     * @param mediaDir media directory path on SD card
     * @return path converted to file URL, properly UTF-8 URL encoded
     */
    public static String urlEncodeMediaDir(String mediaDir) {
        String base;
        // Use android.net.Uri class to ensure whole path is properly encoded
        // File.toURL() does not work here, and URLEncoder class is not directly usable
        // with existing slashes
        Uri mediaDirUri = Uri.fromFile(new File(mediaDir));

        // Build complete URL
        base = mediaDirUri.toString() +"/";

        return base;
    }


    /**
     * Take an array of Long and return an array of long
     * 
     * @param array The input with type Long[]
     * @return The output with type long[]
     */
    public static long[] toPrimitive(Long[] array) {
        long[] results = new long[array.length];
        if (array != null) {
            for (int i = 0; i < array.length; i++) {
                results[i] = array[i].longValue();
            }
        }
        return results;
    }
    public static long[] toPrimitive(Collection<Long> array) {
        long[] results = new long[array.size()];
        if (array != null) {
            int i = 0;
            for (Long item : array) {
                results[i++] = item.longValue();
            }
        }
        return results;
    }
  

    public static void updateProgressBars(Context context, View view, double progress, int maxX, int y, boolean singleBar) {
    	updateProgressBars(context, view, progress, maxX, y, singleBar, true);
    }
	public static void updateProgressBars(Context context, View view, double progress, int maxX, int y, boolean singleBar, boolean changeColor) {
        if (view == null) {
            return;
        }
        if (singleBar) {
        	if (changeColor) {
                if (progress < 0.5) {
                    view.setBackgroundColor(context.getResources().getColor(R.color.progressbar_1));
                } else if (progress < 0.65) {
                    view.setBackgroundColor(context.getResources().getColor(R.color.progressbar_2));
                } else if (progress < 0.75) {
                    view.setBackgroundColor(context.getResources().getColor(R.color.progressbar_3));
                } else {
                    view.setBackgroundColor(context.getResources().getColor(R.color.progressbar_4));            
                }        		
        	}
            FrameLayout.LayoutParams lparam = new FrameLayout.LayoutParams(0, 0);            
            lparam.height = y;
            lparam.width = (int) (maxX * progress);
            view.setLayoutParams(lparam);
        } else {
            LinearLayout.LayoutParams lparam = new LinearLayout.LayoutParams(0, 0);            
            lparam.height = y;
            lparam.width = (int) (maxX * progress);
            view.setLayoutParams(lparam);
        }
    }  


    /**
     * Calculate the UTC offset
     */
    public static double utcOffset() {
        Calendar cal = Calendar.getInstance();
        // 4am
        return 4 * 60 * 60 - (cal.get(Calendar.ZONE_OFFSET) + cal.get(Calendar.DST_OFFSET)) / 1000;
    }

    /**
     * Adds a menu item to the given menu.
     */
    public static MenuItem addMenuItem(Menu menu, int groupId, int itemId, int order, int titleRes,
            int iconRes) {
        MenuItem item = menu.add(groupId, itemId, order, titleRes);
        item.setIcon(iconRes);
        return item;
    }

    /**
     * Adds a menu item to the given menu and marks it as a candidate to be in the action bar.
     */
    public static MenuItem addMenuItemInActionBar(Menu menu, int groupId, int itemId, int order,
            int titleRes, int iconRes) {
        MenuItem item = addMenuItem(menu, groupId, itemId, order, titleRes, iconRes);
        setShowAsActionIfRoom(item);
        return item;
    }

    /**
     * Sets the menu item to appear in the action bar via reflection.
     * <p>
     * This method uses reflection so that it works on all platforms. It any error occurs, assume
     * the action bar is not available and just proceed.
     */
    private static void setShowAsActionIfRoom(MenuItem item) {
        try {
            Field showAsActionIfRoom = item.getClass().getField("SHOW_AS_ACTION_IF_ROOM");
            Method setShowAsAction = item.getClass().getMethod("setShowAsAction", int.class);
            setShowAsAction.invoke(item, showAsActionIfRoom.get(null));
        } catch (SecurityException e) {
        } catch (NoSuchMethodException e) {
        } catch (NoSuchFieldException e) {
        } catch (IllegalArgumentException e) {
        } catch (IllegalAccessException e) {
        } catch (InvocationTargetException e) {
        } catch (NullPointerException e) {
        }
    }

    /** Returns the filename without the extension. */
    public static String removeExtension(String filename) {
      int dotPosition = filename.lastIndexOf('.');
      if (dotPosition == -1) {
        return filename;
      }
      return filename.substring(0, dotPosition);
    }

    /** Returns a list of files for the installed custom fonts. */
    public static File[] getCustomFonts(Context context) {
        SharedPreferences preferences = PrefSettings.getSharedPrefs(context);
        String deckPath = preferences.getString("deckPath",
                AnkiDroidApp.getStorageDirectory() + "/AnkiDroid");
        String fontsPath = deckPath + "/fonts/";
        File fontsDir = new File(fontsPath);
        if (!fontsDir.exists() || !fontsDir.isDirectory()) {
          return new File[0];
        }
        return fontsDir.listFiles();
    }
}
