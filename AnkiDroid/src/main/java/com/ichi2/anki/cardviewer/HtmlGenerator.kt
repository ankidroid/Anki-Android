/*
 *  Copyright (c) 2021 David Allison <davidallisongithub@gmail.com>
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

import android.content.Context
import android.content.res.Resources
import androidx.annotation.CheckResult
import com.ichi2.anki.preferences.sharedPrefs
import com.ichi2.anki.reviewer.ReviewerCustomFonts
import com.ichi2.libanki.Card
import com.ichi2.libanki.Sound
import com.ichi2.libanki.Utils
import timber.log.Timber
import java.io.IOException

class HtmlGenerator(
    private val typeAnswer: TypeAnswer,
    val cardAppearance: CardAppearance,
    val cardTemplate: CardTemplate,
    val resources: Resources,
    private val baseUrl: String
) {

    @CheckResult
    fun generateHtml(card: Card, reload: Boolean, side: Side): CardHtml {
        return CardHtml.createInstance(card, reload, side, this)
    }

    fun filterTypeAnswer(content: String, side: Side): String {
        return when (side) {
            Side.FRONT -> typeAnswer.filterQuestion(content)
            Side.BACK -> typeAnswer.filterAnswer(content)
        }
    }

    fun expandSounds(content: String): String {
        return Sound.expandSounds(baseUrl, content)
    }

    companion object {
        fun createInstance(
            context: Context,
            typeAnswer: TypeAnswer,
            baseUrl: String
        ): HtmlGenerator {
            val preferences = context.sharedPrefs()
            val cardAppearance = CardAppearance.create(ReviewerCustomFonts(context), preferences)
            val cardHtmlTemplate = loadCardTemplate(context)

            return HtmlGenerator(
                typeAnswer,
                cardAppearance,
                cardHtmlTemplate,
                context.resources,
                baseUrl
            )
        }

        /**
         * Load the template for the card
         */
        fun loadCardTemplate(viewer: Context): CardTemplate {
            try {
                val data = Utils.convertStreamToString(viewer.assets.open("card_template.html"))
                return CardTemplate(data)
            } catch (e: IOException) {
                Timber.w(e)
                throw RuntimeException(e)
            }
        }
    }
}
