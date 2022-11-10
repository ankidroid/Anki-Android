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

    override val type: EFieldType = EFieldType.TEXT

    override val isModified: Boolean
        get() = thisModified

    override var imagePath: String? = null

    override var audioPath: String? = null

    override var text: String?
        get() = mText
        set(value) {
            mText = value!!
            setThisModified()
        }

    override var hasTemporaryMedia: Boolean = false

    override var name: String?
        get() = mName
        set(value) {
            mName = value
        }

    override val formattedValue: String?
        get() = text

    override fun setFormattedString(col: Collection, value: String) {
        mText = value
    }

    companion object {
        private const val serialVersionUID = -6508967905716947525L
    }
}
