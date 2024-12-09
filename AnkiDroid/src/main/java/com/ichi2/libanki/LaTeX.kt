/***************************************************************************************
 * Copyright (c) 2009 Edu Zamora <edu.zasu@gmail.com>                                   *
 * Copyright (c) 2012 Kostas Spyropoulos <inigo.aldana@gmail.com>                       *
 * Copyright (c) 2015 Houssam Salem <houssam.salem.au@gmail.com>                        *
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

import androidx.annotation.VisibleForTesting
import com.ichi2.utils.HtmlUtils.escape
import java.util.regex.Matcher
import java.util.regex.Pattern

/**
 * This class is used to detect LaTeX tags in HTML and convert them to their corresponding image
 * file names.
 *
 * Anki provides shortcut forms for certain expressions. These three forms are considered valid
 * LaTeX tags in Anki:
 * ```
 * 1 - [latex]...[/latex]
 * 2 - [$]...[$]
 * 3 - [$$]...[$$]
 * ```
 * Unlike the original python implementation of this class, the AnkiDroid version does not support
 * the generation of LaTeX images.
 */
object LaTeX {
    /**
     * Patterns used to identify LaTeX tags
     */
    private val STANDARD_PATTERN =
        Pattern.compile(
            "\\[latex](.+?)\\[/latex]",
            Pattern.DOTALL or Pattern.CASE_INSENSITIVE,
        )
    private val EXPRESSION_PATTERN =
        Pattern.compile(
            "\\[\\$](.+?)\\[/\\$]",
            Pattern.DOTALL or Pattern.CASE_INSENSITIVE,
        )
    private val MATH_PATTERN =
        Pattern.compile(
            "\\[\\$\\$](.+?)\\[/\\$\\$]",
            Pattern.DOTALL or Pattern.CASE_INSENSITIVE,
        )

    /**
     * Convert HTML with embedded latex tags to image links.
     * NOTE: _imgLink produces an alphanumeric filename so there is no need to escape the replacement string.
     */
    fun mungeQA(
        html: String,
        col: Collection,
        svg: Boolean,
    ): String {
        return mungeQA(html, col.media, svg)
    }

    fun convertHTML(
        html: String,
        media: Media,
        svg: Boolean,
    ): String {
        val stringBuffer = StringBuffer()
        STANDARD_PATTERN.matcher(html).run {
            while (find()) {
                appendReplacement(stringBuffer, imgLink(group(1)!!, svg, media))
            }
            appendTail(stringBuffer)
        }
        return stringBuffer.toString()
    }

    fun convertExpression(
        input: String,
        media: Media,
        svg: Boolean,
    ): String {
        val stringBuffer = StringBuffer()
        EXPRESSION_PATTERN.matcher(input).run {
            while (find()) {
                appendReplacement(stringBuffer, imgLink("$" + group(1) + "$", svg, media))
            }
            appendTail(stringBuffer)
        }
        return stringBuffer.toString()
    }

    fun convertMath(
        input: String,
        media: Media,
        svg: Boolean,
    ): String {
        val stringBuffer = StringBuffer()
        MATH_PATTERN.matcher(input).run {
            while (find()) {
                appendReplacement(
                    stringBuffer,
                    imgLink("\\begin{displaymath}" + group(1) + "\\end{displaymath}", svg, media),
                )
            }
            appendTail(stringBuffer)
        }
        return stringBuffer.toString()
    }

    // It's only goal is to allow testing with a different media manager.
    @VisibleForTesting
    fun mungeQA(
        html: String,
        m: Media,
        svg: Boolean,
    ): String =
        arrayOf(::convertHTML, ::convertExpression, ::convertMath).fold(html) { input, transformer ->
            transformer(input, m, svg)
        }

    /**
     * Return an img link for LATEX.
     */
    @VisibleForTesting
    internal fun imgLink(
        latex: String,
        svg: Boolean,
        m: Media,
    ): String {
        val txt = latexFromHtml(latex)
        val ext = if (svg) "svg" else "png"
        val fname = "latex-" + Utils.checksum(txt) + "." + ext
        return if (m.have(fname)) {
            Matcher.quoteReplacement("<img class=latex alt=\"" + escape(latex) + "\" src=\"" + fname + "\">")
        } else {
            Matcher.quoteReplacement(latex)
        }
    }

    /**
     * Convert entities and fix newlines.
     */
    private fun latexFromHtml(latex: String): String = Utils.stripHTML(latex.replace("<br( /)?>|<div>".toRegex(), "\n"))
}
