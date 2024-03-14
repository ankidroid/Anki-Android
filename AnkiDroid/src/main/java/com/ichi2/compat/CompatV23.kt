/***************************************************************************************
 * Copyright (c) 2017 Profpatsch <mail@profpatsch.de>                                   *
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

package com.ichi2.compat

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.Bitmap
import android.media.MediaRecorder
import android.media.ThumbnailUtils
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.Vibrator
import android.provider.MediaStore
import android.view.View
import androidx.appcompat.widget.TooltipCompat
import com.ichi2.utils.KotlinCleanup
import timber.log.Timber
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.io.Serializable
import java.util.Locale

/** Baseline implementation of [Compat]. Check [Compat]'s for more detail.  */
@KotlinCleanup("add extension method logging file.delete() failure" + "Fix Deprecation")
@Suppress("Deprecation")
open class CompatV23 : Compat {
    // Until API26, ignore notification channels
    override fun setupNotificationChannel(context: Context) { /* pre-API26, do nothing */
    }

    // Until API26, tooltips cannot be defined declaratively in layouts
    override fun setTooltipTextByContentDescription(view: View) {
        TooltipCompat.setTooltipText(view, view.contentDescription)
    }

    // Until API 26 just specify time, after that specify effect also
    override fun vibrate(context: Context, durationMillis: Long) {
        val vibratorManager = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator?
        vibratorManager?.vibrate(durationMillis)
    }

    // Until API 26 do the copy using streams
    @Throws(IOException::class)
    override fun copyFile(source: String, target: String) {
        try {
            FileInputStream(source).use { fileInputStream -> copyFile(fileInputStream, target) }
        } catch (e: IOException) {
            Timber.e(e, "copyFile() error copying source %s", source)
            throw e
        }
    }

    // Until API 26 do the copy using streams
    @Throws(IOException::class)
    override fun copyFile(source: String, target: OutputStream): Long {
        var count: Long
        try {
            FileInputStream(source).use { fileInputStream -> count = copyFile(fileInputStream, target) }
        } catch (e: IOException) {
            Timber.e(e, "copyFile() error copying source %s", source)
            throw e
        }
        return count
    }

    // Until API 26 do the copy using streams
    @Throws(IOException::class)
    override fun copyFile(source: InputStream, target: String): Long {
        var bytesCopied: Long
        try {
            FileOutputStream(target).use { targetStream -> bytesCopied = copyFile(source, targetStream) }
        } catch (ioe: IOException) {
            Timber.e(ioe, "Error while copying to file %s", target)
            throw ioe
        }
        return bytesCopied
    }

    // Internal implementation under the API26 copyFile APIs
    @Throws(IOException::class)
    private fun copyFile(source: InputStream, target: OutputStream): Long {
        // balance memory and performance, it appears 32k is the best trade-off
        // https://stackoverflow.com/questions/10143731/android-optimal-buffer-size
        val buffer = ByteArray(1024 * 32)
        var count: Long = 0
        var n: Int
        @KotlinCleanup("This code feels hard to read, Improve readability")
        while (source.read(buffer).also { n = it } != -1) {
            target.write(buffer, 0, n)
            count += n.toLong()
        }
        target.flush()
        return count
    }

    // Until API 26
    /* This method actually read the full content of the directory.
    * It is linear in time and space in the number of file and directory in the directory.
    * However, hasNext and next should be constant in time and space. */
    @Throws(IOException::class)
    override fun contentOfDirectory(directory: File): FileStream {
        val paths = directory.listFiles()
        if (paths == null) {
            if (!directory.exists()) {
                throw FileNotFoundException(directory.path)
            }
            throw IOException("Directory " + directory.path + "'s file can not be listed. Probable cause are that it's not a directory (which violate the method's assumption) or a permission issue.")
        }
        val length = paths.size
        return object : FileStream {
            override fun close() {
                // No op. Nothing to close here.
            }

            private var mOrd = 0
            override operator fun hasNext(): Boolean {
                return mOrd < length
            }

            override operator fun next(): File {
                return paths[mOrd++]
            }
        }
    }

    // Until API 26
    @Throws(IOException::class)
    override fun deleteFile(file: File) {
        if (!file.delete()) {
            if (!file.exists()) {
                throw FileNotFoundException(file.canonicalPath)
            }
            throw IOException("Unable to delete: " + file.canonicalPath)
        }
    }

    // Until API 26
    @Throws(IOException::class)
    override fun createDirectories(directory: File) {
        if (directory.exists()) {
            if (!directory.isDirectory) {
                throw IOException("$directory is not a directory")
            }
            return
        }
        if (!directory.mkdirs()) {
            throw IOException("Failed to create $directory")
        }
    }

    // Until API 29
    @Throws(FileNotFoundException::class)
    override fun saveImage(context: Context, bitmap: Bitmap, baseFileName: String, extension: String, format: Bitmap.CompressFormat, quality: Int): Uri {
        val pictures = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
        val ankiDroidDirectory = File(pictures, "AnkiDroid")
        if (!ankiDroidDirectory.exists()) {
            ankiDroidDirectory.mkdirs()
        }
        val imageFile = File(ankiDroidDirectory, "$baseFileName.$extension")
        bitmap.compress(format, quality, FileOutputStream(imageFile))
        return Uri.fromFile(imageFile)
    }

    // Until API 29
    override fun hasVideoThumbnail(path: String): Boolean? {
        return try {
            ThumbnailUtils.createVideoThumbnail(path, MediaStore.Images.Thumbnails.MINI_KIND) != null
        } catch (e: Exception) {
            null
        }
    }

    // Until API31 the MediaRecorder constructor was default, ignoring the Context
    override fun getMediaRecorder(context: Context): MediaRecorder {
        return MediaRecorder()
    }

    // Until API 33
    override fun resolveActivity(
        packageManager: PackageManager,
        intent: Intent,
        flags: ResolveInfoFlagsCompat
    ): ResolveInfo? {
        return packageManager.resolveActivity(intent, flags.value.toInt())
    }

    // Until API 33
    override fun resolveService(
        packageManager: PackageManager,
        intent: Intent,
        flags: ResolveInfoFlagsCompat
    ): ResolveInfo? {
        return packageManager.resolveService(intent, flags.value.toInt())
    }

    // Until API 33
    override fun queryIntentActivities(
        packageManager: PackageManager,
        intent: Intent,
        flags: ResolveInfoFlagsCompat
    ): List<ResolveInfo> {
        return packageManager.queryIntentActivities(intent, flags.value.toInt())
    }

    // Until API 33
    override fun <T : Serializable?> getSerializableExtra(
        intent: Intent,
        name: String,
        className: Class<T>
    ): T? {
        return try {
            @Suppress("UNCHECKED_CAST")
            intent.getSerializableExtra(name) as? T?
        } catch (e: Exception) {
            return null
        }
    }

    // Until API 33
    override fun getPackageInfo(packageManager: PackageManager, packageName: String, flags: PackageInfoFlagsCompat): PackageInfo? =
        packageManager.getPackageInfo(packageName, flags.value.toInt())

    // Until API 33
    @Suppress("UNCHECKED_CAST")
    override fun <T : Serializable?> getSerializable(
        bundle: Bundle,
        key: String,
        clazz: Class<T>
    ): T? = bundle.getSerializable(key) as? T?

    override fun normalize(locale: Locale): Locale {
        // normalises to "spa_MEX"
        val iso3Code = getIso3Code(locale) ?: return locale
        // convert back from this key to a two-letter mapping
        return twoLetterSystemLocaleMapping[iso3Code] ?: locale
    }

    override val AXIS_RELATIVE_X: Int = 27
    override val AXIS_RELATIVE_Y: Int = 28
    override val AXIS_GESTURE_X_OFFSET: Int = 48
    override val AXIS_GESTURE_Y_OFFSET: Int = 49
    override val AXIS_GESTURE_SCROLL_X_DISTANCE: Int = 50
    override val AXIS_GESTURE_SCROLL_Y_DISTANCE: Int = 51
    override val AXIS_GESTURE_PINCH_SCALE_FACTOR: Int = 52

    companion object {
        /**
         * Maps from the ISO 3 code of a locale to the locale in
         */
        private val twoLetterSystemLocaleMapping: Map<String, Locale>

        fun getIso3Code(locale: Locale): String? {
            try {
                if (locale.country.isBlank()) {
                    return locale.isO3Language
                }
                return "${locale.isO3Language}_${locale.isO3Country}"
            } catch (e: Exception) {
                // MissingResourceException can be thrown, in which case return null
                return null
            }
        }
        init {
            val locales = Locale.getAvailableLocales()
            val validLocales = mutableMapOf<String, Locale>()
            for (locale in locales) {
                val code = getIso3Code(locale) ?: continue
                validLocales.putIfAbsent(code, locale)
            }
            twoLetterSystemLocaleMapping = validLocales
        }
    }
}
