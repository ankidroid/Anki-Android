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

import com.ichi2.anki.cardviewer.ViewerCommand.CommandProcessor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/** Accepts peripheral input, mapping via various keybinding strategies,
 * and converting them to commands for the Reviewer. */
public class PeripheralKeymap {

    private final ReviewerUi mReviewerUI;
    private final KeyMap mAnswerKeyMap;
    private final KeyMap mQuestionKeyMap;

    private boolean mHasSetup = false;

    public PeripheralKeymap(ReviewerUi reviewerUi, CommandProcessor commandProcessor) {
        this.mReviewerUI = reviewerUi;
        this.mQuestionKeyMap = new KeyMap(commandProcessor);
        this.mAnswerKeyMap = new KeyMap(commandProcessor);
    }

    public void setup() {
        List<PeripheralCommand> commands = PeripheralCommand.getDefaultCommands();

        for (PeripheralCommand command : commands) {
            //NOTE: Can be both
            if (command.isQuestion()) {
                mQuestionKeyMap.addCommand(command);
            }
            if (command.isAnswer()) {
                mAnswerKeyMap.addCommand(command);
            }
         }

        mHasSetup = true;
    }


    @SuppressWarnings("unused")
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        return false;
    }

    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (!mHasSetup) {
            return false;
        }
        if (mReviewerUI.isDisplayingAnswer()) {
            return mAnswerKeyMap.onKeyUp(keyCode, event);
        } else {
            return mQuestionKeyMap.onKeyUp(keyCode, event);
        }
    }

    private static class KeyMap {
        public HashMap<Integer, List<Integer>> mKeyCodeToCommand = new HashMap<>();
        public HashMap<Integer, List<Integer>> mUnicodeToCommand = new HashMap<>();
        private final CommandProcessor mProcessor;

        private KeyMap(CommandProcessor commandProcessor) {
            this.mProcessor = commandProcessor;
        }

        public boolean onKeyUp(int keyCode, KeyEvent event) {
            boolean ret = false;

            {
                List<Integer> a = mKeyCodeToCommand.get(keyCode);
                if (a != null) {
                    for (Integer command : a) {
                        ret |= mProcessor.executeCommand(command);
                    }
                }
            }
            {
                List<Integer> unicodeLookup = mUnicodeToCommand.get(event.getUnicodeChar());
                if (unicodeLookup != null) {
                    for (Integer command : unicodeLookup) {
                        ret |= mProcessor.executeCommand(command);
                    }
                }
            }

            return ret;
        }


        public void addCommand(PeripheralCommand command) {
            //COULD_BE_BETTER: DefaultDict
            if (command.getUnicodeCharacter() != null) {
                //NB: Int is correct here, the value from KeyCode is an int.
                int unicodeChar = command.getUnicodeCharacter();
                if (!mUnicodeToCommand.containsKey(unicodeChar)) {
                    mUnicodeToCommand.put(unicodeChar, new ArrayList<>());
                }
                //noinspection ConstantConditions
                mUnicodeToCommand.get(unicodeChar).add(command.getCommand());
            }

            if (command.getKeycode() != null) {
                Integer c = command.getKeycode();
                if (!mKeyCodeToCommand.containsKey(c)) {
                    mKeyCodeToCommand.put(c, new ArrayList<>());
                }
                //noinspection ConstantConditions
                mKeyCodeToCommand.get(c).add(command.getCommand());
            }
        }
    }
}
