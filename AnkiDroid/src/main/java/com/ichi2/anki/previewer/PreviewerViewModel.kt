/*
 *  Copyright (c) 2023 Brayan Oliveira <brayandso.dev@gmail.com>
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
package com.ichi2.anki.previewer

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.google.android.material.color.MaterialColors.getColor
import com.ichi2.anki.AnkiDroidApp
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.Flag
import com.ichi2.anki.LanguageUtils
import com.ichi2.anki.OnErrorListener
import com.ichi2.anki.launchCatching
import com.ichi2.anki.servicelayer.MARKED_TAG
import com.ichi2.anki.servicelayer.NoteService
import com.ichi2.libanki.Card
import com.ichi2.libanki.Sound.addPlayButtons
import com.ichi2.themes.Themes
import com.ichi2.utils.toRGBHex
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.intellij.lang.annotations.Language
import org.jetbrains.annotations.VisibleForTesting
import org.json.JSONObject
import timber.log.Timber

class PreviewerViewModel(private val selectedCardIds: LongArray, firstIndex: Int) :
    ViewModel(),
    OnErrorListener {

    override val onError = MutableSharedFlow<String>()
    val eval = MutableSharedFlow<String>()
    val currentIndex = MutableStateFlow(firstIndex)
    val backsideOnly = MutableStateFlow(false)
    val isMarked = MutableStateFlow(false)
    val flagCode: MutableStateFlow<Int> = MutableStateFlow(Flag.NONE.code)
    private val showingAnswer = MutableStateFlow(false)
    val isBackButtonEnabled =
        combine(currentIndex, showingAnswer, backsideOnly) { index, showingAnswer, isBackSideOnly ->
            index != 0 || (showingAnswer && !isBackSideOnly)
        }
    val isNextButtonEnabled = combine(currentIndex, showingAnswer) { index, showingAnswer ->
        index != selectedCardIds.lastIndex || !showingAnswer
    }

    private lateinit var currentCard: Card

    init {
        launchCatching {
            currentIndex.collectLatest { index ->
                currentCard = withCol { getCard(selectedCardIds[index]) }
                showQuestion()
                if (backsideOnly.value) {
                    showAnswer()
                }
            }
        }
    }

    fun toggleBacksideOnly() {
        Timber.v("toggleBacksideOnly() %b", !backsideOnly.value)
        launchCatching {
            backsideOnly.emit(!backsideOnly.value)
            if (backsideOnly.value && !showingAnswer.value) {
                showAnswer()
            }
        }
    }

    fun toggleMark() {
        launchCatching {
            // TODO: Consider a context receiver
            val note = withCol { currentCard.note(this) }
            NoteService.toggleMark(note)
            isMarked.emit(NoteService.isMarked(note))
        }
    }

    fun setFlag(flag: Flag) {
        launchCatching {
            withCol {
                setUserFlagForCards(listOf(currentCard.id), flag.code)
            }
            flagCode.emit(flag.code)
        }
    }

    fun cardId() = currentCard.id

    /**
     * MUST be called once before accessing [currentCard] for the first time
     *
     * @param reload useful if the note has been edited
     */
    fun loadCurrentCard(reload: Boolean = false) {
        Timber.v("loadCurrentCard()")
        launchCatching {
            if (!this::currentCard.isInitialized || reload) {
                currentCard = withCol { getCard(selectedCardIds[currentIndex.value]) }
            }
            val answerShouldBeShown = showingAnswer.value || backsideOnly.value
            showQuestion()
            if (answerShouldBeShown) {
                showAnswer()
            }
        }
    }

    private suspend fun updateFlagIcon() {
        flagCode.emit(currentCard.userFlag())
    }

    private suspend fun updateMarkIcon() {
        val note = withCol { currentCard.note(this) }
        isMarked.emit(note.hasTag(MARKED_TAG))
    }

    private suspend fun showQuestion() {
        Timber.v("showQuestion()")
        showingAnswer.emit(false)

        val questionData = withCol { currentCard.question(this) }
        val question = mungeQA(questionData)
        val answer = withCol { media.escapeMediaFilenames(currentCard.answer(this)) }
        val bodyClass = bodyClassForCardOrd(currentCard.ord)

        eval.emit("_showQuestion(${Json.encodeToString(question)}, ${Json.encodeToString(answer)}, '$bodyClass');")

        updateFlagIcon()
        updateMarkIcon()
    }

    /** Needs the question already being displayed to work (i.e. [showQuestion]),
     * because of how the `_showAnswer()` javascript method works */
    private suspend fun showAnswer() {
        Timber.v("showAnswer()")
        showingAnswer.emit(true)
        val answerData = withCol { currentCard.answer(this) }
        val answer = mungeQA(answerData)
        eval.emit("_showAnswer(${Json.encodeToString(answer)});")
    }

    /** From the [desktop code](https://github.com/ankitects/anki/blob/1ff55475b93ac43748d513794bcaabd5d7df6d9d/qt/aqt/reviewer.py#L358) */
    private suspend fun mungeQA(text: String): String =
        typeAnsFilter(prepareCardTextForDisplay(text))

    private suspend fun prepareCardTextForDisplay(text: String): String {
        return addPlayButtons(withCol { media.escapeMediaFilenames(text) })
    }

    /** From the [desktop code](https://github.com/ankitects/anki/blob/1ff55475b93ac43748d513794bcaabd5d7df6d9d/qt/aqt/reviewer.py#L671) */
    private suspend fun typeAnsFilter(text: String): String {
        return if (showingAnswer.value) {
            typeAnsAnswerFilter(currentCard, text)
        } else {
            typeAnsQuestionFilter(text)
        }
    }

    /**
     * Shows the current card's answer
     * or the next question if the answer is already being shown
     */
    fun onNextButtonClick() {
        launchCatching {
            if (!showingAnswer.value && !backsideOnly.value) {
                showAnswer()
            } else {
                currentIndex.update { it + 1 }
            }
        }
    }

    /**
     * Shows the previous' card question
     * or hides the current answer if the first card is being shown
     */
    fun onPreviousButtonClick() {
        launchCatching {
            if (currentIndex.value > 0) {
                currentIndex.update { it - 1 }
            } else if (showingAnswer.value && !backsideOnly.value) {
                showQuestion()
            }
        }
    }

    companion object {
        fun factory(selectedCardIds: LongArray, currentIndex: Int): ViewModelProvider.Factory {
            return viewModelFactory {
                initializer {
                    PreviewerViewModel(selectedCardIds, currentIndex)
                }
            }
        }

        /**
         * Not exactly equal to anki's stdHtml.
         *
         * Aimed to be used only for reviewing/previewing cards
         */
        fun stdHtml(
            context: Context = AnkiDroidApp.instance,
            nightMode: Boolean = false
        ): String {
            val languageDirectionality = if (LanguageUtils.appLanguageIsRTL()) "rtl" else "ltr"

            val baseTheme: String
            val docClass: String
            if (nightMode) {
                docClass = "night-mode"
                baseTheme = "dark"
            } else {
                docClass = ""
                baseTheme = "light"
            }

            val colors = if (!nightMode) {
                val canvasColor = getColor(
                    context,
                    android.R.attr.colorBackground,
                    android.R.color.white
                ).toRGBHex()
                val fgColor =
                    getColor(context, android.R.attr.textColor, android.R.color.black).toRGBHex()
                ":root { --canvas: $canvasColor ; --fg: $fgColor; }"
            } else {
                val canvasColor = getColor(
                    context,
                    android.R.attr.colorBackground,
                    android.R.color.black
                ).toRGBHex()
                val fgColor =
                    getColor(context, android.R.attr.textColor, android.R.color.white).toRGBHex()
                ":root[class*=night-mode] { --canvas: $canvasColor; --fg: $fgColor; }"
            }

            @Suppress("UnnecessaryVariable") // necessary for the HTML notation
            @Language("HTML")
            val html = """
                <!DOCTYPE html>
                <html class="$docClass" dir="$languageDirectionality" data-bs-theme="$baseTheme">
                <head>
                    <title>AnkiDroid</title>
                        <link rel="stylesheet" type="text/css" href="file:///android_asset/backend/web/root-vars.css">
                        <link rel="stylesheet" type="text/css" href="file:///android_asset/backend/web/reviewer.css">
                    <style type="text/css">
                        .night-mode button { --canvas: #606060; --fg: #eee; }
                        $colors
                    </style>
                </head>
                <body class="${bodyClass()}">
                    <div id="_mark" hidden>&#x2605;</div>
                    <div id="_flag" hidden>&#x2691;</div>
                    <div id="qa"></div>
                    <script src="file:///android_asset/jquery.min.js"></script>
                    <script src="file:///android_asset/mathjax/tex-chtml.js"></script>
                    <script src="file:///android_asset/backend/web/reviewer.js"></script>
                    <script>bridgeCommand = function(){};</script>
                </body>
                </html>
            """.trimIndent()
            return html
        }

        /** @return body classes used when showing a card */
        fun bodyClassForCardOrd(
            cardOrd: Int,
            nightMode: Boolean = Themes.currentTheme.isNightMode
        ): String {
            return "card card${cardOrd + 1} ${bodyClass(nightMode)}"
        }

        private fun bodyClass(nightMode: Boolean = Themes.currentTheme.isNightMode): String {
            return if (nightMode) "nightMode night_mode" else ""
        }

        /** From the [desktop code](https://github.com/ankitects/anki/blob/1ff55475b93ac43748d513794bcaabd5d7df6d9d/qt/aqt/reviewer.py#L669] */
        @VisibleForTesting
        val typeAnsRe = Regex("\\[\\[type:(.+?)]]")

        /** removes `[[type:]]` blocks in questions */
        @VisibleForTesting
        fun typeAnsQuestionFilter(text: String) =
            typeAnsRe.replace(text, "")

        private suspend fun getTypeAnswerField(card: Card, text: String): JSONObject? {
            val match = typeAnsRe.find(text) ?: return null

            val typeAnsFieldName = match.groups[1]!!.value.let {
                if (it.startsWith("cloze:")) {
                    it.split(":")[1]
                } else {
                    it
                }
            }

            val fields = withCol { card.model(this).flds }
            for (i in 0 until fields.length()) {
                val field = fields.get(i) as JSONObject
                if (field.getString("name") == typeAnsFieldName) {
                    return field
                }
            }
            return null
        }

        private suspend fun getExpectedTypeInAnswer(card: Card, field: JSONObject): String? {
            val fieldName = field.getString("name")
            val expected = withCol { card.note(this).getItem(fieldName) }
            return if (fieldName.startsWith("cloze:")) {
                val clozeIdx = card.ord + 1
                withCol {
                    extractClozeForTyping(expected, clozeIdx).takeIf { it.isNotBlank() }
                }
            } else {
                expected
            }
        }

        /** Adapted from the [desktop code](https://github.com/ankitects/anki/blob/1ff55475b93ac43748d513794bcaabd5d7df6d9d/qt/aqt/reviewer.py#L720) */
        suspend fun typeAnsAnswerFilter(card: Card, text: String): String {
            val typeAnswerField = getTypeAnswerField(card, text)
                ?: return typeAnsRe.replace(text, "")
            val expectedAnswer = getExpectedTypeInAnswer(card, typeAnswerField)
                ?: return typeAnsRe.replace(text, "")
            val typeFont = typeAnswerField.getString("font")
            val typeSize = typeAnswerField.getString("size")
            val answerComparison = withCol { compareAnswer(expectedAnswer, provided = "") }
            return typeAnsRe.replace(text) {
                @Language("HTML")
                val output =
                    """<div style="font-family: '$typeFont'; font-size: ${typeSize}px">$answerComparison</div>"""
                output
            }
        }
    }
}
