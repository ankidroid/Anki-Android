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
import com.ichi2.utils.KotlinCleanup
import java.io.File
import java.util.regex.Pattern

/**
 * Implementation of Audio field types
 */
@KotlinCleanup("want name & hasTemporaryMedia to be a property in the interface rather than a getter/setter")
abstract class AudioField : FieldBase(), IField {
    private var mAudioPath: String? = null
    protected var currentName: String? = null
    protected var currentHasTemporaryMedia = false
    abstract override fun getType(): EFieldType
    override fun setType(type: EFieldType): Boolean {
        return false
    }

    abstract override fun isModified(): Boolean
    override fun getHtml(): String? {
        return null
    }

    override fun setHtml(html: String): Boolean {
        return false
    }

    override fun setImagePath(pathToImage: String): Boolean {
        return false
    }

    override fun getImagePath(): String? {
        return null
    }

    override fun setAudioPath(pathToAudio: String?): Boolean {
        mAudioPath = pathToAudio
        setThisModified()
        return true
    }

    override fun getAudioPath(): String? {
        return mAudioPath
    }

    override fun getText(): String? {
        return null
    }

    override fun setText(text: String): Boolean {
        return false
    }

    abstract override fun setHasTemporaryMedia(hasTemporaryMedia: Boolean)
    abstract override fun hasTemporaryMedia(): Boolean
    abstract override fun getName(): String?
    abstract override fun setName(name: String)
    override fun getFormattedValue(): String {
        if (audioPath == null) {
            return ""
        }
        val file = File(audioPath!!)

        return if (file.exists()) String.format("[sound:%s]", file.name) else ""
    }

    override fun setFormattedString(col: Collection, value: String) {
        val p = Pattern.compile(PATH_REGEX)
        val m = p.matcher(value)
        var res = ""
        if (m.find()) {
            res = m.group(1)!!
        }
        val mediaDir = col.media.dir() + "/"
        audioPath = mediaDir + res
    }

    companion object {
        protected const val PATH_REGEX = "\\[sound:(.*)]"
    }
}
