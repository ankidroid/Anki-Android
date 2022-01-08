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

import androidx.annotation.CheckResult
import androidx.annotation.VisibleForTesting
import com.ichi2.libanki.Collection
import org.jsoup.Jsoup
import timber.log.Timber
import java.io.File

/**
 * Field with an image.
 */
class ImageField : FieldBase(), IField {
    private var mImagePath: String? = null
    private var mHasTemporaryMedia = false
    private var mName: String? = null
    override fun getType(): EFieldType {
        return EFieldType.IMAGE
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
        mImagePath = pathToImage
        setThisModified()
        return true
    }

    override fun getImagePath(): String? {
        return mImagePath
    }

    override fun setAudioPath(pathToAudio: String): Boolean {
        return false
    }

    override fun getAudioPath(): String? {
        return null
    }

    override fun getText(): String? {
        return null
    }

    override fun setText(text: String): Boolean {
        return false
    }

    override fun setHasTemporaryMedia(hasTemporaryMedia: Boolean) {
        mHasTemporaryMedia = hasTemporaryMedia
    }

    override fun hasTemporaryMedia(): Boolean {
        return mHasTemporaryMedia
    }

    override fun getName(): String {
        return mName!!
    }

    override fun setName(name: String) {
        mName = name
    }

    override fun getFormattedValue(): String {
        val file = File(imagePath!!)
        return formatImageFileName(file)
    }

    override fun setFormattedString(col: Collection, value: String) {
        setImagePath(getImageFullPath(col, value))
    }

    companion object {
        private const val serialVersionUID = 4431611060655809687L
        @JvmStatic
        @VisibleForTesting
        fun formatImageFileName(file: File): String {
            return if (file.exists()) {
                String.format("<img src=\"%s\">", file.name)
            } else {
                ""
            }
        }

        @JvmStatic
        @VisibleForTesting
        fun getImageFullPath(col: Collection, value: String): String {
            val path = parseImageSrcFromHtml(value)
            if ("" == path) {
                return ""
            }
            val mediaDir = col.media.dir() + "/"
            return mediaDir + path
        }

        @JvmStatic
        @VisibleForTesting
        @CheckResult
        fun parseImageSrcFromHtml(html: String?): String {
            return if (html == null) {
                ""
            } else try {
                val doc = Jsoup.parseBodyFragment(html)
                val image = doc.selectFirst("img[src]") ?: return ""
                image.attr("src")
            } catch (e: Exception) {
                Timber.w(e)
                ""
            }
        }
    }
}
