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

import androidx.annotation.Nullable;

public class Binding {
    public static class ModifierKeys {
        // null == true/false works.
        @Nullable
        private final Boolean mShift;
        @Nullable
        private final Boolean mCtrl;
        @Nullable
        private final Boolean mAlt;


        private ModifierKeys(@Nullable Boolean shift, @Nullable Boolean ctrl, @Nullable Boolean alt) {
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

        /** Allows shift, but not Ctrl/Alt */
        public static ModifierKeys allowShift() {
            return new ModifierKeys(null, false, false);
        }


        public boolean matches(KeyEvent event) {
            // return false if Ctrl+1 is pressed and 1 is expected
            return (mShift == null || mShift == event.isShiftPressed()) &&
                    (mCtrl == null || mCtrl == event.isCtrlPressed()) &&
                    (mAlt == null || mAlt == event.isAltPressed());
        }
    }
}
