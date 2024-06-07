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
import anki.frontend.SetSchedulingStatesRequest
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.Ease
import com.ichi2.anki.asyncIO
import com.ichi2.anki.cardviewer.CardMediaPlayer
import com.ichi2.anki.launchCatchingIO
import com.ichi2.anki.pages.AnkiServer
import com.ichi2.anki.previewer.CardViewerViewModel
import com.ichi2.anki.reviewer.CardSide
import com.ichi2.libanki.sched.CurrentQueueState
import com.ichi2.libanki.undoableOp
import com.ichi2.libanki.utils.TimeManager
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow

class ReviewerViewModel(cardMediaPlayer: CardMediaPlayer) : CardViewerViewModel(cardMediaPlayer) {

    private var queueState: Deferred<CurrentQueueState?> = asyncIO {
        // this assumes that the Reviewer won't be launched if there isn't a queueState
        withCol { sched.currentQueueState() }!!
    }
    override var currentCard = asyncIO {
        queueState.await()!!.topCard
    }
    var isQueueFinishedFlow = MutableSharedFlow<Boolean>()

    override val server = AnkiServer(this).also { it.start() }
    private val stateMutationKey = TimeManager.time.intTimeMS().toString()
    val statesMutationEval = MutableSharedFlow<String>()

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
            showAnswerInternal()
            loadAndPlaySounds(CardSide.ANSWER)
        }
    }

    fun answerAgain() = answerCard(Ease.AGAIN)
    fun answerHard() = answerCard(Ease.HARD)
    fun answerGood() = answerCard(Ease.GOOD)
    fun answerEasy() = answerCard(Ease.EASY)

    fun onStateMutationCallback() {
        statesMutated = true
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
                undoableOp { sched.answerCard(it, ease.value) }
                updateCurrentCard()
            }
        }
    }

    private suspend fun loadAndPlaySounds(side: CardSide) {
        cardMediaPlayer.loadCardSounds(currentCard.await())
        cardMediaPlayer.playAllSoundsForSide(side)
    }

    private suspend fun updateCurrentCard() {
        queueState = asyncIO {
            withCol {
                sched.currentQueueState()
            }
        }
        queueState.await()?.let {
            currentCard = CompletableDeferred(it.topCard)
            showQuestion()
            loadAndPlaySounds(CardSide.QUESTION)
        } ?: isQueueFinishedFlow.emit(true)
    }

    // TODO
    override suspend fun typeAnsFilter(text: String): String {
        return text
    }

    companion object {
        fun factory(soundPlayer: CardMediaPlayer): ViewModelProvider.Factory {
            return viewModelFactory {
                initializer {
                    ReviewerViewModel(soundPlayer)
                }
            }
        }
    }
}
