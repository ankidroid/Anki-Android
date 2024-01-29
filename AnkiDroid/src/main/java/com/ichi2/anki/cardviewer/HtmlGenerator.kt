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
import anki.config.ConfigKey
import com.ichi2.anki.preferences.sharedPrefs
import com.ichi2.anki.reviewer.ReviewerCustomFonts
import com.ichi2.libanki.Card
import com.ichi2.libanki.Collection
import com.ichi2.libanki.Sound
import com.ichi2.libanki.stripAvRefs
import timber.log.Timber
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader

class HtmlGenerator(
    private val typeAnswer: TypeAnswer,
    val cardAppearance: CardAppearance,
    val cardTemplate: CardTemplate,
    private val showAudioPlayButtons: Boolean,
    val resources: Resources
) {

    @CheckResult
    fun generateHtml(col: Collection, card: Card, side: SingleCardSide): CardHtml {
        return CardHtml.createInstance(col, card, side, this)
    }

    fun filterTypeAnswer(content: String, side: SingleCardSide): String {
        return when (side) {
            SingleCardSide.FRONT -> typeAnswer.filterQuestion(content)
            SingleCardSide.BACK -> typeAnswer.filterAnswer(content)
        }
    }

    fun expandSounds(content: String): String {
        return if (showAudioPlayButtons) {
            Sound.expandSounds(content)
        } else {
            stripAvRefs(content)
        }
    }

    companion object {
        fun createInstance(
            context: Context,
            col: Collection,
            typeAnswer: TypeAnswer
        ): HtmlGenerator {
            val preferences = context.sharedPrefs()
            val cardAppearance = CardAppearance.create(ReviewerCustomFonts(), preferences)
            val cardHtmlTemplate = loadCardTemplate(context)
            val showAudioPlayButtons = !col.config.getBool(ConfigKey.Bool.HIDE_AUDIO_PLAY_BUTTONS)
            return HtmlGenerator(
                typeAnswer,
                cardAppearance,
                cardHtmlTemplate,
                showAudioPlayButtons,
                context.resources
            )
        }

        /**
         * Load the template for the card
         */
        private fun loadCardTemplate(viewer: Context): CardTemplate {
            try {
                val data = convertStreamToString(viewer.assets.open("card_template.html"))
                return CardTemplate(data)
            } catch (e: IOException) {
                Timber.w(e)
                throw RuntimeException(e)
            }
        }

        /**
         * Converts an InputStream to a String.
         *
         * @param input InputStream to convert
         * @return String version of the InputStream
         */
        private fun convertStreamToString(input: InputStream?): String {
            var contentOfMyInputStream = ""
            try {
                val rd = BufferedReader(InputStreamReader(input), 4096)
                var line: String?
                val sb = StringBuilder()
                while (rd.readLine().also { line = it } != null) {
                    sb.append(line)
                }
                rd.close()
                contentOfMyInputStream = sb.toString()
            } catch (e: Exception) {
                Timber.w(e)
            }
            return contentOfMyInputStream
        }
    }
}
