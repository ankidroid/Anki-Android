/****************************************************************************************
 * Copyright (c) 2013 Bibek Shrestha <bibekshrestha@gmail.com>                          *
 * Copyright (c) 2013 Zaur Molotnikov <qutorial@gmail.com>                              *
 * Copyright (c) 2013 Nicolas Raoul <nicolas.raoul@gmail.com>                           *
 * Copyright (c) 2013 Flavio Lerda <flerda@gmail.com>                                   *
 * Copyright (c) 2014 Timothy Rae <perceptualchaos2@gmail.com>                          *
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

package com.ichi2.anki.multimediacard.language

import com.ichi2.utils.HashUtil.HashMapInit
import java.util.HashMap
import java.util.Locale

/**
 * This language lister is used to call glosbe.com translation services.
 * <p>
 * Glosbe expects the languages to follow the ISO 639-3 codes.
 * <p>
 * It can be extended freely here, to support more languages.
 */
class LanguagesListerGlosbe : LanguageListerBase() {
    companion object {
        private var locale_map: HashMap<String, Locale>? = null

        /**
         * Convert from 3 letter ISO 639-2 language code to ISO 639-1
         * @param req 3 letter language code
         * @return 2 letter language code
         */
        @JvmStatic
        fun requestToResponseLangCode(req: String): String {
            if (locale_map == null) {
                val languages = Locale.getISOLanguages()
                locale_map = HashMapInit(languages.size)
                for (language in languages) {
                    val locale = Locale(language)
                    locale_map!![locale.isO3Language] = locale
                }
            }
            return locale_map!![req]!!.language
        }
    }

    init {
        val languages = arrayOf(
            "eng", "deu", "jpn", "fra", "spa", "pol", "ita", "rus",
            "ces", "zho", "nld", "por", "swe", "hrv", "hin", "hun", "vie", "ara", "tur"
        )
        // Java Locale uses ISO 639-2 rather than 639-3 so we currently only support the subset of
        // the languages on Glosbe which are in ISO 639-2. "Chinese Mandarin" ("cmn") for example
        // is not supported, but "Chinese" ("zho") is.
        for (l in languages) {
            val locale = Locale(l)
            addLanguage(locale.displayLanguage, locale.isO3Language)
        }
    }
}
