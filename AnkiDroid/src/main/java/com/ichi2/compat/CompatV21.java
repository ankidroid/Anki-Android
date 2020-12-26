/***************************************************************************************
 * Copyright (c) 2015 Timothy Rae <perceptualchaos2@gmail.com>                          *
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

import com.ichi2.async.ProgressSenderAndCancelListener;
import com.ichi2.utils.FileUtil;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.FileProvider;
import timber.log.Timber;

/** Baseline implementation of {@link Compat}. Check  {@link Compat}'s for more detail. */
public class CompatV21 implements Compat {

    // Update to PendingIntent.FLAG_MUTABLE once available (API 31)
    @SuppressWarnings("unused")
    public static final int FLAG_MUTABLE = 1 << 25;
    // Update to PendingIntent.FLAG_IMMUTABLE once available (API 23)
    public static final int FLAG_IMMUTABLE =  1 << 26;

    // Until API26, ignore notification channels
    @Override
    public void setupNotificationChannel(Context context, String id, String name) { /* pre-API26, do nothing */ }

    // Until API 23 the methods have "current" in the name
    @Override
    @SuppressWarnings("deprecation")
    public void setTime(TimePicker picker, int hour, int minute) {
        picker.setCurrentHour(hour);
        picker.setCurrentMinute(minute);
    }

    // Until API 26 just specify time, after that specify effect also
    @Override
    @SuppressWarnings("deprecation")
    public void vibrate(Context context, long durationMillis) {
        Vibrator vibratorManager = (Vibrator)context.getSystemService(Context.VIBRATOR_SERVICE);
        if (vibratorManager != null) {
            vibratorManager.vibrate(durationMillis);
        }
    }

    // Until API31 the MediaRecorder constructor was default, ignoring the Context
    @Override
    @SuppressWarnings("deprecation")
    public MediaRecorder getMediaRecorder(Context context) {
        return new MediaRecorder();
    }

    // Until API 26 do the copy using streams
    public void copyFile(@NonNull String source, @NonNull String target) throws IOException {
        try (InputStream fileInputStream = new FileInputStream(new File(source))) {
            copyFile(fileInputStream, target);
        } catch (IOException e) {
            Timber.e(e, "copyFile() error copying source %s", source);
            throw e;
        }
    }

    // Until API 26 do the copy using streams
    public long copyFile(@NonNull String source, @NonNull OutputStream target) throws IOException {
        long count;

        try (InputStream fileInputStream = new FileInputStream(new File(source))) {
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
    public static long copyFile(@NonNull InputStream source, @NonNull OutputStream target) throws IOException {
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

    // Explores the source directory tree recursively and copies each directory and each file inside each directory
    @Override
    public void copyDirectory(@NonNull File srcDir, @NonNull File destDir, @NonNull ProgressSenderAndCancelListener<Integer> ioTask, boolean deleteAfterCopy) throws IOException {
        // If destDir exists, it must be a directory. If not, create it
        FileUtil.ensureFileIsDirectory(destDir);

        final File[] srcFiles = FileUtil.listFiles(srcDir);

        // Copy the contents of srcDir to destDir
        for (final File srcFile : srcFiles) {
            final File destFile = new File(destDir, srcFile.getName());
            if (srcFile.isDirectory()) {
                copyDirectory(srcFile, destFile, ioTask, deleteAfterCopy);
            } else if (srcFile.length() != destFile.length()) {
                OutputStream out = new FileOutputStream(destFile, false);
                ioTask.doProgress((int) copyFile(srcFile.getAbsolutePath(), out) / 1024);
                out.close();
            }
            if (deleteAfterCopy) {
                srcFile.delete();
            }
        }

        if (deleteAfterCopy) {
            srcDir.delete();
        }
    }

    // Attempts to first rename the contents of the directory. This operation is instant, but it fails if the
    // source and destination paths are not on the same storage partition.
    // In case rename fails, it explores the directory tree recursively and copies, then deletes every directory & every
    // file inside each directory.
    @Override
    public void moveDirectory(@NonNull final File srcDir, @NonNull final File destDir, @NonNull ProgressSenderAndCancelListener<Integer> ioTask) throws IOException {
        // If destDir exists, attempt to move the contents of srcDir by renaming
        // Otherwise, attempt to rename srcDir to destDir
        boolean renameSuccessful = true;
        if (destDir.exists()) {
            final File[] srcFiles = FileUtil.listFiles(srcDir);

            for (final File srcFile : srcFiles) {
                final File destFile = new File(destDir, srcFile.getName());
                if (!srcFile.renameTo(destFile)) {
                    renameSuccessful = false;
                    break;
                }
            }
            if (renameSuccessful) {
                srcDir.delete();
            }
        } else {
            renameSuccessful = srcDir.renameTo(destDir);
        }

        // If srcDir couldn't be moved by renaming, do a copy and delete
        if (!renameSuccessful) {
            copyDirectory(srcDir, destDir, ioTask, true);
        }
    }

    // Until API 23 the methods have "current" in the name
    @Override
    @SuppressWarnings("deprecation")
    public int getHour(TimePicker picker) { return picker.getCurrentHour(); }

    // Until API 23 the methods have "current" in the name
    @Override
    @SuppressWarnings("deprecation")
    public int getMinute(TimePicker picker) { return picker.getCurrentMinute(); }

    @Override
    @SuppressWarnings("deprecation")
    public boolean hasVideoThumbnail(@NonNull String path) {
        return ThumbnailUtils.createVideoThumbnail(path, MediaStore.Images.Thumbnails.MINI_KIND) != null;
    }
    
    @Override
    @SuppressWarnings("deprecation")
    public void requestAudioFocus(AudioManager audioManager, AudioManager.OnAudioFocusChangeListener audioFocusChangeListener,
                                  @Nullable AudioFocusRequest audioFocusRequest) {
        audioManager.requestAudioFocus(audioFocusChangeListener, AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void abandonAudioFocus(AudioManager audioManager, AudioManager.OnAudioFocusChangeListener audioFocusChangeListener,
                                  @Nullable AudioFocusRequest audioFocusRequest) {
        audioManager.abandonAudioFocus(audioFocusChangeListener);
    }

    @Override
    public PendingIntent getImmutableActivityIntent(Context context, int requestCode, Intent intent, int flags) {
        //noinspection WrongConstant
        return PendingIntent.getActivity(context, requestCode, intent, flags | FLAG_IMMUTABLE);
    }

    @Override
    public PendingIntent getImmutableBroadcastIntent(Context context, int requestCode, Intent intent, int flags) {
        //noinspection WrongConstant
        return PendingIntent.getBroadcast(context, requestCode, intent, flags | FLAG_IMMUTABLE);
    }

    @Override
    @SuppressWarnings({"deprecation", "RedundantSuppression"})
    public Uri saveImage(Context context, Bitmap bitmap, String baseFileName, String extension, Bitmap.CompressFormat format, int quality) throws FileNotFoundException {
        File pictures = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
        File ankiDroidFolder = new File(pictures, "AnkiDroid");
        if (!ankiDroidFolder.exists()) {
            //noinspection ResultOfMethodCallIgnored
            ankiDroidFolder.mkdirs();
        }
        File imageFile = new File(ankiDroidFolder, baseFileName + "." + extension);
        bitmap.compress(format, quality, new FileOutputStream(imageFile));
        return Uri.fromFile(imageFile);
    }
}
