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

package com.ichi2.anki.cardviewer;

import androidx.annotation.CheckResult;
import androidx.annotation.NonNull;

public class CardTemplate {
    @NonNull
    private final String mContent;

    public CardTemplate(@NonNull String content) {
        this.mContent = content;
    }

    @CheckResult
    @NonNull
    public String render(String content, String style, String cardClass) {
        return mContent.replace("::content::", content).replace("::style::", style).replace("::class::", cardClass);
    }
}
