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

import androidx.core.text.HtmlCompat
import com.ichi2.libanki.Consts.FIELD_SEPARATOR
import timber.log.Timber
import java.io.UnsupportedEncodingException
import java.math.BigInteger
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.util.Locale
import java.util.regex.Matcher
import java.util.regex.Pattern

// TODO switch to standalone functions and properties and remove Utils container
object Utils {
    // Used to format doubles with English's decimal separator system
    val ENGLISH_LOCALE = Locale("en_US")

    // Regex pattern used in removing tags from text before diff
    private val commentPattern = Pattern.compile("(?s)<!--.*?-->")
    private val stylePattern = Pattern.compile("(?si)<style.*?>.*?</style>")
    private val scriptPattern = Pattern.compile("(?si)<script.*?>.*?</script>")
    private val tagPattern = Pattern.compile("(?s)<.*?>")
    private val typePattern = Pattern.compile("(?s)\\[\\[type:.+?]]")
    private val avRefPattern = Pattern.compile("(?s)\\[anki:play:.:\\d+?]")
    private val htmlEntitiesPattern = Pattern.compile("&#?\\w+;")

    /*
     * HTML
     * ***********************************************************************************************
     */

    /**
     * Strips a text from <style>...</style>, <script>...</script> and <_any_tag_> HTML tags.
     * @param inputParam The HTML text to be cleaned.
     * @return The text without the aforementioned tags.
     </_any_tag_> */
    fun stripHTML(inputParam: String): String {
        var s = commentPattern.matcher(inputParam).replaceAll("")
        s = stripHTMLScriptAndStyleTags(s)
        s = tagPattern.matcher(s).replaceAll("")
        return entsToTxt(s)
    }

    /**
     * Strips <style>...</style> and <script>...</script> HTML tags and content from a string.
     * @param inputParam The HTML text to be cleaned.
     * @return The text without the aforementioned tags.
     */
    fun stripHTMLScriptAndStyleTags(inputParam: String): String {
        var htmlMatcher = stylePattern.matcher(inputParam)
        val s = htmlMatcher.replaceAll("")
        htmlMatcher = scriptPattern.matcher(s)
        return htmlMatcher.replaceAll("")
    }

    /**
     * Takes a string and replaces all the HTML symbols in it with their unescaped representation.
     * This should only affect substrings of the form `&something;` and not tags.
     * Internet rumour says that Html.fromHtml() doesn't cover all cases, but it doesn't get less
     * vague than that.
     * @param htmlInput The HTML escaped text
     * @return The text with its HTML entities unescaped.
     */
    // TODO see if method can be refactored to remove the reference to HtmlCompat
    private fun entsToTxt(htmlInput: String): String {
        // entitydefs defines nbsp as \xa0 instead of a standard space, so we
        // replace it first
        val html = htmlInput.replace("&nbsp;", " ")
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

    /**
     * Strip special fields like `[[type:...]]` and `[anki:play...]` from a string.
     * @param input The text to be cleaned.
     * @return The text without special fields.
     */
    fun stripSpecialFields(input: String): String {
        val s = typePattern.matcher(input).replaceAll("")
        return avRefPattern.matcher(s).replaceAll("")
    }

    /**
     * Strip HTML and special fields from a string.
     * @param input The text to be cleaned.
     * @return The text without HTML and special fields.
     */
    fun stripHTMLAndSpecialFields(input: String): String {
        val s = stripHTML(input)
        return stripSpecialFields(s)
    }

    /*
     * IDs
     * ***********************************************************************************************
     */

    /** Given a list of integers, return a string '(int1,int2,...)'.  */
    fun ids2str(ids: IntArray?): String =
        StringBuilder()
            .apply {
                append("(")
                if (ids != null) {
                    val s = ids.contentToString()
                    append(s.substring(1, s.length - 1))
                }
                append(")")
            }.toString()

    /** Given a list of integers, return a string '(int1,int2,...)'.  */
    fun ids2str(ids: LongArray?): String =
        StringBuilder()
            .apply {
                append("(")
                if (ids != null) {
                    val s = ids.contentToString()
                    append(s.substring(1, s.length - 1))
                }
                append(")")
            }.toString()

    /** Given a list of integers, return a string '(int1,int2,...)', in order given by the iterator.  */
    fun <T> ids2str(ids: Iterable<T>): String =
        StringBuilder(512)
            .apply {
                append("(")
                for ((index, id) in ids.withIndex()) {
                    if (index != 0) {
                        append(", ")
                    }
                    append(id)
                }
                append(")")
            }.toString()

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

    // TODO ensure manual conversion is correct
    fun splitFields(fields: String): MutableList<String> {
        // -1 ensures that we don't drop empty fields at the ends
        return fields.split(FIELD_SEPARATOR).toMutableList()
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
    fun checksum(data: String?): String {
        if (data == null) {
            return ""
        }
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
        var result = biginteger.toString(16)

        // pad with zeros to length of 40 This method used to pad
        // to the length of 32. As it turns out, sha1 has a digest
        // size of 160 bits, leading to a hex digest size of 40,
        // not 32.
        if (result.length < 40) {
            val zeroes = "0000000000000000000000000000000000000000"
            result = zeroes.substring(0, zeroes.length - result.length) + result
        }
        return result
    }
}
