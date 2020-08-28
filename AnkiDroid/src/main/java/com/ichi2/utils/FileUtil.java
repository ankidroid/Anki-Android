package com.ichi2.utils;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.os.StatFs;

import com.ichi2.compat.CompatHelper;
import com.ichi2.libanki.utils.SystemTime;
import com.ichi2.libanki.utils.TimeUtils;

import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.util.Pair;
import timber.log.Timber;

public class FileUtil {
    /** Gets the free disk space given a file */
    public static long getFreeDiskSpace(File file, long defaultValue) {
        try {
            return CompatHelper.getCompat().getAvailableBytes(new StatFs(file.getParentFile().getPath()));
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
    @NotNull
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

}
