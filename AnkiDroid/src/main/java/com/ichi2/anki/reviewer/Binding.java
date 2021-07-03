/*
 *  Copyright (c) 2021 David Allison <davidallisongithub@gmail.com>
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

package com.ichi2.anki.reviewer;

import android.view.KeyEvent;

import com.ichi2.anki.cardviewer.Gesture;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

public class Binding {

    @Nullable
    private final ModifierKeys mModifierKeys;

    @Nullable
    private final Integer mKeyCode;

    @Nullable
    private final Character mUnicodeCharacter;

    @Nullable
    private final Gesture mGesture;

    private Binding(@Nullable ModifierKeys modifierKeys, @Nullable Integer keyCode, @Nullable Character unicodeCharacter, @Nullable Gesture gesture) {
        this.mModifierKeys = modifierKeys;
        this.mKeyCode = keyCode;
        this.mUnicodeCharacter = unicodeCharacter;
        this.mGesture = gesture;
    }

    @Nullable
    public Character getUnicodeCharacter() {
        return mUnicodeCharacter;
    }

    @Nullable
    public Integer getKeycode() {
        return mKeyCode;
    }

    @Nullable
    public Gesture getGesture() {
        return mGesture;
    }

    @VisibleForTesting(otherwise = VisibleForTesting.NONE)
    @Nullable
    public ModifierKeys getModifierKeys() {
        return mModifierKeys;
    }

    /** This returns multiple bindings due to the "default" implementation not knowing what the keycode for a button is */
    public static List<Binding> key(KeyEvent event) {
        // convert to iterator when this is moved to Kotlin
        ModifierKeys modifiers = new ModifierKeys(event.isShiftPressed(), event.isCtrlPressed(), event.isAltPressed());

        List<Binding> ret = new ArrayList<>();
        int keyCode = event.getKeyCode();
        if (keyCode != 0) {
            ret.add(Binding.keyCode(modifiers, keyCode));
        }

        // passing in metaState: 0 means that Ctrl+1 returns '1' instead of '\0'
        // NOTE: We do not differentiate on upper/lower case via KeyEvent.META_CAPS_LOCK_ON
        int unicodeChar = event.getUnicodeChar(event.getMetaState() & (KeyEvent.META_SHIFT_ON | KeyEvent.META_NUM_LOCK_ON));

        ret.add(Binding.unicode(modifiers, (char)unicodeChar));
        return ret;
    }

    /** 
     * Specifies a unicode binding from an unknown input device
     * See {@link AppDefinedModifierKeys}
     * */
    public static Binding unicode(char unicodeChar) {
        return unicode(AppDefinedModifierKeys.allowShift(), unicodeChar);
    }

    public static Binding unicode(ModifierKeys modifierKeys, char unicodeChar) {
        return new Binding(modifierKeys, null, (Character) unicodeChar, null);
    }

    public static Binding keyCode(int keyCode) {
        return keyCode(ModifierKeys.none(), keyCode);
    }

    public static Binding keyCode(ModifierKeys modifiers, int keyCode) {
        return new Binding(modifiers, keyCode, null, null);
    }

    public static Binding gesture(Gesture gesture) {
        return new Binding(null, null, null, gesture);
    }

    public boolean isKey() {
        return mKeyCode != null || mUnicodeCharacter != null;
    }

    public boolean isGesture() {
        return mGesture != null;
    }


    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Binding binding = (Binding) o;
        return Objects.equals(mKeyCode, binding.mKeyCode) &&
                Objects.equals(mUnicodeCharacter, binding.mUnicodeCharacter) &&
                Objects.equals(mGesture, binding.mGesture) &&
                Objects.equals(mModifierKeys, binding.mModifierKeys);
    }


    @Override
    public int hashCode() {
        return Objects.hash(mKeyCode, mUnicodeCharacter, mGesture, mModifierKeys);
    }


    public boolean matchesModifier(KeyEvent event) {
        return mModifierKeys == null || mModifierKeys.matches(event);
    }


    public static class ModifierKeys {
        private final boolean mShift;
        private final boolean mCtrl;
        private final boolean mAlt;


        private ModifierKeys(boolean shift, boolean ctrl, boolean alt) {
            this.mShift = shift;
            this.mCtrl = ctrl;
            this.mAlt = alt;
        }


        public static ModifierKeys none() {
            return new ModifierKeys(false, false, false);
        }

        public static ModifierKeys ctrl() {
            return new ModifierKeys(false, true, false);
        }

        public static ModifierKeys shift() {
            return new ModifierKeys(true, false, false);
        }


        public boolean matches(KeyEvent event) {
            // return false if Ctrl+1 is pressed and 1 is expected
            return shiftMatches(event) && ctrlMatches(event) && altMatches(event);
        }

        private boolean shiftMatches(KeyEvent event) {
            return mShift == event.isShiftPressed();
        }

        private boolean ctrlMatches(KeyEvent event) {
            return mCtrl == event.isCtrlPressed();
        }

        private boolean altMatches(KeyEvent event) {
            return altMatches(event.isAltPressed());
        }

        protected boolean shiftMatches(boolean shiftPressed) {
            return mShift == shiftPressed;
        }

        protected boolean ctrlMatches(boolean ctrlPressed) {
            return mCtrl == ctrlPressed;
        }

        protected boolean altMatches(boolean altPressed) {
            return mAlt == altPressed;
        }


        @Override
        public boolean equals(Object o) {
            // equals allowing subclasses
            if (this == o) {
                return true;
            }
            if (!(o instanceof ModifierKeys)) {
                return false;
            }

            ModifierKeys keys = (ModifierKeys) o;

            // special case: easy comparison
            if (keys.getClass() == ModifierKeys.class) {
                return keys.mShift == mShift && keys.mCtrl == mCtrl && keys.mAlt == mAlt;
            }

            // allow subclasses to work - a subclass which overrides shiftMatches will return true on one of the tests
            return (shiftMatches(true) == keys.shiftMatches(true) || shiftMatches(false) == keys.shiftMatches(false)) &&
                    (ctrlMatches(true) == keys.ctrlMatches(true) || ctrlMatches(false) == keys.ctrlMatches(false)) &&
                    (altMatches(true) == keys.altMatches(true) || altMatches(false) == keys.altMatches(false));
        }

        @Override
        public int hashCode() {
            // AKA: use .equals to allow subclasses to work - this will be very inefficient if placed in a HashMap
            // But we don't store data in a HashMap, so we're fine
            return 0;
        }
    }

    /** Modifier keys which cannot be defined by a binding */
    private static class AppDefinedModifierKeys extends ModifierKeys {

        /**
         * Specifies a keycode combination binding from an unknown input device
         * Should be due to the "default" key bindings and never from user input
         *
         * If we do not know what the device is, "*" could be a key on the keyboard or Shift + 8
         *
         * So we need to ignore shift, rather than match it to a value
         *
         * If we have bindings in the app, then we know whether we need shift or not (in actual fact, we should
         * be fine to use keycodes).
         * */
        public static ModifierKeys allowShift() {
            return new AppDefinedModifierKeys();
        }

        private AppDefinedModifierKeys() {
            super(false, false, false); // shift doesn't matter: alt and ctrl are off.
        }


        @Override
        protected boolean shiftMatches(boolean shiftPressed) {
            return true;
        }
    }
}
