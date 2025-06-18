/*
 *  Copyright (c) 2025 David Allison <davidallisongithub@gmail.com>
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

package com.ichi2.libanki

import com.ichi2.anki.common.annotations.NeedsTest
import com.ichi2.libanki.TemplateManager.PartiallyRenderedCard
import com.ichi2.libanki.TemplateManager.PartiallyRenderedCard.Companion.avTagsToNative
import com.ichi2.libanki.TemplateManager.TemplateRenderContext.TemplateRenderOutput
import net.ankiweb.rsdroid.exceptions.BackendTemplateException

@NeedsTest(
    "TTS tags `fieldText` is correctly extracted when sources are parsed to file scheme",
)
fun TemplateManager.TemplateRenderContext.render(col: Collection): TemplateRenderOutput {
    val partial: PartiallyRenderedCard
    try {
        partial = partiallyRender(col)
    } catch (e: BackendTemplateException) {
        return TemplateRenderOutput(
            questionText = e.localizedMessage ?: e.toString(),
            answerText = e.localizedMessage ?: e.toString(),
            questionAvTags = emptyList(),
            answerAvTags = emptyList(),
        )
    }

    val qtext = applyCustomFilters(partial.qnodes, this, frontSide = null)
    val qout = col.backend.extractAvTags(text = qtext, questionSide = true)
    var qoutText = qout.text

    val atext = applyCustomFilters(partial.anodes, this, frontSide = qout.text)
    val aout = col.backend.extractAvTags(text = atext, questionSide = false)
    var aoutText = aout.text

    if (!_browser) {
        val svg = noteType.latexsvg
        qoutText = LaTeX.mungeQA(qout.text, col, svg)
        aoutText = LaTeX.mungeQA(aout.text, col, svg)
    }

    return TemplateRenderOutput(
        questionText = qoutText,
        answerText = aoutText,
        questionAvTags = avTagsToNative(qout.avTagsList),
        answerAvTags = avTagsToNative(aout.avTagsList),
        css = noteType().css,
    )
}
