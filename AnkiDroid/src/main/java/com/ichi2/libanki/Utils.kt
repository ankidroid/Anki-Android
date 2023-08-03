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
package com.ichi2.libanki

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.StatFs
import androidx.core.text.HtmlCompat
import com.ichi2.anki.AnkiFont
import com.ichi2.anki.AnkiFont.Companion.createAnkiFont
import com.ichi2.anki.BuildConfig
import com.ichi2.anki.CollectionHelper
import com.ichi2.anki.R
import com.ichi2.compat.CompatHelper.Companion.compat
import com.ichi2.libanki.Consts.FIELD_SEPARATOR
import com.ichi2.utils.HashUtil.HashSetInit
import com.ichi2.utils.KotlinCleanup
import timber.log.Timber
import java.io.*
import java.math.BigInteger
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.util.*
import java.util.regex.Matcher
import java.util.regex.Pattern
import kotlin.collections.Collection
import kotlin.math.*

@KotlinCleanup("IDE Lint")
@KotlinCleanup("timeQuantity methods: single source line per return")
@KotlinCleanup("see if we can switch to standalone functions and properties and remove Utils container")
object Utils {
    // Used to format doubles with English's decimal separator system
    val ENGLISH_LOCALE = Locale("en_US")
    private const val TIME_MINUTE_LONG: Long = 60 // seconds
    private const val TIME_HOUR_LONG = 60 * TIME_MINUTE_LONG
    private const val TIME_DAY_LONG = 24 * TIME_HOUR_LONG

    // These are doubles on purpose because we want a rounded, not integer result later.
    // Use values from Anki Desktop:
    // https://github.com/ankitects/anki/blob/05cc47a5d3d48851267cda47f62af79f468eb028/rslib/src/sched/timespan.rs#L83
    private const val TIME_MINUTE = 60.0 // seconds
    private const val TIME_HOUR = 60.0 * TIME_MINUTE
    private const val TIME_DAY = 24.0 * TIME_HOUR
    private const val TIME_MONTH = 30.0 * TIME_DAY
    private const val TIME_YEAR = 12.0 * TIME_MONTH

    // List of all extensions we accept as font files.
    private val FONT_FILE_EXTENSIONS = arrayOf(".ttf", ".ttc", ".otf")

    // Regex pattern used in removing tags from text before diff
    private val commentPattern = Pattern.compile("(?s)<!--.*?-->")
    private val stylePattern = Pattern.compile("(?si)<style.*?>.*?</style>")
    private val scriptPattern = Pattern.compile("(?si)<script.*?>.*?</script>")
    private val tagPattern = Pattern.compile("(?s)<.*?>")
    private val imgPattern = Pattern.compile("(?i)<img[^>]+src=[\"']?([^\"'>]+)[\"']?[^>]*>")
    private val soundPattern = Pattern.compile("(?i)\\[sound:([^]]+)]")
    private val htmlEntitiesPattern = Pattern.compile("&#?\\w+;")
    private const val ALL_CHARACTERS =
        "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz"
    private const val BASE91_EXTRA_CHARS = "!#$%&()*+,-./:;<=>?@[]^_`{|}~"

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
    fun timeQuantityTopDeckPicker(context: Context, time_s: Long): String {
        val res = context.resources
        // N.B.: the integer s, min, h, d and (one decimal, rounded by format) double for month, year is
        // hard-coded. See also 01-core.xml
        return if (abs(time_s) < TIME_MINUTE) {
            res.getString(R.string.time_quantity_seconds, time_s)
        } else if (abs(time_s) < TIME_HOUR) {
            res.getString(
                R.string.time_quantity_minutes,
                (time_s / TIME_MINUTE).roundToInt()
            )
        } else if (abs(time_s) < TIME_DAY) {
            res.getString(
                R.string.time_quantity_hours_minutes,
                floor(time_s / TIME_HOUR).toInt(),
                (time_s % TIME_HOUR / TIME_MINUTE).roundToInt()
            )
        } else if (abs(time_s) < TIME_MONTH) {
            res.getString(
                R.string.time_quantity_days_hours,
                floor(time_s / TIME_DAY).toInt(),
                (time_s % TIME_DAY / TIME_HOUR).roundToInt()
            )
        } else if (abs(time_s) < TIME_YEAR) {
            res.getString(R.string.time_quantity_months, time_s / TIME_MONTH)
        } else {
            res.getString(R.string.time_quantity_years, time_s / TIME_YEAR)
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
    fun timeQuantityNextIvl(context: Context, time_s: Long): String {
        val res = context.resources
        // N.B.: the integer s, min, h, d and (one decimal, rounded by format) double for month, year is
        // hard-coded. See also 01-core.xml
        return if (abs(time_s) < TIME_MINUTE) {
            res.getString(R.string.time_quantity_seconds, time_s)
        } else if (abs(time_s) < TIME_HOUR) {
            res.getString(
                R.string.time_quantity_minutes,
                (time_s / TIME_MINUTE).roundToInt()
            )
        } else if (abs(time_s) < TIME_DAY) {
            res.getString(
                R.string.time_quantity_hours,
                (time_s / TIME_HOUR).roundToInt()
            )
        } else if (abs(time_s) < TIME_MONTH) {
            res.getString(
                R.string.time_quantity_days,
                (time_s / TIME_DAY).roundToInt()
            )
        } else if (abs(time_s) < TIME_YEAR) {
            res.getString(R.string.time_quantity_months, time_s / TIME_MONTH)
        } else {
            res.getString(R.string.time_quantity_years, time_s / TIME_YEAR)
        }
    }

    /**
     * Return a string representing how much time remains
     *
     * @param context The application's environment.
     * @param time_s The time to format, in seconds
     * @return The time quantity string. Something like "3 minutes left" or "2 hours left".
     */
    fun remainingTime(context: Context, time_s: Long): String {
        val time_x: Int // Time in unit x
        val remaining_seconds: Int // Time not counted in the number in unit x
        val remaining: Int // Time in the unit smaller than x
        val res = context.resources
        return if (time_s < TIME_HOUR_LONG) {
            // get time remaining, but never less than 1
            time_x = max(
                (time_s / TIME_MINUTE).roundToInt(),
                1
            )
            res.getQuantityString(R.plurals.reviewer_window_title, time_x, time_x)
            // It used to be minutes only. So the word "minutes" is not
            // explicitly written in the ressource name.
        } else if (time_s < TIME_DAY_LONG) {
            time_x = (time_s / TIME_HOUR_LONG).toInt()
            remaining_seconds = (time_s % TIME_HOUR_LONG).toInt()
            remaining =
                (remaining_seconds.toFloat() / TIME_MINUTE).roundToInt()
            res.getQuantityString(
                R.plurals.reviewer_window_title_hours_new,
                time_x,
                time_x,
                remaining
            )
        } else {
            time_x = (time_s / TIME_DAY_LONG).toInt()
            remaining_seconds = (time_s.toFloat() % TIME_DAY_LONG).toInt()
            remaining =
                (remaining_seconds / TIME_HOUR).roundToInt()
            res.getQuantityString(
                R.plurals.reviewer_window_title_days_new,
                time_x,
                time_x,
                remaining
            )
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
    fun roundedTimeSpanUnformatted(context: Context, time_s: Long): String {
        // As roundedTimeSpan, but without tags; for place where you don't use HTML
        return roundedTimeSpan(context, time_s).replace("<b>", "").replace("</b>", "")
    }

    /**
     * Return a proper string for a time value in seconds
     *
     * Similar to Anki anki/utils.py's fmtTimeSpan.
     *
     * @param context The application's environment.
     * @param time_s The time to format, in seconds
     * @return The formatted, localized time string. The time is always a float. E.g. "**27.0** days"
     */
    fun roundedTimeSpan(context: Context, time_s: Long): String {
        return if (abs(time_s) < TIME_DAY) {
            context.resources.getString(
                R.string.stats_overview_hours,
                time_s / TIME_HOUR
            )
        } else if (abs(time_s) < TIME_MONTH) {
            context.resources.getString(
                R.string.stats_overview_days,
                time_s / TIME_DAY
            )
        } else if (abs(time_s) < TIME_YEAR) {
            context.resources.getString(
                R.string.stats_overview_months,
                time_s / TIME_MONTH
            )
        } else {
            context.resources.getString(
                R.string.stats_overview_years,
                time_s / TIME_YEAR
            )
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
     * @param inputParam The HTML text to be cleaned.
     * @return The text without the aforementioned tags.
     </_any_tag_> */
    @KotlinCleanup("see if function body could be improved")
    fun stripHTML(inputParam: String): String {
        var s = inputParam
        s = commentPattern.matcher(s).replaceAll("")
        s = stripHTMLScriptAndStyleTags(s)
        val htmlMatcher = tagPattern.matcher(s)
        s = htmlMatcher.replaceAll("")
        return entsToTxt(s)
    }

    /**
     * Strips <style>...</style> and <script>...</script> HTML tags and content from a string.
     * @param inputParam The HTML text to be cleaned.
     * @return The text without the aforementioned tags.
     */
    @KotlinCleanup("see if function body could be improved")
    fun stripHTMLScriptAndStyleTags(inputParam: String): String {
        var s = inputParam
        var htmlMatcher = stylePattern.matcher(s)
        s = htmlMatcher.replaceAll("")
        htmlMatcher = scriptPattern.matcher(s)
        return htmlMatcher.replaceAll("")
    }

    /**
     * Strip HTML but keep media filenames
     */
    fun stripHTMLMedia(s: String, replacement: String = " $1 "): String {
        val imgMatcher = imgPattern.matcher(s)
        return stripHTML(imgMatcher.replaceAll(replacement))
    }

    /**
     * Strip sound but keep media filenames
     */
    fun stripSoundMedia(s: String, replacement: String = " $1 "): String {
        val soundMatcher = soundPattern.matcher(s)
        return soundMatcher.replaceAll(replacement)
    }

    /**
     * Takes a string and replaces all the HTML symbols in it with their unescaped representation.
     * This should only affect substrings of the form `&something;` and not tags.
     * Internet rumour says that Html.fromHtml() doesn't cover all cases, but it doesn't get less
     * vague than that.
     * @param htmlInput The HTML escaped text
     * @return The text with its HTML entities unescaped.
     */
    @KotlinCleanup("see if we can improve var html")
    private fun entsToTxt(htmlInput: String): String {
        // entitydefs defines nbsp as \xa0 instead of a standard space, so we
        // replace it first
        var html = htmlInput
        html = html.replace("&nbsp;", " ")
        val htmlEntities = htmlEntitiesPattern.matcher(html)
        val sb = StringBuffer()
        while (htmlEntities.find()) {
            val spanned =
                HtmlCompat.fromHtml(htmlEntities.group(), HtmlCompat.FROM_HTML_MODE_LEGACY)
            val replacement = Matcher.quoteReplacement(spanned.toString())
            htmlEntities.appendReplacement(sb, replacement)
        }
        htmlEntities.appendTail(sb)
        return sb.toString()
    }

    /*
     * IDs
     * ***********************************************************************************************
     */
    /** Given a list of integers, return a string '(int1,int2,...)'.  */
    @KotlinCleanup("Use scope function on StringBuilder")
    fun ids2str(ids: IntArray?): String {
        val sb = StringBuilder()
        sb.append("(")
        if (ids != null) {
            val s = Arrays.toString(ids)
            sb.append(s.substring(1, s.length - 1))
        }
        sb.append(")")
        return sb.toString()
    }

    /** Given a list of integers, return a string '(int1,int2,...)'.  */
    @KotlinCleanup("Use scope function on StringBuilder")
    fun ids2str(ids: LongArray?): String {
        val sb = StringBuilder()
        sb.append("(")
        if (ids != null) {
            val s = Arrays.toString(ids)
            sb.append(s.substring(1, s.length - 1))
        }
        sb.append(")")
        return sb.toString()
    }

    /** Given a list of integers, return a string '(int1,int2,...)', in order given by the iterator.  */
    @KotlinCleanup("Use scope function on StringBuilder, simplify inner for loop")
    fun <T> ids2str(ids: Iterable<T>): String {
        val sb = StringBuilder(512)
        sb.append("(")
        var isNotFirst = false
        for (id in ids) {
            if (isNotFirst) {
                sb.append(", ")
            } else {
                isNotFirst = true
            }
            sb.append(id)
        }
        sb.append(")")
        return sb.toString()
    }

    // used in ankiweb
    private fun base62(numParam: Int, extra: String): String {
        var num = numParam
        val table = ALL_CHARACTERS + extra
        val len = table.length
        var buf = ""
        var mod: Int
        while (num != 0) {
            mod = num % len
            buf += table.substring(mod, mod + 1)
            num /= len
        }
        return buf
    }

    // all printable characters minus quotes, backslash and separators
    private fun base91(num: Int): String {
        return base62(num, BASE91_EXTRA_CHARS)
    }

    /** return a base91-encoded 64bit random number  */
    fun guid64(): String {
        return base91(
            Random().nextInt((2.0.pow(61.0) - 1).toInt())
        )
    }

    /**
     * Fields
     * ***********************************************************************************************
     */
    fun joinFields(list: Array<String>): String {
        val result = StringBuilder(128)
        for (i in 0 until list.size - 1) {
            result.append(list[i]).append("\u001f")
        }
        if (list.isNotEmpty()) {
            result.append(list[list.size - 1])
        }
        return result.toString()
    }

    @KotlinCleanup("ensure manual conversion is correct")
    fun splitFields(fields: String): Array<String> {
        // -1 ensures that we don't drop empty fields at the ends
        return fields.split(FIELD_SEPARATOR).toTypedArray()
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
    @KotlinCleanup("remove if check return empty string directly if data is null")
    fun checksum(data: String?): String {
        var result = ""
        if (data != null) {
            val md: MessageDigest
            var digest: ByteArray? = null
            try {
                md = MessageDigest.getInstance("SHA1")
                digest = md.digest(data.toByteArray(charset("UTF-8")))
            } catch (e: NoSuchAlgorithmException) {
                Timber.e(e, "Utils.checksum: No such algorithm.")
                throw RuntimeException(e)
            } catch (e: UnsupportedEncodingException) {
                Timber.e(e, "Utils.checksum :: UnsupportedEncodingException")
            }
            val biginteger = BigInteger(1, digest)
            result = biginteger.toString(16)

            // pad with zeros to length of 40 This method used to pad
            // to the length of 32. As it turns out, sha1 has a digest
            // size of 160 bits, leading to a hex digest size of 40,
            // not 32.
            if (result.length < 40) {
                val zeroes = "0000000000000000000000000000000000000000"
                result = zeroes.substring(0, zeroes.length - result.length) + result
            }
        }
        return result
    }

    /**
     * Optimized in case of sortIdx = 0
     * @param fields Fields of a note
     * @param sortIdx An index of the field
     * @return The field at sortIdx, without html media, and the csum of the first field.
     */
    fun sfieldAndCsum(fields: Array<String>, sortIdx: Int): Pair<String, Long> {
        val firstStripped = stripHTMLMedia(fields[0])
        val sortStripped = if (sortIdx == 0) firstStripped else stripHTMLMedia(fields[sortIdx])
        return Pair(sortStripped, fieldChecksumWithoutHtmlMedia(firstStripped))
    }

    /**
     * @param data the string to generate hash from. Html media should be removed
     * @return 32 bit unsigned number from first 8 digits of sha1 hash
     */
    fun fieldChecksumWithoutHtmlMedia(data: String?): Long {
        return java.lang.Long.valueOf(checksum(data).substring(0, 8), 16)
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
    fun convertStreamToString(`is`: InputStream?): String {
        var contentOfMyInputStream = ""
        try {
            val rd = BufferedReader(InputStreamReader(`is`), 4096)
            var line: String?
            val sb = StringBuilder()
            while (rd.readLine().also { line = it } != null) {
                sb.append(line)
            }
            rd.close()
            contentOfMyInputStream = sb.toString()
        } catch (e: Exception) {
            Timber.w(e)
        }
        return contentOfMyInputStream
    }

    /**
     * Determine available storage space
     *
     * @param path the filesystem path you need free space information on
     * @return long indicating the bytes available for that path
     */
    fun determineBytesAvailable(path: String?): Long {
        return StatFs(path).availableBytes
    }

    /**
     * Calls [.writeToFileImpl] and handles IOExceptions
     * Does not close the provided stream
     * @throws IOException Rethrows exception after a set number of retries
     */
    @Throws(IOException::class)
    fun writeToFile(source: InputStream, destination: String) {
        // sometimes this fails and works on retries (hardware issue?)
        val retries = 5
        var retryCnt = 0
        var success = false
        while (!success && retryCnt++ < retries) {
            try {
                writeToFileImpl(source, destination)
                success = true
            } catch (e: IOException) {
                if (retryCnt == retries) {
                    Timber.e("IOException while writing to file, out of retries.")
                    throw e
                } else {
                    Timber.e("IOException while writing to file, retrying...")
                    try {
                        Thread.sleep(200)
                    } catch (e1: InterruptedException) {
                        Timber.w(e1)
                    }
                }
            }
        }
    }

    /**
     * Utility method to write to a file.
     * Throws the exception, so we can report it in syncing log
     */
    @Throws(IOException::class)
    private fun writeToFileImpl(source: InputStream, destination: String) {
        val f = File(destination)
        try {
            Timber.d("Creating new file... = %s", destination)
            f.createNewFile()
            @SuppressLint("DirectSystemCurrentTimeMillisUsage")
            val startTimeMillis =
                System.currentTimeMillis()
            val sizeBytes = compat.copyFile(source, destination)

            @SuppressLint("DirectSystemCurrentTimeMillisUsage")
            val endTimeMillis =
                System.currentTimeMillis()
            Timber.d("Finished writeToFile!")
            val durationSeconds = (endTimeMillis - startTimeMillis) / 1000
            val sizeKb = sizeBytes / 1024
            var speedKbSec: Long = 0
            if (endTimeMillis != startTimeMillis) {
                speedKbSec = sizeKb * 1000 / (endTimeMillis - startTimeMillis)
            }
            Timber.d(
                "Utils.writeToFile: Size: %d Kb, Duration: %d s, Speed: %d Kb/s",
                sizeKb,
                durationSeconds,
                speedKbSec
            )
        } catch (e: IOException) {
            throw IOException(f.name + ": " + e.localizedMessage, e)
        }
    }

    /**
     * @param mediaDir media directory path on SD card
     * @return path converted to file URL, properly UTF-8 URL encoded
     */
    fun getBaseUrl(mediaDir: String): String {
        // Use android.net.Uri class to ensure whole path is properly encoded
        // File.toURL() does not work here, and URLEncoder class is not directly usable
        // with existing slashes
        if (mediaDir.isNotEmpty() && !"null".equals(mediaDir, ignoreCase = true)) {
            val mediaDirUri = Uri.fromFile(File(mediaDir))
            return "$mediaDirUri/"
        }
        return ""
    }

    /**
     * Take an array of Long and return an array of long
     *
     * @param array The input with type Long[]
     * @return The output with type long[]
     */
    @KotlinCleanup("make param non-null")
    @KotlinCleanup("maybe .toLongArray()")
    fun toPrimitive(array: Collection<Long>?): LongArray? {
        if (array == null) {
            return null
        }
        val results = LongArray(array.size)
        var i = 0
        for (item in array) {
            results[i++] = item
        }
        return results
    }

    /**
     * Returns a String array with two elements:
     * 0 - file name
     * 1 - extension
     */
    fun splitFilename(filename: String): Array<String> {
        var name = filename
        var ext = ""
        val dotPosition = filename.lastIndexOf('.')
        if (dotPosition != -1) {
            name = filename.substring(0, dotPosition)
            ext = filename.substring(dotPosition)
        }
        return arrayOf(name, ext)
    }

    /** Returns a list of files for the installed custom fonts.  */
    fun getCustomFonts(context: Context): List<AnkiFont> {
        val deckPath = CollectionHelper.getCurrentAnkiDroidDirectory(context)
        val fontsPath = "$deckPath/fonts/"
        val fontsDir = File(fontsPath)
        var fontsCount = 0
        var fontsList: Array<File>? = null
        if (fontsDir.exists() && fontsDir.isDirectory) {
            @KotlinCleanup("scope function for performance")
            fontsCount = fontsDir.listFiles()!!.size
            fontsList = fontsDir.listFiles()
        }
        var ankiDroidFonts: Array<String?>? = null
        try {
            ankiDroidFonts = context.assets.list("fonts")
        } catch (e: IOException) {
            Timber.e(e, "Error on retrieving ankidroid fonts")
        }
        @KotlinCleanup("See if code in for loop an be improved")
        val fonts: MutableList<AnkiFont> = ArrayList(fontsCount)
        for (i in 0 until fontsCount) {
            val filePath = fontsList!![i].absolutePath
            val filePathExtension = splitFilename(filePath)[1]
            for (fontExtension in FONT_FILE_EXTENSIONS) {
                // Go through the list of allowed extensions.
                if (filePathExtension.equals(fontExtension, ignoreCase = true)) {
                    // This looks like a font file.
                    val font = createAnkiFont(context, filePath, false)
                    if (font != null) {
                        fonts.add(font)
                    }
                    break // No need to look for other file extensions.
                }
            }
        }
        @KotlinCleanup("simplify with ?.forEach and mapNotNull")
        if (ankiDroidFonts != null) {
            for (ankiDroidFont in ankiDroidFonts) {
                // Assume all files in the assets directory are actually fonts.
                val font = createAnkiFont(context, ankiDroidFont!!, true)
                if (font != null) {
                    fonts.add(font)
                }
            }
        }
        return fonts
    }

    /**
     * Simply copy a file to another location
     * @param sourceFile The source file
     * @param destFile The destination file, doesn't need to exist yet.
     */
    @Throws(IOException::class)
    fun copyFile(sourceFile: File?, destFile: File) {
        FileInputStream(sourceFile).use { source -> writeToFile(source, destFile.absolutePath) }
    }

    /**
     * @return The app version, OS version and device model, provided when syncing.
     */
    fun syncPlatform(): String {
        // AnkiWeb reads this string and uses , and : as delimiters, so we remove them.
        val model = Build.MODEL.replace(',', ' ').replace(':', ' ')
        return String.format(
            Locale.US,
            "android:%s:%s:%s",
            BuildConfig.VERSION_NAME,
            Build.VERSION.RELEASE,
            model
        )
    }

    /**
     * @param left An object of type T
     * @param right An object of type T
     * @param <T> A type on which equals can be called
     * @return Whether both objects are equal.
     </T> */
    // Similar as Objects.equals. So deprecated starting at API Level 19 where this methods exists.
    @KotlinCleanup("remove")
    fun <T> equals(left: T?, right: T?): Boolean {
        return left === right || left != null && left == right
    }

    /**
     * @param fields A map from field name to field value
     * @return The set of non empty field values.
     */
    @KotlinCleanup("remove TextUtils at least. Maybe .filter { }")
    fun nonEmptyFields(fields: Map<String, String>): Set<String> {
        val nonempty_fields: MutableSet<String> = HashSetInit(fields.size)
        for (kv in fields.entries) {
            var value = kv.value
            value = stripHTMLMedia(value).trim { it <= ' ' }
            if (value.isNotEmpty()) {
                nonempty_fields.add(kv.key)
            }
        }
        return nonempty_fields
    }
}
