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

package com.ichi2.anki.multimediacard.language

import java.util.*

/**
 * This is some sort of tool, which translates from languages in a user readable form to a code, used to invoke some
 * service. This code depends on service, of course.
 * <p>
 * Specific language listers derive from this one.
 */
open class LanguageListerBase {
    private val mLanguageMap: HashMap<String, String>

    /**
     * @param name
     * @param code This one has to be used in constructor to fill the hash map.
     */
    protected fun addLanguage(name: String, code: String) {
        mLanguageMap[name] = code
    }

    fun getCodeFor(Language: String): String? {
        return if (mLanguageMap.containsKey(Language)) {
            mLanguageMap[Language]
        } else {
            null
        }
    }

    val languages: ArrayList<String>
        get() {
            val res = ArrayList(mLanguageMap.keys)
            Collections.sort(res) { obj: String, str: String? -> obj.compareTo(str!!, ignoreCase = true) }
            return res
        }

    init {
        mLanguageMap = HashMap()
    }
}
