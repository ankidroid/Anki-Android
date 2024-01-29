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

import android.media.MediaPlayer
import android.net.Uri
import androidx.activity.result.ActivityResult
import androidx.core.net.toFile
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.Flag
import com.ichi2.anki.NoteEditor
import com.ichi2.anki.OnErrorListener
import com.ichi2.anki.browser.PreviewerIdsFile
import com.ichi2.anki.cardviewer.MediaErrorHandler
import com.ichi2.anki.cardviewer.SoundErrorBehavior
import com.ichi2.anki.cardviewer.SoundErrorListener
import com.ichi2.anki.cardviewer.SoundPlayer
import com.ichi2.anki.launchCatchingIO
import com.ichi2.anki.servicelayer.MARKED_TAG
import com.ichi2.anki.servicelayer.NoteService
import com.ichi2.libanki.Card
import com.ichi2.libanki.Sound
import com.ichi2.libanki.Sound.addPlayButtons
import com.ichi2.libanki.TtsPlayer
import com.ichi2.libanki.hasTag
import com.ichi2.libanki.note
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.intellij.lang.annotations.Language
import org.jetbrains.annotations.VisibleForTesting
import org.json.JSONObject
import timber.log.Timber

class PreviewerViewModel(previewerIdsFile: PreviewerIdsFile, firstIndex: Int) :
    ViewModel(),
    OnErrorListener {

    override val onError = MutableSharedFlow<String>()
    val onMediaError = MutableSharedFlow<String>()
    val onTtsError = MutableSharedFlow<TtsPlayer.TtsError>()
    val mediaErrorHandler = MediaErrorHandler()

    val eval = MutableSharedFlow<String>()
    val currentIndex = MutableStateFlow(firstIndex)
    val backSideOnly = MutableStateFlow(false)
    val isMarked = MutableStateFlow(false)
    val flagCode: MutableStateFlow<Int> = MutableStateFlow(Flag.NONE.code)
    private val showingAnswer = MutableStateFlow(false)
    private val selectedCardIds: List<Long> = previewerIdsFile.getCardIds()
    val isBackButtonEnabled =
        combine(currentIndex, showingAnswer, backSideOnly) { index, showingAnswer, isBackSideOnly ->
            index != 0 || (showingAnswer && !isBackSideOnly)
        }
    val isNextButtonEnabled = combine(currentIndex, showingAnswer) { index, showingAnswer ->
        index != selectedCardIds.lastIndex || !showingAnswer
    }

    private lateinit var currentCard: Card

    private val showAnswerOnReload get() = showingAnswer.value || backSideOnly.value

    private val soundPlayer = SoundPlayer(createSoundErrorListener())

    override fun onCleared() {
        super.onCleared()
        soundPlayer.close()
    }

    /* *********************************************************************************************
    ************************ Public methods: meant to be used by the View **************************
    ********************************************************************************************* */

    /** Call this after the webView has finished loading the page */
    fun onPageFinished() {
        /* if currentCard has already been initialized, it means that this method was already called
        once and the fragment is being recreated, which happens in configuration changes. */
        if (this::currentCard.isInitialized) {
            launchCatchingIO { showCard(showAnswerOnReload) }
            return
        }
        launchCatchingIO {
            currentIndex.collectLatest {
                showCard(showAnswer = backSideOnly.value)
            }
        }
    }

    fun toggleBackSideOnly() {
        Timber.v("toggleBackSideOnly() %b", !backSideOnly.value)
        launchCatchingIO {
            backSideOnly.emit(!backSideOnly.value)
            if (backSideOnly.value) {
                showAnswer()
            } else {
                showQuestion()
            }
        }
    }

    fun toggleMark() {
        launchCatchingIO {
            val note = withCol { currentCard.note() }
            NoteService.toggleMark(note)
            isMarked.emit(NoteService.isMarked(note))
        }
    }

    fun setFlag(flag: Flag) {
        launchCatchingIO {
            withCol {
                setUserFlagForCards(listOf(currentCard.id), flag.code)
            }
            flagCode.emit(flag.code)
        }
    }

    /**
     * Shows the current card's answer
     * or the next question if the answer is already being shown
     */
    fun onNextButtonClick() {
        launchCatchingIO {
            if (!showingAnswer.value && !backSideOnly.value) {
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
        launchCatchingIO {
            if (currentIndex.value > 0) {
                currentIndex.update { it - 1 }
            } else if (showingAnswer.value && !backSideOnly.value) {
                showQuestion()
            }
        }
    }

    fun getNoteEditorDestination() = NoteEditorDestination(currentCard.id)

    fun handleEditCardResult(result: ActivityResult) {
        if (result.data?.getBooleanExtra(NoteEditor.RELOAD_REQUIRED_EXTRA_KEY, false) == true ||
            result.data?.getBooleanExtra(NoteEditor.NOTE_CHANGED_EXTRA_KEY, false) == true
        ) {
            Timber.v("handleEditCardResult()")
            launchCatchingIO {
                showCard(showAnswerOnReload)
            }
        }
    }

    fun cardsCount() = selectedCardIds.count()

    fun playSoundFromUrl(url: String) {
        launchCatchingIO {
            Sound.getAvTag(currentCard, url)?.let {
                soundPlayer.playOneSound(it)
            }
        }
    }

    fun setSoundPlayerEnabled(isEnabled: Boolean) {
        soundPlayer.isEnabled = isEnabled
    }

    /* *********************************************************************************************
    *************************************** Internal methods ***************************************
    ********************************************************************************************* */

    private suspend fun showCard(showAnswer: Boolean) {
        currentCard = withCol { getCard(selectedCardIds[currentIndex.value]) }
        if (showAnswer) showAnswer() else showQuestion()
    }

    private suspend fun updateFlagIcon() {
        flagCode.emit(currentCard.userFlag())
    }

    private suspend fun updateMarkIcon() {
        val isMarkedValue = withCol { currentCard.note().hasTag(MARKED_TAG) }
        isMarked.emit(isMarkedValue)
    }

    private fun bodyClass(): String = bodyClassForCardOrd(currentCard.ord)

    private suspend fun showQuestion() {
        Timber.v("showQuestion()")
        showingAnswer.emit(false)

        val questionData = withCol { currentCard.question(this) }
        val question = mungeQA(questionData)
        val answer = withCol { media.escapeMediaFilenames(currentCard.answer(this)) }

        eval.emit("_showQuestion(${Json.encodeToString(question)}, ${Json.encodeToString(answer)}, '${bodyClass()}');")

        updateFlagIcon()
        updateMarkIcon()
    }

    private suspend fun showAnswer() {
        Timber.v("showAnswer()")
        showingAnswer.emit(true)
        val answerData = withCol { currentCard.answer(this) }
        val answer = mungeQA(answerData)
        eval.emit("_showAnswer(${Json.encodeToString(answer)}, '${bodyClass()}');")
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

    private fun createSoundErrorListener(): SoundErrorListener {
        return object : SoundErrorListener {
            override fun onError(uri: Uri): SoundErrorBehavior {
                val file = uri.toFile()
                // There is a multitude of transient issues with the MediaPlayer.
                // Retrying fixes most of these
                if (file.exists()) return SoundErrorBehavior.RETRY_AUDIO
                mediaErrorHandler.processMissingSound(file) { fileName ->
                    viewModelScope.launch { onMediaError.emit(fileName) }
                }
                return SoundErrorBehavior.CONTINUE_AUDIO
            }

            override fun onMediaPlayerError(
                mp: MediaPlayer?,
                which: Int,
                extra: Int,
                uri: Uri
            ): SoundErrorBehavior {
                Timber.w("Media Error: (%d, %d)", which, extra)
                return onError(uri)
            }

            override fun onTtsError(error: TtsPlayer.TtsError, isAutomaticPlayback: Boolean) {
                mediaErrorHandler.processTtsFailure(error, isAutomaticPlayback) {
                    viewModelScope.launch { onTtsError.emit(error) }
                }
            }
        }
    }

    companion object {
        fun factory(previewerIdsFile: PreviewerIdsFile, currentIndex: Int): ViewModelProvider.Factory {
            return viewModelFactory {
                initializer {
                    PreviewerViewModel(previewerIdsFile, currentIndex)
                }
            }
        }

        /* ********************************** Type-in answer ************************************ */

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
            val expected = withCol { card.note().getItem(fieldName) }
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
