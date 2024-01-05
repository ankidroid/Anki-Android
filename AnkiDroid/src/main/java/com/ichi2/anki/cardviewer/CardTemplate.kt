/*
 *  Copyright (c) 2020 David Allison <davidallisongithub@gmail.com>
 *
 *  This program is free software; you can redistribute it and/or modify it under
 *  the terms of the GNU General Public License as published by the Free Software
 *  Foundation; either version 3 of the License, or (at your option) any later
 *  version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY
 *  WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 *  PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along with
 *  this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.ichi2.anki.cardviewer

import androidx.annotation.CheckResult
import java.lang.IllegalStateException

class CardTemplate(template: String) {
    private var preStyle: String? = null
    private var preScript: String? = null
    private var preClass: String? = null
    private var preContent: String? = null
    private var postContent: String? = null

    @CheckResult
    fun render(content: String, style: String, script: String, cardClass: String): String {
        return preStyle + style + preScript + script + preClass + cardClass + preContent + content + postContent
    }

    init {
        // Note: This refactoring means the template must be in the specific order of style, class, content.
        // Since this is a const loaded from an asset file, I'm fine with this.
        val classDelim = "::class::"
        val styleDelim = "::style::"
        val scriptDelim = "::script::"
        val contentDelim = "::content::"
        val styleIndex = template.indexOf(styleDelim)
        val scriptIndex = template.indexOf(scriptDelim)
        val classIndex = template.indexOf(classDelim)
        val contentIndex = template.indexOf(contentDelim)
        try {
            preStyle = template.substring(0, styleIndex)
            preScript = template.substring(styleIndex + styleDelim.length, scriptIndex)
            preClass = template.substring(scriptIndex + scriptDelim.length, classIndex)
            preContent = template.substring(classIndex + classDelim.length, contentIndex)
            postContent = template.substring(contentIndex + contentDelim.length)
        } catch (ex: StringIndexOutOfBoundsException) {
            throw IllegalStateException("The card template had replacement string order, or content changed", ex)
        }
    }
}
