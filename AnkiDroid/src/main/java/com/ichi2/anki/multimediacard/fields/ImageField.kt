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
import com.ichi2.utils.KotlinCleanup
import org.jsoup.Jsoup
import timber.log.Timber
import java.io.File

/**
 * Field with an image.
 */
@KotlinCleanup("convert properties to single-line overrides")
class ImageField : FieldBase(), IField {
    @get:JvmName("getImagePath_unused")
    var imagePath: String? = null
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
        imagePath = pathToImage
        setThisModified()
        return true
    }

    override fun getImagePath(): String? {
        return imagePath
    }

    override fun setAudioPath(pathToAudio: String?): Boolean {
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
        val file = File(getImagePath()!!)
        return formatImageFileName(file)
    }

    override fun setFormattedString(col: Collection, value: String) {
        imagePath = getImageFullPath(col, value)
    }

    companion object {
        private const val serialVersionUID = 4431611060655809687L
        @VisibleForTesting
        fun formatImageFileName(file: File): String {
            return if (file.exists()) {
                String.format("<img src=\"%s\">", file.name)
            } else {
                ""
            }
        }

        @VisibleForTesting
        @KotlinCleanup("remove ? from value")
        fun getImageFullPath(col: Collection, value: String?): String {
            val path = parseImageSrcFromHtml(value)
            if ("" == path) {
                return ""
            }
            val mediaDir = col.media.dir() + "/"
            return mediaDir + path
        }

        @VisibleForTesting
        @CheckResult
        @KotlinCleanup("remove ? from html")
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
