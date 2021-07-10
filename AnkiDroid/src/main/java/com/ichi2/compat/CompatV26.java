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

package com.ichi2.compat;

import android.annotation.TargetApi;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.os.VibrationEffect;
import android.os.Vibrator;

import com.ichi2.async.ProgressSenderAndCancelListener;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;

import timber.log.Timber;

/** Implementation of {@link Compat} for SDK level 26 */
@TargetApi(26)
public class CompatV26 extends CompatV23 implements Compat {

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
    @Override
    public void setupNotificationChannel(Context context, String id, String name) {
        Timber.i("Creating notification channel with id/name: %s/%s",id, name);
        NotificationManager manager = (NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE);
        NotificationChannel notificationChannel = new NotificationChannel(id, name, NotificationManager.IMPORTANCE_DEFAULT);
        notificationChannel.setShowBadge(true);
        notificationChannel.setLockscreenVisibility(NotificationCompat.VISIBILITY_PUBLIC);
        manager.createNotificationChannel(notificationChannel);
    }

    @Override
    public void vibrate(Context context, long durationMillis) {
        Vibrator vibratorManager = (Vibrator)context.getSystemService(Context.VIBRATOR_SERVICE);
        if (vibratorManager != null) {
            VibrationEffect effect = VibrationEffect.createOneShot(durationMillis, VibrationEffect.DEFAULT_AMPLITUDE);
            vibratorManager.vibrate(effect);
        }
    }

    @Override
    public void copyFile(@NonNull String source, @NonNull String target) throws IOException {
        Files.copy(Paths.get(source), Paths.get(target), StandardCopyOption.REPLACE_EXISTING);
    }

    @Override
    public long copyFile(@NonNull String source, @NonNull OutputStream target) throws IOException {
        return Files.copy(Paths.get(source), target);
    }

    @Override
    public long copyFile(@NonNull InputStream source, @NonNull String target) throws IOException {
        return Files.copy(source, Paths.get(target), StandardCopyOption.REPLACE_EXISTING);
    }

    /**
     * Copies directory represented by source to the directory represented by destination.
     * Assumes that the App has access to the Legacy Storage Directory via WRITE_EXTERNAL_STORAGE.
     * Sends progress in kilobytes via the listener, ioTask, after each file copy.
     * <p><br>
     * Uses {@link Files#walkFileTree(Path, FileVisitor)} to visit all levels of the file tree and copy
     * each directory and file to the destination.
     * <p><br>
     * @param source Abstract representation of source directory
     * @param destination Abstract representation of destination directory
     * @param ioTask Listener used to send progress updates
     * @return <code>true</code> if directory copied to destination
     */
    @Override
    public boolean copyDirectory(@NonNull File source, @NonNull File destination, ProgressSenderAndCancelListener<Integer> ioTask) {
        Path sourceDirPath = source.toPath();
        Path destinationDirPath = destination.toPath();

        try {
            Files.walkFileTree(sourceDirPath, new SimpleFileVisitor<Path>() {

                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                    Files.createDirectories(destinationDirPath.resolve(sourceDirPath.relativize(dir)));
                    return FileVisitResult.CONTINUE;
                }


                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    File destination = destinationDirPath.resolve(sourceDirPath.relativize(file)).toFile();

                    // Copy if source file and destination file aren't of the same length
                    // i.e., copy if destination file wasn't copied completely
                    if (file.toFile().length() != destination.length()) {
                        OutputStream outputStream = new FileOutputStream(destination, false);
                        long bytesCopied = copyFile(file.toString(), outputStream);
                        ioTask.doProgress((int) bytesCopied / 1024);
                        outputStream.close();
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            Timber.w(e);
            return false;
        }

        return true;
    }
}
