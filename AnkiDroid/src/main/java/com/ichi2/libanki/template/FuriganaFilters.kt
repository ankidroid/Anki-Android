/***************************************************************************************
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

package com.ichi2.libanki.template

import java.util.regex.Matcher
import java.util.regex.Pattern

object FuriganaFilters {
    private val r = Pattern.compile(" ?([^ >]+?)\\[(.+?)]")
    private const val RUBY = "<ruby><rb>$1</rb><rt>$2</rt></ruby>"
    private fun noSound(match: Matcher, repl: String): String {
        val matchGroupZero: String? = match.group(0)
        return if (match.group(2)?.startsWith("sound:") == true && matchGroupZero != null) {
            // return without modification
            matchGroupZero
        } else {
            r.matcher(match.group(0) as CharSequence).replaceAll(repl)
        }
    }

    @JvmStatic
    fun kanjiFilter(txt: String?): String {
        val m = r.matcher(txt as CharSequence)
        val sb = StringBuffer()
        while (m.find()) {
            m.appendReplacement(sb, noSound(m, "$1"))
        }
        m.appendTail(sb)
        return sb.toString()
    }

    @JvmStatic
    fun kanaFilter(txt: String?): String {
        val m = r.matcher(txt as CharSequence)
        val sb = StringBuffer()
        while (m.find()) {
            m.appendReplacement(sb, noSound(m, "$2"))
        }
        m.appendTail(sb)
        return sb.toString()
    }

    @JvmStatic
    fun furiganaFilter(txt: String?): String {
        val m = r.matcher(txt as CharSequence)
        val sb = StringBuffer()
        while (m.find()) {
            m.appendReplacement(sb, noSound(m, RUBY))
        }
        m.appendTail(sb)
        return sb.toString()
    }
}
