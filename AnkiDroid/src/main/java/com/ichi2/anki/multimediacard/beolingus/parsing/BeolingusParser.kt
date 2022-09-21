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
package com.ichi2.anki.multimediacard.beolingus.parsing

import com.ichi2.utils.KotlinCleanup
import org.intellij.lang.annotations.Language
import timber.log.Timber
import java.util.regex.Pattern

/**
 * This class parses beolingus pages
 */
object BeolingusParser {
    private val PRONUNCIATION_PATTERN = Pattern.compile(
        "<a href=\"([^\"]+)\"[^>]*>" +
            "<img src=\"/pics/s1[.]png\"[^>]*title=\"([^\"]+)\"[^>]*>"
    )
    private val MP3_PATTERN = Pattern.compile("href=\"([^\"]+\\.mp3)\">")

    /**
     * @param html HTML page from beolingus, with translation of the word we search
     * @return `"no"` or the pronunciation URL
     */
    @KotlinCleanup("AFTER fixing @KotlinCleanup in LoadPronunciationActivity see if wordToSearchFor can be made non null")
    fun getPronunciationAddressFromTranslation(@Language("HTML") html: String, wordToSearchFor: String?): String {
        val m = PRONUNCIATION_PATTERN.matcher(html)
        while (m.find()) {
            // Perform .contains() due to #5376 (a "%20{noun}" suffix).
            // See #5810 for discussion on Locale complexities. Currently unhandled.
            @KotlinCleanup("improve null handling of m.group() possibly returning null")
            if (m.group(2)!!.contains(wordToSearchFor!!, ignoreCase = true)) {
                Timber.d("pronunciation URL is https://dict.tu-chemnitz.de%s", m.group(1))
                return "https://dict.tu-chemnitz.de" + m.group(1)
            }
        }
        Timber.d("Unable to find pronunciation URL")
        return "no"
    }

    /**
     * @return `"no"`, or the http address of the mp3 file
     */
    fun getMp3AddressFromPronunciation(@Language("HTML") pronunciationPageHtml: String): String {
        // Only log the page if you need to work with the regex
        // Timber.d("pronunciationPageHtml is %s", pronunciationPageHtml);
        val m = MP3_PATTERN.matcher(pronunciationPageHtml)
        if (m.find()) {
            Timber.d("MP3 address is https://dict.tu-chemnitz.de%s", m.group(1))
            return "https://dict.tu-chemnitz.de" + m.group(1)
        }
        Timber.d("Unable to find MP3 file address")
        return "no"
    }
}
