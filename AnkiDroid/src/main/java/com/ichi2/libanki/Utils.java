/****************************************************************************************
 * Copyright (c) 2009 Daniel Svärd <daniel.svard@gmail.com>                             *
 * Copyright (c) 2009 Edu Zamora <edu.zasu@gmail.com>                                   *
 * Copyright (c) 2011 Norbert Nagold <norbert.nagold@gmail.com>                         *
 * Copyright (c) 2012 Kostas Spyropoulos <inigo.aldana@gmail.com>                       *
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
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.text.Html;

import com.ichi2.anki.AnkiFont;
import com.ichi2.anki.CollectionHelper;
import com.ichi2.anki.R;
import com.ichi2.utils.LanguageUtil;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.nio.channels.FileChannel;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Date;
import java.text.Normalizer;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import timber.log.Timber;

public class Utils {
    // Used to format doubles with English's decimal separator system
    public static final Locale ENGLISH_LOCALE = new Locale("en_US");

    public static final int CHUNK_SIZE = 32768;

    private static NumberFormat mCurrentPercentageFormat;

    // These are doubles on purpose because we want a rounded, not integer result later.
    private static final double TIME_MINUTE = 60.0;  // seconds
    private static final double TIME_HOUR = 60 * TIME_MINUTE;
    private static final double TIME_DAY = 24 * TIME_HOUR;
    // How long is a year? This is a tropical year, according to NIST.
    // http://www.physics.nist.gov/Pubs/SP811/appenB9.html
    private static final double TIME_YEAR = 31556930.0;  // seconds
    // Pretty much everybody agrees that one year is twelve months
    private static final double TIME_MONTH = TIME_YEAR / 12.0;


    // List of all extensions we accept as font files.
    private static final String[] FONT_FILE_EXTENSIONS = new String[] {".ttf",".ttc",".otf"};

    /* Prevent class from being instantiated */
    private Utils() { }

    // Regex pattern used in removing tags from text before diff
    private static final Pattern stylePattern = Pattern.compile("(?s)<style.*?>.*?</style>");
    private static final Pattern scriptPattern = Pattern.compile("(?s)<script.*?>.*?</script>");
    private static final Pattern tagPattern = Pattern.compile("<.*?>");
    private static final Pattern imgPattern = Pattern.compile("<img src=[\\\"']?([^\\\"'>]+)[\\\"']? ?/?>");
    private static final Pattern htmlEntitiesPattern = Pattern.compile("&#?\\w+;");

    private static final String ALL_CHARACTERS = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    private static final String BASE91_EXTRA_CHARS = "!#$%&()*+,-./:;<=>?@[]^_`{|}~";

    public static final int FILE_COPY_BUFFER_SIZE = 2048;

    /**The time in integer seconds. Pass scale=1000 to get milliseconds. */
    public static double now() {
        return (System.currentTimeMillis() / 1000.0);
    }


    /**The time in integer seconds. Pass scale=1000 to get milliseconds. */
    public static long intNow() {
        return intNow(1);
    }
    public static long intNow(int scale) {
        return (long) (now() * scale);
    }

    /**
     * Return a string representing a time quantity
     *
     * @param context The application's environment.
     * @param time_s The time to format, in seconds
     * @return The time quantity string. Something like "3 s" or "1.7 yr".
     */
    public static String timeQuantity(Context context, long time_s) {
        Resources res = context.getResources();
        // N.B.: the integer s, min, h, d and (one decimal, rounded by format) double for month, year is
        // hard-coded. See also 01-core.xml
        if (Math.abs(time_s) < TIME_MINUTE ) {
            return res.getString(R.string.time_quantity_seconds, time_s);
        } else if (Math.abs(time_s) < TIME_HOUR) {
            return res.getString(R.string.time_quantity_minutes, (int) Math.round(time_s/TIME_MINUTE));
        } else if (Math.abs(time_s) < TIME_DAY) {
            return res.getString(R.string.time_quantity_hours, (int) Math.round(time_s/TIME_HOUR));
        } else if (Math.abs(time_s) < TIME_MONTH) {
            return res.getString(R.string.time_quantity_days, (int) Math.round(time_s/TIME_DAY));
        } else if (Math.abs(time_s) < TIME_YEAR) {
            return res.getString(R.string.time_quantity_months, time_s/TIME_MONTH);
        } else {
            return res.getString(R.string.time_quantity_years, time_s/TIME_YEAR);
        }
    }

    /**
     * Return a string representing a time
     * (If you want a certain unit, use the strings directly)
     *
     * @param context The application's environment.
     * @param time_s The time to format, in seconds
     * @return The formatted, localized time string. The time is always an integer.
     */
    public static String timeSpan(Context context, long time_s) {
        int time_x;  // Time in unit x
        Resources res = context.getResources();
        if (Math.abs(time_s) < TIME_MINUTE ) {
            time_x = (int) time_s;
            return res.getQuantityString(R.plurals.time_span_seconds, time_x, time_x);
        } else if (Math.abs(time_s) < TIME_HOUR) {
            time_x = (int) Math.round(time_s/TIME_MINUTE);
            return res.getQuantityString(R.plurals.time_span_minutes, time_x, time_x);
        } else if (Math.abs(time_s) < TIME_DAY) {
            time_x = (int) Math.round(time_s/TIME_HOUR);
            return res.getQuantityString(R.plurals.time_span_hours, time_x, time_x);
        } else if (Math.abs(time_s) < TIME_MONTH) {
            time_x = (int) Math.round(time_s/TIME_DAY);
            return res.getQuantityString(R.plurals.time_span_days, time_x, time_x);
        } else if (Math.abs(time_s) < TIME_YEAR) {
            time_x = (int) Math.round(time_s/TIME_MONTH);
            return res.getQuantityString(R.plurals.time_span_months, time_x, time_x);
        } else {
            time_x = (int) Math.round(time_s/TIME_YEAR);
            return res.getQuantityString(R.plurals.time_span_years, time_x, time_x);
        }
    }

    /**
     * Return a proper string for a time value in seconds
     *
     * @param context The application's environment.
     * @param time_s The time to format, in seconds
     * @return The formatted, localized time string. The time is always a float.
     */
    public static String roundedTimeSpan(Context context, long time_s) {
        if (Math.abs(time_s) < TIME_DAY) {
            return context.getResources().getString(R.string.stats_overview_hours, time_s/TIME_HOUR);
        } else if (Math.abs(time_s) < TIME_MONTH) {
            return context.getResources().getString(R.string.stats_overview_days, time_s/TIME_DAY);
        } else if (Math.abs(time_s) < TIME_YEAR) {
            return context.getResources().getString(R.string.stats_overview_months,time_s/TIME_MONTH);
        } else {
            return context.getResources().getString(R.string.stats_overview_years, time_s/TIME_YEAR);
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
            mCurrentPercentageFormat = NumberFormat.getPercentInstance(LanguageUtil.getLocale());
        }
        return mCurrentPercentageFormat.format(value);
    }

    // Removed fmtDouble(). Was used only by other functions here. We now use getString() with localized formatting now.

    /**
     * HTML
     * ***********************************************************************************************
     */

    /**
     * Strips a text from <style>...</style>, <script>...</script> and <_any_tag_> HTML tags.
     * @param s The HTML text to be cleaned.
     * @return The text without the aforementioned tags.
     */
    public static String stripHTML(String s) {
        Matcher htmlMatcher = stylePattern.matcher(s);
        s = htmlMatcher.replaceAll("");
        htmlMatcher = scriptPattern.matcher(s);
        s = htmlMatcher.replaceAll("");
        htmlMatcher = tagPattern.matcher(s);
        s = htmlMatcher.replaceAll("");
        return entsToTxt(s);
    }


    /**
     * Strip HTML but keep media filenames
     */
    public static String stripHTMLMedia(String s) {
        Matcher imgMatcher = imgPattern.matcher(s);
        return stripHTML(imgMatcher.replaceAll(" $1 "));
    }


    /**
     * Takes a string and replaces all the HTML symbols in it with their unescaped representation.
     * This should only affect substrings of the form &something; and not tags.
     * Internet rumour says that Html.fromHtml() doesn't cover all cases, but it doesn't get less
     * vague than that.
     * @param html The HTML escaped text
     * @return The text with its HTML entities unescaped.
     */
    private static String entsToTxt(String html) {
        // entitydefs defines nbsp as \xa0 instead of a standard space, so we
        // replace it first
        html = html.replace("&nbsp;", " ");
        Matcher htmlEntities = htmlEntitiesPattern.matcher(html);
        StringBuffer sb = new StringBuffer();
        while (htmlEntities.find()) {
            htmlEntities.appendReplacement(sb, Html.fromHtml(htmlEntities.group()).toString());
        }
        htmlEntities.appendTail(sb);
        return sb.toString();
    }

    /**
     * IDs
     * ***********************************************************************************************
     */

    /** Given a list of integers, return a string '(int1,int2,...)'. */
    public static String ids2str(int[] ids) {
        StringBuilder sb = new StringBuilder();
        sb.append("(");
        if (ids != null) {
            String s = Arrays.toString(ids);
            sb.append(s.substring(1, s.length() - 1));
        }
        sb.append(")");
        return sb.toString();
    }


    /** Given a list of integers, return a string '(int1,int2,...)'. */
    public static String ids2str(long[] ids) {
        StringBuilder sb = new StringBuilder();
        sb.append("(");
        if (ids != null) {
            String s = Arrays.toString(ids);
            sb.append(s.substring(1, s.length() - 1));
        }
        sb.append(")");
        return sb.toString();
    }

    /** Given a list of integers, return a string '(int1,int2,...)'. */
    public static String ids2str(Long[] ids) {
        StringBuilder sb = new StringBuilder();
        sb.append("(");
        if (ids != null) {
            String s = Arrays.toString(ids);
            sb.append(s.substring(1, s.length() - 1));
        }
        sb.append(")");
        return sb.toString();
    }

    /** Given a list of integers, return a string '(int1,int2,...)'. */
    public static <T> String ids2str(List<T> ids) {
        StringBuilder sb = new StringBuilder(512);
        sb.append("(");
        boolean isNotFirst = false;
        for (T id : ids) {
            if (isNotFirst) {
                sb.append(", ");
            } else {
                isNotFirst = true;
            }
            sb.append(id);
        }
        sb.append(")");
        return sb.toString();
    }


    /** Given a list of integers, return a string '(int1,int2,...)'. */
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
                    Timber.e(e, "ids2str :: JSONException");
                }
            }
        }
        str.append(")");
        return str.toString();
    }


    /** LIBANKI: not in libanki */
    public static long[] arrayList2array(List<Long> list) {
        long[] ar = new long[list.size()];
        int i = 0;
        for (long l : list) {
            ar[i++] = l;
        }
        return ar;
    }

    public static Long[] list2ObjectArray(List<Long> list) {
        return list.toArray(new Long[list.size()]);
    }

    /** Return a non-conflicting timestamp for table. */
    public static long timestampID(DB db, String table) {
        // be careful not to create multiple objects without flushing them, or they
        // may share an ID.
        long t = intNow(1000);
        while (db.queryScalar("SELECT id FROM " + table + " WHERE id = " + t) != 0) {
            t += 1;
        }
        return t;
    }


    /** Return the first safe ID to use. */
    public static long maxID(DB db) {
        long now = intNow(1000);
        now = Math.max(now, db.queryLongScalar("SELECT MAX(id) FROM cards"));
        now = Math.max(now, db.queryLongScalar("SELECT MAX(id) FROM notes"));
        return now + 1;
    }


    // used in ankiweb
    public static String base62(int num, String extra) {
        String table = ALL_CHARACTERS + extra;
        int len = table.length();
        String buf = "";
        int mod = 0;
        while (num != 0) {
            mod = num % len;
            buf = buf + table.substring(mod, mod + 1);
            num = num / len;
        }
        return buf;
    }

    // all printable characters minus quotes, backslash and separators
    public static String base91(int num) {
        return base62(num, BASE91_EXTRA_CHARS);
    }


    /** return a base91-encoded 64bit random number */
    public static String guid64() {
        return base91((new Random()).nextInt((int) (Math.pow(2, 61) - 1)));
    }

    // increment a guid by one, for note type conflicts
    public static String incGuid(String guid) {
        return new StringBuffer(_incGuid(new StringBuffer(guid).reverse().toString())).reverse().toString();
    }

    private static String _incGuid(String guid) {
        String table = ALL_CHARACTERS + BASE91_EXTRA_CHARS;
        int idx = table.indexOf(guid.substring(0, 1));
        if (idx + 1 == table.length()) {
            // overflow
            guid = table.substring(0, 1) + _incGuid(guid.substring(1, guid.length()));
        } else {
            guid = table.substring(idx + 1) + guid.substring(1, guid.length());
        }
        return guid;
    }


    public static long[] jsonArrayToLongArray(JSONArray jsonArray) throws JSONException {
        long[] ar = new long[jsonArray.length()];
        for (int i = 0; i < jsonArray.length(); i++) {
            ar[i] = jsonArray.getLong(i);
        }
        return ar;
    }


    public static Object[] jsonArray2Objects(JSONArray array) {
        Object[] o = new Object[array.length()];
        for (int i = 0; i < array.length(); i++) {
            try {
                o[i] = array.get(i);
            } catch (JSONException e) {
                throw new RuntimeException(e);
            }
        }
        return o;
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
        if (list.length > 0) {
            result.append(list[list.length - 1]);
        }
        return result.toString();
    }


    public static String[] splitFields(String fields) {
        // -1 ensures that we don't drop empty fields at the ends
        return fields.split("\\x1f", -1);
    }

    /**
     * Checksums
     * ***********************************************************************************************
     */

    /**
     * SHA1 checksum.
     * Equivalent to python sha1.hexdigest()
     *
     * @param data the string to generate hash from
     * @return A string of length 40 containing the hexadecimal representation of the MD5 checksum of data.
     */
    public static String checksum(String data) {
        String result = "";
        if (data != null) {
            MessageDigest md = null;
            byte[] digest = null;
            try {
                md = MessageDigest.getInstance("SHA1");
                digest = md.digest(data.getBytes("UTF-8"));
            } catch (NoSuchAlgorithmException e) {
                Timber.e(e, "Utils.checksum: No such algorithm.");
                throw new RuntimeException(e);
            } catch (UnsupportedEncodingException e) {
                Timber.e(e, "Utils.checksum :: UnsupportedEncodingException");
                e.printStackTrace();
            }
            BigInteger biginteger = new BigInteger(1, digest);
            result = biginteger.toString(16);

            // pad with zeros to length of 40 This method used to pad
            // to the length of 32. As it turns out, sha1 has a digest
            // size of 160 bits, leading to a hex digest size of 40,
            // not 32.
            if (result.length() < 40) {
                String zeroes = "0000000000000000000000000000000000000000";
                result = zeroes.substring(0, zeroes.length() - result.length()) + result;
            }
        }
        return result;
    }


    /**
     * @param data the string to generate hash from
     * @return 32 bit unsigned number from first 8 digits of sha1 hash
     */
    public static long fieldChecksum(String data) {
        return Long.valueOf(checksum(stripHTMLMedia(data)).substring(0, 8), 16);
    }

    /**
     * Generate the SHA1 checksum of a file.
     * @param file The file to be checked
     * @return A string of length 32 containing the hexadecimal representation of the SHA1 checksum of the file's contents.
     */
    public static String fileChecksum(String file) {
        byte[] buffer = new byte[1024];
        byte[] digest = null;
        try {
            InputStream fis = new FileInputStream(file);
            MessageDigest md = MessageDigest.getInstance("SHA1");
            int numRead = 0;
            do {
                numRead = fis.read(buffer);
                if (numRead > 0) {
                    md.update(buffer, 0, numRead);
                }
            } while (numRead != -1);
            fis.close();
            digest = md.digest();
        } catch (FileNotFoundException e) {
            Timber.e(e, "Utils.fileChecksum: File not found.");
        } catch (NoSuchAlgorithmException e) {
            Timber.e(e, "Utils.fileChecksum: No such algorithm.");
        } catch (IOException e) {
            Timber.e(e, "Utils.fileChecksum: IO exception.");
        }
        BigInteger biginteger = new BigInteger(1, digest);
        String result = biginteger.toString(16);
        // pad with zeros to length of 40 - SHA1 is 160bit long
        if (result.length() < 40) {
            result = "0000000000000000000000000000000000000000".substring(0, 40 - result.length()) + result;
        }
        return result;
    }


    public static String fileChecksum(File file) {
        return fileChecksum(file.getAbsolutePath());
    }

    /** Replace HTML line break tags with new lines. */
    public static String replaceLineBreak(String text) {
        return text.replaceAll("<br(\\s*\\/*)>", "\n");
    }


    /**
     *  Tempo files
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


    public static void unzipFiles(ZipFile zipFile, String targetDirectory, String[] zipEntries,
                                  Map<String, String> zipEntryToFilenameMap) throws IOException {
        byte[] buf = new byte[FILE_COPY_BUFFER_SIZE];
        File dir = new File(targetDirectory);
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IOException("Failed to create target directory: " + targetDirectory);
        }
        if (zipEntryToFilenameMap == null) {
            zipEntryToFilenameMap = new HashMap<>();
        }
        BufferedInputStream zis = null;
        BufferedOutputStream bos = null;
        try {
            for (String requestedEntry : zipEntries) {
                ZipEntry ze = zipFile.getEntry(requestedEntry);
                if (ze != null) {
                    String name = ze.getName();
                    if (zipEntryToFilenameMap.containsKey(name)) {
                        name = zipEntryToFilenameMap.get(name);
                    }
                    File destFile = new File(dir, name);
                    if (!isInside(destFile, dir)) {
                        Timber.e("Refusing to decompress invalid path: " + destFile.getCanonicalPath());
                        throw new IOException("File is outside extraction target directory.");
                    }

                    if (!ze.isDirectory()) {
                        Timber.i("uncompress %s", name);
                        zis = new BufferedInputStream(zipFile.getInputStream(ze));
                        bos = new BufferedOutputStream(new FileOutputStream(destFile), FILE_COPY_BUFFER_SIZE);
                        int n;
                        while ((n = zis.read(buf, 0, FILE_COPY_BUFFER_SIZE)) != -1) {
                            bos.write(buf, 0, n);
                        }
                        bos.flush();
                        bos.close();
                        zis.close();
                    }
                }
            }
        } finally {
            if (bos != null) {
                bos.close();
            }
            if (zis != null) {
                zis.close();
            }
        }
    }

    /**
     * Checks to see if a given file path resides inside a given directory.
     * Useful for protection against path traversal attacks prior to creating the file
     * @param file the file with an uncertain filesystem location
     * @param dir the directory that should contain the file
     * @return true if the file path is inside the directory
     * @exception IOException if there are security or filesystem issues determining the paths
     */
    public static boolean isInside(@NonNull File file, @NonNull File dir) throws IOException {
        return file.getCanonicalPath().startsWith(dir.getCanonicalPath());
    }

    /**
     * Compress data.
     * @param bytesToCompress is the byte array to compress.
     * @return a compressed byte array.
     * @throws java.io.IOException
     */
    public static byte[] compress(byte[] bytesToCompress, int comp) throws IOException {
        // Compressor with highest level of compression.
        Deflater compressor = new Deflater(comp, true);
        // Give the compressor the data to compress.
        compressor.setInput(bytesToCompress);
        compressor.finish();

        // Create an expandable byte array to hold the compressed data.
        // It is not necessary that the compressed data will be smaller than
        // the uncompressed data.
        ByteArrayOutputStream bos = new ByteArrayOutputStream(bytesToCompress.length);

        // Compress the data
        byte[] buf = new byte[65536];
        while (!compressor.finished()) {
            bos.write(buf, 0, compressor.deflate(buf));
        }

        bos.close();

        // Get the compressed data
        return bos.toByteArray();
    }

    /**
     * Calls {@link #writeToFileImpl(InputStream, String)} and handles IOExceptions
     * @throws IOException Rethrows exception after a set number of retries
     */
    public static void writeToFile(InputStream source, String destination) throws IOException {
        // sometimes this fails and works on retries (hardware issue?)
        final int retries = 5;
        int retryCnt = 0;
        boolean success = false;
        while (!success && retryCnt++ < retries) {
            try {
                writeToFileImpl(source, destination);
                success = true;
            } catch (IOException e) {
                if (retryCnt == retries) {
                    throw e;
                } else {
                    Timber.e("IOException while writing to file, retrying...");
                    try {
                        Thread.sleep(200);
                    } catch (InterruptedException e1) {
                        e1.printStackTrace();
                    }
                }
            }
        }
    }

    /**
     * Utility method to write to a file.
     * Throws the exception, so we can report it in syncing log
     * @throws IOException
     */
    public static void writeToFileImpl(InputStream source, String destination) throws IOException {
        File f = new File(destination);
        try {
            Timber.d("Creating new file... = %s", destination);
            f.createNewFile();

            long startTimeMillis = System.currentTimeMillis();
            OutputStream output = new BufferedOutputStream(new FileOutputStream(destination));

            // Transfer bytes, from source to destination.
            byte[] buf = new byte[CHUNK_SIZE];
            long sizeBytes = 0;
            int len;
            if (source == null) {
                Timber.e("writeToFile :: source is null!");
            }
            while ((len = source.read(buf)) >= 0) {
                output.write(buf, 0, len);
                sizeBytes += len;
            }
            long endTimeMillis = System.currentTimeMillis();

            Timber.d("Finished writeToFile!");
            long durationSeconds = (endTimeMillis - startTimeMillis) / 1000;
            long sizeKb = sizeBytes / 1024;
            long speedKbSec = 0;
            if (endTimeMillis != startTimeMillis) {
                speedKbSec = sizeKb * 1000 / (endTimeMillis - startTimeMillis);
            }
            Timber.d("Utils.writeToFile: Size: %d Kb, Duration: %d s, Speed: %d Kb/s", sizeKb, durationSeconds, speedKbSec);
            output.close();
        } catch (IOException e) {
            throw new IOException(f.getName() + ": " + e.getLocalizedMessage(), e);
        }
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
        cal.setTimeInMillis(System.currentTimeMillis() - (long) utcOffset * 1000L);
        return Date.valueOf(df.format(cal.getTime()));
    }


    public static void printDate(String name, double date) {
        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH-mm-ss");
        df.setTimeZone(TimeZone.getTimeZone("GMT"));
        Calendar cal = new GregorianCalendar(TimeZone.getTimeZone("GMT"));
        cal.setTimeInMillis((long)date * 1000);
        Timber.d("Value of %s: %s", name, cal.getTime().toGMTString());
    }


    // Use DateUtil.formatElapsedTime((long) value) instead of doubleToTime.
    // public static String doubleToTime(double value) { ...}

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

    /**
     * @param mediaDir media directory path on SD card
     * @return path converted to file URL, properly UTF-8 URL encoded
     */
    public static String getBaseUrl(String mediaDir) {
        // Use android.net.Uri class to ensure whole path is properly encoded
        // File.toURL() does not work here, and URLEncoder class is not directly usable
        // with existing slashes
        if (mediaDir.length() != 0 && !mediaDir.equalsIgnoreCase("null")) {
            Uri mediaDirUri = Uri.fromFile(new File(mediaDir));
            return mediaDirUri.toString() +"/";
        }
        return "";
    }


    /**
     * Take an array of Long and return an array of long
     *
     * @param array The input with type Long[]
     * @return The output with type long[]
     */
    public static long[] toPrimitive(Long[] array) {
        if (array == null) {
            return null;
        }
        long[] results = new long[array.length];
        for (int i = 0; i < array.length; i++) {
            results[i] = array[i];
        }
        return results;
    }
    public static long[] toPrimitive(Collection<Long> array) {
        if (array == null) {
            return null;
        }
        long[] results = new long[array.size()];
        int i = 0;
        for (Long item : array) {
            results[i++] = item;
        }
        return results;
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
     * Returns a String array with two elements:
     * 0 - file name
     * 1 - extension
     */
    public static String[] splitFilename(String filename) {
        String name = filename;
        String ext = "";
        int dotPosition = filename.lastIndexOf('.');
        if (dotPosition != -1) {
            name = filename.substring(0, dotPosition);
            ext = filename.substring(dotPosition);
        }
        return new String[] {name, ext};
    }


    /** Returns a list of files for the installed custom fonts. */
    public static List<AnkiFont> getCustomFonts(Context context) {
        String deckPath = CollectionHelper.getCurrentAnkiDroidDirectory(context);
        String fontsPath = deckPath + "/fonts/";
        File fontsDir = new File(fontsPath);
        int fontsCount = 0;
        File[] fontsList = null;
        if (fontsDir.exists() && fontsDir.isDirectory()) {
            fontsCount = fontsDir.listFiles().length;
            fontsList = fontsDir.listFiles();
        }
        String[] ankiDroidFonts = null;
        try {
            ankiDroidFonts = context.getAssets().list("fonts");
        } catch (IOException e) {
            Timber.e(e, "Error on retrieving ankidroid fonts");
        }
        List<AnkiFont> fonts = new ArrayList<>();
        for (int i = 0; i < fontsCount; i++) {
            String filePath = fontsList[i].getAbsolutePath();
            String filePathExtension = splitFilename(filePath)[1];
            for (String fontExtension : FONT_FILE_EXTENSIONS) {
                // Go through the list of allowed extensios.
                if (filePathExtension.equalsIgnoreCase(fontExtension)) {
                    // This looks like a font file.
                    AnkiFont font = AnkiFont.createAnkiFont(context, filePath, false);
                    if (font != null) {
                        fonts.add(font);
                    }
                    break;  // No need to look for other file extensions.
                }
            }
        }
        if (ankiDroidFonts != null) {
            for (String ankiDroidFont : ankiDroidFonts) {
                // Assume all files in the assets directory are actually fonts.
                AnkiFont font = AnkiFont.createAnkiFont(context, ankiDroidFont, true);
                if (font != null) {
                    fonts.add(font);
                }
            }
        }

        return fonts;
    }


    /** Returns a list of apkg-files. */
    public static List<File> getImportableDecks(Context context) {
        String deckPath = CollectionHelper.getCurrentAnkiDroidDirectory(context);
        File dir = new File(deckPath);
        int deckCount = 0;
        File[] deckList = null;
        if (dir.exists() && dir.isDirectory()) {
            deckList = dir.listFiles(new FileFilter(){
                @Override
                public boolean accept(File pathname) {
                    return pathname.isFile() && pathname.getName().endsWith(".apkg");
                }
            });
            deckCount = deckList.length;
        }
        List<File> decks = new ArrayList<>();
        decks.addAll(Arrays.asList(deckList).subList(0, deckCount));
        return decks;
    }


    /**
     * Simply copy a file to another location
     * @param sourceFile The source file
     * @param destFile The destination file, doesn't need to exist yet.
     * @throws IOException
     */
    public static void copyFile(File sourceFile, File destFile) throws IOException {
        if(!destFile.exists()) {
            destFile.createNewFile();
        }

        FileChannel source = null;
        FileChannel destination = null;

        try {
            source = new FileInputStream(sourceFile).getChannel();
            destination = new FileOutputStream(destFile).getChannel();
            destination.transferFrom(source, 0, source.size());
        } finally {
            if (source != null) {
                source.close();
            }
            if (destination != null) {
                destination.close();
            }
        }
    }

    /**
     * Like org.json.JSONObject except that it doesn't escape forward slashes
     * The necessity for this method is due to python's 2.7 json.dumps() function that doesn't escape chracter '/'.
     * The org.json.JSONObject parser accepts both escaped and unescaped forward slashes, so we only need to worry for
     * our output, when we write to the database or syncing.
     *
     * @param json a json object to serialize
     * @return the json serialization of the object
     * @see org.json.JSONObject#toString()
     */
    public static String jsonToString(JSONObject json) {
        return json.toString().replaceAll("\\\\/", "/");
    }

    /**
     * Like org.json.JSONArray except that it doesn't escape forward slashes
     * The necessity for this method is due to python's 2.7 json.dumps() function that doesn't escape chracter '/'.
     * The org.json.JSONArray parser accepts both escaped and unescaped forward slashes, so we only need to worry for
     * our output, when we write to the database or syncing.
     *
     * @param json a json object to serialize
     * @return the json serialization of the object
     * @see org.json.JSONArray#toString()
     */
    public static String jsonToString(JSONArray json) {
        return json.toString().replaceAll("\\\\/", "/");
    }

    /**
     * @return A description of the device, including the model and android version. No commas are present in the
     * returned string.
     */
    public static String platDesc() {
        // AnkiWeb reads this string and uses , and : as delimiters, so we remove them.
        String model = android.os.Build.MODEL.replace(',', ' ').replace(':', ' ');
        return String.format(Locale.US, "android:%s:%s",
                android.os.Build.VERSION.RELEASE, model);
    }


    /*
     *  Return the input string in the Unicode normalized form. This helps with text comparisons, for example a ü
     *  stored as u plus the dots but typed as a single character compare as the same.
     *
     * @param txt Text to be normalized
     * @return The input text in its NFC normalized form form.
    */
    public static String nfcNormalized(String txt) {
        if (!Normalizer.isNormalized(txt, Normalizer.Form.NFC)) {
            return Normalizer.normalize(txt, Normalizer.Form.NFC);
        }
        return txt;
    }


    /**
     * Unescapes all sequences within the given string of text, interpreting them as HTML escaped characters.
     * <p/>
     * Not that this code strips any HTML tags untouched, so if the text contains any HTML tags, they will be ignored.
     *
     * @param htmlText the text to convert
     * @return the unescaped text
     */
    public static String unescape(String htmlText) {
        return Html.fromHtml(htmlText).toString();
    }


    /**
     * Return a random float within the range of min and max.
     */
    public static float randomFloatInRange(float min, float max) {
        Random rand = new Random();
        return rand.nextFloat() * (max - min) + min;
    }
}
