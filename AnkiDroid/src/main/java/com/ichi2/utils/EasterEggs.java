/*
 *  Copyright (c) 2020 David Allison <davidallisongithub@gmail.com>
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

package com.ichi2.utils;

import android.os.Build;

import com.ichi2.anki.NoteEditor;
import com.ichi2.anki.UIUtils;

public class EasterEggs {
    public static void onSaveNote(NoteEditor noteEditor) {
        try {
            // Thank you for the positive review bomb (https://www.youtube.com/watch?v=miPN5kIyHnE)
            String allText = noteEditor.getFieldsText();
            if (allText.toLowerCase().contains("web5ngay")) {
                UIUtils.showThemedToast(noteEditor, "Web5Ngay " + getLoveEmoji(), true);
            }
        } catch (Exception e) {
            // ignore
        }
    }


    private static String getLoveEmoji() {
        // two heart eyes emoji - doesn't appear well on API 16 - quick Google said KitKat's the way to go.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            return "\uD83D\uDE0D\uD83D\uDE0D";
        } else {
            return "❤❤";
        }
    }
}
