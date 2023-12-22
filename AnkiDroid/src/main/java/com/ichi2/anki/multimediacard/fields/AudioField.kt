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

package com.ichi2.anki.multimediacard.fields

import com.ichi2.libanki.Collection
import java.io.File
import java.util.regex.Pattern

/**
 * Implementation of Audio field types
 */
abstract class AudioField : FieldBase(), IField {
    private var mAudioPath: String? = null

    override var imagePath: String? = null

    override var audioPath: String?
        get() = mAudioPath
        set(value) {
            mAudioPath = value
            setThisModified()
        }

    override var text: String? = null

    override var hasTemporaryMedia: Boolean = false

    override val formattedValue: String
        get() =
            audioPath?.let { path ->
                val file = File(path)
                if (file.exists()) "[sound:${file.name}]" else ""
            } ?: ""

    override fun setFormattedString(
        col: Collection,
        value: String,
    ) {
        val p = Pattern.compile(PATH_REGEX)
        val m = p.matcher(value)
        var res = ""
        if (m.find()) {
            res = m.group(1)!!
        }
        val mediaDir = col.media.dir + "/"
        audioPath = mediaDir + res
    }

    companion object {
        protected const val PATH_REGEX = "\\[sound:(.*)]"
    }
}
