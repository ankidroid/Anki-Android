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

package com.ichi2.anki.cardviewer;

import com.ichi2.libanki.Sound;
import com.ichi2.libanki.Utils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TypedAnswer {

    /** Regex pattern used in removing tags from text before diff */
    private static final Pattern sSpanPattern = Pattern.compile("</?span[^>]*>");
    private static final Pattern sBrPattern = Pattern.compile("<br\\s?/?>");

    /**
     * Clean up the correct answer text, so it can be used for the comparison with the typed text
     *
     * @param answer The content of the field the text typed by the user is compared to.
     * @return The correct answer text, with actual HTML and media references removed, and HTML entities unescaped.
     */
    public static String cleanCorrectAnswer(String answer) {
        if (answer == null || "".equals(answer)) {
            return "";
        }
        Matcher matcher = sSpanPattern.matcher(Utils.stripHTML(answer.trim()));
        String answerText = matcher.replaceAll("");
        matcher = sBrPattern.matcher(answerText);
        answerText = matcher.replaceAll("\n");
        matcher = Sound.sSoundPattern.matcher(answerText);
        answerText = matcher.replaceAll("");
        return Utils.nfcNormalized(answerText);
    }
}
