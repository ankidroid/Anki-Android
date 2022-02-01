/***************************************************************************************
 * Copyright (c) 2018 Mike Hardy <github@mikehardy.net>                                 *
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
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.core.app.NotificationCompat
import com.ichi2.async.ProgressSenderAndCancelListener
import com.ichi2.utils.FileUtil.ensureFileIsDirectory
import timber.log.Timber
import java.io.*
import java.nio.file.*
import java.nio.file.attribute.BasicFileAttributes

/** Implementation of {@link Compat} for SDK level 26 and higher. Check  {@link Compat}'s for more detail. */
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

    @Suppress("deprecation")
    override fun vibrate(context: Context, durationMillis: Long) {
        val vibratorManager = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        if (vibratorManager != null) {
            val effect = VibrationEffect.createOneShot(durationMillis, VibrationEffect.DEFAULT_AMPLITUDE)
            vibratorManager.vibrate(effect)
        }
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
        Files.delete(file.toPath())
    }

    // Explores the source directory tree recursively and copies each directory and each file inside each directory
    @Throws(IOException::class)
    override fun copyDirectory(srcDir: File, destDir: File, ioTask: ProgressSenderAndCancelListener<Int>, deleteAfterCopy: Boolean) {
        // If destDir exists, it must be a directory. If not, create it
        ensureFileIsDirectory(destDir)
        val sourceDirPath = srcDir.toPath()
        val destinationDirPath = destDir.toPath()
        Files.walkFileTree(
            sourceDirPath,
            object : SimpleFileVisitor<Path>() {
                @Throws(IOException::class)
                override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
                    Files.createDirectories(destinationDirPath.resolve(sourceDirPath.relativize(dir)))
                    return FileVisitResult.CONTINUE
                }

                @Throws(IOException::class)
                override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                    val destFile = destinationDirPath.resolve(sourceDirPath.relativize(file)).toFile()

                    // Copy if source file and destination file aren't of the same length
                    // i.e., copy if destination file wasn't copied completely
                    if (file.toFile().length() != destFile.length()) {
                        val outputStream: OutputStream = FileOutputStream(destFile, false)
                        val bytesCopied = copyFile(file.toString(), outputStream)
                        ioTask.doProgress(bytesCopied.toInt() / 1024)
                        outputStream.close()
                    }
                    if (deleteAfterCopy) {
                        Files.delete(file)
                    }
                    return FileVisitResult.CONTINUE
                }

                @Throws(IOException::class)
                override fun postVisitDirectory(dir: Path, exc: IOException): FileVisitResult {
                    if (deleteAfterCopy) {
                        Files.delete(dir)
                    }
                    return FileVisitResult.CONTINUE
                }
            }
        )
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
}
