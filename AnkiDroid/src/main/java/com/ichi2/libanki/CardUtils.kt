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

import com.ichi2.libanki.TemplateManager.TemplateRenderContext.TemplateRenderOutput
import com.ichi2.libanki.utils.LibAnkiAlias

@LibAnkiAlias("question")
fun Card.question(
    col: Collection,
    reload: Boolean = false,
    browser: Boolean = false,
): String = renderOutput(col, reload, browser).questionAndStyle()

@LibAnkiAlias("answer")
fun Card.answer(col: Collection): String = renderOutput(col).answerAndStyle()

@LibAnkiAlias("question_av_tags")
fun Card.questionAvTags(col: Collection): List<AvTag> = renderOutput(col).questionAvTags

@LibAnkiAlias("answer_av_tags")
fun Card.answerAvTags(col: Collection): List<AvTag> = renderOutput(col).answerAvTags

/**
 * @throws net.ankiweb.rsdroid.exceptions.BackendInvalidInputException: If the card does not exist
 */
@LibAnkiAlias("render_output")
fun Card.renderOutput(
    col: Collection,
    reload: Boolean = false,
    browser: Boolean = false,
): TemplateRenderOutput {
    if (renderOutput == null || reload) {
        renderOutput = TemplateManager.TemplateRenderContext.fromExistingCard(col, this, browser).render(col)
    }
    return renderOutput!!
}
