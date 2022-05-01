/***************************************************************************************
 * Copyright (c) 2018 Mike Hardy <github@mikehardy.net>                                 *
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

import android.annotation.TargetApi
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.AudioManager.OnAudioFocusChangeListener
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.annotation.RequiresApi
import androidx.annotation.VisibleForTesting
import androidx.core.app.NotificationCompat
import com.ichi2.utils.KotlinCleanup
import timber.log.Timber
import java.io.*
import java.nio.file.*

/** Implementation of [Compat] for SDK level 26 and higher. Check  [Compat]'s for more detail.  */
@TargetApi(26)
open class CompatV26 : CompatV23(), Compat {
    /**
     * In Oreo and higher, you must create a channel for all notifications.
     * This will create the channel if it doesn't exist, or if it exists it will update the name.
     *
     * Note that once a channel is created, only the name may be changed as long as the application
     * is installed on the user device. All other settings are fully under user control.
     *
     * @param context the Context with a handle to the NotificationManager
     * @param id the unique (within the package) id the channel for programmatic access
     * @param name the user-visible name for the channel
     */
    override fun setupNotificationChannel(context: Context, id: String, name: String) {
        Timber.i("Creating notification channel with id/name: %s/%s", id, name)
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notificationChannel = NotificationChannel(id, name, NotificationManager.IMPORTANCE_DEFAULT)
        notificationChannel.setShowBadge(true)
        notificationChannel.lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
        manager.createNotificationChannel(notificationChannel)
    }

    @RequiresApi(Build.VERSION_CODES.S)
    override fun vibrate(context: Context, durationMillis: Long) {
        val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as Vibrator
        val effect = VibrationEffect.createOneShot(durationMillis, VibrationEffect.DEFAULT_AMPLITUDE)
        vibratorManager.vibrate(effect)
    }

    @Throws(IOException::class)
    override fun copyFile(source: String, target: String) {
        Files.copy(Paths.get(source), Paths.get(target), StandardCopyOption.REPLACE_EXISTING)
    }

    @Throws(IOException::class)
    override fun copyFile(source: String, target: OutputStream): Long {
        return Files.copy(Paths.get(source), target)
    }

    @Throws(IOException::class)
    override fun copyFile(source: InputStream, target: String): Long {
        return Files.copy(source, Paths.get(target), StandardCopyOption.REPLACE_EXISTING)
    }

    @Throws(IOException::class)
    override fun deleteFile(file: File) {
        try {
            Files.delete(file.toPath())
        } catch (ex: NoSuchFileException) {
            throw FileNotFoundException(file.canonicalPath)
        }
    }

    @Throws(IOException::class)
    override fun createDirectories(directory: File) {
        Files.createDirectories(directory.toPath())
    }

    override fun requestAudioFocus(
        audioManager: AudioManager,
        audioFocusChangeListener: OnAudioFocusChangeListener,
        audioFocusRequest: AudioFocusRequest?
    ) {
        // requestAudioFocus needs NonNull argument
        if (audioFocusRequest != null) {
            audioManager.requestAudioFocus(audioFocusRequest)
        }
    }

    override fun abandonAudioFocus(
        audioManager: AudioManager,
        audioFocusChangeListener: OnAudioFocusChangeListener,
        audioFocusRequest: AudioFocusRequest?
    ) {
        // abandonAudioFocusRequest needs NonNull argument
        if (audioFocusRequest != null) {
            audioManager.abandonAudioFocusRequest(audioFocusRequest)
        }
    }

    @VisibleForTesting
    @Throws(IOException::class)
    fun newDirectoryStream(dir: Path?): DirectoryStream<Path> {
        return Files.newDirectoryStream(dir)
    }

    /*
     * This method uses [Files.newDirectoryStream].
     * Hence this method, hasNext and next should be constant in time and space.
     */
    @Throws(IOException::class)
    @KotlinCleanup("fix IDE lint issues")
    override fun contentOfDirectory(directory: File): FileStream {
        val pathsStream: DirectoryStream<Path> = try {
            newDirectoryStream(directory.toPath())
        } catch (e: IOException) {
            if (e is NoSuchFileException) {
                throw FileNotFoundException(
                    """
                    ${e.file}
                    ${e.cause}
                    ${e.stackTrace}
                    """.trimIndent()
                )
            }
            throw e
        }
        val paths: Iterator<Path> = pathsStream.iterator()
        return object : FileStream {
            @Throws(IOException::class)
            override fun close() {
                pathsStream.close()
            }

            @Throws(IOException::class)
            override operator fun hasNext(): Boolean {
                return try {
                    paths.hasNext()
                } catch (e: DirectoryIteratorException) {
                    // According to the documentation, it's the only exception it can throws.
                    throw e.cause!!
                }
            }

            @Throws(IOException::class)
            override operator fun next(): File {
                // According to the documentation, if [hasNext] returned true, [next] is guaranteed to succeed.
                return try {
                    paths.next().toFile()
                } catch (e: DirectoryIteratorException) {
                    throw e.cause!!
                }
            }
        }
    }
}
