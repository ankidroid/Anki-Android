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

import com.ichi2.libanki.Consts.DEFAULT_DECK_ID
import com.ichi2.libanki.utils.LibAnkiAlias

@LibAnkiAlias("ephemeral_card")
fun Note.ephemeralCard(
    col: Collection,
    ord: Int = 0,
    customNoteType: NotetypeJson? = null,
    customTemplate: CardTemplate? = null,
    fillEmpty: Boolean = false,
    deckId: DeckId = DEFAULT_DECK_ID,
): Card {
    val card = Card(col, id = null)
    card.ord = ord
    card.did = deckId

    val model = customNoteType ?: notetype
    val template =
        if (customTemplate != null) {
            customTemplate.deepClone()
        } else {
            val index = if (model.isStd) ord else 0
            model.templates[index]
        }
    // may differ in cloze case
    template.setOrd(card.ord)

    val output =
        TemplateManager.TemplateRenderContext
            .fromCardLayout(
                note = this,
                card = card,
                notetype = model,
                template = template,
                fillEmpty = fillEmpty,
            ).render(col)
    card.renderOutput = output
    card.note = this
    return card
}
