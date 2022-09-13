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
import com.ichi2.anki.AbstractFlashcardViewer.Companion.EASE_1
import com.ichi2.anki.AbstractFlashcardViewer.Companion.EASE_2
import com.ichi2.anki.AbstractFlashcardViewer.Companion.EASE_3
import com.ichi2.anki.AbstractFlashcardViewer.Companion.EASE_4
import com.ichi2.anki.cardviewer.Gesture
import com.ichi2.anki.reviewer.ReviewerUi.ControlBlock
import com.ichi2.anki.servicelayer.AnkiMethod
import com.ichi2.anki.servicelayer.SchedulerService.*
import com.ichi2.libanki.Card
import com.ichi2.utils.Computation
import com.ichi2.utils.KotlinCleanup
import kotlinx.coroutines.Job
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers
import org.mockito.Mockito
import timber.log.Timber
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.fail

@KotlinCleanup("change `when` to whenever(); remove `protected` modifiers")
@RunWith(AndroidJUnit4::class)
class ReviewerKeyboardInputTest : RobolectricTest() {
    @Test
    fun whenDisplayingQuestionTyping1DoesNothing() {
        val underTest = KeyboardInputTestReviewer.displayingQuestion()
        underTest.handleAndroidKeyPress(KEYCODE_1)
        assertFalse(underTest.didDisplayAnswer(), "Answer should not be displayed")
        assertFalse(underTest.hasBeenAnswered(), "Answer should not be performed")
    }

    @Test
    fun whenDisplayingAnswerTyping1AnswersFarLeftButton() {
        val underTest = KeyboardInputTestReviewer.displayingAnswer()
        underTest.handleAndroidKeyPress(KEYCODE_1)
        assertEquals(underTest.processedAnswer(), EASE_1)
    }

    @Test
    fun whenDisplayingAnswerTyping2AnswersSecondButton() {
        val underTest = KeyboardInputTestReviewer.displayingAnswer()
        underTest.handleAndroidKeyPress(KEYCODE_2)
        assertEquals(underTest.processedAnswer(), EASE_2)
    }

    @Test
    fun whenDisplayingAnswerTyping3AnswersThirdButton() {
        val underTest = KeyboardInputTestReviewer.displayingAnswer()
        underTest.handleAndroidKeyPress(KEYCODE_3)
        assertEquals(underTest.processedAnswer(), EASE_3)
    }

    @Test
    fun whenDisplayingAnswerTyping4AnswersFarRightButton() {
        val underTest = KeyboardInputTestReviewer.displayingAnswer()
        underTest.handleAndroidKeyPress(KEYCODE_4)
        assertEquals(underTest.processedAnswer(), EASE_4)
    }

    /** START: DEFAULT IS "GOOD"  */
    @Test
    fun spaceAnswersThirdButtonWhenFourButtonsShowing() {
        val underTest = KeyboardInputTestReviewer.displayingAnswer().withButtons(4)
        underTest.handleSpacebar()
        assertEquals(underTest.processedAnswer(), EASE_3)
    }

    @Test
    fun spaceAnswersSecondButtonWhenThreeButtonsShowing() {
        val underTest = KeyboardInputTestReviewer.displayingAnswer().withButtons(3)
        underTest.handleSpacebar()
        assertEquals(underTest.processedAnswer(), EASE_2)
    }

    @Test
    fun spaceAnswersSecondButtonWhenTwoButtonsShowing() {
        val underTest = KeyboardInputTestReviewer.displayingAnswer().withButtons(2)
        underTest.handleSpacebar()
        assertEquals(underTest.processedAnswer(), EASE_2)
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
        assertTrue(underTest.editCardCalled, "Edit Card was called")
    }

    @Test
    fun pressingStarWillMarkCard() {
        val underTest = KeyboardInputTestReviewer.displayingAnswer()
        underTest.currentCard = addNoteUsingBasicModel("a", "").firstCard()
        underTest.handleUnicodeKeyPress('*')
        assertTrue(underTest.markCardCalled, "Mark Card was called")
    }

    @Test
    fun pressingEqualsWillBuryNote() {
        val underTest = KeyboardInputTestReviewer.displayingAnswer()
        underTest.currentCard = addNoteUsingBasicModel("a", "").firstCard()
        underTest.handleUnicodeKeyPress('=')
        assertTrue(underTest.buryNoteCalled, "Bury Note should be called")
    }

    @Test
    fun pressingAtWillSuspendCard() {
        val underTest = KeyboardInputTestReviewer.displayingAnswer()
        underTest.currentCard = addNoteUsingBasicModel("a", "").firstCard()
        underTest.handleUnicodeKeyPress('@')
        assertTrue(underTest.suspendCardCalled, "Suspend Card should be called")
    }

    @Test
    fun pressingExclamationWillSuspendNote() {
        val underTest = KeyboardInputTestReviewer.displayingAnswer()
        underTest.currentCard = addNoteUsingBasicModel("a", "").firstCard()
        underTest.handleUnicodeKeyPress('!')
        assertTrue(underTest.suspendNoteCalled, "Suspend Note should be called")
    }

    @Test
    fun pressingRShouldReplayAudio() {
        val underTest = KeyboardInputTestReviewer.displayingAnswer()
        underTest.handleAndroidKeyPress(KEYCODE_R)
        assertTrue(underTest.replayAudioCalled, "Replay Audio should be called")
    }

    @Test
    fun pressingF5ShouldReplayAudio() {
        val underTest = KeyboardInputTestReviewer.displayingAnswer()
        underTest.handleKeyPress(KEYCODE_F5, '\u0000')
        assertTrue(underTest.replayAudioCalled, "Replay Audio should be called")
    }

    @Test
    fun pressingZShouldUndoIfAvailable() {
        val underTest = KeyboardInputTestReviewer.displayingAnswer().withUndoAvailable(true)
        underTest.handleAndroidKeyPress(KEYCODE_Z)
        assertTrue(underTest.undoCalled, "Undo should be called")
    }

    @Test
    fun pressingZShouldNotUndoIfNotAvailable() {
        val underTest = KeyboardInputTestReviewer.displayingAnswer().withUndoAvailable(false)
        underTest.handleUnicodeKeyPress('z')
        assertFalse(underTest.undoCalled, "Undo is not available so should not be called")
    }

    @Test
    fun pressingSpaceShouldDoNothingIfFocused() {
        val underTest = KeyboardInputTestReviewer.displayingQuestion().focusTextField()
        underTest.handleSpacebar()
        assertFalse(
            underTest.didDisplayAnswer(),
            "When text field is focused, space should not display answer",
        )
    }

    @Test
    fun pressingUndoDoesNothingIfControlsAreBlocked() {
        // We pick an arbitrary action to ensure that nothing happens if controls are blocked
        val underTest = KeyboardInputTestReviewer.displayingQuestion()
            .withUndoAvailable(true)
            .withControlsBlocked(ControlBlock.SLOW)
        underTest.handleUnicodeKeyPress('z')
        assertFalse(underTest.undoCalled, "Undo should not be called as control are blocked")
    }

    private fun assertGamepadButtonAnswers(keycodeButton: Int, ease: Int) {
        val underTest = KeyboardInputTestReviewer.displayingQuestion()
        assertFalse(underTest.didDisplayAnswer(), "Assume: Initially should not display answer")
        underTest.handleGamepadPress(keycodeButton)
        assertTrue(underTest.didDisplayAnswer(), "Initial button should display answer")
        underTest.displayAnswerForTest()
        underTest.handleGamepadPress(keycodeButton)
        assertEquals(underTest.processedAnswer(), ease)
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
            displayAnswer = true
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
                fail("No card was answered")
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

        override fun undo(): Job? {
            undoCalled = true
            return null
        }

        val suspendNoteCalled: Boolean
            get() = mDismissType is SuspendNote
        val buryNoteCalled: Boolean
            get() = mDismissType is BuryNote

        override fun dismiss(dismiss: AnkiMethod<Computation<NextCard<*>>>, executeAfter: Runnable): Boolean {
            mDismissType = dismiss
            return true
        }

        override fun editCard(fromGesture: Gesture?) {
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
                displayAnswer = true
                keyboardInputTestReviewer.mProcessor.setup()
                return keyboardInputTestReviewer
            }

            @CheckResult
            fun displayingQuestion(): KeyboardInputTestReviewer {
                val keyboardInputTestReviewer = KeyboardInputTestReviewer()
                displayAnswer = false
                keyboardInputTestReviewer.mProcessor.setup()
                return keyboardInputTestReviewer
            }
        }
    }
}
