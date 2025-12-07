/*
 Copyright (c) $today.year Shaan Narendran Here <shaannaren06@gmail.com>

 This program is free software; you can redistribute it and/or modify it under
 the terms of the GNU General Public License as published by the Free Software
 Foundation; either version 3 of the License, or (at your option) any later
 version.

 This program is distributed in the hope that it will be useful, but WITHOUT ANY
 WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 PARTICULAR PURPOSE. See the GNU General Public License for more details.

 You should have received a copy of the GNU General Public License along with
 this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.ichi2.utils
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.text.Html
import android.widget.TextView
import androidx.core.graphics.drawable.toDrawable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.net.URL

// Class implemented for Issue #6669
class UrlImageGetter(
    private val textView: TextView,
    private val context: Context,
    private val scope: CoroutineScope,
) : Html.ImageGetter {
    override fun getDrawable(source: String?): Drawable {
        // just a holder to later inject our image
        val holder = BitmapDrawableWrapper(context.resources)
        // Checking for http/https to sanitise for security
        if (source != null && (source.startsWith("http://") || source.startsWith("https://"))) {
            scope.launch(Dispatchers.IO) {
                try {
                    val url = URL(source)

                    // Allows us to look at the dimensions of the file without loading it (to avoid memory issues)
                    val options = BitmapFactory.Options()
                    options.inJustDecodeBounds = true
                    url.openStream().use { stream ->
                        BitmapFactory.decodeStream(stream, null, options)
                    }

                    // shrinkage calculation in case the image size is very large
                    options.inSampleSize = calculateInSampleSize(options, 1024, 1024)
                    options.inJustDecodeBounds = false
                    val bitmap =
                        url.openStream().use { stream ->
                            BitmapFactory.decodeStream(stream, null, options)
                        }

                    withContext(Dispatchers.Main) {
                        if (bitmap != null) {
                            // Injects the image into the holder using the bitmap
                            val drawable = bitmap.toDrawable(context.resources)
                            drawable.setBounds(0, 0, bitmap.width, bitmap.height)
                            holder.drawable = drawable
                            holder.setBounds(0, 0, bitmap.width, bitmap.height)
                            textView.text = textView.text
                            textView.invalidate()
                        }
                    }
                } catch (e: Exception) {
                    Timber.tag("AnkiImageFix").e("Error: ${e.message}")
                }
            }
        }
        return holder
    }

    // Function to calculate the size required (in powers of 2) of the given image to be at least as big as what is required
    private fun calculateInSampleSize(
        options: BitmapFactory.Options,
        reqWidth: Int,
        reqHeight: Int,
    ): Int {
        val (height: Int, width: Int) = options.run { outHeight to outWidth }
        var inSampleSize = 1

        if (height > reqHeight || width > reqWidth) {
            val halfHeight: Int = height / 2
            val halfWidth: Int = width / 2
            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

    class BitmapDrawableWrapper(
        res: android.content.res.Resources,
    ) : BitmapDrawable(res, null as Bitmap?) {
        var drawable: Drawable? = null

        override fun draw(canvas: Canvas) {
            drawable?.draw(canvas)
        }
    }
}
