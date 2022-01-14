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

/**
 * Text Field implementation.
 */
class TextField : FieldBase(), IField {
    private var mText = ""
    private var mName: String? = null
    override fun getType(): EFieldType {
        return EFieldType.TEXT
    }

    override fun setType(type: EFieldType): Boolean {
        return false
    }

    override fun isModified(): Boolean {
        return thisModified
    }

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
        return false
    }

    override fun getAudioPath(): String? {
        return null
    }

    override fun getText(): String {
        return mText
    }

    override fun setText(text: String): Boolean {
        mText = text
        setThisModified()
        return true
    }

    override fun setHasTemporaryMedia(hasTemporaryMedia: Boolean) {}
    override fun hasTemporaryMedia(): Boolean {
        // TODO Auto-generated method stub
        return false
    }

    override fun getName(): String {
        return mName!!
    }

    override fun setName(name: String) {
        mName = name
    }

    override fun getFormattedValue(): String {
        return text
    }

    override fun setFormattedString(col: Collection, value: String) {
        mText = value
    }

    companion object {
        private const val serialVersionUID = -6508967905716947525L
    }
}
