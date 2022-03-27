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

package com.ichi2.compat;

import android.annotation.TargetApi;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.os.VibrationEffect;
import android.os.Vibrator;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.DirectoryIteratorException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Iterator;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.core.app.NotificationCompat;
import timber.log.Timber;

/** Implementation of {@link Compat} for SDK level 26 and higher. Check  {@link Compat}'s for more detail. */
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
    @SuppressWarnings("deprecation")
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

    @Override
    public void deleteFile(@NonNull File file) throws IOException {
        try {
            Files.delete(file.toPath());
        } catch (NoSuchFileException ex) {
            throw new FileNotFoundException(file.getCanonicalPath());
        }
    }

    @Override
    public void createDirectories(@NonNull File directory) throws IOException {
        Files.createDirectories(directory.toPath());
    }


    @Override
    public void requestAudioFocus(AudioManager audioManager, AudioManager.OnAudioFocusChangeListener audioFocusChangeListener,
                                  @Nullable AudioFocusRequest audioFocusRequest) {
        // requestAudioFocus needs NonNull argument
        if (audioFocusRequest != null) {
            audioManager.requestAudioFocus(audioFocusRequest);
        }
    }

    @Override
    public void abandonAudioFocus(AudioManager audioManager, AudioManager.OnAudioFocusChangeListener audioFocusChangeListener,
                                  @Nullable AudioFocusRequest audioFocusRequest) {
        // abandonAudioFocusRequest needs NonNull argument
        if (audioFocusRequest != null) {
            audioManager.abandonAudioFocusRequest(audioFocusRequest);
        }
    }

    @VisibleForTesting
    @NonNull DirectoryStream<Path> newDirectoryStream(Path dir) throws IOException {
        return Files.newDirectoryStream(dir);
    }

    /*
     * This method uses [Files.newDirectoryStream].
     * Hence this method, hasNext and next should be constant in time and space.
     */
    @Override
    public @NonNull FileStream contentOfDirectory(File directory) throws IOException {
        final DirectoryStream<Path> paths_stream;
        try {
            paths_stream = newDirectoryStream(directory.toPath());
        } catch (IOException e) {
            if (e instanceof NoSuchFileException) {
                NoSuchFileException nsfe = (NoSuchFileException) e;
                throw new FileNotFoundException(nsfe.getFile() + "\n" + nsfe.getCause() + "\n" + nsfe.getStackTrace());
            }
            throw e;
        }
        Iterator<Path> paths = paths_stream.iterator();
        return new FileStream() {
            @Override
            public void close() throws IOException {
                paths_stream.close();
            }


            @Override
            public boolean hasNext() throws IOException {
                try {
                    return paths.hasNext();
                } catch (DirectoryIteratorException e) {
                    // According to the documentation, it's the only exception it can throws.
                    throw e.getCause();
                }
            }


            @Override
            public File next() throws IOException {
                // According to the documentation, if [hasNext] returned true, [next] is guaranteed to succeed.
                try {
                    return paths.next().toFile();
                } catch (DirectoryIteratorException e) {
                    throw e.getCause();
                }
            }
        };
    }
}
