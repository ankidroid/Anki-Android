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

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import anki.collection.OpChanges
import anki.collection.OpChangesAfterUndo
import anki.frontend.SetSchedulingStatesRequest
import com.ichi2.anki.AbstractFlashcardViewer
import com.ichi2.anki.AbstractFlashcardViewer.Companion.RESULT_NO_MORE_CARDS
import com.ichi2.anki.CollectionManager
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.Flag
import com.ichi2.anki.Reviewer
import com.ichi2.anki.asyncIO
import com.ichi2.anki.cardviewer.CardMediaPlayer
import com.ichi2.anki.common.time.TimeManager
import com.ichi2.anki.launchCatchingIO
import com.ichi2.anki.libanki.Card
import com.ichi2.anki.libanki.CardId
import com.ichi2.anki.libanki.NoteId
import com.ichi2.anki.libanki.redo
import com.ichi2.anki.libanki.sched.Counts
import com.ichi2.anki.libanki.sched.CurrentQueueState
import com.ichi2.anki.libanki.sched.Ease
import com.ichi2.anki.libanki.undo
import com.ichi2.anki.noteeditor.NoteEditorLauncher
import com.ichi2.anki.observability.ChangeManager
import com.ichi2.anki.observability.undoableOp
import com.ichi2.anki.pages.AnkiServer
import com.ichi2.anki.pages.CardInfoDestination
import com.ichi2.anki.pages.DeckOptionsDestination
import com.ichi2.anki.preferences.reviewer.ViewerAction
import com.ichi2.anki.previewer.CardViewerViewModel
import com.ichi2.anki.previewer.TypeAnswer
import com.ichi2.anki.previewer.typeAnsRe
import com.ichi2.anki.reviewer.BindingProcessor
import com.ichi2.anki.reviewer.CardSide
import com.ichi2.anki.reviewer.ReviewerBinding
import com.ichi2.anki.servicelayer.MARKED_TAG
import com.ichi2.anki.servicelayer.NoteService
import com.ichi2.anki.servicelayer.isBuryNoteAvailable
import com.ichi2.anki.servicelayer.isSuspendNoteAvailable
import com.ichi2.anki.settings.Prefs
import com.ichi2.anki.ui.windows.reviewer.autoadvance.AutoAdvance
import com.ichi2.anki.utils.CollectionPreferences
import com.ichi2.anki.utils.Destination
import com.ichi2.anki.utils.ext.flag
import com.ichi2.anki.utils.ext.setUserFlagForCards
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import org.intellij.lang.annotations.Language
import timber.log.Timber

class ReviewerViewModel(
    cardMediaPlayer: CardMediaPlayer,
) : CardViewerViewModel(cardMediaPlayer),
    ChangeManager.Subscriber,
    BindingProcessor<ReviewerBinding, ViewerAction> {
    private var queueState: Deferred<CurrentQueueState?> =
        asyncIO {
            withCol { sched.currentQueueState() }
        }
    override var currentCard =
        asyncIO {
            queueState.await()?.topCard
                ?: Card(anki.cards.Card.getDefaultInstance())
        }
    val finishResultFlow = MutableSharedFlow<Int>()
    val isMarkedFlow = MutableStateFlow(false)
    val flagFlow = MutableStateFlow(Flag.NONE)
    val actionFeedbackFlow = MutableSharedFlow<String>()
    val canBuryNoteFlow = MutableStateFlow(true)
    val canSuspendNoteFlow = MutableStateFlow(true)
    val undoLabelFlow = MutableStateFlow<String?>(null)
    val redoLabelFlow = MutableStateFlow<String?>(null)
    val countsFlow = MutableStateFlow(Counts() to Counts.Queue.NEW)
    val typeAnswerFlow = MutableStateFlow<TypeAnswer?>(null)
    val clearTypeAnswerFlow = MutableSharedFlow<Unit>()
    val destinationFlow = MutableSharedFlow<Destination>()
    val editNoteTagsFlow = MutableSharedFlow<NoteId>()
    val setDueDateFlow = MutableSharedFlow<CardId>()
    val answerTimerStatusFlow = MutableStateFlow<AnswerTimerStatus?>(null)
    val answerFeedbackFlow = MutableSharedFlow<Ease>()

    override val server: AnkiServer = AnkiServer(this, StudyScreenRepository.getServerPort()).also { it.start() }
    private val stateMutationKey = TimeManager.time.intTimeMS().toString()
    val statesMutationEval = MutableSharedFlow<String>()
    var typedAnswer = ""

    private val autoAdvance = AutoAdvance(this)
    private val isHtmlTypeAnswerEnabled = Prefs.isHtmlTypeAnswerEnabled

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
    private val shouldShowNextTimes: Deferred<Boolean> =
        asyncIO {
            CollectionPreferences.getShowIntervalOnButtons()
        }

    init {
        ChangeManager.subscribe(this)
        launchCatchingIO {
            updateUndoAndRedoLabels()
            // The height of the answer buttons may increase if `Show button time` is enabled.
            // To ensure consistent height, load the times to match the height of the `Show answer`
            // button with the answer buttons.
            updateNextTimes()
        }
        cardMediaPlayer.setOnMediaGroupCompletedListener {
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

    override fun onPageFinished(isAfterRecreation: Boolean) {
        Timber.v("ReviewerViewModel::onPageFinished %b", isAfterRecreation)
        if (isAfterRecreation) {
            launchCatchingIO {
                // TODO handle "Don't keep activities"
                if (showingAnswer.value) showAnswer() else showQuestion()
            }
        } else {
            launchCatchingIO {
                updateCurrentCard()
            }
        }
    }

    /**
     * Sends an [eval] request to load the card answer, and updates components
     * with behavior specific to the `Answer` card side.
     *
     * @see showAnswer
     */
    fun onShowAnswer() {
        Timber.v("ReviewerViewModel::onShowAnswer")
        launchCatchingIO {
            while (!statesMutated) {
                delay(50)
            }
            updateNextTimes()
            showAnswer()
            loadAndPlayMedia(CardSide.ANSWER)
            if (!autoAdvance.shouldWaitForAudio()) {
                autoAdvance.onShowAnswer()
            } // else wait for onMediaGroupCompleted

            if (answerTimerStatusFlow.value == null) return@launchCatchingIO
            val did = currentCard.await().currentDeckId()
            val stopTimerOnAnswer = withCol { decks.configDictForDeckId(did) }.stopTimerOnAnswer
            if (stopTimerOnAnswer) {
                answerTimerStatusFlow.emit(AnswerTimerStatus.Stopped)
            }
        }
    }

    private suspend fun toggleMark() {
        Timber.v("ReviewerViewModel::toggleMark")
        val card = currentCard.await()
        val note = withCol { card.note(this@withCol) }
        NoteService.toggleMark(note)
        isMarkedFlow.emit(NoteService.isMarked(note))
    }

    private suspend fun setFlag(flag: Flag) {
        Timber.v("ReviewerViewModel::setFlag")
        val card = currentCard.await()
        undoableOp {
            setUserFlagForCards(listOf(card.id), flag)
        }
        flagFlow.emit(flag)
    }

    private suspend fun toggleFlag(flag: Flag) {
        Timber.v("ReviewerViewModel::toggleFlag")
        if (flag == flagFlow.value) {
            setFlag(Flag.NONE)
        } else {
            setFlag(flag)
        }
    }

    fun onStateMutationCallback() {
        statesMutated = true
    }

    private suspend fun emitEditNoteDestination() {
        val cardId = currentCard.await().id
        val destination = NoteEditorLauncher.EditNoteFromPreviewer(cardId)
        Timber.i("Opening 'edit note' for card %d", cardId)
        destinationFlow.emit(destination)
    }

    private suspend fun emitAddNoteDestination() {
        Timber.i("Launching 'add note'")
        destinationFlow.emit(NoteEditorLauncher.AddNoteFromReviewer())
    }

    private suspend fun emitCardInfoDestination() {
        val cardId = currentCard.await().id
        val destination = CardInfoDestination(cardId)
        Timber.i("Launching 'card info' for card %d", cardId)
        destinationFlow.emit(destination)
    }

    private suspend fun emitDeckOptionsDestination() {
        val deckId = withCol { decks.getCurrentId() }
        val isFiltered = withCol { decks.isFiltered(deckId) }
        val destination = DeckOptionsDestination(deckId, isFiltered)
        Timber.i("Launching 'deck options' for deck %d", deckId)
        destinationFlow.emit(destination)
    }

    private suspend fun deleteNote() {
        val cardId = currentCard.await().id
        val noteCount =
            undoableOp {
                removeNotes(cids = listOf(cardId))
            }.count
        actionFeedbackFlow.emit(CollectionManager.TR.browsingCardsDeleted(noteCount))
        updateCurrentCard()
    }

    suspend fun buryCard() {
        val cardId = currentCard.await().id
        val noteCount =
            undoableOp {
                sched.buryCards(cids = listOf(cardId))
            }.count
        actionFeedbackFlow.emit(CollectionManager.TR.studyingCardsBuried(noteCount))
        updateCurrentCard()
    }

    private suspend fun buryNote() {
        val noteId = currentCard.await().nid
        val noteCount =
            undoableOp {
                sched.buryNotes(nids = listOf(noteId))
            }.count
        actionFeedbackFlow.emit(CollectionManager.TR.studyingCardsBuried(noteCount))
        updateCurrentCard()
    }

    private suspend fun suspendCard() {
        val cardId = currentCard.await().id
        undoableOp {
            sched.suspendCards(ids = listOf(cardId))
        }.count
        actionFeedbackFlow.emit(CollectionManager.TR.studyingCardSuspended())
        updateCurrentCard()
    }

    private suspend fun suspendNote() {
        val noteId = currentCard.await().nid
        undoableOp {
            sched.suspendNotes(ids = listOf(noteId))
        }
        actionFeedbackFlow.emit(CollectionManager.TR.studyingNoteSuspended())
        updateCurrentCard()
    }

    private suspend fun undo() {
        Timber.v("ReviewerViewModel::undo")
        val changes =
            undoableOp {
                if (undoAvailable()) {
                    undo()
                } else {
                    OpChangesAfterUndo.getDefaultInstance()
                }
            }
        val message =
            if (changes.operation.isEmpty()) {
                CollectionManager.TR.actionsNothingToUndo()
            } else {
                CollectionManager.TR.undoActionUndone(changes.operation)
            }
        actionFeedbackFlow.emit(message)
    }

    private suspend fun redo() {
        Timber.v("ReviewerViewModel::redo")
        val changes =
            undoableOp {
                if (redoAvailable()) {
                    redo()
                } else {
                    OpChangesAfterUndo.getDefaultInstance()
                }
            }
        val message =
            if (changes.operation.isEmpty()) {
                CollectionManager.TR.actionsNothingToRedo()
            } else {
                CollectionManager.TR.undoRedoAction(changes.operation)
            }
        actionFeedbackFlow.emit(message)
    }

    private suspend fun userAction(
        @Reviewer.UserAction number: Int,
    ) {
        eval.emit("javascript: ankidroid.userAction($number);")
    }

    fun stopAutoAdvance() {
        Timber.v("ReviewerViewModel::stopAutoAdvance")
        autoAdvance.cancelQuestionAndAnswerActionJobs()
    }

    private suspend fun toggleAutoAdvance() {
        Timber.v("ReviewerViewModel::toggleAutoAdvance")
        autoAdvance.isEnabled = !autoAdvance.isEnabled
        val message =
            if (autoAdvance.isEnabled) {
                CollectionManager.TR.actionsAutoAdvanceActivated()
            } else {
                CollectionManager.TR.actionsAutoAdvanceDeactivated()
            }
        actionFeedbackFlow.emit(message)

        if (autoAdvance.shouldWaitForAudio() && cardMediaPlayer.isPlaying) return

        if (showingAnswer.value) {
            autoAdvance.onShowAnswer()
        } else {
            autoAdvance.onShowQuestion()
        }
    }

    override suspend fun handlePostRequest(
        uri: String,
        bytes: ByteArray,
    ): ByteArray =
        if (uri.startsWith(AnkiServer.ANKI_PREFIX)) {
            when (uri.substring(AnkiServer.ANKI_PREFIX.length)) {
                "getSchedulingStatesWithContext" -> getSchedulingStatesWithContext()
                "setSchedulingStates" -> setSchedulingStates(bytes)
                else -> super.handlePostRequest(uri, bytes)
            }
        } else {
            super.handlePostRequest(uri, bytes)
        }

    override suspend fun showQuestion() {
        Timber.v("ReviewerViewModel::showQuestion")
        super.showQuestion()
        runStateMutationHook()
        updateMarkIcon()
        updateFlagIcon()
        if (!autoAdvance.shouldWaitForAudio()) {
            autoAdvance.onShowQuestion()
        } // else run in onMediaGroupCompleted
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
            "anki.mutateNextCardStates('$stateMutationKey', async (states, customData, ctx) => { $js });",
        )
    }

    private suspend fun getSchedulingStatesWithContext(): ByteArray {
        val state = queueState.await() ?: return ByteArray(0)
        return state
            .schedulingStatesWithContext()
            .toBuilder()
            .mergeStates(
                state.states
                    .toBuilder()
                    .mergeCurrent(
                        state.states.current
                            .toBuilder()
                            .setCustomData(state.topCard.toBackendCard().customData)
                            .build(),
                    ).build(),
            ).build()
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

    fun answerCard(ease: Ease) {
        Timber.v("ReviewerViewModel::answerCard")
        launchCatchingIO {
            val state = queueState.await() ?: return@launchCatchingIO
            val card = currentCard.await()
            val answer =
                withCol {
                    sched.buildAnswer(
                        card = card,
                        states = state.states,
                        ease,
                    )
                }

            undoableOp { sched.answerCard(answer) }
            answerFeedbackFlow.emit(ease)

            val wasLeech = withCol { sched.stateIsLeech(answer.newState) }
            if (wasLeech) {
                withCol { card.load(this) }
                val isSuspended = card.queue.code < 0
                onLeech(isSuspended)
            }
            updateCurrentCard()
        }
    }

    // https://github.com/ankitects/anki/blob/da907053460e2b78c31199f97bbea3cf3600f0c2/qt/aqt/reviewer.py#L954
    private suspend fun onLeech(isSuspended: Boolean) {
        Timber.i("ReviewerViewModel::onLeech (isSuspended = %b)", isSuspended)
        val message = StringBuilder(CollectionManager.TR.studyingCardWasALeech())
        if (isSuspended) {
            message.append(" ")
            message.append(CollectionManager.TR.studyingItHasBeenSuspended())
        }
        actionFeedbackFlow.emit(message.toString())
    }

    private suspend fun loadAndPlayMedia(side: CardSide) {
        Timber.v("ReviewerViewModel::loadAndPlaySounds")
        cardMediaPlayer.loadCardAvTags(currentCard.await())
        cardMediaPlayer.playAllForSide(side)
    }

    private suspend fun updateMarkIcon() {
        Timber.v("ReviewerViewModel::updateMarkIcon")
        val card = currentCard.await()
        val isMarkedValue = withCol { card.note(this@withCol).hasTag(this@withCol, MARKED_TAG) }
        isMarkedFlow.emit(isMarkedValue)
    }

    private suspend fun updateFlagIcon() {
        Timber.v("ReviewerViewModel::updateFlagIcon")
        val card = currentCard.await()
        flagFlow.emit(card.flag)
    }

    private suspend fun updateCurrentCard() {
        Timber.v("ReviewerViewModel::updateCurrentCard")
        queueState =
            asyncIO {
                withCol {
                    sched.currentQueueState()
                }
            }
        val state = queueState.await()
        if (state == null) {
            finishResultFlow.emit(RESULT_NO_MORE_CARDS)
            return
        }

        val card = state.topCard
        currentCard = CompletableDeferred(card)
        setupAnswerTimer(card)
        autoAdvance.onCardChange(card)
        clearTypeAnswerFlow.emit(Unit) // must be before showQuestion()
        showQuestion()
        loadAndPlayMedia(CardSide.QUESTION)
        canBuryNoteFlow.emit(isBuryNoteAvailable(card))
        canSuspendNoteFlow.emit(isSuspendNoteAvailable(card))
        countsFlow.emit(state.counts to state.countsIndex)
    }

    override suspend fun typeAnsFilter(text: String): String {
        Timber.v("ReviewerViewModel::typeAnsFilter")
        val typeAnswer = TypeAnswer.getInstance(currentCard.await(), text)
        return if (showingAnswer.value) {
            typeAnswerFlow.emit(null)
            typeAnswer?.answerFilter(typedAnswer) ?: text
        } else {
            typeAnswerFlow.emit(typeAnswer)
            if (isHtmlTypeAnswerEnabled) {
                typeAnswer?.let { typeAnsQuestionFilter(text, it) } ?: text
            } else {
                TypeAnswer.removeTags(text)
            }
        }
    }

    // https://github.com/ankitects/anki/blob/da907053460e2b78c31199f97bbea3cf3600f0c2/qt/aqt/reviewer.py#L704
    private fun typeAnsQuestionFilter(
        text: String,
        typeAnswer: TypeAnswer,
    ): String {
        @Language("HTML")
        val repl =
            """
            <center>
            <input type="text" id="typeans" oninput="ankidroid.onTypeAnswerInput(event);" 
               style="font-family: '${typeAnswer.font}'; font-size: ${typeAnswer.fontSize}px;">
            </center>
            """.trimIndent()
        return typeAnsRe.replace(text, repl)
    }

    private suspend fun updateUndoAndRedoLabels() {
        Timber.v("ReviewerViewModel::updateUndoAndRedoLabels")
        undoLabelFlow.emit(withCol { undoLabel() })
        redoLabelFlow.emit(withCol { redoLabel() })
    }

    private suspend fun updateNextTimes() {
        Timber.v("ReviewerViewModel::updateNextTimes")
        if (!shouldShowNextTimes.await()) return
        val state = queueState.await() ?: return

        val nextTimes = AnswerButtonsNextTime.from(state)
        answerButtonsNextTimeFlow.emit(nextTimes)
    }

    private fun flipOrAnswer(ease: Ease) {
        Timber.v("ReviewerViewModel::flipOrAnswer")
        if (showingAnswer.value) {
            answerCard(ease)
        } else {
            onShowAnswer()
        }
    }

    private suspend fun editNoteTags() {
        val noteId = currentCard.await().nid
        editNoteTagsFlow.emit(noteId)
    }

    fun onEditedTags(selectedTags: List<String>) {
        launchCatchingIO {
            val card = currentCard.await()
            val note = withCol { card.note(this@withCol) }
            if (note.tags == selectedTags) {
                Timber.d("No changed tags")
                return@launchCatchingIO
            }

            val tagsString = selectedTags.joinToString(" ")
            withCol { note.setTagsFromStr(this@withCol, tagsString) }
            undoableOp {
                updateNote(note)
            }
        }
    }

    private suspend fun launchSetDueDate() {
        val cardId = currentCard.await().id
        setDueDateFlow.emit(cardId)
    }

    private suspend fun setupAnswerTimer(card: Card) {
        val shouldShowTimer = withCol { card.shouldShowTimer(this@withCol) }
        if (!shouldShowTimer) {
            answerTimerStatusFlow.emit(null)
            return
        }
        val limitInMillis = withCol { card.timeLimit(this@withCol) }
        answerTimerStatusFlow.emit(AnswerTimerStatus.Running(limitInMillis))
    }

    fun executeAction(action: ViewerAction) {
        Timber.v("ReviewerViewModel::executeAction %s", action.name)
        launchCatchingIO {
            when (action) {
                ViewerAction.ADD_NOTE -> emitAddNoteDestination()
                ViewerAction.CARD_INFO -> emitCardInfoDestination()
                ViewerAction.DECK_OPTIONS -> emitDeckOptionsDestination()
                ViewerAction.EDIT -> emitEditNoteDestination()
                ViewerAction.TAG -> editNoteTags()
                ViewerAction.DELETE -> deleteNote()
                ViewerAction.MARK -> toggleMark()
                ViewerAction.REDO -> redo()
                ViewerAction.UNDO -> undo()
                ViewerAction.RESCHEDULE_NOTE -> launchSetDueDate()
                ViewerAction.TOGGLE_AUTO_ADVANCE -> toggleAutoAdvance()
                ViewerAction.BURY_NOTE -> buryNote()
                ViewerAction.BURY_CARD -> buryCard()
                ViewerAction.SUSPEND_NOTE -> suspendNote()
                ViewerAction.SUSPEND_CARD -> suspendCard()
                ViewerAction.UNSET_FLAG -> setFlag(Flag.NONE)
                ViewerAction.FLAG_RED -> setFlag(Flag.RED)
                ViewerAction.FLAG_ORANGE -> setFlag(Flag.ORANGE)
                ViewerAction.FLAG_BLUE -> setFlag(Flag.BLUE)
                ViewerAction.FLAG_GREEN -> setFlag(Flag.GREEN)
                ViewerAction.FLAG_PINK -> setFlag(Flag.PINK)
                ViewerAction.FLAG_TURQUOISE -> setFlag(Flag.TURQUOISE)
                ViewerAction.FLAG_PURPLE -> setFlag(Flag.PURPLE)
                ViewerAction.TOGGLE_FLAG_RED -> toggleFlag(Flag.RED)
                ViewerAction.TOGGLE_FLAG_ORANGE -> toggleFlag(Flag.ORANGE)
                ViewerAction.TOGGLE_FLAG_BLUE -> toggleFlag(Flag.BLUE)
                ViewerAction.TOGGLE_FLAG_GREEN -> toggleFlag(Flag.GREEN)
                ViewerAction.TOGGLE_FLAG_PINK -> toggleFlag(Flag.PINK)
                ViewerAction.TOGGLE_FLAG_TURQUOISE -> toggleFlag(Flag.TURQUOISE)
                ViewerAction.TOGGLE_FLAG_PURPLE -> toggleFlag(Flag.PURPLE)
                ViewerAction.SHOW_ANSWER -> if (!showingAnswer.value) onShowAnswer()
                ViewerAction.FLIP_OR_ANSWER_EASE1 -> flipOrAnswer(Ease.AGAIN)
                ViewerAction.FLIP_OR_ANSWER_EASE2 -> flipOrAnswer(Ease.HARD)
                ViewerAction.FLIP_OR_ANSWER_EASE3 -> flipOrAnswer(Ease.GOOD)
                ViewerAction.FLIP_OR_ANSWER_EASE4 -> flipOrAnswer(Ease.EASY)
                ViewerAction.SHOW_HINT -> eval.emit("ankidroid.showHint()")
                ViewerAction.SHOW_ALL_HINTS -> eval.emit("ankidroid.showAllHints()")
                ViewerAction.EXIT -> finishResultFlow.emit(AbstractFlashcardViewer.RESULT_DEFAULT)
                ViewerAction.USER_ACTION_1 -> userAction(1)
                ViewerAction.USER_ACTION_2 -> userAction(2)
                ViewerAction.USER_ACTION_3 -> userAction(3)
                ViewerAction.USER_ACTION_4 -> userAction(4)
                ViewerAction.USER_ACTION_5 -> userAction(5)
                ViewerAction.USER_ACTION_6 -> userAction(6)
                ViewerAction.USER_ACTION_7 -> userAction(7)
                ViewerAction.USER_ACTION_8 -> userAction(8)
                ViewerAction.USER_ACTION_9 -> userAction(9)
                ViewerAction.SUSPEND_MENU -> suspendCard()
                ViewerAction.BURY_MENU -> buryCard()
                ViewerAction.FLAG_MENU -> {}
            }
        }
    }

    override fun processAction(
        action: ViewerAction,
        binding: ReviewerBinding,
    ): Boolean {
        Timber.v("ReviewerViewModel::processAction")
        if (binding.side != CardSide.BOTH && CardSide.fromAnswer(showingAnswer.value) != binding.side) return false
        executeAction(action)
        return true
    }

    // Based in https://github.com/ankitects/anki/blob/1f95d030bbc7ebcc004ffe1e2be2a320c9fe1e94/qt/aqt/reviewer.py#L201
    // and https://github.com/ankitects/anki/blob/1f95d030bbc7ebcc004ffe1e2be2a320c9fe1e94/qt/aqt/reviewer.py#L219
    override fun opExecuted(
        changes: OpChanges,
        handler: Any?,
    ) {
        launchCatchingIO {
            updateUndoAndRedoLabels()

            if (handler == this) return@launchCatchingIO

            when {
                changes.studyQueues -> updateCurrentCard()
                changes.noteText -> {
                    val card = currentCard.await()
                    withCol { card.load(this) }
                    updateMarkIcon()
                    if (showingAnswer.value) {
                        showAnswer()
                    } else {
                        showQuestion()
                    }
                }
                changes.card -> {
                    val card = currentCard.await()
                    withCol { card.load(this) }
                    updateFlagIcon()
                }
            }
        }
    }

    companion object {
        fun factory(soundPlayer: CardMediaPlayer): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    ReviewerViewModel(soundPlayer)
                }
            }
    }
}
