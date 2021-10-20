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

package com.ichi2.anki;

import android.view.KeyEvent;

import com.ichi2.anki.reviewer.ReviewerUi;
import com.ichi2.anki.servicelayer.AnkiMethod;
import com.ichi2.anki.servicelayer.SchedulerService;
import com.ichi2.anki.servicelayer.SchedulerService.NextCard;
import com.ichi2.libanki.Card;
import com.ichi2.utils.Computation;

import org.junit.Test;
import org.junit.runner.RunWith;

import androidx.annotation.CheckResult;
import androidx.annotation.NonNull;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import timber.log.Timber;

import static android.view.KeyEvent.*;
import static com.ibm.icu.impl.Assert.fail;
import static com.ichi2.anki.AbstractFlashcardViewer.EASE_1;
import static com.ichi2.anki.AbstractFlashcardViewer.EASE_2;
import static com.ichi2.anki.AbstractFlashcardViewer.EASE_3;
import static com.ichi2.anki.AbstractFlashcardViewer.EASE_4;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(AndroidJUnit4.class)
public class ReviewerKeyboardInputTest extends RobolectricTest {

    @Test
    public void whenDisplayingQuestionTyping1DoesNothing() {
        KeyboardInputTestReviewer underTest = KeyboardInputTestReviewer.displayingQuestion();

        underTest.handleAndroidKeyPress(KEYCODE_1);

        assertThat("Answer should not be displayed", !underTest.didDisplayAnswer());
        assertThat("Answer should not be performed", !underTest.hasBeenAnswered());
    }

    @Test
    public void whenDisplayingAnswerTyping1AnswersFarLeftButton() {
        KeyboardInputTestReviewer underTest = KeyboardInputTestReviewer.displayingAnswer();

        underTest.handleAndroidKeyPress(KEYCODE_1);

        assertThat(underTest.processedAnswer(), equalTo(EASE_1));
    }

    @Test
    public void whenDisplayingAnswerTyping2AnswersSecondButton() {
        KeyboardInputTestReviewer underTest = KeyboardInputTestReviewer.displayingAnswer();

        underTest.handleAndroidKeyPress(KEYCODE_2);

        assertThat(underTest.processedAnswer(), equalTo(EASE_2));
    }

    @Test
    public void whenDisplayingAnswerTyping3AnswersThirdButton() {
        KeyboardInputTestReviewer underTest = KeyboardInputTestReviewer.displayingAnswer();

        underTest.handleAndroidKeyPress(KEYCODE_3);

        assertThat(underTest.processedAnswer(), equalTo(EASE_3));
    }

    @Test
    public void whenDisplayingAnswerTyping4AnswersFarRightButton() {
        KeyboardInputTestReviewer underTest = KeyboardInputTestReviewer.displayingAnswer();

        underTest.handleAndroidKeyPress(KEYCODE_4);

        assertThat(underTest.processedAnswer(), equalTo(EASE_4));
    }

    /** START: DEFAULT IS "GOOD" */
    @Test
    public void spaceAnswersThirdButtonWhenFourButtonsShowing() {
        KeyboardInputTestReviewer underTest = KeyboardInputTestReviewer.displayingAnswer().withButtons(4);

        underTest.handleSpacebar();

        assertThat(underTest.processedAnswer(), equalTo(EASE_3));
    }

    @Test
    public void spaceAnswersSecondButtonWhenThreeButtonsShowing() {
        KeyboardInputTestReviewer underTest = KeyboardInputTestReviewer.displayingAnswer().withButtons(3);

        underTest.handleSpacebar();

        assertThat(underTest.processedAnswer(), equalTo(EASE_2));

    }

    @Test
    public void spaceAnswersSecondButtonWhenTwoButtonsShowing() {
        KeyboardInputTestReviewer underTest = KeyboardInputTestReviewer.displayingAnswer().withButtons(2);

        underTest.handleSpacebar();

        assertThat(underTest.processedAnswer(), equalTo(EASE_2));
    }

    /** END: DEFAULT IS "GOOD" */

    @Test
    public void gamepadAAnswerFourthButtonOrShowsAnswer() {
        assertGamepadButtonAnswers(KEYCODE_BUTTON_A, EASE_4);
    }

    @Test
    public void gamepadBAnswersThirdButtonOrShowsAnswer() {
        assertGamepadButtonAnswers(KEYCODE_BUTTON_B, EASE_3);
    }

    @Test
    public void gamepadXAnswersSecondButtonOrShowsAnswer() {
        assertGamepadButtonAnswers(KEYCODE_BUTTON_X, EASE_2);
    }


    @Test
    public void gamepadYAnswersFirstButtonOrShowsAnswer() {
        assertGamepadButtonAnswers(KEYCODE_BUTTON_Y, EASE_1);
    }

    @Test
    public void pressingEWillEditCard() {
        KeyboardInputTestReviewer underTest = KeyboardInputTestReviewer.displayingAnswer();

        underTest.handleAndroidKeyPress(KEYCODE_E);

        assertThat("Edit Card was called", underTest.getEditCardCalled());
    }

    @Test
    public void pressingStarWillMarkCard() {
        KeyboardInputTestReviewer underTest = KeyboardInputTestReviewer.displayingAnswer();
        underTest.mCurrentCard = addNoteUsingBasicModel("a", "").firstCard();

        underTest.handleUnicodeKeyPress('*');

        assertThat("Mark Card was called", underTest.getMarkCardCalled());
    }

    @Test
    public void pressingEqualsWillBuryNote() {
        KeyboardInputTestReviewer underTest = KeyboardInputTestReviewer.displayingAnswer();
        underTest.mCurrentCard = addNoteUsingBasicModel("a", "").firstCard();

        underTest.handleUnicodeKeyPress('=');

        assertThat("Bury Note should be calledd", underTest.getBuryNoteCalled());
    }

    @Test
    public void pressingAtWillSuspendCard() {
        KeyboardInputTestReviewer underTest = KeyboardInputTestReviewer.displayingAnswer();
        underTest.mCurrentCard = addNoteUsingBasicModel("a", "").firstCard();

        underTest.handleUnicodeKeyPress('@');

        assertThat("Suspend Card should be called", underTest.getSuspendCardCalled());
    }

    @Test
    public void pressingExclamationWillSuspendNote() {
        KeyboardInputTestReviewer underTest = KeyboardInputTestReviewer.displayingAnswer();
        underTest.mCurrentCard = addNoteUsingBasicModel("a", "").firstCard();

        underTest.handleUnicodeKeyPress('!');

        assertThat("Suspend Note should be called", underTest.getSuspendNoteCalled());
    }

    @Test
    public void pressingRShouldReplayAudio() {
        KeyboardInputTestReviewer underTest = KeyboardInputTestReviewer.displayingAnswer();

        underTest.handleAndroidKeyPress(KEYCODE_R);

        assertThat("Replay Audio should be called", underTest.getReplayAudioCalled());
    }

    @Test
    public void pressingF5ShouldReplayAudio() {
        KeyboardInputTestReviewer underTest = KeyboardInputTestReviewer.displayingAnswer();

        underTest.handleKeyPress(KEYCODE_F5, '\0');

        assertThat("Replay Audio should be called", underTest.getReplayAudioCalled());
    }

    @Test
    public void pressingZShouldUndoIfAvailable() {
        KeyboardInputTestReviewer underTest = KeyboardInputTestReviewer.displayingAnswer().withUndoAvailable(true);

        underTest.handleAndroidKeyPress(KEYCODE_Z);

        assertThat("Undo should be called", underTest.getUndoCalled());
    }

    @Test
    public void pressingZShouldNotUndoIfNotAvailable() {
        KeyboardInputTestReviewer underTest = KeyboardInputTestReviewer.displayingAnswer().withUndoAvailable(false);

        underTest.handleUnicodeKeyPress('z');

        assertThat("Undo is not available so should not be called", !underTest.getUndoCalled());
    }

    @Test
    public void pressingSpaceShouldDoNothingIfFocused() {
        KeyboardInputTestReviewer underTest = KeyboardInputTestReviewer.displayingQuestion().focusTextField();

        underTest.handleSpacebar();

        assertThat("When text field is focused, space should not display answer",
                !underTest.didDisplayAnswer());
    }

    @Test
    public void pressingUndoDoesNothingIfControlsAreBlocked() {
        //We pick an arbitrary action to ensure that nothing happens if controls are blocked
        KeyboardInputTestReviewer underTest = KeyboardInputTestReviewer.displayingQuestion()
                .withUndoAvailable(true)
                .withControlsBlocked(ReviewerUi.ControlBlock.SLOW);

        underTest.handleUnicodeKeyPress('z');

        assertThat("Undo should not be called as control are blocked", !underTest.getUndoCalled());
    }


    private void assertGamepadButtonAnswers(int keycodeButton, int ease) {
        KeyboardInputTestReviewer underTest = KeyboardInputTestReviewer.displayingQuestion();
        assertThat("Assume: Initially should not display answer", !underTest.didDisplayAnswer());

        underTest.handleGamepadPress(keycodeButton);

        assertThat("Initial button should display answer", underTest.didDisplayAnswer());

        underTest.displayAnswerForTest();

        underTest.handleGamepadPress(keycodeButton);

        assertThat(underTest.processedAnswer(), equalTo(ease));
    }


    static class KeyboardInputTestReviewer extends Reviewer {

        private boolean mDisplayAnswer = false;
        private boolean mFocusTextField = false;
        private Integer mAnswered = null;
        private int mAnswerButtonCount = 4;
        private boolean mEditedCard;
        private boolean mMarkedCard;
        private AnkiMethod<Computation<? extends NextCard<?>>> mDismissType;
        private boolean mUndoCalled;
        private boolean mReplayAudioCalled;
        private ControlBlock mControlsAreBlocked = ControlBlock.UNBLOCKED;
        private boolean mUndoAvailable;


        @Override
        public ControlBlock getControlBlocked() {
            return mControlsAreBlocked;
        }

        @CheckResult
        public static KeyboardInputTestReviewer displayingAnswer() {
            KeyboardInputTestReviewer keyboardInputTestReviewer = new KeyboardInputTestReviewer();
            KeyboardInputTestReviewer.sDisplayAnswer = true;
            keyboardInputTestReviewer.mProcessor.setup();
            return keyboardInputTestReviewer;
        }

        @CheckResult
        public static KeyboardInputTestReviewer displayingQuestion() {
            KeyboardInputTestReviewer keyboardInputTestReviewer = new KeyboardInputTestReviewer();
            KeyboardInputTestReviewer.sDisplayAnswer = false;
            keyboardInputTestReviewer.mProcessor.setup();
            return keyboardInputTestReviewer;
        }

        public KeyboardInputTestReviewer withControlsBlocked(ControlBlock value) {
            mControlsAreBlocked = value;
            return this;
        }

        public void displayAnswerForTest() {
            KeyboardInputTestReviewer.sDisplayAnswer = true;
        }


        @Override
        protected boolean answerFieldIsFocused() {
            return mFocusTextField;
        }

        @Override
        protected void displayCardAnswer() {
            mDisplayAnswer = true;
        }

        public boolean didDisplayAnswer() { return mDisplayAnswer; }

        public void handleUnicodeKeyPress(char unicodeChar) {
            KeyEvent key = getMockKeyEvent();
            // COULD_BE_BETTER: We do not handle shift
            when(key.getUnicodeChar(anyInt())).thenReturn((int)unicodeChar);

            try {
                when(key.getAction()).thenReturn(ACTION_DOWN);
                this.onKeyDown(0, key);
            } catch (Exception e) {
                Timber.e(e);
            }
            try {
                when(key.getAction()).thenReturn(ACTION_UP);
                this.onKeyUp(0, key);
            } catch (Exception e) {
                Timber.e(e);
            }
        }
        public void handleKeyPress(int keycode, char unicodeChar) {
            // COULD_BE_BETTER: Saves 20 seconds on tests to remove AndroidJUnit4,
            // but may let something slip through the cracks.
            KeyEvent e = getMockKeyEvent();
            // COULD_BE_BETTER: We do not handle shift
            when(e.getUnicodeChar(anyInt())).thenReturn((int)unicodeChar);
            when(e.getAction()).thenReturn(ACTION_DOWN);
            when(e.getKeyCode()).thenReturn(keycode);

            try {
                this.onKeyDown(keycode, e);
            } catch (Exception ex) {
                Timber.e(ex);
            }
            when(e.getAction()).thenReturn(ACTION_UP);
            try {
                this.onKeyUp(keycode, e);
            } catch (Exception ex) {
                Timber.e(ex);
            }
        }


        protected KeyEvent getMockKeyEvent() {
            KeyEvent key = mock(KeyEvent.class);
            when(key.isShiftPressed()).thenReturn(false);
            when(key.isCtrlPressed()).thenReturn(false);
            when(key.isAltPressed()).thenReturn(false);
            return key;
        }


        @SuppressWarnings({"unused"}) //useful to obtain unicode for kecode if run under AndroidJUnit4.
        public void handleAndroidKeyPress(int keycode) {
            try {
                this.onKeyDown(keycode, createKeyEvent(ACTION_DOWN, keycode));
            } catch (Exception ex) {
                Timber.e(ex);
            }
            try {
                this.onKeyUp(keycode, createKeyEvent(ACTION_UP, keycode));
            } catch (Exception ex) {
                Timber.e(ex);
            }
        }


        @NonNull
        protected KeyEvent createKeyEvent(int action, int keycode) {
            KeyEvent keyEvent = mock(KeyEvent.class);
            when(keyEvent.getKeyCode()).thenReturn(keycode);
            when(keyEvent.getAction()).thenReturn(action);
            when(keyEvent.isShiftPressed()).thenReturn(false);
            when(keyEvent.isCtrlPressed()).thenReturn(false);
            when(keyEvent.isAltPressed()).thenReturn(false);
            return keyEvent;
        }


        @Override protected void setTitle() {
            //required for interface. Intentionally left blank
        }


        public KeyboardInputTestReviewer focusTextField() {
            mFocusTextField = true;
            return this;
        }


        @Override
        protected int getAnswerButtonCount() {
            return this.mAnswerButtonCount;
        }


        @Override
        protected void answerCard(int ease) {
            mAnswered = ease;
        }


        public int processedAnswer() {
            if (mAnswered == null) {
                fail("No card was answered");
            }
            return mAnswered;
        }


        public KeyboardInputTestReviewer withButtons(int answerButtonCount) {
            mAnswerButtonCount = answerButtonCount;
            return this;
        }


        public void handleSpacebar() {
            handleKeyPress(KEYCODE_SPACE, ' ');
        }


        public void handleGamepadPress(int buttonCode) {
            //Tested under Robolectric - unicode is null
            handleKeyPress(buttonCode, '\0');
        }

        @Override
        protected void undo() {
            mUndoCalled = true;
        }
        public boolean getUndoCalled() {
            return mUndoCalled;
        }

        public boolean getSuspendNoteCalled() {
            return mDismissType instanceof SchedulerService.SuspendNote;
        }


        public boolean getBuryNoteCalled() {
            return mDismissType instanceof SchedulerService.BuryNote;
        }


        public boolean getMarkCardCalled() {
            return mMarkedCard;
        }


        public boolean getEditCardCalled() {
            return mEditedCard;
        }

        @Override
        protected boolean dismiss(AnkiMethod<Computation<? extends NextCard<?>>> dismiss, Runnable executeAfter) {
            this.mDismissType = dismiss;
            return true;
        }

        @Override
        protected void editCard() {
            mEditedCard = true;
        }

        @Override
        protected void onMark(Card card) {
            mMarkedCard = true;
        }


        public boolean getSuspendCardCalled() {
            return mDismissType instanceof SchedulerService.SuspendCard;
        }


        @Override
        protected void playSounds(boolean doAudioReplay) {
            mReplayAudioCalled = true;
        }

        public boolean getReplayAudioCalled() {
            return mReplayAudioCalled;
        }

        @Override
        protected boolean isUndoAvailable() {
            return mUndoAvailable;
        }

        public KeyboardInputTestReviewer withUndoAvailable(boolean value) {
            this.mUndoAvailable = value;
            return this;
        }


        public boolean hasBeenAnswered() {
            return mAnswered != null;
        }

        @Override
        protected void performClickWithVisualFeedback(int ease) {
            answerCard(ease);
        }
    }
}
