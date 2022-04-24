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

package com.ichi2.compat;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaRecorder;
import android.media.ThumbnailUtils;
import android.net.Uri;
import android.os.Environment;
import android.os.Vibrator;
import android.provider.MediaStore;
import android.widget.TimePicker;

import com.ichi2.utils.KotlinCleanup;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import timber.log.Timber;

/** Baseline implementation of {@link Compat}. Check  {@link Compat}'s for more detail. */
@KotlinCleanup("add extension method logging file.delete() failure")
public class CompatV21 implements Compat {

    // Update to PendingIntent.FLAG_MUTABLE once available (API 31)
    @SuppressWarnings("unused")
    public static final int FLAG_MUTABLE = 1 << 25;
    // Update to PendingIntent.FLAG_IMMUTABLE once available (API 23)
    public static final int FLAG_IMMUTABLE =  1 << 26;

    // Until API26, ignore notification channels
    @Override
    public void setupNotificationChannel(@NonNull Context context, @NonNull String id, @NonNull String name) { /* pre-API26, do nothing */ }

    // Until API 23 the methods have "current" in the name
    @Override
    @SuppressWarnings( {"deprecation", "RedundantSuppression"})
    public void setTime(@NonNull TimePicker picker, int hour, int minute) {
        picker.setCurrentHour(hour);
        picker.setCurrentMinute(minute);
    }

    // Until API 26 just specify time, after that specify effect also
    @Override
    @SuppressWarnings( {"deprecation", "RedundantSuppression"})
    public void vibrate(@NonNull Context context, long durationMillis) {
        Vibrator vibratorManager = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        if (vibratorManager != null) {
            vibratorManager.vibrate(durationMillis);
        }
    }

    // Until API31 the MediaRecorder constructor was default, ignoring the Context
    @NonNull
    @Override
    @SuppressWarnings( {"deprecation", "RedundantSuppression"})
    public MediaRecorder getMediaRecorder(@NonNull Context context) {
        return new MediaRecorder();
    }

    // Until API 26 do the copy using streams
    public void copyFile(@NonNull String source, @NonNull String target) throws IOException {
        try (InputStream fileInputStream = new FileInputStream(source)) {
            copyFile(fileInputStream, target);
        } catch (IOException e) {
            Timber.e(e, "copyFile() error copying source %s", source);
            throw e;
        }
    }

    // Until API 26 do the copy using streams
    public long copyFile(@NonNull String source, @NonNull OutputStream target) throws IOException {
        long count;

        try (InputStream fileInputStream = new FileInputStream(source)) {
            count = copyFile(fileInputStream, target);
        } catch (IOException e) {
            Timber.e(e, "copyFile() error copying source %s", source);
            throw e;
        }

        return count;
    }

    // Until API 26 do the copy using streams
    public long copyFile(@NonNull InputStream source, @NonNull String target) throws IOException {
        long bytesCopied;

        try (OutputStream targetStream = new FileOutputStream(target)) {
            bytesCopied = copyFile(source, targetStream);
        } catch (IOException ioe) {
            Timber.e(ioe, "Error while copying to file %s", target);
            throw ioe;
        }
        return bytesCopied;
    }

    // Internal implementation under the API26 copyFile APIs
    private long copyFile(@NonNull InputStream source, @NonNull OutputStream target) throws IOException {
        // balance memory and performance, it appears 32k is the best trade-off
        // https://stackoverflow.com/questions/10143731/android-optimal-buffer-size
        final byte[] buffer = new byte[1024 * 32];
        long count = 0;
        int n;
        while ((n = source.read(buffer)) != -1) {
            target.write(buffer, 0, n);
            count += n;
        }
        target.flush();
        return count;
    }

    @Override
    public void deleteFile(@NonNull File file) throws IOException {
        if (!file.delete()) {
            if (!file.exists()) {
                throw new FileNotFoundException(file.getCanonicalPath());
            }
            throw new IOException("Unable to delete: " + file.getCanonicalPath());
        }
    }


    @Override
    public void createDirectories(@NonNull File directory) throws IOException {
        if (directory.exists()) {
            if (!directory.isDirectory()) {
                throw new IOException(directory + " is not a directory");
            }
            return;
        }
        if (!directory.mkdirs()) {
            throw new IOException("Failed to create " + directory);
        }
    }


    // Until API 23 the methods have "current" in the name
    @Override
    @SuppressWarnings( {"deprecation", "RedundantSuppression"})
    public int getHour(@NonNull TimePicker picker) {
        return picker.getCurrentHour();
    }

    // Until API 23 the methods have "current" in the name
    @Override
    @SuppressWarnings( {"deprecation", "RedundantSuppression"})
    public int getMinute(@NonNull TimePicker picker) {
        return picker.getCurrentMinute();
    }

    @Override
    @SuppressWarnings({"deprecation", "RedundantSuppression"})
    public boolean hasVideoThumbnail(@NonNull String path) {
        return ThumbnailUtils.createVideoThumbnail(path, MediaStore.Images.Thumbnails.MINI_KIND) != null;
    }
    
    @Override
    @SuppressWarnings( {"deprecation", "RedundantSuppression"})
    public void requestAudioFocus(@NonNull AudioManager audioManager, @NonNull AudioManager.OnAudioFocusChangeListener audioFocusChangeListener,
                                  @Nullable AudioFocusRequest audioFocusRequest) {
        audioManager.requestAudioFocus(audioFocusChangeListener, AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK);
    }

    @Override
    @SuppressWarnings( {"deprecation", "RedundantSuppression"})
    public void abandonAudioFocus(@NonNull AudioManager audioManager, @NonNull AudioManager.OnAudioFocusChangeListener audioFocusChangeListener,
                                  @Nullable AudioFocusRequest audioFocusRequest) {
        audioManager.abandonAudioFocus(audioFocusChangeListener);
    }


    @NonNull
    @Override
    public PendingIntent getImmutableActivityIntent(@NonNull Context context, int requestCode, @NonNull Intent intent, int flags) {
        //noinspection WrongConstant
        return PendingIntent.getActivity(context, requestCode, intent, flags | FLAG_IMMUTABLE);
    }


    @NonNull
    @Override
    public PendingIntent getImmutableBroadcastIntent(@NonNull Context context, int requestCode, @NonNull Intent intent, int flags) {
        //noinspection WrongConstant
        return PendingIntent.getBroadcast(context, requestCode, intent, flags | FLAG_IMMUTABLE);
    }


    @NonNull
    @Override
    @SuppressWarnings( {"deprecation", "RedundantSuppression"})
    public Uri saveImage(@NonNull Context context, @NonNull Bitmap bitmap, @NonNull String baseFileName, @NonNull String extension, @NonNull Bitmap.CompressFormat format, int quality) throws FileNotFoundException {
        File pictures = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
        File ankiDroidDirectory = new File(pictures, "AnkiDroid");
        if (!ankiDroidDirectory.exists()) {
            //noinspection ResultOfMethodCallIgnored
            ankiDroidDirectory.mkdirs();
        }
        File imageFile = new File(ankiDroidDirectory, baseFileName + "." + extension);
        bitmap.compress(format, quality, new FileOutputStream(imageFile));
        return Uri.fromFile(imageFile);
    }

    /* This method actually read the full content of the directory.
    * It is linear in time and space in the number of file and directory in the directory.
    * However, hasNext and next should be constant in time and space. */
    @Override
    public @NonNull FileStream contentOfDirectory(@NonNull File directory) throws IOException {
        File[] paths = directory.listFiles();
        if (paths == null) {
            if (!directory.exists()) {
                throw new FileNotFoundException(directory.getPath());
            }
            throw new IOException("Directory " + directory.getPath() + "'s file can not be listed. Probable cause are that it's not a directory (which violate the method's assumption) or a permission issue.");
        }
        int length = paths.length;
        return new FileStream() {
            @Override
            public void close() {
                // No op. Nothing to close here.
            }


            private int mOrd = 0;
            @Override
            public boolean hasNext() {
                return mOrd < length;
            }

            @Override
            public File next() {
                return paths[mOrd++];
            }
        };
    }
}
