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

import android.view.KeyEvent;

public class KeyUtils {
    public static boolean isDigit(KeyEvent event) {
        int unicodeChar = event.getUnicodeChar(0);
        return unicodeChar >= '0' && unicodeChar <= '9';
    }


    public static int getDigit(KeyEvent event) {
        int unicodeChar = event.getUnicodeChar(0);
        return unicodeChar - '0';
    }
}
