/****************************************************************************************
 * Copyright (c) 2011 Flavio Lerda <flerda@gmail.com>                                   *
 * Copyright (c) 2022 Arthur Milchior <arthur@milchior.fr>                              *
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

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.PackageManager.NameNotFoundException
import android.graphics.Bitmap
import android.graphics.Bitmap.CompressFormat
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.AudioManager.OnAudioFocusChangeListener
import android.media.MediaRecorder
import android.net.Uri
import android.os.Bundle
import android.os.Parcel
import android.os.Parcelable
import android.util.SparseArray
import android.widget.TimePicker
import androidx.annotation.IntDef
import java.io.*

/**
 * This interface defines a set of functions that are not available on all platforms.
 *
 *
 * A set of implementations for the supported platforms are available.
 *
 *
 * Each implementation ends with a `V<n>` suffix, identifying the minimum API version on which this implementation
 * can be used. For example, see [CompatV21].
 *
 *
 * Each implementation `CompatVn` should extend the implementation `CompatVm` for the greatest m<n such that `CompatVm`
 * exists. E.g. as of July 2021 `CompatV23` extends `CompatV21` because there is no `CompatV22`.
 * If `CompatV22` were to be created one day, it will extends `CompatV22` and be extended by `CompatV23`.
 *
 *
 * Each method `method` must be implemented in the lowest Compat implementation (right now `CompatV21`, but
 * it will change when min sdk change). It must also be implemented in `CompatVn` if, in version `n` and higher,
 * a different implementation must be used. This can be done either because some method used in the API `n` got
 * deprecated, changed its behavior, or because the implementation of `method` can be more efficient.
 *
 *
 * When you call method `method` from some device with API `n`, it will uses the implementation in `CompatVm`,
 * for `m < n` as great as possible. The value of `m` being at least the current min SDK. The method may be empty,
 * for example `setupNotificationChannel`'s implementation in `CompatV21` is empty since
 * notification channels were introduced in API 26.
 *
 *
 * Example: `CompatV26` extends `CompatV23` which extends `CompatV21`. The method `vibrate` is
 * defined in `CompatV21` where only the number of seconds of vibration is taken into consideration, and is
 * redefined in `CompatV26` - using `@Override` - where the style of vibration is also taken into
 * consideration. It means that  on devices using APIs 21 to 25 included, the implementation of `CompatV21` is
 * used, and on devices using API 26 and higher, the implementation of `CompatV26` is used.
 * On the other hand a method like `setTime` that got defined in `CompatV21` and redefined in
 * `CompatV23` due to a change of API, need not be implemented again in CompatV26.
 */
interface Compat {
    fun setupNotificationChannel(context: Context)
    fun setTime(picker: TimePicker, hour: Int, minute: Int)
    fun getHour(picker: TimePicker): Int
    fun getMinute(picker: TimePicker): Int
    fun vibrate(context: Context, durationMillis: Long)
    fun getMediaRecorder(context: Context): MediaRecorder
    fun <T> readSparseArray(parcel: Parcel, loader: ClassLoader, clazz: Class<T>): SparseArray<T>?
    fun <T : Parcelable> getParcelableArrayList(bundle: Bundle, key: String, clazz: Class<T>): ArrayList<T>?

    /**
     * Retrieve extended data from the intent.
     * @param name – The name of the desired item.
     * @param className – The type of the object expected.
     * @return the value of an item previously added with putExtra(), or null if no [Serializable] value was found.
     */
    fun <T : Serializable?> getSerializableExtra(intent: Intent, name: String, className: Class<T>): T?

    /**
     * Retrieve extended data from the intent.
     * @param name – The name of the desired item.
     * @param clazz – The type of the object expected.
     * @return the value of an item previously added with putExtra(), or null if no [Parcelable] value was found.
     */
    fun <T : Parcelable?> getParcelableExtra(intent: Intent, name: String, clazz: Class<T>): T?

    /**
     * Retrieve overall information about an application package that is
     * installed on the system.
     *
     * @see PackageManager.getPackageInfo
     */
    @Throws(NameNotFoundException::class)
    fun getPackageInfo(packageManager: PackageManager, packageName: String, flags: PackageInfoFlagsCompat): PackageInfo

    /**
     * Copy file at path [source] to path [target]
     */
    @Throws(IOException::class)
    fun copyFile(source: String, target: String)

    /**
     * Copy file at path [source] to [target]
     * @return the number of bytes read or written
     */
    @Throws(IOException::class)
    fun copyFile(source: String, target: OutputStream): Long

    /**
     * Copy file at path [source] to path [target]
     * @return the number of bytes read or written
     */
    @Throws(IOException::class)
    fun copyFile(source: InputStream, target: String): Long

    /**
     * Deletes a provided file/directory. If the file is a directory then the directory must be empty
     * @see File.delete
     * @see java.nio.file.Files.delete
     * @throws FileNotFoundException If the file does not exist
     * @throws IOException If the file failed to be deleted
     */
    @Throws(IOException::class)
    fun deleteFile(file: File)

    /**
     * Whether a directory has at least one files
     * @return Whether the directory has file.
     * @throws SecurityException If a security manager exists and its SecurityManager.checkRead(String)
     * method denies read access to the directory
     * @throws FileNotFoundException if the file do not exists
     * @throws NotDirectoryException if the file could not otherwise be opened because it is not
     * a directory (optional specific exception), (starting at API 26)
     * @throws IOException – if an I/O error occurs
     */
    @Throws(IOException::class)
    fun hasFiles(directory: File): Boolean {
        contentOfDirectory(directory).use { stream -> return stream.hasNext() }
    }

    /**
     * Same as [File::createDirectories]. Does not throw if directory already exists
     * @param directory a directory to create. Create parents if necessary
     * @throws IOException
     */
    @Throws(IOException::class)
    fun createDirectories(directory: File)
    fun hasVideoThumbnail(path: String): Boolean
    fun requestAudioFocus(audioManager: AudioManager, audioFocusChangeListener: OnAudioFocusChangeListener, audioFocusRequest: AudioFocusRequest?)
    fun abandonAudioFocus(audioManager: AudioManager, audioFocusChangeListener: OnAudioFocusChangeListener, audioFocusRequest: AudioFocusRequest?)

    @IntDef(
        flag = true,
        value = [
            PendingIntent.FLAG_ONE_SHOT, PendingIntent.FLAG_NO_CREATE, PendingIntent.FLAG_CANCEL_CURRENT, PendingIntent.FLAG_UPDATE_CURRENT, // PendingIntent.FLAG_IMMUTABLE
            // PendingIntent.FLAG_MUTABLE
            Intent.FILL_IN_ACTION, Intent.FILL_IN_DATA, Intent.FILL_IN_CATEGORIES, Intent.FILL_IN_COMPONENT, Intent.FILL_IN_PACKAGE, Intent.FILL_IN_SOURCE_BOUNDS, Intent.FILL_IN_SELECTOR, Intent.FILL_IN_CLIP_DATA
        ]
    )
    @kotlin.annotation.Retention(AnnotationRetention.SOURCE)
    annotation class PendingIntentFlags

    /**
     * Retrieve a PendingIntent that will start a new activity, like calling
     * [Context.startActivity(Intent)][Context.startActivity].
     * Note that the activity will be started outside of the context of an
     * existing activity, so you must use the [ Intent.FLAG_ACTIVITY_NEW_TASK][Intent.FLAG_ACTIVITY_NEW_TASK] launch flag in the Intent.
     *
     *
     * For security reasons, the [android.content.Intent]
     * you supply here should almost always be an *explicit intent*,
     * that is specify an explicit component to be delivered to through
     * [Intent.setClass]
     *
     * @param context The Context in which this PendingIntent should start
     * the activity.
     * @param requestCode Private request code for the sender
     * @param intent Intent of the activity to be launched.
     * @param flags May be [PendingIntent.FLAG_ONE_SHOT], [PendingIntent.FLAG_NO_CREATE],
     * [PendingIntent.FLAG_CANCEL_CURRENT], [PendingIntent.FLAG_UPDATE_CURRENT],
     * or any of the flags as supported by
     * [Intent.fillIn()][Intent.fillIn] to control which unspecified parts
     * of the intent that can be supplied when the actual send happens.
     *
     * @return Returns an existing or new PendingIntent matching the given
     * parameters.  May return null only if [PendingIntent.FLAG_NO_CREATE] has been
     * supplied.
     */
    fun getImmutableActivityIntent(context: Context, requestCode: Int, intent: Intent, @PendingIntentFlags flags: Int): PendingIntent

    /**
     * Retrieve a PendingIntent that will perform a broadcast, like calling
     * [Context.sendBroadcast()][Context.sendBroadcast].
     *
     *
     * For security reasons, the [android.content.Intent]
     * you supply here should almost always be an *explicit intent*,
     * that is specify an explicit component to be delivered to through
     * [Intent.setClass]
     *
     * @param context The Context in which this PendingIntent should perform
     * the broadcast.
     * @param requestCode Private request code for the sender
     * @param intent The Intent to be broadcast.
     * @param flags May be [PendingIntent.FLAG_ONE_SHOT], [PendingIntent.FLAG_NO_CREATE],
     * [PendingIntent.FLAG_CANCEL_CURRENT], [PendingIntent.FLAG_UPDATE_CURRENT],
     * [PendingIntent.FLAG_IMMUTABLE] or any of the flags as supported by
     * [Intent.fillIn()][Intent.fillIn] to control which unspecified parts
     * of the intent that can be supplied when the actual send happens.
     *
     * @return Returns an existing or new PendingIntent matching the given
     * parameters.  May return null only if [PendingIntent.FLAG_NO_CREATE] has been
     * supplied.
     */
    fun getImmutableBroadcastIntent(context: Context, requestCode: Int, intent: Intent, @PendingIntentFlags flags: Int): PendingIntent

    /**
     * Writes an image represented by bitmap to the Pictures/AnkiDroid directory under the primary
     * external storage directory. Requires the WRITE_EXTERNAL_STORAGE permission to be obtained on devices running
     * API <= 28. If this condition isn't satisfied, this method will throw a [FileNotFoundException].
     *
     * @param context Used to insert the image into the appropriate MediaStore collection for API >= 29
     * @param bitmap Bitmap to be saved
     * @param baseFileName the filename of the image to be saved excluding the file extension
     * @param extension File extension of the image to be saved
     * @param format The format bitmap should be compressed to
     * @param quality Hint to the compressor specifying the quality of the compressed image
     * @return The path of the saved image
     * @throws FileNotFoundException if the device's API is <= 28 and has not obtained the
     * WRITE_EXTERNAL_STORAGE permission
     */
    @Throws(FileNotFoundException::class)
    fun saveImage(context: Context, bitmap: Bitmap, baseFileName: String, extension: String, format: CompressFormat, quality: Int): Uri

    /**
     *
     * @param directory A directory.
     * @return a FileStream over file and directory of this directory.
     * null in case of trouble. This stream must be closed explicitly when done with it.
     * @throws NotDirectoryException if the file exists and is not a directory (starting at API 26)
     * @throws FileNotFoundException if the file do not exists
     * @throws IOException if files can not be listed. On non existing or non-directory file up to API 25. This also occurred on an existing directory because of permission issue
     * that we could not reproduce. See https://github.com/ankidroid/Anki-Android/issues/10358
     * @throws SecurityException – If a security manager exists and its SecurityManager.checkRead(String) method denies read access to the directory
     */
    @Throws(IOException::class)
    fun contentOfDirectory(directory: File): FileStream

    companion object {
        /* Mock the Intent PROCESS_TEXT constants introduced in API 23. */
        const val ACTION_PROCESS_TEXT = "android.intent.action.PROCESS_TEXT"
        const val EXTRA_PROCESS_TEXT = "android.intent.extra.PROCESS_TEXT"
    }
}
