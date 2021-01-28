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

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import timber.log.Timber;

/**
 * This class parses beolingus pages
 */
public class BeolingusParser {

    private static final Pattern PRONUNC_PATTERN = Pattern.compile("" +
            "<a href=\"([^\"]+)\"[^>]*>" +
            "<img src=\"/pics/s1[.]png\"[^>]*title=\"([^\"]+)\"[^>]*>");
    private static final Pattern MP3_PATTERN = Pattern.compile("href=\"([^\"]+\\.mp3)\">");

    /**
     * @param html HTML page from beolingus, with translation of the word we search
     * @return {@code "no"} or the pronunciation URL
     */
    public static String getPronunciationAddressFromTranslation(String html, String wordToSearchFor) {
        Matcher m = PRONUNC_PATTERN.matcher(html);
        while (m.find()) {
            //Perform .contains() due to #5376 (a "%20{noun}" suffix).
            //Perform .toLowerCase() due to #5810 ("hello" should match "Hello").
            //See #5810 for discussion on Locale complexities. Currently unhandled.
            if (m.group(2).toLowerCase(Locale.ROOT).contains(wordToSearchFor.toLowerCase(Locale.ROOT))) {
                Timber.d("pronunciation URL is https://dict.tu-chemnitz.de%s", m.group(1));
                return "https://dict.tu-chemnitz.de" + m.group(1);
            }
        }
        Timber.d("Unable to find pronunciation URL");
        return "no";
    }


    /**
     * @return {@code "no"}, or the http address of the mp3 file
     */
    public static String getMp3AddressFromPronounciation(String pronunciationPageHtml) {
        // Only log the page if you need to work with the regex
        // Timber.d("pronunciationPageHtml is %s", pronunciationPageHtml);
        Matcher m = MP3_PATTERN.matcher(pronunciationPageHtml);
        if (m.find()) {
            Timber.d("MP3 address is https://dict.tu-chemnitz.de%s", m.group(1));
            return "https://dict.tu-chemnitz.de" + m.group(1);
        }
        Timber.d("Unable to find MP3 file address");
        return "no";
    }

}
