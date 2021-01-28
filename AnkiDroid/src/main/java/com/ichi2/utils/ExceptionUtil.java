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

import androidx.annotation.CheckResult;
import androidx.annotation.NonNull;

public class ExceptionUtil {
    public static boolean containsMessage(Throwable e, String needle) {
        if (e == null) {
            return false;
        }

        if (containsMessage(e.getCause(), needle)) {
            return true;
        }

        String message = e.getMessage();
        return message != null && message.contains(needle);
    }

    @NonNull
    @CheckResult
    public static String getExceptionMessage(Throwable e) {
        StringBuilder ret = new StringBuilder();
        Throwable cause = e;
        while (cause != null) {
            if (cause != e) {
                ret.append("\n");
            }
            ret.append(cause.getLocalizedMessage());
            cause = cause.getCause();
        }

        return ret.toString();
    }
}
