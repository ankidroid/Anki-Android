// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.ui.windows.reviewer

import androidx.core.content.edit
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import anki.scheduler.CardAnswer.Rating
import app.cash.turbine.test
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.common.preferences.sharedPrefs
import com.ichi2.anki.common.utils.ext.cardStateCustomizer
import com.ichi2.anki.libanki.DeckConfig
import com.ichi2.anki.preferences.reviewer.ViewerAction
import com.ichi2.anki.ui.windows.reviewer.autoadvance.AnswerAction
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import org.json.JSONTokener
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

@RunWith(AndroidJUnit4::class)
class ReviewerViewModelTest : RobolectricTest() {
    private val viewModelStore = ViewModelStore()

    override fun getCollectionStorageMode() = CollectionStorageMode.IN_MEMORY_WITH_MEDIA

    @Before
    fun disableAutoplay() {
        // stored in collection; no need for a reset
        updateDeckConfig(col.decks.selected()) { autoplay = false }
    }

    @After
    fun clearViewModels() {
        viewModelStore.clear()
    }

    @Test
    fun `HTML answer marks nosuggest fields and requests a Done key`() =
        runTest {
            val field = htmlAnswerField("nosuggest:type")

            assertEquals("true", field.attr("data-ankidroid-nosuggest"))
            assertEquals("done", field.attr("enterkeyhint"))
        }

    @Test
    fun `ordinary HTML answers keep their keyboard defaults`() =
        runTest {
            val field = htmlAnswerField("type")

            assertFalse(field.hasAttr("data-ankidroid-nosuggest"))
            assertFalse(field.hasAttr("enterkeyhint"))
        }

    private suspend fun TestScope.htmlAnswerField(filter: String): Element {
        targetContext.sharedPrefs().edit { putBoolean("useInputTag", true) }
        val card = addBasicWithTypingNote("front", "back").firstCard()
        val noteType = card.noteType(col)
        noteType.templates[0].qfmt = "{{Front}}{{$filter:Back}}"
        col.notetypes.save(noteType)
        val viewModel = ReviewerViewModel(SavedStateHandle()).also { viewModelStore.put("reviewer", it) }
        lateinit var html: String
        viewModel.eval
            .filter { it.startsWith("_showQuestion(") }
            .test {
                viewModel.onPageFinished(false)
                advanceUntilIdle()
                // The first argument is a JSON string containing the question HTML.
                html = assertIs<String>(JSONTokener(awaitItem().removePrefix("_showQuestion(")).nextValue())
                cancelAndIgnoreRemainingEvents()
            }
        return assertNotNull(Jsoup.parseBodyFragment(html).selectFirst("input#typeans"))
    }

    @Test
    fun `burying a card shows the next question once`() = runTest { assertShowsNextQuestionOnce(ViewerAction.BURY_CARD) }

    @Test
    fun `burying a note shows the next question once`() = runTest { assertShowsNextQuestionOnce(ViewerAction.BURY_NOTE) }

    @Test
    fun `suspending a card shows the next question once`() = runTest { assertShowsNextQuestionOnce(ViewerAction.SUSPEND_CARD) }

    @Test
    fun `suspending a note shows the next question once`() = runTest { assertShowsNextQuestionOnce(ViewerAction.SUSPEND_NOTE) }

    @Test
    fun `deleting a note shows the next question once`() = runTest { assertShowsNextQuestionOnce(ViewerAction.DELETE) }

    private suspend fun TestScope.assertShowsNextQuestionOnce(action: ViewerAction) {
        addBasicNote("one", "1")
        addBasicNote("two", "2")
        val viewModel = ReviewerViewModel(SavedStateHandle()).also { viewModelStore.put("reviewer", it) }

        viewModel.eval
            .filter { it.startsWith("_showQuestion(") }
            .test {
                viewModel.onPageFinished(false)
                advanceUntilIdle()
                awaitItem()

                viewModel.executeAction(action)
                advanceUntilIdle()
                assertTrue(awaitItem().contains("two"))
                expectNoEvents()
            }
    }

    @Test
    fun `showing the answer stops the question audio`() =
        runTest {
            addBasicNote()
            val viewModel = ReviewerViewModel(SavedStateHandle()).also { viewModelStore.put("reviewer", it) }
            viewModel.onPageFinished(false)
            advanceUntilIdle()
            val audio = Job().also { viewModel.cardMediaPlayer.playAvTagsJob = it }

            viewModel.onShowAnswer()
            advanceUntilIdle()

            assertTrue(audio.isCancelled, "question audio should stop")
        }

    @Test
    fun `the answer auto advances after interrupting a replay with autoplay disabled`() =
        runTest {
            val card = addBasicNote("[sound:question.mkv]").firstCard()
            val viewModel = startReviewerWithAnswerTimer()
            viewModel.awaitVideoStart("question.mkv") { viewModel.executeAction(ViewerAction.PLAY_MEDIA) }

            viewModel.onShowAnswer()
            runCurrent()
            assertTrue(viewModel.showingAnswer.value)
            advanceTimeBy(2.seconds)
            runCurrent()

            assertEquals(1, col.getCard(card.id).reps, "the answer timer should grade this card")
        }

    @Test
    fun `the next question auto advances with autoplay disabled`() =
        runTest {
            addBasicNote("one")
            addBasicNote("two")
            updateDeckConfig(col.decks.selected()) {
                secondsToShowQuestion = 1.0
                secondsToShowAnswer = 0.0
                jsonObject.put("waitForAudio", true)
            }
            val viewModel = ReviewerViewModel(SavedStateHandle()).also { viewModelStore.put("reviewer", it) }
            viewModel.onPageFinished(false)
            runCurrent()
            viewModel.executeAction(ViewerAction.TOGGLE_AUTO_ADVANCE)
            runCurrent()

            viewModel.onShowAnswer()
            viewModel.answerCard(Rating.GOOD)
            runCurrent()
            assertFalse(viewModel.showingAnswer.value)
            advanceTimeBy(2.seconds)
            runCurrent()

            assertTrue(viewModel.showingAnswer.value, "the question timer should show the answer")
        }

    @Test
    fun `finishing a question sound during the flip does not grade a still playing answer`() =
        runTest {
            val card = addBasicNote("[sound:question.mkv]", "[sound:answer.mkv]").firstCard()
            val viewModel = startReviewerWithAnswerTimer()
            updateDeckConfig(card.did) { autoplay = true }
            viewModel.awaitVideoStart("question.mkv") { viewModel.playSoundFromUrl("playsound:q:0") }
            val questionPlayback = assertNotNull(viewModel.cardMediaPlayer.playAvTagsJob)

            // Hold delivery of _showAnswer to the WebView between the side change and answer playback.
            val renderAnswer = CompletableDeferred<Unit>()
            backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) {
                viewModel.eval.onSubscription { renderAnswer.await() }.collect()
            }
            viewModel.onShowAnswer()
            runCurrent()
            assertTrue(viewModel.showingAnswer.value)
            viewModel.onVideoFinished()
            questionPlayback.join()

            viewModel.awaitVideoStart("answer.mkv") { renderAnswer.complete(Unit) }
            advanceTimeBy(2.seconds)
            runCurrent()

            assertEquals(0, col.getCard(card.id).reps, "the answer recording has not finished")
        }

    private fun TestScope.startReviewerWithAnswerTimer(): ReviewerViewModel {
        updateDeckConfig(col.decks.selected()) {
            secondsToShowQuestion = 0.0
            secondsToShowAnswer = 1.0
            jsonObject.put(DeckConfig.ANSWER_ACTION, AnswerAction.ANSWER_GOOD.code)
            jsonObject.put("waitForAudio", true)
        }
        val viewModel = ReviewerViewModel(SavedStateHandle()).also { viewModelStore.put("reviewer", it) }
        viewModel.onPageFinished(false)
        runCurrent()
        viewModel.executeAction(ViewerAction.TOGGLE_AUTO_ADVANCE)
        runCurrent()
        return viewModel
    }

    private suspend fun ReviewerViewModel.awaitVideoStart(
        filename: String,
        start: () -> Unit,
    ) {
        eval.filter { "video.play()" in it && filename in it }.test {
            start()
            awaitItem()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `answering waits for the custom scheduler of the first card`() =
        runTest {
            val card = addBasicNote().firstCard()
            col.config.cardStateCustomizer = "await new Promise(resolve => setTimeout(resolve, 5000));"
            val viewModel = ReviewerViewModel(SavedStateHandle()).also { viewModelStore.put("reviewer", it) }

            viewModel.onShowAnswer()
            viewModel.answerCard(Rating.GOOD)
            viewModel.onPageFinished(false)
            advanceUntilIdle()
            assertFalse(viewModel.showingAnswer.value)
            assertEquals(0, col.getCard(card.id).reps)

            viewModel.onStateMutationCallback()
            advanceUntilIdle()
            assertEquals(1, col.getCard(card.id).reps)
        }

    @Test
    fun `restored answer can be graded after process death`() =
        runTest {
            val card = addBasicNote().firstCard()
            val savedStateHandle = SavedStateHandle(mapOf("showingAnswer" to true))
            val viewModel = ReviewerViewModel(savedStateHandle).also { viewModelStore.put("reviewer", it) }

            viewModel.onPageFinished(true)
            advanceUntilIdle()
            assertTrue(viewModel.showingAnswer.value)

            viewModel.answerCard(Rating.GOOD)
            advanceUntilIdle()
            assertEquals(1, col.getCard(card.id).reps)
        }

    @Test
    fun `restored answer waits for the custom scheduler after process death`() =
        runTest {
            val card = addBasicNote().firstCard()
            col.config.cardStateCustomizer = "await new Promise(resolve => setTimeout(resolve, 5000));"
            val savedStateHandle = SavedStateHandle(mapOf("showingAnswer" to true))
            val viewModel = ReviewerViewModel(savedStateHandle).also { viewModelStore.put("reviewer", it) }

            viewModel.statesMutationEvalFlow.test {
                viewModel.onPageFinished(true)
                advanceUntilIdle()
                assertTrue(viewModel.showingAnswer.value)
                assertTrue(awaitItem().contains(col.config.cardStateCustomizer))

                viewModel.answerCard(Rating.GOOD)
                advanceUntilIdle()
                assertEquals(0, col.getCard(card.id).reps)
                cancelAndIgnoreRemainingEvents()

                viewModel.onStateMutationCallback()
                advanceUntilIdle()
                assertEquals(1, col.getCard(card.id).reps)
            }
        }

    @Test
    fun `restoring answer with retained view model does not rerun custom scheduler`() =
        runTest {
            val card = addBasicNote().firstCard()
            col.config.cardStateCustomizer = "await new Promise(resolve => setTimeout(resolve, 5000));"
            val viewModel = ReviewerViewModel(SavedStateHandle()).also { viewModelStore.put("reviewer", it) }

            viewModel.statesMutationEvalFlow.test {
                viewModel.onPageFinished(false)
                advanceUntilIdle()
                awaitItem()
                viewModel.onStateMutationCallback()
                viewModel.onShowAnswer()
                advanceUntilIdle()
                assertTrue(viewModel.showingAnswer.value)

                viewModel.onPageFinished(true)
                advanceUntilIdle()
                expectNoEvents()
                assertTrue(viewModel.showingAnswer.value)
                cancelAndIgnoreRemainingEvents()

                viewModel.answerCard(Rating.GOOD)
                advanceUntilIdle()
                assertEquals(1, col.getCard(card.id).reps)
            }
        }
}
