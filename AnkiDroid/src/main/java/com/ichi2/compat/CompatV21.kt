/*
 * Copyright (c) 2015 Timothy Rae <perceptualchaos2@gmail.com>
 * Copyright (c) 2022 Arthur Milchior <arthur@milchior.fr>
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
package com.ichi2.compat

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Bitmap.CompressFormat
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.AudioManager.OnAudioFocusChangeListener
import android.media.MediaRecorder
import android.media.ThumbnailUtils
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.Parcel
import android.os.Parcelable
import android.os.Vibrator
import android.provider.MediaStore
import android.util.SparseArray
import android.widget.TimePicker
import com.ichi2.utils.KotlinCleanup
import timber.log.Timber
import java.io.*

/** Baseline implementation of [Compat]. Check  [Compat]'s for more detail.  */
@KotlinCleanup("add extension method logging file.delete() failure" + "Fix Deprecation")
@Suppress("Deprecation")
open class CompatV21 : Compat {
    // Until API26, ignore notification channels
    override fun setupNotificationChannel(context: Context) { /* pre-API26, do nothing */
    }

    // Until API 23 the methods have "current" in the name
    override fun setTime(picker: TimePicker, hour: Int, minute: Int) {
        picker.currentHour = hour
        picker.currentMinute = minute
    }

    // Until API 26 just specify time, after that specify effect also
    override fun vibrate(context: Context, durationMillis: Long) {
        val vibratorManager = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator?
        vibratorManager?.vibrate(durationMillis)
    }

    // Until API31 the MediaRecorder constructor was default, ignoring the Context
    override fun getMediaRecorder(context: Context): MediaRecorder {
        return MediaRecorder()
    }

    override fun <T> readSparseArray(parcel: Parcel, loader: ClassLoader, clazz: Class<T>): SparseArray<T>? {
        return parcel.readSparseArray(loader)
    }

    override fun <T : Parcelable> getParcelableArrayList(bundle: Bundle, key: String, clazz: Class<T>): ArrayList<T>? {
        return bundle.getParcelableArrayList(key)
    }

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

    override fun <T : Parcelable?> getParcelableExtra(
        intent: Intent,
        name: String,
        clazz: Class<T>
    ): T? {
        return intent.getParcelableExtra<T>(name)
    }

    override fun getPackageInfo(packageManager: PackageManager, packageName: String, flags: PackageInfoFlagsCompat): PackageInfo =
        packageManager.getPackageInfo(packageName, flags.value.toInt())

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

    @Throws(IOException::class)
    override fun deleteFile(file: File) {
        if (!file.delete()) {
            if (!file.exists()) {
                throw FileNotFoundException(file.canonicalPath)
            }
            throw IOException("Unable to delete: " + file.canonicalPath)
        }
    }

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

    // Until API 23 the methods have "current" in the name
    override fun getHour(picker: TimePicker): Int {
        return picker.currentHour
    }

    // Until API 23 the methods have "current" in the name
    override fun getMinute(picker: TimePicker): Int {
        return picker.currentMinute
    }

    override fun hasVideoThumbnail(path: String): Boolean {
        return ThumbnailUtils.createVideoThumbnail(path, MediaStore.Images.Thumbnails.MINI_KIND) != null
    }

    override fun requestAudioFocus(
        audioManager: AudioManager,
        audioFocusChangeListener: OnAudioFocusChangeListener,
        audioFocusRequest: AudioFocusRequest?
    ) {
        audioManager.requestAudioFocus(
            audioFocusChangeListener, AudioManager.STREAM_MUSIC,
            AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
        )
    }

    override fun abandonAudioFocus(
        audioManager: AudioManager,
        audioFocusChangeListener: OnAudioFocusChangeListener,
        audioFocusRequest: AudioFocusRequest?
    ) {
        audioManager.abandonAudioFocus(audioFocusChangeListener)
    }

    @SuppressLint("WrongConstant")
    override fun getImmutableActivityIntent(context: Context, requestCode: Int, intent: Intent, flags: Int): PendingIntent {
        return PendingIntent.getActivity(context, requestCode, intent, flags or FLAG_IMMUTABLE)
    }

    @SuppressLint("WrongConstant")
    override fun getImmutableBroadcastIntent(context: Context, requestCode: Int, intent: Intent, flags: Int): PendingIntent {
        return PendingIntent.getBroadcast(context, requestCode, intent, flags or FLAG_IMMUTABLE)
    }

    @Throws(FileNotFoundException::class)
    override fun saveImage(context: Context, bitmap: Bitmap, baseFileName: String, extension: String, format: CompressFormat, quality: Int): Uri {
        val pictures = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
        val ankiDroidDirectory = File(pictures, "AnkiDroid")
        if (!ankiDroidDirectory.exists()) {
            ankiDroidDirectory.mkdirs()
        }
        val imageFile = File(ankiDroidDirectory, "$baseFileName.$extension")
        bitmap.compress(format, quality, FileOutputStream(imageFile))
        return Uri.fromFile(imageFile)
    }

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

    companion object {
        // Update to PendingIntent.FLAG_MUTABLE once available (API 31)
        @Suppress("unused")
        const val FLAG_MUTABLE = 1 shl 25

        // Update to PendingIntent.FLAG_IMMUTABLE once available (API 23)
        const val FLAG_IMMUTABLE = 1 shl 26
    }
}
