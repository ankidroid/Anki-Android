// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.deckpicker

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.Drawable
import androidx.core.graphics.drawable.toDrawable
import com.ichi2.utils.BitmapUtil
import com.ichi2.utils.ImageAlpha
import java.io.File

internal fun decodeDeckPickerBackground(
    context: Context,
    imageFile: File,
    srcWidth: Int,
    srcHeight: Int,
): Drawable? {
    val metrics = context.resources.displayMetrics
    return bitmapToBackgroundDrawable(
        context,
        decodeSubsampledBackgroundBitmap(
            file = imageFile,
            srcWidth = srcWidth,
            srcHeight = srcHeight,
            reqWidth = metrics.widthPixels,
            reqHeight = metrics.heightPixels,
        ),
    )
}

internal fun bitmapToBackgroundDrawable(
    context: Context,
    bitmap: Bitmap?,
): Drawable? {
    bitmap ?: return null
    return bitmap.toDrawable(context.resources)
}

internal fun decodeSubsampledBackgroundBitmap(
    file: File,
    srcWidth: Int,
    srcHeight: Int,
    reqWidth: Int,
    reqHeight: Int,
    decodeFile: (String, BitmapFactory.Options) -> Bitmap? = { path, options ->
        BitmapFactory.decodeFile(path, options)
    },
): Bitmap? {
    val options = BitmapFactory.Options()
    options.inSampleSize = BitmapUtil.calculateInSampleSize(srcWidth, srcHeight, reqWidth, reqHeight)
    options.inPreferredConfig = BitmapUtil.preferredConfig(ImageAlpha.hasAlpha(file))
    return decodeFile(file.absolutePath, options)
}
