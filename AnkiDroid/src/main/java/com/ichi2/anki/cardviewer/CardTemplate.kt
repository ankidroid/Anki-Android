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
    private final String mPreStyle;
    private final String mPreScript;
    private final String mPreClass;
    private final String mPreContent;
    private final String mPostContent;

    public CardTemplate(@NonNull String template) {
        // Note: This refactoring means the template must be in the specific order of style, class, content.
        // Since this is a const loaded from an asset file, I'm fine with this.
        String classDelim = "::class::";
        String styleDelim = "::style::";
        String scriptDelim = "::script::";
        String contentDelim = "::content::";

        int styleIndex = template.indexOf(styleDelim);
        int scriptIndex = template.indexOf(scriptDelim);
        int classIndex = template.indexOf(classDelim);
        int contentIndex = template.indexOf(contentDelim);

        try {
            this.mPreStyle = template.substring(0, styleIndex);
            this.mPreScript = template.substring(styleIndex + styleDelim.length(), scriptIndex);
            this.mPreClass = template.substring(scriptIndex + scriptDelim.length(), classIndex);
            this.mPreContent = template.substring(classIndex + classDelim.length(), contentIndex);
            this.mPostContent = template.substring(contentIndex + contentDelim.length());
        } catch (StringIndexOutOfBoundsException ex) {
            throw new IllegalStateException("The card template had replacement string order, or content changed", ex);
        }
    }

    @CheckResult
    @NonNull
    public String render(String content, String style, String script, String cardClass) {
        return mPreStyle + style + mPreScript + script + mPreClass + cardClass + mPreContent + content + mPostContent;
    }
}
