/****************************************************************************************
 * Copyright (c) 2018 Mike Hardy <mike@mikehardy.net>                                   *
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

package com.ichi2.utils

import android.text.TextUtils
import androidx.annotation.CheckResult
import org.bitbucket.cowwoc.diffmatchpatch.DiffMatchPatch

/**
 * Functions for diff, match and patch. Computes the difference between two texts to create a patch. Applies the patch
 * onto another text, allowing for errors.
 */
open class DiffEngine {
    private val mDiffMatchPatch = DiffMatchPatch()

    /**
     * Return two strings to display as typed and correct text.
     *
     * @param typed (cleaned-up) text the user typed in,
     * @param correct (cleaned-up) correct text
     * @return Two-element String array with HTML representation of the diffs between the inputs.
     */
    fun diffedHtmlStrings(typed: String?, correct: String?): Array<String> {
        val prettyTyped = StringBuilder()
        val prettyCorrect = StringBuilder()
        for (aDiff in mDiffMatchPatch.diffMain(typed, correct)) {
            val text = escapeLoneMarks(aDiff.text)
            when (aDiff.operation!!) {
                DiffMatchPatch.Operation.INSERT -> prettyTyped.append(wrapBad(text))
                DiffMatchPatch.Operation.DELETE -> prettyCorrect.append(wrapMissing(text))
                DiffMatchPatch.Operation.EQUAL -> {
                    prettyTyped.append(wrapGood(text))
                    prettyCorrect.append(wrapGood(text))
                }
            }
        }
        return arrayOf(prettyTyped.toString(), prettyCorrect.toString())
    }

    companion object {
        private fun wrapBad(s: String): String {
            // We do the comparison with “<”s &c. in the strings, but should of course not just put those in the HTML
            // output. Also, it looks like the Android WebView swallows single “\”s, so replace those with the entity by
            // hand.
            return """<span class="typeBad">""" + escapeHtml(s) + "</span>"
        }

        @CheckResult
        fun wrapGood(s: String?): String {
            return """<span class="typeGood">""" + escapeHtml(s) + "</span>"
        }

        @CheckResult
        fun wrapMissing(s: String?): String {
            return """<span class="typeMissed">""" + escapeHtml(s) + "</span>"
        }

        /** Prevents combining marks not getting highlighted properly if a span starts with them, by adding a "&nbsp;" before them (#10665) */
        fun escapeLoneMarks(s: String): String {
            if (s[0].category.code.startsWith("M")) {
                return "\\xa0$s"
            }
            return s
        }

        /** Escapes dangerous HTML tags (for XSS-like issues/rendering problems)  */
        protected fun escapeHtml(s: String?): String {
            return TextUtils.htmlEncode(s)
                .replace("\\xa0", "&nbsp;")
                .replace("\\", "&#x5c;")
        }
    }
}
