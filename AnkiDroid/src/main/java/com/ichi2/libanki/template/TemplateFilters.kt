/****************************************************************************************
 * Copyright (c) 2020 Arthur Milchior <arthur@milchior.fr>                              *
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

package com.ichi2.libanki.template

import com.ichi2.anki.AnkiDroidApp
import com.ichi2.anki.R
import com.ichi2.libanki.Utils
import com.ichi2.libanki.template.FuriganaFilters.furiganaFilter
import com.ichi2.libanki.template.FuriganaFilters.kanaFilter
import com.ichi2.libanki.template.FuriganaFilters.kanjiFilter
import com.ichi2.utils.KotlinCleanup
import timber.log.Timber
import java.lang.Exception
import java.util.*
import java.util.regex.Matcher
import java.util.regex.Pattern

/**
 * Port template_filters.rs
 */
@KotlinCleanup("IDE Lint")
object TemplateFilters {
    const val CLOZE_DELETION_REPLACEMENT = "[...]"
    private val fHookFieldMod = Pattern.compile("^(.*?)(?:\\((.*)\\))?$")
    const val CLOZE_REG = "(?si)\\{\\{(c)%s::(.*?)(::(.*?))?\\}\\}"

    /**
     * @param txtInput The current text the filter may change. It may be changed by multiple filter.
     * @param filterInput The name of the filter to apply.
     * @param field_name The name of the field whose text is shown
     * @param tag The entire content of the tag.
     * @return Result of filter on current txt.
     */
    @KotlinCleanup("maybe change var to val")
    fun apply_filter(txtInput: String, filterInput: String, field_name: String, tag: String): String {
        // Timber.d("Models.get():: Processing field: modifier=%s, extra=%s, tag=%s, txt=%s", mod, extra, tag, txt);
        // built-in modifiers
        val txt = txtInput
        var filter = filterInput
        return if ("text" == filter) {
            // strip html
            if (txt.isNotEmpty()) {
                Utils.stripHTML(txt)
            } else {
                ""
            }
        } else if ("type" == filter) {
            // type answer field; convert it to [[type:...]] for the gui code
            // to process
            String.format(Locale.US, "[[%s]]", tag)
        } else if (filter.startsWith("cq-") || filter.startsWith("ca-")) {
            // cloze deletion
            val split = filter.split("-").toTypedArray()
            filter = split[0]
            val extra = split[1]
            if (txt.isNotEmpty() && extra.isNotEmpty()) {
                clozeText(txt, extra, filter[1])
            } else {
                ""
            }
        } else {
            // hook-based field modifier
            val m = fHookFieldMod.matcher(filter)
            if (m.matches()) {
                filter = m.group(1)!!
                @Suppress("UNUSED_VARIABLE")
                val extra = m.group(2)
            }
            try {
                when (filter) {
                    "hint" -> runHint(txt, field_name)
                    "kanji" -> kanjiFilter(txt)
                    "kana" -> kanaFilter(txt)
                    "furigana" -> furiganaFilter(txt)
                    else -> txt
                }
            } catch (e: Exception) {
                Timber.e(e, "Exception while running hook %s", filter)
                AnkiDroidApp.appResources.getString(R.string.filter_error, filter)
            }
        }
    }

    private fun runHint(txt: String, tag: String): String {
        if (txt.trim { it <= ' ' }.length == 0) {
            return ""
        }
        // random id
        val domId = "hint" + txt.hashCode()
        return "<a class=hint href=\"#\" onclick=\"this.style.display='none';document.getElementById('" +
            domId + "').style.display='block';_relinquishFocus();return false;\">" +
            tag + "</a><div id=\"" +
            domId + "\" class=hint style=\"display: none\">" + txt + "</div>"
    }

    @KotlinCleanup("see if we can remove the var")
    private fun clozeText(txtInput: String, ord: String, type: Char): String {
        var txt = txtInput
        if (!Pattern.compile(String.format(Locale.US, CLOZE_REG, ord)).matcher(txt).find()) {
            return ""
        }
        txt = removeFormattingFromMathjax(txt, ord)
        val m = Pattern.compile(String.format(Locale.US, CLOZE_REG, ord)).matcher(txt)
        val repl = StringBuffer()
        while (m.find()) {
            // replace chosen cloze with type
            @KotlinCleanup("maybe make non-null")
            var buf: String?
            buf = if (type == 'q') {
                if (!m.group(4).isNullOrEmpty()) {
                    "[" + m.group(4) + "]"
                } else {
                    CLOZE_DELETION_REPLACEMENT
                }
            } else {
                m.group(2)
            }
            if ("c" == m.group(1)) {
                buf = "<span class=cloze>$buf</span>"
            }
            m.appendReplacement(repl, Matcher.quoteReplacement(buf!!))
        }
        txt = m.appendTail(repl).toString()
        // and display other clozes normally
        return txt.replace(String.format(Locale.US, CLOZE_REG, "\\d+").toRegex(), "$2")
    }

    /**
     * Marks all clozes within MathJax to prevent formatting them.
     *
     * Active Cloze deletions within MathJax should not be wrapped inside
     * a Cloze <span>, as that would interfere with MathJax. This method finds
     * all Cloze deletions number `ord` in `txt` which are inside MathJax inline
     * or display formulas, and replaces their opening '{{c123' with a '{{C123'.
     * The clozeText method interprets the upper-case C as "don't wrap this
     * Cloze in a <span>".
     </span></span> */
    fun removeFormattingFromMathjax(txt: String, ord: String): String {
        val creg = CLOZE_REG.replace("(?si)", "")
        // Scan the string left to right.
        // After a MathJax opening - \( or \[ - flip in_mathjax to True.
        // After a MathJax closing - \) or \] - flip in_mathjax to False.
        // When a Cloze pattern number `ord` is found and we are in MathJax,
        // replace its '{{c' with '{{C'.
        //
        // TODO: Report mismatching opens/closes - e.g. '\(\]'
        // TODO: Report errors in this method better than printing to stdout.
        // flags in middle of expression deprecated
        var in_mathjax = false

        // The following regex matches one of 3 things, noted below:
        val regex = "(?si)" +
            "(\\\\[(\\[])|" + // group 1, MathJax opening
            "(\\\\[])])|" + // group 2, MathJax close
            "(" + String.format(Locale.US, creg, ord) +
            ")"
        val m = Pattern.compile(regex).matcher(txt)
        val replacement = StringBuffer()
        while (m.find()) {
            if (m.group(1) != null) {
                if (in_mathjax) {
                    Timber.d("MathJax opening found while already in MathJax")
                }
                in_mathjax = true
            } else if (m.group(2) != null) {
                if (!in_mathjax) {
                    Timber.d("MathJax close found while not in MathJax")
                }
                in_mathjax = false
            } else if (m.group(3) != null) {
                if (in_mathjax) {
                    // appendReplacement has an issue with backslashes, so...
                    m.appendReplacement(
                        replacement,
                        Matcher.quoteReplacement(
                            m.group(0)!!.replace(
                                "{{c$ord::",
                                "{{C$ord::"
                            )
                        )
                    )
                    continue
                }
            } else {
                Timber.d("Unexpected: no expected capture group is present")
            }
            // appendReplacement has an issue with backslashes, so...
            m.appendReplacement(replacement, Matcher.quoteReplacement(m.group(0)!!))
        }
        return m.appendTail(replacement).toString()
    }
}
