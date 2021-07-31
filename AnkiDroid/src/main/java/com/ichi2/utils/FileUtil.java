/*
 *  Copyright (c) 2020 David Allison <davidallisongithub@gmail.com>
 *
 *  This program is free software; you can redistribute it and/or modify it under
 *  the terms of the GNU General Public License as published by the Free Software
 *  Foundation; either version 3 of the License, or (at your option) any later
 *  version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY
 *  WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 *  PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along with
 *  this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.utils;

import android.content.ContentResolver;
import android.net.Uri;
import android.os.StatFs;

import com.ichi2.compat.CompatHelper;


import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.AbstractMap;
import java.util.Map;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import timber.log.Timber;

public class FileUtil {
    /** Gets the free disk space given a file */
    public static long getFreeDiskSpace(File file, long defaultValue) {
        try {
            return new StatFs(file.getParentFile().getPath()).getAvailableBytes();
        } catch (IllegalArgumentException e) {
            Timber.e(e, "Free space could not be retrieved");
            return defaultValue;
        }
    }

    /**
     *
     * @param uri               uri to the content to be internalized, used if filePath not real/doesn't work.
     * @param internalFile      an internal cache temp file that the data is copied/internalized into.
     * @param contentResolver   this is needed to open an inputStream on the content uri.
     * @return  the internal file after copying the data across.
     * @throws IOException
     */
    @NonNull
    public static File internalizeUri(Uri uri, File internalFile, ContentResolver contentResolver) throws IOException {
        // If we got a real file name, do a copy from it
        InputStream inputStream;

        try {
            inputStream = contentResolver.openInputStream(uri);
        } catch (Exception e) {
            Timber.w(e, "internalizeUri() unable to open input stream from content resolver for Uri %s", uri);
            throw e;
        }

        try {
            CompatHelper.getCompat().copyFile(inputStream, internalFile.getAbsolutePath());
        } catch (Exception e) {
            Timber.w(e, "internalizeUri() unable to internalize file from Uri %s to File %s", uri, internalFile.getAbsolutePath());
            throw e;
        }
        return internalFile;
    }


    /**
     * @return Key: Filename; Value: extension including dot
     */
    @Nullable
    public static Map.Entry<String, String> getFileNameAndExtension(@Nullable String fileName) {
        if (fileName == null) {
            return null;
        }
        int index = fileName.lastIndexOf(".");
        if (index < 1) {
            return null;
        }

        return new AbstractMap.SimpleEntry<>(fileName.substring(0, index), fileName.substring(index));
    }

    /**
     * Calculates the size of a directory by recursively exploring the directory tree and summing the length of each
     * file. The time taken to calculate directory size is proportional to the number of files in the directory
     * and all of its sub-directories.
     * It is assumed that directory contains no symbolic links.
     *
     * @throws IOException if the directory argument doesn't denote a directory
     * @param directory Abstract representation of the file/directory whose size needs to be calculated
     * @return Size of the directory in bytes
     */
    public static long getDirectorySize(File directory) throws IOException {
        long directorySize = 0;
        File[] files = listFiles(directory);

        for (File file : files) {
            if (file.isDirectory()) {
                directorySize += getDirectorySize(file);
            } else {
                directorySize += file.length();
            }
        }
        return directorySize;
    }

    /**
     * If dir exists, it must be a directory.
     * If not, it is created, along with any necessary parent directories (see {@link File#mkdirs()}).
     * @param dir Abstract representation of a directory
     * @throws IOException if dir is not a directory or could not be created
     */
    public static void ensureFileIsDirectory(@NonNull File dir) throws IOException {
        if (dir.exists()) {
            if (!dir.isDirectory()) {
                throw new IOException(dir + " exists but is not a directory");
            }
        } else if (!dir.mkdirs() && !dir.isDirectory()) {
            throw new IOException(dir + " directory cannot be created");
        }
    }

    /**
     * Wraps {@link File#listFiles()} and throws an exception instead of returning <code>null</code> if dir does not
     * denote an actual directory.
     *
     * @throws IOException if the contents of dir cannot be listed
     * @param dir Abstract representation of a directory
     * @return An array of abstract representations of the files / directories present in the directory represented
     * by dir
     */
    public static @NonNull File[] listFiles(@NonNull File dir) throws IOException {
        File[] children = dir.listFiles();
        if (children == null) {
            throw new IOException("Failed to list the contents of '" + dir + "'");
        }
        return children;
    }
}
