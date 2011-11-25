/***************************************************************************************
 * Copyright (c) 2009 Edu Zamora <edu.zasu@gmail.com>                                   *
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

package com.ichi2.anki;

import android.text.Html;
import android.util.Log;

import java.lang.StringBuilder;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Class used to display and handle correctly LaTeX.
 */
public class LaTeX {

    /**
     * Patterns used to identify LaTeX tags
     */
    public static Pattern sStandardPattern = Pattern
            .compile("\\[latex\\](.+?)\\[/latex\\]");
    public static Pattern sExpressionPattern = Pattern
            .compile("\\[\\$\\](.+?)\\[/\\$\\]");
    public static Pattern sMathPattern = Pattern
            .compile("\\[\\$\\$\\](.+?)\\[/\\$\\$\\]"); 

    ///Use this to replace <br> tags
    private static Pattern sBRPattern = Pattern
            .compile("<br( /)?>");

    /* Prevent class from being instantiated */
    private LaTeX() { }

    /**
     * Parses the content (belonging to deck), replacing LaTeX with img tags
     *
     * @TODO should check to see if the image exists or not? Or can we assume that
     * the user is bright enough to compile stuff if they are using LaTeX?
     * @param deck Deck whose content is being parsed
     * @param content HTML content of a card
     * @return content Content with the onload events for the img tags
     */
    public static String parseLaTeX(Deck deck, String content) {

        StringBuilder stringBuilder = new StringBuilder();
        String contentLeft = content;
        String latex;

        //First pass, grab everything that the standard pattern gets
        Log.i(AnkiDroidApp.TAG, "parseLaTeX");
        Matcher matcher = sStandardPattern.matcher(contentLeft);
        while (matcher.find()) {
            latex = matcher.group(1);
            String img = mungeLatex(deck, latex);
            img = "latex-" + Utils.checksum(img) + ".png";

            String imgTag = matcher.group();
            int markerStart = contentLeft.indexOf(imgTag);
            stringBuilder.append(contentLeft.substring(0, markerStart));
            stringBuilder.append("<img src=\"" + img + "\" alt=\"" + latex + "\">");

            contentLeft = contentLeft.substring(markerStart + imgTag.length());
        }
        stringBuilder.append(contentLeft);

        //Second pass, grab everything that the expression pattern gets
        contentLeft = stringBuilder.toString();
        stringBuilder = new StringBuilder();
        matcher = sExpressionPattern.matcher(contentLeft);
        while (matcher.find()) {
            latex = matcher.group(1);
            String img = "$" + mungeLatex(deck, latex) + "$";
            img = "latex-" + Utils.checksum(img) + ".png";

            String imgTag = matcher.group();
            int markerStart = contentLeft.indexOf(imgTag);
            stringBuilder.append(contentLeft.substring(0, markerStart));
            stringBuilder.append("<img src=\"" + img + "\" alt=\"" + latex + "\">");

            contentLeft = contentLeft.substring(markerStart + imgTag.length());
        }
        stringBuilder.append(contentLeft);

        //Thid pass, grab everything that the math pattern gets
        contentLeft = stringBuilder.toString();
        stringBuilder = new StringBuilder();
        matcher = sMathPattern.matcher(contentLeft);
        while (matcher.find()) {
            latex = matcher.group(1);
            String img = "\\begin{displaymath}" + mungeLatex(deck, latex) + "\\end{displaymath}";
            img = "latex-" + Utils.checksum(img) + ".png";

            String imgTag = matcher.group();
            int markerStart = contentLeft.indexOf(imgTag);
            stringBuilder.append(contentLeft.substring(0, markerStart));
            stringBuilder.append("<img src=\"" + img + "\" alt=\"" + latex + "\">");

            contentLeft = contentLeft.substring(markerStart + imgTag.length());
        }
        stringBuilder.append(contentLeft);

        return stringBuilder.toString();
    }

    private static final Pattern htmlNamedEntityPattern = Pattern.compile("(?i)&[a-z]+;");

    /**
     * Convert entities, fix newlines, convert to utf8, and wrap pre/postamble.
     *
     * @param deck The deck, needed to get the latex pre/post-ambles
     * @param latex The latex trsing to be munged
     * @return the latex string transformed as described above
     */
    private static String mungeLatex(Deck deck, String latex) {
        // Deal with HTML named entities
        StringBuilder sb = new StringBuilder(latex);
        Matcher namedEntity = htmlNamedEntityPattern.matcher(sb);
        while (namedEntity.find()) {
            sb.replace(namedEntity.start(), namedEntity.end(), Html.fromHtml(namedEntity.group()).toString());
            namedEntity = htmlNamedEntityPattern.matcher(sb);
        }
        latex = sb.toString();

        // Fix endlines
        latex = sBRPattern.matcher(latex).replaceAll("\n");
     
        // Add pre/post-ambles
        latex = deck.getVar("latexPre") + "\n" + latex + "\n" + deck.getVar("latexPost");

        // TODO: Do we need to convert to UTF-8?

        return latex;
    }
}
