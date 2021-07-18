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

import java.util.HashMap;
import java.util.List;

/** Accepts peripheral input, mapping via various keybinding strategies,
 * and converting them to commands for the Reviewer. */
public class PeripheralKeymap {

    private final ReviewerUi mReviewerUI;
    private final KeyMap mKeyMap;

    private boolean mHasSetup = false;

    public PeripheralKeymap(ReviewerUi reviewerUi, CommandProcessor commandProcessor) {
        this.mReviewerUI = reviewerUi;
        this.mKeyMap = new KeyMap(commandProcessor);
    }

    public void setup() {
        for (PeripheralCommand command : PeripheralCommand.getDefaultCommands()) {
            mKeyMap.addCommand(command, command.getSide());
        }

        mHasSetup = true;
    }


    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (!mHasSetup || event.getRepeatCount() > 0) {
            return false;
        }

        return mKeyMap.onKeyUp(keyCode, event);
    }

    @SuppressWarnings( {"unused", "RedundantSuppression"})
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        return false;
    }

    private class KeyMap {
        public final HashMap<MappableBinding, PeripheralCommand> mBindingMap = new HashMap<>();
        private final CommandProcessor mProcessor;

        private KeyMap(CommandProcessor commandProcessor) {
            this.mProcessor = commandProcessor;
        }

        @SuppressWarnings( {"unused", "RedundantSuppression"})
        public boolean onKeyUp(int keyCode, KeyEvent event) {
            boolean ret = false;

            List<Binding> bindings = Binding.key(event);
            CardSide side = mReviewerUI.isDisplayingAnswer() ? CardSide.ANSWER : CardSide.QUESTION;

            for (Binding b: bindings) {

                MappableBinding binding = new MappableBinding(b, side);
                PeripheralCommand command = mBindingMap.get(binding);
                if (command == null) {
                    continue;
                }

                ret |= mProcessor.executeCommand(command.getCommand());
            }

            return ret;
        }


        public void addCommand(PeripheralCommand command, CardSide side) {
            MappableBinding key = new MappableBinding(command.getBinding(), side);
            mBindingMap.put(key, command);
        }
    }
}
