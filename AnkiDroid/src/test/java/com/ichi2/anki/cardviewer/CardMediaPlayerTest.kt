// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.cardviewer

import com.ichi2.anki.CardUtils
import com.ichi2.anki.cardviewer.MediaErrorBehavior.CONTINUE_MEDIA
import com.ichi2.anki.cardviewer.MediaErrorBehavior.RETRY_MEDIA
import com.ichi2.anki.cardviewer.MediaErrorBehavior.STOP_MEDIA
import com.ichi2.anki.cardviewer.SingleCardSide.BACK
import com.ichi2.anki.libanki.AvTag
import com.ichi2.anki.libanki.Card
import com.ichi2.anki.libanki.SoundOrVideoTag
import com.ichi2.anki.libanki.TemplateManager
import com.ichi2.anki.libanki.TtsPlayer
import com.ichi2.anki.reviewer.CardSide
import com.ichi2.testutils.JvmTest
import com.ichi2.testutils.TestException
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.coVerifySequence
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.assertFalse

class CardMediaPlayerTest : JvmTest() {
    internal val tagPlayer: SoundTagPlayer = mockk<SoundTagPlayer>()
    internal val ttsPlayer: TtsPlayer = mockk<TtsPlayer>()
    private lateinit var card: Card
    internal val onMediaGroupCompleted: () -> Unit =
        mockk<() -> Unit>().also {
            every { it.invoke() } answers { }
        }

    @Test
    fun `no sounds fires completed listener`() =
        runSoundPlayerTest(
            answers = emptyList(),
            questions = emptyList(),
        ) {
            playAllAndWait(BACK)

            verifyNoSoundsPlayed()
        }

    @Test
    fun singleSoundSuccess() =
        runSoundPlayerTest(
            questions = listOf(SoundOrVideoTag("abc.mp3")),
        ) {
            playAllAndWait()

            coVerify(exactly = 1) { tagPlayer.play(SoundOrVideoTag("abc.mp3"), any()) }
            coVerify(exactly = 0) { ttsPlayer.play(any()) }
            ensureOnMediaGroupCompletedCalled()
        }

    @Test
    fun `back is not played on front`() =
        runSoundPlayerTest(
            answers = listOf(SoundOrVideoTag("abc.mp3")),
        ) {
            playAllAndWait()

            verifyNoSoundsPlayed()
        }

    @Test
    fun `front is not played on back`() =
        runSoundPlayerTest(
            questions = listOf(SoundOrVideoTag("abc.mp3")),
        ) {
            playAllAndWait(BACK)

            verifyNoSoundsPlayed()
        }

    @Test
    fun `replay - front may be played on back`() =
        runSoundPlayerTest(
            questions = listOf(SoundOrVideoTag("front.mp3")),
            answers = listOf(SoundOrVideoTag("back.mp3")),
            replayQuestion = true,
        ) {
            replayAllAndWait(BACK)

            coVerifyOrder {
                tagPlayer.play(SoundOrVideoTag("front.mp3"), any())
                tagPlayer.play(SoundOrVideoTag("back.mp3"), any())
            }
        }

    @Test
    fun `replay when replayQuestion is false`() =
        runSoundPlayerTest(
            questions = listOf(SoundOrVideoTag("front.mp3")),
            answers = listOf(SoundOrVideoTag("back.mp3")),
            replayQuestion = false,
        ) {
            replayAllAndWait(BACK)

            coVerifyOrder {
                tagPlayer.play(SoundOrVideoTag("back.mp3"), any())
            }
        }

    @Test
    fun `autoplay plays the side if the deck plays media automatically`() =
        runSoundPlayerTest(
            questions = listOf(SoundOrVideoTag("front.mp3")),
            autoplay = true,
        ) {
            autoplayAllForSide(card, CardSide.QUESTION)
            playAvTagsJob?.join()

            coVerifySequence {
                tagPlayer.play(SoundOrVideoTag("front.mp3"), any())
            }
        }

    @Test
    fun `autoplay plays nothing if the deck does not play media automatically`() =
        runSoundPlayerTest(
            questions = listOf(SoundOrVideoTag("front.mp3")),
            autoplay = false,
        ) {
            autoplayAllForSide(card, CardSide.QUESTION)
            playAvTagsJob?.join()

            coVerify(exactly = 0) { tagPlayer.play(any(), any()) }
        }

    @Test
    fun `autoplay stops the playing media if the deck does not play media automatically`() =
        runSoundPlayerTest(
            answers = listOf(SoundOrVideoTag("back.mp3")),
            autoplay = false,
        ) {
            val started = CompletableDeferred<Unit>()
            coEvery { tagPlayer.play(any(), any()) } coAnswers {
                started.complete(Unit)
                awaitCancellation()
            }
            playOne(SoundOrVideoTag("front.mp3"))
            started.await()

            autoplayAllForSide(card, CardSide.ANSWER)

            assertFalse(isPlaying, "media of the previous side is playing")
        }

    @Test
    fun `onMediaGroupCompleted is called after exception`() =
        runSoundPlayerTest(
            questions = listOf(SoundOrVideoTag("aa.mp3")),
        ) {
            coEvery { tagPlayer.play(any(), any()) } throws TestException("test")

            playAllAndWait()

            coVerify(exactly = 1) { tagPlayer.play(any(), any()) }
            ensureOnMediaGroupCompletedCalled()
        }

    @Test
    fun `replay calls play twice`() =
        runSoundPlayerTest(
            questions = listOf(SoundOrVideoTag("aa.mp3"), SoundOrVideoTag("bb.mp3")),
        ) {
            coEvery { tagPlayer.play(any(), any()) } throws MediaException(RETRY_MEDIA)

            playAllAndWait()

            coVerifySequence {
                tagPlayer.play(SoundOrVideoTag("aa.mp3"), any())
                tagPlayer.play(SoundOrVideoTag("aa.mp3"), any())
                tagPlayer.play(SoundOrVideoTag("bb.mp3"), any())
                tagPlayer.play(SoundOrVideoTag("bb.mp3"), any())
            }

            ensureOnMediaGroupCompletedCalled()
        }

    @Test
    fun `stop stops playback and calls completed listener`() =
        runSoundPlayerTest(
            questions = listOf(SoundOrVideoTag("aa.mp3"), SoundOrVideoTag("bb.mp3")),
        ) {
            coEvery { tagPlayer.play(any(), any()) } throws MediaException(STOP_MEDIA)

            playAllAndWait()

            coVerifySequence {
                tagPlayer.play(SoundOrVideoTag("aa.mp3"), any())
            }

            ensureOnMediaGroupCompletedCalled()
        }

    @Test
    fun `continue continues playback and calls completed listener`() =
        runSoundPlayerTest(
            questions = listOf(SoundOrVideoTag("aa.mp3"), SoundOrVideoTag("bb.mp3")),
        ) {
            coEvery { tagPlayer.play(any(), any()) } throws MediaException(CONTINUE_MEDIA)

            playAllAndWait()

            coVerifySequence {
                tagPlayer.play(SoundOrVideoTag("aa.mp3"), any())
                tagPlayer.play(SoundOrVideoTag("bb.mp3"), any())
            }

            ensureOnMediaGroupCompletedCalled()
        }

    @Test
    fun `retry playing single sound`() =
        runSoundPlayerTest {
            coEvery { tagPlayer.play(any(), any()) } throws MediaException(RETRY_MEDIA)

            playOneAndWait(SoundOrVideoTag("a.mp3"))

            coVerifySequence {
                tagPlayer.play(SoundOrVideoTag("a.mp3"), any())
                tagPlayer.play(SoundOrVideoTag("a.mp3"), any())
            }
        }

    @Test
    fun `replay follows a change of the deck's replay question option`() =
        runSoundPlayerTest(
            questions = listOf(SoundOrVideoTag("front.mp3")),
            answers = listOf(SoundOrVideoTag("back.mp3")),
            replayQuestion = true,
        ) {
            updateDeckConfig(CardUtils.getDeckIdForCard(card)) { replayq = false }

            replayAllAndWait(BACK)

            coVerifySequence {
                tagPlayer.play(SoundOrVideoTag("back.mp3"), any())
            }
        }

    @Test
    fun `media requested while the player is being disabled is not played`() =
        runSoundPlayerTest(
            questions = listOf(SoundOrVideoTag("question.mp3")),
        ) {
            val started = CompletableDeferred<Unit>()
            val stopped = CompletableDeferred<Unit>()
            coEvery { tagPlayer.play(SoundOrVideoTag("question.mp3"), any()) } coAnswers {
                started.complete(Unit)
                try {
                    awaitCancellation()
                } finally {
                    withContext(NonCancellable) { stopped.await() }
                }
            }
            coEvery { tagPlayer.play(SoundOrVideoTag("one.mp3"), any()) } coAnswers { awaitCancellation() }
            playAllForSide(card, CardSide.QUESTION)
            started.await()

            coroutineScope {
                // suspends until the question stops playing
                launch(start = CoroutineStart.UNDISPATCHED) { setEnabled(false) }
                launch(start = CoroutineStart.UNDISPATCHED) { playOne(SoundOrVideoTag("one.mp3")) }
                stopped.complete(Unit)
            }

            assertFalse(isPlaying, "media is playing in a disabled player")
            coVerify(exactly = 0) { tagPlayer.play(SoundOrVideoTag("one.mp3"), any()) }
        }

    @Test
    fun `the player becomes idle if the request replacing a playback is cancelled`() =
        runSoundPlayerTest(
            questions = listOf(SoundOrVideoTag("question.mp3")),
        ) {
            val started = CompletableDeferred<Unit>()
            val stopped = CompletableDeferred<Unit>()
            coEvery { tagPlayer.play(SoundOrVideoTag("question.mp3"), any()) } coAnswers {
                started.complete(Unit)
                try {
                    awaitCancellation()
                } finally {
                    withContext(NonCancellable) { stopped.await() }
                }
            }
            playAllForSide(card, CardSide.QUESTION)
            started.await()

            coroutineScope {
                // suspends until the question stops playing
                val request = launch(start = CoroutineStart.UNDISPATCHED) { playOne(SoundOrVideoTag("one.mp3")) }
                request.cancelAndJoin()
                stopped.complete(Unit)
            }
            awaitIdle()

            coVerify(exactly = 0) { tagPlayer.play(SoundOrVideoTag("one.mp3"), any()) }
        }

    @Test
    fun `the player is not idle while a playback replaces another`() =
        runSoundPlayerTest(
            questions = listOf(SoundOrVideoTag("question.mp3")),
        ) {
            val started = Channel<Unit>(Channel.UNLIMITED)
            coEvery { tagPlayer.play(any(), any()) } coAnswers {
                started.send(Unit)
                awaitCancellation()
            }
            playAllForSide(card, CardSide.QUESTION)
            started.receive()
            val wasIdle = AtomicBoolean(false)
            // unconfined: observes every change of the playback state
            val idleWatcher =
                CoroutineScope(Dispatchers.Unconfined).launch {
                    awaitIdle()
                    wasIdle.set(true)
                }

            playOne(SoundOrVideoTag("one.mp3"))
            started.receive()

            assertFalse(wasIdle.get(), "the player was idle between two playbacks")
            idleWatcher.cancel()
            stop()
        }

    private fun verifyNoSoundsPlayed() {
        coVerify(exactly = 0) { tagPlayer.play(any(), any()) }
        coVerify(exactly = 0) { ttsPlayer.play(any()) }
        ensureOnMediaGroupCompletedCalled()
    }

    private fun ensureOnMediaGroupCompletedCalled() {
        verify(exactly = 1) { onMediaGroupCompleted.invoke() }
    }

    private suspend fun CardMediaPlayer.playAllAndWait(side: SingleCardSide = SingleCardSide.FRONT) {
        this.playAllForSide(card, side.toCardSide())
        playAvTagsJob?.join()
    }

    private suspend fun CardMediaPlayer.replayAllAndWait(side: SingleCardSide) {
        this.replayAll(card, side)
        playAvTagsJob?.join()
    }

    private suspend fun CardMediaPlayer.playOneAndWait(tag: AvTag) {
        playOne(tag)
        playAvTagsJob?.join()
    }

    fun setup(
        questions: List<AvTag>,
        answers: List<AvTag>,
        replayQuestion: Boolean?,
        autoplay: Boolean?,
    ) {
        card = addBasicNote().firstCard()
        mockkObject(card)

        every { card.renderOutput(any()) } answers {
            TemplateManager.TemplateRenderContext.TemplateRenderOutput(
                questionText = "",
                answerText = "",
                questionAvTags = questions,
                answerAvTags = answers,
                css = "",
            )
        }

        if (replayQuestion != null) {
            updateDeckConfig(CardUtils.getDeckIdForCard(card)) {
                replayq = replayQuestion
            }
        }
        if (autoplay != null) {
            updateDeckConfig(CardUtils.getDeckIdForCard(card)) {
                this.autoplay = autoplay
            }
        }
    }
}

/**
 *
 * @param autoplay [Card.autoplay]
 * @param replayQuestion [Card.replayQuestionAudioOnAnswerSide]
 */
fun CardMediaPlayerTest.runSoundPlayerTest(
    questions: List<AvTag> = emptyList(),
    answers: List<AvTag> = emptyList(),
    replayQuestion: Boolean? = null,
    autoplay: Boolean? = null,
    testBody: suspend CardMediaPlayer.() -> Unit,
) = runTest {
    val cardMediaPlayer =
        CardMediaPlayer(
            soundTagPlayer = tagPlayer,
            ttsPlayer = CompletableDeferred(ttsPlayer),
            mediaErrorListener = mockk(),
        )
    cardMediaPlayer.setOnMediaGroupCompletedListener(onMediaGroupCompleted)
    assertThat("can play sounds", cardMediaPlayer.isEnabled)
    setup(questions, answers, replayQuestion, autoplay)
    testBody(cardMediaPlayer)
}
