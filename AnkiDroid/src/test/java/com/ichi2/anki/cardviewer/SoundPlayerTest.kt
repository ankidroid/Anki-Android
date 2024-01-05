/*
 *  Copyright (c) 2023 David Allison <davidallisongithub@gmail.com>
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

package com.ichi2.anki.cardviewer

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.CardUtils
import com.ichi2.anki.IntroductionActivity
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.cardviewer.Side.BACK
import com.ichi2.anki.cardviewer.SoundErrorBehavior.*
import com.ichi2.libanki.AvTag
import com.ichi2.libanki.SoundOrVideoTag
import com.ichi2.libanki.TemplateManager
import com.ichi2.libanki.TtsPlayer
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
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SoundPlayerTest : RobolectricTest() {
    internal val tagPlayer: SoundTagPlayer = mockk<SoundTagPlayer>()
    internal val ttsPlayer: TtsPlayer = mockk<TtsPlayer>()
    internal val onSoundGroupCompleted: () -> Unit = mockk<() -> Unit>().also {
        every { it.invoke() } answers { }
    }

    @Test
    fun `no sounds fires completed listener`() = runSoundPlayerTest(
        answers = emptyList(),
        questions = emptyList()
    ) {
        playAllSoundsAndWait()

        verifyNoSoundsPlayed()
    }

    @Test
    fun singleSoundSuccess() = runSoundPlayerTest(
        questions = listOf(SoundOrVideoTag("abc.mp3")),
        side = Side.FRONT
    ) {
        playAllSoundsAndWait()

        coVerify(exactly = 1) { tagPlayer.play(SoundOrVideoTag("abc.mp3"), any()) }
        coVerify(exactly = 0) { ttsPlayer.play(any()) }
        ensureOnSoundGroupCompletedCalled()
    }

    @Test
    fun `back is not played on front`() = runSoundPlayerTest(
        answers = listOf(SoundOrVideoTag("abc.mp3")),
        side = Side.FRONT
    ) {
        playAllSoundsAndWait()

        verifyNoSoundsPlayed()
    }

    @Test
    fun `front is not played on back`() = runSoundPlayerTest(
        questions = listOf(SoundOrVideoTag("abc.mp3")),
        side = BACK
    ) {
        playAllSoundsAndWait()

        verifyNoSoundsPlayed()
    }

    @Test
    fun `replay - front may be played on back`() = runSoundPlayerTest(
        questions = listOf(SoundOrVideoTag("front.mp3")),
        answers = listOf(SoundOrVideoTag("back.mp3")),
        replayQuestion = true,
        side = BACK
    ) {
        replayAllSoundsAndWait()

        coVerifyOrder {
            tagPlayer.play(SoundOrVideoTag("front.mp3"), any())
            tagPlayer.play(SoundOrVideoTag("back.mp3"), any())
        }
    }

    @Test
    fun `replay when replayQuestion is false`() = runSoundPlayerTest(
        questions = listOf(SoundOrVideoTag("front.mp3")),
        answers = listOf(SoundOrVideoTag("back.mp3")),
        replayQuestion = false,
        side = BACK
    ) {
        replayAllSoundsAndWait()

        coVerifyOrder {
            tagPlayer.play(SoundOrVideoTag("back.mp3"), any())
        }
    }

    @Test
    fun `onSoundGroupCompleted is called after exception`() = runSoundPlayerTest(
        questions = listOf(SoundOrVideoTag("aa.mp3"))
    ) {
        coEvery { tagPlayer.play(any(), any()) } throws TestException("test")

        playAllSoundsAndWait()

        coVerify(exactly = 1) { tagPlayer.play(any(), any()) }
        ensureOnSoundGroupCompletedCalled()
    }

    @Test
    fun `replay calls play twice`() = runSoundPlayerTest(
        questions = listOf(SoundOrVideoTag("aa.mp3"), SoundOrVideoTag("bb.mp3"))
    ) {
        coEvery { tagPlayer.play(any(), any()) } throws SoundException(RETRY_AUDIO)

        playAllSoundsAndWait()

        coVerifySequence {
            tagPlayer.play(SoundOrVideoTag("aa.mp3"), any())
            tagPlayer.play(SoundOrVideoTag("aa.mp3"), any())
            tagPlayer.play(SoundOrVideoTag("bb.mp3"), any())
            tagPlayer.play(SoundOrVideoTag("bb.mp3"), any())
        }

        ensureOnSoundGroupCompletedCalled()
    }

    @Test
    fun `stop stops playback and calls completed listener`() = runSoundPlayerTest(
        questions = listOf(SoundOrVideoTag("aa.mp3"), SoundOrVideoTag("bb.mp3"))
    ) {
        coEvery { tagPlayer.play(any(), any()) } throws SoundException(STOP_AUDIO)

        playAllSoundsAndWait()

        coVerifySequence {
            tagPlayer.play(SoundOrVideoTag("aa.mp3"), any())
        }

        ensureOnSoundGroupCompletedCalled()
    }

    @Test
    fun `continue continues playback and calls completed listener`() = runSoundPlayerTest(
        questions = listOf(SoundOrVideoTag("aa.mp3"), SoundOrVideoTag("bb.mp3"))
    ) {
        coEvery { tagPlayer.play(any(), any()) } throws SoundException(CONTINUE_AUDIO)

        playAllSoundsAndWait()

        coVerifySequence {
            tagPlayer.play(SoundOrVideoTag("aa.mp3"), any())
            tagPlayer.play(SoundOrVideoTag("bb.mp3"), any())
        }

        ensureOnSoundGroupCompletedCalled()
    }

    @Test
    fun `retry playing single sound`() = runSoundPlayerTest {
        coEvery { tagPlayer.play(any(), any()) } throws SoundException(RETRY_AUDIO)

        playOneSoundAndWait(SoundOrVideoTag("a.mp3"))

        coVerifySequence {
            tagPlayer.play(SoundOrVideoTag("a.mp3"), any())
            tagPlayer.play(SoundOrVideoTag("a.mp3"), any())
        }
    }

    private fun verifyNoSoundsPlayed() {
        coVerify(exactly = 0) { tagPlayer.play(any(), any()) }
        coVerify(exactly = 0) { ttsPlayer.play(any()) }
        ensureOnSoundGroupCompletedCalled()
    }

    private fun ensureOnSoundGroupCompletedCalled() {
        verify(exactly = 1) { onSoundGroupCompleted.invoke() }
    }

    private suspend fun SoundPlayer.playAllSoundsAndWait() {
        this.playAllSounds()?.join()
    }

    private suspend fun SoundPlayer.replayAllSoundsAndWait() {
        this.replayAllSounds()?.join()
    }

    private suspend fun SoundPlayer.playOneSoundAndWait(tag: AvTag) {
        playOneSound(tag)?.join()
    }

    suspend fun SoundPlayer.setup(
        questions: List<AvTag>,
        answers: List<AvTag>,
        side: Side,
        replayQuestion: Boolean?,
        autoplay: Boolean?
    ) {
        val card = addNoteUsingBasicModel().firstCard()
        mockkObject(card)

        every { card.renderOutput() } answers {
            TemplateManager.TemplateRenderContext.TemplateRenderOutput(
                questionText = "",
                answerText = "",
                questionAvTags = questions,
                answerAvTags = answers,
                css = ""
            )
        }

        if (replayQuestion != null) {
            updateDeckConfig(CardUtils.getDeckIdForCard(card)) {
                put("replayq", replayQuestion)
            }
        }
        if (autoplay != null) {
            updateDeckConfig(CardUtils.getDeckIdForCard(card)) {
                put("autoplay", autoplay)
            }
        }

        this.loadCardSounds(card, side)
    }
}

/**
 *
 * @param autoplay [CardSoundConfig.autoplay]
 * @param replayQuestion [CardSoundConfig.replayQuestion]
 */
fun SoundPlayerTest.runSoundPlayerTest(
    questions: List<AvTag> = emptyList(),
    answers: List<AvTag> = emptyList(),
    side: Side = Side.FRONT,
    replayQuestion: Boolean? = null,
    autoplay: Boolean? = null,
    testBody: suspend SoundPlayer.() -> Unit
) =
    runTest {
        // PERF: See if we can make this faster
        val lifecycle = startRegularActivity<IntroductionActivity>().lifecycle
        // val lifecycle = mockk<Lifecycle>()
        // every { lifecycle.currentState } answers { Lifecycle.State.RESUMED }

        val soundPlayer = SoundPlayer(
            soundTagPlayer = tagPlayer,
            ttsPlayer = CompletableDeferred(ttsPlayer),
            lifecycle = lifecycle,
            onSoundGroupCompleted = onSoundGroupCompleted,
            soundErrorListener = mockk()
        )
        assertThat("can play sounds", soundPlayer.canPlaySounds())
        soundPlayer.setup(questions, answers, side, replayQuestion, autoplay)
        testBody(soundPlayer)
    }
