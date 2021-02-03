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

import android.content.Context;
import android.text.TextUtils;
import android.view.KeyEvent;

import com.ichi2.anki.cardviewer.Gesture;
import com.ichi2.utils.StringUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import androidx.annotation.Nullable;
import timber.log.Timber;

public class Binding {

    public static final char KEY_PREFIX = '\u2328';

    public static final char GESTURE_PREFIX = '\u235D';

    private final ModifierKeys modifierKeys;

    @Nullable
    private final Integer mKeyCode;

    @Nullable
    private final Character mUnicodeCharacter;

    @Nullable
    private final Gesture mGesture;

    private Binding(ModifierKeys modifierKeys, Integer keyCode, Character unicodeCharacter, Gesture gesture) {
        this.modifierKeys = modifierKeys;
        this.mKeyCode = keyCode;
        this.mUnicodeCharacter = unicodeCharacter;
        this.mGesture = gesture;
    }

    public Character getUnicodeCharacter() {
        return mUnicodeCharacter;
    }

    public Integer getKeycode() {
        return mKeyCode;
    }

    public Gesture getGesture() {
        return mGesture;
    }

    public static Binding key(KeyEvent event) {
        ModifierKeys modifiers = new ModifierKeys(event.isShiftPressed(), event.isCtrlPressed(), event.isAltPressed());

        int keyCode = event.getKeyCode();
        if (keyCode != 0) {
            return Binding.keyCode(modifiers, keyCode);
        } else {
            // passing in metaState: 0 means that Ctrl+1 returns '1' instead of '\0'
            // NOTE: We do not differentiate on upper/lower case via KeyEvent.META_CAPS_LOCK_ON
            int unicodeChar = event.getUnicodeChar(event.getMetaState() & (KeyEvent.META_SHIFT_ON | KeyEvent.META_NUM_LOCK_ON));

            return Binding.unicode(modifiers, (char)unicodeChar);
        }
    }

    public static Binding unicode(char unicodeChar) {
        return unicode(ModifierKeys.none(), unicodeChar);
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

    private static Binding unknown() {
        return new Binding(ModifierKeys.none(), null, null, null);
    }


    @Override
    public int hashCode() {
        int result = modifierKeys != null ? modifierKeys.hashCode() : 0;
        result = 31 * result + (mKeyCode != null ? mKeyCode.hashCode() : 0);
        result = 31 * result + (mUnicodeCharacter != null ? mUnicodeCharacter.hashCode() : 0);
        result = 31 * result + (mGesture != null ? mGesture.hashCode() : 0);
        return result;
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

        return Objects.equals(modifierKeys, binding.modifierKeys)
                && Objects.equals(mKeyCode, binding.mKeyCode)
                && Objects.equals(mUnicodeCharacter, binding.mUnicodeCharacter)
                && Objects.equals(mGesture, binding.mGesture);
    }

    public String toDisplayString(Context context) {
        StringBuilder string = new StringBuilder();

        if (mKeyCode != null) {
            string.append(KEY_PREFIX);
            string.append(' ');
            string.append(modifierKeys.toString());
            String keyCodeString = KeyEvent.keyCodeToString(mKeyCode);
            string.append(StringUtil.capitalize(keyCodeString.replace("KEYCODE_", "").replace('_', ' ')));
        } else if (mUnicodeCharacter != null) {
            string.append(KEY_PREFIX);
            string.append(' ');
            string.append(modifierKeys.toString());
            string.append(mUnicodeCharacter);
        } else if (mGesture != null) {
            string.append(GESTURE_PREFIX);
            string.append(' ');
            string.append(context.getString(mGesture.getResourceId()));
        }

        return string.toString();
    }

    @Override
    public String toString() {
        StringBuilder string = new StringBuilder();

        if (mKeyCode != null) {
            string.append(KEY_PREFIX);
            string.append(modifierKeys.toString());
            String temp = KeyEvent.keyCodeToString(mKeyCode);
            string.append(temp);
        } else if (mUnicodeCharacter != null) {
            string.append(KEY_PREFIX);
            string.append(modifierKeys.toString());
            string.append(mUnicodeCharacter);
        } else if (mGesture != null) {
            string.append(GESTURE_PREFIX);
            string.append(mGesture);
        }

        return string.toString();
    }

    public boolean isKey() {
        return mKeyCode != null || mUnicodeCharacter != null;
    }

    public boolean isGesture() {
        return mGesture != null;
    }

    public static class ModifierKeys {
        @Nullable
        private final boolean mShift;
        @Nullable
        private final boolean mCtrl;
        @Nullable
        private final boolean mAlt;

        public ModifierKeys(boolean shift, boolean ctrl, boolean alt) {
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

        public static ModifierKeys alt() {
            return new ModifierKeys(false, false, true);
        }


        @Override
        public int hashCode() {
            int result = (mShift ? 1 : 0);
            result = 31 * result + (mCtrl ? 1 : 0);
            result = 31 * result + (mAlt ? 1 : 0);
            return result;
        }

        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            ModifierKeys modifiers = (ModifierKeys) o;

            return mShift == modifiers.mShift && mCtrl == modifiers.mCtrl && mAlt == modifiers.mAlt;

        }

        public boolean matches(KeyEvent event) {
            return mShift == event.isShiftPressed() &&
                    mCtrl == event.isCtrlPressed() &&
                    mAlt == event.isAltPressed();
        }

        @Override
        public String toString() {
            StringBuilder string = new StringBuilder();

            if (mCtrl) {
                string.append("Ctrl+");
            }
            if (mShift) {
                string.append("Shift+");
            }
            if (mAlt) {
                string.append("Alt+");
            }

            return string.toString();
        }

        public static ModifierKeys fromString(String from) {
            return new ModifierKeys(from.contains("Shift"), from.contains("Ctrl"), from.contains("Alt"));
        }
    }

    public static Binding fromString(String from) {

        try {
            if (from.charAt(0) == GESTURE_PREFIX) {
                return gesture(Gesture.valueOf(Gesture.class, from.substring(1)));
            }

            if (from.charAt(0) == KEY_PREFIX) {
                from = from.substring(1);

                ModifierKeys modifiers = ModifierKeys.none();
                int plus = from.lastIndexOf("+");
                if (plus != -1) {
                    modifiers = ModifierKeys.fromString(from.substring(0, plus + 1));
                    from = from.substring(plus + 1);
                }

                int keyCode = KeyEvent.keyCodeFromString(from);
                if (keyCode != KeyEvent.KEYCODE_UNKNOWN) {
                    return keyCode(modifiers, keyCode);
                }

                return unicode(modifiers, from.charAt(0));
            }
        } catch (Exception ex) {
            Timber.d(ex);
        }

        return unknown();
    }
}
