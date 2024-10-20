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

import android.net.Uri
import androidx.annotation.CheckResult
import androidx.annotation.VisibleForTesting
import com.ichi2.annotations.NeedsTest
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
    var extraImagePathRef: String? = null
    private var _name: String? = null

    override val type: EFieldType = EFieldType.IMAGE

    override val isModified: Boolean
        get() = thisModified

    override var mediaPath: String?
        get() = extraImagePathRef
        set(value) {
            extraImagePathRef = value
            setThisModified()
        }

    override var text: String? = null

    override var hasTemporaryMedia: Boolean = false

    override var name: String?
        get() = _name
        set(value) {
            _name = value
        }

    override val formattedValue: String
        get() {
            val file = File(mediaPath!!)
            return formatImageFileName(file)
        }

    override fun setFormattedString(col: Collection, value: String) {
        extraImagePathRef = getImageFullPath(col, value)
    }

    companion object {
        private const val SERIAL_VERSION_UID = 4431611060655809687L

        @VisibleForTesting
        @NeedsTest("files with HTML illegal chars can be imported and rendered")
        fun formatImageFileName(file: File): String {
            return if (file.exists()) {
                val encodedName = Uri.encode(file.name)
                """<img src="$encodedName">"""
            } else {
                ""
            }
        }

        @VisibleForTesting
        fun getImageFullPath(col: Collection, value: String): String {
            val path = parseImageSrcFromHtml(value)

            return if (path.isNotEmpty()) {
                "${col.media.dir}/$path"
            } else {
                ""
            }
        }

        @VisibleForTesting
        @CheckResult
        fun parseImageSrcFromHtml(html: String): String {
            return try {
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
