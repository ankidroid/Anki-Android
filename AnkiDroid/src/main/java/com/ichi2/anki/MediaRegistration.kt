/*
 * Copyright (c) 2021 Akshay Jadhav <jadhavakshay0701@gmail.com>
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.ichi2.anki

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.annotation.CheckResult
import com.ichi2.anki.UIUtils.showThemedToast
import com.ichi2.anki.multimediacard.fields.ImageField
import com.ichi2.compat.CompatHelper
import com.ichi2.libanki.exception.EmptyMediaException
import com.ichi2.utils.ContentResolverUtil.getFileName
import com.ichi2.utils.FileUtil.getFileNameAndExtension
import timber.log.Timber
import java.io.*

/**
 * RegisterMediaForWebView is used for registering media in temp path,
 * this class is required in summer note class for paste image event and in visual editor activity for importing media,
 * (extracted code to avoid duplication of code).
 */
class MediaRegistration(private val context: Context) {
    // Use the same HTML if the same image is pasted multiple times.
    private val pastedImageCache = HashMap<String, String?>()

    /**
     * Loads an image into the collection.media directory and returns a HTML reference
     * @param uri The uri of the image to load
     * @return HTML referring to the loaded image
     */
    @Throws(IOException::class)
    fun loadImageIntoCollection(uri: Uri): String? {
        val fileName: String
        val filename = getFileName(context.contentResolver, uri)
        val fd = openInputStreamWithURI(uri)
        val fileNameAndExtension = getFileNameAndExtension(filename)
        fileName = if (checkFilename(fileNameAndExtension!!)) {
            "${fileNameAndExtension.key}-name"
        } else {
            fileNameAndExtension.key
        }
        var clipCopy: File
        var bytesWritten: Long
        openInputStreamWithURI(uri).use { copyFd ->
            // no conversion to jpg in cases of gif and jpg and if png image with alpha channel
            if (shouldConvertToJPG(fileNameAndExtension.value, copyFd)) {
                clipCopy = File.createTempFile(fileName, ".jpg")
                bytesWritten = CompatHelper.compat.copyFile(fd, clipCopy.absolutePath)
                // return null if jpg conversion false.
                if (!convertToJPG(clipCopy)) {
                    return null
                }
            } else {
                clipCopy = File.createTempFile(fileName, fileNameAndExtension.value)
                bytesWritten = CompatHelper.compat.copyFile(fd, clipCopy.absolutePath)
            }
        }
        val tempFilePath = clipCopy.absolutePath
        // register media for webView
        if (!registerMediaForWebView(tempFilePath)) {
            return null
        }
        Timber.d("File was %d bytes", bytesWritten)
        if (bytesWritten > MEDIA_MAX_SIZE) {
            Timber.w("File was too large: %d bytes", bytesWritten)
            showThemedToast(context, context.getString(R.string.note_editor_paste_too_large), false)
            File(tempFilePath).delete()
            return null
        }
        val field = ImageField()
        field.hasTemporaryMedia = true
        field.extraImagePathRef = tempFilePath
        return field.formattedValue
    }

    @Throws(FileNotFoundException::class)
    private fun openInputStreamWithURI(uri: Uri): InputStream {
        return context.contentResolver.openInputStream(uri)!!
    }

    private fun convertToJPG(file: File): Boolean {
        val bm = BitmapFactory.decodeFile(file.absolutePath)
        try {
            FileOutputStream(file.absolutePath).use { outStream ->
                bm.compress(Bitmap.CompressFormat.JPEG, 100, outStream)
                outStream.flush()
            }
        } catch (e: IOException) {
            Timber.w("MediaRegistration : Unable to convert file to png format")
            CrashReportService.sendExceptionReport(e, "Unable to convert file to png format")
            showThemedToast(context, context.resources.getString(R.string.multimedia_editor_png_paste_error, e.message), true)
            return false
        }
        return true // successful conversion to jpg.
    }

    private fun shouldConvertToJPG(fileNameExtension: String, fileStream: InputStream): Boolean {
        if (".jpg" == fileNameExtension) {
            return false // we are already a jpg, no conversion
        }
        if (".gif" == fileNameExtension) {
            return false // gifs may have animation, conversion would ruin them
        }
        if (".png" == fileNameExtension && doesInputStreamContainTransparency(fileStream)) {
            return false // pngs with transparency would be ruined by conversion
        }
        return true
    }

    private fun checkFilename(fileNameAndExtension: Map.Entry<String, String>): Boolean {
        return fileNameAndExtension.key.length <= 3
    }

    fun onImagePaste(uri: Uri): String? {
        return try {
            // check if cache already holds registered file or not
            if (!pastedImageCache.containsKey(uri.toString())) {
                pastedImageCache[uri.toString()] = loadImageIntoCollection(uri)
            }
            pastedImageCache[uri.toString()]
        } catch (ex: NullPointerException) {
            // Tested under FB Messenger and GMail, both apps do nothing if this occurs.
            // This typically works if the user copies again - don't know the exact cause

            //  java.lang.SecurityException: Permission Denial: opening provider
            //  org.chromium.chrome.browser.util.ChromeFileProvider from ProcessRecord{80125c 11262:com.ichi2.anki/u0a455}
            //  (pid=11262, uid=10455) that is not exported from UID 10057
            Timber.w(ex, "Failed to paste image")
            null
        } catch (ex: SecurityException) {
            Timber.w(ex, "Failed to paste image")
            null
        } catch (e: Exception) {
            // NOTE: This is happy path coding which works on Android 9.
            CrashReportService.sendExceptionReport("File is invalid issue:8880", "RegisterMediaForWebView:onImagePaste URI of file:$uri")
            Timber.w(e, "Failed to paste image")
            showThemedToast(context, context.getString(R.string.multimedia_editor_something_wrong), false)
            null
        }
    }

    @CheckResult
    fun registerMediaForWebView(imagePath: String?): Boolean {
        if (imagePath == null) {
            // Nothing to register - continue with execution.
            return true
        }
        Timber.i("Adding media to collection: %s", imagePath)
        val f = File(imagePath)
        return try {
            CollectionHelper.instance.getColUnsafe(context)!!.media.addFile(f)
            true
        } catch (e: IOException) {
            Timber.w(e, "Failed to add file")
            false
        } catch (e: EmptyMediaException) {
            Timber.w(e, "Failed to add file")
            false
        }
    }

    companion object {
        private const val MEDIA_MAX_SIZE = 5 * 1000 * 1000
        private const val COLOR_GREY = 0
        private const val COLOR_TRUE = 2
        private const val COLOR_INDEX = 3
        private const val COLOR_GREY_ALPHA = 4
        private const val COLOR_TRUE_ALPHA = 6

        /**
         * given an inputStream of a file,
         * returns true if found that it has transparency (in its header)
         * code: https://stackoverflow.com/a/31311718/14148406
         */
        private fun doesInputStreamContainTransparency(inputStream: InputStream): Boolean {
            try {
                // skip: png signature,header chunk declaration,width,height,bitDepth :
                inputStream.skip((12 + 4 + 4 + 4 + 1).toLong())
                when (inputStream.read()) {
                    COLOR_GREY_ALPHA, COLOR_TRUE_ALPHA -> return true
                    COLOR_INDEX, COLOR_GREY, COLOR_TRUE -> return false
                }
                return true
            } catch (e: Exception) {
                Timber.w(e, "Failed to check transparency of inputStream")
            }
            return false
        }
    }
}
