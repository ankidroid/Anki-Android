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

import android.content.ClipDescription
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.annotation.CheckResult
import com.ichi2.anki.multimediacard.fields.ImageField
import com.ichi2.anki.multimediacard.fields.MediaClipField
import com.ichi2.annotations.NeedsTest
import com.ichi2.compat.CompatHelper
import com.ichi2.libanki.exception.EmptyMediaException
import com.ichi2.utils.ClipboardUtil
import com.ichi2.utils.ContentResolverUtil.getFileName
import com.ichi2.utils.FileNameAndExtension
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

private typealias DisplayMediaError = (MediaRegistration.MediaError) -> Unit

/**
 * Utility class for media registration and handling errors during media paste actions.
 */
object MediaRegistration {
    /**
     * Represents different types of media errors.
     */
    sealed class MediaError {
        data object GenericError : MediaError()

        class ConversionError(
            val message: String,
        ) : MediaError()

        data object ImageTooLarge : MediaError()

        data object VideoTooLarge : MediaError()

        data object AudioTooLarge : MediaError()

        fun toHumanReadableString(context: Context): String =
            when (this) {
                is GenericError -> context.getString(R.string.multimedia_editor_something_wrong)
                is ConversionError -> context.getString(R.string.multimedia_editor_png_paste_error, message)
                is ImageTooLarge -> context.getString(R.string.note_editor_image_too_large)
                is VideoTooLarge -> context.getString(R.string.note_editor_video_too_large)
                is AudioTooLarge -> context.getString(R.string.note_editor_audio_too_large)
            }
    }

    /**
     * Maximum allowed media file size in bytes.
     * The limit is set to 5 MB.
     */
    private const val MEDIA_MAX_SIZE_BYTES = 5 * 1000 * 1000

    /**
     * Handles the paste action for media.
     *
     * @param context The application context.
     * @param uri The URI of the media to be pasted.
     * @param description The description of the clipboard content.
     * @param pasteAsPng A flag indicating whether to convert the media to PNG format.
     * @param showError A callback function for displaying error messages based on media error type.
     * @return A string reference to the media if successfully processed, or null if an error occurred.
     */
    fun onPaste(
        context: Context,
        uri: Uri,
        description: ClipDescription,
        pasteAsPng: Boolean,
        showError: DisplayMediaError,
    ): String? =
        try {
            loadMediaIntoCollection(context, uri, description, pasteAsPng, showError)
        } catch (ex: NullPointerException) {
            // Tested under FB Messenger and GMail, both apps do nothing if this occurs.
            // This typically works if the user copies again - don't know the exact cause

            //  java.lang.SecurityException: Permission Denial: opening provider
            //  org.chromium.chrome.browser.util.ChromeFileProvider from ProcessRecord{80125c 11262:com.ichi2.anki/u0a455}
            //  (pid=11262, uid=10455) that is not exported from UID 10057
            Timber.w(ex, "Failed to paste media")
            showError(MediaError.GenericError)
            null
        } catch (ex: SecurityException) {
            Timber.w(ex, "Failed to paste media")
            showError(MediaError.GenericError)
            null
        } catch (e: Exception) {
            // NOTE: This is happy path coding which works on Android 9.
            CrashReportService.sendExceptionReport("File is invalid issue:8880", "RegisterMediaForWebView:onImagePaste URI of file:$uri")
            Timber.w(e, "Failed to paste media")
            showError(MediaError.GenericError)
            null
        }

    /**
     * Checks if the media file size exceeds the allowed limit.
     *
     * @param bytesWritten The size of the media file in bytes.
     * @param isImage `true` if the media is an image, otherwise `false`.
     * @param isVideo `true` if the media is a video, otherwise `false`.
     * @param showError A callback function to display an error message if the file exceeds the size limit.
     *                  It receives a [MediaError] type and an optional error message.
     * @return `true` if the file size is within the allowed limit, otherwise `false`.
     */
    @NeedsTest("all cases should work as expected")
    private fun checkMediaSize(
        bytesWritten: Long,
        isImage: Boolean,
        isVideo: Boolean,
        showError: DisplayMediaError,
    ): Boolean {
        if (bytesWritten <= MEDIA_MAX_SIZE_BYTES) return true

        when {
            isImage -> showError(MediaError.ImageTooLarge)
            isVideo -> showError(MediaError.VideoTooLarge)
            else -> showError(MediaError.AudioTooLarge)
        }
        return false
    }

    /**
     * Loads media into the collection.media directory and returns a HTML reference
     * @param uri The uri of the image to load
     * @return HTML referring to the loaded image
     */
    // TODO: remove the Android dependencies and handle them outside the method
    @Throws(IOException::class)
    fun loadMediaIntoCollection(
        context: Context,
        uri: Uri,
        description: ClipDescription,
        pasteAsPng: Boolean,
        showError: DisplayMediaError,
    ): String? {
        val filename = getFileName(context.contentResolver, uri)
        val (fileName, fileExtensionWithDot) =
            FileNameAndExtension
                .fromString(filename)
                ?.renameForCreateTempFile()
                ?: throw IllegalStateException("Unable to determine valid filename")
        var clipCopy: File = File.createTempFile(fileName, fileExtensionWithDot)
        var bytesWritten: Long = 0
        val isImage = ClipboardUtil.hasImage(description)
        val isVideo = ClipboardUtil.hasVideo(description)

        // Opens InputStream, copies to file, and converts to PNG if needed. Returns null on failure.
        context.contentResolver.openInputStream(uri).use { fd ->
            if (fd == null) return@use
            if (pasteAsPng) {
                clipCopy = File.createTempFile(fileName, ".png")
                bytesWritten = CompatHelper.compat.copyFile(fd, clipCopy.absolutePath)
                if (!convertToPNG(clipCopy, showError)) {
                    return null
                }
            } else {
                bytesWritten = CompatHelper.compat.copyFile(fd, clipCopy.absolutePath)
            }
        }
        val tempFilePath = clipCopy.absolutePath

        val checkMediaSize = checkMediaSize(bytesWritten, isImage, isVideo, showError)

        if (!checkMediaSize) {
            File(tempFilePath).delete()
            clipCopy.delete()
            return null
        }

        // register media for webView
        if (!registerMediaForWebView(tempFilePath)) {
            clipCopy.delete()
            return null
        }
        Timber.d("File was %d bytes", bytesWritten)

        val field = if (isImage) ImageField() else MediaClipField()

        field.hasTemporaryMedia = true
        field.mediaPath = tempFilePath
        return field.formattedValue
    }

    @NeedsTest("check the output if being converted")
    private fun convertToPNG(
        file: File,
        showError: DisplayMediaError,
    ): Boolean {
        val bm = BitmapFactory.decodeFile(file.absolutePath)
        try {
            FileOutputStream(file.absolutePath).use { outStream ->
                bm.compress(Bitmap.CompressFormat.PNG, 100, outStream)
                outStream.flush()
            }
        } catch (e: IOException) {
            Timber.w("MediaRegistration : Unable to convert file to png format")
            CrashReportService.sendExceptionReport(e, "Unable to convert file to png format")
            showError(MediaError.ConversionError(e.message ?: ""))
            return false
        }
        return true
    }

    @CheckResult
    fun registerMediaForWebView(mediaPath: String?): Boolean {
        if (mediaPath == null) {
            // Nothing to register - continue with execution.
            return true
        }
        Timber.i("Adding media to collection: %s", mediaPath)
        val f = File(mediaPath)
        return try {
            CollectionManager.getColUnsafe().media.addFile(f)
            true
        } catch (e: IOException) {
            Timber.w(e, "Failed to add file")
            false
        } catch (e: EmptyMediaException) {
            Timber.w(e, "Failed to add file")
            false
        }
    }
}
