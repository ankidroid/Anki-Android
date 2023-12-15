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
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.google.android.material.color.MaterialColors.getColor
import com.ichi2.anki.AnkiDroidApp
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.LanguageUtils
import com.ichi2.anki.servicelayer.MARKED_TAG
import com.ichi2.anki.servicelayer.NoteService
import com.ichi2.libanki.Card
import com.ichi2.libanki.addPlayButtons
import com.ichi2.themes.Themes
import com.ichi2.utils.toRGBHex
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.ankiweb.rsdroid.BackendException
import org.intellij.lang.annotations.Language
import timber.log.Timber

class PreviewerViewModel(mediaDir: String, private val selectedCardIds: LongArray, firstIndex: Int) : ViewModel() {
    val eval = MutableSharedFlow<String>()
    val onError = MutableSharedFlow<String>()
    val currentIndex = MutableStateFlow(firstIndex)
    val backsideOnly = MutableStateFlow(false)
    val isMarked = MutableStateFlow(false)

    private var showingAnswer = false

    // TODO maybe move the server to a Service and move it out of here?
    private val server = PreviewerServer(mediaDir).also { it.start() }
    private lateinit var currentCard: Card

    fun toggleBacksideOnly() {
        Timber.v("toggleBacksideOnly() %b", !backsideOnly.value)
        launchCatching {
            backsideOnly.emit(!backsideOnly.value)
            if (backsideOnly.value && !showingAnswer) {
                showAnswer()
            }
        }
    }

    fun toggleMark() {
        launchCatching {
            val note = currentCard.note()
            NoteService.toggleMark(note)
            isMarked.emit(NoteService.isMarked(note))
        }
    }

    fun serverBaseUrl() = server.baseUrl()

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
            val answerShouldBeShown = showingAnswer || backsideOnly.value
            showQuestion()
            if (answerShouldBeShown) {
                showAnswer()
            }
        }
    }

    private suspend fun updateMarkIcon() {
        isMarked.emit(currentCard.note().hasTag(MARKED_TAG))
    }

    private suspend fun showQuestion() {
        Timber.v("showQuestion()")
        showingAnswer = false

        val question = prepareCardTextForDisplay(currentCard.question())
        val answer = withCol { media.escapeMediaFilenames(currentCard.answer()) }
        val bodyClass = bodyClassForCardOrd(currentCard.ord)

        eval.emit("_showQuestion(${Json.encodeToString(question)}, ${Json.encodeToString(answer)}, '$bodyClass');")

        updateMarkIcon()
    }

    /** Needs the question already being displayed to work (i.e. [showQuestion]),
     * because of how the `_showAnswer()` javascript method works */
    private suspend fun showAnswer() {
        Timber.v("showAnswer()")
        showingAnswer = true
        val answer = prepareCardTextForDisplay(currentCard.answer())
        eval.emit("_showAnswer(${Json.encodeToString(answer)});")
    }

    private suspend fun prepareCardTextForDisplay(text: String): String {
        return addPlayButtons(withCol { media.escapeMediaFilenames(text) })
    }

    suspend fun displayCard(index: Int) {
        if (index !in 0..selectedCardIds.lastIndex) {
            return
        }
        currentIndex.emit(index)
        currentCard = withCol { getCard(selectedCardIds[index]) }
        showQuestion()
        if (backsideOnly.value) {
            showAnswer()
        }
    }

    private suspend fun showAnswerOrDisplayCard(index: Int) {
        if (!showingAnswer && !backsideOnly.value) {
            showAnswer()
        } else {
            displayCard(index)
        }
    }

    suspend fun showAnswerOrPreviousCard() {
        showAnswerOrDisplayCard(currentIndex.value - 1)
    }

    suspend fun showAnswerOrNextCard() {
        showAnswerOrDisplayCard(currentIndex.value + 1)
    }

    fun launchCatching(block: suspend PreviewerViewModel.() -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                block.invoke(this@PreviewerViewModel)
            } catch (cancellationException: CancellationException) {
                // CancellationException should be re-thrown to propagate it to the parent coroutine
                throw cancellationException
            } catch (backendException: BackendException) {
                Timber.w(backendException)
                val message = backendException.localizedMessage ?: backendException.toString()
                onError.emit(message)
            } catch (exception: Exception) {
                Timber.w(exception)
                onError.emit(exception.toString())
            }
        }
    }

    companion object {
        fun factory(mediaDir: String, selectedCardIds: LongArray, currentIndex: Int): ViewModelProvider.Factory {
            return viewModelFactory {
                initializer {
                    PreviewerViewModel(mediaDir, selectedCardIds, currentIndex)
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
                val canvasColor = getColor(context, android.R.attr.colorBackground, android.R.color.white).toRGBHex()
                val fgColor = getColor(context, android.R.attr.textColor, android.R.color.black).toRGBHex()
                ":root { --canvas: $canvasColor ; --fg: $fgColor; }"
            } else {
                val canvasColor = getColor(context, android.R.attr.colorBackground, android.R.color.black).toRGBHex()
                val fgColor = getColor(context, android.R.attr.textColor, android.R.color.white).toRGBHex()
                ":root[class*=night-mode] { --canvas: $canvasColor; --fg: $fgColor; }"
            }

            @Suppress("UnnecessaryVariable") // necessary for the HTML notation
            @Language("HTML")
            val html = """
                <!DOCTYPE html>
                <html class="$docClass" dir="$languageDirectionality" data-bs-theme="$baseTheme">
                <head>
                    <title>AnkiDroid</title>
                        <link rel="stylesheet" type="text/css" href="/assets/web/root-vars.css">
                        <link rel="stylesheet" type="text/css" href="/assets/web/reviewer.css">
                    <style type="text/css">
                        .night-mode button { --canvas: #606060; --fg: #eee; }
                        $colors
                    </style>
                </head>
                <body class="${bodyClass()}">
                    <div id="_mark" hidden>&#x2605;</div>
                    <div id="_flag" hidden>&#x2691;</div>
                    <div id="qa"></div>
                    <script src="/assets/jquery.min.js"></script>
                    <script src="/assets/mathjax/tex-chtml.js"></script>
                    <script src="/assets/web/reviewer.js"></script>
                    <script>bridgeCommand = function(){};</script>
                </body>
                </html>
            """.trimIndent()
            return html
        }

        /** @return body classes used when showing a card */
        fun bodyClassForCardOrd(cardOrd: Int, nightMode: Boolean = Themes.currentTheme.isNightMode): String {
            return "card card${cardOrd + 1} ${bodyClass(nightMode)}"
        }

        private fun bodyClass(nightMode: Boolean = Themes.currentTheme.isNightMode): String {
            return if (nightMode) "nightMode night_mode" else ""
        }
    }
}
