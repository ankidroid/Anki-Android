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

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.PackageManager.NameNotFoundException
import android.content.pm.ResolveInfo
import android.graphics.Bitmap
import android.graphics.Bitmap.CompressFormat
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.AudioManager.OnAudioFocusChangeListener
import android.media.MediaRecorder
import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.annotation.CheckResult
import java.io.*
import java.util.*

/**
 * This interface defines a set of functions that are not available on all platforms.
 *
 *
 * A set of implementations for the supported platforms are available.
 *
 *
 * Each implementation ends with a `V<n>` suffix, identifying the minimum API version on which this implementation
 * can be used. For example, see [CompatV23].
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
    fun setTooltipTextByContentDescription(view: View)
    fun vibrate(context: Context, durationMillis: Long)
    fun getMediaRecorder(context: Context): MediaRecorder
    fun resolveActivity(packageManager: PackageManager, intent: Intent, flags: ResolveInfoFlagsCompat): ResolveInfo?
    fun resolveService(packageManager: PackageManager, intent: Intent, flags: ResolveInfoFlagsCompat): ResolveInfo?
    fun queryIntentActivities(packageManager: PackageManager, intent: Intent, flags: ResolveInfoFlagsCompat): List<ResolveInfo>

    /**
     * Retrieve extended data from the intent.
     * @param name – The name of the desired item.
     * @param className – The type of the object expected.
     * @return the value of an item previously added with putExtra(), or null if no [Serializable] value was found.
     */
    fun <T : Serializable?> getSerializableExtra(intent: Intent, name: String, className: Class<T>): T?

    /**
     * Returns the value associated with the given key, or `null` if:
     * * No mapping of the desired type exists for the given key.
     * * A `null` value is explicitly associated with the key.
     * * The object is not of type `clazz`.
     *
     * @param key a String, or `null`
     * @param clazz The expected class of the returned type
     * @return a Serializable value, or `null`
     */
    fun <T : Serializable?> getSerializable(bundle: Bundle, key: String, clazz: Class<T>): T?

    /**
     * Retrieve overall information about an application package that is
     * installed on the system.
     *
     * @see PackageManager.getPackageInfo
     * @throws NameNotFoundException if no such package is available to the caller.
     * * Can be null: https://cs.android.com/android/platform/superproject/+/master:frameworks/base/services/core/java/com/android/server/pm/ComputerEngine.java;drc=c4ad8bc669e66262a00798b57132347a0d0aa2ac;bpv=1;bpt=1;l=1705?q=getPackageInfoInternal&ss=android&gsn=getPackageInfoInternalBody&gs=kythe%3A%2F%2Fandroid.googlesource.com%2Fplatform%2Fsuperproject%3Flang%3Djava%3Fpath%3Dcom.android.server.pm.ComputerEngine%23977e4a94695fef516f4b2d9fa73dea77cfaf06eff40c6fb3ec9bd80c6e18a08f
     */
    @Throws(NameNotFoundException::class)
    fun getPackageInfo(packageManager: PackageManager, packageName: String, flags: PackageInfoFlagsCompat): PackageInfo?

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
     * @throws java.nio.file.NotDirectoryException if the file could not otherwise be opened because it is not
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
     * @throws java.nio.file.NotDirectoryException if the file exists and is not a directory (starting at API 26)
     * @throws FileNotFoundException if the file do not exists
     * @throws IOException if files can not be listed. On non existing or non-directory file up to API 25. This also occurred on an existing directory because of permission issue
     * that we could not reproduce. See https://github.com/ankidroid/Anki-Android/issues/10358
     * @throws SecurityException – If a security manager exists and its SecurityManager.checkRead(String) method denies read access to the directory
     */
    @Throws(IOException::class)
    fun contentOfDirectory(directory: File): FileStream

    /**
     * Converts a locale to a 'two letter' code (ISO-639-1 + ISO 3166-1 alpha-2)
     * Locale("spa", "MEX", "001") => Locale("es", "MX", "001")
     */
    @CheckResult
    fun normalize(locale: Locale): Locale
}
