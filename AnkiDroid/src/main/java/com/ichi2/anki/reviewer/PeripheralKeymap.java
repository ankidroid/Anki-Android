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
import java.util.Objects;

import androidx.annotation.NonNull;

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


    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (!mHasSetup) {
            return false;
        }
        if (mReviewerUI.isDisplayingAnswer()) {
            return mAnswerKeyMap.onKeyUp(keyCode, event);
        } else {
            return mQuestionKeyMap.onKeyUp(keyCode, event);
        }
    }

    @SuppressWarnings( {"unused", "RedundantSuppression"})
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        return false;
    }

    private static class KeyMap {
        public final HashMap<MappableBinding, PeripheralCommand> mBindingMap = new HashMap<>();
        private final CommandProcessor mProcessor;

        private KeyMap(CommandProcessor commandProcessor) {
            this.mProcessor = commandProcessor;
        }

        @SuppressWarnings( {"unused", "RedundantSuppression"})
        public boolean onKeyUp(int keyCode, KeyEvent event) {
            boolean ret = false;

            List<Binding> bindings = Binding.key(event);

            for (Binding b: bindings) {
                MappableBinding binding = new MappableBinding(b);
                PeripheralCommand command = mBindingMap.get(binding);
                if (command == null) {
                    continue;
                }

                ret |= mProcessor.executeCommand(command.getCommand());
            }

            return ret;
        }


        public void addCommand(PeripheralCommand command) {
            MappableBinding key = new MappableBinding(command.getBinding());
            mBindingMap.put(key, command);
        }
    }

    /**
     * Custom class to use for a custom equals/hashcode implementation in a HashMap/set
     * https://stackoverflow.com/questions/5453226/java-need-a-hash-map-where-one-supplies-a-function-to-do-the-hashing
     * */
    public static class MappableBinding {
        @NonNull
        private final Binding mBinding;

        public MappableBinding(@NonNull Binding binding) {
            mBinding = binding;
        }

        @NonNull
        public static MappableBinding fromBinding(Binding b) {
            return new MappableBinding(b);
        }

        @NonNull
        public Binding getBinding() {
            return mBinding;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            MappableBinding mappableBinding = (MappableBinding) o;
            Binding binding = mappableBinding.mBinding;

            return Objects.equals(mBinding.getKeycode(), binding.getKeycode()) &&
                    Objects.equals(mBinding.getUnicodeCharacter(), binding.getUnicodeCharacter()) &&
                    Objects.equals(mBinding.getGesture(), binding.getGesture()) &&
                    modifierEquals(binding.getModifierKeys());
        }


        @Override
        public int hashCode() {
            // don't include the modifierKeys
            return Objects.hash(mBinding.getKeycode(), mBinding.getUnicodeCharacter(), mBinding.getGesture());
        }


        protected boolean modifierEquals(Binding.ModifierKeys keys) {
            // equals allowing subclasses
            Binding.ModifierKeys thisKeys = mBinding.getModifierKeys();

            if (thisKeys == keys) {
                return true;
            }
            // one is null
            if (keys == null || thisKeys == null) {
                return false;
            }

            // Perf: Could get a slight improvement if we check that both instances are not subclasses

            // allow subclasses to work - a subclass which overrides shiftMatches will return true on one of the tests
            return (thisKeys.shiftMatches(true) == keys.shiftMatches(true) || thisKeys.shiftMatches(false) == keys.shiftMatches(false)) &&
                    (thisKeys.ctrlMatches(true) == keys.ctrlMatches(true) || thisKeys.ctrlMatches(false) == keys.ctrlMatches(false)) &&
                    (thisKeys.altMatches(true) == keys.altMatches(true) || thisKeys.altMatches(false) == keys.altMatches(false));
        }
    }
}
