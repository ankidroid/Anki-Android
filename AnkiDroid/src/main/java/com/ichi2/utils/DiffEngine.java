/****************************************************************************************
 * Copyright (c) 2018 Mike Hardy <mike@mikehardy.net>                                   *
 *                                                                                      *
 * This program is free software; you can redistribute it and/or modify it under        *
 * the terms of the GNU General Public License as published by the Free Software        *
 * Foundation; either version 3 of the License, or (at your option) any later           *
 * version.                                                                             *
 *                                                                                      *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY      *
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A      *
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.             *
 *                                                                                      *
 * You should have received a copy of the GNU General Public License along with         *
 * this program.  If not, see <http://www.gnu.org/licenses/>.                           *
 ****************************************************************************************/

package com.ichi2.utils;

import com.ichi2.compat.CompatHelper;

import org.bitbucket.cowwoc.diffmatchpatch.DiffMatchPatch;

/**
 * Functions for diff, match and patch. Computes the difference between two texts to create a patch. Applies the patch
 * onto another text, allowing for errors.
 */
public class DiffEngine {

    private DiffMatchPatch diffMatchPatch = new DiffMatchPatch();


    /**
     * Return two strings to display as typed and correct text.
     *
     * @param typed (cleaned-up) text the user typed in,
     * @param correct (cleaned-up) correct text
     * @return Two-element String array with HTML representation of the diffs between the inputs.
     */
    public String[] diffedHtmlStrings(String typed, String correct) {
        StringBuilder prettyTyped = new StringBuilder();
        StringBuilder prettyCorrect = new StringBuilder();
        for (DiffMatchPatch.Diff aDiff : diffMatchPatch.diffMain(typed, correct)) {
            switch (aDiff.operation) {
                case INSERT:
                    prettyTyped.append(wrapBad(aDiff.text));
                    break;
                case DELETE:
                    prettyCorrect.append(wrapMissing(aDiff.text));
                    break;
                case EQUAL:
                    prettyTyped.append(wrapGood(aDiff.text));
                    prettyCorrect.append(wrapGood(aDiff.text));
                    break;
            }
        }
        return new String[] {prettyTyped.toString(), prettyCorrect.toString()};
    }


    private static String wrapBad(String in) {
        // We do the comparison with “<”s &c. in the strings, but should of course not just put those in the HTML
        // output. Also, it looks like the Android WebView swallows single “\”s, so replace those with the entity by
        // hand.
        return "<span class=\"typeBad\">" + CompatHelper.getCompat().detagged(in).replace("\\", "&#x5c;") + "</span>";
    }


    public static String wrapGood(String in) {
        return "<span class=\"typeGood\">" + CompatHelper.getCompat().detagged(in).replace("\\", "&#x5c;") + "</span>";
    }


    public static String wrapMissing(String in) {
        return "<span class=\"typeMissed\">" + CompatHelper.getCompat().detagged(in).replace("\\", "&#x5c;") + "</span>";
    }
}