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

import org.junit.Test;
import org.junit.runner.RunWith;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import timber.log.Timber;

import static org.hamcrest.MatcherAssert.assertThat;

@RunWith(AndroidJUnit4.class)
public class AbstractFlashcardViewerKeyboardInputTest extends RobolectricTest {

    @Test
    public void spaceShowsAnswer() {
        KeyboardInputTestCardViewer underTest = KeyboardInputTestCardViewer.create();

        underTest.handleKeyPress(KeyEvent.KEYCODE_SPACE);

        assertThat("Space should display answer on any card viewer",  underTest.didDisplayAnswer());
    }

    @Test
    @RunInBackground
    public void enterShowsAnswer() {
        KeyboardInputTestCardViewer underTest = KeyboardInputTestCardViewer.create();

        underTest.handleKeyPress(KeyEvent.KEYCODE_ENTER);

        assertThat("Enter should display answer on any card viewer",  underTest.didDisplayAnswer());
    }

    @Test
    public void numPadEnterShowsAnswer() {
        KeyboardInputTestCardViewer underTest = KeyboardInputTestCardViewer.create();

        underTest.handleKeyPress(KeyEvent.KEYCODE_NUMPAD_ENTER);

        assertThat("NumPad Enter should display answer on any card viewer",  underTest.didDisplayAnswer());
    }

    @Test
    public void spaceDoesNotShowAnswerIfTextFieldFocused() {
        KeyboardInputTestCardViewer underTest = KeyboardInputTestCardViewer.create();
        underTest.focusTextField();

        underTest.handleKeyPress(KeyEvent.KEYCODE_SPACE);

        assertThat("When text field is focused, space should not display answer",
                !underTest.didDisplayAnswer());

    }


    private static class KeyboardInputTestCardViewer extends AbstractFlashcardViewer {

        private boolean mDisplayAnswer = false;
        private boolean mFocusTextField = false;


        public static KeyboardInputTestCardViewer create() {
            AbstractFlashcardViewer.sDisplayAnswer = false;
            return new KeyboardInputTestCardViewer();
        }

        @Override
        protected boolean answerFieldIsFocused() {
            return mFocusTextField;
        }


        @Override
        protected void performReload() {
            // intentionally blank
        }


        @Override
        protected void displayCardAnswer() {
            mDisplayAnswer = true;
        }

        public boolean didDisplayAnswer() { return mDisplayAnswer; }

        public void handleKeyPress(int keycode) {
            //COULD_BE_BETTER: Saves 20 seconds on tests to remove AndroidJUnit4,
            // but may let something slip through the cracks.
            try {
                this.onKeyDown(keycode, new KeyEvent(KeyEvent.ACTION_DOWN, keycode));
            } catch (Exception e) {
                Timber.e(e);
            }
            try {
                this.onKeyUp(keycode, new KeyEvent(KeyEvent.ACTION_UP, keycode));
            } catch (Exception e) {
                Timber.e(e);
            }
        }
        @Override protected void setTitle() {
            //required for interface. Intentionally left blank
        }


        public void focusTextField() {
            mFocusTextField = true;
        }
    }
}
