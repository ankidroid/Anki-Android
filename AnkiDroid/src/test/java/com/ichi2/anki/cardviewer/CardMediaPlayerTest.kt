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
import com.ichi2.testutils.withQueuedCollectionAccess
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

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
    fun `stopping playback does not call completed listener`() =
        runSoundPlayerTest(
            questions = listOf(SoundOrVideoTag("aa.mp3")),
        ) {
            val started = CompletableDeferred<Unit>()
            coEvery { tagPlayer.play(any(), any()) } coAnswers {
                started.complete(Unit)
                awaitCancellation()
            }

            playAllForSide(card, CardSide.QUESTION)
            started.await()
            stop()

            verify(exactly = 0) { onMediaGroupCompleted.invoke() }
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
    fun `playing one sound over the side's media calls completed listener`() =
        runSoundPlayerTest(
            questions = listOf(SoundOrVideoTag("front.mp3")),
        ) {
            val started = CompletableDeferred<Unit>()
            coEvery { tagPlayer.play(SoundOrVideoTag("front.mp3"), any()) } coAnswers {
                started.complete(Unit)
                awaitCancellation()
            }
            coEvery { tagPlayer.play(SoundOrVideoTag("one.mp3"), any()) } returns Unit
            playAllForSide(card, CardSide.QUESTION)
            started.await()

            playOneAndWait(SoundOrVideoTag("one.mp3"))

            ensureOnMediaGroupCompletedCalled()
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

    @Test
    fun `stopping while replay looks up tags prevents that replay`() =
        runTest {
            val front = SoundOrVideoTag("front.mp3")
            val card = addBasicNote("[sound:front.mp3]").firstCard()
            val player = CardMediaPlayer(tagPlayer, CompletableDeferred(ttsPlayer), mockk())
            val stopReturned = AtomicBoolean()
            val playedAfterStop = AtomicBoolean()
            coEvery { tagPlayer.play(front, any()) } coAnswers { playedAfterStop.set(stopReturned.get()) }

            withQueuedCollectionAccess {
                val replay = launch(start = CoroutineStart.UNDISPATCHED) { player.replayAll(card, SingleCardSide.FRONT) }
                player.stop()
                stopReturned.set(true)
                replay.join()
                player.awaitIdle()
            }

            assertFalse(playedAfterStop.get(), "the replay started after stop returned")
        }

    @Test
    fun `stopping while side playback looks up tags prevents that playback`() =
        runTest {
            val front = SoundOrVideoTag("front.mp3")
            val card = addBasicNote("[sound:front.mp3]").firstCard()
            val player = CardMediaPlayer(tagPlayer, CompletableDeferred(ttsPlayer), mockk())
            val stopReturned = AtomicBoolean()
            val playedAfterStop = AtomicBoolean()
            coEvery { tagPlayer.play(front, any()) } coAnswers { playedAfterStop.set(stopReturned.get()) }

            withQueuedCollectionAccess {
                val playback = launch(start = CoroutineStart.UNDISPATCHED) { player.playAllForSide(card, CardSide.QUESTION) }
                player.stop()
                stopReturned.set(true)
                playback.join()
                player.awaitIdle()
            }

            assertFalse(playedAfterStop.get(), "the side playback started after stop returned")
        }

    @Test
    fun `playing with no outgoing media dispatches before starting playback`() =
        runTest {
            val player =
                CardMediaPlayer(
                    tagPlayer,
                    CompletableDeferred(ttsPlayer),
                    mockk(),
                    dispatcher = StandardTestDispatcher(testScheduler),
                )
            val tag = SoundOrVideoTag("sound.mp3")
            coEvery { tagPlayer.play(tag, any()) } returns Unit

            player.playOne(tag)

            coVerify(exactly = 0) { tagPlayer.play(any(), any()) }
            runCurrent()
            player.awaitIdle()
            coVerify(exactly = 1) { tagPlayer.play(tag, any()) }
        }

    @Test
    fun `stop waits for outgoing audio when its replacement is cancelled before dispatch`() =
        runTest {
            val player =
                CardMediaPlayer(
                    tagPlayer,
                    CompletableDeferred(ttsPlayer),
                    mockk(),
                    dispatcher = StandardTestDispatcher(testScheduler),
                )
            val finishCleanup = CompletableDeferred<Unit>()
            val outgoing =
                backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) {
                    try {
                        awaitCancellation()
                    } finally {
                        withContext(NonCancellable) { finishCleanup.await() }
                    }
                }
            player.playAvTagsJob = outgoing

            val replacement = launch(start = CoroutineStart.UNDISPATCHED) { player.playOne(SoundOrVideoTag("next.mp3")) }
            val stopping = launch(start = CoroutineStart.UNDISPATCHED) { player.stop() }
            runCurrent()
            try {
                assertFalse(stopping.isCompleted, "stop returned while the outgoing audio was still cleaning up")
                assertTrue(player.isPlaying, "the player became idle before the outgoing audio stopped")
            } finally {
                finishCleanup.complete(Unit)
            }
            replacement.join()
            stopping.join()

            coVerify(exactly = 0) { tagPlayer.play(any(), any()) }
        }

    @Test
    fun `stop waits for outgoing audio while a replacement is pending`() =
        runSoundPlayerTest {
            val started = CompletableDeferred<Unit>()
            val cleanupStarted = CompletableDeferred<Unit>()
            val finishCleanup = CompletableDeferred<Unit>()
            coEvery { tagPlayer.play(SoundOrVideoTag("first.mp3"), any()) } coAnswers {
                started.complete(Unit)
                try {
                    awaitCancellation()
                } finally {
                    cleanupStarted.complete(Unit)
                    withContext(NonCancellable) { finishCleanup.await() }
                }
            }
            coEvery { tagPlayer.play(SoundOrVideoTag("second.mp3"), any()) } returns Unit
            playOne(SoundOrVideoTag("first.mp3"))
            started.await()

            coroutineScope {
                launch(start = CoroutineStart.UNDISPATCHED) { playOne(SoundOrVideoTag("second.mp3")) }
                cleanupStarted.await()
                val stopping = launch(start = CoroutineStart.UNDISPATCHED) { stop() }
                try {
                    assertFalse(stopping.isCompleted, "stop returned while the outgoing audio was still cleaning up")
                } finally {
                    finishCleanup.complete(Unit)
                }
            }
        }

    @Test
    fun `a cancelled job set by a test leaves the player idle`() =
        runSoundPlayerTest {
            playAvTagsJob = Job()

            stop()

            assertFalse(isPlaying, "the player is playing a cancelled job")
            withTimeout(1.seconds) { awaitIdle() }
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
