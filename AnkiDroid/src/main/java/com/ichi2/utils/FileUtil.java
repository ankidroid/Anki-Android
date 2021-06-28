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
     * @param filePath          path to the file to be internalized.
     * @param internalFile      an internal cache temp file that the data is copied/internalized into.
     * @param contentResolver   this is needed to open an inputStream on the content uri.
     * @return  the internal file after copying the data across.
     * @throws IOException
     */
    @NonNull
    public static File internalizeUri(
            Uri uri, @Nullable String filePath, File internalFile, ContentResolver contentResolver
    ) throws IOException {

        // If we got a real file name, do a copy from it
        InputStream inputStream;
        if (filePath != null) {
            Timber.d("internalizeUri() got file path for direct copy from Uri %s", uri);
            try {
                inputStream = new FileInputStream(new File(filePath));
            } catch (FileNotFoundException e) {
                Timber.w(e, "internalizeUri() unable to open input stream on file %s", filePath);
                throw e;
            }
        } else {
            try {
                inputStream = contentResolver.openInputStream(uri);
            } catch (Exception e) {
                Timber.w(e, "internalizeUri() unable to open input stream from content resolver for Uri %s", uri);
                throw e;
            }
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
}
