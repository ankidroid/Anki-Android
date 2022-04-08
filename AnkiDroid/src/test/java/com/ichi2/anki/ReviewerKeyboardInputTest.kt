/*
 Copyright (c) 2020 David Allison <davidallisongithub@gmail.com>

 This program is free software; you can redistribute it and/or modify it under
 the terms of the GNU General Public License as published by the Free Software
 Foundation; either version 3 of the License, or (at your option) any later
 version.

 This program is distributed in the hope that it will be useful, but WITHOUT ANY
 WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 PARTICULAR PURPOSE. See the GNU General Public License for more details.

 You should have received a copy of the GNU General Public License along with
 this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.ichi2.anki

import android.view.KeyEvent
import android.view.KeyEvent.*
import androidx.annotation.CheckResult
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ibm.icu.impl.Assert
import com.ichi2.anki.AbstractFlashcardViewer.Companion.EASE_1
import com.ichi2.anki.AbstractFlashcardViewer.Companion.EASE_2
import com.ichi2.anki.AbstractFlashcardViewer.Companion.EASE_3
import com.ichi2.anki.AbstractFlashcardViewer.Companion.EASE_4
import com.ichi2.anki.reviewer.ReviewerUi.ControlBlock
import com.ichi2.anki.servicelayer.AnkiMethod
import com.ichi2.anki.servicelayer.SchedulerService.BuryNote
import com.ichi2.anki.servicelayer.SchedulerService.NextCard
import com.ichi2.anki.servicelayer.SchedulerService.SuspendCard
import com.ichi2.anki.servicelayer.SchedulerService.SuspendNote
import com.ichi2.libanki.Card
import com.ichi2.utils.Computation
import com.ichi2.utils.KotlinCleanup
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers
import org.mockito.Mockito
import timber.log.Timber
import java.lang.Exception

@KotlinCleanup("change `when` to whenever(); remove `protected` modifiers")
@RunWith(AndroidJUnit4::class)
class ReviewerKeyboardInputTest : RobolectricTest() {
    @Test
    fun whenDisplayingQuestionTyping1DoesNothing() {
        val underTest = KeyboardInputTestReviewer.displayingQuestion()
        underTest.handleAndroidKeyPress(KEYCODE_1)
        assertThat("Answer should not be displayed", !underTest.didDisplayAnswer())
        assertThat("Answer should not be performed", !underTest.hasBeenAnswered())
    }

    @Test
    fun whenDisplayingAnswerTyping1AnswersFarLeftButton() {
        val underTest = KeyboardInputTestReviewer.displayingAnswer()
        underTest.handleAndroidKeyPress(KEYCODE_1)
        assertThat(underTest.processedAnswer(), equalTo(EASE_1))
    }

    @Test
    fun whenDisplayingAnswerTyping2AnswersSecondButton() {
        val underTest = KeyboardInputTestReviewer.displayingAnswer()
        underTest.handleAndroidKeyPress(KEYCODE_2)
        assertThat(underTest.processedAnswer(), equalTo(EASE_2))
    }

    @Test
    fun whenDisplayingAnswerTyping3AnswersThirdButton() {
        val underTest = KeyboardInputTestReviewer.displayingAnswer()
        underTest.handleAndroidKeyPress(KEYCODE_3)
        assertThat(underTest.processedAnswer(), equalTo(EASE_3))
    }

    @Test
    fun whenDisplayingAnswerTyping4AnswersFarRightButton() {
        val underTest = KeyboardInputTestReviewer.displayingAnswer()
        underTest.handleAndroidKeyPress(KEYCODE_4)
        assertThat(underTest.processedAnswer(), equalTo(EASE_4))
    }

    /** START: DEFAULT IS "GOOD"  */
    @Test
    fun spaceAnswersThirdButtonWhenFourButtonsShowing() {
        val underTest = KeyboardInputTestReviewer.displayingAnswer().withButtons(4)
        underTest.handleSpacebar()
        assertThat(underTest.processedAnswer(), equalTo(EASE_3))
    }

    @Test
    fun spaceAnswersSecondButtonWhenThreeButtonsShowing() {
        val underTest = KeyboardInputTestReviewer.displayingAnswer().withButtons(3)
        underTest.handleSpacebar()
        assertThat(underTest.processedAnswer(), equalTo(EASE_2))
    }

    @Test
    fun spaceAnswersSecondButtonWhenTwoButtonsShowing() {
        val underTest = KeyboardInputTestReviewer.displayingAnswer().withButtons(2)
        underTest.handleSpacebar()
        assertThat(underTest.processedAnswer(), equalTo(EASE_2))
    }

    /** END: DEFAULT IS "GOOD"  */
    @Test
    fun gamepadAAnswerFourthButtonOrShowsAnswer() {
        assertGamepadButtonAnswers(KEYCODE_BUTTON_A, EASE_4)
    }

    @Test
    fun gamepadBAnswersThirdButtonOrShowsAnswer() {
        assertGamepadButtonAnswers(KEYCODE_BUTTON_B, EASE_3)
    }

    @Test
    fun gamepadXAnswersSecondButtonOrShowsAnswer() {
        assertGamepadButtonAnswers(KEYCODE_BUTTON_X, EASE_2)
    }

    @Test
    fun gamepadYAnswersFirstButtonOrShowsAnswer() {
        assertGamepadButtonAnswers(KEYCODE_BUTTON_Y, EASE_1)
    }

    @Test
    fun pressingEWillEditCard() {
        val underTest = KeyboardInputTestReviewer.displayingAnswer()
        underTest.handleAndroidKeyPress(KEYCODE_E)
        assertThat("Edit Card was called", underTest.editCardCalled)
    }

    @Test
    fun pressingStarWillMarkCard() {
        val underTest = KeyboardInputTestReviewer.displayingAnswer()
        underTest.currentCard = addNoteUsingBasicModel("a", "").firstCard()
        underTest.handleUnicodeKeyPress('*')
        assertThat("Mark Card was called", underTest.markCardCalled)
    }

    @Test
    fun pressingEqualsWillBuryNote() {
        val underTest = KeyboardInputTestReviewer.displayingAnswer()
        underTest.currentCard = addNoteUsingBasicModel("a", "").firstCard()
        underTest.handleUnicodeKeyPress('=')
        assertThat("Bury Note should be called", underTest.buryNoteCalled)
    }

    @Test
    fun pressingAtWillSuspendCard() {
        val underTest = KeyboardInputTestReviewer.displayingAnswer()
        underTest.currentCard = addNoteUsingBasicModel("a", "").firstCard()
        underTest.handleUnicodeKeyPress('@')
        assertThat("Suspend Card should be called", underTest.suspendCardCalled)
    }

    @Test
    fun pressingExclamationWillSuspendNote() {
        val underTest = KeyboardInputTestReviewer.displayingAnswer()
        underTest.currentCard = addNoteUsingBasicModel("a", "").firstCard()
        underTest.handleUnicodeKeyPress('!')
        assertThat("Suspend Note should be called", underTest.suspendNoteCalled)
    }

    @Test
    fun pressingRShouldReplayAudio() {
        val underTest = KeyboardInputTestReviewer.displayingAnswer()
        underTest.handleAndroidKeyPress(KEYCODE_R)
        assertThat("Replay Audio should be called", underTest.replayAudioCalled)
    }

    @Test
    fun pressingF5ShouldReplayAudio() {
        val underTest = KeyboardInputTestReviewer.displayingAnswer()
        underTest.handleKeyPress(KEYCODE_F5, '\u0000')
        assertThat("Replay Audio should be called", underTest.replayAudioCalled)
    }

    @Test
    fun pressingZShouldUndoIfAvailable() {
        val underTest = KeyboardInputTestReviewer.displayingAnswer().withUndoAvailable(true)
        underTest.handleAndroidKeyPress(KEYCODE_Z)
        assertThat("Undo should be called", underTest.undoCalled)
    }

    @Test
    fun pressingZShouldNotUndoIfNotAvailable() {
        val underTest = KeyboardInputTestReviewer.displayingAnswer().withUndoAvailable(false)
        underTest.handleUnicodeKeyPress('z')
        assertThat("Undo is not available so should not be called", !underTest.undoCalled)
    }

    @Test
    fun pressingSpaceShouldDoNothingIfFocused() {
        val underTest = KeyboardInputTestReviewer.displayingQuestion().focusTextField()
        underTest.handleSpacebar()
        assertThat(
            "When text field is focused, space should not display answer",
            !underTest.didDisplayAnswer()
        )
    }

    @Test
    fun pressingUndoDoesNothingIfControlsAreBlocked() {
        // We pick an arbitrary action to ensure that nothing happens if controls are blocked
        val underTest = KeyboardInputTestReviewer.displayingQuestion()
            .withUndoAvailable(true)
            .withControlsBlocked(ControlBlock.SLOW)
        underTest.handleUnicodeKeyPress('z')
        assertThat("Undo should not be called as control are blocked", !underTest.undoCalled)
    }

    private fun assertGamepadButtonAnswers(keycodeButton: Int, ease: Int) {
        val underTest = KeyboardInputTestReviewer.displayingQuestion()
        assertThat("Assume: Initially should not display answer", !underTest.didDisplayAnswer())
        underTest.handleGamepadPress(keycodeButton)
        assertThat("Initial button should display answer", underTest.didDisplayAnswer())
        underTest.displayAnswerForTest()
        underTest.handleGamepadPress(keycodeButton)
        assertThat(underTest.processedAnswer(), equalTo(ease))
    }

    internal class KeyboardInputTestReviewer : Reviewer() {
        private var mDisplayAnswer = false
        private var mFocusTextField = false
        private var mAnswered: Int? = null
        private var mAnswerButtonCount = 4
        var editCardCalled = false
            private set
        var markCardCalled = false
            private set
        private var mDismissType: AnkiMethod<Computation<NextCard<*>>>? = null
        var undoCalled = false
            private set
        var replayAudioCalled = false
            private set
        override var controlBlocked = ControlBlock.UNBLOCKED
        private var mUndoAvailable = false
        fun withControlsBlocked(value: ControlBlock): KeyboardInputTestReviewer {
            controlBlocked = value
            return this
        }

        fun displayAnswerForTest() {
            sDisplayAnswer = true
        }

        override fun answerFieldIsFocused(): Boolean {
            return mFocusTextField
        }

        override fun displayCardAnswer() {
            mDisplayAnswer = true
        }

        fun didDisplayAnswer(): Boolean {
            return mDisplayAnswer
        }

        fun handleUnicodeKeyPress(unicodeChar: Char) {
            val key = mockKeyEvent
            // COULD_BE_BETTER: We do not handle shift
            Mockito.`when`(key.getUnicodeChar(ArgumentMatchers.anyInt())).thenReturn(unicodeChar.code)
            try {
                Mockito.`when`(key.action).thenReturn(ACTION_DOWN)
                onKeyDown(0, key)
            } catch (e: Exception) {
                Timber.e(e)
            }
            try {
                Mockito.`when`(key.action).thenReturn(ACTION_UP)
                onKeyUp(0, key)
            } catch (e: Exception) {
                Timber.e(e)
            }
        }

        fun handleKeyPress(keycode: Int, unicodeChar: Char) {
            // COULD_BE_BETTER: Saves 20 seconds on tests to remove AndroidJUnit4,
            // but may let something slip through the cracks.
            val e = mockKeyEvent
            // COULD_BE_BETTER: We do not handle shift
            Mockito.`when`(e.getUnicodeChar(ArgumentMatchers.anyInt())).thenReturn(unicodeChar.code)
            Mockito.`when`(e.action).thenReturn(ACTION_DOWN)
            Mockito.`when`(e.keyCode).thenReturn(keycode)
            try {
                onKeyDown(keycode, e)
            } catch (ex: Exception) {
                Timber.e(ex)
            }
            Mockito.`when`(e.action).thenReturn(ACTION_UP)
            try {
                onKeyUp(keycode, e)
            } catch (ex: Exception) {
                Timber.e(ex)
            }
        }

        protected val mockKeyEvent: KeyEvent
            get() {
                val key = Mockito.mock(KeyEvent::class.java)
                Mockito.`when`(key.isShiftPressed).thenReturn(false)
                Mockito.`when`(key.isCtrlPressed).thenReturn(false)
                Mockito.`when`(key.isAltPressed).thenReturn(false)
                return key
            }

        // useful to obtain unicode for keycode if run under AndroidJUnit4.
        fun handleAndroidKeyPress(keycode: Int) {
            try {
                onKeyDown(keycode, createKeyEvent(ACTION_DOWN, keycode))
            } catch (ex: Exception) {
                Timber.e(ex)
            }
            try {
                onKeyUp(keycode, createKeyEvent(ACTION_UP, keycode))
            } catch (ex: Exception) {
                Timber.e(ex)
            }
        }

        protected fun createKeyEvent(action: Int, keycode: Int): KeyEvent {
            val keyEvent = Mockito.mock(KeyEvent::class.java)
            Mockito.`when`(keyEvent.keyCode).thenReturn(keycode)
            Mockito.`when`(keyEvent.action).thenReturn(action)
            Mockito.`when`(keyEvent.isShiftPressed).thenReturn(false)
            Mockito.`when`(keyEvent.isCtrlPressed).thenReturn(false)
            Mockito.`when`(keyEvent.isAltPressed).thenReturn(false)
            return keyEvent
        }

        override fun setTitle() {
            // required for interface. Intentionally left blank
        }

        fun focusTextField(): KeyboardInputTestReviewer {
            mFocusTextField = true
            return this
        }

        override val answerButtonCount: Int
            get() = mAnswerButtonCount

        override fun answerCard(ease: Int) {
            mAnswered = ease
        }

        fun processedAnswer(): Int {
            if (mAnswered == null) {
                Assert.fail("No card was answered")
            }
            return mAnswered!!
        }

        fun withButtons(answerButtonCount: Int): KeyboardInputTestReviewer {
            mAnswerButtonCount = answerButtonCount
            return this
        }

        fun handleSpacebar() {
            handleKeyPress(KEYCODE_SPACE, ' ')
        }

        fun handleGamepadPress(buttonCode: Int) {
            // Tested under Robolectric - unicode is null
            handleKeyPress(buttonCode, '\u0000')
        }

        override fun undo() {
            undoCalled = true
        }

        val suspendNoteCalled: Boolean
            get() = mDismissType is SuspendNote
        val buryNoteCalled: Boolean
            get() = mDismissType is BuryNote

        override fun dismiss(dismiss: AnkiMethod<Computation<NextCard<*>>>, executeAfter: Runnable): Boolean {
            mDismissType = dismiss
            return true
        }

        override fun editCard() {
            editCardCalled = true
        }

        override fun onMark(card: Card?) {
            markCardCalled = true
        }

        val suspendCardCalled: Boolean
            get() = mDismissType is SuspendCard

        override fun playSounds(doAudioReplay: Boolean) {
            replayAudioCalled = true
        }

        override val isUndoAvailable: Boolean
            get() = mUndoAvailable

        fun withUndoAvailable(value: Boolean): KeyboardInputTestReviewer {
            mUndoAvailable = value
            return this
        }

        fun hasBeenAnswered(): Boolean {
            return mAnswered != null
        }

        override fun performClickWithVisualFeedback(ease: Int) {
            answerCard(ease)
        }

        companion object {
            @CheckResult
            fun displayingAnswer(): KeyboardInputTestReviewer {
                val keyboardInputTestReviewer = KeyboardInputTestReviewer()
                sDisplayAnswer = true
                keyboardInputTestReviewer.mProcessor.setup()
                return keyboardInputTestReviewer
            }

            @CheckResult
            fun displayingQuestion(): KeyboardInputTestReviewer {
                val keyboardInputTestReviewer = KeyboardInputTestReviewer()
                sDisplayAnswer = false
                keyboardInputTestReviewer.mProcessor.setup()
                return keyboardInputTestReviewer
            }
        }
    }
}
