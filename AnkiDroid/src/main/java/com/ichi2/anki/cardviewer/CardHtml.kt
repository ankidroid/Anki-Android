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
import com.ichi2.anki.R
import com.ichi2.anki.TtsParser
import com.ichi2.anki.cardviewer.SingleSoundSide.ANSWER
import com.ichi2.anki.cardviewer.SingleSoundSide.QUESTION
import com.ichi2.annotations.NeedsTest
import com.ichi2.libanki.*
import com.ichi2.libanki.template.MathJax
import net.ankiweb.rsdroid.RustCleanup
import timber.log.Timber

@RustCleanup("transition to an instance of TemplateRenderOutput")
class CardHtml(
    @RustCleanup("legacy")
    private val beforeSoundTemplateExpansion: String,
    private val ord: Int,
    private val context: HtmlGenerator
) {

    fun getTemplateHtml(): String {
        val content = getContent()

        val requiresMathjax = MathJax.textContainsMathjax(content)

        val style = getStyle()
        val script = getScripts(requiresMathjax)
        val cardClass = getCardClass(requiresMathjax)

        Timber.v("content card = \n %s", content)
        Timber.v("::style:: / %s", style)

        return context.cardTemplate.render(content, style, script, cardClass)
    }

    private fun getContent(): String {
        var content = context.expandSounds(beforeSoundTemplateExpansion)
        content = CardAppearance.fixBoldStyle(content)
        return content
    }

    private fun getStyle(): String {
        return context.cardAppearance.style
    }

    private fun getCardClass(requiresMathjax: Boolean): String {
        // CSS class for card-specific styling
        return if (requiresMathjax) {
            context.cardAppearance.getCardClass(ord + 1) + " mathjax-needs-to-render"
        } else {
            context.cardAppearance.getCardClass(ord + 1)
        }
    }

    @NeedsTest("js files can be loaded with the specified sources")
    private fun getScripts(requiresMathjax: Boolean): String {
        return when (requiresMathjax) {
            false -> ""
            true ->
                """        <script src="file:///android_asset/mathjax/conf.js"></script>
        <script src="file:///android_asset/mathjax/tex-chtml.js"></script>"""
        }
    }

    companion object {
        fun createInstance(card: Card, side: Side, context: HtmlGenerator): CardHtml {
            val content = displayString(card, side, context)
            return CardHtml(
                content,
                card.ord,
                context
            )
        }

        /**
         * String, as it will be displayed in the web viewer.
         * Sound/video removed, image escaped...
         * Or warning if required
         * TODO: This is no longer entirely true as more post-processing occurs
         */
        private fun displayString(card: Card, side: Side, context: HtmlGenerator): String {
            var content: String = if (side == Side.FRONT) card.question() else card.answer()
            content = card.col.media.escapeMediaFilenames(content)
            content = context.filterTypeAnswer(content, side)
            Timber.v("question: '%s'", content)
            return enrichWithQADiv(content)
        }

        /**
         * Adds a div html tag around the contents to have an indication, where answer/question is displayed
         *
         * @param content The content to surround with tags.
         * @return The enriched content
         */
        fun enrichWithQADiv(content: String?): String {
            val sb = StringBuilder()
            sb.append("""<div id="qa">""")
            sb.append(content)
            sb.append("</div>")
            return sb.toString()
        }

        fun legacyGetTtsTags(card: Card, cardSide: SingleSoundSide, context: Context): List<TTSTag> {
            val cardSideContent: String = when (cardSide) {
                QUESTION -> card.question(true)
                ANSWER -> card.pureAnswer()
            }
            return TtsParser.getTextsToRead(cardSideContent, context.getString(R.string.reviewer_tts_cloze_spoken_replacement))
        }
    }
}
