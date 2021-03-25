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

import android.annotation.SuppressLint;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.net.Uri;
import android.text.Spanned;

import androidx.annotation.NonNull;
import android.os.StatFs;
import android.text.TextUtils;
import android.util.Pair;

import com.ichi2.anki.AnkiFont;
import com.ichi2.anki.CollectionHelper;
import com.ichi2.anki.R;
import com.ichi2.compat.CompatHelper;
import com.ichi2.utils.HashUtil;
import com.ichi2.utils.ImportUtils;

import com.ichi2.utils.JSONArray;
import com.ichi2.utils.JSONException;
import com.ichi2.utils.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;

import androidx.annotation.Nullable;
import androidx.core.text.HtmlCompat;
import timber.log.Timber;

import static com.ichi2.libanki.Consts.FIELD_SEPARATOR;

@SuppressWarnings({"PMD.AvoidThrowingRawExceptionTypes","PMD.AvoidReassigningParameters",
        "PMD.MethodNamingConventions","PMD.FieldDeclarationsShouldBeAtStartOfClass"})
public class Utils {
    // Used to format doubles with English's decimal separator system
    public static final Locale ENGLISH_LOCALE = new Locale("en_US");

    public static final int CHUNK_SIZE = 32768;

    private static final long TIME_MINUTE_LONG = 60;  // seconds
    private static final long TIME_HOUR_LONG = 60 * TIME_MINUTE_LONG;
    private static final long TIME_DAY_LONG = 24 * TIME_HOUR_LONG;
    // These are doubles on purpose because we want a rounded, not integer result later.
    // Use values from Anki Desktop:
    // https://github.com/ankitects/anki/blob/05cc47a5d3d48851267cda47f62af79f468eb028/rslib/src/sched/timespan.rs#L83
    private static final double TIME_MINUTE = 60.0;  // seconds
    private static final double TIME_HOUR = 60.0 * TIME_MINUTE;
    private static final double TIME_DAY = 24.0 * TIME_HOUR;
    private static final double TIME_MONTH = 30.0 * TIME_DAY;
    private static final double TIME_YEAR = 12.0 * TIME_MONTH;


    // List of all extensions we accept as font files.
    private static final String[] FONT_FILE_EXTENSIONS = new String[] {".ttf",".ttc",".otf"};

    /* Prevent class from being instantiated */
    private Utils() { }

    // Regex pattern used in removing tags from text before diff
    private static final Pattern commentPattern = Pattern.compile("(?s)<!--.*?-->");
    private static final Pattern stylePattern = Pattern.compile("(?si)<style.*?>.*?</style>");
    private static final Pattern scriptPattern = Pattern.compile("(?si)<script.*?>.*?</script>");
    private static final Pattern tagPattern = Pattern.compile("(?s)<.*?>");
    private static final Pattern imgPattern = Pattern.compile("(?i)<img[^>]+src=[\"']?([^\"'>]+)[\"']?[^>]*>");
    private static final Pattern soundPattern = Pattern.compile("(?i)\\[sound:([^]]+)]");
    private static final Pattern htmlEntitiesPattern = Pattern.compile("&#?\\w+;");

    private static final String ALL_CHARACTERS = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    private static final String BASE91_EXTRA_CHARS = "!#$%&()*+,-./:;<=>?@[]^_`{|}~";

    private static final int FILE_COPY_BUFFER_SIZE = 1024 * 32;

    /**
     * Return a string representing a time quantity
     *
     * Equivalent to Anki's anki/utils.py's shortTimeFmt, applied to a number.
     * I.e. equivalent to Anki's anki/utils.py's fmtTimeSpan, with the parameter short=True.
     *
     * @param context The application's environment.
     * @param time_s The time to format, in seconds
     * @return The time quantity string. Something like "3 s" or "1.7
     * yr". Only months and year have a number after the decimal.
     */
    public static String timeQuantityTopDeckPicker(Context context, long time_s) {
        Resources res = context.getResources();
        // N.B.: the integer s, min, h, d and (one decimal, rounded by format) double for month, year is
        // hard-coded. See also 01-core.xml
        if (Math.abs(time_s) < TIME_MINUTE ) {
            return res.getString(R.string.time_quantity_seconds, time_s);
        } else if (Math.abs(time_s) < TIME_HOUR) {
            return res.getString(R.string.time_quantity_minutes, (int) Math.round(time_s/TIME_MINUTE));
        } else if (Math.abs(time_s) < TIME_DAY) {
            return res.getString(R.string.time_quantity_hours_minutes, (int) Math.floor(time_s/TIME_HOUR), (int) Math.round((time_s % TIME_HOUR) / TIME_MINUTE));
        } else if (Math.abs(time_s) < TIME_MONTH) {
            return res.getString(R.string.time_quantity_days_hours, (int) Math.floor(time_s/TIME_DAY), (int) Math.round((time_s % TIME_DAY) / TIME_HOUR));
        } else if (Math.abs(time_s) < TIME_YEAR) {
            return res.getString(R.string.time_quantity_months, time_s/TIME_MONTH);
        } else {
            return res.getString(R.string.time_quantity_years, time_s/TIME_YEAR);
        }
    }


    /**
     * Return a string representing a time quantity
     *
     * Equivalent to Anki's anki/utils.py's shortTimeFmt, applied to a number.
     * I.e. equivalent to Anki's anki/utils.py's fmtTimeSpan, with the parameter short=True.
     *
     * @param context The application's environment.
     * @param time_s The time to format, in seconds
     * @return The time quantity string. Something like "3 s" or "1.7
     * yr". Only months and year have a number after the decimal.
     */
    public static String timeQuantityNextIvl(Context context, long time_s) {
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
     * Return a string representing how much time remains
     *
     * @param context The application's environment.
     * @param time_s The time to format, in seconds
     * @return The time quantity string. Something like "3 minutes left" or "2 hours left".
     */
    public static String remainingTime(Context context, long time_s) {
        int time_x;  // Time in unit x
        int remaining_seconds; // Time not counted in the number in unit x
        int remaining; // Time in the unit smaller than x
        Resources res = context.getResources();
        if (time_s < TIME_HOUR_LONG) {
            // get time remaining, but never less than 1
            time_x = Math.max((int) Math.round(time_s / TIME_MINUTE), 1);
            return res.getQuantityString(R.plurals.reviewer_window_title, time_x, time_x);
            //It used to be minutes only. So the word "minutes" is not
            //explicitly written in the ressource name.
        } else if (time_s < TIME_DAY_LONG) {
            time_x = (int) (time_s / TIME_HOUR_LONG);
            remaining_seconds = (int) (time_s % TIME_HOUR_LONG);
            remaining = (int) Math.round((float) remaining_seconds / TIME_MINUTE);
            return res.getQuantityString(R.plurals.reviewer_window_title_hours, time_x, time_x, remaining);

        } else {
            time_x = (int) (time_s / TIME_DAY_LONG);
            remaining_seconds = (int) ((float) time_s % TIME_DAY_LONG);
            remaining = (int) Math.round(remaining_seconds / TIME_HOUR);
            return res.getQuantityString(R.plurals.reviewer_window_title_days, time_x, time_x, remaining);
        }
    }

    /**
     * Return a string representing a time
     * (If you want a certain unit, use the strings directly)
     *
     * @param context The application's environment.
     * @param time_s The time to format, in seconds
     * @return The formatted, localized time string. The time is always an integer.
     *  e.g. something like "3 seconds" or "1 year".
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
     * Similar to Anki anki/utils.py's fmtTimeSpan.
     *
     * @param context The application's environment.
     * @param time_s The time to format, in seconds
     * @return The formatted, localized time string. The time is always a float. E.g. "27.0 days"
     */
    public static String roundedTimeSpanUnformatted(Context context, long time_s) {
        // As roundedTimeSpan, but without tags; for place where you don't use HTML
        return roundedTimeSpan(context, time_s).replace("<b>", "").replace("</b>", "");
    }

    /**
     * Return a proper string for a time value in seconds
     *
     * Similar to Anki anki/utils.py's fmtTimeSpan.
     *
     * @param context The application's environment.
     * @param time_s The time to format, in seconds
     * @return The formatted, localized time string. The time is always a float. E.g. "<b>27.0</b> days"
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

    /*
     * Locale
     * ***********************************************************************************************
     */


    /*
    * HTML
     * ***********************************************************************************************
     */

    /**
     * Strips a text from <style>...</style>, <script>...</script> and <_any_tag_> HTML tags.
     * @param s The HTML text to be cleaned.
     * @return The text without the aforementioned tags.
     */
    public static String stripHTML(String s) {
        s = commentPattern.matcher(s).replaceAll("");
        s = stripHTMLScriptAndStyleTags(s);
        Matcher htmlMatcher = tagPattern.matcher(s);
        s = htmlMatcher.replaceAll("");
        return entsToTxt(s);
    }

    /**
     * Strips <style>...</style> and <script>...</script> HTML tags and content from a string.
     * @param s The HTML text to be cleaned.
     * @return The text without the aforementioned tags.
     */
    public static String stripHTMLScriptAndStyleTags(String s) {
        Matcher htmlMatcher = stylePattern.matcher(s);
        s = htmlMatcher.replaceAll("");
        htmlMatcher = scriptPattern.matcher(s);
        return htmlMatcher.replaceAll("");
    }


    /**
     * Strip HTML but keep media filenames
     */
    public static String stripHTMLMedia(@NonNull String s) {
        return stripHTMLMedia(s, " $1 ");
    }


    public static String stripHTMLMedia(@NonNull String s, String replacement) {
        Matcher imgMatcher = imgPattern.matcher(s);
        return stripHTML(imgMatcher.replaceAll(replacement));
    }


    /**
     * Strip sound but keep media filenames
     */
    public static String stripSoundMedia(String s) {
        return stripSoundMedia(s, " $1 ");
    }


    public static String stripSoundMedia(String s, String replacement) {
        Matcher soundMatcher = soundPattern.matcher(s);
        return soundMatcher.replaceAll(replacement);
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
            final Spanned spanned = HtmlCompat.fromHtml(htmlEntities.group(), HtmlCompat.FROM_HTML_MODE_LEGACY);
            final String replacement = Matcher.quoteReplacement(spanned.toString());
            htmlEntities.appendReplacement(sb, replacement);
        }
        htmlEntities.appendTail(sb);
        return sb.toString();
    }

    /*
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

    /** Given a list of integers, return a string '(int1,int2,...)', in order given by the iterator. */
    public static <T> String ids2str(Iterable<T> ids) {
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
                        str.append(ids.getLong(i));
                    } else {
                        str.append(ids.getLong(i)).append(",");
                    }
                } catch (JSONException e) {
                    Timber.e(e, "ids2str :: JSONException");
                }
            }
        }
        str.append(")");
        return str.toString();
    }


    /** LIBANKI: not in libanki
     *  Transform a collection of Long into an array of Long */
    public static long[] collection2Array(java.util.Collection<Long> list) {
        long[] ar = new long[list.size()];
        int i = 0;
        for (long l : list) {
            ar[i++] = l;
        }
        return ar;
    }

    public static Long[] list2ObjectArray(List<Long> list) {
        return list.toArray(new Long[0]);
    }


    // used in ankiweb
    private static String base62(int num, String extra) {
        String table = ALL_CHARACTERS + extra;
        int len = table.length();
        String buf = "";
        int mod;
        while (num != 0) {
            mod = num % len;
            buf = buf + table.substring(mod, mod + 1);
            num = num / len;
        }
        return buf;
    }

    // all printable characters minus quotes, backslash and separators
    private static String base91(int num) {
        return base62(num, BASE91_EXTRA_CHARS);
    }


    /** return a base91-encoded 64bit random number */
    public static String guid64() {
        return base91((new Random()).nextInt((int) (Math.pow(2, 61) - 1)));
    }

    // increment a guid by one, for note type conflicts
    @SuppressWarnings({"unused"}) //used in Anki
    public static String incGuid(String guid) {
        return new StringBuffer(_incGuid(new StringBuffer(guid).reverse().toString())).reverse().toString();
    }

    private static String _incGuid(String guid) {
        String table = ALL_CHARACTERS + BASE91_EXTRA_CHARS;
        int idx = table.indexOf(guid.substring(0, 1));
        if (idx + 1 == table.length()) {
            // overflow
            guid = table.substring(0, 1) + _incGuid(guid.substring(1));
        } else {
            guid = table.substring(idx + 1) + guid.substring(1);
        }
        return guid;
    }


    public static Object[] jsonArray2Objects(JSONArray array) {
        Object[] o = new Object[array.length()];
        for (int i = 0; i < array.length(); i++) {
            o[i] = array.get(i);
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
        return fields.split(FIELD_SEPARATOR, -1);
    }

    /*
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
    @SuppressWarnings("CharsetObjectCanBeUsed")
    @NonNull
    public static String checksum(String data) {
        String result = "";
        if (data != null) {
            MessageDigest md;
            byte[] digest = null;
            try {
                md = MessageDigest.getInstance("SHA1");
                digest = md.digest(data.getBytes("UTF-8"));
            } catch (NoSuchAlgorithmException e) {
                Timber.e(e, "Utils.checksum: No such algorithm.");
                throw new RuntimeException(e);
            } catch (UnsupportedEncodingException e) {
                Timber.e(e, "Utils.checksum :: UnsupportedEncodingException");
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
     * Optimized in case of sortIdx = 0
     * @param fields Fields of a note
     * @param sortIdx An index of the field
     * @return The field at sortIdx, without html media, and the csum of the first field.
     */
    public static Pair<String, Long> sfieldAndCsum(String[] fields, int sortIdx) {
        String firstStripped = stripHTMLMedia(fields[0]);
        String sortStripped = (sortIdx == 0) ?  firstStripped: stripHTMLMedia(fields[sortIdx]);
        return new Pair<>(sortStripped, fieldChecksumWithoutHtmlMedia(firstStripped));
    }

    /**
     * @param data the string to generate hash from.
     * @return 32 bit unsigned number from first 8 digits of sha1 hash
     */
    public static long fieldChecksum(String data) {
        return fieldChecksumWithoutHtmlMedia(stripHTMLMedia(data));
    }

    /**
     * @param data the string to generate hash from. Html media should be removed
     * @return 32 bit unsigned number from first 8 digits of sha1 hash
     */
    public static long fieldChecksumWithoutHtmlMedia(String data) {
        return Long.valueOf(checksum(data).substring(0, 8), 16);
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


    /*
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
            Timber.w(e);
        }

        return contentOfMyInputStream;
    }

    public static void unzipAllFiles(ZipFile zipFile, String targetDirectory) throws IOException {
        List<String> entryNames = new ArrayList<>();
        Enumeration<ZipArchiveEntry> i = zipFile.getEntries();
        while (i.hasMoreElements()) {
            ZipArchiveEntry e = i.nextElement();
            entryNames.add(e.getName());
        }

        unzipFiles(zipFile, targetDirectory, entryNames.toArray(new String[0]), null);

    }


    /**
     * @param zipFile A zip file
     * @param targetDirectory Directory in which to unzip some of the zipped field
     * @param zipEntries files of the zip folder to unzip
     * @param zipEntryToFilenameMap Renaming rules from name in zip file to name in the device
     * @throws IOException if the directory can't be created
     */
    public static void unzipFiles(ZipFile zipFile, String targetDirectory, @NonNull String[] zipEntries,
                                  @Nullable Map<String, String> zipEntryToFilenameMap) throws IOException {
        File dir = new File(targetDirectory);
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IOException("Failed to create target directory: " + targetDirectory);
        }
        if (zipEntryToFilenameMap == null) {
            zipEntryToFilenameMap = HashUtil.HashMapInit(0);
        }
        for (String requestedEntry : zipEntries) {
            ZipArchiveEntry ze = zipFile.getEntry(requestedEntry);
            if (ze != null) {
                String name = ze.getName();
                if (zipEntryToFilenameMap.containsKey(name)) {
                    name = zipEntryToFilenameMap.get(name);
                }
                File destFile = new File(dir, name);
                if (!isInside(destFile, dir)) {
                    Timber.e("Refusing to decompress invalid path: %s", destFile.getCanonicalPath());
                    throw new IOException("File is outside extraction target directory.");
                }

                if (!ze.isDirectory()) {
                    Timber.i("uncompress %s", name);
                    try (InputStream zis = zipFile.getInputStream(ze)) {
                        writeToFile(zis, destFile.getAbsolutePath());
                    }
                }
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
     * Given a ZipFile, iterate through the ZipEntries to determine the total uncompressed size
     * TODO warning: vulnerable to resource exhaustion attack if entries contain spoofed sizes
     *
     * @param zipFile ZipFile of unknown total uncompressed size
     * @return total uncompressed size of zipFile
     */
    public static long calculateUncompressedSize(ZipFile zipFile) {

        long totalUncompressedSize = 0;
        Enumeration<ZipArchiveEntry> e = zipFile.getEntries();
        while (e.hasMoreElements()) {
            ZipArchiveEntry ze = e.nextElement();
            totalUncompressedSize += ze.getSize();
        }

        return totalUncompressedSize;
    }


    /**
     * Determine available storage space
     *
     * @param path the filesystem path you need free space information on
     * @return long indicating the bytes available for that path
     */
    public static long determineBytesAvailable(String path) {
        return new StatFs(path).getAvailableBytes();
    }


    /**
     * Calls {@link #writeToFileImpl(InputStream, String)} and handles IOExceptions
     * Does not close the provided stream
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
                    Timber.e("IOException while writing to file, out of retries.");
                    throw e;
                } else {
                    Timber.e("IOException while writing to file, retrying...");
                    try {
                        Thread.sleep(200);
                    } catch (InterruptedException e1) {
                        Timber.w(e1);
                    }
                }
            }
        }
    }

    /**
     * Utility method to write to a file.
     * Throws the exception, so we can report it in syncing log
     */
    private static void writeToFileImpl(InputStream source, String destination) throws IOException {
        File f = new File(destination);
        try {
            Timber.d("Creating new file... = %s", destination);
            f.createNewFile();

            @SuppressLint("DirectSystemCurrentTimeMillisUsage")
            long startTimeMillis = System.currentTimeMillis();
            long sizeBytes = CompatHelper.getCompat().copyFile(source, destination);
            @SuppressLint("DirectSystemCurrentTimeMillisUsage")
            long endTimeMillis = System.currentTimeMillis();

            Timber.d("Finished writeToFile!");
            long durationSeconds = (endTimeMillis - startTimeMillis) / 1000;
            long sizeKb = sizeBytes / 1024;
            long speedKbSec = 0;
            if (endTimeMillis != startTimeMillis) {
                speedKbSec = sizeKb * 1000 / (endTimeMillis - startTimeMillis);
            }
            Timber.d("Utils.writeToFile: Size: %d Kb, Duration: %d s, Speed: %d Kb/s", sizeKb, durationSeconds, speedKbSec);
        } catch (IOException e) {
            throw new IOException(f.getName() + ": " + e.getLocalizedMessage(), e);
        }
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
        return !list.isEmpty();
    }

    /**
     * @param mediaDir media directory path on SD card
     * @return path converted to file URL, properly UTF-8 URL encoded
     */
    public static String getBaseUrl(String mediaDir) {
        // Use android.net.Uri class to ensure whole path is properly encoded
        // File.toURL() does not work here, and URLEncoder class is not directly usable
        // with existing slashes
        if (mediaDir.length() != 0 && !"null".equalsIgnoreCase(mediaDir)) {
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
        List<AnkiFont> fonts = new ArrayList<>(fontsCount);
        for (int i = 0; i < fontsCount; i++) {
            String filePath = fontsList[i].getAbsolutePath();
            String filePathExtension = splitFilename(filePath)[1];
            for (String fontExtension : FONT_FILE_EXTENSIONS) {
                // Go through the list of allowed extensions.
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
        List<File> decks = new ArrayList<>();
        if (dir.exists() && dir.isDirectory()) {
            File[] deckList = dir.listFiles(pathname -> pathname.isFile() && ImportUtils.isValidPackageName(pathname.getName()));
            decks.addAll(Arrays.asList(deckList).subList(0, deckList.length));
        }
        return decks;
    }


    /**
     * Simply copy a file to another location
     * @param sourceFile The source file
     * @param destFile The destination file, doesn't need to exist yet.
     */
    public static void copyFile(File sourceFile, File destFile) throws IOException {
        try (FileInputStream source = new FileInputStream(sourceFile)) {
            writeToFile(source, destFile.getAbsolutePath());
        }
    }

    /**
     * Like org.json.JSONObject except that it doesn't escape forward slashes
     * The necessity for this method is due to python's 2.7 json.dumps() function that doesn't escape character '/'.
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
     * The necessity for this method is due to python's 2.7 json.dumps() function that doesn't escape character '/'.
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
        return HtmlCompat.fromHtml(htmlText, HtmlCompat.FROM_HTML_MODE_LEGACY).toString();
    }


    /**
     * Return a random float within the range of min and max.
     */
    public static float randomFloatInRange(float min, float max) {
        Random rand = new Random();
        return rand.nextFloat() * (max - min) + min;
    }

    /**
       Set usn to 0 in every object.

       This method is called during full sync, before uploading, so
       during an instant, the value will be zero while the object is
       not actually online. This is not a problem because if the sync
       fails, a full sync will occur again next time.

       @return whether there was a non-zero usn; in this case the list
       should be saved before the upload.
     */
    public static boolean markAsUploaded(List<? extends JSONObject> ar) {
        boolean changed = false;
        for (JSONObject obj: ar) {
            if (obj.optInt("usn", 1) != 0) {
                obj.put("usn", 0);
                changed = true;
            }
        }
        return changed;
    }


    /**
     * @param left An object of type T
     * @param right An object of type T
     * @param <T> A type on which equals can be called
     * @return Whether both objects are equal.
     */
    // Similar as Objects.equals. So deprecated starting at API Level 19 where this methods exists.
    public static <T> boolean equals(@Nullable T left, @Nullable T right) {
        //noinspection EqualsReplaceableByObjectsCall
        return left == right || (left != null && left.equals(right));
    }

    /**
     * @param sflds Some fields
     * @return Array with the same elements, trimmed
     */
    public static @NonNull String[] trimArray(@NonNull String[] sflds) {
        int nbField = sflds.length;
        String[] fields = new String[nbField];
        for (int i = 0; i < nbField; i++) {
            fields[i] = sflds[i].trim();
        }
        return fields;
    }


    /**
     * @param fields A map from field name to field value
     * @return The set of non empty field values.
     */
    public static Set<String> nonEmptyFields(Map<String, String> fields) {
        Set<String> nonempty_fields = HashUtil.HashSetInit(fields.size());
        for (Map.Entry<String, String> kv: fields.entrySet()) {
            String value = kv.getValue();
            value = Utils.stripHTMLMedia(value).trim();
            if (!TextUtils.isEmpty(value)) {
                nonempty_fields.add(kv.getKey());
            }
        }
        return nonempty_fields;
    }
}
