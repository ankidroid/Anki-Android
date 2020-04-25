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

package com.ichi2.anki.reviewer;

import android.view.KeyEvent;

import com.ichi2.anki.cardviewer.ViewerCommand;
import com.ichi2.anki.cardviewer.ViewerCommand.CommandProcessor;
import com.ichi2.anki.cardviewer.ViewerCommand.ViewerCommandDef;

/** Accepts peripheral input, mapping via various keybinding strategies,
 * and converting them to commands for the Reviewer. */
public class PeripheralKeymap {

    private final CommandProcessor mCommandProcessor;
    private final ReviewerUi mReviewerUI;


    public PeripheralKeymap(ReviewerUi reviewerUi, CommandProcessor commandProcessor) {
        this.mReviewerUI = reviewerUi;
        this.mCommandProcessor = commandProcessor;
    }

    @SuppressWarnings("unused")
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        return false;
    }

    public boolean onKeyUp(int keyCode, KeyEvent event) {
        char keyPressed = (char) event.getUnicodeChar();

        if (mReviewerUI.isDisplayingAnswer()) {
            if (keyPressed == '1' || keyCode == KeyEvent.KEYCODE_BUTTON_Y) {
                executeCommand(ViewerCommand.COMMAND_ANSWER_FIRST_BUTTON);
                return true;
            }
            if (keyPressed == '2' || keyCode == KeyEvent.KEYCODE_BUTTON_X) {
                executeCommand(ViewerCommand.COMMAND_ANSWER_SECOND_BUTTON);
                return true;
            }
            if (keyPressed == '3' || keyCode == KeyEvent.KEYCODE_BUTTON_B) {
                executeCommand(ViewerCommand.COMMAND_ANSWER_THIRD_BUTTON);
                return true;
            }
            if (keyPressed == '4' || keyCode == KeyEvent.KEYCODE_BUTTON_A) {
                executeCommand(ViewerCommand.COMMAND_ANSWER_FOURTH_BUTTON);
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_SPACE || keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER) {
                executeCommand(ViewerCommand.COMMAND_ANSWER_RECOMMENDED);
                return true;
            }
        } else {
            if (keyCode == KeyEvent.KEYCODE_BUTTON_Y || keyCode == KeyEvent.KEYCODE_BUTTON_X
                    || keyCode == KeyEvent.KEYCODE_BUTTON_B || keyCode == KeyEvent.KEYCODE_BUTTON_A) {
                executeCommand(ViewerCommand.COMMAND_SHOW_ANSWER);
                return true;
            }
        }
        if (keyPressed == 'e') {
            executeCommand(ViewerCommand.COMMAND_EDIT);
            return true;
        }
        if (keyPressed == '*') {
            executeCommand(ViewerCommand.COMMAND_MARK);
            return true;
        }
        if (keyPressed == '-') {
            executeCommand(ViewerCommand.COMMAND_BURY_CARD);
            return true;
        }
        if (keyPressed == '=') {
            executeCommand(ViewerCommand.COMMAND_BURY_NOTE);
            return true;
        }
        if (keyPressed == '@') {
            executeCommand(ViewerCommand.COMMAND_SUSPEND_CARD);
            return true;
        }
        if (keyPressed == '!') {
            executeCommand(ViewerCommand.COMMAND_SUSPEND_NOTE);
            return true;
        }
        if (keyPressed == 'r' || keyCode == KeyEvent.KEYCODE_F5) {
            executeCommand(ViewerCommand.COMMAND_PLAY_MEDIA);
            return true;
        }

        // different from Anki Desktop
        if (keyPressed == 'z') {
            executeCommand(ViewerCommand.COMMAND_UNDO);
            return true;
        }
        return false;
    }


    private void executeCommand(@ViewerCommandDef int command) {
        this.mCommandProcessor.executeCommand(command);
    }
}
