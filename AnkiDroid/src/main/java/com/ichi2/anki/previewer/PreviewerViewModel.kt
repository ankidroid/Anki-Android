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

import androidx.activity.result.ActivityResult
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.Flag
import com.ichi2.anki.NoteEditor
import com.ichi2.anki.OnErrorListener
import com.ichi2.anki.asyncIO
import com.ichi2.anki.browser.PreviewerIdsFile
import com.ichi2.anki.cardviewer.CardMediaPlayer
import com.ichi2.anki.cardviewer.SingleCardSide
import com.ichi2.anki.launchCatchingIO
import com.ichi2.anki.noteeditor.NoteEditorLauncher
import com.ichi2.anki.pages.AnkiServer
import com.ichi2.anki.reviewer.CardSide
import com.ichi2.anki.servicelayer.MARKED_TAG
import com.ichi2.anki.servicelayer.NoteService
import com.ichi2.annotations.NeedsTest
import com.ichi2.libanki.Card
import com.ichi2.libanki.undoableOp
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import org.intellij.lang.annotations.Language
import org.jetbrains.annotations.VisibleForTesting
import timber.log.Timber

class PreviewerViewModel(previewerIdsFile: PreviewerIdsFile, firstIndex: Int, cardMediaPlayer: CardMediaPlayer) :
    CardViewerViewModel(cardMediaPlayer),
    OnErrorListener {

    val currentIndex = MutableStateFlow(firstIndex)
    val backSideOnly = MutableStateFlow(false)
    val isMarked = MutableStateFlow(false)
    val flag: MutableStateFlow<Flag> = MutableStateFlow(Flag.NONE)
    private val selectedCardIds: List<Long> = previewerIdsFile.getCardIds()
    val isBackButtonEnabled =
        combine(currentIndex, showingAnswer, backSideOnly) { index, showingAnswer, isBackSideOnly ->
            index != 0 || (showingAnswer && !isBackSideOnly)
        }
    val isNextButtonEnabled = combine(currentIndex, showingAnswer) { index, showingAnswer ->
        index != selectedCardIds.lastIndex || !showingAnswer
    }

    private val showAnswerOnReload get() = showingAnswer.value || backSideOnly.value

    override var currentCard: Deferred<Card> = asyncIO {
        withCol { getCard(selectedCardIds[firstIndex]) }
    }
    override val server = AnkiServer(this).also { it.start() }

    /* *********************************************************************************************
    ************************ Public methods: meant to be used by the View **************************
    ********************************************************************************************* */

    /** Call this after the webView has finished loading the page */
    @NeedsTest("16302 - a sound-only card on the back/flipped with 'don't keep activities'")
    @NeedsTest("16302 - on config changes, sound continues to play")
    override fun onPageFinished(isAfterRecreation: Boolean) {
        if (isAfterRecreation) {
            launchCatchingIO {
                showCard(showAnswerOnReload)
                // isAfterRecreation can either mean:
                // * after config change (ViewModel exists)
                // * after recreation (ViewModel did not exist)
                // if the ViewModel existed, we want to continue playing audio
                // if not, we want to setup the sound player
                cardMediaPlayer.ensureCardSoundsLoaded(currentCard.await())
            }
            return
        }
        launchCatchingIO {
            currentIndex.collectLatest {
                showCard(showAnswer = backSideOnly.value)
                loadAndPlaySounds()
            }
        }
    }

    fun toggleBackSideOnly() {
        Timber.v("toggleBackSideOnly() %b", !backSideOnly.value)
        launchCatchingIO {
            backSideOnly.emit(!backSideOnly.value)
            if (!backSideOnly.value && showingAnswer.value) {
                showQuestion()
                cardMediaPlayer.autoplayAllSoundsForSide(CardSide.QUESTION)
            } else if (backSideOnly.value && !showingAnswer.value) {
                showAnswerInternal()
                cardMediaPlayer.autoplayAllSoundsForSide(CardSide.ANSWER)
            }
        }
    }

    fun toggleMark() {
        launchCatchingIO {
            val card = currentCard.await()
            val note = withCol { card.note(this@withCol) }
            NoteService.toggleMark(note)
            isMarked.emit(NoteService.isMarked(note))
        }
    }

    fun setFlag(flag: Flag) {
        launchCatchingIO {
            val card = currentCard.await()
            undoableOp {
                setUserFlagForCards(listOf(card.id), flag)
            }
            this.flag.emit(flag)
        }
    }

    fun toggleFlag(flag: Flag) {
        if (this@PreviewerViewModel.flag.value == flag) {
            setFlag(Flag.NONE)
        } else {
            setFlag(flag)
        }
    }

    /**
     * Shows the current card's answer
     * or the next question if the answer is already being shown
     */
    fun onNextButtonClick() {
        launchCatchingIO {
            if (!showingAnswer.value && !backSideOnly.value) {
                showAnswerInternal()
                cardMediaPlayer.autoplayAllSoundsForSide(CardSide.ANSWER)
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

    suspend fun getNoteEditorDestination() = NoteEditorLauncher.EditNoteFromPreviewer(currentCard.await().id)

    fun handleEditCardResult(result: ActivityResult) {
        if (result.data?.getBooleanExtra(NoteEditor.RELOAD_REQUIRED_EXTRA_KEY, false) == true ||
            result.data?.getBooleanExtra(NoteEditor.NOTE_CHANGED_EXTRA_KEY, false) == true
        ) {
            Timber.v("handleEditCardResult()")
            launchCatchingIO {
                showCard(showAnswerOnReload)
                loadAndPlaySounds()
            }
        }
    }

    fun replayAudios() {
        launchCatchingIO {
            val side = if (showingAnswer.value) SingleCardSide.BACK else SingleCardSide.FRONT
            cardMediaPlayer.replayAllSounds(side)
        }
    }

    fun cardsCount() = selectedCardIds.count()

    fun onSliderChange(value: Int) {
        launchCatchingIO {
            currentIndex.emit(value - 1)
        }
    }

    /* *********************************************************************************************
    *************************************** Internal methods ***************************************
    ********************************************************************************************* */

    private suspend fun showCard(showAnswer: Boolean) {
        currentCard = asyncIO {
            withCol { getCard(selectedCardIds[currentIndex.value]) }
        }
        if (showAnswer) showAnswerInternal() else showQuestion()
        updateFlagIcon()
        updateMarkIcon()
    }

    private suspend fun updateFlagIcon() {
        flag.emit(currentCard.await().userFlag())
    }

    private suspend fun updateMarkIcon() {
        val card = currentCard.await()
        val isMarkedValue = withCol { card.note(this@withCol).hasTag(this@withCol, MARKED_TAG) }
        isMarked.emit(isMarkedValue)
    }

    private suspend fun loadAndPlaySounds() {
        val side: CardSide = when {
            backSideOnly.value -> CardSide.BOTH
            showingAnswer.value -> CardSide.ANSWER
            else -> CardSide.QUESTION
        }
        cardMediaPlayer.loadCardSounds(currentCard.await())
        cardMediaPlayer.autoplayAllSoundsForSide(side)
    }

    /** From the [desktop code](https://github.com/ankitects/anki/blob/1ff55475b93ac43748d513794bcaabd5d7df6d9d/qt/aqt/reviewer.py#L671) */
    override suspend fun typeAnsFilter(text: String): String {
        return if (showingAnswer.value) {
            typeAnsAnswerFilter(currentCard.await(), text)
        } else {
            typeAnsQuestionFilter(text)
        }
    }

    companion object {
        fun factory(previewerIdsFile: PreviewerIdsFile, currentIndex: Int, cardMediaPlayer: CardMediaPlayer): ViewModelProvider.Factory {
            return viewModelFactory {
                initializer {
                    PreviewerViewModel(previewerIdsFile, currentIndex, cardMediaPlayer)
                }
            }
        }

        /** removes `[[type:]]` blocks in questions */
        @VisibleForTesting
        fun typeAnsQuestionFilter(text: String) =
            typeAnsRe.replace(text, "")

        /** Adapted from the [desktop code](https://github.com/ankitects/anki/blob/1ff55475b93ac43748d513794bcaabd5d7df6d9d/qt/aqt/reviewer.py#L720) */
        suspend fun typeAnsAnswerFilter(card: Card, text: String): String {
            val typeAnswerField = getTypeAnswerField(card, text)
                ?: return typeAnsRe.replace(text, "")
            val expectedAnswer = getExpectedTypeInAnswer(card, typeAnswerField)
                ?: return typeAnsRe.replace(text, "")
            val typeFont = typeAnswerField.getString("font")
            val typeSize = getFontSize(typeAnswerField)
            val answerComparison = withCol { compareAnswer(expectedAnswer, provided = "") }

            @Language("HTML")
            val output =
                """<div style="font-family: '$typeFont'; font-size: ${typeSize}px">$answerComparison</div>"""
            return typeAnsRe.replace(text, output)
        }
    }
}
