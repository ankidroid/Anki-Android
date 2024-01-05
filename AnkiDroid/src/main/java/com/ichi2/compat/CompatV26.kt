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
import android.content.Context
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.View
import androidx.annotation.VisibleForTesting
import com.ichi2.anki.NotificationChannels
import java.io.*
import java.nio.file.*

/** Implementation of [Compat] for SDK level 26 and higher. Check  [Compat]'s for more detail.  */
@TargetApi(26)
open class CompatV26 : CompatV24(), Compat {
    /**
     * In Oreo and higher, you must create a channel for all notifications.
     * This will create the channel if it doesn't exist, or if it exists it will update the name.
     */
    override fun setupNotificationChannel(context: Context) {
        NotificationChannels.setup(context)
    }

    override fun setTooltipTextByContentDescription(view: View) { /* Nothing to do API26+ */
    }

    @Suppress("DEPRECATION") // VIBRATOR_SERVICE => VIBRATOR_MANAGER_SERVICE handled in CompatV31
    override fun vibrate(context: Context, durationMillis: Long) {
        val vibratorManager = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
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
    override fun contentOfDirectory(directory: File): FileStream {
        val pathsStream: DirectoryStream<Path> = try {
            newDirectoryStream(directory.toPath())
        } catch (noSuchFileException: NoSuchFileException) {
            throw FileNotFoundException(
                """
                    ${noSuchFileException.file}
                    ${noSuchFileException.cause}
                    ${noSuchFileException.stackTrace}
                """.trimIndent()
            )
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
