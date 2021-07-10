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

import android.content.Context;
import android.os.Vibrator;
import android.widget.TimePicker;

import com.ichi2.async.ProgressSenderAndCancelListener;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import androidx.annotation.NonNull;
import timber.log.Timber;

/** Baseline implementation of {@link Compat} with implementations for older APIs */
public class CompatV21 implements Compat {

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


    /**
     * Copies directory represented by source to the directory represented by destination.
     * Assumes that the App has access to the Legacy Storage Directory via WRITE_EXTERNAL_STORAGE.
     * Sends progress in kilobytes via the listener, ioTask, after each file copy.
     * <p><br>
     * Explores the directory tree recursively and copies each directory and file to the destination.
     * <p><br>
     * @param source Abstract representation of source directory
     * @param destination Abstract representation of destination directory
     * @param ioTask Listener used to send progress updates
     * @return <code>true</code> if directory copied to destination
     */
    @Override
    public boolean copyDirectory(@NonNull File source, @NonNull File destination, ProgressSenderAndCancelListener<Integer> ioTask) {
        if (source.isDirectory()) {
            // Create destination directory
            if (!destination.exists()) {
                destination.mkdirs();
            }

            String[] files = source.list();

            // Directory is empty and has already been created at the destination
            if (files == null) {
                return true;
            }

            // Copy directory contents
            for (String file : files) {
                File srcFile = new File(source, file);
                File destFile = new File(destination, file);

                // Copy if source file and destination file aren't of the same length
                // i.e., copy if destination file wasn't copied completely
                if (srcFile.length() != destFile.length()) {
                    if (!copyDirectory(srcFile, destFile, ioTask)) {
                        return false;
                    }
                }
            }
        } else {
            try {
                OutputStream out = new FileOutputStream(destination, false);
                ioTask.doProgress((int) copyFile(source.getAbsolutePath(), out) / 1024);
                out.close();
            } catch (IOException e) {
                Timber.w(e);
                return false;
            }
        }
        return true;
    }


    /**
     * Moves directory represented by source to the directory represented by destination.
     * Assumes that the App has access to the Legacy Storage Directory via WRITE_EXTERNAL_STORAGE.
     * Sends progress in kilobytes via the listener, ioTask, after each file copy.
     * <p><br>
     * Attempts to move the directory by renaming its path. This is the fastest approach but it is only possible if the
     * source & destination directories are on the same storage partition.
     * <p><br>
     * If renaming the path isn't possible, it explores the directory tree and copies each directory and file
     * to the destination.
     * <p><br>
     * @param source Abstract representation of source directory
     * @param destination Abstract representation of destination directory
     * @param ioTask Listener used to send progress updates
     * @return <code>true</code> if directory moved to destination
     */
    @Override
    public boolean moveDirectory(@NonNull File source, @NonNull File destination, ProgressSenderAndCancelListener<Integer> ioTask) {
        // Try renaming the file's paths - faster way to move a directory
        boolean directoryCopied = source.renameTo(destination);

        // If it doesn't work, resort to exploring the directory tree manually and copying files
        if (!directoryCopied) {
            directoryCopied = copyDirectory(source, destination, ioTask);
        }

        if (directoryCopied) {
            source.delete();
            return true;
        }

        return false;
    }

    // Until API 23 the methods have "current" in the name
    @Override
    @SuppressWarnings("deprecation")
    public int getHour(TimePicker picker) { return picker.getCurrentHour(); }

    // Until API 23 the methods have "current" in the name
    @Override
    @SuppressWarnings("deprecation")
    public int getMinute(TimePicker picker) { return picker.getCurrentMinute(); }
}