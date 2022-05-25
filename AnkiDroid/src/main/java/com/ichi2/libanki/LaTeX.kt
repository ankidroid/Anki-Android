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
import com.ichi2.utils.KotlinCleanup
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
@KotlinCleanup("fix IDE lint issues")
object LaTeX {
    /**
     * Patterns used to identify LaTeX tags
     */
    val STANDARD_PATTERN = Pattern.compile(
        "\\[latex](.+?)\\[/latex]",
        Pattern.DOTALL or Pattern.CASE_INSENSITIVE
    )
    val EXPRESSION_PATTERN = Pattern.compile(
        "\\[\\$](.+?)\\[/\\$]",
        Pattern.DOTALL or Pattern.CASE_INSENSITIVE
    )
    val MATH_PATTERN = Pattern.compile(
        "\\[\\$\\$](.+?)\\[/\\$\\$]",
        Pattern.DOTALL or Pattern.CASE_INSENSITIVE
    )

    /**
     * Convert HTML with embedded latex tags to image links.
     * NOTE: _imgLink produces an alphanumeric filename so there is no need to escape the replacement string.
     */
    @JvmStatic
    fun mungeQA(html: String, col: Collection, model: Model): String {
        return mungeQA(html, col.media, model)
    }

    // It's only goal is to allow testing with a different media manager.
    @VisibleForTesting
    @JvmStatic
    @KotlinCleanup("refactor each matcher/sb code group into a standalone function")
    fun mungeQA(html: String, m: Media, model: Model): String {
        @KotlinCleanup("declare val variables for sb and matcher for each instantiation instead of using a single var variable")
        var sb = StringBuffer()
        @KotlinCleanup("use a scope function like run/with to have matcher in scope to simplify its usage")
        var matcher = STANDARD_PATTERN.matcher(html)
        while (matcher.find()) {
            matcher.appendReplacement(sb, _imgLink(matcher.group(1)!!, model, m))
        }
        matcher.appendTail(sb)
        matcher = EXPRESSION_PATTERN.matcher(sb.toString())
        sb = StringBuffer()
        while (matcher.find()) {
            matcher.appendReplacement(sb, _imgLink("$" + matcher.group(1) + "$", model, m))
        }
        matcher.appendTail(sb)
        matcher = MATH_PATTERN.matcher(sb.toString())
        sb = StringBuffer()
        while (matcher.find()) {
            matcher.appendReplacement(
                sb,
                _imgLink("\\begin{displaymath}" + matcher.group(1) + "\\end{displaymath}", model, m)
            )
        }
        matcher.appendTail(sb)
        return sb.toString()
    }

    /**
     * Return an img link for LATEX.
     */
    @VisibleForTesting
    @JvmStatic
    internal fun _imgLink(latex: String, model: Model, m: Media): String {
        val txt = _latexFromHtml(latex)
        @KotlinCleanup("use an if expression to determine extension type and make ext a val")
        var ext = "png"
        if (model.optBoolean("latexsvg", false)) {
            ext = "svg"
        }
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
    @JvmStatic
    @KotlinCleanup("remove the intermediary var, reduce function body to single line by inlining the method calls")
    private fun _latexFromHtml(latex: String): String {
        var l = latex.replace("<br( /)?>|<div>".toRegex(), "\n")
        l = Utils.stripHTML(l)
        return l
    }
}
