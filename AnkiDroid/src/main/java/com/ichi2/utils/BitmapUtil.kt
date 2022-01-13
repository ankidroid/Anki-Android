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

package com.ichi2.utils

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.widget.ImageView
import com.ichi2.anki.AnkiDroidApp
import timber.log.Timber
import java.io.File
import java.io.FileInputStream
import java.lang.Exception

object BitmapUtil {
    @JvmStatic
    fun decodeFile(theFile: File, IMAGE_MAX_SIZE: Int): Bitmap? {
        var bmp: Bitmap? = null
        try {
            if (!theFile.exists()) {
                Timber.i("not displaying preview - image does not exist: '%s'", theFile.path)
                return null
            }
            // Decode image size
            val o = BitmapFactory.Options()
            o.inJustDecodeBounds = true
            var fis: FileInputStream? = null
            try {
                fis = FileInputStream(theFile)
                BitmapFactory.decodeStream(fis, null, o)
            } finally {
                fis?.close()
            }
            var scale = 1
            if (o.outHeight > IMAGE_MAX_SIZE || o.outWidth > IMAGE_MAX_SIZE) {
                scale = Math.pow(
                    2.0,
                    Math.round(
                        Math.log(IMAGE_MAX_SIZE / Math.max(o.outHeight, o.outWidth).toDouble()) /
                            Math.log(0.5)
                    ).toDouble()
                ).toInt()
            }

            // Decode with inSampleSize
            val o2 = BitmapFactory.Options()
            o2.inSampleSize = scale
            try {
                fis = FileInputStream(theFile)
                bmp = BitmapFactory.decodeStream(fis, null, o2)
            } finally {
                fis!!.close() // don't need a null check, as we reuse the variable.
            }
        } catch (e: Exception) {
            // #5513 - We don't know the reason for the crash, let's find out.
            AnkiDroidApp.sendExceptionReport(e, "BitmapUtil decodeFile")
        }
        return bmp
    }

    @JvmStatic
    fun freeImageView(imageView: ImageView?) {
        // This code behaves differently on various OS builds. That is why put into try catch.
        try {
            if (imageView != null) {
                @Suppress("UNUSED_VARIABLE")
                val dr = (imageView.drawable ?: return) as? BitmapDrawable ?: return
                val bd = imageView.drawable as BitmapDrawable
                if (bd.bitmap != null) {
                    bd.bitmap.recycle()
                    imageView.setImageBitmap(null)
                }
            }
        } catch (e: Exception) {
            Timber.e(e)
        }
    }
}
