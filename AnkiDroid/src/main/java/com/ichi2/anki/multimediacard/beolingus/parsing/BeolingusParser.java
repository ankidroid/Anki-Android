/****************************************************************************************
 * Copyright (c) 2013 Bibek Shrestha <bibekshrestha@gmail.com>                          *
 * Copyright (c) 2013 Zaur Molotnikov <qutorial@gmail.com>                              *
 * Copyright (c) 2013 Nicolas Raoul <nicolas.raoul@gmail.com>                           *
 * Copyright (c) 2013 Flavio Lerda <flerda@gmail.com>                                   *
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

package com.ichi2.anki.multimediacard.beolingus.parsing;

/**
 * This class parses beolingus pages
 */
public class BeolingusParser {
    private static String PRONUNC_STOPPER = "<img src=\"/pics/s1.png\"";
    private static String MP3_STOPPER = ".mp3\">Listen";


    /**
     * @param translationHtml = html page from beolingus, with translation of the word we search
     * @param wordToSearchFor
     * @return "no" or http address of the page with translation First this function searches for the picture as
     *         described above, this picture is in the pronunciation link. Then picture title is being compared to the
     *         word we search. If they match, means word found, and we have to go back in text, from image, inside the
     *         link, <a href="... and find there the address with pronunciation page, which is returned
     */
    public static String getPronounciationAddressFromTranslation(String translationHtml, String wordToSearchFor) {
        String pronounciationIndicator = PRONUNC_STOPPER;
        if (!translationHtml.contains(pronounciationIndicator)) {
            return "no";
        }

        int indIndicator = 0;
        do {
            indIndicator = translationHtml.indexOf(pronounciationIndicator, indIndicator + 1);
            if (indIndicator == -1) {
                return "no";
            }
            String title = "title=\"";

            int indTitle = translationHtml.indexOf(title, indIndicator);

            if (indTitle == -1) {
                return "no";
            }

            int indNextQuote = translationHtml.indexOf("\"", indTitle + title.length());
            if (indNextQuote == -1) {
                return "no";
            }

            // Must be equal to the word translating
            String titleValue = translationHtml.substring(indTitle + title.length(), indNextQuote);

            if (!titleValue.contentEquals(wordToSearchFor)) {
                continue;
            }

            break;
            // indIndicator is pointing to the right one indicator!
        } while (true);

        String href = "href=\"";
        // Rolling back for the reference
        while (indIndicator > 0) {
            indIndicator -= 1;
            if (!translationHtml.substring(indIndicator, indIndicator + href.length()).contentEquals(href)) {
                continue;
            }

            break;
            // indIndicator contains where href starts;
        }

        int indNextQuote = translationHtml.indexOf("\"", indIndicator + href.length());
        if (indNextQuote == -1) {
            return "no";
        }

        String pronounciationAddress = translationHtml.substring(indIndicator + href.length(), indNextQuote);

        return "http://dict.tu-chemnitz.de" + pronounciationAddress;
    }


    // It searches for a link to mp3 file
    // First "mp3" is found, than it takes all the address, going before it.
    /**
     * @param pronunciationPageHtml
     * @return "no" is returned or the http address of the mp3 file
     */
    public static String getMp3AddressFromPronounciation(String pronunciationPageHtml) {
        if (pronunciationPageHtml.startsWith("FAILED")) {
            return "no";
        }

        String mp3 = MP3_STOPPER;

        if (!pronunciationPageHtml.contains(mp3)) {
            return "no";
        }

        int indMp3 = pronunciationPageHtml.indexOf(mp3);
        int indAddrEnd = indMp3 + ".mp3".length();

        int addrStart = 0;
        // Back to find the address start;
        while (indMp3 > 0) {
            indMp3 -= 1;
            if (pronunciationPageHtml.charAt(indMp3) == '\"') {
                addrStart = indMp3 + 1;
                break;
            }

        }

        return "http://dict.tu-chemnitz.de" + pronunciationPageHtml.substring(addrStart, indAddrEnd);

    }

}
