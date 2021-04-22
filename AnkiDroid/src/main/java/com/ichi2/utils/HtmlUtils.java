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

import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class HtmlUtils {
    //#5188 - compat.fromHtml converts newlines into spaces.
    @Nullable
    public static String convertNewlinesToHtml(@Nullable String html) {
        if (html == null) {
            return null;
        }
        String withoutWindowsLineEndings = html.replace("\r\n", "<br/>");
        //replace unix line endings
        return withoutWindowsLineEndings.replace("\n", "<br/>");
    }


    @NonNull
    public static String escape(@NonNull String html) {
        return TextUtils.htmlEncode(html);
    }
}
