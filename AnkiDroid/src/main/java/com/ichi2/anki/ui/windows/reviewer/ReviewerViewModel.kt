/*
 *  Copyright (c) 2024 Brayan Oliveira <brayandso.dev@gmail.com>
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
package com.ichi2.anki.ui.windows.reviewer

import android.text.style.RelativeSizeSpan
import androidx.core.text.buildSpannedString
import androidx.core.text.inSpans
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import anki.collection.OpChanges
import anki.frontend.SetSchedulingStatesRequest
import com.ichi2.anki.CollectionManager
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.Ease
import com.ichi2.anki.Flag
import com.ichi2.anki.Reviewer
import com.ichi2.anki.asyncIO
import com.ichi2.anki.cardviewer.CardMediaPlayer
import com.ichi2.anki.launchCatchingIO
import com.ichi2.anki.noteeditor.NoteEditorLauncher
import com.ichi2.anki.pages.AnkiServer
import com.ichi2.anki.pages.CardInfoDestination
import com.ichi2.anki.pages.DeckOptionsDestination
import com.ichi2.anki.preferences.getShowIntervalOnButtons
import com.ichi2.anki.previewer.CardViewerViewModel
import com.ichi2.anki.reviewer.CardSide
import com.ichi2.anki.servicelayer.MARKED_TAG
import com.ichi2.anki.servicelayer.NoteService
import com.ichi2.anki.servicelayer.isBuryNoteAvailable
import com.ichi2.anki.servicelayer.isSuspendNoteAvailable
import com.ichi2.anki.ui.windows.reviewer.autoadvance.AutoAdvance
import com.ichi2.libanki.ChangeManager
import com.ichi2.libanki.redo
import com.ichi2.libanki.sched.Counts
import com.ichi2.libanki.sched.CurrentQueueState
import com.ichi2.libanki.undo
import com.ichi2.libanki.undoableOp
import com.ichi2.libanki.utils.TimeManager
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow

class ReviewerViewModel(cardMediaPlayer: CardMediaPlayer) :
    CardViewerViewModel(cardMediaPlayer),
    ChangeManager.Subscriber {

    private var queueState: Deferred<CurrentQueueState?> = asyncIO {
        // this assumes that the Reviewer won't be launched if there isn't a queueState
        withCol { sched.currentQueueState() }!!
    }
    override var currentCard = asyncIO {
        queueState.await()!!.topCard
    }
    var isQueueFinishedFlow = MutableSharedFlow<Boolean>()
    val isMarkedFlow = MutableStateFlow(false)
    val flagFlow = MutableStateFlow(Flag.NONE)
    val actionFeedbackFlow = MutableSharedFlow<String>()
    val canBuryNoteFlow = MutableStateFlow(true)
    val canSuspendNoteFlow = MutableStateFlow(true)
    val undoLabelFlow = MutableStateFlow<String?>(null)
    val redoLabelFlow = MutableStateFlow<String?>(null)
    val countsFlow = MutableStateFlow(Counts() to Counts.Queue.NEW)

    override val server = AnkiServer(this).also { it.start() }
    private val stateMutationKey = TimeManager.time.intTimeMS().toString()
    val statesMutationEval = MutableSharedFlow<String>()

    private val autoAdvance = AutoAdvance(this)

    /**
     * A flag that determines if the SchedulingStates in CurrentQueueState are
     * safe to persist in the database when answering a card. This is used to
     * ensure that the custom JS scheduler has persisted its SchedulingStates
     * back to the Reviewer before we save it to the database. If the custom
     * scheduler has not been configured, then it is safe to immediately set
     * this to true.
     *
     * This flag should be set to false when we show the front of the card
     * and only set to true once we know the custom scheduler has finished its
     * execution, or set to true immediately if the custom scheduler has not
     * been configured.
     */
    private var statesMutated = true

    val answerButtonsNextTimeFlow: MutableStateFlow<AnswerButtonsNextTime?> = MutableStateFlow(null)
    private val shouldShowNextTimes: Deferred<Boolean> = asyncIO {
        getShowIntervalOnButtons()
    }

    init {
        ChangeManager.subscribe(this)
        launchCatchingIO {
            updateUndoAndRedoLabels()
        }
        cardMediaPlayer.setOnSoundGroupCompletedListener {
            launchCatchingIO {
                if (!autoAdvance.shouldWaitForAudio()) return@launchCatchingIO

                if (showingAnswer.value) {
                    autoAdvance.onShowAnswer()
                } else {
                    autoAdvance.onShowQuestion()
                }
            }
        }
    }

    /* *********************************************************************************************
    ************************ Public methods: meant to be used by the View **************************
    ********************************************************************************************* */

    override fun onPageFinished(isAfterRecreation: Boolean) {
        if (isAfterRecreation) {
            launchCatchingIO {
                // TODO handle "Don't keep activities"
                if (showingAnswer.value) showAnswerInternal() else showQuestion()
            }
        } else {
            launchCatchingIO {
                updateCurrentCard()
            }
        }
    }

    fun showAnswer() {
        launchCatchingIO {
            while (!statesMutated) {
                delay(50)
            }
            updateNextTimes()
            showAnswerInternal()
            loadAndPlaySounds(CardSide.ANSWER)
            if (!autoAdvance.shouldWaitForAudio()) {
                autoAdvance.onShowAnswer()
            } // else wait for onSoundGroupCompleted
        }
    }

    fun answerAgain() = answerCard(Ease.AGAIN)
    fun answerHard() = answerCard(Ease.HARD)
    fun answerGood() = answerCard(Ease.GOOD)
    fun answerEasy() = answerCard(Ease.EASY)

    fun toggleMark() {
        launchCatchingIO {
            val card = currentCard.await()
            val note = withCol { card.note(this@withCol) }
            NoteService.toggleMark(note)
            isMarkedFlow.emit(NoteService.isMarked(note))
        }
    }

    fun setFlag(flag: Flag) {
        launchCatchingIO {
            val card = currentCard.await()
            undoableOp {
                setUserFlagForCards(listOf(card.id), flag)
            }
            flagFlow.emit(flag)
        }
    }

    fun onStateMutationCallback() {
        statesMutated = true
    }

    suspend fun getEditNoteDestination(): NoteEditorLauncher {
        return NoteEditorLauncher.EditNoteFromPreviewer(currentCard.await().id)
    }

    fun refreshCard() {
        launchCatchingIO {
            updateCurrentCard()
        }
    }

    suspend fun getCardInfoDestination(): CardInfoDestination {
        return CardInfoDestination(currentCard.await().id)
    }

    suspend fun getDeckOptionsDestination(): DeckOptionsDestination {
        val deckId = withCol { decks.getCurrentId() }
        val isFiltered = withCol { decks.isFiltered(deckId) }
        return DeckOptionsDestination(deckId, isFiltered)
    }

    fun deleteNote() {
        launchCatchingIO {
            val cardId = currentCard.await().id
            val noteCount = undoableOp {
                removeNotes(cids = listOf(cardId))
            }.count
            actionFeedbackFlow.emit(CollectionManager.TR.browsingCardsDeleted(noteCount))
            updateCurrentCard()
        }
    }

    fun buryCard() {
        launchCatchingIO {
            val cardId = currentCard.await().id
            val noteCount = undoableOp {
                sched.buryCards(cids = listOf(cardId))
            }.count
            actionFeedbackFlow.emit(CollectionManager.TR.studyingCardsBuried(noteCount))
            updateCurrentCard()
        }
    }

    fun buryNote() {
        launchCatchingIO {
            val noteId = currentCard.await().nid
            val noteCount = undoableOp {
                sched.buryNotes(nids = listOf(noteId))
            }.count
            actionFeedbackFlow.emit(CollectionManager.TR.studyingCardsBuried(noteCount))
            updateCurrentCard()
        }
    }

    fun suspendCard() {
        launchCatchingIO {
            val cardId = currentCard.await().id
            undoableOp {
                sched.suspendCards(ids = listOf(cardId))
            }.count
            actionFeedbackFlow.emit(CollectionManager.TR.studyingCardSuspended())
            updateCurrentCard()
        }
    }

    fun suspendNote() {
        launchCatchingIO {
            val noteId = currentCard.await().nid
            undoableOp {
                sched.suspendNotes(ids = listOf(noteId))
            }
            actionFeedbackFlow.emit(CollectionManager.TR.studyingNoteSuspended())
            updateCurrentCard()
        }
    }

    fun undo() {
        launchCatchingIO {
            val changes = undoableOp {
                undo()
            }
            val message = if (changes.operation.isEmpty()) {
                CollectionManager.TR.actionsNothingToUndo()
            } else {
                CollectionManager.TR.undoActionUndone(changes.operation)
            }
            actionFeedbackFlow.emit(message)
            updateCurrentCard()
        }
    }

    fun redo() {
        launchCatchingIO {
            val changes = undoableOp {
                redo()
            }
            val message = if (changes.operation.isEmpty()) {
                CollectionManager.TR.actionsNothingToRedo()
            } else {
                CollectionManager.TR.undoRedoAction(changes.operation)
            }
            actionFeedbackFlow.emit(message)
            updateCurrentCard()
        }
    }

    fun userAction(@Reviewer.UserAction number: Int) {
        launchCatchingIO {
            eval.emit("javascript: ankidroid.userAction($number);")
        }
    }

    fun stopAutoAdvance() {
        autoAdvance.cancelQuestionAndAnswerActionJobs()
    }

    /* *********************************************************************************************
    *************************************** Internal methods ***************************************
    ********************************************************************************************* */

    override suspend fun handlePostRequest(uri: String, bytes: ByteArray): ByteArray {
        return if (uri.startsWith(AnkiServer.ANKI_PREFIX)) {
            when (uri.substring(AnkiServer.ANKI_PREFIX.length)) {
                "getSchedulingStatesWithContext" -> getSchedulingStatesWithContext()
                "setSchedulingStates" -> setSchedulingStates(bytes)
                else -> super.handlePostRequest(uri, bytes)
            }
        } else {
            super.handlePostRequest(uri, bytes)
        }
    }

    override suspend fun showQuestion() {
        super.showQuestion()
        runStateMutationHook()
        if (!autoAdvance.shouldWaitForAudio()) {
            autoAdvance.onShowQuestion()
        } // else run in onSoundGroupCompleted
    }

    private suspend fun runStateMutationHook() {
        val state = queueState.await() ?: return
        val js = state.customSchedulingJs
        if (js.isEmpty()) {
            statesMutated = true
            return
        }
        statesMutated = false
        statesMutationEval.emit(
            "anki.mutateNextCardStates('$stateMutationKey', async (states, customData, ctx) => { $js });"
        )
    }

    private suspend fun getSchedulingStatesWithContext(): ByteArray {
        val state = queueState.await() ?: return ByteArray(0)
        return state.schedulingStatesWithContext().toBuilder()
            .mergeStates(
                state.states.toBuilder().mergeCurrent(
                    state.states.current.toBuilder()
                        .setCustomData(state.topCard.toBackendCard().customData).build()
                ).build()
            )
            .build()
            .toByteArray()
    }

    private suspend fun setSchedulingStates(bytes: ByteArray): ByteArray {
        val state = queueState.await() ?: return ByteArray(0)
        val req = SetSchedulingStatesRequest.parseFrom(bytes)
        if (req.key == stateMutationKey) {
            state.states = req.states
        }
        return ByteArray(0)
    }

    private fun answerCard(ease: Ease) {
        launchCatchingIO {
            queueState.await()?.let {
                undoableOp<OpChanges> { sched.answerCard(it, ease) }
                updateCurrentCard()
            }
        }
    }

    private suspend fun loadAndPlaySounds(side: CardSide) {
        cardMediaPlayer.loadCardSounds(currentCard.await())
        cardMediaPlayer.playAllSoundsForSide(side)
    }

    private suspend fun updateMarkedStatus() {
        val card = currentCard.await()
        val isMarkedValue = withCol { card.note(this@withCol).hasTag(this@withCol, MARKED_TAG) }
        isMarkedFlow.emit(isMarkedValue)
    }

    private suspend fun updateCurrentCard() {
        queueState = asyncIO {
            withCol {
                sched.currentQueueState()
            }
        }
        val state = queueState.await()
        if (state == null) {
            isQueueFinishedFlow.emit(true)
            return
        }

        val card = state.topCard
        currentCard = CompletableDeferred(card)
        autoAdvance.onCardChange(card)
        showQuestion()
        loadAndPlaySounds(CardSide.QUESTION)
        updateMarkedStatus()
        flagFlow.emit(card.userFlag())
        canBuryNoteFlow.emit(isBuryNoteAvailable(card))
        canSuspendNoteFlow.emit(isSuspendNoteAvailable(card))
        countsFlow.emit(state.counts to state.countsIndex)
    }

    // TODO
    override suspend fun typeAnsFilter(text: String): String {
        return text
    }

    private suspend fun updateUndoAndRedoLabels() {
        undoLabelFlow.emit(withCol { undoLabel() })
        redoLabelFlow.emit(withCol { redoLabel() })
    }

    private suspend fun updateNextTimes() {
        if (!shouldShowNextTimes.await()) return
        val state = queueState.await() ?: return

        val nextTimes = AnswerButtonsNextTime.from(state)
        answerButtonsNextTimeFlow.emit(nextTimes)
    }

    override fun opExecuted(changes: OpChanges, handler: Any?) {
        launchCatchingIO { updateUndoAndRedoLabels() }
    }

    companion object {
        fun factory(soundPlayer: CardMediaPlayer): ViewModelProvider.Factory {
            return viewModelFactory {
                initializer {
                    ReviewerViewModel(soundPlayer)
                }
            }
        }

        fun buildAnswerButtonText(title: String, nextTime: String?): CharSequence {
            return if (nextTime != null) {
                buildSpannedString {
                    inSpans(RelativeSizeSpan(0.8F)) {
                        append(nextTime)
                    }
                    append("\n")
                    append(title)
                }
            } else {
                title
            }
        }
    }
}
