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
import androidx.annotation.CheckResult
import anki.config.ConfigKey
import com.ichi2.anki.libanki.Card
import com.ichi2.anki.libanki.Collection
import com.ichi2.anki.libanki.TemplateManager.TemplateRenderContext.TemplateRenderOutput
import com.ichi2.anki.libanki.template.MathJax
import com.ichi2.anki.multimedia.expandSounds
import com.ichi2.anki.preferences.sharedPrefs
import com.ichi2.anki.reviewer.ReviewerCustomFonts
import com.ichi2.anki.settings.Prefs
import timber.log.Timber

/**
 * Holds Android-specific context which affects how a card is rendered to HTML
 *
 * @see renderCard
 */
class AndroidCardRenderContext(
    private val typeAnswer: TypeAnswer,
    private val cardAppearance: CardAppearance,
    private val cardTemplate: CardTemplate,
    private val showAudioPlayButtons: Boolean,
    private val context: Context,
) {
    /**
     * Renders Android-specific functionality to produce a [RenderedCard]
     * 
     * @return A [RenderedCard] and optionally a [HanUnificationIssue] if one was detected
     */
    @CheckResult
    fun renderCard(
        col: Collection,
        card: Card,
        side: SingleCardSide,
    ): RenderedCardWithIssue {
        // obtain the libAnki-rendered card
        var content: String = if (side == SingleCardSide.FRONT) card.question(col) else card.answer(col)
        // IRI-encodes media: `foo bar` -> `foo%20bar`
        content = col.media.escapeMediaFilenames(content)
        // produces either an <input> or <span>...</span> to denote typed input
        content = filterTypeAnswer(content, side)
        // wraps content in <div id="qa">
        content = enrichWithQADiv(content)
        // expands [anki:q:1] to a play button
        content = expandSounds(content, card.renderOutput(col), col)
        // fixes an Android bug where font-weight:600 does not display
        content = CardAppearance.fixBoldStyle(content)

        // detect and optionally fix Han Unification rendering issues
        val detectionResult = HanUnificationDetector.analyze(content)
        var issue: HanUnificationIssue? = null
        
        if (detectionResult.hasIssue) {
            // Check if auto-fix is enabled
            if (Prefs.hanUnificationAutoFix) {
                val defaultLang = Prefs.hanUnificationDefaultLang
                content = HanUnificationDetector.autoFix(content, defaultLang)
                Timber.i("Auto-fixed Han Unification issue in card %d using lang=%s", card.id, defaultLang)
            } else {
                // Log warning and create issue info for user notification
                Timber.w(
                    "Han Unification issue detected in card %d: Found %d CJK characters without lang attribute. " +
                        "Characters: %s. Consider adding lang=\"ja\", lang=\"zh\", or lang=\"ko\" to disambiguate.",
                    card.id,
                    detectionResult.cjkCharacterCount,
                    detectionResult.sampleCharacters.joinToString(""),
                )
                
                // Only create issue if warnings are not ignored
                if (!Prefs.hanUnificationIgnoreWarnings) {
                    issue = HanUnificationIssue(
                        cardId = card.id,
                        cjkCharacterCount = detectionResult.cjkCharacterCount,
                        sampleCharacters = detectionResult.sampleCharacters,
                    )
                }
            }
        }

        // based on the content, load appropriate scripts such as MathJax, then render
        val renderedCard = render(content, card.ord)
        return RenderedCardWithIssue(renderedCard, issue)
    }

    private fun render(
        content: String,
        ord: Int,
    ): RenderedCard {
        val requiresMathjax = MathJax.textContainsMathjax(content)

        val style = cardAppearance.style
        val script =
            when (requiresMathjax) {
                false -> ""
                true ->
                    """        <script src="file:///android_asset/backend/js/mathjax.js"></script>
        <script src="file:///android_asset/backend/js/vendor/mathjax/tex-chtml-full.js"></script>"""
            }
        val cardClass = cardAppearance.getCardClass(ord + 1) + if (requiresMathjax) " mathjax-needs-to-render" else ""

        Timber.v("content card = \n %s", content)
        Timber.v("::style:: / %s", style)

        return cardTemplate.render(content, style, script, cardClass)
    }

    /**
     * Adds a div html tag around the contents to have an indication, where answer/question is displayed
     *
     * @param content The content to surround with tags.
     * @return The enriched content
     */
    private fun enrichWithQADiv(content: String) =
        buildString {
            append("""<div id="qa">""")
            append(content)
            append("</div>")
        }

    private fun filterTypeAnswer(
        content: String,
        side: SingleCardSide,
    ): String =
        when (side) {
            SingleCardSide.FRONT -> typeAnswer.filterQuestion(content)
            SingleCardSide.BACK -> typeAnswer.filterAnswer(content)
        }

    private fun expandSounds(
        content: String,
        renderOutput: TemplateRenderOutput,
        col: Collection,
    ): String {
        val mediaDir = col.media.dir

        return expandSounds(
            content,
            renderOutput,
            showAudioPlayButtons,
            mediaDir,
        )
    }

    companion object {
        fun createInstance(
            context: Context,
            col: Collection,
            typeAnswer: TypeAnswer,
        ): AndroidCardRenderContext {
            val preferences = context.sharedPrefs()
            val cardAppearance = CardAppearance.create(ReviewerCustomFonts(), preferences)
            val cardHtmlTemplate = CardTemplate.load(context)
            val showAudioPlayButtons = !col.config.getBool(ConfigKey.Bool.HIDE_AUDIO_PLAY_BUTTONS)
            return AndroidCardRenderContext(
                typeAnswer,
                cardAppearance,
                cardHtmlTemplate,
                showAudioPlayButtons,
                context,
            )
        }
    }
}

/**
 * Result of card rendering that may include Han Unification issue information.
 */
data class RenderedCardWithIssue(
    val renderedCard: RenderedCard,
    val issue: HanUnificationIssue?,
)

/**
 * Information about a detected Han Unification issue.
 */
data class HanUnificationIssue(
    val cardId: Long,
    val cjkCharacterCount: Int,
    val sampleCharacters: List<Char>,
)
