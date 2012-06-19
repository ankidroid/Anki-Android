/****************************************************************************************
 * Copyright (c) 2009 Brennan D'Aguilar <brennan.daguilar@gmail.com>                    *
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

/**
 * Parses text input from Anki cards to display ruby text correctly in AnkiDroid. Anki's Japanese language support
 * handles ruby text as: <code>basetext[rubytext]</code> where the base text begins after the first spacer proceeding
 * the ruby text, or the beginning of the string of text if no spacers exist before the start of the base text. This is
 * converted to basic ruby markup: <code><ruby><rb>baseText</rb><rt>rubyText</rt></ruby></code> While webkit on android
 * devices does not support ruby markup yet, the text can be adjusted adequately using css.
 */
public class RubyParser {

    // private static final char RUBY_SPACER_JAP_SPACE = ' ';
    // private static final char RUBY_SPACER_JAP_COMMA = '\u3001';
    // private static final char RUBY_TEXT_START = '[';
    // private static final char RUBY_TEXT_END = ']';
    // private static final char HTML_TAG_START = '<';
    // private static final char HTML_TAG_END = '>';

    /**
     * Converts ruby text from the format used by Anki's Japanese support plugin to html ruby markup.
     * 
     * @param sourceText the japanese text containing ruby text
     * @return html ruby markup equivalent of the input text.
     */
    public static String ankiRubyToMarkup(String sourceText) {
        return sourceText.replaceAll(" ?([^ >]+?)\\[([^(sound:)].*?)\\]", "<ruby><rb>$1</rb><rt>$2</rt></ruby>");
        /*
         * int cursorIndex = 0; int nextRubyTextStart; // The first '[' after the cursorIndex int nextSpacer; // The
         * first spacer (' ', or '、') after the cursorIndex int nextRubyTextEnd; // The first ']' after the cursorIndex
         * int nextHtmlTagStart; // The first '<' after the cursorIndex StringBuilder builder = new StringBuilder(); //
         * Loop until the entire string is parsed while (cursorIndex < sourceText.length() - 1) { // Find the location
         * of the beginning of the next ruby text nextRubyTextStart = sourceText.indexOf(RUBY_TEXT_START, cursorIndex);
         * // Find the location of the next spacing character (only -1 if neither possible // spacing character remains.
         * nextSpacer = sourceText.indexOf(RUBY_SPACER_JAP_SPACE, cursorIndex); if (nextSpacer == -1) { nextSpacer =
         * Math.max(nextSpacer, sourceText.indexOf(RUBY_SPACER_JAP_COMMA, cursorIndex)); } // Check for html tags that
         * come before any ruby text. If found, pass the full tag // without parsing. nextHtmlTagStart =
         * sourceText.indexOf(HTML_TAG_START, cursorIndex); if (nextHtmlTagStart != -1 && (nextSpacer == -1 ||
         * nextHtmlTagStart < nextSpacer) && (nextRubyTextStart == -1 || nextHtmlTagStart < nextRubyTextStart)) { int
         * nextHtmlTagEnd = sourceText.indexOf(HTML_TAG_END, nextHtmlTagStart);
         * builder.append(sourceText.substring(cursorIndex, nextHtmlTagEnd + 1)); cursorIndex = nextHtmlTagEnd + 1; }
         * else // If no html tag is passed through on this cycle, check for ruby text. { // If any unparsed ruby text
         * remains if (nextRubyTextStart != -1) { // If there is any text before the next ruby tag that is part of the
         * ruby base text, // pass it through unparsed. if (nextSpacer < nextRubyTextStart && nextSpacer != -1) { //
         * Remove spaces from the text if (sourceText.charAt(nextSpacer) == RUBY_SPACER_JAP_SPACE) {
         * builder.append(sourceText.substring(cursorIndex, nextSpacer)); } else // If spacing character is not a space
         * (eg. a comma), pass it through as well. { builder.append(sourceText.substring(cursorIndex, nextSpacer + 1));
         * } cursorIndex = nextSpacer + 1; } else { // Find the end of the ruby text, and parse it into html tags.
         * nextRubyTextEnd = sourceText.indexOf(RUBY_TEXT_END, cursorIndex);
         * builder.append(newRubyPair(sourceText.substring(cursorIndex, nextRubyTextStart),
         * sourceText.substring(nextRubyTextStart + 1, nextRubyTextEnd))); cursorIndex = nextRubyTextEnd + 1; } } else {
         * // If no ruby text remains to be parsed, pass any remaining text through and finish.
         * builder.append(sourceText.substring(cursorIndex)); cursorIndex = sourceText.length(); } } } return
         * builder.toString();
         */

    }


    /**
     * Strips kanji from ruby markup. Used for reading in question
     * 
     * @param sourceText the japanese text containing ruby text
     * @return text with kanji substituted by it's reading
     */
    public static String ankiStripKanji(String sourceText) {
        return sourceText.replaceAll(" ?([^ >]+?)\\[([^(sound:)].*?)\\]", "$2");
    }

    /*
     * private static String newRubyPair(String baseText, String rubyText) { return "<ruby><rb>" + baseText +
     * "</rb><rt>" + rubyText + "</rt></ruby>"; }
     */
}
